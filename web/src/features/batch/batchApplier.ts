import {
  doc,
  updateDoc,
  getDoc,
  addDoc,
  deleteDoc,
  collection,
  query,
  where,
  getDocs,
  type DocumentData,
} from 'firebase/firestore';
import { firestore } from '@/lib/firebase';
import { dateStrToTimestamp } from '@/api/firestore/converters';
import { setTagsForTask } from '@/api/firestore/tasks';
import * as firestoreTags from '@/api/firestore/tags';
import type {
  BatchMutationType,
  BatchUndoLogEntry,
  ProposedMutation,
} from '@/types/batch';

/**
 * Applies and undoes a single batch mutation against Firestore.
 *
 * Mirrors the Android `BatchOperationsRepository` shape but operates
 * directly on Firestore docs instead of going through Room. Each apply
 * captures a `pre_state` snapshot of exactly the fields it overwrites,
 * which is what the undo path reads back.
 *
 * Scope:
 *   - TASK: RESCHEDULE, DELETE, COMPLETE, PRIORITY_CHANGE, PROJECT_MOVE,
 *     TAG_CHANGE (slice 15 wired task tag persistence + resolver).
 *   - HABIT: COMPLETE, SKIP, ARCHIVE, DELETE
 *   - PROJECT: ARCHIVE, DELETE
 *
 * Deferred (mutation returned as skipped):
 *   - MEDICATION: matches Android's "Option C" — plan accepted, no write.
 */

export interface ApplyOutcome {
  applied: boolean;
  entry?: BatchUndoLogEntry;
  reason?: string;
}

function taskDoc(uid: string, id: string) {
  return doc(firestore, 'users', uid, 'tasks', id);
}

function habitDoc(uid: string, id: string) {
  return doc(firestore, 'users', uid, 'habits', id);
}

function projectDoc(uid: string, id: string) {
  return doc(firestore, 'users', uid, 'projects', id);
}

function habitCompletionsCol(uid: string) {
  return collection(firestore, 'users', uid, 'habit_completions');
}

function parseIsoDateToMillis(iso: string): number | null {
  const ms = dateStrToTimestamp(iso);
  return ms == null || Number.isNaN(ms) ? null : ms;
}

/** Apply one proposed mutation. Returns the undo-log entry on success.
 *  On failure, returns `{ applied: false, reason }` — the caller records
 *  this as a skipped entry so the undo log still reflects the batch. */
export async function applyMutation(
  uid: string,
  mutation: ProposedMutation,
): Promise<ApplyOutcome> {
  const { entity_type, mutation_type, entity_id, proposed_new_values } = mutation;
  try {
    switch (entity_type) {
      case 'TASK':
        return await applyTaskMutation(
          uid,
          entity_id,
          mutation_type,
          proposed_new_values,
        );
      case 'HABIT':
        return await applyHabitMutation(
          uid,
          entity_id,
          mutation_type,
          proposed_new_values,
        );
      case 'PROJECT':
        return await applyProjectMutation(uid, entity_id, mutation_type);
      case 'MEDICATION':
        return { applied: false, reason: 'medication mutations deferred to follow-up' };
    }
  } catch (e) {
    return { applied: false, reason: (e as Error).message || 'apply failed' };
  }
}

async function applyTaskMutation(
  uid: string,
  id: string,
  mutationType: BatchMutationType,
  values: Record<string, unknown>,
): Promise<ApplyOutcome> {
  const snap = await getDoc(taskDoc(uid, id));
  if (!snap.exists()) return { applied: false, reason: 'task not found' };
  const data = snap.data() as DocumentData;
  const now = Date.now();

  switch (mutationType) {
    case 'RESCHEDULE': {
      const newDue = typeof values.due_date === 'string'
        ? parseIsoDateToMillis(values.due_date)
        : null;
      await updateDoc(taskDoc(uid, id), {
        dueDate: newDue,
        updatedAt: now,
      });
      return {
        applied: true,
        entry: {
          entity_type: 'TASK',
          entity_id: id,
          mutation_type: mutationType,
          pre_state: {
            dueDate: data.dueDate ?? null,
            scheduledStartTime: data.scheduledStartTime ?? null,
          },
          applied: true,
        },
      };
    }
    case 'DELETE': {
      await updateDoc(taskDoc(uid, id), { archivedAt: now, updatedAt: now });
      return {
        applied: true,
        entry: {
          entity_type: 'TASK',
          entity_id: id,
          mutation_type: mutationType,
          pre_state: { archivedAt: data.archivedAt ?? null },
          applied: true,
        },
      };
    }
    case 'COMPLETE': {
      await updateDoc(taskDoc(uid, id), {
        isCompleted: true,
        completedAt: now,
        webStatus: 'done',
        updatedAt: now,
      });
      return {
        applied: true,
        entry: {
          entity_type: 'TASK',
          entity_id: id,
          mutation_type: mutationType,
          pre_state: {
            isCompleted: data.isCompleted ?? false,
            completedAt: data.completedAt ?? null,
            webStatus: data.webStatus ?? (data.isCompleted ? 'done' : 'todo'),
          },
          applied: true,
        },
      };
    }
    case 'PRIORITY_CHANGE': {
      const newPriority = typeof values.priority === 'number' ? values.priority : null;
      if (newPriority == null) {
        return { applied: false, reason: 'missing priority value' };
      }
      await updateDoc(taskDoc(uid, id), {
        priority: newPriority,
        updatedAt: now,
      });
      return {
        applied: true,
        entry: {
          entity_type: 'TASK',
          entity_id: id,
          mutation_type: mutationType,
          pre_state: { priority: data.priority ?? 0 },
          applied: true,
        },
      };
    }
    case 'PROJECT_MOVE': {
      const newProjectId = typeof values.project_id === 'string'
        ? values.project_id
        : null;
      await updateDoc(taskDoc(uid, id), {
        projectId: newProjectId,
        updatedAt: now,
      });
      return {
        applied: true,
        entry: {
          entity_type: 'TASK',
          entity_id: id,
          mutation_type: mutationType,
          pre_state: { projectId: data.projectId ?? null },
          applied: true,
        },
      };
    }
    case 'TAG_CHANGE': {
      const addNames = Array.isArray(values.tags_added)
        ? (values.tags_added as unknown[]).filter(
            (x): x is string => typeof x === 'string',
          )
        : [];
      const removeNames = Array.isArray(values.tags_removed)
        ? (values.tags_removed as unknown[]).filter(
            (x): x is string => typeof x === 'string',
          )
        : [];
      // Resolve names -> tag IDs. Unknown names are auto-created, matching
      // Android's `applyTagDelta` behavior in BatchOperationsRepository.kt.
      const allTags = await firestoreTags.getTags(uid);
      const lowerToTag = new Map(
        allTags.map((t) => [t.name.toLowerCase(), t]),
      );
      const addIds: string[] = [];
      for (const name of addNames) {
        const existing = lowerToTag.get(name.toLowerCase());
        if (existing) {
          addIds.push(existing.id);
          continue;
        }
        const created = await firestoreTags.createTag(uid, { name });
        lowerToTag.set(name.toLowerCase(), created);
        addIds.push(created.id);
      }
      const removeIds = new Set(
        removeNames
          .map((n) => lowerToTag.get(n.toLowerCase())?.id)
          .filter((id): id is string => typeof id === 'string'),
      );
      const priorIds: string[] = Array.isArray(data.tagIds)
        ? (data.tagIds as unknown[]).filter(
            (x): x is string => typeof x === 'string',
          )
        : [];
      const nextIds = Array.from(
        new Set([...priorIds.filter((id) => !removeIds.has(id)), ...addIds]),
      );
      await setTagsForTask(uid, id, nextIds);
      await updateDoc(taskDoc(uid, id), { updatedAt: now });
      return {
        applied: true,
        entry: {
          entity_type: 'TASK',
          entity_id: id,
          mutation_type: mutationType,
          pre_state: { tagIds: priorIds },
          applied: true,
        },
      };
    }
    default:
      return { applied: false, reason: `unsupported task mutation: ${mutationType}` };
  }
}

async function applyHabitMutation(
  uid: string,
  id: string,
  mutationType: BatchMutationType,
  values: Record<string, unknown>,
): Promise<ApplyOutcome> {
  const snap = await getDoc(habitDoc(uid, id));
  if (!snap.exists()) return { applied: false, reason: 'habit not found' };
  const data = snap.data() as DocumentData;
  const now = Date.now();

  switch (mutationType) {
    case 'COMPLETE': {
      const dateIso = typeof values.date === 'string'
        ? values.date
        : new Date().toISOString().slice(0, 10);
      const dateMs = parseIsoDateToMillis(dateIso) ?? now;
      const ref = await addDoc(habitCompletionsCol(uid), {
        habitCloudId: id,
        completedDate: dateMs,
        completedAt: now,
        notes: null,
      });
      return {
        applied: true,
        entry: {
          entity_type: 'HABIT',
          entity_id: id,
          mutation_type: mutationType,
          pre_state: {
            date_iso: dateIso,
            completion_doc_id: ref.id,
          },
          applied: true,
        },
      };
    }
    case 'SKIP': {
      // SKIP = delete any completion row for this (habit, date). Snapshot
      // the deleted docs so undo can re-create them.
      const dateIso = typeof values.date === 'string'
        ? values.date
        : new Date().toISOString().slice(0, 10);
      const dateMs = parseIsoDateToMillis(dateIso);
      if (dateMs == null) {
        return { applied: false, reason: 'invalid date for SKIP' };
      }
      const q = query(
        habitCompletionsCol(uid),
        where('habitCloudId', '==', id),
        where('completedDate', '==', dateMs),
      );
      const qs = await getDocs(q);
      const deleted: Array<{ id: string; data: DocumentData }> = [];
      for (const d of qs.docs) {
        deleted.push({ id: d.id, data: d.data() });
        await deleteDoc(d.ref);
      }
      return {
        applied: true,
        entry: {
          entity_type: 'HABIT',
          entity_id: id,
          mutation_type: mutationType,
          pre_state: {
            date_iso: dateIso,
            deleted_completions: deleted,
          },
          applied: true,
        },
      };
    }
    case 'ARCHIVE':
    case 'DELETE': {
      await updateDoc(habitDoc(uid, id), { isArchived: true, updatedAt: now });
      return {
        applied: true,
        entry: {
          entity_type: 'HABIT',
          entity_id: id,
          mutation_type: mutationType,
          pre_state: { isArchived: data.isArchived ?? false },
          applied: true,
        },
      };
    }
    default:
      return { applied: false, reason: `unsupported habit mutation: ${mutationType}` };
  }
}

async function applyProjectMutation(
  uid: string,
  id: string,
  mutationType: BatchMutationType,
): Promise<ApplyOutcome> {
  const snap = await getDoc(projectDoc(uid, id));
  if (!snap.exists()) return { applied: false, reason: 'project not found' };
  const data = snap.data() as DocumentData;
  const now = Date.now();

  if (mutationType !== 'ARCHIVE' && mutationType !== 'DELETE') {
    return { applied: false, reason: `unsupported project mutation: ${mutationType}` };
  }
  await updateDoc(projectDoc(uid, id), {
    status: 'archived',
    archivedAt: now,
    updatedAt: now,
  });
  return {
    applied: true,
    entry: {
      entity_type: 'PROJECT',
      entity_id: id,
      mutation_type: mutationType,
      pre_state: {
        status: data.status ?? 'active',
        archivedAt: data.archivedAt ?? null,
      },
      applied: true,
    },
  };
}

// ── Undo ────────────────────────────────────────────────────────

export async function undoEntry(
  uid: string,
  entry: BatchUndoLogEntry,
): Promise<boolean> {
  if (!entry.applied) return false;
  try {
    switch (entry.entity_type) {
      case 'TASK':
        return await undoTaskEntry(uid, entry);
      case 'HABIT':
        return await undoHabitEntry(uid, entry);
      case 'PROJECT':
        return await undoProjectEntry(uid, entry);
      case 'MEDICATION':
        return false;
    }
  } catch {
    return false;
  }
}

async function undoTaskEntry(
  uid: string,
  entry: BatchUndoLogEntry,
): Promise<boolean> {
  const now = Date.now();
  const pre = entry.pre_state;
  const ref = taskDoc(uid, entry.entity_id);
  switch (entry.mutation_type) {
    case 'RESCHEDULE':
      await updateDoc(ref, {
        dueDate: pre.dueDate ?? null,
        scheduledStartTime: pre.scheduledStartTime ?? null,
        updatedAt: now,
      });
      return true;
    case 'DELETE':
      await updateDoc(ref, { archivedAt: pre.archivedAt ?? null, updatedAt: now });
      return true;
    case 'COMPLETE':
      await updateDoc(ref, {
        isCompleted: pre.isCompleted ?? false,
        completedAt: pre.completedAt ?? null,
        webStatus: pre.webStatus ?? 'todo',
        updatedAt: now,
      });
      return true;
    case 'PRIORITY_CHANGE':
      await updateDoc(ref, { priority: pre.priority ?? 0, updatedAt: now });
      return true;
    case 'PROJECT_MOVE':
      await updateDoc(ref, { projectId: pre.projectId ?? null, updatedAt: now });
      return true;
    case 'TAG_CHANGE': {
      const priorIds = Array.isArray(pre.tagIds)
        ? (pre.tagIds as unknown[]).filter(
            (x): x is string => typeof x === 'string',
          )
        : [];
      await setTagsForTask(uid, entry.entity_id, priorIds);
      return true;
    }
    default:
      return false;
  }
}

async function undoHabitEntry(
  uid: string,
  entry: BatchUndoLogEntry,
): Promise<boolean> {
  const now = Date.now();
  const pre = entry.pre_state;
  switch (entry.mutation_type) {
    case 'COMPLETE': {
      const completionId = pre.completion_doc_id;
      if (typeof completionId !== 'string') return false;
      await deleteDoc(
        doc(firestore, 'users', uid, 'habit_completions', completionId),
      );
      return true;
    }
    case 'SKIP': {
      const deleted = (pre.deleted_completions as Array<{
        id: string;
        data: DocumentData;
      }>) ?? [];
      for (const d of deleted) {
        // Best-effort re-create: we let Firestore assign a fresh doc id
        // rather than reusing the prior one (addDoc is simpler than
        // setDoc here, and the habit_completions collection has no
        // id-based references).
        await addDoc(habitCompletionsCol(uid), d.data);
      }
      return true;
    }
    case 'ARCHIVE':
    case 'DELETE': {
      await updateDoc(habitDoc(uid, entry.entity_id), {
        isArchived: pre.isArchived ?? false,
        updatedAt: now,
      });
      return true;
    }
    default:
      return false;
  }
}

async function undoProjectEntry(
  uid: string,
  entry: BatchUndoLogEntry,
): Promise<boolean> {
  const now = Date.now();
  const pre = entry.pre_state;
  await updateDoc(projectDoc(uid, entry.entity_id), {
    status: pre.status ?? 'active',
    archivedAt: pre.archivedAt ?? null,
    updatedAt: now,
  });
  return true;
}
