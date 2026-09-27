/**
 * Free-text block describer: turns "15 min reading a novel on the couch"
 * into a BlockSpec plus the cues that drove each choice. Rule-based and
 * offline, so it works in the standalone build and is fully testable; the
 * result is a proposal the user confirms or adjusts before logging.
 */
import {
  CADENCES,
  CUSTOM_CADENCE,
  DEFAULT_SPEC,
  MAX_CUSTOM_MINUTES,
  MIN_CUSTOM_MINUTES,
  type AnchorKey,
  type BlockSpec,
  type ContextKey,
  type DensityKey,
  type IntensityKey,
  type ModalityKey,
  type NoveltyKey,
  type ScratchpadKey,
  type SomaticKey,
  type ValuationKey,
} from './capacityModel';

export type DescribedField = 'modality' | 'anchor' | 'valuation' | 'density' | 'context' | 'scratchpad' | 'novelty' | 'somatic' | 'intensity' | 'duration';

export interface Cue {
  field: DescribedField;
  word: string;
  choice: string;
}

export interface DescribedBlock {
  spec: BlockSpec;
  minutes: number;
  /** 0–1: how much of the classification rests on evidence rather than defaults. */
  confidence: number;
  cues: Cue[];
  /** Fields for which no evidence was found (defaults were used). */
  unsure: DescribedField[];
}

type Lexicon<K extends string> = readonly (readonly [RegExp, K, number])[];

const rx = (s: string) => new RegExp(`\\b(?:${s})\\b`, 'i');

// Activity templates: strong multi-field cues for everyday activities.
interface Template {
  pattern: RegExp;
  word: string;
  spec: Partial<BlockSpec>;
}

const TEMPLATES: readonly Template[] = [
  { pattern: rx('gym|workout|worked out|lifting|weights|run|ran|running|jog|jogging|cycling|bike ride|swim|swimming|yoga|stretch(?:ing|ed)?|pilates'), word: 'workout', spec: { modality: 'zero', anchor: 'treadmill', somatic: 'supine', density: 'null', valuation: 'utility', intensity: 'standard' } },
  { pattern: rx('nap|napped|dozed|slept|sleeping|lay in the dark|lie in the dark|eye mask|meditat\\w*|breathing exercise|breathwork|rested|resting|zoned out'), word: 'rest', spec: { modality: 'zero', anchor: 'none', somatic: 'supine', density: 'null', valuation: 'utility', scratchpad: 'single' } },
  { pattern: rx('cook(?:ed|ing)?|dishes|laundry|clean(?:ed|ing)?|tidied|tidying|chores|vacuum\\w*|groceries|errands|ironing'), word: 'chores', spec: { modality: 'expressive', anchor: 'none', somatic: 'supine', density: 'null', valuation: 'utility', intensity: 'light', novelty: 'monotonous' } },
  { pattern: rx('meeting|standup|stand-up|1:1|one on one|sync|call with|on a call|zoom|teams call|interview'), word: 'meeting', spec: { modality: 'auditory', anchor: 'none', somatic: 'seated', density: 'analysis', valuation: 'utility', context: 'sprint' } },
  { pattern: rx('emails?|inbox|slack|teams messages|admin|paperwork|forms|invoices?|expenses|triage'), word: 'admin', spec: { modality: 'reading', anchor: 'none', somatic: 'seated', density: 'analysis', valuation: 'churn' } },
  { pattern: rx('scroll(?:ed|ing)?|doomscroll\\w*|twitter|x\\.com|instagram|insta|tiktok|reels|shorts|reddit|facebook|feed|threads app'), word: 'scrolling', spec: { modality: 'reading', anchor: 'none', density: 'fiction', valuation: 'churn', scratchpad: 'speculative' } },
  { pattern: rx('youtube|watched videos?|video essays?'), word: 'videos', spec: { modality: 'reading', anchor: 'none', density: 'analysis', valuation: 'churn' } },
  { pattern: rx('movie|film|cinema|documentary'), word: 'film', spec: { modality: 'reading', anchor: 'none', density: 'fiction', valuation: 'art', somatic: 'supine' } },
  { pattern: rx('tv|netflix|series|episodes?|show|streaming|binge\\w*'), word: 'tv', spec: { modality: 'reading', anchor: 'none', density: 'fiction', valuation: 'utility', somatic: 'supine' } },
  { pattern: rx('gam(?:ed|ing)|video ?games?|played (?:a )?game|playstation|xbox|switch|steam|minecraft|fortnite'), word: 'gaming', spec: { modality: 'reading', anchor: 'none', density: 'analysis', valuation: 'utility', intensity: 'heavy', somatic: 'seated' } },
  { pattern: rx('audiobook|audio book'), word: 'audiobook', spec: { modality: 'auditory', density: 'fiction', valuation: 'art' } },
  { pattern: rx('podcast|radio|interview show'), word: 'podcast', spec: { modality: 'auditory', density: 'analysis', valuation: 'utility' } },
  { pattern: rx('called (?:my|a)|phoned|chat(?:ted)? with|talked (?:to|with)|catch(?:ing)? up with|hung out|dinner with|lunch with|coffee with'), word: 'conversation', spec: { modality: 'auditory', density: 'fiction', valuation: 'art', context: 'agency', anchor: 'none' } },
  { pattern: rx('commut(?:e|ed|ing)|drove|driving|on the (?:bus|train|tube|subway)'), word: 'commute', spec: { modality: 'zero', anchor: 'none', somatic: 'seated', density: 'null', valuation: 'utility' } },
  { pattern: rx('journal(?:ed|ing|led)?|diary|free ?wr(?:ote|iting|ite)|morning pages|brain ?dump|reflect(?:ed|ing)?|vent(?:ed|ing)?'), word: 'journaling', spec: { modality: 'expressive', density: 'null', valuation: 'art', context: 'agency', scratchpad: 'tokenized' } },
  { pattern: rx('guitar|piano|bass|drums|violin|sang|singing|jam(?:med|ming)?|improv\\w*|sketch(?:ed|ing)?|drew|drawing|paint(?:ed|ing)?|knit\\w*|crochet\\w*|pottery'), word: 'making', spec: { modality: 'expressive', density: 'null', valuation: 'art', context: 'agency' } },
  { pattern: rx('cod(?:ed|ing)|programm(?:ed|ing)|debug\\w*|implement\\w*|refactor\\w*|wrote code|pull request|compiler|parser|kubernetes|sql|typescript|python|rust\\b|api'), word: 'coding', spec: { modality: 'execution', density: 'proofs', valuation: 'architecture' } },
  { pattern: rx('math|maths|proof|proofs|theorem|equations?|calculus|linear algebra|statistics|physics|problem set|pset'), word: 'math', spec: { density: 'proofs', valuation: 'architecture' } },
  { pattern: rx('essay|thesis|dissertation|manuscript|chapter|draft(?:ed|ing)?|wrote (?:the|a|my)|writing (?:the|a|my)|outline|article for|blog post'), word: 'writing', spec: { modality: 'execution', density: 'analysis', valuation: 'architecture' } },
  { pattern: rx('design(?:ed|ing)?|architect\\w*|whiteboard\\w*|spec(?:ced|cing)? out|planning|planned the|roadmap|prototype\\w*|built|building|made a|make a'), word: 'building', spec: { modality: 'execution', density: 'analysis', valuation: 'architecture' } },
  { pattern: rx('studied|studying|textbook|lecture|course|tutorial|learn(?:ed|ing|t)|revis(?:ed|ing)|flashcards|anki'), word: 'studying', spec: { modality: 'dense', density: 'manuals', valuation: 'architecture', novelty: 'novel' } },
  { pattern: rx('paper|papers|documentation|docs|manual|reference|rfc|spec\\b|specification|contract|legal'), word: 'dense reading', spec: { modality: 'dense', density: 'manuals', valuation: 'architecture' } },
  { pattern: rx('novel|fiction|story|stories|poetry|poems?|literature|short stories'), word: 'fiction', spec: { modality: 'reading', density: 'fiction', valuation: 'art' } },
  { pattern: rx('read|reading|book|articles?|essays?|news|newsletter|magazine|blog|wikipedia'), word: 'reading', spec: { modality: 'reading', density: 'analysis', valuation: 'utility' } },
  { pattern: rx('listen(?:ed|ing)?|heard'), word: 'listening', spec: { modality: 'auditory', density: 'fiction', valuation: 'utility' } },
  { pattern: rx('work(?:ed|ing)? on|worked|finished|shipped|task|tasks|project|ticket|spreadsheet|report|slides|deck|presentation prep'), word: 'work', spec: { modality: 'execution', density: 'analysis', valuation: 'utility' } },
];

const ANCHORS_LEX: Lexicon<AnchorKey> = [
  [rx('music|playlist|album|spotify|lo-?fi|song|songs|on repeat|headphones with music|soundtrack'), 'music', 2],
  [rx('brown noise|white noise|pink noise|rain sounds|ambient|noise app|fan noise|soundscape'), 'brown', 3],
  [rx('fidget\\w*|stress ball|spinner|clicker|worry stone|putty'), 'fidget', 3],
  [rx('walk(?:ed|ing)?|treadmill|pacing|paced|hike|hiking|stroll|strolled|dog|steps'), 'treadmill', 2],
  [rx('silence|quiet|no music|nothing on'), 'none', 2],
];

const SOMATIC_LEX: Lexicon<SomaticKey> = [
  [rx('couch|sofa|bed|lying|lay|laid|reclin\\w*|floor|hammock|pillow|blanket|horizontal|standing|stood|kitchen|outside|garden|park'), 'supine', 2],
  [rx('desk|chair|office|sat|seated|sitting|table|cafe|café|library|workstation'), 'seated', 2],
  [rx('eyes? (?:are |were |feel |felt |got |getting )?(?:hurt|hurting|ache|aching|strain|strained|burning|tired|dry|sore)|eye ?strain|blurry|squint\\w*|screen headache|headache'), 'ocular', 4],
  [rx('slouch\\w*|hunch\\w*|slump\\w*|neck (?:hurts|ache|pain|stiff)|back (?:hurts|ache|pain)|stiff neck|sore back|lumbar|shoulders? (?:tight|tense)'), 'slump', 4],
];

const CONTEXT_LEX: Lexicon<ContextKey> = [
  [rx('boss|manager|client|customer|interview|presentation|presented|exam|test|graded|evaluat\\w*|performance review|review meeting|being watched|judged|stakeholders?|urgent|asap|due (?:in|today|now)|overdue|emergency|on call'), 'scrutiny', 3],
  [rx('deadline|due tomorrow|due this week|due|sprint|ticket|assigned|had to|have to|must|required|for work|for school|homework|obligat\\w*'), 'sprint', 2],
  [rx('trying to|planned|plan to|goal|want(?:ed)? to finish|should|meant to|hoping to'), 'soft', 2],
  [rx('for fun|because i wanted|felt like|freely|my own|for myself|no pressure|relaxed|just for me|hobby'), 'agency', 2],
];

const SCRATCHPAD_LEX: Lexicon<ScratchpadKey> = [
  [rx('rabbit ?hole|got distracted|distracted|tangent|tangents|ended up|side quest|lost track|wandered|went off on|derailed|doom'), 'rabbit', 3],
  [rx('ideas kept|lots of ideas|so many ideas|branching|thinking about|kept thinking|spiral\\w*|overthinking|mind racing'), 'speculative', 3],
  [rx('wrote (?:it|them|ideas) down|noted|jotted|scratchpad|captured|todo list|to-do|parked|added to my list|note to self'), 'tokenized', 3],
  [rx('focused|stayed on|on track|single task|no tangents|locked in|in the zone|heads down'), 'single', 2],
];

const NOVELTY_LEX: Lexicon<NoveltyKey> = [
  [rx('boring|bored|same old|again|repetitive|routine|tedious|monotonous|mindless|autopilot|dull'), 'monotonous', 3],
  [rx('new|first time|never (?:done|tried)|unfamiliar|explor\\w*|different field|cross-?domain|novel idea|discover\\w*|fresh'), 'novel', 3],
  [rx('usual|familiar|as always|normal|regular'), 'routine', 2],
];

const INTENSITY_LEX: Lexicon<IntensityKey> = [
  [rx('intense|intensely|hard|pushed|crunch\\w*|flat out|grind\\w*|deep|heavy|sprint(?:ed)?|all out|full effort|nonstop'), 'heavy', 2],
  [rx('light|lightly|easy|casual|casually|lazy|lazily|gentle|gently|slow|slowly|half-?hearted|low key|low-key|chill'), 'light', 2],
];

const VALUATION_LEX: Lexicon<ValuationKey> = [
  [rx('mindless|pointless|waste of time|wasted|zombie|autopilot'), 'churn', 3],
  [rx('important|meaningful|deep work|real work|core|craft|creative|the big project'), 'architecture', 2],
  [rx('beautiful|moving|art|artistic|literary|profound'), 'art', 2],
  [rx('errand|admin|necessary|had to get done|busywork'), 'utility', 2],
];

const DENSITY_LEX: Lexicon<DensityKey> = [
  [rx('dense|technical|complex|abstract|difficult|advanced|hardcore'), 'proofs', 2],
  [rx('light reading|easy read|fluffy|simple|casual read'), 'fiction', 2],
];

function pick<K extends string>(text: string, lex: Lexicon<K>, field: DescribedField, cues: Cue[]): K | null {
  const scores = new Map<K, number>();
  let best: K | null = null;
  let bestWord = '';
  for (const [pattern, key, weight] of lex) {
    const m = text.match(pattern);
    if (!m) continue;
    const next = (scores.get(key) ?? 0) + weight;
    scores.set(key, next);
    if (best === null || next > (scores.get(best) ?? 0)) {
      best = key;
      bestWord = m[0];
    }
  }
  if (best !== null) cues.push({ field, word: bestWord, choice: best });
  return best;
}

/** Parse a duration in minutes from free text; null when nothing is stated. */
export function parseMinutes(text: string): { minutes: number; word: string } | null {
  const t = text.toLowerCase();
  let m: RegExpMatchArray | null;
  if ((m = t.match(/(\d+(?:[.,]\d+)?)\s*(?:h|hr|hrs|hour|hours)\s*(?:and\s*)?(\d+)\s*(?:m|min|mins|minutes?)\b/))) return { minutes: Math.round(parseFloat(m[1].replace(',', '.')) * 60 + parseInt(m[2], 10)), word: m[0] };
  if ((m = t.match(/(\d+)h(\d{1,2})\b/))) return { minutes: parseInt(m[1], 10) * 60 + parseInt(m[2], 10), word: m[0] };
  if ((m = t.match(/(\d+(?:[.,]\d+)?)\s*(?:-|–)?\s*(?:min|mins|minute|minutes|m)\b/))) return { minutes: Math.round(parseFloat(m[1].replace(',', '.'))), word: m[0] };
  if ((m = t.match(/(\d+(?:[.,]\d+)?)\s*(?:h|hr|hrs|hour|hours)\b/))) return { minutes: Math.round(parseFloat(m[1].replace(',', '.')) * 60), word: m[0] };
  if ((m = t.match(/\b(?:half an hour|half hour|half-hour|thirty minutes)\b/))) return { minutes: 30, word: m[0] };
  if ((m = t.match(/\b(?:an?|one)\s+hour\b/))) return { minutes: 60, word: m[0] };
  if ((m = t.match(/\b(?:quarter (?:of an )?hour|fifteen minutes)\b/))) return { minutes: 15, word: m[0] };
  if ((m = t.match(/\btwenty minutes\b/))) return { minutes: 20, word: m[0] };
  if ((m = t.match(/\bpomodoro\b/))) return { minutes: 25, word: m[0] };
  if ((m = t.match(/\b(?:two|2) hours\b/))) return { minutes: 120, word: m[0] };
  if ((m = t.match(/\ball (?:morning|afternoon|evening)\b/))) return { minutes: 90, word: m[0] };
  return null;
}

/** Describe a block from free text. `defaultMinutes` applies when no duration is stated. */
export function describeBlock(text: string, defaultMinutes = 15): DescribedBlock {
  const t = ` ${text.toLowerCase().replace(/[’']/g, "'").replace(/\s+/g, ' ').trim()} `;
  const cues: Cue[] = [];
  const unsure: DescribedField[] = [];
  const spec: BlockSpec = { ...DEFAULT_SPEC, cadence: 'm15', intensity: 'standard', scratchpad: 'single', context: 'agency', novelty: 'routine', somatic: 'seated' };

  // 1. Activity templates (first match wins the modality; later matches fill fields not yet set).
  const setBy = new Set<string>();
  let activity = false;
  for (const tpl of TEMPLATES) {
    const m = t.match(tpl.pattern);
    if (!m) continue;
    for (const [field, value] of Object.entries(tpl.spec) as [keyof BlockSpec, BlockSpec[keyof BlockSpec]][]) {
      if (setBy.has(field)) continue;
      (spec as unknown as Record<string, unknown>)[field] = value;
      setBy.add(field);
    }
    if (!activity) {
      activity = true;
      cues.push({ field: 'modality', word: m[0], choice: tpl.word });
    }
  }
  if (!activity) unsure.push('modality');

  // 2. Per-field lexicons (evidence overrides template defaults for that field).
  const anchor = pick(t, ANCHORS_LEX, 'anchor', cues);
  if (anchor) spec.anchor = anchor;
  else unsure.push('anchor');
  const somatic = pick(t, SOMATIC_LEX, 'somatic', cues);
  if (somatic) spec.somatic = somatic;
  else if (!setBy.has('somatic')) unsure.push('somatic');
  const context = pick(t, CONTEXT_LEX, 'context', cues);
  if (context) spec.context = context;
  else if (!setBy.has('context')) unsure.push('context');
  const scratch = pick(t, SCRATCHPAD_LEX, 'scratchpad', cues);
  if (scratch) spec.scratchpad = scratch;
  const novelty = pick(t, NOVELTY_LEX, 'novelty', cues);
  if (novelty) spec.novelty = novelty;
  const intensity = pick(t, INTENSITY_LEX, 'intensity', cues);
  if (intensity) spec.intensity = intensity;
  const valuation = pick(t, VALUATION_LEX, 'valuation', cues);
  if (valuation) spec.valuation = valuation;
  else if (!setBy.has('valuation')) unsure.push('valuation');
  const density = pick(t, DENSITY_LEX, 'density', cues);
  if (density) spec.density = density;

  // 3. Coherence rules.
  const modalityKey: ModalityKey = spec.modality;
  if (modalityKey === 'zero') {
    spec.density = 'null';
    spec.scratchpad = 'single';
  }
  if (modalityKey === 'expressive' || modalityKey === 'execution') spec.density = spec.density === 'null' && modalityKey === 'execution' ? 'analysis' : spec.density;
  if (modalityKey === 'expressive') spec.density = 'null';
  if (modalityKey === 'zero' && spec.somatic === 'seated' && !somatic) spec.somatic = 'supine';

  // 4. Duration.
  const parsed = parseMinutes(t);
  const minutes = Math.min(MAX_CUSTOM_MINUTES, Math.max(MIN_CUSTOM_MINUTES, parsed ? parsed.minutes : defaultMinutes));
  if (parsed) cues.push({ field: 'duration', word: parsed.word, choice: `${minutes} min` });
  else unsure.push('duration');
  const standard = CADENCES.find((c) => c.minutes === minutes);
  if (standard) {
    spec.cadence = standard.key;
    delete spec.customMinutes;
  } else {
    spec.cadence = CUSTOM_CADENCE;
    spec.customMinutes = minutes;
  }

  // 5. Confidence: activity evidence dominates; posture, anchor, pressure and value add a
  //    little. A missing duration is not uncertainty (the fixed block length applies).
  let confidence = activity ? 0.6 : 0.1;
  if (somatic || setBy.has('somatic')) confidence += 0.15;
  if (anchor || setBy.has('anchor')) confidence += 0.1;
  if (context || setBy.has('context')) confidence += 0.1;
  if (valuation || setBy.has('valuation')) confidence += 0.05;
  confidence = Math.min(1, confidence);

  return { spec, minutes, confidence, cues, unsure };
}
