import { describe, expect, it } from 'vitest';
import { DEFAULT_CONSTANTS, DEFAULT_STATE, diagnose, gradeCatalog, route, type StateVector } from '../capacityModel';
import { PLAIN_SERIES, joinEffects, plainCap, plainEffect, plainQuadrant, plainReason, plainRegime } from '../capacityCopy';

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

  it('explains a graded block in one sentence', () => {
    const x = state({ E: 0.85, B: 0.15, Fvis: 0.1, Fbody: 0.1, A: 0.5, V: 0.95 });
    const d = diagnose(x, 4, 0.9, k);
    const list = gradeCatalog(x, 4, d, route(x, d, k), k);
    expect(plainReason(list[0], x)).toMatch(/^Fits what you need now;/);
    const locked = list.find((g) => g.caps.length > 0);
    if (locked) expect(plainReason(locked, x)).not.toMatch(/[ΓΦψ]/);
  });
});
