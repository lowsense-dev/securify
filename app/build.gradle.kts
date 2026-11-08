import java.security.MessageDigest
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val libv2ray = file("libs/libv2ray.aar")
if (!libv2ray.exists()) {
    val url = "https://github.com/2dust/AndroidLibXrayLite/releases/download/v26.10.31/libv2ray.aar"
    val part = file("libs/libv2ray.aar.part").apply { parentFile.mkdirs() }
    uri(url).toURL().openStream().use { input -> part.outputStream().use { input.copyTo(it) } }
    val sha = MessageDigest.getInstance("SHA-256").digest(part.readBytes()).joinToString("") { "%02x".format(it) }
    check(sha == "2efdf731b1ed624eb1cb0d5a4d0aa95c0672e89f9702226d1ba2dc90308630b6") { "libv2ray.aar checksum mismatch: $sha" }
    part.renameTo(libv2ray)
}

val keystore = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}
fun secret(key: String, env: String): String? = keystore.getProperty(key) ?: System.getenv(env)

android {
    namespace = "app.securify"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.securify"
        minSdk = 26
        targetSdk = 37
        versionCode = (findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = findProperty("versionName") as String? ?: "1.0.0"
    }

    signingConfigs {
        create("release") {
            secret("storeFile", "SIGNING_STORE_FILE")?.let { storeFile = rootProject.file(it) }
            storePassword = secret("storePassword", "SIGNING_STORE_PASSWORD")
            keyAlias = secret("keyAlias", "SIGNING_KEY_ALIAS")
            keyPassword = secret("keyPassword", "SIGNING_KEY_PASSWORD")
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val release = signingConfigs.getByName("release")
            signingConfig = if (release.storeFile != null) release else signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs.useLegacyPackaging = true
    }
}

dependencies {
    implementation(files("libs/libv2ray.aar"))

    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.material3:material3:1.5.0-rc01")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.graphics:graphics-shapes:1.1.0")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
}
