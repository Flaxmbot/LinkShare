package app.linkshare.core.swarm

import app.linkshare.model.PeerDevice
import app.linkshare.platform.PlatformHttpServer
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue
import java.io.File

class DualLinkTransportTest {
    @Test
    fun transfersPiecesAcrossTwoBoundLanes() = runBlocking {
        val root = createTempDirectory("linkshare-dual-source").toFile()
        val destination = createTempDirectory("linkshare-dual-destination").toFile()
        val source = File(root, "payload.bin")
        val bytes = ByteArray(3 * SwarmManifestBuilder.DEFAULT_PIECE_SIZE + 17) { (it * 31).toByte() }
        source.writeBytes(bytes)
        val server = PlatformHttpServer(18888)
        try {
            server.startServer(root.absolutePath, "1234", 0)
            delay(300)
            val result = DualLinkTransfer().download(
                peer = PeerDevice("loopback", "loopback", "127.0.0.1", 18888),
                pin = "1234",
                remotePath = "/payload.bin",
                destinationDirectory = destination.absolutePath,
                localInterfaces = listOf("127.0.0.1", "127.0.0.1")
            )
            assertTrue(result.isSuccess, result.exceptionOrNull()?.stackTraceToString() ?: "dual-link transfer failed")
            assertContentEquals(bytes, result.getOrThrow().readBytes())
        } finally {
            server.stopServer()
            root.deleteRecursively()
            destination.deleteRecursively()
        }
    }
}
