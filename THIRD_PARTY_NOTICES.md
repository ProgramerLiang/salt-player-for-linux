# Third-party notices

Salt Player for Linux is licensed **LGPL-3.0-or-later** (see [LICENSE](LICENSE)).

## This repository

| Component | License | Copyright | Notes |
|:--|:--|:--|:--|
| Salt Player for Linux (this repository, main code) | LGPL-3.0-or-later | Copyright (C) 2026 Corripo | Linux desktop port |
| Salt Player (椒盐音乐) original work | — | Copyright (C) 2021-2026 Moriafly | The Android app itself is **not** open source; only its public UI toolkit and public documentation/screenshots were used as a design reference. No proprietary code or assets were copied. |

## Code derived from Moriafly/SaltPlayerCommunity

`Moriafly/SaltPlayerCommunity` is licensed **LGPL-2.1-or-later**
(<https://github.com/Moriafly/SaltPlayerCommunity>).

No file from that repository is currently vendored into this project. The player API shape
(`SaltPlayer` / `CommandPlayer` / `Command` / `State` / `Provider` / `Config` / `Callback`) was
used **only as a design reference**; all code in this repository is an independent
implementation. If code from SaltPlayerCommunity is ever vendored later, the original file
header (`Copyright (C) 2025 Moriafly and Contributions`, LGPL-2.1-or-later notice) must be kept
intact and the file must be listed here as `source file → destination file`.

## Dependencies

| Dependency | License | Purpose |
|:--|:--|:--|
| [Moriafly/SaltUI](https://github.com/Moriafly/SaltUI) 3.0.0 | Apache-2.0 | UI toolkit (used as a Maven dependency, not vendored). Apache-2.0 is compatible with LGPL-3.0-or-later. |
| JetBrains Compose Multiplatform | Apache-2.0 | UI framework |
| Kotlin standard library | Apache-2.0 | Language runtime |
| gst1-java-core (org.freedesktop.gstreamer) | LGPL-2.1-or-later | GStreamer Java bindings (runtime: native GStreamer libs, GPL/LGPL per plugin) |
| jaudiotagger | LGPL-2.1-or-later (as distributed; MPL-1.1 alternative) | Tag reading |
| sqlite-jdbc (Xerial) | Apache-2.0 | Embedded library database |
| dbus-java (hypfvieh) | LGPL-3.0-or-later / BSD-2 | MPRIS D-Bus integration |
| JNA | LGPL-2.1-or-later / Apache-2.0 | Native calls for libmpv |
| kotlinx.coroutines / kotlinx.serialization | Apache-2.0 | Async + JSON |
| Material Icons (Compose) | Apache-2.0 | Icons |

## Runtime system dependencies (not bundled)

GStreamer, its plugins, libmpv and PulseAudio/PipeWire/ALSA are supplied by the operating
system and keep their own licenses. See `README.md` for the exact package names per
distribution.
