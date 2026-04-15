package org.outreach.debug

import org.json.JSONObject

fun agentDebugLog(
    hypothesisId: String,
    location: String,
    message: String,
    data: JSONObject? = null,
    runId: String? = null
) {
    // Instrumentation cleaned up: keep call-sites compile-safe as no-op.
}
