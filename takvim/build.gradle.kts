import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

/*
 * Yayın imzası repo dışında durur: ~/.gradle/gradle.properties içindeki
 * `imzaDosyasi` bir .properties dosyasını gösterir (storeFile, storePassword,
 * keyAlias, keyPassword). Ayar yoksa release imzasız derlenir; kodu klonlayan
 * herkes kendi anahtarıyla imzalayabilir. Notlar'la aynı anahtar.
 */
val imza: Properties? = (findProperty("imzaDosyasi") as String?)
    ?.let { file(it) }
    ?.takeIf { it.exists() }
    ?.let { dosya -> Properties().apply { dosya.inputStream().use { load(it) } } }

android {
    namespace = "com.ekosistem.takvim"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ekosistem.takvim"
        minSdk = 21
        targetSdk = 36
        versionCode = 12
        versionName = "0.11.0"
        vectorDrawables.useSupportLibrary = true
        // AppCompat 80'den fazla dil taşıyor; sadece bizim dillerimiz kalsın.
        resourceConfigurations += listOf("tr", "en")
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        if (imza != null) {
            create("yayin") {
                storeFile = file(imza.getProperty("storeFile"))
                storePassword = imza.getProperty("storePassword")
                keyAlias = imza.getProperty("keyAlias")
                keyPassword = imza.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (imza != null) signingConfig = signingConfigs.getByName("yayin")
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
