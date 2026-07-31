package app.linkshare.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.linkshare.core.transport.HardwareCapabilityDetector
import app.linkshare.model.AppSettings
import app.linkshare.ui.theme.NougatAmber
import app.linkshare.ui.theme.NougatBackground
import app.linkshare.ui.theme.NougatCardBorder
import app.linkshare.ui.theme.NougatGreen
import app.linkshare.ui.theme.NougatPurple
import app.linkshare.ui.theme.NougatRed
import app.linkshare.ui.theme.NougatSurface
import app.linkshare.ui.theme.NougatTeal
import app.linkshare.ui.theme.NougatTealLight
import app.linkshare.ui.theme.NougatTextMuted
import app.linkshare.ui.theme.NougatTextSecondary

@Composable
fun SettingsScreen(
    currentSettings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    val detector = remember { HardwareCapabilityDetector(context) }
    val chipsetDetails = remember { detector.getChipsetDetails() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NougatBackground)
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    focusManager.clearFocus()
                })
            }
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // HEADER
        Column {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Configuration & Hardware Specifications",
                style = MaterialTheme.typography.bodySmall,
                color = NougatTextSecondary
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // DEVICE INFO & NAME INPUT
        SectionLabel("DEVICE IDENTITY")
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = NougatSurface),
            shape = RoundedCornerShape(4.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SettingIcon(Icons.Default.DevicesOther, NougatTeal)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Device Name", fontSize = 13.sp, color = NougatTextSecondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        var nameValue by remember { mutableStateOf(currentSettings.deviceName) }
                        BasicTextField(
                            value = nameValue,
                            onValueChange = { v ->
                                nameValue = v
                                onSettingsChanged(currentSettings.copy(deviceName = v))
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            textStyle = TextStyle(
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            ),
                            cursorBrush = SolidColor(NougatTeal),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(NougatBackground, RoundedCornerShape(4.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // CHIPSET & HARDWARE SPECIFICATIONS CARD
        SectionLabel("HARDWARE CHIPSET CAPABILITIES")
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = NougatSurface),
            shape = RoundedCornerShape(4.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SettingIcon(Icons.Default.Memory, NougatTealLight)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(chipsetDetails.deviceModel, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                        Text("SOC: ${chipsetDetails.hardwareSoc}", fontSize = 12.sp, color = NougatTextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = NougatCardBorder, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(12.dp))

                SpecItem(
                    label = "Wi-Fi Direct P2P",
                    supported = chipsetDetails.supportsP2p,
                    detail = if (chipsetDetails.supportsP2p) "Supported (Direct peer connections)" else "Unsupported"
                )
                Spacer(modifier = Modifier.height(8.dp))
                SpecItem(
                    label = "5 GHz Dual-Band Radio",
                    supported = chipsetDetails.supports5GHz,
                    detail = if (chipsetDetails.supports5GHz) "Supported (High-speed 5 GHz band)" else "2.4 GHz Single-Band Only"
                )
                Spacer(modifier = Modifier.height(8.dp))
                SpecItem(
                    label = "Dual-Link STA+P2P Concurrency",
                    supported = chipsetDetails.supportsDualLink,
                    detail = if (chipsetDetails.supportsDualLink) "Supported by hardware radio" else "Hardware time-shares single radio"
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // FEATURES (BOUND TO HARDWARE CAPABILITIES)
        SectionLabel("PROTOCOL FEATURES")
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = NougatSurface),
            shape = RoundedCornerShape(4.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column {
                SettingToggleItem(
                    icon = Icons.Default.FlashOn,
                    iconColor = if (chipsetDetails.supportsDualLink) NougatGreen else NougatTextMuted,
                    title = "Dual-Link Bonding (F2)",
                    subtitle = if (chipsetDetails.supportsDualLink) "Send over 5 GHz + 2.4 GHz simultaneously" else "Requires 5 GHz Dual-Band STA+P2P hardware chip",
                    checked = currentSettings.enableDualLinkF2 && chipsetDetails.supportsDualLink,
                    enabled = chipsetDetails.supportsDualLink,
                    onCheckedChange = {
                        onSettingsChanged(currentSettings.copy(enableDualLinkF2 = it))
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = NougatCardBorder, thickness = 0.5.dp)

                SettingToggleItem(
                    icon = Icons.Default.Groups,
                    iconColor = NougatPurple,
                    title = "Swarm Transfer (F3)",
                    subtitle = "Multi-peer parallel piece distribution",
                    checked = currentSettings.enableSwarmF3,
                    enabled = true,
                    onCheckedChange = {
                        onSettingsChanged(currentSettings.copy(enableSwarmF3 = it))
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // SERVER SECURITY & IDLE TIMEOUT
        SectionLabel("SERVER TIMEOUT & SECURITY")
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = NougatSurface),
            shape = RoundedCornerShape(4.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column {
                SettingToggleItem(
                    icon = Icons.Default.Lock,
                    iconColor = NougatAmber,
                    title = "Require PIN Authentication",
                    subtitle = "4-digit PIN required for FTP & Web logins",
                    checked = currentSettings.ftpRequirePin,
                    enabled = true,
                    onCheckedChange = {
                        onSettingsChanged(currentSettings.copy(ftpRequirePin = it))
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = NougatCardBorder, thickness = 0.5.dp)

                SettingToggleItem(
                    icon = Icons.Default.FolderOpen,
                    iconColor = NougatTeal,
                    title = "WebDAV Protocol Gateway",
                    subtitle = "Allow macOS Finder, Smart TVs, Kodi, & WebDAV clients",
                    checked = currentSettings.enableWebDav,
                    enabled = true,
                    onCheckedChange = {
                        onSettingsChanged(currentSettings.copy(enableWebDav = it))
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = NougatCardBorder, thickness = 0.5.dp)

                SettingToggleItem(
                    icon = Icons.Default.ContentCopy,
                    iconColor = NougatGreen,
                    title = "Universal LAN Clipboard Sync",
                    subtitle = "Auto copy-paste text between phone & connected PCs",
                    checked = currentSettings.enableClipboardSync,
                    enabled = true,
                    onCheckedChange = {
                        onSettingsChanged(currentSettings.copy(enableClipboardSync = it))
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = NougatCardBorder, thickness = 0.5.dp)

                // BANDWIDTH QOS SPEED LIMITER
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SettingIcon(Icons.Default.Speed, NougatPurple)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("QoS Bandwidth Speed Cap", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
                            Text(
                                text = if (currentSettings.maxSpeedMbps == 0) "Unlimited (Full LAN Speed)" else "${currentSettings.maxSpeedMbps} MB/s max transfer speed",
                                fontSize = 12.sp,
                                color = NougatTextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Slider(
                        value = currentSettings.maxSpeedMbps.toFloat(),
                        onValueChange = { v ->
                            onSettingsChanged(currentSettings.copy(maxSpeedMbps = v.toInt()))
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
        }

        Spacer(modifier = Modifier.height(20.dp))

        // AUDIT LOGS & EXPORT CARD
        SectionLabel("ENTERPRISE AUDIT LOGS")
        Spacer(modifier = Modifier.height(8.dp))

        val logger = remember { app.linkshare.core.logging.AuditLogger(context) }
        var exportStatus by remember { mutableStateOf<String?>(null) }
        val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

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
                        Text("Export transfer event logs, IPs, and SHA-256 hashes", fontSize = 12.sp, color = NougatTextMuted)
                    }

                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                val file = logger.exportLogsToDownloads()
                                exportStatus = if (file != null) "Exported to Downloads/LinkShare/" else "Export failed"
                            }
                        },
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Description, contentDescription = null, tint = NougatTeal, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("EXPORT LOGS", color = NougatTeal, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }

                if (exportStatus != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = exportStatus!!, fontSize = 11.sp, color = NougatGreen, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun SpecItem(label: String, supported: Boolean, detail: String) {
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
fun SectionLabel(title: String) {
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
fun SettingIcon(icon: ImageVector, color: Color) {
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
fun SettingToggleItem(
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
