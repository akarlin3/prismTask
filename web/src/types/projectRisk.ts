/**
 * Mirrors the Android `ProjectRiskEntity` and `RiskLevel` enum
 * (`app/src/main/java/.../data/local/entity/ProjectRiskEntity.kt`,
 * `domain/model/RiskLevel.kt`).
 *
 * Storage is a top-level Firestore collection
 * `users/<uid>/project_risks` keyed by `projectCloudId`.
 *
 * Added in v1.8.x as part of the PrismTask-timeline-class web port.
 */
export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH';

/** Default for unknown / null storage values, matching `RiskLevel.fromStorage`. */
export const DEFAULT_RISK_LEVEL: RiskLevel = 'MEDIUM';

export function parseRiskLevel(value: unknown): RiskLevel {
  return value === 'LOW' || value === 'HIGH' ? value : DEFAULT_RISK_LEVEL;
}

export interface ProjectRisk {
  id: string;
  project_id: string;
  title: string;
  level: RiskLevel;
  mitigation: string | null;
  resolved_at: number | null;
  created_at: number;
  updated_at: number;
}

export interface ProjectRiskCreate {
  title: string;
  level?: RiskLevel;
  mitigation?: string | null;
}

export interface ProjectRiskUpdate {
  title?: string;
  level?: RiskLevel;
  mitigation?: string | null;
  resolved_at?: number | null;
}
