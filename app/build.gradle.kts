import java.util.Properties

// NOTE: AGP 9.x has built-in Kotlin support; the 'org.jetbrains.kotlin.android' plugin
// must NOT be applied (it is now an error). See https://kotl.in/gradle/agp-built-in-kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

apply(from = rootProject.file("branding.gradle.kts"))

// Single source of truth for anything user-visible about the app's identity (brief §5).
val brandAppName = extra["brandAppName"] as String
val brandApplicationId = extra["brandApplicationId"] as String
val brandNamespace = extra["brandNamespace"] as String
val brandContactEmail = extra["brandContactEmail"] as String
val brandPrivacyPolicyUrl = extra["brandPrivacyPolicyUrl"] as String
val brandVersionCode = extra["brandVersionCode"] as Int
val brandVersionName = extra["brandVersionName"] as String

// Open Food Facts requires a User-Agent that identifies the app and offers a contact route
// (verified 2026-08-13). Anonymous clients are blocked.
val offUserAgent = "$brandAppName/$brandVersionName (Android; $brandContactEmail)"

// Signing material is read from keystore.properties (gitignored) or environment variables.
// NOTHING secret is ever committed (brief §53).
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

fun secret(key: String, env: String): String? =
    keystoreProps.getProperty(key) ?: System.getenv(env)

val releaseStoreFile = secret("storeFile", "JUSTTHECARBS_STORE_FILE")
val releaseStorePassword = secret("storePassword", "JUSTTHECARBS_STORE_PASSWORD")
val releaseKeyAlias = secret("keyAlias", "JUSTTHECARBS_KEY_ALIAS")
val releaseKeyPassword = secret("keyPassword", "JUSTTHECARBS_KEY_PASSWORD")
val resolvedReleaseStoreFile = releaseStoreFile?.let(rootProject::file)
val missingSigningMaterial = buildList {
    if (releaseStoreFile.isNullOrBlank()) add("storeFile / JUSTTHECARBS_STORE_FILE")
    if (releaseStorePassword.isNullOrBlank()) add("storePassword / JUSTTHECARBS_STORE_PASSWORD")
    if (releaseKeyAlias.isNullOrBlank()) add("keyAlias / JUSTTHECARBS_KEY_ALIAS")
    if (releaseKeyPassword.isNullOrBlank()) add("keyPassword / JUSTTHECARBS_KEY_PASSWORD")
    if (resolvedReleaseStoreFile != null && !resolvedReleaseStoreFile.exists()) {
        add("signing file does not exist: ${resolvedReleaseStoreFile.absolutePath}")
    }
}
val hasSigningMaterial = missingSigningMaterial.isEmpty()

android {
    namespace = brandNamespace
    // AndroidX (core 1.19.0, Compose 1.12.0, lifecycle 2.11.0) requires compiling against API 37.
    // compileSdk is independent of targetSdk: targetSdk stays at the Play-mandated 36 (§4).
    compileSdk = 37

    defaultConfig {
        applicationId = brandApplicationId
        minSdk = 26
        targetSdk = 36
        versionCode = brandVersionCode
        versionName = brandVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        // The launcher label comes from branding.gradle.kts, so it is never hardcoded in a
        // strings.xml that a translator might rename (§5). Do not also declare app_name in res/.
        resValue("string", "app_name", brandAppName)
        buildConfigField("String", "APP_NAME", "\"$brandAppName\"")
        buildConfigField("String", "OFF_USER_AGENT", "\"$offUserAgent\"")
        buildConfigField("String", "CONTACT_EMAIL", "\"$brandContactEmail\"")
        buildConfigField("String", "PRIVACY_POLICY_URL", "\"$brandPrivacyPolicyUrl\"")
    }

    signingConfigs {
        if (hasSigningMaterial) {
            create("release") {
                storeFile = resolvedReleaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Only attach a real signing config; never fall back to the debug key (brief §52).
            signingConfig = if (hasSigningMaterial) signingConfigs.getByName("release") else null
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
        // Required for the generated app_name string; off by default in AGP 9.
        resValues = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/DEPENDENCIES",
            "/META-INF/LICENSE*",
        )
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    // Room's MigrationTestHelper (androidTest) loads exported schema JSON from assets at runtime;
    // without this it cannot find app.justthecarbs.data.local.JustTheCarbsDatabase/<version>.json.
    sourceSets {
        getByName("androidTest") {
            assets.srcDirs("$projectDir/schemas")
        }
    }

    lint {
        warningsAsErrors = false
        abortOnError = true
        checkDependencies = true
    }
}

// A release packaging task without all four secrets used to succeed and leave an unsigned
// artifact in build/outputs. That is too easy to mistake for something uploadable. Dependency
// reports and release-unit-test compilation remain available without a key, but every task graph
// that packages, signs, installs, assembles or bundles the release variant fails before execution.
gradle.taskGraph.whenReady {
    val packagesRelease = allTasks.any { task ->
        task.project == project &&
            task.name.contains("Release") &&
            listOf("assemble", "bundle", "package", "sign", "install").any(task.name::startsWith)
    }
    if (packagesRelease && !hasSigningMaterial) {
        throw GradleException(
            "Release signing is incomplete; refusing to create an unsigned release artifact. " +
                "Missing: ${missingSigningMaterial.joinToString()}. " +
                "Provide keystore.properties or the JUSTTHECARBS_* environment variables."
        )
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)

    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    // camera-view drags in camera-video -> androidx.media3, which merges ACCESS_NETWORK_STATE into
    // the manifest. §9 permits CAMERA and INTERNET only, and the app never records video: it binds
    // Preview and ImageAnalysis, never VideoCapture. Excluding it honours §9 and drops the media3
    // and muxer code from the APK. (LifecycleCameraController would need this — PreviewView does not.)
    implementation(libs.camera.view) {
        exclude(group = "androidx.camera", module = "camera-video")
    }
    // ML Kit. NOTE: com.google.android.datatransport (Google's CCT telemetry transport) arrives
    // transitively via com.google.mlkit:common and CANNOT be excluded. Tested 2026-08-14: removing
    // it produces a fatal NoClassDefFoundError on CCTDestination the moment the scanner opens, so
    // it is load-bearing rather than optional analytics. It is disclosed in the privacy policy and
    // the Data Safety draft instead of being silently accepted. Do not re-attempt the exclusion.
    implementation(libs.mlkit.barcode)
    implementation(libs.mlkit.text)

    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.turbine)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
