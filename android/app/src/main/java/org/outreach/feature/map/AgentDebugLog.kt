package org.outreach.feature.map

import org.json.JSONObject
import java.io.File

private const val AGENT_DEBUG_LOG_PATH =
    "/Users/aqeel/development/cursor/workspaces/initial/.cursor/debug-9b9bfe.log"

internal fun agentDebugLog(
    hypothesisId: String,
    location: String,
    message: String,
    data: Map<String, Any?> = emptyMap()
) {
    // #region agent log
    try {
        val o = JSONObject()
        o.put("sessionId", "9b9bfe")
        o.put("hypothesisId", hypothesisId)
        o.put("location", location)
        o.put("message", message)
        o.put("timestamp", System.currentTimeMillis())
        val dataObj = JSONObject()
        for ((k, v) in data) {
            when (v) {
                null -> dataObj.put(k, JSONObject.NULL)
                is Number -> dataObj.put(k, v)
                is Boolean -> dataObj.put(k, v)
                else -> dataObj.put(k, v.toString())
            }
        }
        o.put("data", dataObj)
        File(AGENT_DEBUG_LOG_PATH).appendText(o.toString() + "\n")
    } catch (_: Exception) {
    }
    // #endregion
}
