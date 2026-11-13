plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.android.legacy-kapt")
}

kapt {
    arguments { arg("room.schemaLocation", "$projectDir/schemas") }
}

android {
    namespace = "com.elysium.vanguard"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    buildFeatures { compose = true }
    testOptions {
        // The Android framework stubs return defaults instead of throwing, so the
        // pure JVM tests can exercise the code paths that touch the framework.
        unitTests.isReturnDefaultValues = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation(platform("androidx.compose:compose-bom:2026.05.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.google.dagger:hilt-android:2.60.1")
    kapt("com.google.dagger:hilt-compiler:2.60.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.4.0")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    kapt("androidx.room:room-compiler:2.8.4")
    implementation("com.google.mediapipe:tasks-genai:0.10.32")
    implementation("com.mrljdx:ffmpeg-kit-full:6.1.4")
    implementation("io.coil-kt:coil-compose:2.5.0")
    implementation("io.coil-kt:coil-video:2.5.0")
    val media3 = "1.3.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("androidx.media3:media3-common:$media3")
    implementation("androidx.media3:media3-session:$media3")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.hilt:hilt-work:1.2.0")
    kapt("androidx.hilt:hilt-compiler:1.2.0")

    // ── UNIVERSAL ARCHIVE ENGINE ─────────────────────────────────────────────
    // ZIP (incl. AES encryption + password), ZIP64, split volumes.
    implementation("net.lingala.zip4j:zip4j:2.11.5")
    // TAR / GZIP / BZIP2 / CPIO / AR / DUMP / 7-Zip readers.
    implementation("org.apache.commons:commons-compress:1.26.2")
    // XZ + LZMA2 (required by the 7-Zip reader for LZMA/LZMA2 coders).
    implementation("org.tukaani:xz:1.9")
    // Zstandard (7-Zip zstd coder + standalone .zst).
    implementation("com.github.luben:zstd-jni:1.5.5-11")
    // RAR3 / RAR5 reading with password support.
    implementation("com.github.junrar:junrar:7.5.5")
    // PDF text extraction for PDF -> Word conversion.
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // ── TESTS ────────────────────────────────────────────────────────────────
    testImplementation("junit:junit:4.13.2")
}
