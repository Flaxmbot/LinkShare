package app.linkshare.core.client

import app.linkshare.platform.Log
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

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
            val endpoint = URL("http://$ip:$port/api/browse?path=${encode(path)}&pin=${encode(pin)}")
            val connection = (endpoint.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 2500
                readTimeout = 5000
            }
            if (connection.responseCode !in 200..299) {
                return Result.failure(IllegalStateException("Peer returned HTTP ${connection.responseCode}"))
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val current = jsonString(body, "currentPath") ?: path
            val parent = jsonString(body, "parentPath") ?: "/"
            val items = Regex("""\{\"name\":\"(.*?)\",\"path\":\"(.*?)\",\"isDirectory\":(true|false),\"size\":(\d+),\"lastModified\":(\d+)\}""")
                .findAll(body)
                .map { match ->
                    RemoteFileItem(
                        name = unescape(match.groupValues[1]),
                        path = unescape(match.groupValues[2]),
                        isDirectory = match.groupValues[3] == "true",
                        sizeBytes = match.groupValues[4].toLongOrNull() ?: 0L,
                        lastModified = match.groupValues[5].toLongOrNull() ?: 0L
                    )
                }.toList()
            Result.success(RemoteDirResponse(current, parent, items))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch remote directory: ${e.message}")
            Result.failure(e)
        }
    }

    fun getStreamUrl(ip: String, port: Int = 8888, pin: String, remotePath: String): String {
        return "http://$ip:$port/api/stream?path=${encode(remotePath)}&pin=${encode(pin)}"
    }

    suspend fun downloadFile(
        ip: String,
        port: Int,
        pin: String,
        remotePath: String,
        destinationDirectory: String
    ): Result<File> = withContextResult {
        try {
            val connection = (URL(getStreamUrl(ip, port, pin, remotePath)).openConnection() as HttpURLConnection).apply {
                connectTimeout = 3000
                readTimeout = 30_000
            }
            if (connection.responseCode !in 200..299) {
                return@withContextResult Result.failure(IllegalStateException("Download failed with HTTP ${connection.responseCode}"))
            }
            val destinationDir = File(destinationDirectory)
            if (!destinationDir.exists() && !destinationDir.mkdirs()) {
                return@withContextResult Result.failure(IllegalStateException("Unable to create destination folder"))
            }
            val destination = File(destinationDir, File(remotePath).name)
            connection.inputStream.use { input ->
                FileOutputStream(destination).use { output -> input.copyTo(output) }
            }
            Result.success(destination)
        } catch (e: Exception) {
            Log.e(TAG, "Download failed: ${e.message}")
            Result.failure(e)
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
    private fun jsonString(json: String, key: String): String? =
        Regex("\\\"$key\\\":\\\"(.*?)\\\"").find(json)?.groupValues?.get(1)?.let(::unescape)
    private fun unescape(value: String): String = value.replace("\\\"", "\"").replace("\\\\", "\\")

    private suspend fun <T> withContextResult(block: suspend () -> T): T =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { block() }
}
