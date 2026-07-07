# Salt Player for Linux Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a first Linux desktop GUI version of Salt Player for Ubuntu 24.04 with local music scanning, playback, queue control, MPRIS integration, and release packages.

**Architecture:** Tauri v2 hosts a React + TypeScript UI and exposes Rust IPC commands for library scanning, config, queue, and playback. Rust owns file system access, metadata extraction, audio output, config/cache persistence, and Linux desktop integration. Frontend renders library, queue, now playing, and settings views, calling backend commands through Tauri.

**Tech Stack:** Tauri v2, Rust, React, TypeScript, Tailwind CSS, Vite, rodio, lofty, walkdir, mpris-server, GitHub Actions.

## Global Constraints

- Primary target: Ubuntu 24.04.
- First release version: `v0.1.0-alpha`.
- License: MIT; retain `Copyright (c) 2021-2026 Moriafly`.
- Config path: `~/.config/salt-player/config.json`.
- Library cache path: `~/.config/salt-player/cache/songs.json`.
- Supported first-version audio extensions: `flac`, `mp3`, `aac`, `ogg`, `wav`, `m4a`, `opus`.
- Packaging targets: `.deb` and AppImage.
- Release trigger: GitHub tag matching `v*`.
- No network dependency for playback or library management.
- Use frequent commits at the end of each task.

---

## File Structure

```text
salt-player-for-linux/
├── LICENSE
├── README.md
├── package.json
├── index.html
├── tsconfig.json
├── vite.config.ts
├── tailwind.config.js
├── postcss.config.js
├── src/
│   ├── main.tsx
│   ├── App.tsx
│   ├── styles.css
│   ├── types.ts
│   ├── hooks/
│   │   ├── useLibrary.ts
│   │   └── usePlayer.ts
│   ├── components/
│   │   ├── Layout.tsx
│   │   ├── Sidebar.tsx
│   │   ├── SongList.tsx
│   │   ├── PlayerBar.tsx
│   │   ├── CoverArt.tsx
│   │   └── ProgressBar.tsx
│   └── pages/
│       ├── Library.tsx
│       ├── Playlist.tsx
│       ├── NowPlaying.tsx
│       └── Settings.tsx
├── src-tauri/
│   ├── Cargo.toml
│   ├── build.rs
│   ├── tauri.conf.json
│   └── src/
│       ├── main.rs
│       ├── lib.rs
│       ├── models.rs
│       ├── config.rs
│       ├── scanner.rs
│       ├── queue.rs
│       ├── audio.rs
│       ├── commands.rs
│       └── mpris.rs
└── .github/workflows/
    ├── build.yml
    └── release.yml
```

---

## Task 1: Bootstrap Tauri Project

**Files:**
- Create: `LICENSE`, `README.md`, `package.json`, `index.html`, `tsconfig.json`, `vite.config.ts`, `tailwind.config.js`, `postcss.config.js`
- Create: `src-tauri/Cargo.toml`, `src-tauri/build.rs`, `src-tauri/tauri.conf.json`, `src-tauri/src/main.rs`, `src-tauri/src/lib.rs`

**Interfaces:**
- Produces a buildable empty Tauri v2 application named `Salt Player`.

- [ ] Create MIT `LICENSE` containing the upstream copyright notice.
- [ ] Create README with project purpose, Ubuntu 24.04 target, build instructions, and MIT license note.
- [ ] Create Vite + React + TypeScript frontend configuration.
- [ ] Create Tauri v2 Rust crate configuration with package name `salt-player`, version `0.1.0-alpha`, and author `Corripo <corripo@pu.edu.kg>`.
- [ ] Add minimal `main.rs` and `lib.rs` that start a Tauri app.
- [ ] Run:

```bash
npm install
npm run build
cd src-tauri && cargo check
```

Expected: frontend build succeeds and `cargo check` succeeds.

- [ ] Commit:

```bash
git add .
git commit -m "chore: 初始化 Tauri Linux 桌面应用"
```

---

## Task 2: Core Rust Models and Config

**Files:**
- Create: `src-tauri/src/models.rs`
- Create: `src-tauri/src/config.rs`
- Modify: `src-tauri/src/lib.rs`

**Interfaces:**
- Produces `Song`, `PlaybackState`, `AppConfig`, `PlayMode`.
- Produces `config_dir()`, `load_config()`, `save_config()`.

**Required Rust types:**

```rust
#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct Song {
    pub id: String,
    pub title: String,
    pub artist: String,
    pub album: String,
    pub duration: f64,
    pub file_path: String,
    pub cover_art: Option<Vec<u8>>,
    pub format: String,
}

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct PlaybackState {
    pub song_id: Option<String>,
    pub title: String,
    pub artist: String,
    pub album: String,
    pub cover_art: Option<Vec<u8>>,
    pub duration: f64,
    pub position: f64,
    pub is_playing: bool,
}

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize, PartialEq)]
pub enum PlayMode {
    Sequential,
    Shuffle,
    Repeat,
}

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct AppConfig {
    pub music_dirs: Vec<String>,
    pub theme: String,
    pub language: String,
}
```

- [ ] Implement `Default for AppConfig` with `~/Music` from `dirs::audio_dir()` when available.
- [ ] Implement config load/save with JSON under `~/.config/salt-player/config.json`.
- [ ] Add tests for default config and JSON round trip using `XDG_CONFIG_HOME` set to a temp directory.
- [ ] Run:

```bash
cd src-tauri && cargo test config models
```

Expected: tests pass.

- [ ] Commit:

```bash
git add src-tauri/src/models.rs src-tauri/src/config.rs src-tauri/src/lib.rs
git commit -m "feat: 添加核心模型和配置持久化"
```

---

## Task 3: Media Library Scanner

**Files:**
- Create: `src-tauri/src/scanner.rs`
- Modify: `src-tauri/src/lib.rs`

**Interfaces:**
- Consumes `Song` and `config_dir()`.
- Produces `scan_directory(path: &Path) -> Vec<Song>`.
- Produces `load_songs_cache() -> Vec<Song>` and `save_songs_cache(songs: &[Song]) -> anyhow::Result<()>`.

**Implementation requirements:**
- Use `walkdir` to recursively visit files.
- Accept only `flac`, `mp3`, `aac`, `ogg`, `wav`, `m4a`, `opus`.
- Use `lofty` to read title, artist, album, duration, and first embedded picture.
- Use file stem as title fallback.
- Use `Unknown Artist` and `Unknown Album` fallbacks.
- Skip unreadable or unsupported files without failing the full scan.
- Store cache as JSON at `~/.config/salt-player/cache/songs.json`.

- [ ] Add tests for empty directory scan, extension filtering, and deterministic song id hashing.
- [ ] Run:

```bash
cd src-tauri && cargo test scanner
```

Expected: scanner tests pass.

- [ ] Commit:

```bash
git add src-tauri/src/scanner.rs src-tauri/src/lib.rs
git commit -m "feat: 实现本地音乐扫描器"
```

---

## Task 4: Queue Manager

**Files:**
- Create: `src-tauri/src/queue.rs`
- Modify: `src-tauri/src/lib.rs`

**Interfaces:**
- Consumes `Song` and `PlayMode`.
- Produces `PlayQueue::new()`, `set_queue(songs, index)`, `current()`, `next()`, `previous()`, `set_mode(mode)`, `queue_info()`.

**Implementation requirements:**
- Sequential mode wraps at the end.
- Repeat mode keeps the same song on next/previous.
- Shuffle mode preserves the current song when toggled.
- Queue info must serialize to frontend with songs, index, and mode.

- [ ] Add tests for sequential next, previous wrapping, repeat behavior, and shuffle preserving current song.
- [ ] Run:

```bash
cd src-tauri && cargo test queue
```

Expected: queue tests pass.

- [ ] Commit:

```bash
git add src-tauri/src/queue.rs src-tauri/src/lib.rs
git commit -m "feat: 添加播放队列管理器"
```

---

## Task 5: Audio Engine and Playback State

**Files:**
- Create: `src-tauri/src/audio.rs`
- Modify: `src-tauri/src/lib.rs`

**Interfaces:**
- Produces `AudioEngine::new() -> anyhow::Result<AudioEngine>`.
- Produces `load(path)`, `play()`, `pause()`, `stop()`, `is_playing()`, `position()`, `set_volume(volume)`.

**Implementation requirements:**
- Use `rodio` for output.
- Return explicit errors for missing file, decoder failure, and audio device initialization failure.
- Track elapsed position with `Instant` for v0.1.0-alpha.
- Keep seek UI in frontend disabled until precise seeking is implemented.

- [ ] Add tests for state transitions that do not require actual audio output by extracting a pure `PlaybackClock` helper.
- [ ] Run:

```bash
cd src-tauri && cargo test audio
cd src-tauri && cargo check
```

Expected: tests pass and crate compiles on Ubuntu 24.04.

- [ ] Commit:

```bash
git add src-tauri/src/audio.rs src-tauri/src/lib.rs
git commit -m "feat: 添加基础音频播放引擎"
```

---

## Task 6: Tauri IPC Commands

**Files:**
- Create: `src-tauri/src/commands.rs`
- Modify: `src-tauri/src/lib.rs`

**Interfaces:**
- Produces commands: `scan_library`, `get_songs`, `play_song`, `pause`, `resume`, `next_song`, `previous_song`, `get_playback_state`, `set_play_mode`, `get_config`, `save_config`.

**Implementation requirements:**
- `scan_library(path)` validates the directory, scans, saves cache, and returns songs.
- `play_song(song_id)` loads the song from current library, resets queue if needed, starts playback, and returns `PlaybackState`.
- Playback commands return user-readable `String` errors over IPC.
- Shared app state uses `Mutex` around library, queue, config, and audio engine.
- Audio init failure must not panic; it should set audio engine to `None` and report an error when playback is attempted.

- [ ] Add Rust tests for command helper functions where possible.
- [ ] Run:

```bash
cd src-tauri && cargo test
cd src-tauri && cargo check
```

Expected: all backend tests pass.

- [ ] Commit:

```bash
git add src-tauri/src/commands.rs src-tauri/src/lib.rs
git commit -m "feat: 暴露 Tauri IPC 播放与扫描命令"
```

---

## Task 7: Frontend Shell and Types

**Files:**
- Create: `src/main.tsx`, `src/App.tsx`, `src/styles.css`, `src/types.ts`
- Create: `src/components/Layout.tsx`, `src/components/Sidebar.tsx`, `src/components/PlayerBar.tsx`
- Create: `src/pages/Library.tsx`, `src/pages/Playlist.tsx`, `src/pages/NowPlaying.tsx`, `src/pages/Settings.tsx`

**Interfaces:**
- Produces four app routes: `/library`, `/playlist`, `/nowplaying`, `/settings`.
- Produces TypeScript `Song`, `PlaybackState`, `AppConfig`, `PlayMode` matching Rust serialization.

**Implementation requirements:**
- Use a restrained desktop-player layout, not a marketing page.
- Sidebar navigation is fixed width.
- Bottom player bar remains visible on all pages.
- Text must fit at `800x600` minimum window.
- Use real controls and disabled states, not explanatory placeholder copy once connected.

- [ ] Build the layout and placeholder pages.
- [ ] Run:

```bash
npm run build
```

Expected: TypeScript and Vite build pass.

- [ ] Commit:

```bash
git add src package.json package-lock.json index.html tsconfig.json vite.config.ts tailwind.config.js postcss.config.js
git commit -m "feat: 添加 React 桌面播放器界面骨架"
```

---

## Task 8: Frontend Hooks and UI Wiring

**Files:**
- Create: `src/hooks/useLibrary.ts`, `src/hooks/usePlayer.ts`
- Create: `src/components/SongList.tsx`, `src/components/CoverArt.tsx`, `src/components/ProgressBar.tsx`
- Modify: `src/components/PlayerBar.tsx`
- Modify: `src/pages/Library.tsx`, `src/pages/Playlist.tsx`, `src/pages/NowPlaying.tsx`, `src/pages/Settings.tsx`

**Interfaces:**
- `useLibrary()` returns `{ songs, loading, error, refresh, scanDirectory }`.
- `usePlayer()` returns `{ state, playSong, pause, resume, next, previous, setMode }`.

**Implementation requirements:**
- Use `@tauri-apps/api/core` `invoke` for all backend calls.
- Library page shows scanned songs and double-click/tap play.
- Settings page lets user input a music directory path and scan it.
- Player bar shows current title, artist, play/pause, previous, next, and progress.
- Now Playing page shows larger cover art, metadata, and controls.
- Playlist page shows current queue once queue IPC is available; otherwise show current library as temporary queue for v0.1.0-alpha.
- Show toast or inline error when scan/playback fails.

- [ ] Add component tests for `ProgressBar` time formatting and empty `SongList` state.
- [ ] Run:

```bash
npm test
npm run build
```

Expected: frontend tests and build pass.

- [ ] Commit:

```bash
git add src
git commit -m "feat: 连接前端音乐库与播放控制"
```

---

## Task 9: MPRIS Integration

**Files:**
- Create: `src-tauri/src/mpris.rs`
- Modify: `src-tauri/src/commands.rs`, `src-tauri/src/lib.rs`, `src-tauri/Cargo.toml`

**Interfaces:**
- Produces `MprisService::new()`, `update_metadata(state)`, `update_playback_status(is_playing)`.

**Implementation requirements:**
- Use `mpris-server` or the smallest compatible Rust MPRIS crate available for Ubuntu 24.04 build.
- Expose player name as `Salt Player`.
- Update title, artist, album, duration, and playback state on play/pause/resume/next/previous.
- If MPRIS DBus registration fails, log the error and keep the app usable.

- [ ] Run:

```bash
cd src-tauri && cargo check
```

Expected: MPRIS integration compiles; app still runs without DBus in CI.

- [ ] Commit:

```bash
git add src-tauri/src/mpris.rs src-tauri/src/commands.rs src-tauri/src/lib.rs src-tauri/Cargo.toml
git commit -m "feat: 添加 Linux MPRIS 媒体控制集成"
```

---

## Task 10: CI and Release Packaging

**Files:**
- Create: `.github/workflows/build.yml`
- Create: `.github/workflows/release.yml`
- Modify: `src-tauri/tauri.conf.json`
- Modify: `README.md`

**Interfaces:**
- Produces CI for every push/PR.
- Produces GitHub release artifacts on `v*` tag.

**Implementation requirements:**
- Use `ubuntu-24.04` runner.
- Install Tauri Linux dependencies: `libwebkit2gtk-4.1-dev`, `libayatana-appindicator3-dev`, `librsvg2-dev`, `patchelf`, `libasound2-dev`.
- Build frontend with `npm run build`.
- Run backend tests with `cargo test`.
- Use official Tauri GitHub action to create `.deb` and AppImage artifacts where possible.
- Release workflow uploads artifacts to GitHub Releases.

- [ ] Run locally:

```bash
npm run build
cd src-tauri && cargo test && cargo check
```

Expected: local checks pass.

- [ ] Commit:

```bash
git add .github/workflows/build.yml .github/workflows/release.yml src-tauri/tauri.conf.json README.md
git commit -m "ci: 添加 Ubuntu 24.04 构建与发布流水线"
```

---

## Task 11: Final Verification and First Release

**Files:**
- Modify: `README.md`
- Modify: any files needed to fix integration issues from verification.

**Interfaces:**
- Produces working first release `v0.1.0-alpha`.

**Verification checklist:**
- [ ] `npm run build` passes.
- [ ] `cd src-tauri && cargo test` passes.
- [ ] `cd src-tauri && cargo clippy -- -D warnings` passes or documented warnings are fixed.
- [ ] `npm run tauri build` produces `.deb` and AppImage on Ubuntu 24.04.
- [ ] App launches on Ubuntu 24.04.
- [ ] User can scan a local folder.
- [ ] User can play, pause, previous, next.
- [ ] Library cache persists after restart.
- [ ] Config persists after restart.
- [ ] README includes build, run, and release instructions.
- [ ] LICENSE is present in release artifact.

**Release steps:**

```bash
git status --short
git push origin main
git tag v0.1.0-alpha
git push origin v0.1.0-alpha
```

Expected: GitHub Actions creates a Release with `.deb` and AppImage assets.

- [ ] Commit final fixes:

```bash
git add .
git commit -m "chore: 准备 v0.1.0-alpha 首个 Linux 适配版本"
```

---

## Self-Review

- Spec coverage: covers Ubuntu 24.04 target, MIT license, Rust backend, React frontend, config/cache paths, scanning, playback, queue, MPRIS, tests, packaging, release.
- Placeholder scan: no unresolved placeholder markers remain.
- Type consistency: Rust and TypeScript models use the same field names for Tauri serialization.
- Scope check: first release is intentionally basic; precise seeking and advanced library browsing can follow after `v0.1.0-alpha`.
