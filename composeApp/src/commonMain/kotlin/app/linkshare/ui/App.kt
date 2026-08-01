package app.linkshare.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.linkshare.model.AppSettings
import app.linkshare.model.PeerDevice
import app.linkshare.platform.PlatformFileSystem
import app.linkshare.platform.PlatformFtpServer
import app.linkshare.platform.PlatformHttpServer
import app.linkshare.ui.screens.DiscoveryScreen
import app.linkshare.ui.screens.ServerScreen
import app.linkshare.ui.screens.SettingsScreen
import app.linkshare.ui.theme.*

enum class NavTab(val label: String, val icon: ImageVector) {
    Server("Server", Icons.Default.Dns),
    Discovery("Discover", Icons.Default.WifiFind),
    Settings("Settings", Icons.Default.Settings)
}

@Composable
fun App(
    httpServer: PlatformHttpServer,
    ftpServer: PlatformFtpServer,
    fileSystem: PlatformFileSystem,
    onDirectoryPick: () -> Unit,
    currentDirectory: String
) {
    LinkShareTheme {
        var selectedTab by remember { mutableStateOf(NavTab.Server) }
        var settings by remember { mutableStateOf(AppSettings()) }
        var discoveredPeers by remember { mutableStateOf(listOf<PeerDevice>()) }
        var isSearching by remember { mutableStateOf(false) }

        Scaffold(
            bottomBar = {
                NavigationBar(
                    containerColor = SurfaceDark,
                    tonalElevation = 0.dp
                ) {
                    NavTab.entries.forEach { tab ->
                        NavigationBarItem(
                            icon = { Icon(tab.icon, tab.label) },
                            label = { Text(tab.label, fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = LinkBlue,
                                selectedTextColor = LinkBlue,
                                unselectedIconColor = TextTertiary,
                                unselectedTextColor = TextTertiary,
                                indicatorColor = LinkBlue.copy(alpha = 0.12f)
                            )
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding)) {
                when (selectedTab) {
                    NavTab.Server -> ServerScreen(
                        httpServer = httpServer,
                        ftpServer = ftpServer,
                        onDirectoryPick = onDirectoryPick,
                        currentDirectory = currentDirectory
                    )
                    NavTab.Discovery -> DiscoveryScreen(
                        discoveredPeers = discoveredPeers,
                        isSearching = isSearching,
                        onStartScan = { isSearching = true },
                        onConnectPeer = { /* Handle peer connection */ },
                        onConnectByIp = { ip, port -> /* Handle IP connection */ }
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
