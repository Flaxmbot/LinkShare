package app.linkshare.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.linkshare.model.SharedAppInfo
import app.linkshare.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun AppSharingScreen(
    onLoadApps: (suspend () -> List<SharedAppInfo>)? = null,
    onPrepareApp: (suspend (SharedAppInfo) -> String?)? = null,
    onInstallApk: ((String) -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<SharedAppInfo>>(emptyList()) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var loading by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("All") }
    var message by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        val loader = onLoadApps ?: return
        loading = true
        scope.launch {
            apps = loader()
            selected = emptySet()
            loading = false
        }
    }
    LaunchedEffect(onLoadApps) { if (onLoadApps != null) refresh() }

    val visibleApps = apps.filter { app ->
        val matchesQuery = query.isBlank() || app.appName.contains(query, true) || app.packageName.contains(query, true)
        val matchesFilter = filter == "All" || (filter == "System" && app.isSystemApp) || (filter == "User" && !app.isSystemApp)
        matchesQuery && matchesFilter
    }

    Column(Modifier.fillMaxSize().background(NougatBackground).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Apps", color = Color.White, fontSize = 24.sp)
                Text("All installed apps, ready to share", color = NougatTextSecondary, fontSize = 12.sp)
            }
            if (onLoadApps != null) IconButton(onClick = { refresh() }) { Icon(Icons.Default.Refresh, "Refresh", tint = NougatTeal) }
        }
        Spacer(Modifier.height(10.dp))
        if (onLoadApps != null) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search installed apps") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NougatTeal, cursorColor = NougatTeal)
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "User", "System").forEach { value ->
                    FilterChip(selected = filter == value, onClick = { filter = value }, label = { Text(value) })
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (onLoadApps == null) {
            Text("Installed apps are available on Android devices.", color = NougatTextSecondary)
        } else if (loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = NougatTeal)
        } else if (visibleApps.isEmpty()) {
            Text("No installed apps match this view.", color = NougatTextSecondary)
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(visibleApps, key = { it.packageName }) { app ->
                    val checked = app.packageName in selected
                    Card(
                        colors = CardDefaults.cardColors(containerColor = if (checked) NougatTeal.copy(alpha = .16f) else NougatSurface),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.clickable { selected = if (checked) selected - app.packageName else selected + app.packageName }
                    ) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Android, null, tint = NougatTeal, modifier = Modifier.size(34.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(app.appName, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(app.packageName, color = NougatTextMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${app.versionName.ifBlank { "Unknown version" }} · ${app.sizeBytes / (1024 * 1024)} MB · ${if (app.isSystemApp) "System" else "User"}", color = NougatTextSecondary, fontSize = 11.sp)
                            }
                            Checkbox(checked = checked, onCheckedChange = { value -> selected = if (value) selected + app.packageName else selected - app.packageName })
                        }
                    }
                }
            }
        }
        if (selected.isNotEmpty() && onPrepareApp != null) {
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    scope.launch {
                        val chosen = apps.filter { it.packageName in selected }
                        val prepared = chosen.count { onPrepareApp(it) != null }
                        message = "$prepared app bundle${if (prepared == 1) "" else "s"} added to the shared folder"
                        selected = emptySet()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NougatTeal),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Send, null)
                Spacer(Modifier.width(8.dp))
                Text("SEND APPS (${selected.size})")
            }
        }
        message?.let { Text(it, color = NougatTealLight, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
    }
}
