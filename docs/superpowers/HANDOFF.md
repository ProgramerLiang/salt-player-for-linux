# Salt Player Linux Handoff

Date: 2026-07-07

## Current State

- Design doc committed: `878c790`, `66e27ab`.
- Implementation plan committed: `42e7854`.
- Ant colony failed after scouting only; no implementation was produced by it.
- Manual minimal scaffold started after failure.
- Git identity configured locally as `Corripo <corripo@pu.edu.kg>`.
- Upstream SaltPlayerSource license is MIT; project LICENSE retains `Copyright (c) 2021-2026 Moriafly`.

## Files Added In Current WIP

- `.gitignore` with `.ant-colony/`.
- Root frontend/Tauri metadata: `package.json`, `index.html`, `tsconfig.json`, `vite.config.ts`, Tailwind/PostCSS configs.
- Minimal React shell: `src/main.tsx`, `src/App.tsx`, `src/styles.css`, `src/test-setup.ts`.
- Minimal Tauri shell: `src-tauri/Cargo.toml`, `src-tauri/build.rs`, `src-tauri/tauri.conf.json`, `src-tauri/src/main.rs`, `src-tauri/src/lib.rs`.
- `LICENSE`, `README.md`.

## Environment Findings

- Node is available: `v26.4.0`.
- npm is available: `11.17.0`.
- Rust toolchain is missing: `rustc: command not found`, so `cargo check`, Tauri build, and release packaging cannot be verified locally yet.

## Next Steps

1. Install Rust toolchain and Ubuntu Tauri dependencies.
2. Run `npm install` and `npm run build`.
3. Run `cd src-tauri && cargo check` after Rust is available.
4. Continue implementation from `docs/superpowers/plans/2026-07-07-salt-player-linux.md`, starting with Rust models/config/scanner.
5. Do not push a release tag until local verification and GitHub push succeed.
