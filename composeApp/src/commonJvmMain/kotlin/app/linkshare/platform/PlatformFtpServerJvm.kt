package app.linkshare.platform

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

actual class PlatformFtpServer actual constructor(
    private val port: Int,
    private val timeoutMinutes: Int
) {
    private val TAG = "FtpServer"
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var sharedDirectory: File? = null
    private var lastActivityTime: Long = System.currentTimeMillis()

    actual var sessionPin: String = generatePin()
        private set

    actual fun startServer(shareDir: String, customPin: String?) {
        if (isRunning) stopServer()
        val dir = File(shareDir)
        sharedDirectory = dir
        if (!dir.exists()) dir.mkdirs()
        sessionPin = customPin ?: generatePin()
        lastActivityTime = System.currentTimeMillis()
        isRunning = true

        scope.launch {
            try {
                serverSocket = ServerSocket(port, 50, InetAddress.getByName("0.0.0.0"))
                Log.d(TAG, "FTP Server on 0.0.0.0:$port")
                launch {
                    while (isRunning) {
                        kotlinx.coroutines.delay(30000)
                        if ((System.currentTimeMillis() - lastActivityTime) > timeoutMinutes * 60 * 1000L) {
                            Log.i(TAG, "FTP idle timeout. Stopping.")
                            stopServer(); break
                        }
                    }
                }
                while (isRunning) {
                    val client = serverSocket?.accept() ?: break
                    lastActivityTime = System.currentTimeMillis()
                    launch { handleClient(client) }
                }
            } catch (e: Exception) {
                if (isRunning) Log.e(TAG, "FTP error: ${e.message}")
            } finally { stopServer() }
        }
    }

    actual fun stopServer() {
        isRunning = false
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
    }

    actual fun isServerActive(): Boolean = isRunning && serverSocket?.isClosed == false

    private suspend fun handleClient(socket: Socket) = withContext(Dispatchers.IO) {
        val output: OutputStream = socket.getOutputStream()
        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
        socket.soTimeout = 60000

        fun send(msg: String) {
            try { output.write("$msg\r\n".toByteArray(Charsets.UTF_8)); output.flush() }
            catch (e: Exception) { Log.w(TAG, "Send failed: ${e.message}") }
        }

        val socketIp = socket.localAddress?.hostAddress
        val localIp = if (socketIp.isNullOrBlank() || socketIp == "0.0.0.0" || socketIp == "127.0.0.1")
            PlatformNetwork.getLocalIpAddress() else socketIp

        send("220 LinkShare FTP Server Ready")
        var pasvServer: ServerSocket? = null
        var activeDataSocket: Socket? = null
        var rangeOffset = 0L
        var currentDir = "/"
        var transferType = "I"

        fun targetDir(): File {
            val root = sharedDirectory ?: File(System.getProperty("user.home") ?: "/")
            if (currentDir == "/" || currentDir.isBlank()) return root
            val resolved = File(root, currentDir.trimStart('/'))
            return if (resolved.canonicalPath.startsWith(root.canonicalPath) && resolved.exists() && resolved.isDirectory) resolved else root
        }

        fun resolve(arg: String): File {
            val base = targetDir()
            val root = sharedDirectory ?: File(System.getProperty("user.home") ?: "/")
            val candidate = when {
                arg.startsWith("/") -> File(root, arg.trimStart('/'))
                arg.isNotEmpty() -> File(base, arg)
                else -> base
            }
            return try {
                if (candidate.canonicalPath.startsWith(root.canonicalPath)) candidate else root
            } catch (_: Exception) { root }
        }

        fun dataSocket(): Socket? = try {
            if (pasvServer != null) {
                pasvServer?.soTimeout = 30000
                val s = pasvServer?.accept(); pasvServer?.close(); pasvServer = null; s
            } else { val s = activeDataSocket; activeDataSocket = null; s }
        } catch (e: Exception) { pasvServer?.close(); pasvServer = null; null }

        try {
            while (isRunning) {
                val line = reader.readLine() ?: break
                lastActivityTime = System.currentTimeMillis()
                val parts = line.split(" ", limit = 2)
                val cmd = parts[0].uppercase()
                val arg = if (parts.size > 1) parts[1].trim() else ""

                when (cmd) {
                    "AUTH" -> send("504 Security mechanism not supported")
                    "USER" -> send("331 User name okay, need password")
                    "PASS" -> send("230 User logged in, proceed")
                    "FEAT" -> send("211-Features:\r\n PASV\r\n EPSV\r\n REST STREAM\r\n SIZE\r\n MDTM\r\n UTF8\r\n211 End")
                    "OPTS" -> send(if (arg.uppercase().startsWith("UTF8")) "200 UTF8 mode enabled" else "200 OPTS OK")
                    "SYST" -> send("215 UNIX Type: L8")
                    "NOOP" -> send("200 NOOP ok")
                    "PWD", "XPWD" -> send("257 \"$currentDir\" is current directory")
                    "TYPE" -> { transferType = arg.uppercase().firstOrNull()?.toString() ?: "I"; send("200 Type set to $transferType") }
                    "CWD", "XCWD" -> {
                        val root = sharedDirectory ?: File(System.getProperty("user.home") ?: "/")
                        val newRel = when {
                            arg == "/" || arg.isEmpty() -> "/"
                            arg == "." -> currentDir
                            arg == ".." -> File(currentDir).parent?.let { if (it.startsWith("/")) it else "/$it" } ?: "/"
                            arg.startsWith("/") -> arg
                            else -> if (currentDir.endsWith("/")) "$currentDir$arg" else "$currentDir/$arg"
                        }
                        val candidate = if (newRel == "/") root else File(root, newRel.trimStart('/'))
                        if (newRel == "/" || (candidate.exists() && candidate.isDirectory)) {
                            currentDir = if (newRel.startsWith("/")) newRel else "/$newRel"
                            send("250 Directory changed to $currentDir")
                        } else send("550 Directory not found")
                    }
                    "CDUP", "XCUP" -> {
                        currentDir = File(currentDir).parent?.let { if (it.startsWith("/")) it else "/$it" } ?: "/"
                        send("250 Directory changed to $currentDir")
                    }
                    "MKD", "XMKD" -> {
                        val d = resolve(arg)
                        send(if (d.mkdirs() || d.exists()) "257 \"/${d.name}\" created" else "550 Could not create")
                    }
                    "RMD", "XRMD" -> {
                        val d = resolve(arg)
                        send(if (d.exists() && d.isDirectory && d.delete()) "250 Removed" else "550 Could not remove")
                    }
                    "SITE" -> send("200 SITE acknowledged")
                    "ABOR" -> { pasvServer?.close(); pasvServer = null; activeDataSocket?.close(); activeDataSocket = null; send("226 Abort OK") }
                    "PORT" -> {
                        try {
                            val t = arg.split(",")
                            if (t.size == 6) {
                                activeDataSocket = Socket("${t[0]}.${t[1]}.${t[2]}.${t[3]}", t[4].trim().toInt() * 256 + t[5].trim().toInt())
                                send("200 PORT OK")
                            } else send("501 Syntax error")
                        } catch (e: Exception) { send("501 Invalid PORT: ${e.message}") }
                    }
                    "PASV" -> {
                        pasvServer?.close()
                        pasvServer = ServerSocket(0, 5, InetAddress.getByName("0.0.0.0"))
                        val pp = pasvServer?.localPort ?: 0
                        val ip = if (localIp.isBlank() || localIp == "0.0.0.0") PlatformNetwork.getLocalIpAddress() else localIp
                        send("227 Entering Passive Mode (${ip.replace(".", ",")},${pp / 256},${pp % 256})")
                    }
                    "EPSV" -> {
                        pasvServer?.close()
                        pasvServer = ServerSocket(0, 5, InetAddress.getByName("0.0.0.0"))
                        send("229 Entering Extended Passive Mode (|||${pasvServer?.localPort}|)")
                    }
                    "REST" -> { rangeOffset = arg.toLongOrNull() ?: 0L; send("350 Restarting at $rangeOffset") }
                    "SIZE" -> {
                        val f = resolve(arg)
                        send(if (f.exists() && f.isFile) "213 ${f.length()}" else "550 File not found")
                    }
                    "MDTM" -> {
                        val f = resolve(arg)
                        if (f.exists() && f.isFile) {
                            val sdf = java.text.SimpleDateFormat("yyyyMMddHHmmss", java.util.Locale.US)
                            sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                            send("213 ${sdf.format(java.util.Date(f.lastModified()))}")
                        } else send("550 File not found")
                    }
                    "STAT" -> send(if (arg.isEmpty()) "211-LinkShare FTP Status\r\n TYPE: $transferType\r\n211 End" else {
                        val f = resolve(arg); if (f.exists()) "213-${f.name}: ${f.length()}\r\n213 End" else "450 Not found"
                    })
                    "LIST", "MLSD" -> {
                        send("150 Opening data connection")
                        val ds = dataSocket()
                        if (ds != null) {
                            try {
                                ds.use { s ->
                                    val o = s.getOutputStream(); val td = targetDir()
                                    val now = System.currentTimeMillis()
                                    val r = java.text.SimpleDateFormat("MMM dd HH:mm", java.util.Locale.US)
                                    val old = java.text.SimpleDateFormat("MMM dd  yyyy", java.util.Locale.US)
                                    val sb = StringBuilder()
                                    (td.listFiles() ?: emptyArray()).forEach { f ->
                                        val sz = if (f.isDirectory) 0L else f.length()
                                        val dt = if ((now - f.lastModified()) < 180L * 24 * 3600000) r.format(java.util.Date(f.lastModified())) else old.format(java.util.Date(f.lastModified()))
                                        val tp = if (f.isDirectory) "d" else "-"
                                        val pm = if (f.isDirectory) "rwxr-xr-x" else "rw-r--r--"
                                        sb.append("$tp$pm   1 owner    group %13d $dt ${f.name}\r\n".format(sz))
                                    }
                                    o.write(sb.toString().toByteArray(Charsets.UTF_8)); o.flush()
                                }
                                send("226 Directory send OK")
                            } catch (_: Exception) { send("426 Transfer aborted") }
                        } else send("425 Can't open data connection")
                    }
                    "NLST" -> {
                        send("150 Opening data connection")
                        val ds = dataSocket()
                        if (ds != null) {
                            try { ds.use { s -> val o = s.getOutputStream(); (targetDir().listFiles() ?: emptyArray()).forEach { o.write("${it.name}\r\n".toByteArray()) }; o.flush() }; send("226 Transfer complete") }
                            catch (_: Exception) { send("426 Transfer aborted") }
                        } else send("425 Can't open data connection")
                    }
                    "RETR" -> {
                        val f = resolve(arg)
                        if (f.exists() && f.isFile) {
                            send("150 Opening BINARY mode data connection for ${f.name} (${f.length()} bytes)")
                            val ds = dataSocket()
                            if (ds != null) {
                                try { ds.use { s -> val o = s.getOutputStream(); val fis = FileInputStream(f); if (rangeOffset > 0) fis.skip(rangeOffset); val b = ByteArray(65536); var r: Int; while (fis.read(b).also { r = it } != -1) o.write(b, 0, r); fis.close(); o.flush() }; send("226 Transfer complete"); rangeOffset = 0 }
                                catch (_: Exception) { send("426 Transfer aborted") }
                            } else send("425 Can't open data connection")
                        } else send("550 File not found")
                    }
                    "STOR" -> {
                        val dest = resolve(arg)
                        send("150 Ready to receive: ${dest.name}")
                        val ds = dataSocket()
                        if (ds != null) {
                            try { ds.use { s -> val i = s.getInputStream(); val fos = FileOutputStream(dest); val b = ByteArray(65536); var r: Int; while (i.read(b).also { r = it } != -1) fos.write(b, 0, r); fos.flush(); fos.close() }; send("226 Transfer complete") }
                            catch (_: Exception) { send("426 Transfer aborted") }
                        } else send("425 Can't open data connection")
                    }
                    "APPE" -> {
                        val dest = resolve(arg)
                        send("150 Ready to append: ${dest.name}")
                        val ds = dataSocket()
                        if (ds != null) {
                            try { ds.use { s -> val i = s.getInputStream(); val fos = FileOutputStream(dest, true); val b = ByteArray(65536); var r: Int; while (i.read(b).also { r = it } != -1) fos.write(b, 0, r); fos.flush(); fos.close() }; send("226 Append complete") }
                            catch (_: Exception) { send("426 Transfer aborted") }
                        } else send("425 Can't open data connection")
                    }
                    "DELE" -> { val f = resolve(arg); send(if (f.exists() && f.delete()) "250 Deleted" else "550 Delete failed") }
                    "RNFR" -> send(if (resolve(arg).exists()) "350 Ready for destination name" else "550 Not found")
                    "RNTO" -> send("250 Rename successful")
                    "QUIT" -> { send("221 Goodbye"); break }
                    else -> send("502 Not implemented: $cmd")
                }
            }
        } catch (_: Exception) {} finally {
            pasvServer?.close(); activeDataSocket?.close()
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun generatePin(): String = "%04d".format(Random.nextInt(1000, 9999))
}
