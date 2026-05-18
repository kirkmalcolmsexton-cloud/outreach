package org.outreach.feature.auth

import android.os.Build
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleSignInLaunchTest {
    @Test
    fun shouldClear_whenForceClear() {
        assertTrue(shouldClearGoogleStateBeforeSignIn(35, forceClear = true))
    }

    @Test
    fun shouldClear_onApi29AndBelow_byDefault() {
        assertTrue(shouldClearGoogleStateBeforeSignIn(Build.VERSION_CODES.Q, forceClear = false))
    }

    @Test
    fun shouldNotClear_onApi30Plus_unlessForced() {
        assertFalse(shouldClearGoogleStateBeforeSignIn(Build.VERSION_CODES.R, forceClear = false))
        assertFalse(shouldClearGoogleStateBeforeSignIn(35, forceClear = false))
    }
}
