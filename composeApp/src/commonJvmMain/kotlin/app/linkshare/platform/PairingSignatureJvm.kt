package app.linkshare.platform

import java.security.MessageDigest

actual object PairingSignature {
    actual fun sign(payload: String, secret: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest("$secret:$payload".toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    actual fun verify(payload: String, signature: String, secret: String): Boolean = sign(payload, secret) == signature
}
