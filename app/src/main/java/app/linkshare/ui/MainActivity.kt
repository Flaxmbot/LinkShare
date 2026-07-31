package app.linkshare.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import app.linkshare.core.discovery.WifiDirectDiscoveryManager
import app.linkshare.core.server.EmbeddedFtpServer
import app.linkshare.core.server.EmbeddedHttpServer
import app.linkshare.core.storage.RealFileManager
import app.linkshare.core.swarm.SwarmNetworkService
import app.linkshare.core.transport.HardwareCapabilityDetector
import app.linkshare.core.transport.LocationStateMonitor
import app.linkshare.core.transport.NetworkUtils
import app.linkshare.core.transport.WifiP2pConnectionManager
import app.linkshare.core.transport.WifiStateMonitor
import app.linkshare.model.AppSettings
import app.linkshare.model.DiscoveryTxtRecord
import app.linkshare.model.FileItem
import app.linkshare.model.PeerDevice
import app.linkshare.ui.navigation.LinkShareMainScreen
import app.linkshare.ui.theme.LinkShareTheme
import app.linkshare.ui.theme.NougatAmber
import app.linkshare.ui.theme.NougatSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var realFileManager: RealFileManager
    private lateinit var capabilityDetector: HardwareCapabilityDetector
    private lateinit var wifiStateMonitor: WifiStateMonitor
    private lateinit var locationStateMonitor: LocationStateMonitor
    private lateinit var settingsRepository: app.linkshare.data.AppSettingsRepository
    private var connectionManager: WifiP2pConnectionManager? = null
    private var discoveryManager: WifiDirectDiscoveryManager? = null
    private lateinit var swarmNetworkService: SwarmNetworkService
    private val ftpServer = EmbeddedFtpServer()
    private val httpServer = EmbeddedHttpServer()

    private val isFtpRunning = MutableStateFlow(false)
    private val isHttpRunning = MutableStateFlow(false)

    // State for Real File Selection & Recipient Swarm Selection
    private val selectedFileMetaState = MutableStateFlow<RealFileManager.SelectedFileMetaData?>(null)
    private val selectedPieceSizeState = MutableStateFlow(1024 * 1024) // 1MB default
    private val selectedPeerIdsState = MutableStateFlow<Set<String>>(emptySet())

    // File Manager state
    private val sharedFilesState = MutableStateFlow<List<FileItem>>(emptyList())

    private val openDocumentLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val meta = realFileManager.getFileMetaData(uri)
            if (meta != null) {
                selectedFileMetaState.value = meta
                Toast.makeText(this, "Selected: ${meta.fileName}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Unable to read file metadata", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // File picker for adding files to the share directory
    private val addFileToShareLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            lifecycleScope.launch {
                copyFileToShareDir(uri)
            }
        }
    }

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
            initServices()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        realFileManager = RealFileManager(this)
        capabilityDetector = HardwareCapabilityDetector(this)
        settingsRepository = app.linkshare.data.AppSettingsRepository(this)

        wifiStateMonitor = WifiStateMonitor(this)
        wifiStateMonitor.startMonitoring()

        locationStateMonitor = LocationStateMonitor(this)
        locationStateMonitor.startMonitoring()

        swarmNetworkService = SwarmNetworkService(realFileManager)

        checkAndRequestPermissions()

        // Initial file list scan
        refreshSharedFiles()

        setContent {
            LinkShareTheme {
                val settings by settingsRepository.settings.collectAsState()
                val ftpRunning by isFtpRunning.collectAsState()
                val httpRunning by isHttpRunning.collectAsState()
                val transferState by swarmNetworkService.transferState.collectAsState()
                val selectedFileMeta by selectedFileMetaState.collectAsState()
                val selectedPieceSize by selectedPieceSizeState.collectAsState()
                val selectedPeerIds by selectedPeerIdsState.collectAsState()
                val isWifiEnabled by wifiStateMonitor.isWifiEnabled.collectAsState()
                val isLocationEnabled by locationStateMonitor.isLocationEnabled.collectAsState()
                val sharedFiles by sharedFilesState.collectAsState()

                val discoveredPeers by discoveryManager?.discoveredPeers?.collectAsState(initial = emptyList())
                    ?: remember { MutableStateFlow<List<PeerDevice>>(emptyList()) }.collectAsState()

                val connectionState by connectionManager?.connectionState?.collectAsState()
                    ?: remember { MutableStateFlow<WifiP2pConnectionManager.ConnectionState>(WifiP2pConnectionManager.ConnectionState.Disconnected) }.collectAsState()

                // Start Swarm ServerSocket background listener on startup
                LaunchedEffect(Unit) {
                    swarmNetworkService.startServerSocket()
                }

                // Auto-connect client socket when Wi-Fi Direct P2P connects to Group Owner
                LaunchedEffect(connectionState) {
                    val state = connectionState
                    if (state is WifiP2pConnectionManager.ConnectionState.Connected) {
                        if (!state.isGroupOwner) {
                            val goIp = state.groupOwnerAddress.hostAddress ?: "192.168.49.1"
                            Log.d("MainActivity", "P2P Connected as Client -> Auto-connecting socket to GO at $goIp:8888")
                            swarmNetworkService.connectToPeerSocket("group_owner", goIp)
                        }
                    }
                }

                val activeIpList = remember(ftpRunning, httpRunning, connectionState, isWifiEnabled) {
                    NetworkUtils.getAllActiveIpAddresses()
                }

                val primaryIp = activeIpList.firstOrNull()?.ip ?: NetworkUtils.getLocalIpAddress()

                val httpQrBitmap = remember(httpRunning, httpServer.sessionPin, primaryIp) {
                    if (httpRunning) {
                        httpServer.generateQrCodeBitmap(httpServer.generateConnectionString(primaryIp))
                    } else null
                }

                Column {
                    // Warning Banner when WiFi Radio is OFF
                    if (!isWifiEnabled) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(NougatAmber)
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(imageVector = Icons.Default.WifiOff, contentDescription = null, tint = Color.Black)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "WiFi radio is OFF. Turn on WiFi to discover peers and run servers.",
                                    color = Color.Black,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Button(
                                onClick = {
                                    try {
                                        startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                                    } catch (_: Exception) {}
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text("Open WiFi", color = Color.White, fontSize = 11.sp)
                            }
                        }
                    }

                    // Warning Banner when Location GPS is OFF
                    if (!isLocationEnabled) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(NougatAmber)
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(imageVector = Icons.Default.LocationOff, contentDescription = null, tint = Color.Black)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Location (GPS) is OFF. Android requires Location for WiFi Direct discovery.",
                                    color = Color.Black,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Button(
                                onClick = {
                                    try {
                                        startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                                    } catch (_: Exception) {}
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text("Turn On GPS", color = Color.White, fontSize = 11.sp)
                            }
                        }
                    }

                    LinkShareMainScreen(
                        transferState = transferState,
                        connectionState = connectionState,
                        selectedFileMeta = selectedFileMeta,
                        selectedPieceSize = selectedPieceSize,
                        selectedPeerIds = selectedPeerIds,
                        discoveredPeers = discoveredPeers,
                        isFtpRunning = ftpRunning,
                        isHttpRunning = httpRunning,
                        activeIpList = activeIpList,
                        ftpPin = if (ftpRunning) ftpServer.sessionPin else null,
                        httpQrBitmap = httpQrBitmap,
                        appSettings = settings,
                        sharedFiles = sharedFiles,
                        onRefreshFiles = { refreshSharedFiles() },
                        onDeleteFile = { fileItem -> deleteSharedFile(fileItem) },
                        onShareFile = { fileItem -> shareFile(fileItem) },
                        onAddFile = { addFileToShareLauncher.launch("*/*") },
                        onSelectFileClicked = {
                            openDocumentLauncher.launch("*/*")
                        },
                        onClearSelectedFile = {
                            selectedFileMetaState.value = null
                        },
                        onPieceSizeSelected = { size ->
                            selectedPieceSizeState.value = size
                        },
                        onPeerToggleSelected = { peerId ->
                            val current = selectedPeerIdsState.value.toMutableSet()
                            if (current.contains(peerId)) {
                                current.remove(peerId)
                            } else {
                                current.add(peerId)
                            }
                            selectedPeerIdsState.value = current
                        },
                        onStartTransferClicked = {
                            val meta = selectedFileMetaState.value
                            if (meta != null) {
                                app.linkshare.service.TransferForegroundService.startService(this@MainActivity)
                                lifecycleScope.launch {
                                    val targets = selectedPeerIdsState.value.toList()
                                    swarmNetworkService.startSendingFile(meta.uri, targets)
                                }
                            } else {
                                Toast.makeText(this@MainActivity, "Please select a file first", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onCancelTransferClicked = {
                            swarmNetworkService.stop()
                            app.linkshare.service.TransferForegroundService.stopService(this@MainActivity)
                        },
                        onConnectPeerClicked = { peer ->
                            connectionManager?.connectToPeer(
                                deviceAddress = peer.id,
                                peerName = peer.name,
                                onSuccess = {
                                    Toast.makeText(this@MainActivity, "Connecting to ${peer.name}...", Toast.LENGTH_SHORT).show()
                                    peer.ipAddress?.let { ip ->
                                        swarmNetworkService.connectToPeerSocket(peer.id, ip)
                                    }
                                },
                                onFailure = { reason ->
                                    Toast.makeText(this@MainActivity, "Connection failed: code $reason", Toast.LENGTH_SHORT).show()
                                }
                            )
                        },
                        onStartServers = {
                            val shareFolder = getShareDirectory()
                            val sharedPin = String.format("%04d", kotlin.random.Random.nextInt(1000, 9999))
                            app.linkshare.service.TransferForegroundService.startService(this@MainActivity)
                            ftpServer.startServer(shareFolder, sharedPin)
                            httpServer.startServer(shareFolder, sharedPin)
                            isFtpRunning.value = true
                            isHttpRunning.value = true
                            refreshSharedFiles()
                            Toast.makeText(this@MainActivity, "Servers started", Toast.LENGTH_SHORT).show()
                        },
                        onStopServers = {
                            ftpServer.stopServer()
                            httpServer.stopServer()
                            isFtpRunning.value = false
                            isHttpRunning.value = false
                            app.linkshare.service.TransferForegroundService.stopService(this@MainActivity)
                            Toast.makeText(this@MainActivity, "Servers stopped", Toast.LENGTH_SHORT).show()
                        },
                        onCopyToClipboard = { text ->
                            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("LinkShare", text))
                            Toast.makeText(this@MainActivity, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        onOpenFileClicked = { fileName ->
                            openReceivedFile(fileName)
                        },
                        onOpenFolderClicked = {
                            openDownloadsFolder()
                        },
                        onSettingsChanged = { newSettings ->
                            settingsRepository.saveSettings(newSettings)
                            discoveryManager?.startDiscovery(
                                DiscoveryTxtRecord(
                                    deviceName = newSettings.deviceName,
                                    supportsF2 = newSettings.enableDualLinkF2,
                                    supportsF3 = newSettings.enableSwarmF3,
                                    ftpActive = isFtpRunning.value
                                )
                            )
                        },
                        onSaveRemoteFileToLocal = { ip, portStr, pin, remoteItem ->
                            downloadRemoteFileToLocal(ip, portStr.toIntOrNull() ?: 8080, pin, remoteItem)
                        }
                    )
                }
            }
        }
    }

    // ---------- File Manager Helpers ----------

    private fun getShareDirectory(): File {
        return realFileManager.getRootStorageDirectory()
    }

    private fun refreshSharedFiles() {
        lifecycleScope.launch {
            val files = withContext(Dispatchers.IO) {
                val dir = getShareDirectory()
                dir.listFiles()?.map { file ->
                    FileItem(
                        name = file.name,
                        sizeBytes = file.length(),
                        lastModified = file.lastModified(),
                        file = file
                    )
                }?.sortedByDescending { it.lastModified } ?: emptyList()
            }
            sharedFilesState.value = files
        }
    }

    private fun deleteSharedFile(fileItem: FileItem) {
        lifecycleScope.launch {
            val deleted = withContext(Dispatchers.IO) {
                fileItem.file.delete()
            }
            if (deleted) {
                Toast.makeText(this@MainActivity, "Deleted: ${fileItem.name}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MainActivity, "Failed to delete: ${fileItem.name}", Toast.LENGTH_SHORT).show()
            }
            refreshSharedFiles()
        }
    }

    private fun shareFile(fileItem: FileItem) {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", fileItem.file)
            val ext = fileItem.name.substringAfterLast('.', "").lowercase()
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share ${fileItem.name}"))
        } catch (e: Exception) {
            Toast.makeText(this, "Share failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun copyFileToShareDir(uri: Uri) {
        val meta = realFileManager.getFileMetaData(uri) ?: return
        withContext(Dispatchers.IO) {
            try {
                val destFile = File(getShareDirectory(), meta.fileName)
                contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output, 65536)
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Added: ${meta.fileName}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Failed to add file: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
        refreshSharedFiles()
    }

    // ---------- Existing Helpers ----------

    private fun openReceivedFile(fileName: String) {
        try {
            val file = realFileManager.getDownloadsTargetFile(fileName)
            if (!file.exists()) {
                Toast.makeText(this, "File not found: ${file.absolutePath}", Toast.LENGTH_SHORT).show()
                return
            }
            val ext = file.extension.lowercase()
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Open $fileName"))
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openDownloadsFolder() {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(realFileManager.getDownloadsTargetFile("").absolutePath), "*/*")
            }
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "Location: Downloads/LinkShare/", Toast.LENGTH_LONG).show()
        }
    }

    private fun downloadRemoteFileToLocal(ip: String, port: Int, pin: String, remoteItem: app.linkshare.core.client.RemoteDeviceClient.RemoteFileItem) {
        lifecycleScope.launch {
            Toast.makeText(this@MainActivity, "Downloading ${remoteItem.name}...", Toast.LENGTH_SHORT).show()
            val localFile = realFileManager.getDownloadsTargetFile(remoteItem.name)
            val client = app.linkshare.core.client.RemoteDeviceClient()
            val result = client.downloadRemoteFile(ip, port, pin, remoteItem.path, localFile)
            result.onSuccess {
                Toast.makeText(this@MainActivity, "Saved to Downloads/LinkShare/${remoteItem.name}", Toast.LENGTH_LONG).show()
                refreshSharedFiles()
            }.onFailure { err ->
                Toast.makeText(this@MainActivity, "Download failed: ${err.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissions.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            requestPermissionsLauncher.launch(missing.toTypedArray())
        } else {
            initServices()
        }

        // Request MANAGE_EXTERNAL_STORAGE for Android 11+ (API 30+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (_: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                } catch (_: Exception) {}
            }
        }
    }

    private fun initServices() {
        val wifiP2pManager = getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
        val channel = wifiP2pManager?.initialize(this, mainLooper, null)

        connectionManager = WifiP2pConnectionManager(wifiP2pManager, channel)
        swarmNetworkService.startServerSocket()

        val currentSettings = settingsRepository.settings.value
        discoveryManager = WifiDirectDiscoveryManager(this, wifiP2pManager, channel)
        discoveryManager?.startDiscovery(
            DiscoveryTxtRecord(
                deviceName = currentSettings.deviceName,
                supportsF2 = currentSettings.enableDualLinkF2,
                supportsF3 = currentSettings.enableSwarmF3
            )
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        wifiStateMonitor.stopMonitoring()
        locationStateMonitor.stopMonitoring()
        discoveryManager?.stopDiscovery()
        connectionManager?.disconnect()
        swarmNetworkService.stop()
        ftpServer.stopServer()
        httpServer.stopServer()
    }
}
