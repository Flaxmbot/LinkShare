package app.linkshare.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

@Composable
fun LocalServerScreen(
    httpServer: PlatformHttpServer,
    ftpServer: PlatformFtpServer,
    onDirectoryPick: () -> Unit,
    onSetMountedDirectory: (String) -> Unit,
    currentDirectory: String,
    onCopyAddress: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val ipAddresses = remember { PlatformNetwork.getAllActiveIpAddresses() }
    val primaryIp = remember { PlatformNetwork.getLocalIpAddress() }
    var isRunning by remember { mutableStateOf(httpServer.isServerActive() || ftpServer.isServerActive()) }

    // Pulsing active indicator animation
    val infiniteTransition = rememberInfiniteTransition()
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000, easing = EaseInOut), RepeatMode.Reverse)
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NougatBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // ---------- SINGLE MASTER SERVER CONTROL CARD ----------
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = NougatSurface),
            shape = RoundedCornerShape(4.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(if (isRunning) NougatGreen.copy(alpha = pulseAlpha) else NougatRed)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (isRunning) "SERVER DAEMON ONLINE" else "SERVER DAEMON STOPPED",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                            Text(
                                text = if (isRunning) "HTTP (Port 8888) · FTP (Port 2121) · WebDAV" else "Tap below to start HTTP, FTP & WebDAV",
                                fontSize = 11.sp,
                                color = if (isRunning) NougatTealLight else NougatTextMuted
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isRunning) NougatGreen.copy(alpha = 0.15f) else NougatRed.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = if (isRunning) "ACTIVE" else "STOPPED",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isRunning) NougatGreen else NougatRed,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // SINGLE BUTTON TO START/STOP BOTH HTTP & FTP SERVERS
                Button(
                    onClick = {
                        if (isRunning) {
                            httpServer.stopServer()
                            ftpServer.stopServer()
                        } else {
                            val dir = currentDirectory.ifBlank { "/storage/emulated/0" }
                            httpServer.startServer(dir)
                            ftpServer.startServer(dir)
                        }
                        isRunning = httpServer.isServerActive() || ftpServer.isServerActive()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRunning) NougatRed else NougatTeal
                    ),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Icon(
                        imageVector = if (isRunning) Icons.Default.PowerSettingsNew else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isRunning) "STOP ALL SERVERS" else "START SERVER DAEMON",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ---------- MOUNTED DIRECTORY SELECTION CARD ----------
        Text(
            text = "SHARED STORAGE MOUNT",
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 1.sp,
            color = NougatTextSecondary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = NougatSurface),
            shape = RoundedCornerShape(4.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Mounted Folder:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = NougatTextMuted
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NougatBackground, RoundedCornerShape(4.dp))
                        .border(0.5.dp, NougatCardBorder, RoundedCornerShape(4.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderSpecial,
                        contentDescription = null,
                        tint = NougatAmber,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = currentDirectory.ifBlank { "/storage/emulated/0" },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = NougatTealLight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 1-TAP QUICK PRESET BUTTONS FOR EASY MOUNTING (bypassing DocumentPicker restriction for /storage/emulated/0)
                Text(
                    text = "Quick Presets:",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = NougatTextMuted
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PresetChip(
                        label = "⚡ Internal Storage",
                        path = "/storage/emulated/0",
                        currentDir = currentDirectory,
                        onClick = { onSetMountedDirectory("/storage/emulated/0") },
                        modifier = Modifier.weight(1f)
                    )
                    PresetChip(
                        label = "📁 Downloads",
                        path = "/storage/emulated/0/Download",
                        currentDir = currentDirectory,
                        onClick = { onSetMountedDirectory("/storage/emulated/0/Download") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PresetChip(
                        label = "📷 DCIM / Photos",
                        path = "/storage/emulated/0/DCIM",
                        currentDir = currentDirectory,
                        onClick = { onSetMountedDirectory("/storage/emulated/0/DCIM") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = onDirectoryPick,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.weight(1f).height(32.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                    ) {
                        Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null, tint = NougatTeal, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Custom Folder...", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NougatTeal)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ---------- PIN SECURITY CARD ----------
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = NougatSurface),
            shape = RoundedCornerShape(4.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "SESSION ACCESS PIN", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NougatTextMuted)
                    Text(
                        text = httpServer.sessionPin,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = NougatAmber,
                        letterSpacing = 4.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = NougatTeal.copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Security, contentDescription = null, tint = NougatTeal, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("PIN AUTH ACTIVE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NougatTealLight)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ---------- CLEAN NETWORK ADDRESSES CARD ----------
        Text(
            text = "NETWORK ADDRESSES & LINKS",
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 1.sp,
            color = NougatTextSecondary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = NougatSurface),
            shape = RoundedCornerShape(4.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AddressRowItem(
                    icon = Icons.Default.Http,
                    iconTint = NougatTeal,
                    label = "WEB PORTAL & EXPLORER",
                    uri = "http://$primaryIp:8888?pin=${httpServer.sessionPin}",
                    onCopy = onCopyAddress
                )

                AddressRowItem(
                    icon = Icons.Default.FolderOpen,
                    iconTint = NougatAmber,
                    label = "WEBDAV NETWORK DRIVE MOUNT",
                    uri = "http://$primaryIp:8888/",
                    onCopy = onCopyAddress
                )

                AddressRowItem(
                    icon = Icons.Default.Storage,
                    iconTint = NougatGreen,
                    label = "FTP CLIENT STORAGE ACCESS",
                    uri = "ftp://$primaryIp:2121",
                    onCopy = onCopyAddress
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ---------- ACTIVE NETWORK INTERFACES ----------
        Text(
            text = "PRIMARY WI-FI LAN INTERFACE",
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 1.sp,
            color = NougatTextSecondary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = NougatSurface),
            shape = RoundedCornerShape(4.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (ipAddresses.isEmpty()) {
                    Text("No active local Wi-Fi interfaces detected", fontSize = 12.sp, color = NougatTextMuted)
                } else {
                    ipAddresses.forEachIndexed { index, info ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (info.label.contains("Wi-Fi")) Icons.Default.Wifi else Icons.Default.Storage,
                                    contentDescription = null,
                                    tint = NougatTeal,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(info.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            }
                            Text(info.ip, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = NougatTealLight)
                        }
                        if (index < ipAddresses.size - 1) {
                            HorizontalDivider(color = NougatCardBorder, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun AddressRowItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    label: String,
    uri: String,
    onCopy: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NougatBackground, RoundedCornerShape(4.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NougatTextMuted)
        }
        Spacer(modifier = Modifier.height(2.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = uri,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = NougatTealLight,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(
                onClick = { onCopy(uri) },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy", tint = NougatTeal, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun PresetChip(
    label: String,
    path: String,
    currentDir: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSelected = currentDir.trimEnd('/') == path.trimEnd('/')
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(4.dp),
        color = if (isSelected) NougatTeal.copy(alpha = 0.2f) else NougatBackground,
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            if (isSelected) NougatTeal else NougatCardBorder
        ),
        modifier = modifier.height(32.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isSelected) NougatTeal else NougatTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
