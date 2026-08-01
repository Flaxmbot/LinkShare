package app.linkshare.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.UIKit.UIPasteboard

actual class PlatformClipboard {
    private val _currentText = MutableStateFlow("")
    actual val currentText: StateFlow<String> = _currentText.asStateFlow()

    actual fun getText(): String {
        val text = UIPasteboard.generalPasteboard.string ?: ""
        _currentText.value = text
        return text
    }

    actual fun setText(text: String) {
        UIPasteboard.generalPasteboard.string = text
        _currentText.value = text
    }
}
