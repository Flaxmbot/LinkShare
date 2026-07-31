package app.linkshare.core.swarm

import android.util.Log
import app.linkshare.model.PeerDevice
import app.linkshare.model.PieceBitset
import app.linkshare.model.SwarmManifest
import app.linkshare.model.TransferState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Core BitTorrent-style Swarm Engine (F3).
 */
class SwarmEngine(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + Job())
) {
    private val TAG = "SwarmEngine"

    private val peerBitsets = ConcurrentHashMap<String, PieceBitset>()
    
    private val _transferState = MutableStateFlow<TransferState>(TransferState.Idle)
    val transferState: StateFlow<TransferState> = _transferState.asStateFlow()

    private var localBitset: PieceBitset? = null
    private var currentManifest: SwarmManifest? = null
    private var startTimeMs: Long = 0L

    /**
     * Initialize sender side swarm for a multi-recipient share (FR3.1).
     */
    fun startSenderSwarm(manifest: SwarmManifest, recipientPeers: List<PeerDevice>) {
        currentManifest = manifest
        localBitset = PieceBitset.allOwned(manifest.pieceCount)
        peerBitsets.clear()
        startTimeMs = System.currentTimeMillis()

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

    /**
     * Initialize recipient side swarm (FR3.4 late-joiner or initial recipient).
     */
    fun startRecipientSwarm(manifest: SwarmManifest, initialPeers: List<PeerDevice>) {
        currentManifest = manifest
        localBitset = PieceBitset(manifest.pieceCount)
        peerBitsets.clear()
        startTimeMs = System.currentTimeMillis()

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

    /**
     * Process received bitset update from a peer (FR3.2).
     */
    fun updatePeerBitset(peerId: String, bitset: PieceBitset) {
        peerBitsets[peerId] = bitset
    }

    /**
     * Process incoming piece bytes from a peer.
     * Performs hash verification (FR3.5) and handles corruption (FR3.6).
     */
    suspend fun onPieceReceived(
        peerId: String,
        pieceIndex: Int,
        pieceData: ByteArray
    ): Boolean = withContext(Dispatchers.Default) {
        val manifest = currentManifest ?: return@withContext false
        val bitset = localBitset ?: return@withContext false

        // 1. Hash verification (FR3.5)
        val isValid = PieceVerifier.verifyPiece(manifest, pieceIndex, pieceData)

        if (!isValid) {
            Log.w(TAG, "Corrupt piece $pieceIndex received from peer $peerId! Rejecting and re-requesting (FR3.6).")
            // FR3.6: Must NOT mark available or propagate corrupt piece
            return@withContext false
        }

        // 2. Mark piece as owned in bitset (piece data written to disk directly via file manager)
        bitset.setPiece(pieceIndex, true)

        // 3. Calculate byte transfer progress directly from bitset count
        val ownedCount = bitset.countOwned()
        val totalBytesTransferred = minOf(manifest.fileSizeBytes, ownedCount.toLong() * manifest.pieceSize)
        val elapsedSec = maxOf(1L, (System.currentTimeMillis() - startTimeMs) / 1000)
        val currentSpeed = totalBytesTransferred / elapsedSec

        if (bitset.isComplete()) {
            _transferState.value = TransferState.Completed(
                fileName = manifest.fileName,
                totalBytes = manifest.fileSizeBytes,
                elapsedTimeMs = System.currentTimeMillis() - startTimeMs,
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

    /**
     * Select next piece to request using Rarest-First strategy (FR3.3).
     */
    fun selectNextPieceRequest(): RarestFirstScheduler.PieceRequest? {
        val bitset = localBitset ?: return null
        if (bitset.isComplete()) return null
        return RarestFirstScheduler.selectNextPiece(bitset, peerBitsets)
    }

    fun reset() {
        peerBitsets.clear()
        localBitset = null
        currentManifest = null
        _transferState.value = TransferState.Idle
    }
}
