package app.linkshare.platform

/** QR matrix used by Quick Connect. JVM platforms use ZXing; unsupported platforms return null. */
expect object QrCode {
    fun encode(value: String): Array<BooleanArray>?
}
