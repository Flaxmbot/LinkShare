package app.linkshare.model

/**
 * Settings configuration for LinkShare app.
 */
data class AppSettings(
    val deviceName: String = "LinkShare-Device",
    val enableDualLinkF2: Boolean = false, // Default OFF as per PRD Section 6 F2
    val enableSwarmF3: Boolean = true,     // Default ON for 2+ recipients as per PRD Section 6 F3
    val ftpIdleTimeoutMinutes: Int = 10,   // Default 10 minutes as per PRD FR8.4
    val ftpRequirePin: Boolean = true,
    val maxSpeedMbps: Int = 0,             // 0 = Unlimited QoS
    val enableWebDav: Boolean = true,      // WebDAV Protocol Gateway
    val enableClipboardSync: Boolean = true // Universal LAN Clipboard Sync
)
