package app.linkshare.platform

/**
 * Platform-specific network utilities.
 */
expect object PlatformNetwork {
    data class IpInfo(
        val label: String,
        val ip: String,
        val interfaceName: String
    )

    /** Returns all active, non-loopback IPv4 addresses categorized by interface type */
    fun getAllActiveIpAddresses(): List<IpInfo>

    /** Returns the best single IP for server connections */
    fun getLocalIpAddress(): String
}
