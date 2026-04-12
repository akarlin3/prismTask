import { useState, useEffect } from 'react';
import { WifiOff } from 'lucide-react';

export function OfflineBanner() {
  const [isOffline, setIsOffline] = useState(!navigator.onLine);

  useEffect(() => {
    const handleOnline = () => setIsOffline(false);
    const handleOffline = () => setIsOffline(true);

    window.addEventListener('online', handleOnline);
    window.addEventListener('offline', handleOffline);

    return () => {
      window.removeEventListener('online', handleOnline);
      window.removeEventListener('offline', handleOffline);
    };
  }, []);

  if (!isOffline) return null;

  return (
    <div
      role="alert"
      aria-live="assertive"
      className="fixed left-0 right-0 top-0 z-[60] flex items-center justify-center gap-2 bg-amber-600 px-4 py-2 text-sm font-medium text-white"
    >
      <WifiOff className="h-4 w-4" aria-hidden="true" />
      You're offline. Changes will sync when reconnected.
    </div>
  );
}
