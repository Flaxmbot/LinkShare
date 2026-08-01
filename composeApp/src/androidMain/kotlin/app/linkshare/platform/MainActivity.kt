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
            // The HTTP/FTP servers require a real filesystem path. A content:// URI
            // cannot be written with java.io.File, so never pass its URI path through.
            val path = getPathFromUri(uri)
            if (!path.isNullOrBlank() && java.io.File(path).isDirectory) {
                currentDirectory = path
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appSharingManager = AppSharingManager(this)

        // Request ALL FILES ACCESS on Android 11+ (API 30+)
        requestAllFilesAccess()

        val detected = fileSystem.getAvailableMountPoints().firstOrNull()
        currentDirectory = detected ?: fileSystem.getDefaultShareDirectory()

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
                },
                onSharingStarted = { startSharingService() },
                onSharingStopped = { stopSharingService() }
            )
        }
    }

    override fun onDestroy() {
        httpServer.stopServer()
        ftpServer.stopServer()
        stopSharingService()
        super.onDestroy()
    }

    private fun startSharingService() {
        val intent = Intent(this, LinkShareForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
    }

    private fun stopSharingService() {
        stopService(Intent(this, LinkShareForegroundService::class.java))
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
