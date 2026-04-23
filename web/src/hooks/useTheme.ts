import { useEffect } from 'react';
import { useThemeStore } from '@/stores/themeStore';

/**
 * Apply the active theme's CSS custom properties on mount and whenever
 * the user changes theme. The four named themes are dark-first with no
 * system/light variant (matches Android), so no media-query listener
 * is needed.
 */
export function useTheme() {
  const store = useThemeStore();
  const themeKey = store.themeKey;
  const applyTheme = store.applyTheme;

  useEffect(() => {
    applyTheme();
  }, [themeKey, applyTheme]);

  return store;
}
