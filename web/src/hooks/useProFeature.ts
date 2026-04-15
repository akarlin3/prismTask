import { useState, useCallback } from 'react';
import { useAuthStore } from '@/stores/authStore';

interface ProFeatureGate {
  isPro: boolean;
  checkAccess: () => boolean;
  showUpgrade: boolean;
  setShowUpgrade: (show: boolean) => void;
  gatedAction: (action: () => void) => void;
}

export function useProFeature(): ProFeatureGate {
  const user = useAuthStore((s) => s.user);
  const [showUpgrade, setShowUpgrade] = useState(false);

  const tier = user?.tier || 'FREE';
  const isPro = tier === 'PRO';

  const checkAccess = useCallback(() => {
    if (isPro) return true;
    setShowUpgrade(true);
    return false;
  }, [isPro]);

  const gatedAction = useCallback(
    (action: () => void) => {
      if (isPro) {
        action();
      } else {
        setShowUpgrade(true);
      }
    },
    [isPro],
  );

  return { isPro, checkAccess, showUpgrade, setShowUpgrade, gatedAction };
}
