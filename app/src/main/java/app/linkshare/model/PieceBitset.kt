package app.linkshare.model

import java.util.BitSet

/**
 * Encapsulates piece ownership for Swarm Transfer (F3).
 */
class PieceBitset(val totalPieces: Int) {
    private val bitSet = BitSet(totalPieces)

    fun hasPiece(index: Int): Boolean = index in 0 until totalPieces && bitSet.get(index)

    fun setPiece(index: Int, owned: Boolean = true) {
        if (index in 0 until totalPieces) {
            bitSet.set(index, owned)
        }
    }

    fun countOwned(): Int = bitSet.cardinality()

    fun isComplete(): Boolean = bitSet.cardinality() == totalPieces

    fun getOwnedPieceIndices(): List<Int> {
        val list = mutableListOf<Int>()
        var i = bitSet.nextSetBit(0)
        while (i >= 0 && i < totalPieces) {
            list.add(i)
            i = bitSet.nextSetBit(i + 1)
        }
        return list
    }

    fun getMissingPieceIndices(): List<Int> {
        val list = mutableListOf<Int>()
        for (i in 0 until totalPieces) {
            if (!bitSet.get(i)) {
                list.add(i)
            }
        }
        return list
    }

    fun toHexString(): String {
        val bytes = bitSet.toByteArray()
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
                val bs = BitSet.valueOf(bytes)
                for (i in 0 until totalPieces) {
                    if (bs.get(i)) {
                        result.setPiece(i, true)
                    }
                }
            } catch (_: Exception) {
                // Return empty bitset on parse error
            }
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
