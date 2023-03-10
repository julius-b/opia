import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
}

group = "app.opia.deskop"
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

                // common
                implementation("com.arkivanov.decompose:decompose:1.0.0")
                implementation("com.arkivanov.decompose:extensions-compose-jetbrains:1.0.0")
                implementation("com.arkivanov.essenty:parcelable:1.0.0")
                implementation("com.arkivanov.essenty:lifecycle:1.0.0")

                implementation("com.arkivanov.mvikotlin:mvikotlin:3.1.0")
                implementation("com.arkivanov.mvikotlin:mvikotlin-extensions-coroutines:3.1.0")
                implementation("com.arkivanov.mvikotlin:rx:3.1.0") // Disposable

                // Desktop
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.6.4") // coroutines Main
                implementation("com.arkivanov.mvikotlin:mvikotlin-main:3.1.0") // DefaultStoreFactory
            }
        }
        val jvmTest by getting
    }
}

compose.desktop {
    application {
        mainClass = "app.opia.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.AppImage)
            // fixes runDistributable: ClassNotFoundException: java.sql.DriverManager
            // https://github.com/JetBrains/compose-jb/blob/master/tutorials/Native_distributions_and_local_execution/README.md#configuring-included-jdk-modules
            // ./gradlew suggestRuntimeModules
            modules("java.sql")
            packageName = "opia-compose"
            packageVersion = "1.0.0"
        }
    }
}
