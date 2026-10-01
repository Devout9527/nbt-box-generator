plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "cn.nbtgen.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "cn.nbtgen.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 22
        versionName = "0.0.22"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    // 纯框架实现，无第三方依赖
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013")
}
