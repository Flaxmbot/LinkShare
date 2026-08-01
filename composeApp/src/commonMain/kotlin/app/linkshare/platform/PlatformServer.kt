package app.linkshare.platform

/**
 * Platform-specific server abstraction.
 * On JVM (Android + Desktop): uses java.net.ServerSocket
 * On iOS: uses POSIX sockets via Kotlin/Native
 */
expect class PlatformHttpServer(port: Int) {
    var sessionPin: String
        private set
    var enterpriseToken: String
        private set
    var activeClipboardText: String

    fun startServer(shareDir: String, customPin: String? = null, timeoutMinutes: Int = 15, maxSpeedMbps: Int = 0)
    fun stopServer()
    fun isServerActive(): Boolean
    fun generateConnectionString(ipAddress: String): String
}

expect class PlatformFtpServer(port: Int, timeoutMinutes: Int) {
    var sessionPin: String
        private set

    fun startServer(shareDir: String, customPin: String? = null)
    fun stopServer()
    fun isServerActive(): Boolean
}
