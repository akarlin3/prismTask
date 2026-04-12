import { Calendar } from 'lucide-react';

export function WeekViewScreen() {
  return (
    <div className="mx-auto max-w-6xl">
      <div className="flex items-center gap-3 mb-6">
        <Calendar className="h-7 w-7 text-[var(--color-accent)]" />
        <h1 className="text-2xl font-bold text-[var(--color-text-primary)]">
          Week View
        </h1>
      </div>
      <div className="rounded-xl border border-[var(--color-border)] bg-[var(--color-bg-card)] p-8 text-center">
        <p className="text-[var(--color-text-secondary)]">
          Weekly calendar with time blocks — coming in Phase 4.
        </p>
      </div>
    </div>
  );
}
