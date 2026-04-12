import { useState, useCallback } from 'react';
import { Outlet } from 'react-router-dom';
import { Sidebar } from './Sidebar';
import { Header } from './Header';
import { MobileNav } from './MobileNav';
import { SearchModal } from '@/components/shared/SearchModal';
import { useUIStore } from '@/stores/uiStore';
import { useIsMobile } from '@/hooks/useMediaQuery';
import { useKeyboardShortcuts } from '@/hooks/useKeyboardShortcuts';
import TaskEditor from '@/features/tasks/TaskEditor';
import { useTaskStore } from '@/stores/taskStore';

export function AppShell() {
  const sidebarCollapsed = useUIStore((s) => s.sidebarCollapsed);
  const isMobile = useIsMobile();
  const setSelectedTask = useTaskStore((s) => s.setSelectedTask);

  const [searchOpen, setSearchOpen] = useState(false);
  const [newTaskOpen, setNewTaskOpen] = useState(false);

  const handleSearch = useCallback(() => {
    setSearchOpen(true);
  }, []);

  const handleNewTask = useCallback(() => {
    setSelectedTask(null);
    setNewTaskOpen(true);
  }, [setSelectedTask]);

  useKeyboardShortcuts({
    onSearch: handleSearch,
    onNewTask: handleNewTask,
  });

  return (
    <div className="flex h-screen bg-[var(--color-bg-primary)]">
      {/* Desktop/Tablet Sidebar */}
      {!isMobile && <Sidebar />}

      {/* Main Content Area */}
      <div
        className={`flex flex-1 flex-col transition-all duration-200 ${
          isMobile ? 'ml-0' : sidebarCollapsed ? 'ml-16' : 'ml-60'
        }`}
      >
        <Header />
        <main className="flex-1 overflow-y-auto p-4 pb-20 lg:pb-4">
          <Outlet />
        </main>
      </div>

      {/* Mobile Bottom Nav */}
      {isMobile && <MobileNav />}

      {/* Global Search Modal (from Ctrl+K shortcut) */}
      <SearchModal isOpen={searchOpen} onClose={() => setSearchOpen(false)} />

      {/* Global New Task (from `n` shortcut) */}
      {newTaskOpen && (
        <TaskEditor
          mode="create"
          onClose={() => setNewTaskOpen(false)}
          onUpdate={() => {
            useTaskStore.getState().fetchToday();
            useTaskStore.getState().fetchOverdue();
          }}
        />
      )}
    </div>
  );
}
