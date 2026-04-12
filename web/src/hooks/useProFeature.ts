import { useState, useCallback } from 'react';
import { useAuthStore } from '@/stores/authStore';

interface ProFeatureGate {
  isPro: boolean;
  isPremium: boolean;
  checkAccess: () => boolean;
  showUpgrade: boolean;
  setShowUpgrade: (show: boolean) => void;
  gatedAction: (action: () => void) => void;
}

const PRO_TIERS = ['PRO', 'PREMIUM', 'ULTRA'];
const PREMIUM_TIERS = ['PREMIUM', 'ULTRA'];

export function useProFeature(): ProFeatureGate {
  const user = useAuthStore((s) => s.user);
  const [showUpgrade, setShowUpgrade] = useState(false);

  const tier = user?.tier || 'FREE';
  const isPro = PRO_TIERS.includes(tier);
  const isPremium = PREMIUM_TIERS.includes(tier);

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

  return { isPro, isPremium, checkAccess, showUpgrade, setShowUpgrade, gatedAction };
}
