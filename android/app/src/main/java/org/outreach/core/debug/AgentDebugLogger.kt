package org.outreach.core.debug

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

private const val DEBUG_ENDPOINT = "http://127.0.0.1:7747/ingest/f7368b29-184e-4539-ae18-cb2344a4388c"
private const val DEBUG_SESSION_ID = "b12de6"
private const val DEBUG_TAG = "OutreachAuthDbg"

object AgentDebugLogger {
    fun log(
        runId: String,
        hypothesisId: String,
        location: String,
        message: String,
        data: Map<String, Any?> = emptyMap()
    ) {
        val payload = JSONObject().apply {
            put("sessionId", DEBUG_SESSION_ID)
            put("runId", runId)
            put("hypothesisId", hypothesisId)
            put("location", location)
            put("message", message)
            put("data", JSONObject(data))
            put("timestamp", System.currentTimeMillis())
        }
        Log.d(DEBUG_TAG, payload.toString())
        thread(start = true) {
            runCatching {
                val conn = (URL(DEBUG_ENDPOINT).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 700
                    readTimeout = 700
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("X-Debug-Session-Id", DEBUG_SESSION_ID)
                }
                conn.outputStream.use { it.write(payload.toString().toByteArray()) }
                conn.responseCode
                conn.disconnect()
            }
        }
    }
}
