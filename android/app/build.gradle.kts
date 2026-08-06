import java.io.File
import java.util.Properties
import org.gradle.api.Project
import org.gradle.api.invocation.Gradle

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
}

private data class ReleaseSigningMaterial(
    val storeFile: File,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
)

/**
 * CI / `android/scripts/build.sh release-bundle`: ANDROID_UPLOAD_* env vars (decrypted repo keystore).
 * Fallback: android/keystore.properties — optional; does not run setup-secrets or match CI unless same .jks.
 */
private fun resolveReleaseSigning(androidRoot: Project): Pair<ReleaseSigningMaterial?, String> {
    val envPath = System.getenv("ANDROID_UPLOAD_KEYSTORE_PATH")?.trim()?.takeIf { it.isNotEmpty() }
    val envStorePass = System.getenv("ANDROID_UPLOAD_KEYSTORE_PASSWORD")?.takeIf { it.isNotBlank() }
    val envAlias = System.getenv("ANDROID_UPLOAD_KEY_ALIAS")?.takeIf { it.isNotBlank() }
    val envKeyPass = System.getenv("ANDROID_UPLOAD_KEY_PASSWORD")?.takeIf { it.isNotBlank() }

    if (!envPath.isNullOrBlank() && envStorePass != null && envAlias != null && envKeyPass != null) {
        val f = File(envPath)
        if (!f.isFile) {
            error(
                "ANDROID_UPLOAD_KEYSTORE_PATH points to missing file: $envPath\n" +
                    "(CI must decode the keystore before Gradle runs.)",
            )
        }
        return Pair(ReleaseSigningMaterial(f, envStorePass, envAlias, envKeyPass), "env")
    }

    val propsFile = androidRoot.file("keystore.properties")
    if (!propsFile.isFile) return Pair(null, "none")
    val p = Properties().apply { propsFile.inputStream().use { load(it) } }
    val storeRelative = p.getProperty("storeFile")?.trim()?.takeIf { it.isNotEmpty() } ?: return Pair(null, "none")
    val storePass = p.getProperty("storePassword") ?: return Pair(null, "none")
    val alias = p.getProperty("keyAlias") ?: return Pair(null, "none")
    val keyPass = p.getProperty("keyPassword") ?: return Pair(null, "none")
    val storeFile = androidRoot.file(storeRelative)
    if (!storeFile.isFile) return Pair(null, "none")
    return Pair(ReleaseSigningMaterial(storeFile, storePass, alias, keyPass), "keystore.properties")
}

private val releaseSigningPair = resolveReleaseSigning(project.rootProject)
private val releaseSigning = releaseSigningPair.first
private val releaseSigningSource = releaseSigningPair.second

/** True when the CLI requested `bundleRelease` (configuration-cache compatible; avoids `doFirst` on that task). */
private fun Gradle.startParameterRequestsBundleRelease(): Boolean =
    startParameter.taskNames.any { name ->
        name.endsWith("bundleRelease", ignoreCase = true)
    }

if (gradle.startParameterRequestsBundleRelease() && releaseSigning == null) {
    error(
        """
        Release signing is not configured.

        Same as GitHub Actions (setup-secrets + repo upload keystore): from repo root,
          OUTREACH_SECRETS_PASSPHRASE=... bash android/scripts/build.sh release-bundle

        Or Gradle-only fallback: copy android/keystore.properties.example to android/keystore.properties
        (personal/offline; differs from CI unless that file points at the same keystore as CI).

        First-time signing setup: bash android/scripts/create-upload-keystore-and-gh-secrets.sh
        """.trimIndent(),
    )
}

if (gradle.startParameterRequestsBundleRelease() &&
    releaseSigning != null &&
    releaseSigningSource == "keystore.properties"
) {
    val ksAge = rootProject.projectDir.parentFile.resolve("secrets/upload-keystore.jks.age")
    if (ksAge.isFile) {
        rootProject.logger.lifecycle(
            """
            Outreach: bundleRelease uses android/keystore.properties.
            For the same google-services.json + signing as GitHub Actions, use:
              OUTREACH_SECRETS_PASSPHRASE=... bash android/scripts/build.sh release-bundle
            """.trimIndent(),
        )
    }
}

android {
    val localProperties = Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) {
            file.inputStream().use { load(it) }
        }
    }
    val rootGradleProperties = Properties().apply {
        val file = rootProject.file("gradle.properties")
        if (file.exists()) {
            file.inputStream().use { load(it) }
        }
    }
    val userGradleProperties = Properties().apply {
        val file = File(System.getProperty("user.home"), ".gradle/gradle.properties")
        if (file.exists()) {
            file.inputStream().use { load(it) }
        }
    }
    // Do NOT use providers.gradleProperty for MAPS_* for the same reason as MAPS_API_KEY: gradle.properties merge order.
    // Dual keys: setup-secrets.sh writes MAPS_API_KEY_DEBUG / MAPS_API_KEY_RELEASE. Legacy: single MAPS_API_KEY for both
    // when the dual properties are unset (e.g. CI placeholder env).
    fun mapsKeyFrom(
        cliKey: String,
        localPropertyKey: String,
        envName: String,
    ): String = sequenceOf(
        gradle.startParameter.projectProperties[cliKey],
        localProperties.getProperty(localPropertyKey),
        System.getenv(envName),
        rootGradleProperties.getProperty(localPropertyKey),
        userGradleProperties.getProperty(localPropertyKey),
    ).firstOrNull { !it.isNullOrBlank() } ?: ""

    val mapsApiKeyLegacy = sequenceOf(
        gradle.startParameter.projectProperties["MAPS_API_KEY"],
        localProperties.getProperty("MAPS_API_KEY"),
        System.getenv("MAPS_API_KEY"),
        rootGradleProperties.getProperty("MAPS_API_KEY"),
        userGradleProperties.getProperty("MAPS_API_KEY"),
    ).firstOrNull { !it.isNullOrBlank() } ?: ""

    var mapsApiKeyDebug = mapsKeyFrom("MAPS_API_KEY_DEBUG", "MAPS_API_KEY_DEBUG", "MAPS_API_KEY_DEBUG")
    var mapsApiKeyRelease = mapsKeyFrom("MAPS_API_KEY_RELEASE", "MAPS_API_KEY_RELEASE", "MAPS_API_KEY_RELEASE")
    if (mapsApiKeyDebug.isEmpty() && mapsApiKeyRelease.isEmpty() && mapsApiKeyLegacy.isNotEmpty()) {
        mapsApiKeyDebug = mapsApiKeyLegacy
        mapsApiKeyRelease = mapsApiKeyLegacy
    }

    val outreachVersionCode =
        rootProject.findProperty("outreach.versionCode")?.toString()?.toIntOrNull() ?: 1
    val outreachVersionName =
        rootProject.findProperty("outreach.versionName")?.toString()?.trim().orEmpty()
            .ifEmpty { "0.1.0" }

    val outreachCompileSdk = 35
    val outreachTargetSdk = 35

    namespace = "org.outreach.app"
    compileSdk = outreachCompileSdk

    defaultConfig {
        applicationId = "org.outreach.app"
        minSdk = 26
        targetSdk = outreachTargetSdk
        versionCode = outreachVersionCode
        versionName = outreachVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Map CLI project properties into the instrumentation Bundle (AGP DSL; avoids nested
        // -Pandroid.testInstrumentationRunnerArguments.* keys and configuration-cache warnings).
        val outreachMockEmail = sequenceOf(
            providers.gradleProperty("outreachMockUserEmail").orNull,
            project.findProperty("outreachMockUserEmail")?.toString()
        ).firstOrNull { !it.isNullOrBlank() }?.trim()
        if (!outreachMockEmail.isNullOrEmpty()) {
            testInstrumentationRunnerArguments["outreach.ui_test.mock_user_email"] = outreachMockEmail
        }
        val authResolution = sequenceOf(
            providers.gradleProperty("outreachAuthResolution").orNull,
            project.findProperty("outreachAuthResolution")?.toString()
        ).firstOrNull { !it.isNullOrBlank() }?.trim()?.lowercase()
        if (authResolution == "mock" || authResolution == "real") {
            testInstrumentationRunnerArguments["outreach.ui_test.auth_resolution"] = authResolution
        }
    }

    signingConfigs {
        if (releaseSigning != null) {
            create("release") {
                storeFile = releaseSigning.storeFile
                storePassword = releaseSigning.storePassword
                keyAlias = releaseSigning.keyAlias
                keyPassword = releaseSigning.keyPassword
            }
        }
    }

    buildTypes {
        debug {
            resValue("string", "google_maps_key", mapsApiKeyDebug)
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (releaseSigning != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            resValue("string", "google_maps_key", mapsApiKeyRelease)
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }
}

dependencies {
    // InstrumentationRegistry.getArguments() for UiAutomationConfig merge (same Bundle as tests); guarded at runtime when not instrumented.
    implementation("androidx.test:core:1.6.1")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
    implementation("androidx.navigation:navigation-compose:2.8.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    implementation("androidx.compose.material3:material3:1.3.0")
    implementation("androidx.compose.material:material-icons-extended:1.7.1")
    implementation("androidx.compose.ui:ui:1.7.1")
    implementation("androidx.compose.ui:ui-tooling-preview:1.7.1")
    debugImplementation("androidx.compose.ui:ui-tooling:1.7.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.7.1")

    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("com.google.android.gms:play-services-basement:18.5.0")
    implementation("com.google.android.gms:play-services-maps:19.0.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.maps.android:maps-compose:4.4.1")
    implementation("com.google.maps.android:android-maps-utils:3.8.2")
    implementation("com.google.firebase:firebase-auth-ktx:23.1.0")
    implementation("com.google.firebase:firebase-firestore-ktx:25.1.0")

    implementation("androidx.car.app:app:1.4.0")
    implementation("androidx.car.app:app-projected:1.4.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.7.1")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
