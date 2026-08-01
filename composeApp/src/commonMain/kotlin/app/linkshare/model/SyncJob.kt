package app.linkshare.model

enum class SyncDirection { Send, Receive, TwoWay }

data class SyncJob(
    val id: String,
    val deviceId: String,
    val localPath: String,
    val remotePath: String,
    val direction: SyncDirection,
    val enabled: Boolean = true,
    val lastRun: Long = 0L,
    val lastError: String? = null
)
