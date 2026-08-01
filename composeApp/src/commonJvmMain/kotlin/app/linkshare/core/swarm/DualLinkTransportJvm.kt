package app.linkshare.core.swarm

import app.linkshare.core.client.RemoteDeviceClient
import app.linkshare.model.PeerDevice
import app.linkshare.platform.PlatformNetwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger

private const val MAGIC = "LSDB1"

/** Two independently bound TCP lanes sharing one verified piece queue. */
class DualLinkTransfer(
    private val client: RemoteDeviceClient = RemoteDeviceClient()
) {
    suspend fun download(
        peer: PeerDevice,
        pin: String,
        remotePath: String,
        destinationDirectory: String,
        localInterfaces: List<String> = PlatformNetwork.getAllActiveIpAddresses().map { it.ip }
    ): Result<File> = withContext(Dispatchers.IO) {
        if (localInterfaces.size < 2) return@withContext Result.failure(IllegalStateException("Two local interfaces are required"))
        var store: FilePieceStore? = null
        try {
            val remoteIp = peer.ipAddress ?: error("Peer has no address")
            val manifest = client.fetchSwarmManifest(remoteIp, peer.port, pin, remotePath).getOrThrow()
            val directory = File(destinationDirectory)
            if (!directory.exists() && !directory.mkdirs()) error("Unable to create destination directory")
            val output = File(directory, manifest.fileName)
            val pieceStore = FilePieceStore(output, manifest)
            store = pieceStore
            val nextPiece = AtomicInteger(0)
            coroutineScope {
                localInterfaces.take(2).map { localIp ->
                    async(Dispatchers.IO) {
                        openLane(localIp, remoteIp, peer.port + 1, pin, remotePath, manifest, pieceStore, nextPiece)
                    }
                }.awaitAll()
            }
            if (!pieceStore.piecesOwned.isComplete()) error("Dual-link transfer ended before all pieces arrived")
            Result.success(output)
        } catch (error: Throwable) {
            Result.failure(error)
        } finally {
            store?.close()
        }
    }

    private suspend fun openLane(
        localIp: String,
        remoteIp: String,
        port: Int,
        pin: String,
        remotePath: String,
        manifest: app.linkshare.model.SwarmManifest,
        store: FilePieceStore,
        nextPiece: AtomicInteger
    ) {
        Socket().use { socket ->
            socket.tcpNoDelay = true
            socket.keepAlive = true
            socket.bind(InetSocketAddress(localIp, 0))
            socket.connect(InetSocketAddress(remoteIp, port), 5000)
            val input = DataInputStream(socket.getInputStream().buffered())
            val output = DataOutputStream(socket.getOutputStream().buffered())
            output.writeUTF(MAGIC)
            output.writeUTF(pin)
            output.writeUTF(remotePath)
            output.flush()
            check(input.readBoolean()) { "Dual-link authentication rejected" }

            while (true) {
                val index = nextPiece.getAndIncrement()
                if (index >= manifest.pieceCount) break
                output.writeInt(index)
                output.flush()
                val returnedIndex = input.readInt()
                val size = input.readInt()
                check(returnedIndex == index && size == manifest.expectedPieceSize(index)) { "Invalid dual-link piece response" }
                val data = ByteArray(size)
                input.readFully(data)
                check(store.writePiece(index, data)) { "Invalid hash for dual-link piece $index" }
            }
        }
    }
}
