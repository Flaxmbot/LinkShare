package app.linkshare.platform

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.documentfile.provider.DocumentFile
import app.linkshare.ui.App

class MainActivity : ComponentActivity() {
    private val httpServer = PlatformHttpServer(8888)
    private val ftpServer = PlatformFtpServer(2121, 10)
    private val fileSystem = PlatformFileSystem()

    private var currentDirectory by mutableStateOf("")

    private val directoryPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            // Convert content URI to real path
            val docFile = DocumentFile.fromTreeUri(this, uri)
            val path = getPathFromUri(uri) ?: docFile?.uri?.path ?: ""
            if (path.isNotBlank()) {
                currentDirectory = path
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Default to Downloads/LinkShare
        if (currentDirectory.isBlank()) {
            val defaultDir = java.io.File(
                android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS
                ), "LinkShare"
            )
            if (!defaultDir.exists()) defaultDir.mkdirs()
            currentDirectory = defaultDir.absolutePath
        }

        setContent {
            App(
                httpServer = httpServer,
                ftpServer = ftpServer,
                fileSystem = fileSystem,
                onDirectoryPick = { directoryPicker.launch(null) },
                currentDirectory = currentDirectory
            )
        }
    }

    override fun onDestroy() {
        httpServer.stopServer()
        ftpServer.stopServer()
        super.onDestroy()
    }

    private fun getPathFromUri(uri: Uri): String? {
        // Try to extract filesystem path from SAF URI
        return try {
            val docId = uri.lastPathSegment ?: return null
            if (docId.contains(":")) {
                val parts = docId.split(":")
                val root = parts[0]
                val path = if (parts.size > 1) parts[1] else ""
                when (root) {
                    "primary" -> "/storage/emulated/0/$path"
                    else -> "/storage/$root/$path"
                }
            } else null
        } catch (_: Exception) { null }
    }
}
