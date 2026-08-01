package app.linkshare.platform

import app.linkshare.core.swarm.LinkCapabilities

data class IpInfo(
    val label: String,
    val ip: String,
    val interfaceName: String
)

/**
 * Platform-specific network utilities.
 */
expect object PlatformNetwork {
    /** Returns all active, non-loopback IPv4 addresses categorized by interface type */
    fun getAllActiveIpAddresses(): List<IpInfo>

    /** Returns the best single IP for server connections */
    fun getLocalIpAddress(): String

    /** Reports whether two independently usable local interfaces are available. */
    fun getLinkCapabilities(): LinkCapabilities
}
