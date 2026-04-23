import { create } from 'zustand';
import {
  applyThemeToDocument,
  DEFAULT_THEME_KEY,
  migrateLegacyAccentToThemeKey,
  THEMES,
  type ThemeKey,
} from '@/theme/themes';

const THEME_STORAGE_KEY = 'prismtask_theme_key';
const LEGACY_ACCENT_KEY = 'prismtask_accent_color';
const LEGACY_MODE_KEY = 'prismtask_theme';

function loadStoredTheme(): ThemeKey {
  try {
    const stored = localStorage.getItem(THEME_STORAGE_KEY);
    if (stored && stored in THEMES) return stored as ThemeKey;
  } catch {
    // private mode / quota — fall through to migration
  }

  // No explicit pick yet → migrate from the pre-parity light/dark + accent
  // picker. We run the migration once and then persist the result so the
  // user doesn't see a different theme on every reload.
  let migratedKey: ThemeKey = DEFAULT_THEME_KEY;
  try {
    const legacyAccent = localStorage.getItem(LEGACY_ACCENT_KEY);
    migratedKey = migrateLegacyAccentToThemeKey(legacyAccent);
    localStorage.setItem(THEME_STORAGE_KEY, migratedKey);
    // Drop the now-obsolete keys so the migration doesn't keep re-running.
    localStorage.removeItem(LEGACY_ACCENT_KEY);
    localStorage.removeItem(LEGACY_MODE_KEY);
  } catch {
    // Ignore; state still carries DEFAULT_THEME_KEY.
  }
  return migratedKey;
}

interface ThemeState {
  themeKey: ThemeKey;
  setThemeKey: (key: ThemeKey) => void;
  applyTheme: () => void;
}

/**
 * Post-parity theme store. Carries a single `themeKey` picked from the
 * four shipped themes (see `web/src/theme/themes.ts`). The legacy
 * `mode` (light/dark/system) + 12-accent picker was replaced because
 * Android treats each named theme as a self-contained visual system
 * — it has no light variants. Existing web users are auto-migrated on
 * first load via `migrateLegacyAccentToThemeKey`.
 */
export const useThemeStore = create<ThemeState>((set, get) => ({
  themeKey: loadStoredTheme(),

  setThemeKey: (key) => {
    try {
      localStorage.setItem(THEME_STORAGE_KEY, key);
    } catch {
      // non-fatal
    }
    set({ themeKey: key });
    get().applyTheme();
  },

  applyTheme: () => {
    applyThemeToDocument(get().themeKey);
  },
}));
