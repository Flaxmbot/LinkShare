package app.linkshare.ui.navigation

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.linkshare.core.client.RemoteDeviceClient
import app.linkshare.core.storage.RealFileManager
import app.linkshare.core.transport.NetworkUtils
import app.linkshare.core.transport.WifiP2pConnectionManager
import app.linkshare.model.AppSettings
import app.linkshare.model.FileItem
import app.linkshare.model.PeerDevice
import app.linkshare.model.TransferState
import app.linkshare.ui.screens.DiscoveryScreen
import app.linkshare.ui.screens.LocalServerScreen
import app.linkshare.ui.screens.RemoteExplorerScreen
import app.linkshare.ui.screens.SettingsScreen
import app.linkshare.ui.screens.TransferScreen
import app.linkshare.ui.theme.NougatSurface
import app.linkshare.ui.theme.NougatTeal
import app.linkshare.ui.theme.NougatTextMuted
import kotlinx.coroutines.launch

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Transfer : Screen("transfer", "Transfer", Icons.AutoMirrored.Filled.Send)
    object Discovery : Screen("discovery", "Discovery", Icons.Default.Radar)
    object Server : Screen("server", "Server", Icons.Default.FolderShared)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

data class RemoteTarget(
    val name: String,
    val ip: String,
    val port: Int = 8080,
    val initialPin: String = ""
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LinkShareMainScreen(
    transferState: TransferState,
    connectionState: WifiP2pConnectionManager.ConnectionState,
    selectedFileMeta: RealFileManager.SelectedFileMetaData?,
    selectedPieceSize: Int,
    selectedPeerIds: Set<String>,
    discoveredPeers: List<PeerDevice>,
    isFtpRunning: Boolean,
    isHttpRunning: Boolean,
    activeIpList: List<NetworkUtils.IpInfo>,
    ftpPin: String?,
    httpQrBitmap: Bitmap?,
    appSettings: AppSettings,
    // File manager
    sharedFiles: List<FileItem>,
    onRefreshFiles: () -> Unit,
    onDeleteFile: (FileItem) -> Unit,
    onShareFile: (FileItem) -> Unit,
    onAddFile: () -> Unit,
    // Callbacks
    onSelectFileClicked: () -> Unit,
    onClearSelectedFile: () -> Unit,
    onPieceSizeSelected: (Int) -> Unit,
    onPeerToggleSelected: (String) -> Unit,
    onStartTransferClicked: () -> Unit,
    onCancelTransferClicked: () -> Unit,
    onConnectPeerClicked: (PeerDevice) -> Unit,
    onStartServers: () -> Unit,
    onStopServers: () -> Unit,
    onCopyToClipboard: (String) -> Unit,
    onOpenFileClicked: (String) -> Unit,
    onOpenFolderClicked: () -> Unit,
    onSettingsChanged: (AppSettings) -> Unit,
    onSaveRemoteFileToLocal: (String, String, String, RemoteDeviceClient.RemoteFileItem) -> Unit = { _, _, _, _ -> }
) {
    val screens = listOf(Screen.Transfer, Screen.Discovery, Screen.Server, Screen.Settings)
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { screens.size })
    val coroutineScope = rememberCoroutineScope()

    var activeRemoteTarget by remember { mutableStateOf<RemoteTarget?>(null) }

    if (activeRemoteTarget != null) {
        val target = activeRemoteTarget!!
        RemoteExplorerScreen(
            peerName = target.name,
            peerIp = target.ip,
            peerPort = target.port,
            initialPin = target.initialPin,
            onBackClicked = { activeRemoteTarget = null },
            onSaveFileToLocal = { pin, remoteFile ->
                onSaveRemoteFileToLocal(target.ip, target.port.toString(), pin, remoteFile)
            }
        )
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar(
                    containerColor = NougatSurface,
                    contentColor = Color.White
                ) {
                    screens.forEachIndexed { index, screen ->
                        val selected = pagerState.currentPage == index
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = screen.icon,
                                    contentDescription = screen.title,
                                    tint = if (selected) NougatTeal else NougatTextMuted
                                )
                            },
                            label = {
                                Text(
                                    text = screen.title,
                                    color = if (selected) NougatTeal else NougatTextMuted,
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            selected = selected,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = NougatTeal.copy(alpha = 0.15f)
                            )
                        )
                    }
                }
            }
        ) { innerPadding ->
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) { page ->
                when (page) {
                    0 -> TransferScreen(
                        transferState = transferState,
                        connectionState = connectionState,
                        selectedFileMeta = selectedFileMeta,
                        selectedPieceSize = selectedPieceSize,
                        selectedPeerIds = selectedPeerIds,
                        discoveredPeers = discoveredPeers,
                        onSelectFileClicked = onSelectFileClicked,
                        onClearSelectedFile = onClearSelectedFile,
                        onPieceSizeSelected = onPieceSizeSelected,
                        onPeerToggleSelected = onPeerToggleSelected,
                        onStartTransferClicked = onStartTransferClicked,
                        onCancelClicked = onCancelTransferClicked,
                        onOpenFileClicked = onOpenFileClicked,
                        onOpenFolderClicked = onOpenFolderClicked
                    )
                    1 -> DiscoveryScreen(
                        discoveredPeers = discoveredPeers,
                        onConnectPeerClicked = onConnectPeerClicked,
                        onBrowsePeerFilesClicked = { peer ->
                            val peerIp = peer.ipAddress ?: "192.168.49.1"
                            activeRemoteTarget = RemoteTarget(peer.name, peerIp)
                        },
                        onManualConnectClicked = { ip, pin ->
                            activeRemoteTarget = RemoteTarget("Remote Device ($ip)", ip, initialPin = pin)
                        }
                    )
                    2 -> LocalServerScreen(
                        isFtpRunning = isFtpRunning,
                        isHttpRunning = isHttpRunning,
                        activeIpList = activeIpList,
                        ftpPin = ftpPin,
                        httpQrBitmap = httpQrBitmap,
                        onStartServers = onStartServers,
                        onStopServers = onStopServers,
                        onCopyToClipboard = onCopyToClipboard,
                        sharedFiles = sharedFiles,
                        onRefreshFiles = onRefreshFiles,
                        onDeleteFile = onDeleteFile,
                        onShareFile = onShareFile,
                        onAddFile = onAddFile
                    )
                    3 -> SettingsScreen(
                        currentSettings = appSettings,
                        onSettingsChanged = onSettingsChanged
                    )
                }
            }
        }
    }
}
