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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.linkshare.platform.PlatformFtpServer
import app.linkshare.platform.PlatformHttpServer
import app.linkshare.platform.PlatformNetwork
import app.linkshare.ui.theme.*

@Composable
fun ServerScreen(
    httpServer: PlatformHttpServer,
    ftpServer: PlatformFtpServer,
    onDirectoryPick: () -> Unit,
    currentDirectory: String,
    modifier: Modifier = Modifier
) {
    val ipAddresses = remember { PlatformNetwork.getAllActiveIpAddresses() }
    val primaryIp = remember { PlatformNetwork.getLocalIpAddress() }
    var httpRunning by remember { mutableStateOf(httpServer.isServerActive()) }
    var ftpRunning by remember { mutableStateOf(ftpServer.isServerActive()) }
    var showQrDialog by remember { mutableStateOf(false) }

    // Pulse animation for active indicator
    val infiniteTransition = rememberInfiniteTransition()
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = EaseInOut), RepeatMode.Reverse)
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Text("Server", fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Text("Share files over your local network", fontSize = 14.sp, color = TextSecondary)

        Spacer(Modifier.height(4.dp))

        // Mount Directory Card
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark2),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Folder, "Directory", tint = AccentAmber, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Shared Directory", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(BackgroundDark)
                        .border(1.dp, BorderDark, RoundedCornerShape(8.dp))
                        .clickable { onDirectoryPick() }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        currentDirectory.ifBlank { "Tap to select directory..." },
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (currentDirectory.isBlank()) TextTertiary else TextSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.Default.ChevronRight, "Pick", tint = TextTertiary, modifier = Modifier.size(18.dp))
                }
            }
        }

        // HTTP Server Card
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark2),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Status indicator
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (httpRunning) AccentGreen.copy(alpha = pulseAlpha) else AccentRed.copy(alpha = 0.5f))
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("HTTP Server", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary, modifier = Modifier.weight(1f))
                    Text(
                        if (httpRunning) "Active" else "Stopped",
                        fontSize = 12.sp,
                        color = if (httpRunning) AccentGreen else TextTertiary
                    )
                }

                Spacer(Modifier.height(12.dp))

                AnimatedVisibility(httpRunning, enter = fadeIn(), exit = fadeOut()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Connection URL
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(BackgroundDark)
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Link, "URL", tint = LinkBlue, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "http://$primaryIp:8888",
                                fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = LinkBlueLight,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // PIN display
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(BackgroundDark)
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Lock, "PIN", tint = AccentAmber, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("PIN: ", fontSize = 12.sp, color = TextSecondary)
                            Text(
                                httpServer.sessionPin,
                                fontSize = 20.sp, fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace, color = TextPrimary,
                                letterSpacing = 6.sp
                            )
                        }

                        // QR Code button
                        OutlinedButton(
                            onClick = { showQrDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                brush = Brush.linearGradient(listOf(BorderDark, BorderDark))
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                        ) {
                            Icon(Icons.Default.QrCode2, "QR", modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Show QR Code", fontSize = 13.sp)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Start/Stop button
                Button(
                    onClick = {
                        if (httpRunning) {
                            httpServer.stopServer()
                        } else if (currentDirectory.isNotBlank()) {
                            httpServer.startServer(currentDirectory)
                        }
                        httpRunning = httpServer.isServerActive()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (httpRunning) AccentRed.copy(alpha = 0.15f) else LinkBlue
                    )
                ) {
                    Icon(
                        if (httpRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                        "Toggle",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (httpRunning) "Stop Server" else "Start Server",
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // FTP Server Card
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark2),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (ftpRunning) AccentGreen.copy(alpha = pulseAlpha) else AccentRed.copy(alpha = 0.5f))
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("FTP Server", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary, modifier = Modifier.weight(1f))
                    Text(
                        if (ftpRunning) "Active" else "Stopped",
                        fontSize = 12.sp,
                        color = if (ftpRunning) AccentGreen else TextTertiary
                    )
                }

                AnimatedVisibility(ftpRunning, enter = fadeIn(), exit = fadeOut()) {
                    Column(modifier = Modifier.padding(top = 10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(BackgroundDark)
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Storage, "FTP", tint = AccentGreen, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "ftp://$primaryIp:2121",
                                fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = AccentGreen
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (ftpRunning) ftpServer.stopServer()
                        else if (currentDirectory.isNotBlank()) ftpServer.startServer(currentDirectory)
                        ftpRunning = ftpServer.isServerActive()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (ftpRunning) AccentRed.copy(alpha = 0.15f) else SurfaceDark3
                    )
                ) {
                    Icon(
                        if (ftpRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                        "Toggle",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (ftpRunning) "Stop FTP" else "Start FTP",
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Network Interfaces Card
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark2),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Wifi, "Network", tint = LinkBlue, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Network Interfaces", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                }
                Spacer(Modifier.height(10.dp))
                if (ipAddresses.isEmpty()) {
                    Text("No active network interfaces", fontSize = 13.sp, color = TextTertiary)
                } else {
                    ipAddresses.forEach { info ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(info.label, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.width(80.dp))
                            Text(info.ip, fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = TextPrimary)
                        }
                    }
                }
            }
        }
    }
}
