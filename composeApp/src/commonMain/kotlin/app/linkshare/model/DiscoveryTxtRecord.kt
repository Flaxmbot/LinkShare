package app.linkshare.model

/**
 * Service discovery metadata for LAN peer discovery.
 */
data class DiscoveryTxtRecord(
    val deviceName: String,
    val appVersion: String = "1.0.0",
    val supportsF2: Boolean = false,
    val supportsF3: Boolean = true,
    val ftpActive: Boolean = false,
    val port: Int = 8888
) {
    fun toMap(): Map<String, String> {
        return mapOf(
            KEY_NAME to deviceName,
            KEY_VER to appVersion,
            KEY_F2 to if (supportsF2) "1" else "0",
            KEY_F3 to if (supportsF3) "1" else "0",
            KEY_FTP to if (ftpActive) "1" else "0",
            KEY_PORT to port.toString()
        )
    }

    companion object {
        const val SERVICE_TYPE = "_linkshare._tcp"
        const val KEY_NAME = "name"
        const val KEY_VER = "ver"
        const val KEY_F2 = "f2"
        const val KEY_F3 = "f3"
        const val KEY_FTP = "ftp"
        const val KEY_PORT = "port"

        fun fromMap(map: Map<String, String>, defaultName: String = "Unknown Peer"): DiscoveryTxtRecord {
            return DiscoveryTxtRecord(
                deviceName = map[KEY_NAME] ?: defaultName,
                appVersion = map[KEY_VER] ?: "1.0.0",
                supportsF2 = map[KEY_F2] == "1",
                supportsF3 = map[KEY_F3] != "0",
                ftpActive = map[KEY_FTP] == "1",
                port = map[KEY_PORT]?.toIntOrNull() ?: 8888
            )
        }
    }
}
