import {
  collection,
  doc,
  getDoc,
  getDocs,
  addDoc,
  deleteDoc,
  query,
  where,
  orderBy,
  onSnapshot,
  type Unsubscribe,
  type DocumentData,
} from 'firebase/firestore';
import { firestore } from '@/lib/firebase';
import type {
  TaskDependency,
  TaskDependencyCreate,
} from '@/types/taskDependency';
import { wouldCreateCycle } from '@/utils/dependencyCycleGuard';

// ── Collection reference ──────────────────────────────────────

function depsCol(uid: string) {
  return collection(firestore, 'users', uid, 'task_dependencies');
}

function depDoc(uid: string, depId: string) {
  return doc(firestore, 'users', uid, 'task_dependencies', depId);
}

// ── Firestore doc → Web TaskDependency ────────────────────────

function docToDep(docId: string, data: DocumentData): TaskDependency {
  return {
    id: docId,
    blocker_task_id:
      typeof data.blockerTaskCloudId === 'string' ? data.blockerTaskCloudId : '',
    blocked_task_id:
      typeof data.blockedTaskCloudId === 'string' ? data.blockedTaskCloudId : '',
    created_at: typeof data.createdAt === 'number' ? data.createdAt : Date.now(),
  };
}

// ── Web TaskDependency → Firestore doc ────────────────────────

function depCreateToDoc(data: TaskDependencyCreate): Record<string, unknown> {
  return {
    blockerTaskCloudId: data.blocker_task_id,
    blockedTaskCloudId: data.blocked_task_id,
    createdAt: Date.now(),
  };
}

// ── CRUD operations ──────────────────────────────────────────

export async function getAllDependencies(uid: string): Promise<TaskDependency[]> {
  const q = query(depsCol(uid), orderBy('createdAt', 'asc'));
  const snap = await getDocs(q);
  return snap.docs.map((d) => docToDep(d.id, d.data()));
}

export async function getDependenciesByBlocker(
  uid: string,
  blockerTaskId: string,
): Promise<TaskDependency[]> {
  const q = query(
    depsCol(uid),
    where('blockerTaskCloudId', '==', blockerTaskId),
  );
  const snap = await getDocs(q);
  return snap.docs.map((d) => docToDep(d.id, d.data()));
}

export async function getDependenciesByBlocked(
  uid: string,
  blockedTaskId: string,
): Promise<TaskDependency[]> {
  const q = query(
    depsCol(uid),
    where('blockedTaskCloudId', '==', blockedTaskId),
  );
  const snap = await getDocs(q);
  return snap.docs.map((d) => docToDep(d.id, d.data()));
}

export async function getDependency(
  uid: string,
  depId: string,
): Promise<TaskDependency | null> {
  const snap = await getDoc(depDoc(uid, depId));
  if (!snap.exists()) return null;
  return docToDep(snap.id, snap.data());
}

/** Thrown when an `add` would close a cycle in the dependency graph. */
export class DependencyCycleError extends Error {
  constructor(blocker: string, blocked: string) {
    super(`Adding (${blocker} → ${blocked}) would close a cycle`);
    this.name = 'DependencyCycleError';
  }
}

/**
 * Add a dependency edge after running the cycle guard against the full
 * existing edge set. Mirrors Android `TaskDependencyRepository.addDependency`
 * which also rejects cycle-closing edges before write.
 *
 * Self-edges and edges that would close a cycle throw
 * [DependencyCycleError]. Caller is expected to catch and surface.
 */
export async function addDependency(
  uid: string,
  data: TaskDependencyCreate,
): Promise<TaskDependency> {
  const existing = await getAllDependencies(uid);
  if (wouldCreateCycle(existing, data.blocker_task_id, data.blocked_task_id)) {
    throw new DependencyCycleError(data.blocker_task_id, data.blocked_task_id);
  }
  const payload = depCreateToDoc(data);
  const ref = await addDoc(depsCol(uid), payload);
  return docToDep(ref.id, payload);
}

export async function deleteDependency(uid: string, depId: string): Promise<void> {
  await deleteDoc(depDoc(uid, depId));
}

// ── Real-time listener ───────────────────────────────────────

export function subscribeToDependencies(
  uid: string,
  callback: (deps: TaskDependency[]) => void,
): Unsubscribe {
  const q = query(depsCol(uid), orderBy('createdAt', 'asc'));
  return onSnapshot(q, (snap) => {
    callback(snap.docs.map((d) => docToDep(d.id, d.data())));
  });
}
