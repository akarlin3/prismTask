import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import App from './App.tsx';
import './index.css';

// Capture PWA install prompt for later use
window.addEventListener('beforeinstallprompt', (e) => {
  e.preventDefault();
  (window as unknown as Record<string, unknown>).__pwaInstallPrompt = e;
});

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
