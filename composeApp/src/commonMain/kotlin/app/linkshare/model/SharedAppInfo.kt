package app.linkshare.model

data class SharedAppInfo(
    val appName: String,
    val packageName: String,
    val sizeBytes: Long,
    val isSystemApp: Boolean = false,
    val versionName: String = "",
    val versionCode: Long = 0L,
    val apkCount: Int = 1,
    val isAvailable: Boolean = true
)
