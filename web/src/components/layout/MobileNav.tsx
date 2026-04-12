import { useState, useRef, useEffect, useCallback } from 'react';
import { NavLink } from 'react-router-dom';
import {
  Sun,
  CheckSquare,
  FolderKanban,
  Activity,
  MoreHorizontal,
  Calendar,
  LayoutGrid,
  FileText,
  Archive,
  Settings,
  X,
} from 'lucide-react';

const PRIMARY_NAV = [
  { to: '/', icon: Sun, label: 'Today' },
  { to: '/tasks', icon: CheckSquare, label: 'Tasks' },
  { to: '/projects', icon: FolderKanban, label: 'Projects' },
  { to: '/habits', icon: Activity, label: 'Habits' },
] as const;

const MORE_ITEMS = [
  { to: '/calendar', icon: Calendar, label: 'Calendar' },
  { to: '/eisenhower', icon: LayoutGrid, label: 'Eisenhower' },
  { to: '/templates', icon: FileText, label: 'Templates' },
  { to: '/archive', icon: Archive, label: 'Archive' },
  { to: '/settings', icon: Settings, label: 'Settings' },
] as const;

export function MobileNav() {
  const [moreOpen, setMoreOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);

  const closeMenu = useCallback(() => setMoreOpen(false), []);

  // Close menu on outside click
  useEffect(() => {
    if (!moreOpen) return;
    const handler = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        closeMenu();
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [moreOpen, closeMenu]);

  const isMoreActive = MORE_ITEMS.some((item) =>
    window.location.pathname.startsWith(item.to),
  );

  return (
    <>
      {/* More menu overlay */}
      {moreOpen && (
        <div className="fixed inset-0 z-40 bg-black/30" aria-hidden="true" onClick={closeMenu} />
      )}

      {/* More menu panel */}
      {moreOpen && (
        <div
          ref={menuRef}
          role="menu"
          aria-label="More navigation options"
          className="fixed bottom-16 left-2 right-2 z-50 rounded-xl border border-[var(--color-border)] bg-[var(--color-bg-card)] p-2 shadow-xl animate-slide-up"
        >
          <div className="mb-1 flex items-center justify-between px-3 py-1">
            <span className="text-xs font-semibold text-[var(--color-text-secondary)] uppercase tracking-wider">
              More
            </span>
            <button
              onClick={closeMenu}
              className="rounded-md p-1 text-[var(--color-text-secondary)] hover:text-[var(--color-text-primary)]"
              aria-label="Close menu"
            >
              <X className="h-4 w-4" />
            </button>
          </div>
          {MORE_ITEMS.map(({ to, icon: Icon, label }) => (
            <NavLink
              key={to}
              to={to}
              role="menuitem"
              onClick={closeMenu}
              className={({ isActive }) =>
                `flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors ${
                  isActive
                    ? 'bg-[var(--color-accent)]/10 text-[var(--color-accent)]'
                    : 'text-[var(--color-text-primary)] hover:bg-[var(--color-bg-secondary)]'
                }`
              }
            >
              <Icon className="h-5 w-5" aria-hidden="true" />
              {label}
            </NavLink>
          ))}
        </div>
      )}

      {/* Bottom nav bar */}
      <nav
        className="fixed bottom-0 left-0 right-0 z-30 border-t border-[var(--color-border)] bg-[var(--color-bg-primary)] lg:hidden"
        aria-label="Main navigation"
      >
        <ul className="flex items-center justify-around">
          {PRIMARY_NAV.map(({ to, icon: Icon, label }) => (
            <li key={to} className="flex-1">
              <NavLink
                to={to}
                end={to === '/'}
                className={({ isActive }) =>
                  `flex flex-col items-center gap-0.5 py-2 text-xs transition-colors min-h-[48px] justify-center ${
                    isActive
                      ? 'text-[var(--color-accent)]'
                      : 'text-[var(--color-text-secondary)]'
                  }`
                }
              >
                <Icon className="h-5 w-5" aria-hidden="true" />
                <span>{label}</span>
              </NavLink>
            </li>
          ))}
          {/* More button */}
          <li className="flex-1">
            <button
              onClick={() => setMoreOpen(!moreOpen)}
              className={`flex w-full flex-col items-center gap-0.5 py-2 text-xs transition-colors min-h-[48px] justify-center ${
                isMoreActive || moreOpen
                  ? 'text-[var(--color-accent)]'
                  : 'text-[var(--color-text-secondary)]'
              }`}
              aria-label="More options"
              aria-expanded={moreOpen}
              aria-haspopup="true"
            >
              <MoreHorizontal className="h-5 w-5" aria-hidden="true" />
              <span>More</span>
            </button>
          </li>
        </ul>
      </nav>
    </>
  );
}
