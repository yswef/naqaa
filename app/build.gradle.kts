import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Signing material is supplied either by keystore.properties (local builds) or by the
// environment (CI). Nothing is committed: a missing key simply yields an unsigned release.
val signingProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun signingValue(property: String, environment: String): String =
    signingProperties.getProperty(property) ?: System.getenv(environment) ?: ""

val storeFilePath = signingValue("storeFile", "NAQAA_STORE_FILE")
val storePasswordValue = signingValue("storePassword", "NAQAA_STORE_PASSWORD")
val keyAliasValue = signingValue("keyAlias", "NAQAA_KEY_ALIAS")
val keyPasswordValue = signingValue("keyPassword", "NAQAA_KEY_PASSWORD")
val signingReady = storeFilePath.isNotBlank() && File(storeFilePath).exists() &&
    storePasswordValue.isNotBlank() && keyAliasValue.isNotBlank() && keyPasswordValue.isNotBlank()

android {
    namespace = "com.naqaa.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.naqaa.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 8
        versionName = "1.0.7"
        resourceConfigurations += listOf("ar", "en")
    }

    signingConfigs {
        if (signingReady) {
            create("release") {
                storeFile = file(storeFilePath)
                storePassword = storePasswordValue
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
                // All three schemes: v2 and v3 are what modern Android checks, and the JAR
                // signature is kept because the installers on some older vendor builds still
                // look for it and refuse the file when it is missing.
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (signingReady) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            // The universal file carries both architectures, so a phone whose architecture the
            // user cannot determine still installs with one download.
            isUniversalApk = true
        }
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "DebugProbesKt.bin", "kotlin-tooling-metadata.json")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.05.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.core:core-ktx:1.16.0")

    testImplementation("junit:junit:4.13.2")
}
