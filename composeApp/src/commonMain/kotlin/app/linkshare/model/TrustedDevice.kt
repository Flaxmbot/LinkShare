package app.linkshare.model

enum class DevicePermission { ReadOnly, UploadOnly, ReadWrite, MediaStreaming, Admin }

data class TrustedDevice(
    val deviceId: String,
    val name: String,
    val platform: String,
    val host: String? = null,
    val port: Int = 8888,
    val permissions: Set<DevicePermission> = setOf(DevicePermission.ReadOnly),
    val isOnline: Boolean = false,
    val lastSeen: Long = 0L,
    val supportsSwarm: Boolean = true,
    val supportsDualLink: Boolean = false
)

data class PairingPayload(
    val version: Int,
    val deviceId: String,
    val deviceName: String,
    val host: String,
    val port: Int,
    val dualLinkPort: Int,
    val sessionPin: String,
    val nonce: String,
    val expiresAt: Long,
    val capabilities: Set<String>,
    val signature: String
)
