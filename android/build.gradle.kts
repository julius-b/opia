plugins {
    id("org.jetbrains.compose")
    id("com.android.application")
    kotlin("android")
}

group = "app.opia.android"
version = "1.0-SNAPSHOT"

dependencies {
    implementation(project(":common"))

    // common
    implementation("com.arkivanov.decompose:decompose:1.0.0-beta-04")
    implementation("com.arkivanov.decompose:extensions-compose-jetbrains:1.0.0-beta-04")
    implementation("com.arkivanov.decompose:extensions-compose-jetpack:1.0.0-beta-04")
    implementation("com.arkivanov.essenty:parcelable:1.0.0")
    implementation("com.arkivanov.essenty:lifecycle:1.0.0")

    implementation("com.arkivanov.mvikotlin:mvikotlin:3.1.0")
    implementation("com.arkivanov.mvikotlin:mvikotlin-extensions-coroutines:3.1.0")
    implementation("com.arkivanov.mvikotlin:rx:3.1.0") // Disposable

    // used in Broadcast Receiver
    implementation("com.squareup.sqldelight:runtime:1.5.5")
    implementation("com.squareup.sqldelight:coroutines-extensions:1.5.5")

    // Android
    implementation("com.arkivanov.mvikotlin:mvikotlin-logging:3.1.0")
    implementation("com.arkivanov.mvikotlin:mvikotlin-timetravel:3.1.0")
    implementation("com.github.UnifiedPush:android-connector:2.1.1")
    implementation("androidx.activity:activity-compose:1.6.1")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:1.2.2")
}

android {
    namespace = "app.opia.android"
    compileSdk = 33
    buildToolsVersion = "33.0.1"

    defaultConfig {
        applicationId = "app.opia.android"
        minSdk = 24
        targetSdk = 33
        versionCode = 1
        versionName = "1.0-SNAPSHOT"
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.4.0"
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