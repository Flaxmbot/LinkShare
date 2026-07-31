package app.linkshare.core.transport

import java.io.BufferedReader
import java.io.FileReader
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Discovers active IPv4 network interface IP addresses (WLAN, Hotspot, P2P) with priority ordering.
 * Filters out cellular interfaces (rmnet, ccmni, tun) for reliable local sharing.
 */
object NetworkUtils {

    data class IpInfo(
        val label: String,
        val ip: String,
        val interfaceName: String
    )

    private val registeredClientIps = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    fun registerClientIp(ip: String) {
        if (ip.isNotBlank() && !ip.startsWith("127.")) {
            registeredClientIps.add(ip)
        }
    }

    fun unregisterClientIp(ip: String) {
        registeredClientIps.remove(ip)
    }

    /**
     * Returns all active, non-loopback, non-cellular IPv4 addresses categorized by interface type.
     */
    fun getAllActiveIpAddresses(): List<IpInfo> {
        val result = mutableListOf<IpInfo>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                val name = intf.name.lowercase()
                
                // Skip cellular, dummy, VPN interfaces unless no other option
                if (isIgnoredInterface(name)) continue

                val addresses = intf.inetAddresses
                for (addr in addresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress ?: continue
                        if (host.startsWith("127.")) continue

                        val label = when {
                            name.startsWith("wlan") -> "Wi-Fi"
                            name.contains("p2p") -> "Wi-Fi Direct"
                            name.startsWith("ap") || name.startsWith("softap") || name.startsWith("swlan") -> "Hotspot"
                            name.startsWith("eth") -> "Ethernet"
                            else -> "Local Network ($name)"
                        }
                        result.add(IpInfo(label, host, intf.name))
                    }
                }
            }
        } catch (_: Exception) {}

        // Sort priority: Wi-Fi > Hotspot > Ethernet > Wi-Fi Direct > Others
        return result.sortedBy { info ->
            val name = info.interfaceName.lowercase()
            when {
                name.startsWith("wlan") -> 0
                name.startsWith("ap") || name.startsWith("softap") || name.startsWith("swlan") -> 1
                name.startsWith("eth") -> 2
                name.contains("p2p") -> 3
                else -> 4
            }
        }
    }

    /**
     * Returns the best single IP for server connections (prioritizing Wi-Fi WLAN, then Hotspot, then P2P).
     */
    fun getLocalIpAddress(): String {
        val all = getAllActiveIpAddresses()
        if (all.isNotEmpty()) {
            return all.first().ip
        }

        // Fallback scan if filtering skipped everything
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return "192.168.49.1"
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                for (addr in intf.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress
                        if (host != null && !host.startsWith("127.")) {
                            return host
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return "192.168.49.1"
    }

    private fun isIgnoredInterface(name: String): Boolean {
        val ignoredPrefixes = listOf(
            "rmnet", "ccmni", "pdp", "dummy", "tun", "tap", "v4-rmnet", 
            "pnet", "sit", "cellular", "usb", "rndis", "bt-pan", "br-"
        )
        return ignoredPrefixes.any { name.startsWith(it) }
    }

    /**
     * Combines socket-registered peer IPs and `/proc/net/arp` scan results.
     */
    fun getConnectedArpIpAddresses(): List<String> {
        val ips = mutableSetOf<String>()
        ips.addAll(registeredClientIps)
        try {
            BufferedReader(FileReader("/proc/net/arp")).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val tokens = line!!.split("\\s+".toRegex())
                    if (tokens.size >= 4) {
                        val ip = tokens[0]
                        val flags = tokens[2]
                        if (ip.startsWith("192.168.49.") && flags != "0x0") {
                            ips.add(ip)
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return ips.toList()
    }
}
