import { useState, useCallback, useRef, useEffect } from 'react';
import { Moon, Sun, Monitor, LogOut, Search } from 'lucide-react';
import { toast } from 'sonner';
import { useAuthStore } from '@/stores/authStore';
import { useThemeStore } from '@/stores/themeStore';
import { useTaskStore } from '@/stores/taskStore';
import { useProjectStore } from '@/stores/projectStore';
import { NLPInput } from '@/components/shared/NLPInput';
import { SearchModal } from '@/components/shared/SearchModal';
import { Avatar } from '@/components/ui/Avatar';
import type { ThemeMode } from '@/stores/themeStore';

export function Header() {
  const [showUserMenu, setShowUserMenu] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);
  const user = useAuthStore((s) => s.user);
  const logout = useAuthStore((s) => s.logout);
  const themeMode = useThemeStore((s) => s.mode);
  const setMode = useThemeStore((s) => s.setMode);
  const createTask = useTaskStore((s) => s.createTask);
  const projects = useProjectStore((s) => s.projects);

  // Close user menu on outside click
  useEffect(() => {
    if (!showUserMenu) return;
    const handler = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        setShowUserMenu(false);
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [showUserMenu]);

  const handleTaskCreate = useCallback(
    async (data: {
      title: string;
      due_date?: string;
      priority?: number;
      project_suggestion?: string;
    }) => {
      // Find a matching project or use the first one
      let targetProjectId = projects[0]?.id;
      if (data.project_suggestion) {
        const match = projects.find((p) =>
          p.title.toLowerCase().includes(data.project_suggestion!.toLowerCase()),
        );
        if (match) targetProjectId = match.id;
      }

      if (!targetProjectId) {
        toast.error('No project available. Create a project first.');
        return;
      }

      try {
        await createTask(targetProjectId, {
          title: data.title,
          due_date: data.due_date,
          priority: (data.priority as 1 | 2 | 3 | 4) || 3,
        });
        toast.success('Task created!');
        // Refresh today tasks
        useTaskStore.getState().fetchToday();
        useTaskStore.getState().fetchOverdue();
        useTaskStore.getState().fetchUpcoming(7);
      } catch {
        toast.error('Failed to create task');
      }
    },
    [createTask, projects],
  );

  const themeOptions: { mode: ThemeMode; icon: typeof Sun; label: string }[] = [
    { mode: 'light', icon: Sun, label: 'Light' },
    { mode: 'dark', icon: Moon, label: 'Dark' },
    { mode: 'system', icon: Monitor, label: 'System' },
  ];

  return (
    <>
      <header
        className="flex h-14 items-center gap-4 border-b border-[var(--color-border)] bg-[var(--color-bg-primary)] px-4"
        role="banner"
      >
        {/* NLP Quick Add Bar */}
        <NLPInput onTaskCreate={handleTaskCreate} />

        {/* Search Button */}
        <button
          onClick={() => setSearchOpen(true)}
          className="flex items-center gap-1.5 rounded-lg border border-[var(--color-border)] px-3 py-1.5 text-sm text-[var(--color-text-secondary)] transition-colors hover:border-[var(--color-accent)] hover:text-[var(--color-text-primary)]"
          aria-label="Search tasks (Ctrl+K)"
        >
          <Search className="h-4 w-4" aria-hidden="true" />
          <span className="hidden sm:inline">Search</span>
          <kbd className="ml-1 hidden rounded bg-[var(--color-bg-secondary)] px-1.5 py-0.5 text-xs font-mono sm:inline" aria-hidden="true">
            ⌘K
          </kbd>
        </button>

        {/* Theme Toggle */}
        <div className="flex items-center gap-1 rounded-lg border border-[var(--color-border)] p-1" role="radiogroup" aria-label="Theme">
          {themeOptions.map(({ mode, icon: Icon, label }) => (
            <button
              key={mode}
              onClick={() => setMode(mode)}
              className={`rounded-md p-1.5 transition-colors ${
                themeMode === mode
                  ? 'bg-[var(--color-accent)]/10 text-[var(--color-accent)]'
                  : 'text-[var(--color-text-secondary)] hover:text-[var(--color-text-primary)]'
              }`}
              role="radio"
              aria-checked={themeMode === mode}
              aria-label={`${label} theme`}
            >
              <Icon className="h-4 w-4" aria-hidden="true" />
            </button>
          ))}
        </div>

        {/* User Menu */}
        <div className="relative" ref={menuRef}>
          <button
            onClick={() => setShowUserMenu(!showUserMenu)}
            aria-label="User menu"
            aria-expanded={showUserMenu}
            aria-haspopup="true"
          >
            <Avatar name={user?.name} src={user?.avatar_url} size="md" />
          </button>

          {showUserMenu && (
            <div
              className="absolute right-0 top-full z-50 mt-2 w-48 rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-card)] p-1 shadow-lg"
              role="menu"
              aria-label="User options"
            >
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
                role="menuitem"
              >
                <LogOut className="h-4 w-4" aria-hidden="true" />
                Sign Out
              </button>
            </div>
          )}
        </div>
      </header>

      {/* Search Modal */}
      <SearchModal isOpen={searchOpen} onClose={() => setSearchOpen(false)} />
    </>
  );
}
