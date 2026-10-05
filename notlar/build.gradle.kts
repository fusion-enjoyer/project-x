import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

/*
 * Yayın imzası repo dışında durur: ~/.gradle/gradle.properties içindeki
 * `imzaDosyasi` bir .properties dosyasını gösterir (storeFile, storePassword,
 * keyAlias, keyPassword). Ayar yoksa release imzasız derlenir; kodu klonlayan
 * herkes kendi anahtarıyla imzalayabilir.
 */
val imza: Properties? = (findProperty("imzaDosyasi") as String?)
    ?.let { file(it) }
    ?.takeIf { it.exists() }
    ?.let { dosya -> Properties().apply { dosya.inputStream().use { load(it) } } }

android {
    namespace = "com.ekosistem.notlar"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ekosistem.notlar"
        minSdk = 21
        targetSdk = 36
        versionCode = 24
        versionName = "0.21.0"
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

    testOptions {
        // Saf mantık testleri Android sınıflarına dokunursa çökmesin, varsayılan dönsün.
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.documentfile:documentfile:1.0.1")
    // Görsel küçültülürken EXIF yönünü okumak için (eski sürümlerde de çalışır).
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    testImplementation("junit:junit:4.13.2")
    // Android'deki org.json testte boş taslak; gerçeği yalnızca testlere girer.
    testImplementation("org.json:json:20240303")
}
