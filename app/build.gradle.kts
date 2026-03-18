plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.skyler.caffeinate"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.skyler.caffeinate"
        minSdk = 33
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"

        buildConfigField("String", "GITHUB_REPO", "\"skylerkatz/andriod-caffinate\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
}
