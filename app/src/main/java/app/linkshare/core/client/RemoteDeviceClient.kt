package app.linkshare.core.client

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Client for browsing and streaming files hosted by remote LinkShare peer devices.
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

    data class DirectoryResult(
        val currentPath: String,
        val parentPath: String,
        val files: List<RemoteFileItem>
    )

    /**
     * Fetch remote folder file tree via GET /api/browse
     */
    suspend fun fetchRemoteDirectory(
        ip: String,
        port: Int = 8080,
        pin: String,
        path: String = "/"
    ): Result<DirectoryResult> = withContext(Dispatchers.IO) {
        try {
            val encodedPath = URLEncoder.encode(path, "UTF-8")
            val urlStr = "http://$ip:$port/api/browse?path=$encodedPath&pin=$pin"
            val connection = URL(urlStr).openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.requestMethod = "GET"

            if (connection.responseCode == 200) {
                val jsonText = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonObj = JSONObject(jsonText)

                val currentPath = jsonObj.optString("currentPath", "/")
                val parentPath = jsonObj.optString("parentPath", "/")
                val filesArray = jsonObj.getJSONArray("files")

                val items = mutableListOf<RemoteFileItem>()
                for (i in 0 until filesArray.length()) {
                    val item = filesArray.getJSONObject(i)
                    items.add(
                        RemoteFileItem(
                            name = item.getString("name"),
                            path = item.getString("path"),
                            isDirectory = item.getBoolean("isDirectory"),
                            sizeBytes = item.getLong("size"),
                            lastModified = item.optLong("lastModified", 0L)
                        )
                    )
                }

                Result.success(DirectoryResult(currentPath, parentPath, items))
            } else {
                Result.failure(Exception("HTTP ${connection.responseCode}: ${connection.responseMessage}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch remote directory: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Generate direct media stream URL for in-app video/audio player modal
     */
    fun getStreamUrl(ip: String, port: Int = 8080, pin: String, path: String): String {
        val encodedPath = URLEncoder.encode(path, "UTF-8")
        return "http://$ip:$port/api/stream?path=$encodedPath&pin=$pin"
    }

    /**
     * Download remote file directly into local target file
     */
    suspend fun downloadRemoteFile(
        ip: String,
        port: Int = 8080,
        pin: String,
        remotePath: String,
        localFile: File,
        onProgress: ((Long, Long) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val encodedPath = URLEncoder.encode(remotePath, "UTF-8")
            val urlStr = "http://$ip:$port/api/download?path=$encodedPath&pin=$pin"
            val connection = URL(urlStr).openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.requestMethod = "GET"

            if (connection.responseCode == 200) {
                val contentLength = connection.contentLengthLong
                val inputStream = connection.inputStream
                val outputStream = FileOutputStream(localFile)

                val buffer = ByteArray(65536)
                var bytesRead: Int
                var totalDownloaded = 0L

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalDownloaded += bytesRead
                    onProgress?.invoke(totalDownloaded, contentLength)
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()

                Result.success(localFile)
            } else {
                Result.failure(Exception("HTTP ${connection.responseCode}: ${connection.responseMessage}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download error: ${e.message}")
            Result.failure(e)
        }
    }
}
