import { useParams } from 'react-router-dom';
import { FileText } from 'lucide-react';

export function TaskDetailScreen() {
  const { id } = useParams<{ id: string }>();

  return (
    <div className="mx-auto max-w-3xl">
      <div className="flex items-center gap-3 mb-6">
        <FileText className="h-7 w-7 text-[var(--color-accent)]" />
        <h1 className="text-2xl font-bold text-[var(--color-text-primary)]">
          Task #{id}
        </h1>
      </div>
      <div className="rounded-xl border border-[var(--color-border)] bg-[var(--color-bg-card)] p-8 text-center">
        <p className="text-[var(--color-text-secondary)]">
          Task detail/editor with tabbed interface — coming in Phase 1.
        </p>
      </div>
    </div>
  );
}
