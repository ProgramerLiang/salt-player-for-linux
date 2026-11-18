import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.api.tasks.bundling.Tar

plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.compose")
}

group = "io.github.corripo"
version = "0.2.0"

val saltUiVersion = "3.0.0"
val coroutinesVersion = "1.11.0"

dependencies {
    // --- UI: Compose Multiplatform desktop + SaltUI (the official Salt Player toolkit) ---
    implementation(compose.desktop.currentOs)
    // Material icons are the only Material piece we use: every visual component comes from SaltUI.
    implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
    implementation("io.github.moriafly:salt-ui:$saltUiVersion")

    // --- Kotlin runtime helpers ---
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.13.0")

    // --- data layer ---
    implementation("org.xerial:sqlite-jdbc:3.54.1.0")
    implementation("net.jthink:jaudiotagger:3.0.1")

    // --- audio backends ---
    // 1) GStreamer (primary). Native GStreamer libs are supplied by the distribution.
    implementation("org.freedesktop.gstreamer:gst1-java-core:1.4.0")
    // 2) libmpv (secondary), driven in-process through JNA.
    implementation("net.java.dev.jna:jna:5.18.1")

    // --- desktop integration: MPRIS over D-Bus ---
    implementation("com.github.hypfvieh:dbus-java-core:5.2.2")
    implementation("com.github.hypfvieh:dbus-java-transport-native-unixsocket:5.2.2")

    implementation("org.slf4j:slf4j-api:2.0.17")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.17")
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        optIn.add("com.moriafly.salt.ui.UnstableSaltUiApi")
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

compose.desktop {
    application {
        mainClass = "com.saltplayer.MainKt"
        jvmArgs("-Dskiko.renderApi=SOFTWARE")

        nativeDistributions {
            targetFormats(
                TargetFormat.Deb,
                TargetFormat.Rpm,
                TargetFormat.AppImage,
            )
            // tar.gz is not part of Compose TargetFormat; see the packageTarGz task below.
            packageName = "salt-player"
            packageVersion = "0.2.0"
            description = "Salt Player for Linux"
            vendor = "Corripo"
            copyright = "© 2021-2026 Moriafly · Linux port © 2026 Corripo · MIT"
            includeAllModules = true

            linux {
                iconFile.set(project.file("../icons/icon.png"))
                appCategory = "AudioVideo"
                menuGroup = "AudioVideo"
                shortcut = true
                debMaintainer = "Corripo <corripo@users.noreply.github.com>"
                rpmLicenseType = "MIT"
                appRelease = "1"
                fileAssociation("mp3", "audio", "MP3 audio")
                fileAssociation("flac", "audio", "FLAC audio")
                fileAssociation("ogg", "audio", "Ogg Vorbis audio")
                fileAssociation("opus", "audio", "Opus audio")
                fileAssociation("m4a", "audio", "MPEG-4 audio")
                fileAssociation("wav", "audio", "WAV audio")
            }
        }
    }
}

/**
 * Compose's [TargetFormat] has no tar.gz target, so build one from the jpackage
 * app image produced by `createDistributable`.
 */
val packageTarGz by tasks.registering(Tar::class) {
    group = "compose desktop"
    description = "Packs the native distributable into a portable .tar.gz archive"
    dependsOn("createDistributable")
    archiveFileName.set("salt-player-${project.version}-linux-x64.tar.gz")
    destinationDirectory.set(layout.buildDirectory.dir("compose/binaries/main/tar.gz"))
    compression = Compression.GZIP
    archiveExtension.set("tar.gz")
    from(layout.buildDirectory.dir("compose/binaries/main/app")) {
        into("salt-player")
    }
}


/**
 * Fallback Debian package builder for hosts whose jpackage lacks the linux-deb packager
 * (e.g. Arch Linux OpenJDK). Uses only ar/tar/gzip and works without root privileges.
 */
val packageDebFallback by tasks.registering(Exec::class) {
    group = "compose desktop"
    description = "Builds a .deb from the app image without dpkg-deb or fakeroot"
    dependsOn("createDistributable")
    val imageDir = layout.buildDirectory.dir("compose/binaries/main/app/salt-player")
    val outputFile = layout.buildDirectory.file("compose/binaries/main/deb/salt-player_${project.version}_amd64.deb")
    inputs.dir(imageDir)
    outputs.file(outputFile)
    commandLine(
        rootProject.file("scripts/build-deb.sh").absolutePath,
        imageDir.get().asFile.absolutePath,
        outputFile.get().asFile.absolutePath,
        project.version.toString(),
    )
}
