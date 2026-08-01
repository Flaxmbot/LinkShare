package app.linkshare.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.linkshare.model.PieceBitset
import app.linkshare.model.TransferState
import app.linkshare.ui.theme.*

@Composable
fun TransferScreen(
    transferState: TransferState,
    onCancelTransfer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NougatBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Transfers",
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 1.sp,
            color = NougatTextSecondary
        )

        Spacer(modifier = Modifier.height(12.dp))

        when (transferState) {
            is TransferState.Idle -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
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
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = null,
                            tint = NougatTextMuted,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Nothing is moving right now", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Browse a nearby device to download files here.",
                            fontSize = 12.sp,
                            color = NougatTextMuted
                        )
                    }
                }
            }

            is TransferState.Connecting -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = NougatSurface),
                    shape = RoundedCornerShape(4.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = NougatTeal)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Connecting…", fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(transferState.fileName, fontSize = 12.sp, color = NougatTealLight, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            is TransferState.Transferring -> {
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
                                Icon(imageVector = Icons.Default.Download, contentDescription = null, tint = NougatTeal, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = transferState.fileName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color.White
                                )
                            }
                            IconButton(onClick = onCancelTransfer, modifier = Modifier.size(28.dp)) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Cancel", tint = NougatRed)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Progress Bar
                        LinearProgressIndicator(
                            progress = { transferState.progressFraction },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = NougatTeal,
                            trackColor = NougatBackground
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${formatFileSize(transferState.bytesTransferred)} / ${formatFileSize(transferState.totalBytes)}",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = NougatTextSecondary
                            )
                            Text(
                                text = "${formatFileSize(transferState.speedBytesPerSec)}/s",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = NougatGreen
                            )
                        }

                        if (transferState.pieceBitset != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "SWARM PIECE BITSET (${transferState.pieceBitset.countOwned()}/${transferState.pieceBitset.totalPieces})",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = NougatTextMuted
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            PieceBitsetGrid(bitset = transferState.pieceBitset)
                        }
                    }
                }
            }

            is TransferState.Completed -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = NougatSurface),
                    shape = RoundedCornerShape(4.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = NougatGreen, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Transfer complete", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(transferState.fileName, fontSize = 12.sp, color = NougatTealLight)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Avg Speed: ${formatFileSize(transferState.averageSpeedBytesPerSec)}/s · Time: ${transferState.elapsedTimeMs / 1000}s",
                            fontSize = 11.sp,
                            color = NougatTextMuted
                        )
                    }
                }
            }

            is TransferState.Failed -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = NougatSurface),
                    shape = RoundedCornerShape(4.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(imageVector = Icons.Default.Error, contentDescription = null, tint = NougatRed, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Transfer Failed", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(transferState.errorMessage, fontSize = 12.sp, color = NougatRed)
                    }
                }
            }
        }
    }
}

@Composable
fun PieceBitsetGrid(bitset: PieceBitset) {
    val total = bitset.totalPieces
    val columns = 16
    val rows = (total + columns - 1) / columns

    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        for (r in 0 until minOf(rows, 8)) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                for (c in 0 until columns) {
                    val idx = r * columns + c
                    if (idx < total) {
                        val owned = bitset.hasPiece(idx)
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (owned) NougatGreen else NougatCardBorder)
                        )
                    }
                }
            }
        }
    }
}
