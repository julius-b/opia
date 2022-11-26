import org.jetbrains.compose.compose
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
}

group = "app.opia"
version = "1.0-SNAPSHOT"

kotlin {
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "11"
        }
        withJava()
    }
    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(project(":common"))
                implementation(compose.desktop.currentOs)
                runtimeOnly("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.6.4") // coroutines Main
                implementation("com.arkivanov.mvikotlin:mvikotlin-main:3.0.0") // DefaultStoreFactory

                implementation("com.squareup.sqldelight:runtime:1.5.4")
                implementation("com.squareup.sqldelight:coroutines-extensions:1.5.4")

                implementation("com.squareup.moshi:moshi:1.14.0")
                implementation("com.squareup.moshi:moshi-kotlin:1.14.0")
                implementation("com.squareup.moshi:moshi-adapters:1.14.0")

                implementation("com.squareup.okhttp3:logging-interceptor:4.10.0")
                implementation("com.squareup.retrofit2:retrofit:2.9.0")
                implementation("com.squareup.retrofit2:converter-moshi:2.9.0")

                implementation("com.arkivanov.decompose:decompose:1.0.0-alpha-07")
                implementation("com.arkivanov.decompose:extensions-compose-jetbrains:1.0.0-alpha-07")
                implementation("com.arkivanov.essenty:parcelable:0.6.0")
                implementation("com.arkivanov.essenty:lifecycle:0.6.0")

                implementation("com.arkivanov.mvikotlin:mvikotlin:3.0.0")
                implementation("com.arkivanov.mvikotlin:mvikotlin-extensions-coroutines:3.0.0")
                implementation("com.arkivanov.mvikotlin:mvikotlin-extensions-reaktive:3.0.0")
                implementation("com.arkivanov.mvikotlin:rx:3.0.0") // Disposable

                implementation("com.badoo.reaktive:reaktive:1.2.2")
                implementation("com.badoo.reaktive:coroutines-interop:1.2.2")
            }
        }
        val jvmTest by getting
    }
}

compose.desktop {
    application {
        mainClass = "app.opia.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "opia-compose"
            packageVersion = "1.0.0"
        }
    }
}
