package app.linkshare.model

/**
 * Encapsulates piece ownership for Swarm Transfer (F3).
 * Pure Kotlin implementation replacing java.util.BitSet for multiplatform.
 */
class PieceBitset(val totalPieces: Int) {
    private val bits = BooleanArray(totalPieces)

    @Synchronized
    fun hasPiece(index: Int): Boolean = index in 0 until totalPieces && bits[index]

    @Synchronized
    fun setPiece(index: Int, owned: Boolean = true) {
        if (index in 0 until totalPieces) {
            bits[index] = owned
        }
    }

    @Synchronized
    fun countOwned(): Int = bits.count { it }

    @Synchronized
    fun isComplete(): Boolean = bits.all { it }

    @Synchronized
    fun getOwnedPieceIndices(): List<Int> {
        return bits.indices.filter { bits[it] }
    }

    @Synchronized
    fun getMissingPieceIndices(): List<Int> {
        return bits.indices.filter { !bits[it] }
    }

    @Synchronized
    fun toHexString(): String {
        val byteCount = (totalPieces + 7) / 8
        val bytes = ByteArray(byteCount)
        for (i in 0 until totalPieces) {
            if (bits[i]) {
                bytes[i / 8] = (bytes[i / 8].toInt() or (1 shl (i % 8))).toByte()
            }
        }
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        fun fromHexString(hex: String, totalPieces: Int): PieceBitset {
            val result = PieceBitset(totalPieces)
            if (hex.isBlank()) return result
            try {
                val bytes = ByteArray(hex.length / 2) { i ->
                    hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
                }
                for (i in 0 until totalPieces) {
                    if (i / 8 < bytes.size && (bytes[i / 8].toInt() and (1 shl (i % 8))) != 0) {
                        result.setPiece(i, true)
                    }
                }
            } catch (_: Exception) {}
            return result
        }

        fun allOwned(totalPieces: Int): PieceBitset {
            val bs = PieceBitset(totalPieces)
            for (i in 0 until totalPieces) {
                bs.setPiece(i, true)
            }
            return bs
        }
    }
}
