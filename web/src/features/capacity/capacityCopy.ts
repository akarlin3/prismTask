/**
 * Plain-language layer over the capacity model: names, meanings, and
 * generated explanations used by the simple interface. Pure functions.
 */
import {
  compositeStrain,
  type Comparison,
  type Diagnostics,
  type GradedBlock,
  type GuardrailType,
  type GuardrailSeverity,
  type GuardrailWarning,
  type Routing,
  type Standing,
  type StateKey,
  type StateVector,
} from './capacityModel';

export interface PlainSeries {
  key: StateKey;
  name: string;
  meaning: string;
  /** 'high' when a higher value is better, 'low' when lower is better, 'mid' for a target. */
  better: 'high' | 'low' | 'mid';
}

export const PLAIN_SERIES: readonly PlainSeries[] = [
  { key: 'E', name: 'Energy', meaning: 'Reserve you can spend. 1 is fully charged, 0 is empty.', better: 'high' },
  { key: 'B', name: 'Backlog', meaning: 'Open loops and half-finished thoughts. 0 is clear, 1 is jammed.', better: 'low' },
  { key: 'Fvis', name: 'Eye strain', meaning: 'Screen and focus fatigue in the eyes. 0 is fresh.', better: 'low' },
  { key: 'Fbody', name: 'Body strain', meaning: 'Neck, back and muscle tension. 0 is fresh.', better: 'low' },
  { key: 'A', name: 'Activation', meaning: 'How switched-on you are. 0.5 is the sweet spot; low is stuck, high is scattered.', better: 'mid' },
  { key: 'V', name: 'Depth', meaning: 'How substantive your recent activity was. 1 is real work or art, 0 is churn.', better: 'high' },
];

export const PLAIN_BY_KEY: Record<StateKey, PlainSeries> = Object.fromEntries(PLAIN_SERIES.map((s) => [s.key, s])) as Record<StateKey, PlainSeries>;

/** Headline and one-sentence guidance for the routed quadrant. */
export function plainQuadrant(r: Routing): { headline: string; guidance: string } {
  switch (r.quadrant) {
    case 'SINGULARITY':
      return r.title.includes('Zero-Input Rest')
        ? { headline: 'Stop taking things in', guidance: 'Nothing you read or listen to will restore you right now. Rest with no input; if that does not reopen a window, sleep.' }
        : { headline: 'Stop and sleep', guidance: 'You are past the point where rest or work helps. End the session and sleep.' };
    case 'I-A':
      return { headline: 'Rest, no input', guidance: 'Your head is full and your reserves are low. Lie down somewhere dark and quiet with nothing playing.' };
    case 'I-B':
      return { headline: 'Write it out', guidance: 'Your head is full but you have energy. Get it onto paper: journal, improvise, dump the scratchpad.' };
    case 'II':
      return { headline: 'Take in something gentle', guidance: 'Reserves are low but gentle intake will restore you: fiction, an audiobook with your eyes closed.' };
    case 'III':
      return { headline: 'Move your body', guidance: 'Strain is the limiting factor. Walk, stretch, unload your spine, and keep your eyes off screens.' };
    case 'IV':
      return { headline: 'Good to build', guidance: 'You have the reserves for deep work. Start a focused sprint and stop at the boundary, not when the flow runs out.' };
    case 'IV-B':
      return { headline: 'Build, with a scratchpad ready', guidance: 'You can do deep work, but your backlog is growing. Keep tangents on paper, or write for fifteen minutes first.' };
    default:
      return { headline: r.title, guidance: r.summary };
  }
}

/** Short plain label for the intake regime. */
export function plainRegime(d: Diagnostics): { label: string; detail: string; tone: 'good' | 'warn' | 'bad' } {
  switch (d.regime) {
    case 'nominal':
      return { label: 'Intake helps right now', detail: 'Gentle reading or listening would restore you rather than drain you.', tone: 'good' };
    case 'arousal-limited':
      return { label: 'Wake up first', detail: 'You are under-activated. Intake will drain you until you get moving or put music on.', tone: 'warn' };
    case 'saturated':
      return { label: 'Nothing to refill', detail: 'You are already charged. Taking things in would only add backlog; make something instead.', tone: 'warn' };
    case 'singularity':
    default:
      return {
        label: d.singularityMode === 'late' ? 'Past the limit: sleep' : d.singularityMode === 'somatic' ? 'Strain at the limit: sleep' : 'Intake drains you',
        detail: 'No reading or listening can restore you in this state.',
        tone: 'bad',
      };
  }
}

/** Plain rewrite of a catalog cap reason. */
export function plainCap(cap: string): string {
  if (cap.startsWith('backlog lock')) return 'Intake is locked until you write something out.';
  if (cap.startsWith('optical cutoff')) return 'Your eyes need a break: nothing on screen or page.';
  if (cap.startsWith('input prohibited')) return 'Taking things in would drain you right now.';
  if (cap.startsWith('depleting intake')) return 'This would drain more than it restores.';
  if (cap.startsWith('under-arousal gate')) return 'Under-activated rather than tired: rest is unlikely to take.';
  if (cap.startsWith('terminal strain')) return 'Strain is at the limit; no more output.';
  if (cap.startsWith('boundary trips')) return plainBoundary(cap);
  if (cap.startsWith('not indicated')) return 'Not needed yet.';
  return cap;
}

/** Which limit a boundary trip hits inside the block, in words. */
function plainBoundary(cap: string): string {
  if (/F_vis ≥/.test(cap)) return 'Your eyes would reach their limit within fifteen minutes.';
  if (/(^|[^_])F ≥/.test(cap)) return 'Strain would reach its limit within fifteen minutes.';
  if (/E </.test(cap)) return 'Energy would drop below its floor within fifteen minutes.';
  if (/t_late/.test(cap)) return 'The late-phase limit would trip within fifteen minutes.';
  if (/B ≥/.test(cap)) return 'Backlog would fill up within fifteen minutes.';
  if (/Φ_in </.test(cap)) return 'Intake would start to drain more than it restores within fifteen minutes.';
  if (/Φ_out </.test(cap)) return 'Output would start to cost more than it clears within fifteen minutes.';
  return 'Reaches a limit within fifteen minutes.';
}

const EFFECT_THRESHOLD = 0.03;

/** Sentence fragments describing the predicted effect of a block. */
export function plainEffect(before: StateVector, after: StateVector): string[] {
  const out: string[] = [];
  const dE = after.E - before.E;
  const dB = after.B - before.B;
  const dF = compositeStrain(after) - compositeStrain(before);
  const dA = Math.abs(after.A - 0.5) - Math.abs(before.A - 0.5);
  if (dE >= EFFECT_THRESHOLD) out.push('restores energy');
  else if (dE <= -EFFECT_THRESHOLD) out.push('costs energy');
  if (dB <= -EFFECT_THRESHOLD) out.push('clears backlog');
  else if (dB >= EFFECT_THRESHOLD) out.push('adds backlog');
  if (dF <= -EFFECT_THRESHOLD) out.push('eases strain');
  else if (dF >= EFFECT_THRESHOLD) out.push('adds strain');
  if (dA <= -EFFECT_THRESHOLD) out.push('brings activation toward the sweet spot');
  else if (dA >= EFFECT_THRESHOLD) out.push('pushes activation off the sweet spot');
  if (out.length === 0) out.push('roughly neutral');
  return out;
}

/**
 * One plain sentence explaining a scored block: fit, predicted effect and safe duration. The
 * guardrails it trips are reported separately (`plainGuardrail`), never folded in here.
 */
export function plainReason(g: GradedBlock, x: StateVector): string {
  // Fit is described, not judged: how closely the block matches what the routed state calls for.
  const fit = g.fit >= 90 ? 'Fits what your state calls for' : g.fit >= 60 ? 'A fair fit for your state' : g.fit >= 30 ? 'A loose fit for your state' : 'Not what your state calls for right now';
  const effect = g.predicted ? plainEffect(x, g.predicted).join(', ') : 'no simulated effect';
  // A boundary inside fifteen minutes is a guardrail warning of its own; only a usable horizon is stated here.
  const horizon = g.entry.kind !== 'sleep' && g.horizon < 100 && g.boundMinutes >= 15 ? ` Safe for about ${g.boundMinutes} minutes.` : '';
  return `${fit}; ${effect}.${horizon}`;
}

/** The name of each guardrail, as shown on a warning. */
export const GUARDRAIL_LABEL: Record<GuardrailType, string> = {
  singularity: 'Input prohibited',
  backlogLock: 'Backlog lock',
  opticalCutoff: 'Optical cutoff',
  depletingIntake: 'Depleting intake',
  underArousal: 'Under-arousal gate',
  terminalStrain: 'Terminal strain',
  boundary: 'Hard boundary',
  notIndicated: 'Not indicated',
};

/** A guardrail warning in words: its name, what it means for this block, and whether it is a warning or a note. */
export function plainGuardrail(w: GuardrailWarning): { label: string; text: string; severity: GuardrailSeverity } {
  return { label: GUARDRAIL_LABEL[w.type], text: plainCap(w.detail), severity: w.severity };
}

export function joinEffects(parts: string[]): string {
  if (parts.length <= 1) return parts[0] ?? '';
  return `${parts.slice(0, -1).join(', ')} and ${parts[parts.length - 1]}`;
}

/**
 * Short label for where a block stands against the best option right now. Judgement-free: the
 * words describe how closely the score matches the recommendation, never whether the block is
 * good or bad, and the pill only shows with Show math or in Advanced mode.
 */
export const STANDING_LABEL: Record<Standing, string> = {
  best: 'Top match',
  close: 'Close match',
  behind: 'Partial match',
  far: 'Different path',
};

const DIFF_THRESHOLD = 0.02;

/** Phrases for how a block's predicted end state differs from the reference block's. */
export function plainDifferences(c: Comparison): string[] {
  if (!c.deltas) return [];
  const out: string[] = [];
  const d = c.deltas;
  if (d.E >= DIFF_THRESHOLD) out.push('more energy');
  else if (d.E <= -DIFF_THRESHOLD) out.push('less energy');
  if (d.B <= -DIFF_THRESHOLD) out.push('less backlog');
  else if (d.B >= DIFF_THRESHOLD) out.push('more backlog');
  const dF = c.strainDelta ?? 0;
  if (dF <= -DIFF_THRESHOLD) out.push('less strain');
  else if (dF >= DIFF_THRESHOLD) out.push('more strain');
  const dA = c.arousalErrorDelta ?? 0;
  if (dA <= -DIFF_THRESHOLD) out.push('activation closer to the sweet spot');
  else if (dA >= DIFF_THRESHOLD) out.push('activation further from the sweet spot');
  if (d.V >= DIFF_THRESHOLD) out.push('more depth');
  else if (d.V <= -DIFF_THRESHOLD) out.push('less depth');
  // Three differences at most, in meter order, so the sentence stays readable.
  return out.slice(0, 3);
}

/**
 * One sentence comparing a block with the best option for the current state. Judgement-free: it
 * names the recommended block and the predicted differences, and never ranks the block as better,
 * worse or behind (the margin is available under Show math).
 */
export function plainComparison(g: GradedBlock): string {
  const c = g.comparison;
  if (c.self || (c.standing === 'best' && c.margin === 0 && !c.deltas)) return 'This is the recommended block for your state right now.';
  const diffs = joinEffects(plainDifferences(c));
  return diffs ? `Compared with the recommended block (${c.against.name}): ${diffs}.` : `About the same result as the recommended block (${c.against.name}).`;
}
