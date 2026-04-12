import { LayoutGrid } from 'lucide-react';

export function EisenhowerScreen() {
  return (
    <div className="mx-auto max-w-6xl">
      <div className="flex items-center gap-3 mb-6">
        <LayoutGrid className="h-7 w-7 text-[var(--color-accent)]" />
        <h1 className="text-2xl font-bold text-[var(--color-text-primary)]">
          Eisenhower Matrix
        </h1>
      </div>
      <div className="rounded-xl border border-[var(--color-border)] bg-[var(--color-bg-card)] p-8 text-center">
        <p className="text-[var(--color-text-secondary)]">
          Four-quadrant task prioritization matrix — coming in Phase 3.
        </p>
      </div>
    </div>
  );
}
