package org.outreach.feature.car

import android.content.pm.PackageManager
import android.os.Build
import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.validation.HostValidator
import org.outreach.app.BuildConfig
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/**
 * Android Auto entry point (phone projection).
 *
 * ### Real vehicle vs development build (important)
 * Per [Test Android apps for cars](https://developer.android.com/training/cars/testing),
 * **apps built with the Android for Cars App Library do not appear on Android Auto when installed from a
 * sideloaded APK** (adb install, Android Studio Run, file manager APK). The Android Auto “Unknown sources”
 * developer toggle applies to **media / messaging / parked apps only**, not to Car App Library apps.
 *
 * To see this app on a **physical head unit** (e.g. Hyundai), install from a **trusted Play distribution**:
 * [Google Play Internal App Sharing](https://support.google.com/googleplay/android-developer/answer/9844679),
 * an **internal / closed testing** track, or production. Iterating still uses DHU or emulator without full review.
 *
 * ### Local smoke test (no Play required)
 * Use [Desktop Head Unit (DHU)](https://developer.android.com/training/cars/testing/dhu): install the app on the phone,
 * run the DHU on your machine, connect USB — Outreach should appear in the DHU launcher for template apps.
 *
 * ### Store listing
 * Navigation apps must meet [Android Auto app quality](https://developer.android.com/docs/quality-guidelines/auto-app-quality)
 * and Play policies to be discoverable as a navigation provider to all users.
 */
class OutreachCarAppService : CarAppService() {

    override fun createHostValidator(): HostValidator {
        if (BuildConfig.DEBUG) {
            return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        }
        val digest = signingCertSha256Hex(this, ANDROID_AUTO_PACKAGE)
        val builder = HostValidator.Builder(applicationContext)
        if (digest != null) {
            builder.addAllowedHost(ANDROID_AUTO_PACKAGE, digest)
        }
        return builder.build()
    }

    override fun onCreateSession(sessionInfo: SessionInfo): Session {
        return OutreachNavigationSession()
    }

    companion object {
        private const val ANDROID_AUTO_PACKAGE = "com.google.android.projection.gearhead"

        /**
         * SHA-256 of the Android Auto host signing cert (required for release host validation).
         */
        private fun signingCertSha256Hex(service: CarAppService, packageName: String): String? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
            return try {
                val pkg = service.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
                val signers = pkg.signingInfo?.apkContentsSigners ?: return null
                val sig = signers.firstOrNull() ?: return null
                val factory = CertificateFactory.getInstance("X509")
                val cert = factory.generateCertificate(ByteArrayInputStream(sig.toByteArray())) as X509Certificate
                val digest = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
                digest.joinToString(":") { b -> "%02X".format(b) }
            } catch (_: Exception) {
                null
            }
        }
    }
}
