plugins {
    id("org.jetbrains.compose")
    id("com.android.application")
    // nonfree
    id("com.google.gms.google-services")
    kotlin("android")
}

group = "app.opia.android"
version = "1.0-SNAPSHOT"

dependencies {
    implementation(project(":common"))

    // common
    implementation("com.arkivanov.decompose:decompose:2.0.0")
    implementation("com.arkivanov.decompose:extensions-compose-jetbrains:2.0.0")
    implementation("com.arkivanov.decompose:extensions-compose-jetpack:2.0.0")
    implementation("com.arkivanov.essenty:parcelable:1.1.0")
    implementation("com.arkivanov.essenty:lifecycle:1.1.0")

    implementation("com.arkivanov.mvikotlin:mvikotlin:3.2.1")
    implementation("com.arkivanov.mvikotlin:mvikotlin-extensions-coroutines:3.2.1")
    implementation("com.arkivanov.mvikotlin:rx:3.2.1") // Disposable

    implementation("com.michael-bull.kotlin-result:kotlin-result:1.1.18") // Result type

    // used in Broadcast Receiver
    implementation("com.squareup.sqldelight:runtime:1.5.5")
    implementation("com.squareup.sqldelight:coroutines-extensions:1.5.5")

    // Android
    implementation("com.arkivanov.mvikotlin:mvikotlin-logging:3.2.1")
    implementation("com.arkivanov.mvikotlin:mvikotlin-timetravel:3.2.1")
    implementation("androidx.activity:activity-compose:1.7.2")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:1.2.2")

    // nonfree
    implementation("com.google.firebase:firebase-messaging-ktx:23.2.0")
}

android {
    namespace = "app.opia.android"
    compileSdk = 33
    buildToolsVersion = "33.0.1"

    defaultConfig {
        applicationId = "app.opia.android"
        minSdk = 24
        targetSdk = 33
        versionCode = 2
        versionName = "1.1-SNAPSHOT"
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.0"
    }
    compileOptions {
        // https://developer.android.com/studio/write/java8-support#library-desugaring
        isCoreLibraryDesugaringEnabled = true

        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}