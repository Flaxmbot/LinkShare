package app.linkshare.core.discovery

import app.linkshare.model.PeerDevice
import app.linkshare.platform.Log
import app.linkshare.platform.PlatformNetwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

/**
 * High-performance 1-2 second parallel LAN subnet scanner & IP verifier.
 */
object LanScanner {
    private const val TAG = "LanScanner"

    suspend fun scanLocalNetwork(onPeerDiscovered: (PeerDevice) -> Unit = {}): List<PeerDevice> = withContext(Dispatchers.IO) {
        val localAddresses = PlatformNetwork.getAllActiveIpAddresses()
            .map { it.ip }
            .filter { it != "127.0.0.1" && it.count { c -> c == '.' } == 3 }
            .distinct()
        if (localAddresses.isEmpty()) return@withContext emptyList()

        val localIps = localAddresses.toSet()
        val subnets = localAddresses.map { it.substringBeforeLast(".") + "." }.distinct()
        Log.d(TAG, "Scanning ${subnets.size} local subnet(s): ${subnets.joinToString()}")

        val results = mutableListOf<PeerDevice>()

        val jobs = subnets.flatMap { prefix -> (1..254).map { prefix to it } }
            .filter { (prefix, lastOctet) -> "$prefix$lastOctet" !in localIps }
            .map { (prefix, lastOctet) ->
            async {
                val targetIp = "$prefix$lastOctet"
                val peer = probePeer(targetIp, 8888)
                if (peer != null) {
                    synchronized(results) { results.add(peer) }
                    onPeerDiscovered(peer)
                }
            }
        }

        jobs.awaitAll()
        Log.d(TAG, "Fast LAN scan complete. Discovered ${results.size} active peers.")
        results
    }

    suspend fun probePeer(ip: String, port: Int = 8888): PeerDevice? = withContext(Dispatchers.IO) {
        try {
            // First check fast TCP socket reachability
            val socket = Socket()
            socket.connect(InetSocketAddress(ip, port), 800)
            socket.close()

            // Socket open! Query LinkShare API status endpoint
            val url = URL("http://$ip:$port/api/status")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 1200
                readTimeout = 1200
            }

            if (conn.responseCode == 200) {
                val text = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
                if (text.contains("\"status\":\"active\"")) {
                    val peerName = Regex("\"name\":\"(.*?)\"").find(text)?.groupValues?.getOrNull(1)
                        ?: "LinkShare Peer"
                    val advertisedPort = Regex("\"port\":(\\d+)").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: port
                    return@withContext PeerDevice(
                        id = "peer_$ip:$advertisedPort",
                        name = peerName,
                        ipAddress = ip,
                        port = advertisedPort,
                        supportsF2DualLink = true,
                        supportsF3Swarm = true,
                        ftpServerActive = false
                    )
                }
            }
        } catch (_: Exception) {}
        null
    }
}
