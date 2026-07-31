package app.linkshare.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Upload
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.linkshare.core.transport.NetworkUtils
import app.linkshare.model.FileItem
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
fun LocalServerScreen(
    isFtpRunning: Boolean,
    isHttpRunning: Boolean,
    activeIpList: List<NetworkUtils.IpInfo>,
    ftpPin: String?,
    httpQrBitmap: Bitmap?,
    onStartServers: () -> Unit,
    onStopServers: () -> Unit,
    onCopyToClipboard: (String) -> Unit,
    // File Manager
    sharedFiles: List<FileItem>,
    onRefreshFiles: () -> Unit,
    onDeleteFile: (FileItem) -> Unit,
    onShareFile: (FileItem) -> Unit,
    onAddFile: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf<FileItem?>(null) }
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
                    text = "Server",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "FTP & HTTP File Server",
                    style = MaterialTheme.typography.bodySmall,
                    color = NougatTextSecondary
                )
            }

            Surface(
                shape = RoundedCornerShape(4.dp),
                color = if (isFtpRunning) NougatGreen.copy(alpha = 0.15f) else NougatRed.copy(alpha = 0.12f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isFtpRunning) NougatGreen else NougatRed)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isFtpRunning) "Online" else "Offline",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isFtpRunning) NougatGreen else NougatRed
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // SERVER TOGGLE
        Button(
            onClick = {
                if (isFtpRunning || isHttpRunning) onStopServers() else onStartServers()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isFtpRunning) NougatRed else NougatTeal
            ),
            shape = RoundedCornerShape(4.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PowerSettingsNew,
                contentDescription = null,
                tint = Color.White
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isFtpRunning) "STOP SERVERS" else "START SERVERS",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                letterSpacing = 1.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // CONNECTION INFO
        if (isFtpRunning || isHttpRunning) {
            val primaryIpInfo = activeIpList.firstOrNull()
            val primaryIp = primaryIpInfo?.ip ?: "192.168.49.1"

            // PROMINENT SECURITY PIN CARD
            if (ftpPin != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = NougatTeal.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, NougatTeal.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = NougatTeal, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("SECURITY PIN", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = NougatTealLight, letterSpacing = 1.sp)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Enter this PIN when prompted on laptop or phone",
                                fontSize = 11.sp,
                                color = NougatTextSecondary
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = NougatTeal
                        ) {
                            Text(
                                text = ftpPin,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = Color.White,
                                letterSpacing = 4.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            // FTP Card
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SettingIcon(Icons.Default.Storage, NougatTeal)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("FTP FILE SERVER", fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp, color = NougatTextSecondary)
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = NougatGreen.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "WINDOWS FILE EXPLORER",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = NougatGreen,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val ftpUri = "ftp://${primaryIpInfo?.ip ?: primaryIp}:2121"
                    val isWifiDirect = primaryIpInfo?.label?.contains("Direct", ignoreCase = true) == true
                    val contextTag = if (isWifiDirect) "📱 Phone-to-Phone" else "💻 Laptop / PC Network"

                    AddressRow(label = "$contextTag (${primaryIpInfo?.label ?: "Primary"})", uri = ftpUri, onCopy = onCopyToClipboard)

                    if (activeIpList.size > 1) {
                        Spacer(modifier = Modifier.height(6.dp))
                        val altInfo = activeIpList[1]
                        val altTag = if (altInfo.label.contains("Direct", ignoreCase = true)) "📱 Direct" else "💻 Laptop Network"
                        AddressRow(label = "$altTag (${altInfo.label})", uri = "ftp://${altInfo.ip}:2121", onCopy = onCopyToClipboard)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // HTTP Card
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SettingIcon(Icons.Default.Http, NougatPurple)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("WEB PORTAL", fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp, color = NougatTextSecondary)
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = NougatGreen.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "PORT 8080",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = NougatGreen,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val httpUri = "http://${primaryIpInfo?.ip ?: primaryIp}:8080?pin=${ftpPin ?: ""}"
                    AddressRow(label = primaryIpInfo?.label ?: "Primary Network", uri = httpUri, onCopy = onCopyToClipboard)

                    if (activeIpList.size > 1) {
                        Spacer(modifier = Modifier.height(4.dp))
                        val altInfo = activeIpList[1]
                        Text(
                            text = "Alt Network (${altInfo.label}): http://${altInfo.ip}:8080?pin=${ftpPin ?: ""}",
                            fontSize = 11.sp,
                            color = NougatTextMuted
                        )
                    }

                    // QR Code
                    httpQrBitmap?.let { bmp ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White, RoundedCornerShape(4.dp))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "QR Code",
                                modifier = Modifier.size(180.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Scan with phone camera to open web portal",
                            fontSize = 12.sp,
                            color = NougatTextMuted,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        // UNIVERSAL LAN CLIPBOARD SYNC CARD
        var localClipboard by remember { mutableStateOf("Copy text on phone or PC to sync instantly across LAN") }
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, tint = NougatTeal, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "UNIVERSAL LAN CLIPBOARD",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 1.sp,
                            color = NougatTextSecondary
                        )
                    }
                    Surface(shape = RoundedCornerShape(4.dp), color = NougatGreen.copy(alpha = 0.15f)) {
                        Text("ACTIVE SYNC", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = NougatGreen, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NougatBackground, RoundedCornerShape(4.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = localClipboard,
                        fontSize = 12.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { onCopyToClipboard(localClipboard) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy", tint = NougatTeal, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // LOCAL STORAGE & RECEIVED FILES MANAGER SECTION
        var selectedCategory by remember { mutableStateOf("All") }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "RECEIVED & LOCAL FILES (${sharedFiles.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp,
                    color = NougatTextSecondary
                )
                Text(
                    text = "Downloads/LinkShare/",
                    fontSize = 11.sp,
                    color = NougatTealLight
                )
            }

            Row {
                IconButton(onClick = onRefreshFiles, modifier = Modifier.size(32.dp)) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh", tint = NougatTeal, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(onClick = onAddFile, modifier = Modifier.size(32.dp)) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add file", tint = NougatTeal, modifier = Modifier.size(18.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // CATEGORY FILTER CHIPS
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("All", "Apps", "Photos", "Videos", "Documents").forEach { cat ->
                val isSelected = selectedCategory == cat
                Surface(
                    modifier = Modifier.clickable { selectedCategory = cat },
                    shape = RoundedCornerShape(4.dp),
                    color = if (isSelected) NougatTeal else NougatSurface,
                    border = BorderStroke(0.5.dp, if (isSelected) NougatTeal else NougatCardBorder)
                ) {
                    Text(
                        text = cat,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color.White else NougatTextSecondary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // DELETE CONFIRMATION DIALOG
        showDeleteDialog?.let { fileToDelete ->
            AlertDialog(
                onDismissRequest = { showDeleteDialog = null },
                title = { Text("Delete File?", fontWeight = FontWeight.Bold, color = Color.White) },
                text = {
                    Text("\"${fileToDelete.name}\" will be permanently removed from storage. This cannot be undone.", color = NougatTextSecondary)
                },
                confirmButton = {
                    TextButton(onClick = {
                        onDeleteFile(fileToDelete)
                        showDeleteDialog = null
                    }) {
                        Text("DELETE", color = NougatRed, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = null }) {
                        Text("CANCEL", color = NougatTextSecondary)
                    }
                },
                containerColor = NougatSurface,
                shape = RoundedCornerShape(4.dp)
            )
        }

        val filteredFiles = remember(sharedFiles, selectedCategory) {
            when (selectedCategory) {
                "Apps" -> sharedFiles.filter { f -> f.name.substringAfterLast('.', "").lowercase() == "apk" }
                "Photos" -> sharedFiles.filter { f -> f.name.substringAfterLast('.', "").lowercase() in listOf("jpg", "jpeg", "png", "webp", "gif") }
                "Videos" -> sharedFiles.filter { f -> f.name.substringAfterLast('.', "").lowercase() in listOf("mp4", "mkv", "webm", "avi", "mov") }
                "Documents" -> sharedFiles.filter { f -> f.name.substringAfterLast('.', "").lowercase() in listOf("pdf", "doc", "docx", "txt", "zip") }
                else -> sharedFiles
            }
        }

        if (filteredFiles.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = NougatSurface),
                shape = RoundedCornerShape(4.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = NougatTextMuted,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (selectedCategory == "All") "No files in storage folder" else "No $selectedCategory files found",
                        fontWeight = FontWeight.SemiBold,
                        color = NougatTextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onAddFile,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = NougatTeal, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ADD FILES", color = NougatTeal, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = NougatSurface),
                shape = RoundedCornerShape(4.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column {
                    filteredFiles.forEachIndexed { index, fileItem ->
                        FileListItem(
                            fileItem = fileItem,
                            onShareClicked = { onShareFile(fileItem) },
                            onDeleteClicked = { showDeleteDialog = fileItem }
                        )
                        if (index < filteredFiles.size - 1) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = NougatCardBorder, thickness = 0.5.dp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun AddressRow(
    label: String,
    uri: String,
    onCopy: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NougatBackground, RoundedCornerShape(4.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(text = label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NougatTextMuted)
        Spacer(modifier = Modifier.height(2.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = uri,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = NougatTealLight,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(
                onClick = { onCopy(uri) },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy", tint = NougatTeal, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun FileListItem(
    fileItem: FileItem,
    onShareClicked: () -> Unit,
    onDeleteClicked: () -> Unit
) {
    val ext = fileItem.name.substringAfterLast('.', "").lowercase()
    val iconColor = when (ext) {
        "jpg", "jpeg", "png", "webp", "gif" -> NougatGreen
        "mp4", "mkv", "avi", "mov" -> NougatPurple
        "mp3", "wav", "flac", "aac" -> NougatAmber
        "zip", "rar", "7z", "tar", "gz" -> NougatAmber
        "pdf" -> NougatRed
        "apk" -> NougatTeal
        else -> NougatTealLight
    }

    val fileIcon = when (ext) {
        "jpg", "jpeg", "png", "webp", "gif" -> Icons.Default.Image
        "mp4", "mkv", "avi", "mov" -> Icons.Default.Movie
        "mp3", "wav", "flac", "aac" -> Icons.Default.AudioFile
        "zip", "rar", "7z", "tar", "gz" -> Icons.Default.FolderZip
        "pdf", "doc", "docx", "txt" -> Icons.Default.Description
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(iconColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = fileIcon, contentDescription = null, tint = iconColor, modifier = Modifier.size(22.dp))
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = fileItem.name,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatFileSize(fileItem.sizeBytes),
                fontSize = 12.sp,
                color = NougatTextMuted
            )
        }

        IconButton(onClick = onShareClicked, modifier = Modifier.size(36.dp)) {
            Icon(imageVector = Icons.Default.Share, contentDescription = "Share", tint = NougatTeal, modifier = Modifier.size(18.dp))
        }

        IconButton(onClick = onDeleteClicked, modifier = Modifier.size(36.dp)) {
            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = NougatRed.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
        }
    }
}
