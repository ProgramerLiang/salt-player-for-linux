#!/usr/bin/env python3
# SPDX-License-Identifier: LGPL-3.0-or-later
# Copyright (C) 2026 Corripo
"""Generate the application icon at every size a Linux desktop asks for.

The artwork is a single salt crystal (isometric cube) resting on a soft gradient tile, plus
three falling grains. It is deliberately built from three bold shapes so the silhouette still
reads at 16 px, where thin strokes and text would turn to mud.
"""
import pathlib
import subprocess

ROOT = pathlib.Path(__file__).resolve().parent.parent
SIZES = [16, 24, 32, 48, 64, 128, 256, 512]

SVG = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512">
  <defs>
    <linearGradient id="tile" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0%" stop-color="#4A97F2"/>
      <stop offset="55%" stop-color="#2271DA"/>
      <stop offset="100%" stop-color="#1550A8"/>
    </linearGradient>
    <linearGradient id="glass" x1="0" y1="0" x2="1" y2="0">
      <stop offset="0%" stop-color="#FFFFFF"/>
      <stop offset="55%" stop-color="#EEF6FF"/>
      <stop offset="100%" stop-color="#CBDEF8"/>
    </linearGradient>
    <linearGradient id="cap" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0%" stop-color="#FFD79A"/>
      <stop offset="100%" stop-color="#E8A03C"/>
    </linearGradient>
  </defs>

  <rect width="512" height="512" rx="112" fill="url(#tile)"/>

  <!-- shaker lid: dome with three salt holes -->
  <path d="M150 214c0-64 48-106 106-106s106 42 106 106z" fill="url(#cap)"/>
  <g fill="#8A5A12" opacity="0.75">
    <circle cx="226" cy="176" r="11"/>
    <circle cx="286" cy="176" r="11"/>
    <circle cx="256" cy="200" r="11"/>
  </g>

  <!-- glass body -->
  <path d="M146 210h220v128c0 30-24 54-54 54H200c-30 0-54-24-54-54z" fill="url(#glass)"/>
  <rect x="146" y="210" width="220" height="18" fill="#BBD6F6" opacity="0.9"/>

  <!-- salt inside, shown as a level line -->
  <path d="M158 306c34-16 74-24 98-24s64 8 98 24v32c0 24-20 44-44 44H202c-24 0-44-20-44-44z" fill="#FFFFFF" opacity="0.85"/>
  <g fill="#FFFFFF">
    <circle cx="196" cy="286" r="7" opacity="0.9"/>
    <circle cx="256" cy="278" r="6" opacity="0.8"/>
    <circle cx="316" cy="286" r="7" opacity="0.9"/>
  </g>

  <!-- falling grains below the shaker -->
  <g fill="#FFFFFF" opacity="0.95">
    <circle cx="238" cy="446" r="12"/>
    <circle cx="278" cy="470" r="9"/>
  </g>
</svg>
"""



def main() -> None:
    svg_path = ROOT / "icons" / "salt-shaker.svg"
    svg_path.parent.mkdir(parents=True, exist_ok=True)
    svg_path.write_text(SVG, encoding="utf-8")

    # Compose Desktop wants a single high resolution PNG for the launcher.
    subprocess.run(
        ["rsvg-convert", "-w", "512", "-h", "512", "-o", str(ROOT / "icons" / "icon.png"), str(svg_path)],
        check=True,
    )

    for size in SIZES:
        out_dir = ROOT / "icons" / "hicolor" / f"{size}x{size}" / "apps"
        out_dir.mkdir(parents=True, exist_ok=True)
        subprocess.run(
            ["rsvg-convert", "-w", str(size), "-h", str(size), "-o",
             str(out_dir / "salt-shaker.png"), str(svg_path)],
            check=True,
        )
    print("generated", svg_path, "and", len(SIZES), "hicolor sizes")


if __name__ == "__main__":
    main()
