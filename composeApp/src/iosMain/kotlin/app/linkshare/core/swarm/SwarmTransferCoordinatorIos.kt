package app.linkshare.core.swarm

import app.linkshare.core.client.RemoteDeviceClient
import app.linkshare.model.PeerDevice
import java.io.File

actual class SwarmTransferCoordinator actual constructor(
    private val client: RemoteDeviceClient
) {
    actual suspend fun download(
        peers: List<PeerDevice>,
        pin: String,
        remotePath: String,
        destinationDirectory: String
    ): Result<File> = Result.failure(UnsupportedOperationException("Swarm transfers are not available on iOS yet"))
}
