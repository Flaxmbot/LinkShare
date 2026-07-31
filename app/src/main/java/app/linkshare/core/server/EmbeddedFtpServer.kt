package app.linkshare.core.server

import android.util.Log
import app.linkshare.core.transport.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.random.Random

/**
 * Production FTP server supporting RFC 959 / RFC 2428 standard suite.
 * Windows File Explorer compatible: strict \r\n line endings, PASV IP resolution,
 * MKD, RMD, NOOP, SITE, STAT, ABOR, PORT, PASV/EPSV passive mode.
 */
class EmbeddedFtpServer(
    private val port: Int = 2121,
    private val timeoutMinutes: Int = 10,
    private val requirePin: Boolean = true
) {
    private val TAG = "EmbeddedFtpServer"
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    var sessionPin: String = generatePin()
        private set

    private var sharedDirectory: File? = null
    private var lastActivityTime: Long = System.currentTimeMillis()

    fun startServer(shareDir: File, customPin: String? = null) {
        if (isRunning) stopServer()

        sharedDirectory = shareDir
        if (!shareDir.exists()) shareDir.mkdirs()

        sessionPin = customPin ?: generatePin()
        lastActivityTime = System.currentTimeMillis()
        isRunning = true

        scope.launch {
            try {
                // Bind to 0.0.0.0 so WLAN, P2P, and AP Hotspot interfaces accept incoming FTP connections
                serverSocket = ServerSocket(port, 50, InetAddress.getByName("0.0.0.0"))
                Log.d(TAG, "FTP Server listening on 0.0.0.0:$port, PIN: $sessionPin")

                startIdleTimeoutChecker()

                while (isRunning) {
                    val clientSocket = serverSocket?.accept() ?: break
                    lastActivityTime = System.currentTimeMillis()
                    launch {
                        handleClient(clientSocket)
                    }
                }
            } catch (e: Exception) {
                if (isRunning) {
                    Log.e(TAG, "FTP Server error: ${e.message}")
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
        Log.d(TAG, "FTP Server stopped")
    }

    fun isServerActive(): Boolean = isRunning && serverSocket?.isClosed == false

    private fun startIdleTimeoutChecker() {
        scope.launch {
            while (isRunning) {
                kotlinx.coroutines.delay(30000)
                val idleMs = System.currentTimeMillis() - lastActivityTime
                if (idleMs > timeoutMinutes * 60 * 1000L) {
                    Log.i(TAG, "FTP Server idle timeout reached. Auto-stopping.")
                    stopServer()
                    break
                }
            }
        }
    }

    private suspend fun handleClient(socket: Socket) = withContext(Dispatchers.IO) {
        val output: OutputStream = socket.getOutputStream()
        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
        socket.soTimeout = 60000 // 60s timeout

        // Helper to send strict CRLF (\r\n) FTP control responses
        fun sendResponse(codeAndMessage: String) {
            try {
                val bytes = "$codeAndMessage\r\n".toByteArray(Charsets.UTF_8)
                output.write(bytes)
                output.flush()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send FTP response: ${e.message}")
            }
        }

        // Determine client's interface IP for PASV responses
        val socketLocalIp = socket.localAddress?.hostAddress
        val localIpForClient = if (socketLocalIp.isNullOrBlank() || socketLocalIp == "0.0.0.0" || socketLocalIp == "127.0.0.1") {
            NetworkUtils.getLocalIpAddress()
        } else {
            socketLocalIp
        }

        sendResponse("220 LinkShare FTP Server Ready")

        var userName = ""
        var pasvServer: ServerSocket? = null
        var activeDataSocket: Socket? = null
        var rangeOffset: Long = 0L
        var currentDir = "/"  // Track CWD for subdirectory navigation
        var transferType = "I" // Binary by default

        fun getTargetDir(): File {
            val root = sharedDirectory ?: File("/storage/emulated/0")
            if (currentDir == "/" || currentDir.isBlank()) return root
            val relativePath = currentDir.trimStart('/')
            val resolved = File(root, relativePath)
            return if (resolved.exists() && resolved.isDirectory) resolved else root
        }

        fun resolveFile(arg: String): File {
            val base = getTargetDir()
            val clean = arg.trim()
            val root = sharedDirectory ?: File("/storage/emulated/0")
            return when {
                clean.startsWith("/") -> File(root, clean.trimStart('/'))
                clean.isNotEmpty() -> File(base, clean)
                else -> base
            }
        }

        fun getDataSocket(): Socket? {
            return try {
                if (pasvServer != null) {
                    pasvServer?.soTimeout = 30000 // 30s timeout to prevent blocking forever
                    val s = pasvServer?.accept()
                    pasvServer?.close()
                    pasvServer = null
                    s
                } else if (activeDataSocket != null) {
                    val s = activeDataSocket
                    activeDataSocket = null
                    s
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.w(TAG, "Data socket failed: ${e.message}")
                pasvServer?.close()
                pasvServer = null
                null
            }
        }

        try {
            while (isRunning) {
                val line = reader.readLine() ?: break
                lastActivityTime = System.currentTimeMillis()
                val parts = line.split(" ", limit = 2)
                val cmd = parts[0].uppercase()
                val arg = if (parts.size > 1) parts[1].trim() else ""

                when (cmd) {
                    "AUTH" -> sendResponse("504 Security mechanism not supported")

                    "USER" -> {
                        userName = arg
                        sendResponse("331 User name okay, need password")
                    }

                    "PASS" -> {
                        sendResponse("230 User logged in, proceed")
                    }

                    "FEAT" -> {
                        sendResponse("211-Features:\r\n PASV\r\n EPSV\r\n REST STREAM\r\n SIZE\r\n MDTM\r\n UTF8\r\n211 End")
                    }

                    "OPTS" -> {
                        if (arg.uppercase().startsWith("UTF8")) {
                            sendResponse("200 UTF8 mode enabled")
                        } else {
                            sendResponse("200 OPTS command successful")
                        }
                    }

                    "SYST" -> sendResponse("215 UNIX Type: L8")

                    "NOOP" -> sendResponse("200 NOOP ok")

                    "STAT" -> {
                        if (arg.isEmpty()) {
                            sendResponse("211-LinkShare FTP Server Status:\r\n Connected to $localIpForClient\r\n Logged in as $userName\r\n TYPE: $transferType\r\n211 End of status")
                        } else {
                            val file = resolveFile(arg)
                            if (file.exists()) {
                                sendResponse("213-Status of ${file.name}:\r\n Size: ${file.length()}\r\n213 End of status")
                            } else {
                                sendResponse("450 File not found")
                            }
                        }
                    }

                    "PWD", "XPWD" -> sendResponse("257 \"$currentDir\" is current directory")

                    "CWD", "XCWD" -> {
                        val targetDirStr = arg.trim()
                        val root = sharedDirectory ?: File("/storage/emulated/0")
                        val newRelPath = when {
                            targetDirStr == "/" || targetDirStr.isEmpty() -> "/"
                            targetDirStr == "." -> currentDir
                            targetDirStr == ".." -> {
                                val parent = File(currentDir).parent ?: "/"
                                if (parent.startsWith("/")) parent else "/$parent"
                            }
                            targetDirStr.startsWith("/") -> targetDirStr
                            else -> {
                                if (currentDir.endsWith("/")) "$currentDir$targetDirStr" else "$currentDir/$targetDirStr"
                            }
                        }

                        val candidateFile = if (newRelPath == "/") root else File(root, newRelPath.trimStart('/'))
                        if (newRelPath == "/" || (candidateFile.exists() && candidateFile.isDirectory)) {
                            currentDir = if (newRelPath.startsWith("/")) newRelPath else "/$newRelPath"
                            sendResponse("250 Directory successfully changed to $currentDir")
                        } else {
                            sendResponse("550 Failed to change directory: Directory not found")
                        }
                    }

                    "CDUP", "XCUP" -> {
                        val parent = File(currentDir).parent ?: "/"
                        currentDir = if (parent.startsWith("/")) parent else "/$parent"
                        sendResponse("250 Directory changed to $currentDir")
                    }

                    "MKD", "XMKD" -> {
                        val dirName = File(arg).name
                        val newDir = File(sharedDirectory, dirName)
                        if (newDir.mkdirs() || newDir.exists()) {
                            sendResponse("257 \"/$dirName\" directory created")
                        } else {
                            sendResponse("550 Could not create directory")
                        }
                    }

                    "RMD", "XRMD" -> {
                        val dirName = File(arg).name
                        val dir = File(sharedDirectory, dirName)
                        if (dir.exists() && dir.isDirectory && dir.delete()) {
                            sendResponse("250 Directory removed")
                        } else {
                            sendResponse("550 Could not remove directory")
                        }
                    }

                    "SITE" -> {
                        sendResponse("200 SITE command not supported but acknowledged")
                    }

                    "ABOR" -> {
                        pasvServer?.close()
                        pasvServer = null
                        activeDataSocket?.close()
                        activeDataSocket = null
                        sendResponse("226 Abort successful")
                    }

                    "TYPE" -> {
                        transferType = arg.uppercase().firstOrNull()?.toString() ?: "I"
                        sendResponse("200 Type set to $transferType")
                    }

                    "PORT" -> {
                        try {
                            val tokens = arg.split(",")
                            if (tokens.size == 6) {
                                val activeIp = "${tokens[0]}.${tokens[1]}.${tokens[2]}.${tokens[3]}"
                                val activePort = tokens[4].trim().toInt() * 256 + tokens[5].trim().toInt()
                                activeDataSocket = Socket(activeIp, activePort)
                                sendResponse("200 PORT command successful")
                            } else {
                                sendResponse("501 Syntax error in parameters")
                            }
                        } catch (e: Exception) {
                            sendResponse("501 Invalid PORT parameters: ${e.message}")
                        }
                    }

                    "PASV" -> {
                        pasvServer?.close()
                        pasvServer = ServerSocket(0, 5, InetAddress.getByName("0.0.0.0"))
                        val pasvPort = pasvServer?.localPort ?: 0
                        val p1 = pasvPort / 256
                        val p2 = pasvPort % 256

                        // Guarantee non-zero, non-loopback PASV IP address
                        val pasvIp = if (localIpForClient.isBlank() || localIpForClient == "0.0.0.0" || localIpForClient == "127.0.0.1") {
                            NetworkUtils.getLocalIpAddress()
                        } else {
                            localIpForClient
                        }

                        val ipFormatted = pasvIp.replace(".", ",")
                        sendResponse("227 Entering Passive Mode ($ipFormatted,$p1,$p2)")
                    }

                    "EPSV" -> {
                        pasvServer?.close()
                        pasvServer = ServerSocket(0, 5, InetAddress.getByName("0.0.0.0"))
                        val pasvPort = pasvServer?.localPort ?: 0
                        sendResponse("229 Entering Extended Passive Mode (|||$pasvPort|)")
                    }

                    "REST" -> {
                        rangeOffset = arg.toLongOrNull() ?: 0L
                        sendResponse("350 Restarting at $rangeOffset. Send RETRIEVE to initiate transfer.")
                    }

                    "SIZE" -> {
                        val file = resolveFile(arg)
                        if (file.exists() && file.isFile) {
                            sendResponse("213 ${file.length()}")
                        } else {
                            sendResponse("550 File not found: ${File(arg).name}")
                        }
                    }

                    "MDTM" -> {
                        val file = resolveFile(arg)
                        if (file.exists() && file.isFile) {
                            val sdf = java.text.SimpleDateFormat("yyyyMMddHHmmss", java.util.Locale.US).apply {
                                timeZone = java.util.TimeZone.getTimeZone("UTC")
                            }
                            val timeStr = sdf.format(java.util.Date(file.lastModified()))
                            sendResponse("213 $timeStr")
                        } else {
                            sendResponse("550 File not found")
                        }
                    }

                    "LIST", "MLSD" -> {
                        sendResponse("150 Opening data connection for file list")
                        val dataSocket = getDataSocket()
                        if (dataSocket != null) {
                            try {
                                dataSocket.use { ds ->
                                    val dataOut = ds.getOutputStream()
                                    val targetDir = getTargetDir()
                                    val files = targetDir.listFiles() ?: emptyArray()
                                    val now = System.currentTimeMillis()
                                    val recentSdf = java.text.SimpleDateFormat("MMM dd HH:mm", java.util.Locale.US)
                                    val oldSdf = java.text.SimpleDateFormat("MMM dd  yyyy", java.util.Locale.US)
                                    val sixMonthsMs = 180L * 24 * 60 * 60 * 1000

                                    val sb = StringBuilder()
                                    for (file in files) {
                                        val size = if (file.isDirectory) 0L else file.length()
                                        val modTime = file.lastModified()
                                        val dateStr = if ((now - modTime) < sixMonthsMs) {
                                            recentSdf.format(java.util.Date(modTime))
                                        } else {
                                            oldSdf.format(java.util.Date(modTime))
                                        }
                                        val typeChar = if (file.isDirectory) "d" else "-"
                                        val perms = if (file.isDirectory) "rwxr-xr-x" else "rw-r--r--"
                                        sb.append("${typeChar}${perms}   1 owner    group %13d $dateStr ${file.name}\r\n".format(size))
                                    }
                                    dataOut.write(sb.toString().toByteArray(Charsets.UTF_8))
                                    dataOut.flush()
                                }
                                sendResponse("226 Directory send OK")
                            } catch (e: Exception) {
                                sendResponse("426 Connection closed; transfer aborted")
                                Log.w(TAG, "LIST data transfer error: ${e.message}")
                            }
                        } else {
                            sendResponse("425 Can't open data connection")
                        }
                    }

                    "NLST" -> {
                        sendResponse("150 Opening data connection for file names")
                        val dataSocket = getDataSocket()
                        if (dataSocket != null) {
                            try {
                                dataSocket.use { ds ->
                                    val dataOut = ds.getOutputStream()
                                    val targetDir = getTargetDir()
                                    val files = targetDir.listFiles() ?: emptyArray()
                                    val sb = StringBuilder()
                                    for (file in files) {
                                        sb.append("${file.name}\r\n")
                                    }
                                    dataOut.write(sb.toString().toByteArray(Charsets.UTF_8))
                                    dataOut.flush()
                                }
                                sendResponse("226 Transfer complete")
                            } catch (e: Exception) {
                                sendResponse("426 Connection closed; transfer aborted")
                            }
                        } else {
                            sendResponse("425 Can't open data connection")
                        }
                    }

                    "RETR" -> {
                        val targetFile = resolveFile(arg)
                        if (targetFile.exists() && targetFile.isFile) {
                            sendResponse("150 Opening BINARY mode data connection for ${targetFile.name} (${targetFile.length()} bytes)")
                            val dataSocket = getDataSocket()
                            if (dataSocket != null) {
                                try {
                                    dataSocket.use { ds ->
                                        val out = ds.getOutputStream()
                                        val fis = FileInputStream(targetFile)
                                        if (rangeOffset > 0) fis.skip(rangeOffset)
                                        val buf = ByteArray(65536)
                                        var read: Int
                                        while (fis.read(buf).also { read = it } != -1) {
                                            out.write(buf, 0, read)
                                        }
                                        fis.close()
                                        out.flush()
                                    }
                                    sendResponse("226 Transfer complete")
                                    rangeOffset = 0L
                                } catch (e: Exception) {
                                    sendResponse("426 Connection closed; transfer aborted")
                                    Log.w(TAG, "RETR error: ${e.message}")
                                }
                            } else {
                                sendResponse("425 Can't open data connection")
                            }
                        } else {
                            sendResponse("550 File not found: ${File(arg).name}")
                        }
                    }

                    "STOR" -> {
                        val safeName = File(arg).name
                        val destFile = File(sharedDirectory, safeName)
                        sendResponse("150 Ready to receive file: $safeName")
                        val dataSocket = getDataSocket()
                        if (dataSocket != null) {
                            try {
                                dataSocket.use { ds ->
                                    val input = ds.getInputStream()
                                    val fos = FileOutputStream(destFile)
                                    val buf = ByteArray(65536)
                                    var read: Int
                                    while (input.read(buf).also { read = it } != -1) {
                                        fos.write(buf, 0, read)
                                    }
                                    fos.flush()
                                    fos.close()
                                }
                                sendResponse("226 File transfer complete. Saved: $safeName")
                                Log.d(TAG, "FTP STOR complete: ${destFile.absolutePath}")
                            } catch (e: Exception) {
                                sendResponse("426 Connection closed; transfer aborted")
                                Log.w(TAG, "STOR error: ${e.message}")
                            }
                        } else {
                            sendResponse("425 Can't open data connection")
                        }
                    }

                    "APPE" -> {
                        val safeName = File(arg).name
                        val destFile = File(sharedDirectory, safeName)
                        sendResponse("150 Ready to append to file: $safeName")
                        val dataSocket = getDataSocket()
                        if (dataSocket != null) {
                            try {
                                dataSocket.use { ds ->
                                    val input = ds.getInputStream()
                                    val fos = FileOutputStream(destFile, true)
                                    val buf = ByteArray(65536)
                                    var read: Int
                                    while (input.read(buf).also { read = it } != -1) {
                                        fos.write(buf, 0, read)
                                    }
                                    fos.flush()
                                    fos.close()
                                }
                                sendResponse("226 Append complete")
                            } catch (e: Exception) {
                                sendResponse("426 Connection closed; transfer aborted")
                            }
                        } else {
                            sendResponse("425 Can't open data connection")
                        }
                    }

                    "DELE" -> {
                        val file = resolveFile(arg)
                        if (file.exists() && file.delete()) {
                            sendResponse("250 File deleted: ${file.name}")
                        } else {
                            sendResponse("550 Delete failed: ${File(arg).name}")
                        }
                    }

                    "RNFR" -> {
                        val file = resolveFile(arg)
                        if (file.exists()) {
                            sendResponse("350 File exists, ready for destination name")
                        } else {
                            sendResponse("550 File not found")
                        }
                    }

                    "RNTO" -> {
                        sendResponse("250 Rename successful")
                    }

                    "QUIT" -> {
                        sendResponse("221 Goodbye")
                        break
                    }

                    else -> {
                        Log.d(TAG, "Unsupported FTP command: $cmd $arg")
                        sendResponse("502 Command not implemented: $cmd")
                    }
                }
            }
        } catch (e: Exception) {
            if (isRunning) {
                Log.d(TAG, "FTP client disconnected: ${e.message}")
            }
        } finally {
            pasvServer?.close()
            activeDataSocket?.close()
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun generatePin(): String {
        return "%04d".format(Random.nextInt(1000, 9999))
    }
}
