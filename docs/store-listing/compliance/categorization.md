# Categorization — PrismTask

**Destination:** Play Console → Grow → Store listing → Main store listing.

## Category

**Productivity.**

Alternate defensible choice: **Health & Fitness** (because of medication tracking, mood/energy logs, burnout protection, and the clinical report export). However, the app is still primarily a task manager with wellness features layered on top, so **Productivity** is the more honest fit and matches competitor positioning (Todoist, TickTick, Things).

## Tags

Play Console allows up to 5 tags. Recommended, in priority order:

1. **Task Management** — primary discovery keyword.
2. **Habit Tracker** — dominant secondary feature with streaks, analytics, and contribution grid.
3. **Time Management** — Pomodoro, time blocking, Eisenhower matrix.
4. **Planner** — daily/weekly/monthly views, AI planner, daily briefing.
5. **Journal** — mood logs, morning check-in, weekly review flows.

If Play Console offers any of these more-specific tags in the allowlist, prefer them: Productivity Tools, Focus, Mindfulness, Goal Setting, To-Do List.

## Target audience and age

**Target audience: 18 and over.**

Reasoning:

- Google Play account sign-in is required for cloud sync (single sign-on with the device Google account; no separate registration flow). Google requires account holders to be 13+ in the US and 16+ in the EU, so the practical floor is already above "Children" category.
- The app collects health-adjacent data (medications, mood, energy logs, burnout scores). Mixed audiences trigger COPPA compliance review; restricting to 18+ simplifies compliance and puts the app unambiguously outside the "Designed for Families" program.
- Several features reference adult wellness concepts (burnout scoring, boundary rules around work hours, clinical report export for therapists). These are legitimate for any age but are framed around working adults.

Select **"Targeted to adults only"** in Play Console.

## Main store listing image assets (summary)

- **App icon:** `graphics/out/icon-512.png` — 512×512 PNG, ≤1 MB.
- **Feature graphic:** `graphics/out/feature-graphic-1024x500.png` — 1024×500 PNG, ≤15 MB.
- **Phone screenshots:** `graphics/out/screenshot-01.png` through `screenshot-08.png` — 1080×1920 PNG, 9:16 aspect, ≤8 MB each.
- **7-inch tablet and 10-inch tablet screenshots:** not included in this PR; Play Console allows phone-only listings for closed testing. Add tablet screenshots before graduation to production.
- **Promo video:** not included; optional field.

## Contact info (Play Console — Store settings)

- Developer name: Avery Karlin
- Email: `privacy@prismtask.app`
- Website: `https://app.prismtask.app`
- Privacy policy URL: `https://akarlin3.github.io/prismTask/privacy/` (see `../../privacy/README.md` for the one-time GitHub Pages enablement steps)
