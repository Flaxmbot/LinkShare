package app.linkshare.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.linkshare.core.transport.WifiP2pConnectionManager
import app.linkshare.model.PeerDevice
import app.linkshare.ui.theme.NougatBackground
import app.linkshare.ui.theme.NougatCardBorder
import app.linkshare.ui.theme.NougatGreen
import app.linkshare.ui.theme.NougatPurple
import app.linkshare.ui.theme.NougatSurface
import app.linkshare.ui.theme.NougatTeal
import app.linkshare.ui.theme.NougatTealLight
import app.linkshare.ui.theme.NougatTextMuted
import app.linkshare.ui.theme.NougatTextSecondary

@Composable
fun DiscoveryScreen(
    discoveredPeers: List<PeerDevice>,
    onConnectPeerClicked: (PeerDevice) -> Unit,
    onBrowsePeerFilesClicked: (PeerDevice) -> Unit = {},
    onManualConnectClicked: (String, String) -> Unit = { _, _ -> },
    onRefreshScanClicked: () -> Unit = {}
) {
    var isDiscoveryEnabled by remember { mutableStateOf(true) }
    var showManualConnectDialog by remember { mutableStateOf(false) }
    var ipInput by remember { mutableStateOf("") }
    var pinInput by remember { mutableStateOf("") }

    val infiniteTransition = rememberInfiniteTransition(label = "radarPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "radarPulseAlpha"
    )

    // Manual IP Connect Dialog
    if (showManualConnectDialog) {
        AlertDialog(
            onDismissRequest = { showManualConnectDialog = false },
            title = { Text("Connect to Remote Device", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column {
                    Text("Enter IP address & 4-digit PIN of the remote device hosting LinkShare:", fontSize = 12.sp, color = NougatTextSecondary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("IP Address", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NougatTextMuted)
                    Spacer(modifier = Modifier.height(4.dp))
                    BasicTextField(
                        value = ipInput,
                        onValueChange = { ipInput = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, fontFamily = FontFamily.Monospace),
                        cursorBrush = SolidColor(NougatTeal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(NougatBackground, RoundedCornerShape(4.dp))
                            .padding(10.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Security PIN", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NougatTextMuted)
                    Spacer(modifier = Modifier.height(4.dp))
                    BasicTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 4) pinInput = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, fontFamily = FontFamily.Monospace, letterSpacing = 4.sp),
                        cursorBrush = SolidColor(NougatTeal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(NougatBackground, RoundedCornerShape(4.dp))
                            .padding(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (ipInput.isNotBlank()) {
                            onManualConnectClicked(ipInput.trim(), pinInput.trim())
                            showManualConnectDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NougatTeal),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("BROWSE FILES", fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
        modifier = Modifier
            .fillMaxSize()
            .background(NougatBackground)
            .padding(16.dp)
    ) {
        var refreshRotation by remember { mutableStateOf(0f) }
        val animatedRotation by androidx.compose.animation.core.animateFloatAsState(
            targetValue = refreshRotation,
            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
            label = "refreshRotationAnim"
        )

        // TOOLBAR HEADER
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Discovery",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Wi-Fi Direct & Mesh Peer Scanner",
                    style = MaterialTheme.typography.bodySmall,
                    color = NougatTextSecondary
                )
            }

            IconButton(
                onClick = {
                    refreshRotation += 360f
                    onRefreshScanClicked()
                },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(NougatSurface)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = NougatTeal,
                    modifier = Modifier.graphicsLayer { rotationZ = animatedRotation }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // DISCOVERY TOGGLE CARD
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Radar,
                        contentDescription = null,
                        tint = if (isDiscoveryEnabled) NougatTeal.copy(alpha = pulseAlpha) else NougatTextMuted,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (isDiscoveryEnabled) "Scanning" else "Paused",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                        Text(
                            text = if (isDiscoveryEnabled) "Listening on _linkshare._tcp" else "Tap to resume discovery",
                            fontSize = 12.sp,
                            color = NougatTextSecondary
                        )
                    }
                }

                Switch(
                    checked = isDiscoveryEnabled,
                    onCheckedChange = { isDiscoveryEnabled = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = NougatTeal,
                        uncheckedThumbColor = NougatTextMuted,
                        uncheckedTrackColor = NougatCardBorder
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // SECTION TITLE & MANUAL CONNECT BUTTON
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "NEARBY DEVICES (${discoveredPeers.size})",
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
                Text("CONNECT BY IP", color = NougatTeal, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (discoveredPeers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.SearchOff,
                        contentDescription = null,
                        tint = NougatTextMuted,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Searching for devices...",
                        fontWeight = FontWeight.SemiBold,
                        color = NougatTextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Open LinkShare on nearby phones or connect directly via IP",
                        fontSize = 12.sp,
                        color = NougatTextMuted
                    )
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
                            text = peer.id,
                            fontSize = 11.sp,
                            color = NougatTextMuted
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (peer.supportsF2DualLink) {
                                FeatureChip(icon = Icons.Default.FlashOn, label = "⚡ Direct Speed", color = NougatGreen)
                            }
                            if (peer.supportsF3Swarm) {
                                FeatureChip(icon = Icons.Default.Groups, label = "👥 Group Share", color = NougatPurple)
                            }
                            if (peer.ftpServerActive) {
                                FeatureChip(icon = Icons.Default.FolderShared, label = "📁 File Server", color = NougatTealLight)
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
                    Text(text = "BROWSE FILES", color = NougatTeal, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onConnectClicked,
                    colors = ButtonDefaults.buttonColors(containerColor = NougatTeal),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(
                        text = "SEND FILES",
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
