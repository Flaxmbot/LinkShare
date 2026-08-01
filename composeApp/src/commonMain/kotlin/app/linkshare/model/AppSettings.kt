package app.linkshare.model

/**
 * Settings configuration for LinkShare app.
 */
data class AppSettings(
    val deviceName: String = "LinkShare-Device",
    val enableDualLinkF2: Boolean = false,
    val enableSwarmF3: Boolean = true,
    val ftpIdleTimeoutMinutes: Int = 10,
    val ftpRequirePin: Boolean = true,
    val maxSpeedMbps: Int = 0,
    val enableWebDav: Boolean = true,
    val enableClipboardSync: Boolean = true
)
