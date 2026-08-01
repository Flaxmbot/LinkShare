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
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NougatBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "SETTINGS & PREFERENCES",
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 1.sp,
            color = NougatTextSecondary
        )

        // Device Info Section
        SettingsSection("DEVICE IDENTIFICATION") {
            SettingsItem(
                icon = Icons.Default.Smartphone,
                title = "Device Name",
                subtitle = settings.deviceName
            )
        }

        // Protocols & Gateway Section
        SettingsSection("PROTOCOLS & GATEWAYS") {
            SettingsToggle(
                icon = Icons.Default.Http,
                title = "WebDAV Gateway",
                subtitle = "Enable WebDAV network drive mounting on Smart TVs and Kodi",
                checked = settings.enableWebDav,
                onCheckedChange = { onSettingsChange(settings.copy(enableWebDav = it)) }
            )
            HorizontalDivider(color = NougatCardBorder, thickness = 0.5.dp)
            SettingsToggle(
                icon = Icons.Default.ContentPaste,
                title = "Universal Clipboard Sync",
                subtitle = "Sync clipboard text seamlessly across connected LAN peers",
                checked = settings.enableClipboardSync,
                onCheckedChange = { onSettingsChange(settings.copy(enableClipboardSync = it)) }
            )
            HorizontalDivider(color = NougatCardBorder, thickness = 0.5.dp)
            SettingsToggle(
                icon = Icons.Default.Security,
                title = "Require FTP PIN",
                subtitle = "Require PIN authentication for incoming FTP clients",
                checked = settings.ftpRequirePin,
                onCheckedChange = { onSettingsChange(settings.copy(ftpRequirePin = it)) }
            )
        }

        // P2P Engine Section
        SettingsSection("P2P TRANSFER ENGINE") {
            SettingsToggle(
                icon = Icons.Default.FlashOn,
                title = "Dual-Link Channel Bonding (F2)",
                subtitle = "Aggregate Wi-Fi + Wi-Fi Direct links for maximum transfer speed",
                checked = settings.enableDualLinkF2,
                onCheckedChange = { onSettingsChange(settings.copy(enableDualLinkF2 = it)) }
            )
            HorizontalDivider(color = NougatCardBorder, thickness = 0.5.dp)
            SettingsToggle(
                icon = Icons.Default.Groups,
                title = "Swarm Multi-Peer Distribution (F3)",
                subtitle = "BitTorrent-style parallel piece distribution for 2+ recipients",
                checked = settings.enableSwarmF3,
                onCheckedChange = { onSettingsChange(settings.copy(enableSwarmF3 = it)) }
            )
        }

        // App Information
        SettingsSection("ABOUT LINKSHARE") {
            SettingsItem(
                icon = Icons.Default.Info,
                title = "Version",
                subtitle = "1.0.0 (Kotlin Multiplatform)"
            )
            HorizontalDivider(color = NougatCardBorder, thickness = 0.5.dp)
            SettingsItem(
                icon = Icons.Default.Code,
                title = "Open Source License",
                subtitle = "Apache License 2.0 · GitHub repository"
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = NougatTeal,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = NougatSurface),
            shape = RoundedCornerShape(4.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            content = content
        )
    }
}

@Composable
private fun SettingsItem(icon: ImageVector, title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = NougatTeal, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text(text = subtitle, fontSize = 12.sp, color = NougatTextMuted)
        }
    }
}

@Composable
private fun SettingsToggle(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = NougatTeal, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text(text = subtitle, fontSize = 11.sp, color = NougatTextMuted, lineHeight = 15.sp)
        }
        Switch(
            checked = checked,
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
