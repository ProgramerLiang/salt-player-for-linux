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
      <stop offset="0%" stop-color="#3E8FF0"/>
      <stop offset="55%" stop-color="#1F6FD8"/>
      <stop offset="100%" stop-color="#1550A8"/>
    </linearGradient>
    <linearGradient id="top" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0%" stop-color="#FFFFFF"/>
      <stop offset="100%" stop-color="#DCEBFF"/>
    </linearGradient>
    <linearGradient id="left" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0%" stop-color="#CFE2FB"/>
      <stop offset="100%" stop-color="#9FC4F2"/>
    </linearGradient>
    <linearGradient id="right" x1="1" y1="0" x2="0" y2="1">
      <stop offset="0%" stop-color="#B4D2F7"/>
      <stop offset="100%" stop-color="#82B0E9"/>
    </linearGradient>
  </defs>

  <rect x="0" y="0" width="512" height="512" rx="112" ry="112" fill="url(#tile)"/>

  <!-- salt crystal: isometric cube, 3 facets -->
  <g transform="translate(256 232)">
    <polygon points="0,-132 114,-66 0,0 -114,-66" fill="url(#top)"/>
    <polygon points="-114,-66 0,0 0,132 -114,66" fill="url(#left)"/>
    <polygon points="114,-66 0,0 0,132 114,66" fill="url(#right)"/>
  </g>

  <!-- falling grains -->
  <g fill="#FFFFFF">
    <circle cx="196" cy="392" r="17"/>
    <circle cx="256" cy="424" r="13"/>
    <circle cx="316" cy="392" r="17"/>
  </g>
</svg>
"""


def main() -> None:
    svg_path = ROOT / "icons" / "salt-player.svg"
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
             str(out_dir / "salt-player.png"), str(svg_path)],
            check=True,
        )
    print("generated", svg_path, "and", len(SIZES), "hicolor sizes")


if __name__ == "__main__":
    main()
