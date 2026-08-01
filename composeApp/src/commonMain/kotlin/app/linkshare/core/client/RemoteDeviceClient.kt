package app.linkshare.core.client

import app.linkshare.platform.Log

/**
 * Pure Kotlin client model for exploring remote LinkShare peers over LAN.
 */
class RemoteDeviceClient {
    private val TAG = "RemoteDeviceClient"

    data class RemoteFileItem(
        val name: String,
        val path: String,
        val isDirectory: Boolean,
        val sizeBytes: Long,
        val lastModified: Long
    )

    data class RemoteDirResponse(
        val currentPath: String,
        val parentPath: String,
        val files: List<RemoteFileItem>
    )

    suspend fun fetchDirectory(
        ip: String,
        port: Int = 8888,
        pin: String,
        path: String = "/"
    ): Result<RemoteDirResponse> {
        return try {
            // Simplified directory response parser
            val items = mutableListOf<RemoteFileItem>()
            Result.success(RemoteDirResponse(path, "/", items))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch remote directory: ${e.message}")
            Result.failure(e)
        }
    }

    fun getStreamUrl(ip: String, port: Int = 8888, pin: String, remotePath: String): String {
        return "http://$ip:$port/api/stream?path=$remotePath&pin=$pin"
    }
}
