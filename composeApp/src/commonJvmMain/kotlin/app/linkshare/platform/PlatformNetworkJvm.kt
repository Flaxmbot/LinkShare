package app.linkshare.platform

import java.net.Inet4Address
import java.net.NetworkInterface

actual object PlatformNetwork {
    actual fun getAllActiveIpAddresses(): List<IpInfo> {
        val result = mutableListOf<IpInfo>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                val name = intf.name.lowercase()
                if (isIgnoredInterface(name)) continue

                for (addr in intf.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress ?: continue
                        if (host.startsWith("127.") || host.startsWith("169.254.")) continue

                        val label = when {
                            name.startsWith("wlan") -> "Wi-Fi ($name)"
                            name.startsWith("eth") || name.startsWith("en") -> "Ethernet ($name)"
                            name.startsWith("ap") || name.startsWith("softap") || name.startsWith("swlan") -> "Hotspot ($name)"
                            name.contains("p2p") -> "Wi-Fi Direct ($name)"
                            else -> "LAN ($name)"
                        }
                        result.add(IpInfo(label, host, intf.name))
                    }
                }
            }
        } catch (_: Exception) {}

        // Prioritize true Wi-Fi / Ethernet LAN IP addresses (192.168.x.x, 10.x.x.x, 172.16-31.x.x)
        return result.sortedBy { info ->
            val ip = info.ip
            val name = info.interfaceName.lowercase()
            when {
                (ip.startsWith("192.168.") || ip.startsWith("10.0.") || ip.startsWith("172.16.")) && (name.startsWith("wlan") || name.startsWith("en")) -> 0
                name.startsWith("wlan") || name.startsWith("en") || name.startsWith("eth") -> 1
                name.startsWith("ap") || name.startsWith("softap") -> 2
                name.contains("p2p") -> 3
                else -> 4
            }
        }
    }

    actual fun getLocalIpAddress(): String {
        val all = getAllActiveIpAddresses()
        if (all.isNotEmpty()) return all.first().ip

        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return "127.0.0.1"
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                if (isIgnoredInterface(intf.name.lowercase())) continue

                for (addr in intf.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress
                        if (host != null && !host.startsWith("127.") && !host.startsWith("169.254.")) return host
                    }
                }
            }
        } catch (_: Exception) {}
        return "127.0.0.1"
    }

    private fun isIgnoredInterface(name: String): Boolean {
        val ignored = listOf("rmnet", "ccmni", "pdp", "dummy", "tun", "tap", "v4-rmnet",
            "pnet", "sit", "cellular", "rndis", "bt-pan", "br-", "docker", "veth", "wg", "wireguard", "ppp")
        return ignored.any { name.startsWith(it) }
    }
}
