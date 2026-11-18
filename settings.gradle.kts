pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    /**
     * Repositories:
     * 1. Maven Central — Compose Multiplatform, Kotlin, SaltUI, GStreamer bindings, SQLite, D-Bus.
     * 2. A Google Maven mirror — Compose Desktop pulls a few `androidx.*` artifacts
     *    (e.g. `androidx.savedstate`). `dl.google.com` is unreachable on some networks, so a
     *    mirror is tried before the upstream repository; the upstream stays as the last resort.
     */
    repositories {
        mavenCentral()
        maven("https://maven.aliyun.com/repository/google")
        google()
    }
}

rootProject.name = "salt-player"
include(":app")
