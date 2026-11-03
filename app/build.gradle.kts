import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing: put keystore.properties in the project root (never commit it),
// or set CARO_KEYSTORE / CARO_KEYSTORE_PASSWORD / CARO_KEY_ALIAS / CARO_KEY_PASSWORD env vars.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(prop: String, env: String): String? =
    keystoreProps.getProperty(prop) ?: System.getenv(env)

android {
    namespace = "com.sanoob.caro"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sanoob.caro"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    val storePath = signingValue("storeFile", "CARO_KEYSTORE")
    signingConfigs {
        if (storePath != null) {
            create("release") {
                storeFile = file(storePath)
                storePassword = signingValue("storePassword", "CARO_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "CARO_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "CARO_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (storePath != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
