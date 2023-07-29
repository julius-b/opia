plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    id("com.android.library")
    id("kotlin-parcelize")
    id("com.squareup.sqldelight")
    id("com.google.devtools.ksp")
}

sqldelight {
    database("OpiaDatabase") {
        packageName = "app.opia.db"
    }
}

group = "app.opia"
version = "1.0-SNAPSHOT"

kotlin {
    android()
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        kotlinOptions {
            jvmTarget = "11"
        }
    }
    jvm("desktop") {
        compilations.all {
            kotlinOptions.jvmTarget = "11"
        }
    }
    java {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(compose.runtime)
                api(compose.foundation)
                api(compose.material)
                api(compose.materialIconsExtended)
                api(compose.ui)
                api("androidx.appcompat:appcompat:1.6.1")
                api("androidx.compose.ui:ui-text:1.4.3")

                implementation("com.squareup.sqldelight:runtime:1.5.5")
                implementation("com.squareup.sqldelight:coroutines-extensions:1.5.5")

                implementation("com.squareup.moshi:moshi:1.15.0")
                implementation("com.squareup.moshi:moshi-kotlin:1.15.0") // reflection
                implementation("com.squareup.moshi:moshi-adapters:1.15.0")
                implementation("com.squareup.moshi:moshi-kotlin-codegen:1.15.0")
                //ksp("com.squareup.moshi:moshi-kotlin-codegen:1.15.0")
                configurations["ksp"].dependencies.add(project.dependencies.create("com.squareup.moshi:moshi-kotlin-codegen:1.15.0"))

                implementation("com.squareup.okhttp3:logging-interceptor:4.10.0")
                implementation("com.squareup.retrofit2:retrofit:2.9.0")
                implementation("com.squareup.retrofit2:converter-moshi:2.9.0")

                implementation("com.arkivanov.decompose:decompose:2.0.0")
                implementation("com.arkivanov.decompose:extensions-compose-jetbrains:2.0.0")
                implementation("com.arkivanov.essenty:parcelable:1.1.0")
                implementation("com.arkivanov.essenty:lifecycle:1.1.0")

                implementation("com.arkivanov.mvikotlin:mvikotlin:3.2.1")
                implementation("com.arkivanov.mvikotlin:mvikotlin-extensions-coroutines:3.2.1")
                implementation("com.arkivanov.mvikotlin:rx:3.2.1") // Disposable

                implementation("com.michael-bull.kotlin-result:kotlin-result:1.1.18") // Result type
                implementation("com.russhwolf:multiplatform-settings:1.0.0")

                implementation("ch.oxc.nikea:nikea-kt:1.0-SNAPSHOT")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val androidMain by getting {
            dependencies {
                api("androidx.core:core-ktx:1.10.1")
                implementation("com.squareup.sqldelight:android-driver:1.5.5")
                implementation("com.squareup.sqldelight:sqlite-driver:1.5.5")

                implementation("com.google.android.material:material:1.9.0")
            }
        }
        val desktopMain by getting {
            dependencies {
                api(compose.preview)
                implementation("com.squareup.sqldelight:sqlite-driver:1.5.5")

                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.6.4") // coroutines Main
                implementation("com.arkivanov.mvikotlin:mvikotlin-main:3.2.1") // DefaultStoreFactory
            }
        }
        val desktopTest by getting
    }
}

android {
    // NOTE: needs to be different from actual android to prevent 'Type app.opia.android.Buildconfig is defined multiple times'
    namespace = "app.opia.common"
    compileSdk = 33
    buildToolsVersion = "33.0.1"

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    defaultConfig {
        minSdk = 24
        targetSdk = 33
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true

        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    dependencies {
        coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:1.2.2")
    }
}
