import { useEffect, useState } from 'react';
import { Loader2 } from 'lucide-react';
import { toast } from 'sonner';
import {
  DEFAULT_REMINDER_MODE_PREFERENCES,
  REMINDER_INTERVAL_MAX_MINUTES,
  REMINDER_INTERVAL_MIN_MINUTES,
  getReminderModePreferences,
  setReminderModePreferences,
  type MedicationReminderModePreferences,
} from '@/api/firestore/medicationPreferences';
import type { MedicationReminderMode } from '@/api/firestore/medicationSlots';
import { getFirebaseUid } from '@/stores/firebaseUid';

const PRESETS_MINUTES = [120, 240, 360, 480];

/**
 * Settings section for the global default medication reminder mode.
 *
 * Web is settings-only — Android delivers the actual reminders. The
 * persistent banner makes that explicit. Per-slot and per-medication
 * overrides on the slot/medication editors will inherit this default
 * when null. Web slot editor will gain its own picker in a follow-up
 * once the picker UX is settled on Android (PR3).
 */
export function MedicationReminderModeSection() {
  const [prefs, setPrefs] = useState<MedicationReminderModePreferences>(
    DEFAULT_REMINDER_MODE_PREFERENCES,
  );
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  const uid = (() => {
    try {
      return getFirebaseUid();
    } catch {
      return null;
    }
  })();

  useEffect(() => {
    if (!uid) {
      setLoading(false);
      return;
    }
    setLoading(true);
    getReminderModePreferences(uid)
      .then(setPrefs)
      .catch((e) =>
        toast.error((e as Error).message || 'Failed to load reminder preferences'),
      )
      .finally(() => setLoading(false));
  }, [uid]);

  const persist = async (next: MedicationReminderModePreferences) => {
    if (!uid) return;
    const previous = prefs;
    setPrefs(next);
    setSaving(true);
    try {
      await setReminderModePreferences(uid, next);
    } catch (e) {
      setPrefs(previous);
      toast.error((e as Error).message || 'Save failed');
    } finally {
      setSaving(false);
    }
  };

  const handleModeChange = (mode: MedicationReminderMode) => {
    if (mode === prefs.mode) return;
    persist({ ...prefs, mode });
  };

  const handleIntervalChange = (rawMinutes: number) => {
    const minutes = Math.min(
      REMINDER_INTERVAL_MAX_MINUTES,
      Math.max(REMINDER_INTERVAL_MIN_MINUTES, Math.round(rawMinutes)),
    );
    if (minutes === prefs.interval_default_minutes) return;
    persist({ ...prefs, interval_default_minutes: minutes });
  };

  if (!uid) {
    return (
      <p className="text-xs text-[var(--color-text-secondary)]">
        Sign in to manage medication reminder preferences.
      </p>
    );
  }

  return (
    <div className="flex flex-col gap-3 text-sm">
      <p className="text-xs text-[var(--color-text-secondary)]">
        Default reminder mode for medication slots. Each slot can override
        this in the medication slot editor.
      </p>

      <div
        role="status"
        className="rounded-md border border-[var(--color-border)] bg-[var(--color-bg-secondary)] p-3 text-xs text-[var(--color-text-secondary)]"
      >
        <strong className="text-[var(--color-text-primary)]">
          Reminder delivery is currently Android-only.
        </strong>{' '}
        Settings sync to Firestore so your phone picks them up. Web
        reminder delivery will arrive with Web Push in a future release.
      </div>

      {loading ? (
        <div className="flex items-center gap-2 py-2 text-[var(--color-text-secondary)]">
          <Loader2 className="h-4 w-4 animate-spin" /> Loading…
        </div>
      ) : (
        <>
          <div role="radiogroup" aria-label="Default reminder mode" className="flex gap-2">
            <ModeRadio
              label="Clock"
              checked={prefs.mode === 'CLOCK'}
              onChange={() => handleModeChange('CLOCK')}
            />
            <ModeRadio
              label="Interval"
              checked={prefs.mode === 'INTERVAL'}
              onChange={() => handleModeChange('INTERVAL')}
            />
          </div>

          <p className="text-xs text-[var(--color-text-secondary)]">
            {prefs.mode === 'CLOCK'
              ? 'Reminders fire at each slot’s ideal time.'
              : `Reminders fire ${formatInterval(prefs.interval_default_minutes)} after the most recent dose.`}
          </p>

          {prefs.mode === 'INTERVAL' && (
            <IntervalPicker
              currentMinutes={prefs.interval_default_minutes}
              disabled={saving}
              onChange={handleIntervalChange}
            />
          )}
        </>
      )}
    </div>
  );
}

function ModeRadio({
  label,
  checked,
  onChange,
}: {
  label: string;
  checked: boolean;
  onChange: () => void;
}) {
  return (
    <label
      className={`cursor-pointer rounded-md border px-3 py-1.5 text-xs ${
        checked
          ? 'border-[var(--color-accent)] bg-[var(--color-accent)]/10 text-[var(--color-text-primary)]'
          : 'border-[var(--color-border)] bg-[var(--color-bg-secondary)] text-[var(--color-text-secondary)]'
      }`}
    >
      <input
        type="radio"
        className="sr-only"
        checked={checked}
        onChange={onChange}
        name="medication-reminder-mode"
      />
      {label}
    </label>
  );
}

function IntervalPicker({
  currentMinutes,
  disabled,
  onChange,
}: {
  currentMinutes: number;
  disabled: boolean;
  onChange: (minutes: number) => void;
}) {
  const isCustom = !PRESETS_MINUTES.includes(currentMinutes);
  const [customText, setCustomText] = useState(currentMinutes.toString());

  // Keep the custom field in sync if the parent changes (e.g. preset click).
  useEffect(() => {
    setCustomText(currentMinutes.toString());
  }, [currentMinutes]);

  return (
    <div className="flex flex-col gap-2">
      <div className="flex flex-wrap gap-1.5">
        {PRESETS_MINUTES.map((mins) => (
          <button
            key={mins}
            type="button"
            disabled={disabled}
            onClick={() => onChange(mins)}
            className={`rounded-md border px-2 py-1 text-xs ${
              !isCustom && currentMinutes === mins
                ? 'border-[var(--color-accent)] bg-[var(--color-accent)]/10 text-[var(--color-text-primary)]'
                : 'border-[var(--color-border)] bg-[var(--color-bg-secondary)] text-[var(--color-text-secondary)]'
            } disabled:opacity-50`}
          >
            {formatInterval(mins)}
          </button>
        ))}
        <button
          type="button"
          disabled={disabled}
          onClick={() => {
            if (!isCustom) {
              onChange(currentMinutes + 1);
            }
          }}
          className={`rounded-md border px-2 py-1 text-xs ${
            isCustom
              ? 'border-[var(--color-accent)] bg-[var(--color-accent)]/10 text-[var(--color-text-primary)]'
              : 'border-[var(--color-border)] bg-[var(--color-bg-secondary)] text-[var(--color-text-secondary)]'
          } disabled:opacity-50`}
        >
          Custom
        </button>
      </div>
      {isCustom && (
        <label className="flex flex-col gap-1 text-xs">
          <span className="text-[var(--color-text-secondary)]">
            Custom interval (minutes, {REMINDER_INTERVAL_MIN_MINUTES}–{REMINDER_INTERVAL_MAX_MINUTES})
          </span>
          <input
            type="number"
            inputMode="numeric"
            min={REMINDER_INTERVAL_MIN_MINUTES}
            max={REMINDER_INTERVAL_MAX_MINUTES}
            value={customText}
            onChange={(e) => setCustomText(e.target.value)}
            onBlur={() => {
              const parsed = Number.parseInt(customText, 10);
              if (Number.isFinite(parsed)) onChange(parsed);
            }}
            className="w-32 rounded-md border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-2 py-1 text-xs text-[var(--color-text-primary)] outline-none focus:border-[var(--color-accent)]"
          />
        </label>
      )}
    </div>
  );
}

function formatInterval(mins: number): string {
  if (mins % 60 === 0) return `${mins / 60}h`;
  return `${mins}m`;
}
