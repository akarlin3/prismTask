/**
 * Mirrors the Android `ProjectPhaseEntity`
 * (`app/src/main/java/.../data/local/entity/ProjectPhaseEntity.kt`).
 *
 * Phases group tasks under a project with date ranges and version
 * anchors. Storage is a top-level Firestore collection
 * `users/<uid>/project_phases` keyed by `projectCloudId` — the parent
 * project's Firestore doc id (which is also the web `Project.id`).
 *
 * Added in v1.8.x as part of the PrismTask-timeline-class web port
 * (Phase 2 of `WEB_PROJECT_ROADMAP_PORT_AUDIT.md`).
 */
export interface ProjectPhase {
  id: string;
  project_id: string;
  title: string;
  description: string | null;
  /** Token key resolved against the active theme palette. */
  color_key: string | null;
  /** Epoch millis (matches Android storage). `null` when unset. */
  start_date: number | null;
  end_date: number | null;
  /** Free-form version anchor at phase end (e.g. "v1.9.0"). */
  version_anchor: string | null;
  version_note: string | null;
  order_index: number;
  completed_at: number | null;
  created_at: number;
  updated_at: number;
}

export interface ProjectPhaseCreate {
  title: string;
  description?: string | null;
  color_key?: string | null;
  start_date?: number | null;
  end_date?: number | null;
  version_anchor?: string | null;
  version_note?: string | null;
  order_index?: number;
}

export interface ProjectPhaseUpdate {
  title?: string;
  description?: string | null;
  color_key?: string | null;
  start_date?: number | null;
  end_date?: number | null;
  version_anchor?: string | null;
  version_note?: string | null;
  order_index?: number;
  completed_at?: number | null;
}
