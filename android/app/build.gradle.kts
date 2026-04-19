import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
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

    namespace = "org.outreach.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "org.outreach.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
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

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
