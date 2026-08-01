package app.linkshare.core.swarm

import app.linkshare.model.PeerDevice
import app.linkshare.model.PieceBitset
import app.linkshare.model.SwarmManifest
import app.linkshare.model.TransferState
import app.linkshare.model.currentTimeMillis
import app.linkshare.platform.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Core BitTorrent-style Swarm Engine (F3).
 */
class SwarmEngine(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + Job())
) {
    private val TAG = "SwarmEngine"

    private val peerBitsets = mutableMapOf<String, PieceBitset>()

    private val _transferState = MutableStateFlow<TransferState>(TransferState.Idle)
    val transferState: StateFlow<TransferState> = _transferState.asStateFlow()

    private var localBitset: PieceBitset? = null
    private var currentManifest: SwarmManifest? = null
    private var startTimeMs: Long = 0L

    fun startSenderSwarm(manifest: SwarmManifest, recipientPeers: List<PeerDevice>) {
        currentManifest = manifest
        localBitset = PieceBitset.allOwned(manifest.pieceCount)
        peerBitsets.clear()
        startTimeMs = currentTimeMillis()

        _transferState.value = TransferState.Transferring(
            fileName = manifest.fileName,
            bytesTransferred = manifest.fileSizeBytes,
            totalBytes = manifest.fileSizeBytes,
            speedBytesPerSec = 0,
            isDualLinkActive = false,
            isSwarmActive = recipientPeers.size > 1,
            activePeers = recipientPeers.size,
            pieceBitset = localBitset
        )
    }

    fun startRecipientSwarm(manifest: SwarmManifest, initialPeers: List<PeerDevice>) {
        currentManifest = manifest
        localBitset = PieceBitset(manifest.pieceCount)
        peerBitsets.clear()
        startTimeMs = currentTimeMillis()

        _transferState.value = TransferState.Transferring(
            fileName = manifest.fileName,
            bytesTransferred = 0L,
            totalBytes = manifest.fileSizeBytes,
            speedBytesPerSec = 0,
            isDualLinkActive = false,
            isSwarmActive = initialPeers.size > 1,
            activePeers = initialPeers.size,
            pieceBitset = localBitset
        )
    }

    fun updatePeerBitset(peerId: String, bitset: PieceBitset) {
        peerBitsets[peerId] = bitset
    }

    suspend fun onPieceReceived(
        peerId: String,
        pieceIndex: Int,
        pieceData: ByteArray
    ): Boolean = withContext(Dispatchers.Default) {
        val manifest = currentManifest ?: return@withContext false
        val bitset = localBitset ?: return@withContext false

        val isValid = PieceVerifier.verifyPiece(manifest, pieceIndex, pieceData)

        if (!isValid) {
            Log.w(TAG, "Corrupt piece $pieceIndex from peer $peerId. Rejecting (FR3.6).")
            return@withContext false
        }

        bitset.setPiece(pieceIndex, true)

        val ownedCount = bitset.countOwned()
        val totalBytesTransferred = minOf(manifest.fileSizeBytes, ownedCount.toLong() * manifest.pieceSize)
        val elapsedSec = maxOf(1L, (currentTimeMillis() - startTimeMs) / 1000)
        val currentSpeed = totalBytesTransferred / elapsedSec

        if (bitset.isComplete()) {
            _transferState.value = TransferState.Completed(
                fileName = manifest.fileName,
                totalBytes = manifest.fileSizeBytes,
                elapsedTimeMs = currentTimeMillis() - startTimeMs,
                averageSpeedBytesPerSec = currentSpeed
            )
        } else {
            _transferState.value = TransferState.Transferring(
                fileName = manifest.fileName,
                bytesTransferred = totalBytesTransferred,
                totalBytes = manifest.fileSizeBytes,
                speedBytesPerSec = currentSpeed,
                isDualLinkActive = false,
                isSwarmActive = true,
                activePeers = peerBitsets.size,
                pieceBitset = bitset
            )
        }
        true
    }

    fun reset() {
        peerBitsets.clear()
        localBitset = null
        currentManifest = null
        _transferState.value = TransferState.Idle
    }
}
