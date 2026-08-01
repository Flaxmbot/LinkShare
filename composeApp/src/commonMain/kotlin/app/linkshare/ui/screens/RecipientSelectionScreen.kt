package app.linkshare.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.linkshare.model.PeerDevice
import app.linkshare.model.SelectableFile
import app.linkshare.ui.theme.*

@Composable
fun RecipientSelectionScreen(
    files: List<SelectableFile>,
    peers: List<PeerDevice>,
    isSearching: Boolean,
    onScan: () -> Unit,
    onBack: () -> Unit,
    onConfirm: (List<PeerDevice>) -> Unit
) {
    var selected by remember { mutableStateOf(setOf<String>()) }
    Column(Modifier.fillMaxSize().background(NougatBackground).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text("Choose devices", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text("${files.size} selected · ${formatFileSize(files.sumOf { it.sizeBytes })}", color = NougatTextSecondary, fontSize = 12.sp)
            }
            IconButton(onClick = onScan, enabled = !isSearching) { Icon(Icons.Default.Refresh, "Scan", tint = NougatTeal) }
        }
        Card(colors = CardDefaults.cardColors(containerColor = NougatSurface), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Hub, null, tint = NougatTeal, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Swarm-ready transfer", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("You can select more than one recipient", color = NougatTextSecondary, fontSize = 11.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (isSearching) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = NougatTeal) }
        } else if (peers.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.DevicesOther, null, tint = NougatTextMuted, modifier = Modifier.size(46.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("No nearby devices", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("Scan again or connect a device with QR", color = NougatTextMuted, fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(peers, key = { it.id }) { peer ->
                    val checked = peer.id in selected
                    Row(Modifier.fillMaxWidth().background(if (checked) NougatTeal.copy(alpha = .15f) else NougatSurface, RoundedCornerShape(12.dp)).clickable { selected = if (checked) selected - peer.id else selected + peer.id }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(NougatTeal.copy(alpha = .14f)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Smartphone, null, tint = NougatTeal) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(peer.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text(peer.ipAddress ?: "Nearby device", color = NougatTextMuted, fontSize = 11.sp)
                        }
                        Checkbox(checked = checked, onCheckedChange = { value -> selected = if (value) selected + peer.id else selected - peer.id })
                    }
                }
            }
        }
        Button(onClick = { onConfirm(peers.filter { it.id in selected }) }, enabled = selected.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = NougatTeal)) {
            Text("SEND TO ${selected.size} DEVICE${if (selected.size == 1) "" else "S"}", fontWeight = FontWeight.Bold)
        }
    }
}
