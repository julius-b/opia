package app.opia.common.utils

enum class Platform {
    desktop, windows, macos, linux, android
}

expect fun getPlatformName(): Platform
