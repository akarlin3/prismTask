# Widget Launcher Previews Audit

**Scope:** Every PrismTask home-screen widget shows a blank/default tile in
the launcher widget picker (no rich preview). Find out why and ship the
fix.

**Date:** 2026-04-30
**Branch:** `fix/widget-launcher-previews`
**Baseline SHA:** origin/main @ `4a1660f8` (v1.8.1)

## 1. Widget previews missing in launcher picker (RED → PROCEED)

### Premise verification

Confirmed. The 14 `app/src/main/res/xml/*_widget_info.xml` `<appwidget-provider>`
declarations contain **zero** preview attributes. Verified via
`grep -E "preview" app/src/main/res/xml/*_widget_info.xml` — no matches.

Pre-existing widget art was authored as `app/src/main/res/layout/widget_preview_*.xml`
(14 files, ~1,215 LOC) and supporting drawables `app/src/main/res/drawable/widget_preview_*.xml`
(21 files), but **all 35 files are git-untracked** — never committed
(`git status` shows them as `??`). They sit on disk on the working clone
only, which is why the launcher picker never sees them.

### Findings

- **Widget info inventory** (14 files, all with `<appwidget-provider>` and
  string description, none with `previewLayout` or `previewImage`):

  ```
  calendar_widget_info.xml          quick_add_widget_info.xml
  eisenhower_widget_info.xml        stats_sparkline_widget_info.xml
  focus_widget_info.xml             streak_calendar_widget_info.xml
  habit_streak_widget_info.xml      timer_widget_info.xml
  inbox_widget_info.xml             today_widget_info.xml
  medication_widget_info.xml        upcoming_widget_info.xml
  productivity_widget_info.xml      project_widget_info.xml
  ```

- **Preview layout inventory** (14 files, 1:1 name mapping with the above
  by stripping `widget_info` and prefixing `widget_preview_`):

  ```
  widget_preview_calendar.xml          widget_preview_quick_add.xml
  widget_preview_eisenhower.xml        widget_preview_stats_sparkline.xml
  widget_preview_focus.xml             widget_preview_streak_calendar.xml
  widget_preview_habit_streak.xml      widget_preview_timer.xml
  widget_preview_inbox.xml             widget_preview_today.xml
  widget_preview_medication.xml        widget_preview_upcoming.xml
  widget_preview_productivity.xml      widget_preview_project.xml
  ```

- **Drawable references inside the preview layouts** all resolve to
  drawables in the untracked drawable set:

  | Referenced drawable                  | Present? |
  | ------------------------------------ | -------- |
  | `widget_preview_surface`             | ✓        |
  | `widget_preview_check_empty/filled`  | ✓        |
  | `widget_preview_dot_muted/primary`   | ✓        |
  | `widget_preview_heat_0..4`           | ✓        |
  | `widget_preview_pill[_primary]`      | ✓        |
  | `widget_preview_quadrant_q1..q4`     | ✓        |
  | `widget_preview_sparkline`           | ✓        |

- **Unused drawables** (also untracked): `widget_preview_card`,
  `widget_preview_dot_error`, `widget_preview_dot_success`,
  `widget_preview_dot_warning`. Five files, ~5 KB total. Worth keeping —
  they're referenced by zero layouts today but match the dot-color
  semantics already used (muted/primary). Cheap to retain, costs nothing
  in APK size beyond a handful of vectors.

- **API gating note.** `android:previewLayout` was added in API 31
  (Android 12). `minSdk = 26`. On Android 8.0–11, `previewLayout` is
  silently ignored and the launcher falls back to the app icon — which
  is the *current* behavior, so wiring `previewLayout` cannot regress
  pre-12 users. Adding `previewImage` as a legacy fallback would require
  pre-rendering 14 PNGs at multiple densities, which is a much larger
  scope; deferring.

- **CLAUDE.md drift (not load-bearing for this audit, but worth noting):**
  Says `WIDGETS_ENABLED = false`. Actual `app/build.gradle.kts` line
  reads `buildConfigField("boolean", "WIDGETS_ENABLED", "true")`.
  Widgets are live; the doc is stale.

### Risk classification

**RED** for the user-visible UX gap (the launcher picker is the *only*
discovery surface for widgets, and a blank tile reads as "broken or
incomplete app"). **YELLOW** if measured purely by data integrity — no
crash, no data loss, just bad first impression.

### Recommendation

**PROCEED** as a single PR:

1. Wire `android:previewLayout="@layout/widget_preview_<name>"` into all
   14 `*_widget_info.xml` files.
2. `git add` the 14 untracked preview layouts in
   `app/src/main/res/layout/` and the 21 untracked supporting drawables
   in `app/src/main/res/drawable/`.
3. **Defer:** legacy `previewImage` PNGs for pre-Android-12 users (low
   user impact, high authoring cost — pre-render 14×3-density PNGs).

Single coherent scope (wire-up + asset commit are inseparable —
declaring `@layout/widget_preview_X` without committing the layout
breaks the build). Bundle into one PR.

## Ranked improvement table

| # | Improvement                            | Wall-clock saved | Cost   | Ratio |
| - | -------------------------------------- | ---------------- | ------ | ----- |
| 1 | Wire `previewLayout` on all 14 widgets | High (UX)        | ~10min | High  |

## Anti-patterns flagged (not fixed in this audit)

- **CLAUDE.md says widgets are scaffolded/disabled; reality is widgets
  are shipped and enabled.** Out of scope for this audit; flag for
  next CLAUDE.md sweep.
- **No `previewImage` fallback for Android 8–11.** Deferred — high cost,
  low impact (Android 12+ is the bulk of installs in 2026).
- **Five unreferenced supporting drawables** (`widget_preview_card`,
  `widget_preview_dot_error/success/warning`). Trivially cheap to keep;
  trim only if a future audit shows we never adopt them.

## Phase 3 — Bundle summary (post-fan-out)

| Improvement                                       | PR(s) | Notes                                                            |
| ------------------------------------------------- | ----- | ---------------------------------------------------------------- |
| Wire `previewLayout` on all 14 widgets + commit assets | #1008 | Auto-squash-merge enabled; lands on main when required CI green. |

- **Measured impact (post-merge sanity to do on a real device):** install
  the build with PR #1008 merged, open the launcher widget picker, and
  confirm each widget shows its rich preview tile. Pre-Android-12
  launchers continue to fall back to the app icon (this is by design —
  `previewLayout` is API 31+).
- **Re-baselined wall-clock estimate:** ~10 min implementation matched
  the audit estimate; no rework.
- **Memory entry candidates:** none — the failure mode (untracked
  `widget_preview_*` layouts + missing `previewLayout` attribute) is
  a one-off oversight from prior widget polish work, not a recurring
  pattern. The `git status` and `grep -E "preview" *_widget_info.xml`
  triage was sufficient and is generic.
- **Schedule for next audit:** none queued from this scope. Optional
  follow-up — generate `previewImage` PNGs to cover Android 8–11 users
  if that segment becomes load-bearing.

## Anti-patterns to fix in a separate sweep (not this audit)

- **CLAUDE.md drift:** the project overview still says widgets are
  scaffolded with `WIDGETS_ENABLED = false`. Actual code reads
  `WIDGETS_ENABLED = true` and widgets are themed + shipped through
  v1.7. A focused CLAUDE.md sweep should reconcile.
