package app.linkshare.core.swarm

import app.linkshare.model.SwarmManifest
import platform.Foundation.NSData
import platform.Security.SecDigestTransformCreate
import kotlinx.cinterop.*
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH

actual object PieceVerifier {
    actual fun verifyPiece(manifest: SwarmManifest, pieceIndex: Int, pieceData: ByteArray): Boolean {
        if (!manifest.isValidPieceIndex(pieceIndex)) return false
        val expectedHash = manifest.pieceHashes.getOrNull(pieceIndex) ?: return false
        val actualHash = sha256Hex(pieceData)
        return actualHash.equals(expectedHash, ignoreCase = true)
    }

    actual fun sha256Hex(data: ByteArray): String {
        val digest = UByteArray(CC_SHA256_DIGEST_LENGTH)
        data.usePinned { pinned ->
            digest.usePinned { digestPinned ->
                CC_SHA256(pinned.addressOf(0), data.size.toUInt(), digestPinned.addressOf(0))
            }
        }
        return digest.joinToString("") { it.toString(16).padStart(2, '0') }
    }
}
