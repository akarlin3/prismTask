import { create } from 'zustand';

export type FontScale = 0.9 | 1.0 | 1.1 | 1.25;

interface A11yState {
  fontScale: FontScale;
  highContrast: boolean;
  reducedMotion: boolean;
  setFontScale: (v: FontScale) => void;
  setHighContrast: (v: boolean) => void;
  setReducedMotion: (v: boolean) => void;
}

const STORAGE_KEY = 'prismtask_a11y';

function load(): Partial<A11yState> {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return {};
    return JSON.parse(raw) as Partial<A11yState>;
  } catch {
    return {};
  }
}

function persist(state: Partial<A11yState>) {
  try {
    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({
        fontScale: state.fontScale,
        highContrast: state.highContrast,
        reducedMotion: state.reducedMotion,
      }),
    );
  } catch {
    // non-fatal
  }
}

const stored = load();

/**
 * Client-only accessibility store driving three root-level overrides:
 *   - CSS custom property `--font-scale` (0.9 / 1.0 / 1.1 / 1.25)
 *   - `.a11y-high-contrast` class on <html>
 *   - `.a11y-reduced-motion` class on <html>
 *
 * Applied via `applyA11yToDocument` (below), called on mount + on any
 * state change from `AccessibilitySection`.
 */
export const useA11yStore = create<A11yState>((set, get) => ({
  fontScale: (stored.fontScale ?? 1.0) as FontScale,
  highContrast: stored.highContrast ?? false,
  reducedMotion: stored.reducedMotion ?? false,

  setFontScale: (v) => {
    set({ fontScale: v });
    persist({ ...get(), fontScale: v });
  },
  setHighContrast: (v) => {
    set({ highContrast: v });
    persist({ ...get(), highContrast: v });
  },
  setReducedMotion: (v) => {
    set({ reducedMotion: v });
    persist({ ...get(), reducedMotion: v });
  },
}));

export function applyA11yToDocument(
  state: Pick<A11yState, 'fontScale' | 'highContrast' | 'reducedMotion'>,
): void {
  if (typeof document === 'undefined') return;
  const root = document.documentElement;
  root.style.setProperty('--font-scale', String(state.fontScale));
  root.classList.toggle('a11y-high-contrast', state.highContrast);
  root.classList.toggle('a11y-reduced-motion', state.reducedMotion);
}
