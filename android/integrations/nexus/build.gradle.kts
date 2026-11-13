import java.util.Properties

plugins {
    id("com.android.library")
    id("com.android.legacy-kapt")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val nexusLocalProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

fun nexusPublicConfig(propertyName: String, environmentName: String, defaultValue: String): String =
    (providers.gradleProperty(propertyName).orNull
        ?: providers.environmentVariable(environmentName).orNull
        ?: nexusLocalProperties.getProperty(propertyName)
        ?: defaultValue)
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")

val nexusSupabaseUrl = nexusPublicConfig(
    "ELYSIUM_NEXUS_SUPABASE_URL",
    "ELYSIUM_NEXUS_SUPABASE_URL",
    "https://trccikkcmdqnutwfjrbf.supabase.co"
)
val nexusSupabasePublishableKey = nexusPublicConfig(
    "ELYSIUM_NEXUS_PUBLISHABLE_KEY",
    "ELYSIUM_NEXUS_PUBLISHABLE_KEY",
    ""
)

android {
    namespace = "com.elysium.nexus"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    defaultConfig {
        buildConfigField("String", "NEXUS_SUPABASE_URL", "\"$nexusSupabaseUrl\"")
        buildConfigField("String", "NEXUS_SUPABASE_PUBLISHABLE_KEY", "\"$nexusSupabasePublishableKey\"")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isReturnDefaultValues = true }
}

kapt {
    arguments { arg("room.schemaLocation", "$projectDir/schemas") }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.window:window:1.3.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation(platform("androidx.compose:compose-bom:2026.05.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    kapt("androidx.room:room-compiler:2.8.4")
    // Room's published processor still resolves metadata 2.3; these sources are
    // compiled with the host APK's Kotlin 2.4 toolchain.
    kapt("org.jetbrains.kotlin:kotlin-metadata-jvm:2.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // 2.5.2 introduced private Realtime channels required by the server RLS policy.
    implementation(platform("io.github.jan-tennert.supabase:bom:2.5.4"))
    implementation("io.github.jan-tennert.supabase:gotrue-kt")
    implementation("io.github.jan-tennert.supabase:functions-kt")
    implementation("io.github.jan-tennert.supabase:realtime-kt")
    implementation(platform("io.ktor:ktor-bom:2.3.13"))
    implementation("io.ktor:ktor-client-okhttp")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("io.github.webrtc-sdk:android:125.6422.07")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.json:json:20250517")
}
