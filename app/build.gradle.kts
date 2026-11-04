import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing: put keystore.properties in the project root (never commit it),
// or set CP_KEYSTORE / CP_KEYSTORE_PASSWORD / CP_KEY_ALIAS / CP_KEY_PASSWORD env vars.
// Without them the release APK is signed with the debug key so it can still be installed.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(prop: String, env: String): String? =
    keystoreProps.getProperty(prop) ?: System.getenv(env)?.takeIf { it.isNotBlank() }

android {
    namespace = "com.chaosparty"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.chaosparty.game"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"
    }

    val storePath = signingValue("storeFile", "CP_KEYSTORE")
    signingConfigs {
        if (storePath != null) {
            create("release") {
                storeFile = file(storePath)
                storePassword = signingValue("storePassword", "CP_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "CP_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "CP_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (storePath != null) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    packaging {
        resources.excludes += listOf("META-INF/*.kotlin_module", "kotlin/**", "DebugProbesKt.bin")
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty("shots", project.findProperty("shots")?.toString() ?: "")
            // Optional: point Robolectric at a folder with pre-downloaded android-all jars.
            project.findProperty("roboDir")?.let { dir ->
                it.systemProperty("robolectric.offline", "true")
                it.systemProperty("robolectric.dependency.dir", dir.toString())
            }
        }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
