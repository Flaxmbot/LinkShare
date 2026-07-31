package app.linkshare.data

import android.content.Context
import android.content.SharedPreferences
import app.linkshare.model.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists AppSettings across application restarts using SharedPreferences.
 */
class AppSettingsRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("linkshare_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun loadSettings(): AppSettings {
        val defaultName = android.os.Build.MODEL
        return AppSettings(
            deviceName = prefs.getString(KEY_DEVICE_NAME, defaultName) ?: defaultName,
            enableDualLinkF2 = prefs.getBoolean(KEY_ENABLE_F2, false),
            enableSwarmF3 = prefs.getBoolean(KEY_ENABLE_F3, true),
            ftpIdleTimeoutMinutes = prefs.getInt(KEY_FTP_TIMEOUT, 10),
            ftpRequirePin = prefs.getBoolean(KEY_FTP_REQUIRE_PIN, true)
        )
    }

    fun saveSettings(newSettings: AppSettings) {
        prefs.edit()
            .putString(KEY_DEVICE_NAME, newSettings.deviceName)
            .putBoolean(KEY_ENABLE_F2, newSettings.enableDualLinkF2)
            .putBoolean(KEY_ENABLE_F3, newSettings.enableSwarmF3)
            .putInt(KEY_FTP_TIMEOUT, newSettings.ftpIdleTimeoutMinutes)
            .putBoolean(KEY_FTP_REQUIRE_PIN, newSettings.ftpRequirePin)
            .apply()
        _settings.value = newSettings
    }

    companion object {
        private const val KEY_DEVICE_NAME = "device_name"
        private const val KEY_ENABLE_F2 = "enable_f2"
        private const val KEY_ENABLE_F3 = "enable_f3"
        private const val KEY_FTP_TIMEOUT = "ftp_timeout"
        private const val KEY_FTP_REQUIRE_PIN = "ftp_require_pin"
    }
}
