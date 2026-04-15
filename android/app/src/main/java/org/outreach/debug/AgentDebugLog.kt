package org.outreach.debug

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val SESSION = "e50947"
private const val ENDPOINT_PRIMARY =
    "http://10.0.2.2:7747/ingest/f7368b29-184e-4539-ae18-cb2344a4388c"
private const val ENDPOINT_SECONDARY =
    "http://127.0.0.1:7747/ingest/f7368b29-184e-4539-ae18-cb2344a4388c"

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
            postPayload(ENDPOINT_PRIMARY, payload)
            postPayload(ENDPOINT_SECONDARY, payload)
        } catch (_: Exception) {
        }
    }.start()
}

private fun postPayload(endpoint: String, payload: JSONObject) {
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
        runCatching { conn.inputStream.close() }
        conn.disconnect()
    }
}
