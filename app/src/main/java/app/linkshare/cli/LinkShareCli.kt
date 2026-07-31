package app.linkshare.cli

import app.linkshare.core.server.EmbeddedFtpServer
import app.linkshare.core.server.EmbeddedHttpServer
import java.io.File
import kotlin.system.exitProcess

/**
 * Headless CLI Runner for LinkShare Servers on Home Servers, NAS, or Android Terminal environments.
 * Usage: LinkShareCli --port 8080 --ftp-port 2121 --dir /storage/emulated/0
 */
class LinkShareCli {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            var httpPort = 8080
            var ftpPort = 2121
            var sharePath = "/storage/emulated/0"
            var customPin: String? = null
            var timeoutMins = 0 // 0 = Never

            var i = 0
            while (i < args.size) {
                when (args[i]) {
                    "--port", "-p" -> httpPort = args.getOrNull(i + 1)?.toIntOrNull() ?: 8080
                    "--ftp-port" -> ftpPort = args.getOrNull(i + 1)?.toIntOrNull() ?: 2121
                    "--dir", "-d" -> sharePath = args.getOrNull(i + 1) ?: sharePath
                    "--pin" -> customPin = args.getOrNull(i + 1)
                    "--timeout" -> timeoutMins = args.getOrNull(i + 1)?.toIntOrNull() ?: 0
                    "--help", "-h" -> {
                        printUsage()
                        exitProcess(0)
                    }
                }
                i += 2
            }

            val shareDir = File(sharePath)
            if (!shareDir.exists()) shareDir.mkdirs()

            println("====================================================")
            println("            LinkShare Headless CLI Daemon           ")
            println("====================================================")
            println("Root Share Directory: ${shareDir.absolutePath}")
            println("HTTP Web Explorer:    http://0.0.0.0:$httpPort")
            println("FTP Server:           ftp://0.0.0.0:$ftpPort")

            val ftpServer = EmbeddedFtpServer(port = ftpPort)
            val httpServer = EmbeddedHttpServer(port = httpPort)

            ftpServer.startServer(shareDir, customPin)
            httpServer.startServer(shareDir, customPin, timeoutMins)

            println("Security PIN:         ${httpServer.sessionPin}")
            println("Status:               RUNNING (Press Ctrl+C to stop)")
            println("====================================================")

            Runtime.getRuntime().addShutdownHook(Thread {
                println("\nShutting down LinkShare CLI servers...")
                ftpServer.stopServer()
                httpServer.stopServer()
            })

            while (true) {
                Thread.sleep(5000)
            }
        }

        private fun printUsage() {
            println("LinkShare CLI Runner")
            println("Options:")
            println("  --port, -p <port>       HTTP Server Port (default: 8080)")
            println("  --ftp-port <port>       FTP Server Port (default: 2121)")
            println("  --dir, -d <path>        Root Storage Path (default: /storage/emulated/0)")
            println("  --pin <pin>             Custom 4-digit security PIN")
            println("  --timeout <minutes>     Idle server timeout in minutes (default: 0 = Never)")
        }
    }
}
