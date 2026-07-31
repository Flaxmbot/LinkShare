package app.linkshare.core.transport

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Monitors real-time device Location (GPS) provider state.
 * Android OS strictly requires Location to be ON for WifiP2pManager service discovery.
 */
class LocationStateMonitor(private val context: Context) {

    private val locationManager = context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private val _isLocationEnabled = MutableStateFlow(checkLocationEnabled())
    val isLocationEnabled: StateFlow<Boolean> = _isLocationEnabled.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action == LocationManager.PROVIDERS_CHANGED_ACTION) {
                _isLocationEnabled.value = checkLocationEnabled()
            }
        }
    }

    fun checkLocationEnabled(): Boolean {
        if (locationManager == null) return false
        val gps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val network = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        return gps || network
    }

    fun startMonitoring() {
        _isLocationEnabled.value = checkLocationEnabled()
        try {
            val filter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
            context.registerReceiver(receiver, filter)
        } catch (_: Exception) {}
    }

    fun stopMonitoring() {
        try {
            context.unregisterReceiver(receiver)
        } catch (_: Exception) {}
    }
}
