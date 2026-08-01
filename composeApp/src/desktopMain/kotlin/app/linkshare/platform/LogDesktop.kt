package app.linkshare.platform

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

actual object Log {
    private val fmt = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    actual fun d(tag: String, message: String) { println("[${LocalDateTime.now().format(fmt)}] D/$tag: $message") }
    actual fun e(tag: String, message: String) { System.err.println("[${LocalDateTime.now().format(fmt)}] E/$tag: $message") }
    actual fun w(tag: String, message: String) { System.err.println("[${LocalDateTime.now().format(fmt)}] W/$tag: $message") }
    actual fun i(tag: String, message: String) { println("[${LocalDateTime.now().format(fmt)}] I/$tag: $message") }
}
