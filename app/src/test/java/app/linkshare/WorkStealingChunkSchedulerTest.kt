package app.linkshare

import app.linkshare.core.transport.TransportLink
import app.linkshare.core.transport.WorkStealingChunkScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

@OptIn(ExperimentalCoroutinesApi::class)
class WorkStealingChunkSchedulerTest {

    class MockTransportLink(
        override val linkId: String,
        private val shouldFailAfterChunk: Int = -1
    ) : TransportLink {
        var alive = true
        override val isAlive: Boolean get() = alive

        val sentChunks = ConcurrentHashMap<Int, Boolean>()

        override suspend fun sendChunk(chunkIndex: Int, data: ByteArray): Boolean {
            if (!alive) return false
            kotlinx.coroutines.delay(10)
            if (shouldFailAfterChunk >= 0 && sentChunks.size >= shouldFailAfterChunk) {
                alive = false
                return false
            }
            sentChunks[chunkIndex] = true
            return true
        }
    }

    @Test
    fun testDualLinkWorkStealingAndFailover() = runTest {
        val linkP2p = MockTransportLink("P2P_Link", shouldFailAfterChunk = 1) // fails on 2nd attempt
        val linkSta = MockTransportLink("STA_Link")

        val scheduler = WorkStealingChunkScheduler(listOf(linkP2p, linkSta))

        val chunks = (0 until 10).map { i -> i to "chunk_$i".toByteArray() }

        val success = scheduler.scheduleChunks(chunks)
        assertTrue(success)

        // Verify total 10 chunks were processed across remaining active link
        val totalSent = linkP2p.sentChunks.size + linkSta.sentChunks.size
        assertEquals(10, totalSent)
        assertFalse(linkP2p.isAlive)
        assertTrue(linkSta.isAlive)
    }
}
