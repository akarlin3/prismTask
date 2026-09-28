import { describe, expect, it } from 'vitest';
import { DEFAULT_CONSTANTS, DEFAULT_STATE, diagnose, gradeCatalog, route, type StateVector } from '../capacityModel';
import { GUARDRAIL_LABEL, PLAIN_SERIES, STANDING_LABEL, joinEffects, plainCap, plainComparison, plainDifferences, plainEffect, plainGuardrail, plainQuadrant, plainReason, plainRegime } from '../capacityCopy';

const k = DEFAULT_CONSTANTS;
const state = (over: Partial<StateVector>): StateVector => ({ ...DEFAULT_STATE, ...over });

describe('plain-language copy', () => {
  it('names every state variable with a meaning', () => {
    expect(PLAIN_SERIES.map((s) => s.key)).toEqual(['E', 'B', 'Fvis', 'Fbody', 'A', 'V']);
    for (const s of PLAIN_SERIES) expect(s.meaning.length).toBeGreaterThan(10);
  });

  it('gives every quadrant a headline and guidance without symbols', () => {
    const cases: Partial<StateVector>[] = [
      { E: 0.3, B: 0.7 },
      { E: 0.6, B: 0.7 },
      { E: 0.4, B: 0.3, V: 0.85 },
      { E: 0.6, B: 0.3, Fbody: 0.6 },
      { E: 0.8, B: 0.2, V: 0.9 },
      { E: 0.8, B: 0.5, V: 0.9 },
      { E: 0.3, B: 0.4, V: 0.3 },
    ];
    for (const over of cases) {
      const x = state({ Fvis: 0.1, Fbody: 0.1, A: 0.5, ...over });
      const r = route(x, diagnose(x, 4, 0.4, k), k);
      const q = plainQuadrant(r);
      expect(q.headline.length).toBeGreaterThan(3);
      expect(q.guidance).not.toMatch(/[ΓΦψ]/);
    }
    expect(plainQuadrant(route(state({ E: 0.3 }), diagnose(state({ E: 0.3 }), 17, 0.4, k), k)).headline).toBe('Stop and sleep');
  });

  it('labels regimes and caps in words', () => {
    expect(plainRegime(diagnose(state({ E: 0.5, B: 0.2, V: 0.85, A: 0.5 }), 4, 0.2, k)).tone).toBe('good');
    expect(plainRegime(diagnose(state({ E: 0.95 }), 4, 0.2, k)).label).toBe('Nothing to refill');
    expect(plainRegime(diagnose(state({ E: 0.3 }), 17, 0.2, k)).tone).toBe('bad');
    expect(plainCap('backlog lock: B ≥ 0.65 until an output block runs')).toMatch(/locked/);
    expect(plainCap('something else')).toBe('something else');
  });

  it('describes predicted effects and joins them readably', () => {
    const before = state({ E: 0.5, B: 0.5, Fbody: 0.5, A: 0.5 });
    expect(plainEffect(before, { ...before, E: 0.6, B: 0.3, Fbody: 0.3 })).toEqual(['restores energy', 'clears backlog', 'eases strain']);
    expect(plainEffect(before, before)).toEqual(['roughly neutral']);
    expect(joinEffects(['a', 'b', 'c'])).toBe('a, b and c');
    expect(joinEffects(['a'])).toBe('a');
  });

  it('explains a scored block in one sentence', () => {
    const x = state({ E: 0.85, B: 0.15, Fvis: 0.1, Fbody: 0.1, A: 0.5, V: 0.95 });
    const d = diagnose(x, 4, 0.9, k);
    const list = gradeCatalog(x, 4, d, route(x, d, k), k);
    expect(plainReason(list[0], x)).toMatch(/^Fits what your state calls for;/);
    const locked = list.find((g) => g.caps.length > 0);
    if (locked) expect(plainReason(locked, x)).not.toMatch(/[ΓΦψ]/);
  });

  it('names the guardrail on a warning instead of folding it into the verdict', () => {
    const x = state({ E: 0.7, B: 0.7, Fvis: 0.1, Fbody: 0.1, A: 0.5, V: 0.9 });
    const d = diagnose(x, 4, 0.4, k, true);
    const list = gradeCatalog(x, 4, d, route(x, d, k), k);
    const locked = list.find((g) => g.guardrails.some((w) => w.type === 'backlogLock'))!;
    expect(locked).toBeDefined();
    const w = plainGuardrail(locked.guardrails.find((g) => g.type === 'backlogLock')!);
    expect(w.label).toBe('Backlog lock');
    expect(w.text).toMatch(/locked until you write/);
    // A locked backlog is an efficiency note, not a severe warning.
    expect(w.severity).toBe('note');
    // The reason and the comparison describe fit and effect without judging; the guardrail is a separate line.
    expect(plainReason(locked, x)).toMatch(/^(Fits what your state calls for|A fair fit for your state|A loose fit for your state|Not what your state calls for right now);/);
    expect(plainReason(locked, x)).not.toMatch(/wrong|priority/i);
    expect(plainComparison(locked)).not.toMatch(/Not now/);
    expect(Object.keys(GUARDRAIL_LABEL)).toEqual(['singularity', 'backlogLock', 'opticalCutoff', 'depletingIntake', 'underArousal', 'terminalStrain', 'boundary', 'notIndicated']);
    for (const label of Object.values(GUARDRAIL_LABEL)) expect(label).toMatch(/^[A-Z]/);
  });

  it('compares every block with the best one in words, never with a letter', () => {
    const x = state({ E: 0.85, B: 0.15, Fvis: 0.1, Fbody: 0.1, A: 0.5, V: 0.95 });
    const d = diagnose(x, 4, 0.9, k);
    const list = gradeCatalog(x, 4, d, route(x, d, k), k);
    const best = list[0];
    expect(plainComparison(best)).toBe('This is the recommended block for your state right now.');
    const escaped = best.entry.name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    for (const g of list.slice(1)) {
      const text = plainComparison(g);
      expect(text).not.toMatch(/\b[A-F]\b(?! )|grade/i);
      expect(text).toContain(best.entry.name);
      expect(text).toMatch(/\.$/);
      // Judgement-free: differences are named, the block is never ranked as better, worse or behind.
      expect(text).not.toMatch(/\b(behind|better|worse|wrong|bad|good)\b/i);
      expect(text).toMatch(new RegExp(`^(Compared with the recommended block \\(${escaped}\\): |About the same result as the recommended block \\(${escaped}\\)\\.$)`));
    }
    expect(Object.values(STANDING_LABEL)).toEqual(['Top match', 'Close match', 'Partial match', 'Different path']);
    for (const label of Object.values(STANDING_LABEL)) expect(label).not.toMatch(/behind|better|worse|good|bad/i);
    const c = { ...best.comparison, deltas: { E: -0.05, B: 0.03, Fvis: 0, Fbody: 0, A: 0, V: 0 }, strainDelta: 0.04, arousalErrorDelta: -0.03 };
    expect(plainDifferences(c)).toEqual(['less energy', 'more backlog', 'more strain']);
    expect(plainDifferences({ ...c, strainDelta: 0 })).toEqual(['less energy', 'more backlog', 'activation closer to the sweet spot']);
    expect(plainDifferences({ ...c, deltas: null })).toEqual([]);
  });

  it('names the limit a boundary trip hits, in words', () => {
    expect(plainCap('boundary trips inside 15 m (F ≥ 0.60)')).toBe('Strain would reach its limit within fifteen minutes.');
    expect(plainCap('boundary trips inside 15 m (F_vis ≥ 0.60)')).toBe('Your eyes would reach their limit within fifteen minutes.');
    expect(plainCap('boundary trips inside 15 m (E < 0.30)')).toBe('Energy would drop below its floor within fifteen minutes.');
    expect(plainCap('boundary trips inside 15 m (t_late)')).toBe('The late-phase limit would trip within fifteen minutes.');
    expect(plainCap('boundary trips inside 15 m (B ≥ 0.60)')).toBe('Backlog would fill up within fifteen minutes.');
    expect(plainCap('boundary trips inside 15 m (Φ_in < 0)')).toMatch(/^Intake would start to drain/);
    expect(plainCap('boundary trips inside 15 m (Φ_out < 0)')).toMatch(/^Output would start to cost/);
    expect(plainCap('under-arousal gate: A = 0.20 with E = 0.70 — rest rejected')).not.toMatch(/will not help/);
  });
});
