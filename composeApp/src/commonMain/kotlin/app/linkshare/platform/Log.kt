package app.linkshare.platform

/**
 * Platform-agnostic logging abstraction.
 */
expect object Log {
    fun d(tag: String, message: String)
    fun e(tag: String, message: String)
    fun w(tag: String, message: String)
    fun i(tag: String, message: String)
}
