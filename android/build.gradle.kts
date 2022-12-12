plugins {
    id("org.jetbrains.compose")
    id("com.android.application")
    kotlin("android")
}

group = "app.opia.android"
version = "1.0-SNAPSHOT"

dependencies {
    implementation(project(":common"))
    implementation(compose.material)
    implementation("androidx.activity:activity-compose:1.6.1")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:1.2.2")

    implementation("com.squareup.sqldelight:runtime:1.5.4")
    implementation("com.squareup.sqldelight:coroutines-extensions:1.5.4")

    implementation("com.squareup.moshi:moshi:1.14.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.14.0")
    implementation("com.squareup.moshi:moshi-adapters:1.14.0")
    implementation("com.squareup.moshi:moshi-kotlin-codegen:1.14.0")

    implementation("com.squareup.okhttp3:logging-interceptor:4.10.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.9.0")

    implementation("com.arkivanov.decompose:decompose:1.0.0-beta-01")
    implementation("com.arkivanov.decompose:extensions-compose-jetbrains:1.0.0-beta-01")
    implementation("com.arkivanov.essenty:parcelable:0.6.0")
    implementation("com.arkivanov.essenty:lifecycle:0.6.0")

    implementation("com.arkivanov.mvikotlin:mvikotlin:3.0.2")
    implementation("com.arkivanov.mvikotlin:mvikotlin-extensions-coroutines:3.0.2")
    implementation("com.arkivanov.mvikotlin:mvikotlin-extensions-reaktive:3.0.2")
    implementation("com.arkivanov.mvikotlin:rx:3.0.2") // Disposable

    implementation("com.badoo.reaktive:reaktive:1.2.2")
    implementation("com.badoo.reaktive:coroutines-interop:1.2.2")

    // Android
    implementation("com.arkivanov.mvikotlin:mvikotlin-logging:3.0.2")
    implementation("com.arkivanov.mvikotlin:mvikotlin-timetravel:3.0.2")
}

android {
    namespace = "app.opia.android"
    compileSdk = 33
    buildToolsVersion = "30.0.3"

    defaultConfig {
        applicationId = "app.opia.android"
        minSdk = 24
        targetSdk = 33
        versionCode = 1
        versionName = "1.0-SNAPSHOT"
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.3.2"
    }
    compileOptions {
        // https://developer.android.com/studio/write/java8-support#library-desugaring
        isCoreLibraryDesugaringEnabled = true

        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
}