plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "it.furlan.nassync"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.nassync.mobile"
        minSdk = 26
        targetSdk = 36
        versionCode = 26
        versionName = "1.0.26"
    }

    // La chiave di firma NON e' in questo repository.
    // Le build di F-Droid vengono firmate da F-Droid stesso; per firmare in proprio
    // impostare le variabili d'ambiente NASSYNC_KEYSTORE, NASSYNC_KEYSTORE_PASSWORD,
    // NASSYNC_KEY_ALIAS e NASSYNC_KEY_PASSWORD.
    val keystorePath = System.getenv("NASSYNC_KEYSTORE")
    if (keystorePath != null) {
        signingConfigs {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("NASSYNC_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("NASSYNC_KEY_ALIAS")
                keyPassword = System.getenv("NASSYNC_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Toglie il codice inutilizzato: app piu' piccola, installazione e avvio piu' veloci
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystorePath != null) signingConfig = signingConfigs.getByName("release")
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
        compose = true
        buildConfig = true
    }

    // Due versioni dallo stesso codice:
    // - play:   per Google Play, senza link di donazione (non ammessi da Google)
    // - fdroid: per F-Droid / APK, con il pulsante "Sostieni lo sviluppo"
    flavorDimensions += "store"
    productFlavors {
        create("play") {
            dimension = "store"
            buildConfigField("String", "DONATE_URL", "\"\"")
        }
        create("fdroid") {
            dimension = "store"
            buildConfigField("String", "DONATE_URL", "\"https://www.paypal.me/enricofurlan\"")
        }
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.github.rburgst:okhttp-digest:3.1.0")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-video:2.7.0")
}
