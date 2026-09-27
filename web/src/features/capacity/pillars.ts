/**
 * The Seven Pillars: constitutional filters every evaluation runs through.
 * Each pillar inspects the block being evaluated against the current state
 * and returns pass / flag / block with a one-line reason. Pure functions.
 */
import {
  compositeStrain,
  resolveSpec,
  type BlockSpec,
  type Constants,
  type Diagnostics,
  type StateVector,
} from './capacityModel';

export type PillarId = 'impartiality' | 'curiosity' | 'deconstruction' | 'somatic' | 'creativity' | 'strength' | 'empathy';
export type PillarStatus = 'pass' | 'flag' | 'block' | 'na';

export interface PillarVerdict {
  id: PillarId;
  name: string;
  symbol: string;
  status: PillarStatus;
  note: string;
}

export interface PillarContext {
  /** The block mentions family or relationships (Radical Empathy). */
  relational?: boolean;
  relationalCue?: string;
  /** Predicted state at the end of the block, when simulated. */
  predicted?: StateVector;
}

const f2 = (v: number) => v.toFixed(2);

/** Run a block through the seven pillars against the current state. */
export function evaluatePillars(spec: BlockSpec, x: StateVector, d: Diagnostics, k: Constants, ctx: PillarContext = {}): PillarVerdict[] {
  const b = resolveSpec(spec);
  const I1 = b.u.Ivis + b.u.Iaud;
  const O1 = b.u.O1;
  const F = compositeStrain(x);
  const out: PillarVerdict[] = [];

  // 1. Objective Impartiality — strip ego bias from energy and fatigue audits.
  {
    let status: PillarStatus = 'pass';
    let note = `Calibrations are blended into the model at gain ${f2(k.kalmanGain)}, not copied.`;
    if (O1 >= 0.8 && (spec.somatic === 'seated' || spec.somatic === 'supine') && F >= 0.5) {
      status = 'flag';
      note = `Felt strain under-reports: the model reads F = ${f2(F)} during deep output. The boundary follows the model.`;
    } else if (O1 >= 0.8) {
      status = 'flag';
      note = 'Deep output masks somatic signals; the boundary is enforced by the integrated strain, not by how it feels.';
    }
    out.push({ id: 'impartiality', name: 'Objective Impartiality', symbol: 'O_filter', status, note });
  }

  // 2. Curiosity — substantive depth floor.
  {
    const churn = spec.valuation === 'churn' && (I1 > 0 || O1 > 0);
    const shallow = x.V < k.Vmin;
    const status: PillarStatus = churn ? 'block' : shallow && I1 > 0 ? 'flag' : 'pass';
    const note = churn
      ? `Superficial intake fails the admissibility floor (V = 0.10 < V_min = ${f2(k.Vmin)}). Swap it for a book, art, or rest.`
      : shallow && I1 > 0
        ? `Recent activity was shallow (V = ${f2(x.V)} < ${f2(k.Vmin)}); intake is drained of value until depth recovers.`
        : 'Substantive depth holds.';
    out.push({ id: 'curiosity', name: 'Curiosity', symbol: 'V_curiosity', status, note });
  }

  // 3. Intellectual Deconstruction — prevent the quadratic β_in C_in I₁² spike.
  {
    const cost = k.betaIn * b.theta.Cin * I1 * I1;
    const branching = spec.scratchpad === 'speculative' || spec.scratchpad === 'rabbit';
    const status: PillarStatus = cost >= 0.25 ? 'block' : cost >= 0.12 || (branching && I1 > 0) ? 'flag' : 'pass';
    const note =
      cost >= 0.25
        ? `Quadratic intake cost ${f2(cost)} / h: split the material into first principles and drop intensity or density.`
        : cost >= 0.12
          ? `Intake cost ${f2(cost)} / h is climbing; chunk the material and tokenize tangents.`
          : branching && I1 > 0
            ? 'Untokenized tangents multiply backlog accrual; write them down as you go.'
            : I1 > 0
              ? `Intake cost ${f2(cost)} / h is within budget.`
              : 'No intake to deconstruct.';
    out.push({ id: 'deconstruction', name: 'Intellectual Deconstruction', symbol: 'C_deconstruct', status, note });
  }

  // 4. Somatic Grounding — ocular, neuromuscular baselines; enforce the singularity.
  {
    let status: PillarStatus = 'pass';
    let note = `F = ${f2(F)}; baselines within range.`;
    if (d.regime === 'singularity' && (I1 > 0 || O1 > 0)) {
      status = 'block';
      note = d.singularityMode === 'somatic' ? `Terminal strain (F = ${f2(F)}): shut down and sleep.` : 'Burnout singularity: no intake can restore you; rest with nothing going in, or sleep.';
    } else if (d.guardrails.opticalCutoff && b.u.Ivis > 0) {
      status = 'block';
      note = `Eyes are past the cutoff (F_vis = ${f2(x.Fvis)}); no screen or page.`;
    } else if (F >= 0.5 && b.somatic.staticSeated) {
      status = 'flag';
      note = `Strain is ${f2(F)} and this block is seated; take it lying down or on your feet.`;
    }
    out.push({ id: 'somatic', name: 'Somatic Grounding', symbol: 'F_somatic', status, note });
  }

  // 5. Creativity — active backlog clearance over passive consumption.
  {
    let status: PillarStatus = 'pass';
    let note = O1 > 0 ? 'Expressive or generative output digests the backlog.' : 'Rest neither adds to nor clears the backlog.';
    if (x.B >= k.BsatLock && I1 > 0) {
      status = 'block';
      note = `Backlog is saturated (B = ${f2(x.B)}); intake is locked until an expressive block clears it.`;
    } else if (x.B >= 0.4 && I1 > 0 && O1 === 0) {
      status = 'flag';
      note = `Backlog is ${f2(x.B)} and this block only consumes; an expressive block (journal, play, write) would clear it.`;
    }
    out.push({ id: 'creativity', name: 'Creativity', symbol: 'O_creative', status, note });
  }

  // 6. Strength Through Hardship — attenuate quadratic drag under necessary pressure.
  {
    const P = b.theta.P;
    const S = b.theta.S;
    let status: PillarStatus = 'na';
    let note = 'No external pressure on this block.';
    if (P > 0 && O1 + I1 > 0) {
      if (S >= 0.7) {
        status = 'pass';
        note = `A deadline you own: pressure drag attenuated by σ_strength = ${f2(k.sigmaStrength)}.`;
      } else {
        status = 'flag';
        note = `Pressure P = ${f2(P)} without agency (S = ${f2(S)}): the drag is not attenuated. Reclaim pacing or scope before continuing.`;
      }
    }
    out.push({ id: 'strength', name: 'Strength Through Hardship', symbol: 'σ_strength', status, note });
  }

  // 7. Radical Empathy — relational stewardship as sovereign action.
  {
    let status: PillarStatus = 'na';
    let note = 'No relational load in this block.';
    if (ctx.relational) {
      const reactive = b.theta.P >= 0.6 || b.theta.S < 0.7;
      status = reactive ? 'flag' : 'pass';
      note = reactive
        ? `Relational stewardship${ctx.relationalCue ? ` (“${ctx.relationalCue}”)` : ''} logged as pressure: convert it into a chosen, bounded act (own the pacing, set the end time) rather than reactive depletion.`
        : `Relational stewardship${ctx.relationalCue ? ` (“${ctx.relationalCue}”)` : ''} as sovereign, high-agency action.`;
    }
    out.push({ id: 'empathy', name: 'Radical Empathy', symbol: 'R_empathy', status, note });
  }

  return out;
}

export function pillarSummary(verdicts: PillarVerdict[]): { blocks: number; flags: number } {
  return { blocks: verdicts.filter((v) => v.status === 'block').length, flags: verdicts.filter((v) => v.status === 'flag').length };
}
