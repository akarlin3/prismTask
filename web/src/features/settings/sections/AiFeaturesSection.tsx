import { useSettingsStore } from '@/stores/settingsStore';

/**
 * AI Features settings section — privacy parity with Android's
 * `app/src/main/java/com/averycorp/prismtask/ui/screens/settings/sections/AiSection.kt`.
 *
 * The toggle is the master opt-out for every Anthropic-touching feature
 * (NLP quick-add, weekly review narrative, daily briefing, weekly plan,
 * pomodoro plan, time blocking, Eisenhower auto-classify, conversation
 * task extraction, syllabus parsing). When OFF, the request-side gate in
 * `web/src/api/client.ts` short-circuits all `/ai/*`, `/tasks/parse`, and
 * `/syllabus/parse` requests with a synthetic 451 — no PrismTask data
 * leaves the browser. The flag is synced cross-device via Firestore at
 * `users/{uid}/prefs/user_prefs.ai_features_enabled`.
 */
export function AiFeaturesSection() {
  const enabled = useSettingsStore((s) => s.aiFeaturesEnabled);
  const setSetting = useSettingsStore((s) => s.setSetting);

  return (
    <div className="flex flex-col gap-3">
      <p className="text-xs text-[var(--color-text-secondary)]">
        AI features use Anthropic&rsquo;s Claude API to analyze your tasks,
        habits, schedules, and &mdash; for natural-language batch commands
        &mdash; your medication names. When the master toggle below is off, no
        PrismTask data is sent to Anthropic and the AI-powered features
        (quick-add parsing, weekly review narrative, daily briefing, weekly
        plan, pomodoro plan, time blocking) become unavailable. Anthropic does
        not train on inputs and deletes them within 30 days under standard API
        terms. See{' '}
        <a
          href="/privacy"
          className="text-[var(--color-accent)] hover:underline"
        >
          Privacy Policy
        </a>{' '}
        for full disclosure.
      </p>

      <div className="flex items-center justify-between py-2">
        <div className="pr-4">
          <p className="text-sm font-medium text-[var(--color-text-primary)]">
            Use Claude AI for advanced features
          </p>
          <p className="text-xs text-[var(--color-text-secondary)]">
            {enabled
              ? 'On — task / habit / project / medication names are sent to Anthropic for AI-powered features. Anthropic does not train on inputs and deletes them within 30 days under standard API terms.'
              : 'Off — no PrismTask data is sent to Anthropic. AI-powered features below are inactive until you turn this back on.'}
          </p>
        </div>
        <button
          role="switch"
          aria-checked={enabled}
          aria-label="Use Claude AI for advanced features"
          onClick={() => setSetting('aiFeaturesEnabled', !enabled)}
          className={`relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full transition-colors ${
            enabled
              ? 'bg-[var(--color-accent)]'
              : 'bg-[var(--color-border)]'
          }`}
        >
          <span
            className={`inline-block h-5 w-5 transform rounded-full bg-white shadow transition-transform ${
              enabled ? 'translate-x-[22px]' : 'translate-x-0.5'
            } mt-0.5`}
          />
        </button>
      </div>
    </div>
  );
}
