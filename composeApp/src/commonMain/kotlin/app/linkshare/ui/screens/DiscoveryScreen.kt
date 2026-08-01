package app.linkshare.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
    onBrowsePeerFilesClicked: (PeerDevice) -> Unit,
    modifier: Modifier = Modifier
) {
    val networkAvailable = PlatformNetwork.getAllActiveIpAddresses().isNotEmpty()
    val localIp = PlatformNetwork.getLocalIpAddress()

    Column(
        modifier = modifier.fillMaxSize().background(NougatBackground).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = NougatSurface), shape = RoundedCornerShape(12.dp)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(NougatTeal.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Wifi, null, tint = NougatTeal, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Nearby devices", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(if (networkAvailable) "Ready to scan your local network" else "Turn on Wi‑Fi to find nearby devices", color = if (networkAvailable) NougatTextSecondary else NougatAmber, fontSize = 12.sp)
                    if (networkAvailable && localIp != "127.0.0.1") Text(localIp, color = NougatTextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
                IconButton(onClick = onStartScan, enabled = !isSearching) {
                    Icon(Icons.Default.Refresh, "Refresh devices", tint = if (isSearching) NougatTextMuted else NougatTeal)
                }
            }
        }

        if (!networkAvailable) {
            Card(colors = CardDefaults.cardColors(containerColor = NougatAmber.copy(alpha = .10f)), shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WifiOff, null, tint = NougatAmber)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Wi‑Fi is off or unavailable", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("Turn on Wi‑Fi, join the same local network, then tap refresh.", color = NougatTextSecondary, fontSize = 12.sp)
                    }
                }
            }
        }

        Text("DEVICES (${discoveredPeers.size})", color = NougatTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)

        if (isSearching) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    NougatRadar()
                    Spacer(Modifier.height(14.dp))
                    Text("Looking for LinkShare devices…", color = NougatTextSecondary, fontSize = 13.sp)
                }
            }
        } else if (discoveredPeers.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.DevicesOther, null, tint = NougatTextMuted, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(10.dp))
                    Text(if (networkAvailable) "No devices found" else "Waiting for Wi‑Fi", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("Tap refresh after both devices join the same Wi‑Fi.", color = NougatTextMuted, fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(discoveredPeers, key = { it.id }) { peer ->
                    Card(colors = CardDefaults.cardColors(containerColor = NougatSurface), shape = RoundedCornerShape(12.dp)) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp).clip(CircleShape).background(NougatTeal.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Smartphone, null, tint = NougatTeal)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(peer.name, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(peer.ipAddress ?: "Local device", color = NougatTextMuted, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                            }
                            Button(onClick = { onBrowsePeerFilesClicked(peer) }, shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                                Text("Open", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NougatRadar() {
    val transition = rememberInfiniteTransition(label = "radar")
    val pulse by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "pulse")
    Canvas(Modifier.size(150.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.minDimension / 2
        drawCircle(NougatTeal.copy(alpha = .12f), radius, center, style = Stroke(2.dp.toPx()))
        drawCircle(NougatTeal.copy(alpha = (1f - pulse) * .7f), radius * pulse, center, style = Stroke(2.dp.toPx()))
        drawLine(NougatTeal.copy(alpha = .5f), center, Offset(center.x + radius * .72f, center.y - radius * .72f), strokeWidth = 2.dp.toPx())
        drawCircle(NougatTeal, 6.dp.toPx(), center)
    }
}
