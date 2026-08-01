package app.linkshare.platform

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.linkshare.ui.App
import javax.swing.JFileChooser

fun main() = application {
    val httpServer = remember { PlatformHttpServer(8888) }
    val ftpServer = remember { PlatformFtpServer(2121, 10) }
    val fileSystem = remember { PlatformFileSystem() }
    var currentDirectory by remember { mutableStateOf(fileSystem.getDefaultShareDirectory()) }

    Window(
        onCloseRequest = {
            httpServer.stopServer()
            ftpServer.stopServer()
            exitApplication()
        },
        title = "LinkShare",
        state = rememberWindowState(
            width = 420.dp,
            height = 780.dp,
            position = WindowPosition(Alignment.Center)
        )
    ) {
        App(
            httpServer = httpServer,
            ftpServer = ftpServer,
            fileSystem = fileSystem,
            onDirectoryPick = {
                val chooser = JFileChooser(currentDirectory)
                chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                chooser.dialogTitle = "Select Shared Directory"
                val result = chooser.showOpenDialog(null)
                if (result == JFileChooser.APPROVE_OPTION) {
                    currentDirectory = chooser.selectedFile.absolutePath
                }
            },
            currentDirectory = currentDirectory,
            appsEnabled = false
        )
    }
}
