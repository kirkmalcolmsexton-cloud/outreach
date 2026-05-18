package org.outreach.debug

@Suppress("UNUSED_PARAMETER")
object AgentDebugLog {
    fun emit(
        hypothesisId: String,
        location: String,
        message: String,
        data: Map<String, Any?> = emptyMap(),
        runId: String = "pre-fix",
    ) = Unit
}
