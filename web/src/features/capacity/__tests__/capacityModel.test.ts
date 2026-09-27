import { describe, expect, it } from 'vitest';
import {
  ANCHORS,
  CADENCES,
  DEFAULT_CONSTANTS,
  DEFAULT_SPEC,
  DEFAULT_STATE,
  STATE_KEYS,
  applySleepReset,
  arousalPotential,
  circadianDrag,
  criticalIntensity,
  decodePersisted,
  defaultPersisted,
  derivatives,
  diagnose,
  encodePersisted,
  gammaArousal,
  integrateBlock,
  blockMinutes,
  gradeCatalog,
  inferKind,
  letterGrade,
  nextBacklogLatch,
  prescribe,
  resolveSpec,
  route,
  type BlockSpec,
  type StateVector,
} from '../capacityModel';

const k = DEFAULT_CONSTANTS;

const spec = (over: Partial<BlockSpec>): BlockSpec => ({ ...DEFAULT_SPEC, ...over });

const state = (over: Partial<StateVector>): StateVector => ({ ...DEFAULT_STATE, ...over });

const REST = spec({ modality: 'zero', anchor: 'none', density: 'null', somatic: 'supine', scratchpad: 'single' });

describe('kernels', () => {
  it('Γ_arousal peaks at A* and decays symmetrically', () => {
    expect(gammaArousal(0.5, k)).toBeCloseTo(1, 10);
    expect(gammaArousal(0.3, k)).toBeCloseTo(gammaArousal(0.7, k), 10);
    expect(gammaArousal(0.3, k)).toBeCloseTo(Math.exp(-0.04 / 0.08), 10);
  });

  it('A_inst follows the spec weights', () => {
    const u = resolveSpec(spec({ modality: 'execution', anchor: 'music' })).u;
    expect(arousalPotential(u)).toBeCloseTo(0.4 * 0.8 + 0.25 * 0.55, 10);
    const dense = resolveSpec(spec({ modality: 'dense', anchor: 'none' })).u;
    expect(arousalPotential(dense)).toBeCloseTo(0.45 * 0.85, 10);
  });

  it('ψ(t) is flat at ψ₀ until onset, then grows exponentially', () => {
    expect(circadianDrag(0, k)).toBeCloseTo(0.1, 10);
    expect(circadianDrag(10, k)).toBeCloseTo(0.1, 10);
    expect(circadianDrag(14, k)).toBeCloseTo(0.1 * Math.E, 6);
    expect(circadianDrag(16, k)).toBeGreaterThan(circadianDrag(14, k));
  });

  it('I* matches the closed form and the sign of Φ_in', () => {
    const x = state({ E: 0.4, B: 0.3, A: 0.5, V: 0.85 });
    const psi = 0.1;
    const Istar = criticalIntensity(x, 0.2, psi, k);
    const expected = (0.85 * 0.85 * 0.6 * 1 - 0.4 * 0.3 - 0.1) / (0.45 * 0.2);
    expect(Istar).toBeCloseTo(expected, 10);
    // Below the boundary, Φ_in > 0; above it, Φ_in < 0.
    const below = derivatives(x, { ...resolveSpec(spec({ modality: 'auditory', density: 'fiction' })) }, psi, k);
    expect(below.I1).toBeLessThan(Istar);
    expect(below.phiIn).toBeGreaterThan(0);
    const hot = state({ E: 0.9, B: 0.6, A: 0.5, V: 0.85 });
    const IstarHot = criticalIntensity(hot, 0.9, psi, k);
    const above = derivatives(hot, resolveSpec(spec({ modality: 'dense', density: 'proofs' })), psi, k);
    expect(IstarHot).toBeLessThan(above.I1);
    expect(above.phiIn).toBeLessThan(0);
  });
});

describe('integration', () => {
  it('keeps the state on the unit manifold under extreme inputs', () => {
    const extremes: BlockSpec[] = [
      spec({ modality: 'dense', anchor: 'music', valuation: 'churn', density: 'proofs', context: 'scrutiny', scratchpad: 'rabbit', somatic: 'slump', cadence: 'm90' }),
      spec({ modality: 'execution', anchor: 'treadmill', context: 'scrutiny', somatic: 'ocular', cadence: 'm90' }),
      REST,
    ];
    for (const s of extremes) {
      for (const x0 of [state({ E: 0, B: 1, Fvis: 1, Fbody: 1, A: 1, V: 0 }), state({ E: 1, B: 0, Fvis: 0, Fbody: 0, A: 0, V: 1 })]) {
        const r = integrateBlock(x0, 20, resolveSpec(s), 90, k);
        for (const key of STATE_KEYS) {
          expect(r.x[key]).toBeGreaterThanOrEqual(0);
          expect(r.x[key]).toBeLessThanOrEqual(1);
          expect(Number.isFinite(r.x[key])).toBe(true);
        }
        expect(r.trace).toHaveLength(91);
      }
    }
  });

  it('zero-vector rest restores energy, decays backlog and ocular strain', () => {
    const x0 = state({ E: 0.3, B: 0.5, Fvis: 0.5, Fbody: 0.4 });
    const r = integrateBlock(x0, 4, resolveSpec(REST), 45, k);
    expect(r.x.E).toBeGreaterThan(x0.E);
    expect(r.x.B).toBeLessThan(x0.B);
    expect(r.x.Fvis).toBeLessThan(x0.Fvis);
    expect(r.x.Fbody).toBeLessThan(x0.Fbody);
    expect(r.hoursAwake).toBeCloseTo(4.75, 10);
    expect(r.mean.phiIn).toBe(0);
    expect(r.mean.phiOut).toBe(0);
  });

  it('flow-state execution under pure agency yields Φ_out > 0 and digests backlog', () => {
    const x0 = state({ E: 0.7, B: 0.4, Fvis: 0.1, Fbody: 0.1, A: 0.45, V: 0.9 });
    const r = integrateBlock(x0, 4, resolveSpec(spec({ modality: 'execution', anchor: 'music', context: 'agency' })), 45, k);
    expect(r.mean.phiOut).toBeGreaterThan(0);
    expect(r.x.B).toBeLessThan(x0.B);
    expect(r.x.Fbody).toBeGreaterThan(x0.Fbody);
    // Sustainable flow: the (1 − E) yield saturation keeps pure-agency execution near energy-neutral.
    expect(Math.abs(r.x.E - x0.E)).toBeLessThan(0.1);
  });

  it('circadian drag taxes output: the same sprint depletes E late in the phase', () => {
    const x0 = state({ E: 0.7, B: 0.2, Fvis: 0.1, Fbody: 0.1, A: 0.45, V: 0.9 });
    const b = resolveSpec(spec({ modality: 'execution', anchor: 'music', context: 'agency' }));
    const day = integrateBlock(x0, 4, b, 45, k);
    const night = integrateBlock(x0, 18, b, 45, k);
    expect(night.x.E).toBeLessThan(day.x.E);
    expect(night.mean.dE).toBeLessThan(0);
  });

  it('external scrutiny turns the same output block depleting', () => {
    const x0 = state({ E: 0.7, B: 0.4, Fvis: 0.3, Fbody: 0.3, A: 0.45, V: 0.9 });
    const agency = integrateBlock(x0, 4, resolveSpec(spec({ modality: 'execution', context: 'agency' })), 45, k);
    const scrutiny = integrateBlock(x0, 4, resolveSpec(spec({ modality: 'execution', context: 'scrutiny' })), 45, k);
    expect(scrutiny.mean.phiOut).toBeLessThan(0);
    expect(scrutiny.x.E).toBeLessThan(agency.x.E);
  });

  it('churn accrues backlog faster than literature at equal intake', () => {
    const x0 = state({ E: 0.6, B: 0.2, V: 0.5 });
    const churn = integrateBlock(x0, 4, resolveSpec(spec({ modality: 'reading', valuation: 'churn', density: 'fiction' })), 45, k);
    const art = integrateBlock(x0, 4, resolveSpec(spec({ modality: 'reading', valuation: 'art', density: 'fiction' })), 45, k);
    expect(churn.x.B).toBeGreaterThan(art.x.B);
    expect(churn.x.V).toBeLessThan(art.x.V);
  });

  it('unbuffered intake branches backlog through (1 + γ_assoc), and a rabbit hole adds Ω_switch on top', () => {
    const x0 = state({ E: 0.6, B: 0.2, V: 1 });
    const run = (scratchpad: BlockSpec['scratchpad']) => integrateBlock(x0, 4, resolveSpec(spec({ modality: 'dense', density: 'proofs', scratchpad })), 60, k);
    const tokenized = run('tokenized');
    const speculative = run('speculative');
    const rabbit = run('rabbit');
    expect(tokenized.mean.accrual).toBeCloseTo(0.35 * 0.9 * 0.85, 1);
    expect(speculative.mean.accrual).toBeGreaterThan(tokenized.mean.accrual * 1.4);
    expect(rabbit.mean.accrual).toBeGreaterThan(speculative.mean.accrual + 0.2);
    expect(rabbit.x.B).toBeGreaterThan(speculative.x.B);
    expect(speculative.x.B).toBeGreaterThan(tokenized.x.B);
    // The report decomposition is exact: dB = accrual − decay − digestion.
    expect(rabbit.mean.dB).toBeCloseTo(rabbit.mean.accrual - rabbit.mean.decay - rabbit.mean.digestion, 10);
  });

  it('novel cross-domain stimulation lifts arousal through ξ_novelty', () => {
    const x0 = state({ A: 0.2 });
    const flat = integrateBlock(x0, 4, resolveSpec(spec({ modality: 'expressive', anchor: 'none', novelty: 'monotonous' })), 25, k);
    const novel = integrateBlock(x0, 4, resolveSpec(spec({ modality: 'expressive', anchor: 'none', novelty: 'novel' })), 25, k);
    expect(arousalPotential(resolveSpec(spec({ modality: 'expressive', anchor: 'none' })).u, 0.15)).toBeCloseTo(0.4 * 0.35 + 0.15, 10);
    expect(novel.x.A).toBeGreaterThan(flat.x.A + 0.05);
  });

  it('somatic markers modulate F_body: slump > seated > treadmill', () => {
    const x0 = state({ Fbody: 0.3 });
    const s = (somatic: BlockSpec['somatic'], anchor: BlockSpec['anchor'] = 'none') =>
      integrateBlock(x0, 4, resolveSpec(spec({ modality: 'execution', somatic, anchor })), 45, k).x.Fbody;
    expect(s('slump')).toBeGreaterThan(s('seated'));
    expect(s('seated')).toBeGreaterThan(s('seated', 'treadmill'));
    expect(s('seated', 'treadmill')).toBeLessThan(x0.Fbody + 0.05);
  });

  it('reported ocular strain accelerates F_vis even on an auditory block', () => {
    const x0 = state({ Fvis: 0.3 });
    const plain = integrateBlock(x0, 4, resolveSpec(spec({ modality: 'auditory', somatic: 'seated' })), 45, k);
    const strained = integrateBlock(x0, 4, resolveSpec(spec({ modality: 'auditory', somatic: 'ocular' })), 45, k);
    expect(plain.x.Fvis).toBeLessThan(x0.Fvis);
    expect(strained.x.Fvis).toBeGreaterThan(plain.x.Fvis);
  });

  it('sleep reset restores E, clears strain and resets arousal tone', () => {
    const x = applySleepReset(state({ E: 0.2, B: 0.8, Fvis: 0.7, Fbody: 0.6, A: 0.9 }), 7.5);
    expect(x.E).toBeGreaterThan(0.9);
    expect(x.B).toBeLessThan(0.15);
    expect(x.Fvis).toBeLessThan(0.01);
    expect(x.A).toBeCloseTo(0.3, 10);
  });
});

describe('diagnostics and routing', () => {
  it('flags the burnout singularity late in the circadian phase', () => {
    const x = state({ E: 0.3, B: 0.6 });
    const d = diagnose(x, 17, 0.2, k);
    expect(d.latePhase).toBe(true);
    expect(d.regime).toBe('singularity');
    expect(route(x, d, k).quadrant).toBe('SINGULARITY');
  });

  it('distinguishes saturation (E high) from the structural singularity (E low)', () => {
    const full = state({ E: 0.95, B: 0.2, A: 0.5, V: 0.85 });
    expect(diagnose(full, 4, 0.4, k).regime).toBe('saturated');
    const jammed = state({ E: 0.3, B: 1.0, A: 0.5, V: 0.6 });
    expect(diagnose(jammed, 4, 0.4, k).regime).toBe('singularity');
    expect(diagnose(jammed, 4, 0.4, k).singularityMode).toBe('structural');
    expect(diagnose(jammed, 17, 0.4, k).singularityMode).toBe('late');
  });

  it('prescribes zero-input rest before sleep for a structural singularity', () => {
    const x = state({ E: 0.3, B: 0.4, Fvis: 0.1, Fbody: 0.1, A: 0.5, V: 0.3 });
    const d = diagnose(x, 4, 0.4, k);
    const r = route(x, d, k);
    expect(r.quadrant).toBe('SINGULARITY');
    const ps = prescribe(x, 4, d, r, k);
    expect(ps[0].kind).toBe('rest');
    expect(ps[0].spec?.modality).toBe('zero');
    expect(ps.some((p) => p.kind === 'sleep')).toBe(true);
  });

  it('marks arousal-limited intake when Γ alone closes the restorative zone', () => {
    const x = state({ E: 0.4, B: 0.3, A: 0.05, V: 0.85 });
    const d = diagnose(x, 4, 0.2, k);
    expect(d.numerator).toBeLessThanOrEqual(0);
    expect(d.numeratorOpt).toBeGreaterThan(0);
    expect(d.regime).toBe('arousal-limited');
    expect(route(x, d, k).quadrant).toBe('II');
  });

  it('raises the high-capacity guardrails from the state vector', () => {
    const optical = diagnose(state({ Fvis: 0.65 }), 4, 0.4, k);
    expect(optical.guardrails.opticalCutoff).toBe(true);
    const saturated = diagnose(state({ B: 0.7, E: 0.8 }), 4, 0.4, k);
    expect(saturated.guardrails.backlogSaturated).toBe(true);
    expect(diagnose(state({ B: 0.5 }), 4, 0.4, k, true).guardrails.backlogSaturated).toBe(true);
    const under = diagnose(state({ E: 0.7, A: 0.2 }), 4, 0.4, k);
    expect(under.guardrails.underArousal).toBe(true);
    expect(under.guardrails.trueDepletion).toBe(false);
    const depleted = diagnose(state({ E: 0.3, A: 0.2, B: 0.2, V: 0.9 }), 4, 0.4, k);
    expect(depleted.guardrails.trueDepletion).toBe(true);
    expect(route(state({ E: 0.3, A: 0.2, B: 0.2, V: 0.9 }), depleted, k).quadrant).toBe('I-A');
    const terminal = diagnose(state({ Fbody: 0.85, E: 0.8 }), 4, 0.4, k);
    expect(terminal.regime).toBe('singularity');
    expect(terminal.singularityMode).toBe('somatic');
    expect(route(state({ Fbody: 0.85, E: 0.8 }), terminal, k).title).toBe('Terminal Sleep Reset');
  });

  it('latches the backlog lock until an output block digests it or B clears', () => {
    const rest = resolveSpec(spec({ modality: 'zero' }));
    const express = resolveSpec(spec({ modality: 'expressive' }));
    const intake = resolveSpec(spec({ modality: 'reading' }));
    expect(nextBacklogLatch(false, state({ B: 0.7 }), intake, k)).toBe(true);
    expect(nextBacklogLatch(true, state({ B: 0.55 }), rest, k)).toBe(true);
    expect(nextBacklogLatch(true, state({ B: 0.55 }), intake, k)).toBe(true);
    expect(nextBacklogLatch(true, state({ B: 0.55 }), express, k)).toBe(false);
    expect(nextBacklogLatch(true, state({ B: 0.3 }), rest, k)).toBe(false);
  });

  it('routes each quadrant from its trigger predicate', () => {
    const cases: Array<[Partial<StateVector>, string]> = [
      [{ E: 0.3, B: 0.7, A: 0.5, V: 0.8 }, 'I-A'],
      // Jammed backlog at low E is Quadrant I-A even when the structural singularity also holds:
      // zero-input flush is that regime's own remedy.
      [{ E: 0.2, B: 0.9, A: 0.5, V: 0.6 }, 'I-A'],
      // Structural singularity with a clear backlog: no intake is admissible, rest before sleep.
      [{ E: 0.3, B: 0.4, Fvis: 0.1, Fbody: 0.1, A: 0.5, V: 0.3 }, 'SINGULARITY'],
      [{ E: 0.6, B: 0.7, A: 0.5, V: 0.8 }, 'I-B'],
      [{ E: 0.6, B: 0.3, Fbody: 0.6, A: 0.5, V: 0.8 }, 'III'],
      [{ E: 0.4, B: 0.3, Fvis: 0.1, Fbody: 0.1, A: 0.5, V: 0.85 }, 'II'],
      [{ E: 0.8, B: 0.2, Fvis: 0.1, Fbody: 0.1, A: 0.5, V: 0.9 }, 'IV'],
      [{ E: 0.8, B: 0.5, Fvis: 0.1, Fbody: 0.1, A: 0.5, V: 0.9 }, 'IV-B'],
    ];
    for (const [over, expected] of cases) {
      const x = state(over);
      const d = diagnose(x, 4, 0.4, k);
      expect(route(x, d, k).quadrant, JSON.stringify(over)).toBe(expected);
    }
  });
});

describe('prescription engine', () => {
  const quadrantStates: Partial<StateVector>[] = [
    { E: 0.3, B: 0.7 },
    { E: 0.6, B: 0.7 },
    { E: 0.6, B: 0.3, Fbody: 0.6 },
    { E: 0.4, B: 0.3, Fvis: 0.1, Fbody: 0.1, A: 0.5, V: 0.85 },
    { E: 0.8, B: 0.2, Fvis: 0.1, Fbody: 0.1, V: 0.9 },
    { E: 0.8, B: 0.5, Fvis: 0.1, Fbody: 0.1, V: 0.9 },
    { E: 0.3, B: 0.6 },
  ];

  it('emits two or three configurations with cadence-aligned hard boundaries', () => {
    const valid = new Set(CADENCES.map((c) => c.minutes));
    quadrantStates.forEach((over, i) => {
      const x = state(over);
      const hours = i === quadrantStates.length - 1 ? 17 : 4;
      const d = diagnose(x, hours, 0.4, k);
      const r = route(x, d, k);
      const ps = prescribe(x, hours, d, r, k);
      expect(ps.length, r.quadrant).toBeGreaterThanOrEqual(2);
      expect(ps.length).toBeLessThanOrEqual(3);
      for (const p of ps) {
        if (p.kind === 'sleep') {
          expect(p.spec).toBeNull();
          expect(p.boundMinutes).toBe(0);
        } else {
          expect(p.spec).not.toBeNull();
          expect(valid.has(p.boundMinutes)).toBe(true);
          expect(p.predicted).not.toBeNull();
        }
      }
    });
  });

  it('never prescribes intake under the backlog lock or visual intake under the optical cutoff', () => {
    const locked = state({ E: 0.45, B: 0.5, Fvis: 0.1, Fbody: 0.1, A: 0.5, V: 0.9 });
    const dl = diagnose(locked, 4, 0.2, k, true);
    const rl = route(locked, dl, k);
    expect(rl.quadrant).toBe('II');
    const pl = prescribe(locked, 4, dl, rl, k);
    expect(pl.length).toBeGreaterThanOrEqual(2);
    for (const p of pl) if (p.spec) expect(resolveSpec(p.spec).u.Ivis + resolveSpec(p.spec).u.Iaud).toBe(0);

    const blurred = state({ E: 0.45, B: 0.2, Fvis: 0.62, Fbody: 0.1, A: 0.5, V: 0.9 });
    const db = diagnose(blurred, 4, 0.2, k);
    const rb = route(blurred, db, k);
    expect(rb.quadrant).toBe('III');
    const pb = prescribe(blurred, 4, db, rb, k);
    expect(pb.length).toBeGreaterThanOrEqual(2);
    for (const p of pb) if (p.spec) expect(resolveSpec(p.spec).u.Ivis).toBe(0);
  });

  it('caps Quadrant II intake at c_II · I*(t) and leads with an arousal ramp when under-aroused', () => {
    // num = 0.85·0.85·0.55 − 0.4·0.59 − 0.1 = 0.0614 → I*(0.20) = 0.68 → 0.7·I* = 0.48:
    // audio (0.35) admissible, page reading (0.50) not.
    const x = state({ E: 0.45, B: 0.59, Fvis: 0.1, Fbody: 0.1, A: 0.5, V: 0.85 });
    const d = diagnose(x, 4, 0.2, k);
    const cap = d.IstarFiction * k.absorbCapFraction;
    expect(cap).toBeGreaterThan(0.35);
    expect(cap).toBeLessThan(0.5);
    const ps = prescribe(x, 4, d, route(x, d, k), k);
    expect(ps.some((p) => p.spec?.modality === 'auditory')).toBe(true);
    expect(ps.some((p) => p.spec?.modality === 'reading')).toBe(false);

    const under = state({ E: 0.8, B: 0.2, Fvis: 0.1, Fbody: 0.1, A: 0.2, V: 0.9 });
    const du = diagnose(under, 4, 0.9, k);
    expect(du.guardrails.underArousal).toBe(true);
    const pu = prescribe(under, 4, du, route(under, du, k), k);
    expect(pu[0].name).toMatch(/Arousal ramp/);
    expect(pu[0].spec?.novelty).toBe('novel');
    expect(pu.some((p) => p.kind === 'rest')).toBe(false);
  });

  it('caps the execution horizon before somatic strain crosses the gate', () => {
    const x = state({ E: 0.9, B: 0.1, Fvis: 0.1, Fbody: 0.45, A: 0.5, V: 1 });
    const d = diagnose(x, 4, 0.9, k);
    const r = route(x, d, k);
    const ps = prescribe(x, 4, d, r, k);
    const sprint = ps.find((p) => p.kind === 'execute' && p.spec?.anchor === 'music');
    expect(sprint).toBeDefined();
    expect(sprint!.boundMinutes).toBeLessThan(90);
    expect(sprint!.stopRule).toContain('F ≥ 0.55');
  });
});

describe('graded block catalog', () => {
  const gradeAll = (over: Partial<StateVector>, hours = 4, latch = false) => {
    const x = state(over);
    const d = diagnose(x, hours, 0.4, k, latch);
    return { x, d, r: route(x, d, k), list: gradeCatalog(x, hours, d, route(x, d, k), k) };
  };

  it('grades every catalog entry on [0, 100] with a consistent letter, best first', () => {
    const { list } = gradeAll({ E: 0.6, B: 0.3 });
    expect(list.length).toBeGreaterThanOrEqual(15);
    for (let i = 0; i < list.length; i += 1) {
      const g = list[i];
      expect(g.score).toBeGreaterThanOrEqual(0);
      expect(g.score).toBeLessThanOrEqual(100);
      expect(g.grade).toBe(letterGrade(g.score));
      if (i > 0) expect(list[i - 1].score).toBeGreaterThanOrEqual(g.score);
      if (g.entry.kind !== 'sleep') {
        expect(g.spec).not.toBeNull();
        expect(g.predicted).not.toBeNull();
      }
    }
    expect(letterGrade(80)).toBe('A');
    expect(letterGrade(64.9)).toBe('C');
    expect(letterGrade(34)).toBe('F');
  });

  it('puts execution on top in Quadrant IV and rest on top in Quadrant I-A', () => {
    const iv = gradeAll({ E: 0.85, B: 0.15, Fvis: 0.1, Fbody: 0.1, A: 0.5, V: 0.95 });
    expect(iv.r.quadrant).toBe('IV');
    expect(iv.list[0].entry.kind).toBe('execute');
    expect(iv.list[0].grade).toBe('A');
    expect(iv.list.find((g) => g.entry.kind === 'sleep')!.grade).toBe('F');

    const ia = gradeAll({ E: 0.3, B: 0.7, Fvis: 0.2, Fbody: 0.2, A: 0.5, V: 0.8 });
    expect(ia.r.quadrant).toBe('I-A');
    expect(ia.list[0].entry.kind).toBe('rest');
    for (const g of ia.list.filter((g) => g.entry.kind === 'execute')) expect(g.score).toBeLessThan(50);
  });

  it('treats a reached goal as a shortened horizon, not a violation', () => {
    // E just under the rest goal and B already clear: rest and digestion reach their goals
    // within minutes, which must not read as "boundary trips inside 15 m".
    const { list } = gradeAll({ E: 0.64, B: 0.02, Fvis: 0.05, Fbody: 0.51, A: 0.5, V: 0.98 });
    for (const g of list.filter((g) => g.entry.kind === 'rest' || g.entry.kind === 'express')) {
      expect(g.caps.some((c) => c.startsWith('boundary trips'))).toBe(false);
      expect(g.horizon).toBe(100);
      expect(g.boundMinutes).toBeGreaterThanOrEqual(15);
    }
    // Execution at F_body = 0.51 does trip the F ≥ 0.55 gate inside 15 m.
    const sprint = list.find((g) => g.entry.id === 'execute-sprint')!;
    expect(sprint.caps.some((c) => c.startsWith('boundary trips'))).toBe(true);
    expect(sprint.grade).toBe('F');
  });

  it('caps intake blocks under the backlog lock and visual blocks under the optical cutoff', () => {
    const locked = gradeAll({ E: 0.7, B: 0.5, Fvis: 0.1, Fbody: 0.1, A: 0.5, V: 0.9 }, 4, true);
    for (const g of locked.list) {
      if (!g.spec) continue;
      const u = resolveSpec(g.spec).u;
      if (u.Ivis + u.Iaud > 0) {
        expect(g.score).toBeLessThanOrEqual(15);
        expect(g.caps.some((c) => c.startsWith('backlog lock'))).toBe(true);
      }
    }
    const blurred = gradeAll({ E: 0.7, B: 0.2, Fvis: 0.7, Fbody: 0.1, A: 0.5, V: 0.9 });
    for (const g of blurred.list) {
      if (g.spec && resolveSpec(g.spec).u.Ivis > 0) expect(g.score).toBeLessThanOrEqual(15);
    }
  });

  it('rejects rest under the under-arousal gate and promotes sleep in a late-phase singularity', () => {
    const under = gradeAll({ E: 0.8, B: 0.2, Fvis: 0.1, Fbody: 0.1, A: 0.2, V: 0.9 });
    expect(under.d.guardrails.underArousal).toBe(true);
    for (const g of under.list.filter((g) => g.entry.kind === 'rest')) {
      expect(g.score).toBeLessThanOrEqual(35);
      expect(g.caps.some((c) => c.startsWith('under-arousal gate'))).toBe(true);
    }
    const late = gradeAll({ E: 0.4, B: 0.4 }, 17);
    expect(late.list[0].entry.kind).toBe('sleep');
    expect(late.list[0].grade).toBe('A');
    for (const g of late.list) if (g.spec && resolveSpec(g.spec).u.Ivis + resolveSpec(g.spec).u.Iaud > 0) expect(g.score).toBeLessThanOrEqual(10);
  });
});

describe('flexibility: intensity, custom duration, presets', () => {
  it('scales intake and output intensities and keeps them on [0, 1]', () => {
    const light = resolveSpec(spec({ modality: 'dense', intensity: 'light' })).u;
    const heavy = resolveSpec(spec({ modality: 'dense', intensity: 'heavy' })).u;
    expect(light.Ivis).toBeCloseTo(0.85 * 0.7, 10);
    expect(heavy.Ivis).toBe(1);
    expect(resolveSpec(spec({ modality: 'execution', intensity: 'heavy' })).u.O1).toBe(1);
    expect(resolveSpec(spec({ modality: 'execution' })).u.O1).toBe(0.8);
  });

  it('honours custom durations within bounds', () => {
    expect(blockMinutes(spec({ cadence: 'custom', customMinutes: 37 }))).toBe(37);
    expect(blockMinutes(spec({ cadence: 'custom', customMinutes: 1 }))).toBe(5);
    expect(blockMinutes(spec({ cadence: 'custom', customMinutes: 999 }))).toBe(240);
    expect(blockMinutes(spec({ cadence: 'm45' }))).toBe(45);
    const back = decodePersisted(JSON.stringify({ spec: { cadence: 'custom', customMinutes: 37, intensity: 'heavy' } }));
    expect(back.spec.cadence).toBe('custom');
    expect(back.spec.customMinutes).toBe(37);
    expect(back.spec.intensity).toBe('heavy');
    // A custom cadence without minutes falls back to the default cadence.
    expect(decodePersisted(JSON.stringify({ spec: { cadence: 'custom' } })).spec.cadence).toBe(DEFAULT_SPEC.cadence);
  });

  it('grades user presets alongside the built-in catalog and infers their kind', () => {
    const twinSpec: BlockSpec = { cadence: 'm25', modality: 'expressive', anchor: 'none', valuation: 'art', density: 'null', context: 'agency', scratchpad: 'tokenized', novelty: 'routine', somatic: 'supine' };
    const preset = { id: 'p1', name: 'Bass practice', spec: { ...twinSpec, cadence: 'custom' as const, customMinutes: 40 }, createdAt: '2026-09-27T00:00:00.000Z' };
    expect(inferKind(preset.spec)).toBe('express');
    expect(inferKind(spec({ modality: 'zero', anchor: 'treadmill' }))).toBe('somatic');
    expect(inferKind(spec({ modality: 'reading' }))).toBe('absorb');
    const x = state({ E: 0.6, B: 0.7, Fvis: 0.1, Fbody: 0.1, A: 0.5, V: 0.8 });
    const d = diagnose(x, 4, 0.4, k);
    const r = route(x, d, k);
    expect(r.quadrant).toBe('I-B');
    const list = gradeCatalog(x, 4, d, r, k, [preset]);
    const mine = list.find((g) => g.entry.presetId === 'p1')!;
    expect(mine).toBeDefined();
    expect(mine.entry.name).toBe('Bass practice');
    expect(mine.fit).toBe(100);
    expect(mine.grade).toMatch(/[ABC]/);

    expect(mine.spec?.cadence).toBe('custom');
    expect(mine.spec?.customMinutes).toBe(40);
    // An identical preset at the built-in cadence scores exactly like its built-in twin.
    const same = gradeCatalog(x, 4, d, r, k, [{ ...preset, id: 'p3', spec: twinSpec }]);
    const clone = same.find((g) => g.entry.presetId === 'p3')!;
    const builtIn = same.find((g) => g.entry.id === 'express-silence')!;
    expect(clone.score).toBe(builtIn.score);
    const back = decodePersisted(JSON.stringify({ presets: [preset, { id: 'bad' }, { id: 'p2', name: '   ' }] }));
    expect(back.presets).toHaveLength(1);
    expect(back.presets[0].name).toBe('Bass practice');
  });
});

describe('persistence codec', () => {
  it('round-trips a full store', () => {
    const store = defaultPersisted();
    store.x = state({ E: 0.42 });
    store.hoursAwake = 6.5;
    store.spec = spec({ modality: 'auditory', anchor: ANCHORS[1].key });
    const back = decodePersisted(encodePersisted(store));
    expect(back.x.E).toBeCloseTo(0.42, 10);
    expect(back.hoursAwake).toBe(6.5);
    expect(back.spec.modality).toBe('auditory');
    expect(back.constants).toEqual(DEFAULT_CONSTANTS);
  });

  it('falls back to defaults on corrupt or hostile payloads', () => {
    expect(decodePersisted(null)).toEqual(defaultPersisted());
    expect(decodePersisted('{not json')).toEqual(defaultPersisted());
    const hostile = JSON.stringify({ x: { E: 7, B: -1, Fvis: 'x', Fbody: 0, A: 0, V: 0 }, spec: { modality: 'evil', novelty: 'weird' }, constants: { alphaIn: 99 }, backlogLatch: 'yes' });
    const d = decodePersisted(hostile);
    expect(d.x).toEqual(DEFAULT_STATE);
    expect(d.spec.modality).toBe(DEFAULT_SPEC.modality);
    expect(d.spec.novelty).toBe('routine');
    expect(d.constants.alphaIn).toBe(2);
    expect(d.backlogLatch).toBe(false);
  });
});
