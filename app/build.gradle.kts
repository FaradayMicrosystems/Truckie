import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Optional settings go in local.properties (not committed). GitHub builds write it from repository secrets:
//   UPDATE_TOKEN=...       read-only GitHub token, only needed while the repo is private (update check)
//   KEYSTORE_FILE=...      shared signing key, so each new APK installs over the last one
//   KEYSTORE_PASSWORD=...  KEY_ALIAS=...  KEY_PASSWORD=...
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun prop(name: String, default: String = "") = localProps.getProperty(name, default)
fun quoted(name: String, default: String = "") = "\"${prop(name, default)}\""

android {
    namespace = "app.truckie"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.truckie"
        minSdk = 26
        targetSdk = 35
        versionCode = 8
        versionName = "0.4.2"
        buildConfigField("String", "UPDATE_REPO", "\"FaradayMicrosystems/truckie\"")
        // Never commit the token here: it comes from local.properties or the CI secret.
        buildConfigField("String", "UPDATE_TOKEN", quoted("UPDATE_TOKEN"))
    }

    // One permanent key, committed in signing/ (private repo), so every build signs the same and
    // updates install over each other. local.properties can still point at another keystore.
    val signing = java.util.Properties().apply {
        rootProject.file("signing/signing.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }
    fun signProp(key: String): String = prop(key).ifBlank { signing.getProperty(key).orEmpty() }
    val keystore = signProp("KEYSTORE_FILE").takeIf { it.isNotBlank() }?.let { rootProject.file(it) }?.takeIf { it.exists() }
    defaultConfig {
        // Lets the app warn about "App not installed" if a build ever falls back to a throwaway key.
        buildConfigField("boolean", "SHARED_SIGNING_KEY", (keystore != null).toString())
    }

    signingConfigs {
        if (keystore != null) {
            create("shared") {
                storeFile = keystore
                storePassword = signProp("KEYSTORE_PASSWORD")
                keyAlias = signProp("KEY_ALIAS")
                keyPassword = signProp("KEY_PASSWORD")
            }
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    buildTypes {
        debug {
            if (keystore != null) signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = false
            if (keystore != null) signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation(project(":core"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Hold-and-drag re-ordering in the stop list.
    implementation("sh.calvin.reorderable:reorderable:2.4.3")
    // Android Auto screens (same library and version as pinnr).
    implementation("androidx.car.app:app:1.4.0")
    implementation("androidx.car.app:app-projected:1.4.0")
}
