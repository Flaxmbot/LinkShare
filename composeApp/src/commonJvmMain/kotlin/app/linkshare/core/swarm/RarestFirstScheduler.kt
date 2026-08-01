package app.linkshare.core.swarm

import app.linkshare.model.PieceBitset
import java.util.concurrent.ConcurrentHashMap

/**
 * Rarest-First Piece Selection Scheduler for Swarm Transfer (F3).
 */
object RarestFirstScheduler {

    data class PieceRequest(
        val pieceIndex: Int,
        val fromPeerId: String
    )

    fun selectNextPiece(
        localBitset: PieceBitset,
        peerBitsets: ConcurrentHashMap<String, PieceBitset>
    ): PieceRequest? {
        val missing = localBitset.getMissingPieceIndices()
        if (missing.isEmpty()) return null

        val pieceCounts = mutableMapOf<Int, MutableList<String>>()
        for (pieceIdx in missing) {
            for ((peerId, peerBs) in peerBitsets) {
                if (peerBs.hasPiece(pieceIdx)) {
                    pieceCounts.getOrPut(pieceIdx) { mutableListOf() }.add(peerId)
                }
            }
        }

        if (pieceCounts.isEmpty()) return null

        val rarest = pieceCounts.minByOrNull { it.value.size } ?: return null
        val selectedPeer = rarest.value.random()

        return PieceRequest(pieceIndex = rarest.key, fromPeerId = selectedPeer)
    }
}
