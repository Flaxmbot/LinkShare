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
                        if (host.startsWith("127.")) continue

                        val label = when {
                            name.startsWith("wlan") -> "Wi-Fi"
                            name.contains("p2p") -> "Wi-Fi Direct"
                            name.startsWith("ap") || name.startsWith("softap") || name.startsWith("swlan") -> "Hotspot"
                            name.startsWith("eth") -> "Ethernet"
                            name.startsWith("en") -> "Ethernet"
                            name.startsWith("wi") || name.startsWith("wl") -> "Wi-Fi"
                            else -> "Network ($name)"
                        }
                        result.add(IpInfo(label, host, intf.name))
                    }
                }
            }
        } catch (_: Exception) {}

        return result.sortedBy { info ->
            val name = info.interfaceName.lowercase()
            when {
                name.startsWith("wlan") || name.startsWith("en") || name.startsWith("wi") || name.startsWith("wl") -> 0
                name.startsWith("ap") || name.startsWith("softap") || name.startsWith("swlan") -> 1
                name.startsWith("eth") -> 2
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
                for (addr in intf.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress
                        if (host != null && !host.startsWith("127.")) return host
                    }
                }
            }
        } catch (_: Exception) {}
        return "127.0.0.1"
    }

    private fun isIgnoredInterface(name: String): Boolean {
        val ignored = listOf("rmnet", "ccmni", "pdp", "dummy", "tun", "tap", "v4-rmnet",
            "pnet", "sit", "cellular", "rndis", "bt-pan", "br-", "docker", "veth")
        return ignored.any { name.startsWith(it) }
    }
}
