package app.linkshare.core.swarm

import app.linkshare.model.SwarmManifest
import java.security.MessageDigest

actual object PieceVerifier {
    actual fun verifyPiece(manifest: SwarmManifest, pieceIndex: Int, pieceData: ByteArray): Boolean {
        if (!manifest.isValidPieceIndex(pieceIndex)) return false
        val expectedHash = manifest.pieceHashes.getOrNull(pieceIndex) ?: return false
        val actualHash = sha256Hex(pieceData)
        return actualHash.equals(expectedHash, ignoreCase = true)
    }

    actual fun sha256Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(data)
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
