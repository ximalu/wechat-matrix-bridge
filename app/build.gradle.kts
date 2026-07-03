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
        versionCode = 21
        versionName = "1.3.1"
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("WMB_KEYSTORE_PATH") ?: error(
                "签名配置缺失：请在环境变量或 local.properties 中设置 WMB_KEYSTORE_PATH、" +
                "WMB_KEYSTORE_PASS、WMB_KEY_PASS"
            )
            storeFile = file(keystorePath)
            storePassword = System.getenv("WMB_KEYSTORE_PASS") ?: error("WMB_KEYSTORE_PASS 未设置")
            keyAlias = System.getenv("WMB_KEY_ALIAS") ?: "wmbridge"
            keyPassword = System.getenv("WMB_KEY_PASS") ?: error("WMB_KEY_PASS 未设置")
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
