package app.linkshare.platform

import app.linkshare.model.FileItem
import java.io.File

actual class PlatformFileSystem actual constructor() {

    actual fun getDefaultShareDirectory(): String {
        val userHome = System.getProperty("user.home") ?: "/"
        val isAndroid = System.getProperty("os.name")?.lowercase()?.contains("linux") == true
                && File("/storage/emulated/0").exists()
        return if (isAndroid) "/storage/emulated/0" else userHome
    }

    actual fun listFiles(directoryPath: String): List<FileItem> {
        val dir = File(directoryPath)
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        return (dir.listFiles() ?: emptyArray())
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            .map { f ->
                FileItem(
                    name = f.name,
                    path = f.absolutePath,
                    sizeBytes = if (f.isDirectory) 0L else f.length(),
                    lastModified = f.lastModified(),
                    isDirectory = f.isDirectory
                )
            }
    }

    actual fun exists(path: String): Boolean = File(path).exists()
    actual fun isDirectory(path: String): Boolean = File(path).isDirectory
    actual fun fileSize(path: String): Long = File(path).length()

    actual fun mkdirs(path: String): Boolean {
        val f = File(path)
        return f.exists() || f.mkdirs()
    }

    actual fun deleteRecursively(path: String): Boolean = File(path).deleteRecursively()

    actual fun canonicalPath(path: String): String = try {
        File(path).canonicalPath
    } catch (_: Exception) { path }

    actual fun parentPath(path: String): String? = File(path).parent

    actual fun fileName(path: String): String = File(path).name

    actual fun resolve(parent: String, child: String): String = File(parent, child).absolutePath

    actual fun lastModified(path: String): Long = File(path).lastModified()
}
