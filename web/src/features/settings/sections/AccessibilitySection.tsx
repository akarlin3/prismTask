import { useEffect } from 'react';
import {
  applyA11yToDocument,
  useA11yStore,
  type FontScale,
} from '@/stores/a11yStore';

export function AccessibilitySection() {
  const state = useA11yStore();

  // Apply on mount and whenever any flag changes.
  useEffect(() => {
    applyA11yToDocument(state);
  }, [state]);

  return (
    <div className="flex flex-col gap-4">
      <div>
        <label className="mb-1 block text-sm font-medium text-[var(--color-text-primary)]">
          Font scale
        </label>
        <p className="mb-2 text-xs text-[var(--color-text-secondary)]">
          Scales text size app-wide. Takes effect immediately.
        </p>
        <div
          className="flex items-center gap-1 rounded-lg border border-[var(--color-border)] p-1"
          role="radiogroup"
          aria-label="Font scale"
        >
          {([0.9, 1.0, 1.1, 1.25] as FontScale[]).map((scale) => (
            <button
              key={scale}
              role="radio"
              aria-checked={state.fontScale === scale}
              onClick={() => state.setFontScale(scale)}
              className={`flex-1 rounded-md px-3 py-1.5 text-sm font-medium transition-colors ${
                state.fontScale === scale
                  ? 'bg-[var(--color-accent)]/10 text-[var(--color-accent)]'
                  : 'text-[var(--color-text-secondary)] hover:text-[var(--color-text-primary)]'
              }`}
            >
              {scale === 0.9
                ? 'Small'
                : scale === 1.0
                ? 'Default'
                : scale === 1.1
                ? 'Large'
                : 'X-Large'}
            </button>
          ))}
        </div>
      </div>

      <ToggleRow
        label="High contrast"
        description="Adds an explicit outline around interactive elements and bumps text weight."
        checked={state.highContrast}
        onChange={state.setHighContrast}
      />
      <ToggleRow
        label="Reduced motion"
        description="Disables non-essential animations. System-level reduced motion is already honored."
        checked={state.reducedMotion}
        onChange={state.setReducedMotion}
      />
    </div>
  );
}

function ToggleRow({
  label,
  description,
  checked,
  onChange,
}: {
  label: string;
  description: string;
  checked: boolean;
  onChange: (v: boolean) => void;
}) {
  return (
    <div className="flex items-start justify-between gap-3 py-2">
      <div>
        <p className="text-sm font-medium text-[var(--color-text-primary)]">
          {label}
        </p>
        <p className="text-xs text-[var(--color-text-secondary)]">
          {description}
        </p>
      </div>
      <button
        role="switch"
        aria-checked={checked}
        onClick={() => onChange(!checked)}
        className={`relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full transition-colors ${
          checked ? 'bg-[var(--color-accent)]' : 'bg-[var(--color-border)]'
        }`}
      >
        <span
          className={`inline-block h-5 w-5 transform rounded-full bg-white shadow transition-transform ${
            checked ? 'translate-x-[22px]' : 'translate-x-0.5'
          } mt-0.5`}
        />
      </button>
    </div>
  );
}
