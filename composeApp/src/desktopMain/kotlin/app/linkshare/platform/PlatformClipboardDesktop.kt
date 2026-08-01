package app.linkshare.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

actual class PlatformClipboard {
    private val _currentText = MutableStateFlow("")
    actual val currentText: StateFlow<String> = _currentText.asStateFlow()

    actual fun getText(): String {
        return try {
            val clip = Toolkit.getDefaultToolkit().systemClipboard
            val data = clip.getData(DataFlavor.stringFlavor) as? String ?: ""
            _currentText.value = data
            data
        } catch (_: Exception) { _currentText.value }
    }

    actual fun setText(text: String) {
        try {
            val clip = Toolkit.getDefaultToolkit().systemClipboard
            clip.setContents(StringSelection(text), null)
            _currentText.value = text
        } catch (_: Exception) {}
    }
}
