package app.linkshare.core.transport

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Monitors real-time device WiFi radio state (Enabled vs Disabled).
 */
class WifiStateMonitor(private val context: Context) {

    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private val _isWifiEnabled = MutableStateFlow(wifiManager?.isWifiEnabled ?: false)
    val isWifiEnabled: StateFlow<Boolean> = _isWifiEnabled.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action == WifiManager.WIFI_STATE_CHANGED_ACTION) {
                val state = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN)
                _isWifiEnabled.value = (state == WifiManager.WIFI_STATE_ENABLED)
            }
        }
    }

    fun startMonitoring() {
        _isWifiEnabled.value = wifiManager?.isWifiEnabled ?: false
        try {
            val filter = IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION)
            context.registerReceiver(receiver, filter)
        } catch (_: Exception) {}
    }

    fun stopMonitoring() {
        try {
            context.unregisterReceiver(receiver)
        } catch (_: Exception) {}
    }
}
