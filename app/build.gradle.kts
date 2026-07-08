import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.compose")
}

group = "com.saltplayer.linux"
version = "0.1.0-alpha"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
    maven("https://jitpack.io")
}

dependencies {
    // Compose Desktop
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)

    // SaltUI — the original Salt Player UI library
    implementation("io.github.moriafly:salt-ui:2.9.0-beta02")

    // Audio metadata
    implementation("net.jthink:jaudiotagger:3.0.1")

    // MP3 / OGG playback via Java Sound SPI
    implementation("com.googlecode.soundlibs:mp3spi:1.9.5.4")
    implementation("com.googlecode.soundlibs:vorbisspi:1.0.3.3")

    // JSON serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")

    // Image loading (coil3 for Compose Multiplatform)
    implementation("io.coil-kt.coil3:coil-compose:3.1.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.1.0")
}

compose.desktop {
    application {
        mainClass = "com.saltplayer.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.AppImage)
            packageName = "salt-player"
            packageVersion = "0.1.0-alpha"
            description = "Salt Player for Linux"
            vendor = "Corripo"

            linux {
                iconFile.set(project.file("../icons/icon.png"))
            }
        }
    }
}

kotlin {
    jvmToolchain(17)
}
