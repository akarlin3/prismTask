import { useState } from 'react';
import { Loader2, Meh, Smile, Frown, Angry, Laugh } from 'lucide-react';
import { toast } from 'sonner';
import { Modal } from '@/components/ui/Modal';
import { Button } from '@/components/ui/Button';
import {
  createLog,
  type TimeOfDay,
  type MoodEnergyLogInput,
} from '@/api/firestore/moodEnergyLogs';
import { getFirebaseUid } from '@/stores/firebaseUid';
import { useSettingsStore } from '@/stores/settingsStore';
import { useLogicalToday } from '@/utils/useLogicalToday';

const MOOD_META: { value: number; label: string; Icon: typeof Smile }[] = [
  { value: 1, label: 'Awful', Icon: Angry },
  { value: 2, label: 'Low', Icon: Frown },
  { value: 3, label: 'OK', Icon: Meh },
  { value: 4, label: 'Good', Icon: Smile },
  { value: 5, label: 'Great', Icon: Laugh },
];

const TIME_OF_DAY_OPTIONS: TimeOfDay[] = [
  'morning',
  'afternoon',
  'evening',
  'night',
];

/**
 * Quick-log modal for mood + energy. Writes directly to the
 * Firestore-native `mood_energy_logs` collection (no backend
 * round-trip). Default date is the user's logical today (Start-of-Day
 * aware via settings).
 */
export function MoodLogModal({
  isOpen,
  onClose,
  onLogged,
}: {
  isOpen: boolean;
  onClose: () => void;
  onLogged?: () => void;
}) {
  const startOfDayHour = useSettingsStore((s) => s.startOfDayHour);
  const todayIso = useLogicalToday(startOfDayHour);

  const [mood, setMood] = useState(3);
  const [energy, setEnergy] = useState(3);
  const [notes, setNotes] = useState('');
  const [timeOfDay, setTimeOfDay] = useState<TimeOfDay>('morning');
  const [saving, setSaving] = useState(false);

  const handleSave = async () => {
    setSaving(true);
    try {
      const uid = getFirebaseUid();
      const input: MoodEnergyLogInput = {
        date_iso: todayIso,
        mood,
        energy,
        notes: notes.trim(),
        time_of_day: timeOfDay,
      };
      await createLog(uid, input);
      toast.success('Logged mood & energy');
      onLogged?.();
      onClose();
      setMood(3);
      setEnergy(3);
      setNotes('');
    } catch (e) {
      toast.error((e as Error).message || 'Failed to log mood');
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal isOpen={isOpen} onClose={onClose} title="Log Mood & Energy" size="sm">
      <div className="flex flex-col gap-4">
        <div>
          <span className="mb-1.5 block text-xs font-semibold uppercase tracking-wide text-[var(--color-text-secondary)]">
            Mood
          </span>
          <div className="grid grid-cols-5 gap-1.5">
            {MOOD_META.map(({ value, label, Icon }) => {
              const selected = mood === value;
              return (
                <button
                  key={value}
                  onClick={() => setMood(value)}
                  aria-pressed={selected}
                  className={`flex flex-col items-center gap-1 rounded-md border px-2 py-2 text-xs transition-colors ${
                    selected
                      ? 'border-[var(--color-accent)] bg-[var(--color-accent)]/10 text-[var(--color-accent)]'
                      : 'border-[var(--color-border)] text-[var(--color-text-secondary)] hover:text-[var(--color-text-primary)]'
                  }`}
                >
                  <Icon className="h-5 w-5" aria-hidden="true" />
                  {label}
                </button>
              );
            })}
          </div>
        </div>

        <div>
          <div className="mb-1.5 flex items-center justify-between">
            <span className="text-xs font-semibold uppercase tracking-wide text-[var(--color-text-secondary)]">
              Energy
            </span>
            <span className="font-mono text-xs text-[var(--color-text-primary)]">
              {energy}/5
            </span>
          </div>
          <input
            type="range"
            min={1}
            max={5}
            step={1}
            value={energy}
            onChange={(e) => setEnergy(Number(e.target.value))}
            className="w-full"
            aria-label="Energy level"
          />
          <div className="mt-0.5 flex justify-between text-[10px] text-[var(--color-text-secondary)]">
            <span>Drained</span>
            <span>Peak</span>
          </div>
        </div>

        <div>
          <span className="mb-1.5 block text-xs font-semibold uppercase tracking-wide text-[var(--color-text-secondary)]">
            Time of day
          </span>
          <div
            className="flex gap-1"
            role="radiogroup"
            aria-label="Time of day"
          >
            {TIME_OF_DAY_OPTIONS.map((opt) => (
              <button
                key={opt}
                role="radio"
                aria-checked={timeOfDay === opt}
                onClick={() => setTimeOfDay(opt)}
                className={`flex-1 rounded-md border px-2 py-1.5 text-xs capitalize transition-colors ${
                  timeOfDay === opt
                    ? 'border-[var(--color-accent)] bg-[var(--color-accent)]/10 text-[var(--color-accent)]'
                    : 'border-[var(--color-border)] text-[var(--color-text-secondary)] hover:text-[var(--color-text-primary)]'
                }`}
              >
                {opt}
              </button>
            ))}
          </div>
        </div>

        <label className="text-sm">
          <span className="mb-1 block text-xs font-semibold uppercase tracking-wide text-[var(--color-text-secondary)]">
            Notes (optional)
          </span>
          <textarea
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            rows={3}
            placeholder="What influenced how you feel?"
            className="w-full rounded-md border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-3 py-2 text-sm text-[var(--color-text-primary)] outline-none focus:border-[var(--color-accent)]"
          />
        </label>

        <div className="mt-2 flex justify-end gap-2">
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button onClick={handleSave} disabled={saving}>
            {saving && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
            Log
          </Button>
        </div>
      </div>
    </Modal>
  );
}
