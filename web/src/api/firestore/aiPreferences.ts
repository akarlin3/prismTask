import {
  doc,
  getDoc,
  onSnapshot,
  setDoc,
  type DocumentData,
  type Unsubscribe,
} from 'firebase/firestore';
import { firestore } from '@/lib/firebase';

/**
 * AI-features master opt-out preference, synced cross-device with Android.
 *
 * Mirrors Android's `KEY_AI_FEATURES_ENABLED` in
 * `app/src/main/java/com/averycorp/prismtask/data/preferences/UserPreferencesDataStore.kt:229`.
 *
 * Storage path matches Android's `GenericPreferenceSyncService` which serializes
 * `user_prefs` DataStore to `users/{uid}/prefs/user_prefs`. The field name on
 * the Firestore document is the raw DataStore key name (`ai_features_enabled`)
 * to satisfy the type-tagged schema documented in `PreferenceSyncSerialization.kt`
 * (each serialized key has a sibling `__pref_types[<key>]` entry — Android writes
 * `"bool"` here when it pushes; web writes the value with no type tag and merges,
 * which is fine because Android's pull side reads from `__pref_types` only when
 * present and falls back to leaving the existing local value alone otherwise).
 *
 * Default is `true` — opt-out, not opt-in — matching Android's default.
 *
 * Privacy invariant: when this flag is `false`, no PrismTask data should reach
 * Anthropic via the backend. The `aiFeatureGateInterceptor` in `api/client.ts`
 * enforces this at the HTTP layer with a synthetic 451 response.
 */

const DOC_NAME = 'user_prefs';
const FIELD_AI_FEATURES_ENABLED = 'ai_features_enabled';
const META_TYPES = '__pref_types';
const META_UPDATED_AT = '__pref_updated_at';

export const DEFAULT_AI_FEATURES_ENABLED = true;

function prefsDoc(uid: string) {
  return doc(firestore, 'users', uid, 'prefs', DOC_NAME);
}

function readEnabled(data: DocumentData | undefined): boolean {
  if (!data) return DEFAULT_AI_FEATURES_ENABLED;
  const v = data[FIELD_AI_FEATURES_ENABLED];
  if (typeof v === 'boolean') return v;
  return DEFAULT_AI_FEATURES_ENABLED;
}

export async function getAiFeaturesEnabled(uid: string): Promise<boolean> {
  const snap = await getDoc(prefsDoc(uid));
  return readEnabled(snap.exists() ? snap.data() : undefined);
}

export async function setAiFeaturesEnabled(
  uid: string,
  enabled: boolean,
): Promise<void> {
  await setDoc(
    prefsDoc(uid),
    {
      [FIELD_AI_FEATURES_ENABLED]: enabled,
      // Tag the type so Android's GenericPreferenceSyncService pull side
      // reconstructs the correct Preferences.Key<Boolean>. Merge=true means
      // we don't clobber other type tags Android may have written.
      [META_TYPES]: { [FIELD_AI_FEATURES_ENABLED]: 'bool' },
      [META_UPDATED_AT]: Date.now(),
    },
    { merge: true },
  );
}

export function subscribeToAiFeaturesEnabled(
  uid: string,
  cb: (enabled: boolean) => void,
): Unsubscribe {
  return onSnapshot(prefsDoc(uid), (snap) =>
    cb(readEnabled(snap.exists() ? snap.data() : undefined)),
  );
}
