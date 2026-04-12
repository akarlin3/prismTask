import { useEffect } from 'react';
import { RouterProvider } from 'react-router-dom';
import { Toaster } from 'sonner';
import { router } from '@/routes';
import { useAuthStore } from '@/stores/authStore';
import { useThemeStore } from '@/stores/themeStore';

export default function App() {
  const hydrateFromStorage = useAuthStore((s) => s.hydrateFromStorage);
  const applyTheme = useThemeStore((s) => s.applyTheme);
  const themeMode = useThemeStore((s) => s.mode);

  // Hydrate auth state from localStorage on mount
  useEffect(() => {
    hydrateFromStorage();
  }, [hydrateFromStorage]);

  // Apply theme on mount and mode changes
  useEffect(() => {
    applyTheme();
  }, [applyTheme, themeMode]);

  // Listen for system theme changes
  useEffect(() => {
    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
    const handler = () => {
      if (useThemeStore.getState().mode === 'system') {
        useThemeStore.getState().applyTheme();
      }
    };
    mediaQuery.addEventListener('change', handler);
    return () => mediaQuery.removeEventListener('change', handler);
  }, []);

  return (
    <>
      <RouterProvider router={router} />
      <Toaster
        position="bottom-right"
        toastOptions={{
          style: {
            background: 'var(--color-bg-card)',
            color: 'var(--color-text-primary)',
            border: '1px solid var(--color-border)',
          },
        }}
      />
    </>
  );
}
