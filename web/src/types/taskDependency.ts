/**
 * Mirrors the Android `TaskDependencyEntity`
 * (`data/local/entity/TaskDependencyEntity.kt`).
 *
 * A directed edge: completing `blocker_task_id` unblocks
 * `blocked_task_id`. Edges are immutable once written — they are added
 * or deleted, never updated. Storage is a top-level Firestore
 * collection `users/<uid>/task_dependencies` whose docs reference the
 * two task cloud IDs.
 *
 * Cycle prevention happens at write time via `dependencyCycleGuard.ts`
 * (mirrors Android `domain/usecase/DependencyCycleGuard.kt`).
 *
 * Added in v1.8.x as part of the PrismTask-timeline-class web port.
 */
export interface TaskDependency {
  id: string;
  blocker_task_id: string;
  blocked_task_id: string;
  created_at: number;
}

export interface TaskDependencyCreate {
  blocker_task_id: string;
  blocked_task_id: string;
}
