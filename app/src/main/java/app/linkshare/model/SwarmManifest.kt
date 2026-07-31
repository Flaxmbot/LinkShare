package app.linkshare.model

/**
 * Manifest created by sender prior to Swarm Transfer (F3).
 */
data class SwarmManifest(
    val fileId: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val pieceSize: Int, // e.g. 1MB = 1,048,576 bytes
    val pieceCount: Int,
    val pieceHashes: List<String>, // Hex SHA-256 string for each piece index
    val totalHash: String // Hex SHA-256 string for full file
) {
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
