import { Modal } from '@/components/ui/Modal';
import { Checkbox } from '@/components/ui/Checkbox';
import { Button } from '@/components/ui/Button';
import type { MedicationSlot } from '@/types/dailyEssentials';

interface MedicationSlotDetailModalProps {
  slot: MedicationSlot;
  onClose: () => void;
  onToggleSlot: (taken: boolean) => void;
}

/**
 * Detail modal for a materialized medication slot. The backend only knows
 * about slot-level ``taken_at``, so the individual med checkboxes here
 * mirror the slot-level state until mobile syncs more granular data.
 */
export function MedicationSlotDetailModal({
  slot,
  onClose,
  onToggleSlot,
}: MedicationSlotDetailModalProps) {
  const slotTaken = slot.takenAt !== null;

  return (
    <Modal
      isOpen
      onClose={onClose}
      title={`${slot.displayTime} Meds`}
      size="sm"
      footer={
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose}>
            Close
          </Button>
          <Button onClick={() => onToggleSlot(!slotTaken)}>
            {slotTaken ? 'Mark All Not Taken' : 'Mark All Taken'}
          </Button>
        </div>
      }
    >
      <div className="flex flex-col gap-3">
        <p className="text-sm text-[color:var(--color-text-muted)]">
          {slot.medLabels.length === 1
            ? '1 medication scheduled for this slot.'
            : `${slot.medLabels.length} medications scheduled for this slot.`}
        </p>
        <ul className="flex flex-col gap-2">
          {slot.medLabels.map((label, idx) => (
            <li
              key={`${slot.slotKey}-${idx}`}
              className="flex items-center justify-between rounded-md border border-[color:var(--color-border)] px-3 py-2"
            >
              <span className="text-sm">{label}</span>
              <Checkbox
                checked={slotTaken}
                onChange={() => onToggleSlot(!slotTaken)}
              />
            </li>
          ))}
        </ul>
      </div>
    </Modal>
  );
}
