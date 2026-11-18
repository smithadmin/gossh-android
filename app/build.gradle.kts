import com.android.build.api.variant.HasUnitTestBuilder

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.gossh"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.gossh"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
    packaging {
        resources {
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/versions/**", "META-INF/DEPENDENCIES")
        }
    }
}

androidComponents {
    beforeVariants(selector().all()) {
        (it as? HasUnitTestBuilder)?.enableUnitTest = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")

    // SSH (maintained JSch fork) + a full BouncyCastle for modern key types on Android
    implementation("com.github.mwiede:jsch:0.2.20")
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
}
