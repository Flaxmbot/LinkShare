package app.linkshare.platform

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
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

actual class PlatformHttpServer actual constructor(private val port: Int) {
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    actual var sessionPin: String = generatePin()
        private set
    actual var enterpriseToken: String = "token_${generatePin()}${generatePin()}"
        private set
    actual var activeClipboardText: String = ""

    private var sharedDirectory: File? = null
    private var lastActivityTime: Long = System.currentTimeMillis()
    private var idleTimeoutMs: Long = 15 * 60 * 1000L
    private var maxSpeedLimitBps: Long = 0L

    actual fun startServer(shareDir: String, customPin: String?, timeoutMinutes: Int, maxSpeedMbps: Int) {
        if (isRunning) stopServer()
        val dir = File(shareDir)
        sharedDirectory = dir
        if (!dir.exists()) dir.mkdirs()
        sessionPin = customPin ?: generatePin()
        idleTimeoutMs = if (timeoutMinutes <= 0) Long.MAX_VALUE else timeoutMinutes * 60 * 1000L
        maxSpeedLimitBps = if (maxSpeedMbps <= 0) 0L else (maxSpeedMbps.toLong() * 1024 * 1024 / 8)
        lastActivityTime = System.currentTimeMillis()
        isRunning = true

        scope.launch {
            try {
                serverSocket = ServerSocket(port, 50, InetAddress.getByName("0.0.0.0"))
                launch {
                    while (isRunning) {
                        kotlinx.coroutines.delay(10000)
                        if (idleTimeoutMs != Long.MAX_VALUE && (System.currentTimeMillis() - lastActivityTime) > idleTimeoutMs) {
                            stopServer()
                            break
                        }
                    }
                }
                while (isRunning) {
                    val socket = serverSocket?.accept() ?: break
                    lastActivityTime = System.currentTimeMillis()
                    launch { handleHttpRequest(socket) }
                }
            } catch (e: Exception) {
                if (isRunning) Log.d("HttpServer", "Server error: ${e.message}")
            } finally {
                stopServer()
            }
        }
    }

    actual fun stopServer() {
        isRunning = false
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
    }

    actual fun isServerActive(): Boolean = isRunning && serverSocket?.isClosed == false

    actual fun generateConnectionString(ipAddress: String): String =
        "http://$ipAddress:$port?pin=$sessionPin"

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
            var authHeader: String? = null

            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line.isNullOrEmpty()) break
                val lower = line!!.lowercase()
                when {
                    lower.startsWith("range:") -> rangeHeader = line!!.substring(6).trim()
                    lower.startsWith("content-type:") -> contentType = line!!.substring(13).trim()
                    lower.startsWith("authorization:") -> authHeader = line!!.substring(14).trim()
                }
            }

            val cleanPath = rawPath.substringBefore("?")
            if (cleanPath == "/favicon.ico") {
                sendResponse(output, "204 No Content", "text/plain", ByteArray(0))
                socket.close(); return@withContext
            }
            if (method == "OPTIONS") {
                val h = "HTTP/1.1 200 OK\r\nDAV: 1, 2\r\nMS-Author-Via: DAV\r\n" +
                    "Allow: OPTIONS, GET, HEAD, POST, PUT, DELETE, PROPFIND, MKCOL, COPY, MOVE\r\n" +
                    "Content-Length: 0\r\nConnection: close\r\n\r\n"
                output.write(h.toByteArray()); output.flush(); socket.close(); return@withContext
            }

            val submittedPin = extractParam(rawPath, "pin")
            val isBearerValid = authHeader?.startsWith("Bearer ") == true && authHeader.substring(7).trim() == enterpriseToken
            val hasValidPin = !true || (submittedPin != null && submittedPin == sessionPin) || isBearerValid

            if (!hasValidPin) {
                val html = buildLoginPage(submittedPin != null && submittedPin != sessionPin)
                sendResponse(output, "200 OK", "text/html; charset=utf-8", html.toByteArray(Charsets.UTF_8))
                socket.close(); return@withContext
            }

            val rootDir = sharedDirectory ?: File(System.getProperty("user.home") ?: "/")
            val reqRelPath = extractParam(rawPath, "path") ?: cleanPath
            val targetFile = resolveFile(reqRelPath, rootDir)

            when {
                cleanPath == "/api/status" -> {
                    val json = """{"status":"active","port":$port,"pin":"$sessionPin","token":"$enterpriseToken","dir":"${esc(rootDir.absolutePath)}"}"""
                    sendResponse(output, "200 OK", "application/json; charset=utf-8", json.toByteArray())
                }
                cleanPath == "/api/clipboard" -> {
                    if (method == "POST") {
                        val body = reader.readText()
                        if (body.isNotBlank()) activeClipboardText = body.trim()
                        sendResponse(output, "200 OK", "application/json", """{"status":"updated"}""".toByteArray())
                    } else {
                        sendResponse(output, "200 OK", "application/json; charset=utf-8",
                            """{"clipboard":"${esc(activeClipboardText)}"}""".toByteArray())
                    }
                }
                method == "PROPFIND" -> {
                    val dir = if (targetFile.exists() && targetFile.isDirectory) targetFile else rootDir
                    sendResponse(output, "207 Multi-Status", "application/xml; charset=utf-8",
                        buildPropfind(dir, rootDir).toByteArray())
                }
                method == "MKCOL" -> {
                    if (!targetFile.exists()) { targetFile.mkdirs(); sendResponse(output, "201 Created", "text/plain", "Created".toByteArray()) }
                    else sendResponse(output, "405 Method Not Allowed", "text/plain", "Exists".toByteArray())
                }
                cleanPath == "/api/browse" -> {
                    val dir = if (targetFile.exists() && targetFile.isDirectory) targetFile else rootDir
                    val rel = relPath(rootDir, dir)
                    val parentRel = if (rel == "/") "/" else (File(rel).parent?.replace('\\', '/') ?: "/")
                    val files = dir.listFiles() ?: emptyArray()
                    val jsonFiles = files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                        .joinToString(",", "[", "]") { f ->
                            val fRel = relPath(rootDir, f)
                            """{"name":"${esc(f.name)}","path":"${esc(fRel)}","isDirectory":${f.isDirectory},"size":${f.length()},"lastModified":${f.lastModified()}}"""
                        }
                    sendResponse(output, "200 OK", "application/json; charset=utf-8",
                        """{"currentPath":"${esc(rel)}","parentPath":"${esc(parentRel)}","files":$jsonFiles}""".toByteArray())
                }
                cleanPath == "/api/stream" || cleanPath == "/api/download" || (targetFile.exists() && targetFile.isFile) -> {
                    if (!targetFile.exists() || !targetFile.isFile) {
                        sendResponse(output, "404 Not Found", "text/plain", "File not found".toByteArray())
                    } else {
                        val mime = getMime(targetFile)
                        val isDl = cleanPath == "/api/download" || extractParam(rawPath, "download") == "true"
                        streamFile(output, targetFile, mime, rangeHeader, isDl)
                    }
                }
                method == "POST" && (cleanPath == "/api/upload" || cleanPath.startsWith("/upload")) -> {
                    val dir = if (targetFile.exists() && targetFile.isDirectory) targetFile else rootDir
                    val count = handleUpload(contentType, dir, input)
                    val targetRel = relPath(rootDir, dir)
                    sendResponse(output, "200 OK", "application/json; charset=utf-8",
                        """{"status":"success","count":$count,"path":"${esc(targetRel)}"}""".toByteArray())
                }
                method == "POST" && (cleanPath == "/api/delete" || cleanPath.startsWith("/delete")) -> {
                    if (targetFile.exists() && targetFile.canonicalPath != rootDir.canonicalPath) targetFile.deleteRecursively()
                    sendResponse(output, "200 OK", "application/json", """{"status":"deleted"}""".toByteArray())
                }
                cleanPath == "/" || cleanPath.isEmpty() || cleanPath == "/index.html" -> {
                    val dir = if (targetFile.exists() && targetFile.isDirectory) targetFile else rootDir
                    sendResponse(output, "200 OK", "text/html; charset=utf-8", buildExplorerUI(dir, rootDir).toByteArray())
                }
                else -> sendResponse(output, "404 Not Found", "text/html", "<h2>404 Not Found</h2>".toByteArray())
            }
        } catch (_: Exception) {} finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun streamFile(output: OutputStream, file: File, mimeType: String, rangeHeader: String?, forceDownload: Boolean) {
        val total = file.length()
        var start = 0L; var end = total - 1
        val isRange = rangeHeader?.startsWith("bytes=") == true
        if (isRange) {
            val rv = rangeHeader!!.substring(6).split("-")
            start = rv[0].toLongOrNull() ?: 0L
            if (rv.size > 1 && rv[1].isNotEmpty()) end = rv[1].toLongOrNull() ?: (total - 1)
        }
        if (start >= total) { sendResponse(output, "416 Range Not Satisfiable", "text/plain", "Range error".toByteArray()); return }
        val len = (end - start) + 1
        val status = if (isRange) "206 Partial Content" else "200 OK"
        val disposition = if (forceDownload) "attachment; filename=\"${file.name}\"" else "inline"
        val sb = StringBuilder()
        sb.append("HTTP/1.1 $status\r\nContent-Type: $mimeType\r\nContent-Disposition: $disposition\r\n")
        sb.append("Content-Length: $len\r\nAccept-Ranges: bytes\r\n")
        if (isRange) sb.append("Content-Range: bytes $start-$end/$total\r\n")
        sb.append("Connection: close\r\nAccess-Control-Allow-Origin: *\r\n\r\n")
        output.write(sb.toString().toByteArray()); output.flush()
        val fis = FileInputStream(file); fis.skip(start)
        val buf = ByteArray(65536); var remaining = len
        while (remaining > 0) {
            val toRead = minOf(buf.size.toLong(), remaining).toInt()
            val read = fis.read(buf, 0, toRead); if (read <= 0) break
            output.write(buf, 0, read); remaining -= read
            if (maxSpeedLimitBps > 0L) { val ms = (read * 1000L) / maxSpeedLimitBps; if (ms > 0) Thread.sleep(ms) }
        }
        output.flush(); fis.close()
    }

    private fun sendResponse(output: OutputStream, status: String, ct: String, body: ByteArray) {
        val h = "HTTP/1.1 $status\r\nContent-Type: $ct\r\nContent-Length: ${body.size}\r\nConnection: close\r\nAccess-Control-Allow-Origin: *\r\n\r\n"
        output.write(h.toByteArray()); if (body.isNotEmpty()) output.write(body); output.flush()
    }

    private fun handleUpload(contentType: String?, targetDir: File, input: InputStream): Int {
        if (contentType == null || !contentType.contains("boundary=")) return 0
        val boundary = "--" + contentType.substringAfter("boundary=").trim()
        val boundaryBytes = boundary.toByteArray(Charsets.ISO_8859_1)
        var count = 0
        try {
            val buf = ByteArray(64 * 1024); val bos = ByteArrayOutputStream()
            var r: Int; while (input.read(buf).also { r = it } != -1) bos.write(buf, 0, r)
            val body = bos.toByteArray(); var pos = 0
            while (pos < body.size) {
                val bi = indexOf(body, pos, body.size - pos, boundaryBytes); if (bi < 0) break
                val hs = bi + boundaryBytes.size; if (hs >= body.size - 2) break
                val he = indexOf(body, hs, body.size - hs, "\r\n\r\n".toByteArray(Charsets.ISO_8859_1)); if (he < 0) break
                val header = String(body, hs, he - hs, Charsets.UTF_8); val cs = he + 4
                val nbi = indexOf(body, cs, body.size - cs, ("\r\n" + boundary).toByteArray(Charsets.ISO_8859_1))
                val ce = if (nbi >= 0) nbi else body.size
                if (header.contains("filename=\"")) {
                    val rawName = header.substringAfter("filename=\"").substringBefore("\"")
                    val name = File(rawName).name
                    if (name.isNotBlank() && ce - cs > 0) {
                        FileOutputStream(File(targetDir, name)).use { it.write(body, cs, ce - cs) }; count++
                    }
                }
                pos = ce
            }
        } catch (_: Exception) {}
        return count
    }

    private fun indexOf(data: ByteArray, offset: Int, length: Int, pattern: ByteArray): Int {
        if (length < pattern.size) return -1
        for (i in offset..(offset + length - pattern.size)) {
            var match = true
            for (j in pattern.indices) { if (data[i + j] != pattern[j]) { match = false; break } }
            if (match) return i
        }
        return -1
    }

    private fun extractParam(url: String, name: String): String? {
        if (!url.contains("?")) return null
        for (pair in url.substringAfter("?").split("&")) {
            val kv = pair.split("=", limit = 2)
            if (kv.size == 2 && kv[0].equals(name, ignoreCase = true))
                return try { URLDecoder.decode(kv[1], "UTF-8").trim() } catch (_: Exception) { kv[1].trim() }
        }
        return null
    }

    private fun resolveFile(path: String?, root: File): File {
        if (path.isNullOrBlank() || path == "/" || path == ".") return root
        val candidate = File(root, path.trimStart('/'))
        return if (candidate.canonicalPath.startsWith(root.canonicalPath)) candidate else root
    }

    private fun relPath(root: File, target: File): String {
        return try {
            val r = root.canonicalPath; val t = target.canonicalPath
            if (t == r) "/" else t.substring(r.length).replace('\\', '/')
        } catch (_: Exception) { "/" }
    }

    private fun buildPropfind(dir: File, root: File): String {
        val sb = StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<d:multistatus xmlns:d=\"DAV:\">\n")
        fun addEntry(f: File, isDir: Boolean) {
            val rel = relPath(root, f)
            sb.append("  <d:response><d:href>${escXml(rel)}</d:href><d:propstat><d:prop>")
            if (isDir) sb.append("<d:resourcetype><d:collection/></d:resourcetype>")
            else { sb.append("<d:resourcetype/>"); sb.append("<d:getcontentlength>${f.length()}</d:getcontentlength>") }
            sb.append("</d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>\n")
        }
        addEntry(dir, true)
        dir.listFiles()?.forEach { addEntry(it, it.isDirectory) }
        sb.append("</d:multistatus>")
        return sb.toString()
    }

    private fun getMime(file: File): String = when (file.extension.lowercase()) {
        "mp4" -> "video/mp4"; "mkv" -> "video/x-matroska"; "webm" -> "video/webm"
        "avi" -> "video/x-msvideo"; "mov" -> "video/quicktime"; "3gp" -> "video/3gpp"; "flv" -> "video/x-flv"
        "m4v" -> "video/mp4"; "ts" -> "video/mp2t"; "wmv" -> "video/x-ms-wmv"
        "m4a" -> "audio/mp4"; "mp3" -> "audio/mpeg"; "wav" -> "audio/wav"; "flac" -> "audio/flac"
        "aac" -> "audio/aac"; "ogg" -> "audio/ogg"; "opus" -> "audio/opus"; "wma" -> "audio/x-ms-wma"
        "mid", "midi" -> "audio/midi"; "amr" -> "audio/amr"
        "jpg", "jpeg" -> "image/jpeg"; "png" -> "image/png"; "webp" -> "image/webp"
        "gif" -> "image/gif"; "svg" -> "image/svg+xml"; "bmp" -> "image/bmp"
        "heic" -> "image/heic"; "ico" -> "image/x-icon"; "tiff", "tif" -> "image/tiff"
        "pdf" -> "application/pdf"
        "doc" -> "application/msword"; "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xls" -> "application/vnd.ms-excel"; "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "ppt" -> "application/vnd.ms-powerpoint"; "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "zip" -> "application/zip"; "rar" -> "application/x-rar-compressed"; "7z" -> "application/x-7z-compressed"
        "tar" -> "application/x-tar"; "gz" -> "application/gzip"
        "apk" -> "application/vnd.android.package-archive"
        "txt", "log", "md", "csv" -> "text/plain; charset=utf-8"
        "html", "htm" -> "text/html; charset=utf-8"; "css" -> "text/css; charset=utf-8"
        "js" -> "application/javascript; charset=utf-8"; "json" -> "application/json; charset=utf-8"
        "xml" -> "application/xml; charset=utf-8"
        "py" -> "text/x-python; charset=utf-8"; "java", "kt" -> "text/plain; charset=utf-8"
        "c", "cpp", "h" -> "text/plain; charset=utf-8"
        "sh", "bat", "ps1" -> "text/plain; charset=utf-8"
        "yaml", "yml" -> "text/yaml; charset=utf-8"; "toml" -> "text/plain; charset=utf-8"
        "sql" -> "text/plain; charset=utf-8"
        else -> "application/octet-stream"
    }

    private fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
    private fun escXml(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    private fun escHtml(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun escJs(s: String) = s.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"")
    private fun generatePin(): String = "%04d".format(Random.nextInt(1000, 9999))

    private fun fmtSize(bytes: Long): String = when {
        bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }

    private fun buildLoginPage(hasError: Boolean): String {
        val err = if (hasError) "<div class='err'>Invalid PIN. Check the 4-digit PIN on host device.</div>" else ""
        return """<!DOCTYPE html><html lang="en"><head><meta charset="UTF-8"><title>LinkShare</title>
<meta name="viewport" content="width=device-width,initial-scale=1">
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:'Segoe UI Variable','Segoe UI',system-ui,-apple-system,sans-serif;background:#202020;color:#fff;display:flex;justify-content:center;align-items:center;min-height:100vh;padding:1rem}
.card{background:#2d2d2d;border:1px solid #404040;border-radius:8px;padding:2.5rem 2rem;max-width:380px;width:100%;text-align:center}
h1{font-size:1.25rem;font-weight:600;margin-bottom:.4rem}
p{color:#999;font-size:.85rem;margin-bottom:1.5rem}
.err{background:rgba(255,69,58,.12);border:1px solid rgba(255,69,58,.3);color:#ff453a;padding:.7rem;border-radius:6px;font-size:.83rem;margin-bottom:1rem}
input[type=password]{width:100%;padding:.85rem;border-radius:6px;border:1px solid #404040;background:#1a1a1a;color:#fff;font-size:1.8rem;font-weight:700;text-align:center;letter-spacing:12px;outline:none;margin-bottom:1rem;transition:border-color .2s}
input[type=password]:focus{border-color:#0078d4}
button{width:100%;padding:.8rem;border-radius:6px;border:none;background:#0078d4;color:#fff;font-weight:600;cursor:pointer;font-size:.9rem;transition:background .15s}
button:hover{background:#1a86d9}
</style></head><body><div class="card"><h1>LinkShare</h1><p>Enter the 4-digit PIN shown on the host device</p>$err
<form action="/" method="get"><input type="password" name="pin" placeholder="····" maxlength="4" autofocus required><button type="submit">Connect</button></form></div></body></html>"""
    }

    private fun buildExplorerUI(currentDir: File, rootDir: File): String {
        val rel = relPath(rootDir, currentDir)
        val files = currentDir.listFiles() ?: emptyArray()
        val sorted = files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        val sdf = SimpleDateFormat("M/d/yyyy h:mm a", Locale.US)

        val rows = sorted.joinToString("\n") { f ->
            val fRel = relPath(rootDir, f)
            val enc = URLEncoder.encode(fRel, "UTF-8")
            val ext = f.extension.lowercase()
            val isDir = f.isDirectory
            val size = if (isDir) "" else fmtSize(f.length())
            val date = sdf.format(Date(f.lastModified()))
            val type = if (isDir) "File folder" else when(ext) {
                "mp4","mkv","webm","avi","mov","3gp","flv","m4v","ts","wmv" -> "Video file"
                "m4a","mp3","wav","flac","aac","ogg","opus","wma" -> "Audio file"
                "jpg","jpeg","png","webp","gif","svg","bmp","heic","ico","tiff","tif" -> "Image file"
                "pdf" -> "PDF document"
                "doc","docx" -> "Word document"; "xls","xlsx" -> "Excel spreadsheet"
                "ppt","pptx" -> "PowerPoint presentation"
                "zip","rar","7z","tar","gz" -> "Archive"
                "apk" -> "Android package"; "txt","log","md" -> "Text file"
                "html","htm" -> "HTML file"; "css" -> "CSS file"; "js" -> "JavaScript file"
                "json" -> "JSON file"; "xml" -> "XML file"; "csv" -> "CSV file"
                "py" -> "Python file"; "java","kt" -> "Source file"
                else -> "${ext.uppercase()} file"
            }
            val icon = if (isDir) """<svg width="16" height="16" viewBox="0 0 24 24" fill="#FFB300"><path d="M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z"/></svg>""" else when(ext) {
                "mp4","mkv","webm","avi","mov","3gp","flv","m4v" -> """<svg width="16" height="16" viewBox="0 0 24 24" fill="#AB47BC"><path d="M18 4l2 4h-3l-2-4h-2l2 4h-3l-2-4H8l2 4H7L5 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V4h-4z"/></svg>"""
                "m4a","mp3","wav","flac","aac","ogg","opus" -> """<svg width="16" height="16" viewBox="0 0 24 24" fill="#FFB300"><path d="M12 3v10.55c-.59-.34-1.27-.55-2-.55-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4V7h4V3h-6z"/></svg>"""
                "jpg","jpeg","png","webp","gif","svg","bmp","heic" -> """<svg width="16" height="16" viewBox="0 0 24 24" fill="#4CAF50"><path d="M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2zM8.5 13.5l2.5 3.01L14.5 12l4.5 6H5l3.5-4.5z"/></svg>"""
                "pdf" -> """<svg width="16" height="16" viewBox="0 0 24 24" fill="#EF5350"><path d="M20 2H8c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm-8.5 7.5c0 .83-.67 1.5-1.5 1.5H9v2H7.5V7H10c.83 0 1.5.67 1.5 1.5v1z"/></svg>"""
                "zip","rar","7z","tar","gz" -> """<svg width="16" height="16" viewBox="0 0 24 24" fill="#FFB300"><path d="M20 6h-8l-2-2H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2zm-6 10h-2v-2h2v2zm0-4h-2v-2h2v2z"/></svg>"""
                "apk" -> """<svg width="16" height="16" viewBox="0 0 24 24" fill="#009688"><path d="M17 1.01L7 1c-1.1 0-2 .9-2 2v18c0 1.1.9 2 2 2h10c1.1 0 2-.9 2-2V3c0-1.1-.9-1.99-2-1.99zM17 19H7V5h10v14z"/></svg>"""
                else -> """<svg width="16" height="16" viewBox="0 0 24 24" fill="#4DB6AC"><path d="M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm2 16H8v-2h8v2zm0-4H8v-2h8v2zm-3-5V3.5L18.5 9H13z"/></svg>"""
            }
            val isMedia = !isDir && ext in listOf("mp4","mkv","webm","avi","mov","3gp","flv","m4v","ts","wmv","m4a","mp3","wav","flac","aac","ogg","opus","wma","amr","mid","midi")
            val isImg = !isDir && ext in listOf("jpg","jpeg","png","webp","gif","svg","bmp","heic","ico","tiff","tif")
            val isDoc = !isDir && ext in listOf("pdf","txt","log","md","csv","html","htm","css","js","json","xml","py","java","kt","c","cpp","h","sh","yaml","yml","sql")

            """<tr class="row" data-path="${escHtml(fRel)}" data-name="${escHtml(f.name)}" data-isdir="$isDir" data-media="$isMedia" data-img="$isImg" data-doc="$isDoc" data-enc="$enc" ondblclick="${if(isDir) "nav('${escJs(fRel)}')" else if(isMedia) "playMedia('$enc','${escJs(f.name)}')" else if(isImg) "viewImg('$enc','${escJs(f.name)}')" else if(isDoc) "viewDoc('$enc','${escJs(f.name)}')" else "dl('$enc')"}">
<td class="c-icon">$icon</td><td class="c-name">${escHtml(f.name)}</td>
<td class="c-date">$date</td><td class="c-type">$type</td><td class="c-size">$size</td></tr>"""
        }

        val breadcrumbs = buildBreadcrumbs(rel)

        return """<!DOCTYPE html><html lang="en"><head><meta charset="UTF-8"><title>LinkShare — ${escHtml(rel)}</title>
<meta name="viewport" content="width=device-width,initial-scale=1">
<link rel="preconnect" href="https://fonts.googleapis.com">
<link href="https://fonts.googleapis.com/css2?family=Segoe+UI+Variable:wght@400;500;600&display=swap" rel="stylesheet">
<style>
:root{--bg:#1e1e1e;--surface:#282828;--surface2:#323232;--border:#3a3a3a;--text:#e4e4e4;--text2:#999;--accent:#0078d4;--accent-hover:#1a86d9;--hover:#2a2d2e;--selected:#264f78;--danger:#ff453a;--success:#30d158}
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:'Segoe UI Variable','Segoe UI',system-ui,-apple-system,sans-serif;background:var(--bg);color:var(--text);min-height:100vh;display:flex;flex-direction:column;font-size:13px;-webkit-font-smoothing:antialiased;user-select:none;-webkit-user-select:none}
/* Toolbar */
.toolbar{background:var(--surface);border-bottom:1px solid var(--border);padding:6px 12px;display:flex;align-items:center;gap:4px;flex-wrap:wrap}
.tb-btn{background:none;border:1px solid transparent;color:var(--text2);padding:5px 10px;border-radius:4px;cursor:pointer;font-size:12px;display:inline-flex;align-items:center;gap:5px;transition:all .15s}
.tb-btn:hover{background:var(--hover);color:var(--text);border-color:var(--border)}
.tb-sep{width:1px;height:20px;background:var(--border);margin:0 4px}
/* Address bar */
.addr-bar{background:var(--surface);border-bottom:1px solid var(--border);padding:5px 12px;display:flex;align-items:center;gap:8px}
.addr-path{flex:1;background:var(--bg);border:1px solid var(--border);border-radius:4px;padding:5px 10px;display:flex;align-items:center;gap:2px;overflow-x:auto;white-space:nowrap}
.crumb{color:var(--accent);cursor:pointer;font-size:12px;padding:2px 4px;border-radius:3px;text-decoration:none}
.crumb:hover{background:var(--hover);text-decoration:underline}
.crumb-sep{color:var(--text2);font-size:11px;margin:0 1px}
.addr-search{background:var(--bg);border:1px solid var(--border);border-radius:4px;padding:5px 10px;color:var(--text);font-size:12px;width:200px;outline:none}
.addr-search:focus{border-color:var(--accent)}
/* Main table */
.content{flex:1;overflow:auto;padding:0}
table{width:100%;border-collapse:collapse}
thead th{position:sticky;top:0;background:var(--surface);border-bottom:1px solid var(--border);padding:6px 12px;text-align:left;font-weight:500;color:var(--text2);font-size:12px;cursor:pointer;white-space:nowrap}
thead th:hover{color:var(--text)}
.row{cursor:default;transition:background .1s}
.row:hover{background:var(--hover)}
.row.selected{background:var(--selected)}
.row td{padding:5px 12px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;border-bottom:1px solid #252525}
.c-icon{width:32px;text-align:center;font-size:16px}
.c-name{min-width:200px;font-weight:400}
.c-date{width:160px;color:var(--text2)}
.c-type{width:140px;color:var(--text2)}
.c-size{width:90px;color:var(--text2);text-align:right}
/* Status bar */
.statusbar{background:var(--surface);border-top:1px solid var(--border);padding:4px 12px;font-size:11px;color:var(--text2);display:flex;justify-content:space-between}
/* Context menu */
.ctx-menu{position:fixed;background:var(--surface2);border:1px solid var(--border);border-radius:8px;padding:4px 0;min-width:200px;z-index:10000;box-shadow:0 8px 32px rgba(0,0,0,.5);display:none}
.ctx-item{padding:7px 16px;cursor:pointer;display:flex;align-items:center;gap:10px;font-size:12.5px;color:var(--text)}
.ctx-item:hover{background:var(--hover)}
.ctx-item.danger{color:var(--danger)}
.ctx-sep{height:1px;background:var(--border);margin:4px 8px}
/* Modal overlay */
.modal{position:fixed;top:0;left:0;width:100%;height:100%;background:rgba(0,0,0,.8);z-index:9999;display:none;justify-content:center;align-items:center;padding:1rem}
.modal.open{display:flex}
.modal-box{background:var(--surface);border:1px solid var(--border);border-radius:8px;max-width:960px;width:100%;max-height:90vh;display:flex;flex-direction:column;overflow:hidden}
.modal-head{padding:10px 16px;background:var(--surface2);border-bottom:1px solid var(--border);display:flex;justify-content:space-between;align-items:center}
.modal-head h3{font-size:13px;font-weight:500}
.modal-close{background:none;border:none;color:var(--text2);font-size:18px;cursor:pointer;width:32px;height:32px;display:flex;align-items:center;justify-content:center;border-radius:4px}
.modal-close:hover{background:var(--hover);color:var(--text)}
.modal-body{flex:1;display:flex;justify-content:center;align-items:center;background:#000;overflow:auto;min-height:200px}
.modal-body video,.modal-body audio{max-width:100%;max-height:75vh;outline:none}
.modal-body img{max-width:100%;max-height:80vh;object-fit:contain}
.modal-body iframe{width:100%;height:75vh;border:none}
/* Upload Toast Panel */
.upload-toast{position:fixed;bottom:20px;right:20px;background:var(--surface2);border:1px solid var(--border);border-radius:8px;padding:12px 16px;width:320px;box-shadow:0 8px 24px rgba(0,0,0,0.4);display:none;flex-direction:column;gap:8px;z-index:10000}
.upload-toast.active{display:flex}
.upload-toast-header{display:flex;justify-content:space-between;align-items:center;font-weight:600;font-size:12px;color:var(--text)}
.upload-toast-file{font-size:11px;color:var(--text2);overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.upload-progress-track{width:100%;height:6px;background:var(--bg);border-radius:3px;overflow:hidden}
.upload-progress-fill{height:100%;background:var(--accent);width:0%;transition:width 0.1s}
.upload-toast-stats{display:flex;justify-content:space-between;font-size:10px;color:var(--text2)}
/* Mobile */
@media(max-width:768px){
  .c-date,.c-type{display:none}
  .c-size{width:60px}
  .addr-search{width:120px}
  .toolbar{padding:4px 8px}
  .tb-btn span{display:none}
}
@media(max-width:480px){
  .c-size{display:none}
  .addr-search{display:none}
}
</style></head><body>
<input type="file" id="fi" multiple style="display:none" onchange="uploadFiles(this.files)">
<div class="toolbar">
  <button class="tb-btn" onclick="goBack()" title="Back">◀</button>
  <button class="tb-btn" onclick="goUp()" title="Up">▲</button>
  <div class="tb-sep"></div>
  <button class="tb-btn" onclick="location.reload()" title="Refresh">↻ <span>Refresh</span></button>
  <button class="tb-btn" onclick="document.getElementById('fi').click()" title="Upload">⇪ <span>Upload</span></button>
  <button class="tb-btn" onclick="newFolder()" title="New folder">+ <span>New folder</span></button>
</div>
<div class="addr-bar">
  <div class="addr-path">$breadcrumbs</div>
  <input class="addr-search" type="text" placeholder="Search files..." oninput="filterFiles(this.value)">
</div>
<div class="content">
<table><thead><tr><th style="width:32px"></th><th onclick="sortBy('name')">Name ↕</th><th onclick="sortBy('date')">Date modified ↕</th><th onclick="sortBy('type')">Type ↕</th><th onclick="sortBy('size')" style="text-align:right">Size ↕</th></tr></thead>
<tbody id="tbody">$rows</tbody></table>
${if(sorted.isEmpty()) "<div style='padding:3rem;text-align:center;color:var(--text2)'>This folder is empty</div>" else ""}
</div>
<div class="statusbar"><span id="itemCount">${sorted.size} items</span><span>LinkShare Explorer</span></div>

<!-- Context Menu -->
<div class="ctx-menu" id="ctx">
  <div class="ctx-item" id="ctx-open" onclick="ctxOpen()">Open</div>
  <div class="ctx-item" id="ctx-stream" onclick="ctxStream()">Play / View</div>
  <div class="ctx-sep"></div>
  <div class="ctx-item" onclick="ctxDownload()">Download</div>
  <div class="ctx-item" onclick="ctxCopyLink()">Copy link</div>
  <div class="ctx-sep"></div>
  <div class="ctx-item danger" onclick="ctxDelete()">Delete</div>
</div>

<!-- Media Modal -->
<div class="modal" id="modal">
  <div class="modal-box">
    <div class="modal-head"><h3 id="modalTitle">Viewer</h3><button class="modal-close" onclick="closeModal()">✕</button></div>
    <div class="modal-body" id="modalBody"></div>
  </div>
</div>

<!-- Upload Progress Toast -->
<div class="upload-toast" id="uploadToast">
  <div class="upload-toast-header"><span>Uploading to LinkShare...</span><span id="uploadPercent">0%</span></div>
  <div class="upload-toast-file" id="uploadFileName">Preparing files...</div>
  <div class="upload-progress-track"><div class="upload-progress-fill" id="uploadProgressFill"></div></div>
  <div class="upload-toast-stats"><span id="uploadTransferred">0 MB</span><span id="uploadSpeed">0 MB/s</span></div>
</div>

<script>
var PIN='$sessionPin',CUR='${escJs(rel)}';
var ctxTarget=null;

function nav(p){location.href='/?path='+encodeURIComponent(p)+'&pin='+PIN}
function goBack(){history.back()}
function goUp(){var p=CUR.split('/').filter(Boolean);p.pop();nav('/'+p.join('/'))}
function dl(enc){var a=document.createElement('a');a.href='/api/download?path='+enc+'&pin='+PIN;a.download='';a.click()}
function playMedia(enc,name){
  var url='/api/stream?path='+enc+'&pin='+PIN;
  document.getElementById('modalTitle').textContent=name;
  var isAudio=name.match(/\.(mp3|m4a|wav|flac|aac|ogg|opus|wma|amr|mid|midi)$/i);
  document.getElementById('modalBody').innerHTML=isAudio?
    '<audio controls autoplay style="width:100%;max-width:500px;margin:2rem"><source src="'+url+'"></audio>':
    '<video controls autoplay style="max-width:100%;max-height:75vh"><source src="'+url+'"></video>';
  document.getElementById('modal').classList.add('open');
}
function viewImg(enc,name){
  document.getElementById('modalTitle').textContent=name;
  document.getElementById('modalBody').innerHTML='<img src="/api/stream?path='+enc+'&pin='+PIN+'">';
  document.getElementById('modal').classList.add('open');
}
function viewDoc(enc,name){
  document.getElementById('modalTitle').textContent=name;
  document.getElementById('modalBody').innerHTML='<iframe src="/api/stream?path='+enc+'&pin='+PIN+'"></iframe>';
  document.getElementById('modal').classList.add('open');
}
function closeModal(){document.getElementById('modalBody').innerHTML='';document.getElementById('modal').classList.remove('open')}
function uploadFiles(files){
  if(!files||!files.length)return;
  var toast=document.getElementById('uploadToast');
  var fill=document.getElementById('uploadProgressFill');
  var percent=document.getElementById('uploadPercent');
  var fileLabel=document.getElementById('uploadFileName');
  var transferredLabel=document.getElementById('uploadTransferred');
  var speedLabel=document.getElementById('uploadSpeed');
  
  toast.classList.add('active');
  fileLabel.textContent=files.length==1?files[0].name:files.length+' files';

  var fd=new FormData();for(var i=0;i<files.length;i++)fd.append('f'+i,files[i]);
  var xhr=new XMLHttpRequest();
  var startTime=Date.now();
  
  xhr.upload.onprogress=function(e){
    if(e.lengthComputable){
      var pct=Math.round((e.loaded/e.total)*100);
      fill.style.width=pct+'%';
      percent.textContent=pct+'%';
      var elapsed=(Date.now()-startTime)/1000;
      var spd=elapsed>0?(e.loaded/elapsed):(0);
      transferredLabel.textContent=(e.loaded/1048576).toFixed(1)+' / '+(e.total/1048576).toFixed(1)+' MB';
      speedLabel.textContent=(spd/1048576).toFixed(1)+' MB/s';
    }
  };
  xhr.onload=function(){
    toast.classList.remove('active');
    location.reload();
  };
  xhr.onerror=function(){
    toast.classList.remove('active');
    alert('Upload failed.');
  };
  xhr.open('POST','/api/upload?path='+encodeURIComponent(CUR)+'&pin='+PIN);
  xhr.send(fd);
}
function newFolder(){
  var name=prompt('New folder name:');if(!name)return;
  fetch('/api/upload?path='+encodeURIComponent(CUR+'/'+name)+'&pin='+PIN,{method:'MKCOL'}).then(function(){location.reload()});
}
function filterFiles(q){
  var rows=document.querySelectorAll('.row');var ql=q.toLowerCase();var count=0;
  rows.forEach(function(r){var n=r.getAttribute('data-name').toLowerCase();var v=n.includes(ql);r.style.display=v?'':'none';if(v)count++});
  document.getElementById('itemCount').textContent=count+' items';
}
// Selection
document.addEventListener('click',function(e){
  document.querySelectorAll('.row.selected').forEach(function(r){r.classList.remove('selected')});
  var row=e.target.closest('.row');if(row)row.classList.add('selected');
  document.getElementById('ctx').style.display='none';
});
// Context menu
document.addEventListener('contextmenu',function(e){
  var row=e.target.closest('.row');if(!row){document.getElementById('ctx').style.display='none';return}
  e.preventDefault();ctxTarget=row;
  document.querySelectorAll('.row.selected').forEach(function(r){r.classList.remove('selected')});
  row.classList.add('selected');
  var ctx=document.getElementById('ctx');
  var isDir=row.getAttribute('data-isdir')==='true';
  var isMedia=row.getAttribute('data-media')==='true';
  var isImg=row.getAttribute('data-img')==='true';
  var isDoc=row.getAttribute('data-doc')==='true';
  document.getElementById('ctx-open').style.display=isDir?'':'none';
  document.getElementById('ctx-stream').style.display=(!isDir&&(isMedia||isImg||isDoc))?'':'none';
  ctx.style.left=Math.min(e.clientX,window.innerWidth-220)+'px';
  ctx.style.top=Math.min(e.clientY,window.innerHeight-200)+'px';
  ctx.style.display='block';
});
function ctxOpen(){if(ctxTarget)nav(ctxTarget.getAttribute('data-path'))}
function ctxStream(){
  if(!ctxTarget)return;var enc=ctxTarget.getAttribute('data-enc'),name=ctxTarget.getAttribute('data-name');
  if(ctxTarget.getAttribute('data-media')==='true')playMedia(enc,name);
  else if(ctxTarget.getAttribute('data-img')==='true')viewImg(enc,name);
  else if(ctxTarget.getAttribute('data-doc')==='true')viewDoc(enc,name);
}
function ctxDownload(){if(ctxTarget)dl(ctxTarget.getAttribute('data-enc'))}
function ctxCopyLink(){
  if(!ctxTarget)return;
  var url=location.origin+'/api/stream?path='+ctxTarget.getAttribute('data-enc')+'&pin='+PIN;
  navigator.clipboard.writeText(url);
}
function ctxDelete(){
  if(!ctxTarget)return;
  if(!confirm('Delete "'+ctxTarget.getAttribute('data-name')+'"?'))return;
  fetch('/api/delete?path='+ctxTarget.getAttribute('data-enc')+'&pin='+PIN,{method:'POST'}).then(function(){location.reload()});
}
// Keyboard
document.addEventListener('keydown',function(e){if(e.key==='Escape')closeModal()});
// Drag & drop upload
document.querySelector('.content').addEventListener('dragover',function(e){e.preventDefault()});
document.querySelector('.content').addEventListener('drop',function(e){e.preventDefault();if(e.dataTransfer.files.length)uploadFiles(e.dataTransfer.files)});
</script>
</body></html>"""
    }

    private fun buildBreadcrumbs(relPath: String): String {
        val sb = StringBuilder("<a class='crumb' onclick=\"nav('/')\">This PC</a>")
        val parts = relPath.split('/').filter { it.isNotBlank() }
        var acc = ""
        for (part in parts) {
            acc += "/$part"
            sb.append("<span class='crumb-sep'>›</span><a class='crumb' onclick=\"nav('${escJs(acc)}')\">$part</a>")
        }
        return sb.toString()
    }
}
