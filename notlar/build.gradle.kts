plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ekosistem.notlar"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ekosistem.notlar"
        minSdk = 21
        targetSdk = 36
        versionCode = 14
        versionName = "0.11.0"
        vectorDrawables.useSupportLibrary = true
        // AppCompat 80'den fazla dil taşıyor; sadece bizim dillerimiz kalsın.
        resourceConfigurations += listOf("tr", "en")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.documentfile:documentfile:1.0.1")
}
