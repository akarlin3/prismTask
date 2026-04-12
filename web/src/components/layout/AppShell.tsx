import { Outlet } from 'react-router-dom';
import { Sidebar } from './Sidebar';
import { Header } from './Header';
import { MobileNav } from './MobileNav';
import { useUIStore } from '@/stores/uiStore';
import { useIsMobile } from '@/hooks/useMediaQuery';

export function AppShell() {
  const sidebarCollapsed = useUIStore((s) => s.sidebarCollapsed);
  const isMobile = useIsMobile();

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
    </div>
  );
}
