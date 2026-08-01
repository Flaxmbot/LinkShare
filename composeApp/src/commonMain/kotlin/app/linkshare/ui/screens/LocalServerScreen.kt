package app.linkshare.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.linkshare.platform.PlatformFtpServer
import app.linkshare.platform.PlatformHttpServer
import app.linkshare.platform.PlatformNetwork
import app.linkshare.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalServerScreen(
    httpServer: PlatformHttpServer,
    ftpServer: PlatformFtpServer,
    onDirectoryPick: () -> Unit,
    onSetMountedDirectory: (String) -> Unit,
    currentDirectory: String,
    onCopyAddress: (String) -> Unit,
    mountPoints: List<String> = emptyList(),
    onSharingStarted: (String) -> Unit = {},
    onSharingStopped: () -> Unit = {},
    onReceiveClicked: () -> Unit = {},
    onSendClicked: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val ipAddresses = remember { PlatformNetwork.getAllActiveIpAddresses() }
    val primaryIp = ipAddresses.firstOrNull()?.ip ?: PlatformNetwork.getLocalIpAddress()
    var isRunning by remember { mutableStateOf(httpServer.isServerActive() || ftpServer.isServerActive()) }
    var menuExpanded by remember { mutableStateOf(false) }
    var autoReconnect by remember { mutableStateOf(true) }
    var swarmMode by remember { mutableStateOf(true) }
    val selectedDirectory = currentDirectory.ifBlank { mountPoints.firstOrNull() ?: "/storage/emulated/0" }

    LaunchedEffect(Unit) {
        while (true) {
            isRunning = httpServer.isServerActive() || ftpServer.isServerActive()
            kotlinx.coroutines.delay(800)
        }
    }

    Column(
        modifier = modifier.fillMaxSize().background(NougatBackground)
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Share files", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("Fast, private transfers over your nearby network", color = NougatTextSecondary, fontSize = 13.sp)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onSendClicked,
                modifier = Modifier.weight(1f).height(92.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NougatTeal)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.NorthEast, null, modifier = Modifier.size(27.dp))
                    Spacer(Modifier.height(6.dp))
                    Text("SEND", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }
            OutlinedButton(
                onClick = onReceiveClicked,
                modifier = Modifier.weight(1f).height(92.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NougatTealLight)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.SouthWest, null, modifier = Modifier.size(27.dp))
                    Spacer(Modifier.height(6.dp))
                    Text("RECEIVE", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().background(NougatSurface, RoundedCornerShape(12.dp)).padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            HubStat("${if (isRunning) "ON" else "OFF"}", "sharing")
            HubStat("${if (swarmMode) "2+" else "1"}", "transfer lanes")
            HubStat("${if (autoReconnect) "ON" else "OFF"}", "auto-link")
        }

        Text("Choose what to share", color = NougatTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            CategoryChip(Icons.Default.PhotoLibrary, "Photos", Modifier.weight(1f))
            CategoryChip(Icons.Default.VideoLibrary, "Videos", Modifier.weight(1f))
            CategoryChip(Icons.Default.MusicNote, "Music", Modifier.weight(1f))
            CategoryChip(Icons.Default.Folder, "Files", Modifier.weight(1f))
        }

        Card(colors = CardDefaults.cardColors(containerColor = NougatSurface), shape = RoundedCornerShape(4.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = (if (isRunning) NougatGreen else NougatTextMuted).copy(alpha = .16f)
                    ) {
                        Icon(
                            if (isRunning) Icons.Default.WifiTethering else Icons.Default.WifiOff,
                            contentDescription = null,
                            tint = if (isRunning) NougatGreen else NougatTextMuted,
                            modifier = Modifier.padding(10.dp).size(22.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (isRunning) "Sharing is on" else "Sharing is off", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(if (isRunning) "Other devices can access your shared folder" else "Turn this on when you want to share", color = NougatTextSecondary, fontSize = 12.sp)
                    }
                    Switch(checked = isRunning, onCheckedChange = {
                        if (it) {
                            httpServer.startServer(selectedDirectory)
                            ftpServer.startServer(selectedDirectory)
                            onSharingStarted(selectedDirectory)
                        } else {
                            httpServer.stopServer()
                            ftpServer.stopServer()
                            onSharingStopped()
                        }
                    })
                }
                Text(
                    if (isRunning) "HTTP, WebDAV and FTP are available on this network" else "Nothing is exposed until you turn sharing on",
                    color = if (isRunning) NougatTealLight else NougatTextMuted,
                    fontSize = 12.sp
                )
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = NougatSurface), shape = RoundedCornerShape(4.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Hub, null, tint = NougatTeal, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Swarm transfer", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("True dual-link · BitTorrent-style piece sharing", color = NougatTextSecondary, fontSize = 12.sp)
                    }
                    Switch(checked = swarmMode, onCheckedChange = { swarmMode = it })
                }
                Text(
                    if (swarmMode) "Multiple devices can seed the same transfer at once for higher throughput."
                    else "Transfers use the standard single-peer path.",
                    color = if (swarmMode) NougatTealLight else NougatTextMuted,
                    fontSize = 12.sp
                )
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = NougatSurface), shape = RoundedCornerShape(4.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Devices, null, tint = NougatAmber, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Remembered devices", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("Sign in once, reconnect when devices return in range", color = NougatTextSecondary, fontSize = 12.sp)
                    }
                    Switch(checked = autoReconnect, onCheckedChange = { autoReconnect = it })
                }
                listOf("Studio Mac" to "Trusted · last seen 2 min ago", "Pixel 8" to "Trusted · last seen yesterday").forEach { (name, status) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(32.dp).background(NougatTeal.copy(alpha = .12f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Computer, null, tint = NougatTealLight, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(status, color = NougatTextMuted, fontSize = 11.sp)
                        }
                        Icon(Icons.Default.VerifiedUser, null, tint = NougatGreen, modifier = Modifier.size(18.dp))
                    }
                }
                OutlinedButton(onClick = { /* pairing entry point */ }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(4.dp)) {
                    Icon(Icons.Default.AddLink, null, tint = NougatTeal)
                    Spacer(Modifier.width(8.dp))
                    Text("Pair a device", color = NougatTealLight)
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = NougatSurface), shape = RoundedCornerShape(4.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Shared folder", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("This is the folder WebDAV, HTTP and FTP can read and write.", color = NougatTextSecondary, fontSize = 12.sp)
                Box {
                    OutlinedButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(14.dp)
                    ) {
                        Icon(Icons.Default.Folder, null, tint = NougatAmber)
                        Spacer(Modifier.width(10.dp))
                        Text(selectedDirectory, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White)
                        Icon(Icons.Default.ExpandMore, null, tint = NougatTextSecondary)
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        mountPoints.distinct().forEach { path ->
                            DropdownMenuItem(
                                text = { Text(path, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                leadingIcon = { Icon(Icons.Default.Storage, null) },
                                onClick = { onSetMountedDirectory(path); menuExpanded = false }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Choose another folder…") },
                            leadingIcon = { Icon(Icons.Default.FolderOpen, null) },
                            onClick = { menuExpanded = false; onDirectoryPick() }
                        )
                    }
                }
                Text("On Android, Internal storage (/storage/emulated/0) appears here when available.", color = NougatTextMuted, fontSize = 11.sp)
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = NougatSurface), shape = RoundedCornerShape(4.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Connect from another device", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                if (ipAddresses.isEmpty()) {
                    Text("No local network address is available. Turn on Wi‑Fi or connect Ethernet.", color = NougatAmber, fontSize = 12.sp)
                } else {
                    ipAddresses.take(5).forEach { info ->
                        ConnectionRow(info.label, "http://${info.ip}:8888/", Icons.Default.Language, onCopyAddress)
                    }
                }
                ConnectionRow("FTP", "ftp://$primaryIp:2121", Icons.Default.Storage, onCopyAddress)
                HorizontalDivider(color = NougatCardBorder)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, null, tint = NougatAmber, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Session PIN", color = NougatTextSecondary, fontSize = 11.sp)
                        Text(httpServer.sessionPin, color = NougatAmber, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, letterSpacing = 3.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun HubStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = NougatTealLight, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(label, color = NougatTextMuted, fontSize = 10.sp)
    }
}

@Composable
private fun CategoryChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier = Modifier) {
    Surface(color = NougatSurfaceLight, shape = RoundedCornerShape(20.dp), modifier = modifier) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(icon, null, tint = NougatTealLight, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, color = Color.White, fontSize = 10.sp, maxLines = 1)
        }
    }
}

@Composable
private fun ConnectionRow(label: String, address: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onCopy: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = NougatTeal, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = NougatTextSecondary, fontSize = 11.sp)
            Text(address, color = NougatTealLight, fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = { onCopy(address) }) { Icon(Icons.Default.ContentCopy, "Copy", tint = NougatTeal) }
    }
}
