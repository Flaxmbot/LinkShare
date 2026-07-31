package app.linkshare.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.linkshare.core.client.RemoteDeviceClient
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
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun RemoteExplorerScreen(
    peerName: String,
    peerIp: String,
    peerPort: Int = 8080,
    initialPin: String = "",
    onBackClicked: () -> Unit,
    onSaveFileToLocal: (String, RemoteDeviceClient.RemoteFileItem) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember { RemoteDeviceClient() }

    var currentPin by remember { mutableStateOf(initialPin) }
    var showPinDialog by remember { mutableStateOf(initialPin.isBlank()) }
    var pinInput by remember { mutableStateOf(initialPin) }

    var currentPath by remember { mutableStateOf("/") }
    var parentPath by remember { mutableStateOf("/") }
    var remoteFiles by remember { mutableStateOf<List<RemoteDeviceClient.RemoteFileItem>>(emptyList()) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var streamingMediaItem by remember { mutableStateOf<RemoteDeviceClient.RemoteFileItem?>(null) }

    fun loadDirectory(targetPath: String) {
        isLoading = true
        errorMessage = null
        scope.launch {
            val result = client.fetchRemoteDirectory(peerIp, peerPort, currentPin, targetPath)
            isLoading = false
            result.onSuccess { dirResult ->
                currentPath = dirResult.currentPath
                parentPath = dirResult.parentPath
                remoteFiles = dirResult.files
                showPinDialog = false
            }.onFailure { err ->
                errorMessage = err.message
                if (err.message?.contains("401") == true || err.message?.contains("200") == false) {
                    showPinDialog = true
                }
            }
        }
    }

    LaunchedEffect(currentPin) {
        if (currentPin.isNotBlank()) {
            loadDirectory(currentPath)
        }
    }

    // ---------- PIN ENTRY DIALOG ----------
    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Enter Remote PIN", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column {
                    Text("Enter the 4-digit PIN displayed on $peerName ($peerIp)", fontSize = 13.sp, color = NougatTextSecondary)
                    Spacer(modifier = Modifier.height(12.dp))
                    BasicTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 4) pinInput = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 6.sp
                        ),
                        cursorBrush = SolidColor(NougatTeal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(NougatBackground, RoundedCornerShape(4.dp))
                            .padding(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        currentPin = pinInput
                        loadDirectory(currentPath)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NougatTeal),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("CONNECT", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = onBackClicked) {
                    Text("CANCEL", color = NougatTextSecondary)
                }
            },
            containerColor = NougatSurface,
            shape = RoundedCornerShape(4.dp)
        )
    }

    // ---------- IN-APP MEDIA STREAMER DIALOG ----------
    streamingMediaItem?.let { mediaItem ->
        val streamUrl = client.getStreamUrl(peerIp, peerPort, currentPin, mediaItem.path)
        AlertDialog(
            onDismissRequest = { streamingMediaItem = null },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = mediaItem.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { streamingMediaItem = null }, modifier = Modifier.size(24.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = NougatTextMuted)
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Streaming directly from $peerName without saving",
                        fontSize = 11.sp,
                        color = NougatTealLight
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(Uri.parse(streamUrl), getMimeType(mediaItem.name))
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Opening in external player...", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NougatTeal),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("PLAY IN MEDIA PLAYER", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {},
            containerColor = NougatSurface,
            shape = RoundedCornerShape(4.dp)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NougatBackground)
    ) {
        // ---------- HEADER ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(NougatSurface)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClicked, modifier = Modifier.size(36.dp)) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = peerName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                Text(text = "Remote File Explorer · $peerIp", fontSize = 12.sp, color = NougatTealLight)
            }
            IconButton(onClick = { loadDirectory(currentPath) }, modifier = Modifier.size(36.dp)) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh", tint = NougatTeal)
            }
        }

        HorizontalDivider(color = NougatCardBorder, thickness = 0.5.dp)

        // ---------- ADDRESS BREADCRUMB BAR ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(NougatSurface.copy(alpha = 0.6f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (currentPath != "/") {
                IconButton(
                    onClick = { loadDirectory(parentPath) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Up", tint = NougatTeal, modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = if (currentPath == "/") "Internal Storage" else "Internal Storage$currentPath",
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = NougatTealLight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        HorizontalDivider(color = NougatCardBorder, thickness = 0.5.dp)

        // ---------- CONTENT AREA ----------
        Box(modifier = Modifier.weight(1f)) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NougatTeal)
                }
            } else if (errorMessage != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = NougatRed, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Connection Failed", fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(errorMessage ?: "Unable to connect to peer", fontSize = 12.sp, color = NougatTextMuted)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { showPinDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = NougatTeal),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("RE-ENTER PIN", fontWeight = FontWeight.Bold)
                    }
                }
            } else if (remoteFiles.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("This folder is empty", color = NougatTextMuted, fontSize = 14.sp)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(remoteFiles) { index, item ->
                        RemoteFileListItem(
                            item = item,
                            onItemClicked = {
                                if (item.isDirectory) {
                                    loadDirectory(item.path)
                                } else if (isMediaFile(item.name)) {
                                    streamingMediaItem = item
                                } else {
                                    onSaveFileToLocal(currentPin, item)
                                }
                            },
                            onStreamClicked = { streamingMediaItem = item },
                            onDownloadClicked = { onSaveFileToLocal(currentPin, item) }
                        )
                        if (index < remoteFiles.size - 1) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = NougatCardBorder, thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RemoteFileListItem(
    item: RemoteDeviceClient.RemoteFileItem,
    onItemClicked: () -> Unit,
    onStreamClicked: () -> Unit,
    onDownloadClicked: () -> Unit
) {
    val ext = item.name.substringAfterLast('.', "").lowercase()
    val isMedia = !item.isDirectory && ext in listOf("mp4", "mkv", "webm", "avi", "mov", "mp3", "wav", "flac", "aac")
    val isImg = !item.isDirectory && ext in listOf("jpg", "jpeg", "png", "webp", "gif")

    val iconColor = when {
        item.isDirectory -> NougatAmber
        isImg -> NougatGreen
        isMedia -> NougatPurple
        ext == "pdf" -> NougatRed
        ext == "apk" -> NougatTeal
        else -> NougatTealLight
    }

    val icon = when {
        item.isDirectory -> Icons.Default.Folder
        isImg -> Icons.Default.Image
        isMedia -> if (ext in listOf("mp3", "wav", "flac", "aac")) Icons.Default.AudioFile else Icons.Default.Movie
        ext in listOf("zip", "rar", "7z", "tar", "gz") -> Icons.Default.FolderZip
        ext in listOf("pdf", "doc", "docx", "txt") -> Icons.Default.Description
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onItemClicked)
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
            Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(22.dp))
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (item.isDirectory) "Folder" else formatFileSize(item.sizeBytes),
                fontSize = 12.sp,
                color = NougatTextMuted
            )
        }

        if (isMedia) {
            IconButton(onClick = onStreamClicked, modifier = Modifier.size(36.dp)) {
                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Stream", tint = NougatTeal, modifier = Modifier.size(20.dp))
            }
        }

        if (!item.isDirectory) {
            IconButton(onClick = onDownloadClicked, modifier = Modifier.size(36.dp)) {
                Icon(imageVector = Icons.Default.Download, contentDescription = "Download", tint = NougatTealLight, modifier = Modifier.size(18.dp))
            }
        }
    }
}

private fun isMediaFile(name: String): Boolean {
    val ext = name.substringAfterLast('.', "").lowercase()
    return ext in listOf("mp4", "mkv", "webm", "avi", "mov", "mp3", "wav", "flac", "aac")
}

private fun getMimeType(name: String): String {
    val ext = name.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "mp4" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "avi" -> "video/x-msvideo"
        "mov" -> "video/quicktime"
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "flac" -> "audio/flac"
        "aac" -> "audio/aac"
        else -> "video/*"
    }
}
