package app.linkshare.model

/**
 * Represents a discovered peer device in LinkShare.
 */
data class PeerDevice(
    val id: String,
    val name: String,
    val ipAddress: String? = null,
    val port: Int = 8888,
    val appVersion: String = "1.0.0",
    val supportsF2DualLink: Boolean = false,
    val supportsF3Swarm: Boolean = true,
    val ftpServerActive: Boolean = false,
    val lastSeenTimestamp: Long = currentTimeMillis(),
    val signalDbm: Int = -60,
    val accessPin: String? = null,
    val accessToken: String? = null
)

internal expect fun currentTimeMillis(): Long
