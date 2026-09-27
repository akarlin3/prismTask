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

## Governing equations (per hour; as specified)

```
A_inst = 0.45 I₁ + 0.25 I_anchor + 0.40 O₁ + 0.15 O_anchor
Γ(A)   = exp(−(A − A*)² / 2σ_A²)                       A* = 0.50, σ_A = 0.20
dA/dt  = κ_A (max(A_inst, A_rest) − A)
dV/dt  = κ_V (V_target − V)                            (held when I₁ + O₁ = 0)
Φ_in   = α_in V I₁ (1−E) Γ − β_in C_in I₁² − δ_in B I₁ − ψ I₁
Φ_out  = η_flow S (1−P) E O₁ Γ − (β_out P + ω F)(O₁² + O_anchor²)
dB/dt  = κ (C_in / V) I₁ + Ω_switch − λ B − μ V S (1−P) O₁
dF_vis/dt  = γ_vis I_vis − ρ_vis (1 − I_vis)
dF_body/dt = γ_posture 𝟙[static seated] + σ_load (O₁² + O_anchor²) − ρ_body 𝟙[kinetic / supported]
I*(t)  = (α_in V (1−E) Γ − δ_in B − ψ) / (β_in C_in)
```

Blocks integrate with forward Euler at 1-minute sub-steps, projecting back onto
`[0,1]⁶` after every step. `Ω_switch` is a rate over the block (0.25 / h for an
unbuffered rabbit hole).

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

A literal `I* ≤ 0` also fires whenever `E → 1` (nothing left to restore), which is why the
singularity is gated on `E < 0.50` and the high-`E` case is labelled *saturated*.

Routing order:

1. Late-phase singularity → **Sleep Reset**
2. `B ≥ 0.60 ∧ E < 0.40` → **I-A Zero-Input Flush**
3. `B ≥ 0.60` → **I-B Expressive Digestion**
4. `F ≥ 0.50` → **III Somatic Reset**
5. `E < 0.50 ∧ num_opt ≤ 0` → **Singularity / Zero-Input Rest**
6. `E < 0.50` → **II Controlled Absorption** (capped below `I*(C_in = 0.20)`)
7. `B < 0.40` → **IV High-Leverage Execution**
8. otherwise → **IV-B Buffered Execution** (`0.40 ≤ B < 0.60`, tokenized scratchpad)

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
