package app.opia.common.utils

actual fun getPlatformName(): Platform {
    val os = System.getProperty("os.name").lowercase()
    return when {
        os.contains("win") -> Platform.windows
        os.contains("nix") || os.contains("nux") || os.contains("aix") -> Platform.linux
        os.contains("mac") -> Platform.macos
        else -> Platform.desktop
    }
}
