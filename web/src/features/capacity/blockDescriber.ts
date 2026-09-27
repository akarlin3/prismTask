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
  DEFAULT_SPEED,
  MAX_CUSTOM_MINUTES,
  MIN_CUSTOM_MINUTES,
  PLAYBACK_SPEEDS,
  modalityIsPlayback,
  nearestSpeed,
  type AnchorKey,
  type BlockSpec,
  type ContextKey,
  type DensityKey,
  type IntensityKey,
  type ModalityKey,
  type NoveltyKey,
  type ScratchpadKey,
  type SomaticKey,
  type SpeedKey,
  type ValuationKey,
} from './capacityModel';

export type DescribedField = 'modality' | 'anchor' | 'valuation' | 'density' | 'context' | 'scratchpad' | 'novelty' | 'somatic' | 'intensity' | 'duration' | 'speed';

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
  /** Familial or relational stewardship was mentioned (Radical Empathy pillar). */
  relational: boolean;
  relationalCue?: string;
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
  { pattern: rx('gym|workout|worked out|lifting|weights|run|ran|running|jog|jogging|cycling|bike ride|swim|swimming|hiit|crossfit|rowing|climbing|spin class'), word: 'workout', spec: { modality: 'zero', anchor: 'vigorous', somatic: 'moving', density: 'null', valuation: 'utility', intensity: 'standard' } },
  { pattern: rx('yoga|stretch(?:ing|ed)?|pilates|tai chi|mobility work|foam roll\\w*'), word: 'stretching', spec: { modality: 'zero', anchor: 'treadmill', somatic: 'moving', density: 'null', valuation: 'utility', intensity: 'light' } },
  { pattern: rx('nap|napped|dozed|slept|sleeping|lay in the dark|lie in the dark|eye mask|meditat\\w*|breathing exercise|breathwork|rested|resting|zoned out'), word: 'rest', spec: { modality: 'zero', anchor: 'none', somatic: 'supine', density: 'null', valuation: 'utility', scratchpad: 'single' } },
  { pattern: rx('cook(?:ed|ing)?|dishes|laundry|clean(?:ed|ing)?|tidied|tidying|chores|vacuum\\w*|groceries|errands|ironing|garden(?:ed|ing)?|mowed|weeding|repair(?:ed|ing)?|assembl\\w*|woodwork\\w*|diy|sewing|sewed|3d print\\w*'), word: 'chores', spec: { modality: 'manual', anchor: 'none', somatic: 'moving', density: 'null', valuation: 'utility', intensity: 'light', novelty: 'monotonous' } },
  { pattern: rx('gave a (?:talk|presentation|lecture|speech)|present(?:ed|ing) to|taught|teaching|tutor(?:ed|ing)|lectur(?:ed|ing)|led (?:the|a) (?:meeting|workshop|class|session)|ran (?:the|a) (?:meeting|workshop|session)|facilitat\\w*|pitch(?:ed|ing)|demo(?:ed|ing)? to|hosted'), word: 'presenting', spec: { modality: 'speaking', anchor: 'none', somatic: 'standing', density: 'analysis', valuation: 'utility', context: 'scrutiny' } },
  { pattern: rx('meeting|standup|stand-up|1:1|one on one|sync|call with|on a call|zoom|teams call|interview'), word: 'meeting', spec: { modality: 'social', anchor: 'none', somatic: 'seated', density: 'analysis', valuation: 'utility', context: 'sprint' } },
  { pattern: rx('emails?|inbox|slack|teams messages|admin|paperwork|forms|invoices?|expenses|triage|taxes|tax return|bills|banking|insurance|bureaucracy|scheduling|calendar'), word: 'admin', spec: { modality: 'reading', anchor: 'none', somatic: 'seated', density: 'analysis', valuation: 'churn' } },
  { pattern: rx('scroll(?:ed|ing)?|doomscroll\\w*|twitter|x\\.com|instagram|insta|tiktok|reels|shorts|reddit|facebook|feed|threads app'), word: 'scrolling', spec: { modality: 'skimming', anchor: 'none', density: 'chatter', valuation: 'churn', scratchpad: 'speculative' } },
  { pattern: rx('youtube|watched videos?|video essays?|lecture videos?|watched a lecture'), word: 'videos', spec: { modality: 'watching', anchor: 'none', density: 'analysis', valuation: 'churn' } },
  { pattern: rx('movie|film|cinema|documentary'), word: 'film', spec: { modality: 'watching', anchor: 'none', density: 'fiction', valuation: 'art', somatic: 'supine' } },
  { pattern: rx('tv|netflix|series|episodes?|show|streaming|binge\\w*'), word: 'tv', spec: { modality: 'watching', anchor: 'none', density: 'fiction', valuation: 'utility', somatic: 'supine' } },
  { pattern: rx('gam(?:ed|ing)|video ?games?|played (?:a )?game|playstation|xbox|switch|steam|minecraft|fortnite'), word: 'gaming', spec: { modality: 'interactive', anchor: 'none', density: 'analysis', valuation: 'utility', intensity: 'heavy', somatic: 'seated' } },
  { pattern: rx('audiobook|audio book'), word: 'audiobook', spec: { modality: 'auditory', density: 'fiction', valuation: 'art' } },
  { pattern: rx('podcast|radio|interview show'), word: 'podcast', spec: { modality: 'auditory', density: 'analysis', valuation: 'utility' } },
  { pattern: rx('called (?:my|a)|phoned|chat(?:ted)? with|talked (?:to|with)|catch(?:ing)? up with|hung out|dinner with|lunch with|coffee with|date night|family dinner'), word: 'conversation', spec: { modality: 'social', density: 'fiction', valuation: 'connection', context: 'agency', anchor: 'none' } },
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
  { pattern: rx('walk(?:ed|ing)?|stroll(?:ed|ing)?|hike|hiking|pacing|paced'), word: 'walk', spec: { modality: 'zero', anchor: 'treadmill', somatic: 'moving', density: 'null', valuation: 'utility' } },
  { pattern: rx('work(?:ed|ing)? on|worked|finished|shipped|task|tasks|project|ticket|spreadsheet|report|slides|deck|presentation prep'), word: 'work', spec: { modality: 'execution', density: 'analysis', valuation: 'utility' } },
];

const ANCHORS_LEX: Lexicon<AnchorKey> = [
  [rx('music|playlist|album|spotify|lo-?fi|song|songs|on repeat|headphones with music|soundtrack'), 'music', 2],
  [rx('brown noise|white noise|pink noise|rain sounds|ambient|noise app|fan noise|soundscape'), 'brown', 3],
  [rx('podcast (?:on|in the background)|people talking|chatter in the background|radio on|talk radio|with a podcast on|voices in the background'), 'voices', 4],
  [rx('tv on|tv in the background|with the tv on|youtube in the background|video in the background|stream in the background|second screen|something playing on the tv'), 'screen', 4],
  [rx('fidget\\w*|stress ball|spinner|clicker|worry stone|putty'), 'fidget', 3],
  [rx('walk(?:ed|ing)?|treadmill|pacing|paced|hike|hiking|stroll|strolled|dog|steps'), 'treadmill', 2],
  [rx('sprints|hill sprints|lifting|weights|hiit|crossfit|hard workout|ran hard|cycling hard|climbing|rowing|spin class|while running|on the bike|on a run'), 'vigorous', 3],
  [rx('silence|quiet|no music|nothing on'), 'none', 2],
];

const SOMATIC_LEX: Lexicon<SomaticKey> = [
  [rx('couch|sofa|bed|lying|lay|laid|reclin\\w*|floor|hammock|pillow|blanket|horizontal'), 'supine', 2],
  [rx('walking around|on my feet|kitchen|outside|garden|park|moving around|around the house|pacing'), 'moving', 2],
  [rx('standing desk|stood at (?:my|the) desk|at a standing desk|stand-up desk|standing still|stood still|standing up'), 'standing', 3],
  [rx('desk|chair|office|sat|seated|sitting|table|cafe|café|library|workstation'), 'seated', 2],
  [rx('eyes? (?:are |were |feel |felt |got |getting )?(?:hurt|hurting|ache|aching|strain|strained|burning|tired|dry|sore)|eye ?strain|blurry|squint\\w*|screen headache|headache'), 'ocular', 4],
  [rx('slouch\\w*|hunch\\w*|slump\\w*|neck (?:hurts|ache|pain|stiff)|back (?:hurts|ache|pain)|stiff neck|sore back|lumbar|shoulders? (?:tight|tense)'), 'slump', 4],
];

const CONTEXT_LEX: Lexicon<ContextKey> = [
  [rx('emergency|crisis|panic\\w*|outage|incident|urgent(?:ly)?|asap|on call|meltdown|due (?:in an hour|tonight|now)|overdue|fire drill|damage control'), 'crisis', 4],
  [rx('boss|manager|client|customer|interview|presentation|presented|exam|test|graded|evaluat\\w*|performance review|review meeting|being watched|judged|stakeholders?|due (?:in|today)'), 'scrutiny', 3],
  [rx('assigned|told to|had to|have to|made me|required|mandatory|forced|compulsory|obligat\\w*|for work|for school|homework|no choice'), 'imposed', 3],
  [rx('deadline|due tomorrow|due this week|due|sprint|ticket|shipping|launch|my own deadline|committed to'), 'sprint', 2],
  [rx('trying to|planned|plan to|goal|want(?:ed)? to finish|should|meant to|hoping to'), 'soft', 2],
  [rx('for fun|because i wanted|felt like|freely|my own|for myself|no pressure|relaxed|just for me|hobby'), 'agency', 2],
];

const SCRATCHPAD_LEX: Lexicon<ScratchpadKey> = [
  [rx('constantly switching|kept switching|all over the place|couldn\'t focus|could not focus|scattered|chaos|chaotic|ping-?pong|a hundred tabs|notifications kept|every few minutes'), 'chaos', 5],
  [rx('a few tangents|one tangent|briefly distracted|small detour|drifted a bit|drifted once|wandered a little|minor tangent'), 'mild', 4],
  [rx('rabbit ?hole|got distracted|distracted|tangent|tangents|ended up|side quest|lost track|wandered|went off on|derailed|doom'), 'rabbit', 3],
  [rx('ideas kept|lots of ideas|so many ideas|branching|thinking about|kept thinking|spiral\\w*|overthinking|mind racing'), 'speculative', 3],
  [rx('wrote (?:it|them|ideas) down|noted|jotted|scratchpad|captured|todo list|to-do|parked|added to my list|note to self'), 'tokenized', 3],
  [rx('focused|stayed on|on track|single task|no tangents|locked in|in the zone|heads down'), 'single', 2],
];

const NOVELTY_LEX: Lexicon<NoveltyKey> = [
  [rx('mind-?numbing|soul-?crushing|brain-?dead|numbing|deadening|for the hundredth time|on autopilot for hours'), 'deadening', 4],
  [rx('completely new|brand new|totally unfamiliar|over my head|out of my depth|bleeding edge|cutting edge|frontier|mind-?blowing|overwhelming(?:ly)? new|everything was new'), 'frontier', 4],
  [rx('boring|bored|same old|again|repetitive|routine|tedious|monotonous|mindless|autopilot|dull'), 'monotonous', 3],
  [rx('new|first time|never (?:done|tried)|unfamiliar|explor\\w*|different field|cross-?domain|novel idea|discover\\w*|fresh'), 'novel', 3],
  [rx('usual|familiar|as always|normal|regular'), 'routine', 2],
];

const INTENSITY_LEX: Lexicon<IntensityKey> = [
  [rx('all out|flat out|max effort|maxed out|as hard as i could|full send|redlin\\w*|no breaks|absolutely crushed|balls to the wall|everything i had'), 'max', 4],
  [rx('barely|half-?asleep|dozy|drowsy|minimal effort|going through the motions|coasting|phoning it in|zero effort|could barely'), 'minimal', 4],
  [rx('intense|intensely|hard|pushed|crunch\\w*|grind\\w*|deep|heavy|sprint(?:ed)?|full effort|nonstop'), 'heavy', 2],
  [rx('light|lightly|easy|casual|casually|lazy|lazily|gentle|gently|slow|slowly|half-?hearted|low key|low-key|chill'), 'light', 2],
];

const VALUATION_LEX: Lexicon<ValuationKey> = [
  [rx('numb(?:ing|ed)? out|zoned out|zombie|dissociat\\w*|killing time|time-?killing|for no reason|couldn\'t stop scrolling|staring at the wall'), 'numbing', 4],
  [rx('with (?:my|a|the) (?:friend|friends|mom|mum|dad|partner|wife|husband|kids?|son|daughter|sister|brother|family|parents)|checked in on|caught up with|hung out|helped (?:my|a)|cared for|looked after|comforted|date night|family dinner'), 'connection', 3],
  [rx('mindless|pointless|waste of time|wasted|autopilot'), 'churn', 3],
  [rx('important|meaningful|deep work|real work|core|craft|creative|the big project'), 'architecture', 2],
  [rx('beautiful|moving|art|artistic|literary|profound'), 'art', 2],
  [rx('errand|admin|necessary|had to get done|busywork'), 'utility', 2],
];

const DENSITY_LEX: Lexicon<DensityKey> = [
  [rx('research paper|cutting-?edge|frontier|unfamiliar (?:formalism|notation)|way over my head|graduate-?level|phd-?level|dense math|category theory'), 'frontier', 3],
  [rx('small talk|memes|gossip|banter|chit-?chat|light chat|shitpost\\w*|fluff'), 'chatter', 3],
  [rx('dense|technical|complex|abstract|difficult|advanced|hardcore'), 'proofs', 2],
  [rx('light reading|easy read|fluffy|simple|casual read'), 'fiction', 2],
];

/** Both eye strain and a slump reported: the combined marker. */
const OCULAR_RX = SOMATIC_LEX.find(([, key]) => key === 'ocular')![0];
const SLUMP_RX = SOMATIC_LEX.find(([, key]) => key === 'slump')![0];

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

/** Parse a playback speed ("at 1.5x", "2× speed", "double speed", "slowed down"); null when none is stated. */
export function parseSpeed(text: string): { factor: number; word: string } | null {
  const t = text.toLowerCase();
  let m: RegExpMatchArray | null;
  // "1.5x", "2×", "1.25 x speed" — but not "3 x 10" (sets × reps) or "2xl".
  if ((m = t.match(/(\d(?:[.,]\d{1,2})?)\s*(?:×|x)(?:\s*(?:speed|playback))?(?![a-z0-9])(?!\s*\d)/))) {
    const f = parseFloat(m[1].replace(',', '.'));
    if (f >= 0.5 && f <= 4) return { factor: f, word: m[0].trim() };
  }
  if ((m = t.match(/\b(?:at|on)\s+(\d(?:[.,]\d{1,2})?)\s*(?:times(?: the)?(?: normal)? speed|speed)\b/))) {
    const f = parseFloat(m[1].replace(',', '.'));
    if (f >= 0.5 && f <= 4) return { factor: f, word: m[0].trim() };
  }
  if ((m = t.match(/\b(?:double|twice the|2x) speed\b/))) return { factor: 2, word: m[0] };
  if ((m = t.match(/\btriple speed\b/))) return { factor: 3, word: m[0] };
  if ((m = t.match(/\b(?:half speed|slowed (?:it |them )?down|slower speed|slow speed|at a slower pace)\b/))) return { factor: 0.75, word: m[0] };
  if ((m = t.match(/\b(?:sped up|speeded up|faster speed|fast speed|on fast|on high speed)\b/))) return { factor: 1.5, word: m[0] };
  if ((m = t.match(/\b(?:normal speed|regular speed|1x)\b/))) return { factor: 1, word: m[0] };
  return null;
}

const RELATIONAL = rx('mom|mum|mother|dad|father|parents?|kids?|child(?:ren)?|son|daughter|baby|toddler|wife|husband|partner|spouse|girlfriend|boyfriend|family|sister|brother|sibling|grand(?:ma|pa|mother|father)|in-laws?|friend|friends|caregiv\\w*|looked after|took care of|helping (?:my|a)|helped (?:my|a)|visit(?:ed|ing)? (?:my|the)|argument|argued|fight with|comfort(?:ed|ing)?|support(?:ed|ing)? (?:my|a)');

/**
 * Describe a block from free text. `defaultMinutes` applies when no duration is stated;
 * `defaultSpeed` is the usual playback speed, applied to listening blocks that state none.
 */
export function describeBlock(text: string, defaultMinutes = 15, defaultSpeed: SpeedKey = DEFAULT_SPEED): DescribedBlock {
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
  let somatic = pick(t, SOMATIC_LEX, 'somatic', cues);
  if (somatic && OCULAR_RX.test(t) && SLUMP_RX.test(t)) {
    somatic = 'wrecked';
    const cue = cues.find((c) => c.field === 'somatic');
    if (cue) cue.choice = 'wrecked';
  }
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
  // Playback speed only means something when something is being played back.
  const speed = parseSpeed(t);
  if (modalityIsPlayback(modalityKey)) {
    if (speed) {
      spec.speed = nearestSpeed(speed.factor);
      cues.push({ field: 'speed', word: speed.word, choice: PLAYBACK_SPEEDS.find((o) => o.key === spec.speed)?.label ?? spec.speed });
    } else if (defaultSpeed !== DEFAULT_SPEED) {
      spec.speed = defaultSpeed;
    }
  } else {
    delete spec.speed;
  }
  if (modalityKey === 'expressive' || modalityKey === 'execution') spec.density = spec.density === 'null' && modalityKey === 'execution' ? 'analysis' : spec.density;
  if (modalityKey === 'expressive') spec.density = 'null';
  if (modalityKey === 'zero' && spec.somatic === 'seated' && !somatic) spec.somatic = 'supine';
  if (modalityKey === 'manual' && !setBy.has('density') && !density) spec.density = 'null';

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

  const rel = t.match(RELATIONAL);
  return { spec, minutes, confidence, cues, unsure, relational: !!rel, relationalCue: rel ? rel[0] : undefined };
}
