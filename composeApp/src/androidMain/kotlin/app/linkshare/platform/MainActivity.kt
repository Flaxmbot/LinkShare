package app.linkshare.platform

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.documentfile.provider.DocumentFile
import app.linkshare.core.storage.AppSharingManager
import app.linkshare.ui.App

class MainActivity : ComponentActivity() {
    private val httpServer = PlatformHttpServer(8888)
    private val ftpServer = PlatformFtpServer(2121, 10)
    private val fileSystem = PlatformFileSystem()
    lateinit var appSharingManager: AppSharingManager
        private set

    private var currentDirectory by mutableStateOf("/storage/emulated/0")

    private val directoryPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {}
            val docFile = DocumentFile.fromTreeUri(this, uri)
            val path = getPathFromUri(uri) ?: docFile?.uri?.path ?: "/storage/emulated/0"
            if (path.isNotBlank()) {
                currentDirectory = path
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appSharingManager = AppSharingManager(this)

        // Request ALL FILES ACCESS on Android 11+ (API 30+)
        requestAllFilesAccess()

        val primaryStorage = "/storage/emulated/0"
        if (java.io.File(primaryStorage).exists() && java.io.File(primaryStorage).canRead()) {
            currentDirectory = primaryStorage
        } else {
            val defaultDir = java.io.File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "LinkShare"
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
                currentDirectory = currentDirectory,
                onCopyAddress = { text ->
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("LinkShare", text))
                }
            )
        }
    }

    override fun onDestroy() {
        httpServer.stopServer()
        ftpServer.stopServer()
        super.onDestroy()
    }

    private fun requestAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (_: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                }
            }
        }
    }

    private fun getPathFromUri(uri: Uri): String? {
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
