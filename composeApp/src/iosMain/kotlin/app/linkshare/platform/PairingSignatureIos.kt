package app.linkshare.platform

actual object PairingSignature {
    actual fun sign(payload: String, secret: String): String = ("$secret:$payload").hashCode().toUInt().toString(16)
    actual fun verify(payload: String, signature: String, secret: String): Boolean = sign(payload, secret) == signature
}
