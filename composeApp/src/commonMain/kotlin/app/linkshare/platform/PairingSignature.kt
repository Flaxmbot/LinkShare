package app.linkshare.platform

expect object PairingSignature {
    fun sign(payload: String, secret: String): String
    fun verify(payload: String, signature: String, secret: String): Boolean
}
