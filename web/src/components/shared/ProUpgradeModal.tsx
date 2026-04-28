import { Crown, Sparkles, Zap } from 'lucide-react';
import { Modal } from '@/components/ui/Modal';
import { Button } from '@/components/ui/Button';

interface ProUpgradeModalProps {
  isOpen: boolean;
  onClose: () => void;
  featureName: string;
  featureDescription?: string;
}

const PRO_FEATURES = [
  { icon: Sparkles, label: 'AI-Powered Eisenhower Matrix' },
  { icon: Zap, label: 'Smart Pomodoro Planning' },
  { icon: Sparkles, label: 'AI NLP Task Parsing' },
  { icon: Crown, label: 'Cloud Sync & Backup' },
  { icon: Crown, label: 'Advanced Analytics' },
];

export function ProUpgradeModal({
  isOpen,
  onClose,
  featureName,
  featureDescription,
}: ProUpgradeModalProps) {
  return (
    <Modal isOpen={isOpen} onClose={onClose} title="Upgrade to Pro" size="sm">
      <div className="flex flex-col items-center text-center">
        {/* Crown icon */}
        <div className="mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-amber-100">
          <Crown className="h-8 w-8 text-amber-500" />
        </div>

        {/* Feature name */}
        <h3 className="mb-1 text-lg font-bold text-[var(--color-text-primary)]">
          {featureName}
        </h3>
        {featureDescription && (
          <p className="mb-4 text-sm text-[var(--color-text-secondary)]">
            {featureDescription}
          </p>
        )}

        {/* Feature list */}
        <div className="mb-6 w-full rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-secondary)] p-4">
          <p className="mb-3 text-xs font-semibold uppercase tracking-wider text-[var(--color-text-secondary)]">
            Pro Features Include
          </p>
          <ul className="flex flex-col gap-2">
            {PRO_FEATURES.map(({ icon: Icon, label }) => (
              <li
                key={label}
                className="flex items-center gap-2 text-sm text-[var(--color-text-primary)]"
              >
                <Icon className="h-4 w-4 shrink-0 text-amber-500" />
                {label}
              </li>
            ))}
          </ul>
        </div>

        {/* Pricing */}
        <div className="mb-4">
          <span className="text-3xl font-bold text-[var(--color-text-primary)]">$7.99</span>
          <span className="text-sm text-[var(--color-text-secondary)]"> / month</span>
          <div className="text-sm text-[var(--color-text-secondary)]">
            or $5 / month billed annually ($59.99 / year) — 7-day free trial
          </div>
        </div>

        {/* CTA */}
        <Button className="w-full mb-2" onClick={onClose}>
          <Crown className="h-4 w-4" />
          Upgrade to Pro
        </Button>
        <button
          onClick={onClose}
          className="text-sm text-[var(--color-text-secondary)] hover:text-[var(--color-text-primary)]"
        >
          Maybe Later
        </button>
      </div>
    </Modal>
  );
}
