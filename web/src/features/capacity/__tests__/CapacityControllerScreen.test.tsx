import { beforeEach, describe, expect, it, vi } from 'vitest';
import { act, fireEvent, render, screen, within } from '@testing-library/react';
import { CapacityControllerScreen } from '../CapacityControllerScreen';
import { STORAGE_KEY, decodePersisted } from '../capacityModel';

function seed(extra: Record<string, unknown> = {}) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify({ version: 1, uiMode: 'advanced', ...extra }));
}

describe('CapacityControllerScreen', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('opens in the simple flow by default: status, one recommendation, and nothing assumed for your block', () => {
    render(<CapacityControllerScreen />);
    const status = screen.getByRole('region', { name: /^Status$/i });
    expect(within(status).getByRole('heading', { name: /Good to build/i })).toBeInTheDocument();
    expect(within(status).getByLabelText('Energy')).toBeInTheDocument();
    expect(within(status).queryByText(/I\* /)).not.toBeInTheDocument();
    // One recommendation, no list of alternatives, no standings to compare against each other.
    const rec = screen.getByRole('region', { name: /Recommended block/i });
    expect(within(rec).getByRole('heading', { name: /Recommended now/i })).toBeInTheDocument();
    expect(within(rec).getByRole('button', { name: /^Use this$/ })).toBeInTheDocument();
    expect(within(rec).queryAllByLabelText(/^Standing /)).toHaveLength(0);
    expect(screen.queryByRole('button', { name: /Show all/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('region', { name: /Next block/i })).not.toBeInTheDocument();
    // Your block starts empty: nothing is assumed until you describe something or use the recommendation.
    const mine = screen.getByRole('region', { name: /^Your block$/i });
    expect(within(mine).getByRole('heading', { name: /^Your block$/i })).toBeInTheDocument();
    expect(mine).toHaveTextContent(/Nothing is assumed until you do/);
    expect(within(mine).queryByLabelText(/Before you log it/i)).not.toBeInTheDocument();
    expect(within(mine).queryByRole('button', { name: /^Log Block$/i })).not.toBeInTheDocument();
    expect(within(mine).queryByRole('button', { name: /^Tangents:/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('region', { name: /State-space HUD/i })).not.toBeInTheDocument();

    // Use the recommendation: its details become adjustable chips and it reads as the recommended block.
    fireEvent.click(within(rec).getByRole('button', { name: /^Use this$/ }));
    expect(decodePersisted(localStorage.getItem(STORAGE_KEY)).suggested).toBe(true);
    const details = within(mine).getByLabelText(/Block details/i);
    expect(within(details).getByRole('button', { name: /^Tangents:/i })).toHaveAttribute('aria-expanded', 'false');
    const preview = within(mine).getByLabelText(/Before you log it/i);
    // Judgement-free: no standing pill without Show math, just the comparison in words.
    expect(within(preview).queryByLabelText(/^Standing /)).not.toBeInTheDocument();
    expect(preview).toHaveTextContent(/This is the recommended block/);
    expect(preview).toHaveTextContent(/Before you log it · 15 min/);
    expect(within(preview).getByLabelText(/^Energy \d\.\d\d now, \d\.\d\d after the block$/)).toBeInTheDocument();
    expect(preview).toHaveTextContent(/Then:/);
    // Adjust one field in place: the chip records it and the block is compared afresh.
    fireEvent.click(within(details).getByRole('button', { name: /^Tangents:/i }));
    expect(within(details).getByRole('group', { name: /Adjust Tangents/i })).toBeInTheDocument();
    fireEvent.click(within(details).getByRole('radio', { name: /Constant switching, scattered/i }));
    expect(within(details).getByRole('button', { name: /^Tangents: Constant switching, scattered ← you/i })).toBeInTheDocument();
    expect(decodePersisted(localStorage.getItem(STORAGE_KEY)).spec.scratchpad).toBe('chaos');
    expect(within(mine).getByLabelText(/Before you log it/i)).toHaveTextContent(/recommended block \(/);
    expect(within(mine).getByLabelText(/Before you log it/i)).not.toHaveTextContent(/behind|better|worse/i);
    fireEvent.click(within(details).getByRole('button', { name: /^Done$/i }));
    expect(within(mine).queryByRole('radiogroup', { name: /What you did/i })).not.toBeInTheDocument();

    // Log Block integrates it and the card returns to nothing assumed.
    fireEvent.click(within(mine).getByRole('button', { name: /^Log Block$/i }));
    expect(screen.getByText(/Block k1 · \d+\.\d h awake/i)).toBeInTheDocument();
    expect(decodePersisted(localStorage.getItem(STORAGE_KEY)).history).toHaveLength(1);
    expect(decodePersisted(localStorage.getItem(STORAGE_KEY)).suggested).toBe(false);
    expect(within(mine).queryByLabelText(/Before you log it/i)).not.toBeInTheDocument();

    // Describing a churn block is compared, not judged: no standing pill, no warning (its guardrails are
    // efficiency notes), no pillar scorecard, and no "counts as churn" line.
    fireEvent.change(within(mine).getByLabelText(/Describe it in your own words/i), { target: { value: 'scrolled instagram' } });
    fireEvent.click(within(mine).getByRole('button', { name: /Read it/i }));
    const churnPreview = within(mine).getByLabelText(/Before you log it/i);
    expect(within(churnPreview).queryByLabelText(/^Standing /)).not.toBeInTheDocument();
    expect(churnPreview).toHaveTextContent(/recommended block \(/);
    expect(within(churnPreview).queryByLabelText(/Guardrail warnings/i)).not.toBeInTheDocument();
    expect(within(churnPreview).queryByLabelText(/Guardrail notes/i)).not.toBeInTheDocument();
    expect(mine).not.toHaveTextContent(/behind|Not now|churn|good idea/i);
    expect(within(mine).queryByLabelText(/Seven pillars/i)).not.toBeInTheDocument();
    fireEvent.click(within(mine).getByRole('button', { name: /^Clear$/i }));
    expect(within(mine).queryByLabelText(/Understood as/i)).not.toBeInTheDocument();
    expect(mine).toHaveTextContent(/Nothing is assumed until you do/);

    // Show math reveals symbols and the diagnostics line.
    fireEvent.click(screen.getByRole('button', { name: /Show math/i }));
    expect(within(status).getAllByText(/I\* /).length).toBeGreaterThan(0);
    expect(decodePersisted(localStorage.getItem(STORAGE_KEY)).showMath).toBe(true);
    // With Show math on, the same churn block shows its standing, the pillar row and its efficiency notes.
    fireEvent.change(within(mine).getByLabelText(/Describe it in your own words/i), { target: { value: 'scrolled instagram' } });
    fireEvent.click(within(mine).getByRole('button', { name: /Read it/i }));
    const mathPreview = within(mine).getByLabelText(/Before you log it/i);
    expect(within(mathPreview).getByLabelText(/^Standing (Partial match|Different path)$/)).toBeInTheDocument();
    expect(within(mathPreview).getByLabelText(/Guardrail notes/i)).toHaveTextContent(/Depleting intake|Hard boundary/);
    expect(within(mathPreview).queryByLabelText(/Guardrail warnings/i)).not.toBeInTheDocument();
    expect(within(mine).getByLabelText(/Seven pillars/i)).toBeInTheDocument();
    fireEvent.click(within(mine).getByRole('button', { name: /^Clear$/i }));

    // Advanced reveals the instrument panel and persists.
    fireEvent.click(screen.getByRole('button', { name: /^advanced$/i }));
    expect(screen.getByRole('region', { name: /State-space HUD/i })).toBeInTheDocument();
    expect(decodePersisted(localStorage.getItem(STORAGE_KEY)).uiMode).toBe('advanced');
  });

  it('keeps a log of past blocks that can be edited and deleted, recomputing what follows', () => {
    render(<CapacityControllerScreen />);
    const rec = screen.getByRole('region', { name: /Recommended block/i });
    const mine = screen.getByRole('region', { name: /^Your block$/i });
    const log = screen.getByRole('region', { name: /Block log/i });
    expect(log).toHaveTextContent(/Nothing logged yet/);
    // Two blocks: the recommendation, then a described one.
    fireEvent.click(within(rec).getByRole('button', { name: /^Use this$/ }));
    fireEvent.click(within(mine).getByRole('button', { name: /^Log Block$/i }));
    fireEvent.change(within(mine).getByLabelText(/Describe it in your own words/i), { target: { value: 'read a novel on the couch' } });
    fireEvent.click(within(mine).getByRole('button', { name: /Read it/i }));
    fireEvent.click(within(mine).getByRole('button', { name: /^Log Block$/i }));
    let rows = within(within(log).getByRole('list', { name: /Logged blocks/i })).getAllByRole('listitem');
    expect(rows).toHaveLength(2);
    expect(rows[0]).toHaveTextContent(/k2/);
    expect(rows[0]).toHaveTextContent(/read a novel on the couch/);
    expect(rows[1]).toHaveTextContent(/k1/);
    const before = decodePersisted(localStorage.getItem(STORAGE_KEY));
    expect(before.history).toHaveLength(2);
    // Edit the first block: a longer length and a note; the second block is recomputed from the new state.
    fireEvent.click(within(rows[1]).getByRole('button', { name: /^Edit$/i }));
    const editor = within(log).getByRole('group', { name: /Edit block 1/i });
    fireEvent.click(within(editor).getByRole('radio', { name: /^45m/i }));
    fireEvent.change(within(editor).getByLabelText(/Block note/i), { target: { value: 'first block, longer' } });
    fireEvent.click(within(editor).getByRole('button', { name: /Save changes/i }));
    const after = decodePersisted(localStorage.getItem(STORAGE_KEY));
    expect(after.history[0].dtMinutes).toBe(45);
    expect(after.history[0].note).toBe('first block, longer');
    expect(after.history[0].xBefore).toEqual(before.history[0].xBefore);
    expect(after.history[0].xAfter).not.toEqual(before.history[0].xAfter);
    expect(after.history[1].xBefore).toEqual(after.history[0].xAfter);
    expect(after.x).toEqual(after.history[1].xAfter);
    expect(after.blockIndex).toBe(2);
    expect(within(log).queryByRole('group', { name: /Edit block/i })).not.toBeInTheDocument();
    expect(log).toHaveTextContent(/first block, longer/);
    // Delete the first block: the remaining one is renumbered and recomputed from the origin.
    rows = within(within(log).getByRole('list', { name: /Logged blocks/i })).getAllByRole('listitem');
    fireEvent.click(within(rows[1]).getByRole('button', { name: /^Delete block 1$/i }));
    const gone = decodePersisted(localStorage.getItem(STORAGE_KEY));
    expect(gone.history).toHaveLength(1);
    expect(gone.history[0].k).toBe(1);
    expect(gone.history[0].note).toBe('read a novel on the couch');
    expect(gone.history[0].xBefore).toEqual(before.history[0].xBefore);
    expect(gone.x).toEqual(gone.history[0].xAfter);
    expect(gone.blockIndex).toBe(1);
    expect(within(within(log).getByRole('list', { name: /Logged blocks/i })).getAllByRole('listitem')).toHaveLength(1);
    expect(screen.getByText(/Deleted block k1/)).toBeInTheDocument();
  });

  it('describes a block in plain words, shows what it understood, and logs it with the note', () => {
    render(<CapacityControllerScreen />);
    const log = screen.getByRole('region', { name: /^Your block$/i });
    fireEvent.change(within(log).getByLabelText(/Describe it in your own words/i), { target: { value: 'read a novel on the couch' } });
    fireEvent.click(within(log).getByRole('button', { name: /Read it/i }));
    const understood = within(log).getByLabelText(/Understood as/i);
    expect(understood).toHaveTextContent(/Activity: Reading/);
    expect(understood).toHaveTextContent(/Body: Lying down/);
    expect(understood).toHaveTextContent(/Length: 15 min/);
    expect(understood).toHaveTextContent(/Fairly sure/);
    // Adjust one field in place: the chip records that you changed it.
    fireEvent.click(within(understood).getByRole('button', { name: /^Body:/i }));
    fireEvent.click(within(understood).getByRole('radio', { name: /^Sitting/i }));
    expect(within(understood).getByRole('button', { name: /^Body: Sitting ← you/i })).toBeInTheDocument();
    fireEvent.click(within(log).getByRole('button', { name: /^Log Block$/i }));
    const persisted = decodePersisted(localStorage.getItem(STORAGE_KEY));
    expect(persisted.history).toHaveLength(1);
    expect(persisted.history[0].note).toBe('read a novel on the couch');
    expect(persisted.history[0].spec?.modality).toBe('reading');
    expect(persisted.history[0].spec?.somatic).toBe('seated');
    expect(persisted.history[0].dtMinutes).toBe(15);
  });

  it('reads a playback speed, offers it as a chip for listening blocks, and remembers the usual speed', () => {
    render(<CapacityControllerScreen />);
    const log = screen.getByRole('region', { name: /^Your block$/i });
    // No speed chip on a block where nothing is played back.
    fireEvent.change(within(log).getByLabelText(/Describe it in your own words/i), { target: { value: 'called my mom at 2x' } });
    fireEvent.click(within(log).getByRole('button', { name: /Read it/i }));
    expect(within(log).getByLabelText(/Understood as/i)).toHaveTextContent(/Activity: Talking with people/);
    expect(within(log).queryByRole('button', { name: /^Speed:/i })).not.toBeInTheDocument();
    fireEvent.change(within(log).getByLabelText(/Describe it in your own words/i), { target: { value: 'audiobook at 2x on a walk' } });
    fireEvent.click(within(log).getByRole('button', { name: /Read it/i }));
    const understood = within(log).getByLabelText(/Understood as/i);
    expect(within(understood).getByRole('button', { name: /^Speed: Double speed \(2×\) ← “2x”$/i })).toBeInTheDocument();
    expect(decodePersisted(localStorage.getItem(STORAGE_KEY)).spec.speed).toBe('x2');
    const fast = within(log).getByLabelText(/Before you log it/i).textContent;
    // Adjust the speed in place: the chip records it, the preview changes.
    fireEvent.click(within(understood).getByRole('button', { name: /^Speed:/i }));
    fireEvent.click(within(understood).getByRole('radio', { name: /^Slower \(0\.75×\)$/i }));
    expect(within(understood).getByRole('button', { name: /^Speed: Slower \(0\.75×\) ← you$/i })).toBeInTheDocument();
    expect(decodePersisted(localStorage.getItem(STORAGE_KEY)).spec.speed).toBe('x075');
    expect(within(log).getByLabelText(/Before you log it/i).textContent).not.toBe(fast);
    // The usual listening speed lives under "How this works" and is persisted.
    fireEvent.change(screen.getByLabelText(/Usual listening speed/i), { target: { value: 'x15' } });
    expect(decodePersisted(localStorage.getItem(STORAGE_KEY)).listeningSpeed).toBe('x15');
    fireEvent.change(within(log).getByLabelText(/Describe it in your own words/i), { target: { value: 'listened to a podcast' } });
    fireEvent.click(within(log).getByRole('button', { name: /Read it/i }));
    expect(decodePersisted(localStorage.getItem(STORAGE_KEY)).spec.speed).toBe('x15');
    expect(within(log).getByLabelText(/Understood as/i)).toHaveTextContent(/Speed: 1\.5× \(assumed\)/);
  });

  it('reads music as its own activity, without a playback speed', () => {
    render(<CapacityControllerScreen />);
    const log = screen.getByRole('region', { name: /^Your block$/i });
    fireEvent.change(within(log).getByLabelText(/Describe it in your own words/i), { target: { value: 'listened to an album on the couch at 1.5x' } });
    fireEvent.click(within(log).getByRole('button', { name: /Read it/i }));
    const understood = within(log).getByLabelText(/Understood as/i);
    expect(understood).toHaveTextContent(/Activity: Listening to music/);
    expect(understood).not.toHaveTextContent(/Background: Familiar music/);
    expect(within(log).queryByRole('button', { name: /^Speed:/i })).not.toBeInTheDocument();
    expect(decodePersisted(localStorage.getItem(STORAGE_KEY)).spec).toMatchObject({ modality: 'music', anchor: 'none' });
  });

  it('runs the block through the seven pillars', () => {
    render(<CapacityControllerScreen />);
    const log = screen.getByRole('region', { name: /^Your block$/i });
    expect(within(log).queryByLabelText(/Seven pillars/i)).not.toBeInTheDocument();
    fireEvent.change(within(log).getByLabelText(/Describe it in your own words/i), { target: { value: 'journaled about the week' } });
    fireEvent.click(within(log).getByRole('button', { name: /Read it/i }));
    // The pillar row is a scorecard, so the judgement-free simple flow keeps it behind Show math.
    expect(within(log).queryByLabelText(/Seven pillars/i)).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /Show math/i }));
    const pillars = within(log).getByLabelText(/Seven pillars/i);
    expect(within(pillars).getAllByRole('button')).toHaveLength(7);
    expect(within(pillars).getByRole('button', { name: /Curiosity: pass/i })).toBeInTheDocument();
    fireEvent.change(within(log).getByLabelText(/Describe it in your own words/i), { target: { value: 'scrolled twitter, my mom kept calling and I had to answer' } });
    fireEvent.click(within(log).getByRole('button', { name: /Read it/i }));
    expect(within(pillars).getByRole('button', { name: /Curiosity: block/i })).toBeInTheDocument();
    expect(within(pillars).getByRole('button', { name: /Radical Empathy: flag/i })).toBeInTheDocument();
    expect(pillars).toHaveTextContent(/blocking/);
    fireEvent.click(within(pillars).getByRole('button', { name: /Radical Empathy: flag/i }));
    expect(pillars).toHaveTextContent(/chosen, bounded act/);
  });

  it('opens the full form when a description cannot be placed', () => {
    render(<CapacityControllerScreen />);
    const log = screen.getByRole('region', { name: /^Your block$/i });
    fireEvent.change(within(log).getByLabelText(/Describe it in your own words/i), { target: { value: 'the thing with Bob' } });
    fireEvent.click(within(log).getByRole('button', { name: /Read it/i }));
    expect(within(log).getByLabelText(/Understood as/i)).toHaveTextContent(/Could not tell/);
    // The activity picker opens by itself with the guess pre-selected.
    expect(within(log).getByRole('group', { name: /Adjust Activity/i })).toBeInTheDocument();
    expect(within(log).getByRole('radio', { name: /Focused work \(making things\)/i })).toHaveAttribute('aria-checked', 'true');
  });

  it('supports custom durations and user presets in the simple flow', () => {
    render(<CapacityControllerScreen />);
    fireEvent.click(within(screen.getByRole('region', { name: /Recommended block/i })).getByRole('button', { name: /^Use this$/ }));
    const log = screen.getByRole('region', { name: /^Your block$/i });
    fireEvent.click(within(log).getByRole('button', { name: /^Length:/i }));
    fireEvent.change(within(log).getByLabelText(/Custom duration in minutes/i), { target: { value: '37' } });
    expect(within(log).getByLabelText(/Before you log it/i)).toHaveTextContent(/Before you log it · 37 min/);
    expect(decodePersisted(localStorage.getItem(STORAGE_KEY)).spec.customMinutes).toBe(37);
    fireEvent.click(within(log).getByRole('button', { name: /^Done$/i }));

    fireEvent.click(within(log).getByRole('button', { name: /Save this block as a preset/i }));
    fireEvent.change(within(log).getByLabelText(/Preset name/i), { target: { value: 'Bass practice' } });
    fireEvent.submit(within(log).getByLabelText(/Preset name/i).closest('form')!);
    expect(decodePersisted(localStorage.getItem(STORAGE_KEY)).presets.map((p) => p.name)).toEqual(['Bass practice']);
    // Presets compete for the recommendation but are never listed as extra defaults; Advanced shows and deletes them.
    expect(screen.queryByRole('button', { name: /Show all/i })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /^advanced$/i }));
    const catalog = screen.getByRole('region', { name: /Block catalog/i });
    expect(within(catalog).getByText('Bass practice')).toBeInTheDocument();
    fireEvent.click(within(catalog).getByRole('button', { name: /Delete preset Bass practice/i }));
    expect(decodePersisted(localStorage.getItem(STORAGE_KEY)).presets).toHaveLength(0);
  });

  it('renders the HUD, status strip, audit form and prescription engine', () => {
    seed();
    render(<CapacityControllerScreen />);
    expect(screen.getByRole('heading', { name: /6D Capacity Controller/i })).toBeInTheDocument();
    expect(screen.getByRole('region', { name: /State-space HUD/i })).toBeInTheDocument();
    expect(screen.getByRole('region', { name: /Diagnostic status/i })).toBeInTheDocument();
    expect(screen.getByRole('region', { name: /Telemetry ingestion audit/i })).toBeInTheDocument();
    expect(screen.getByRole('region', { name: /Dynamic prescription engine/i })).toBeInTheDocument();
    expect(screen.getByRole('radiogroup', { name: /Primary Modality Vector/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Integrate Discrete Flux/i })).toBeInTheDocument();
  });

  it('integrates a block, advances the counter, logs it, reports the transition and persists to localStorage', () => {
    seed();
    render(<CapacityControllerScreen />);
    const form = screen.getByRole('region', { name: /Telemetry ingestion audit/i });
    expect(within(form).getByRole('radiogroup', { name: /Novelty/i })).toBeInTheDocument();
    expect(within(form).getByRole('radio', { name: /Unbuffered Speculative Intake/i })).toBeInTheDocument();
    fireEvent.click(within(form).getByRole('radio', { name: /Nothing \/ Rest/i }));
    fireEvent.click(within(form).getByRole('radio', { name: /^25m/i }));
    fireEvent.click(screen.getByRole('button', { name: /Integrate Discrete Flux/i }));

    expect(screen.getByText(/block k1 · t_awake 0\.4 h/i)).toBeInTheDocument();
    expect(screen.getByText(/Block Log · 1 entries/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/State vector update/i)).toHaveTextContent(/block k1 · Δt 25 m/i);
    expect(screen.getByRole('button', { name: /Copy Report/i })).toBeInTheDocument();
    // Exactly one trailing operational prompt.
    expect(screen.getAllByLabelText(/Operational prompt/i)).toHaveLength(1);
    expect(screen.getByLabelText(/Operational prompt/i)).toHaveTextContent(/Lock in «|Terminate the session/);

    const persisted = decodePersisted(localStorage.getItem(STORAGE_KEY));
    expect(persisted.blockIndex).toBe(1);
    expect(persisted.history).toHaveLength(1);
    expect(persisted.history[0].spec?.modality).toBe('zero');
    expect(persisted.hoursAwake).toBeCloseTo(25 / 60, 6);
  });

  it('restores persisted state on mount and supports undo', () => {
    seed({ x: { E: 0.31, B: 0.72, Fvis: 0.2, Fbody: 0.2, A: 0.5, V: 0.8 }, hoursAwake: 5, blockIndex: 3, history: [], spec: { modality: 'auditory' } });
    render(<CapacityControllerScreen />);
    // B ≥ 0.60 ∧ E < 0.40 routes to Quadrant I-A.
    expect(screen.getByText('Zero-Input Flush')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Undo/i })).toBeDisabled();

    fireEvent.click(screen.getByRole('button', { name: /Integrate Discrete Flux/i }));
    expect(screen.getByRole('button', { name: /Undo/i })).toBeEnabled();
    fireEvent.click(screen.getByRole('button', { name: /Undo/i }));
    expect(screen.getByText(/block k3 · t_awake 5\.0 h/i)).toBeInTheDocument();
  });

  it('calibrates the state vector through the override modal', () => {
    seed();
    render(<CapacityControllerScreen />);
    fireEvent.click(screen.getByRole('button', { name: /Override/i }));
    const dialog = screen.getByRole('dialog', { name: /Manual Override/i });
    fireEvent.change(within(dialog).getByLabelText('Cognitive Backlog value'), { target: { value: '0.9' } });
    fireEvent.change(within(dialog).getByLabelText('Systemic Energy value'), { target: { value: '0.2' } });
    // Objective Impartiality: the report is blended at the Kalman gain (0.6 by default)...
    expect(within(dialog).getByLabelText(/Blended calibration/i)).toHaveTextContent(/0\.25 → 0\.64/);
    // ...unless trust is set to 1, which copies the report.
    fireEvent.change(within(dialog).getByLabelText('Trust in self-report value'), { target: { value: '1' } });
    expect(within(dialog).getByLabelText(/Blended calibration/i)).toHaveTextContent(/0\.25 → 0\.90/);
    fireEvent.click(within(dialog).getByRole('button', { name: /Apply Calibration/i }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.getByText('Zero-Input Flush')).toBeInTheDocument();
    expect(decodePersisted(localStorage.getItem(STORAGE_KEY)).x.B).toBeCloseTo(0.9, 6);
  });

  it('surfaces guardrail violations for the armed block', () => {
    seed({ x: { E: 0.7, B: 0.7, Fvis: 0.65, Fbody: 0.2, A: 0.5, V: 0.8 }, hoursAwake: 3, blockIndex: 2, history: [], spec: { modality: 'reading', cadence: 'm25' } });
    render(<CapacityControllerScreen />);
    const form = screen.getByRole('region', { name: /Telemetry ingestion audit/i });
    expect(within(form).getByText(/Optical cutoff: F_vis = 0\.65 ≥ 0\.60 forces I_vis = 0/i)).toBeInTheDocument();
    expect(within(form).getByText(/Backlog saturated \(B = 0\.70, lock at 0\.65\)/i)).toBeInTheDocument();
    // Routed to I-B: backlog jammed with reserves available.
    expect(screen.getByText('Expressive Digestion')).toBeInTheDocument();
  });

  it('lists every catalog block compared with the best and arms one into the audit form', () => {
    seed({ x: { E: 0.85, B: 0.15, Fvis: 0.1, Fbody: 0.1, A: 0.5, V: 0.95 }, hoursAwake: 3, blockIndex: 0, history: [], spec: { modality: 'zero' } });
    render(<CapacityControllerScreen />);
    const catalog = screen.getByRole('region', { name: /Block catalog/i });
    const standings = within(catalog).getAllByLabelText(/^Standing /);
    expect(standings.length).toBeGreaterThanOrEqual(15);
    // Quadrant IV: the top card is an execution block; the others are compared with it by name.
    expect(within(catalog).getByText('Deep work with music on repeat')).toBeInTheDocument();
    expect(standings[0]).toHaveTextContent('Top match');
    expect(within(catalog).getAllByText(/^Compared with the recommended block \(Deep work at a walking desk\): /).length).toBeGreaterThan(3);
    expect(catalog).not.toHaveTextContent(/behind|Not now/);
    // In a healthy state no guardrail is severe: the catalog carries notes (Advanced shows the math), not warnings.
    expect(within(catalog).queryAllByLabelText(/Guardrail warnings/i)).toHaveLength(0);
    expect(within(catalog).getAllByLabelText(/Guardrail notes/i).length).toBeGreaterThan(0);
    expect(catalog).toHaveTextContent(/Depleting intake|Not indicated/);
    const armButtons = within(catalog).getAllByRole('button', { name: /^Arm$/ });
    fireEvent.click(armButtons[0]);
    const form = screen.getByRole('region', { name: /Telemetry ingestion audit/i });
    expect(within(form).getByRole('radio', { name: /Deep Execution/i })).toHaveAttribute('aria-checked', 'true');
  });

  it('flags the burnout singularity late in the circadian phase', () => {
    seed({ x: { E: 0.3, B: 0.5, Fvis: 0.2, Fbody: 0.2, A: 0.5, V: 0.8 }, hoursAwake: 17, blockIndex: 9, history: [] });
    render(<CapacityControllerScreen />);
    expect(screen.getByRole('alert')).toHaveTextContent(/Burnout Singularity/i);
    // Prescription card, trailing prompt and catalog entry all offer the reset.
    expect(screen.getAllByRole('button', { name: /Log Sleep Reset/i }).length).toBeGreaterThanOrEqual(2);
    const catalog = screen.getByRole('region', { name: /Block catalog/i });
    expect(within(catalog).getAllByLabelText(/^Standing /)[0]).toHaveTextContent('Top match');
    expect(within(catalog).getByText('Go to sleep')).toBeInTheDocument();
    // Intake in a singularity is a severe guardrail, so here the catalog does carry warnings.
    expect(within(catalog).getAllByLabelText(/Guardrail warnings/i).length).toBeGreaterThan(0);
    expect(catalog).toHaveTextContent(/Input prohibited — Taking things in would drain you right now/);
  });

  it('warns in the simple flow only when a block would cross a hard limit', () => {
    seed({ uiMode: 'simple', x: { E: 0.7, B: 0.2, Fvis: 0.65, Fbody: 0.2, A: 0.5, V: 0.9 }, hoursAwake: 3, blockIndex: 1, history: [] });
    render(<CapacityControllerScreen />);
    const mine = screen.getByRole('region', { name: /^Your block$/i });
    // A page in front of tired eyes: the optical cutoff is severe, so it is the one warning shown.
    fireEvent.change(within(mine).getByLabelText(/Describe it in your own words/i), { target: { value: 'read a novel on the couch' } });
    fireEvent.click(within(mine).getByRole('button', { name: /Read it/i }));
    const preview = within(mine).getByLabelText(/Before you log it/i);
    expect(within(preview).getByLabelText(/Guardrail warnings/i)).toHaveTextContent(/Optical cutoff — Your eyes need a break/);
    expect(within(preview).queryByLabelText(/Guardrail notes/i)).not.toBeInTheDocument();
    expect(within(preview).queryByLabelText(/^Standing /)).not.toBeInTheDocument();
    // An audiobook with the eyes closed trips nothing severe: no warning at all.
    fireEvent.change(within(mine).getByLabelText(/Describe it in your own words/i), { target: { value: 'listened to an audiobook lying down' } });
    fireEvent.click(within(mine).getByRole('button', { name: /Read it/i }));
    const quiet = within(mine).getByLabelText(/Before you log it/i);
    expect(within(quiet).queryByLabelText(/Guardrail warnings/i)).not.toBeInTheDocument();
    expect(quiet).not.toHaveTextContent(/Optical cutoff|Hard boundary/);
  });

  it('starting a block runs a countdown for its length and logs it when the time is up', () => {
    vi.useFakeTimers();
    try {
      vi.setSystemTime(new Date('2026-09-28T14:00:00'));
      render(<CapacityControllerScreen />);
      const rec = screen.getByRole('region', { name: /Recommended block/i });
      const mine = screen.getByRole('region', { name: /^Your block$/i });
      fireEvent.click(within(rec).getByRole('button', { name: /^Start$/ }));
      const running = within(mine).getByLabelText(/Block in progress/i);
      expect(within(running).getByRole('timer', { name: /Time left/i })).toHaveTextContent(/^15:00$/);
      expect(running).toHaveTextContent(/15 min · ends/);
      expect(within(running).getByRole('progressbar', { name: /Block progress/i })).toHaveAttribute('aria-valuenow', '0');
      expect(decodePersisted(localStorage.getItem(STORAGE_KEY)).timer?.minutes).toBe(15);
      // While it runs there is nothing else to log or start.
      expect(within(mine).queryByRole('button', { name: /^Log Block$/i })).not.toBeInTheDocument();
      expect(within(mine).queryByLabelText(/Before you log it/i)).not.toBeInTheDocument();
      expect(within(rec).getByRole('button', { name: /^Start$/ })).toBeDisabled();
      expect(within(running).getByRole('button', { name: /Finish early/i })).toBeDisabled();
      act(() => {
        vi.advanceTimersByTime(60_000);
      });
      expect(within(mine).getByRole('timer', { name: /Time left/i })).toHaveTextContent(/^14:00$/);
      act(() => {
        vi.advanceTimersByTime(5 * 60_000);
      });
      expect(within(mine).getByRole('button', { name: /Finish early/i })).toBeEnabled();
      expect(within(mine).getByRole('progressbar', { name: /Block progress/i })).toHaveAttribute('aria-valuenow', '40');
      // The end of the countdown logs the block at its full length and empties the card.
      act(() => {
        vi.advanceTimersByTime(9 * 60_000);
      });
      const saved = decodePersisted(localStorage.getItem(STORAGE_KEY));
      expect(saved.timer).toBeNull();
      expect(saved.history).toHaveLength(1);
      expect(saved.history[0].dtMinutes).toBe(15);
      expect(saved.suggested).toBe(false);
      expect(within(mine).queryByLabelText(/Block in progress/i)).not.toBeInTheDocument();
      expect(mine).toHaveTextContent(/Nothing is assumed until you do/);
      expect(screen.getByText(/Block k1 logged/i)).toBeInTheDocument();
      expect(within(rec).getByRole('button', { name: /^Start$/ })).toBeEnabled();
    } finally {
      vi.useRealTimers();
    }
  });

  it('keeps a running block across a reload, finishes it early with the minutes done, or cancels it', () => {
    vi.useFakeTimers();
    try {
      vi.setSystemTime(new Date('2026-09-28T14:10:00'));
      seed({ uiMode: 'simple', timer: { startedAt: Date.now() - 10 * 60_000, minutes: 15, spec: { modality: 'reading', cadence: 'm15' }, note: 'read a novel on the couch' } });
      render(<CapacityControllerScreen />);
      const mine = screen.getByRole('region', { name: /^Your block$/i });
      const running = within(mine).getByLabelText(/Block in progress/i);
      expect(running).toHaveTextContent(/read a novel on the couch/);
      expect(within(running).getByRole('timer', { name: /Time left/i })).toHaveTextContent(/^5:00$/);
      expect(running).toHaveTextContent(/Finish early logs the 10 minutes done so far/);
      fireEvent.click(within(running).getByRole('button', { name: /Finish early/i }));
      let saved = decodePersisted(localStorage.getItem(STORAGE_KEY));
      expect(saved.timer).toBeNull();
      expect(saved.history).toHaveLength(1);
      expect(saved.history[0].dtMinutes).toBe(10);
      expect(saved.history[0].note).toBe('read a novel on the couch');
      // A described block starts from its own words; Cancel logs nothing and keeps the block for later.
      fireEvent.change(within(mine).getByLabelText(/Describe it in your own words/i), { target: { value: 'journaled about the week' } });
      fireEvent.click(within(mine).getByRole('button', { name: /Read it/i }));
      fireEvent.click(within(mine).getByRole('button', { name: /^Start block/i }));
      expect(within(mine).getByLabelText(/Block in progress/i)).toHaveTextContent(/journaled about the week/);
      expect(decodePersisted(localStorage.getItem(STORAGE_KEY)).timer?.note).toBe('journaled about the week');
      fireEvent.click(within(mine).getByRole('button', { name: /^Chime on$/i }));
      expect(decodePersisted(localStorage.getItem(STORAGE_KEY)).timerChime).toBe(false);
      fireEvent.click(within(mine).getByRole('button', { name: /^Cancel$/i }));
      saved = decodePersisted(localStorage.getItem(STORAGE_KEY));
      expect(saved.timer).toBeNull();
      expect(saved.history).toHaveLength(1);
      expect(within(mine).getByRole('button', { name: /^Log Block$/i })).toBeInTheDocument();
    } finally {
      vi.useRealTimers();
    }
  });

  it('logs a countdown that ended while the page was closed', () => {
    vi.useFakeTimers();
    try {
      vi.setSystemTime(new Date('2026-09-28T15:00:00'));
      seed({ uiMode: 'simple', timer: { startedAt: Date.now() - 30 * 60_000, minutes: 15, spec: { modality: 'expressive', cadence: 'm15' }, note: null } });
      render(<CapacityControllerScreen />);
      const saved = decodePersisted(localStorage.getItem(STORAGE_KEY));
      expect(saved.timer).toBeNull();
      expect(saved.history).toHaveLength(1);
      expect(saved.history[0].dtMinutes).toBe(15);
      expect(screen.queryByLabelText(/Block in progress/i)).not.toBeInTheDocument();
      expect(screen.getByText(/Block k1 logged/i)).toBeInTheDocument();
    } finally {
      vi.useRealTimers();
    }
  });

  it('offers the timer in Advanced mode as well', () => {
    vi.useFakeTimers();
    try {
      vi.setSystemTime(new Date('2026-09-28T16:00:00'));
      seed({ spec: { modality: 'expressive', cadence: 'm25' } });
      render(<CapacityControllerScreen />);
      const form = screen.getByRole('region', { name: /Telemetry ingestion audit/i });
      fireEvent.click(within(form).getByRole('button', { name: /Start Block Timer · Δt = 25 m/i }));
      expect(within(form).getByRole('timer', { name: /Time left/i })).toHaveTextContent(/^25:00$/);
      expect(within(form).getByRole('button', { name: /Start Block Timer/i })).toBeDisabled();
      act(() => {
        vi.advanceTimersByTime(25 * 60_000);
      });
      expect(decodePersisted(localStorage.getItem(STORAGE_KEY)).history).toHaveLength(1);
      expect(decodePersisted(localStorage.getItem(STORAGE_KEY)).history[0].dtMinutes).toBe(25);
      expect(within(form).queryByLabelText(/Block in progress/i)).not.toBeInTheDocument();
    } finally {
      vi.useRealTimers();
    }
  });
});
