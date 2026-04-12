import {
  collection,
  doc,
  getDoc,
  getDocs,
  addDoc,
  updateDoc,
  deleteDoc,
  query,
  orderBy,
  onSnapshot,
  type Unsubscribe,
  type DocumentData,
} from 'firebase/firestore';
import { firestore } from '@/lib/firebase';
import type { Project, ProjectDetail } from '@/types/project';
import { timestampToIso } from './converters';

// ── Collection reference ──────────────────────────────────────

function projectsCol(uid: string) {
  return collection(firestore, 'users', uid, 'projects');
}

function projectDoc(uid: string, projectId: string) {
  return doc(firestore, 'users', uid, 'projects', projectId);
}

// ── Firestore doc → Web Project ──────────────────────────────

function docToProject(docId: string, data: DocumentData, uid: string): Project {
  return {
    id: docId,
    goal_id: '',
    user_id: uid,
    title: data.name ?? '',
    description: data.description ?? null,
    status: data.status ?? 'active',
    due_date: null,
    color: data.color ?? '#4A90D9',
    icon: data.icon ?? '📁',
    sort_order: data.sortOrder ?? 0,
    created_at: timestampToIso(data.createdAt) ?? new Date().toISOString(),
    updated_at: timestampToIso(data.updatedAt) ?? new Date().toISOString(),
  };
}

// ── Web Project → Firestore doc ──────────────────────────────

function projectCreateToDoc(data: { title: string; description?: string; color?: string; icon?: string; sort_order?: number }): Record<string, unknown> {
  const now = Date.now();
  return {
    name: data.title,
    description: data.description ?? null,
    color: data.color ?? '#4A90D9',
    icon: data.icon ?? '📁',
    status: 'active',
    sortOrder: data.sort_order ?? 0,
    createdAt: now,
    updatedAt: now,
  };
}

function projectUpdateToDoc(data: Record<string, unknown>): Record<string, unknown> {
  const result: Record<string, unknown> = { updatedAt: Date.now() };
  if (data.title !== undefined) result.name = data.title;
  if (data.description !== undefined) result.description = data.description;
  if (data.color !== undefined) result.color = data.color;
  if (data.icon !== undefined) result.icon = data.icon;
  if (data.status !== undefined) result.status = data.status;
  if (data.sort_order !== undefined) result.sortOrder = data.sort_order;
  return result;
}

// ── CRUD operations ──────────────────────────────────────────

export async function getProjects(uid: string): Promise<Project[]> {
  const q = query(projectsCol(uid), orderBy('createdAt', 'desc'));
  const snap = await getDocs(q);
  return snap.docs.map((d) => docToProject(d.id, d.data(), uid));
}

export async function getProject(uid: string, projectId: string): Promise<ProjectDetail | null> {
  const snap = await getDoc(projectDoc(uid, projectId));
  if (!snap.exists()) return null;
  return { ...docToProject(snap.id, snap.data()!, uid), tasks: [] };
}

export async function createProject(
  uid: string,
  data: { title: string; description?: string; color?: string; icon?: string; sort_order?: number },
): Promise<Project> {
  const firestoreData = projectCreateToDoc(data);
  const ref = await addDoc(projectsCol(uid), firestoreData);
  return docToProject(ref.id, firestoreData, uid);
}

export async function updateProject(
  uid: string,
  projectId: string,
  data: Record<string, unknown>,
): Promise<Project> {
  const firestoreData = projectUpdateToDoc(data);
  await updateDoc(projectDoc(uid, projectId), firestoreData);
  const snap = await getDoc(projectDoc(uid, projectId));
  return docToProject(snap.id, snap.data()!, uid);
}

export async function deleteProject(uid: string, projectId: string): Promise<void> {
  await deleteDoc(projectDoc(uid, projectId));
}

// ── Real-time listener ───────────────────────────────────────

export function subscribeToProjects(
  uid: string,
  callback: (projects: Project[]) => void,
): Unsubscribe {
  const q = query(projectsCol(uid), orderBy('createdAt', 'desc'));
  return onSnapshot(q, (snap) => {
    const projects = snap.docs.map((d) => docToProject(d.id, d.data(), uid));
    callback(projects);
  });
}
