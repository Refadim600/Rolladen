plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "de.refadim.rollladen"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.refadim.rollladen"
        // Android 5.0 und neuer (java.time über Desugaring)
        minSdk = 21
        targetSdk = 35
        versionCode = 5
        versionName = "2.0.2"
    }

    // Fester Schlüssel im Repo: Jeder Build hat dieselbe Signatur, Updates lassen sich drüber installieren.
    signingConfigs {
        create("fixed") {
            storeFile = file("rollladen.jks")
            storePassword = "rollladen"
            keyAlias = "rollladen"
            keyPassword = "rollladen"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("fixed")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fixed")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("androidx.documentfile:documentfile:1.0.1")
    // Bilder der Diashow
    implementation("io.coil-kt:coil-compose:2.7.0")
    // Wetterkarte (OpenStreetMap + DWD-Radar per WMS)
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    // WebSocket zu den Taster-Modulen
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")
}
