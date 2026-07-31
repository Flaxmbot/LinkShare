package app.linkshare.core.storage

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import app.linkshare.model.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ShareMe-Style Installed App Sharing & APK Extractor Engine.
 * Inspects installed Android applications and extracts raw .apk files for 1-tap sharing over LAN.
 */
class AppSharingManager(private val context: Context) {

    private val TAG = "AppSharingManager"

    data class InstalledAppInfo(
        val appName: String,
        val packageName: String,
        val apkFile: File,
        val sizeBytes: Long,
        val isSystemApp: Boolean
    )

    /**
     * Get list of all shareable installed applications
     */
    suspend fun getInstalledApps(includeSystemApps: Boolean = false): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)
        val appList = mutableListOf<InstalledAppInfo>()

        for (pkg in packages) {
            val appInfo = pkg.applicationInfo ?: continue
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            if (!includeSystemApps && isSystem) continue

            val apkPath = appInfo.sourceDir ?: continue
            val apkFile = File(apkPath)
            if (!apkFile.exists() || !apkFile.canRead()) continue

            val label = try {
                pm.getApplicationLabel(appInfo).toString()
            } catch (_: Exception) {
                pkg.packageName
            }

            appList.add(
                InstalledAppInfo(
                    appName = label,
                    packageName = pkg.packageName,
                    apkFile = apkFile,
                    sizeBytes = apkFile.length(),
                    isSystemApp = isSystem
                )
            )
        }

        return@withContext appList.sortedBy { it.appName.lowercase() }
    }

    /**
     * Extract an installed app into Downloads/LinkShare/Apps/<AppName>.apk for sharing
     */
    suspend fun extractAppApk(app: InstalledAppInfo): FileItem? = withContext(Dispatchers.IO) {
        try {
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val targetDir = File(downloadsDir, "LinkShare/Apps")
            if (!targetDir.exists()) targetDir.mkdirs()

            val safeName = app.appName.replace(Regex("[^a-zA-Z0-9._-]"), "_") + ".apk"
            val targetFile = File(targetDir, safeName)

            if (!targetFile.exists() || targetFile.length() != app.apkFile.length()) {
                app.apkFile.copyTo(targetFile, overwrite = true)
            }

            Log.d(TAG, "Extracted APK for ${app.appName} to ${targetFile.absolutePath}")
            FileItem(
                name = targetFile.name,
                sizeBytes = targetFile.length(),
                lastModified = targetFile.lastModified(),
                file = targetFile
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract APK for ${app.appName}: ${e.message}")
            null
        }
    }
}
