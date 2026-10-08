plugins {
    alias(libs.plugins.android.application)
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
}

android {
    namespace = "com.example.filesapp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.filesapp"
        minSdk = 29 // Android 10 (API 29) compliance
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("debugConfig") {
            storeFile = file("${rootDir}/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        val keystoreFileEnv = System.getenv("KEYSTORE_FILE")
        val keystorePasswordEnv = System.getenv("KEYSTORE_PASSWORD")
        val keyAliasEnv = System.getenv("KEY_ALIAS")
        val keyPasswordEnv = System.getenv("KEY_PASSWORD")

        val hasReleaseCredentials = !keystoreFileEnv.isNullOrBlank() &&
                !keystorePasswordEnv.isNullOrBlank() &&
                !keyAliasEnv.isNullOrBlank() &&
                !keyPasswordEnv.isNullOrBlank()

        if (hasReleaseCredentials) {
            val keyFile = file(keystoreFileEnv)
            if (!keyFile.exists()) {
                throw org.gradle.api.GradleException("Release KEYSTORE_FILE specified at '$keystoreFileEnv' does not exist!")
            }
            create("release") {
                storeFile = keyFile
                storePassword = keystorePasswordEnv
                keyAlias = keyAliasEnv
                keyPassword = keyPasswordEnv
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debugConfig")
        }
        release {
            // DIAGNOSTIC R8 CONFIG (ChatGPT suggestion - 2026-10-08).
            // R8 ON with -keep ** (keeps all classes, shrinking still runs).
            // If this launches: shrinking was removing something needed.
            // If this crashes: shrinking is NOT the cause.
            isMinifyEnabled = true
            // Resource shrinking disabled: it was stripping resources and
            // crashing the app on launch. Re-enable only after verifying.
            isShrinkResources = false
            val releaseSigning = signingConfigs.findByName("release")
            if (releaseSigning != null) {
                signingConfig = releaseSigning
            } else {
                // If release signing credentials are missing, do not silently fall back to debug.
                signingConfig = null
            }
            proguardFiles(
                // AGP 9 requires proguard-android-optimize.txt; disable the
                // aggressive optimizations via -dontoptimize in proguard-rules.pro
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/license.txt"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/NOTICE.txt"
            excludes += "META-INF/notice.txt"
            excludes += "META-INF/ASL2.0"
            excludes += "META-INF/INDEX.LIST"
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Coroutines & Lifecycle
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Google Play Services Auth & Drive API
    implementation("com.google.android.gms:play-services-auth:20.7.0")
    implementation("com.google.api-client:google-api-client-android:2.2.0")
    implementation("com.google.apis:google-api-services-drive:v3-rev20230822-2.0.0")

    // Coil for Compose Image Loading
    implementation("io.coil-kt:coil-compose:2.5.0")

    // Archive support (7Z, TAR, RAR, GZ)
    implementation("org.apache.commons:commons-compress:1.26.0")
    implementation("org.tukaani:xz:1.9")
    implementation("com.github.junrar:junrar:7.5.5")
    // Password-protected ZIP (AES + ZipCrypto) - ZArchiver parity
    implementation("net.lingala.zip4j:zip4j:2.11.5")

    // Nearby Share (P2P file sharing) - Google Files parity
    implementation("com.google.android.gms:play-services-nearby:19.3.0")

    // PDF text search support
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // DocumentFile for SAF (Storage Access Framework)
    implementation("androidx.documentfile:documentfile:1.0.1")

    // FTP network client
    implementation("commons-net:commons-net:3.10.0")

    // QR Code generation
    implementation("com.google.zxing:core:3.5.3")

    // Unit Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}
