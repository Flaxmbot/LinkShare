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
            .background(BackgroundDark)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings", fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Text("Configure LinkShare preferences", fontSize = 14.sp, color = TextSecondary)

        Spacer(Modifier.height(4.dp))

        // Device section
        SettingsSection("Device") {
            SettingsItem(
                icon = Icons.Default.Smartphone,
                title = "Device Name",
                subtitle = settings.deviceName
            )
        }

        // Server section
        SettingsSection("Server") {
            SettingsToggle(
                icon = Icons.Default.Http,
                title = "WebDAV Protocol",
                subtitle = "Enable WebDAV gateway for native OS mounting",
                checked = settings.enableWebDav,
                onCheckedChange = { onSettingsChange(settings.copy(enableWebDav = it)) }
            )
            SettingsToggle(
                icon = Icons.Default.ContentPaste,
                title = "Clipboard Sync",
                subtitle = "Sync clipboard text between connected devices",
                checked = settings.enableClipboardSync,
                onCheckedChange = { onSettingsChange(settings.copy(enableClipboardSync = it)) }
            )
            SettingsToggle(
                icon = Icons.Default.Security,
                title = "Require FTP PIN",
                subtitle = "Require PIN authentication for FTP connections",
                checked = settings.ftpRequirePin,
                onCheckedChange = { onSettingsChange(settings.copy(ftpRequirePin = it)) }
            )
        }

        // Transfer section
        SettingsSection("Transfer") {
            SettingsToggle(
                icon = Icons.Default.SwapHoriz,
                title = "Dual-Link (F2)",
                subtitle = "Use multiple network paths simultaneously",
                checked = settings.enableDualLinkF2,
                onCheckedChange = { onSettingsChange(settings.copy(enableDualLinkF2 = it)) }
            )
            SettingsToggle(
                icon = Icons.Default.Hub,
                title = "Swarm Transfer (F3)",
                subtitle = "BitTorrent-style multi-peer file distribution",
                checked = settings.enableSwarmF3,
                onCheckedChange = { onSettingsChange(settings.copy(enableSwarmF3 = it)) }
            )
        }

        // About section
        SettingsSection("About") {
            SettingsItem(
                icon = Icons.Default.Info,
                title = "Version",
                subtitle = "1.0.0"
            )
            SettingsItem(
                icon = Icons.Default.Code,
                title = "Open Source",
                subtitle = "github.com/Flaxmbot/LinkShare"
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = LinkBlue,
            modifier = Modifier.padding(bottom = 8.dp))
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark2),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(4.dp), content = content)
        }
    }
}

@Composable
private fun SettingsItem(icon: ImageVector, title: String, subtitle: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, title, tint = TextSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = TextPrimary)
            Text(subtitle, fontSize = 12.sp, color = TextSecondary)
        }
    }
}

@Composable
private fun SettingsToggle(
    icon: ImageVector, title: String, subtitle: String,
    checked: Boolean, onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, title, tint = TextSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = TextPrimary)
            Text(subtitle, fontSize = 12.sp, color = TextSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = LinkBlue,
                uncheckedTrackColor = SurfaceDark3
            )
        )
    }
}
