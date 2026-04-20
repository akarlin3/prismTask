# PrismTask — Theme Specification

**Purpose:** ground-truth reference for any code agent (Claude Code, etc.) creating or modifying a PrismTask theme.

**A theme is not a color palette.** A complete theme is a full stylistic system spanning **six axes**: colors, typography, shape, density, decorative treatments, and behavior flags. If any axis is missing, the theme is incomplete — screens will fall back to defaults that clash with the intended look.

---

## Where themes live

- `themes.js` — runtime token definitions used by the React mockups (this file is the source of truth for mockups).
- `app/src/main/java/com/averycorp/prismtask/ui/theme/ThemeColors.kt` — Kotlin color tokens for the Android app.
- `app/src/main/java/com/averycorp/prismtask/ui/theme/ThemeFonts.kt` — Kotlin font definitions.

When adding a theme, **update all three** and keep token names identical across them.

---

## Required shape

Every theme MUST define the full schema below. Do not omit keys — add the new theme as an object in `PRISM_THEMES` with this exact structure:

```js
NEW_THEME_ID: {
  id: 'NEW_THEME_ID',           // string, ALL_CAPS, matches key
  label: 'Display Name',        // human-facing label (e.g. 'Cyberpunk')
  tagline: 'Short descriptor',  // one-line theme description

  // ── 1. COLOR SYSTEM ────────────────────────────────────────────
  colors: {
    // ─── 1a. Core surface + brand (13 keys) ────────────────
    background:     '#...',  // app background
    surface:        '#...',  // card / sheet background
    surfaceVariant: '#...',  // raised surface, input fields
    border:         'rgba(...)', // hairline borders — usually primary @ low alpha

    primary:        '#...',  // brand accent (CTAs, active state, focus)
    secondary:      '#...',  // complementary accent (tags, highlights)

    onBackground:   '#...',  // primary text on background
    onSurface:      '#...',  // secondary text / labels on surface
    muted:          '#...',  // tertiary text, disabled, hint

    urgentAccent:   '#...',  // urgent priority flag color
    urgentSurface:  '#...',  // urgent priority background tint

    tagSurface:     '#...',  // tag chip background
    tagText:        '#...',  // tag chip foreground

    // ─── 1b. Semantic state (4 keys) ────────────────────────
    // Render each theme in its OWN language — do NOT reuse Material green/red.
    // Cyberpunk → electric neons; Matrix → green-spectrum variations + single alarm red;
    // Synthwave → vibrant retro/sunset palette; Void → muted editorial pigments.
    successColor:     '#...',  // completion, positive outcomes
    warningColor:     '#...',  // caution, moderate urgency
    destructiveColor: '#...',  // delete, critical failures, overdue
    infoColor:        '#...',  // neutral informational states

    // ─── 1c. Swipe action backgrounds (6 keys) ─────────────
    // Used as the revealed-gutter color behind list rows. Should be
    // SATURATED enough to read at a glance while swiping, and feel
    // like they belong to this theme (not generic material greens).
    swipeComplete:    '#...',  // swipe-to-complete background
    swipeDelete:      '#...',  // swipe-to-delete background
    swipeReschedule:  '#...',  // swipe-to-reschedule background
    swipeArchive:     '#...',  // swipe-to-archive background
    swipeMove:        '#...',  // swipe-to-move background
    swipeFlag:        '#...',  // swipe-to-flag background

    // ─── 1d. Eisenhower matrix quadrants (4 keys) ──────────
    // Four distinct hues for the 2×2 urgency/importance grid.
    // Q1 is the attention-demanding color (usually destructive/warm),
    // Q2 is the theme's signature (important work to invest in),
    // Q3 is a caution/warn hue, Q4 is muted/recedes.
    quadrantQ1:       '#...',  // urgent + important  (do now)
    quadrantQ2:       '#...',  // important + not urgent  (schedule)
    quadrantQ3:       '#...',  // urgent + not important  (delegate)
    quadrantQ4:       '#...',  // neither  (eliminate)

    // ─── 1e. Data-visualization palette (8 colors) ─────────
    // Categorical chart palette. Order matters — index 0 is the
    // primary series, 1 is secondary, etc. Must be DISTINGUISHABLE
    // at small sizes (bar/pie segments) against `surface`. Stay in
    // the theme's palette language.
    dataVisualizationPalette: ['#...', '#...', '#...', '#...',
                               '#...', '#...', '#...', '#...'],
  },

  // ── 2. TYPOGRAPHY ──────────────────────────────────────────────
  fonts: {
    body:    '"Family Name", system-ui, sans-serif', // body text
    display: '"Family Name", "Fallback", sans-serif', // headlines, hero numerals
    mono:    '"Family Name", ui-monospace, monospace', // timestamps, code
  },
  displayUpper:     true | false,   // UPPERCASE display type?
  displayTracking:  '0.06em',       // letter-spacing for display type (negative allowed, e.g. '-0.02em')

  // ── 3. SHAPE / COMPONENT GEOMETRY ──────────────────────────────
  chipShape:  'sharp' | 'pill',     // tag and button corner style
  radius:     0 | 2 | 10 | 18,      // general radius (buttons, inputs)
  cardRadius: 0 | 4 | 14 | 22,      // card corner radius (0 = terminal-flat)

  // ── 4. DENSITY ─────────────────────────────────────────────────
  density: 'tight' | 'airy',        // tight = compact padding + small gaps; airy = generous

  // ── 5. DECORATIVE TREATMENTS ───────────────────────────────────
  glow:       'none' | 'soft' | 'strong' | 'heavy', // primary-color glow on accents

  // Feature flags — set EXACTLY ONE of these true to define the theme's personality.
  // Components branch on these to apply signature treatments.
  brackets:   true, // corner brackets [ ] on hero cards (Cyberpunk-style)
  terminal:   true, // > prompts, caret cursor, rain backdrop (Matrix-style)
  editorial:  true, // hairlines, serif numerals, generous whitespace (Void-style)
  sunset:     true, // radial glow behind heroes (Synthwave-style)

  // Optional secondary decorations (can combine):
  scanlines:    true, // horizontal CRT scanlines over whole screen
  gridFloor:    true, // perspective grid floor on hero sections
  hudDividers:  true, // dashed HUD-style separator lines
},
```

---

## Reference implementations

The four shipped themes map 1-to-1 onto the four personalities. **When creating a new theme, pick ONE of these as your structural template** — copy it, then change values. Do not mix patterns.

| Theme       | Personality flag | Vibe                     | Typography                 | Radius     | Density | Glow    |
|-------------|------------------|--------------------------|----------------------------|------------|---------|---------|
| `CYBERPUNK` | `brackets`       | Neon HUD, sharp          | Chakra Petch / Audiowide   | 2 / 4      | tight   | strong  |
| `SYNTHWAVE` | `sunset`         | Retrowave, soft          | Rajdhani / Monoton         | 18 / 22    | airy    | heavy   |
| `MATRIX`    | `terminal`       | Green CRT terminal       | Share Tech Mono / VT323    | 0 / 0      | tight   | soft    |
| `VOID`      | `editorial`      | Editorial minimal        | Space Grotesk / Fraunces   | 10 / 14    | airy    | none    |

---

## Component-level contracts

Components read theme props via `theme.xxx` and branch. **Your theme must produce a coherent output in every one of these branches.** Verify by rendering all six main screens (today, tasks, daily, recurring, timer, settings) AND the ten flow screens (task detail, add task, templates, onboarding, pro, bug report, appearance, a11y, sync, shake).

Key branches to check:

1. **`ThemedCard`** (in `phone.jsx`) — reads `brackets`, `sunset`, `terminal`, `editorial`, `cardRadius`, `glow`. Each flag renders a different card treatment.
2. **`BottomNav`** — reads `colors.primary`, `glow`, `chipShape`.
3. **`SubSectionLabel`** (settings-helpers) — reads `editorial`, `terminal` to toggle hairline prefix / `# `-prefix.
4. **Hero number / headline elements** — read `fonts.display`, `displayUpper`, `displayTracking`.
5. **Priority pills / tag chips** — read `chipShape`, `colors.tagSurface`, `colors.tagText`, `colors.urgentAccent`.

---

## Checklist for adding a theme

Run through this list before declaring a theme done:

- [ ] `id` / `label` / `tagline` set
- [ ] All **36 color keys** present: 13 core surface/brand + 4 semantic state + 6 swipe actions + 4 Eisenhower quadrants + 1 `dataVisualizationPalette` array (length 8) = 28 properties on `colors`
- [ ] Semantic state colors match theme language (no generic Material green/red)
- [ ] Swipe colors are saturated enough to read at a glance while revealed
- [ ] Eisenhower quadrant colors are 4 DISTINCT hues (not 4 shades of the same)
- [ ] `dataVisualizationPalette` has exactly 8 entries, distinguishable at chart-size
- [ ] `fonts.body` / `fonts.display` / `fonts.mono` all set (with fallbacks)
- [ ] Google Fonts link added to `Theme Mockups.html` `<head>` for any new families
- [ ] `displayUpper` + `displayTracking` set
- [ ] `chipShape` + `radius` + `cardRadius` set
- [ ] `density` set (`'tight'` or `'airy'`)
- [ ] `glow` set
- [ ] **Exactly one** personality flag chosen (`brackets` | `terminal` | `editorial` | `sunset`) — or a new one defined end-to-end in `ThemedCard` and other branches
- [ ] Theme added to `THEME_ORDER` array
- [ ] `ThemeColors.kt` + `ThemeFonts.kt` mirrored in the Android codebase
- [ ] Rendered in mockups across all 16 screens without visual regressions

---

## Common mistakes (do not repeat)

- ❌ **"Just the colors."** A theme with only `colors` defined will render with VOID's defaults for type, shape, and decoration — defeating the purpose.
- ❌ **Two personality flags at once** (e.g. `brackets: true` AND `terminal: true`). Components will stack treatments and look broken. Pick one.
- ❌ **Missing `displayTracking`.** Display headlines will render with browser default spacing, breaking the intended rhythm.
- ❌ **Missing font fallbacks.** Always list at least one system fallback after the primary family.
- ❌ **New color outside the documented schema.** If you need a new semantic color, add it to the schema in **every** theme AND this document — do not one-off.
- ❌ **Copying Material Design's semantic state colors** (green=success, red=error). Each theme has its own language: Matrix expresses success through brightness within the green spectrum; Void expresses it through a muted sage; Cyberpunk through an icy mint next to its cyan primary. Follow the theme.
- ❌ **Using the same hue for all 4 Eisenhower quadrants.** They must be four DISTINCT hues — users scan them as a 2×2 grid.
- ❌ **`dataVisualizationPalette` with <8 entries or duplicates.** Charts need 8 categorical slots; duplicates make adjacent bars/slices merge.

---

## Prompt template for Claude Code

When asking Claude Code to add a theme, use this shape:

> Add a new theme `<ID>` to `themes.js`, `ThemeColors.kt`, and `ThemeFonts.kt`.
> Base structure on the `<CYBERPUNK|SYNTHWAVE|MATRIX|VOID>` template.
> Read `THEME_SPEC.md` first — every key in the schema must be present.
> Personality flag: `<brackets|terminal|editorial|sunset>`.
> Color direction: `<describe palette>`.
> Typography direction: `<display font + body font + tracking/casing>`.
> Shape direction: `<sharp|rounded|flat>`, density `<tight|airy>`, glow `<none|soft|strong|heavy>`.
> When finished, render all 16 screens in the mockup page and verify no visual regressions.
