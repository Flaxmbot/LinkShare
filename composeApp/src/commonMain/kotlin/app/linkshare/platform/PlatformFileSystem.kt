package app.linkshare.platform

import app.linkshare.model.FileItem

/**
 * Platform-specific file system access.
 */
expect class PlatformFileSystem() {
    /** Returns the default directory to share files from */
    fun getDefaultShareDirectory(): String

    /** List files in the given directory path */
    fun listFiles(directoryPath: String): List<FileItem>

    /** Check if a path exists */
    fun exists(path: String): Boolean

    /** Check if a path is a directory */
    fun isDirectory(path: String): Boolean

    /** Get file size in bytes */
    fun fileSize(path: String): Long

    /** Create directory if not exists */
    fun mkdirs(path: String): Boolean

    /** Delete file or directory recursively */
    fun deleteRecursively(path: String): Boolean

    /** Get canonical/absolute path */
    fun canonicalPath(path: String): String

    /** Get parent directory path */
    fun parentPath(path: String): String?

    /** Get file name from path */
    fun fileName(path: String): String

    /** Resolve a child path under a parent */
    fun resolve(parent: String, child: String): String

    /** Get file last modified time */
    fun lastModified(path: String): Long
}
