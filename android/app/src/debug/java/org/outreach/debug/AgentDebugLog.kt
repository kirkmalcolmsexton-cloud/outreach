package org.outreach.debug

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Posts NDJSON debug events to the Cursor ingest server (use `adb reverse tcp:7747 tcp:7747`). */
object AgentDebugLog {
    private const val TAG = "OutreachDebug0061a7"
    private const val SESSION_ID = "0061a7"
    private const val INGEST_URL =
        "http://127.0.0.1:7747/ingest/f7368b29-184e-4539-ae18-cb2344a4388c"

    fun emit(
        hypothesisId: String,
        location: String,
        message: String,
        data: Map<String, Any?> = emptyMap(),
        runId: String = "pre-fix",
    ) {
        val payload = JSONObject().apply {
            put("sessionId", SESSION_ID)
            put("hypothesisId", hypothesisId)
            put("location", location)
            put("message", message)
            put("timestamp", System.currentTimeMillis())
            put("runId", runId)
            put("data", JSONObject(data))
        }
        val line = payload.toString()
        Log.d(TAG, line)
        Thread {
            runCatching {
                val conn = (URL(INGEST_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("X-Debug-Session-Id", SESSION_ID)
                    doOutput = true
                    connectTimeout = 3_000
                    readTimeout = 3_000
                }
                conn.outputStream.use { it.write(line.toByteArray(Charsets.UTF_8)) }
                conn.inputStream.use { it.readBytes() }
                conn.disconnect()
            }
        }.start()
    }
}
