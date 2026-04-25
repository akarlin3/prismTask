import { useState } from 'react';
import { Button } from '@/components/ui/Button';
import { Modal } from '@/components/ui/Modal';
import { epochToHHMM } from '@/features/medication/backloggedHelpers';

/**
 * Modal that lets the user stamp an `intended_time` on a slot's
 * tier-state — parity with Android's `MedicationTimeEditSheet`. Opened
 * via right-click (desktop) or touch-and-hold (mobile) on the tier
 * picker, both wired in `MedicationScreen.tsx`.
 *
 * The HTML5 `<input type="time">` works on every modern browser and
 * touch device without an extra date-picker dependency. We compose the
 * picked HH:mm with today's date in local time before saving — same
 * approach as Android's `Calendar.getInstance().apply { ... }`.
 *
 * Future times are capped to "now" by the underlying
 * {@link setTierStateIntendedTime} — backdating only.
 */
export function MedicationTimeEditModal({
  isOpen,
  slotKey,
  slotLabel,
  initialIntendedTime,
  dateIso,
  onClose,
  onSave,
}: {
  isOpen: boolean;
  slotKey: string;
  slotLabel: string;
  initialIntendedTime: number | null;
  dateIso: string;
  onClose: () => void;
  onSave: (intendedTime: number) => void;
}) {
  // The modal mounts fresh whenever the parent toggles `timeEditingSlot`
  // — useState's lazy initializer runs once per mount, so the seed is
  // always current. No effect / re-sync needed.
  const [timeStr, setTimeStr] = useState<string>(() =>
    epochToHHMM(initialIntendedTime ?? Date.now()),
  );

  const handleSave = () => {
    const [hh, mm] = timeStr.split(':').map((s) => parseInt(s, 10));
    if (Number.isNaN(hh) || Number.isNaN(mm)) {
      onClose();
      return;
    }
    // Compose against the slot's date (not always today — supports
    // editing past days from the date-shifted view) in local time.
    // The doc id is `${dateIso}__${slotKey}` regardless of clock, so
    // saving "yesterday at 8 AM" stamps yesterday's row correctly.
    const composed = new Date(dateIso + 'T00:00:00');
    composed.setHours(hh, mm, 0, 0);
    onSave(composed.getTime());
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={`When did you take ${slotLabel}?`}
      size="sm"
      footer={
        <div className="flex justify-end gap-2">
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button onClick={handleSave}>Save</Button>
        </div>
      }
    >
      <div className="flex flex-col gap-3">
        <p className="text-sm text-[var(--color-text-secondary)]">
          Backlog the time you actually took this dose. Future times are
          capped to now.
        </p>
        <label
          htmlFor={`time-edit-${slotKey}`}
          className="text-xs font-medium uppercase tracking-wide text-[var(--color-text-secondary)]"
        >
          Time taken
        </label>
        <input
          id={`time-edit-${slotKey}`}
          type="time"
          value={timeStr}
          onChange={(e) => setTimeStr(e.target.value)}
          className="rounded-md border border-[var(--color-border)] bg-[var(--color-bg-card)] px-3 py-2 text-base text-[var(--color-text-primary)] focus:border-[var(--color-accent)] focus:outline-none"
        />
      </div>
    </Modal>
  );
}

