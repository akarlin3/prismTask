import { useState } from 'react';
import { Modal } from '@/components/ui/Modal';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { authApi } from '@/api/auth';
import { useAuthStore } from '@/stores/authStore';
import { firestore } from '@/lib/firebase';
import { doc, updateDoc, serverTimestamp } from 'firebase/firestore';

/**
 * Two-step typed-DELETE confirmation flow that mirrors the Android idiom
 * (`DeleteAccountSection.kt` → `DeleteDialogStep.EXPLAIN | CONFIRM`).
 *
 * Step EXPLAIN tells the user what's about to happen — sign-out, 30-day
 * grace window during which a sign-in reverts to active, eventual
 * permanent removal of all data. Step CONFIRM gates the destructive
 * action behind the user typing the literal word "DELETE" (trim-tolerant
 * to mirror Android).
 *
 * On submit the modal:
 *   1. Calls POST `/api/v1/auth/me/deletion` with `initiated_from='web'`.
 *   2. Mirrors the deletion fields onto Firestore `users/{uid}` so
 *      Android's next sign-in `checkDeletionStatus()` (which reads from
 *      Firestore, not Postgres) detects the web-initiated deletion.
 *   3. Signs the user out via the existing auth store path.
 *
 * Errors during step 1 keep the modal open and let the user retry.
 * Errors during step 2 (Firestore mark) are non-fatal — backend already
 * recorded the truth in Postgres; the user can still sign back in within
 * the 30-day window. We log a warning and proceed to sign-out.
 */
type Step = 'explain' | 'confirming' | 'submitting' | 'submitted' | 'error';

interface Props {
  isOpen: boolean;
  onClose: () => void;
}

const REQUIRED_TEXT = 'DELETE';

export function DeleteAccountModal({ isOpen, onClose }: Props) {
  const [step, setStep] = useState<Step>('explain');
  const [confirmText, setConfirmText] = useState('');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const { firebaseUid, logout } = useAuthStore();

  const matched = confirmText.trim() === REQUIRED_TEXT;

  function reset() {
    setStep('explain');
    setConfirmText('');
    setErrorMessage(null);
  }

  function handleClose() {
    if (step === 'submitting' || step === 'submitted') return; // mid-flight: lock
    reset();
    onClose();
  }

  async function handleSubmit() {
    if (!matched) return;
    setStep('submitting');
    setErrorMessage(null);
    try {
      await authApi.requestAccountDeletion('web');

      // Mirror to Firestore so Android sees the web-initiated deletion
      // on its next sign-in. Non-fatal if it fails — backend Postgres
      // is the source of truth for the deletion state.
      if (firebaseUid) {
        try {
          await updateDoc(doc(firestore, 'users', firebaseUid), {
            deletion_pending_at: serverTimestamp(),
            deletion_initiated_from: 'web',
          });
        } catch (e) {
          // eslint-disable-next-line no-console
          console.warn('Failed to mirror deletion to Firestore (non-fatal):', e);
        }
      }

      setStep('submitted');
      // Brief pause so the user sees the success state before sign-out
      // tears the modal down with the rest of the app shell.
      window.setTimeout(() => {
        logout();
      }, 800);
    } catch (e: unknown) {
      const message =
        e instanceof Error
          ? e.message
          : 'Could not schedule deletion. Please try again.';
      setErrorMessage(message);
      setStep('error');
    }
  }

  const submitting = step === 'submitting';
  const submitted = step === 'submitted';

  return (
    <Modal
      isOpen={isOpen}
      onClose={handleClose}
      title="Delete account"
      size="sm"
      persistent={submitting || submitted}
      footer={
        <div className="flex justify-end gap-2">
          {step === 'explain' && (
            <>
              <Button variant="ghost" onClick={handleClose}>
                Cancel
              </Button>
              <Button variant="danger" onClick={() => setStep('confirming')}>
                Continue
              </Button>
            </>
          )}
          {step === 'confirming' && (
            <>
              <Button variant="ghost" onClick={() => setStep('explain')}>
                Back
              </Button>
              <Button variant="danger" disabled={!matched} onClick={handleSubmit}>
                Delete account
              </Button>
            </>
          )}
          {step === 'submitting' && (
            <Button variant="danger" loading disabled>
              Scheduling…
            </Button>
          )}
          {step === 'submitted' && (
            <Button variant="ghost" disabled>
              Signing out…
            </Button>
          )}
          {step === 'error' && (
            <>
              <Button variant="ghost" onClick={handleClose}>
                Cancel
              </Button>
              <Button variant="danger" onClick={handleSubmit}>
                Retry
              </Button>
            </>
          )}
        </div>
      }
    >
      {step === 'explain' && (
        <div className="space-y-3 text-sm text-[var(--color-text-primary)]">
          <p>Deleting your account will:</p>
          <ul className="list-disc space-y-1 pl-5 text-[var(--color-text-secondary)]">
            <li>Sign you out everywhere immediately.</li>
            <li>
              Schedule permanent deletion in <span className="font-medium">30 days</span>.
              You can reverse this any time within that window by signing back in.
            </li>
            <li>
              Remove all your tasks, habits, projects, and synced data after the grace
              period.
            </li>
          </ul>
        </div>
      )}

      {step === 'confirming' && (
        <div className="space-y-3 text-sm text-[var(--color-text-primary)]">
          <p>
            Type <span className="font-mono font-semibold">DELETE</span> to confirm.
          </p>
          <Input
            type="text"
            value={confirmText}
            onChange={(e) => setConfirmText(e.target.value)}
            placeholder="DELETE"
            autoFocus
            aria-label="Type DELETE to confirm"
          />
        </div>
      )}

      {step === 'submitting' && (
        <p className="text-sm text-[var(--color-text-secondary)]">
          Scheduling deletion…
        </p>
      )}

      {step === 'submitted' && (
        <p className="text-sm text-[var(--color-text-primary)]">
          Deletion scheduled. You can restore your account by signing in within the
          next 30 days. Signing you out…
        </p>
      )}

      {step === 'error' && (
        <div className="space-y-2 text-sm">
          <p className="text-[var(--color-error)]">
            {errorMessage ?? 'Something went wrong.'}
          </p>
          <p className="text-[var(--color-text-secondary)]">
            No deletion has been scheduled. You can try again or cancel.
          </p>
        </div>
      )}
    </Modal>
  );
}
