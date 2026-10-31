// Build modul app Chill World
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.vinz.chillworld"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.vinz.chillworld"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            buildConfigField("boolean", "GL_DEBUG", "true")
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("boolean", "GL_DEBUG", "false")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // jangan kompres texture jpg biar loading cepat dan aman
    androidResources {
        noCompress += listOf("jpg", "png", "glsl", "vert", "frag", "comp")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
}
