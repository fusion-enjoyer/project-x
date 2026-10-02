plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

// Ekosistemin ortak tasarım dili: renkler, alt sayfa, ayar satırı, anahtar,
// boş durum. Her uygulama kendi vurgu rengini @color/vurgu ile ezer.
android {
    namespace = "com.ekosistem.tasarim"
    compileSdk = 36

    defaultConfig {
        minSdk = 21
        vectorDrawables.useSupportLibrary = true
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
    api("androidx.appcompat:appcompat:1.7.0")
}
