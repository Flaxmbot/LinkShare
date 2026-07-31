package app.linkshare.core.transport

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

/**
 * Interface representing a network socket transport link (e.g. WiFi Direct socket, Infra WiFi socket).
 */
interface TransportLink {
    val linkId: String
    val isAlive: Boolean
    suspend fun sendChunk(chunkIndex: Int, data: ByteArray): Boolean
}

/**
 * Work-stealing chunk scheduler for Dual-Link Bonding (F2).
 * Assigns next chunk to whichever link is free, and handles link drop failover seamlessly (FR2.3, FR2.4).
 */
class WorkStealingChunkScheduler(
    private val links: List<TransportLink>
) {
    private val mutex = Mutex()
    private val pendingChunks = ArrayDeque<Pair<Int, ByteArray>>()
    private val completedChunks = ConcurrentHashMap<Int, Boolean>()
    private val inFlightChunks = ConcurrentHashMap<Int, String>() // ChunkIndex -> LinkId

    private val _activeLinksCount = MutableStateFlow(links.count { it.isAlive })
    val activeLinksCount: StateFlow<Int> = _activeLinksCount.asStateFlow()

    /**
     * Submit chunks for transmission.
     */
    suspend fun scheduleChunks(chunks: List<Pair<Int, ByteArray>>): Boolean {
        mutex.withLock {
            pendingChunks.clear()
            completedChunks.clear()
            inFlightChunks.clear()
            pendingChunks.addAll(chunks)
        }

        val activeList = links.filter { it.isAlive }
        if (activeList.isEmpty()) return false

        _activeLinksCount.value = activeList.size

        val scope = CoroutineScope(Dispatchers.IO + Job())
        
        // Launch worker loop per available link
        val workers = activeList.map { link ->
            scope.async {
                runWorker(link)
            }
        }

        workers.awaitAll()

        // Check if all chunks completed successfully
        return mutex.withLock { pendingChunks.isEmpty() && inFlightChunks.isEmpty() }
    }

    private suspend fun runWorker(link: TransportLink) {
        while (true) {
            val chunk = mutex.withLock {
                if (pendingChunks.isEmpty()) null else pendingChunks.poll()
            } ?: break

            val (chunkIndex, data) = chunk

            mutex.withLock { inFlightChunks[chunkIndex] = link.linkId }

            val success = try {
                link.sendChunk(chunkIndex, data)
            } catch (_: Exception) {
                false
            }

            if (success) {
                mutex.withLock {
                    inFlightChunks.remove(chunkIndex)
                    completedChunks[chunkIndex] = true
                }
            } else {
                // Link dropped or failed mid-transfer (FR2.4 failover handling)
                mutex.withLock {
                    inFlightChunks.remove(chunkIndex)
                    pendingChunks.addFirst(chunk) // Re-queue chunk for remaining workers
                }
                _activeLinksCount.value = links.count { it.isAlive }
                // Stop this worker loop since this link failed
                break
            }
        }
    }
}
