import { useCallback, useEffect, useMemo, useState } from 'react';
import { addDays, format, parseISO } from 'date-fns';
import {
  ChevronLeft,
  ChevronRight,
  Pill,
  PlusCircle,
  Undo2,
} from 'lucide-react';
import { toast } from 'sonner';
import { dailyEssentialsApi } from '@/api/dailyEssentials';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { MedicationSlotDetailModal } from '@/features/daily-essentials/MedicationSlotDetailModal';
import { useSettingsStore } from '@/stores/settingsStore';
import { logicalToday } from '@/utils/dayBoundary';
import type {
  MedicationSlot,
  MedicationSlotCompletion,
} from '@/types/dailyEssentials';

const ANYTIME_KEY = 'anytime';

function displayTimeFor(slotKey: string): string {
  if (slotKey === ANYTIME_KEY) return 'Anytime';
  return slotKey;
}

function prettifyMedId(key: string): string {
  const idx = key.indexOf(':');
  const raw = idx >= 0 ? key.slice(idx + 1) : key;
  return raw
    .split(/[_\s]+/)
    .map((w) => (w ? w[0].toUpperCase() + w.slice(1) : w))
    .join(' ');
}

function rowToSlot(row: MedicationSlotCompletion): MedicationSlot {
  return {
    slotKey: row.slot_key,
    displayTime: displayTimeFor(row.slot_key),
    medLabels: row.med_ids.map(prettifyMedId),
    medIds: row.med_ids,
    takenAt: row.taken_at,
  };
}

/**
 * Dedicated Medication screen. Scoped to the same slot-toggle data path
 * the Today MedicationSlotList already uses — no tier picker, no slot
 * CRUD (both require backend work on top of the current
 * `/daily-essentials/slots` surface).
 *
 * What this screen adds over the Today row:
 *   - Per-day navigation (prev / today / next)
 *   - Full-card layout per slot instead of the compact row
 *   - Summary header (N slots, M taken)
 *   - Shared DayBoundary-aware "today" default so late-night users
 *     see the correct logical day.
 */
export function MedicationScreen() {
  const startOfDayHour = useSettingsStore((s) => s.startOfDayHour);
  const todayIso = logicalToday(Date.now(), startOfDayHour);

  const [dateIso, setDateIso] = useState(todayIso);
  const [rows, setRows] = useState<MedicationSlotCompletion[]>([]);
  const [loading, setLoading] = useState(false);
  const [openSlot, setOpenSlot] = useState<MedicationSlot | null>(null);

  const load = useCallback(
    async (iso: string) => {
      setLoading(true);
      try {
        const fetched = await dailyEssentialsApi.listSlots(iso);
        setRows(fetched);
      } catch (e) {
        toast.error((e as Error).message || 'Failed to load slots');
      } finally {
        setLoading(false);
      }
    },
    [],
  );

  useEffect(() => {
    load(dateIso);
  }, [dateIso, load]);

  const slots = useMemo(() => rows.map(rowToSlot), [rows]);
  const takenCount = slots.filter((s) => s.takenAt !== null).length;

  const handleToggle = async (slot: MedicationSlot, taken: boolean) => {
    try {
      const updated = await dailyEssentialsApi.toggleSlot({
        date: dateIso,
        slot_key: slot.slotKey,
        med_ids: slot.medIds,
        taken,
      });
      setRows((prev) => {
        const next = prev.filter((r) => r.slot_key !== updated.slot_key);
        return [...next, updated];
      });
      setOpenSlot(null);
    } catch {
      toast.error('Failed to update slot');
    }
  };

  const shift = (days: number) => {
    const next = format(addDays(parseISO(dateIso), days), 'yyyy-MM-dd');
    setDateIso(next);
  };

  return (
    <div className="mx-auto max-w-3xl pb-16">
      <header className="mb-6 flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <Pill
            className="h-5 w-5 text-[var(--color-accent)]"
            aria-hidden="true"
          />
          <div>
            <h1 className="text-xl font-semibold text-[var(--color-text-primary)]">
              Medications
            </h1>
            <p className="text-sm text-[var(--color-text-secondary)]">
              {format(parseISO(dateIso), 'EEEE, MMMM d')}
              {dateIso !== todayIso && ' · ' + (dateIso < todayIso ? 'Past' : 'Future')}
            </p>
          </div>
        </div>
        <div className="flex items-center gap-1.5">
          <Button variant="ghost" size="sm" onClick={() => shift(-1)} aria-label="Previous day">
            <ChevronLeft className="h-4 w-4" />
          </Button>
          {dateIso !== todayIso && (
            <Button
              variant="secondary"
              size="sm"
              onClick={() => setDateIso(todayIso)}
            >
              <Undo2 className="mr-1 h-3.5 w-3.5" />
              Today
            </Button>
          )}
          <Button variant="ghost" size="sm" onClick={() => shift(1)} aria-label="Next day">
            <ChevronRight className="h-4 w-4" />
          </Button>
        </div>
      </header>

      {slots.length > 0 && (
        <p className="mb-4 text-xs text-[var(--color-text-secondary)]">
          {takenCount} of {slots.length} slot{slots.length === 1 ? '' : 's'} taken
        </p>
      )}

      {loading && slots.length === 0 ? (
        <p className="py-8 text-center text-sm text-[var(--color-text-secondary)]">
          Loading…
        </p>
      ) : slots.length === 0 ? (
        <EmptyState
          icon={<PlusCircle className="h-8 w-8" />}
          title="No medication slots"
          description="Set up your medication schedule on Android — slot config on web is coming later in Phase G."
        />
      ) : (
        <ul className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          {[...slots]
            .sort((a, b) => {
              if (a.slotKey === ANYTIME_KEY) return 1;
              if (b.slotKey === ANYTIME_KEY) return -1;
              return a.slotKey.localeCompare(b.slotKey);
            })
            .map((slot) => {
              const taken = slot.takenAt !== null;
              return (
                <li
                  key={slot.slotKey}
                  className={`flex flex-col gap-2 rounded-xl border p-4 transition-colors ${
                    taken
                      ? 'border-emerald-500/40 bg-emerald-500/5'
                      : 'border-[var(--color-border)] bg-[var(--color-bg-card)]'
                  }`}
                >
                  <div className="flex items-center justify-between">
                    <span className="text-sm font-semibold text-[var(--color-text-primary)]">
                      {slot.displayTime}
                    </span>
                    <span
                      className={`rounded-full px-2 py-0.5 text-[10px] font-medium uppercase tracking-wide ${
                        taken
                          ? 'bg-emerald-500 text-white'
                          : 'bg-[var(--color-bg-secondary)] text-[var(--color-text-secondary)]'
                      }`}
                    >
                      {taken ? 'Taken' : 'Pending'}
                    </span>
                  </div>
                  <p className="text-xs text-[var(--color-text-secondary)]">
                    {slot.medLabels.length === 0
                      ? 'No medications linked'
                      : slot.medLabels.join(', ')}
                  </p>
                  <div className="flex justify-end gap-1.5">
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => setOpenSlot(slot)}
                    >
                      Details
                    </Button>
                    <Button
                      variant={taken ? 'secondary' : 'primary'}
                      size="sm"
                      onClick={() => handleToggle(slot, !taken)}
                    >
                      {taken ? 'Mark Not Taken' : 'Mark Taken'}
                    </Button>
                  </div>
                </li>
              );
            })}
        </ul>
      )}

      {openSlot && (
        <MedicationSlotDetailModal
          slot={openSlot}
          onClose={() => setOpenSlot(null)}
          onToggleSlot={(taken) => handleToggle(openSlot, taken)}
        />
      )}
    </div>
  );
}
