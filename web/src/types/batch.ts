/**
 * Types matching `backend/app/schemas/ai.py` — BatchParseRequest /
 * BatchParseResponse / ProposedMutation / AmbiguousEntityHint.
 *
 * Entity and mutation string literals must match the backend regexes:
 *   _BATCH_ENTITY_TYPE_PATTERN   = "^(TASK|HABIT|PROJECT|MEDICATION)$"
 *   _BATCH_MUTATION_TYPE_PATTERN = "^(RESCHEDULE|DELETE|COMPLETE|SKIP|
 *                                   PRIORITY_CHANGE|TAG_CHANGE|PROJECT_MOVE|
 *                                   ARCHIVE|STATE_CHANGE)$"
 */

export type BatchEntityType = 'TASK' | 'HABIT' | 'PROJECT' | 'MEDICATION';

export type BatchMutationType =
  | 'RESCHEDULE'
  | 'DELETE'
  | 'COMPLETE'
  | 'SKIP'
  | 'PRIORITY_CHANGE'
  | 'TAG_CHANGE'
  | 'PROJECT_MOVE'
  | 'ARCHIVE'
  | 'STATE_CHANGE';

/** One task as the backend sees it in `BatchTaskContext`. Priority is the
 *  Android 0–4 scale (0=None, 4=Urgent); callers convert with
 *  `webToAndroidPriority` before sending. */
export interface BatchTaskContext {
  id: string;
  title: string;
  due_date?: string | null;
  scheduled_start_time?: string | null;
  priority?: number;
  project_id?: string | null;
  project_name?: string | null;
  tags?: string[];
  life_category?: string | null;
  is_completed?: boolean;
}

export interface BatchHabitContext {
  id: string;
  name: string;
  is_archived?: boolean;
}

export interface BatchProjectContext {
  id: string;
  name: string;
  status?: string | null;
}

export interface BatchMedicationContext {
  id: string;
  name: string;
}

export interface BatchUserContext {
  today: string;
  timezone: string;
  tasks: BatchTaskContext[];
  habits: BatchHabitContext[];
  projects: BatchProjectContext[];
  medications: BatchMedicationContext[];
}

export interface BatchParseRequest {
  command_text: string;
  user_context: BatchUserContext;
}

export interface ProposedMutation {
  entity_type: BatchEntityType;
  entity_id: string;
  mutation_type: BatchMutationType;
  /** Free-form dict; key names depend on mutation_type (see backend schema).
   *  Examples: `{ due_date: "2026-04-30" }` for RESCHEDULE,
   *  `{ tags_added: ["work"], tags_removed: [] }` for TAG_CHANGE. */
  proposed_new_values: Record<string, unknown>;
  human_readable_description: string;
}

export interface AmbiguousEntityHint {
  phrase: string;
  candidate_entity_type: BatchEntityType;
  candidate_entity_ids: string[];
  note?: string | null;
}

export interface BatchParseResponse {
  mutations: ProposedMutation[];
  confidence: number;
  ambiguous_entities: AmbiguousEntityHint[];
  /** Server always returns true; included for contract visibility. */
  proposed: boolean;
  /** Client-side annotation: how many mutations were dropped from
   *  `mutations` because their `entity_id` appeared in any
   *  `ambiguous_entities[].candidate_entity_ids`. Server never sets this —
   *  it's populated by the auto-strip safeguard in `batchStore`. */
  stripped_ambiguous_count?: number;
}

/** Per-entry record persisted in the batch history (localStorage, per-uid).
 *  Mirrors Android's `BatchUndoLogEntry` but without Room-specific fields. */
export interface BatchUndoLogEntry {
  entity_type: BatchEntityType;
  entity_id: string;
  mutation_type: BatchMutationType;
  /** Snapshot of the fields the mutation overwrites. Shape depends on
   *  mutation_type — see BatchOperationsRepository.kt for the reference
   *  schema. */
  pre_state: Record<string, unknown>;
  applied: boolean;
  skip_reason?: string;
}

export interface BatchHistoryRecord {
  batch_id: string;
  command_text: string;
  created_at: number; // epoch ms
  expires_at: number; // epoch ms — matches Android's 24h UNDO_WINDOW
  undone_at: number | null;
  entries: BatchUndoLogEntry[];
  applied_count: number;
  skipped_count: number;
}
