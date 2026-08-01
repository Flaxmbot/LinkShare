package app.linkshare.core.swarm

import app.linkshare.core.client.RemoteDeviceClient
import app.linkshare.model.PeerDevice
import app.linkshare.platform.PlatformNetwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Real JVM transfer loop for verified, resumable piece downloads.
 * Each peer must expose the authenticated swarm endpoints.
 */
actual class SwarmTransferCoordinator actual constructor(
    private val client: RemoteDeviceClient
) {
    actual suspend fun download(
        peers: List<PeerDevice>,
        pin: String,
        remotePath: String,
        destinationDirectory: String
    ): Result<File> = withContext(Dispatchers.IO) {
        if (peers.isEmpty()) return@withContext Result.failure(IllegalArgumentException("No transfer peers"))
        try {
            val first = peers.first()
            val manifest = client.fetchSwarmManifest(first.ipAddress ?: return@withContext Result.failure(IllegalArgumentException("Peer has no address")), first.port, pin, remotePath).getOrThrow()
            val directory = File(destinationDirectory)
            if (!directory.exists() && !directory.mkdirs()) error("Unable to create destination folder")
            val output = File(directory, manifest.fileName)
            val store = FilePieceStore(output, manifest)
            val engine = SwarmEngine()
            engine.startRecipientSwarm(manifest, peers)
            engine.setDualLinkActive(PlatformNetwork.getLinkCapabilities().supportsDualLink)
            engine.attachPieceStore(store)
            peers.forEach { peer ->
                val bitset = app.linkshare.model.PieceBitset.allOwned(manifest.pieceCount)
                engine.updatePeerBitset(peer.id, bitset)
            }

            coroutineScope {
                val workers = peers.mapIndexed { workerIndex, _ ->
                    async(Dispatchers.IO) {
                        while (true) {
                            val request = engine.requestNextPiece() ?: break
                            val peer = peers[(request.pieceIndex + workerIndex) % peers.size]
                            val piece = client.fetchSwarmPiece(peer.ipAddress ?: "", peer.port, pin, remotePath, request.pieceIndex).getOrElse {
                                engine.releasePiece(request.pieceIndex)
                                throw it
                            }
                            if (!engine.onPieceReceived(peer.id, request.pieceIndex, piece)) {
                                throw IllegalStateException("Peer returned an invalid piece ${request.pieceIndex}")
                            }
                        }
                    }
                }
                workers.awaitAll()
            }
            store.close()
            Result.success(output)
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }
}
