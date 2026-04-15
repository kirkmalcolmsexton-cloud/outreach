package org.outreach.debug

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val SESSION = "59457d"
private const val LOG_TAG = "AGENT_DEBUG_59457d"
private val ENDPOINTS = listOf(
    // Android emulator -> host loopback.
    "http://10.0.2.2:7747/ingest/f7368b29-184e-4539-ae18-cb2344a4388c",
    // Physical device with adb reverse -> host loopback.
    "http://127.0.0.1:7747/ingest/f7368b29-184e-4539-ae18-cb2344a4388c"
)

/**
 * Fire-and-forget debug log to host ingest (Android emulator: 10.0.2.2 = host loopback).
 * For physical devices use: adb reverse tcp:7747 tcp:7747
 */
fun agentDebugLog(
    hypothesisId: String,
    location: String,
    message: String,
    data: JSONObject? = null,
    runId: String? = null
) {
    Thread {
        try {
            val payload = JSONObject().apply {
                put("sessionId", SESSION)
                put("hypothesisId", hypothesisId)
                put("location", location)
                put("message", message)
                put("timestamp", System.currentTimeMillis())
                if (runId != null) put("runId", runId)
                if (data != null) put("data", data)
            }
            Log.d(LOG_TAG, payload.toString())
            ENDPOINTS.forEach { endpoint ->
                runCatching {
                    val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        setRequestProperty("Content-Type", "application/json")
                        setRequestProperty("X-Debug-Session-Id", SESSION)
                        doOutput = true
                        connectTimeout = 1500
                        readTimeout = 1500
                    }
                    conn.outputStream.use { os ->
                        os.write(payload.toString().toByteArray(Charsets.UTF_8))
                    }
                    conn.inputStream.close()
                    conn.disconnect()
                }
            }
        } catch (_: Exception) {
        }
    }.start()
}
