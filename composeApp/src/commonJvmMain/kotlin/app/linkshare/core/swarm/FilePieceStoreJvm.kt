package app.linkshare.core.swarm

import app.linkshare.model.PieceBitset
import app.linkshare.model.SwarmManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

/** Resumable piece store shared by Android and Desktop JVM targets. */
class FilePieceStore(
    private val dataFile: File,
    override val manifest: SwarmManifest,
    existingPieces: PieceBitset = PieceBitset(manifest.pieceCount)
) : SwarmPieceStore {
    private val file = RandomAccessFile(dataFile, "rw")
    override val piecesOwned: PieceBitset = existingPieces

    init {
        if (file.length() != manifest.fileSizeBytes) file.setLength(manifest.fileSizeBytes)
        // Rebuild ownership from verified bytes so interrupted downloads resume safely.
        for (index in 0 until manifest.pieceCount) {
            val data = ByteArray(manifest.expectedPieceSize(index))
            try {
                synchronized(file) {
                    file.seek(index.toLong() * manifest.pieceSize)
                    file.readFully(data)
                }
                if (PieceVerifier.verifyPiece(manifest, index, data)) piecesOwned.setPiece(index)
            } catch (_: Exception) {
                // A missing or incomplete piece remains unowned and will be requested again.
            }
        }
    }

    override suspend fun readPiece(index: Int): ByteArray = withContext(Dispatchers.IO) {
        require(manifest.isValidPieceIndex(index))
        val size = manifest.expectedPieceSize(index)
        val data = ByteArray(size)
        synchronized(file) {
            file.seek(index.toLong() * manifest.pieceSize)
            file.readFully(data)
        }
        data
    }

    override suspend fun writePiece(index: Int, data: ByteArray): Boolean = withContext(Dispatchers.IO) {
        if (!manifest.isValidPieceIndex(index) || data.size != manifest.expectedPieceSize(index)) return@withContext false
        if (!PieceVerifier.verifyPiece(manifest, index, data)) return@withContext false
        synchronized(file) {
            file.seek(index.toLong() * manifest.pieceSize)
            file.write(data)
        }
        piecesOwned.setPiece(index)
        true
    }

    override suspend fun close() = withContext(Dispatchers.IO) {
        synchronized(file) { file.fd.sync(); file.close() }
    }
}
