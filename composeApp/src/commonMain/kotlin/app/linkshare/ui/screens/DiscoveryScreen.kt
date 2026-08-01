package app.linkshare.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.linkshare.model.PeerDevice
import app.linkshare.platform.PlatformNetwork
import app.linkshare.ui.theme.*

@Composable
fun DiscoveryScreen(
    discoveredPeers: List<PeerDevice>,
    isSearching: Boolean,
    onStartScan: () -> Unit,
    onConnectPeer: (PeerDevice) -> Unit,
    onConnectByIp: (String, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showManualConnect by remember { mutableStateOf(false) }
    var manualIp by remember { mutableStateOf("") }
    var manualPort by remember { mutableStateOf("8888") }
    val localIp = remember { PlatformNetwork.getLocalIpAddress() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Text("Discover Devices", fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Text("Find devices on your local network", fontSize = 14.sp, color = TextSecondary)

        // Radar animation area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceDark2),
            contentAlignment = Alignment.Center
        ) {
            if (isSearching) {
                RadarAnimation()
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Wifi, "Search",
                        tint = TextTertiary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Tap Scan to search for devices", fontSize = 13.sp, color = TextTertiary)
                }
            }
        }

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onStartScan,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LinkBlue),
                enabled = !isSearching
            ) {
                Icon(
                    if (isSearching) Icons.Default.HourglassTop else Icons.Default.Radar,
                    "Scan", modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(if (isSearching) "Scanning..." else "Scan Network", fontWeight = FontWeight.Medium)
            }
            OutlinedButton(
                onClick = { showManualConnect = !showManualConnect },
                shape = RoundedCornerShape(8.dp),
                border = ButtonDefaults.outlinedButtonBorder,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
            ) {
                Icon(Icons.Default.AddLink, "Manual", modifier = Modifier.size(18.dp))
            }
        }

        // Manual IP connect
        if (showManualConnect) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark2),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Connect by IP Address", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                    Text(
                        "Enter the IP address and port of the device running LinkShare server. " +
                        "You can find this information on the Server screen of the host device.",
                        fontSize = 12.sp, color = TextTertiary, lineHeight = 16.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = manualIp,
                            onValueChange = { manualIp = it },
                            label = { Text("IP Address", fontSize = 12.sp) },
                            placeholder = { Text("192.168.1.100", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(2f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = LinkBlue,
                                unfocusedBorderColor = BorderDark,
                                cursorColor = LinkBlue
                            )
                        )
                        OutlinedTextField(
                            value = manualPort,
                            onValueChange = { manualPort = it },
                            label = { Text("Port", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                if (manualIp.isNotBlank()) onConnectByIp(manualIp.trim(), manualPort.toIntOrNull() ?: 8888)
                            }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = LinkBlue,
                                unfocusedBorderColor = BorderDark,
                                cursorColor = LinkBlue
                            )
                        )
                    }
                    Button(
                        onClick = {
                            if (manualIp.isNotBlank()) onConnectByIp(manualIp.trim(), manualPort.toIntOrNull() ?: 8888)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = LinkBlue),
                        enabled = manualIp.isNotBlank()
                    ) {
                        Text("Connect", fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        // Your IP info
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark2),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, "Info", tint = LinkBlue, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Your IP: ", fontSize = 12.sp, color = TextSecondary)
                Text(localIp, fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = TextPrimary)
            }
        }

        // Discovered devices list
        if (discoveredPeers.isNotEmpty()) {
            Text("Devices Found (${discoveredPeers.size})", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(discoveredPeers) { peer ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark2),
                    modifier = Modifier.fillMaxWidth().clickable { onConnectPeer(peer) }
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(LinkBlue.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Devices, "Device", tint = LinkBlue, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(peer.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                            Text(
                                peer.ipAddress ?: "Unknown IP",
                                fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = TextSecondary
                            )
                        }
                        Icon(Icons.Default.ChevronRight, "Connect", tint = TextTertiary)
                    }
                }
            }

            if (discoveredPeers.isEmpty() && !isSearching) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No devices found. Tap Scan to search.", fontSize = 13.sp, color = TextTertiary)
                    }
                }
            }
        }
    }
}

@Composable
private fun RadarAnimation() {
    val infiniteTransition = rememberInfiniteTransition()

    val ring1 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart)
    )
    val ring2 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(2000, easing = LinearEasing),
            RepeatMode.Restart,
            initialStartOffset = StartOffset(600)
        )
    )
    val ring3 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(2000, easing = LinearEasing),
            RepeatMode.Restart,
            initialStartOffset = StartOffset(1200)
        )
    )

    Canvas(modifier = Modifier.size(160.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val maxRadius = size.minDimension / 2

        // Draw expanding rings
        listOf(ring1, ring2, ring3).forEach { progress ->
            val radius = maxRadius * progress
            val alpha = (1f - progress).coerceIn(0f, 0.6f)
            drawCircle(
                color = Color(0xFF0078D4).copy(alpha = alpha),
                radius = radius,
                center = center,
                style = Stroke(width = 2f)
            )
        }

        // Center dot
        drawCircle(
            color = Color(0xFF0078D4),
            radius = 6f,
            center = center
        )
    }
}
