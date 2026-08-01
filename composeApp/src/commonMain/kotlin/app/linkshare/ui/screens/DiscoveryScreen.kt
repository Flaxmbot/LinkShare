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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
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
    onConnectPeerClicked: (PeerDevice) -> Unit,
    onBrowsePeerFilesClicked: (PeerDevice) -> Unit,
    onConnectByIp: (String, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var isDiscoveryEnabled by remember { mutableStateOf(true) }
    var showManualConnectDialog by remember { mutableStateOf(false) }
    var manualIpInput by remember { mutableStateOf("") }
    var manualPortInput by remember { mutableStateOf("8888") }
    val localIp = remember { PlatformNetwork.getLocalIpAddress() }

    if (showManualConnectDialog) {
        AlertDialog(
            onDismissRequest = { showManualConnectDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Language, contentDescription = null, tint = NougatTeal, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Connect to Peer by IP", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter the IP address of the peer device running LinkShare on the same network.",
                        fontSize = 12.sp,
                        color = NougatTextSecondary
                    )
                    OutlinedTextField(
                        value = manualIpInput,
                        onValueChange = { manualIpInput = it },
                        label = { Text("IP Address (e.g. 192.168.1.5)", fontSize = 11.sp, color = NougatTextMuted) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NougatTeal,
                            unfocusedBorderColor = NougatCardBorder,
                            cursorColor = NougatTeal
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = manualPortInput,
                        onValueChange = { manualPortInput = it },
                        label = { Text("Port", fontSize = 11.sp, color = NougatTextMuted) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NougatTeal,
                            unfocusedBorderColor = NougatCardBorder,
                            cursorColor = NougatTeal
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (manualIpInput.isNotBlank()) {
                            onConnectByIp(manualIpInput.trim(), manualPortInput.toIntOrNull() ?: 8888)
                            showManualConnectDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NougatTeal),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("CONNECT", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualConnectDialog = false }) {
                    Text("CANCEL", color = NougatTextSecondary)
                }
            },
            containerColor = NougatSurface,
            shape = RoundedCornerShape(4.dp)
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NougatBackground)
            .padding(16.dp)
    ) {
        // ---------- DISCOVERY CONTROL HEADER CARD ----------
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = NougatSurface),
            shape = RoundedCornerShape(4.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(NougatTeal.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.WifiTethering,
                            contentDescription = null,
                            tint = NougatTeal,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                        text = "Nearby devices",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color.White
                        )
                        Text(
                            text = "Connected as $localIp",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = NougatTealLight
                        )
                    }
                }

                IconButton(onClick = onStartScan, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Radar,
                        contentDescription = "Scan",
                        tint = NougatTeal,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ---------- SECTION TITLE & MANUAL CONNECT BUTTON ----------
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Devices on this network (${discoveredPeers.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 1.sp,
                color = NougatTextSecondary
            )

            OutlinedButton(
                onClick = { showManualConnectDialog = true },
                shape = RoundedCornerShape(4.dp)
            ) {
                Icon(imageVector = Icons.Default.Language, contentDescription = null, tint = NougatTeal, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add by address", color = NougatTeal, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ---------- RADAR SEARCH ANIMATION AREA & DEVICE LIST ----------
        if (discoveredPeers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(4.dp))
                    .background(NougatSurface),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (isSearching) {
                        RadarPulseAnimation()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Looking for LinkShare devices…",
                            fontWeight = FontWeight.SemiBold,
                            color = NougatTealLight,
                            fontSize = 13.sp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.WifiFind,
                            contentDescription = null,
                            tint = NougatTextMuted,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No nearby devices found",
                            fontWeight = FontWeight.SemiBold,
                            color = NougatTextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap the search icon above or connect directly via IP",
                            fontSize = 12.sp,
                            color = NougatTextMuted
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(discoveredPeers) { peer ->
                    PeerListItem(
                        peer = peer,
                        onConnectClicked = { onConnectPeerClicked(peer) },
                        onBrowseFilesClicked = { onBrowsePeerFilesClicked(peer) }
                    )
                }
            }
        }
    }
}

@Composable
fun PeerListItem(
    peer: PeerDevice,
    onConnectClicked: () -> Unit,
    onBrowseFilesClicked: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NougatSurface),
        shape = RoundedCornerShape(4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(NougatTeal.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SignalWifi4Bar,
                            contentDescription = null,
                            tint = NougatTeal,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = peer.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                        Text(
                            text = peer.ipAddress ?: "Unknown IP",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = NougatTextMuted
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (peer.supportsF2DualLink) {
                                FeatureChip(icon = Icons.Default.FlashOn, label = "Direct Speed", color = NougatGreen)
                            }
                            if (peer.supportsF3Swarm) {
                                FeatureChip(icon = Icons.Default.Groups, label = "Group Share", color = NougatPurple)
                            }
                            if (peer.ftpServerActive) {
                                FeatureChip(icon = Icons.Default.FolderShared, label = "File Server", color = NougatTealLight)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onBrowseFilesClicked,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null, tint = NougatTeal, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Browse", color = NougatTeal, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onConnectClicked,
                    colors = ButtonDefaults.buttonColors(containerColor = NougatTeal),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(
                        text = "Send",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

@Composable
fun FeatureChip(
    icon: ImageVector,
    label: String,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(10.dp))
            Spacer(modifier = Modifier.width(3.dp))
            Text(text = label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
fun RadarPulseAnimation() {
    val infiniteTransition = rememberInfiniteTransition()

    val r1 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart)
    )
    val r2 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart, initialStartOffset = StartOffset(700))
    )
    val r3 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart, initialStartOffset = StartOffset(1400))
    )

    Canvas(modifier = Modifier.size(140.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val maxR = size.minDimension / 2

        listOf(r1, r2, r3).forEach { progress ->
            val radius = maxR * progress
            val alpha = (1f - progress).coerceIn(0f, 0.7f)
            drawCircle(
                color = Color(0xFF009688).copy(alpha = alpha),
                radius = radius,
                center = center,
                style = Stroke(width = 2.5f)
            )
        }

        drawCircle(
            color = Color(0xFF009688),
            radius = 6f,
            center = center
        )
    }
}
