import { useState } from 'react';
import { Button } from '@/components/ui/Button';
import { DeleteAccountModal } from './DeleteAccountModal';

/**
 * "Delete account" entry point in the Settings → Account section.
 * Renders alongside Log Out. Opening the modal hands off to
 * {@link DeleteAccountModal}, which owns the typed-DELETE flow and the
 * sign-out side effect. The web counterpart of Android's
 * `DeleteAccountSection` composable (`DeleteAccountSection.kt:46-105`).
 */
export function DeleteAccountSection() {
  const [open, setOpen] = useState(false);

  return (
    <>
      <Button variant="danger" onClick={() => setOpen(true)}>
        Delete Account
      </Button>
      <DeleteAccountModal isOpen={open} onClose={() => setOpen(false)} />
    </>
  );
}
