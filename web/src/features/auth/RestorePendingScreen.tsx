import { useState } from 'react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/Button';
import { PrismLogo } from '@/components/shared/PrismLogo';
import { useAuthStore } from '@/stores/authStore';

/**
 * Full-screen takeover shown immediately after sign-in when the backend
 * reports the account is in the deletion-grace window. Mirrors Android's
 * `RestoreAccountPrompt` (`AuthScreen.kt:276-321`): the user must explicitly
 * choose to restore the account or to let the deletion proceed before any
 * sync runs — otherwise a quiet web-side sync would re-establish their data
 * and silently overwrite the deletion mark.
 *
 * The decision lives outside the normal route tree (no nav, no AppShell).
 */
export function RestorePendingScreen() {
  const scheduledFor = useAuthStore((s) => s.deletionScheduledFor);
  const restoreAccount = useAuthStore((s) => s.restoreAccount);
  const abandonRestore = useAuthStore((s) => s.abandonRestore);

  const [restoring, setRestoring] = useState(false);
  const [signingOut, setSigningOut] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const formattedDate = scheduledFor ? formatDate(scheduledFor) : null;

  const onRestore = async () => {
    setErrorMessage(null);
    setRestoring(true);
    try {
      await restoreAccount();
      toast.success('Account restored. Welcome back.');
    } catch (e: unknown) {
      const message =
        e instanceof Error
          ? e.message
          : 'Could not restore account. Please try again.';
      setErrorMessage(message);
    } finally {
      setRestoring(false);
    }
  };

  const onSignOut = async () => {
    setSigningOut(true);
    try {
      await abandonRestore();
      toast(
        'Signed out. Your account is still scheduled for deletion — sign back in within the grace period to restore it.',
      );
    } finally {
      setSigningOut(false);
    }
  };

  const busy = restoring || signingOut;

  return (
    <div className="flex min-h-screen items-center justify-center bg-[var(--color-bg-primary)] px-4">
      <div className="w-full max-w-md">
        <div className="mb-8 flex flex-col items-center text-center">
          <PrismLogo variant="full" size={48} className="mb-4" />
          <h1 className="text-2xl font-bold text-[var(--color-text-primary)]">
            Your Account Is Scheduled for Deletion
          </h1>
          {formattedDate ? (
            <p className="mt-3 text-sm text-[var(--color-text-secondary)]">
              Your account will be permanently deleted on{' '}
              <span className="font-medium text-[var(--color-text-primary)]">
                {formattedDate}
              </span>
              . Restore now to keep your data, or sign out to let the deletion
              proceed.
            </p>
          ) : (
            <p className="mt-3 text-sm text-[var(--color-text-secondary)]">
              Your account is in the deletion grace window. Restore now to keep
              your data, or sign out to let the deletion proceed.
            </p>
          )}
        </div>

        {errorMessage && (
          <div
            role="alert"
            className="mb-4 rounded-lg bg-red-500/10 px-3 py-2 text-sm text-red-500"
          >
            {errorMessage}
          </div>
        )}

        <div className="flex flex-col gap-3">
          <Button
            type="button"
            variant="primary"
            className="w-full"
            loading={restoring}
            disabled={busy}
            onClick={onRestore}
          >
            Restore Account
          </Button>
          <Button
            type="button"
            variant="secondary"
            className="w-full"
            loading={signingOut}
            disabled={busy}
            onClick={onSignOut}
          >
            Sign Out
          </Button>
        </div>

        <p className="mt-6 text-center text-xs text-[var(--color-text-secondary)]">
          No data will sync to this device until you make a choice.
        </p>
      </div>
    </div>
  );
}

function formatDate(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return iso;
  try {
    return date.toLocaleDateString(undefined, {
      year: 'numeric',
      month: 'long',
      day: 'numeric',
    });
  } catch {
    return date.toDateString();
  }
}
