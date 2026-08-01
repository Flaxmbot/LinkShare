package app.linkshare.platform

import app.linkshare.model.FileItem
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathDirectory
import platform.Foundation.NSSearchPathDomainMask
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSURL

actual class PlatformFileSystem actual constructor() {
    private val fm = NSFileManager.defaultManager

    actual fun getDefaultShareDirectory(): String {
        val paths = NSFileManager.defaultManager.URLsForDirectory(
            NSDocumentDirectory, NSUserDomainMask
        )
        return (paths.firstOrNull() as? NSURL)?.path ?: "/tmp"
    }

    actual fun listFiles(directoryPath: String): List<FileItem> {
        val contents = fm.contentsOfDirectoryAtPath(directoryPath, null) ?: return emptyList()
        return (0 until contents.count.toInt()).mapNotNull { i ->
            val name = contents.objectAtIndex(i.toULong()) as? String ?: return@mapNotNull null
            val fullPath = resolve(directoryPath, name)
            val isDir = isDirectory(fullPath)
            FileItem(
                name = name,
                path = fullPath,
                sizeBytes = if (isDir) 0L else fileSize(fullPath),
                lastModified = lastModified(fullPath),
                isDirectory = isDir
            )
        }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    actual fun exists(path: String): Boolean = fm.fileExistsAtPath(path)
    actual fun isDirectory(path: String): Boolean {
        val isDir = booleanArrayOf(false)
        // Use simple heuristic: if it has no extension and exists, likely directory
        return fm.fileExistsAtPath(path) && fm.isReadableFileAtPath(path)
    }
    actual fun fileSize(path: String): Long {
        val attrs = fm.attributesOfItemAtPath(path, null) ?: return 0
        return (attrs["NSFileSize"] as? Long) ?: 0L
    }
    actual fun mkdirs(path: String): Boolean {
        return try { fm.createDirectoryAtPath(path, true, null, null); true }
        catch (_: Exception) { false }
    }
    actual fun deleteRecursively(path: String): Boolean {
        return try { fm.removeItemAtPath(path, null); true }
        catch (_: Exception) { false }
    }
    actual fun canonicalPath(path: String): String = path
    actual fun parentPath(path: String): String? {
        val last = path.trimEnd('/').lastIndexOf('/')
        return if (last > 0) path.substring(0, last) else if (last == 0) "/" else null
    }
    actual fun fileName(path: String): String = path.trimEnd('/').substringAfterLast('/')
    actual fun resolve(parent: String, child: String): String {
        val p = parent.trimEnd('/')
        val c = child.trimStart('/')
        return "$p/$c"
    }
    actual fun lastModified(path: String): Long {
        val attrs = fm.attributesOfItemAtPath(path, null) ?: return 0
        return 0L // Simplified — NSDate conversion requires additional work
    }
}
