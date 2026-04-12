import { create } from 'zustand';

interface UIState {
  sidebarOpen: boolean;
  sidebarCollapsed: boolean;
  mobileNavVisible: boolean;
  activeModal: string | null;

  toggleSidebar: () => void;
  setSidebarOpen: (open: boolean) => void;
  toggleSidebarCollapsed: () => void;
  setSidebarCollapsed: (collapsed: boolean) => void;
  setMobileNavVisible: (visible: boolean) => void;
  openModal: (modalId: string) => void;
  closeModal: () => void;
}

export const useUIStore = create<UIState>((set) => ({
  sidebarOpen: true,
  sidebarCollapsed:
    localStorage.getItem('prismtask_sidebar_collapsed') === 'true',
  mobileNavVisible: false,
  activeModal: null,

  toggleSidebar: () =>
    set((state) => ({ sidebarOpen: !state.sidebarOpen })),
  setSidebarOpen: (open) => set({ sidebarOpen: open }),

  toggleSidebarCollapsed: () =>
    set((state) => {
      const collapsed = !state.sidebarCollapsed;
      localStorage.setItem(
        'prismtask_sidebar_collapsed',
        String(collapsed),
      );
      return { sidebarCollapsed: collapsed };
    }),
  setSidebarCollapsed: (collapsed) => {
    localStorage.setItem(
      'prismtask_sidebar_collapsed',
      String(collapsed),
    );
    set({ sidebarCollapsed: collapsed });
  },

  setMobileNavVisible: (visible) => set({ mobileNavVisible: visible }),
  openModal: (modalId) => set({ activeModal: modalId }),
  closeModal: () => set({ activeModal: null }),
}));
