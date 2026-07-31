package app.linkshare.model

import java.io.File

/**
 * Represents a file in the LinkShare shared directory for the file manager UI.
 */
data class FileItem(
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val file: File
)
