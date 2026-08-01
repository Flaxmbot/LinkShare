package app.linkshare.core.client

import app.linkshare.platform.Log
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import app.linkshare.model.SwarmManifest
import app.linkshare.core.swarm.PieceVerifier
import java.io.FileInputStream
import java.security.MessageDigest
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.atomic.AtomicInteger

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

    data class PairingResult(val deviceId: String, val token: String, val permissions: List<String>)

    suspend fun completePairing(
        ip: String,
        port: Int,
        pin: String,
        deviceId: String,
        deviceName: String,
        platform: String,
        nonce: String,
        signature: String = "",
        clientToken: String = ""
    ): Result<PairingResult> = withContextResult {
        try {
            val url = URL("http://$ip:$port/api/pair/complete?pin=${encode(pin)}&deviceId=${encode(deviceId)}&deviceName=${encode(deviceName)}&platform=${encode(platform)}&nonce=${encode(nonce)}&signature=${encode(signature)}&clientToken=${encode(clientToken)}")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 3000
                readTimeout = 5000
            }
            if (connection.responseCode !in 200..299) return@withContextResult Result.failure(IllegalStateException("Pairing failed with HTTP ${connection.responseCode}"))
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            Result.success(PairingResult(
                deviceId = jsonString(body, "deviceId") ?: deviceId,
                token = jsonString(body, "token") ?: "",
                permissions = jsonString(body, "permissions")?.split(',')?.filter { it.isNotBlank() } ?: emptyList()
            ))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun uploadFile(
        ip: String,
        port: Int,
        pin: String,
        sourcePath: String,
        remotePath: String
        ,accessToken: String? = null
    ): Result<Long> = withContextResult {
        try {
            val source = File(sourcePath)
            if (!source.isFile) return@withContextResult Result.failure(IllegalArgumentException("Source file not found"))
            val url = URL("http://$ip:$port/api/receive?path=${encode(remotePath)}&pin=${encode(pin)}")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "PUT"
                doOutput = true
                setFixedLengthStreamingMode(source.length())
                connectTimeout = 3000
                readTimeout = 30000
                setRequestProperty("Content-Type", "application/octet-stream")
                if (!accessToken.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $accessToken")
            }
            source.inputStream().use { input -> connection.outputStream.use { output -> input.copyTo(output) } }
            if (connection.responseCode !in 200..299) return@withContextResult Result.failure(IllegalStateException("Upload failed with HTTP ${connection.responseCode}"))
            Result.success(source.length())
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun uploadSwarmFile(
        ip: String,
        port: Int,
        pin: String,
        sourcePath: String,
        remotePath: String,
        pieceSize: Int = 1024 * 1024,
        accessToken: String? = null
    ): Result<Long> = withContextResult {
        val source = File(sourcePath)
        if (!source.isFile) return@withContextResult Result.failure(IllegalArgumentException("Source file not found"))
        val manifest = buildManifest(source, pieceSize)
        try {
            val startUrl = URL("http://$ip:$port/api/swarm/receive/start?path=${encode(remotePath)}&fileId=${encode(manifest.fileId)}&fileName=${encode(manifest.fileName)}&fileSize=${manifest.fileSizeBytes}&pieceSize=${manifest.pieceSize}&pieceCount=${manifest.pieceCount}&totalHash=${encode(manifest.totalHash)}&pieceHashes=${encode(manifest.pieceHashes.joinToString("|"))}&pin=${encode(pin)}")
            val start = (startUrl.openConnection() as HttpURLConnection).apply { requestMethod = "POST"; connectTimeout = 3000; readTimeout = 5000 }
            if (!accessToken.isNullOrBlank()) start.setRequestProperty("Authorization", "Bearer $accessToken")
            if (start.responseCode !in 200..299) return@withContextResult Result.failure(IllegalStateException("Swarm start failed with HTTP ${start.responseCode}"))
            val transferId = jsonString(start.inputStream.bufferedReader().use { it.readText() }, "transferId") ?: return@withContextResult Result.failure(IllegalStateException("Missing swarm transfer id"))
            val next = AtomicInteger(0)
            coroutineScope {
                (0 until minOf(4, maxOf(1, manifest.pieceCount))).map {
                    async {
                        while (true) {
                            val index = next.getAndIncrement()
                            if (index >= manifest.pieceCount) break
                            val data = readPiece(source, manifest, index)
                            val pieceUrl = URL("http://$ip:$port/api/swarm/receive/piece?transferId=${encode(transferId)}&piece=$index&pin=${encode(pin)}")
                            val connection = (pieceUrl.openConnection() as HttpURLConnection).apply {
                                requestMethod = "PUT"; doOutput = true; setFixedLengthStreamingMode(data.size); connectTimeout = 3000; readTimeout = 30000
                            }
                            if (!accessToken.isNullOrBlank()) connection.setRequestProperty("Authorization", "Bearer $accessToken")
                            connection.outputStream.use { it.write(data) }
                            if (connection.responseCode !in 200..299) error("Swarm piece $index failed with HTTP ${connection.responseCode}")
                        }
                    }
                }.awaitAll()
            }
            val completeUrl = URL("http://$ip:$port/api/swarm/receive/complete?transferId=${encode(transferId)}&path=${encode(remotePath)}&pin=${encode(pin)}")
            val complete = (completeUrl.openConnection() as HttpURLConnection).apply { requestMethod = "POST"; connectTimeout = 3000; readTimeout = 10000 }
            if (!accessToken.isNullOrBlank()) complete.setRequestProperty("Authorization", "Bearer $accessToken")
            if (complete.responseCode !in 200..299) return@withContextResult Result.failure(IllegalStateException("Swarm commit failed with HTTP ${complete.responseCode}"))
            Result.success(source.length())
        } catch (e: Exception) { Result.failure(e) }
    }

    private fun buildManifest(file: File, pieceSize: Int): SwarmManifest {
        val hashes = mutableListOf<String>()
        val total = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(pieceSize)
            var read: Int
            while (input.read(buffer).also { read = it } > 0) {
                val piece = buffer.copyOf(read)
                hashes += PieceVerifier.sha256Hex(piece)
                total.update(piece)
            }
        }
        return SwarmManifest(PieceVerifier.sha256Hex("${file.absolutePath}:${file.length()}:${file.lastModified()}".toByteArray()), file.name, file.length(), pieceSize, if (file.length() == 0L) 0 else ((file.length() + pieceSize - 1) / pieceSize).toInt(), hashes, total.digest().joinToString("") { "%02x".format(it) })
    }

    private fun readPiece(file: File, manifest: SwarmManifest, index: Int): ByteArray {
        val data = ByteArray(manifest.expectedPieceSize(index))
        java.io.RandomAccessFile(file, "r").use { input -> input.seek(index.toLong() * manifest.pieceSize); input.readFully(data) }
        return data
    }

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

    suspend fun fetchSwarmManifest(
        ip: String,
        port: Int = 8888,
        pin: String,
        remotePath: String
    ): Result<SwarmManifest> = withContextResult {
        try {
            val connection = (URL("http://$ip:$port/api/swarm/manifest?path=${encode(remotePath)}&pin=${encode(pin)}").openConnection() as HttpURLConnection).apply {
                connectTimeout = 3000
                readTimeout = 15000
            }
            if (connection.responseCode !in 200..299) return@withContextResult Result.failure(IllegalStateException("Manifest failed with HTTP ${connection.responseCode}"))
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val hashes = Regex("\\\"pieceHashes\\\":\\[(.*?)]").find(body)?.groupValues?.get(1)?.let { raw ->
                Regex("\\\"(.*?)\\\"").findAll(raw).map { unescape(it.groupValues[1]) }.toList()
            } ?: emptyList()
            Result.success(SwarmManifest(
                fileId = jsonString(body, "fileId") ?: error("Missing fileId"),
                fileName = jsonString(body, "fileName") ?: File(remotePath).name,
                fileSizeBytes = jsonLong(body, "fileSizeBytes"),
                pieceSize = jsonLong(body, "pieceSize").toInt(),
                pieceCount = jsonLong(body, "pieceCount").toInt(),
                pieceHashes = hashes,
                totalHash = jsonString(body, "totalHash") ?: error("Missing totalHash")
            ))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun fetchSwarmPiece(
        ip: String,
        port: Int = 8888,
        pin: String,
        remotePath: String,
        pieceIndex: Int
    ): Result<ByteArray> = withContextResult {
        try {
            val url = URL("http://$ip:$port/api/swarm/piece?path=${encode(remotePath)}&piece=$pieceIndex&pin=${encode(pin)}")
            val connection = (url.openConnection() as HttpURLConnection).apply { connectTimeout = 3000; readTimeout = 30000 }
            if (connection.responseCode !in 200..299) return@withContextResult Result.failure(IllegalStateException("Piece failed with HTTP ${connection.responseCode}"))
            Result.success(connection.inputStream.use { it.readBytes() })
        } catch (e: Exception) { Result.failure(e) }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
    private fun jsonString(json: String, key: String): String? =
        Regex("\\\"$key\\\":\\\"(.*?)\\\"").find(json)?.groupValues?.get(1)?.let(::unescape)
    private fun jsonLong(json: String, key: String): Long =
        Regex("\\\"$key\\\":(-?\\d+)").find(json)?.groupValues?.get(1)?.toLongOrNull() ?: error("Missing $key")
    private fun unescape(value: String): String = value.replace("\\\"", "\"").replace("\\\\", "\\")

    private suspend fun <T> withContextResult(block: suspend () -> T): T =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { block() }
}
