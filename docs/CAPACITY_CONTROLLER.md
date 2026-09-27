# 6D Capacity Controller — model notes

Web route: `/capacity` (Wellness → Capacity). Source: `web/src/features/capacity/`.

- `capacityModel.ts` — pure engine (state, catalogs, governing equations, discrete
  integration, diagnostics, quadrant routing, prescription engine, persistence codec).
- `CapacityControllerScreen.tsx` — the single-component UI (HUD, status strip,
  telemetry audit form, prescription engine, block log, override / constants / sleep modals).
- `__tests__/` — engine and screen tests (`npm run test:run -- src/features/capacity`).

State persists in `localStorage` under `prismtask.capacity.v1` (state vector, hours awake,
block index, last 200 log entries, constant overrides, last armed form).

## State, controls, context

```
x = [E, B, F_vis, F_body, A, V]ᵀ ∈ [0,1]⁶        F = max(F_vis, F_body)
u = [I_vis, I_aud, I_anchor, O₁, O_anchor]ᵀ       I₁ = I_vis + I_aud
θ = [C_in, P, S_agency, ψ(t)]ᵀ
```

## Governing equations (per hour; as specified, v2)

```
A_inst = 0.45 I₁ + 0.25 I_anchor + 0.40 O₁ + 0.15 O_anchor + ξ_novelty
Γ(A)   = exp(−(A − A*)² / 2σ_A²)                       A* = 0.50, σ_A = 0.20
dA/dt  = κ_A (max(A_inst, A_rest) − A)
dV/dt  = κ_V (V_target − V)                            (held when I₁ + O₁ = 0)
Φ_in   = α_in V I₁ (1−E) Γ − β_in C_in I₁² − δ_in B I₁ − ψ I₁
Φ_out  = η_flow S (1−P) E O₁ Γ − (β_out P + ω F)(O₁² + O_anchor²)
dB/dt  = κ (C_in / V) I₁ (1 + γ_assoc) + Ω_switch − λ B − μ V S (1−P) O₁
dF_vis/dt  = γ_vis I_vis − ρ_vis (1 − I_vis)
dF_body/dt = γ_posture 𝟙[static seated] + σ_load (O₁² + O_anchor²) − ρ_body 𝟙[kinetic / supported]
I*(t)  = (α_in V (1−E) Γ − δ_in B − ψ) / (β_in C_in)
```

Blocks integrate with forward Euler at 1-minute sub-steps, projecting back onto
`[0,1]⁶` after every step. `Ω_switch` is a rate over the block.

Telemetry mappings for the associative and novelty terms:

| Scratchpad discipline | γ_assoc | Ω_switch |
|---|---|---|
| Single Thread Flow | 0 | 0 |
| Tokenized To Scratchpad | 0 | 0 |
| Unbuffered Speculative Intake | 0.5 | 0 |
| Unbuffered Rabbit Hole | 1.0 | 0.25 / h |

| Novelty / entropy stimulation | ξ_novelty |
|---|---|
| Monotonous | 0.00 |
| Routine | 0.05 |
| Novel Cross-Domain | 0.15 |

Anchors: brown noise `I_anchor = 0.30`, familiar lyrical music `0.55`, background speech
`0.75`, background video `0.85`, tactile fidget `O_anchor = 0.20`, walking treadmill
`O_anchor = 0.35`, vigorous exercise `O_anchor = 0.65` (the Quadrant III range is `[0.2, 0.4]`).

## Closures (terms the spec references but does not pin)

| Term | Closure | Default |
|---|---|---|
| Energy balance | `dE/dt = Φ_in + Y_out(1−E) − K_out + R_rest − c_basal − ψ O₁` with `Φ_out = Y_out − K_out` | — |
| Passive recovery | `R_rest = ρ_E (E_cap − E)⁺ (1−I₁)(1−O₁) · restGain`, `E_cap = 1 − ψ(t)` | ρ_E = 0.30 |
| Circadian drag | `ψ(t) = ψ₀ · exp(max(0, t_awake − t_onset) / τ_ψ)` | ψ₀ = 0.10, t_onset = 10 h, τ_ψ = 4 h |
| Late phase | `t_awake ≥ t_late` trips the sleep singularity | t_late = 16 h |
| Tonic arousal | relaxation target `max(A_inst, A_rest)` | A_rest = 0.20 |
| Relaxation gains | κ_A, κ_V | 2.0 / h, 3.0 / h |
| Postural cost of output | σ_load | 0.25 |
| Admissibility floor | V_min | 0.25 |
| Somatic markers | supine: 𝟙kin = 1, restGain 1.25 · seated: 𝟙seat = 1 · ocular strain: γ_vis × 1.6, I_vis floor 0.35 · slump: γ_posture × 2 | — |
| Sleep reset | `E += (1−E)(1−e^{−h/3})`, `B ·= e^{−0.25h}`, `F ·= e^{−h/1.5}`, `A = 0.30`, `t_awake = 0` | — |
| Backlog lock latch | set once `B ≥ B_sat`; released by an output block (`O₁ > 0`, `I₁ = 0`) or once `B < 0.40` | — |

The yield saturation `(1 − E)` mirrors the `(1 − E)` factor the spec puts on restorative
input, and `ψ O₁` mirrors the `ψ I₁` input tax; without them, a pure-agency sprint pins
E at 1.0 within two blocks and a midnight hyperfocus reads as restorative. `Φ_in` and
`Φ_out` are still displayed exactly as the spec defines them.

Every constant (spec and closure) is editable in the Constants panel and stored with the log.

## Diagnostics and routing

`I*(t)` is evaluated with the live `Γ` and the armed block's `C_in`. Two extra readouts drive
the badge: `num_opt = α_in V (1−E) − δ_in B − ψ` (numerator at `Γ = 1`) and the fiction cap
`I*(C_in = 0.20)`.

| Regime | Condition | Badge |
|---|---|---|
| nominal | `num > 0` | green |
| arousal-limited | `num ≤ 0 < num_opt` | amber — ramp A toward A* first |
| saturated | `num_opt ≤ 0 ∧ E ≥ 0.50` | amber — reserves full, route to output |
| singularity (structural) | `num_opt ≤ 0 ∧ E < 0.50` | red, flashing — prohibit input, zero-input rest |
| singularity (late) | `t_awake ≥ t_late` | red, flashing — terminate session, sleep |
| singularity (somatic) | `F ≥ F_term = 0.80` | red, flashing — terminal sleep reset |

High-capacity guardrails (§4 of the v2 spec), all thresholds editable in the Constants panel:

| Guardrail | Condition | Effect |
|---|---|---|
| Optical cutoff | `F_vis ≥ 0.60` | `I_vis` forced to 0: no prescription carries visual intake; arming one shows a violation |
| Backlog saturated | `B ≥ 0.65`, latched | `I₁ > 0` prohibited until an expressive digestion (or execution) block runs, or `B < 0.40` |
| Under-arousal gate | `E ≥ 0.50 ∧ A < 0.35` | rest rejected; prescriptions lead with an arousal ramp (expressive output, music anchor, novel cross-domain stimulation) |
| True depletion | `E < 0.40 ∧ A < 0.35` | routes to Quadrant I-A (supine sensory isolation) regardless of B |
| Hyper-focus masking | armed `O₁ ≥ 0.80` | the somatic marker is logged but the boundary is enforced by the integrated F; the form says so |

A literal `I* ≤ 0` also fires whenever `E → 1` (nothing left to restore), which is why the
singularity is gated on `E < 0.50` and the high-`E` case is labelled *saturated*.

Routing order:

1. Late-phase singularity → **Terminal Sleep Reset**
2. `F ≥ 0.80` → **Terminal Sleep Reset**
3. `B ≥ 0.60 ∧ E < 0.40` → **I-A Zero-Input Flush** (`Δt ∈ [15, 25]` m)
4. `B ≥ 0.60` → **I-B Expressive Digestion** (`O₁ ∈ [0.3, 0.5]`, `I₁ = 0`, `S ≥ 0.9`, `P ≤ 0.1`)
5. `E < 0.40 ∧ A < 0.35` → **I-A Zero-Input Flush · True Depletion**
6. `F ≥ 0.50` → **III Somatic Reset** (`O_anchor ∈ [0.2, 0.4]`, `I_vis = 0`)
7. `E < 0.50 ∧ num_opt ≤ 0` → **Singularity / Zero-Input Rest**
8. `E < 0.50` → **II Controlled Intake** (`I₁ ≤ 0.7·I*(t)`, `C_in ≤ 0.30`, `V ≥ 0.85`, `I_vis = 0` preferred)
9. `B < 0.40` → **IV High-Leverage Execution** (`O₁ ∈ [0.6, 0.9]`, `I₁ = 0`, `S ≥ 0.7`, familiar music loop)
10. otherwise → **IV-B Buffered Execution** (`0.40 ≤ B < 0.60`, tokenized scratchpad)

Secondary constraints that also hold (somatic gate, arousal off tone, elevated ψ, `V < V_min`)
are listed on the card as flags.

## Prescriptions with hard time boundaries

Each quadrant yields 2–3 candidate configurations. Every candidate is simulated forward from
the current state for up to 120 minutes with its own stop rule (for execution: `E < 0.35`,
`B ≥ 0.60`, `F ≥ 0.55`, `Φ_out < 0`, or `t_late`; for absorption: `Φ_in < 0`, `B ≥ 0.60`,
`F_vis ≥ 0.60`, `E ≥ 0.70`; and so on). The first violation minute is snapped down to the
largest standard cadence (15 / 25 / 45 / 60 / 90) — that is the hard boundary shown on the
card, together with the predicted state at the boundary. A candidate that violates inside 15
minutes is marked inadmissible. **Arm This Block** loads the configuration into the audit form.

## Compared block catalog

A fixed catalog of block archetypes (sensory-isolation rest, brown-noise rest, treadmill walk,
supine deload, audio narrative, audiobook on the treadmill, literature on the page, structured
analysis, dense technical absorption, scratchpad synthesis, improv in silence, pacing dictation,
arousal ramp, generative sprint, walking-desk execution, deadline sprint, terminal sleep reset)
is always listed, whatever the routed quadrant. Each entry is forward-simulated from the
current state and scored on `[0, 100]`; the score orders the list, and every entry is then
**compared with the best one** rather than graded on its own:

```
score   = 0.45 · fit + 0.40 · outcome + 0.15 · horizon, then capped by guardrails
fit     = routing-fit table: block kind × routed quadrant (0–100)
outcome = 50 + 50 · clamp(ΔU / 0.2, −1, 1),   U(x) = E − B − F − |A − A*| + 0.25 V
horizon = 100 · min(1, first-violation minute / catalog cadence)
```

Caps (with the reason shown on the card): input in a non-somatic singularity → 10, intake under
the backlog lock → 15, visual intake under the optical cutoff → 15, depleting intake
(`Φ_in < 0`, intake-led blocks only: talking, presenting and hands-on work carry incidental
intake whose cost is already in the outcome) → 30, rest under the under-arousal gate → 35, execution at terminal strain → 10,
a boundary that trips inside 15 m → 25. The sleep entry scores 100 in a late-phase or somatic
singularity, 70 in a structural one, and 15 otherwise.

### Comparisons instead of grades

There are no letter grades. `compareBlocks` measures every scored block against the best option
for the current state (`Comparison`): the reference block's name, the score margin, a *standing*,
and the predicted end state minus the reference's for every meter (plus the composite-strain and
`|A − A*|` differences). Standings:

| Standing | Rule | Shown as |
|---|---|---|
| `best` | the reference itself (or the same block by `sameBlock`), or margin ≥ 0 | **Best now** |
| `close` | margin ≥ −8 | **Nearly as good** |
| `behind` | margin ≥ −25 | **A step behind** |
| `far` | margin < −25 | **Well behind** |
| `blocked` | any guardrail cap applies, whatever the margin | **Not now** |

The sentence under each block is generated from the comparison (`plainComparison`): *"A step
behind Rest in the dark: less energy, more backlog and more strain."*, *"Nearly as good as Deep
work at a walking desk: less backlog, more strain."*, *"Not now: intake is locked until you write
something out."*, or *"The best option for your state right now."* for the reference. At most
three differences are named, in meter order (energy, backlog, strain, activation, depth), each
only when the end states differ by ≥ 0.02. Show math adds the score and the margin in points.

**Arm** loads the entry into the audit form with its effective cadence (the catalog cadence, or
the largest standard cadence that survives the stop rule).

## Simple and advanced interface

The screen opens in **Simple** mode (persisted as `uiMode`), a single column written in plain
language; **Show math** (persisted as `showMath`) reveals the symbols, predicates and mono
diagnostics beside the plain copy.

1. **How this works** — a collapsed three-step explainer.
2. **Status** — a plain headline for the routed quadrant (*Good to build*, *Write it out*, *Move
   your body*, *Rest, no input*, *Take in something gentle*, *Stop and sleep*, …), a regime chip
   in words (*Intake helps right now* / *Wake up first* / *Nothing to refill* / *Intake drains
   you*), one sentence of guidance, six meters with plain names and a "higher / lower is better"
   hint (definitions on hover), and the awake time / block count.
3. **Recommended now** — exactly one block: the best-scoring entry of the catalog (built-in
   archetypes plus your presets) for this state, with its detail, a one-sentence reason (fit,
   predicted effect, safe duration, or the cap that applies), the boundary, and **Use this**.
   No other blocks are listed in the simple flow; the full compared catalog lives in Advanced.
4. **Your block** — starts empty: *nothing is assumed* until you describe a block or press
   **Use this** (`suggested` in the persisted state). Then the block's details are adjustable
   chips, the Seven Pillars row and the *Before you log it* comparison appear, followed by
   warnings in words, **Log Block** (which empties the card again) and *Save this block as a
   preset*. **Clear** empties it by hand. The full form (How long, Intensity, Playback speed,
   What you did, Background anchor, How substantive, How dense, Pressure and control, Tangents,
   Novelty, Body and posture) is one link away.

The copy layer lives in `capacityCopy.ts` (`PLAIN_SERIES`, `plainQuadrant`, `plainRegime`,
`plainCap`, `plainEffect`, `plainReason`).

**Advanced** restores the full instrument panel (status strip, HUD with sparklines and rates,
trajectory chart, full audit form, state-vector report, prescription cards, trailing prompt,
block log, catalog cards, constants panel, JSON export). The header toggle switches modes;
Calibrate / Sleep Reset / Undo are available in both.

### Describe a block in your own words

`blockDescriber.ts` turns free text into a proposed block, offline and deterministically:
activity templates (workout, nap, chores, meeting, emails, scrolling, videos, film, TV, gaming,
audiobook, podcast, conversation, commute, journaling, making, coding, math, writing, building,
studying, dense reading, fiction, reading, listening, generic work) set the modality and its
natural density / value, then per-field lexicons override anchor, posture, pressure, tangents,
novelty, intensity, value and density from cues in the text, and coherence rules tidy up
(rest has no density, expressing has no intake density, rest defaults to lying down). Durations
are parsed from phrasings like *15 min*, *2h*, *1h30*, *half an hour*, *a pomodoro*; without one,
the fixed block length applies. The result carries a confidence (activity evidence dominates), the
cue word behind each choice, and the fields that were assumed. The log card shows this as
*Understood as* with the plain choice per field; below 0.5 confidence the full form opens with the
guess pre-selected. The description is kept as a `note` on the log entry.

### Adjusting a categorised block

Every field of the armed block is a chip (*Activity*, *Length*, *Background*, *Body*, *Kind of
thing*, *Density*, *Pressure*, *Tangents*, *Novelty*, *Intensity*, and *Speed* whenever something
is playing). Tapping a chip opens a picker
for that field alone; a changed chip reads *← you*, a described one shows the cue word, an
assumed one is dimmed. *Open the full form* still shows every group at once.

### Wider parameter ranges

Every axis of a block spans a wider range of options, with plain labels in the simple flow:

| Axis | Options (model value) |
|---|---|
| Activity `T₁` | Rest (0) · Listening (`I_aud` 0.35, playback) · Watching (`I_vis` 0.35 + `I_aud` 0.15, playback) · Browsing / skimming (`I_vis` 0.30) · Reading (0.50) · Studying dense material (0.85) · Talking with people (`I_aud` 0.30 + `O₁` 0.30) · Presenting / teaching (`I_aud` 0.15 + `O₁` 0.55) · Games / interactive (`I_vis` 0.55 + `O₁` 0.30) · Hands-on (`I_vis` 0.15 + `O₁` 0.30) · Expressing (`O₁` 0.35) · Focused work (`O₁` 0.80) |
| Background `T₂` | Silence · Background noise (`I_a` 0.30) · Familiar music (0.55) · Podcast / people talking (0.75) · TV / video in the background (0.85) · Fidget (`O_a` 0.20) · Walking (`O_a` 0.35, kinetic) · Hard exercise (`O_a` 0.65, kinetic) |
| Kind of thing `V_target` | Numbing (0, inadmissible) · Churn (0.10, inadmissible) · Necessary stuff (0.50) · People and care (0.70) · Books / art / music (0.85) · Real work or craft (1.00) |
| Density `C_in` | Nothing (0.05) · Small talk, memes (0.10) · Light (0.20) · Medium (0.40) · Dense (0.65) · Very dense (0.90) · Extreme: new research (1.00) |
| Pressure `P, S` | My own choice (0, 1) · A soft goal (0.25, 0.85) · A real deadline I own (0.60, 0.70) · Told to, little say (0.70, 0.35) · Being judged (0.90, 0.25) · Emergency, no control (1.00, 0.05) |
| Tangents `γ_assoc, Ω` | Stayed on track (0, 0) · Wrote tangents down (0, 0) · A few tangents, let go (0.25, 0) · Ideas kept branching (0.5, 0) · Rabbit hole (1.0, 0.25) · Constant switching (1.5, 0.50) |
| Novelty `ξ` | Mind-numbing (−0.05) · Boring (0) · Familiar (0.05) · New territory (0.15) · Overwhelmingly new (0.30) |
| Body | Lying down (rest ×1.25) · Moving around (kinetic) · Standing still · Sitting · Eyes hurting · Slouching or stiff · Eyes and back both hurting |
| Intensity | Barely ×0.4 · Easy ×0.7 · Normal ×1 · Hard ×1.25 · All out ×1.6 |
| Playback speed | 0.5× … 3× (listening and watching only) |
| Length | 5–480 min (custom), standard cadences 15 / 25 / 45 / 60 / 90 |

The block kind used by the routing-fit table follows the control vector: `O₁ ≥ 0.6` execute;
`O₁ > 0` and `O₁ ≥ I₁` express (talking, presenting, hands-on); `I₁ > 0` absorb (including games
and video); otherwise a kinetic anchor is somatic and the rest is rest. The describer maps
everyday phrases onto the wider ends (*gave a talk* → presenting, standing, being judged; *family
dinner* → talking with people, people and care; *production outage, on call* → emergency;
*kept switching tabs* → constant switching; *eyes are tired and my neck hurts* → eyes and back;
*zoned out, mind-numbing* → numbing, mind-numbing; *flat out* → all out; *at my standing desk* →
standing still; *lifting weights* → hard exercise). The Constants panel's slider ranges were
widened in step (gains to 5, drag schedule to 48 h).

### Before you log it

The block you suggest (described in words, taken from the recommendation, or adjusted chip by
chip) is scored exactly like a catalog entry at its own length and compared with the recommended
block (`gradeBlock(spec, …, against)`, `Comparison.self` when it is that block by content), and
the card shows its standing pill, the comparison sentence, its reason, and all six meters as
*now → after the block* (bar with the current fill, the change band, and a marker at the
predicted value), followed by the routed headline the state would land on. In Advanced mode the
predicted-Δx panel carries the same standing and margin. Everything updates live before anything
is logged.

### The Seven Pillars

Every evaluation of a block runs through seven constitutional filters (`pillars.ts`,
`evaluatePillars`), each returning pass / flag / block / n/a with a one-line reason, shown as a
row under the block being logged (simple and advanced):

| Pillar | Operator | Check |
|---|---|---|
| Objective Impartiality | `O_filter` | Calibrations are blended into the model estimate with a Kalman gain `K_filter` (default 0.6, `blendCalibration`) instead of copied; deep output (`O₁ ≥ 0.8`) flags that felt strain under-reports the integrated `F`. |
| Curiosity | `V_curiosity` | Blocks churn (`V_target < V_min`); flags intake while recent depth `V < V_min`. |
| Intellectual Deconstruction | `C_deconstruct` | Watches the quadratic intake cost `β_in C_in I₁²` (flag ≥ 0.12 / h, block ≥ 0.25 / h) and untokenized tangents. |
| Somatic Grounding | `F_somatic` | Blocks intake or output in a singularity and visual intake past the optical cutoff; flags seated blocks at `F ≥ 0.5`. |
| Creativity | `O_creative` | Blocks intake under the backlog lock; flags passive consumption at `B ≥ 0.4`; passes expressive or generative output. |
| Strength Through Hardship | `σ_strength` | A deadline the operator owns (`S ≥ 0.7`) has its `β_out·P` drag attenuated by `σ_strength` (default 0.35) in `Φ_out`; pressure without agency is flagged. |
| Radical Empathy | `R_empathy` | Relational cues in the description (family, partner, friends, caregiving) at high pressure or low agency are flagged as reactive; with agency they pass as sovereign action. |

### Fixed block length

`blockLength` (default 15 min, editable in *How this works*) is the length of every block in the
simple flow: grades and boundaries are computed for that length, **Start** arms it, the log card
is titled for it, and the describer defaults to it. Other lengths remain available under *Change
what you did* (standard cadences or a custom 5–240 min).

### Everyday vocabulary

Every option has a plain label used by the simple interface (for example *Reading or watching*,
*Lying down or moving*, *A real deadline*, *Fell down a rabbit hole*) beside the model label used
in Advanced mode, and the catalog includes everyday blocks (short nap, meditate, gym, call a
friend, TV, meeting, emails, social media, video games, cook or do chores) so the grades speak to
habits the spec never named.

### Flexibility

- **Custom duration**: `cadence: 'custom'` with `customMinutes` (5–480) anywhere a block is logged;
  prescriptions and catalog boundaries still snap to the standard cadences.
- **Intensity**: Minimal ×0.4 / Light ×0.7 / Standard ×1 / Heavy ×1.25 / Maximal ×1.6 scales the
  modality's `I_vis`, `I_aud` and `O₁` (clamped to 1) before integration.
- **Playback speed** (`speed`, `PLAYBACK_SPEEDS`: 0.5× / 0.75× / 1× / 1.25× / 1.5× / 1.75× / 2× /
  2.5× / 3×): the factor multiplies the intake channels `I_vis` and `I_aud` of the played-back
  modalities (listening, watching) only, clamped to 1 and compounding with intensity; never the
  output, and never a page you read yourself. At 2× an audiobook delivers twice the words per
  minute, so restoration rises linearly with `I₁` while the quadratic `β_in C_in I₁²` cost, the
  `δ_in B I₁` drag and the backlog accrual rise faster: a fast playback crosses `I*(t)` sooner and
  reads as *depleting intake* earlier. The describer reads *at 1.5x*, *2× speed*, *double speed*,
  *slowed down*, *sped up* (`parseSpeed`, snapped to the nearest catalog speed; *3 x 10 squats* is
  not a speed) and only attaches a speed to blocks with an intake channel. **Usual listening
  speed** (`listeningSpeed`, set under *How this works*) is applied to the built-in listening
  entries in the catalog and to described listening blocks that state no speed; presets keep the
  speed they were saved with.
- **Presets**: any armed block can be saved by name (`presets`, up to 50). Presets are graded like
  built-in catalog entries (kind inferred from the control vector: `O₁ ≥ 0.6` execute, `O₁ > 0`
  express, `I₁ > 0` absorb, kinetic anchor somatic, else rest), carry a *your preset* tag, and can
  be deleted from the list.

## Operating protocol in the UI

The copilot's three-step protocol maps onto the screen:

1. **Ingestion & audit** — the segmented telemetry form (cadence, modality, anchor,
   valuation, density, context, scratchpad discipline, novelty, somatic marker) with a live
   predicted Δx and routing preview before the block is integrated.
2. **State vector update** — after each block, the *State Vector Update* panel lists every
   variable as `old → new` with its flux (ΔE with Φ_in / Φ_out, backlog as associative load
   vs digested vs decay, Γ efficiency for A), followed by I*(t), the regime, and the active
   guardrails. *Copy Report* puts the same content on the clipboard as Markdown.
3. **Prescriptive recommendation** — the routed quadrant card, 2–3 configurations with hard
   boundaries, and exactly one trailing operational prompt (*Lock in «…» with the boundary at
   N m?*) that arms the top configuration.

Copy stays peer-level and free of praise or padding, per the behavioural rules.
