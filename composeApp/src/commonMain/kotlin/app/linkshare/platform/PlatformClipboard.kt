package app.linkshare.platform

import kotlinx.coroutines.flow.StateFlow

/**
 * Platform-specific clipboard access.
 */
expect class PlatformClipboard {
    val currentText: StateFlow<String>
    fun getText(): String
    fun setText(text: String)
}
