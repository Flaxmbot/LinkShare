package app.linkshare.core.storage

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.util.Log
import app.linkshare.core.swarm.ManifestGenerator
import app.linkshare.model.SwarmManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile

/**
 * Handles real file reading from Storage Access Framework (Content Resolver)
 * and writing incoming swarm pieces to disk storage at exact byte offsets.
 */
class RealFileManager(private val context: Context) {
    private val TAG = "RealFileManager"

    data class SelectedFileMetaData(
        val uri: Uri,
        val fileName: String,
        val fileSizeBytes: Long
    )

    /**
     * Inspect Uri and extract real filename and file size.
     */
    fun getFileMetaData(uri: Uri): SelectedFileMetaData? {
        var name = "shared_file"
        var size = 0L

        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex != -1) name = cursor.getString(nameIndex) ?: name
                if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
            }
        }

        if (size == 0L) {
            try {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { pfd ->
                    size = pfd.length
                }
            } catch (_: Exception) {}
        }

        return SelectedFileMetaData(uri, name, size)
    }

    /**
     * Generate real SwarmManifest from an actual Content Uri.
     */
    suspend fun createManifestFromUri(uri: Uri, pieceSize: Int = ManifestGenerator.DEFAULT_PIECE_SIZE): SwarmManifest? = withContext(Dispatchers.IO) {
        val metaData = getFileMetaData(uri) ?: return@withContext null
        val inputStream: InputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null

        return@withContext inputStream.use { stream ->
            ManifestGenerator.generateManifestFromStream(
                fileId = "${metaData.fileName}_${metaData.fileSizeBytes}_${System.currentTimeMillis()}",
                fileName = metaData.fileName,
                fileSizeBytes = metaData.fileSizeBytes,
                inputStream = stream,
                pieceSize = pieceSize
            )
        }
    }

    /**
     * Read specific piece byte chunk from Uri.
     */
    suspend fun readPieceFromUri(uri: Uri, pieceIndex: Int, pieceSize: Int): ByteArray? = withContext(Dispatchers.IO) {
        val inputStream: InputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
        val offset = pieceIndex.toLong() * pieceSize

        return@withContext inputStream.use { stream ->
            stream.skip(offset)
            val buffer = ByteArray(pieceSize)
            var totalRead = 0
            while (totalRead < pieceSize) {
                val read = stream.read(buffer, totalRead, pieceSize - totalRead)
                if (read <= 0) break
                totalRead += read
            }
            if (totalRead == pieceSize) buffer else buffer.copyOf(totalRead)
        }
    }

    /**
     * Get primary internal storage root (/storage/emulated/0).
     */
    fun getRootStorageDirectory(): File {
        val root = Environment.getExternalStorageDirectory()
        if (root != null && root.canRead()) {
            return root
        }
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val linkShareDir = File(downloadsDir, "LinkShare")
        if (!linkShareDir.exists()) {
            linkShareDir.mkdirs()
        }
        return linkShareDir
    }

    /**
     * Get or create real output file in Downloads/LinkShare/
     */
    fun getDownloadsTargetFile(fileName: String): File {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val linkShareDir = File(downloadsDir, "LinkShare")
        if (!linkShareDir.exists()) {
            linkShareDir.mkdirs()
        }
        val target = File(linkShareDir, fileName)
        require(isSafePath(linkShareDir, target)) { "Illegal file path outside base directory: $fileName" }
        return target
    }

    /**
     * Security check against directory traversal attacks (e.g. "../../etc/passwd").
     */
    fun isSafePath(baseDir: File, targetFile: File): Boolean {
        return try {
            val baseCanonical = baseDir.canonicalPath
            val targetCanonical = targetFile.canonicalPath
            targetCanonical.startsWith(baseCanonical)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Write piece bytes to output file at exact offset (pieceIndex * pieceSize).
     */
    suspend fun writePieceToFile(targetFile: File, pieceIndex: Int, pieceSize: Int, data: ByteArray) = withContext(Dispatchers.IO) {
        val offset = pieceIndex.toLong() * pieceSize
        RandomAccessFile(targetFile, "rw").use { raf ->
            raf.seek(offset)
            raf.write(data)
        }
        Log.d(TAG, "Wrote piece $pieceIndex (${data.size} bytes) to ${targetFile.absolutePath} at offset $offset")
    }
}
