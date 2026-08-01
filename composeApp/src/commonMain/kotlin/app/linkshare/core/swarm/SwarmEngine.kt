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
    data class PieceRequest(val pieceIndex: Int, val fromPeerId: String)
    private val TAG = "SwarmEngine"

    private val peerBitsets = mutableMapOf<String, PieceBitset>()
    private val reservedPieces = mutableSetOf<Int>()
    private val stateLock = Any()

    private val _transferState = MutableStateFlow<TransferState>(TransferState.Idle)
    val transferState: StateFlow<TransferState> = _transferState.asStateFlow()

    private var localBitset: PieceBitset? = null
    private var currentManifest: SwarmManifest? = null
    private var pieceStore: SwarmPieceStore? = null
    private var startTimeMs: Long = 0L
    private var dualLinkActive: Boolean = false

    fun startSenderSwarm(manifest: SwarmManifest, recipientPeers: List<PeerDevice>) {
        currentManifest = manifest
        localBitset = PieceBitset.allOwned(manifest.pieceCount)
        peerBitsets.clear()
        reservedPieces.clear()
        dualLinkActive = false
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
        reservedPieces.clear()
        dualLinkActive = false
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

    /** Marks that the transport has two independently usable network links. */
    fun setDualLinkActive(active: Boolean) {
        dualLinkActive = active
        val state = _transferState.value
        if (state is TransferState.Transferring) {
            _transferState.value = state.copy(isDualLinkActive = active)
        }
    }

    fun attachPieceStore(store: SwarmPieceStore) {
        require(currentManifest == null || currentManifest == store.manifest) { "Piece store manifest does not match transfer" }
        pieceStore = store
        localBitset = store.piecesOwned
    }

    /** Selects and reserves a rarest available piece for a worker. */
    fun requestNextPiece(): PieceRequest? {
        val local = localBitset ?: return null
        synchronized(stateLock) {
            val candidates = local.getMissingPieceIndices()
                .filter { it !in reservedPieces }
                .flatMap { piece ->
                    peerBitsets.mapNotNull { (peerId, bitset) ->
                        if (bitset.hasPiece(piece)) piece to peerId else null
                    }
                }
                .groupBy({ it.first }, { it.second })
            val piece = candidates.entries
                .sortedWith(compareBy<Map.Entry<Int, List<String>>> { it.value.size }.thenBy { it.key })
                .firstOrNull() ?: return null
            val peerId = piece.value.sorted().first()
            reservedPieces += piece.key
            return PieceRequest(piece.key, peerId)
        }
    }

    fun releasePiece(pieceIndex: Int) {
        synchronized(stateLock) { reservedPieces.remove(pieceIndex) }
    }

    suspend fun onPieceReceived(
        peerId: String,
        pieceIndex: Int,
        pieceData: ByteArray
    ): Boolean = withContext(Dispatchers.Default) {
        val manifest = currentManifest ?: return@withContext false
        val bitset = localBitset ?: return@withContext false

        if (!manifest.isValidPieceIndex(pieceIndex) || pieceData.size != manifest.expectedPieceSize(pieceIndex)) {
            releasePiece(pieceIndex)
            return@withContext false
        }
        if (bitset.hasPiece(pieceIndex)) {
            releasePiece(pieceIndex)
            return@withContext true
        }

        val isValid = PieceVerifier.verifyPiece(manifest, pieceIndex, pieceData)

        if (!isValid) {
            releasePiece(pieceIndex)
            Log.w(TAG, "Corrupt piece $pieceIndex from peer $peerId. Rejecting (FR3.6).")
            return@withContext false
        }

        if (pieceStore != null && !pieceStore!!.writePiece(pieceIndex, pieceData)) {
            releasePiece(pieceIndex)
            return@withContext false
        }

        bitset.setPiece(pieceIndex, true)
        releasePiece(pieceIndex)

        val ownedCount = bitset.countOwned()
        val totalBytesTransferred = bitset.getOwnedPieceIndices()
            .sumOf { manifest.expectedPieceSize(it).toLong() }
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
                isDualLinkActive = dualLinkActive,
                isSwarmActive = true,
                activePeers = peerBitsets.size,
                pieceBitset = bitset
            )
        }
        true
    }

    fun reset() {
        peerBitsets.clear()
        reservedPieces.clear()
        dualLinkActive = false
        localBitset = null
        currentManifest = null
        pieceStore = null
        _transferState.value = TransferState.Idle
    }
}
