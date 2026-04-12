import { useParams } from 'react-router-dom';
import { BarChart3 } from 'lucide-react';

export function HabitAnalyticsScreen() {
  const { id } = useParams<{ id: string }>();

  return (
    <div className="mx-auto max-w-4xl">
      <div className="flex items-center gap-3 mb-6">
        <BarChart3 className="h-7 w-7 text-[var(--color-accent)]" />
        <h1 className="text-2xl font-bold text-[var(--color-text-primary)]">
          Habit Analytics #{id}
        </h1>
      </div>
      <div className="rounded-xl border border-[var(--color-border)] bg-[var(--color-bg-card)] p-8 text-center">
        <p className="text-[var(--color-text-secondary)]">
          Habit analytics with charts and contribution grid — coming in Phase 3.
        </p>
      </div>
    </div>
  );
}
