package app.linkshare.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.linkshare.core.storage.RealFileManager
import app.linkshare.core.transport.WifiP2pConnectionManager
import app.linkshare.model.PeerDevice
import app.linkshare.model.PieceBitset
import app.linkshare.model.TransferState
import app.linkshare.ui.theme.NougatAmber
import app.linkshare.ui.theme.NougatBackground
import app.linkshare.ui.theme.NougatCardBorder
import app.linkshare.ui.theme.NougatGreen
import app.linkshare.ui.theme.NougatPurple
import app.linkshare.ui.theme.NougatRed
import app.linkshare.ui.theme.NougatSurface
import app.linkshare.ui.theme.NougatTeal
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import app.linkshare.ui.theme.NougatTealLight
import app.linkshare.ui.theme.NougatTextMuted
import app.linkshare.ui.theme.NougatTextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransferScreen(
    transferState: TransferState,
    connectionState: WifiP2pConnectionManager.ConnectionState,
    selectedFileMeta: RealFileManager.SelectedFileMetaData?,
    selectedPieceSize: Int,
    selectedPeerIds: Set<String>,
    discoveredPeers: List<PeerDevice>,
    onSelectFileClicked: () -> Unit,
    onClearSelectedFile: () -> Unit,
    onPieceSizeSelected: (Int) -> Unit,
    onPeerToggleSelected: (String) -> Unit,
    onStartTransferClicked: () -> Unit,
    onCancelClicked: () -> Unit,
    onOpenFileClicked: (String) -> Unit,
    onOpenFolderClicked: () -> Unit
) {
    var dropdownExpanded by remember { mutableStateOf(false) }
    var showNoRecipientDialog by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NougatBackground)
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // HEADER
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Transfer",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "P2P File Distribution",
                    style = MaterialTheme.typography.bodySmall,
                    color = NougatTextSecondary
                )
            }

            Surface(
                shape = RoundedCornerShape(4.dp),
                color = if (connectionState is WifiP2pConnectionManager.ConnectionState.Connected) NougatGreen.copy(alpha = 0.15f) else NougatTeal.copy(alpha = 0.12f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (connectionState is WifiP2pConnectionManager.ConnectionState.Connected) Icons.Default.Wifi else Icons.Default.WifiOff,
                        contentDescription = null,
                        tint = if (connectionState is WifiP2pConnectionManager.ConnectionState.Connected) NougatGreen else NougatTeal,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (connectionState is WifiP2pConnectionManager.ConnectionState.Connected) "Active" else "Ready",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (connectionState is WifiP2pConnectionManager.ConnectionState.Connected) NougatGreen else NougatTeal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // NO RECIPIENT DIALOG
        if (showNoRecipientDialog) {
            AlertDialog(
                onDismissRequest = { showNoRecipientDialog = false },
                title = { Text("No Recipients", fontWeight = FontWeight.Bold) },
                text = { Text("Select at least one device or connect via Wi-Fi Direct before starting.") },
                confirmButton = {
                    TextButton(onClick = { showNoRecipientDialog = false }) {
                        Text("OK", color = NougatTeal, fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = NougatSurface,
                shape = RoundedCornerShape(4.dp)
            )
        }

        // FILE SELECTION CARD
        if (selectedFileMeta == null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectFileClicked() },
                colors = CardDefaults.cardColors(containerColor = NougatSurface),
                shape = RoundedCornerShape(4.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(NougatTeal.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.UploadFile,
                            contentDescription = null,
                            tint = NougatTeal,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "CHOOSE FILE, PHOTO, OR VIDEO",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        letterSpacing = 1.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap anywhere to select files to send",
                        fontSize = 12.sp,
                        color = NougatTextMuted
                    )
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = NougatSurface),
                shape = RoundedCornerShape(4.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = getFileIcon(selectedFileMeta.fileName),
                                contentDescription = null,
                                tint = NougatTeal,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = selectedFileMeta.fileName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color.White,
                                    maxLines = 1
                                )
                                Text(
                                    text = formatFileSize(selectedFileMeta.fileSizeBytes),
                                    fontSize = 12.sp,
                                    color = NougatTextSecondary
                                )
                            }
                        }
                        IconButton(onClick = onClearSelectedFile) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Clear", tint = NougatTextMuted)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = NougatCardBorder)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Piece Size", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = NougatTextSecondary)

                        Box {
                            OutlinedButton(
                                onClick = { dropdownExpanded = true },
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text("${selectedPieceSize / 1024 / 1024} MB", color = NougatTeal, fontWeight = FontWeight.Bold)
                            }

                            DropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false }
                            ) {
                                listOf(512 * 1024 to "512 KB", 1024 * 1024 to "1 MB", 2048 * 1024 to "2 MB", 4096 * 1024 to "4 MB").forEach { (size, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            onPieceSizeSelected(size)
                                            dropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    val totalPieces = if (selectedFileMeta.fileSizeBytes == 0L) 1 else Math.ceil(selectedFileMeta.fileSizeBytes.toDouble() / selectedPieceSize).toInt()
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$totalPieces piece(s) · SHA-256 validation",
                        fontSize = 12.sp,
                        color = NougatTextMuted
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // RECIPIENTS CARD
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = NougatSurface),
            shape = RoundedCornerShape(4.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("RECIPIENTS", fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp, color = NougatTextSecondary)
                    val modeLabel = if (selectedPeerIds.size > 1) "Swarm" else "Direct"
                    val modeColor = if (selectedPeerIds.size > 1) NougatPurple else NougatTeal
                    StatusChip(icon = Icons.Default.Groups, label = modeLabel, color = modeColor)
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (discoveredPeers.isEmpty()) {
                    Text(
                        text = "No peers discovered. Open LinkShare on the recipient device.",
                        fontSize = 13.sp,
                        color = NougatTextMuted
                    )
                } else {
                    discoveredPeers.forEach { peer ->
                        val isSelected = selectedPeerIds.contains(peer.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPeerToggleSelected(peer.id) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { onPeerToggleSelected(peer.id) },
                                colors = CheckboxDefaults.colors(checkedColor = NougatTeal)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(text = peer.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
                                Text(text = peer.id, fontSize = 11.sp, color = NougatTextMuted)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // START BUTTON
        if (selectedFileMeta != null) {
            Button(
                onClick = {
                    if (selectedPeerIds.isEmpty() && connectionState !is WifiP2pConnectionManager.ConnectionState.Connected) {
                        showNoRecipientDialog = true
                    } else {
                        onStartTransferClicked()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NougatTeal),
                shape = RoundedCornerShape(4.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("START TRANSFER", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 1.sp)
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // TRANSFER PROGRESS / COMPLETION CARD
        AnimatedVisibility(
            visible = transferState !is TransferState.Idle,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut()
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = NougatSurface),
                shape = RoundedCornerShape(4.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    when (transferState) {
                        is TransferState.Connecting -> {
                            Text("Connecting...", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("${transferState.targetPeerCount} peer(s) · ${transferState.fileName}", fontSize = 12.sp, color = NougatTextSecondary)
                            Spacer(modifier = Modifier.height(12.dp))
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = NougatTeal,
                                trackColor = NougatCardBorder
                            )
                        }
                        is TransferState.Transferring -> {
                            val animatedProgress by animateFloatAsState(
                                targetValue = transferState.progressFraction,
                                animationSpec = tween(400),
                                label = "progress"
                            )

                            val speedMBps = transferState.speedBytesPerSec / (1024.0 * 1024.0)
                            val transferredBytes = (transferState.totalBytes * transferState.progressFraction).toLong()
                            val remainingBytes = (transferState.totalBytes - transferredBytes).coerceAtLeast(0L)
                            val etaSeconds = if (transferState.speedBytesPerSec > 0) (remainingBytes / transferState.speedBytesPerSec).toInt() else 0

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = transferState.fileName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${(animatedProgress * 100).toInt()}%",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = NougatTeal
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            LinearProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = NougatTeal,
                                trackColor = NougatCardBorder
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "%.1f MB/s · ETA %02d:%02d".format(speedMBps, etaSeconds / 60, etaSeconds % 60),
                                    fontSize = 12.sp,
                                    color = NougatTealLight
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (transferState.isDualLinkActive) {
                                        StatusChip(icon = Icons.Default.FlashOn, label = "Dual-Link", color = NougatGreen)
                                    }
                                    if (transferState.isSwarmActive) {
                                        StatusChip(icon = Icons.Default.Groups, label = "Swarm (${transferState.activePeers})", color = NougatPurple)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedButton(
                                onClick = onCancelClicked,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(4.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, NougatRed.copy(alpha = 0.5f))
                            ) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = null, tint = NougatRed, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("CANCEL TRANSFER", color = NougatRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                        is TransferState.Completed -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = NougatGreen, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Transfer Complete", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(transferState.fileName, fontSize = 13.sp, color = NougatTextSecondary)
                                Spacer(modifier = Modifier.height(4.dp))
                                val avgMbps = (transferState.averageSpeedBytesPerSec * 8 / 1_000_000.0)
                                Text("%.1f Mbps average".format(avgMbps), color = NougatTeal, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(NougatGreen.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(imageVector = Icons.Default.Folder, contentDescription = null, tint = NougatGreen, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text("Saved to", fontSize = 11.sp, color = NougatGreen)
                                        Text("Downloads/LinkShare/${transferState.fileName}", fontSize = 12.sp, color = Color.White)
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { onOpenFileClicked(transferState.fileName) },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = NougatTeal),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Icon(imageVector = Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("OPEN", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }

                                    OutlinedButton(
                                        onClick = onOpenFolderClicked,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text("FOLDER", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                        is TransferState.Failed -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Error, contentDescription = null, tint = NougatRed, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Transfer Failed", fontWeight = FontWeight.Bold, color = NougatRed, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(transferState.errorMessage, fontSize = 12.sp, color = NougatTextSecondary)
                                }
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    }
}

fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1_073_741_824 -> "%.2f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> "%.2f MB".format(bytes / 1_048_576.0)
        bytes >= 1024 -> "%.2f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
}

fun getFileIcon(fileName: String): androidx.compose.ui.graphics.vector.ImageVector {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "png", "jpg", "jpeg", "webp", "gif" -> Icons.Default.Image
        "mp4", "mkv", "avi", "mov" -> Icons.Default.Movie
        "mp3", "wav", "flac", "aac" -> Icons.Default.AudioFile
        "zip", "rar", "7z", "tar", "gz" -> Icons.Default.FolderZip
        "pdf", "doc", "docx", "txt" -> Icons.Default.Description
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }
}

@Composable
fun StatusChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PieceGrid(bitset: PieceBitset) {
    val total = bitset.totalPieces
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        for (i in 0 until minOf(total, 64)) {
            val isOwned = bitset.hasPiece(i)
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (isOwned) NougatTeal else NougatCardBorder)
            )
        }
    }
}
