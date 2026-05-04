# Widget Launch Action — Sealed-Class Migration Audit

**Date:** 2026-05-04
**Branch:** `claude/widget-sealed-action-migration-ZUbqf`
**Scope:** Forward-defense migration of widget → `MainActivity` deep-link
action strings to a `sealed class WidgetLaunchAction` with exhaustive
`when` dispatch.
**Verdict:** **STOP — no work needed.** The migration is already shipped.
**Disposition:** F.1 backlog item closed by recon; no PR opened.

---

## Operator decision (locked, pre-audit)

Forward-defense migration kept on F.1 backlog per operator decision May 1 —
even though Cowork defect-family hardening returned GREEN-GO (no current
recurrence), the migration was meant to prevent FUTURE recurrence. Framed
as an "easy win." Estimated ~50–100 LOC.

The audit's job: verify that estimate, scope the change, and either
implement (Phase 2 auto-fires) or report STOP if recon invalidates the
premise.

---

## Headline finding (STOP-A + STOP-B both fired)

The migration described in the prompt is already implemented and shipped:

- `app/src/main/java/com/averycorp/prismtask/widget/launch/WidgetLaunchAction.kt`
  — sealed class with 11 variants (10 data objects + `OpenTask` data class
  carrying a `taskId` payload), companion `deserialize(wireId, taskId)`.
- `app/src/main/java/com/averycorp/prismtask/MainActivity.kt:617-630` —
  `onNewIntent` parses the wire id via `WidgetLaunchAction.deserialize` and
  publishes the typed action to `launchActionState`.
- `app/src/main/java/com/averycorp/prismtask/ui/navigation/NavGraph.kt:375-410`
  — exhaustive `when` over `WidgetLaunchAction` performs the actual
  navigation dispatch. Adding a new subclass without a `when` branch is a
  compile error here.
- `app/src/test/java/com/averycorp/prismtask/widget/launch/WidgetLaunchActionTest.kt`
  — 7 tests pin round-trip, payload handling, unknown/null rejection,
  wire-id uniqueness, and wire-id stability (cross-process intent contract).
- All 14 widget call sites (`TodayWidget`, `FocusWidget`, `TimerWidget`,
  `EisenhowerWidget`, `MedicationWidget`, `StatsSparklineWidget`,
  `HabitStreakWidget`, `UpcomingWidget`, `InboxWidget`, `QuickAddWidget`,
  `CalendarWidget`, `StreakCalendarWidget`) reference
  `WidgetLaunchAction.<Variant>.wireId` rather than raw string literals.

The `MainActivity` → `NavGraph` dispatch composes via `launchActionState`,
not a private `handleWidgetLaunch` method as the prompt's hypothesis
sketch (B.2) imagined — but the load-bearing forward-defense property
(exhaustive `when` over the sealed class) is in place at the NavGraph
level. Compile-time enforcement is therefore present.

---

## Phase 1 — recon (memory #18 quad sweep)

### A.1 Drive-by detection (GREEN — premise invalidated)

`git log --all -S "EXTRA_LAUNCH_ACTION" --oneline` returns one commit:

```
bca7143 chore: bump to v1.8.17 (build 815) [skip ci]
```

The repository state on `main` is a single squashed commit at v1.8.17
(build 815). Per-PR history (PR #1042, PR #1044, PR #1056, etc. cited in
the prompt) is not retrievable from this clone. The relevant signal is
that the file `WidgetLaunchAction.kt` already exists in that single
commit — i.e., it shipped at-or-before v1.8.17.

The class's KDoc points at `docs/audits/DEFECT_FAMILY_HARDENING_AUDIT.md
§C` as the origin of the migration. That audit doc is **not** present in
the repo (`find docs -iname "*hardening*"` returns nothing). The migration
itself is real and verifiable in source; only its history doc is missing.

### A.2 Parked-branch sweep (GREEN — empty)

```
git branch -a | grep -iE "widget|sealed|launch|deep.?link|action"
```

Returns only the current task branch:

```
* claude/widget-sealed-action-migration-ZUbqf
  remotes/origin/claude/widget-sealed-action-migration-ZUbqf
```

No parked WIP branches with related work.

### A.3 Shape-grep — definitive action-string list (GREEN)

`grep -rn "EXTRA_LAUNCH_ACTION" app/` enumerates every consumer/producer.
**Producers (widgets, 14 call sites):**

| File | Line | Variant |
|---|---|---|
| `widget/StatsSparklineWidget.kt` | 94 | `OpenInsights` |
| `widget/TodayWidget.kt` | 96 | `OpenToday` |
| `widget/TodayWidget.kt` | 218 | `OpenTask` |
| `widget/FocusWidget.kt` | 82 | `OpenTask` |
| `widget/FocusWidget.kt` | 88 | `OpenTimer` |
| `widget/MedicationWidget.kt` | 85 | `OpenMedication` |
| `widget/TimerWidget.kt` | 77 | `OpenTimer` |
| `widget/TimerWidget.kt` | 102 | `OpenTimer` |
| `widget/HabitStreakWidget.kt` | 75 | `OpenHabits` |
| `widget/UpcomingWidget.kt` | 85 | `OpenToday` |
| `widget/InboxWidget.kt` | 99 | `OpenInbox` |
| `widget/EisenhowerWidget.kt` | 137 | `OpenMatrix` |
| `widget/QuickAddWidget.kt` | 72 | `QuickAdd` |
| `widget/QuickAddWidget.kt` | 76 | `VoiceInput` |
| `widget/QuickAddWidget.kt` | 113 | `OpenTemplates` |
| `widget/CalendarWidget.kt` | 240 | `OpenTask` |
| `widget/StreakCalendarWidget.kt` | 93 | `OpenHabits` |

**Consumer (single):** `MainActivity.kt:626-628` (`parseLaunchAction`).

**Sealed-class variant count:** **11** (10 data objects + 1 data class).
The prompt's framing of "5 action strings" is materially out of date —
the surface has grown ~2.2× since that estimate. This is documented as
**STOP-B** below.

### A.4 Sibling-primitive (e) axis (YELLOW — one finding, deferred)

Searched non-widget surfaces that share the stringly-typed-intent-action
defect family:

- **Notifications package:** `MedicationReminderReceiver`,
  `EscalationBroadcastReceiver`, `CompleteTaskReceiver`, `LogMedicationReceiver`,
  `MedStepReminderReceiver`, `HabitFollowUpReceiver`, etc. — these consume
  Intent extras (e.g., task ids, medication ids) but do not dispatch on a
  freeform action-string. They use Android's standard
  `Intent.ACTION_BOOT_COMPLETED` (BootReceiver) and per-receiver named
  extras. **Not affected by the same defect class.**
- **`BriefingNotificationWorker.kt:69`** — `putExtra("open_screen",
  "daily_briefing")`. This is a raw stringly-typed intent payload for
  screen routing, used by the daily-briefing notification. **Sibling
  defect candidate** — same shape (string key, string value) with no
  compile-time exhaustiveness check on the receiver side.
- **`WeeklyReviewWorker.kt:120`** — `EXTRA_OPEN_WEEKLY_REVIEWS =
  "open_weekly_reviews"`. Single named constant for one extra. Not a
  dispatch surface; one fixed flag. **Not a sibling defect.**
- **Automation engine action handlers (PR #1056-style):** domain-typed
  via `AutomationAction` sealed class already (per CLAUDE.md context). No
  sibling defect.
- **Quick-Add intent paths:** route through `WidgetLaunchAction.QuickAdd`
  + `WidgetLaunchAction.VoiceInput`. Already typed.

**Net (e) axis finding:** one sibling — the briefing notification's
`"open_screen"` extra. Surfaced as a deferred item below; not pulled into
this audit's scope per prompt's anti-pattern rule ("Do not widen scope to
non-widget Intent action surfaces without operator pre-approval").

### A.5 STOP-E check (call-site survey)

`grep -rn "EXTRA_LAUNCH_ACTION\|widget.launch.WidgetLaunchAction"` outside
`MainActivity.kt` and the `widget/` package returns:

```
ui/navigation/NavGraph.kt:67: import com.averycorp.prismtask.widget.launch.WidgetLaunchAction
```

`NavGraph` consumes the typed sealed class (not a raw string), so STOP-E
does not fire. The Activity remains the sole `EXTRA_LAUNCH_ACTION`
deserializer; routing is delegated to `NavGraph` over the typed value.

---

## B. Hypothesis verdict

| Sub-hypothesis | Verdict | Notes |
|---|---|---|
| B.1 Sealed-class shape | **GREEN — already shipped** | 11 variants, `data object` for singletons, `data class OpenTask(taskId)` for parameterized. `deserialize(wireId, taskId)` companion. Diverges from the prompt's `actionString` field name (uses `wireId`) and from `fromActionString` companion (uses `deserialize`); behavior is equivalent. |
| B.2 `MainActivity.onNewIntent` integration | **GREEN — already shipped, different shape** | `onNewIntent` calls `parseLaunchAction(intent)` and writes to `launchActionState: MutableState<WidgetLaunchAction?>`. NavGraph reads the state and runs the exhaustive `when`. Compile-time exhaustiveness is preserved at the NavGraph layer rather than a private `handleWidgetLaunch`. |
| B.3 Widget `PendingIntent` construction | **GREEN — already shipped** | All 14 call sites (table above) reference `WidgetLaunchAction.<Variant>.wireId` / `WidgetLaunchAction.OpenTask.WIRE_ID`. No raw `"open_*"` literals remain in `app/src/main/`. |
| B.4 Test coverage | **GREEN — already shipped** | `WidgetLaunchActionTest` covers round-trip, OpenTask payload + missing-payload rejection, unknown/null wireId, wireId uniqueness, and wireId stability (pinned literal strings). The originally-cited `MainActivityActionConstantsTest` does not exist; the stability invariant it would have covered is folded into `WidgetLaunchActionTest`'s `wire ids are stable strings (cross-process intent contract)` test. **One missing item** — none of the 7 tests reference exhaustiveness over the sealed hierarchy by failing-when-a-variant-is-added. The compile-time `when` in NavGraph is the actual exhaustiveness gate. |

---

## C. STOP-conditions evaluated

| STOP | Fired? | Evidence |
|---|---|---|
| **STOP-A** — pre-existing implementation | **YES** | `WidgetLaunchAction.kt`, `WidgetLaunchActionTest.kt`, MainActivity dispatch, NavGraph exhaustive `when`, all 14 widget call sites already migrated. |
| **STOP-B** — surface materially different | **YES** | Prompt says 5 strings; reality is 11 variants with a parameterized payload (`OpenTask(taskId)`). Operator's "easy win" framing was anchored on a stale 5-string surface and a missing audit doc reference. |
| **STOP-C** — sibling stringly-typed surface | **YES (mild)** | `BriefingNotificationWorker.kt:69` `"open_screen" → "daily_briefing"` raw-string extra. **Not pulled in.** Surfaced as deferred item below. |
| **STOP-D** — Phase 2 LOC > estimate | **N/A** | No Phase 2 to size; STOP-A short-circuited it. |
| **STOP-E** — non-MainActivity consumer | **NO** | NavGraph is the only sibling consumer and it consumes the typed sealed class, not the raw string. |

Per audit-first protocol ("STOP-and-report on wrong premises is the one
real halt"), Phase 2 does not auto-fire. The deliverable for this session
is the audit + closure of the F.1 backlog item.

---

## D. Premise verification (memory #22 bidirectional)

| # | Premise | Verification | Result |
|---|---|---|---|
| D.1 | PR #1042 + PR #1044 shipped | `git log --grep="open_timer\|open_matrix"` returns only `bca7143` | **Cannot verify** — repo is single-squashed commit at v1.8.17. Per-PR history is not present in this clone. The migration's *artifacts* are present in source; the cited PRs cannot be confirmed as their origin. |
| D.2 | `MainActivityActionConstantsTest` exists and pins 5 strings | `find . -name "MainActivityActionConstants*"` → empty | **FALSE.** No such test file. The 5-string baseline implied by this test never existed in the current repo state, OR was retired when the sealed-class migration absorbed its invariant into `WidgetLaunchActionTest.wire ids are stable strings`. |
| D.3 | No prior sealed-class migration attempted | A.1 + A.2 sweeps | **FALSE.** Sealed class is shipped. |
| D.4 | All current action strings flow through `MainActivity.onNewIntent` only | A.5 grep | **TRUE.** NavGraph consumes the typed value downstream; no other component reads the raw extra. |
| D.5 | Action string count = 5 | A.3 enumeration | **FALSE.** Count is **11** (10 singletons + `OpenTask` data class with `taskId` payload). |

**Three of five premises are wrong** (D.2, D.3, D.5) and one is unverifiable
(D.1). The operator's prompt was anchored on a session-memory snapshot that
materially predates v1.8.17. Per memory #22 bidirectional verification:
report rather than rationalize. **No code change.**

---

## Phase 2 — SKIPPED

Phase 2 does not fire. STOP-A and STOP-B both blocked it; the migration's
artifacts are already in source, tested, and dispatched exhaustively at
the NavGraph layer.

---

## Phase 3 — Bundle summary

**PRs merged this session:** none.
**Branch state:** `claude/widget-sealed-action-migration-ZUbqf` will land
this audit doc only. No source / test / build / config files touched.
**App version:** unchanged at v1.8.17 (build 815). No tag.
**F.1 closure:** "Sealed-enum migration for widget deep-link" closes
**0 → 1.0** as a paper closure — recon confirmed it shipped under a prior
audit (`DEFECT_FAMILY_HARDENING_AUDIT §C` per the class's KDoc) before
this branch existed. Backlog should be marked done with a pointer to
`WidgetLaunchAction.kt` + this audit doc.

**Verification gates not exercised** (no Phase 2 work):

- ktlint / detekt — N/A
- `./gradlew :app:compileDebugKotlin` — N/A
- `./gradlew :app:assembleDebugAndroidTest` — N/A
- `WidgetLaunchActionTest` — already present and presumed green per
  CI history; not re-run as part of this session
- AVD widget-tap smoke tests — N/A
- Forward-defense compile-error proof (deliberately-broken sealed-variant
  experiment described in prompt's Phase 3) — recon confirms NavGraph's
  `when` over `WidgetLaunchAction` is exhaustive, so the property holds by
  construction. Not exercised live.

**Memory entry candidates:**

- *Possible:* "Forward-defense migration items on the backlog should
  be drive-by-checked against current source before scoping — a stale
  prompt anchor (5 action strings, missing audit doc, named tests that
  don't exist) cost ~one Phase 1 sweep here." Decline-default per memory
  #28 token-efficiency rule unless this pattern recurs across a second
  audit.
- *Decline:* (e)-axis sibling finding (`BriefingNotificationWorker`
  `"open_screen"`). Single occurrence, not a defect family.

---

## Deferred (with re-trigger criteria, NOT auto-filed per memory #30)

### Deferred-1: `BriefingNotificationWorker` raw `"open_screen"` extra

**Surface:** `app/src/main/java/com/averycorp/prismtask/notifications/BriefingNotificationWorker.kt:69`
stamps `putExtra("open_screen", "daily_briefing")` on a notification's
content intent. There is no typed contract on the receiver side; if the
target screen route is renamed or the receiver branch is removed, the
notification tap silently no-ops — the same defect class this widget
audit guards against.

**Re-trigger criteria** (file follow-up only when one of these holds):

1. A second sibling notification adds another raw `"open_*"` screen-routing
   extra, making this a pattern (≥2 surfaces) rather than a one-off.
2. A user-visible defect lands where a notification deep-link silently
   no-ops post-refactor.
3. The notification surface gains a second action variant beyond the
   single existing route.

If none of those fire, leave as-is — single-occurrence raw strings are
not worth a sealed-class wrapper.

---

## Open questions for operator

1. **F.1 backlog closure framing.** Should the F.1 item be marked "done
   (shipped under DEFECT_FAMILY_HARDENING_AUDIT §C)" or
   "deduplicated — superseded"? Either works for the timeline; "done"
   credits the prior audit, "deduplicated" flags that this F.1 entry
   was redundant when filed.
2. **Missing reference doc.** The shipped `WidgetLaunchAction.kt`'s KDoc
   points at `docs/audits/DEFECT_FAMILY_HARDENING_AUDIT.md §C`, but that
   file is not in the repo. Either (a) the doc was written and never
   committed, (b) it lives under a different name and the KDoc reference
   has rotted, or (c) it was deleted post-merge. Worth a 2-minute
   `git log --all -- "**/DEFECT_FAMILY_HARDENING*"` if/when full PR
   history is restored — out of scope here because the squash rolled it up.
3. **Deferred-1 disposition.** Should `BriefingNotificationWorker`'s raw
   `"open_screen"` extra be filed against F.1 or against a new "notification
   deep-link hardening" item? Operator call. Default: leave deferred.

---

## Anti-patterns avoided

- Did **not** open a PR with a duplicate sealed class.
- Did **not** rename `wireId`/`deserialize` to match the prompt's
  speculative `actionString`/`fromActionString` names — that would have
  been pure churn and broken the existing test.
- Did **not** auto-file the (e)-axis `BriefingNotificationWorker` finding
  as a new timeline item per memory #30.
- Did **not** widen scope to notification action strings without operator
  pre-approval.
- Did **not** rationalize past the wrong-premise signal (memory #22 +
  audit-first hard rule). Three of five premises were wrong; that's a
  STOP, not a "fix the audit doc to match reality" prompt to keep going.
