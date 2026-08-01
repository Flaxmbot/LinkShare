package app.linkshare.platform

import app.linkshare.core.swarm.LinkCapabilities

import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.posix.AF_INET
import platform.posix.freeifaddrs
import platform.posix.getifaddrs
import platform.posix.ifaddrs
import platform.posix.sockaddr_in
import platform.posix.inet_ntoa

actual object PlatformNetwork {
    actual fun getAllActiveIpAddresses(): List<IpInfo> {
        val result = mutableListOf<IpInfo>()
        memScoped {
            val ifap = alloc<kotlinx.cinterop.CPointerVar<ifaddrs>>()
            if (getifaddrs(ifap.ptr) == 0) {
                var cursor = ifap.value
                while (cursor != null) {
                    val ifa = cursor!!.pointed
                    val addr = ifa.ifa_addr
                    if (addr != null && addr.pointed.sa_family.toInt() == AF_INET) {
                        val sin = addr.reinterpret<sockaddr_in>().pointed
                        val ip = inet_ntoa(sin.sin_addr)?.toKString() ?: continue
                        if (!ip.startsWith("127.")) {
                            val name = ifa.ifa_name?.toKString() ?: "unknown"
                            val label = when {
                                name.startsWith("en") -> "Wi-Fi"
                                name.startsWith("bridge") -> "Bridge"
                                name.startsWith("lo") -> continue
                                else -> "Network ($name)"
                            }
                            result.add(IpInfo(label, ip, name))
                        }
                    }
                    cursor = ifa.ifa_next
                }
                freeifaddrs(ifap.value)
            }
        }
        return result
    }

    actual fun getLocalIpAddress(): String {
        val all = getAllActiveIpAddresses()
        return all.firstOrNull()?.ip ?: "127.0.0.1"
    }

    actual fun getLinkCapabilities(): LinkCapabilities {
        val names = getAllActiveIpAddresses().map { it.interfaceName }.distinct()
        return LinkCapabilities(false, names.size, names)
    }
}
