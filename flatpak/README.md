# Flatpak build

The manifest uses the Freedesktop 24.08 platform and its OpenJDK 21 SDK extension. The
GStreamer runtime already contains `gstreamer1-plugins-base/good/bad/ugly` equivalents; the
application detects missing elements at runtime and falls back to libmpv or Java Sound.

```bash
# 1. Build the portable distribution outside the sandbox.
cd .. && ./gradlew :app:packageTarGz

# 2. Unpack it where flatpak-builder expects it.
mkdir -p flatpak/salt-player
tar -xzf app/build/compose/binaries/main/tar.gz/salt-player-*.tar.gz \
    --strip-components=1 -C flatpak/salt-player

# 3. Build and install.
flatpak-builder --user --install --force-clean build-flatpak io.github.Corripo.SaltPlayer.yml

# 4. Run.
flatpak run io.github.Corripo.SaltPlayer
```

Permissions are deliberately minimal: read-only access to `~/Music`, read/write access only to
the app's own XDG directories, PulseAudio, Wayland/X11 fallback, the session bus (MPRIS) and the
desktop portal. The media-key setting is exposed through MPRIS; a separate global hotkey is not
installed inside the sandbox.
