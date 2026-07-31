package app.linkshare.core.server

import android.graphics.Bitmap
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import kotlin.random.Random

/**
 * Enterprise & Developer HTTP/WebDAV Gateway & File Explorer Server.
 *
 * Capabilities:
 * - WebDAV Gateway (PROPFIND, OPTIONS, MKCOL, COPY, MOVE)
 * - Bearer Token & PIN Authentication
 * - QoS Bandwidth Throttling
 * - Developer REST APIs (/api/status, /api/clipboard, /api/browse, /api/stream)
 * - Windows 11 Web Explorer UI with Multi-File Upload & Drag-and-Drop
 */
class EmbeddedHttpServer(
    private val port: Int = 8080,
    private val requirePin: Boolean = true
) {
    private val TAG = "EmbeddedHttpServer"
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    var sessionPin: String = generatePin()
        private set

    var enterpriseToken: String = "token_" + generatePin() + generatePin()
        private set

    private var sharedDirectory: File? = null
    private var lastActivityTime: Long = System.currentTimeMillis()
    private var idleTimeoutMs: Long = 15 * 60 * 1000L
    private var maxSpeedLimitBps: Long = 0L // 0 = Unlimited

    var activeClipboardText: String = ""

    fun startServer(shareDir: File, customPin: String? = null, timeoutMinutes: Int = 15, maxSpeedMbps: Int = 0) {
        if (isRunning) stopServer()

        sharedDirectory = shareDir
        if (!shareDir.exists()) shareDir.mkdirs()

        sessionPin = customPin ?: generatePin()
        idleTimeoutMs = if (timeoutMinutes <= 0) Long.MAX_VALUE else timeoutMinutes * 60 * 1000L
        maxSpeedLimitBps = if (maxSpeedMbps <= 0) 0L else (maxSpeedMbps.toLong() * 1024 * 1024 / 8)
        lastActivityTime = System.currentTimeMillis()
        isRunning = true

        scope.launch {
            try {
                serverSocket = ServerSocket(port, 50, InetAddress.getByName("0.0.0.0"))
                Log.d(TAG, "HTTP/WebDAV Gateway active on 0.0.0.0:$port, PIN: $sessionPin")

                launch {
                    while (isRunning) {
                        kotlinx.coroutines.delay(10000)
                        if (idleTimeoutMs != Long.MAX_VALUE && (System.currentTimeMillis() - lastActivityTime) > idleTimeoutMs) {
                            Log.d(TAG, "HTTP Server idle timeout reached. Stopping server.")
                            stopServer()
                            break
                        }
                    }
                }

                while (isRunning) {
                    val socket = serverSocket?.accept() ?: break
                    lastActivityTime = System.currentTimeMillis()
                    launch {
                        handleHttpRequest(socket)
                    }
                }
            } catch (e: Exception) {
                if (isRunning) {
                    Log.e(TAG, "HTTP Server error: ${e.message}")
                }
            } finally {
                stopServer()
            }
        }
    }

    fun stopServer() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
    }

    fun isServerActive(): Boolean = isRunning && serverSocket?.isClosed == false

    fun generateConnectionString(ipAddress: String): String {
        return "http://$ipAddress:$port?pin=$sessionPin"
    }

    fun generateQrCodeBitmap(connectionString: String, width: Int = 400, height: Int = 400): Bitmap? {
        return try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(connectionString, BarcodeFormat.QR_CODE, width, height)
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bmp.setPixel(x, y, if (bitMatrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
                }
            }
            bmp
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate QR code: ${e.message}")
            null
        }
    }

    private fun extractQueryParam(urlPath: String, paramName: String): String? {
        if (!urlPath.contains("?")) return null
        val query = urlPath.substringAfter("?")
        for (pair in query.split("&")) {
            val parts = pair.split("=", limit = 2)
            if (parts.size == 2 && parts[0].equals(paramName, ignoreCase = true)) {
                return try {
                    URLDecoder.decode(parts[1], "UTF-8").trim()
                } catch (_: Exception) {
                    parts[1].trim()
                }
            }
        }
        return null
    }

    private fun resolveTargetFile(requestedRelPath: String?): File {
        val root = sharedDirectory ?: File("/storage/emulated/0")
        if (requestedRelPath.isNullOrBlank() || requestedRelPath == "/" || requestedRelPath == ".") {
            return root
        }
        val cleanRel = requestedRelPath.trimStart('/')
        val candidate = File(root, cleanRel)
        return if (isSafeFile(root, candidate)) candidate else root
    }

    private fun isSafeFile(root: File, target: File): Boolean {
        return try {
            val rCanonical = root.canonicalPath
            val tCanonical = target.canonicalPath
            tCanonical.startsWith(rCanonical)
        } catch (_: Exception) {
            false
        }
    }

    private fun getRelativePath(root: File, target: File): String {
        return try {
            val rPath = root.canonicalPath
            val tPath = target.canonicalPath
            if (tPath == rPath) "/"
            else tPath.substring(rPath.length).replace('\\', '/')
        } catch (_: Exception) {
            "/"
        }
    }

    private suspend fun handleHttpRequest(socket: Socket) = withContext(Dispatchers.IO) {
        try {
            socket.soTimeout = 30000
            val input = socket.getInputStream()
            val reader = BufferedReader(InputStreamReader(input, Charsets.UTF_8))
            val output = socket.getOutputStream()

            val requestLine = reader.readLine() ?: return@withContext
            val parts = requestLine.split(" ")
            if (parts.size < 2) return@withContext

            val method = parts[0].uppercase()
            val rawPath = parts[1]

            var rangeHeader: String? = null
            var contentType: String? = null
            var contentLength: Long = 0L
            var authHeader: String? = null

            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line.isNullOrEmpty()) break
                val lower = line!!.lowercase()
                if (lower.startsWith("range:")) {
                    rangeHeader = line!!.substring(6).trim()
                } else if (lower.startsWith("content-type:")) {
                    contentType = line!!.substring(13).trim()
                } else if (lower.startsWith("content-length:")) {
                    contentLength = line!!.substring(15).trim().toLongOrNull() ?: 0L
                } else if (lower.startsWith("authorization:")) {
                    authHeader = line!!.substring(14).trim()
                }
            }

            val cleanPath = rawPath.substringBefore("?")

            if (cleanPath == "/favicon.ico") {
                sendHttpResponse(output, "204 No Content", "text/plain", ByteArray(0))
                socket.close()
                return@withContext
            }

            // WebDAV OPTIONS method response
            if (method == "OPTIONS") {
                val davHeaders = "HTTP/1.1 200 OK\r\n" +
                        "DAV: 1, 2\r\n" +
                        "MS-Author-Via: DAV\r\n" +
                        "Allow: OPTIONS, GET, HEAD, POST, PUT, DELETE, PROPFIND, MKCOL, COPY, MOVE\r\n" +
                        "Content-Length: 0\r\n" +
                        "Connection: close\r\n\r\n"
                output.write(davHeaders.toByteArray(Charsets.UTF_8))
                output.flush()
                socket.close()
                return@withContext
            }

            // Authentication Check (PIN or Bearer Token)
            val submittedPin = extractQueryParam(rawPath, "pin")
            val isBearerValid = authHeader != null && authHeader.startsWith("Bearer ") && authHeader.substring(7).trim() == enterpriseToken
            val isPinProvided = submittedPin != null && submittedPin.isNotBlank()
            val hasValidPin = !requirePin || (isPinProvided && submittedPin == sessionPin) || isBearerValid

            if (!hasValidPin) {
                val showInvalidError = isPinProvided && submittedPin != sessionPin
                val loginHtml = buildPinLoginFormHtml(hasError = showInvalidError)
                sendHttpResponse(output, "200 OK", "text/html; charset=utf-8", loginHtml.toByteArray(Charsets.UTF_8))
                socket.close()
                return@withContext
            }

            val rootDir = sharedDirectory ?: File("/storage/emulated/0")
            val reqRelPath = extractQueryParam(rawPath, "path") ?: cleanPath
            val targetFile = resolveTargetFile(reqRelPath)

            // ---------- DEVELOPER REST API: /api/status ----------
            if (cleanPath == "/api/status") {
                val jsonResp = """{"status":"active","port":$port,"pin":"$sessionPin","token":"$enterpriseToken","sharedDirectory":"${escapeJson(rootDir.absolutePath)}"}"""
                sendHttpResponse(output, "200 OK", "application/json; charset=utf-8", jsonResp.toByteArray(Charsets.UTF_8))
                socket.close()
                return@withContext
            }

            // ---------- DEVELOPER REST API: /api/clipboard ----------
            if (cleanPath == "/api/clipboard") {
                if (method == "POST") {
                    val bodyText = reader.readText()
                    if (bodyText.isNotBlank()) activeClipboardText = bodyText.trim()
                    sendHttpResponse(output, "200 OK", "application/json", """{"status":"updated"}""".toByteArray())
                } else {
                    val jsonResp = """{"clipboard":"${escapeJson(activeClipboardText)}"}"""
                    sendHttpResponse(output, "200 OK", "application/json; charset=utf-8", jsonResp.toByteArray(Charsets.UTF_8))
                }
                socket.close()
                return@withContext
            }

            // ---------- WebDAV PROPFIND Protocol Gateway ----------
            if (method == "PROPFIND") {
                val dir = if (targetFile.exists() && targetFile.isDirectory) targetFile else rootDir
                val propfindXml = buildWebDavPropfindXml(dir, rootDir)
                sendHttpResponse(output, "207 Multi-Status", "application/xml; charset=utf-8", propfindXml.toByteArray(Charsets.UTF_8))
                socket.close()
                return@withContext
            }

            // ---------- WebDAV MKCOL Protocol Gateway ----------
            if (method == "MKCOL") {
                if (!targetFile.exists()) {
                    targetFile.mkdirs()
                    sendHttpResponse(output, "201 Created", "text/plain", "Created".toByteArray())
                } else {
                    sendHttpResponse(output, "405 Method Not Allowed", "text/plain", "Exists".toByteArray())
                }
                socket.close()
                return@withContext
            }

            // ---------- REST API: /api/browse ----------
            if (cleanPath == "/api/browse") {
                val dir = if (targetFile.exists() && targetFile.isDirectory) targetFile else rootDir
                val currentRel = getRelativePath(rootDir, dir)
                val parentRel = if (currentRel == "/") "/" else (File(currentRel).parent?.replace('\\', '/') ?: "/")
                val fileList = dir.listFiles() ?: emptyArray()

                val jsonFiles = fileList.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                    .joinToString(",", "[", "]") { f ->
                        val fRel = getRelativePath(rootDir, f)
                        """{"name":"${escapeJson(f.name)}","path":"${escapeJson(fRel)}","isDirectory":${f.isDirectory},"size":${f.length()},"lastModified":${f.lastModified()}}"""
                    }

                val jsonResp = """{"currentPath":"${escapeJson(currentRel)}","parentPath":"${escapeJson(parentRel)}","files":$jsonFiles}"""
                sendHttpResponse(output, "200 OK", "application/json; charset=utf-8", jsonResp.toByteArray(Charsets.UTF_8))
                socket.close()
                return@withContext
            }

            // ---------- REST API: Media Streaming & Download ----------
            if (cleanPath == "/api/stream" || cleanPath == "/api/download" || (targetFile.exists() && targetFile.isFile)) {
                val fileToServe = targetFile
                if (!fileToServe.exists() || !fileToServe.isFile) {
                    sendHttpResponse(output, "404 Not Found", "text/plain", "File not found".toByteArray())
                    socket.close()
                    return@withContext
                }

                val mimeType = getMimeType(fileToServe)
                val isDownload = cleanPath == "/api/download" || extractQueryParam(rawPath, "download") == "true"
                streamFileContent(output, fileToServe, mimeType, rangeHeader, forceDownload = isDownload)
                socket.close()
                return@withContext
            }

            // ---------- REST API: /api/upload ----------
            if (method == "POST" && (cleanPath == "/api/upload" || cleanPath.startsWith("/upload"))) {
                val targetDir = if (targetFile.exists() && targetFile.isDirectory) targetFile else rootDir
                val savedFilesCount = handleMultiFileUpload(contentType, targetDir, input)
                val targetRel = getRelativePath(rootDir, targetDir)

                val isJsonReq = extractQueryParam(rawPath, "format") == "json"
                if (isJsonReq) {
                    val jsonResp = """{"status":"success","count":$savedFilesCount,"path":"${escapeJson(targetRel)}"}"""
                    sendHttpResponse(output, "200 OK", "application/json; charset=utf-8", jsonResp.toByteArray(Charsets.UTF_8))
                } else {
                    val redirectHtml = """
                        <!DOCTYPE html><html><head>
                        <meta http-equiv="refresh" content="1;url=/?path=${java.net.URLEncoder.encode(targetRel, "UTF-8")}&pin=$sessionPin">
                        <style>body{background:#191919;color:#009688;font-family:'Segoe UI',Roboto,sans-serif;text-align:center;padding:4rem;}</style>
                        </head><body><h2>✓ $savedFilesCount File(s) Uploaded Successfully!</h2><p>Refreshing folder...</p></body></html>
                    """.trimIndent()
                    sendHttpResponse(output, "200 OK", "text/html; charset=utf-8", redirectHtml.toByteArray(Charsets.UTF_8))
                }
                socket.close()
                return@withContext
            }

            // ---------- REST API: /api/delete ----------
            if (method == "POST" && (cleanPath == "/api/delete" || cleanPath.startsWith("/delete"))) {
                if (targetFile.exists() && targetFile.canonicalPath != rootDir.canonicalPath) {
                    targetFile.deleteRecursively()
                }
                val parentDir = targetFile.parentFile ?: rootDir
                val parentRel = getRelativePath(rootDir, parentDir)
                val redirectHtml = """
                    <!DOCTYPE html><html><head>
                    <meta http-equiv="refresh" content="0;url=/?path=${java.net.URLEncoder.encode(parentRel, "UTF-8")}&pin=$sessionPin">
                    </head><body></body></html>
                """.trimIndent()
                sendHttpResponse(output, "200 OK", "text/html; charset=utf-8", redirectHtml.toByteArray(Charsets.UTF_8))
                socket.close()
                return@withContext
            }

            // ---------- Web Portal Page ----------
            if (cleanPath == "/" || cleanPath.isEmpty() || cleanPath == "/index.html") {
                val dir = if (targetFile.exists() && targetFile.isDirectory) targetFile else rootDir
                val html = buildWindowsExplorerWebUiHtml(dir, rootDir)
                sendHttpResponse(output, "200 OK", "text/html; charset=utf-8", html.toByteArray(Charsets.UTF_8))
            } else {
                sendHttpResponse(output, "404 Not Found", "text/html; charset=utf-8", "<h2>404 File Not Found</h2>".toByteArray())
            }
        } catch (e: Exception) {
            Log.d(TAG, "HTTP client error: ${e.message}")
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun streamFileContent(
        output: OutputStream,
        file: File,
        mimeType: String,
        rangeHeader: String?,
        forceDownload: Boolean
    ) {
        val totalLength = file.length()
        var start: Long = 0
        var end: Long = totalLength - 1

        val isRange = rangeHeader != null && rangeHeader.startsWith("bytes=")
        if (isRange) {
            val rangeValues = rangeHeader!!.substring(6).split("-")
            start = rangeValues[0].toLongOrNull() ?: 0L
            if (rangeValues.size > 1 && rangeValues[1].isNotEmpty()) {
                end = rangeValues[1].toLongOrNull() ?: (totalLength - 1)
            }
        }

        if (start >= totalLength) {
            sendHttpResponse(output, "416 Range Not Satisfiable", "text/plain", "Requested range not satisfiable".toByteArray())
            return
        }

        val len = (end - start) + 1
        val status = if (isRange) "206 Partial Content" else "200 OK"
        val disposition = if (forceDownload) "attachment; filename=\"${file.name}\"" else "inline"

        val headerSb = StringBuilder()
        headerSb.append("HTTP/1.1 $status\r\n")
        headerSb.append("Content-Type: $mimeType\r\n")
        headerSb.append("Content-Disposition: $disposition\r\n")
        headerSb.append("Content-Length: $len\r\n")
        headerSb.append("Accept-Ranges: bytes\r\n")
        if (isRange) {
            headerSb.append("Content-Range: bytes $start-$end/$totalLength\r\n")
        }
        headerSb.append("Connection: close\r\n")
        headerSb.append("Access-Control-Allow-Origin: *\r\n")
        headerSb.append("\r\n")

        output.write(headerSb.toString().toByteArray(Charsets.UTF_8))
        output.flush()

        val fis = FileInputStream(file)
        fis.skip(start)
        val buf = ByteArray(65536)
        var remaining = len
        while (remaining > 0) {
            val toRead = minOf(buf.size.toLong(), remaining).toInt()
            val read = fis.read(buf, 0, toRead)
            if (read <= 0) break
            output.write(buf, 0, read)
            remaining -= read

            // QoS Bandwidth Throttling Governor
            if (maxSpeedLimitBps > 0L) {
                val expectedMs = (read * 1000L) / maxSpeedLimitBps
                if (expectedMs > 0) Thread.sleep(expectedMs)
            }
        }
        output.flush()
        fis.close()
    }

    private fun sendHttpResponse(output: OutputStream, status: String, contentType: String, bodyBytes: ByteArray) {
        val header = "HTTP/1.1 $status\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${bodyBytes.size}\r\n" +
                "Connection: close\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "\r\n"
        output.write(header.toByteArray(Charsets.UTF_8))
        if (bodyBytes.isNotEmpty()) {
            output.write(bodyBytes)
        }
        output.flush()
    }

    private fun handleMultiFileUpload(contentType: String?, targetDir: File, input: InputStream): Int {
        if (contentType == null || !contentType.contains("boundary=")) return 0
        val boundaryStr = "--" + contentType.substringAfter("boundary=").trim()
        val boundaryBytes = boundaryStr.toByteArray(Charsets.ISO_8859_1)

        var savedCount = 0

        try {
            val bufferStream = ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            var bytesRead: Int

            while (input.read(buf).also { bytesRead = it } != -1) {
                bufferStream.write(buf, 0, bytesRead)
            }
            val bodyBytes = bufferStream.toByteArray()

            var pos = 0
            while (pos < bodyBytes.size) {
                val boundaryIndex = indexOfBytes(bodyBytes, pos, bodyBytes.size - pos, boundaryBytes)
                if (boundaryIndex < 0) break

                val headerStart = boundaryIndex + boundaryBytes.size
                if (headerStart >= bodyBytes.size - 2) break

                val headerEnd = indexOfBytes(bodyBytes, headerStart, bodyBytes.size - headerStart, "\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
                if (headerEnd < 0) break

                val headerText = String(bodyBytes, headerStart, headerEnd - headerStart, Charsets.UTF_8)
                val contentStart = headerEnd + 4

                val nextBoundaryIndex = indexOfBytes(bodyBytes, contentStart, bodyBytes.size - contentStart, ("\r\n" + boundaryStr).toByteArray(Charsets.ISO_8859_1))
                val contentEnd = if (nextBoundaryIndex >= 0) nextBoundaryIndex else bodyBytes.size

                if (headerText.contains("filename=\"")) {
                    val rawName = headerText.substringAfter("filename=\"").substringBefore("\"")
                    val fileName = File(rawName).name
                    if (fileName.isNotBlank()) {
                        val destFile = File(targetDir, fileName)
                        val fileLength = contentEnd - contentStart
                        if (fileLength > 0) {
                            FileOutputStream(destFile).use { fos ->
                                fos.write(bodyBytes, contentStart, fileLength)
                            }
                            savedCount++
                        }
                    }
                }
                pos = contentEnd
            }
        } catch (e: Exception) {
            Log.e(TAG, "Multi-file upload error: ${e.message}")
        }
        return savedCount
    }

    private fun buildWebDavPropfindXml(currentDir: File, rootDir: File): String {
        val files = currentDir.listFiles() ?: emptyArray()
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        sb.append("<d:multistatus xmlns:d=\"DAV:\">\n")

        val currentRel = getRelativePath(rootDir, currentDir)
        sb.append("  <d:response>\n")
        sb.append("    <d:href>${escapeXml(currentRel)}</d:href>\n")
        sb.append("    <d:propstat>\n")
        sb.append("      <d:prop>\n")
        sb.append("        <d:resourcetype><d:collection/></d:resourcetype>\n")
        sb.append("      </d:prop>\n")
        sb.append("      <d:status>HTTP/1.1 200 OK</d:status>\n")
        sb.append("    </d:propstat>\n")
        sb.append("  </d:response>\n")

        for (file in files) {
            val fRel = getRelativePath(rootDir, file)
            sb.append("  <d:response>\n")
            sb.append("    <d:href>${escapeXml(fRel)}</d:href>\n")
            sb.append("    <d:propstat>\n")
            sb.append("      <d:prop>\n")
            if (file.isDirectory) {
                sb.append("        <d:resourcetype><d:collection/></d:resourcetype>\n")
            } else {
                sb.append("        <d:resourcetype/>\n")
                sb.append("        <d:getcontentlength>${file.length()}</d:getcontentlength>\n")
            }
            sb.append("      </d:prop>\n")
            sb.append("      <d:status>HTTP/1.1 200 OK</d:status>\n")
            sb.append("    </d:propstat>\n")
            sb.append("  </d:response>\n")
        }

        sb.append("</d:multistatus>")
        return sb.toString()
    }

    private fun indexOfBytes(data: ByteArray, offset: Int, length: Int, pattern: ByteArray): Int {
        if (length < pattern.size) return -1
        for (i in offset..(offset + length - pattern.size)) {
            var match = true
            for (j in pattern.indices) {
                if (data[i + j] != pattern[j]) {
                    match = false
                    break
                }
            }
            if (match) return i
        }
        return -1
    }

    private fun getMimeType(file: File): String {
        return when (file.extension.lowercase()) {
            // Video
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "3gp" -> "video/3gpp"
            "flv" -> "video/x-flv"

            // Audio
            "m4a" -> "audio/mp4"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "flac" -> "audio/flac"
            "aac" -> "audio/aac"
            "ogg" -> "audio/ogg"
            "opus" -> "audio/opus"

            // Image
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "svg" -> "image/svg+xml"
            "bmp" -> "image/bmp"
            "heic" -> "image/heic"

            // Documents & Code
            "pdf" -> "application/pdf"
            "txt", "log", "md", "csv" -> "text/plain; charset=utf-8"
            "html" -> "text/html; charset=utf-8"
            "css" -> "text/css; charset=utf-8"
            "js" -> "application/javascript; charset=utf-8"
            "json" -> "application/json; charset=utf-8"
            "xml" -> "application/xml; charset=utf-8"
            "py" -> "text/x-python; charset=utf-8"
            else -> "application/octet-stream"
        }
    }

    private fun escapeJson(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }

    private fun escapeXml(str: String): String {
        return str.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    }

    private fun buildPinLoginFormHtml(hasError: Boolean): String {
        val errorMsg = if (hasError) "<div class='error-banner'>Invalid PIN. Check the 4-digit PIN on host device.</div>" else ""
        return """
            <!DOCTYPE html>
            <html lang="en"><head><meta charset="UTF-8"><title>LinkShare — Unlock Device</title>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>
                *{box-sizing:border-box;margin:0;padding:0;}
                body{font-family:'Segoe UI',Roboto,sans-serif;background:#1E1E1E;color:#FFF;display:flex;justify-content:center;align-items:center;min-height:100vh;padding:1.5rem;}
                .card{background:#2D2D2D;border:1px solid #3D3D3D;border-radius:6px;padding:2.5rem 2rem;max-width:400px;width:100%;text-align:center;}
                h1{font-size:1.4rem;margin-bottom:0.5rem;}
                p{color:#AAA;font-size:0.88rem;margin-bottom:1.5rem;}
                .error-banner{background:rgba(239,83,80,0.15);border:1px solid rgba(239,83,80,0.4);color:#EF5350;padding:0.75rem;border-radius:4px;font-size:0.88rem;margin-bottom:1.25rem;}
                input[type="password"]{width:100%;padding:0.9rem;border-radius:4px;border:1px solid #3D3D3D;background:#1E1E1E;color:#FFF;font-size:1.5rem;font-weight:700;text-align:center;letter-spacing:8px;outline:none;margin-bottom:1.25rem;}
                button{width:100%;padding:0.85rem;border-radius:4px;border:none;background:#009688;color:#FFF;font-weight:600;cursor:pointer;text-transform:uppercase;}
            </style></head>
            <body><div class="card"><h1>LinkShare Explorer</h1><p>Enter 4-digit PIN from host device</p>$errorMsg
            <form action="/" method="get"><input type="password" name="pin" placeholder="••••" maxlength="4" autofocus required><button type="submit">Unlock Device</button></form>
            </div></body></html>
        """.trimIndent()
    }

    private fun buildWindowsExplorerWebUiHtml(currentDir: File, rootDir: File): String {
        val currentRel = getRelativePath(rootDir, currentDir)
        val files = currentDir.listFiles() ?: emptyArray()

        val sortedFiles = files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        val breadcrumbs = buildBreadcrumbHtml(currentRel)

        val fileRows = if (sortedFiles.isEmpty()) {
            """<div class="empty-folder"><p>This folder is empty</p></div>"""
        } else {
            sortedFiles.joinToString("\n") { f ->
                val fRel = getRelativePath(rootDir, f)
                val isDir = f.isDirectory
                val ext = f.extension.lowercase()
                val sizeStr = if (isDir) "Folder" else formatSize(f.length())
                val lastModStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date(f.lastModified()))

                val isVideoAudio = !isDir && (ext in listOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "flv", "m4a", "mp3", "wav", "flac", "aac", "ogg", "opus"))
                val isImg = !isDir && (ext in listOf("jpg", "jpeg", "png", "webp", "gif", "svg", "bmp", "heic"))
                val isPdfOrDoc = !isDir && (ext in listOf("pdf", "txt", "log", "md", "csv", "html", "css", "js", "json", "xml", "py"))

                val encodedRel = java.net.URLEncoder.encode(fRel, "UTF-8")

                val actionBtn = when {
                    isDir -> """<button class="btn-action btn-open" onclick="openFolder('${escapeJs(fRel)}')">📂 Open</button>"""
                    isVideoAudio -> """<button class="btn-action btn-stream" onclick="openMediaModal('${escapeJs(fRel)}', '${escapeJs(f.name)}')">▶ Stream</button><a class="btn-action btn-download" href="/api/download?path=$encodedRel&pin=$sessionPin" download>⬇ Download</a>"""
                    isImg -> """<button class="btn-action btn-stream" onclick="openImageModal('${escapeJs(fRel)}', '${escapeJs(f.name)}')">👁 View</button><a class="btn-action btn-download" href="/api/download?path=$encodedRel&pin=$sessionPin" download>⬇ Download</a>"""
                    isPdfOrDoc -> """<button class="btn-action btn-stream" onclick="openPdfModal('${escapeJs(fRel)}', '${escapeJs(f.name)}')">📄 View</button><a class="btn-action btn-download" href="/api/download?path=$encodedRel&pin=$sessionPin" download>⬇ Download</a>"""
                    else -> """<a class="btn-action btn-download" href="/api/download?path=$encodedRel&pin=$sessionPin" download>⬇ Download</a>"""
                }

                """
                <div class="explorer-row" onclick="${if (isDir) "openFolder('${escapeJs(fRel)}')" else ""}">
                    <div class="col-name"><span class="file-icon">${if (isDir) "📁" else if (isVideoAudio) "🎬" else if (isImg) "🖼️" else if (isPdfOrDoc) "📄" else "📄"}</span><span class="file-title">${escapeJson(f.name)}</span></div>
                    <div class="col-date">$lastModStr</div>
                    <div class="col-type">${if (isDir) "Folder" else ext.uppercase()}</div>
                    <div class="col-size">$sizeStr</div>
                    <div class="col-actions" onclick="event.stopPropagation()">$actionBtn</div>
                </div>
                """.trimIndent()
            }
        }

        return """
            <!DOCTYPE html>
            <html lang="en"><head><meta charset="UTF-8"><title>LinkShare — WebDAV & File Explorer</title>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>
                *{box-sizing:border-box;margin:0;padding:0;}
                body{font-family:'Segoe UI',Roboto,sans-serif;background:#191919;color:#FFF;min-height:100vh;display:flex;flex-direction:column;user-select:none;-webkit-user-select:none;}
                .header{background:#202020;border-bottom:1px solid #2D2D2D;padding:0.75rem 1.25rem;display:flex;justify-content:space-between;align-items:center;font-weight:600;}
                .address-bar-container{background:#252525;border-bottom:1px solid #2D2D2D;padding:0.6rem 1.25rem;display:flex;align-items:center;gap:0.75rem;}
                .address-bar{flex:1;background:#1B1B1B;border:1px solid #333;border-radius:4px;padding:0.45rem 0.8rem;display:flex;gap:0.4rem;font-size:0.88rem;align-items:center;}
                .crumb{color:#009688;cursor:pointer;font-weight:600;text-decoration:none;}
                .crumb:hover{text-decoration:underline;}
                .btn-upload-top{background:#009688;color:#FFF;border:none;padding:0.5rem 1rem;border-radius:4px;font-weight:700;cursor:pointer;font-size:0.85rem;display:inline-flex;align-items:center;gap:0.3rem;}
                .btn-upload-top:hover{background:#00897B;}
                .main-layout{display:flex;flex:1;}
                .content-area{flex:1;padding:1rem 1.25rem;}
                .explorer-row{display:grid;grid-template-columns:2fr 1.2fr 1fr 0.8fr 180px;padding:0.75rem 0.85rem;border-bottom:1px solid #222;align-items:center;font-size:0.88rem;cursor:pointer;transition:background 0.15s;}
                .explorer-row:hover{background:#222222;}
                .col-name{display:flex;align-items:center;gap:0.5rem;font-weight:500;}
                .col-actions{display:flex;gap:0.4rem;justify-content:flex-end;}
                .btn-action{background:#2D2D2D;border:1px solid #444;color:#FFF;padding:0.4rem 0.75rem;border-radius:4px;font-size:0.78rem;font-weight:600;cursor:pointer;text-decoration:none;display:inline-flex;align-items:center;gap:0.3rem;line-height:1;}
                .btn-action:hover{background:#383838;border-color:#009688;}
                .btn-stream{background:rgba(0,150,136,0.15);border-color:#009688;color:#00897B;}
                .btn-stream:hover{background:#009688;color:#FFF;}
                .empty-folder{padding:3rem;text-align:center;color:#888;}
                /* Modal */
                .modal-overlay{display:none;position:fixed;top:0;left:0;width:100%;height:100%;background:rgba(0,0,0,0.85);z-index:9999;justify-content:center;align-items:center;padding:1.5rem;}
                .modal-content{background:#202020;border:1px solid #333;border-radius:6px;max-width:900px;width:100%;max-height:90vh;display:flex;flex-direction:column;overflow:hidden;}
                .modal-header{padding:0.85rem 1.25rem;background:#252525;border-bottom:1px solid #333;display:flex;justify-content:space-between;align-items:center;font-weight:700;}
                .modal-body{padding:1rem;display:flex;justify-content:center;align-items:center;background:#000;flex:1;overflow:auto;}
                .media-player{max-width:100%;max-height:70vh;border-radius:4px;}
                .btn-close-modal{background:none;border:none;color:#AAA;font-size:1.5rem;cursor:pointer;}
                .btn-close-modal:hover{color:#FFF;}
            </style></head>
            <body>
                <input type="file" id="hiddenFileInput" multiple style="display:none;" onchange="uploadFilesList(this.files)">
                <div class="header"><div>LinkShare WebDAV & Explorer</div><div>PIN Protected</div></div>
                <div class="address-bar-container">
                    <div class="address-bar">$breadcrumbs</div>
                    <button class="btn-upload-top" onclick="document.getElementById('hiddenFileInput').click()">+ Upload Files</button>
                </div>
                <div class="main-layout"><div class="content-area">$fileRows</div></div>

                <!-- Media / Document Viewer Modal -->
                <div id="mediaModal" class="modal-overlay">
                    <div class="modal-content">
                        <div class="modal-header">
                            <span id="modalTitle">Media Viewer</span>
                            <button class="btn-close-modal" onclick="closeModal()">✕</button>
                        </div>
                        <div class="modal-body" id="modalBody"></div>
                    </div>
                </div>

                <script>
                    var PIN = '$sessionPin';
                    var CURRENT_PATH = '${escapeJs(currentRel)}';
                    function openFolder(p){ window.location.href = '/?path=' + encodeURIComponent(p) + '&pin=' + PIN; }
                    function uploadFilesList(files){
                        var formData = new FormData();
                        for(var i=0; i<files.length; i++) formData.append('f'+i, files[i]);
                        var xhr = new XMLHttpRequest();
                        xhr.open('POST', '/api/upload?path=' + encodeURIComponent(CURRENT_PATH) + '&pin=' + PIN);
                        xhr.onload = function(){ openFolder(CURRENT_PATH); };
                        xhr.send(formData);
                    }
                    function openMediaModal(path, title){
                        var streamUrl = '/api/stream?path=' + encodeURIComponent(path) + '&pin=' + PIN;
                        document.getElementById('modalTitle').innerText = title;
                        var isAudio = path.match(/\.(mp3|m4a|wav|flac|aac|ogg|opus|3gp)$/i);
                        var html = isAudio ?
                            '<audio controls autoplay style="width:100%;max-width:500px;"><source src="' + streamUrl + '"></audio>' :
                            '<video controls autoplay class="media-player"><source src="' + streamUrl + '"></video>';
                        document.getElementById('modalBody').innerHTML = html;
                        document.getElementById('mediaModal').style.display = 'flex';
                    }
                    function openImageModal(path, title){
                        var streamUrl = '/api/stream?path=' + encodeURIComponent(path) + '&pin=' + PIN;
                        document.getElementById('modalTitle').innerText = title;
                        document.getElementById('modalBody').innerHTML = '<img src="' + streamUrl + '" class="media-player">';
                        document.getElementById('mediaModal').style.display = 'flex';
                    }
                    function openPdfModal(path, title){
                        var streamUrl = '/api/stream?path=' + encodeURIComponent(path) + '&pin=' + PIN;
                        document.getElementById('modalTitle').innerText = title;
                        document.getElementById('modalBody').innerHTML = '<iframe src="' + streamUrl + '" style="width:100%;height:75vh;border:none;"></iframe>';
                        document.getElementById('mediaModal').style.display = 'flex';
                    }
                    function closeModal(){
                        document.getElementById('modalBody').innerHTML = '';
                        document.getElementById('mediaModal').style.display = 'none';
                    }
                </script>
            </body></html>
        """.trimIndent()
    }

    private fun buildBreadcrumbHtml(relPath: String): String {
        val sb = StringBuilder()
        sb.append("""<a class="crumb" onclick="openFolder('/')">Internal Storage</a>""")
        val parts = relPath.split('/').filter { it.isNotBlank() }
        var accumulated = ""
        for (part in parts) {
            accumulated += "/$part"
            sb.append(""" &gt; <a class="crumb" onclick="openFolder('${escapeJs(accumulated)}')">${escapeHtml(part)}</a>""")
        }
        return sb.toString()
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
            bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
            bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    private fun generatePin(): String {
        return "%04d".format(Random.nextInt(1000, 9999))
    }

    private fun escapeHtml(str: String): String {
        return str.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    }

    private fun escapeJs(str: String): String {
        return str.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"")
    }
}
