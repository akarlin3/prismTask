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

  it('an unbuffered rabbit hole adds Ω_switch·Δt of backlog versus a tokenized tangent', () => {
    const x0 = state({ E: 0.6, B: 0.2 });
    const tokenized = integrateBlock(x0, 4, resolveSpec(spec({ modality: 'dense', scratchpad: 'tokenized' })), 60, k);
    const rabbit = integrateBlock(x0, 4, resolveSpec(spec({ modality: 'dense', scratchpad: 'rabbit' })), 60, k);
    // The extra accrual is 0.25/h minus its own λ-decay over the hour.
    expect(rabbit.x.B - tokenized.x.B).toBeGreaterThan(0.2);
    expect(rabbit.x.B - tokenized.x.B).toBeLessThanOrEqual(0.25);
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
    const hostile = JSON.stringify({ x: { E: 7, B: -1, Fvis: 'x', Fbody: 0, A: 0, V: 0 }, spec: { modality: 'evil' }, constants: { alphaIn: 99 } });
    const d = decodePersisted(hostile);
    expect(d.x).toEqual(DEFAULT_STATE);
    expect(d.spec.modality).toBe(DEFAULT_SPEC.modality);
    expect(d.constants.alphaIn).toBe(2);
  });
});
