import { describe, expect, it } from 'vitest';
import { describeBlock, parseMinutes, parseSpeed } from '../blockDescriber';
import { blockMinutes, resolveSpec } from '../capacityModel';

describe('parseMinutes', () => {
  it('reads common duration phrasings', () => {
    expect(parseMinutes('15 min reading')?.minutes).toBe(15);
    expect(parseMinutes('read for 20 minutes')?.minutes).toBe(20);
    expect(parseMinutes('a 45m call')?.minutes).toBe(45);
    expect(parseMinutes('coded for 2h')?.minutes).toBe(120);
    expect(parseMinutes('1.5 hours of study')?.minutes).toBe(90);
    expect(parseMinutes('1h30 walk')?.minutes).toBe(90);
    expect(parseMinutes('half an hour nap')?.minutes).toBe(30);
    expect(parseMinutes('an hour at the gym')?.minutes).toBe(60);
    expect(parseMinutes('one pomodoro of emails')?.minutes).toBe(25);
    expect(parseMinutes('read a novel')).toBeNull();
  });
});

describe('parseSpeed', () => {
  it('reads playback speeds and ignores rep counts', () => {
    expect(parseSpeed('audiobook at 1.5x')?.factor).toBe(1.5);
    expect(parseSpeed('podcast on 2× speed')?.factor).toBe(2);
    expect(parseSpeed('lecture at 1,75x')?.factor).toBe(1.75);
    expect(parseSpeed('listened on double speed')?.factor).toBe(2);
    expect(parseSpeed('at 2 times the speed')?.factor).toBe(2);
    expect(parseSpeed('slowed it down')?.factor).toBe(0.75);
    expect(parseSpeed('sped up')?.factor).toBe(1.5);
    expect(parseSpeed('normal speed')?.factor).toBe(1);
    expect(parseSpeed('3 x 10 squats')).toBeNull();
    expect(parseSpeed('2xl hoodie')).toBeNull();
    expect(parseSpeed('read a novel')).toBeNull();
  });
});

describe('describeBlock', () => {
  it('reads a novel on the couch as light fiction intake, lying down, 15 minutes', () => {
    const d = describeBlock('15 min reading a novel on the couch');
    expect(d.spec.modality).toBe('reading');
    expect(d.spec.density).toBe('fiction');
    expect(d.spec.valuation).toBe('art');
    expect(d.spec.somatic).toBe('supine');
    expect(d.spec.cadence).toBe('m15');
    expect(d.minutes).toBe(15);
    expect(d.confidence).toBeGreaterThanOrEqual(0.75);
    expect(d.cues.map((c) => c.field)).toEqual(expect.arrayContaining(['modality', 'somatic', 'duration']));
  });

  it('defaults to the given block length when no duration is stated', () => {
    expect(describeBlock('read a novel').minutes).toBe(15);
    expect(describeBlock('read a novel', 25).spec.cadence).toBe('m25');
    const odd = describeBlock('read a novel for 37 minutes');
    expect(odd.spec.cadence).toBe('custom');
    expect(blockMinutes(odd.spec)).toBe(37);
  });

  it('maps everyday activities onto the model', () => {
    const emails = describeBlock('answered emails at my desk and got distracted by twitter');
    expect(emails.spec.modality).toBe('reading');
    expect(emails.spec.valuation).toBe('churn');
    expect(emails.spec.somatic).toBe('seated');
    expect(emails.spec.scratchpad).toBe('rabbit');

    const walk = describeBlock('walked the dog while listening to a podcast');
    expect(walk.spec.modality).toBe('auditory');
    expect(walk.spec.anchor).toBe('treadmill');
    expect(walk.spec.density).toBe('analysis');

    const code = describeBlock('coded the parser with lo-fi on, deadline tomorrow, neck hurts');
    expect(code.spec.modality).toBe('execution');
    expect(code.spec.density).toBe('proofs');
    expect(code.spec.valuation).toBe('architecture');
    expect(code.spec.anchor).toBe('music');
    expect(code.spec.context).toBe('sprint');
    expect(code.spec.somatic).toBe('slump');

    const gym = describeBlock('an hour at the gym lifting weights');
    expect(gym.spec.modality).toBe('zero');
    expect(gym.spec.anchor).toBe('treadmill');
    expect(gym.minutes).toBe(60);
    expect(resolveSpec(gym.spec).somatic.kineticOrSupported).toBe(true);

    const nap = describeBlock('napped for 20 minutes with an eye mask');
    expect(nap.spec.modality).toBe('zero');
    expect(nap.spec.somatic).toBe('supine');
    expect(nap.spec.density).toBe('null');

    const meeting = describeBlock('45 min meeting with my manager about the launch');
    expect(meeting.spec.modality).toBe('auditory');
    expect(meeting.spec.context).toBe('scrutiny');
    expect(meeting.minutes).toBe(45);

    const journal = describeBlock('journaled about the week, wrote ideas down, music on');
    expect(journal.spec.modality).toBe('expressive');
    expect(journal.spec.scratchpad).toBe('tokenized');
    expect(journal.spec.anchor).toBe('music');
    expect(journal.spec.density).toBe('null');

    const study = describeBlock('studied the linear algebra textbook, eyes are tired');
    expect(study.spec.modality).toBe('dense');
    expect(study.spec.density).toBe('proofs');
    expect(study.spec.somatic).toBe('ocular');
    expect(study.spec.novelty).toBe('novel');

    const chores = describeBlock('did the dishes and laundry, boring');
    expect(chores.spec.modality).toBe('expressive');
    expect(chores.spec.valuation).toBe('utility');
    expect(chores.spec.novelty).toBe('monotonous');
    expect(chores.spec.intensity).toBe('light');

    const taxes = describeBlock('helped my mom with her taxes for an hour, had to');
    expect(taxes.spec.modality).toBe('reading');
    expect(taxes.spec.valuation).toBe('churn');
    expect(taxes.spec.context).toBe('sprint');
    expect(taxes.minutes).toBe(60);
    expect(taxes.relational).toBe(true);

    const scroll = describeBlock('scrolled tiktok in bed');
    expect(scroll.spec.valuation).toBe('churn');
    expect(scroll.spec.somatic).toBe('supine');
  });

  it('attaches a playback speed to listening and watching blocks only', () => {
    const fast = describeBlock('audiobook at 1.5x on a walk');
    expect(fast.spec.modality).toBe('auditory');
    expect(fast.spec.speed).toBe('x15');
    expect(fast.cues.find((c) => c.field === 'speed')).toEqual({ field: 'speed', word: '1.5x', choice: '1.5×' });
    expect(resolveSpec(fast.spec).u.Iaud).toBeCloseTo(0.35 * 1.5, 10);
    expect(describeBlock('watched a lecture on youtube at 2x').spec.speed).toBe('x2');
    expect(describeBlock('podcast at 1.6x').spec.speed).toBe('x15');
    // No intake, no speed — even when one is stated.
    expect(describeBlock('did 3 x 10 squats at the gym').spec.speed).toBeUndefined();
    expect(describeBlock('journaled at 2x speed').spec.speed).toBeUndefined();
    // The usual listening speed fills in for listening blocks that state none.
    expect(describeBlock('listened to a podcast', 15, 'x2').spec.speed).toBe('x2');
    expect(describeBlock('listened to a podcast at normal speed', 15, 'x2').spec.speed).toBe('x1');
    expect(describeBlock('read a novel', 15, 'x2').spec.speed).toBeUndefined();
    expect(describeBlock('listened to a podcast').spec.speed).toBeUndefined();
  });

  it('reports low confidence and an unsure activity for text it cannot place', () => {
    const d = describeBlock('the thing with Bob');
    expect(d.unsure).toContain('modality');
    expect(d.confidence).toBeLessThan(0.5);
    expect(d.spec.modality).toBe('execution');
  });

  it('keeps every produced spec loadable', () => {
    for (const text of ['15 min reading a novel on the couch', 'gym', 'meeting', 'coded', 'napped', 'x']) {
      const d = describeBlock(text);
      expect(() => resolveSpec(d.spec)).not.toThrow();
      expect(blockMinutes(d.spec)).toBeGreaterThanOrEqual(5);
    }
  });
});
