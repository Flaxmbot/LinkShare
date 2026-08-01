package app.linkshare.model

sealed class TransferState {
    data object Idle : TransferState()

    data class Connecting(
        val targetPeerCount: Int,
        val fileName: String
    ) : TransferState()

    data class Transferring(
        val fileName: String,
        val bytesTransferred: Long,
        val totalBytes: Long,
        val speedBytesPerSec: Long,
        val isDualLinkActive: Boolean,
        val isSwarmActive: Boolean,
        val activePeers: Int,
        val pieceBitset: PieceBitset? = null
    ) : TransferState() {
        val progressFraction: Float
            get() = if (totalBytes > 0) bytesTransferred.toFloat() / totalBytes.toFloat() else 0f
    }

    data class Completed(
        val fileName: String,
        val totalBytes: Long,
        val elapsedTimeMs: Long,
        val averageSpeedBytesPerSec: Long
    ) : TransferState()

    data class Failed(
        val fileName: String,
        val errorMessage: String
    ) : TransferState()
}
