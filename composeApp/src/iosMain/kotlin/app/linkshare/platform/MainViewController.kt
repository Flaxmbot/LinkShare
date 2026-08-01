package app.linkshare.platform

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController

fun MainViewController() = ComposeUIViewController {
    val httpServer = remember { PlatformHttpServer(8888) }
    val ftpServer = remember { PlatformFtpServer(2121, 10) }
    val fileSystem = remember { PlatformFileSystem() }
    val currentDirectory = remember { fileSystem.getDefaultShareDirectory() }

    App(
        httpServer = httpServer,
        ftpServer = ftpServer,
        fileSystem = fileSystem,
        onDirectoryPick = { /* iOS document picker integration */ },
        currentDirectory = currentDirectory,
        appsEnabled = false
    )
}
