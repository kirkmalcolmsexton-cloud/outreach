package org.outreach.debug

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

private const val SESSION_ID = "6a1d26"
private const val INGEST =
    "http://10.0.2.2:7747/ingest/f7368b29-184e-4539-ae18-cb2344a4388c"

/** Fire-and-forget NDJSON to host ingest (emulator: 10.0.2.2 = host). Does not log secrets. */
internal fun agentDebugLog(
    hypothesisId: String,
    location: String,
    message: String,
    data: Map<String, Any?> = emptyMap(),
) {
    thread {
        runCatching {
            val payload =
                JSONObject().apply {
                    put("sessionId", SESSION_ID)
                    put("hypothesisId", hypothesisId)
                    put("location", location)
                    put("message", message)
                    put("timestamp", System.currentTimeMillis())
                    val d = JSONObject()
                    data.forEach { (k, v) ->
                        if (v != null) d.put(k, v)
                    }
                    put("data", d)
                }
            val conn = (URL(INGEST).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("X-Debug-Session-Id", SESSION_ID)
                doOutput = true
                connectTimeout = 3_000
                readTimeout = 3_000
                outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            }
            conn.inputStream.use { it.readBytes() }
            conn.disconnect()
        }.onFailure { Log.w("AgentDebug", "ingest failed: ${it.message}") }
        // Duplicate to logcat so adb can recover NDJSON if host ingest is unreachable (emulator network).
        runCatching {
            val oneLine =
                JSONObject().apply {
                    put("sessionId", SESSION_ID)
                    put("hypothesisId", hypothesisId)
                    put("location", location)
                    put("message", message)
                    put("timestamp", System.currentTimeMillis())
                    val d = JSONObject()
                    data.forEach { (k, v) -> if (v != null) d.put(k, v) }
                    put("data", d)
                }.toString()
            // WARN so physical devices surface this under default logcat filters (INFO was absent in capture).
            Log.w("OutreachAgentDebug", oneLine)
        }
    }
}
