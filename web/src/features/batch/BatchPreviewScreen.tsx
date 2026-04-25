import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import {
  AlertTriangle,
  ArrowLeft,
  Calendar,
  Check,
  Flag,
  FolderInput,
  Loader2,
  Sparkles,
  Trash2,
  TriangleAlert,
} from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { useBatchStore } from '@/stores/batchStore';
import type { BatchMutationType, ProposedMutation } from '@/types/batch';

const UNDO_TOAST_MS = 30_000;

const MUTATION_LABELS: Record<BatchMutationType, string> = {
  RESCHEDULE: 'Reschedule',
  DELETE: 'Delete',
  COMPLETE: 'Complete',
  SKIP: 'Skip',
  PRIORITY_CHANGE: 'Change priority',
  TAG_CHANGE: 'Change tags',
  PROJECT_MOVE: 'Move project',
  ARCHIVE: 'Archive',
  STATE_CHANGE: 'Change tier',
};

function mutationIcon(type: BatchMutationType) {
  switch (type) {
    case 'RESCHEDULE':
      return <Calendar className="h-4 w-4 text-amber-500" />;
    case 'DELETE':
    case 'ARCHIVE':
      return <Trash2 className="h-4 w-4 text-red-500" />;
    case 'COMPLETE':
    case 'SKIP':
      return <Check className="h-4 w-4 text-emerald-500" />;
    case 'PRIORITY_CHANGE':
      return <Flag className="h-4 w-4 text-blue-500" />;
    case 'PROJECT_MOVE':
      return <FolderInput className="h-4 w-4 text-indigo-500" />;
    case 'TAG_CHANGE':
      return <Sparkles className="h-4 w-4 text-purple-500" />;
    case 'STATE_CHANGE':
      return <Sparkles className="h-4 w-4 text-amber-700" />;
  }
}

export function BatchPreviewScreen() {
  const navigate = useNavigate();
  const pendingCommand = useBatchStore((s) => s.pendingCommand);
  const pendingResponse = useBatchStore((s) => s.pendingResponse);
  const isParsing = useBatchStore((s) => s.isParsing);
  const parseError = useBatchStore((s) => s.parseError);
  const parsePendingCommand = useBatchStore((s) => s.parsePendingCommand);
  const clearPending = useBatchStore((s) => s.clearPending);
  const commit = useBatchStore((s) => s.commit);
  const undo = useBatchStore((s) => s.undo);

  const [excluded, setExcluded] = useState<Set<number>>(new Set());
  const [committing, setCommitting] = useState(false);

  useEffect(() => {
    if (!pendingCommand) {
      navigate('/', { replace: true });
      return;
    }
    if (!pendingResponse && !isParsing && !parseError) {
      parsePendingCommand();
    }
  }, [
    pendingCommand,
    pendingResponse,
    isParsing,
    parseError,
    parsePendingCommand,
    navigate,
  ]);

  const mutations = useMemo(
    () => pendingResponse?.mutations ?? [],
    [pendingResponse],
  );
  const selected = useMemo(
    () => mutations.filter((_, idx) => !excluded.has(idx)),
    [mutations, excluded],
  );

  const toggleExcluded = (idx: number) => {
    setExcluded((prev) => {
      const next = new Set(prev);
      if (next.has(idx)) next.delete(idx);
      else next.add(idx);
      return next;
    });
  };

  const handleCancel = () => {
    clearPending();
    navigate('/', { replace: true });
  };

  const handleApprove = async () => {
    if (!pendingCommand || selected.length === 0) return;
    setCommitting(true);
    try {
      const record = await commit(pendingCommand, selected as ProposedMutation[]);
      clearPending();

      const description = record.applied_count === 0
        ? 'No changes applied'
        : `${record.applied_count} change${record.applied_count === 1 ? '' : 's'} applied`;
      const skippedSuffix = record.skipped_count > 0
        ? ` (${record.skipped_count} skipped)`
        : '';

      if (record.applied_count > 0) {
        toast.success(`${description}${skippedSuffix}`, {
          duration: UNDO_TOAST_MS,
          action: {
            label: 'Undo',
            onClick: () => undo(record.batch_id),
          },
        });
      } else {
        toast.error(`${description}${skippedSuffix}`);
      }
      navigate('/', { replace: true });
    } catch (e) {
      toast.error((e as Error).message || 'Failed to apply batch');
    } finally {
      setCommitting(false);
    }
  };

  if (!pendingCommand) return null;

  return (
    <div className="mx-auto max-w-3xl pb-16">
      <header className="mb-6 flex items-center gap-3">
        <Button variant="ghost" size="sm" onClick={handleCancel} aria-label="Back">
          <ArrowLeft className="h-4 w-4" />
        </Button>
        <div>
          <h1 className="text-xl font-semibold text-[var(--color-text-primary)]">
            Batch Preview
          </h1>
          <p className="mt-0.5 text-sm text-[var(--color-text-secondary)]">
            {pendingCommand}
          </p>
        </div>
      </header>

      {isParsing && (
        <div className="flex items-center gap-3 rounded-xl border border-[var(--color-border)] bg-[var(--color-bg-card)] p-6">
          <Loader2 className="h-5 w-5 animate-spin text-[var(--color-accent)]" />
          <span className="text-sm text-[var(--color-text-primary)]">
            Thinking through your command…
          </span>
        </div>
      )}

      {parseError && !isParsing && (
        <div className="flex items-start gap-3 rounded-xl border border-red-500/40 bg-red-500/5 p-4">
          <TriangleAlert className="mt-0.5 h-5 w-5 shrink-0 text-red-500" />
          <div className="flex-1">
            <p className="text-sm font-medium text-red-500">Couldn't parse command</p>
            <p className="mt-1 text-sm text-[var(--color-text-secondary)]">{parseError}</p>
          </div>
          <Button variant="ghost" size="sm" onClick={() => parsePendingCommand()}>
            Retry
          </Button>
        </div>
      )}

      {pendingResponse && !isParsing && !parseError && (
        <>
          {pendingResponse.ambiguous_entities.length > 0 && (
            <div
              className="mb-4 flex items-start gap-3 rounded-xl border border-amber-500/40 bg-amber-500/5 p-4"
              role="alert"
            >
              <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0 text-amber-500" />
              <div className="flex-1">
                <p className="text-sm font-medium text-amber-500">
                  Some references are ambiguous
                </p>
                <ul className="mt-1 space-y-0.5 text-sm text-[var(--color-text-secondary)]">
                  {pendingResponse.ambiguous_entities.map((hint, idx) => (
                    <li key={idx}>
                      <span className="font-medium">"{hint.phrase}"</span>
                      {hint.note ? ` — ${hint.note}` : ''}
                    </li>
                  ))}
                </ul>
              </div>
            </div>
          )}

          {mutations.length === 0 ? (
            <EmptyState
              title="No matching changes"
              description="The AI couldn't identify any mutations for this command. Try rephrasing — for example, 'reschedule all overdue tasks to tomorrow'."
            />
          ) : (
            <>
              <div className="mb-3 flex items-center justify-between text-sm text-[var(--color-text-secondary)]">
                <span>
                  {selected.length} of {mutations.length} change
                  {mutations.length === 1 ? '' : 's'} selected
                </span>
                <span>
                  Confidence: {Math.round((pendingResponse.confidence ?? 0) * 100)}%
                </span>
              </div>

              <ul className="space-y-2">
                {mutations.map((m, idx) => {
                  const isExcluded = excluded.has(idx);
                  return (
                    <li
                      key={`${m.entity_type}-${m.entity_id}-${idx}`}
                      className={`flex items-start gap-3 rounded-xl border p-4 transition ${
                        isExcluded
                          ? 'border-dashed border-[var(--color-border)] bg-transparent opacity-60'
                          : 'border-[var(--color-border)] bg-[var(--color-bg-card)]'
                      }`}
                    >
                      <div className="mt-0.5">{mutationIcon(m.mutation_type)}</div>
                      <div className="min-w-0 flex-1">
                        <div className="flex flex-wrap items-center gap-1.5 text-xs text-[var(--color-text-secondary)]">
                          <span className="rounded bg-[var(--color-bg-secondary)] px-1.5 py-0.5 font-medium uppercase tracking-wide">
                            {m.entity_type}
                          </span>
                          <span>•</span>
                          <span>{MUTATION_LABELS[m.mutation_type] ?? m.mutation_type}</span>
                        </div>
                        <p className="mt-1 text-sm text-[var(--color-text-primary)]">
                          {m.human_readable_description}
                        </p>
                      </div>
                      <label className="flex shrink-0 cursor-pointer items-center gap-2 text-sm text-[var(--color-text-secondary)]">
                        <input
                          type="checkbox"
                          checked={!isExcluded}
                          onChange={() => toggleExcluded(idx)}
                          className="h-4 w-4 cursor-pointer rounded border-[var(--color-border)] text-[var(--color-accent)]"
                          aria-label={isExcluded ? 'Include this change' : 'Exclude this change'}
                        />
                        <span>{isExcluded ? 'Skip' : 'Apply'}</span>
                      </label>
                    </li>
                  );
                })}
              </ul>

              <div className="mt-6 flex justify-end gap-2">
                <Button variant="ghost" onClick={handleCancel}>
                  Cancel
                </Button>
                <Button
                  onClick={handleApprove}
                  disabled={selected.length === 0 || committing}
                >
                  {committing ? (
                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  ) : null}
                  Apply {selected.length} change{selected.length === 1 ? '' : 's'}
                </Button>
              </div>
            </>
          )}
        </>
      )}
    </div>
  );
}
