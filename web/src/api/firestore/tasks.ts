import {
  collection,
  doc,
  getDoc,
  getDocs,
  addDoc,
  updateDoc,
  deleteDoc,
  query,
  where,
  orderBy,
  onSnapshot,
  type Unsubscribe,
  type DocumentData,
} from 'firebase/firestore';
import { firestore } from '@/lib/firebase';
import type { Task, TaskStatus } from '@/types/task';
import {
  timestampToDateStr,
  timestampToTimeStr,
  timestampToIso,
  dateStrToTimestamp,
  isoToTimestamp,
  startOfTodayMs,
  endOfTodayMs,
  startOfDaysFromNowMs,
  androidToWebPriority,
  webToAndroidPriority,
} from './converters';

// ── Collection reference ──────────────────────────────────────

function tasksCol(uid: string) {
  return collection(firestore, 'users', uid, 'tasks');
}

function taskDoc(uid: string, taskId: string) {
  return doc(firestore, 'users', uid, 'tasks', taskId);
}

// ── Firestore doc → Web Task ──────────────────────────────────

function docToTask(docId: string, data: DocumentData, uid: string): Task {
  return {
    id: docId,
    project_id: data.projectId ?? '',
    user_id: uid,
    parent_id: data.parentTaskId ?? null,
    title: data.title ?? '',
    description: data.description ?? null,
    notes: data.notes ?? null,
    status: mapStatus(data),
    priority: androidToWebPriority(data.priority ?? 0),
    due_date: timestampToDateStr(data.dueDate),
    due_time: timestampToTimeStr(data.dueTime),
    planned_date: timestampToDateStr(data.plannedDate),
    completed_at: timestampToIso(data.completedAt),
    urgency_score: 0,
    recurrence_json: data.recurrenceRule ?? null,
    eisenhower_quadrant: data.eisenhowerQuadrant ?? null,
    eisenhower_updated_at: timestampToIso(data.eisenhowerUpdatedAt),
    estimated_duration: data.estimatedDuration ?? null,
    actual_duration: null,
    sort_order: data.sortOrder ?? 0,
    depth: 0,
    created_at: timestampToIso(data.createdAt) ?? new Date().toISOString(),
    updated_at: timestampToIso(data.updatedAt) ?? new Date().toISOString(),
    subtasks: [],
    tags: [],
  };
}

function mapStatus(data: DocumentData): TaskStatus {
  // If the doc has a web-style status field, prefer it
  if (data.webStatus) return data.webStatus as TaskStatus;
  return data.isCompleted ? 'done' : 'todo';
}

// ── Web Task → Firestore doc ──────────────────────────────────

function taskCreateToDoc(data: Partial<Task> & { title: string }): Record<string, unknown> {
  const now = Date.now();
  return {
    title: data.title,
    description: data.description ?? null,
    notes: data.notes ?? null,
    priority: webToAndroidPriority(data.priority ?? 4),
    isCompleted: data.status === 'done',
    webStatus: data.status ?? 'todo',
    projectId: data.project_id ?? '',
    parentTaskId: data.parent_id ?? null,
    dueDate: dateStrToTimestamp(data.due_date),
    dueTime: null,
    plannedDate: dateStrToTimestamp(data.planned_date),
    recurrenceRule: data.recurrence_json ?? null,
    estimatedDuration: data.estimated_duration ?? null,
    eisenhowerQuadrant: data.eisenhower_quadrant ?? null,
    eisenhowerUpdatedAt: data.eisenhower_updated_at ? isoToTimestamp(data.eisenhower_updated_at) : null,
    sortOrder: data.sort_order ?? 0,
    isFlagged: false,
    lifeCategory: null,
    tags: [],
    createdAt: now,
    updatedAt: now,
    completedAt: data.status === 'done' ? now : null,
    archivedAt: null,
  };
}

function taskUpdateToDoc(data: Record<string, unknown>): Record<string, unknown> {
  const doc: Record<string, unknown> = { updatedAt: Date.now() };
  if (data.title !== undefined) doc.title = data.title;
  if (data.description !== undefined) doc.description = data.description;
  if (data.notes !== undefined) doc.notes = data.notes;
  if (data.priority !== undefined) doc.priority = webToAndroidPriority(data.priority as number);
  if (data.status !== undefined) {
    doc.isCompleted = data.status === 'done';
    doc.webStatus = data.status;
    if (data.status === 'done') doc.completedAt = Date.now();
  }
  if (data.project_id !== undefined) doc.projectId = data.project_id;
  if (data.parent_id !== undefined) doc.parentTaskId = data.parent_id;
  if (data.due_date !== undefined) doc.dueDate = dateStrToTimestamp(data.due_date as string | null);
  if (data.planned_date !== undefined) doc.plannedDate = dateStrToTimestamp(data.planned_date as string | null);
  if (data.sort_order !== undefined) doc.sortOrder = data.sort_order;
  if (data.recurrence_json !== undefined) doc.recurrenceRule = data.recurrence_json;
  if (data.estimated_duration !== undefined) doc.estimatedDuration = data.estimated_duration;
  if (data.eisenhower_quadrant !== undefined) doc.eisenhowerQuadrant = data.eisenhower_quadrant;
  return doc;
}

// ── CRUD operations ──────────────────────────────────────────

export async function getTodayTasks(uid: string): Promise<Task[]> {
  const todayStart = startOfTodayMs();
  const todayEnd = endOfTodayMs();
  const q = query(
    tasksCol(uid),
    where('dueDate', '>=', todayStart),
    where('dueDate', '<', todayEnd),
    where('isCompleted', '==', false),
  );
  const snap = await getDocs(q);
  return snap.docs.map((d) => docToTask(d.id, d.data(), uid));
}

export async function getOverdueTasks(uid: string): Promise<Task[]> {
  const todayStart = startOfTodayMs();
  const q = query(
    tasksCol(uid),
    where('dueDate', '<', todayStart),
    where('isCompleted', '==', false),
    orderBy('dueDate', 'asc'),
  );
  const snap = await getDocs(q);
  return snap.docs.map((d) => docToTask(d.id, d.data(), uid));
}

export async function getUpcomingTasks(uid: string, days = 7): Promise<Task[]> {
  const todayEnd = endOfTodayMs();
  const futureEnd = startOfDaysFromNowMs(days + 1);
  const q = query(
    tasksCol(uid),
    where('dueDate', '>=', todayEnd),
    where('dueDate', '<', futureEnd),
    where('isCompleted', '==', false),
    orderBy('dueDate', 'asc'),
  );
  const snap = await getDocs(q);
  return snap.docs.map((d) => docToTask(d.id, d.data(), uid));
}

export async function getTasksByProject(uid: string, projectId: string): Promise<Task[]> {
  const q = query(
    tasksCol(uid),
    where('projectId', '==', projectId),
    orderBy('sortOrder', 'asc'),
  );
  const snap = await getDocs(q);
  return snap.docs.map((d) => docToTask(d.id, d.data(), uid));
}

export async function getAllTasks(uid: string): Promise<Task[]> {
  const q = query(tasksCol(uid), orderBy('sortOrder', 'asc'));
  const snap = await getDocs(q);
  return snap.docs.map((d) => docToTask(d.id, d.data(), uid));
}

export async function getTask(uid: string, taskId: string): Promise<Task | null> {
  const snap = await getDoc(taskDoc(uid, taskId));
  if (!snap.exists()) return null;
  return docToTask(snap.id, snap.data()!, uid);
}

export async function createTask(
  uid: string,
  data: Partial<Task> & { title: string },
): Promise<Task> {
  const firestoreData = taskCreateToDoc(data);
  const ref = await addDoc(tasksCol(uid), firestoreData);
  return docToTask(ref.id, firestoreData, uid);
}

export async function updateTask(
  uid: string,
  taskId: string,
  data: Record<string, unknown>,
): Promise<Task> {
  const firestoreData = taskUpdateToDoc(data);
  await updateDoc(taskDoc(uid, taskId), firestoreData);
  // Re-read the full document to return updated task
  const snap = await getDoc(taskDoc(uid, taskId));
  return docToTask(snap.id, snap.data()!, uid);
}

export async function deleteTask(uid: string, taskId: string): Promise<void> {
  await deleteDoc(taskDoc(uid, taskId));
}

export async function getSubtasks(uid: string, parentTaskId: string): Promise<Task[]> {
  const q = query(
    tasksCol(uid),
    where('parentTaskId', '==', parentTaskId),
    orderBy('sortOrder', 'asc'),
  );
  const snap = await getDocs(q);
  return snap.docs.map((d) => docToTask(d.id, d.data(), uid));
}

// ── Real-time listener ───────────────────────────────────────

export function subscribeToTasks(
  uid: string,
  callback: (tasks: Task[]) => void,
): Unsubscribe {
  const q = query(tasksCol(uid), orderBy('updatedAt', 'desc'));
  return onSnapshot(q, (snap) => {
    const tasks = snap.docs.map((d) => docToTask(d.id, d.data(), uid));
    callback(tasks);
  });
}
