import '@testing-library/jest-dom/vitest';

// jsdom doesn't implement window.matchMedia. Stub a no-match version so
// hooks like useMediaQuery (and any component that goes through Modal /
// ResponsiveDialog / similar) work in tests. Tests can override per-call
// via `vi.spyOn(window, 'matchMedia')` if they need a specific result.
if (typeof window !== 'undefined' && typeof window.matchMedia !== 'function') {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: (query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: () => {},
      removeListener: () => {},
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => false,
    }),
  });
}
