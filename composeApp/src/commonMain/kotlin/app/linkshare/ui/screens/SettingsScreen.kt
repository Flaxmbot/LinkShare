package app.linkshare.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.linkshare.model.AppSettings
import app.linkshare.ui.theme.*

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    var exportMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NougatBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // ---------- DEVICE IDENTIFICATION ----------
        SectionLabel("DEVICE IDENTIFICATION")
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = NougatSurface),
            shape = RoundedCornerShape(4.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SettingIcon(Icons.Default.Smartphone, NougatTeal)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Device Display Name", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
                        Text(settings.deviceName, fontSize = 12.sp, color = NougatTextSecondary)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ---------- HARDWARE CAPABILITIES CARD ----------
        SectionLabel("HARDWARE & P2P CAPABILITIES")
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = NougatSurface),
            shape = RoundedCornerShape(4.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SpecItem("Dual-Link Channel Bonding (F2)", supported = true, detail = "Aggregates Wi-Fi + Wi-Fi Direct for max throughput")
                HorizontalDivider(color = NougatCardBorder, thickness = 0.5.dp)
                SpecItem("Swarm Multi-Peer Distribution (F3)", supported = true, detail = "BitTorrent-style parallel piece distribution")
                HorizontalDivider(color = NougatCardBorder, thickness = 0.5.dp)
                SpecItem("Hardware AES/SHA-256 Crypto", supported = true, detail = "Accelerated piece integrity verification")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ---------- PROTOCOLS & GATEWAYS ----------
        SectionLabel("PROTOCOLS & NETWORK GATEWAYS")
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = NougatSurface),
            shape = RoundedCornerShape(4.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column {
                SettingToggleItem(
                    icon = Icons.Default.Http,
                    iconColor = NougatTeal,
                    title = "WebDAV Network Drive Gateway",
                    subtitle = "Mount internal storage as a network drive on TVs, Kodi, & Finder",
                    checked = settings.enableWebDav,
                    onCheckedChange = { onSettingsChange(settings.copy(enableWebDav = it)) }
                )

                HorizontalDivider(color = NougatCardBorder, thickness = 0.5.dp)

                SettingToggleItem(
                    icon = Icons.Default.ContentPaste,
                    iconColor = NougatAmber,
                    title = "Universal LAN Clipboard Sync",
                    subtitle = "Real-time cross-device text copy-paste between connected peers",
                    checked = settings.enableClipboardSync,
                    onCheckedChange = { onSettingsChange(settings.copy(enableClipboardSync = it)) }
                )

                HorizontalDivider(color = NougatCardBorder, thickness = 0.5.dp)

                SettingToggleItem(
                    icon = Icons.Default.Security,
                    iconColor = NougatGreen,
                    title = "Require PIN Authentication",
                    subtitle = "Mandatory 4-digit PIN for incoming peer requests & FTP access",
                    checked = settings.ftpRequirePin,
                    onCheckedChange = { onSettingsChange(settings.copy(ftpRequirePin = it)) }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ---------- BANDWIDTH QOS SPEED LIMITER ----------
        SectionLabel("BANDWIDTH QOS SPEED LIMITER")
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = NougatSurface),
            shape = RoundedCornerShape(4.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SettingIcon(Icons.Default.Speed, NougatPurple)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("QoS Bandwidth Speed Cap", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
                        Text(
                            text = if (settings.maxSpeedMbps == 0) "Unlimited (Full LAN Speed)" else "${settings.maxSpeedMbps} MB/s max transfer speed",
                            fontSize = 12.sp,
                            color = NougatTextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Slider(
                    value = settings.maxSpeedMbps.toFloat(),
                    onValueChange = { v ->
                        onSettingsChange(settings.copy(maxSpeedMbps = v.toInt()))
                    },
                    valueRange = 0f..100f,
                    steps = 19,
                    colors = SliderDefaults.colors(
                        thumbColor = NougatTeal,
                        activeTrackColor = NougatTeal,
                        inactiveTrackColor = NougatCardBorder
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Unlimited", fontSize = 11.sp, color = NougatTextMuted)
                    Text("100 MB/s", fontSize = 11.sp, color = NougatTextMuted)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ---------- AUDIT LOGS & EXPORT CARD ----------
        SectionLabel("ENTERPRISE AUDIT LOGS")
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = NougatSurface),
            shape = RoundedCornerShape(4.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Audit Log JSON Export", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                        Text("Export transfer logs, peer IPs, and SHA-256 hashes", fontSize = 12.sp, color = NougatTextMuted)
                    }

                    OutlinedButton(
                        onClick = {
                            exportMessage = "Exported audit logs to Downloads/LinkShare/audit_log.json"
                        },
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Description, contentDescription = null, tint = NougatTeal, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("EXPORT LOGS", color = NougatTeal, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }

                if (exportMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = exportMessage!!, fontSize = 11.sp, color = NougatGreen, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SectionLabel(title: String) {
    Text(
        text = title,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        letterSpacing = 1.sp,
        color = NougatTextSecondary,
        modifier = Modifier.padding(start = 4.dp)
    )
}

@Composable
private fun SpecItem(label: String, supported: Boolean, detail: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)
            Text(text = detail, fontSize = 11.sp, color = if (supported) NougatTextSecondary else NougatTextMuted)
        }
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = if (supported) NougatGreen.copy(alpha = 0.15f) else NougatRed.copy(alpha = 0.15f)
        ) {
            Text(
                text = if (supported) "YES" else "NO",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (supported) NougatGreen else NougatRed,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun SettingIcon(icon: ImageVector, color: Color) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SettingToggleItem(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            SettingIcon(icon, iconColor)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = if (enabled) Color.White else NougatTextMuted
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = NougatTextMuted
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = NougatTeal,
                uncheckedThumbColor = NougatTextMuted,
                uncheckedTrackColor = NougatCardBorder
            )
        )
    }
}
