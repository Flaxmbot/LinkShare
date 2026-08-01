package app.linkshare.core.storage

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import app.linkshare.model.FileItem
import app.linkshare.platform.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * ShareME/ShareIT-Style Installed App Sharing & APK Extractor Engine.
 * Inspects installed Android applications and extracts raw .apk files for 1-tap sharing over LAN.
 */
class AppSharingManager(private val context: Context) {

    private val TAG = "AppSharingManager"

    data class InstalledAppInfo(
        val appName: String,
        val packageName: String,
        val apkFiles: List<File>,
        val sizeBytes: Long,
        val isSystemApp: Boolean,
        val versionName: String,
        val versionCode: Long,
        val isAvailable: Boolean
    )

    /**
     * Get list of all shareable installed applications
     */
    suspend fun getInstalledApps(includeSystemApps: Boolean = true): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)
        val appList = mutableListOf<InstalledAppInfo>()

        for (pkg in packages) {
            val appInfo = pkg.applicationInfo ?: continue
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            if (!includeSystemApps && isSystem) continue

            val apkFiles = buildList {
                appInfo.sourceDir?.let { add(File(it)) }
                appInfo.splitSourceDirs?.forEach { add(File(it)) }
            }
            val availableFiles = apkFiles.filter { it.exists() && it.canRead() }

            val label = try {
                pm.getApplicationLabel(appInfo).toString()
            } catch (_: Exception) {
                pkg.packageName
            }

            appList.add(
                InstalledAppInfo(
                    appName = label,
                    packageName = pkg.packageName,
                    apkFiles = availableFiles,
                    sizeBytes = availableFiles.sumOf { it.length() },
                    isSystemApp = isSystem,
                    versionName = pkg.versionName.orEmpty(),
                    versionCode = if (android.os.Build.VERSION.SDK_INT >= 28) pkg.longVersionCode else pkg.versionCode.toLong(),
                    isAvailable = availableFiles.isNotEmpty()
                )
            )
        }

        return@withContext appList.sortedBy { it.appName.lowercase() }
    }

    /**
     * Extract an installed app into Downloads/LinkShare/Apps/<AppName>.apk for sharing
     */
    suspend fun extractAppApk(app: InstalledAppInfo, destinationDirectory: String? = null): FileItem? = withContext(Dispatchers.IO) {
        try {
            val targetDir = if (!destinationDirectory.isNullOrBlank()) File(destinationDirectory) else {
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                File(downloadsDir, "LinkShare/Apps")
            }
            if (!targetDir.exists()) targetDir.mkdirs()

            if (!app.isAvailable) return@withContext null
            val safeName = app.appName.replace(Regex("[^a-zA-Z0-9._-]"), "_") + ".apks"
            val targetFile = File(targetDir, safeName)
            ZipOutputStream(FileOutputStream(targetFile)).use { zip ->
                app.apkFiles.forEachIndexed { index, source ->
                    val entryName = if (index == 0) "base.apk" else "split-$index.apk"
                    zip.putNextEntry(ZipEntry(entryName))
                    source.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
                zip.putNextEntry(ZipEntry("metadata.txt"))
                zip.write("package=${app.packageName}\nversion=${app.versionName}\nversionCode=${app.versionCode}\n".toByteArray())
                zip.closeEntry()
            }

            Log.d(TAG, "Extracted APK for ${app.appName} to ${targetFile.absolutePath}")
            FileItem(
                name = targetFile.name,
                path = targetFile.absolutePath,
                sizeBytes = app.apkFiles.sumOf { it.length() },
                lastModified = targetFile.lastModified(),
                isDirectory = false
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract APK for ${app.appName}: ${e.message}")
            null
        }
    }
}
