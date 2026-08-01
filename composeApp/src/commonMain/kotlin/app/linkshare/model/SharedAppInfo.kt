package app.linkshare.model

data class SharedAppInfo(
    val appName: String,
    val packageName: String,
    val sizeBytes: Long,
    val isSystemApp: Boolean = false
)
