package app.linkshare.platform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

actual class PlatformClipboard(context: Context) {
    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    private val _currentText = MutableStateFlow("")
    actual val currentText: StateFlow<String> = _currentText.asStateFlow()

    init {
        clipboard?.addPrimaryClipChangedListener {
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString() ?: ""
                if (text.isNotBlank() && text != _currentText.value) {
                    _currentText.value = text
                }
            }
        }
    }

    actual fun getText(): String {
        val clip = clipboard?.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text?.toString() ?: ""
            _currentText.value = text
            return text
        }
        return _currentText.value
    }

    actual fun setText(text: String) {
        if (text.isBlank()) return
        try {
            clipboard?.setPrimaryClip(ClipData.newPlainText("LinkShare", text))
            _currentText.value = text
        } catch (_: Exception) {}
    }
}
