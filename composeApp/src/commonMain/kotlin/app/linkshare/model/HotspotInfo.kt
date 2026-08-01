package app.linkshare.model

data class HotspotInfo(
    val ssid: String,
    val password: String,
    val address: String,
    val isActive: Boolean = true
)
