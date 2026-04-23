import { Check, CircleDashed, CircleSlash, CircleOff } from 'lucide-react';
import type { MedicationTier } from '@/api/firestore/medicationSlots';

const TIER_ORDER: MedicationTier[] = ['SKIPPED', 'PARTIAL', 'COMPLETE'];

const TIER_META: Record<
  MedicationTier,
  { label: string; className: string; Icon: typeof Check }
> = {
  SKIPPED: {
    label: 'Skipped',
    className: 'text-red-500 border-red-500/40',
    Icon: CircleSlash,
  },
  PARTIAL: {
    label: 'Partial',
    className: 'text-amber-500 border-amber-500/40',
    Icon: CircleDashed,
  },
  COMPLETE: {
    label: 'Complete',
    className: 'text-emerald-500 border-emerald-500/40',
    Icon: Check,
  },
};

/**
 * Three-way tier picker (SKIPPED / PARTIAL / COMPLETE) for a
 * medication slot on a given date. `userSet` tells the caller the
 * current value came from a manual pick rather than an auto-derived
 * tier so it can render "Override" vs "Auto" affordance.
 */
export function MedicationTierPicker({
  value,
  isUserSet,
  onChange,
  onClear,
}: {
  value: MedicationTier | null;
  isUserSet: boolean;
  onChange: (tier: MedicationTier) => void;
  onClear?: () => void;
}) {
  return (
    <div className="flex items-center gap-1.5">
      {TIER_ORDER.map((tier) => {
        const selected = value === tier;
        const meta = TIER_META[tier];
        return (
          <button
            key={tier}
            onClick={() => onChange(tier)}
            aria-pressed={selected}
            title={`Mark ${meta.label}`}
            className={`flex items-center gap-1 rounded-md border px-2 py-0.5 text-xs font-medium transition-colors ${
              selected
                ? `bg-[var(--color-bg-card)] ${meta.className}`
                : 'border-[var(--color-border)] text-[var(--color-text-secondary)] hover:text-[var(--color-text-primary)]'
            }`}
          >
            <meta.Icon className="h-3 w-3" aria-hidden="true" />
            {meta.label}
          </button>
        );
      })}
      {isUserSet && onClear && (
        <button
          onClick={onClear}
          title="Clear override and return to auto-computed tier"
          className="ml-1 flex items-center gap-1 rounded-md border border-[var(--color-border)] px-2 py-0.5 text-xs text-[var(--color-text-secondary)] hover:text-[var(--color-text-primary)]"
        >
          <CircleOff className="h-3 w-3" aria-hidden="true" />
          Auto
        </button>
      )}
    </div>
  );
}
