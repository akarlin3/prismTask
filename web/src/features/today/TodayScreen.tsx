import { Sun } from 'lucide-react';

export function TodayScreen() {
  return (
    <div className="mx-auto max-w-3xl">
      <div className="flex items-center gap-3 mb-6">
        <Sun className="h-7 w-7 text-[var(--color-accent)]" />
        <h1 className="text-2xl font-bold text-[var(--color-text-primary)]">
          Today
        </h1>
      </div>
      <div className="rounded-xl border border-[var(--color-border)] bg-[var(--color-bg-card)] p-8 text-center">
        <p className="text-[var(--color-text-secondary)]">
          Today view with overdue, today, and planned sections — coming in Phase 1.
        </p>
      </div>
    </div>
  );
}
