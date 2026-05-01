import type { AmbiguousEntityHint } from '@/types/batch';
import type { MedicationCandidateOption } from '@/stores/batchStore';

/**
 * Inline radio-group picker for a single ambiguous medication phrase.
 * Mirrors `BatchPreviewScreen.kt`'s `DisambiguationPicker` Composable on
 * Android: same one-shot semantics (picking is permanent for that hint),
 * same row-level click target with role="radio".
 */
export function DisambiguationPicker({
  hint,
  candidates,
  onPick,
}: {
  hint: AmbiguousEntityHint;
  candidates: MedicationCandidateOption[];
  onPick: (entityId: string) => void;
}) {
  const groupName = `med-pick-${hint.phrase}`;
  return (
    <div
      role="radiogroup"
      aria-label={`Pick the medication you meant for "${hint.phrase}"`}
      className="mt-3 rounded-xl border border-[var(--color-border)] bg-[var(--color-bg-card)] p-4"
    >
      <p className="text-sm font-medium text-[var(--color-text-primary)]">
        Pick the medication you meant
      </p>
      <p className="mt-0.5 text-sm text-[var(--color-text-secondary)]">
        "{hint.phrase}"
      </p>
      <ul className="mt-3 space-y-1">
        {candidates.map((c) => (
          <li key={c.entity_id}>
            <label className="flex cursor-pointer items-center gap-3 rounded-lg p-2 hover:bg-[var(--color-bg-secondary)]">
              <input
                type="radio"
                name={groupName}
                value={c.entity_id}
                onChange={() => onPick(c.entity_id)}
                className="h-4 w-4 cursor-pointer"
              />
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium text-[var(--color-text-primary)]">
                  {c.name}
                </p>
                {c.display_label && c.display_label !== c.name ? (
                  <p className="truncate text-xs text-[var(--color-text-secondary)]">
                    {c.display_label}
                  </p>
                ) : null}
              </div>
            </label>
          </li>
        ))}
      </ul>
    </div>
  );
}
