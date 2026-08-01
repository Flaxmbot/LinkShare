package app.linkshare.core.swarm

import app.linkshare.core.client.RemoteDeviceClient
import app.linkshare.model.PeerDevice
import java.io.File

expect class SwarmTransferCoordinator(client: RemoteDeviceClient) {
    suspend fun download(
        peers: List<PeerDevice>,
        pin: String,
        remotePath: String,
        destinationDirectory: String
    ): Result<File>
}
