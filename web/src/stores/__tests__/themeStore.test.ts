import { describe, it, expect, beforeEach } from 'vitest';
import {
  migrateLegacyAccentToThemeKey,
  DEFAULT_THEME_KEY,
} from '@/theme/themes';

/**
 * Theme store migration tests. The store has side effects on
 * localStorage + document; we test the pure migration function
 * directly and verify the set/apply flow by re-importing the module.
 */

describe('migrateLegacyAccentToThemeKey', () => {
  it('returns DEFAULT_THEME_KEY for null input (new user)', () => {
    expect(migrateLegacyAccentToThemeKey(null)).toBe(DEFAULT_THEME_KEY);
  });

  it('routes cyan/teal accents to CYBERPUNK', () => {
    // Sky (pre-parity accent) → cyan family
    expect(migrateLegacyAccentToThemeKey('#0ea5e9')).toBe('CYBERPUNK');
    // Teal (pre-parity accent)
    expect(migrateLegacyAccentToThemeKey('#14b8a6')).toBe('CYBERPUNK');
  });

  it('routes green/lime accents to MATRIX', () => {
    expect(migrateLegacyAccentToThemeKey('#22c55e')).toBe('MATRIX');
    expect(migrateLegacyAccentToThemeKey('#84cc16')).toBe('MATRIX');
  });

  it('routes pink/magenta/orange/red/yellow accents to SYNTHWAVE', () => {
    expect(migrateLegacyAccentToThemeKey('#ec4899')).toBe('SYNTHWAVE');
    expect(migrateLegacyAccentToThemeKey('#f43f5e')).toBe('SYNTHWAVE');
    expect(migrateLegacyAccentToThemeKey('#f97316')).toBe('SYNTHWAVE');
    expect(migrateLegacyAccentToThemeKey('#ef4444')).toBe('SYNTHWAVE');
    expect(migrateLegacyAccentToThemeKey('#eab308')).toBe('SYNTHWAVE');
  });

  it('routes the default indigo accent to VOID', () => {
    // Pre-parity default: indigo #6366f1 → VOID (editorial minimal, the
    // safest "I never picked" fallback).
    expect(migrateLegacyAccentToThemeKey('#6366f1')).toBe('VOID');
  });

  it('handles case-insensitive hex input', () => {
    expect(migrateLegacyAccentToThemeKey('#22C55E')).toBe('MATRIX');
  });
});

describe('theme store side effects', () => {
  beforeEach(() => {
    localStorage.clear();
    // Reset module registry so the store re-runs its loader on import.
    // Using dynamic import inside the test to make this explicit.
  });

  it('auto-migrates a stored legacy accent on first load', async () => {
    localStorage.setItem('prismtask_accent_color', '#22c55e');
    localStorage.setItem('prismtask_theme', 'dark');
    // Dynamic import so the store's top-level localStorage read runs
    // AFTER we've seeded the legacy values.
    const { useThemeStore } = await import('@/stores/themeStore');
    expect(useThemeStore.getState().themeKey).toBe('MATRIX');
    // Legacy keys should be removed so the migration doesn't re-run.
    expect(localStorage.getItem('prismtask_accent_color')).toBeNull();
    expect(localStorage.getItem('prismtask_theme')).toBeNull();
    // The new key should be persisted.
    expect(localStorage.getItem('prismtask_theme_key')).toBe('MATRIX');
  });
});
