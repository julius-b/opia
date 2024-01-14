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
    implementation("com.arkivanov.decompose:decompose:2.2.2")
    implementation("com.arkivanov.decompose:extensions-compose-jetbrains:2.2.2")
    implementation("com.arkivanov.decompose:extensions-compose-jetpack:2.2.2")
    implementation("com.arkivanov.essenty:parcelable:1.3.0")
    implementation("com.arkivanov.essenty:lifecycle:1.3.0")

    implementation("com.arkivanov.mvikotlin:mvikotlin:3.3.0")
    implementation("com.arkivanov.mvikotlin:mvikotlin-extensions-coroutines:3.3.0")
    implementation("com.arkivanov.mvikotlin:rx:3.3.0") // Disposable

    implementation("com.michael-bull.kotlin-result:kotlin-result:1.1.18") // Result type

    // used in Broadcast Receiver
    implementation("app.cash.sqldelight:runtime:2.0.1")
    implementation("app.cash.sqldelight:coroutines-extensions:2.0.1")

    // Android
    implementation("com.arkivanov.mvikotlin:mvikotlin-logging:3.3.0")
    implementation("com.arkivanov.mvikotlin:mvikotlin-timetravel:3.3.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.appcompat:appcompat:1.6.1")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:1.2.2")

    // nonfree
    implementation("com.google.firebase:firebase-messaging-ktx:23.4.0")
}

android {
    namespace = "app.opia.android"
    compileSdk = 34
    buildToolsVersion = "34.0.0"

    defaultConfig {
        applicationId = "app.opia.android"
        minSdk = 24
        targetSdk = 34
        versionCode = 3
        versionName = "1.2-SNAPSHOT"
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.7"
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