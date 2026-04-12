import { Settings } from 'lucide-react';
import { useThemeStore, ACCENT_COLORS } from '@/stores/themeStore';
import type { ThemeMode } from '@/stores/themeStore';

export function SettingsScreen() {
  const { mode, accentColor, setMode, setAccentColor } = useThemeStore();

  return (
    <div className="mx-auto max-w-2xl">
      <div className="flex items-center gap-3 mb-6">
        <Settings className="h-7 w-7 text-[var(--color-accent)]" />
        <h1 className="text-2xl font-bold text-[var(--color-text-primary)]">
          Settings
        </h1>
      </div>

      {/* Theme Section */}
      <div className="rounded-xl border border-[var(--color-border)] bg-[var(--color-bg-card)] p-6 mb-4">
        <h2 className="mb-4 text-lg font-semibold text-[var(--color-text-primary)]">
          Appearance
        </h2>

        {/* Theme Mode */}
        <div className="mb-6">
          <label className="mb-2 block text-sm font-medium text-[var(--color-text-primary)]">
            Theme
          </label>
          <div className="flex gap-2">
            {(['light', 'dark', 'system'] as ThemeMode[]).map((m) => (
              <button
                key={m}
                onClick={() => setMode(m)}
                className={`rounded-lg px-4 py-2 text-sm font-medium capitalize transition-colors ${
                  mode === m
                    ? 'bg-[var(--color-accent)] text-white'
                    : 'border border-[var(--color-border)] text-[var(--color-text-secondary)] hover:bg-[var(--color-bg-secondary)]'
                }`}
              >
                {m}
              </button>
            ))}
          </div>
        </div>

        {/* Accent Color */}
        <div>
          <label className="mb-2 block text-sm font-medium text-[var(--color-text-primary)]">
            Accent Color
          </label>
          <div className="flex flex-wrap gap-2">
            {ACCENT_COLORS.map(({ name, value }) => (
              <button
                key={value}
                onClick={() => setAccentColor(value)}
                className={`h-8 w-8 rounded-full transition-transform ${
                  accentColor === value
                    ? 'scale-110 ring-2 ring-offset-2 ring-[var(--color-accent)]'
                    : 'hover:scale-105'
                }`}
                style={{ backgroundColor: value }}
                title={name}
                aria-label={name}
              />
            ))}
          </div>
        </div>
      </div>

      {/* Other Settings Placeholder */}
      <div className="rounded-xl border border-[var(--color-border)] bg-[var(--color-bg-card)] p-6">
        <h2 className="mb-2 text-lg font-semibold text-[var(--color-text-primary)]">
          More Settings
        </h2>
        <p className="text-sm text-[var(--color-text-secondary)]">
          Account, notifications, data export/import, and more — coming in a later phase.
        </p>
      </div>
    </div>
  );
}
