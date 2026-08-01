package app.linkshare.platform

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Environment
import android.net.wifi.WifiManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.linkshare.core.storage.AppSharingManager
import app.linkshare.ui.App
import app.linkshare.model.HotspotInfo
import app.linkshare.model.SharedAppInfo
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class MainActivity : ComponentActivity() {
    private val httpServer = PlatformHttpServer(8888)
    private val ftpServer = PlatformFtpServer(2121, 0)
    private val fileSystem = PlatformFileSystem()
    lateinit var appSharingManager: AppSharingManager
        private set

    private var currentDirectory by mutableStateOf("/storage/emulated/0")
    private var hotspotReservation: WifiManager.LocalOnlyHotspotReservation? = null
    private var scannedConnection by mutableStateOf<String?>(null)

    private val qrScanner = registerForActivityResult(ScanContract()) { result ->
        if (!result.contents.isNullOrBlank()) scannedConnection = result.contents
    }
    private var pendingHotspotReady: ((HotspotInfo) -> Unit)? = null
    private var pendingHotspotError: ((String) -> Unit)? = null

    private val hotspotPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants.values.all { it }
        val onReady = pendingHotspotReady
        val onError = pendingHotspotError
        pendingHotspotReady = null
        pendingHotspotError = null
        if (granted && onReady != null && onError != null) {
            startLocalShareHotspotInternal(onReady, onError)
        } else {
            onError?.invoke("Nearby Wi-Fi permission is required to create a hotspot")
        }
    }

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
        scannedConnection = intent?.dataString
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
                onSharingStopped = { stopSharingService() },
                initialDeviceName = getPreferences(MODE_PRIVATE).getString("device_name", "LinkShare-Device") ?: "LinkShare-Device",
                onDeviceNameChanged = { name -> getPreferences(MODE_PRIVATE).edit().putString("device_name", name).apply() },
                onStartHotspot = { onReady, onError -> startLocalShareHotspot(onReady, onError) }
                ,incomingConnection = scannedConnection,
                onScanQrCode = {
                    qrScanner.launch(ScanOptions().apply {
                        setPrompt("Scan the LinkShare connection QR code")
                        setBeepEnabled(false)
                        setOrientationLocked(false)
                    })
                },
                onLoadApps = {
                    appSharingManager.getInstalledApps().map { SharedAppInfo(it.appName, it.packageName, it.sizeBytes, it.isSystemApp) }
                },
                onPrepareApp = { app ->
                    val installed = appSharingManager.getInstalledApps().firstOrNull { it.packageName == app.packageName }
                    installed?.let { appSharingManager.extractAppApk(it, currentDirectory)?.path }
                },
                onInstallApk = { path -> installApk(path) }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        scannedConnection = intent?.dataString
    }

    override fun onDestroy() {
        httpServer.stopServer()
        ftpServer.stopServer()
        stopSharingService()
        hotspotReservation?.close()
        hotspotReservation = null
        super.onDestroy()
    }

    private fun startLocalShareHotspot(onReady: (HotspotInfo) -> Unit, onError: (String) -> Unit) {
        val required = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        val missing = required.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) {
            pendingHotspotReady = onReady
            pendingHotspotError = onError
            hotspotPermissionLauncher.launch(missing.toTypedArray())
            return
        }
        startLocalShareHotspotInternal(onReady, onError)
    }

    private fun startLocalShareHotspotInternal(onReady: (HotspotInfo) -> Unit, onError: (String) -> Unit) {
        if (!httpServer.isServerActive()) httpServer.startServer(currentDirectory)
        if (!ftpServer.isServerActive()) ftpServer.startServer(currentDirectory)
        startSharingService()
        val wifiManager = getSystemService(WIFI_SERVICE) as? WifiManager
            ?: return onError("Wi‑Fi is not available on this device")
        hotspotReservation?.let {
            val address = PlatformNetwork.getLocalIpAddress()
            val config = it.wifiConfiguration
            onReady(HotspotInfo(config?.SSID.orEmpty().trim('"'), config?.preSharedKey.orEmpty(), address))
            return
        }
        try {
            wifiManager.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation) {
                    hotspotReservation = reservation
                    val config = reservation.wifiConfiguration
                    val address = PlatformNetwork.getAllActiveIpAddresses()
                        .firstOrNull { info -> info.label.contains("hotspot", true) || info.ip.startsWith("192.168.43.") }
                        ?.ip ?: PlatformNetwork.getLocalIpAddress()
                    onReady(HotspotInfo(config?.SSID.orEmpty().trim('"'), config?.preSharedKey.orEmpty(), address))
                }

                override fun onStopped() {
                    hotspotReservation = null
                    onError("The LinkShare hotspot was stopped by Android")
                }

                override fun onFailed(reason: Int) {
                    onError("Unable to start hotspot (Android error $reason). Turn on Wi‑Fi and try again.")
                }
            }, Handler(Looper.getMainLooper()))
        } catch (e: SecurityException) {
            onError("Allow Nearby Wi‑Fi and Location permissions before creating a hotspot")
        } catch (e: Exception) {
            onError(e.message ?: "Unable to start hotspot")
        }
    }

    private fun startSharingService() {
        val intent = Intent(this, LinkShareForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
    }

    private fun stopSharingService() {
        stopService(Intent(this, LinkShareForegroundService::class.java))
    }

    private fun installApk(path: String) {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", java.io.File(path))
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
        }
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
