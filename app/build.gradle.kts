plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "de.hysight.firereboot"
    compileSdk = 34

    defaultConfig {
        applicationId = "de.hysight.firereboot"
        minSdk = 28
        // Held at 28 intentionally: the app targets Fire HD 10 (Fire OS 7 / Android 9) and
        // benefits from API 28 background-service semantics. Bumping to 29+ would require
        // foreground-service-type declarations and break startForegroundService() from
        // BootReceiver. The matching `lint { disable += "ExpiredTargetSdkVersion" }` below
        // silences the Play Store warning — this app is sideload-only.
        targetSdk = 28
        versionCode = 1
        versionName = "1.0"

        val port = (project.findProperty("REBOOT_PORT") as String?) ?: "8080"
        buildConfigField("int", "REBOOT_PORT", port)
    }

    buildFeatures {
        buildConfig = true
    }

    lint {
        disable += "ExpiredTargetSdkVersion"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Intentional: release APK is signed with the debug keystore for sideloading.
            // The README documents that anyone replacing this APK must reset Device Admin /
            // accessibility manually. To distribute via Play / Amazon, replace with a real
            // signing config sourced from environment variables or a gitignored .properties.
            signingConfig = signingConfigs.getByName("debug")
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
