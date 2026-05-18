package org.outreach.feature.auth

import android.app.Activity
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthSupportReportTest {
    @Test
    fun signInCancelledUserMessage_includesReportBlock() {
        val report = "flow=account\ngmsStatusCode=12501"
        val msg = signInCancelledUserMessage(report)
        assertTrue(msg.contains("Sign-in cancelled"))
        assertTrue(msg.contains(report))
        assertTrue(msg.contains("Test user"))
    }
}
