package app.linkshare.core.swarm

import app.linkshare.model.PieceBitset

/**
 * Rarest-First piece scheduler for Swarm Transfer (F3 / FR3.3).
 * Prioritizes downloading pieces that have the lowest availability count among connected swarm peers.
 */
object RarestFirstScheduler {

    data class PieceRequest(
        val pieceIndex: Int,
        val targetPeerId: String
    )

    /**
     * Selects next rarest piece missing from local bitset that is available on one of connected peers.
     */
    fun selectNextPiece(
        localBitset: PieceBitset,
        peerBitsets: Map<String, PieceBitset>
    ): PieceRequest? {
        val missingIndices = localBitset.getMissingPieceIndices()
        if (missingIndices.isEmpty() || peerBitsets.isEmpty()) return null

        // 1. Calculate frequency distribution for missing pieces across peers
        val pieceAvailability = mutableMapOf<Int, MutableList<String>>() // PieceIndex -> List<PeerId>

        for (pieceIndex in missingIndices) {
            val peersWithPiece = mutableListOf<String>()
            for ((peerId, bitset) in peerBitsets) {
                if (bitset.hasPiece(pieceIndex)) {
                    peersWithPiece.add(peerId)
                }
            }
            if (peersWithPiece.isNotEmpty()) {
                pieceAvailability[pieceIndex] = peersWithPiece
            }
        }

        if (pieceAvailability.isEmpty()) return null

        // 2. Find missing piece with minimum availability (rarest)
        val rarestEntry = pieceAvailability.entries.minByOrNull { it.value.size } ?: return null

        val rarestPieceIndex = rarestEntry.key
        val candidatePeers = rarestEntry.value

        // 3. Pick a candidate peer (e.g. first or round-robin)
        val selectedPeerId = candidatePeers.random()

        return PieceRequest(
            pieceIndex = rarestPieceIndex,
            targetPeerId = selectedPeerId
        )
    }
}
