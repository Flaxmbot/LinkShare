package app.linkshare.core.swarm

import app.linkshare.model.SwarmManifest
import java.security.MessageDigest

/**
 * Hash verifier for received swarm pieces (FR3.5 & FR3.6).
 * Rejects corrupt piece injection.
 */
object PieceVerifier {

    /**
     * Compute hex SHA-256 hash of byte array.
     */
    fun computeSha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        return hash.joinToString("") { "%02x".format(it) }
    }

    /**
     * Verify received piece bytes against expected hash in manifest.
     */
    fun verifyPiece(manifest: SwarmManifest, pieceIndex: Int, data: ByteArray): Boolean {
        if (!manifest.isValidPieceIndex(pieceIndex)) return false
        val expectedHash = manifest.pieceHashes[pieceIndex]
        val computedHash = computeSha256(data)
        return expectedHash.equals(computedHash, ignoreCase = true)
    }

    /**
     * Verify full file content against manifest total Hash.
     */
    fun verifyFullFile(manifest: SwarmManifest, fullFileBytes: ByteArray): Boolean {
        val computedHash = computeSha256(fullFileBytes)
        return manifest.totalHash.equals(computedHash, ignoreCase = true)
    }
}
