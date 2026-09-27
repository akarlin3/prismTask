/**
 * Plain-language layer over the capacity model: names, meanings, and
 * generated explanations used by the simple interface. Pure functions.
 */
import {
  compositeStrain,
  type Comparison,
  type Diagnostics,
  type GradedBlock,
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
  if (cap.startsWith('under-arousal gate')) return 'You are under-activated, not tired: rest will not help.';
  if (cap.startsWith('terminal strain')) return 'Strain is at the limit; no more output.';
  if (cap.startsWith('boundary trips')) return 'Hits a limit within fifteen minutes.';
  if (cap.startsWith('not indicated')) return 'Not needed yet.';
  return cap;
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
 * One plain sentence explaining a scored block: the cap that applies, else fit, predicted effect
 * and safe duration. `skipCaps` leaves the cap out (when the comparison line already names it).
 */
export function plainReason(g: GradedBlock, x: StateVector, skipCaps = false): string {
  const fit = g.fit >= 90 ? 'Fits what you need now' : g.fit >= 60 ? 'Reasonable now' : g.fit >= 30 ? 'Not the priority now' : 'Wrong move for this state';
  if (g.caps.length > 0 && !skipCaps) return `${plainCap(g.caps[0])} ${fit}.`;
  const effect = g.predicted ? plainEffect(x, g.predicted).join(', ') : 'no simulated effect';
  const horizon = g.entry.kind === 'sleep' ? '' : g.horizon < 100 ? (g.boundMinutes < 15 ? ' Hits a limit within fifteen minutes.' : ` Safe for about ${g.boundMinutes} minutes.`) : '';
  return `${fit}; ${effect}.${horizon}`;
}

export function joinEffects(parts: string[]): string {
  if (parts.length <= 1) return parts[0] ?? '';
  return `${parts.slice(0, -1).join(', ')} and ${parts[parts.length - 1]}`;
}

/** Short label for where a block stands against the best option right now. */
export const STANDING_LABEL: Record<Standing, string> = {
  best: 'Best now',
  close: 'Nearly as good',
  behind: 'A step behind',
  far: 'Well behind',
  blocked: 'Not now',
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

/** One sentence comparing a block with the best option for the current state. */
export function plainComparison(g: GradedBlock): string {
  const c = g.comparison;
  const self = c.standing === 'best' && c.against.id === g.entry.id;
  if (c.standing === 'blocked') {
    const cap = plainCap(g.caps[0] ?? '');
    return `Not now: ${cap.charAt(0).toLowerCase()}${cap.slice(1)}`;
  }
  if (self || (c.standing === 'best' && c.margin === 0 && !c.deltas)) return 'The best option for your state right now.';
  const diffs = joinEffects(plainDifferences(c));
  if (c.standing === 'best') {
    if (c.margin > 0) return diffs ? `Better than anything on the list: ${diffs} than ${c.against.name}.` : `Better than anything on the list.`;
    return diffs ? `As good as ${c.against.name}: ${diffs}.` : `As good as ${c.against.name}.`;
  }
  const lead = c.standing === 'close' ? `Nearly as good as ${c.against.name}` : c.standing === 'behind' ? `A step behind ${c.against.name}` : `Well behind ${c.against.name}`;
  return `${lead}: ${diffs || 'about the same result'}.`;
}
