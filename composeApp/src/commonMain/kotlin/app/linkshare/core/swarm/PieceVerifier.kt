package app.linkshare.core.swarm

import app.linkshare.model.SwarmManifest

/**
 * SHA-256 piece verification for Swarm Transfer integrity (FR3.5).
 */
expect object PieceVerifier {
    fun verifyPiece(manifest: SwarmManifest, pieceIndex: Int, pieceData: ByteArray): Boolean
    fun sha256Hex(data: ByteArray): String
}
