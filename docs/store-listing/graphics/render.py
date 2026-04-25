#!/usr/bin/env python3
"""PrismTask Play Store graphics renderer.

Rasterizes every SVG under graphics/src/ to a PNG under graphics/out/ at
Play-Store-correct dimensions.

Default backend: cairosvg (highest fidelity).
Fallback backend: svglib + reportlab + Pillow (acceptable; loses
advanced SVG features but hits the target dimensions cleanly).

Idempotent. Running twice produces the same bytes.

Usage:
  python render.py            # render everything
  python render.py --check    # do not render; only validate output
                              # dimensions against Play Store requirements
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

# ---------------------------------------------------------------------------
# Render specs: each SVG -> expected output dimensions.
# ---------------------------------------------------------------------------

HERE = Path(__file__).resolve().parent
SRC = HERE / "src"
OUT = HERE / "out"

SCREENSHOT_DIR = SRC / "screenshots"

# (svg_path_relative_to_src, output_filename, width, height)
RENDER_SPECS = [
    ("icon.svg", "icon-512.png", 512, 512),
    ("feature-graphic.svg", "feature-graphic-1024x500.png", 1024, 500),
    ("screenshots/01-today-cyberpunk.svg", "screenshot-01.png", 1080, 1920),
    ("screenshots/02-eisenhower-synthwave.svg", "screenshot-02.png", 1080, 1920),
    ("screenshots/03-habits-matrix.svg", "screenshot-03.png", 1080, 1920),
    ("screenshots/04-pomodoro-void.svg", "screenshot-04.png", 1080, 1920),
    ("screenshots/05-ai-weekly-review-cyberpunk.svg", "screenshot-05.png", 1080, 1920),
    ("screenshots/06-ai-time-block-synthwave.svg", "screenshot-06.png", 1080, 1920),
    ("screenshots/07-theme-picker-all.svg", "screenshot-07.png", 1080, 1920),
    ("screenshots/08-onboarding-matrix.svg", "screenshot-08.png", 1080, 1920),
]

# Play Console limits (2026-04-24):
#   - Icon: 512x512 PNG, <= 1 MB
#   - Feature graphic: 1024x500 JPG/PNG, <= 15 MB
#   - Phone screenshots: each side 320..3840 px, 16:9 or 9:16, <= 8 MB each
PLAY_STORE_LIMITS = {
    "icon-512.png": {"width": 512, "height": 512, "max_bytes": 1 * 1024 * 1024},
    "feature-graphic-1024x500.png": {"width": 1024, "height": 500, "max_bytes": 15 * 1024 * 1024},
}
for i in range(1, 9):
    PLAY_STORE_LIMITS[f"screenshot-{i:02d}.png"] = {
        "width": 1080,
        "height": 1920,
        "max_bytes": 8 * 1024 * 1024,
    }


# ---------------------------------------------------------------------------
# Backend detection.
# ---------------------------------------------------------------------------

def _try_cairosvg() -> object | None:
    """cairosvg is the highest-fidelity backend but needs libcairo at runtime.
    On Windows that means installing GTK+ runtime; otherwise import fails."""
    try:
        import cairosvg  # type: ignore[import-not-found]
    except Exception:
        return None
    return cairosvg


def _try_resvg() -> object | None:
    """resvg-py is a pure Rust binding with no system libcairo dependency —
    the preferred Windows default when cairosvg cannot load."""
    try:
        import resvg_py  # type: ignore[import-not-found]
    except Exception:
        return None
    return resvg_py


def _try_pillow_fallback() -> tuple[object, object] | None:
    """svglib + reportlab fallback. Note: reportlab 4.x pulls in rlPyCairo,
    which also needs libcairo — this backend is effectively the same
    dependency as cairosvg on Windows. Kept as a last resort."""
    try:
        from reportlab.graphics import renderPM  # type: ignore[import-not-found]
        from svglib.svglib import svg2rlg  # type: ignore[import-not-found]
    except Exception:
        return None
    return (svg2rlg, renderPM)


def render_cairosvg(cairosvg_mod, svg_path: Path, png_path: Path, width: int, height: int) -> None:
    cairosvg_mod.svg2png(
        url=str(svg_path),
        write_to=str(png_path),
        output_width=width,
        output_height=height,
    )


def render_resvg(resvg_mod, svg_path: Path, png_path: Path, width: int, height: int) -> None:
    png_bytes = resvg_mod.svg_to_bytes(
        svg_path=str(svg_path),
        width=width,
        height=height,
    )
    png_path.write_bytes(png_bytes)


def render_pillow(svg2rlg, renderPM, svg_path: Path, png_path: Path, width: int, height: int) -> None:
    drawing = svg2rlg(str(svg_path))
    scale_x = width / drawing.width
    scale_y = height / drawing.height
    scale = min(scale_x, scale_y)
    drawing.width *= scale
    drawing.height *= scale
    drawing.scale(scale, scale)
    renderPM.drawToFile(drawing, str(png_path), fmt="PNG", dpi=72)


# ---------------------------------------------------------------------------
# Check mode.
# ---------------------------------------------------------------------------

def check_outputs() -> int:
    """Validate every expected PNG's dimensions against Play Store limits."""
    try:
        from PIL import Image  # type: ignore[import-not-found]
    except Exception:
        print("ERROR: --check requires Pillow. pip install Pillow", file=sys.stderr)
        return 2

    failures = 0
    for _, png_name, expected_w, expected_h in RENDER_SPECS:
        png_path = OUT / png_name
        if not png_path.exists():
            print(f"  [MISSING] {png_name} — run render.py to create it")
            failures += 1
            continue
        try:
            with Image.open(png_path) as im:
                w, h = im.size
        except Exception as e:
            print(f"  [UNREADABLE] {png_name}: {e}")
            failures += 1
            continue
        size = png_path.stat().st_size
        limit = PLAY_STORE_LIMITS[png_name]
        dims_ok = (w == expected_w and h == expected_h)
        bytes_ok = (size <= limit["max_bytes"])
        tag = "OK" if (dims_ok and bytes_ok) else "FAIL"
        dim_str = f"{w}x{h}"
        bytes_str = f"{size / 1024:.1f} KiB"
        print(f"  [{tag}] {png_name}: {dim_str} ({bytes_str})")
        if not dims_ok:
            print(f"         expected {expected_w}x{expected_h}")
            failures += 1
        if not bytes_ok:
            print(f"         size exceeds Play Store limit of {limit['max_bytes'] / 1024 / 1024:.1f} MB")
            failures += 1
    if failures:
        print(f"\n{failures} check failure(s).")
        return 1
    print("\nAll outputs pass Play Store dimension/size checks.")
    return 0


# ---------------------------------------------------------------------------
# Render mode.
# ---------------------------------------------------------------------------

def render_all() -> int:
    OUT.mkdir(parents=True, exist_ok=True)

    cairosvg_mod = _try_cairosvg()
    resvg_mod = None
    pillow_deps = None
    if cairosvg_mod is None:
        resvg_mod = _try_resvg()
    if cairosvg_mod is None and resvg_mod is None:
        pillow_deps = _try_pillow_fallback()

    if cairosvg_mod is None and resvg_mod is None and pillow_deps is None:
        print(
            "ERROR: no SVG renderer available.\n"
            "  Install one of (in order of preference):\n"
            "    pip install resvg-py          # no system deps, works on Windows\n"
            "    pip install cairosvg          # needs libcairo runtime\n"
            "    pip install svglib reportlab  # also needs libcairo\n",
            file=sys.stderr,
        )
        return 2

    if cairosvg_mod is not None:
        backend = "cairosvg"
    elif resvg_mod is not None:
        backend = "resvg-py"
    else:
        backend = "svglib+reportlab"
    print(f"Rendering with backend: {backend}\n")

    failures = 0
    for svg_rel, png_name, width, height in RENDER_SPECS:
        svg_path = SRC / svg_rel
        png_path = OUT / png_name
        if not svg_path.exists():
            print(f"  [MISSING-SVG] {svg_rel}")
            failures += 1
            continue
        try:
            if cairosvg_mod is not None:
                render_cairosvg(cairosvg_mod, svg_path, png_path, width, height)
            elif resvg_mod is not None:
                render_resvg(resvg_mod, svg_path, png_path, width, height)
            else:
                svg2rlg, renderPM = pillow_deps  # type: ignore[misc]
                render_pillow(svg2rlg, renderPM, svg_path, png_path, width, height)
            size = png_path.stat().st_size
            print(f"  [OK] {png_name} ({width}x{height}, {size / 1024:.1f} KiB)")
        except Exception as e:
            print(f"  [FAIL] {svg_rel} -> {png_name}: {e}")
            failures += 1

    if failures:
        print(f"\n{failures} render failure(s).")
        return 1
    print("\nAll graphics rendered.")
    return 0


# ---------------------------------------------------------------------------
# Entry point.
# ---------------------------------------------------------------------------

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--check",
        action="store_true",
        help="Validate output PNG dimensions and sizes against Play Store requirements. "
        "Does not re-render.",
    )
    args = parser.parse_args()
    if args.check:
        return check_outputs()
    return render_all()


if __name__ == "__main__":
    sys.exit(main())
