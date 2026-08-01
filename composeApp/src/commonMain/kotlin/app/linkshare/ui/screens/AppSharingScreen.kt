package app.linkshare.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        val loader = onLoadApps ?: return
        loading = true
        scope.launch {
            apps = loader()
            loading = false
        }
    }
    LaunchedEffect(onLoadApps) { if (onLoadApps != null) refresh() }

    Column(Modifier.fillMaxSize().background(NougatBackground).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Apps", color = Color.White, fontSize = 24.sp)
                Text("Share installed Android apps as APK files", color = NougatTextSecondary, fontSize = 12.sp)
            }
            if (onLoadApps != null) IconButton(onClick = { refresh() }) { Icon(Icons.Default.Refresh, "Refresh", tint = NougatTeal) }
        }
        Spacer(Modifier.height(12.dp))
        if (onLoadApps == null) {
            Text("App sharing is available on Android devices.", color = NougatTextSecondary)
        } else if (loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = NougatTeal)
        } else if (apps.isEmpty()) {
            Text("No user-installed apps were found.", color = NougatTextSecondary)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(apps, key = { it.packageName }) { app ->
                    Card(colors = CardDefaults.cardColors(containerColor = NougatSurface)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Android, null, tint = NougatTeal, modifier = Modifier.size(30.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(app.appName, color = Color.White)
                                Text("${app.packageName} • ${app.sizeBytes / (1024 * 1024)} MB", color = NougatTextMuted, fontSize = 11.sp)
                            }
                            IconButton(onClick = {
                                val preparer = onPrepareApp ?: return@IconButton
                                scope.launch {
                                    message = if (preparer(app) != null) "APK added to the shared folder" else "Could not prepare APK"
                                }
                            }) { Icon(Icons.Default.Send, "Share app", tint = NougatTeal) }
                        }
                    }
                }
            }
        }
        if (message != null) Text(message!!, color = NougatTealLight, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
    }
}
