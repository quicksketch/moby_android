import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing, from environment variables (the release workflow) or an
// uncommitted keystore.properties file (local builds). Without either, release
// builds are unsigned. See "Releasing" in README.md.
val keystoreProperties = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}

fun signingValue(env: String, property: String): String? =
    System.getenv(env)?.takeIf { it.isNotEmpty() } ?: keystoreProperties.getProperty(property)

val releaseKeystore = signingValue("MOBY_KEYSTORE_FILE", "storeFile")

android {
    namespace = "org.mobyapp.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "org.mobyapp.android"
        minSdk = 26
        targetSdk = 37
        // The release workflow sets these from the release's tag.
        versionCode = providers.gradleProperty("mobyVersionCode").orNull?.toInt() ?: 1
        versionName = providers.gradleProperty("mobyVersionName").orNull ?: "0.1.0"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = rootProject.file(releaseKeystore)
                storePassword = signingValue("MOBY_KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signingValue("MOBY_KEY_ALIAS", "keyAlias")
                // PKCS12 keystores, keytool's default, use one password for
                // the keystore and its key.
                keyPassword = signingValue("MOBY_KEY_PASSWORD", "keyPassword") ?: storePassword
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.media3.transformer)
    implementation(libs.media3.effect)
    implementation(libs.media3.common)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.coil.compose)
    implementation(libs.coil.video)
    implementation(libs.coil.svg)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    // Android's org.json is only a stub in JVM unit tests; this is the real one.
    testImplementation(libs.json)
    testImplementation(libs.kotlinx.coroutines.test)
}
