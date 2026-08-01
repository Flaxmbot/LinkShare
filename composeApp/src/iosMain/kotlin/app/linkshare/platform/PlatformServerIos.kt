package app.linkshare.platform

import app.linkshare.model.currentTimeMillis
import kotlinx.coroutines.*
import platform.posix.*
import kotlinx.cinterop.*
import kotlin.random.Random

actual class PlatformHttpServer actual constructor(private val port: Int) {
    private var isRunning = false
    private var serverFd = -1
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    actual var sessionPin: String = generatePin()
        private set
    actual var enterpriseToken: String = "token_${generatePin()}${generatePin()}"
        private set
    actual var activeClipboardText: String = ""

    private var sharedDirectory: String = ""

    actual fun startServer(shareDir: String, customPin: String?, timeoutMinutes: Int, maxSpeedMbps: Int) {
        if (isRunning) stopServer()
        sharedDirectory = shareDir
        sessionPin = customPin ?: generatePin()
        isRunning = true

        scope.launch {
            try {
                serverFd = socket(AF_INET, SOCK_STREAM, 0)
                if (serverFd < 0) { Log.e("HttpServer", "Failed to create socket"); return@launch }

                memScoped {
                    val reuse = alloc<IntVar>(); reuse.value = 1
                    setsockopt(serverFd, SOL_SOCKET, SO_REUSEADDR, reuse.ptr, sizeOf<IntVar>().toUInt())
                    val addr = alloc<sockaddr_in>()
                    addr.sin_family = AF_INET.toUShort()
                    addr.sin_port = htons(port.toUShort())
                    addr.sin_addr.s_addr = htonl(0u) // INADDR_ANY
                    if (bind(serverFd, addr.ptr.reinterpret(), sizeOf<sockaddr_in>().toUInt()) < 0) {
                        Log.e("HttpServer", "Bind failed on port $port"); close(serverFd); return@launch
                    }
                    listen(serverFd, 10)
                    Log.i("HttpServer", "iOS HTTP Server listening on port $port")
                }

                while (isRunning) {
                    val clientFd = accept(serverFd, null, null)
                    if (clientFd < 0) { if (isRunning) continue else break }
                    launch { handleClientIos(clientFd) }
                }
            } catch (e: Exception) {
                Log.e("HttpServer", "iOS server error: ${e.message}")
            }
        }
    }

    actual fun stopServer() {
        isRunning = false
        if (serverFd >= 0) { close(serverFd); serverFd = -1 }
    }

    actual fun isServerActive(): Boolean = isRunning && serverFd >= 0

    actual fun generateConnectionString(ipAddress: String): String =
        "http://$ipAddress:$port?pin=$sessionPin"

    private fun handleClientIos(fd: Int) {
        try {
            val buf = ByteArray(8192)
            val n = buf.usePinned { recv(fd, it.addressOf(0), buf.size.toULong(), 0).toInt() }
            if (n <= 0) { close(fd); return }

            val request = buf.decodeToString(0, n)
            val lines = request.split("\r\n")
            val requestLine = lines.firstOrNull() ?: ""
            val parts = requestLine.split(" ")
            if (parts.size < 2) { close(fd); return }
            val path = parts[1]

            // Simple response: serve a basic HTML page or file listing
            val response = buildString {
                append("HTTP/1.1 200 OK\r\n")
                append("Content-Type: text/html; charset=utf-8\r\n")
                append("Connection: close\r\n")
                val body = "<html><body><h1>LinkShare iOS Server</h1><p>Server running on port $port</p></body></html>"
                append("Content-Length: ${body.length}\r\n\r\n")
                append(body)
            }
            val bytes = response.encodeToByteArray()
            bytes.usePinned { send(fd, it.addressOf(0), bytes.size.toULong(), 0) }
        } catch (_: Exception) {} finally { close(fd) }
    }

    private fun generatePin(): String = "%04d".format(Random.nextInt(1000, 9999))
}

actual class PlatformFtpServer actual constructor(
    private val port: Int,
    private val timeoutMinutes: Int
) {
    actual var sessionPin: String = "%04d".format(Random.nextInt(1000, 9999))
        private set

    actual fun startServer(shareDir: String, customPin: String?) {
        Log.i("FtpServer", "FTP server is not available on iOS due to platform restrictions")
    }

    actual fun stopServer() {}
    actual fun isServerActive(): Boolean = false
}
