import { describe, expect, it } from 'vitest';
import { DEFAULT_CONSTANTS, DEFAULT_SPEC, DEFAULT_STATE, blendCalibration, diagnose, resolveSpec, derivatives, type BlockSpec, type StateVector } from '../capacityModel';
import { evaluatePillars, pillarSummary } from '../pillars';
import { describeBlock } from '../blockDescriber';

const k = DEFAULT_CONSTANTS;
const state = (over: Partial<StateVector>): StateVector => ({ ...DEFAULT_STATE, ...over });
const spec = (over: Partial<BlockSpec>): BlockSpec => ({ ...DEFAULT_SPEC, ...over });
const run = (s: BlockSpec, x: StateVector, hours = 4, ctx = {}) => evaluatePillars(s, x, diagnose(x, hours, 0.4, k), k, ctx);

describe('seven pillars', () => {
  it('returns all seven verdicts in constitutional order', () => {
    const v = run(spec({}), state({}));
    expect(v.map((p) => p.id)).toEqual(['impartiality', 'curiosity', 'deconstruction', 'somatic', 'creativity', 'strength', 'empathy']);
    for (const p of v) expect(p.note.length).toBeGreaterThan(5);
  });

  it('Curiosity blocks churn and flags shallow recent depth', () => {
    const churn = run(spec({ modality: 'reading', valuation: 'churn' }), state({}));
    expect(churn.find((p) => p.id === 'curiosity')!.status).toBe('block');
    const shallow = run(spec({ modality: 'reading', valuation: 'art' }), state({ V: 0.1 }));
    expect(shallow.find((p) => p.id === 'curiosity')!.status).toBe('flag');
    expect(run(spec({ modality: 'zero', valuation: 'churn' }), state({})).find((p) => p.id === 'curiosity')!.status).toBe('pass');
  });

  it('Intellectual Deconstruction flags the quadratic intake spike', () => {
    const dense = run(spec({ modality: 'dense', density: 'proofs', intensity: 'heavy' }), state({}));
    expect(dense.find((p) => p.id === 'deconstruction')!.status).toBe('block');
    const light = run(spec({ modality: 'auditory', density: 'fiction' }), state({}));
    expect(light.find((p) => p.id === 'deconstruction')!.status).toBe('pass');
    const branching = run(spec({ modality: 'reading', density: 'fiction', scratchpad: 'rabbit' }), state({}));
    expect(branching.find((p) => p.id === 'deconstruction')!.status).toBe('flag');
  });

  it('Somatic Grounding enforces the singularity and the optical cutoff', () => {
    const late = run(spec({ modality: 'reading' }), state({ E: 0.3 }), 17);
    expect(late.find((p) => p.id === 'somatic')!.status).toBe('block');
    const blurred = run(spec({ modality: 'reading' }), state({ Fvis: 0.7 }));
    expect(blurred.find((p) => p.id === 'somatic')!.status).toBe('block');
    const seatedStrain = run(spec({ modality: 'execution', somatic: 'seated' }), state({ Fbody: 0.55 }));
    expect(seatedStrain.find((p) => p.id === 'somatic')!.status).toBe('flag');
    expect(seatedStrain.find((p) => p.id === 'impartiality')!.status).toBe('flag');
  });

  it('Creativity prefers output when the backlog is loaded', () => {
    const passive = run(spec({ modality: 'reading', valuation: 'art' }), state({ B: 0.5 }));
    expect(passive.find((p) => p.id === 'creativity')!.status).toBe('flag');
    const locked = run(spec({ modality: 'reading', valuation: 'art' }), state({ B: 0.7 }));
    expect(locked.find((p) => p.id === 'creativity')!.status).toBe('block');
    const express = run(spec({ modality: 'expressive' }), state({ B: 0.7 }));
    expect(express.find((p) => p.id === 'creativity')!.status).toBe('pass');
  });

  it('Strength Through Hardship attenuates owned pressure and flags pressure without agency', () => {
    const owned = run(spec({ modality: 'execution', context: 'sprint' }), state({}));
    expect(owned.find((p) => p.id === 'strength')!.status).toBe('pass');
    const judged = run(spec({ modality: 'execution', context: 'scrutiny' }), state({}));
    expect(judged.find((p) => p.id === 'strength')!.status).toBe('flag');
    expect(run(spec({ modality: 'execution', context: 'agency' }), state({})).find((p) => p.id === 'strength')!.status).toBe('na');
    // The attenuation is in the dynamics: an owned deadline costs less than the un-attenuated drag.
    const x = state({ E: 0.7, Fvis: 0.3, Fbody: 0.3 });
    const b = resolveSpec(spec({ modality: 'execution', context: 'sprint' }));
    const withStrength = derivatives(x, b, 0.1, k).phiOut;
    const without = derivatives(x, b, 0.1, { ...k, sigmaStrength: 0 }).phiOut;
    expect(withStrength).toBeGreaterThan(without);
  });

  it('Radical Empathy reads relational stewardship from the description', () => {
    const d = describeBlock('helped my mom with her taxes, had to');
    expect(d.relational).toBe(true);
    const reactive = run(d.spec, state({}), 4, { relational: true, relationalCue: d.relationalCue });
    expect(reactive.find((p) => p.id === 'empathy')!.status).toBe('flag');
    const chosen = run(spec({ modality: 'auditory', context: 'agency' }), state({}), 4, { relational: true });
    expect(chosen.find((p) => p.id === 'empathy')!.status).toBe('pass');
    expect(run(spec({}), state({})).find((p) => p.id === 'empathy')!.status).toBe('na');
    expect(pillarSummary(reactive).flags).toBeGreaterThanOrEqual(1);
  });

  it('Objective Impartiality blends a self-report into the model estimate', () => {
    const model = state({ E: 0.7, B: 0.25 });
    const reported = state({ E: 0.2, B: 0.9 });
    const blended = blendCalibration(model, reported, 0.6);
    expect(blended.E).toBeCloseTo(0.4, 10);
    expect(blended.B).toBeCloseTo(0.64, 10);
    expect(blendCalibration(model, reported, 1)).toEqual(reported);
    expect(blendCalibration(model, reported, 0)).toEqual(model);
  });
});
