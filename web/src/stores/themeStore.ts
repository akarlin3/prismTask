import { create } from 'zustand';

export type ThemeMode = 'light' | 'dark' | 'system';

export const ACCENT_COLORS = [
  { name: 'Indigo', value: '#6366f1' },
  { name: 'Blue', value: '#3b82f6' },
  { name: 'Sky', value: '#0ea5e9' },
  { name: 'Teal', value: '#14b8a6' },
  { name: 'Green', value: '#22c55e' },
  { name: 'Lime', value: '#84cc16' },
  { name: 'Yellow', value: '#eab308' },
  { name: 'Orange', value: '#f97316' },
  { name: 'Red', value: '#ef4444' },
  { name: 'Pink', value: '#ec4899' },
  { name: 'Purple', value: '#a855f7' },
  { name: 'Rose', value: '#f43f5e' },
] as const;

interface ThemeState {
  mode: ThemeMode;
  accentColor: string;

  setMode: (mode: ThemeMode) => void;
  setAccentColor: (color: string) => void;
  getEffectiveMode: () => 'light' | 'dark';
  applyTheme: () => void;
}

function getSystemTheme(): 'light' | 'dark' {
  if (typeof window === 'undefined') return 'light';
  return window.matchMedia('(prefers-color-scheme: dark)').matches
    ? 'dark'
    : 'light';
}

export const useThemeStore = create<ThemeState>((set, get) => ({
  mode: (localStorage.getItem('prismtask_theme') as ThemeMode) || 'system',
  accentColor:
    localStorage.getItem('prismtask_accent_color') || '#6366f1',

  setMode: (mode) => {
    localStorage.setItem('prismtask_theme', mode);
    set({ mode });
    get().applyTheme();
  },

  setAccentColor: (color) => {
    localStorage.setItem('prismtask_accent_color', color);
    set({ accentColor: color });
    get().applyTheme();
  },

  getEffectiveMode: () => {
    const { mode } = get();
    return mode === 'system' ? getSystemTheme() : mode;
  },

  applyTheme: () => {
    const effective = get().getEffectiveMode();
    const { accentColor } = get();
    const root = document.documentElement;

    if (effective === 'dark') {
      root.classList.add('dark');
    } else {
      root.classList.remove('dark');
    }

    root.style.setProperty('--color-accent', accentColor);
  },
}));
