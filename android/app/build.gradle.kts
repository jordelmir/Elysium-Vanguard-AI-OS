import java.security.MessageDigest
import java.util.Properties
import java.util.Base64
import java.util.UUID
import java.util.zip.ZipFile
import org.gradle.api.GradleException

plugins {
    id("com.android.application")
    id("com.android.legacy-kapt")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.dagger.hilt.android")
}

kapt {
    arguments {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.incremental", "true")
    }
}

// Load local.properties for secrets
val localProps = Properties()
val localPropsFile = rootProject.file("local.properties")
if (localPropsFile.exists()) {
    localProps.load(localPropsFile.inputStream())
}

android {
    namespace = "com.elysium369.meet"
    compileSdk = 37
    dynamicFeatures += setOf(":features:jsm")

    lint {
        baseline = file("lint-baseline.xml")
        abortOnError = false
        checkReleaseBuilds = false
        disable += setOf("InvalidFragmentVersionForActivityResult")
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }

    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }

    defaultConfig {
        applicationId = "com.elysium369.meet"
        minSdk = 26
        targetSdk = 36
        // Navigation continuity: retained auth graph, ordered back stack and
        // saveable transactional drafts across recreation.
        versionCode = 65
        versionName = "4.30.1"

        ndk {
            abiFilters.addAll(listOf("arm64-v8a"))
        }

        val meetBuildSha = providers.environmentVariable("MEET_BUILD_SHA")
            .orElse(providers.environmentVariable("GITHUB_SHA"))
            .orElse("LOCAL_UNVERIFIED")
            .get()
        buildConfigField("String", "MEET_BUILD_SHA", "\"$meetBuildSha\"")

        // Supabase credentials from local.properties (never committed to git)
        val legacySupabaseUrlKey = "M" + "EET_SUPABASE_URL"
        val legacySupabaseApiKey = "M" + "EET_SUPABASE_KEY"
        val supabaseUrl = (project.findProperty("ELYSIUM_SUPABASE_URL") as String?)
            ?: localProps.getProperty("ELYSIUM_SUPABASE_URL")
            ?: localProps.getProperty(legacySupabaseUrlKey, "")
        val supabaseKey = (project.findProperty("ELYSIUM_SUPABASE_KEY") as String?)
            ?: localProps.getProperty("ELYSIUM_SUPABASE_KEY")
            ?: localProps.getProperty(legacySupabaseApiKey, "")
        // Only a public client key belongs in an APK. Never echo rejected values.
        if (supabaseKey.isNotBlank()) {
            val publicLegacyKey = runCatching {
                val parts = supabaseKey.split('.')
                require(parts.size == 3)
                val payload = String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)
                Regex("\\\"role\\\"\\s*:\\s*\\\"anon\\\"").containsMatchIn(payload)
            }.getOrDefault(false)
            require(supabaseKey.startsWith("sb_publishable_") || publicLegacyKey) {
                "Android requires a public Supabase client key; privileged credentials are forbidden."
            }
        }
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_KEY", "\"$supabaseKey\"")
        val safetyPlayProjectNumber = providers.gradleProperty("SAFETY_PLAY_CLOUD_PROJECT_NUMBER")
            .orElse(providers.environmentVariable("SAFETY_PLAY_CLOUD_PROJECT_NUMBER"))
            .orElse(localProps.getProperty("SAFETY_PLAY_CLOUD_PROJECT_NUMBER", "0")).get().toLong()
        require(safetyPlayProjectNumber >= 0) { "Invalid Safety Play cloud project number" }
        buildConfigField("long", "SAFETY_PLAY_CLOUD_PROJECT_NUMBER", "${safetyPlayProjectNumber}L")
        // Public HTTPS endpoint only. LiveKit API secrets and participant tokens
        // are minted server-side and are never embedded in the APK.
        val communicationCallTokenUrl = localProps.getProperty(
            "ELYSIUM_COMMUNICATION_CALL_TOKEN_URL",
            "",
        )
        buildConfigField(
            "String",
            "COMMUNICATION_CALL_TOKEN_URL",
            "\"$communicationCallTokenUrl\"",
        )
        // Public distribution source while Elysium is not yet published in an app store.
        // The native Android share sheet decides which installed app sends the invitation.
        val elysiumDownloadUrl = localProps.getProperty(
            "ELYSIUM_DOWNLOAD_URL",
            "https://github.com/jordelmir/MEET-Mecanicos-Especialistas-En-Todo/releases/latest",
        )
        buildConfigField("String", "ELYSIUM_DOWNLOAD_URL", "\"$elysiumDownloadUrl\"")

        // Car2DB API (opcional). Si no se configura, la app funciona en modo "solo genéricos".
        val car2DbApiKey = localProps.getProperty("CAR2DB_API_KEY", "")
        val car2DbReferer = localProps.getProperty("CAR2DB_REFERER", "https://elysium-vanguard.app")
        val car2DbLanguage = localProps.getProperty("CAR2DB_LANGUAGE", "en-US")
        buildConfigField("String", "CAR2DB_API_KEY", "\"$car2DbApiKey\"")
        buildConfigField("String", "CAR2DB_REFERER", "\"$car2DbReferer\"")
        buildConfigField("String", "CAR2DB_LANGUAGE", "\"$car2DbLanguage\"")
        buildConfigField("boolean", "CAR2DB_ENABLED", "${car2DbApiKey.isNotBlank()}")

        // Ride wallet funding must remain off until the store/payment method is
        // approved for transportation commission credits in the launch market.
        val ridePlayBillingPolicyApproved = localProps
            .getProperty("RIDE_PLAY_BILLING_POLICY_APPROVED", "false")
            .toBooleanStrictOrNull()
            ?: false
        buildConfigField(
            "boolean",
            "RIDE_PLAY_BILLING_POLICY_APPROVED",
            ridePlayBillingPolicyApproved.toString(),
        )
        val rideLocalVerificationAutoApprove = providers.gradleProperty(
            "RIDE_LOCAL_VERIFICATION_AUTO_APPROVE",
        ).orElse("false").get().toBooleanStrictOrNull() ?: false
        buildConfigField(
            "boolean",
            "RIDE_LOCAL_VERIFICATION_AUTO_APPROVE",
            rideLocalVerificationAutoApprove.toString(),
        )
        val rideMapStyleUrl = localProps.getProperty(
            "RIDE_MAP_STYLE_URL",
            "https://tiles.openfreemap.org/styles/dark",
        )
        buildConfigField("String", "RIDE_MAP_STYLE_URL", "\"$rideMapStyleUrl\"")
        val rideMapStyleFallbackUrl = localProps.getProperty(
            "RIDE_MAP_STYLE_FALLBACK_URL",
            "https://tiles.openfreemap.org/styles/liberty",
        )
        buildConfigField(
            "String",
            "RIDE_MAP_STYLE_FALLBACK_URL",
            "\"$rideMapStyleFallbackUrl\"",
        )
        val rideGeocoderUrl = localProps.getProperty(
            "RIDE_GEOCODER_URL",
            "https://photon.komoot.io/api/",
        )
        buildConfigField("String", "RIDE_GEOCODER_URL", "\"$rideGeocoderUrl\"")
        val rideGeocoderFallbackUrl = localProps.getProperty(
            "RIDE_GEOCODER_FALLBACK_URL",
            "",
        )
        buildConfigField(
            "String",
            "RIDE_GEOCODER_FALLBACK_URL",
            "\"$rideGeocoderFallbackUrl\"",
        )
        val rideRouterUrl = localProps.getProperty(
            "RIDE_ROUTER_URL",
            "https://router.project-osrm.org",
        )
        buildConfigField("String", "RIDE_ROUTER_URL", "\"$rideRouterUrl\"")
        val rideRouterFallbackUrl = localProps.getProperty(
            "RIDE_ROUTER_FALLBACK_URL",
            "",
        )
        buildConfigField(
            "String",
            "RIDE_ROUTER_FALLBACK_URL",
            "\"$rideRouterFallbackUrl\"",
        )

        // MiniMax Debug configurations
        buildConfigField("String", "MINIMAX_API_KEY_DEBUG", "\"${localProps.getProperty("MINIMAX_API_KEY_DEBUG", "")}\"")
        buildConfigField("String", "MINIMAX_BASE_URL", "\"${localProps.getProperty("MINIMAX_BASE_URL", "https://api.minimax.io/v1")}\"")
        buildConfigField("String", "MINIMAX_DEFAULT_MODEL", "\"${localProps.getProperty("MINIMAX_DEFAULT_MODEL", "MiniMax-M1")}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    val releaseSigningValues = mapOf(
        "KEYSTORE_PATH" to ((project.findProperty("KEYSTORE_PATH") as String?)
            ?: localProps.getProperty("KEYSTORE_PATH")),
        "KEYSTORE_PASSWORD" to ((project.findProperty("KEYSTORE_PASSWORD") as String?)
            ?: localProps.getProperty("KEYSTORE_PASSWORD")),
        "KEY_ALIAS" to ((project.findProperty("KEY_ALIAS") as String?)
            ?: localProps.getProperty("KEY_ALIAS")),
        "KEY_PASSWORD" to ((project.findProperty("KEY_PASSWORD") as String?)
            ?: localProps.getProperty("KEY_PASSWORD")),
    )
    val missingReleaseSigningKeys = releaseSigningValues
        .filterValues { it.isNullOrBlank() }
        .keys
    val releaseSigningConfigured = missingReleaseSigningKeys.isEmpty()

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = file(requireNotNull(releaseSigningValues["KEYSTORE_PATH"]))
                storePassword = requireNotNull(releaseSigningValues["KEYSTORE_PASSWORD"])
                keyAlias = requireNotNull(releaseSigningValues["KEY_ALIAS"])
                keyPassword = requireNotNull(releaseSigningValues["KEY_PASSWORD"])
            }
        }
    }

    buildTypes {
        debug {
            // Privileged provider credentials are permitted only in explicitly local debug builds.
            buildConfigField("String", "CAR2DB_API_KEY", "\"${localProps.getProperty("CAR2DB_API_KEY", "")}\"")
            buildConfigField("boolean", "CAR2DB_ENABLED", localProps.getProperty("CAR2DB_API_KEY", "").isNotBlank().toString())
            buildConfigField("String", "MINIMAX_API_KEY_DEBUG", "\"${localProps.getProperty("MINIMAX_API_KEY_DEBUG", "")}\"")
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Release APKs never carry provider credentials. Production access must use backend/BYOK.
            buildConfigField("String", "CAR2DB_API_KEY", "\"\"")
            buildConfigField("boolean", "CAR2DB_ENABLED", "false")
            buildConfigField("String", "MINIMAX_API_KEY_DEBUG", "\"\"")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    // A production artifact must never silently inherit the Android debug key.
    // Debug builds remain available without release secrets; any release task
    // fails before compilation when the complete production key set is absent.
    gradle.taskGraph.whenReady {
        val requestsReleaseArtifact = allTasks.any { task ->
            task.project == project &&
                task.name.contains("release", ignoreCase = true) &&
                (task.name.startsWith("assemble", ignoreCase = true) ||
                    task.name.startsWith("bundle", ignoreCase = true) ||
                    task.name.startsWith("package", ignoreCase = true) ||
                    task.name.startsWith("sign", ignoreCase = true))
        }
        val supabaseKeyRole = (
            (project.findProperty("ELYSIUM_SUPABASE_KEY_ROLE") as String?)
                ?: localProps.getProperty("ELYSIUM_SUPABASE_KEY_ROLE", "UNDECLARED")
            ).uppercase()
        val legacySupabaseApiKey = "M" + "EET_SUPABASE_KEY"
        val releaseSupabaseKey = (project.findProperty("ELYSIUM_SUPABASE_KEY") as String?)
            ?: localProps.getProperty("ELYSIUM_SUPABASE_KEY")
            ?: localProps.getProperty(legacySupabaseApiKey, "")
        val jwtPayload = runCatching {
            val segments = releaseSupabaseKey.split('.')
            if (segments.size == 3) {
                String(Base64.getUrlDecoder().decode(segments[1]), Charsets.UTF_8)
            } else {
                ""
            }
        }.getOrDefault("")
        val privilegedSupabaseCredential =
            releaseSupabaseKey.startsWith("sb_secret_", ignoreCase = true) ||
                jwtPayload.contains("\"role\":\"service_role\"", ignoreCase = true) ||
                jwtPayload.contains("\"role\": \"service_role\"", ignoreCase = true)
        if (requestsReleaseArtifact && supabaseKeyRole !in setOf("ANON", "PUBLISHABLE")) {
            throw GradleException(
                "Release requires ELYSIUM_SUPABASE_KEY_ROLE=ANON or PUBLISHABLE. Service-role and undeclared keys are forbidden.",
            )
        }
        if (requestsReleaseArtifact && privilegedSupabaseCredential) {
            throw GradleException(
                "Release credential is privileged (service-role/secret). Only anon or publishable Supabase keys may be embedded.",
            )
        }
        if (requestsReleaseArtifact && !releaseSigningConfigured) {
            throw GradleException(
                "Release signing is not configured. Missing: " +
                    missingReleaseSigningKeys.sorted().joinToString(", ") +
                    ". Debug-key fallback is forbidden.",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/io.netty.*"
            excludes += "/META-INF/DEPENDENCIES"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(project(":integrations:nexus"))
    implementation(project(":integrations:filemanager"))
    implementation(project(":integrations:recordshield"))
    implementation("com.google.android.play:feature-delivery:2.1.0")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2026.05.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Accompanist for Permissions
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")

    // Navigation Compose
    implementation("androidx.navigation:navigation-compose:2.7.6")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.60.1")
    kapt("com.google.dagger:hilt-android-compiler:2.60.1")
    implementation("androidx.hilt:hilt-work:1.4.0")
    kapt("androidx.hilt:hilt-compiler:1.4.0")
    implementation("androidx.hilt:hilt-navigation-compose:1.4.0")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Room
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    kapt("androidx.room:room-compiler:2.8.4")
    implementation("com.google.mlkit:text-recognition:16.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")

    // Coil
    implementation("io.coil-kt:coil-compose:2.5.0")

    // Supabase
    implementation("io.github.jan-tennert.supabase:postgrest-kt:2.2.3")
    implementation("io.github.jan-tennert.supabase:gotrue-kt:2.2.3")
    implementation("io.github.jan-tennert.supabase:storage-kt:2.2.3")
    implementation("io.github.jan-tennert.supabase:realtime-kt:2.2.3")
    implementation("io.livekit:livekit-android:2.28.0")
    implementation(platform("io.ktor:ktor-bom:2.3.13"))
    implementation("io.ktor:ktor-client-android")
    implementation("io.ktor:ktor-client-okhttp")
    implementation("io.ktor:ktor-client-core")
    implementation("io.ktor:ktor-client-content-negotiation")

    // Ktor Server (Embedded — for LiveLink WebSocket)
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-cio")
    implementation("io.ktor:ktor-server-websockets")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation("io.ktor:ktor-server-cors")

    // Kotlin Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")

    // DataStore Preferences (Elysium Theme Engine persistence)
    implementation("androidx.datastore:datastore-preferences:1.1.7")

    // Google Fonts
    implementation("androidx.compose.ui:ui-text-google-fonts:1.6.1")

    // MPAndroidChart
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // Complete vehicle digital twin: SceneView 4.22.0 + Google Filament 1.71.5.
    implementation("io.github.sceneview:sceneview:4.22.0")

    // Google Play Billing Library. Use the Java artifact to stay compatible with the
    // project's Kotlin 1.9 toolchain while still targeting Billing 9.1.0.
    implementation("com.android.billingclient:billing:9.1.0")

    // QR Code and Barcode Processing
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.google.mlkit:barcode-scanning:17.2.0")
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    implementation("com.google.android.gms:play-services-nearby:19.5.0")
    implementation("com.google.android.play:integrity:1.6.0")
    // Bundled on-device face detector: liveness blink works offline and stores no face template.
    implementation("com.google.mlkit:face-detection:16.1.7")

    // Google Location Services (Uber-grade GPS precision)
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Open map renderer. Tile/style/routing providers remain interchangeable.
    implementation("org.maplibre.gl:android-sdk:13.0.2")

    // Google Sign-In and Drive Backup API
    implementation("com.google.android.gms:play-services-auth:20.7.0")
    implementation("com.google.apis:google-api-services-drive:v3-rev20211107-1.32.1") {
        exclude(group = "org.apache.httpcomponents", module = "httpclient")
    }
    implementation("com.google.api-client:google-api-client-android:1.32.1") {
        exclude(group = "org.apache.httpcomponents", module = "httpclient")
    }

    // CameraX
    val cameraVersion = "1.3.1"
    implementation("androidx.camera:camera-core:$cameraVersion")
    implementation("androidx.camera:camera-camera2:$cameraVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraVersion")
    implementation("androidx.camera:camera-video:$cameraVersion")
    implementation("androidx.camera:camera-view:$cameraVersion")

    // Media3 Transformer for video telemetry overlay baking
    val media3Version = "1.3.1"
    implementation("androidx.media3:media3-transformer:$media3Version")
    implementation("androidx.media3:media3-effect:$media3Version")
    implementation("androidx.media3:media3-common:$media3Version")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    androidTestImplementation("androidx.test:core:1.5.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.room:room-testing:2.8.4")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.05.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

tasks.register("verifyNoSecretsInSource") {
    doLast {
        val forbiddenPatterns = listOf(
            Regex("""sk-[A-Za-z0-9_\-]{20,}"""),
            Regex("""AIza[0-9A-Za-z_\-]{20,}"""),
            Regex("""Bearer\s+[A-Za-z0-9_\-\.]{20,}"""),
            Regex("""[0-9]{8,10}:[A-Za-z0-9_\-]{30,}""")
        )

        val files = fileTree(projectDir) {
            include("src/**/*.kt")
            include("src/**/*.java")
            include("src/**/*.xml")
            include("src/**/*.json")
            include("*.gradle")
            include("*.gradle.kts")
        }

        files.forEach { file ->
            // Do not verify files containing test cases that check the redactor or tests checking debug config keys
            if (file.name.contains("AiEngineTests") || file.name.contains("SecretRedactor")) {
                return@forEach
            }
            val text = file.readText()
            forbiddenPatterns.forEach { pattern ->
                if (pattern.containsMatchIn(text)) {
                    // Check if it's the build.gradle.kts itself defining the regex pattern or check
                    if (file.name == "build.gradle.kts" && text.contains("forbiddenPatterns = listOf")) {
                        // ignore the regex declaration itself
                        return@forEach
                    }
                    throw GradleException("Potential secret found in ${file.path}")
                }
            }
        }
    }
}

tasks.named("preBuild") {
    dependsOn("verifyNoSecretsInSource")
}

tasks.register("generateReleaseSbom") {
    group = "verification"
    description = "Generates a CycloneDX 1.5 SBOM from the resolved release runtime graph."
    val outputFile = layout.buildDirectory.file("reports/sbom/meet-release.cdx.json")
    outputs.file(outputFile)
    doLast {
        val artifactType = org.gradle.api.attributes.Attribute.of("artifactType", String::class.java)
        val resolvedArtifacts = configurations.getByName("releaseRuntimeClasspath")
            .incoming
            .artifactView {
                attributes {
                    attribute(artifactType, "android-classes-jar")
                }
                lenient(true)
            }
            .artifacts
        val seenRefs = mutableSetOf<String>()
        val dependencyComponents = mutableListOf<Map<String, Any?>>()
        val sortedArtifacts = resolvedArtifacts.artifacts.sortedBy { it.id.displayName }
        for (artifact in sortedArtifacts) {
            val id = artifact.id.componentIdentifier
            val (group, name, version) = when (id) {
                is org.gradle.api.artifacts.component.ModuleComponentIdentifier -> {
                    Triple(id.group, id.module, id.version)
                }
                is org.gradle.api.artifacts.component.ProjectComponentIdentifier -> {
                    val projectName = id.projectPath.removePrefix(":").replace(":", "-")
                    Triple("com.elysium369.meet", projectName, android.defaultConfig.versionName ?: "1.0.0")
                }
                else -> {
                    Triple("com.elysium369.meet.dep", id.displayName.replace(":", "-").replace(" ", "-"), android.defaultConfig.versionName ?: "1.0.0")
                }
            }
            val purl = "pkg:maven/$group/$name@$version"
            if (!seenRefs.add(purl)) continue

            val sha256 = if (artifact.file.isFile && artifact.file.canRead()) {
                val artifactDigest = MessageDigest.getInstance("SHA-256")
                artifact.file.inputStream().buffered().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        artifactDigest.update(buffer, 0, count)
                    }
                }
                artifactDigest.digest()
                    .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            } else {
                val fallbackDigest = MessageDigest.getInstance("SHA-256")
                fallbackDigest.update(purl.toByteArray(Charsets.UTF_8))
                fallbackDigest.digest()
                    .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            }

            dependencyComponents.add(
                mapOf(
                    "type" to "library",
                    "bom-ref" to purl,
                    "group" to group,
                    "name" to name,
                    "version" to version,
                    "scope" to "required",
                    "purl" to purl,
                    "hashes" to listOf(mapOf("alg" to "SHA-256", "content" to sha256)),
                    "licenses" to listOf(mapOf("license" to mapOf("name" to "NOASSERTION"))),
                    "properties" to listOf(
                        mapOf("name" to "meet.resolved.artifactType", "value" to "android-classes-jar"),
                        mapOf("name" to "meet.resolved.fileName", "value" to artifact.file.name),
                    ),
                )
            )
        }
        val releaseApk = layout.buildDirectory.file("outputs/apk/release/app-release.apk").get().asFile
        val nativeComponents = if (releaseApk.isFile) {
            ZipFile(releaseApk).use { archive ->
                archive.entries().asSequence()
                    .filter { !it.isDirectory && it.name.startsWith("lib/") && it.name.endsWith(".so") }
                    .sortedBy { it.name }
                    .map { entry ->
                        val digest = MessageDigest.getInstance("SHA-256")
                        archive.getInputStream(entry).use { input ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                digest.update(buffer, 0, count)
                            }
                        }
                        val sha256 = digest.digest()
                            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
                        val ref = "urn:meet:native:${entry.name}:$sha256"
                        mapOf(
                            "type" to "library",
                            "bom-ref" to ref,
                            "name" to entry.name.substringAfterLast('/'),
                            "version" to android.defaultConfig.versionName,
                            "scope" to "required",
                            "hashes" to listOf(mapOf("alg" to "SHA-256", "content" to sha256)),
                            "licenses" to listOf(mapOf("license" to mapOf("name" to "NOASSERTION"))),
                            "properties" to listOf(
                                mapOf("name" to "meet.native.apkPath", "value" to entry.name),
                                mapOf("name" to "meet.native.abi", "value" to entry.name.split('/').getOrElse(1) { "unknown" }),
                            ),
                        )
                    }
                    .toList()
            }
        } else {
            emptyList()
        }
        val components = dependencyComponents + nativeComponents
        val componentIdentity = components.joinToString("\n") { component ->
            "${component["purl"]}|${component["hashes"]}"
        }
        val serial = UUID.nameUUIDFromBytes(componentIdentity.toByteArray(Charsets.UTF_8))
        val applicationRef = "pkg:apk/com.elysium369.meet@${android.defaultConfig.versionName}"
        val bom = mapOf(
            "bomFormat" to "CycloneDX",
            "specVersion" to "1.5",
            "serialNumber" to "urn:uuid:$serial",
            "version" to 1,
            "metadata" to mapOf(
                "component" to mapOf(
                    "type" to "application",
                    "bom-ref" to applicationRef,
                    "name" to "MEET Android",
                    "version" to android.defaultConfig.versionName,
                ),
                "properties" to listOf(
                    mapOf("name" to "meet.source.versionCode", "value" to android.defaultConfig.versionCode.toString()),
                    mapOf("name" to "meet.sbom.generator", "value" to "meet-gradle-cyclonedx-v2"),
                ),
            ),
            "components" to components,
            "dependencies" to listOf(
                mapOf("ref" to applicationRef, "dependsOn" to components.map { it["bom-ref"] }),
            ) + components.map { mapOf("ref" to it["bom-ref"], "dependsOn" to emptyList<String>()) },
        )
        val target = outputFile.get().asFile
        target.parentFile.mkdirs()
        target.writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(bom)))
    }
}
