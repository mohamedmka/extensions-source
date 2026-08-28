plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    compileSdk = 34
    
    defaultConfig {
        minSdk = 21
        targetSdk = 34
    }
    
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    
    kotlinOptions {
        jvmTarget = "11"
    }
    
    namespace = "eu.kanade.tachiyomi.extension.ar.procomic"
}

dependencies {
    // Tachiyomi/Mihon API
    compileOnly(libs.tachiyomi.api)
    
    // HTTP Client
    compileOnly(libs.okhttp)
    
    // Parsing
    compileOnly(libs.jsoup)
    
    // Kotlin
    implementation(libs.kotlin.stdlib)
    
    // Android
    compileOnly(libs.androidx.core)
}
