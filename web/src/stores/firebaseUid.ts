/**
 * Shared Firebase UID accessor.
 * Avoids circular imports between authStore and data stores.
 */
let _uid: string | null = null;

export function setFirebaseUid(uid: string | null): void {
  _uid = uid;
}

export function getFirebaseUid(): string {
  if (!_uid) throw new Error('Not authenticated');
  return _uid;
}
