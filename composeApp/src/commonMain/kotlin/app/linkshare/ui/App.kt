package app.linkshare.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.linkshare.core.discovery.LanScanner
import app.linkshare.core.client.RemoteDeviceClient
import app.linkshare.core.swarm.SwarmTransferCoordinator
import app.linkshare.model.AppSettings
import app.linkshare.model.PeerDevice
import app.linkshare.model.HotspotInfo
import app.linkshare.model.TransferState
import app.linkshare.model.SharedAppInfo
import app.linkshare.model.currentTimeMillis
import app.linkshare.platform.PlatformFileSystem
import app.linkshare.platform.PlatformFtpServer
import app.linkshare.platform.PlatformHttpServer
import app.linkshare.platform.PlatformNetwork
import app.linkshare.platform.QrCode
import app.linkshare.ui.screens.DiscoveryScreen
import app.linkshare.ui.screens.LocalServerScreen
import app.linkshare.ui.screens.RemoteExplorerScreen
import app.linkshare.ui.screens.SettingsScreen
import app.linkshare.ui.screens.TransferScreen
import app.linkshare.ui.screens.AppSharingScreen
import app.linkshare.ui.theme.*
import kotlinx.coroutines.launch

enum class NavTab(val label: String, val icon: ImageVector) {
    Server("Home", Icons.Default.Home),
    Discovery("Nearby", Icons.Default.Radar),
    Transfer("Transfers", Icons.Default.SwapHoriz),
    Apps("Apps", Icons.Default.Android),
    Settings("Settings", Icons.Default.Settings)
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun App(
    httpServer: PlatformHttpServer,
    ftpServer: PlatformFtpServer,
    fileSystem: PlatformFileSystem,
    onDirectoryPick: () -> Unit,
    currentDirectory: String,
    onCopyAddress: (String) -> Unit = {},
    onSharingStarted: (String) -> Unit = {},
    onSharingStopped: () -> Unit = {},
    initialDeviceName: String = "LinkShare-Device",
    onDeviceNameChanged: (String) -> Unit = {},
    onStartHotspot: ((onReady: (HotspotInfo) -> Unit, onError: (String) -> Unit) -> Unit)? = null,
    incomingConnection: String? = null,
    onScanQrCode: (() -> Unit)? = null,
    onLoadApps: (suspend () -> List<SharedAppInfo>)? = null,
    onPrepareApp: (suspend (SharedAppInfo) -> String?)? = null,
    onInstallApk: ((String) -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    var currentMountedDir by remember(currentDirectory) { mutableStateOf(currentDirectory.ifBlank { "/storage/emulated/0" }) }

    LinkShareTheme {
        var selectedTab by remember { mutableStateOf(NavTab.Server) }
        var settings by remember { mutableStateOf(AppSettings(deviceName = initialDeviceName)) }
        val mountPoints = remember { fileSystem.getAvailableMountPoints() }
        LaunchedEffect(settings.deviceName) {
            httpServer.deviceName = settings.deviceName
        }
        var discoveredPeers by remember { mutableStateOf(listOf<PeerDevice>()) }
        var isSearching by remember { mutableStateOf(false) }

        var activeRemotePeer by remember { mutableStateOf<PeerDevice?>(null) }
        var incomingPin by remember { mutableStateOf<String?>(null) }
        var transferState by remember { mutableStateOf<TransferState>(TransferState.Idle) }
        val remoteClient = remember { RemoteDeviceClient() }
        val swarmCoordinator = remember { SwarmTransferCoordinator(remoteClient) }
        var showQuickConnect by remember { mutableStateOf(false) }
        var hotspotInfo by remember { mutableStateOf<HotspotInfo?>(null) }
        var hotspotError by remember { mutableStateOf<String?>(null) }
        val connectionAddresses = remember { PlatformNetwork.getAllActiveIpAddresses() }
        val quickConnectIp = connectionAddresses.firstOrNull {
            it.ip.startsWith("192.168.137.") ||
                it.label.contains("hotspot", ignoreCase = true) ||
                it.interfaceName.contains("vEthernet", ignoreCase = true)
        }?.ip ?: connectionAddresses.firstOrNull()?.ip ?: PlatformNetwork.getLocalIpAddress()
        val quickConnectUrl = "http://$quickConnectIp:8888?pin=${httpServer.sessionPin}"
        val activeQuickConnectUrl = hotspotInfo?.let {
            "http://${it.address}:8888?pin=${httpServer.sessionPin}"
        } ?: quickConnectUrl

        LaunchedEffect(incomingConnection) {
            val value = incomingConnection ?: return@LaunchedEffect
            val linkMatch = Regex("linkshare://connect\\?host=([^&]+).*?[?&]pin=([^&]+)").find(value)
            val webMatch = Regex("https?://([^:/?]+)(?::(\\d+))?.*?[?&]pin=([^&]+)").find(value)
            val match = linkMatch ?: webMatch
            if (match != null) {
                val host = if (linkMatch != null) linkMatch.groupValues[1] else match.groupValues[1]
                val port = if (linkMatch != null) {
                    Regex("[?&]port=(\\d+)").find(value)?.groupValues?.get(1)?.toIntOrNull() ?: 8888
                } else match.groupValues[2].toIntOrNull() ?: 8888
                incomingPin = if (linkMatch != null) linkMatch.groupValues[2] else match.groupValues[3]
                activeRemotePeer = PeerDevice("qr-$host", "LinkShare device", host, port)
            }
        }

        if (showQuickConnect) {
            QuickConnectDialog(
                connectionUrl = quickConnectUrl,
                hotspotInfo = hotspotInfo,
                hotspotError = hotspotError,
                onStartHotspot = if (onStartHotspot == null) null else {
                    {
                        hotspotError = null
                        onStartHotspot({ hotspotInfo = it }, { hotspotError = it })
                    }
                },
                onScanQrCode = onScanQrCode,
                onCopy = { onCopyAddress(activeQuickConnectUrl) },
                onDismiss = { showQuickConnect = false }
            )
        }

        // Automatically start fast 1-2 second scan when switching to Discovery tab
        fun triggerFastScan() {
            isSearching = true
            scope.launch {
                val peers = LanScanner.scanLocalNetwork()
                discoveredPeers = peers
                isSearching = false
            }
        }

        LaunchedEffect(selectedTab) {
            if (selectedTab == NavTab.Discovery) {
                triggerFastScan()
            }
        }

        Scaffold(
            topBar = {
                if (activeRemotePeer == null) {
                    CenterAlignedTopAppBar(
                        title = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("LinkShare", fontWeight = FontWeight.Bold)
                                Text("Private sharing on your network", fontSize = 10.sp, color = NougatTextMuted)
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = NougatBackground,
                            titleContentColor = Color.White
                        ),
                        actions = {
                            IconButton(onClick = { showQuickConnect = true }) {
                                Icon(Icons.Default.QrCode2, "Quick connect", tint = NougatTeal)
                            }
                        }
                    )
                }
            },
            bottomBar = {
                if (activeRemotePeer == null) {
                    NavigationBar(
                        containerColor = NougatSurface,
                        tonalElevation = 4.dp
                    ) {
                        NavTab.entries.forEach { tab ->
                            val isSelected = selectedTab == tab
                            NavigationBarItem(
                                icon = { Icon(tab.icon, tab.label, tint = if (isSelected) NougatTeal else NougatTextMuted) },
                                label = {
                                    Text(
                                        tab.label,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) NougatTeal else NougatTextMuted
                                    )
                                },
                                selected = isSelected,
                                onClick = { selectedTab = tab },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = NougatTeal,
                                    selectedTextColor = NougatTeal,
                                    unselectedIconColor = NougatTextMuted,
                                    unselectedTextColor = NougatTextMuted,
                                    indicatorColor = NougatTeal.copy(alpha = 0.15f)
                                )
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NougatBackground)
                    .padding(padding)
            ) {
                if (activeRemotePeer != null) {
                    val peer = activeRemotePeer!!
                    RemoteExplorerScreen(
                        peerName = peer.name,
                        peerIp = peer.ipAddress ?: "127.0.0.1",
                        peerPort = peer.port,
                        initialPin = incomingPin,
                        onBackClicked = { activeRemotePeer = null },
                        onSaveFileToLocal = { pin, remoteItem ->
                            transferState = TransferState.Connecting(1, remoteItem.name)
                            selectedTab = NavTab.Transfer
                            activeRemotePeer = null
                            scope.launch {
                                val startedAt = currentTimeMillis()
                                val result = if (remoteItem.sizeBytes >= 1024L * 1024L) {
                                    swarmCoordinator.download(
                                        peers = listOf(peer),
                                        pin = pin,
                                        remotePath = remoteItem.path,
                                        destinationDirectory = currentMountedDir
                                    )
                                } else {
                                    remoteClient.downloadFile(
                                        ip = peer.ipAddress ?: return@launch,
                                        port = peer.port,
                                        pin = pin,
                                        remotePath = remoteItem.path,
                                        destinationDirectory = currentMountedDir
                                    )
                                }
                                result.onSuccess { file ->
                                    val elapsed = (currentTimeMillis() - startedAt).coerceAtLeast(1L)
                                    transferState = TransferState.Completed(
                                        fileName = file.name,
                                        totalBytes = file.length(),
                                        elapsedTimeMs = elapsed,
                                        averageSpeedBytesPerSec = file.length() * 1000L / elapsed
                                    )
                                    if (file.name.endsWith(".apk", ignoreCase = true)) onInstallApk?.invoke(file.absolutePath)
                                }.onFailure { error ->
                                    transferState = TransferState.Failed(remoteItem.name, error.message ?: "Download failed")
                                }
                            }
                        }
                    )
                } else {
                    when (selectedTab) {
                        NavTab.Server -> LocalServerScreen(
                            httpServer = httpServer,
                            ftpServer = ftpServer,
                            onDirectoryPick = onDirectoryPick,
                            onSetMountedDirectory = { newDir -> currentMountedDir = newDir },
                            currentDirectory = currentMountedDir,
                            onCopyAddress = onCopyAddress,
                            mountPoints = mountPoints,
                            onSharingStarted = onSharingStarted,
                            onSharingStopped = onSharingStopped
                        )
                        NavTab.Discovery -> DiscoveryScreen(
                            discoveredPeers = discoveredPeers,
                            isSearching = isSearching,
                            onStartScan = { triggerFastScan() },
                            onBrowsePeerFilesClicked = { peer ->
                                activeRemotePeer = peer
                            }
                        )
                        NavTab.Transfer -> TransferScreen(
                            transferState = transferState,
                            onCancelTransfer = { transferState = TransferState.Idle }
                        )
                        NavTab.Apps -> AppSharingScreen(
                            onLoadApps = onLoadApps,
                            onPrepareApp = onPrepareApp,
                            onInstallApk = onInstallApk
                        )
                        NavTab.Settings -> SettingsScreen(
                            settings = settings,
                            onSettingsChange = { settings = it; onDeviceNameChanged(it.deviceName) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickConnectDialog(
    connectionUrl: String,
    hotspotInfo: HotspotInfo?,
    hotspotError: String?,
    onStartHotspot: (() -> Unit)?,
    onScanQrCode: (() -> Unit)?,
    onCopy: () -> Unit,
    onDismiss: () -> Unit
) {
    val qrPayload = hotspotInfo?.let {
        "linkshare://connect?host=${it.address}&port=8888&pin=${connectionUrl.substringAfter("pin=")}&ssid=${it.ssid}&password=${it.password}"
    } ?: connectionUrl
    val matrix = remember(qrPayload) { QrCode.encode(qrPayload) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NougatSurface,
        title = { Text("Quick connect", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (hotspotInfo == null) "Scan this LinkShare code on the other device to open your shared folder."
                    else "Scan this LinkShare code on the other device. It includes the hotspot session and PIN.",
                    color = NougatTextSecondary,
                    fontSize = 13.sp
                )
                if (onStartHotspot != null && hotspotInfo == null) {
                    OutlinedButton(onClick = onStartHotspot) { Text("Create LinkShare hotspot") }
                }
                if (onScanQrCode != null) {
                    OutlinedButton(onClick = onScanQrCode) { Text("Scan a connection QR") }
                }
                if (hotspotInfo != null) {
                    Text("Wi‑Fi: ${hotspotInfo.ssid}", color = NougatTealLight, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 12.sp)
                    Text("Password: ${hotspotInfo.password}", color = NougatTealLight, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 12.sp)
                }
                if (hotspotError != null) Text(hotspotError, color = NougatRed, fontSize = 12.sp)
                if (matrix != null) {
                    Canvas(Modifier.size(220.dp).background(Color.White)) {
                        val cell = size.width / matrix.first().size
                        matrix.forEachIndexed { y, row ->
                            row.forEachIndexed { x, filled ->
                                if (filled) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell, cell))
                            }
                        }
                    }
                }
                Text(if (hotspotInfo == null) connectionUrl else "http://${hotspotInfo.address}:8888?pin=${connectionUrl.substringAfter("pin=")}", color = NougatTealLight, fontSize = 11.sp)
            }
        },
        confirmButton = { Button(onClick = onCopy) { Text("Copy link") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
