import { Activity } from 'lucide-react';

export function HabitListScreen() {
  return (
    <div className="mx-auto max-w-4xl">
      <div className="flex items-center gap-3 mb-6">
        <Activity className="h-7 w-7 text-[var(--color-accent)]" />
        <h1 className="text-2xl font-bold text-[var(--color-text-primary)]">
          Habits
        </h1>
      </div>
      <div className="rounded-xl border border-[var(--color-border)] bg-[var(--color-bg-card)] p-8 text-center">
        <p className="text-[var(--color-text-secondary)]">
          Habit tracker with streaks and completions — coming in Phase 3.
        </p>
      </div>
    </div>
  );
}
