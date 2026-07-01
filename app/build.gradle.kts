plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ximalu.wmbridge"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ximalu.wmbridge"
        minSdk = 26
        targetSdk = 35
        versionCode = 8
        versionName = "1.0.0-beta8"
    }

    signingConfigs {
        create("release") {
            storeFile = file("wmbridge.keystore")
            storePassword = "wmbridge123"
            keyAlias = "wmbridge"
            keyPassword = "wmbridge123"
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }

    applicationVariants.all {
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            output.outputFileName = "WMBridge.${versionName}.apk"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.9.3")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
