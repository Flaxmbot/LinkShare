package app.linkshare.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.linkshare.core.discovery.LanScanner
import app.linkshare.model.AppSettings
import app.linkshare.model.PeerDevice
import app.linkshare.model.TransferState
import app.linkshare.platform.PlatformFileSystem
import app.linkshare.platform.PlatformFtpServer
import app.linkshare.platform.PlatformHttpServer
import app.linkshare.ui.screens.DiscoveryScreen
import app.linkshare.ui.screens.LocalServerScreen
import app.linkshare.ui.screens.RemoteExplorerScreen
import app.linkshare.ui.screens.SettingsScreen
import app.linkshare.ui.screens.TransferScreen
import app.linkshare.ui.theme.*
import kotlinx.coroutines.launch

enum class NavTab(val label: String, val icon: ImageVector) {
    Server("SERVER", Icons.Default.Dns),
    Discovery("DISCOVER", Icons.Default.Radar),
    Transfer("TRANSFERS", Icons.Default.SwapHoriz),
    Settings("SETTINGS", Icons.Default.Settings)
}

@Composable
fun App(
    httpServer: PlatformHttpServer,
    ftpServer: PlatformFtpServer,
    fileSystem: PlatformFileSystem,
    onDirectoryPick: () -> Unit,
    currentDirectory: String,
    onCopyAddress: (String) -> Unit = {}
) {
    val scope = rememberCoroutineScope()

    LinkShareTheme {
        var selectedTab by remember { mutableStateOf(NavTab.Server) }
        var settings by remember { mutableStateOf(AppSettings()) }
        var discoveredPeers by remember { mutableStateOf(listOf<PeerDevice>()) }
        var isSearching by remember { mutableStateOf(false) }

        var activeRemotePeer by remember { mutableStateOf<PeerDevice?>(null) }
        var transferState by remember { mutableStateOf<TransferState>(TransferState.Idle) }

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
                        Text("Incoming File Transfer Request", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "${peer.name} (${peer.ipAddress}) wants to send files to your device over LAN.",
                            fontSize = 12.sp,
                            color = NougatTextSecondary
                        )
                        Text(
                            text = "Destination: $currentDirectory",
                            fontSize = 11.sp,
                            color = NougatTealLight
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            transferState = TransferState.Connecting(1, peer.name)
                            selectedTab = NavTab.Transfer
                            pendingTransferPeer = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NougatTeal),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("ACCEPT & RECEIVE", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingTransferPeer = null }) {
                        Text("DECLINE", color = NougatRed)
                    }
                },
                containerColor = NougatSurface,
                shape = RoundedCornerShape(4.dp)
            )
        }

        Scaffold(
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
                        }
                    )
                } else {
                    when (selectedTab) {
                        NavTab.Server -> LocalServerScreen(
                            httpServer = httpServer,
                            ftpServer = ftpServer,
                            onDirectoryPick = onDirectoryPick,
                            currentDirectory = currentDirectory,
                            onCopyAddress = onCopyAddress
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
