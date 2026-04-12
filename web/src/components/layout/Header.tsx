import { useState } from 'react';
import { Sparkles, Moon, Sun, Monitor, LogOut, User } from 'lucide-react';
import { useAuthStore } from '@/stores/authStore';
import { useThemeStore } from '@/stores/themeStore';
import type { ThemeMode } from '@/stores/themeStore';

export function Header() {
  const [quickAddValue, setQuickAddValue] = useState('');
  const [showUserMenu, setShowUserMenu] = useState(false);
  const user = useAuthStore((s) => s.user);
  const logout = useAuthStore((s) => s.logout);
  const themeMode = useThemeStore((s) => s.mode);
  const setMode = useThemeStore((s) => s.setMode);

  const handleQuickAdd = (e: React.FormEvent) => {
    e.preventDefault();
    // NLP parse will be implemented in a future phase
    setQuickAddValue('');
  };

  const themeOptions: { mode: ThemeMode; icon: typeof Sun; label: string }[] = [
    { mode: 'light', icon: Sun, label: 'Light' },
    { mode: 'dark', icon: Moon, label: 'Dark' },
    { mode: 'system', icon: Monitor, label: 'System' },
  ];

  return (
    <header className="flex h-14 items-center gap-4 border-b border-[var(--color-border)] bg-[var(--color-bg-primary)] px-4">
      {/* Quick Add Bar */}
      <form onSubmit={handleQuickAdd} className="flex flex-1 items-center">
        <div className="relative flex-1 max-w-2xl">
          <Sparkles className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-[var(--color-text-secondary)]" />
          <input
            type="text"
            placeholder="Quick add task... (e.g. 'Buy milk tomorrow !high #shopping')"
            value={quickAddValue}
            onChange={(e) => setQuickAddValue(e.target.value)}
            className="w-full rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-secondary)] py-2 pl-10 pr-4 text-sm text-[var(--color-text-primary)] placeholder-[var(--color-text-secondary)] outline-none focus:border-[var(--color-accent)] focus:ring-1 focus:ring-[var(--color-accent)]"
          />
        </div>
      </form>

      {/* Theme Toggle */}
      <div className="flex items-center gap-1 rounded-lg border border-[var(--color-border)] p-1">
        {themeOptions.map(({ mode, icon: Icon, label }) => (
          <button
            key={mode}
            onClick={() => setMode(mode)}
            className={`rounded-md p-1.5 transition-colors ${
              themeMode === mode
                ? 'bg-[var(--color-accent)]/10 text-[var(--color-accent)]'
                : 'text-[var(--color-text-secondary)] hover:text-[var(--color-text-primary)]'
            }`}
            aria-label={label}
            title={label}
          >
            <Icon className="h-4 w-4" />
          </button>
        ))}
      </div>

      {/* User Menu */}
      <div className="relative">
        <button
          onClick={() => setShowUserMenu(!showUserMenu)}
          className="flex h-8 w-8 items-center justify-center rounded-full bg-[var(--color-accent)] text-sm font-medium text-white"
          aria-label="User menu"
        >
          {user?.name?.[0]?.toUpperCase() || <User className="h-4 w-4" />}
        </button>

        {showUserMenu && (
          <>
            <div
              className="fixed inset-0 z-40"
              onClick={() => setShowUserMenu(false)}
            />
            <div className="absolute right-0 top-full z-50 mt-2 w-48 rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-card)] p-1 shadow-lg">
              <div className="border-b border-[var(--color-border)] px-3 py-2">
                <p className="text-sm font-medium text-[var(--color-text-primary)]">
                  {user?.name || 'User'}
                </p>
                <p className="text-xs text-[var(--color-text-secondary)]">
                  {user?.email || ''}
                </p>
              </div>
              <button
                onClick={() => {
                  setShowUserMenu(false);
                  logout();
                }}
                className="mt-1 flex w-full items-center gap-2 rounded-md px-3 py-2 text-sm text-red-500 hover:bg-red-50 dark:hover:bg-red-500/10"
              >
                <LogOut className="h-4 w-4" />
                Sign Out
              </button>
            </div>
          </>
        )}
      </div>
    </header>
  );
}
