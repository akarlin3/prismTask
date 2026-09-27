/**
 * 6D Discrete-Time Capacity Controller — pure model engine.
 *
 * State vector on the unit manifold:
 *   x = [E, B, F_vis, F_body, A, V]^T ∈ [0, 1]^6
 *
 * Control / context vectors:
 *   u = [I_vis, I_aud, I_anchor, O_1, O_anchor]^T
 *   θ = [C_in, P, S_agency, ψ(t)]^T
 *
 * Everything in this file is side-effect free so it can be unit tested and
 * reused by the screen, the live block preview, and the prescription engine.
 * Rates are per hour; Δt is expressed in minutes at the API boundary.
 *
 * Closure notes (terms the governing equations reference but do not pin):
 *   • dE/dt is closed as
 *       Φ_in + Y_out·(1 − E) − K_out + R_rest − c_basal − ψ(t)·O_1
 *     where Φ_out = Y_out − K_out splits into the flow yield
 *     Y_out = η_flow S (1−P) E O_1 Γ and the cost K_out = (β_out P + ω F)(O_1² + O_anchor²).
 *     The yield saturates with the same (1 − E) factor the spec applies to
 *     restorative input, and circadian drag taxes output exactly as the
 *     −ψ(t) I_1 term taxes input; the cost is always paid in full.
 *     R_rest = ρ_E · (E_cap − E)⁺ · (1 − I_1)(1 − O_1) · restGain with
 *     E_cap = 1 − ψ(t): zero-input rest restores E toward a circadian-limited
 *     ceiling; only a sleep reset lifts the ceiling.
 *     Φ_in and Φ_out are reported exactly as the spec defines them.
 *   • ψ(t) = ψ_0 · exp(max(0, t_awake − t_onset) / τ_ψ): constant at the
 *     spec default (0.10) until the onset hour, then exponential sleep drag.
 *   • dA/dt relaxes toward max(A_inst, A_rest): tonic activation never
 *     collapses to zero during a zero-vector block.
 *   • σ_load (postural cost of output) and κ_A / κ_V (relaxation gains) are
 *     not given by the spec; defaults are documented in DEFAULT_CONSTANTS.
 *
 * Extended specification (v2):
 *   • A_inst gains an additive novelty term ξ_novelty (high-entropy,
 *     cross-domain stimulation) so under-arousal can be distinguished from
 *     depletion.
 *   • Backlog accrual carries the associative branching multiplier
 *     (1 + γ_assoc); γ_assoc ∈ [0.5, 1.0] during unbuffered speculative
 *     intake and 0 once tangents are tokenized to a scratchpad.
 *   • Guardrails: optical cutoff (F_vis ≥ 0.60 ⇒ I_vis = 0), backlog
 *     saturation lock (B ≥ 0.65 ⇒ I₁ = 0 until an output block digests it),
 *     the depletion-vs-under-arousal gate on A < 0.35, and a terminal sleep
 *     reset at F ≥ 0.80.
 */

export type StateKey = 'E' | 'B' | 'Fvis' | 'Fbody' | 'A' | 'V';
export type StateVector = Record<StateKey, number>;

export const STATE_KEYS: readonly StateKey[] = ['E', 'B', 'Fvis', 'Fbody', 'A', 'V'];

export interface ControlVector {
  Ivis: number;
  Iaud: number;
  Ianchor: number;
  O1: number;
  Oanchor: number;
}

export interface ContextVector {
  Cin: number;
  P: number;
  S: number;
}

export interface SomaticFlags {
  /** 𝟙[static seated] in the F_body equation. */
  staticSeated: boolean;
  /** 𝟙[kinetic anchor / supported] in the F_body equation. */
  kineticOrSupported: boolean;
  /** Multiplier on γ_posture (cervical/lumbar slump doubles the load). */
  postureGain: number;
  /** Multiplier on γ_vis (reported active ocular strain). */
  ocularGain: number;
  /** I_vis floor applied to the ocular equation when strain is reported. */
  ocularFloor: number;
  /** Multiplier on passive recovery R_rest (supported/supine deload). */
  restGain: number;
}

export interface BlockInputs {
  u: ControlVector;
  theta: ContextVector;
  Vtarget: number;
  omegaSwitch: number;
  /** Associative branching factor γ_assoc on backlog accrual. */
  gammaAssoc: number;
  /** Novelty stimulation ξ_novelty added to A_inst. */
  xiNovelty: number;
  somatic: SomaticFlags;
}

export interface Constants {
  // — Spec constants (§C.8) —
  alphaIn: number;
  betaIn: number;
  deltaIn: number;
  etaFlow: number;
  betaOut: number;
  omega: number;
  cBasal: number;
  kappa: number;
  lambda: number;
  mu: number;
  gammaVis: number;
  rhoVis: number;
  gammaPosture: number;
  rhoBody: number;
  psi0: number;
  // — Arousal kernel (§C.1) —
  Astar: number;
  sigmaA: number;
  // — Closure constants —
  sigmaLoad: number;
  kappaA: number;
  kappaV: number;
  rhoE: number;
  Arest: number;
  psiOnsetHours: number;
  tauPsiHours: number;
  lateHours: number;
  Vmin: number;
  // — High-capacity guardrails (§4) —
  FvisCutoff: number;
  BsatLock: number;
  AunderArousal: number;
  Fterminal: number;
  absorbCapFraction: number;
  // — Seven pillars —
  /** Strength Through Hardship: attenuates the β_out·P drag when the operator owns the deadline (S ≥ 0.7). */
  sigmaStrength: number;
  /** Objective Impartiality: Kalman gain applied to self-reported calibrations (1 = copy the report). */
  kalmanGain: number;
}

export const DEFAULT_CONSTANTS: Constants = Object.freeze({
  alphaIn: 0.85,
  betaIn: 0.45,
  deltaIn: 0.4,
  etaFlow: 0.9,
  betaOut: 0.35,
  omega: 0.5,
  cBasal: 0.05,
  kappa: 0.35,
  lambda: 0.15,
  mu: 0.4,
  gammaVis: 0.4,
  rhoVis: 0.3,
  gammaPosture: 0.25,
  rhoBody: 0.35,
  psi0: 0.1,
  Astar: 0.5,
  sigmaA: 0.2,
  sigmaLoad: 0.25,
  kappaA: 2.0,
  kappaV: 3.0,
  rhoE: 0.3,
  Arest: 0.2,
  psiOnsetHours: 10,
  tauPsiHours: 4,
  lateHours: 16,
  Vmin: 0.25,
  FvisCutoff: 0.6,
  BsatLock: 0.65,
  AunderArousal: 0.35,
  Fterminal: 0.8,
  absorbCapFraction: 0.7,
  sigmaStrength: 0.35,
  kalmanGain: 0.6,
});

export interface ConstantMeta {
  key: keyof Constants;
  symbol: string;
  label: string;
  group: 'spec' | 'closure' | 'guardrail';
  min: number;
  max: number;
  step: number;
}

export const CONSTANT_META: readonly ConstantMeta[] = [
  { key: 'alphaIn', symbol: 'α_in', label: 'Restorative input gain', group: 'spec', min: 0, max: 2, step: 0.01 },
  { key: 'betaIn', symbol: 'β_in', label: 'Density cost of input', group: 'spec', min: 0.01, max: 2, step: 0.01 },
  { key: 'deltaIn', symbol: 'δ_in', label: 'Backlog drag on input', group: 'spec', min: 0, max: 2, step: 0.01 },
  { key: 'etaFlow', symbol: 'η_flow', label: 'Flow yield gain', group: 'spec', min: 0, max: 2, step: 0.01 },
  { key: 'betaOut', symbol: 'β_out', label: 'Pressure cost of output', group: 'spec', min: 0, max: 2, step: 0.01 },
  { key: 'omega', symbol: 'ω', label: 'Somatic cost of output', group: 'spec', min: 0, max: 2, step: 0.01 },
  { key: 'cBasal', symbol: 'c_basal', label: 'Basal drain', group: 'spec', min: 0, max: 0.5, step: 0.005 },
  { key: 'kappa', symbol: 'κ', label: 'Backlog accrual', group: 'spec', min: 0, max: 2, step: 0.01 },
  { key: 'lambda', symbol: 'λ', label: 'Backlog decay', group: 'spec', min: 0, max: 2, step: 0.01 },
  { key: 'mu', symbol: 'μ', label: 'Backlog digestion by output', group: 'spec', min: 0, max: 2, step: 0.01 },
  { key: 'gammaVis', symbol: 'γ_vis', label: 'Ocular strain rate', group: 'spec', min: 0, max: 2, step: 0.01 },
  { key: 'rhoVis', symbol: 'ρ_vis', label: 'Ocular recovery rate', group: 'spec', min: 0, max: 2, step: 0.01 },
  { key: 'gammaPosture', symbol: 'γ_posture', label: 'Static posture strain', group: 'spec', min: 0, max: 2, step: 0.01 },
  { key: 'rhoBody', symbol: 'ρ_body', label: 'Kinetic recovery rate', group: 'spec', min: 0, max: 2, step: 0.01 },
  { key: 'psi0', symbol: 'ψ_0', label: 'Baseline circadian drag', group: 'spec', min: 0, max: 1, step: 0.01 },
  { key: 'Astar', symbol: 'A*', label: 'Optimal arousal tone', group: 'spec', min: 0.1, max: 0.9, step: 0.01 },
  { key: 'sigmaA', symbol: 'σ_A', label: 'Arousal tolerance', group: 'spec', min: 0.05, max: 0.6, step: 0.01 },
  { key: 'sigmaLoad', symbol: 'σ_load', label: 'Postural cost of output', group: 'closure', min: 0, max: 2, step: 0.01 },
  { key: 'kappaA', symbol: 'κ_A', label: 'Arousal relaxation gain', group: 'closure', min: 0.1, max: 10, step: 0.1 },
  { key: 'kappaV', symbol: 'κ_V', label: 'Substance relaxation gain', group: 'closure', min: 0.1, max: 10, step: 0.1 },
  { key: 'rhoE', symbol: 'ρ_E', label: 'Passive energy recovery', group: 'closure', min: 0, max: 2, step: 0.01 },
  { key: 'Arest', symbol: 'A_rest', label: 'Tonic arousal floor', group: 'closure', min: 0, max: 0.5, step: 0.01 },
  { key: 'psiOnsetHours', symbol: 't_onset', label: 'Drag onset (h awake)', group: 'closure', min: 0, max: 24, step: 0.5 },
  { key: 'tauPsiHours', symbol: 'τ_ψ', label: 'Drag e-folding time (h)', group: 'closure', min: 0.5, max: 24, step: 0.5 },
  { key: 'lateHours', symbol: 't_late', label: 'Late-phase threshold (h awake)', group: 'closure', min: 8, max: 30, step: 0.5 },
  { key: 'Vmin', symbol: 'V_min', label: 'Admissibility floor', group: 'closure', min: 0.01, max: 0.9, step: 0.01 },
  { key: 'FvisCutoff', symbol: 'F_vis^cut', label: 'Optical cutoff (force I_vis = 0)', group: 'guardrail', min: 0.2, max: 1, step: 0.01 },
  { key: 'BsatLock', symbol: 'B_sat', label: 'Backlog saturation (lock I₁ = 0)', group: 'guardrail', min: 0.2, max: 1, step: 0.01 },
  { key: 'AunderArousal', symbol: 'A_under', label: 'Under-arousal gate on A', group: 'guardrail', min: 0.05, max: 0.5, step: 0.01 },
  { key: 'Fterminal', symbol: 'F_term', label: 'Terminal sleep reset on F', group: 'guardrail', min: 0.5, max: 1, step: 0.01 },
  { key: 'absorbCapFraction', symbol: 'c_II', label: 'Quadrant II cap fraction of I*', group: 'guardrail', min: 0.1, max: 1, step: 0.05 },
  { key: 'sigmaStrength', symbol: 'σ_strength', label: 'Pressure drag attenuation when owned (S ≥ 0.7)', group: 'guardrail', min: 0, max: 1, step: 0.05 },
  { key: 'kalmanGain', symbol: 'K_filter', label: 'Trust in self-reported calibration', group: 'guardrail', min: 0, max: 1, step: 0.05 },
];

export const DEFAULT_STATE: StateVector = Object.freeze({
  E: 0.7,
  B: 0.25,
  Fvis: 0.1,
  Fbody: 0.1,
  A: 0.45,
  V: 0.6,
});

// ---------------------------------------------------------------------------
// Telemetry catalogs (segmented options in the audit form)
// ---------------------------------------------------------------------------

export type CadenceKey = 'm15' | 'm25' | 'm45' | 'm60' | 'm90';
export type IntensityKey = 'light' | 'standard' | 'heavy';
export const CUSTOM_CADENCE = 'custom';
export const MIN_CUSTOM_MINUTES = 5;
export const MAX_CUSTOM_MINUTES = 240;
/** Every block is fifteen minutes unless the user says otherwise. */
export const DEFAULT_BLOCK_LENGTH = 15;

/** A spec whose length is `minutes`: a standard cadence key when one matches, else custom. */
export function withMinutes(spec: BlockSpec, minutes: number): BlockSpec {
  const m = Math.min(MAX_CUSTOM_MINUTES, Math.max(MIN_CUSTOM_MINUTES, Math.round(minutes)));
  const standard = CADENCES.find((c) => c.minutes === m);
  if (standard) {
    const { customMinutes: _cm, ...rest } = spec;
    void _cm;
    return { ...rest, cadence: standard.key };
  }
  return { ...spec, cadence: CUSTOM_CADENCE, customMinutes: m };
}
export type ModalityKey = 'zero' | 'auditory' | 'reading' | 'dense' | 'expressive' | 'execution';
export type AnchorKey = 'none' | 'brown' | 'music' | 'fidget' | 'treadmill';
export type ValuationKey = 'churn' | 'utility' | 'art' | 'architecture';
export type DensityKey = 'null' | 'fiction' | 'analysis' | 'manuals' | 'proofs';
export type ContextKey = 'agency' | 'soft' | 'sprint' | 'scrutiny';
export type ScratchpadKey = 'single' | 'tokenized' | 'speculative' | 'rabbit';
export type NoveltyKey = 'monotonous' | 'routine' | 'novel';
export type SomaticKey = 'supine' | 'seated' | 'ocular' | 'slump';
export type SpeedKey = 'x075' | 'x1' | 'x125' | 'x15' | 'x175' | 'x2' | 'x25' | 'x3';

export interface BlockSpec {
  /** A standard cadence, or `custom` with `customMinutes`. */
  cadence: CadenceKey | typeof CUSTOM_CADENCE;
  customMinutes?: number;
  /** Scales the modality's I / O intensities: light ×0.7, standard ×1, heavy ×1.25. */
  intensity?: IntensityKey;
  /**
   * Playback speed for listening or watching (audiobook, podcast, video): scales the intake
   * channels I_vis / I_aud, i.e. the words per minute. Absent means 1×. No effect on output.
   */
  speed?: SpeedKey;
  modality: ModalityKey;
  anchor: AnchorKey;
  valuation: ValuationKey;
  density: DensityKey;
  context: ContextKey;
  scratchpad: ScratchpadKey;
  novelty: NoveltyKey;
  somatic: SomaticKey;
}

export interface Option<K extends string> {
  key: K;
  label: string;
  detail: string;
  /** Short mono parameter readout shown on the segmented button. */
  params: string;
  /** Everyday wording for the simple interface (falls back to `label`). */
  plain?: string;
}

export const CADENCES: readonly (Option<CadenceKey> & { minutes: number })[] = [
  { key: 'm15', label: '15m', detail: 'Micro-Block', params: 'Δt=0.25h', minutes: 15 },
  { key: 'm25', label: '25m', detail: 'Pomodoro', params: 'Δt=0.42h', minutes: 25 },
  { key: 'm45', label: '45m', detail: 'Standard', params: 'Δt=0.75h', minutes: 45 },
  { key: 'm60', label: '60m', detail: 'Extended', params: 'Δt=1.00h', minutes: 60 },
  { key: 'm90', label: '90m', detail: 'Ultradian', params: 'Δt=1.50h', minutes: 90 },
];

export const MODALITIES: readonly (Option<ModalityKey> & { Ivis: number; Iaud: number; O1: number })[] = [
  { key: 'zero', label: 'Nothing / Rest', plain: 'Rest, nothing going in', detail: 'Zero-vector: mask, rest, no input or output', params: 'I=0 O=0', Ivis: 0, Iaud: 0, O1: 0 },
  { key: 'auditory', label: 'Auditory Narrative', plain: 'Listening', detail: 'Audiobook, podcast, conversation', params: 'I_aud=0.35', Ivis: 0, Iaud: 0.35, O1: 0 },
  { key: 'reading', label: 'Visual Reading', plain: 'Reading or watching', detail: 'Prose on page, screens', params: 'I_vis=0.50', Ivis: 0.5, Iaud: 0, O1: 0 },
  { key: 'dense', label: 'Dense Technical In', plain: 'Studying dense material', detail: 'Papers, docs, code review', params: 'I_vis=0.85', Ivis: 0.85, Iaud: 0, O1: 0 },
  { key: 'expressive', label: 'Expressive Out', plain: 'Expressing (writing, playing)', detail: 'Journal, improv, sketching, chores', params: 'O_1=0.35', Ivis: 0, Iaud: 0, O1: 0.35 },
  { key: 'execution', label: 'Deep Execution', plain: 'Focused work (making things)', detail: 'Code, drafting, building', params: 'O_1=0.80', Ivis: 0, Iaud: 0, O1: 0.8 },
];

export const ANCHORS: readonly (Option<AnchorKey> & { Ianchor: number; Oanchor: number; kinetic: boolean })[] = [
  { key: 'none', label: 'None / Silence', plain: 'Silence', detail: 'No secondary channel', params: 'I_a=0 O_a=0', Ianchor: 0, Oanchor: 0, kinetic: false },
  { key: 'brown', label: 'Ambient Brown Noise', plain: 'Background noise', detail: 'Brown or white noise, rain, ambient', params: 'I_a=0.30', Ianchor: 0.3, Oanchor: 0, kinetic: false },
  { key: 'music', label: 'Familiar Lyrical Music', plain: 'Familiar music', detail: 'On repeat, low cognitive density', params: 'I_a=0.55', Ianchor: 0.55, Oanchor: 0, kinetic: false },
  { key: 'fidget', label: 'Tactile Fidget', plain: 'Fidget or hands busy', detail: 'Hand-scale kinetic', params: 'O_a=0.20', Ianchor: 0, Oanchor: 0.2, kinetic: false },
  { key: 'treadmill', label: 'Walking Treadmill', plain: 'Walking or moving', detail: 'Walk, treadmill, pacing, workout', params: 'O_a=0.35', Ianchor: 0, Oanchor: 0.35, kinetic: true },
];

export const VALUATIONS: readonly (Option<ValuationKey> & { V: number; admissible: boolean })[] = [
  { key: 'churn', label: 'Churn', plain: 'Scrolling, feeds, inbox', detail: 'Feeds, inbox, scrolling — inadmissible', params: 'V=0.10', V: 0.1, admissible: false },
  { key: 'utility', label: 'Utility', plain: 'Necessary stuff', detail: 'Chores, admin, meetings, errands', params: 'V=0.50', V: 0.5, admissible: true },
  { key: 'art', label: 'Literature / Art', plain: 'Books, art, music, people', detail: 'Generative or restorative', params: 'V=0.85', V: 0.85, admissible: true },
  { key: 'architecture', label: 'Deep Architecture', plain: 'Real work or craft', detail: 'Structural yield', params: 'V=1.00', V: 1.0, admissible: true },
];

export const DENSITIES: readonly (Option<DensityKey> & { Cin: number })[] = [
  { key: 'null', label: 'Null', plain: 'Nothing to take in', detail: 'No intake', params: 'C_in=0.05', Cin: 0.05 },
  { key: 'fiction', label: 'Fiction Narrative', plain: 'Light: stories, chat', detail: 'Low-entropy prose', params: 'C_in=0.20', Cin: 0.2 },
  { key: 'analysis', label: 'Structured Analysis', plain: 'Medium: articles, meetings', detail: 'Essays, reports', params: 'C_in=0.40', Cin: 0.4 },
  { key: 'manuals', label: 'System Manuals', plain: 'Dense: manuals, textbooks', detail: 'Reference, specs', params: 'C_in=0.65', Cin: 0.65 },
  { key: 'proofs', label: 'Abstract Proofs / Code', plain: 'Very dense: math, code', detail: 'Maximal entropy', params: 'C_in=0.90', Cin: 0.9 },
];

export const CONTEXTS: readonly (Option<ContextKey> & { P: number; S: number })[] = [
  { key: 'agency', label: 'Pure Agency', plain: 'My own choice', detail: 'Self-directed', params: 'P=0.00 S=1.00', P: 0, S: 1 },
  { key: 'soft', label: 'Soft Goal', plain: 'A soft goal', detail: 'Intent without stakes', params: 'P=0.25 S=0.85', P: 0.25, S: 0.85 },
  { key: 'sprint', label: 'Sprint Deliverable', plain: 'A real deadline', detail: 'Committed date', params: 'P=0.60 S=0.70', P: 0.6, S: 0.7 },
  { key: 'scrutiny', label: 'External Scrutiny', plain: 'Being judged', detail: 'Evaluation threat', params: 'P=0.90 S=0.25', P: 0.9, S: 0.25 },
];

export const SCRATCHPADS: readonly (Option<ScratchpadKey> & { omega: number; gammaAssoc: number })[] = [
  { key: 'single', label: 'Single Thread Flow', plain: 'Stayed on track', detail: 'No tangents surfaced', params: 'γ_a=0 Ω=0', omega: 0, gammaAssoc: 0 },
  { key: 'tokenized', label: 'Tokenized To Scratchpad', plain: 'Wrote tangents down', detail: 'Tangent written, then dropped', params: 'γ_a=0 Ω=0', omega: 0, gammaAssoc: 0 },
  { key: 'speculative', label: 'Unbuffered Speculative Intake', plain: 'Ideas kept branching', detail: 'Sub-threads spawned, not externalized', params: 'γ_a=0.5 Ω=0', omega: 0, gammaAssoc: 0.5 },
  { key: 'rabbit', label: 'Unbuffered Rabbit Hole', plain: 'Fell down a rabbit hole', detail: 'Divergent context switch', params: 'γ_a=1.0 Ω=0.25', omega: 0.25, gammaAssoc: 1 },
];

export const NOVELTIES: readonly (Option<NoveltyKey> & { xi: number })[] = [
  { key: 'monotonous', label: 'Monotonous', plain: 'Boring, repetitive', detail: 'Low-entropy, repetitive', params: 'ξ=0.00', xi: 0 },
  { key: 'routine', label: 'Routine', plain: 'Familiar', detail: 'Familiar domain', params: 'ξ=0.05', xi: 0.05 },
  { key: 'novel', label: 'Novel Cross-Domain', plain: 'New territory', detail: 'High-entropy associative stimulation', params: 'ξ=0.15', xi: 0.15 },
];

export const SOMATICS: readonly (Option<SomaticKey> & SomaticFlags)[] = [
  {
    key: 'supine',
    label: 'Supported / Supine',
    plain: 'Lying down or moving',
    detail: 'Supported, supine, standing or moving: no static seat',
    params: '𝟙seat=0 𝟙kin=1',
    staticSeated: false,
    kineticOrSupported: true,
    postureGain: 1,
    ocularGain: 1,
    ocularFloor: 0,
    restGain: 1.25,
  },
  {
    key: 'seated',
    label: 'Ergonomic Seated',
    plain: 'Sitting',
    detail: 'Neutral spine, screen at eye line',
    params: '𝟙seat=1 𝟙kin=0',
    staticSeated: true,
    kineticOrSupported: false,
    postureGain: 1,
    ocularGain: 1,
    ocularFloor: 0,
    restGain: 1,
  },
  {
    key: 'ocular',
    label: 'Active Ocular Strain',
    plain: 'Eyes hurting',
    detail: 'Accommodation fatigue reported',
    params: 'γ_vis×1.6',
    staticSeated: true,
    kineticOrSupported: false,
    postureGain: 1,
    ocularGain: 1.6,
    ocularFloor: 0.35,
    restGain: 1,
  },
  {
    key: 'slump',
    label: 'Cervical / Lumbar Slump',
    plain: 'Slouching or stiff',
    detail: 'Collapsed posture, neck or back pain',
    params: 'γ_post×2.0',
    staticSeated: true,
    kineticOrSupported: false,
    postureGain: 2,
    ocularGain: 1,
    ocularFloor: 0,
    restGain: 1,
  },
];

export const INTENSITIES: readonly (Option<IntensityKey> & { factor: number })[] = [
  { key: 'light', label: 'Light', plain: 'Easy', detail: 'Easy pace, ×0.7 intensity', params: '×0.70', factor: 0.7 },
  { key: 'standard', label: 'Standard', plain: 'Normal', detail: 'As modelled', params: '×1.00', factor: 1 },
  { key: 'heavy', label: 'Heavy', plain: 'Hard', detail: 'Pushing, ×1.25 intensity', params: '×1.25', factor: 1.25 },
];

/**
 * Playback speeds for listening / watching blocks. The factor multiplies the intake channels:
 * at 2× an audiobook delivers twice the words per minute, so restoration rises linearly with
 * I₁ while the quadratic β_in C_in I₁² cost and the backlog accrual rise faster — a fast
 * playback tips past I*(t) sooner. Output channels are untouched.
 */
export const PLAYBACK_SPEEDS: readonly (Option<SpeedKey> & { factor: number })[] = [
  { key: 'x075', label: '0.75×', plain: 'Slower (0.75×)', detail: 'Slowed down: three quarters of the words per minute', params: 'I×0.75', factor: 0.75 },
  { key: 'x1', label: '1×', plain: 'Normal speed', detail: 'As recorded', params: 'I×1.00', factor: 1 },
  { key: 'x125', label: '1.25×', plain: '1.25×', detail: 'A little faster', params: 'I×1.25', factor: 1.25 },
  { key: 'x15', label: '1.5×', plain: '1.5×', detail: 'Half again as many words per minute', params: 'I×1.50', factor: 1.5 },
  { key: 'x175', label: '1.75×', plain: '1.75×', detail: 'Fast', params: 'I×1.75', factor: 1.75 },
  { key: 'x2', label: '2×', plain: 'Double speed (2×)', detail: 'Twice the words per minute', params: 'I×2.00', factor: 2 },
  { key: 'x25', label: '2.5×', plain: '2.5×', detail: 'Very fast', params: 'I×2.50', factor: 2.5 },
  { key: 'x3', label: '3×', plain: 'Triple speed (3×)', detail: 'Three times the words per minute', params: 'I×3.00', factor: 3 },
];

export const DEFAULT_SPEED: SpeedKey = 'x1';

/** Playback factor of a spec (1 when unset). */
export function speedFactor(spec: BlockSpec): number {
  return PLAYBACK_SPEEDS.find((o) => o.key === (spec.speed ?? DEFAULT_SPEED))?.factor ?? 1;
}

/** The catalog speed closest to a stated factor ("1.6x" → 1.5×). */
export function nearestSpeed(factor: number): SpeedKey {
  let best = PLAYBACK_SPEEDS[0];
  for (const o of PLAYBACK_SPEEDS) if (Math.abs(o.factor - factor) < Math.abs(best.factor - factor)) best = o;
  return best.key;
}

/** True when the modality carries an intake channel (so playback speed and density matter). */
export function modalityHasIntake(modality: ModalityKey): boolean {
  const m = MODALITIES.find((o) => o.key === modality);
  return !!m && m.Ivis + m.Iaud > 0;
}

export const DEFAULT_SPEC: BlockSpec = Object.freeze({
  cadence: 'm15',
  intensity: 'standard',
  modality: 'execution',
  anchor: 'music',
  valuation: 'architecture',
  density: 'proofs',
  context: 'agency',
  scratchpad: 'tokenized',
  novelty: 'routine',
  somatic: 'seated',
});

function find<K extends string, T extends Option<K>>(list: readonly T[], key: K): T {
  const hit = list.find((o) => o.key === key);
  if (!hit) throw new Error(`Unknown option key: ${key}`);
  return hit;
}

export function cadenceMinutes(key: CadenceKey): number {
  return find(CADENCES, key).minutes;
}

/** Block length in minutes, honouring a custom cadence. */
export function blockMinutes(spec: BlockSpec): number {
  if (spec.cadence === CUSTOM_CADENCE) {
    const m = spec.customMinutes ?? 25;
    return Math.min(MAX_CUSTOM_MINUTES, Math.max(MIN_CUSTOM_MINUTES, Math.round(m)));
  }
  return cadenceMinutes(spec.cadence);
}

export function intensityFactor(spec: BlockSpec): number {
  return INTENSITIES.find((o) => o.key === (spec.intensity ?? 'standard'))?.factor ?? 1;
}

/** Resolve a form spec into the numeric control/context vectors. */
export function resolveSpec(spec: BlockSpec): BlockInputs {
  const modality = find(MODALITIES, spec.modality);
  const anchor = find(ANCHORS, spec.anchor);
  const valuation = find(VALUATIONS, spec.valuation);
  const density = find(DENSITIES, spec.density);
  const context = find(CONTEXTS, spec.context);
  const scratch = find(SCRATCHPADS, spec.scratchpad);
  const novelty = find(NOVELTIES, spec.novelty);
  const somatic = find(SOMATICS, spec.somatic);
  const kinetic = anchor.kinetic;
  const f = intensityFactor(spec);
  const r = speedFactor(spec);
  return {
    u: {
      Ivis: clamp01(modality.Ivis * f * r),
      Iaud: clamp01(modality.Iaud * f * r),
      Ianchor: anchor.Ianchor,
      O1: clamp01(modality.O1 * f),
      Oanchor: anchor.Oanchor,
    },
    theta: { Cin: density.Cin, P: context.P, S: context.S },
    Vtarget: valuation.V,
    omegaSwitch: scratch.omega,
    gammaAssoc: scratch.gammaAssoc,
    xiNovelty: novelty.xi,
    somatic: {
      // A walking anchor is incompatible with a static seat: the kinetic
      // indicator wins and the seated indicator is released.
      staticSeated: somatic.staticSeated && !kinetic,
      kineticOrSupported: somatic.kineticOrSupported || kinetic,
      postureGain: somatic.postureGain,
      ocularGain: somatic.ocularGain,
      ocularFloor: somatic.ocularFloor,
      restGain: somatic.restGain,
    },
  };
}

// ---------------------------------------------------------------------------
// Governing equations
// ---------------------------------------------------------------------------

export const clamp01 = (v: number): number => (v < 0 ? 0 : v > 1 ? 1 : v);

export function clampState(x: StateVector): StateVector {
  return {
    E: clamp01(x.E),
    B: clamp01(x.B),
    Fvis: clamp01(x.Fvis),
    Fbody: clamp01(x.Fbody),
    A: clamp01(x.A),
    V: clamp01(x.V),
  };
}

export function compositeStrain(x: StateVector): number {
  return Math.max(x.Fvis, x.Fbody);
}

/** ψ(t): exponential sleep drag as a function of hours awake. */
export function circadianDrag(hoursAwake: number, k: Constants): number {
  const over = Math.max(0, hoursAwake - k.psiOnsetHours);
  return Math.min(2, k.psi0 * Math.exp(over / k.tauPsiHours));
}

/** Γ_arousal(A) = exp(−(A − A*)² / 2σ_A²). */
export function gammaArousal(A: number, k: Constants): number {
  const d = A - k.Astar;
  return Math.exp(-(d * d) / (2 * k.sigmaA * k.sigmaA));
}

/** A_inst(u) = 0.45 I_1 + 0.25 I_anchor + 0.40 O_1 + 0.15 O_anchor + ξ_novelty. */
export function arousalPotential(u: ControlVector, xiNovelty = 0): number {
  const I1 = clamp01(u.Ivis + u.Iaud);
  return clamp01(0.45 * I1 + 0.25 * u.Ianchor + 0.4 * u.O1 + 0.15 * u.Oanchor + xiNovelty);
}

export interface Derivatives {
  dE: number;
  dB: number;
  dFvis: number;
  dFbody: number;
  dA: number;
  dV: number;
  phiIn: number;
  phiOut: number;
  rest: number;
  gamma: number;
  Ainst: number;
  I1: number;
  /** Backlog accrual κ (C_in/V) I₁ (1 + γ_assoc) + Ω_switch. */
  accrual: number;
  /** Backlog clearance λB. */
  decay: number;
  /** Backlog digestion μ V S (1−P) O₁. */
  digestion: number;
  /** Numerator of I*(t) with the live Γ. */
  numerator: number;
  /** Numerator of I*(t) evaluated at Γ = 1 (best achievable arousal). */
  numeratorOpt: number;
  Istar: number;
  psi: number;
}

export function derivatives(x: StateVector, b: BlockInputs, psi: number, k: Constants): Derivatives {
  const { u, theta, somatic } = b;
  const I1 = clamp01(u.Ivis + u.Iaud);
  const gamma = gammaArousal(x.A, k);
  const F = compositeStrain(x);
  const Vsafe = Math.max(x.V, 0.02);
  const load2 = u.O1 * u.O1 + u.Oanchor * u.Oanchor;

  const phiIn =
    k.alphaIn * x.V * I1 * (1 - x.E) * gamma -
    k.betaIn * theta.Cin * I1 * I1 -
    k.deltaIn * x.B * I1 -
    psi * I1;

  const yieldOut = k.etaFlow * theta.S * (1 - theta.P) * x.E * u.O1 * gamma;
  // Strength Through Hardship: a deadline the operator owns (S ≥ 0.7) carries attenuated quadratic drag.
  const owned = theta.S >= 0.7 ? 1 - k.sigmaStrength : 1;
  const costOut = (k.betaOut * theta.P * owned + k.omega * F) * load2;
  const phiOut = yieldOut - costOut;

  const Ecap = clamp01(1 - psi);
  const rest = k.rhoE * Math.max(0, Ecap - x.E) * (1 - I1) * (1 - u.O1) * somatic.restGain;

  const dE = phiIn + yieldOut * (1 - x.E) - costOut + rest - k.cBasal - psi * u.O1;

  const accrual = k.kappa * (theta.Cin / Vsafe) * I1 * (1 + b.gammaAssoc) + b.omegaSwitch;
  const decay = k.lambda * x.B;
  const digestion = k.mu * x.V * theta.S * (1 - theta.P) * u.O1;
  const dB = accrual - decay - digestion;

  const IvisEff = Math.max(u.Ivis, somatic.ocularFloor);
  const dFvis = k.gammaVis * somatic.ocularGain * IvisEff - k.rhoVis * (1 - IvisEff);

  const dFbody =
    k.gammaPosture * somatic.postureGain * (somatic.staticSeated ? 1 : 0) +
    k.sigmaLoad * load2 -
    k.rhoBody * (somatic.kineticOrSupported ? 1 : 0);

  const Ainst = arousalPotential(u, b.xiNovelty);
  const dA = k.kappaA * (Math.max(Ainst, k.Arest) - x.A);

  const active = I1 + u.O1 > 0;
  const dV = active ? k.kappaV * (b.Vtarget - x.V) : 0;

  const numerator = k.alphaIn * x.V * (1 - x.E) * gamma - k.deltaIn * x.B - psi;
  const numeratorOpt = k.alphaIn * x.V * (1 - x.E) - k.deltaIn * x.B - psi;
  const Istar = numerator / (k.betaIn * Math.max(theta.Cin, 0.01));

  return { dE, dB, dFvis, dFbody, dA, dV, phiIn, phiOut, rest, gamma, Ainst, I1, accrual, decay, digestion, numerator, numeratorOpt, Istar, psi };
}

/** I*(t) for a prospective intake of density C_in from state x. */
export function criticalIntensity(x: StateVector, Cin: number, psi: number, k: Constants): number {
  const gamma = gammaArousal(x.A, k);
  const numerator = k.alphaIn * x.V * (1 - x.E) * gamma - k.deltaIn * x.B - psi;
  return numerator / (k.betaIn * Math.max(Cin, 0.01));
}

export interface BlockResult {
  x: StateVector;
  hoursAwake: number;
  delta: StateVector;
  /** Time-averaged rates over the block (per hour). */
  mean: {
    dE: number;
    dB: number;
    dFvis: number;
    dFbody: number;
    dA: number;
    dV: number;
    phiIn: number;
    phiOut: number;
    rest: number;
    gamma: number;
    Istar: number;
    psi: number;
    accrual: number;
    decay: number;
    digestion: number;
  };
  /** Per-minute trajectory (x at each substep boundary, including x₀). */
  trace: StateVector[];
}

/**
 * Integrate one discrete block with forward-Euler sub-stepping (1 min) and
 * per-step projection back onto the unit manifold.
 */
export function integrateBlock(
  x0: StateVector,
  hoursAwake0: number,
  b: BlockInputs,
  dtMinutes: number,
  k: Constants,
  stepMinutes = 1,
): BlockResult {
  const steps = Math.max(1, Math.round(dtMinutes / stepMinutes));
  const h = dtMinutes / steps / 60;
  let x = clampState(x0);
  let hoursAwake = hoursAwake0;
  const trace: StateVector[] = [x];
  const acc = { dE: 0, dB: 0, dFvis: 0, dFbody: 0, dA: 0, dV: 0, phiIn: 0, phiOut: 0, rest: 0, gamma: 0, Istar: 0, psi: 0, accrual: 0, decay: 0, digestion: 0 };
  for (let i = 0; i < steps; i += 1) {
    const psi = circadianDrag(hoursAwake, k);
    const d = derivatives(x, b, psi, k);
    acc.dE += d.dE;
    acc.dB += d.dB;
    acc.dFvis += d.dFvis;
    acc.dFbody += d.dFbody;
    acc.dA += d.dA;
    acc.dV += d.dV;
    acc.phiIn += d.phiIn;
    acc.phiOut += d.phiOut;
    acc.rest += d.rest;
    acc.gamma += d.gamma;
    acc.Istar += d.Istar;
    acc.psi += d.psi;
    acc.accrual += d.accrual;
    acc.decay += d.decay;
    acc.digestion += d.digestion;
    x = clampState({
      E: x.E + h * d.dE,
      B: x.B + h * d.dB,
      Fvis: x.Fvis + h * d.dFvis,
      Fbody: x.Fbody + h * d.dFbody,
      A: x.A + h * d.dA,
      V: x.V + h * d.dV,
    });
    hoursAwake += h;
    trace.push(x);
  }
  const n = steps;
  const mean = {
    dE: acc.dE / n,
    dB: acc.dB / n,
    dFvis: acc.dFvis / n,
    dFbody: acc.dFbody / n,
    dA: acc.dA / n,
    dV: acc.dV / n,
    phiIn: acc.phiIn / n,
    phiOut: acc.phiOut / n,
    rest: acc.rest / n,
    gamma: acc.gamma / n,
    Istar: acc.Istar / n,
    psi: acc.psi / n,
    accrual: acc.accrual / n,
    decay: acc.decay / n,
    digestion: acc.digestion / n,
  };
  const delta: StateVector = {
    E: x.E - x0.E,
    B: x.B - x0.B,
    Fvis: x.Fvis - x0.Fvis,
    Fbody: x.Fbody - x0.Fbody,
    A: x.A - x0.A,
    V: x.V - x0.V,
  };
  return { x, hoursAwake, delta, mean, trace };
}

/**
 * Backlog saturation latch (§4): set once B ≥ B_sat, released by an output
 * block (O₁ > 0 with I₁ = 0 — expressive digestion or execution) or once the
 * backlog has objectively cleared below 0.40.
 */
export function nextBacklogLatch(latch: boolean, xAfter: StateVector, b: BlockInputs | null, k: Constants): boolean {
  if (xAfter.B >= k.BsatLock) return true;
  if (xAfter.B < 0.4) return false;
  if (b && b.u.O1 > 0 && b.u.Ivis + b.u.Iaud === 0) return false;
  return latch;
}

/**
 * Objective Impartiality: blend a self-reported state into the model's estimate with a
 * scalar Kalman gain instead of overwriting it. K = 1 copies the report; K = 0 ignores it.
 */
export function blendCalibration(model: StateVector, reported: StateVector, gain: number): StateVector {
  const K = Math.min(1, Math.max(0, gain));
  const out = { ...model };
  for (const key of STATE_KEYS) out[key] = clamp01(model[key] + K * (reported[key] - model[key]));
  return out;
}

/** Biological sleep reset applied to the state vector. */
export function applySleepReset(x: StateVector, sleepHours: number): StateVector {
  const h = Math.max(0, sleepHours);
  return clampState({
    E: x.E + (1 - x.E) * (1 - Math.exp(-h / 3)),
    B: x.B * Math.exp(-0.25 * h),
    Fvis: x.Fvis * Math.exp(-h / 1.5),
    Fbody: x.Fbody * Math.exp(-h / 1.5),
    A: 0.3,
    V: x.V,
  });
}

// ---------------------------------------------------------------------------
// Diagnostics + quadrant routing
// ---------------------------------------------------------------------------

export type InputRegime = 'nominal' | 'arousal-limited' | 'saturated' | 'singularity';

export interface Diagnostics {
  psi: number;
  gamma: number;
  F: number;
  numerator: number;
  numeratorOpt: number;
  /** I*(t) at the planned density. */
  Istar: number;
  /** I*(t) at fiction density (C_in = 0.20) — the absorption cap. */
  IstarFiction: number;
  /** I* at Γ = 1 and fiction density — the cap after an arousal ramp. */
  IstarFictionOpt: number;
  hoursAwake: number;
  latePhase: boolean;
  regime: InputRegime;
  /** Late-phase and somatic singularities mandate sleep; structural ones mandate zero-input rest first. */
  singularityMode: 'late' | 'structural' | 'somatic' | null;
  guardrails: Guardrails;
}

export interface Guardrails {
  /** F_vis ≥ F_vis^cut ⇒ force I_vis = 0. */
  opticalCutoff: boolean;
  /** B ≥ B_sat (or latched since) ⇒ prohibit I₁ > 0 until an output block digests it. */
  backlogSaturated: boolean;
  /** E ≥ 0.50 ∧ A < A_under ⇒ reject rest; ramp arousal. */
  underArousal: boolean;
  /** E < 0.40 ∧ A < A_under ⇒ true depletion; supine sensory isolation. */
  trueDepletion: boolean;
  /** F ≥ F_term ⇒ terminal sleep reset. */
  terminalSomatic: boolean;
}

export function diagnose(x: StateVector, hoursAwake: number, plannedCin: number, k: Constants, backlogLatch = false): Diagnostics {
  const psi = circadianDrag(hoursAwake, k);
  const gamma = gammaArousal(x.A, k);
  const numerator = k.alphaIn * x.V * (1 - x.E) * gamma - k.deltaIn * x.B - psi;
  const numeratorOpt = k.alphaIn * x.V * (1 - x.E) - k.deltaIn * x.B - psi;
  const Istar = numerator / (k.betaIn * Math.max(plannedCin, 0.01));
  const IstarFiction = numerator / (k.betaIn * 0.2);
  const IstarFictionOpt = numeratorOpt / (k.betaIn * 0.2);
  const latePhase = hoursAwake >= k.lateHours;
  const F = compositeStrain(x);
  const guardrails: Guardrails = {
    opticalCutoff: x.Fvis >= k.FvisCutoff,
    backlogSaturated: x.B >= k.BsatLock || backlogLatch,
    underArousal: x.E >= 0.5 && x.A < k.AunderArousal,
    trueDepletion: x.E < 0.4 && x.A < k.AunderArousal,
    terminalSomatic: F >= k.Fterminal,
  };
  let regime: InputRegime = 'nominal';
  let singularityMode: Diagnostics['singularityMode'] = null;
  if (latePhase) {
    regime = 'singularity';
    singularityMode = 'late';
  } else if (guardrails.terminalSomatic) {
    regime = 'singularity';
    singularityMode = 'somatic';
  } else if (numeratorOpt <= 0 && x.E < 0.5) {
    regime = 'singularity';
    singularityMode = 'structural';
  } else if (numeratorOpt <= 0) regime = 'saturated';
  else if (numerator <= 0) regime = 'arousal-limited';
  return {
    psi,
    gamma,
    F,
    numerator,
    numeratorOpt,
    Istar,
    IstarFiction,
    IstarFictionOpt,
    hoursAwake,
    latePhase,
    regime,
    singularityMode,
    guardrails,
  };
}

export type QuadrantId = 'SINGULARITY' | 'I-A' | 'I-B' | 'II' | 'III' | 'IV' | 'IV-B';

export interface Routing {
  quadrant: QuadrantId;
  title: string;
  /** The predicate that fired, with live values substituted. */
  trigger: string;
  summary: string;
  /** Secondary constraints that also hold and must shape the block. */
  flags: string[];
}

const f2 = (v: number): string => v.toFixed(2);

export function route(x: StateVector, d: Diagnostics, k: Constants): Routing {
  const F = compositeStrain(x);
  const flags: string[] = [];
  if (F >= 0.5) flags.push(`F = ${f2(F)} ≥ 0.50 — somatic constraint: keep the block kinetic or supported, zero ocular accommodation.`);
  if (d.regime === 'arousal-limited')
    flags.push(`Γ = ${f2(d.gamma)} — arousal off tone (A = ${f2(x.A)}, A* = ${f2(k.Astar)}). Intake is depleting until A is ramped; lead with a kinetic or music anchor.`);
  if (d.guardrails.opticalCutoff) flags.push(`Optical cutoff: F_vis = ${f2(x.Fvis)} ≥ ${f2(k.FvisCutoff)} — I_vis forced to 0 (audio narrative or darkness only).`);
  if (d.guardrails.backlogSaturated)
    flags.push(`Backlog saturated: B = ${f2(x.B)} (lock at ${f2(k.BsatLock)}) — I₁ > 0 prohibited until an expressive digestion block runs.`);
  if (d.guardrails.underArousal)
    flags.push(`Under-arousal gate: E = ${f2(x.E)} ≥ 0.50 with A = ${f2(x.A)} < ${f2(k.AunderArousal)} — low felt energy is dopamine friction, not depletion. Reject rest; prescribe a sensory anchor, kinetic movement, or novel cross-domain stimulation.`);
  if (d.regime === 'saturated' && x.B < 0.6)
    flags.push(`I* ≤ 0 at E = ${f2(x.E)} — reserves cannot absorb more input; route to output.`);
  if (x.V < k.Vmin) flags.push(`V = ${f2(x.V)} < V_min = ${f2(k.Vmin)} — recent intake was inadmissible churn; restore substantive depth before dense input.`);
  if (d.psi > k.psi0 * 1.5 && d.regime !== 'singularity')
    flags.push(`ψ(t) = ${f2(d.psi)} — circadian drag elevated at ${d.hoursAwake.toFixed(1)} h awake; shorten every horizon.`);

  if (d.singularityMode === 'late') {
    return {
      quadrant: 'SINGULARITY',
      title: 'Burnout Singularity / Sleep Reset',
      trigger: `t_awake = ${d.hoursAwake.toFixed(1)} h ≥ t_late = ${k.lateHours} h · ψ(t) = ${f2(d.psi)}`,
      summary:
        'Late-phase circadian threshold tripped: passive rest can no longer lift E above E_cap = 1 − ψ(t). Prohibit input (I = 0), terminate the session, and transition to biological sleep. Do not negotiate one more block.',
      flags,
    };
  }
  if (d.singularityMode === 'somatic') {
    return {
      quadrant: 'SINGULARITY',
      title: 'Terminal Sleep Reset',
      trigger: `F = max(F_vis, F_body) = ${f2(d.F)} ≥ F_term = ${f2(k.Fterminal)}`,
      summary:
        'Composite somatic strain has crossed the terminal threshold; hyper-focus masking has likely hidden the accumulation. Full shutdown: I = 0, O = 0, then biological sleep to reset [E → 1.0, B → 0.0, F → 0.0].',
      flags,
    };
  }
  if (x.B >= 0.6 && x.E < 0.4) {
    return {
      quadrant: 'I-A',
      title: 'Zero-Input Flush',
      trigger: `B = ${f2(x.B)} ≥ 0.60 ∧ E = ${f2(x.E)} < 0.40`,
      summary:
        'Backlog is jammed and reserves are too low to digest it through output. Sensory isolation: dark room, eye mask, zero screens, zero podcasts. Let λB do the work.',
      flags,
    };
  }
  if (x.B >= 0.6) {
    return {
      quadrant: 'I-B',
      title: 'Expressive Digestion',
      trigger: `B = ${f2(x.B)} ≥ 0.60 ∧ E = ${f2(x.E)} ≥ 0.40`,
      summary:
        'Backlog is jammed but reserves can drive the −μ V S (1−P) O₁ digestion term. Low-stakes journaling, instrument improv, analog scratchpad synthesis at P ≤ 0.1, S_agency = 1.0.',
      flags,
    };
  }
  if (d.guardrails.trueDepletion) {
    return {
      quadrant: 'I-A',
      title: 'Zero-Input Flush · True Depletion',
      trigger: `E = ${f2(x.E)} < 0.40 ∧ A = ${f2(x.A)} < ${f2(k.AunderArousal)}`,
      summary:
        'Low arousal with depleted reserves is biological depletion, not under-arousal. Enforce supine sensory isolation: I = 0, O = 0, P = 0, eye mask, no anchors. Re-audit at 15–25 m.',
      flags,
    };
  }
  if (F >= 0.5) {
    return {
      quadrant: 'III',
      title: 'Somatic Reset',
      trigger: `F = max(F_vis, F_body) = ${f2(F)} ≥ 0.50`,
      summary:
        'Composite strain is the binding constraint; ω·F now taxes every unit of output. Walking treadmill, resistance-band mobility, spinal deloading, zero ocular accommodation.',
      flags: flags.filter((s) => !s.startsWith('F =')),
    };
  }
  if (x.E < 0.5 && d.singularityMode === 'structural') {
    return {
      quadrant: 'SINGULARITY',
      title: 'Burnout Singularity / Zero-Input Rest',
      trigger: `num(I*) = ${f2(d.numeratorOpt)} ≤ 0 at Γ = 1 ∧ E = ${f2(x.E)} < 0.50`,
      summary:
        'No admissible intake exists even at optimal arousal: every consumption vector is strictly depleting. Prohibit input (I = 0) and enforce zero-input rest. If I* is still ≤ 0 after the rest block, terminate the session and sleep.',
      flags,
    };
  }
  if (x.E < 0.5) {
    return {
      quadrant: 'II',
      title: 'Controlled Absorption',
      trigger: `E = ${f2(x.E)} < 0.50 ∧ I*(C_in = 0.20) = ${f2(d.regime === 'arousal-limited' ? d.IstarFictionOpt : d.IstarFiction)} > 0`,
      summary: `Reserves are low but restorative intake exists. Substantive literature or an audio narrative with eye mask, intensity capped at I₁ ≤ ${f2(k.absorbCapFraction)}·I*(t), C_in ≤ 0.30, V ≥ 0.85. Stop the moment Φ_in turns negative.`,
      flags,
    };
  }
  if (x.B < 0.4) {
    return {
      quadrant: 'IV',
      title: 'High-Leverage Execution',
      trigger: `B = ${f2(x.B)} < 0.40 ∧ E = ${f2(x.E)} ≥ 0.50 ∧ F = ${f2(F)} < 0.50`,
      summary:
        'All three gates open. Core generative sprint with a familiar-music anchor; the η_flow S (1−P) E O₁ Γ term is at its most productive here. Hard-stop at the horizon — do not ride the flow into somatic debt.',
      flags,
    };
  }
  return {
    quadrant: 'IV-B',
    title: 'Buffered Execution',
    trigger: `0.40 ≤ B = ${f2(x.B)} < 0.60 ∧ E = ${f2(x.E)} ≥ 0.50 ∧ F = ${f2(F)} < 0.50`,
    summary:
      'Reserves are available but the backlog is accumulating. Output digests it (−μ V S (1−P) O₁): execute with tokenized scratchpad discipline and a shorter horizon, or flush first with a 15 m expressive block.',
    flags,
  };
}

// ---------------------------------------------------------------------------
// Prescription engine: candidate configurations with hard time boundaries
// ---------------------------------------------------------------------------

export type ConfigKind = 'rest' | 'absorb' | 'express' | 'execute' | 'somatic' | 'sleep';

export interface Prescription {
  kind: ConfigKind;
  name: string;
  rationale: string;
  /** Loadable form spec (null for the sleep transition). */
  spec: BlockSpec | null;
  sleepHours?: number;
  /** Hard boundary in minutes (0 when the action is a sleep transition). */
  boundMinutes: number;
  /** The first admissibility violation on the simulated trajectory. */
  stopRule: string;
  /** Minute at which the stop rule first fires on the simulated trajectory (≤ 120). */
  horizonMinutes: number;
  /** Predicted state at the hard boundary. */
  predicted: StateVector | null;
  predictedDelta: StateVector | null;
  admissible: boolean;
}

const CADENCE_MINUTES = CADENCES.map((c) => c.minutes);

function snapCadence(minutes: number): number {
  let best = 0;
  for (const m of CADENCE_MINUTES) if (m <= minutes && m > best) best = m;
  return best;
}

/** Smallest standard cadence that covers `minutes` (90 if none does). */
function snapCadenceUp(minutes: number): number {
  for (const m of CADENCE_MINUTES) if (m >= minutes) return m;
  return CADENCE_MINUTES[CADENCE_MINUTES.length - 1];
}

/**
 * A stop is either a goal (the block has done its job: rest has recovered E,
 * digestion has cleared B) or a violation (a guardrail or flux sign trips).
 * Goals shorten the useful horizon; violations bound it hard.
 */
export type StopKind = 'goal' | 'violation';
export interface Stop {
  kind: StopKind;
  reason: string;
}

interface StopCheck {
  (x: StateVector, d: Derivatives, minute: number, hoursAwake: number): Stop | null;
}

const goal = (reason: string): Stop => ({ kind: 'goal', reason });
const violation = (reason: string): Stop => ({ kind: 'violation', reason });

function simulateHorizon(
  x0: StateVector,
  hoursAwake0: number,
  spec: BlockSpec,
  k: Constants,
  check: StopCheck,
  maxMinutes = 120,
): { horizon: number; reason: string; kind: StopKind | 'none' } {
  const b = resolveSpec(spec);
  let x = clampState(x0);
  let hoursAwake = hoursAwake0;
  const h = 1 / 60;
  for (let m = 0; m < maxMinutes; m += 1) {
    const psi = circadianDrag(hoursAwake, k);
    const d = derivatives(x, b, psi, k);
    const stop = check(x, d, m, hoursAwake);
    if (stop) return { horizon: m, reason: stop.reason, kind: stop.kind };
    x = clampState({
      E: x.E + h * d.dE,
      B: x.B + h * d.dB,
      Fvis: x.Fvis + h * d.dFvis,
      Fbody: x.Fbody + h * d.dFbody,
      A: x.A + h * d.dA,
      V: x.V + h * d.dV,
    });
    hoursAwake += h;
  }
  return { horizon: maxMinutes, reason: `no boundary within ${maxMinutes} m`, kind: 'none' };
}

const STOP_RULES: Record<Exclude<ConfigKind, 'sleep'>, { text: string; check: StopCheck }> = {
  rest: {
    text: 'stop when E ≥ 0.65 (recovered) or the block elapses',
    check: (x, _d, m) => (m > 0 && x.E >= 0.65 ? goal('E ≥ 0.65') : null),
  },
  absorb: {
    text: 'stop when Φ_in < 0, B ≥ 0.60, F_vis ≥ 0.60 (violations) or E ≥ 0.70 (recovered)',
    check: (x, d, m) =>
      m > 0 && d.phiIn < 0
        ? violation('Φ_in < 0')
        : x.B >= 0.6
          ? violation('B ≥ 0.60')
          : x.Fvis >= 0.6
            ? violation('F_vis ≥ 0.60')
            : m > 0 && x.E >= 0.7
              ? goal('E ≥ 0.70')
              : null,
  },
  express: {
    text: 'stop when E < 0.30, F ≥ 0.60, Φ_out < 0 (violations) or B ≤ 0.35 (digested)',
    check: (x, d, m) =>
      x.E < 0.3
        ? violation('E < 0.30')
        : compositeStrain(x) >= 0.6
          ? violation('F ≥ 0.60')
          : m > 0 && d.phiOut < 0
            ? violation('Φ_out < 0')
            : m > 0 && x.B <= 0.35
              ? goal('B ≤ 0.35')
              : null,
  },
  execute: {
    text: 'stop when E < 0.35, B ≥ 0.60, F ≥ 0.55, Φ_out < 0, or t_late trips',
    check: (x, d, m, hoursAwake) =>
      x.E < 0.35
        ? violation('E < 0.35')
        : x.B >= 0.6
          ? violation('B ≥ 0.60')
          : compositeStrain(x) >= 0.55
            ? violation('F ≥ 0.55')
            : m > 0 && d.phiOut < 0
              ? violation('Φ_out < 0')
              : hoursAwake >= 16
                ? violation('t_late')
                : null,
  },
  somatic: {
    text: 'stop when E < 0.30 (violation) or F ≤ 0.30 (deloaded)',
    check: (x, _d, m) => (x.E < 0.3 ? violation('E < 0.30') : m > 0 && compositeStrain(x) <= 0.3 ? goal('F ≤ 0.30') : null),
  },
};

type SpecBase = Omit<BlockSpec, 'cadence' | 'novelty'> & { novelty?: NoveltyKey };

function withCadence(spec: SpecBase, minutes: number): BlockSpec {
  const cadence = CADENCES.find((c) => c.minutes === minutes)?.key ?? 'm15';
  return { novelty: 'routine', ...spec, cadence };
}

function buildPrescription(
  kind: Exclude<ConfigKind, 'sleep'>,
  name: string,
  rationale: string,
  base: SpecBase,
  x: StateVector,
  hoursAwake: number,
  k: Constants,
  preferredMax = 90,
): Prescription {
  const rule = STOP_RULES[kind];
  const probe = withCadence(base, 90);
  const { horizon, reason, kind: stopKind } = simulateHorizon(x, hoursAwake, probe, k, rule.check);
  let bound: number;
  let admissible: boolean;
  let stopRule: string;
  if (stopKind === 'violation') {
    const snapped = Math.min(snapCadence(horizon), preferredMax);
    admissible = snapped >= 15;
    bound = admissible ? snapped : 15;
    stopRule = admissible
      ? `${rule.text}. Simulated first violation: ${reason} at ${horizon} m → hard boundary ${bound} m.`
      : `${rule.text}. Violation (${reason}) inside 15 m — no admissible window; re-route.`;
  } else {
    bound = Math.max(15, Math.min(snapCadenceUp(horizon), preferredMax));
    admissible = true;
    stopRule =
      stopKind === 'goal'
        ? `${rule.text}. Simulated: ${reason} reached at ${horizon} m → boundary ${bound} m.`
        : `${rule.text}. No boundary inside 120 m → boundary ${bound} m.`;
  }
  const spec = withCadence(base, bound);
  const result = integrateBlock(x, hoursAwake, resolveSpec(spec), bound, k);
  return {
    kind,
    name,
    rationale,
    spec,
    boundMinutes: bound,
    stopRule,
    horizonMinutes: horizon,
    predicted: result.x,
    predictedDelta: result.delta,
    admissible,
  };
}

const REST_BASE: SpecBase = { modality: 'zero', anchor: 'none', valuation: 'utility', density: 'null', context: 'agency', scratchpad: 'single', somatic: 'supine' };

export function prescribe(x: StateVector, hoursAwake: number, d: Diagnostics, r: Routing, k: Constants, maxMinutes?: number): Prescription[] {
  const F = compositeStrain(x);
  const out: Prescription[] = [];
  const g = d.guardrails;
  const push = (kind: Exclude<ConfigKind, 'sleep'>, name: string, rationale: string, base: SpecBase, preferredMaxIn = 90) => {
    const preferredMax = maxMinutes ? Math.min(preferredMaxIn, Math.max(15, maxMinutes)) : preferredMaxIn;
    const m = MODALITIES.find((o) => o.key === base.modality)!;
    // Guardrails: the optical cutoff forbids any visual intake, the backlog
    // lock forbids any intake at all. Candidates that violate them are dropped.
    if (g.opticalCutoff && m.Ivis > 0) return;
    if (g.backlogSaturated && m.Ivis + m.Iaud > 0) return;
    out.push(buildPrescription(kind, name, rationale, base, x, hoursAwake, k, preferredMax));
  };
  const arousalRamp = () =>
    push(
      'express',
      'Arousal ramp: free-write over familiar music, novel domain',
      `A = ${x.A.toFixed(2)} < ${k.AunderArousal.toFixed(2)} with E = ${x.E.toFixed(2)}: under-arousal, not depletion. Expressive output plus the music anchor and cross-domain novelty lift A_inst to ≈ ${arousalPotential(resolveSpec(withCadence({ modality: 'expressive', anchor: 'music', valuation: 'art', density: 'null', context: 'agency', scratchpad: 'tokenized', novelty: 'novel', somatic: 'seated' }, 15)).u, 0.15).toFixed(2)} before the main block.`,
      { modality: 'expressive', anchor: 'music', valuation: 'art', density: 'null', context: 'agency', scratchpad: 'tokenized', novelty: 'novel', somatic: 'seated' },
      15,
    );

  switch (r.quadrant) {
    case 'SINGULARITY': {
      if (d.singularityMode === 'somatic') {
        push('somatic', 'Shutdown bridge: supine, dark, zero input', 'I = 0, O = 0. Spinal deload and closed eyes while the session is terminated; this is the bridge to sleep, not a recovery block.', REST_BASE, 15);
        out.push({
          kind: 'sleep',
          name: 'Terminal sleep reset',
          rationale: `F = ${F.toFixed(2)} ≥ ${k.Fterminal.toFixed(2)}. Somatic afferents were masked by output immersion; only a full sleep reset clears F and B together.`,
          spec: null,
          sleepHours: 7.5,
          boundMinutes: 0,
          stopRule: 'Session terminated. Log the sleep reset on waking; the next audit starts at t_awake = 0.',
          horizonMinutes: 0,
          predicted: applySleepReset(x, 7.5),
          predictedDelta: null,
          admissible: true,
        });
        break;
      }
      if (d.singularityMode === 'structural') {
        push(
          'rest',
          'Zero-input rest: dark room, eye mask, supine',
          'I = 0, O = 0. Backlog decays at λB and E recovers toward E_cap while nothing new is ingested. Re-audit I*(t) at the boundary.',
          { modality: 'zero', anchor: 'none', valuation: 'utility', density: 'null', context: 'agency', scratchpad: 'single', somatic: 'supine' },
          45,
        );
        push(
          'rest',
          'Zero-input rest under brown noise',
          'Same vector with broadband masking to suppress intrusive intake (C_in,2 ≤ 0.15 adds no backlog).',
          { modality: 'zero', anchor: 'brown', valuation: 'utility', density: 'null', context: 'agency', scratchpad: 'single', somatic: 'supine' },
          45,
        );
      } else {
        push(
          'rest',
          'Wind-down: zero-vector, masked, supine',
          'Terminate all input and output now. This block exists only to bridge to sleep; it is not a recovery block.',
          { modality: 'zero', anchor: 'none', valuation: 'utility', density: 'null', context: 'agency', scratchpad: 'single', somatic: 'supine' },
          15,
        );
      }
      out.push({
        kind: 'sleep',
        name: 'Biological sleep transition',
        rationale: d.latePhase
          ? `t_awake = ${hoursAwake.toFixed(1)} h. Passive rest cannot lift E above E_cap = ${Math.max(0, 1 - d.psi).toFixed(2)}; only a sleep reset resets ψ(t).`
          : 'Mandatory if I*(t) is still ≤ 0 after the rest block: the I* numerator is non-positive at optimal arousal and rest has not reopened a restorative zone.',
        spec: null,
        sleepHours: 7.5,
        boundMinutes: 0,
        stopRule: 'Session terminated. Log the sleep reset on waking; the next audit starts at t_awake = 0.',
        horizonMinutes: 0,
        predicted: applySleepReset(x, 7.5),
        predictedDelta: null,
        admissible: true,
      });
      break;
    }
    case 'I-A': {
      push(
        'rest',
        'Sensory isolation: dark room, eye mask',
        'I = 0, O = 0, P = 0. Backlog decays at λB with no new accrual; passive recovery runs at the supine gain. Micro-cadence: re-audit at 15–25 m.',
        REST_BASE,
        25,
      );
      push(
        'rest',
        'Zero-input flush under brown noise',
        'Broadband masking suppresses intrusive intake without adding cognitive density (C_in,2 ≤ 0.15).',
        { ...REST_BASE, anchor: 'brown' },
        25,
      );
      if (F >= 0.4)
        push(
          'somatic',
          'Kinetic flush: walking, no input',
          'F is elevated; a walking anchor deloads F_body while the backlog decays.',
          { modality: 'zero', anchor: 'treadmill', valuation: 'utility', density: 'null', context: 'agency', scratchpad: 'single', somatic: 'supine' },
          25,
        );
      break;
    }
    case 'I-B': {
      if (g.underArousal) arousalRamp();
      push(
        'express',
        'Analog scratchpad synthesis, familiar music',
        'O₁ = 0.35 at P = 0, S = 1 maximises the digestion term; the music anchor holds A near tone.',
        { modality: 'expressive', anchor: 'music', valuation: 'art', density: 'null', context: 'agency', scratchpad: 'tokenized', somatic: 'seated' },
        45,
      );
      push(
        'express',
        'Instrument improv or free-write in silence',
        'Same output vector, no anchor: use when A is already above tone.',
        { modality: 'expressive', anchor: 'none', valuation: 'art', density: 'null', context: 'agency', scratchpad: 'tokenized', somatic: 'supine' },
        45,
      );
      push(
        'express',
        'Pacing dictation on the treadmill',
        'Expressive output with a kinetic anchor: digests backlog and deloads posture simultaneously.',
        { modality: 'expressive', anchor: 'treadmill', valuation: 'art', density: 'null', context: 'agency', scratchpad: 'tokenized', somatic: 'supine' },
        25,
      );
      break;
    }
    case 'III': {
      push(
        'somatic',
        'Walking treadmill, zero ocular load',
        'ρ_body engages, I_vis = 0 lets ρ_vis recover accommodation. No screens, no reading.',
        { modality: 'zero', anchor: 'treadmill', valuation: 'utility', density: 'null', context: 'agency', scratchpad: 'single', somatic: 'supine' },
        45,
      );
      push(
        'somatic',
        'Spinal deload, supine, eyes closed',
        'Supported posture with the ocular channel fully closed; resistance-band mobility counts as this block.',
        { modality: 'zero', anchor: 'none', valuation: 'utility', density: 'null', context: 'agency', scratchpad: 'single', somatic: 'supine' },
        25,
      );
      if (d.IstarFiction * k.absorbCapFraction > 0.35 && x.E < 0.7)
        push(
          'absorb',
          'Audiobook on the treadmill',
          `Auditory narrative (I_aud = 0.35) is under ${k.absorbCapFraction.toFixed(1)}·I* = ${(d.IstarFiction * k.absorbCapFraction).toFixed(2)}; the walk keeps F_body falling.`,
          { modality: 'auditory', anchor: 'treadmill', valuation: 'art', density: 'fiction', context: 'agency', scratchpad: 'single', somatic: 'supine' },
          45,
        );
      break;
    }
    case 'II': {
      const Istar = d.regime === 'arousal-limited' ? d.IstarFictionOpt : d.IstarFiction;
      const cap = Istar * k.absorbCapFraction;
      if (cap > 0.35)
        push(
          'absorb',
          'Audio narrative, eye mask, supine',
          `I_aud = 0.35 ≤ ${k.absorbCapFraction.toFixed(1)}·I*(C_in = 0.20) = ${cap.toFixed(2)}. I_vis = 0 dissipates ocular strain while fiction density keeps the β_in C_in I₁² cost minimal.`,
          { modality: 'auditory', anchor: 'none', valuation: 'art', density: 'fiction', context: 'agency', scratchpad: 'single', somatic: 'supine' },
          45,
        );
      if (cap > 0.5 && x.Fvis < 0.4)
        push(
          'absorb',
          'Substantive literature on the page, familiar music',
          `I_vis = 0.50 ≤ ${k.absorbCapFraction.toFixed(1)}·I* = ${cap.toFixed(2)} and F_vis = ${x.Fvis.toFixed(2)} leaves ocular headroom; the music anchor holds A near tone.`,
          { modality: 'reading', anchor: 'music', valuation: 'art', density: 'fiction', context: 'agency', scratchpad: 'single', somatic: 'supine' },
          45,
        );
      if (d.regime === 'arousal-limited' || out.length === 0)
        push(
          'rest',
          'Arousal ramp: walk with familiar music, no input',
          `Γ = ${d.gamma.toFixed(2)} at A = ${x.A.toFixed(2)}. Raise A toward A* before any intake so Φ_in can be positive.`,
          { modality: 'zero', anchor: 'treadmill', valuation: 'utility', density: 'null', context: 'agency', scratchpad: 'single', somatic: 'supine' },
          25,
        );
      if (out.length < 2)
        push('rest', 'Zero-vector rest, supine', 'Fallback when no intake vector clears the cap: passive recovery only.', REST_BASE, 25);
      break;
    }
    case 'IV': {
      if (g.underArousal) arousalRamp();
      push(
        'execute',
        'Core generative sprint, familiar music loop',
        'O₁ = 0.80, I₁ = 0, I_anchor = 0.55 puts A_inst at ≈ 0.51 ≈ A*. Deep-architecture valuation, code/proof density, every tangent tokenized. Somatic afferents are masked above O₁ = 0.80: the boundary is enforced by the integrated F, not by felt strain.',
        { modality: 'execution', anchor: 'music', valuation: 'architecture', density: 'proofs', context: 'agency', scratchpad: 'tokenized', somatic: 'seated' },
        90,
      );
      push(
        'execute',
        'Walking-desk execution',
        'Same output vector on the treadmill: trades some arousal (A_inst ≈ 0.42) for a falling F_body.',
        { modality: 'execution', anchor: 'treadmill', valuation: 'architecture', density: 'proofs', context: 'soft', scratchpad: 'tokenized', somatic: 'supine' },
        60,
      );
      if (criticalIntensity(x, 0.65, d.psi, k) > 0.85)
        push(
          'absorb',
          'Dense technical absorption sprint',
          `I_vis = 0.85 is under I*(C_in = 0.65) = ${criticalIntensity(x, 0.65, d.psi, k).toFixed(2)}: intake is restorative at this state.`,
          { modality: 'dense', anchor: 'music', valuation: 'architecture', density: 'manuals', context: 'agency', scratchpad: 'tokenized', somatic: 'seated' },
          45,
        );
      break;
    }
    case 'IV-B': {
      if (g.underArousal) arousalRamp();
      push(
        'execute',
        'Buffered execution sprint',
        'Deep execution with every tangent tokenized (Ω = 0); the μ V S O₁ term digests the backlog while producing.',
        { modality: 'execution', anchor: 'music', valuation: 'architecture', density: 'proofs', context: 'agency', scratchpad: 'tokenized', somatic: 'seated' },
        45,
      );
      push(
        'express',
        'Buffer flush before executing',
        'Fifteen minutes of expressive output first: dumps the backlog below 0.40 so the next block routes to Quadrant IV.',
        { modality: 'expressive', anchor: 'none', valuation: 'art', density: 'null', context: 'agency', scratchpad: 'tokenized', somatic: 'seated' },
        25,
      );
      break;
    }
    default:
      break;
  }
  if (out.length < 2 && !out.some((p) => p.kind === 'express') && !g.trueDepletion && x.E >= 0.4)
    push(
      'express',
      'Expressive digestion, silence',
      'Fallback under the intake lock: output digests backlog while nothing new is ingested.',
      { modality: 'expressive', anchor: 'none', valuation: 'art', density: 'null', context: 'agency', scratchpad: 'tokenized', somatic: 'supine' },
      25,
    );
  if (out.length < 2) push('rest', 'Zero-vector rest, supine', 'Fallback: passive recovery only.', REST_BASE, 25);
  return out.slice(0, 3);
}

// ---------------------------------------------------------------------------
// Block catalog: a fixed set of archetypes, graded against the current state
// ---------------------------------------------------------------------------

export interface CatalogEntry {
  id: string;
  name: string;
  kind: ConfigKind;
  detail: string;
  /** Default cadence in minutes; the grade uses min(cadence, simulated horizon). */
  minutes: number;
  spec: SpecBase | null;
  /** Set for user presets. */
  presetId?: string;
}

/** Block kind implied by a spec's control vector (used for user presets). */
export function inferKind(spec: BlockSpec): Exclude<ConfigKind, 'sleep'> {
  const m = MODALITIES.find((o) => o.key === spec.modality)!;
  if (m.O1 >= 0.6) return 'execute';
  if (m.O1 > 0) return 'express';
  if (m.Ivis + m.Iaud > 0) return 'absorb';
  return spec.anchor === 'treadmill' || spec.anchor === 'fidget' ? 'somatic' : 'rest';
}

export function presetEntry(p: UserPreset): CatalogEntry {
  const s = p.spec;
  const base: SpecBase = {
    modality: s.modality,
    anchor: s.anchor,
    valuation: s.valuation,
    density: s.density,
    context: s.context,
    scratchpad: s.scratchpad,
    novelty: s.novelty,
    somatic: s.somatic,
    intensity: s.intensity,
    speed: s.speed,
  };
  return {
    id: `preset:${p.id}`,
    name: p.name,
    kind: inferKind(p.spec),
    detail: 'Your preset',
    minutes: blockMinutes(p.spec),
    spec: base,
    presetId: p.id,
  };
}

export const BLOCK_CATALOG: readonly CatalogEntry[] = [
  { id: 'rest-isolation', name: 'Rest in the dark', kind: 'rest', detail: 'Lie down, eyes closed, nothing playing', minutes: 25, spec: { ...REST_BASE } },
  { id: 'rest-brown', name: 'Rest with background noise', kind: 'rest', detail: 'Lie down under brown noise or rain sounds', minutes: 25, spec: { ...REST_BASE, anchor: 'brown' } },
  { id: 'nap', name: 'Short nap', kind: 'rest', detail: 'Twenty minutes, eye mask, silence', minutes: 25, spec: { ...REST_BASE } },
  { id: 'meditate', name: 'Meditate or breathe', kind: 'rest', detail: 'Sit or lie still with your breath', minutes: 15, spec: { ...REST_BASE } },
  { id: 'somatic-walk', name: 'Walk without input', kind: 'somatic', detail: 'A walk with no screens and no audio', minutes: 25, spec: { ...REST_BASE, anchor: 'treadmill' } },
  { id: 'gym', name: 'Gym, run or stretch', kind: 'somatic', detail: 'Any workout; the body works, the head rests', minutes: 45, spec: { ...REST_BASE, anchor: 'treadmill' } },
  { id: 'somatic-fidget', name: 'Lie down with a fidget', kind: 'somatic', detail: 'Unload your spine, eyes closed, hands busy', minutes: 25, spec: { ...REST_BASE, anchor: 'fidget' } },
  { id: 'absorb-audio', name: 'Audiobook with eyes closed', kind: 'absorb', detail: 'A story, lying down, screen off', minutes: 45, spec: { modality: 'auditory', anchor: 'none', valuation: 'art', density: 'fiction', context: 'agency', scratchpad: 'single', somatic: 'supine' } },
  { id: 'absorb-audio-walk', name: 'Audiobook on a walk', kind: 'absorb', detail: 'A story while you walk', minutes: 45, spec: { modality: 'auditory', anchor: 'treadmill', valuation: 'art', density: 'fiction', context: 'agency', scratchpad: 'single', somatic: 'supine' } },
  { id: 'call-friend', name: 'Call or hang out with a friend', kind: 'absorb', detail: 'Easy conversation, no agenda', minutes: 25, spec: { modality: 'auditory', anchor: 'none', valuation: 'art', density: 'fiction', context: 'agency', scratchpad: 'single', somatic: 'supine' } },
  { id: 'absorb-literature', name: 'Read a book with music on', kind: 'absorb', detail: 'Fiction or literature, lying down', minutes: 45, spec: { modality: 'reading', anchor: 'music', valuation: 'art', density: 'fiction', context: 'agency', scratchpad: 'single', somatic: 'supine' } },
  { id: 'absorb-analysis', name: 'Read articles or essays', kind: 'absorb', detail: 'Non-fiction at a desk, tangents noted', minutes: 45, spec: { modality: 'reading', anchor: 'music', valuation: 'art', density: 'analysis', context: 'agency', scratchpad: 'tokenized', somatic: 'seated' } },
  { id: 'tv', name: 'TV or streaming', kind: 'absorb', detail: 'An episode on the couch', minutes: 45, spec: { modality: 'reading', anchor: 'none', valuation: 'utility', density: 'fiction', context: 'agency', scratchpad: 'single', somatic: 'supine' } },
  { id: 'absorb-dense', name: 'Study dense material', kind: 'absorb', detail: 'Textbooks, papers, documentation, with music', minutes: 45, spec: { modality: 'dense', anchor: 'music', valuation: 'architecture', density: 'manuals', context: 'agency', scratchpad: 'tokenized', somatic: 'seated' } },
  { id: 'meeting', name: 'Meeting or work call', kind: 'absorb', detail: 'Listening and answering, with a deadline in the room', minutes: 45, spec: { modality: 'auditory', anchor: 'none', valuation: 'utility', density: 'analysis', context: 'sprint', scratchpad: 'single', somatic: 'seated' } },
  { id: 'emails', name: 'Emails and admin', kind: 'absorb', detail: 'Inbox, messages, forms', minutes: 25, spec: { modality: 'reading', anchor: 'none', valuation: 'churn', density: 'analysis', context: 'sprint', scratchpad: 'speculative', somatic: 'seated' } },
  { id: 'social', name: 'Social media', kind: 'absorb', detail: 'Feeds and scrolling', minutes: 15, spec: { modality: 'reading', anchor: 'none', valuation: 'churn', density: 'fiction', context: 'agency', scratchpad: 'speculative', somatic: 'seated' } },
  { id: 'gaming', name: 'Video games', kind: 'absorb', detail: 'Screen, fast input, some output', minutes: 45, spec: { modality: 'reading', anchor: 'none', valuation: 'utility', density: 'analysis', context: 'agency', scratchpad: 'single', novelty: 'routine', somatic: 'seated', intensity: 'heavy' } },
  { id: 'express-journal', name: 'Journal with music on', kind: 'express', detail: 'Get it onto paper; note the tangents', minutes: 25, spec: { modality: 'expressive', anchor: 'music', valuation: 'art', density: 'null', context: 'agency', scratchpad: 'tokenized', somatic: 'seated' } },
  { id: 'express-silence', name: 'Free-write or play in silence', kind: 'express', detail: 'Journal, improvise, sketch; no input', minutes: 25, spec: { modality: 'expressive', anchor: 'none', valuation: 'art', density: 'null', context: 'agency', scratchpad: 'tokenized', somatic: 'supine' } },
  { id: 'express-walk', name: 'Think out loud on a walk', kind: 'express', detail: 'Dictate or talk it through while walking', minutes: 25, spec: { modality: 'expressive', anchor: 'treadmill', valuation: 'art', density: 'null', context: 'agency', scratchpad: 'tokenized', somatic: 'supine' } },
  { id: 'chores', name: 'Cook or do chores', kind: 'express', detail: 'Hands busy, low stakes, moving around', minutes: 25, spec: { modality: 'expressive', anchor: 'none', valuation: 'utility', density: 'null', context: 'agency', scratchpad: 'single', novelty: 'monotonous', somatic: 'supine', intensity: 'light' } },
  { id: 'ramp', name: 'Warm-up: free-write about something new', kind: 'express', detail: 'Fifteen minutes with music on, in unfamiliar territory', minutes: 15, spec: { modality: 'expressive', anchor: 'music', valuation: 'art', density: 'null', context: 'agency', scratchpad: 'tokenized', novelty: 'novel', somatic: 'seated' } },
  { id: 'execute-sprint', name: 'Deep work with music on repeat', kind: 'execute', detail: 'Build, code, write or design; your own choice of what', minutes: 45, spec: { modality: 'execution', anchor: 'music', valuation: 'architecture', density: 'proofs', context: 'agency', scratchpad: 'tokenized', somatic: 'seated' } },
  { id: 'execute-walk', name: 'Deep work at a walking desk', kind: 'execute', detail: 'The same work, on your feet', minutes: 45, spec: { modality: 'execution', anchor: 'treadmill', valuation: 'architecture', density: 'proofs', context: 'soft', scratchpad: 'tokenized', somatic: 'supine' } },
  { id: 'execute-deadline', name: 'Deep work under a deadline', kind: 'execute', detail: 'Committed delivery, music on', minutes: 45, spec: { modality: 'execution', anchor: 'music', valuation: 'architecture', density: 'proofs', context: 'sprint', scratchpad: 'tokenized', somatic: 'seated' } },
  { id: 'sleep', name: 'Go to sleep', kind: 'sleep', detail: 'Full reset, 7.5 h', minutes: 0, spec: null },
];

/** How a block stands against the best option for the current state. */
export type Standing = 'best' | 'close' | 'behind' | 'far' | 'blocked';

export interface Comparison {
  /** The reference block: the best option right now (or the block itself). */
  against: { id: string; name: string };
  /** score − reference score; 0 for the reference itself. */
  margin: number;
  standing: Standing;
  /** Predicted end state minus the reference's, per meter; null when either has no prediction. */
  deltas: StateVector | null;
  /** Composite strain difference (this − reference). */
  strainDelta: number | null;
  /** Distance from A* relative to the reference (negative: closer to the sweet spot). */
  arousalErrorDelta: number | null;
}

export interface GradedBlock {
  entry: CatalogEntry;
  /** Loadable spec with the effective cadence (null for sleep). */
  spec: BlockSpec | null;
  score: number;
  /** Where this block stands against the best option for the current state. */
  comparison: Comparison;
  /** Sub-scores, each on [0, 100]. */
  fit: number;
  outcome: number;
  horizon: number;
  /** Caps applied by guardrails / admissibility, with the reason. */
  caps: string[];
  boundMinutes: number;
  horizonMinutes: number;
  stopReason: string;
  predicted: StateVector | null;
  delta: StateVector | null;
  /** ΔU of the state utility over the block. */
  deltaUtility: number | null;
}

/** State utility used to score predicted outcomes: reserves up, backlog / strain / arousal error down, depth up. */
export function stateUtility(x: StateVector, k: Constants): number {
  return x.E - x.B - compositeStrain(x) - Math.abs(x.A - k.Astar) + 0.25 * x.V;
}

type ScoredBlock = Omit<GradedBlock, 'comparison'>;

/** Margins (points) that still read as "nearly as good" / "a step behind" the best. */
export const CLOSE_MARGIN = 8;
export const BEHIND_MARGIN = 25;

/** Two loadable specs describe the same block (same controls, context and length). */
export function sameBlock(a: BlockSpec | null, b: BlockSpec | null): boolean {
  if (!a || !b) return a === b;
  return (
    a.modality === b.modality &&
    a.anchor === b.anchor &&
    a.valuation === b.valuation &&
    a.density === b.density &&
    a.context === b.context &&
    a.scratchpad === b.scratchpad &&
    a.novelty === b.novelty &&
    a.somatic === b.somatic &&
    (a.intensity ?? 'standard') === (b.intensity ?? 'standard') &&
    (a.speed ?? DEFAULT_SPEED) === (b.speed ?? DEFAULT_SPEED) &&
    blockMinutes(a) === blockMinutes(b)
  );
}

/**
 * Compare a scored block with the reference (the best option right now). A block that a
 * guardrail caps is "blocked" whatever its margin; otherwise the margin to the best decides.
 */
export function compareBlocks(g: ScoredBlock, against: ScoredBlock, k: Constants): Comparison {
  const self = g.entry.id === against.entry.id || (g.entry.kind === against.entry.kind && sameBlock(g.spec, against.spec));
  const margin = self ? 0 : g.score - against.score;
  let standing: Standing;
  if (!self && g.caps.length > 0) standing = 'blocked';
  else if (self || margin >= 0) standing = 'best';
  else if (margin >= -CLOSE_MARGIN) standing = 'close';
  else if (margin >= -BEHIND_MARGIN) standing = 'behind';
  else standing = 'far';
  const a = g.predicted;
  const b = against.predicted;
  const both = a && b;
  return {
    against: { id: against.entry.id, name: against.entry.name },
    margin,
    standing,
    deltas: both ? { E: a.E - b.E, B: a.B - b.B, Fvis: a.Fvis - b.Fvis, Fbody: a.Fbody - b.Fbody, A: a.A - b.A, V: a.V - b.V } : null,
    strainDelta: both ? compositeStrain(a) - compositeStrain(b) : null,
    arousalErrorDelta: both ? Math.abs(a.A - k.Astar) - Math.abs(b.A - k.Astar) : null,
  };
}

type FitRow = Record<ConfigKind, number>;

/** Routing fit: how well each block kind serves the routed quadrant (0–100). */
function fitTable(r: Routing, d: Diagnostics, F: number): FitRow {
  switch (r.quadrant) {
    case 'SINGULARITY':
      return d.singularityMode === 'structural'
        ? { rest: 100, somatic: 60, express: 35, absorb: 0, execute: 10, sleep: 70 }
        : { rest: 60, somatic: 40, express: 5, absorb: 0, execute: 0, sleep: 100 };
    case 'I-A':
      return { rest: 100, somatic: F >= 0.4 ? 70 : 45, express: 20, absorb: 0, execute: 0, sleep: 30 };
    case 'I-B':
      return { rest: 40, somatic: 50, express: 100, absorb: 0, execute: 35, sleep: 10 };
    case 'III':
      return { rest: 70, somatic: 100, express: 45, absorb: 35, execute: 10, sleep: 20 };
    case 'II':
      return { rest: 60, somatic: 50, express: 40, absorb: 100, execute: 10, sleep: 10 };
    case 'IV':
      return { rest: 10, somatic: 30, express: 50, absorb: 40, execute: 100, sleep: 0 };
    case 'IV-B':
      return { rest: 10, somatic: 30, express: 80, absorb: 20, execute: 90, sleep: 0 };
    default:
      return { rest: 50, somatic: 50, express: 50, absorb: 50, execute: 50, sleep: 0 };
  }
}

/** Score one catalog entry against the current state (comparison attached by the caller). */
function gradeEntry(entry: CatalogEntry, x: StateVector, hoursAwake: number, d: Diagnostics, r: Routing, k: Constants): ScoredBlock {
  const F = compositeStrain(x);
  const fits = fitTable(r, d, F);
  const g = d.guardrails;
  const U0 = stateUtility(x, k);
  {
    const caps: string[] = [];
    const fit = fits[entry.kind];
    if (entry.kind === 'sleep' || !entry.spec) {
      const indicated = d.regime === 'singularity';
      const predicted = applySleepReset(x, 7.5);
      const score = indicated ? (d.singularityMode === 'structural' ? 70 : 100) : 15;
      if (!indicated) caps.push('not indicated: I* numerator > 0 at Γ = 1, F < F_term, t_awake < t_late');
      return {
        entry,
        spec: null,
        score,
        fit,
        outcome: 100,
        horizon: 100,
        caps,
        boundMinutes: 0,
        horizonMinutes: 0,
        stopReason: 'session terminated',
        predicted,
        delta: { E: predicted.E - x.E, B: predicted.B - x.B, Fvis: predicted.Fvis - x.Fvis, Fbody: predicted.Fbody - x.Fbody, A: predicted.A - x.A, V: predicted.V - x.V },
        deltaUtility: stateUtility(predicted, k) - U0,
      };
    }
    const kind = entry.kind as Exclude<ConfigKind, 'sleep'>;
    const rule = STOP_RULES[kind];
    const probe = withCadence(entry.spec, 90);
    const { horizon, reason, kind: stopKind } = simulateHorizon(x, hoursAwake, probe, k, rule.check, entry.minutes);
    let bound: number;
    let horizonScore: number;
    let stopReason: string;
    if (stopKind === 'violation') {
      bound = snapCadence(horizon);
      horizonScore = 100 * Math.min(1, horizon / entry.minutes);
      stopReason = `${reason} at ${horizon} m`;
    } else if (stopKind === 'goal') {
      bound = Math.min(entry.minutes, Math.max(15, snapCadenceUp(horizon)));
      horizonScore = 100;
      stopReason = `${reason} reached at ${horizon} m`;
    } else {
      bound = entry.minutes;
      horizonScore = 100;
      stopReason = `no boundary inside ${entry.minutes} m`;
    }
    const effective = Math.max(bound, Math.min(15, entry.minutes));
    const specOut: BlockSpec = withMinutes(withCadence(entry.spec, 15), effective);
    const inputs = resolveSpec(specOut);
    const I1 = inputs.u.Ivis + inputs.u.Iaud;
    const result = integrateBlock(x, hoursAwake, inputs, effective, k);
    const dU = stateUtility(result.x, k) - U0;
    const outcome = 50 + 50 * Math.max(-1, Math.min(1, dU / 0.2));
    let score = 0.45 * fit + 0.4 * outcome + 0.15 * horizonScore;
    let cap = 100;
    const capTo = (v: number, why: string) => {
      cap = Math.min(cap, v);
      caps.push(why);
    };
    // Specific reasons first; the generic boundary cap last so the plain reason leads with the cause.
    if (I1 > 0 && d.regime === 'singularity' && d.singularityMode !== 'somatic') capTo(10, 'input prohibited: I*(t) ≤ 0');
    if (I1 > 0 && g.backlogSaturated) capTo(15, `backlog lock: B ≥ ${k.BsatLock.toFixed(2)} until an output block runs`);
    if (inputs.u.Ivis > 0 && g.opticalCutoff) capTo(15, `optical cutoff: F_vis ≥ ${k.FvisCutoff.toFixed(2)} forces I_vis = 0`);
    if (I1 > 0 && result.mean.phiIn < 0) capTo(30, `depleting intake: Φ_in = ${result.mean.phiIn.toFixed(3)} < 0 (I₁ = ${I1.toFixed(2)} vs I* = ${result.mean.Istar.toFixed(2)})`);
    if (kind === 'rest' && g.underArousal) capTo(35, `under-arousal gate: A = ${x.A.toFixed(2)} with E = ${x.E.toFixed(2)} — rest rejected`);
    if (kind === 'execute' && d.singularityMode === 'somatic') capTo(10, 'terminal strain: F ≥ F_term');
    if (stopKind === 'violation' && bound < 15) capTo(25, `boundary trips inside 15 m (${reason})`);
    score = Math.min(score, cap);
    score = Math.round(Math.max(0, Math.min(100, score)));
    return {
      entry,
      spec: specOut,
      score,
      fit: Math.round(fit),
      outcome: Math.round(outcome),
      horizon: Math.round(horizonScore),
      caps,
      boundMinutes: bound,
      horizonMinutes: horizon,
      stopReason,
      predicted: result.x,
      delta: result.delta,
      deltaUtility: dU,
    };
  }
}

/**
 * Score every catalog entry (and any user presets) against the current state, sorted best
 * first, each compared with the best. `listeningSpeed` is the usual playback speed applied to
 * the built-in listening entries (presets keep their own speed).
 */
export function gradeCatalog(
  x: StateVector,
  hoursAwake: number,
  d: Diagnostics,
  r: Routing,
  k: Constants,
  presets: readonly UserPreset[] = [],
  blockLength?: number,
  listeningSpeed: SpeedKey = DEFAULT_SPEED,
): GradedBlock[] {
  const builtIn: CatalogEntry[] = BLOCK_CATALOG.map((e) =>
    e.spec && e.spec.modality === 'auditory' && !e.spec.speed && listeningSpeed !== DEFAULT_SPEED ? { ...e, spec: { ...e.spec, speed: listeningSpeed } } : e,
  );
  const entries: CatalogEntry[] = [...presets.map(presetEntry), ...builtIn].map((e) =>
    blockLength && e.kind !== 'sleep' ? { ...e, minutes: Math.min(MAX_CUSTOM_MINUTES, Math.max(MIN_CUSTOM_MINUTES, Math.round(blockLength))) } : e,
  );
  const scored = entries.map((entry) => gradeEntry(entry, x, hoursAwake, d, r, k)).sort((a, b) => b.score - a.score || a.entry.name.localeCompare(b.entry.name));
  const best = scored[0];
  return scored.map((g) => ({ ...g, comparison: compareBlocks(g, best, k) }));
}

/**
 * Score an arbitrary block (the one being programmed or described) exactly as a catalog
 * entry would be, at its own length, and compare it with `against` (the best option right
 * now; the block itself when omitted).
 */
export function gradeBlock(spec: BlockSpec, x: StateVector, hoursAwake: number, d: Diagnostics, r: Routing, k: Constants, name = 'This block', against?: GradedBlock): GradedBlock {
  const { cadence: _cadence, customMinutes: _cm, ...base } = spec;
  void _cadence;
  void _cm;
  const entry: CatalogEntry = { id: 'armed', name, kind: inferKind(spec), detail: 'The block you are about to log', minutes: blockMinutes(spec), spec: base };
  const scored = { ...gradeEntry(entry, x, hoursAwake, d, r, k), spec };
  // The armed block keeps its own length: the score already reflects any boundary inside it.
  return { ...scored, comparison: compareBlocks(scored, against ?? scored, k) };
}

// ---------------------------------------------------------------------------
// Persistence codec
// ---------------------------------------------------------------------------

export interface HistoryEntry {
  k: number;
  at: string;
  kind: 'block' | 'sleep' | 'override';
  /** Free-text description the block was logged from, when any. */
  note?: string;
  dtMinutes: number;
  spec: BlockSpec | null;
  sleepHours?: number;
  xBefore: StateVector;
  xAfter: StateVector;
  hoursAwakeBefore: number;
  hoursAwakeAfter: number;
  mean: BlockResult['mean'] | null;
  quadrant: QuadrantId;
}

export interface UserPreset {
  id: string;
  name: string;
  spec: BlockSpec;
  createdAt: string;
}

export interface PersistedState {
  version: 1;
  x: StateVector;
  hoursAwake: number;
  /** User-defined block presets; graded alongside the built-in catalog. */
  presets: UserPreset[];
  /** Simple mode: show symbols, predicates and mono diagnostics alongside the plain copy. */
  showMath: boolean;
  /** Fixed block length in minutes used for grading, boundaries and one-click logging. */
  blockLength: number;
  /** Usual playback speed for listening blocks: applied to the catalog's listening entries and described listening blocks. */
  listeningSpeed: SpeedKey;
  /** Set when B crosses B_sat; cleared by an output block or once B < 0.40. */
  backlogLatch: boolean;
  /** Simple (single-column flow) or advanced (full instrument panel) interface. */
  uiMode: 'simple' | 'advanced';
  blockIndex: number;
  history: HistoryEntry[];
  constants: Constants;
  spec: BlockSpec;
  updatedAt: string;
}

export const STORAGE_KEY = 'prismtask.capacity.v1';
export const HISTORY_LIMIT = 200;

export function defaultPersisted(): PersistedState {
  return {
    version: 1,
    x: { ...DEFAULT_STATE },
    hoursAwake: 0,
    presets: [],
    showMath: false,
    blockLength: DEFAULT_BLOCK_LENGTH,
    listeningSpeed: DEFAULT_SPEED,
    backlogLatch: false,
    uiMode: 'simple',
    blockIndex: 0,
    history: [],
    constants: { ...DEFAULT_CONSTANTS },
    spec: { ...DEFAULT_SPEC },
    updatedAt: new Date(0).toISOString(),
  };
}

function isFiniteNumber(v: unknown): v is number {
  return typeof v === 'number' && Number.isFinite(v);
}

function sanitizeState(raw: unknown): StateVector | null {
  if (!raw || typeof raw !== 'object') return null;
  const r = raw as Record<string, unknown>;
  const out: Partial<StateVector> = {};
  for (const key of STATE_KEYS) {
    const v = r[key];
    if (!isFiniteNumber(v)) return null;
    out[key] = clamp01(v);
  }
  return out as StateVector;
}

function sanitizeConstants(raw: unknown): Constants {
  const out: Constants = { ...DEFAULT_CONSTANTS };
  if (!raw || typeof raw !== 'object') return out;
  const r = raw as Record<string, unknown>;
  for (const meta of CONSTANT_META) {
    const v = r[meta.key];
    if (isFiniteNumber(v)) out[meta.key] = Math.min(meta.max, Math.max(meta.min, v));
  }
  return out;
}

type CatalogedSpecKey = 'cadence' | 'modality' | 'anchor' | 'valuation' | 'density' | 'context' | 'scratchpad' | 'novelty' | 'somatic';
const SPEC_KEYS: readonly CatalogedSpecKey[] = ['cadence', 'modality', 'anchor', 'valuation', 'density', 'context', 'scratchpad', 'novelty', 'somatic'];
const SPEC_CATALOG: Record<CatalogedSpecKey, readonly Option<string>[]> = {
  cadence: CADENCES,
  modality: MODALITIES,
  anchor: ANCHORS,
  valuation: VALUATIONS,
  density: DENSITIES,
  context: CONTEXTS,
  scratchpad: SCRATCHPADS,
  novelty: NOVELTIES,
  somatic: SOMATICS,
};

export function sanitizeSpec(raw: unknown): BlockSpec {
  const out: Record<string, unknown> = { ...DEFAULT_SPEC };
  if (raw && typeof raw === 'object') {
    const r = raw as Record<string, unknown>;
    for (const key of SPEC_KEYS) {
      const v = r[key];
      if (typeof v === 'string' && SPEC_CATALOG[key].some((o) => o.key === v)) out[key] = v;
    }
    if (typeof r.intensity === 'string' && INTENSITIES.some((o) => o.key === r.intensity)) out.intensity = r.intensity;
    if (typeof r.speed === 'string' && PLAYBACK_SPEEDS.some((o) => o.key === r.speed)) out.speed = r.speed;
    if (isFiniteNumber(r.customMinutes)) out.customMinutes = Math.min(MAX_CUSTOM_MINUTES, Math.max(MIN_CUSTOM_MINUTES, Math.round(r.customMinutes)));
    if (r.cadence === CUSTOM_CADENCE && isFiniteNumber(out.customMinutes)) out.cadence = CUSTOM_CADENCE;
  }
  return out as unknown as BlockSpec;
}

/** Parse a persisted JSON payload, tolerating missing or corrupt fields. */
export function decodePersisted(json: string | null): PersistedState {
  const base = defaultPersisted();
  if (!json) return base;
  let raw: unknown;
  try {
    raw = JSON.parse(json);
  } catch {
    return base;
  }
  if (!raw || typeof raw !== 'object') return base;
  const r = raw as Record<string, unknown>;
  const x = sanitizeState(r.x) ?? base.x;
  const hoursAwake = isFiniteNumber(r.hoursAwake) ? Math.max(0, r.hoursAwake) : 0;
  const blockIndex = isFiniteNumber(r.blockIndex) ? Math.max(0, Math.floor(r.blockIndex)) : 0;
  const history = Array.isArray(r.history)
    ? (r.history as unknown[])
        .filter((h): h is HistoryEntry => {
          if (!h || typeof h !== 'object') return false;
          const e = h as Record<string, unknown>;
          return sanitizeState(e.xBefore) !== null && sanitizeState(e.xAfter) !== null && typeof e.at === 'string';
        })
        .slice(-HISTORY_LIMIT)
    : [];
  const presets: UserPreset[] = Array.isArray(r.presets)
    ? (r.presets as unknown[])
        .filter((e): e is Record<string, unknown> => !!e && typeof e === 'object')
        .filter((e) => typeof e.id === 'string' && typeof e.name === 'string' && e.name.trim().length > 0)
        .slice(0, 50)
        .map((e) => ({ id: e.id as string, name: (e.name as string).trim().slice(0, 60), spec: sanitizeSpec(e.spec), createdAt: typeof e.createdAt === 'string' ? e.createdAt : base.updatedAt }))
    : [];
  return {
    version: 1,
    x,
    hoursAwake,
    presets,
    showMath: r.showMath === true,
    blockLength: isFiniteNumber(r.blockLength) ? Math.min(MAX_CUSTOM_MINUTES, Math.max(MIN_CUSTOM_MINUTES, Math.round(r.blockLength))) : DEFAULT_BLOCK_LENGTH,
    listeningSpeed: typeof r.listeningSpeed === 'string' && PLAYBACK_SPEEDS.some((o) => o.key === r.listeningSpeed) ? (r.listeningSpeed as SpeedKey) : DEFAULT_SPEED,
    backlogLatch: r.backlogLatch === true,
    uiMode: r.uiMode === 'advanced' ? 'advanced' : 'simple',
    blockIndex,
    history,
    constants: sanitizeConstants(r.constants),
    spec: sanitizeSpec(r.spec),
    updatedAt: typeof r.updatedAt === 'string' ? r.updatedAt : base.updatedAt,
  };
}

export function encodePersisted(state: PersistedState): string {
  return JSON.stringify({ ...state, history: state.history.slice(-HISTORY_LIMIT) });
}
