plugins {
    id("com.android.application")
}

android {
    namespace = "com.jhopanstore.vpnhospot"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.jhopanstore.vpnhospot"
        minSdk = 23
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildFeatures {
        buildConfig = true
    }

    // ── APK Splits: 3 variant (arm64-v8a, armeabi-v7a, universal) ──
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = true
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Sign release builds with the project keystore when present.
            // CI injects it via GitHub secrets; local devs must place the
            // .keystore file at app/vpnhospot-release.keystore themselves.
            val keystoreFile = rootProject.file("app/vpnhospot-release.keystore")
            val storePwd = providers.environmentVariable("VPN_HOSPOT_KEYSTORE_PASSWORD")
                .orElse(providers.gradleProperty("vpnHospot.keystorePassword")).orNull
            val keyPwd = providers.environmentVariable("VPN_HOSPOT_KEY_PASSWORD")
                .orElse(providers.gradleProperty("vpnHospot.keyPassword")).orNull
            if (keystoreFile.exists() && storePwd != null && keyPwd != null) {
                signingConfig = signingConfigs.create("release") {
                    this.storeFile = keystoreFile
                    this.storePassword = storePwd
                    this.keyAlias = "vpnhospot"
                    this.keyPassword = keyPwd
                }
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
