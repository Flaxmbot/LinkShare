package app.linkshare.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.linkshare.model.AppSettings
import app.linkshare.ui.theme.*

@Composable
fun SettingsScreen(settings: AppSettings, onSettingsChange: (AppSettings) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().background(NougatBackground).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Settings", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("Only the options that change how sharing works.", color = NougatTextSecondary, fontSize = 13.sp)

        SettingsCard {
            SettingRow(
                icon = Icons.Default.Smartphone,
                title = "Device name",
                subtitle = settings.deviceName,
                trailing = { Icon(Icons.Default.ChevronRight, null, tint = NougatTextMuted) }
            )
        }

        SettingsCard {
            SettingRow(
                icon = Icons.Default.Lock,
                title = "Require a PIN",
                subtitle = "People need the PIN shown on Home to connect",
                trailing = {
                    Switch(
                        checked = settings.ftpRequirePin,
                        onCheckedChange = { onSettingsChange(settings.copy(ftpRequirePin = it)) }
                    )
                }
            )
            HorizontalDivider(color = NougatCardBorder)
            SettingRow(
                icon = Icons.Default.ContentPaste,
                title = "Clipboard sync",
                subtitle = "Keep copied text available to connected devices",
                trailing = {
                    Switch(
                        checked = settings.enableClipboardSync,
                        onCheckedChange = { onSettingsChange(settings.copy(enableClipboardSync = it)) }
                    )
                }
            )
        }

        SettingsCard {
            SettingRow(
                icon = Icons.Default.Speed,
                title = "Transfer speed",
                subtitle = if (settings.maxSpeedMbps == 0) "Unlimited" else "Up to ${settings.maxSpeedMbps} MB/s",
                trailing = { Icon(Icons.Default.Tune, null, tint = NougatTeal) }
            )
            Slider(
                value = settings.maxSpeedMbps.toFloat(),
                onValueChange = { onSettingsChange(settings.copy(maxSpeedMbps = it.toInt())) },
                valueRange = 0f..100f,
                steps = 19,
                colors = SliderDefaults.colors(thumbColor = NougatTeal, activeTrackColor = NougatTeal, inactiveTrackColor = NougatCardBorder)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Unlimited", color = NougatTextMuted, fontSize = 11.sp)
                Text("100 MB/s", color = NougatTextMuted, fontSize = 11.sp)
            }
        }

        SettingsCard {
            SettingRow(Icons.Default.Info, "About LinkShare", "Private file sharing over your local network")
            Text("No cloud account. Your files stay on the devices you choose.", color = NougatTextSecondary, fontSize = 12.sp, modifier = Modifier.padding(start = 48.dp, top = 4.dp))
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = NougatSurface), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun SettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = NougatTeal, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = NougatTextSecondary, fontSize = 12.sp)
        }
        trailing?.invoke()
    }
}
