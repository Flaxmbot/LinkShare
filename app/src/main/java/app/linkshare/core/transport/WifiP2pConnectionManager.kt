package app.linkshare.core.transport

import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetAddress

/**
 * Manages WiFi Direct Group formation, P2P IP address resolution, and state flow.
 */
class WifiP2pConnectionManager(
    private val wifiP2pManager: WifiP2pManager?,
    private val channel: WifiP2pManager.Channel?
) {
    private val TAG = "WifiP2pConnManager"

    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        data class Connecting(val peerName: String) : ConnectionState()
        data class Connected(
            val ipAddress: String,
            val isGroupOwner: Boolean,
            val groupOwnerAddress: InetAddress,
            val connectedClients: List<String>
        ) : ConnectionState()
        data class Error(val errorMessage: String) : ConnectionState()
    }

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    fun connectToPeer(deviceAddress: String, peerName: String, onSuccess: () -> Unit, onFailure: (Int) -> Unit) {
        if (wifiP2pManager == null || channel == null) {
            Log.w(TAG, "WiFi P2P Manager is null")
            _connectionState.value = ConnectionState.Error("WiFi P2P service unavailable")
            onFailure(-1)
            return
        }

        _connectionState.value = ConnectionState.Connecting(peerName)

        val config = WifiP2pConfig().apply {
            this.deviceAddress = deviceAddress
            // Intent 7 allows flexible negotiation between two devices during connection setup
            this.groupOwnerIntent = 7
        }

        wifiP2pManager.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Initiated P2P connection to $deviceAddress ($peerName)")
                requestConnectionInfo()
                onSuccess()
            }

            override fun onFailure(reason: Int) {
                Log.e(TAG, "Failed P2P connection to $deviceAddress: reason $reason")
                _connectionState.value = ConnectionState.Error("P2P connection failed (code $reason)")
                onFailure(reason)
            }
        })
    }

    fun requestConnectionInfo() {
        if (wifiP2pManager == null || channel == null) return

        wifiP2pManager.requestConnectionInfo(channel) { p2pInfo ->
            if (p2pInfo != null && p2pInfo.groupFormed) {
                val goAddress = p2pInfo.groupOwnerAddress
                val goHost = goAddress?.hostAddress ?: "192.168.49.1"
                val arpClients = NetworkUtils.getConnectedArpIpAddresses()
                Log.d(TAG, "P2P Group Formed! GO Address: $goHost, IsGO: ${p2pInfo.isGroupOwner}, Clients: $arpClients")

                _connectionState.value = ConnectionState.Connected(
                    ipAddress = goHost,
                    isGroupOwner = p2pInfo.isGroupOwner,
                    groupOwnerAddress = goAddress ?: InetAddress.getByName("192.168.49.1"),
                    connectedClients = arpClients
                )
            } else {
                _connectionState.value = ConnectionState.Disconnected
            }
        }
    }

    fun disconnect() {
        if (wifiP2pManager == null || channel == null) return
        wifiP2pManager.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Removed P2P Group")
                _connectionState.value = ConnectionState.Disconnected
            }
            override fun onFailure(reason: Int) {
                Log.e(TAG, "Failed to remove P2P group: $reason")
                _connectionState.value = ConnectionState.Disconnected
            }
        })
    }
}
