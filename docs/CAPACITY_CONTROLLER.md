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

Anchors: brown noise `I_anchor = 0.30`, familiar lyrical music `0.55`, tactile fidget
`O_anchor = 0.20`, walking treadmill `O_anchor = 0.35` (the Quadrant III range is
`[0.2, 0.4]`).

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

## Graded block catalog

A fixed catalog of block archetypes (sensory-isolation rest, brown-noise rest, treadmill walk,
supine deload, audio narrative, audiobook on the treadmill, literature on the page, structured
analysis, dense technical absorption, scratchpad synthesis, improv in silence, pacing dictation,
arousal ramp, generative sprint, walking-desk execution, deadline sprint, terminal sleep reset)
is always listed, whatever the routed quadrant. Each entry is forward-simulated from the
current state and scored on `[0, 100]`:

```
score   = 0.45 · fit + 0.40 · outcome + 0.15 · horizon, then capped by guardrails
fit     = routing-fit table: block kind × routed quadrant (0–100)
outcome = 50 + 50 · clamp(ΔU / 0.2, −1, 1),   U(x) = E − B − F − |A − A*| + 0.25 V
horizon = 100 · min(1, first-violation minute / catalog cadence)
```

Caps (with the reason shown on the card): input in a non-somatic singularity → 10, intake under
the backlog lock → 15, visual intake under the optical cutoff → 15, depleting intake
(`Φ_in < 0`) → 30, rest under the under-arousal gate → 35, execution at terminal strain → 10,
a boundary that trips inside 15 m → 25. The sleep entry scores 100 in a late-phase or somatic
singularity, 70 in a structural one, and 15 otherwise.

Letters: A ≥ 80, B ≥ 65, C ≥ 50, D ≥ 35, F below. **Arm** loads the entry into the audit form
with its effective cadence (the catalog cadence, or the largest standard cadence that survives
the stop rule).

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
3. **What to do next** — the top three graded blocks (built-in catalog plus your presets) as
   cards: grade, name, a one-sentence reason in words (fit, predicted effect, safe duration, or
   the cap that applies), the boundary, and **Start**. *Show all N blocks* lists the rest.
4. **Log the block you just did** — the armed block summarised in one line with *Change what you
   did* (opens the full form with plain legends: How long, Intensity, What you did, Background
   anchor, How substantive, How dense, Pressure and control, Tangents, Novelty, Body and posture),
   then duration (standard cadences or a custom 5–240 min), Tangents and Body, an expected-effect
   sentence (*restores energy, clears backlog → then: Good to build*), warnings in words, **Log
   Block**, and *Save this block as a preset*.

The copy layer lives in `capacityCopy.ts` (`PLAIN_SERIES`, `plainQuadrant`, `plainRegime`,
`plainCap`, `plainEffect`, `plainReason`).

**Advanced** restores the full instrument panel (status strip, HUD with sparklines and rates,
trajectory chart, full audit form, state-vector report, prescription cards, trailing prompt,
block log, catalog cards, constants panel, JSON export). The header toggle switches modes;
Calibrate / Sleep Reset / Undo are available in both.

### Flexibility

- **Custom duration**: `cadence: 'custom'` with `customMinutes` (5–240) anywhere a block is logged;
  prescriptions and catalog boundaries still snap to the standard cadences.
- **Intensity**: Light ×0.7 / Standard ×1 / Heavy ×1.25 scales the modality's `I_vis`, `I_aud` and
  `O₁` (clamped to 1) before integration.
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
