package app.linkshare.core.swarm

import app.linkshare.model.PieceBitset
import app.linkshare.model.SwarmManifest

/** Wire-level data shared by direct, dual-link, and multi-peer transfer transports. */
data class SwarmHandshake(
    val sessionId: String,
    val peerId: String,
    val deviceName: String,
    val manifest: SwarmManifest,
    val piecesOwned: String
)

data class SwarmPieceRequest(
    val sessionId: String,
    val pieceIndex: Int,
    val requesterPeerId: String
)

data class SwarmPieceResponse(
    val sessionId: String,
    val pieceIndex: Int,
    val pieceData: ByteArray,
    val sourcePeerId: String
)

data class LinkCapabilities(
    val supportsDualLink: Boolean,
    val linkCount: Int,
    val interfaceNames: List<String>
)

interface SwarmPieceStore {
    val manifest: SwarmManifest
    val piecesOwned: PieceBitset
    suspend fun readPiece(index: Int): ByteArray
    suspend fun writePiece(index: Int, data: ByteArray): Boolean
    suspend fun close()
}
