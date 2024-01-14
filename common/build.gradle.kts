plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    id("com.android.library")
    id("kotlin-parcelize")
    id("app.cash.sqldelight")
    id("com.google.devtools.ksp")
}

sqldelight {
    databases {
        create("OpiaDatabase") {
            packageName.set("app.opia.db")
        }
    }
}

group = "app.opia"
version = "1.0-SNAPSHOT"

kotlin {
    androidTarget()
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
                implementation("androidx.appcompat:appcompat:1.6.1")
                implementation("androidx.compose.ui:ui-text:1.4.3")

                implementation("app.cash.sqldelight:runtime:2.0.1")
                implementation("app.cash.sqldelight:coroutines-extensions:2.0.1")
                implementation("app.cash.sqldelight:primitive-adapters:2.0.1")

                implementation("com.squareup.moshi:moshi:1.15.0")
                implementation("com.squareup.moshi:moshi-kotlin:1.15.0") // reflection
                implementation("com.squareup.moshi:moshi-adapters:1.15.0")
                implementation("com.squareup.moshi:moshi-kotlin-codegen:1.15.0")
                //ksp("com.squareup.moshi:moshi-kotlin-codegen:1.15.0")
                configurations["ksp"].dependencies.add(project.dependencies.create("com.squareup.moshi:moshi-kotlin-codegen:1.15.0"))

                implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
                implementation("com.squareup.retrofit2:retrofit:2.9.0")
                implementation("com.squareup.retrofit2:converter-moshi:2.9.0")

                implementation("com.arkivanov.decompose:decompose:2.2.2")
                implementation("com.arkivanov.decompose:extensions-compose-jetbrains:2.2.2")
                implementation("com.arkivanov.essenty:parcelable:1.3.0")
                implementation("com.arkivanov.essenty:lifecycle:1.3.0")

                implementation("com.arkivanov.mvikotlin:mvikotlin:3.3.0")
                implementation("com.arkivanov.mvikotlin:mvikotlin-extensions-coroutines:3.3.0")
                implementation("com.arkivanov.mvikotlin:rx:3.3.0") // Disposable

                implementation("com.michael-bull.kotlin-result:kotlin-result:1.1.18") // Result type
                implementation("com.russhwolf:multiplatform-settings:1.1.1")
                implementation("co.touchlab:kermit:2.0.2")

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
                api("androidx.core:core-ktx:1.12.0")
                implementation("app.cash.sqldelight:android-driver:2.0.1")
                implementation("app.cash.sqldelight:sqlite-driver:2.0.1")

                implementation("com.google.android.material:material:1.11.0")
            }
        }
        val desktopMain by getting {
            dependencies {
                api(compose.preview)
                implementation("app.cash.sqldelight:sqlite-driver:2.0.1")

                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.7.3") // coroutines Main
                implementation("com.arkivanov.mvikotlin:mvikotlin-main:3.3.0") // DefaultStoreFactory
            }
        }
        val desktopTest by getting
    }
}

android {
    // NOTE: needs to be different from actual android to prevent 'Type app.opia.android.Buildconfig is defined multiple times'
    namespace = "app.opia.common"
    compileSdk = 34
    buildToolsVersion = "34.0.0"

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    defaultConfig {
        minSdk = 24
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
