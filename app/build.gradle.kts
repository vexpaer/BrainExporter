import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val signKeystorePath: String = providers.gradleProperty("SIGNING_KEYSTORE").orNull
    ?: rootProject.file(".signing/brainexporter-release.jks").path
val signKeyAlias: String = providers.gradleProperty("SIGNING_KEY_ALIAS").orNull ?: "brainexporter"
val signKeyPassword: String? = run {
    providers.gradleProperty("SIGNING_KEY_PASSWORD").orNull
        ?: rootProject.file(".signing/key-password.txt").takeIf { it.exists() }?.readText()?.trim()
}
val signStorePassword: String? = providers.gradleProperty("SIGNING_STORE_PASSWORD").orNull
    ?: rootProject.file(".signing/signing-password.txt").takeIf { it.exists() }?.readText()?.trim()

android {
    namespace = "io.github.vexpaer.brainexporter.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.vexpaer.brainexporter"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "0.3.0"
    }

    signingConfigs {
        if (signKeyPassword != null && signStorePassword != null) {
            create("release") {
                storeFile = file(signKeystorePath)
                keyAlias = signKeyAlias
                keyPassword = signKeyPassword
                storePassword = signStorePassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures.compose = true
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions.jvmTarget = "17"
}

dependencies {
    implementation(project(":sdk-core"))
    implementation(project(":core-runtime"))
    implementation(project(":plugin-device-rtbci"))
    implementation(project(":plugin-algorithm-basic"))
    implementation(project(":plugin-ui-monitor"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.3")
    testImplementation("junit:junit:4.13.2")
}
