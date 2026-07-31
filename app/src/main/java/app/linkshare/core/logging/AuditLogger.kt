package app.linkshare.core.logging

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Enterprise Audit Logger recording all file transfer events, client IP addresses,
 * protocol methods, and file metadata to a local JSON log file for enterprise compliance.
 */
class AuditLogger(private val context: Context) {

    private val TAG = "AuditLogger"
    private val logFile: File by lazy {
        val dir = File(context.filesDir, "logs")
        if (!dir.exists()) dir.mkdirs()
        File(dir, "audit_events.json")
    }

    data class AuditEvent(
        val timestamp: Long = System.currentTimeMillis(),
        val eventType: String,
        val clientIp: String,
        val fileName: String,
        val fileSizeBytes: Long,
        val protocol: String,
        val status: String,
        val sha256: String = ""
    )

    /**
     * Log a transfer event
     */
    suspend fun logEvent(event: AuditEvent) = withContext(Dispatchers.IO) {
        try {
            val currentArray = readLogArray()
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            val dateStr = sdf.format(Date(event.timestamp))

            val jsonObj = JSONObject().apply {
                put("timestamp", dateStr)
                put("epochMs", event.timestamp)
                put("eventType", event.eventType)
                put("clientIp", event.clientIp)
                put("fileName", event.fileName)
                put("fileSizeBytes", event.fileSizeBytes)
                put("protocol", event.protocol)
                put("status", event.status)
                if (event.sha256.isNotBlank()) {
                    put("sha256", event.sha256)
                }
            }

            currentArray.put(jsonObj)
            logFile.writeText(currentArray.toString(2), Charsets.UTF_8)
            Log.d(TAG, "Audit event logged: ${event.eventType} ${event.fileName} from ${event.clientIp}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write audit log: ${e.message}")
        }
    }

    /**
     * Read all recorded audit log events
     */
    suspend fun getAuditLogsJson(): String = withContext(Dispatchers.IO) {
        if (!logFile.exists()) return@withContext "[]"
        return@withContext try {
            logFile.readText(Charsets.UTF_8)
        } catch (_: Exception) {
            "[]"
        }
    }

    /**
     * Export audit log file into Downloads/LinkShare/audit_export.json
     */
    suspend fun exportLogsToDownloads(): File? = withContext(Dispatchers.IO) {
        try {
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val targetDir = File(downloadsDir, "LinkShare")
            if (!targetDir.exists()) targetDir.mkdirs()

            val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            val exportFileName = "audit_log_${sdf.format(Date())}.json"
            val exportFile = File(targetDir, exportFileName)

            val jsonContent = getAuditLogsJson()
            exportFile.writeText(jsonContent, Charsets.UTF_8)
            exportFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export audit logs: ${e.message}")
            null
        }
    }

    private fun readLogArray(): JSONArray {
        if (!logFile.exists()) return JSONArray()
        return try {
            val text = logFile.readText(Charsets.UTF_8)
            if (text.isBlank()) JSONArray() else JSONArray(text)
        } catch (_: Exception) {
            JSONArray()
        }
    }
}
