import { useEffect, useState } from 'react';
import { Loader2, Plus, Trash2 } from 'lucide-react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/Button';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';
import {
  createSlotDef,
  deleteSlotDef,
  getSlotDefs,
  updateSlotDef,
  type MedicationSlotDef,
} from '@/api/firestore/medicationSlots';
import { getFirebaseUid } from '@/stores/firebaseUid';

/**
 * Slot-definition CRUD — Firestore-native. The existing
 * `/daily-essentials/slots` endpoint is completion-only, so slot
 * configuration lives at `users/{uid}/medication_slot_defs`.
 *
 * Shows as a compact inline editor; mount inside Settings (or any
 * screen that wants it). Auto-loads on mount, persists on blur.
 */
export function MedicationSlotEditor() {
  const [slots, setSlots] = useState<MedicationSlotDef[]>([]);
  const [loading, setLoading] = useState(true);
  const [creating, setCreating] = useState(false);
  const [newKey, setNewKey] = useState('');
  const [newName, setNewName] = useState('');
  const [deleteTarget, setDeleteTarget] = useState<MedicationSlotDef | null>(
    null,
  );

  const uid = (() => {
    try {
      return getFirebaseUid();
    } catch {
      return null;
    }
  })();

  const load = async () => {
    if (!uid) return;
    setLoading(true);
    try {
      const defs = await getSlotDefs(uid);
      setSlots(defs);
    } catch (e) {
      toast.error((e as Error).message || 'Failed to load slots');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [uid]);

  const handleCreate = async () => {
    if (!uid) return;
    const key = newKey.trim();
    const name = newName.trim() || key;
    if (!key) return;
    setCreating(true);
    try {
      const created = await createSlotDef(uid, {
        slot_key: key,
        display_name: name,
        sort_order: slots.length,
      });
      setSlots((prev) => [...prev, created]);
      setNewKey('');
      setNewName('');
      toast.success(`Added slot "${created.display_name}"`);
    } catch (e) {
      toast.error((e as Error).message || 'Failed to add slot');
    } finally {
      setCreating(false);
    }
  };

  const handleRename = async (slot: MedicationSlotDef, nextName: string) => {
    if (!uid) return;
    const name = nextName.trim();
    if (!name || name === slot.display_name) return;
    try {
      await updateSlotDef(uid, slot.id, { display_name: name });
      setSlots((prev) =>
        prev.map((s) => (s.id === slot.id ? { ...s, display_name: name } : s)),
      );
    } catch (e) {
      toast.error((e as Error).message || 'Rename failed');
    }
  };

  const handleDelete = async () => {
    if (!uid || !deleteTarget) return;
    try {
      await deleteSlotDef(uid, deleteTarget.id);
      setSlots((prev) => prev.filter((s) => s.id !== deleteTarget.id));
      toast.success('Slot deleted');
    } catch (e) {
      toast.error((e as Error).message || 'Delete failed');
    } finally {
      setDeleteTarget(null);
    }
  };

  if (!uid) {
    return (
      <p className="text-xs text-[var(--color-text-secondary)]">
        Sign in to manage medication slots.
      </p>
    );
  }

  return (
    <div className="flex flex-col gap-3 text-sm">
      <p className="text-xs text-[var(--color-text-secondary)]">
        Define the time-slot keys that appear on the Medication screen
        (e.g. <code>08:00</code>, <code>noon</code>, <code>anytime</code>).
        Completion state is still persisted to the backend; slot
        definitions live per-user in Firestore.
      </p>

      {loading ? (
        <div className="flex items-center gap-2 py-4 text-[var(--color-text-secondary)]">
          <Loader2 className="h-4 w-4 animate-spin" /> Loading slots…
        </div>
      ) : (
        <ul className="flex flex-col gap-1.5">
          {slots.length === 0 && (
            <li className="rounded-md border border-dashed border-[var(--color-border)] p-3 text-xs text-[var(--color-text-secondary)]">
              No slots yet. Add one below.
            </li>
          )}
          {slots.map((slot) => (
            <li
              key={slot.id}
              className="flex items-center gap-2 rounded-md border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-2 py-1.5"
            >
              <span className="font-mono text-xs text-[var(--color-text-secondary)]">
                {slot.slot_key}
              </span>
              <input
                type="text"
                defaultValue={slot.display_name}
                onBlur={(e) => handleRename(slot, e.target.value)}
                aria-label={`Rename ${slot.slot_key}`}
                className="flex-1 rounded-md border border-transparent bg-transparent px-2 py-0.5 text-sm text-[var(--color-text-primary)] outline-none focus:border-[var(--color-accent)]"
              />
              <button
                onClick={() => setDeleteTarget(slot)}
                className="text-[var(--color-text-secondary)] hover:text-red-500"
                title="Delete slot"
                aria-label={`Delete slot ${slot.display_name}`}
              >
                <Trash2 className="h-4 w-4" aria-hidden="true" />
              </button>
            </li>
          ))}
        </ul>
      )}

      <div className="flex flex-wrap items-center gap-1.5 rounded-md border border-dashed border-[var(--color-border)] p-2">
        <input
          type="text"
          value={newKey}
          onChange={(e) => setNewKey(e.target.value)}
          placeholder="slot key (e.g. 08:00)"
          className="w-32 rounded-md border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-2 py-1 text-xs text-[var(--color-text-primary)] outline-none focus:border-[var(--color-accent)]"
        />
        <input
          type="text"
          value={newName}
          onChange={(e) => setNewName(e.target.value)}
          placeholder="display name"
          className="flex-1 rounded-md border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-2 py-1 text-xs text-[var(--color-text-primary)] outline-none focus:border-[var(--color-accent)]"
        />
        <Button
          size="sm"
          onClick={handleCreate}
          disabled={!newKey.trim() || creating}
        >
          {creating ? (
            <Loader2 className="mr-1 h-3 w-3 animate-spin" />
          ) : (
            <Plus className="mr-1 h-3 w-3" />
          )}
          Add slot
        </Button>
      </div>

      <ConfirmDialog
        isOpen={!!deleteTarget}
        onClose={() => setDeleteTarget(null)}
        onConfirm={handleDelete}
        title="Delete medication slot?"
        message={`"${deleteTarget?.display_name}" will be removed from the Medication screen. Existing completion history on the backend is unaffected.`}
        confirmLabel="Delete"
        variant="danger"
      />
    </div>
  );
}
