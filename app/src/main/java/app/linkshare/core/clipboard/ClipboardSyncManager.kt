package app.linkshare.core.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Universal LAN Text Clipboard Synchronization Manager.
 * Synchronizes clipboard text between phone, PC, and connected peers.
 */
class ClipboardSyncManager(private val context: Context) {

    private val TAG = "ClipboardSyncManager"
    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

    private val _currentClipboardText = MutableStateFlow("")
    val currentClipboardText: StateFlow<String> = _currentClipboardText.asStateFlow()

    init {
        clipboard?.addPrimaryClipChangedListener {
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString() ?: ""
                if (text.isNotBlank() && text != _currentClipboardText.value) {
                    _currentClipboardText.value = text
                    Log.d(TAG, "Local clipboard updated: $text")
                }
            }
        }
    }

    /**
     * Get active clipboard text
     */
    fun getLocalClipboardText(): String {
        val clip = clipboard?.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text?.toString() ?: ""
            _currentClipboardText.value = text
            return text
        }
        return _currentClipboardText.value
    }

    /**
     * Set local clipboard text (received from remote peer or laptop)
     */
    fun setLocalClipboardText(text: String, label: String = "LinkShare Sync") {
        if (text.isBlank()) return
        try {
            val clipData = ClipData.newPlainText(label, text)
            clipboard?.setPrimaryClip(clipData)
            _currentClipboardText.value = text
            Log.d(TAG, "Updated local clipboard from remote: $text")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set clipboard: ${e.message}")
        }
    }
}
