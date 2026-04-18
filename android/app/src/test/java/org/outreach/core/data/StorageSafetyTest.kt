package org.outreach.core.data

import java.io.IOException
import java.time.LocalDate
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageSafetyTest {
    @Test
    fun sortEpochForLastVisited_handlesInvalidDateAsOldest() {
        val now = LocalDate.parse("2026-04-15")
        val fallbackEpoch = now.toEpochDay() - 100000

        assertEquals(fallbackEpoch, sortEpochForLastVisited("not-a-date", now))
        assertEquals(fallbackEpoch, sortEpochForLastVisited(null, now))
    }

    @Test
    fun bodyOrThrow_throwsForFailureResult() {
        val result = SheetsRequestResult.Failure(code = 401, message = "unauthorized")

        val thrown = runCatching { result.bodyOrThrow() }.exceptionOrNull()

        assertTrue(thrown is IOException)
        assertTrue(thrown?.message.orEmpty().contains("401"))
    }

    @Test
    fun bodyOrThrow_returnsPayloadForSuccessResult() {
        val payload = JSONObject(mapOf("ok" to true))
        val result = SheetsRequestResult.Success(payload)

        assertEquals(payload, result.bodyOrThrow())
    }
}
