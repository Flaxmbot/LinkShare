package app.linkshare.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
    currentDirectory: String,
    onCopyAddress: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val ipAddresses = remember { PlatformNetwork.getAllActiveIpAddresses() }
    val primaryIp = remember { PlatformNetwork.getLocalIpAddress() }
    var isHttpRunning by remember { mutableStateOf(httpServer.isServerActive()) }
    var isFtpRunning by remember { mutableStateOf(ftpServer.isServerActive()) }

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
        // ---------- MOUNT DIRECTORY SELECTION CARD ----------
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = NougatSurface),
            shape = RoundedCornerShape(4.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = NougatAmber,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "MOUNTED DIRECTORY",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp,
                        color = NougatTextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NougatBackground, RoundedCornerShape(4.dp))
                        .border(0.5.dp, NougatCardBorder, RoundedCornerShape(4.dp))
                        .clickable { onDirectoryPick() }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = NougatTeal,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = currentDirectory.ifBlank { "Tap to choose shared directory..." },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = if (currentDirectory.isBlank()) NougatTextMuted else NougatTealLight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "CHANGE",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = NougatTeal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ---------- SERVER CONTROL CARD ----------
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = NougatSurface),
            shape = RoundedCornerShape(4.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header & status badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isHttpRunning) NougatGreen.copy(alpha = pulseAlpha) else NougatRed
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isHttpRunning) "HTTP SERVER ACTIVE" else "HTTP SERVER STOPPED",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color.White
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isHttpRunning) NougatGreen.copy(alpha = 0.15f) else NougatRed.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = if (isHttpRunning) "ONLINE" else "OFFLINE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isHttpRunning) NougatGreen else NougatRed,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // PIN Display
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NougatBackground, RoundedCornerShape(4.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "ACCESS PIN", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NougatTextMuted)
                        Text(
                            text = httpServer.sessionPin,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = NougatAmber,
                            letterSpacing = 4.sp
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = NougatSurfaceLight
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Default.Security, contentDescription = null, tint = NougatTeal, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("PIN SECURED", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NougatTealLight)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Start/Stop Button
                Button(
                    onClick = {
                        if (isHttpRunning) {
                            httpServer.stopServer()
                        } else if (currentDirectory.isNotBlank()) {
                            httpServer.startServer(currentDirectory)
                        }
                        isHttpRunning = httpServer.isServerActive()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isHttpRunning) NougatRed else NougatTeal
                    ),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Icon(
                        imageVector = if (isHttpRunning) Icons.Default.PowerSettingsNew else Icons.Default.Http,
                        contentDescription = null,
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isHttpRunning) "STOP HTTP SERVER" else "START HTTP SERVER",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ---------- FTP SERVER CONTROL CARD ----------
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
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (isFtpRunning) NougatGreen.copy(alpha = pulseAlpha) else NougatRed)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "FTP SERVER (PORT 2121)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color.White
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isFtpRunning) NougatGreen.copy(alpha = 0.15f) else NougatRed.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = if (isFtpRunning) "ACTIVE" else "STOPPED",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isFtpRunning) NougatGreen else NougatRed,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (isFtpRunning) {
                            ftpServer.stopServer()
                        } else if (currentDirectory.isNotBlank()) {
                            ftpServer.startServer(currentDirectory)
                        }
                        isFtpRunning = ftpServer.isServerActive()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isFtpRunning) NougatRed else NougatSurfaceLight
                    ),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth().height(40.dp)
                ) {
                    Icon(imageVector = Icons.Default.Storage, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isFtpRunning) "STOP FTP SERVER" else "START FTP SERVER",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ---------- NETWORK ADDRESSES CARD ----------
        Text(
            text = "NETWORK ADDRESSES",
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
                    label = "WEB PORTAL (HTTP)",
                    uri = "http://$primaryIp:8888?pin=${httpServer.sessionPin}",
                    onCopy = onCopyAddress
                )

                AddressRowItem(
                    icon = Icons.Default.FolderOpen,
                    iconTint = NougatAmber,
                    label = "WEBDAV NETWORK DRIVE",
                    uri = "http://$primaryIp:8888/",
                    onCopy = onCopyAddress
                )

                AddressRowItem(
                    icon = Icons.Default.Storage,
                    iconTint = NougatGreen,
                    label = "FTP STORAGE ACCESS",
                    uri = "ftp://$primaryIp:2121",
                    onCopy = onCopyAddress
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ---------- NETWORK INTERFACES LIST ----------
        Text(
            text = "ACTIVE INTERFACES (${ipAddresses.size})",
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
                    Text("No active local network interfaces found", fontSize = 12.sp, color = NougatTextMuted)
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
fun AddressRowItem(
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
