package app.linkshare.model

/**
 * Represents a file in the LinkShare shared directory for the file manager UI.
 * Uses platform-agnostic path strings instead of java.io.File.
 */
data class FileItem(
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val isDirectory: Boolean = false
)
