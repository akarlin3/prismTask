import { useParams } from 'react-router-dom';
import { FolderKanban } from 'lucide-react';

export function ProjectDetailScreen() {
  const { id } = useParams<{ id: string }>();

  return (
    <div className="mx-auto max-w-4xl">
      <div className="flex items-center gap-3 mb-6">
        <FolderKanban className="h-7 w-7 text-[var(--color-accent)]" />
        <h1 className="text-2xl font-bold text-[var(--color-text-primary)]">
          Project #{id}
        </h1>
      </div>
      <div className="rounded-xl border border-[var(--color-border)] bg-[var(--color-bg-card)] p-8 text-center">
        <p className="text-[var(--color-text-secondary)]">
          Project detail with tasks list — coming in Phase 2.
        </p>
      </div>
    </div>
  );
}
