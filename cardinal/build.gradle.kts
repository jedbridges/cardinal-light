plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.light.sdk)
}

android {
    compileSdk = rootProject.ext["compileSdk"] as Int

    signingConfigs {
        create("lightsdkDev") {
            storeFile = file("../sdk/keys/lightsdk-dev.jks")
            storePassword = "android"
            keyAlias = "lightsdk-dev"
            keyPassword = "android"
            enableV3Signing = true
            enableV4Signing = true
        }
    }

    defaultConfig {
        minSdk = rootProject.ext["minSdk"] as Int
        targetSdk = rootProject.ext["targetSdk"] as Int

        manifestPlaceholders["sdkVersion"] = property("sdkVersion") as String
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("lightsdkDev")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("lightsdkDev")
        }
    }

    lint {
        warningsAsErrors = false
        error += "RestrictedApi"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(rootProject.ext["jvmTarget"] as String)
        targetCompatibility = JavaVersion.toVersion(rootProject.ext["jvmTarget"] as String)
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(rootProject.ext["jvmTarget"] as String))
    }
}

dependencies {
    implementation(project(":sdk:client")) {
        // :sdk:ui ships LightQrCodeScanner, which pulls ML Kit's barcode model:
        // libbarhopper_v3.so is 19.3 MB of a 29 MB release APK, two thirds of
        // the whole thing, across four ABIs. Cardinal never scans anything, so
        // it comes out and the APK is 8.3 MB. Every screen was exercised on
        // debug and on a minified release build with no missing classes.
        //
        // If a tool here ever does want the scanner, delete these two lines.
        exclude(group = "com.google.mlkit")
        exclude(group = "com.google.android.gms", module = "play-services-mlkit-barcode-scanning")
    }
    testImplementation(libs.kotlin.test)
    // No Room: reading position and highlights live in the SDK DataStore, and
    // scripture is read straight out of assets. See data/ReaderStore.kt.
}
