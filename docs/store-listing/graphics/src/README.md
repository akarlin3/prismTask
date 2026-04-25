# Graphics Sources — PrismTask Store Listing

This directory holds the hand-authored SVG sources for every Play Store graphic. The Python renderer (`../render.py`) rasterizes each file to the correct PNG dimension in `../out/`.

## File inventory

| SVG | Purpose | Target PNG | Target dimensions |
|---|---|---|---|
| `icon.svg` | Play Store icon (not the in-app icon) | `icon-512.png` | 512×512 |
| `feature-graphic.svg` | Play Store feature graphic | `feature-graphic-1024x500.png` | 1024×500 |
| `screenshots/01-today-cyberpunk.svg` | Today screen, Cyberpunk | `screenshot-01.png` | 1080×1920 |
| `screenshots/02-eisenhower-synthwave.svg` | Eisenhower matrix, Synthwave | `screenshot-02.png` | 1080×1920 |
| `screenshots/03-habits-matrix.svg` | Habits + streak, Matrix | `screenshot-03.png` | 1080×1920 |
| `screenshots/04-pomodoro-void.svg` | Pomodoro, Void | `screenshot-04.png` | 1080×1920 |
| `screenshots/05-ai-weekly-review-cyberpunk.svg` | AI weekly review, Cyberpunk | `screenshot-05.png` | 1080×1920 |
| `screenshots/06-ai-time-block-synthwave.svg` | AI time blocking, Synthwave | `screenshot-06.png` | 1080×1920 |
| `screenshots/07-theme-picker-all.svg` | Four-theme composite | `screenshot-07.png` | 1080×1920 |
| `screenshots/08-onboarding-matrix.svg` | Onboarding, Matrix | `screenshot-08.png` | 1080×1920 |

## Rendering

From the repo root:

```bash
python docs/store-listing/graphics/render.py
```

Check dimensions only (no re-render):

```bash
python docs/store-listing/graphics/render.py --check
```

### Dependencies

`cairosvg` is preferred — it gives the highest fidelity on the gradients and alpha compositing. If `cairosvg` is unavailable on the host (it needs Cairo via libcairo2 or the Windows DLL bundle), `render.py` falls back to `Pillow` which renders a best-effort raster via the `svglib` path. Install the preferred one:

```bash
pip install cairosvg
# OR fallback
pip install Pillow svglib reportlab
```

On Windows, `cairosvg` usually needs `GTK+ for Windows Runtime` installed so that `libcairo-2.dll` is on PATH. If that is a hassle, the Pillow fallback is acceptable for the Play Store — the fonts will fall back to system defaults but the theme colors and layout still read correctly.

## Changing a theme on a screenshot

Each screenshot SVG has its theme palette inlined as hex colors at the top of the file (background, surface, accent, etc., matching `theme-tokens.json`). To re-render screenshot `N` in a different theme:

1. Open `screenshots/N-*.svg` and search-replace the theme hex tokens against those in `../theme-tokens.json` for the target theme.
2. Rename the file to match the new theme (`N-<screen>-<newtheme>.svg`).
3. Re-run `render.py`.

## Commit policy — sources vs outputs

**Recommendation:** commit the SVG sources and the rendered PNGs. The PNGs are the artifact uploaded to Play Console; committing them makes the listing state auditable without needing the Python toolchain on every machine. `render.py` is deterministic, so re-rendering produces a byte-stable output, and the diff of a PNG change is easy to review visually.

If you disagree and want to keep the output directory clean, add `docs/store-listing/graphics/out/` to `.gitignore` and run `render.py` as a pre-release step.
