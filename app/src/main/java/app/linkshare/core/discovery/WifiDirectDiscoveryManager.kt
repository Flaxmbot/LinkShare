package app.linkshare.core.discovery

import android.content.Context
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest
import android.util.Log
import app.linkshare.model.DiscoveryTxtRecord
import app.linkshare.model.PeerDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages F1 Embedded Service Discovery riding Bonjour TXT records (`_linkshare._tcp`) on WiFi Direct.
 * Features a 30-second periodic rediscovery loop to keep peer lists fresh.
 */
class WifiDirectDiscoveryManager(
    private val context: Context,
    private val wifiP2pManager: WifiP2pManager?,
    private val channel: WifiP2pManager.Channel?
) {
    private val TAG = "WifiDirectDiscovery"
    private val SERVICE_TYPE = "_linkshare._tcp"

    private val _discoveredPeersMap = ConcurrentHashMap<String, PeerDevice>()
    private val _discoveredPeers = MutableStateFlow<List<PeerDevice>>(emptyList())
    val discoveredPeers: StateFlow<List<PeerDevice>> = _discoveredPeers.asStateFlow()

    private var serviceRequest: WifiP2pDnsSdServiceRequest? = null
    private var isDiscovering = false
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var loopJob: Job? = null

    fun startDiscovery(localRecord: DiscoveryTxtRecord) {
        if (wifiP2pManager == null || channel == null) {
            Log.w(TAG, "WifiP2pManager or Channel is null, discovery skipped.")
            return
        }

        stopDiscovery()
        isDiscovering = true

        // 1. Add Local Bonjour Service TXT Record
        val recordMap = localRecord.toMap()
        val serviceInfo = WifiP2pDnsSdServiceInfo.newInstance(
            localRecord.deviceName,
            SERVICE_TYPE,
            recordMap
        )

        wifiP2pManager.addLocalService(channel, serviceInfo, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Successfully added local Bonjour TXT record for ${localRecord.deviceName}")
            }
            override fun onFailure(reason: Int) {
                Log.e(TAG, "Failed to add local Bonjour record: $reason")
            }
        })

        // 2. Set Response Listeners
        wifiP2pManager.setDnsSdResponseListeners(
            channel,
            { instanceName, _, srcDevice ->
                Log.d(TAG, "Discovered DNS-SD service: $instanceName on device ${srcDevice.deviceName}")
            },
            { _, txtMap, srcDevice ->
                Log.d(TAG, "Received TXT record from ${srcDevice.deviceName}: $txtMap")
                val peer = parsePeerFromRecord(srcDevice, txtMap)
                _discoveredPeersMap[peer.id] = peer
                _discoveredPeers.value = _discoveredPeersMap.values.toList()
            }
        )

        // 3. Initiate Service Request
        serviceRequest = WifiP2pDnsSdServiceRequest.newInstance()
        wifiP2pManager.addServiceRequest(channel, serviceRequest, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Added DNS-SD service request")
                triggerDiscoverServices()
            }
            override fun onFailure(reason: Int) {
                Log.e(TAG, "Failed to add service request: $reason")
            }
        })

        // 4. Start 30-Second Periodic Rediscovery Loop
        loopJob = scope.launch {
            while (isDiscovering) {
                delay(30000)
                if (isDiscovering) {
                    Log.d(TAG, "Periodic 30s re-trigger of P2P service discovery")
                    triggerDiscoverServices()
                }
            }
        }
    }

    private fun triggerDiscoverServices() {
        if (wifiP2pManager == null || channel == null) return
        wifiP2pManager.discoverServices(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "discoverServices initiated")
            }
            override fun onFailure(reason: Int) {
                Log.w(TAG, "discoverServices failed: reason $reason")
            }
        })
    }

    fun stopDiscovery() {
        isDiscovering = false
        loopJob?.cancel()
        loopJob = null
        if (wifiP2pManager != null && channel != null) {
            wifiP2pManager.clearLocalServices(channel, null)
            wifiP2pManager.clearServiceRequests(channel, null)
        }
        _discoveredPeersMap.clear()
        _discoveredPeers.value = emptyList()
    }

    private fun parsePeerFromRecord(device: WifiP2pDevice, txtMap: Map<String, String>): PeerDevice {
        val parsedRecord = DiscoveryTxtRecord.fromMap(txtMap)
        return PeerDevice(
            id = device.deviceAddress,
            name = parsedRecord.deviceName.ifEmpty { device.deviceName },
            appVersion = parsedRecord.appVersion,
            supportsF2DualLink = parsedRecord.supportsF2,
            supportsF3Swarm = parsedRecord.supportsF3,
            ftpServerActive = parsedRecord.ftpActive
        )
    }
}
