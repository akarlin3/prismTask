#!/usr/bin/env python3
"""Replace in-app launcher icon PNGs with the prism brand mark.

Reads the icon SVG sources at:
  docs/store-listing/graphics/src/icon.svg        (rounded-square, used for
                                                   ic_launcher.png)
  docs/store-listing/graphics/src/icon-round.svg  (circular clip, used for
                                                   ic_launcher_round.png)

Writes the rendered PNGs to:
  app/src/main/res/mipmap-mdpi/ic_launcher{,_round}.png       (48x48)
  app/src/main/res/mipmap-hdpi/ic_launcher{,_round}.png       (72x72)
  app/src/main/res/mipmap-xhdpi/ic_launcher{,_round}.png      (96x96)
  app/src/main/res/mipmap-xxhdpi/ic_launcher{,_round}.png     (144x144)
  app/src/main/res/mipmap-xxxhdpi/ic_launcher{,_round}.png    (192x192)

This replaces the legacy purple-infinity placeholder with the prism +
four-theme-colored rays brand mark that matches the in-app adaptive icon
and the new Play Store icon.

Run from the repo root (the script resolves paths relative to itself, so
cwd doesn't matter):

    python scripts/replace-launcher-icons.py

Idempotent — running twice produces byte-stable output.
"""

from __future__ import annotations

import sys
from pathlib import Path

try:
    import resvg_py  # type: ignore[import-not-found]
except ImportError:
    print(
        "ERROR: resvg-py is not installed.\n"
        "  pip install resvg-py\n",
        file=sys.stderr,
    )
    sys.exit(2)


HERE = Path(__file__).resolve().parent
REPO_ROOT = HERE.parent

DENSITIES = [
    ("mdpi", 48),
    ("hdpi", 72),
    ("xhdpi", 96),
    ("xxhdpi", 144),
    ("xxxhdpi", 192),
]

SOURCES = [
    ("ic_launcher",       REPO_ROOT / "docs/store-listing/graphics/src/icon.svg"),
    ("ic_launcher_round", REPO_ROOT / "docs/store-listing/graphics/src/icon-round.svg"),
]


def main() -> int:
    failures = 0
    written = 0
    for variant, src_path in SOURCES:
        if not src_path.exists():
            print(f"  [MISSING-SVG] {src_path.relative_to(REPO_ROOT)}", file=sys.stderr)
            failures += 1
            continue
        for density, px in DENSITIES:
            out = REPO_ROOT / "app/src/main/res" / f"mipmap-{density}" / f"{variant}.png"
            if not out.parent.exists():
                print(f"  [MISSING-DIR] {out.parent.relative_to(REPO_ROOT)}", file=sys.stderr)
                failures += 1
                continue
            try:
                png_bytes = resvg_py.svg_to_bytes(
                    svg_path=str(src_path),
                    width=px,
                    height=px,
                )
                out.write_bytes(png_bytes)
                size = out.stat().st_size
                print(f"  [OK] {out.relative_to(REPO_ROOT)}  ({px}x{px}, {size / 1024:.1f} KiB)")
                written += 1
            except Exception as e:
                print(f"  [FAIL] {out.relative_to(REPO_ROOT)}: {e}", file=sys.stderr)
                failures += 1

    if failures:
        print(f"\n{failures} failure(s); {written} succeeded.", file=sys.stderr)
        return 1
    print(f"\nWrote {written} launcher PNGs.")
    print("Verify by running the app — the home-screen launcher should now show")
    print("the prism + 4-theme-colored-rays icon instead of the purple infinity.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
