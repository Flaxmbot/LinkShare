package app.linkshare.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.linkshare.core.client.RemoteDeviceClient
import app.linkshare.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun RemoteExplorerScreen(
    peerName: String,
    peerIp: String,
    peerPort: Int = 8888,
    initialPin: String? = null,
    onBackClicked: () -> Unit,
    onSaveFileToLocal: (pin: String, item: RemoteDeviceClient.RemoteFileItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val client = remember { RemoteDeviceClient() }
    val scope = rememberCoroutineScope()

    var currentPath by remember { mutableStateOf("/") }
    var parentPath by remember { mutableStateOf("/") }
    var remoteFiles by remember { mutableStateOf<List<RemoteDeviceClient.RemoteFileItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var currentPin by remember { mutableStateOf(initialPin.orEmpty()) }
    var showPinDialog by remember { mutableStateOf(initialPin.isNullOrBlank()) }

    fun loadDirectory(path: String, pin: String = currentPin) {
        if (pin.isBlank()) {
            showPinDialog = true
            return
        }
        isLoading = true
        errorMessage = null
        scope.launch {
            val res = client.fetchDirectory(peerIp, peerPort, pin, path)
            isLoading = false
            res.onSuccess { data ->
                currentPath = data.currentPath
                parentPath = data.parentPath
                remoteFiles = data.files
            }.onFailure { err ->
                errorMessage = err.message ?: "Failed to connect to peer"
            }
        }
    }

    LaunchedEffect(initialPin) {
        if (!initialPin.isNullOrBlank()) loadDirectory("/", initialPin)
    }

    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { /* Require PIN to proceed */ },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = NougatAmber, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Enter Peer Access PIN", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Enter the 4-digit PIN shown on $peerName ($peerIp)",
                        fontSize = 12.sp,
                        color = NougatTextSecondary
                    )
                    OutlinedTextField(
                        value = currentPin,
                        onValueChange = { if (it.length <= 4) currentPin = it },
                        label = { Text("4-Digit PIN", fontSize = 11.sp, color = NougatTextMuted) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NougatTeal,
                            unfocusedBorderColor = NougatCardBorder,
                            cursorColor = NougatTeal
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (currentPin.isNotBlank()) {
                            showPinDialog = false
                            loadDirectory("/", currentPin)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NougatTeal),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("ACCESS FILES", fontWeight = FontWeight.Bold, color = Color.White)
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NougatBackground)
    ) {
        // ---------- TOP BAR ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(NougatSurface)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClicked) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = peerName, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                Text(text = "Remote File Explorer · $peerIp", fontSize = 11.sp, color = NougatTealLight)
            }
            IconButton(onClick = { loadDirectory(currentPath) }) {
                Icon(imageVector = Icons.Default.Radar, contentDescription = "Refresh", tint = NougatTeal)
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
                text = if (currentPath == "/") "Shared Root" else "Shared Root$currentPath",
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
                    items(remoteFiles) { item ->
                        RemoteFileListItem(
                            item = item,
                            onItemClicked = {
                                if (item.isDirectory) {
                                    loadDirectory(item.path)
                                } else {
                                    onSaveFileToLocal(currentPin, item)
                                }
                            },
                            onDownloadClicked = { onSaveFileToLocal(currentPin, item) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = NougatCardBorder, thickness = 0.5.dp)
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

        if (!item.isDirectory) {
            IconButton(onClick = onDownloadClicked, modifier = Modifier.size(36.dp)) {
                Icon(imageVector = Icons.Default.Download, contentDescription = "Download", tint = NougatTealLight, modifier = Modifier.size(18.dp))
            }
        }
    }
}

fun formatFileSize(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
