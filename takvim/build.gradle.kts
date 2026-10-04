plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ekosistem.takvim"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ekosistem.takvim"
        minSdk = 21
        targetSdk = 36
        versionCode = 4
        versionName = "0.4.0"
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
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":tasarim"))

    testImplementation("junit:junit:4.13.2")
}
