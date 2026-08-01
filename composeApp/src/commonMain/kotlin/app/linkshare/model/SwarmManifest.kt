package app.linkshare.model

/**
 * Manifest created by sender prior to Swarm Transfer (F3).
 */
data class SwarmManifest(
    val fileId: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val pieceSize: Int,
    val pieceCount: Int,
    val pieceHashes: List<String>,
    val totalHash: String
) {
    init {
        require(fileSizeBytes >= 0) { "File size cannot be negative" }
        require(pieceSize > 0) { "Piece size must be positive" }
        require(pieceCount == if (fileSizeBytes == 0L) 0 else ((fileSizeBytes + pieceSize - 1) / pieceSize).toInt()) {
            "Piece count does not match file size"
        }
        require(pieceHashes.size == pieceCount) { "Every piece must have a hash" }
    }

    fun isValidPieceIndex(index: Int): Boolean = index in 0 until pieceCount

    fun expectedPieceSize(index: Int): Int {
        require(isValidPieceIndex(index)) { "Invalid piece index: $index" }
        return if (index == pieceCount - 1) {
            val remainder = (fileSizeBytes % pieceSize).toInt()
            if (remainder == 0) pieceSize else remainder
        } else {
            pieceSize
        }
    }
}
