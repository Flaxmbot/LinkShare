package app.linkshare.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.linkshare.model.FileCategory
import app.linkshare.model.FileItem
import app.linkshare.model.SelectableFile
import app.linkshare.platform.PlatformFileSystem
import app.linkshare.ui.theme.*

@Composable
fun SharePickerScreen(
    fileSystem: PlatformFileSystem,
    directory: String,
    onBack: () -> Unit,
    onSend: (List<SelectableFile>) -> Unit,
    modifier: Modifier = Modifier
) {
    var category by remember { mutableStateOf(FileCategory.All) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var currentDirectory by remember { mutableStateOf(directory) }
    var files by remember(currentDirectory) { mutableStateOf(fileSystem.listFiles(currentDirectory)) }

    fun categoryFor(item: FileItem): FileCategory {
        if (item.isDirectory) return FileCategory.Folders
        return when (item.name.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg", "png", "gif", "webp", "heic" -> FileCategory.Photos
            "mp4", "mkv", "mov", "avi", "webm", "3gp" -> FileCategory.Videos
            "mp3", "wav", "flac", "aac", "m4a", "ogg", "opus" -> FileCategory.Music
            "apk", "apks" -> FileCategory.Apps
            "pdf", "doc", "docx", "txt", "csv", "xls", "xlsx", "ppt", "pptx" -> FileCategory.Documents
            else -> FileCategory.All
        }
    }

    val selectable = files.map { item ->
        SelectableFile(item.path, item.name, item.path, item.sizeBytes, item.lastModified, item.isDirectory, categoryFor(item))
    }.filter { item ->
        (category == FileCategory.All || item.category == category) &&
            (query.isBlank() || item.name.contains(query, true))
    }

    Column(modifier.fillMaxSize().background(NougatBackground)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text("Select files", color = Color.White, fontSize = 19.sp)
                Text(currentDirectory, color = NougatTextMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("${selected.size} selected", color = NougatTealLight, fontSize = 12.sp)
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            singleLine = true,
            placeholder = { Text("Search files and folders") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NougatTeal, cursorColor = NougatTeal)
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(FileCategory.All, FileCategory.Photos, FileCategory.Videos, FileCategory.Music, FileCategory.Apps, FileCategory.Documents, FileCategory.Folders).forEach { value ->
                FilterChip(selected = category == value, onClick = { category = value }, label = { Text(value.name, fontSize = 10.sp) })
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(selectable, key = { it.id }) { item ->
                val checked = item.id in selected
                Row(
                    Modifier.fillMaxWidth().background(if (checked) NougatTeal.copy(alpha = .14f) else NougatSurface, RoundedCornerShape(10.dp)).clickable {
                        if (item.isDirectory) {
                            currentDirectory = item.path
                            files = fileSystem.listFiles(currentDirectory)
                        } else selected = if (checked) selected - item.id else selected + item.id
                    }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(if (item.isDirectory) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile, null, tint = if (item.isDirectory) NougatAmber else NougatTealLight, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (item.isDirectory) "Folder" else formatFileSize(item.sizeBytes), color = NougatTextMuted, fontSize = 11.sp)
                    }
                    if (!item.isDirectory) Checkbox(checked = checked, onCheckedChange = { value -> selected = if (value) selected + item.id else selected - item.id })
                }
            }
        }
        if (selected.isNotEmpty()) {
            val selectedItems = files.filter { it.path in selected }.map { item -> SelectableFile(item.path, item.name, item.path, item.sizeBytes, item.lastModified, item.isDirectory, categoryFor(item)) }
            Button(onClick = { onSend(selectedItems) }, modifier = Modifier.fillMaxWidth().padding(16.dp).height(54.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = NougatTeal)) {
                Icon(Icons.Default.Send, null)
                Spacer(Modifier.width(8.dp))
                Text("SEND (${selected.size})", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
        }
    }
}
