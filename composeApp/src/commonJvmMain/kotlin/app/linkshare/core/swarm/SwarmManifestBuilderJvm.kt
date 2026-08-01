package app.linkshare.core.swarm

import app.linkshare.model.SwarmManifest
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object SwarmManifestBuilder {
    const val DEFAULT_PIECE_SIZE = 1024 * 1024

    fun fromFile(file: File, pieceSize: Int = DEFAULT_PIECE_SIZE): SwarmManifest {
        require(file.isFile) { "Transfer source must be a file" }
        require(pieceSize > 0) { "Piece size must be positive" }
        val pieceHashes = mutableListOf<String>()
        val totalDigest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(pieceSize)
            var read: Int
            while (input.read(buffer).also { read = it } > 0) {
                val piece = buffer.copyOf(read)
                pieceHashes += PieceVerifier.sha256Hex(piece)
                totalDigest.update(piece)
            }
        }
        val pieceCount = if (file.length() == 0L) 0 else ((file.length() + pieceSize - 1) / pieceSize).toInt()
        return SwarmManifest(
            fileId = PieceVerifier.sha256Hex("${file.absolutePath}:${file.length()}:${file.lastModified()}".toByteArray()),
            fileName = file.name,
            fileSizeBytes = file.length(),
            pieceSize = pieceSize,
            pieceCount = pieceCount,
            pieceHashes = pieceHashes,
            totalHash = totalDigest.digest().joinToString("") { "%02x".format(it) }
        )
    }
}
