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
import app.linkshare.model.AppSettings
import app.linkshare.model.PeerDevice
import app.linkshare.model.TransferState
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
import app.linkshare.ui.theme.*
import kotlinx.coroutines.launch

enum class NavTab(val label: String, val icon: ImageVector) {
    Server("Home", Icons.Default.Home),
    Discovery("Nearby", Icons.Default.Radar),
    Transfer("Transfers", Icons.Default.SwapHoriz),
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
    onSharingStopped: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var currentMountedDir by remember(currentDirectory) { mutableStateOf(currentDirectory.ifBlank { "/storage/emulated/0" }) }

    LinkShareTheme {
        var selectedTab by remember { mutableStateOf(NavTab.Server) }
        var settings by remember { mutableStateOf(AppSettings()) }
        val mountPoints = remember { fileSystem.getAvailableMountPoints() }
        var discoveredPeers by remember { mutableStateOf(listOf<PeerDevice>()) }
        var isSearching by remember { mutableStateOf(false) }

        var activeRemotePeer by remember { mutableStateOf<PeerDevice?>(null) }
        var transferState by remember { mutableStateOf<TransferState>(TransferState.Idle) }
        val remoteClient = remember { RemoteDeviceClient() }
        var showQuickConnect by remember { mutableStateOf(false) }
        val quickConnectUrl = "http://${PlatformNetwork.getLocalIpAddress()}:8888?pin=${httpServer.sessionPin}"

        if (showQuickConnect) {
            QuickConnectDialog(
                connectionUrl = quickConnectUrl,
                onCopy = { onCopyAddress(quickConnectUrl) },
                onDismiss = { showQuickConnect = false }
            )
        }

        // Pending file transfer confirmation prompt
        var pendingTransferPeer by remember { mutableStateOf<PeerDevice?>(null) }

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

        // File Transfer Request Confirmation Prompt
        if (pendingTransferPeer != null) {
            val peer = pendingTransferPeer!!
            AlertDialog(
                onDismissRequest = { pendingTransferPeer = null },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.SwapHoriz, contentDescription = null, tint = NougatTeal, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Connect to ${peer.name}", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Open this device to browse shared files and download them to your device.",
                            fontSize = 12.sp,
                            color = NougatTextSecondary
                        )
                        Text(
                            text = "Destination: $currentMountedDir",
                            fontSize = 11.sp,
                            color = NougatTealLight
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            activeRemotePeer = peer
                            pendingTransferPeer = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NougatTeal),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("BROWSE FILES", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingTransferPeer = null }) {
                        Text("CANCEL", color = NougatTextSecondary)
                    }
                },
                containerColor = NougatSurface,
                shape = RoundedCornerShape(4.dp)
            )
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
                        onBackClicked = { activeRemotePeer = null },
                        onSaveFileToLocal = { pin, remoteItem ->
                            transferState = TransferState.Connecting(1, remoteItem.name)
                            selectedTab = NavTab.Transfer
                            activeRemotePeer = null
                            scope.launch {
                                val startedAt = currentTimeMillis()
                                remoteClient.downloadFile(
                                    ip = peer.ipAddress ?: return@launch,
                                    port = peer.port,
                                    pin = pin,
                                    remotePath = remoteItem.path,
                                    destinationDirectory = currentMountedDir
                                ).onSuccess { file ->
                                    val elapsed = (currentTimeMillis() - startedAt).coerceAtLeast(1L)
                                    transferState = TransferState.Completed(
                                        fileName = file.name,
                                        totalBytes = file.length(),
                                        elapsedTimeMs = elapsed,
                                        averageSpeedBytesPerSec = file.length() * 1000L / elapsed
                                    )
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
                            onConnectPeerClicked = { peer ->
                                pendingTransferPeer = peer
                            },
                            onBrowsePeerFilesClicked = { peer ->
                                activeRemotePeer = peer
                            },
                            onConnectByIp = { ip, port ->
                                scope.launch {
                                    val probedPeer = LanScanner.probePeer(ip, port)
                                    val targetPeer = probedPeer ?: PeerDevice(
                                        id = "peer_$ip",
                                        name = "LinkShare Peer ($ip)",
                                        ipAddress = ip,
                                        port = port
                                    )
                                    activeRemotePeer = targetPeer
                                }
                            }
                        )
                        NavTab.Transfer -> TransferScreen(
                            transferState = transferState,
                            onCancelTransfer = { transferState = TransferState.Idle }
                        )
                        NavTab.Settings -> SettingsScreen(
                            settings = settings,
                            onSettingsChange = { settings = it }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickConnectDialog(connectionUrl: String, onCopy: () -> Unit, onDismiss: () -> Unit) {
    val matrix = remember(connectionUrl) { QrCode.encode(connectionUrl) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NougatSurface,
        title = { Text("Quick connect", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Scan this code on the other device to open your shared folder.", color = NougatTextSecondary, fontSize = 13.sp)
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
                Text(connectionUrl, color = NougatTealLight, fontSize = 11.sp)
            }
        },
        confirmButton = { Button(onClick = onCopy) { Text("Copy link") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
