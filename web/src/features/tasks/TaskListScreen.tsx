import { CheckSquare } from 'lucide-react';

export function TaskListScreen() {
  return (
    <div className="mx-auto max-w-4xl">
      <div className="flex items-center gap-3 mb-6">
        <CheckSquare className="h-7 w-7 text-[var(--color-accent)]" />
        <h1 className="text-2xl font-bold text-[var(--color-text-primary)]">
          All Tasks
        </h1>
      </div>
      <div className="rounded-xl border border-[var(--color-border)] bg-[var(--color-bg-card)] p-8 text-center">
        <p className="text-[var(--color-text-secondary)]">
          Task list with filters, sort, and bulk actions — coming in Phase 1.
        </p>
      </div>
    </div>
  );
}
