#!/usr/bin/env bash
# SPDX-License-Identifier: LGPL-3.0-or-later
# Copyright (C) 2026 Corripo
#
# Build a .deb from the jpackage app-image without dpkg-deb/fakeroot.
# Requires only: ar, tar, gzip (binutils is present on every dev machine).
set -euo pipefail

APP_IMAGE="${1:?usage: build-deb.sh <app-image-dir> <output.deb> <version>}"
OUTPUT="${2:?usage: build-deb.sh <app-image-dir> <output.deb> <version>}"
VERSION="${3:?usage: build-deb.sh <app-image-dir> <output.deb> <version>}"

[ -d "$APP_IMAGE" ] || { echo "missing app image: $APP_IMAGE" >&2; exit 1; }
for tool in ar tar gzip; do
  command -v "$tool" >/dev/null || { echo "missing tool: $tool" >&2; exit 1; }
done

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
ROOT="$WORK/pkg"
INSTALL="$ROOT"
CONTROL="$WORK/control"
mkdir -p "$ROOT/DEBIAN" "$INSTALL/usr/share/salt-player" "$INSTALL/usr/share/applications" \
         "$INSTALL/usr/share/icons/hicolor/512x512/apps" "$INSTALL/usr/bin"

cp -a "$APP_IMAGE/." "$INSTALL/usr/share/salt-player/"
cat > "$INSTALL/usr/bin/salt-player" <<'LAUNCH'
#!/usr/bin/env bash
exec /usr/share/salt-player/bin/salt-player "$@"
LAUNCH
chmod 755 "$INSTALL/usr/bin/salt-player"
cat > "$INSTALL/usr/share/applications/salt-player.desktop" <<'DESKTOP'
[Desktop Entry]
Type=Application
Version=1.0
Name=Salt Player
GenericName=Music Player
Comment=Salt Player (椒盐音乐) for Linux
Exec=/usr/bin/salt-player %F
Icon=salt-player
Terminal=false
Categories=AudioVideo;Audio;Player;
MimeType=audio/mpeg;audio/flac;audio/ogg;audio/opus;audio/x-wav;audio/mp4;
StartupNotify=true
DESKTOP
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
for size in 16 24 32 48 64 128 256 512; do
  install -Dm644 "$ROOT_DIR/icons/hicolor/${size}x${size}/apps/salt-player.png" \
    "$INSTALL/usr/share/icons/hicolor/${size}x${size}/apps/salt-player.png"
done
install -Dm644 "$ROOT_DIR/icons/salt-player.svg" \
  "$INSTALL/usr/share/icons/hicolor/scalable/apps/salt-player.svg"

INSTALLED_SIZE=$(du -sk "$INSTALL" | cut -f1)
cat > "$CONTROL" <<CONTROL
Package: salt-player
Version: $VERSION
Section: sound
Priority: optional
Architecture: amd64
Maintainer: Corripo <corripo@users.noreply.github.com>
Description: Salt Player (椒盐音乐) for Linux
 A desktop port of Salt Player with a tablet-style Compose/SaltUI interface,
 local library scanning, gapless playback, MPRIS and multi-backend audio.
Installed-Size: $INSTALLED_SIZE
Depends: libc6, libstdc++6
Recommends: gstreamer1.0-plugins-base, gstreamer1.0-plugins-good
CONTROL
cp "$CONTROL" "$ROOT/DEBIAN/control"
cat > "$ROOT/DEBIAN/postinst" <<'POSTINST'
#!/bin/sh
set -e
gtk-update-icon-cache -q -t -f /usr/share/icons/hicolor >/dev/null 2>&1 || true
update-desktop-database -q /usr/share/applications >/dev/null 2>&1 || true
exit 0
POSTINST
chmod 755 "$ROOT/DEBIAN/postinst"
printf '#!/bin/sh\nset -e\ngtk-update-icon-cache -q -t -f /usr/share/icons/hicolor >/dev/null 2>&1 || true\nexit 0\n' > "$ROOT/DEBIAN/postrm"
chmod 755 "$ROOT/DEBIAN/postrm"

# Debian archives: 2-byte line endings in control tar, root ownership, deterministic order.
tar --owner=0 --group=0 --numeric-owner --mtime='UTC 1970-01-01' \
    --format=gnu -cf "$WORK/data.tar" -C "$INSTALL" .
gzip -9 -n -c "$WORK/data.tar" > "$WORK/data.tar.gz"
printf '2.0\n' > "$WORK/debian-binary"
tar --owner=0 --group=0 --numeric-owner --mtime='UTC 1970-01-01' \
    --format=gnu -cf "$WORK/control.tar" -C "$WORK" control
gzip -9 -n -c "$WORK/control.tar" > "$WORK/control.tar.gz"

mkdir -p "$(dirname "$OUTPUT")"
rm -f "$OUTPUT"
ar rcD "$OUTPUT" "$WORK/debian-binary" "$WORK/control.tar.gz" "$WORK/data.tar.gz"
echo "wrote $OUTPUT ($(stat -c%s "$OUTPUT") bytes)"
