/**
 * Mirrors the Android `ExternalAnchor` sealed class
 * (`domain/model/ExternalAnchor.kt`) as a TypeScript discriminated
 * union, plus the storage envelope `ExternalAnchorEntity`
 * (`data/local/entity/ExternalAnchorEntity.kt`).
 *
 * The polymorphic anchor payload is JSON-encoded into the entity's
 * `anchor_json` field via `encodeExternalAnchor` / `decodeExternalAnchor`,
 * mirroring the Gson adapter in
 * `data/remote/adapter/ExternalAnchorJsonAdapter.kt`. The `type`
 * discriminator strings are the same on both platforms so JSON
 * round-trips byte-for-byte through Firestore.
 *
 * Added in v1.8.x as part of the PrismTask-timeline-class web port.
 */
export type ComparisonOpSymbol = '<' | '<=' | '>' | '>=' | '==';

export const COMPARISON_OPS: readonly ComparisonOpSymbol[] = [
  '<',
  '<=',
  '>',
  '>=',
  '==',
] as const;

export type ExternalAnchor =
  | { type: 'calendar_deadline'; epochMs: number }
  | {
      type: 'numeric_threshold';
      metric: string;
      op: ComparisonOpSymbol;
      value: number;
    }
  | { type: 'boolean_gate'; gateKey: string; expectedState: boolean };

export type ExternalAnchorVariant = ExternalAnchor['type'];

/**
 * Encode an anchor for storage in `external_anchors.anchorJson`.
 * Uses `type` keys that match the Android `ExternalAnchorJsonAdapter`
 * discriminators verbatim.
 */
export function encodeExternalAnchor(anchor: ExternalAnchor): string {
  return JSON.stringify(anchor);
}

/**
 * Decode an anchor from storage. Returns `null` on malformed JSON or
 * an unknown discriminator — mirrors the Gson adapter's
 * malformed-row-drops-the-anchor behavior so a corrupted pull doesn't
 * abort the surrounding sync. Validates per-variant required fields so
 * a partial doc decodes cleanly to null rather than a half-built variant.
 */
export function decodeExternalAnchor(json: string | null | undefined): ExternalAnchor | null {
  if (!json) return null;
  let parsed: unknown;
  try {
    parsed = JSON.parse(json);
  } catch {
    return null;
  }
  if (!parsed || typeof parsed !== 'object') return null;
  const obj = parsed as Record<string, unknown>;
  switch (obj.type) {
    case 'calendar_deadline': {
      const epochMs = obj.epochMs;
      if (typeof epochMs !== 'number' || !Number.isFinite(epochMs)) return null;
      return { type: 'calendar_deadline', epochMs };
    }
    case 'numeric_threshold': {
      const metric = obj.metric;
      const op = obj.op;
      const value = obj.value;
      if (typeof metric !== 'string') return null;
      if (typeof op !== 'string' || !COMPARISON_OPS.includes(op as ComparisonOpSymbol)) {
        return null;
      }
      if (typeof value !== 'number' || !Number.isFinite(value)) return null;
      return {
        type: 'numeric_threshold',
        metric,
        op: op as ComparisonOpSymbol,
        value,
      };
    }
    case 'boolean_gate': {
      const gateKey = obj.gateKey;
      const expectedState = obj.expectedState;
      if (typeof gateKey !== 'string') return null;
      if (typeof expectedState !== 'boolean') return null;
      return { type: 'boolean_gate', gateKey, expectedState };
    }
    default:
      return null;
  }
}

export interface ExternalAnchorRecord {
  id: string;
  project_id: string;
  /** Optional phase parent; null = anchor belongs to the project as a whole. */
  phase_id: string | null;
  label: string;
  anchor: ExternalAnchor;
  created_at: number;
  updated_at: number;
}

export interface ExternalAnchorCreate {
  label: string;
  anchor: ExternalAnchor;
  phase_id?: string | null;
}

export interface ExternalAnchorUpdate {
  label?: string;
  anchor?: ExternalAnchor;
  phase_id?: string | null;
}
