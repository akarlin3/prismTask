#!/usr/bin/env python3
"""Compose tablet-form-factor screenshots from the rendered phone PNGs.

Play Console accepts tablet screenshots with aspect between 9:16 and 16:9.
The phone screenshots are 9:16 portrait; tablet listings expect landscape.
This script letterboxes each 1080x1920 phone PNG inside a wider tablet
canvas filled with the screenshot's theme background color, producing:

  out/tablet-7in-NN.png  at 1920x1200 (8:5 landscape)
  out/tablet-10in-NN.png at 2560x1600 (8:5 landscape)

Run after `render.py` produces the phone PNGs.

Usage:
  python render-tablet.py
  python render-tablet.py --check
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont  # type: ignore[import-not-found]

HERE = Path(__file__).resolve().parent
OUT = HERE / "out"

# Per-screenshot theme background color so the side padding blends with
# the phone screen content. Pulled from theme-tokens.json `background`
# field, except #07 (4-theme composite) which uses the neutral wrapper bg.
SCREENSHOT_BG = {
    1: "#0A0A0F",  # cyberpunk
    2: "#0D0717",  # synthwave
    3: "#010D03",  # matrix
    4: "#111113",  # void
    5: "#0A0A0F",  # cyberpunk
    6: "#0D0717",  # synthwave
    7: "#0B0B14",  # composite neutral
    8: "#010D03",  # matrix
}

TABLET_SIZES = [
    ("7in", 1920, 1200),
    ("10in", 2560, 1600),
]


def hex_to_rgb(h: str) -> tuple[int, int, int]:
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16))


def compose_tablet(
    phone_png: Path,
    out_png: Path,
    canvas_w: int,
    canvas_h: int,
    bg_hex: str,
) -> None:
    bg = hex_to_rgb(bg_hex)
    canvas = Image.new("RGB", (canvas_w, canvas_h), bg)

    with Image.open(phone_png) as phone:
        phone = phone.convert("RGBA")
        # Scale phone to fit canvas height while preserving aspect.
        scale = canvas_h / phone.height
        new_w = int(phone.width * scale)
        new_h = canvas_h
        phone_resized = phone.resize((new_w, new_h), Image.Resampling.LANCZOS)

    # Center horizontally.
    x_offset = (canvas_w - new_w) // 2
    canvas.paste(phone_resized, (x_offset, 0), phone_resized)
    canvas.save(out_png, "PNG", optimize=True)


def render_all() -> int:
    if not OUT.exists():
        print(f"ERROR: {OUT} does not exist. Run render.py first.", file=sys.stderr)
        return 2

    failures = 0
    for i in range(1, 9):
        phone = OUT / f"screenshot-{i:02d}.png"
        if not phone.exists():
            print(f"  [MISSING] {phone.name}")
            failures += 1
            continue
        bg = SCREENSHOT_BG[i]
        for label, w, h in TABLET_SIZES:
            out = OUT / f"tablet-{label}-{i:02d}.png"
            try:
                compose_tablet(phone, out, w, h, bg)
                size = out.stat().st_size
                print(f"  [OK] {out.name} ({w}x{h}, {size / 1024:.1f} KiB)")
            except Exception as e:
                print(f"  [FAIL] {out.name}: {e}")
                failures += 1

    if failures:
        print(f"\n{failures} failure(s).")
        return 1
    print("\nAll tablet screenshots composed.")
    return 0


def check_outputs() -> int:
    failures = 0
    for i in range(1, 9):
        for label, exp_w, exp_h in TABLET_SIZES:
            out = OUT / f"tablet-{label}-{i:02d}.png"
            if not out.exists():
                print(f"  [MISSING] {out.name}")
                failures += 1
                continue
            with Image.open(out) as im:
                w, h = im.size
            size = out.stat().st_size
            ok = (w == exp_w and h == exp_h and size <= 8 * 1024 * 1024)
            tag = "OK" if ok else "FAIL"
            print(f"  [{tag}] {out.name}: {w}x{h} ({size / 1024:.1f} KiB)")
            if not ok:
                failures += 1
    if failures:
        print(f"\n{failures} check failure(s).")
        return 1
    print("\nAll tablet outputs pass dimension/size checks.")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true",
                        help="Validate tablet PNG dimensions; do not re-render.")
    args = parser.parse_args()
    return check_outputs() if args.check else render_all()


if __name__ == "__main__":
    sys.exit(main())
