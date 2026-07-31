package app.linkshare.core.swarm

import app.linkshare.model.SwarmManifest
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * Generates SwarmManifest (F3) from a file or byte stream.
 */
object ManifestGenerator {

    const val DEFAULT_PIECE_SIZE = 1_048_576 // 1MB per piece as per PRD Section 6 F3

    fun generateManifest(file: File, pieceSize: Int = DEFAULT_PIECE_SIZE): SwarmManifest {
        require(file.exists() && file.isFile) { "File must exist and be a regular file" }
        return file.inputStream().use { inputStream ->
            generateManifestFromStream(
                fileId = file.name + "_" + file.length(),
                fileName = file.name,
                fileSizeBytes = file.length(),
                inputStream = inputStream,
                pieceSize = pieceSize
            )
        }
    }

    fun generateManifestFromBytes(
        fileId: String,
        fileName: String,
        bytes: ByteArray,
        pieceSize: Int = DEFAULT_PIECE_SIZE
    ): SwarmManifest {
        return generateManifestFromStream(
            fileId = fileId,
            fileName = fileName,
            fileSizeBytes = bytes.size.toLong(),
            inputStream = bytes.inputStream(),
            pieceSize = pieceSize
        )
    }

    fun generateManifestFromStream(
        fileId: String,
        fileName: String,
        fileSizeBytes: Long,
        inputStream: InputStream,
        pieceSize: Int = DEFAULT_PIECE_SIZE
    ): SwarmManifest {
        val totalPieces = if (fileSizeBytes == 0L) 1 else Math.ceil(fileSizeBytes.toDouble() / pieceSize).toInt()
        val pieceHashes = mutableListOf<String>()
        val totalDigest = MessageDigest.getInstance("SHA-256")

        val buffer = ByteArray(pieceSize)
        var bytesRead: Int

        for (i in 0 until totalPieces) {
            val pieceDigest = MessageDigest.getInstance("SHA-256")
            var currentPieceBytesRead = 0
            val expectedPieceBytes = if (i == totalPieces - 1 && fileSizeBytes > 0) {
                val rem = (fileSizeBytes % pieceSize).toInt()
                if (rem == 0) pieceSize else rem
            } else pieceSize

            while (currentPieceBytesRead < expectedPieceBytes) {
                val toRead = minOf(buffer.size, expectedPieceBytes - currentPieceBytesRead)
                bytesRead = inputStream.read(buffer, 0, toRead)
                if (bytesRead <= 0) break
                pieceDigest.update(buffer, 0, bytesRead)
                totalDigest.update(buffer, 0, bytesRead)
                currentPieceBytesRead += bytesRead
            }
            pieceHashes.add(bytesToHex(pieceDigest.digest()))
        }

        val totalHashHex = bytesToHex(totalDigest.digest())

        return SwarmManifest(
            fileId = fileId,
            fileName = fileName,
            fileSizeBytes = fileSizeBytes,
            pieceSize = pieceSize,
            pieceCount = totalPieces,
            pieceHashes = pieceHashes,
            totalHash = totalHashHex
        )
    }

    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
