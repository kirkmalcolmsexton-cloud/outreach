import groovy.json.JsonOutput
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

/** CI: ANDROID_UPLOAD_KEYSTORE_PATH + password/alias env vars. Local: android/keystore.properties. */
private fun resolveReleaseSigning(androidRoot: Project): ReleaseSigningMaterial? {
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
        return ReleaseSigningMaterial(f, envStorePass, envAlias, envKeyPass)
    }

    val propsFile = androidRoot.file("keystore.properties")
    if (!propsFile.isFile) return null
    val p = Properties().apply { propsFile.inputStream().use { load(it) } }
    val storeRelative = p.getProperty("storeFile")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val storePass = p.getProperty("storePassword") ?: return null
    val alias = p.getProperty("keyAlias") ?: return null
    val keyPass = p.getProperty("keyPassword") ?: return null
    val storeFile = androidRoot.file(storeRelative)
    if (!storeFile.isFile) return null
    return ReleaseSigningMaterial(storeFile, storePass, alias, keyPass)
}

private val releaseSigning = resolveReleaseSigning(project.rootProject)

/** True when the CLI requested `bundleRelease` (configuration-cache compatible; avoids `doFirst` on that task). */
private fun Gradle.startParameterRequestsBundleRelease(): Boolean =
    startParameter.taskNames.any { name ->
        name.endsWith("bundleRelease", ignoreCase = true)
    }

if (gradle.startParameterRequestsBundleRelease() && releaseSigning == null) {
    error(
        """
        Release signing is not configured.
        CI: set ANDROID_UPLOAD_* repository secrets and decode keystore to ANDROID_UPLOAD_KEYSTORE_PATH before Gradle runs.
        Local: copy android/keystore.properties.example to android/keystore.properties.
        First-time secrets: bash android/scripts/create-upload-keystore-and-gh-secrets.sh
        """.trimIndent(),
    )
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
    // Do NOT use providers.gradleProperty("MAPS_API_KEY"): it merges android/gradle.properties and wins
    // before we read local.properties — so a template MAPS_API_KEY there overrides setup-secrets.
    // CLI: only explicit -P MAPS_API_KEY=… (see gradle.startParameter.projectProperties).
    val mapsApiKeyFromCli = gradle.startParameter.projectProperties["MAPS_API_KEY"]
    val mapsApiKey = sequenceOf(
        mapsApiKeyFromCli,
        localProperties.getProperty("MAPS_API_KEY"),
        System.getenv("MAPS_API_KEY"),
        rootGradleProperties.getProperty("MAPS_API_KEY"),
        userGradleProperties.getProperty("MAPS_API_KEY"),
    ).firstOrNull { !it.isNullOrBlank() } ?: ""

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
        resValue("string", "google_maps_key", mapsApiKey)

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

    // #region agent log
    run {
        val logFile = File("/Users/aqeel/development/cursor/workspaces/initial/.cursor/debug-c4c90d.log")
        val payload =
            linkedMapOf(
                "sessionId" to "c4c90d",
                "hypothesisId" to "H1",
                "location" to "android/app/build.gradle.kts:android",
                "message" to "configured SDK levels (SCRUM-42)",
                "data" to
                    mapOf(
                        "compileSdk" to outreachCompileSdk,
                        "targetSdk" to outreachTargetSdk,
                    ),
                "timestamp" to System.currentTimeMillis(),
                "runId" to "post-fix",
            )
        logFile.appendText(JsonOutput.toJson(payload) + "\n")
    }
    // #endregion

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
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (releaseSigning != null) {
                signingConfig = signingConfigs.getByName("release")
            }
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
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.6")
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

// #region agent log
private val agentCarDebugLog =
    File("/Users/aqeel/development/cursor/workspaces/initial/.cursor/debug-ae852b.log")

private fun agentAppendCarDebugNdjson(payload: Map<String, Any?>) {
    agentCarDebugLog.parentFile?.mkdirs()
    agentCarDebugLog.appendText(JsonOutput.toJson(payload) + "\n")
}
// #endregion

// #region agent log
tasks.register("agentLogCarMergedManifest") {
    group = "verification"
    dependsOn("processReleaseManifest")
    doLast {
        val merged =
            layout.buildDirectory
                .file("intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml")
                .get()
                .asFile
        if (!merged.isFile) return@doLast
        val text = merged.readText()
        val carPermRegex =
            Regex("""<uses-permission[^>]*android:name="(androidx\.car\.app\.[^"]+)"""")
        val carPermissions =
            carPermRegex.findAll(text).map { it.groupValues[1] }.distinct().sorted().toList()
        agentAppendCarDebugNdjson(
            mapOf(
                "sessionId" to "ae852b",
                "timestamp" to System.currentTimeMillis(),
                "location" to "app/build.gradle.kts:agentLogCarMergedManifest",
                "message" to "merged release manifest androidx.car.app permission scan",
                "hypothesisId" to "H1",
                "runId" to "post-fix",
                "data" to
                    mapOf(
                        "mergedManifestPath" to merged.absolutePath,
                        "carPermissionNames" to carPermissions,
                        "hasAccessSurface" to
                            carPermissions.contains("androidx.car.app.ACCESS_SURFACE"),
                        "hasNavigationTemplates" to
                            carPermissions.contains("androidx.car.app.NAVIGATION_TEMPLATES"),
                        "mergedHasNavigationCategory" to
                            text.contains("androidx.car.app.category.NAVIGATION"),
                    ),
            ),
        )
    }
}
// #endregion
