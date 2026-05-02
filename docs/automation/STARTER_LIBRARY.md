# Automation Starter Library

User-facing reference for the bundled starter automation rules. The full
inventory lives in
[`app/src/main/java/com/averycorp/prismtask/data/seed/AutomationStarterLibrary.kt`](../../app/src/main/java/com/averycorp/prismtask/data/seed/AutomationStarterLibrary.kt);
this doc is the human-readable index. Architecture rationale lives in
[`docs/audits/AUTOMATION_STARTER_LIBRARY_ARCHITECTURE.md`](../audits/AUTOMATION_STARTER_LIBRARY_ARCHITECTURE.md).

## How To Use

1. Open **Settings → Automation** to reach the rule list.
2. Tap the **library icon** in the top bar (next to the run-history icon)
   to open **Browse Templates**.
3. Browse by category or search by keyword. Tap a card to see the full
   trigger / condition / action breakdown.
4. Tap **Add To My Rules**. The template is imported as a regular rule —
   **disabled by default**.
5. Return to **Settings → Automation** and toggle the rule on to enable
   it. You can edit, run-now, or delete the imported rule the same way as
   any other.

Imported rules sync across your devices via the standard automation-rule
sync once that routing lands (Phase I follow-up). Templates themselves
are bundled in the app — they don't take network or storage on your
account.

## Categories And Rules

### Stay On Top Of Work

| Rule | When It Fires | What It Does |
|---|---|---|
| Notify When Overdue Urgent Task | A task is updated and it's overdue + priority ≥ High | Posts a "Tap to review" notification |
| Notify When High-Priority Task Added | A task is created at priority ≥ High | Posts a notification so it doesn't get buried |
| Daily Morning Kickoff | Daily at 7:00 | Posts "Plan your day" notification |
| Evening Review Prompt | Daily at 21:00 | Reminds you to glance at tomorrow's plan |
| Sunday Weekly Planning | Sunday at 17:00 | Prompts a 10-minute weekly setup |

### Build Healthy Habits

| Rule | When It Fires | What It Does |
|---|---|---|
| 7-Day Streak Celebration | A habit hits a 7-day streak | Posts a milestone notification |
| 30-Day Streak Milestone | A habit hits a 30-day streak | Posts a milestone notification |
| 100-Day Streak Legend | A habit hits a 100-day streak | Posts a milestone notification |
| Weekly Habit Review | Sunday at 18:00 | Prompts a habit retrospective |

### Medication Adherence

| Rule | When It Fires | What It Does |
|---|---|---|
| Morning Medication Reminder | Daily at 8:00 | Reminds you to take morning meds |
| Evening Medication Check | Daily at 20:00 | Reminds you to take evening meds |
| Weekly Adherence AI Summary | Sunday at 19:00 | Asks AI to summarize the week's logs (requires AI features) |

### Focus + Deep Work

| Rule | When It Fires | What It Does |
|---|---|---|
| AI Summarize Recent Completions | A task is completed | Asks AI to summarize the last 50 completions (requires AI features) |
| Manual Focus-Session Kickoff | You tap **Run Now** | Posts a "phone away, deep work" nudge |
| Mid-Day Focus Block | Daily at 14:00 | Reminds you to claim a 25-minute deep-work block |

### Reduce Friction

| Rule | When It Fires | What It Does |
|---|---|---|
| Auto-Tag New Tasks With #today | A task is created | Adds the `#today` tag automatically |
| Auto-Flag Urgent Tasks | A task is created at priority Urgent | Sets the flag so it floats to the top |
| Tag Weekend Tasks As Personal | A task is created on a weekend | Adds `#personal` and the Personal life category |
| Auto-Categorize Health Tasks | A task title contains doctor / dentist / appointment | Sets the Health life category |
| Auto-Categorize Work Tasks | A task title contains meeting / sync / 1:1 | Sets the Work life category |

### Wellness Check-Ins

| Rule | When It Fires | What It Does |
|---|---|---|
| Morning Mood Check | Daily at 7:30 | Prompts you to log mood + energy |
| Midday Wellness Pause | Daily at 13:00 | Stand-up / hydrate / reset reminder |
| Evening Reflection Prompt | Daily at 21:30 | Brief journaling nudge |
| Sunday Weekly Review | Sunday at 17:30 | 5-minute weekly retrospective |

### Power User

| Rule | When It Fires | What It Does | Requires AI |
|---|---|---|---|
| Manual AI Briefing | You tap **Run Now** | Generates an AI briefing of today's tasks | Yes |
| Daily End-Of-Day AI Summary | Daily at 22:00 | Asks AI to summarize what you completed today | Yes |
| Weekly AI Reflection | Sunday at 20:00 | Asks AI to reflect on the past week | Yes |

## AI-Action Rules

Five rules use AI actions (`ai.summarize` / `ai.complete`) and respect the
**AI features** master toggle in Settings. If AI is off, the rule still
fires its trigger + condition pass, but the AI action returns the
451-equivalent gated error and writes a clear log entry — no Anthropic
call is attempted.

The five AI-action rules:

1. AI Summarize Recent Completions (Focus)
2. Weekly Adherence AI Summary (Medication)
3. Manual AI Briefing (Power User)
4. Daily End-Of-Day AI Summary (Power User)
5. Weekly AI Reflection (Power User)

## Editing Imported Rules

The v1.7 series ships the rule list + library; the in-app rule editor
lands in v1.1 of the engine series. Until then, imported rules are
read-only. To "reset" an imported rule, delete it (the trash icon in the
overflow menu) and re-import.

The five built-in seed rules (`Notify When Overdue Urgent Task`,
`Auto-Tag New Tasks With #today`, `Daily Morning Kickoff`,
`7-Day Streak Celebration`, `AI Summarize Recent Completions`) cannot be
deleted — toggling them off is the way to disable them.

## Frequently Asked

**Will the same rule fire twice if I both let it auto-seed and import it
from the library?**
No. The seeder uses the same `templateKey` as the library, so the seeded
copy and a manual import are de-duplicated. If you've already enabled the
seeded copy, the library card shows it as a regular template and tapping
**Add** creates a separate rule with the same template key — but the
seeded copy keeps firing. To avoid duplicate notifications, either delete
the imported copy or toggle the seeded copy off.

**Can I export my own rule as a template?**
Not in v1. Tracked as a v2 backlog item — until then, the library is
curated content shipped with the app.

**Why don't all my Reduce-Friction tag rules persist tags on existing
tasks?**
The `mutate.task tagsAdd` action only fires on *new* tasks (`TaskCreated`
trigger). It doesn't retroactively tag existing tasks. To bulk-tag
existing tasks, use the **Batch Operations** AI prompt instead.
