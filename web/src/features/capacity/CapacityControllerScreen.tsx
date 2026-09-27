import { useEffect, useMemo, useRef, useState, type KeyboardEvent, type ReactNode } from 'react';
import {
  Activity,
  ArrowDownRight,
  ArrowUpRight,
  BedDouble,
  BookOpen,
  ChevronDown,
  ChevronUp,
  ClipboardCopy,
  Grid2x2,
  HelpCircle,
  Save,
  ShieldCheck,
  Sparkles,
  Footprints,
  Gauge,
  Info,
  Minus,
  Moon,
  PenLine,
  Play,
  Settings2,
  Sigma,
  SlidersHorizontal,
  Trash2,
  TriangleAlert,
  Undo2,
  X,
  Zap,
} from 'lucide-react';
import {
  ANCHORS,
  CADENCES,
  CUSTOM_CADENCE,
  DEFAULT_BLOCK_LENGTH,
  INTENSITIES,
  MAX_CUSTOM_MINUTES,
  MIN_CUSTOM_MINUTES,
  CONSTANT_META,
  CONTEXTS,
  DEFAULT_CONSTANTS,
  DEFAULT_STATE,
  DENSITIES,
  HISTORY_LIMIT,
  MODALITIES,
  NOVELTIES,
  PLAYBACK_SPEEDS,
  SCRATCHPADS,
  SOMATICS,
  STATE_KEYS,
  STORAGE_KEY,
  VALUATIONS,
  applySleepReset,
  arousalPotential,
  blendCalibration,
  blockMinutes,
  compositeStrain,
  decodePersisted,
  defaultPersisted,
  diagnose,
  encodePersisted,
  gradeBlock,
  gradeCatalog,
  integrateBlock,
  modalityIsPlayback,
  nextBacklogLatch,
  prescribe,
  replayHistory,
  resolveSpec,
  route,
  type BlockSpec,
  type ConfigKind,
  type Constants,
  type Diagnostics,
  type GradedBlock,
  type GuardrailWarning,
  type HistoryEntry,
  type InputRegime,
  type Option,
  type PersistedState,
  type Prescription,
  type Routing,
  type SpeedKey,
  type Standing,
  type StateKey,
  type StateVector,
  type UserPreset,
  withMinutes,
} from './capacityModel';
import { PLAIN_BY_KEY, STANDING_LABEL, joinEffects, plainComparison, plainEffect, plainGuardrail, plainQuadrant, plainReason, plainRegime } from './capacityCopy';
import { describeBlock, type DescribedBlock, type DescribedField } from './blockDescriber';
import { evaluatePillars, pillarSummary, type PillarVerdict } from './pillars';

// ---------------------------------------------------------------------------
// Presentation metadata
// ---------------------------------------------------------------------------

type Polarity = 'high-good' | 'low-good' | 'target';

interface SeriesMeta {
  key: StateKey;
  symbol: string;
  label: string;
  hint: string;
  hex: string;
  text: string;
  bg: string;
  polarity: Polarity;
}

const SERIES: readonly SeriesMeta[] = [
  { key: 'E', symbol: 'E', label: 'Systemic Energy', hint: '1 peaked · 0 exhausted', hex: '#34d399', text: 'text-emerald-400', bg: 'bg-emerald-400', polarity: 'high-good' },
  { key: 'B', symbol: 'B', label: 'Cognitive Backlog', hint: '1 jammed · 0 clear', hex: '#fbbf24', text: 'text-amber-400', bg: 'bg-amber-400', polarity: 'low-good' },
  { key: 'Fvis', symbol: 'F_vis', label: 'Ocular Strain', hint: '1 acute blur · 0 fresh', hex: '#fb7185', text: 'text-rose-400', bg: 'bg-rose-400', polarity: 'low-good' },
  { key: 'Fbody', symbol: 'F_body', label: 'Postural Strain', hint: '1 heavy tension · 0 fresh', hex: '#c084fc', text: 'text-purple-400', bg: 'bg-purple-400', polarity: 'low-good' },
  { key: 'A', symbol: 'A', label: 'Executive Arousal', hint: 'A* = 0.50 optimal tone', hex: '#22d3ee', text: 'text-cyan-400', bg: 'bg-cyan-400', polarity: 'target' },
  { key: 'V', symbol: 'V', label: 'Substantive Depth', hint: '1 generative · 0 churn', hex: '#818cf8', text: 'text-indigo-400', bg: 'bg-indigo-400', polarity: 'high-good' },
];

const SERIES_BY_KEY: Record<StateKey, SeriesMeta> = Object.fromEntries(SERIES.map((s) => [s.key, s])) as Record<StateKey, SeriesMeta>;

const KIND_ICON: Record<ConfigKind, typeof Activity> = {
  rest: Moon,
  absorb: BookOpen,
  express: PenLine,
  execute: Zap,
  somatic: Footprints,
  sleep: BedDouble,
};

const QUADRANT_TONE: Record<Routing['quadrant'], string> = {
  SINGULARITY: 'border-red-500/70 bg-red-500/10 text-red-300',
  'I-A': 'border-amber-400/60 bg-amber-400/10 text-amber-300',
  'I-B': 'border-amber-400/60 bg-amber-400/10 text-amber-300',
  II: 'border-indigo-400/60 bg-indigo-400/10 text-indigo-300',
  III: 'border-purple-400/60 bg-purple-400/10 text-purple-300',
  IV: 'border-emerald-400/60 bg-emerald-400/10 text-emerald-300',
  'IV-B': 'border-emerald-400/40 bg-emerald-400/5 text-emerald-200',
};

const STANDING_TONE: Record<Standing, string> = {
  best: 'border-emerald-400/70 bg-emerald-400/15 text-emerald-200',
  close: 'border-cyan-400/60 bg-cyan-400/10 text-cyan-200',
  behind: 'border-zinc-500 bg-zinc-800 text-zinc-200',
  far: 'border-amber-400/60 bg-amber-400/10 text-amber-200',
};

/** The guardrails a block trips, as warnings that name the guardrail. */
function GuardrailList({ warnings, showMath, compact = false }: { warnings: GuardrailWarning[]; showMath: boolean; compact?: boolean }) {
  if (warnings.length === 0) return null;
  return (
    <ul className={`grid gap-1 ${compact ? 'mt-1' : 'mt-1.5'}`} aria-label="Guardrail warnings">
      {warnings.map((w) => {
        const p = plainGuardrail(w);
        return (
          <li key={w.type} className="flex items-start gap-1.5 rounded-md border border-amber-400/40 bg-amber-400/10 px-2 py-1.5 text-[11px] leading-snug text-amber-100">
            <TriangleAlert className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
            <span>
              <span className="font-semibold">{p.label}</span> — {p.text}
              {showMath && <span className="ml-1 font-mono text-[10px] text-amber-200/70">{w.detail}</span>}
            </span>
          </li>
        );
      })}
    </ul>
  );
}

/** Where a block stands against the best option right now, as a small pill. */
function StandingPill({ standing, size = 'md' }: { standing: Standing; size?: 'sm' | 'md' }) {
  return (
    <span
      className={`inline-flex shrink-0 items-center whitespace-nowrap rounded-md border font-semibold ${size === 'sm' ? 'px-1.5 py-0.5 text-[10px]' : 'px-2 py-1 text-[11px]'} ${STANDING_TONE[standing]}`}
      aria-label={`Standing ${STANDING_LABEL[standing]}`}
    >
      {STANDING_LABEL[standing]}
    </span>
  );
}

const SLEEP_OPTIONS = [
  { hours: 4, label: '4 h', detail: 'Fragmented' },
  { hours: 6, label: '6 h', detail: 'Short' },
  { hours: 7.5, label: '7.5 h', detail: 'Full architecture' },
  { hours: 9, label: '9 h', detail: 'Extended' },
];

const fmt = (v: number, d = 2): string => v.toFixed(d);
const fmtSigned = (v: number, d = 2): string => {
  const r = Math.abs(v) < 0.5 * 10 ** -d ? 0 : v;
  return (r > 0 ? '+' : r < 0 ? '−' : '±') + Math.abs(r).toFixed(d);
};

function deltaTone(key: StateKey, delta: number, before: number, k: Constants): string {
  const s = SERIES_BY_KEY[key];
  if (Math.abs(delta) < 0.005) return 'text-zinc-500';
  if (s.polarity === 'target') {
    const improved = Math.abs(before + delta - k.Astar) < Math.abs(before - k.Astar);
    return improved ? 'text-emerald-400' : 'text-rose-400';
  }
  const good = s.polarity === 'high-good' ? delta > 0 : delta < 0;
  return good ? 'text-emerald-400' : 'text-rose-400';
}

function describeSpec(spec: BlockSpec): string {
  const m = MODALITIES.find((o) => o.key === spec.modality)!;
  const a = ANCHORS.find((o) => o.key === spec.anchor)!;
  const v = VALUATIONS.find((o) => o.key === spec.valuation)!;
  const d = DENSITIES.find((o) => o.key === spec.density)!;
  const c = CONTEXTS.find((o) => o.key === spec.context)!;
  const s = SCRATCHPADS.find((o) => o.key === spec.scratchpad)!;
  const n = NOVELTIES.find((o) => o.key === spec.novelty)!;
  const so = SOMATICS.find((o) => o.key === spec.somatic)!;
  const sp = PLAYBACK_SPEEDS.find((o) => o.key === (spec.speed ?? 'x1'))!;
  const speed = sp.factor !== 1 && modalityIsPlayback(spec.modality) ? ` ${sp.label}` : '';
  return `T₁ ${m.label}${speed} · T₂ ${a.label} · V ${v.V.toFixed(2)} · C_in ${d.Cin.toFixed(2)} · P ${c.P.toFixed(2)} S ${c.S.toFixed(2)} · γ_a ${s.gammaAssoc.toFixed(1)} Ω ${s.omega.toFixed(2)} · ξ ${n.xi.toFixed(2)} · ${so.label}`;
}

/** Step-2 / Step-3 report in the copilot's Markdown protocol, for pasting into a chat thread. */
function buildMarkdownReport(entry: HistoryEntry, diag: Diagnostics, routing: Routing, prescriptions: Prescription[], k: Constants): string {
  const m = entry.mean;
  const line = (key: StateKey, note: string) => `- **${SERIES_BY_KEY[key].label} (${SERIES_BY_KEY[key].symbol}):** \`${fmt(entry.xBefore[key], 3)}\` → \`${fmt(entry.xAfter[key], 3)}\` (${note})`;
  const guards = Object.entries(diag.guardrails)
    .filter(([, v]) => v)
    .map(([g]) => g);
  const lines = [
    `## State Vector Update · block k${entry.k} · Δt = ${entry.dtMinutes} m`,
    entry.spec ? `Telemetry: ${describeSpec(entry.spec)}` : '',
    '',
    line('E', m ? `ΔE ${fmtSigned(m.dE, 3)} / h · Φ_in ${fmtSigned(m.phiIn, 3)} · Φ_out ${fmtSigned(m.phiOut, 3)}` : 'calibration'),
    line('B', m ? `associative load +${fmt(m.accrual, 3)} / h vs digested −${fmt(m.digestion, 3)} / h, decay −${fmt(m.decay, 3)} / h` : 'calibration'),
    line('Fvis', m ? `${fmtSigned(m.dFvis, 3)} / h` : 'calibration'),
    line('Fbody', m ? `${fmtSigned(m.dFbody, 3)} / h` : 'calibration'),
    line('A', m ? `Γ_arousal efficiency ${fmt(m.gamma, 3)}` : 'calibration'),
    line('V', m ? `${fmtSigned(m.dV, 3)} / h` : 'calibration'),
    '',
    `**Diagnostics:** I*(t) = ${fmt(diag.Istar)} · Γ = ${fmt(diag.gamma, 3)} · ψ(t) = ${fmt(diag.psi, 3)} · regime **${diag.regime}**${diag.singularityMode ? ` (${diag.singularityMode})` : ''} · guardrails: ${guards.length ? guards.join(', ') : 'none'}`,
    '',
    `## Prescription · ${routing.quadrant} ${routing.title}`,
    `Trigger: ${routing.trigger}`,
    '',
    ...prescriptions.map((p, i) => `${i + 1}. **${p.name}** — ${p.kind === 'sleep' ? `${p.sleepHours ?? 7.5} h sleep` : `hard boundary ${p.boundMinutes} m`}. ${p.spec ? describeSpec(p.spec) + '. ' : ''}${p.stopRule}`),
    '',
    prescriptions[0]
      ? prescriptions[0].kind === 'sleep'
        ? `Terminate the session and log the ${prescriptions[0].sleepHours ?? 7.5} h sleep reset on waking?`
        : `Lock in «${prescriptions[0].name}» with the boundary at ${prescriptions[0].boundMinutes} m?`
      : '',
  ];
  void k;
  return lines.filter((l, i, arr) => !(l === '' && arr[i - 1] === '')).join('\n');
}

function lastDeltaEntry(history: HistoryEntry[]): HistoryEntry | null {
  const last = history.length ? history[history.length - 1] : null;
  return last && last.kind === 'block' ? last : null;
}

function loadPersisted(): PersistedState {
  try {
    return decodePersisted(localStorage.getItem(STORAGE_KEY));
  } catch {
    return defaultPersisted();
  }
}

// ---------------------------------------------------------------------------
// Small building blocks
// ---------------------------------------------------------------------------

function SectionTitle({ icon: Icon, children, aside }: { icon: typeof Activity; children: ReactNode; aside?: ReactNode }) {
  return (
    <div className="mb-3 flex items-center justify-between gap-3">
      <h2 className="flex items-center gap-2 text-[11px] font-semibold uppercase tracking-[0.16em] text-zinc-400">
        <Icon className="h-3.5 w-3.5 text-zinc-500" aria-hidden="true" />
        {children}
      </h2>
      {aside}
    </div>
  );
}

function IconButton({
  icon: Icon,
  label,
  onClick,
  disabled,
  tone = 'default',
}: {
  icon: typeof Activity;
  label: string;
  onClick: () => void;
  disabled?: boolean;
  tone?: 'default' | 'danger';
}) {
  const base =
    tone === 'danger'
      ? 'border-red-500/40 text-red-300 hover:border-red-400 hover:bg-red-500/10'
      : 'border-zinc-800 text-zinc-300 hover:border-zinc-600 hover:bg-zinc-900 hover:text-zinc-100';
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className={`inline-flex items-center gap-1.5 rounded-md border px-2.5 py-1.5 text-xs font-medium transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-cyan-400 disabled:cursor-not-allowed disabled:opacity-40 ${base}`}
    >
      <Icon className="h-3.5 w-3.5" aria-hidden="true" />
      <span className="hidden sm:inline">{label}</span>
      <span className="sr-only sm:hidden">{label}</span>
    </button>
  );
}

function Sparkline({ values, hex }: { values: number[]; hex: string }) {
  const w = 96;
  const h = 28;
  if (values.length < 2) {
    return <svg width={w} height={h} viewBox={`0 0 ${w} ${h}`} aria-hidden="true" className="shrink-0" />;
  }
  const pts = values.map((v, i) => [2 + (i / (values.length - 1)) * (w - 4), 2 + (1 - v) * (h - 4)] as const);
  const line = pts.map(([x, y]) => `${x.toFixed(1)},${y.toFixed(1)}`).join(' ');
  const area = `M${pts[0][0].toFixed(1)},${h - 2} L${line.replace(/ /g, ' L')} L${pts[pts.length - 1][0].toFixed(1)},${h - 2} Z`;
  const [ex, ey] = pts[pts.length - 1];
  return (
    <svg width={w} height={h} viewBox={`0 0 ${w} ${h}`} aria-hidden="true" className="shrink-0">
      <path d={area} fill={hex} fillOpacity={0.1} />
      <polyline points={line} fill="none" stroke={hex} strokeWidth={1.5} strokeLinejoin="round" strokeLinecap="round" />
      <circle cx={ex} cy={ey} r={2.5} fill={hex} stroke="#09090b" strokeWidth={1.5} />
    </svg>
  );
}

function StateTile({
  meta,
  value,
  delta,
  rate,
  history,
  k,
}: {
  meta: SeriesMeta;
  value: number;
  delta: number | null;
  rate: number | null;
  history: number[];
  k: Constants;
}) {
  const tone = delta === null ? 'text-zinc-500' : deltaTone(meta.key, delta, value - delta, k);
  const Arrow = delta === null || Math.abs(delta) < 0.005 ? Minus : delta > 0 ? ArrowUpRight : ArrowDownRight;
  return (
    <div className="rounded-lg border border-zinc-800 bg-zinc-900/60 p-3">
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <div className="flex items-baseline gap-2">
            <span className={`font-mono text-sm font-semibold ${meta.text}`}>{meta.symbol}</span>
            <span className="truncate text-[11px] uppercase tracking-[0.12em] text-zinc-400">{meta.label}</span>
          </div>
          <div className="mt-0.5 text-[10.5px] text-zinc-500">{meta.hint}</div>
        </div>
        <Sparkline values={history} hex={meta.hex} />
      </div>
      <div className="mt-2 flex items-end justify-between gap-3">
        <span className="font-mono text-2xl font-semibold leading-none text-zinc-100">{fmt(value, 3)}</span>
        <span className={`flex items-center gap-1 font-mono text-[11px] ${tone}`} title="Δ over the last block · mean rate per hour">
          <Arrow className="h-3.5 w-3.5" aria-hidden="true" />
          {delta === null ? '— / blk' : `${fmtSigned(delta, 3)} / blk`}
          <span className="text-zinc-600">·</span>
          {rate === null ? '— / h' : `${fmtSigned(rate, 2)} / h`}
        </span>
      </div>
      <div className="mt-2 h-1.5 w-full overflow-hidden rounded-full bg-zinc-800" role="meter" aria-valuemin={0} aria-valuemax={1} aria-valuenow={Number(value.toFixed(3))} aria-label={meta.label}>
        <div className={`h-full rounded-full ${meta.bg}`} style={{ width: `${(value * 100).toFixed(1)}%` }} />
      </div>
    </div>
  );
}

function StatCell({ label, value, sub, tone = 'text-zinc-100' }: { label: string; value: string; sub?: string; tone?: string }) {
  return (
    <div className="bg-zinc-950 px-3 py-2.5">
      <div className="font-mono text-[10px] tracking-wide text-zinc-500">{label}</div>
      <div className={`mt-0.5 font-mono text-lg font-semibold leading-tight ${tone}`}>{value}</div>
      {sub && <div className="mt-0.5 font-mono text-[10.5px] text-zinc-500">{sub}</div>}
    </div>
  );
}

function RegimeBadge({ regime, Istar, mode }: { regime: InputRegime; Istar: number; mode: Diagnostics['singularityMode'] }) {
  if (regime === 'singularity') {
    return (
      <div className="flex items-center gap-2 bg-red-500/15 px-3 py-2.5 text-red-300 motion-safe:animate-pulse" role="alert">
        <TriangleAlert className="h-5 w-5 shrink-0" aria-hidden="true" />
        <div>
          <div className="text-[11px] font-bold uppercase tracking-[0.16em]">Burnout Singularity</div>
          <div className="font-mono text-[10.5px]">
            {mode === 'late' ? 'I*(t) ≤ 0 · terminate session · sleep reset' : mode === 'somatic' ? 'F ≥ F_term · terminal sleep reset' : 'I*(t) ≤ 0 · prohibit input · zero-input rest'}
          </div>
        </div>
      </div>
    );
  }
  if (regime === 'saturated' || regime === 'arousal-limited') {
    return (
      <div className="flex items-center gap-2 bg-amber-400/10 px-3 py-2.5 text-amber-300" role="status">
        <TriangleAlert className="h-5 w-5 shrink-0" aria-hidden="true" />
        <div>
          <div className="text-[11px] font-bold uppercase tracking-[0.16em]">I*(t) ≤ 0 · {regime === 'saturated' ? 'Saturated' : 'Arousal-Limited'}</div>
          <div className="font-mono text-[10.5px]">{regime === 'saturated' ? 'reserves full — route to output' : 'ramp A toward A* before intake'}</div>
        </div>
      </div>
    );
  }
  return (
    <div className="flex items-center gap-2 bg-emerald-400/10 px-3 py-2.5 text-emerald-300" role="status">
      <Gauge className="h-5 w-5 shrink-0" aria-hidden="true" />
      <div>
        <div className="text-[11px] font-bold uppercase tracking-[0.16em]">Nominal</div>
        <div className="font-mono text-[10.5px]">I* = {fmt(Istar)} &gt; 0 · restorative zone open</div>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Trajectory chart (last 10 blocks)
// ---------------------------------------------------------------------------

function TrajectoryChart({ points, firstIndex, hidden, onToggle }: { points: StateVector[]; firstIndex: number; hidden: ReadonlySet<StateKey>; onToggle: (key: StateKey) => void }) {
  const [hover, setHover] = useState<number | null>(null);
  const W = 560;
  const H = 200;
  const padL = 30;
  const padR = 46;
  const padT = 10;
  const padB = 22;
  const n = points.length;
  const xAt = (i: number) => padL + (n > 1 ? (i / (n - 1)) * (W - padL - padR) : (W - padL - padR) / 2);
  const yAt = (v: number) => padT + (1 - v) * (H - padT - padB);
  const visible = SERIES.filter((s) => !hidden.has(s.key));

  // Direct end labels only where they do not collide; the legend carries the rest.
  const endLabels: { key: StateKey; y: number; text: string; hex: string }[] = [];
  const used: number[] = [];
  for (const s of [...visible].sort((a, b) => points[n - 1][a.key] - points[n - 1][b.key])) {
    const y = yAt(points[n - 1][s.key]);
    if (used.every((u) => Math.abs(u - y) >= 11)) {
      used.push(y);
      endLabels.push({ key: s.key, y, text: s.symbol, hex: s.hex });
    }
  }

  const onMove = (clientX: number, target: SVGSVGElement) => {
    const rect = target.getBoundingClientRect();
    const px = ((clientX - rect.left) / rect.width) * W;
    let best = 0;
    let bestD = Infinity;
    for (let i = 0; i < n; i += 1) {
      const d = Math.abs(xAt(i) - px);
      if (d < bestD) {
        bestD = d;
        best = i;
      }
    }
    setHover(best);
  };

  const onKey = (e: KeyboardEvent<HTMLDivElement>) => {
    if (e.key === 'ArrowLeft' || e.key === 'ArrowRight') {
      e.preventDefault();
      setHover((h) => {
        const cur = h ?? n - 1;
        const next = e.key === 'ArrowLeft' ? Math.max(0, cur - 1) : Math.min(n - 1, cur + 1);
        return next;
      });
    } else if (e.key === 'Escape') {
      setHover(null);
    }
  };

  const hoverPoint = hover !== null ? points[hover] : null;
  const hoverLeftPct = hover !== null ? (xAt(hover) / W) * 100 : 0;

  return (
    <div className="relative">
      <div
        tabIndex={0}
        role="img"
        aria-label={`State trajectory over the last ${Math.max(0, n - 1)} blocks. Use arrow keys to inspect values.`}
        onKeyDown={onKey}
        onBlur={() => setHover(null)}
        className="rounded-md focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-cyan-400"
      >
        <svg
          viewBox={`0 0 ${W} ${H}`}
          className="block h-auto w-full"
          onMouseMove={(e) => onMove(e.clientX, e.currentTarget)}
          onMouseLeave={() => setHover(null)}
          onTouchStart={(e) => onMove(e.touches[0].clientX, e.currentTarget)}
          onTouchMove={(e) => onMove(e.touches[0].clientX, e.currentTarget)}
        >
          {[0, 0.25, 0.5, 0.75, 1].map((g) => (
            <g key={g}>
              <line x1={padL} x2={W - padR} y1={yAt(g)} y2={yAt(g)} stroke="#27272a" strokeWidth={1} />
              <text x={padL - 6} y={yAt(g) + 3} textAnchor="end" fontSize={9} fontFamily="ui-monospace, monospace" fill="#71717a">
                {g.toFixed(2)}
              </text>
            </g>
          ))}
          {points.map((_, i) => (
            <text key={i} x={xAt(i)} y={H - 6} textAnchor="middle" fontSize={9} fontFamily="ui-monospace, monospace" fill="#71717a">
              k{firstIndex + i}
            </text>
          ))}
          {hover !== null && <line x1={xAt(hover)} x2={xAt(hover)} y1={padT} y2={H - padB} stroke="#52525b" strokeWidth={1} />}
          {visible.map((s) => {
            const d = points.map((p, i) => `${i === 0 ? 'M' : 'L'}${xAt(i).toFixed(1)},${yAt(p[s.key]).toFixed(1)}`).join(' ');
            const last = points[n - 1];
            return (
              <g key={s.key}>
                <path d={d} fill="none" stroke={s.hex} strokeWidth={2} strokeLinejoin="round" strokeLinecap="round" />
                <circle cx={xAt(n - 1)} cy={yAt(last[s.key])} r={4} fill={s.hex} stroke="#09090b" strokeWidth={2} />
                {hover !== null && <circle cx={xAt(hover)} cy={yAt(points[hover][s.key])} r={3} fill={s.hex} stroke="#09090b" strokeWidth={1.5} />}
              </g>
            );
          })}
          {endLabels.map((l) => (
            <text key={l.key} x={W - padR + 8} y={l.y + 3} fontSize={10} fontFamily="ui-monospace, monospace" fill="#d4d4d8">
              {l.text}
            </text>
          ))}
        </svg>
      </div>
      {hoverPoint && hover !== null && (
        <div
          className="pointer-events-none absolute top-1 z-10 rounded-md border border-zinc-700 bg-zinc-900/95 px-2 py-1.5 font-mono text-[10.5px] text-zinc-200 shadow-lg"
          style={{ left: `${Math.min(78, Math.max(2, hoverLeftPct))}%` }}
        >
          <div className="mb-1 text-zinc-400">block k{firstIndex + hover}</div>
          {visible.map((s) => (
            <div key={s.key} className="flex items-center gap-1.5">
              <span className="inline-block h-2 w-2 rounded-full" style={{ backgroundColor: s.hex }} />
              <span className="w-12 text-zinc-400">{s.symbol}</span>
              <span>{fmt(hoverPoint[s.key], 3)}</span>
            </div>
          ))}
        </div>
      )}
      <div className="mt-2 flex flex-wrap gap-x-3 gap-y-1" role="group" aria-label="Series legend">
        {SERIES.map((s) => {
          const off = hidden.has(s.key);
          return (
            <button
              key={s.key}
              type="button"
              aria-pressed={!off}
              onClick={() => onToggle(s.key)}
              className={`flex items-center gap-1.5 rounded px-1 font-mono text-[11px] focus-visible:outline-2 focus-visible:outline-cyan-400 ${off ? 'text-zinc-600 line-through' : 'text-zinc-300'}`}
            >
              <span className="inline-block h-2 w-3 rounded-sm" style={{ backgroundColor: s.hex, opacity: off ? 0.3 : 1 }} />
              {s.symbol}
            </button>
          );
        })}
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Segmented radio group
// ---------------------------------------------------------------------------

function Segmented<K extends string>({
  legend,
  symbol,
  options,
  value,
  onChange,
  columns = 2,
  dimmed = false,
  note,
  tag,
  plain = false,
  showParams = true,
}: {
  legend: string;
  symbol: string;
  options: readonly Option<K>[];
  value: K;
  onChange: (key: K) => void;
  columns?: 2 | 3 | 4;
  dimmed?: boolean;
  note?: string;
  tag?: (key: K) => ReactNode;
  /** Everyday wording for option labels. */
  plain?: boolean;
  /** Show the mono parameter line under each option. */
  showParams?: boolean;
}) {
  const onKey = (e: KeyboardEvent<HTMLDivElement>) => {
    const idx = options.findIndex((o) => o.key === value);
    let next: number;
    if (e.key === 'ArrowRight' || e.key === 'ArrowDown') next = (idx + 1) % options.length;
    else if (e.key === 'ArrowLeft' || e.key === 'ArrowUp') next = (idx - 1 + options.length) % options.length;
    else return;
    e.preventDefault();
    onChange(options[next].key);
    const buttons = e.currentTarget.querySelectorAll<HTMLButtonElement>('button[role="radio"]');
    buttons[next]?.focus();
  };
  return (
    <fieldset className={`min-w-0 transition-opacity ${dimmed ? 'opacity-45' : ''}`}>
      <legend className="mb-1.5 flex w-full items-baseline justify-between gap-2">
        <span className="text-[11px] font-semibold uppercase tracking-[0.14em] text-zinc-400">{legend}</span>
        {symbol && <span className="font-mono text-[10.5px] text-zinc-500">{symbol}</span>}
      </legend>
      <div role="radiogroup" aria-label={legend} onKeyDown={onKey} className={`grid gap-1.5 ${columns === 4 ? 'grid-cols-4' : columns === 3 ? 'grid-cols-3' : 'grid-cols-2'}`}>
        {options.map((o) => {
          const selected = o.key === value;
          return (
            <button
              key={o.key}
              type="button"
              role="radio"
              aria-checked={selected}
              tabIndex={selected ? 0 : -1}
              onClick={() => onChange(o.key)}
              title={o.detail}
              className={`min-w-0 rounded-md border px-2 py-1.5 text-left transition-colors focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-cyan-400 ${
                selected ? 'border-zinc-400 bg-zinc-800 text-zinc-50' : 'border-zinc-800 bg-zinc-900/50 text-zinc-400 hover:border-zinc-600 hover:text-zinc-200'
              }`}
            >
              <span className="block truncate text-[12px] font-medium leading-tight">{plain ? (o.plain ?? o.label) : o.label}</span>
              {(showParams || tag) && (
                <span className="mt-0.5 flex items-center justify-between gap-1">
                  {showParams ? <span className="truncate font-mono text-[10px] text-zinc-500">{o.params}</span> : <span />}
                  {tag?.(o.key)}
                </span>
              )}
            </button>
          );
        })}
      </div>
      {note && <p className="mt-1 text-[10.5px] text-zinc-500">{note}</p>}
    </fieldset>
  );
}

// ---------------------------------------------------------------------------
// Modal shell
// ---------------------------------------------------------------------------

function Panel({ title, onClose, children, footer, wide = false }: { title: string; onClose: () => void; children: ReactNode; footer?: ReactNode; wide?: boolean }) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const onKey = (e: globalThis.KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKey);
    ref.current?.querySelector<HTMLElement>('input, button')?.focus();
    return () => document.removeEventListener('keydown', onKey);
  }, [onClose]);
  return (
    <div className="fixed inset-0 z-[80] flex items-end justify-center bg-black/70 p-0 sm:items-center sm:p-4" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div
        ref={ref}
        role="dialog"
        aria-modal="true"
        aria-label={title}
        className={`flex max-h-[92vh] w-full flex-col overflow-hidden rounded-t-xl border border-zinc-700 bg-zinc-950 text-zinc-200 shadow-2xl sm:rounded-xl ${wide ? 'sm:max-w-3xl' : 'sm:max-w-xl'}`}
      >
        <div className="flex items-center justify-between border-b border-zinc-800 px-4 py-3">
          <h3 className="text-[12px] font-semibold uppercase tracking-[0.16em] text-zinc-300">{title}</h3>
          <button type="button" onClick={onClose} aria-label="Close" className="rounded p-1 text-zinc-400 hover:bg-zinc-800 hover:text-zinc-100 focus-visible:outline-2 focus-visible:outline-cyan-400">
            <X className="h-4 w-4" aria-hidden="true" />
          </button>
        </div>
        <div className="overflow-y-auto px-4 py-4">{children}</div>
        {footer && <div className="flex flex-wrap items-center justify-end gap-2 border-t border-zinc-800 px-4 py-3">{footer}</div>}
      </div>
    </div>
  );
}

function PrimaryButton({ children, onClick, tone = 'default', disabled }: { children: ReactNode; onClick: () => void; tone?: 'default' | 'danger'; disabled?: boolean }) {
  const cls =
    tone === 'danger'
      ? 'border-red-500/60 bg-red-500/15 text-red-200 hover:bg-red-500/25'
      : 'border-cyan-400/60 bg-cyan-400/15 text-cyan-100 hover:bg-cyan-400/25';
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className={`rounded-md border px-3 py-1.5 text-xs font-semibold uppercase tracking-[0.12em] transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-cyan-400 disabled:cursor-not-allowed disabled:opacity-40 ${cls}`}
    >
      {children}
    </button>
  );
}

function GhostButton({ children, onClick, disabled }: { children: ReactNode; onClick: () => void; disabled?: boolean }) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className="rounded-md border border-zinc-700 px-3 py-1.5 text-xs font-medium text-zinc-300 transition-colors hover:bg-zinc-800 hover:text-zinc-100 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-cyan-400 disabled:cursor-not-allowed disabled:opacity-40"
    >
      {children}
    </button>
  );
}

// ---------------------------------------------------------------------------
// Override modal
// ---------------------------------------------------------------------------

function OverrideModal({
  x,
  hoursAwake,
  k,
  onApply,
  onClear,
  onClose,
}: {
  x: StateVector;
  hoursAwake: number;
  k: Constants;
  onApply: (x: StateVector, hoursAwake: number) => void;
  onClear: () => void;
  onClose: () => void;
}) {
  const [draft, setDraft] = useState<StateVector>(x);
  const [hours, setHours] = useState(hoursAwake);
  const [gain, setGain] = useState(k.kalmanGain);
  const [confirmClear, setConfirmClear] = useState(false);
  const blended = useMemo(() => blendCalibration(x, draft, gain), [x, draft, gain]);
  const preview = useMemo(() => {
    const d = diagnose(blended, hours, 0.4, k);
    return route(blended, d, k);
  }, [blended, hours, k]);
  const set = (key: StateKey, v: number) => setDraft((d) => ({ ...d, [key]: Math.min(1, Math.max(0, Number.isFinite(v) ? v : 0)) }));
  return (
    <Panel
      title="Manual Override · Calibrate State Vector"
      onClose={onClose}
      footer={
        <>
          {confirmClear ? (
            <>
              <span className="mr-auto text-[11px] text-red-300">Erases every block, calibration, and constant override in this browser.</span>
              <GhostButton onClick={() => setConfirmClear(false)}>Keep Data</GhostButton>
              <PrimaryButton tone="danger" onClick={onClear}>
                Confirm Clear
              </PrimaryButton>
            </>
          ) : (
            <>
              <button type="button" onClick={() => setConfirmClear(true)} className="mr-auto inline-flex items-center gap-1.5 text-[11px] text-zinc-500 hover:text-red-300 focus-visible:outline-2 focus-visible:outline-cyan-400">
                <Trash2 className="h-3.5 w-3.5" aria-hidden="true" /> Clear All Data
              </button>
              <GhostButton
                onClick={() => {
                  setDraft({ ...DEFAULT_STATE });
                  setHours(0);
                }}
              >
                Restore Defaults
              </GhostButton>
              <PrimaryButton onClick={() => onApply(blended, hours)}>Apply Calibration</PrimaryButton>
            </>
          )}
        </>
      }
    >
      <div className="grid gap-3">
        {SERIES.map((s) => (
          <label key={s.key} className="grid grid-cols-[5rem_1fr_4.5rem] items-center gap-3">
            <span className="min-w-0">
              <span className={`block font-mono text-sm font-semibold ${s.text}`}>{s.symbol}</span>
              <span className="block truncate text-[10px] uppercase tracking-wider text-zinc-500">{s.label}</span>
            </span>
            <input
              id={`cc-override-${s.key}`}
              type="range"
              min={0}
              max={1}
              step={0.01}
              value={draft[s.key]}
              onChange={(e) => set(s.key, Number(e.target.value))}
              className="w-full accent-cyan-400"
              aria-label={`${s.label} slider`}
            />
            <input
              id={`cc-override-${s.key}-num`}
              type="number"
              min={0}
              max={1}
              step={0.01}
              value={draft[s.key]}
              onChange={(e) => set(s.key, Number(e.target.value))}
              className="w-full rounded border border-zinc-700 bg-zinc-900 px-2 py-1 font-mono text-xs text-zinc-100 focus-visible:outline-2 focus-visible:outline-cyan-400"
              aria-label={`${s.label} value`}
            />
          </label>
        ))}
        <label className="grid grid-cols-[5rem_1fr_4.5rem] items-center gap-3 border-t border-zinc-800 pt-3">
          <span className="min-w-0">
            <span className="block font-mono text-sm font-semibold text-zinc-200">t_awake</span>
            <span className="block truncate text-[10px] uppercase tracking-wider text-zinc-500">Hours awake</span>
          </span>
          <input id="cc-override-hours" type="range" min={0} max={30} step={0.25} value={hours} onChange={(e) => setHours(Number(e.target.value))} className="w-full accent-cyan-400" aria-label="Hours awake slider" />
          <input
            id="cc-override-hours-num"
            type="number"
            min={0}
            max={48}
            step={0.25}
            value={hours}
            onChange={(e) => setHours(Math.max(0, Number(e.target.value) || 0))}
            className="w-full rounded border border-zinc-700 bg-zinc-900 px-2 py-1 font-mono text-xs text-zinc-100 focus-visible:outline-2 focus-visible:outline-cyan-400"
            aria-label="Hours awake value"
          />
        </label>
        <label className="grid grid-cols-[5rem_1fr_4.5rem] items-center gap-3 border-t border-zinc-800 pt-3">
          <span className="min-w-0">
            <span className="block font-mono text-sm font-semibold text-zinc-200">K_filter</span>
            <span className="block truncate text-[10px] uppercase tracking-wider text-zinc-500">Trust in self-report</span>
          </span>
          <input id="cc-override-gain" type="range" min={0} max={1} step={0.05} value={gain} onChange={(e) => setGain(Number(e.target.value))} className="w-full accent-cyan-400" aria-label="Trust in self-report slider" />
          <input
            id="cc-override-gain-num"
            type="number"
            min={0}
            max={1}
            step={0.05}
            value={gain}
            onChange={(e) => setGain(Math.min(1, Math.max(0, Number(e.target.value) || 0)))}
            className="w-full rounded border border-zinc-700 bg-zinc-900 px-2 py-1 font-mono text-xs text-zinc-100 focus-visible:outline-2 focus-visible:outline-cyan-400"
            aria-label="Trust in self-report value"
          />
        </label>
        <p className="text-[11px] leading-snug text-zinc-500">
          Objective Impartiality: your report is blended into the model's estimate at this gain rather than copied, so a tired-day rationalisation or a hyper-focus high does not overwrite
          the audit. Set it to 1 to copy your values exactly.
        </p>
        <div className="grid grid-cols-3 gap-1.5 sm:grid-cols-6" aria-label="Blended calibration">
          {SERIES.map((s) => (
            <div key={s.key} className="rounded border border-zinc-800 bg-zinc-900/60 px-2 py-1.5">
              <div className={`font-mono text-[10.5px] ${s.text}`}>{s.symbol}</div>
              <div className="font-mono text-xs text-zinc-200">
                {fmt(x[s.key])} → {fmt(blended[s.key])}
              </div>
            </div>
          ))}
        </div>
        <div className="rounded-md border border-zinc-800 bg-zinc-900/60 px-3 py-2 font-mono text-[11px] text-zinc-400">
          Routes to <span className={`rounded border px-1.5 py-0.5 ${QUADRANT_TONE[preview.quadrant]}`}>{preview.quadrant}</span> {preview.title} · {preview.trigger}
        </div>
      </div>
    </Panel>
  );
}

// ---------------------------------------------------------------------------
// Constants modal
// ---------------------------------------------------------------------------

function ConstantsModal({ constants, onChange, onReset, onClose }: { constants: Constants; onChange: (c: Constants) => void; onReset: () => void; onClose: () => void }) {
  const group = (g: 'spec' | 'closure' | 'guardrail') => CONSTANT_META.filter((m) => m.group === g);
  const field = (m: (typeof CONSTANT_META)[number]) => (
    <label key={m.key} className="grid grid-cols-[4.5rem_1fr_5rem] items-center gap-2 text-[11px]">
      <span className="font-mono text-zinc-200">{m.symbol}</span>
      <span className="truncate text-zinc-500">{m.label}</span>
      <input
        id={`cc-const-${m.key}`}
        type="number"
        min={m.min}
        max={m.max}
        step={m.step}
        value={constants[m.key]}
        onChange={(e) => {
          const v = Number(e.target.value);
          if (!Number.isFinite(v)) return;
          onChange({ ...constants, [m.key]: Math.min(m.max, Math.max(m.min, v)) });
        }}
        className="w-full rounded border border-zinc-700 bg-zinc-900 px-2 py-1 font-mono text-xs text-zinc-100 focus-visible:outline-2 focus-visible:outline-cyan-400"
      />
    </label>
  );
  return (
    <Panel
      title="System Constants"
      onClose={onClose}
      wide
      footer={
        <>
          <GhostButton onClick={onReset}>Restore Spec Defaults</GhostButton>
          <PrimaryButton onClick={onClose}>Done</PrimaryButton>
        </>
      }
    >
      <div className="grid gap-5 md:grid-cols-2">
        <div className="grid content-start gap-2">
          <h4 className="text-[10px] font-semibold uppercase tracking-[0.16em] text-zinc-400">Governing constants (§C.8)</h4>
          {group('spec').map(field)}
        </div>
        <div className="grid content-start gap-2">
          <h4 className="text-[10px] font-semibold uppercase tracking-[0.16em] text-zinc-400">Closure constants</h4>
          <p className="text-[10.5px] leading-snug text-zinc-500">
            Terms the governing equations reference without pinning: postural cost of output, relaxation gains for A and V, passive recovery toward E_cap = 1 − ψ(t), the tonic arousal
            floor, and the circadian drag schedule ψ(t) = ψ₀·exp((t_awake − t_onset)/τ_ψ). Energy closes as dE/dt = Φ_in + Y_out(1 − E) − K_out + R_rest − c_basal − ψ(t)·O₁, with
            Φ_out = Y_out − K_out reported exactly as specified.
          </p>
          {group('closure').map(field)}
          <h4 className="mt-3 text-[10px] font-semibold uppercase tracking-[0.16em] text-zinc-400">High-capacity guardrails (§4)</h4>
          {group('guardrail').map(field)}
        </div>
      </div>
    </Panel>
  );
}

// ---------------------------------------------------------------------------
// Sleep modal
// ---------------------------------------------------------------------------

function SleepModal({ x, initialHours, onConfirm, onClose }: { x: StateVector; initialHours: number; onConfirm: (hours: number) => void; onClose: () => void }) {
  const [hours, setHours] = useState(initialHours);
  const after = useMemo(() => applySleepReset(x, hours), [x, hours]);
  return (
    <Panel
      title="Sleep Architecture Reset"
      onClose={onClose}
      footer={
        <>
          <GhostButton onClick={onClose}>Cancel</GhostButton>
          <PrimaryButton onClick={() => onConfirm(hours)}>Log Sleep Reset</PrimaryButton>
        </>
      }
    >
      <p className="mb-3 text-[12px] leading-relaxed text-zinc-400">
        Terminates the session clock (t_awake → 0, ψ → ψ₀) and applies the sleep transform: E recovers with a 3 h time constant, backlog decays at 0.25 / h, somatic strain clears with a 1.5 h
        time constant, arousal tone resets to 0.30.
      </p>
      <div role="radiogroup" aria-label="Sleep duration" className="grid grid-cols-4 gap-1.5">
        {SLEEP_OPTIONS.map((o) => (
          <button
            key={o.hours}
            type="button"
            role="radio"
            aria-checked={hours === o.hours}
            onClick={() => setHours(o.hours)}
            className={`rounded-md border px-2 py-2 text-left focus-visible:outline-2 focus-visible:outline-cyan-400 ${hours === o.hours ? 'border-zinc-400 bg-zinc-800 text-zinc-50' : 'border-zinc-800 bg-zinc-900/50 text-zinc-400 hover:border-zinc-600'}`}
          >
            <span className="block font-mono text-sm font-semibold">{o.label}</span>
            <span className="block text-[10px] text-zinc-500">{o.detail}</span>
          </button>
        ))}
      </div>
      <div className="mt-3 grid grid-cols-3 gap-1.5 sm:grid-cols-6">
        {SERIES.map((s) => (
          <div key={s.key} className="rounded border border-zinc-800 bg-zinc-900/60 px-2 py-1.5">
            <div className={`font-mono text-[10.5px] ${s.text}`}>{s.symbol}</div>
            <div className="font-mono text-xs text-zinc-200">
              {fmt(x[s.key])} → {fmt(after[s.key])}
            </div>
          </div>
        ))}
      </div>
    </Panel>
  );
}

// ---------------------------------------------------------------------------
// Prescription card
// ---------------------------------------------------------------------------

function PrescriptionCard({ p, x, k, onArm, onSleep }: { p: Prescription; x: StateVector; k: Constants; onArm: (spec: BlockSpec) => void; onSleep: (hours: number) => void }) {
  const Icon = KIND_ICON[p.kind];
  return (
    <div className={`rounded-lg border p-3 ${p.admissible ? 'border-zinc-800 bg-zinc-900/60' : 'border-rose-500/40 bg-rose-500/5'}`}>
      <div className="flex items-start justify-between gap-3">
        <div className="flex min-w-0 items-start gap-2">
          <Icon className="mt-0.5 h-4 w-4 shrink-0 text-zinc-400" aria-hidden="true" />
          <div className="min-w-0">
            <div className="text-[13px] font-semibold leading-tight text-zinc-100">{p.name}</div>
            <div className="mt-1 text-[11.5px] leading-snug text-zinc-400">{p.rationale}</div>
          </div>
        </div>
        <div className="shrink-0 text-right">
          <div className="font-mono text-xl font-semibold leading-none text-zinc-100">{p.kind === 'sleep' ? `${p.sleepHours ?? 7.5} h` : `${p.boundMinutes} m`}</div>
          <div className="mt-0.5 text-[9.5px] uppercase tracking-[0.14em] text-zinc-500">{p.kind === 'sleep' ? 'sleep' : 'hard boundary'}</div>
        </div>
      </div>
      {p.spec && <div className="mt-2 font-mono text-[10.5px] leading-snug text-zinc-500">{describeSpec(p.spec)}</div>}
      <div className="mt-2 text-[10.5px] leading-snug text-zinc-500">{p.stopRule}</div>
      {p.predicted && (
        <div className="mt-2 flex flex-wrap gap-1.5" aria-label="Predicted state at the boundary">
          {STATE_KEYS.map((key) => {
            const s = SERIES_BY_KEY[key];
            const d = p.predicted![key] - x[key];
            return (
              <span key={key} className="inline-flex items-center gap-1 rounded border border-zinc-800 bg-zinc-950 px-1.5 py-0.5 font-mono text-[10.5px]">
                <span className={s.text}>{s.symbol}</span>
                <span className="text-zinc-300">{fmt(p.predicted![key])}</span>
                <span className={deltaTone(key, d, x[key], k)}>{fmtSigned(d)}</span>
              </span>
            );
          })}
        </div>
      )}
      <div className="mt-3 flex justify-end">
        {p.kind === 'sleep' ? (
          <PrimaryButton onClick={() => onSleep(p.sleepHours ?? 7.5)}>Log Sleep Reset</PrimaryButton>
        ) : (
          <GhostButton onClick={() => p.spec && onArm(p.spec)}>Arm This Block</GhostButton>
        )}
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Duration control (standard cadences + custom minutes)
// ---------------------------------------------------------------------------

function DurationControl({ spec, onChange, legend, symbol, showParams = true }: { spec: BlockSpec; onChange: (patch: Partial<BlockSpec>) => void; legend: string; symbol: string; showParams?: boolean }) {
  const custom = spec.cadence === CUSTOM_CADENCE;
  const minutes = spec.customMinutes ?? DEFAULT_BLOCK_LENGTH;
  return (
    <div className="grid gap-1.5">
      <Segmented legend={legend} symbol={symbol} options={CADENCES} value={custom ? ('' as never) : spec.cadence} onChange={(v) => onChange({ cadence: v })} columns={3} showParams={showParams} />
      <div className="flex items-center gap-2">
        <button
          type="button"
          role="radio"
          aria-checked={custom}
          onClick={() => onChange({ cadence: CUSTOM_CADENCE, customMinutes: minutes })}
          className={`rounded-md border px-2 py-1.5 text-[12px] font-medium focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-cyan-400 ${
            custom ? 'border-zinc-400 bg-zinc-800 text-zinc-50' : 'border-zinc-800 bg-zinc-900/50 text-zinc-400 hover:border-zinc-600 hover:text-zinc-200'
          }`}
        >
          Custom
        </button>
        <input
          id="cc-custom-minutes"
          type="number"
          min={MIN_CUSTOM_MINUTES}
          max={MAX_CUSTOM_MINUTES}
          step={1}
          value={minutes}
          onChange={(e) => {
            const v = Math.round(Number(e.target.value));
            if (!Number.isFinite(v)) return;
            onChange({ cadence: CUSTOM_CADENCE, customMinutes: Math.min(MAX_CUSTOM_MINUTES, Math.max(MIN_CUSTOM_MINUTES, v)) });
          }}
          aria-label="Custom duration in minutes"
          className="w-20 rounded border border-zinc-700 bg-zinc-900 px-2 py-1 font-mono text-xs text-zinc-100 focus-visible:outline-2 focus-visible:outline-cyan-400"
        />
        <span className="text-[11px] text-zinc-500">
          minutes ({MIN_CUSTOM_MINUTES}–{MAX_CUSTOM_MINUTES})
        </span>
      </div>
    </div>
  );
}

const FIELD_LABEL: Record<DescribedField, string> = {
  modality: 'Activity',
  anchor: 'Background',
  valuation: 'Kind of thing',
  density: 'Density',
  context: 'Pressure',
  scratchpad: 'Tangents',
  novelty: 'Novelty',
  somatic: 'Body',
  intensity: 'Intensity',
  duration: 'Length',
  speed: 'Speed',
};

function plainChoice(spec: BlockSpec, field: DescribedField): string {
  const find = <K extends string>(list: readonly Option<K>[], key: K) => {
    const o = list.find((x) => x.key === key);
    return o ? (o.plain ?? o.label) : key;
  };
  switch (field) {
    case 'modality':
      return find(MODALITIES, spec.modality);
    case 'anchor':
      return find(ANCHORS, spec.anchor);
    case 'valuation':
      return find(VALUATIONS, spec.valuation);
    case 'density':
      return find(DENSITIES, spec.density);
    case 'context':
      return find(CONTEXTS, spec.context);
    case 'scratchpad':
      return find(SCRATCHPADS, spec.scratchpad);
    case 'novelty':
      return find(NOVELTIES, spec.novelty);
    case 'somatic':
      return find(SOMATICS, spec.somatic);
    case 'intensity':
      return find(INTENSITIES, spec.intensity ?? 'standard');
    case 'speed':
      return find(PLAYBACK_SPEEDS, spec.speed ?? 'x1');
    case 'duration':
      return `${blockMinutes(spec)} min`;
    default:
      return '';
  }
}

function DescribeBox({ defaultMinutes, defaultSpeed, onDescribed }: { defaultMinutes: number; defaultSpeed: SpeedKey; onDescribed: (text: string, result: DescribedBlock) => void }) {
  const [text, setText] = useState('');
  return (
    <form
      className="grid gap-2"
      onSubmit={(e) => {
        e.preventDefault();
        const trimmed = text.trim();
        if (!trimmed) return;
        onDescribed(trimmed, describeBlock(trimmed, defaultMinutes, defaultSpeed));
      }}
    >
      <label htmlFor="cc-describe" className="text-[11px] font-semibold uppercase tracking-[0.14em] text-zinc-400">
        Describe it in your own words
      </label>
      <div className="flex flex-wrap gap-2">
        <input
          id="cc-describe"
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder={`e.g. "read a novel on the couch", "audiobook at 1.5x on a walk", "emails at my desk, got distracted"`}
          maxLength={240}
          className="min-w-0 flex-1 rounded-md border border-zinc-700 bg-zinc-900 px-3 py-2 text-[13px] text-zinc-100 placeholder:text-zinc-600 focus-visible:outline-2 focus-visible:outline-cyan-400"
        />
        <button
          type="submit"
          disabled={!text.trim()}
          className="inline-flex items-center gap-1.5 rounded-md border border-cyan-400/60 bg-cyan-400/15 px-3 py-2 text-xs font-semibold uppercase tracking-[0.12em] text-cyan-100 transition-colors hover:bg-cyan-400/25 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-cyan-400 disabled:cursor-not-allowed disabled:opacity-40"
        >
          <Sparkles className="h-3.5 w-3.5" aria-hidden="true" /> Read it
        </button>
      </div>
    </form>
  );
}

function PresetSaver({ onSave }: { onSave: (name: string) => void }) {
  const [open, setOpen] = useState(false);
  const [name, setName] = useState('');
  if (!open)
    return (
      <button type="button" onClick={() => setOpen(true)} className="inline-flex items-center gap-1.5 justify-self-start text-[11.5px] text-zinc-400 hover:text-zinc-200 focus-visible:outline-2 focus-visible:outline-cyan-400">
        <Save className="h-3.5 w-3.5" aria-hidden="true" /> Save this block as a preset
      </button>
    );
  return (
    <form
      className="flex flex-wrap items-center gap-2"
      onSubmit={(e) => {
        e.preventDefault();
        const trimmed = name.trim();
        if (!trimmed) return;
        onSave(trimmed);
        setName('');
        setOpen(false);
      }}
    >
      <input
        id="cc-preset-name"
        value={name}
        onChange={(e) => setName(e.target.value)}
        placeholder="Preset name"
        maxLength={60}
        aria-label="Preset name"
        className="min-w-0 flex-1 rounded border border-zinc-700 bg-zinc-900 px-2 py-1 text-xs text-zinc-100 focus-visible:outline-2 focus-visible:outline-cyan-400"
      />
      <button
        type="submit"
        disabled={!name.trim()}
        className="rounded-md border border-cyan-400/60 bg-cyan-400/15 px-3 py-1.5 text-xs font-semibold uppercase tracking-[0.12em] text-cyan-100 transition-colors hover:bg-cyan-400/25 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-cyan-400 disabled:cursor-not-allowed disabled:opacity-40"
      >
        Save Preset
      </button>
      <GhostButton onClick={() => setOpen(false)}>Cancel</GhostButton>
    </form>
  );
}

// ---------------------------------------------------------------------------
// Graded catalog card
// ---------------------------------------------------------------------------

function CatalogCard({ g, x, k, onArm, onSleep, onDelete }: { g: GradedBlock; x: StateVector; k: Constants; onArm: (spec: BlockSpec) => void; onSleep: () => void; onDelete?: () => void }) {
  const Icon = KIND_ICON[g.entry.kind];
  const [open, setOpen] = useState(false);
  return (
    <div className="rounded-lg border border-zinc-800 bg-zinc-900/60 p-2.5" data-standing={g.comparison.standing}>
      <div className="flex items-start gap-2.5">
        <div className="min-w-0 flex-1">
          <div className="flex items-start justify-between gap-2">
            <div className="min-w-0">
              <div className="flex items-center gap-1.5 text-[12.5px] font-semibold leading-tight text-zinc-100">
                <Icon className="h-3.5 w-3.5 shrink-0 text-zinc-500" aria-hidden="true" />
                <span className="truncate">{g.entry.name}</span>
                {g.entry.presetId && onDelete && (
                  <button type="button" onClick={onDelete} aria-label={`Delete preset ${g.entry.name}`} className="rounded p-0.5 text-zinc-500 hover:text-rose-300 focus-visible:outline-2 focus-visible:outline-cyan-400">
                    <Trash2 className="h-3.5 w-3.5" aria-hidden="true" />
                  </button>
                )}
              </div>
              <div className="mt-0.5 truncate text-[10.5px] text-zinc-500">{g.entry.detail}</div>
            </div>
            <div className="flex shrink-0 flex-col items-end gap-1">
              <StandingPill standing={g.comparison.standing} size="sm" />
              <div className="text-right font-mono">
                <span className="text-sm font-semibold text-zinc-100">{g.score}</span>
                {g.comparison.margin !== 0 && <span className="ml-1 text-[10px] text-zinc-500">({fmtSigned(g.comparison.margin, 0)})</span>}
                <div className="text-[9.5px] uppercase tracking-[0.12em] text-zinc-500">{g.entry.kind === 'sleep' ? 'sleep' : g.boundMinutes < 15 ? '<15 m' : `${g.boundMinutes} m`}</div>
              </div>
            </div>
          </div>
          <div className="mt-1 text-[11px] leading-snug text-zinc-300">{plainComparison(g)}</div>
          <div className="mt-1.5 flex flex-wrap items-center gap-x-2 gap-y-1 font-mono text-[10px] text-zinc-500">
            <span title="Routing fit for the current quadrant">fit {g.fit}</span>
            <span>·</span>
            <span title="Predicted change in state utility over the block">outcome {g.outcome}</span>
            <span>·</span>
            <span title="Share of the block that survives before a boundary trips">horizon {g.horizon}</span>
            {g.delta && (
              <>
                <span>·</span>
                {(['E', 'B'] as StateKey[]).map((key) => (
                  <span key={key} className={deltaTone(key, g.delta![key], x[key], k)}>
                    {SERIES_BY_KEY[key].symbol} {fmtSigned(g.delta![key])}
                  </span>
                ))}
                <span className={deltaTone('Fvis', compositeStrain(g.predicted!) - compositeStrain(x), compositeStrain(x), k)}>F {fmtSigned(compositeStrain(g.predicted!) - compositeStrain(x))}</span>
              </>
            )}
          </div>
          <GuardrailList warnings={g.guardrails} showMath compact />
          <div className="mt-1.5 flex items-center justify-between gap-2">
            <button type="button" onClick={() => setOpen((o) => !o)} aria-expanded={open} className="text-[10.5px] text-zinc-500 hover:text-zinc-300 focus-visible:outline-2 focus-visible:outline-cyan-400">
              {open ? 'hide detail' : 'detail'}
            </button>
            {g.entry.kind === 'sleep' ? (
              <GhostButton onClick={onSleep}>Log Sleep Reset</GhostButton>
            ) : (
              <GhostButton onClick={() => g.spec && onArm(g.spec)}>Arm</GhostButton>
            )}
          </div>
          {open && g.spec && (
            <div className="mt-1.5 grid gap-1 font-mono text-[10.5px] text-zinc-500">
              <div>{describeSpec(g.spec)}</div>
              <div>stop: {g.stopReason}</div>
              {g.predicted && (
                <div className="flex flex-wrap gap-1">
                  {STATE_KEYS.map((key) => (
                    <span key={key} className="rounded border border-zinc-800 px-1 py-0.5">
                      <span className={SERIES_BY_KEY[key].text}>{SERIES_BY_KEY[key].symbol}</span> {fmt(g.predicted![key])}
                    </span>
                  ))}
                  <span className="rounded border border-zinc-800 px-1 py-0.5">ΔU {fmtSigned(g.deltaUtility ?? 0, 3)}</span>
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Before-you-log preview: comparison with the best option + six meters now → after
// ---------------------------------------------------------------------------

function BlockPreview({ graded, x, minutes, thenHeadline, showMath, k }: { graded: GradedBlock; x: StateVector; minutes: number; thenHeadline: string; showMath: boolean; k: Constants }) {
  const after = graded.predicted ?? x;
  const c = graded.comparison;
  return (
    <div className="rounded-md border border-zinc-800 bg-zinc-950/60 px-3 py-2.5" aria-label="Before you log it">
      <div className="flex flex-wrap items-center justify-between gap-x-2 gap-y-1">
        <span className="text-[11px] font-semibold uppercase tracking-[0.14em] text-zinc-400">Before you log it · {minutes} min</span>
        <StandingPill standing={c.standing} />
      </div>
      <p className="mt-1 text-[12.5px] leading-snug text-zinc-100">{plainComparison(graded)}</p>
      <p className="mt-0.5 text-[11.5px] leading-snug text-zinc-400">{plainReason(graded, x)}</p>
      <GuardrailList warnings={graded.guardrails} showMath={showMath} />
      {showMath && (
        <p className="mt-0.5 font-mono text-[10px] text-zinc-500">
          score {graded.score} ({fmtSigned(c.margin, 0)} vs {c.against.name}) · fit {graded.fit} · outcome {graded.outcome} · horizon {graded.horizon} · stop: {graded.stopReason}
          {graded.deltaUtility !== null ? ` · ΔU ${fmtSigned(graded.deltaUtility, 3)}` : ''}
        </p>
      )}
      <div className="mt-2.5 grid grid-cols-2 gap-1.5 sm:grid-cols-3 md:grid-cols-6" aria-label="Effect on the meters">
        {SERIES.map((sm) => {
          const pl = PLAIN_BY_KEY[sm.key];
          const before = x[sm.key];
          const next = after[sm.key];
          const delta = next - before;
          const tone = deltaTone(sm.key, delta, before, k);
          const Arrow = Math.abs(delta) < 0.005 ? Minus : delta > 0 ? ArrowUpRight : ArrowDownRight;
          const lo = Math.min(before, next);
          const hi = Math.max(before, next);
          return (
            <div key={sm.key} className="rounded-md border border-zinc-800 bg-zinc-900/50 px-2 py-1.5" title={pl.meaning}>
              <div className="flex items-baseline justify-between gap-1">
                <span className="truncate text-[10.5px] font-medium text-zinc-400">
                  {pl.name}
                  {showMath && <span className={`ml-1 font-mono ${sm.text}`}>{sm.symbol}</span>}
                </span>
                <span className={`inline-flex items-center gap-0.5 font-mono text-[10px] ${tone}`}>
                  <Arrow className="h-3 w-3" aria-hidden="true" />
                  {fmtSigned(delta)}
                </span>
              </div>
              <div className="mt-0.5 font-mono text-[12px] text-zinc-100">
                {fmt(before)} <span className="text-zinc-500">→</span> {fmt(next)}
              </div>
              <div className="relative mt-1 h-1.5 w-full overflow-hidden rounded-full bg-zinc-800" role="img" aria-label={`${pl.name} ${fmt(before)} now, ${fmt(next)} after the block`}>
                <div className={`absolute inset-y-0 left-0 rounded-full ${sm.bg} opacity-40`} style={{ width: `${(before * 100).toFixed(1)}%` }} />
                <div className={`absolute inset-y-0 ${sm.bg}`} style={{ left: `${(lo * 100).toFixed(1)}%`, width: `${Math.max(1.5, (hi - lo) * 100).toFixed(1)}%` }} />
                <div className="absolute inset-y-0 w-0.5 bg-zinc-50" style={{ left: `calc(${(next * 100).toFixed(1)}% - 1px)` }} />
              </div>
            </div>
          );
        })}
      </div>
      <div className="mt-2 text-[12px] text-zinc-300">
        <span className="text-zinc-500">Then: </span>
        {thenHeadline}
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Seven pillars row
// ---------------------------------------------------------------------------

const PILLAR_TONE: Record<PillarVerdict['status'], string> = {
  pass: 'border-emerald-400/40 bg-emerald-400/10 text-emerald-200',
  flag: 'border-amber-400/50 bg-amber-400/10 text-amber-100',
  block: 'border-rose-500/60 bg-rose-500/10 text-rose-200',
  na: 'border-zinc-800 bg-zinc-900/40 text-zinc-500',
};

function PillarRow({ verdicts, showMath }: { verdicts: PillarVerdict[]; showMath: boolean }) {
  const [open, setOpen] = useState<PillarVerdict['id'] | null>(null);
  const { blocks, flags } = pillarSummary(verdicts);
  const active = verdicts.find((v) => v.id === open) ?? null;
  return (
    <div className="rounded-md border border-zinc-800 bg-zinc-950/60 px-2.5 py-2" aria-label="Seven pillars">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <span className="flex items-center gap-1.5 text-[11px] font-semibold uppercase tracking-[0.14em] text-zinc-400">
          <ShieldCheck className="h-3.5 w-3.5" aria-hidden="true" /> Seven pillars
        </span>
        <span className="text-[10.5px] text-zinc-500">{blocks > 0 ? `${blocks} blocking` : flags > 0 ? `${flags} to watch` : 'all clear'}</span>
      </div>
      <ul className="mt-1.5 flex flex-wrap gap-1.5">
        {verdicts.map((v) => (
          <li key={v.id}>
            <button
              type="button"
              aria-expanded={open === v.id}
              aria-label={`${v.name}: ${v.status === 'na' ? 'not applicable' : v.status}`}
              title={v.note}
              onClick={() => setOpen((o) => (o === v.id ? null : v.id))}
              className={`rounded border px-1.5 py-0.5 text-[10.5px] transition-colors focus-visible:outline-2 focus-visible:outline-cyan-400 ${PILLAR_TONE[v.status]} ${open === v.id ? 'ring-1 ring-cyan-400' : ''}`}
            >
              {v.status === 'block' ? '✕ ' : v.status === 'flag' ? '! ' : v.status === 'pass' ? '✓ ' : '– '}
              {v.name}
              {showMath && <span className="ml-1 font-mono opacity-70">{v.symbol}</span>}
            </button>
          </li>
        ))}
      </ul>
      {active && <p className="mt-1.5 text-[11.5px] leading-snug text-zinc-300">{active.note}</p>}
      {!active && verdicts.some((v) => v.status === 'block' || v.status === 'flag') && (
        <ul className="mt-1.5 grid gap-0.5">
          {verdicts
            .filter((v) => v.status === 'block' || v.status === 'flag')
            .map((v) => (
              <li key={v.id} className="text-[11px] leading-snug text-zinc-400">
                <span className={v.status === 'block' ? 'text-rose-300' : 'text-amber-300'}>{v.name}:</span> {v.note}
              </li>
            ))}
        </ul>
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// The full block form, bound to any spec (the armed block, or a logged block being edited)
// ---------------------------------------------------------------------------

function SpecForm({ spec, onPatch, plain, showMath }: { spec: BlockSpec; onPatch: (patch: Partial<BlockSpec>) => void; plain: boolean; showMath: boolean }) {
  const setSpec = onPatch;
  const zeroVector = spec.modality === 'zero';
  const u = resolveSpec(spec).u;
  const armedI1 = u.Ivis + u.Iaud;
  return (
    <>
          <DurationControl spec={spec} onChange={setSpec} legend={plain ? 'How long' : 'Cadence'} symbol={plain && !showMath ? '' : 'Δt'} showParams={!plain || showMath} />
          <Segmented legend="Intensity" symbol={plain && !showMath ? '' : '×(I, O₁)'} options={INTENSITIES} value={spec.intensity ?? 'standard'} onChange={(v) => setSpec({ intensity: v })} columns={3} plain={plain} showParams={!plain || showMath} />
          <Segmented
            legend={plain ? 'Playback speed' : 'Playback Speed'}
            symbol={plain && !showMath ? '' : '×I₁'}
            options={PLAYBACK_SPEEDS}
            value={spec.speed ?? 'x1'}
            onChange={(v) => setSpec({ speed: v })}
            columns={4}
            plain={plain}
            showParams={!plain || showMath}
            dimmed={!modalityIsPlayback(spec.modality)}
            note={!modalityIsPlayback(spec.modality) ? (plain ? 'Nothing is being played back in this block: speed has no effect.' : 'Not a playback modality: speed has no effect.') : plain ? 'Faster playback means more comes in per minute.' : undefined}
          />
          <Segmented legend={plain ? 'What you did' : 'Primary Modality Vector'} symbol={plain && !showMath ? '' : 'T₁'} options={MODALITIES} value={spec.modality} onChange={(v) => setSpec({ modality: v })} plain={plain} showParams={!plain || showMath} />
          <Segmented legend={plain ? 'Background anchor' : 'Secondary Anchor'} symbol={plain && !showMath ? '' : 'T₂'} options={ANCHORS} value={spec.anchor} onChange={(v) => setSpec({ anchor: v })} plain={plain} showParams={!plain || showMath} />
          <Segmented
            legend={plain ? 'How substantive' : 'Substantive Valuation'}
            symbol={plain && !showMath ? '' : 'V_target'}
            options={VALUATIONS}
            value={spec.valuation}
            onChange={(v) => setSpec({ valuation: v })}
        plain={plain}
        showParams={!plain || showMath}
            dimmed={zeroVector}
            note={zeroVector ? 'Zero-vector block: V holds its current value.' : undefined}
            tag={(key) => (key === 'churn' ? <span className="rounded bg-rose-500/20 px-1 text-[9px] font-semibold uppercase tracking-wider text-rose-300">inadmissible</span> : null)}
          />
          <Segmented legend={plain ? 'How dense' : 'Cognitive Density'} symbol={plain && !showMath ? '' : 'C_in'} options={DENSITIES} value={spec.density} onChange={(v) => setSpec({ density: v })} plain={plain} showParams={!plain || showMath} dimmed={zeroVector} note={armedI1 === 0 && !zeroVector ? (plain ? 'No intake in this block: density only affects the intake readout.' : 'I₁ = 0 for this modality: density only sets the I*(t) readout.') : undefined} />
          <Segmented legend={plain ? 'Pressure and control' : 'Operational Context'} symbol={plain && !showMath ? '' : 'P, S_agency'} options={CONTEXTS} value={spec.context} onChange={(v) => setSpec({ context: v })} plain={plain} showParams={!plain || showMath} dimmed={zeroVector} />
          <Segmented legend={plain ? 'Tangents' : 'ADHD Scratchpad Discipline'} symbol={plain && !showMath ? '' : 'γ_assoc, Ω_switch'} options={SCRATCHPADS} value={spec.scratchpad} onChange={(v) => setSpec({ scratchpad: v })} plain={plain} showParams={!plain || showMath} dimmed={zeroVector} />
          <Segmented legend={plain ? 'Novelty' : 'Novelty / Entropy Stimulation'} symbol={plain && !showMath ? '' : 'ξ_novelty'} options={NOVELTIES} value={spec.novelty} onChange={(v) => setSpec({ novelty: v })} columns={3} plain={plain} showParams={!plain || showMath} dimmed={zeroVector} />
          <Segmented legend={plain ? 'Body and posture' : 'Somatic & Biomechanical Marker'} symbol={plain && !showMath ? '' : '𝟙seat, 𝟙kin'} options={SOMATICS} value={spec.somatic} onChange={(v) => setSpec({ somatic: v })} plain={plain} showParams={!plain || showMath} />
    </>
  );
}

// ---------------------------------------------------------------------------
// Block log: every past entry, editable and deletable, with the trajectory replayed
// ---------------------------------------------------------------------------

function BlockLog({
  history,
  k,
  plain,
  showMath,
  limit,
  onEdit,
  onDelete,
}: {
  history: HistoryEntry[];
  k: Constants;
  plain: boolean;
  showMath: boolean;
  limit?: number;
  onEdit: (index: number, patch: { spec: BlockSpec; note?: string }) => void;
  onDelete: (index: number) => void;
}) {
  const [editing, setEditing] = useState<number | null>(null);
  const [draft, setDraft] = useState<BlockSpec | null>(null);
  const [note, setNote] = useState('');
  const [showAll, setShowAll] = useState(false);
  const rows = history.map((h, index) => ({ h, index })).reverse();
  const visible = showAll || !limit ? rows : rows.slice(0, limit);
  const begin = (index: number) => {
    const h = history[index];
    if (!h?.spec) return;
    setEditing(index);
    setDraft({ ...h.spec });
    setNote(h.note ?? '');
  };
  const cancel = () => {
    setEditing(null);
    setDraft(null);
  };
  const save = () => {
    if (editing === null || !draft) return;
    onEdit(editing, { spec: draft, note: note.trim() || undefined });
    cancel();
  };
  const remove = (index: number) => {
    cancel();
    onDelete(index);
  };
  if (history.length === 0) return <p className="text-[11.5px] text-zinc-500">Nothing logged yet.</p>;
  return (
    <div className="grid gap-1.5">
      <ul className="grid gap-1.5" aria-label="Logged blocks">
        {visible.map(({ h, index }) => {
          const dE = h.xAfter.E - h.xBefore.E;
          const dB = h.xAfter.B - h.xBefore.B;
          const dF = compositeStrain(h.xAfter) - compositeStrain(h.xBefore);
          const m = h.spec ? MODALITIES.find((o) => o.key === h.spec!.modality) : null;
          const title = h.kind === 'sleep' ? `Sleep reset · ${h.sleepHours ?? 7.5} h` : h.kind === 'override' ? 'Calibration' : `${h.dtMinutes} min · ${plain ? (m?.plain ?? m?.label ?? '') : (m?.label ?? '')}`;
          const time = new Date(h.at).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
          const isEditing = editing === index;
          const what = h.kind === 'block' ? `block ${h.k}` : h.kind === 'sleep' ? 'sleep reset' : 'calibration';
          return (
            <li key={`${h.at}-${index}`} className="rounded-md border border-zinc-800 bg-zinc-950/50 px-2.5 py-2">
              <div className="flex flex-wrap items-start justify-between gap-x-3 gap-y-1">
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-baseline gap-x-2 text-[12px] text-zinc-100">
                    <span className="font-mono text-[10.5px] text-zinc-500">{h.kind === 'block' ? `k${h.k}` : h.kind === 'sleep' ? 'sleep' : 'cal'}</span>
                    <span className="font-medium">{title}</span>
                    <span className="font-mono text-[10.5px] text-zinc-500">{time}</span>
                  </div>
                  {h.note && (
                    <div className="mt-0.5 truncate text-[11px] text-zinc-400" title={h.note}>
                      “{h.note}”
                    </div>
                  )}
                  {!plain && h.spec && <div className="mt-0.5 truncate font-mono text-[10px] text-zinc-500">{describeSpec(h.spec)}</div>}
                  <div className="mt-1 flex flex-wrap gap-x-2 gap-y-0.5 font-mono text-[10.5px]">
                    <span className={deltaTone('E', dE, h.xBefore.E, k)}>
                      {plain ? 'Energy' : 'E'} {fmtSigned(dE)}
                    </span>
                    <span className={deltaTone('B', dB, h.xBefore.B, k)}>
                      {plain ? 'Backlog' : 'B'} {fmtSigned(dB)}
                    </span>
                    <span className={deltaTone('Fvis', dF, compositeStrain(h.xBefore), k)}>
                      {plain ? 'Strain' : 'F'} {fmtSigned(dF)}
                    </span>
                    {showMath && <span className="text-zinc-500">→ {h.quadrant}</span>}
                  </div>
                </div>
                <div className="flex shrink-0 items-center gap-1.5">
                  {h.kind === 'block' && h.spec && !isEditing && <GhostButton onClick={() => begin(index)}>Edit</GhostButton>}
                  <button type="button" onClick={() => remove(index)} aria-label={`Delete ${what}`} className="rounded p-1 text-zinc-500 hover:text-rose-300 focus-visible:outline-2 focus-visible:outline-cyan-400">
                    <Trash2 className="h-3.5 w-3.5" aria-hidden="true" />
                  </button>
                </div>
              </div>
              {isEditing && draft && (
                <div role="group" aria-label={`Edit block ${h.k}`} className="mt-2 grid gap-3 border-t border-zinc-800 pt-2">
                  <label className="grid gap-1 text-[11px] font-semibold uppercase tracking-[0.14em] text-zinc-400">
                    Note
                    <input
                      value={note}
                      onChange={(e) => setNote(e.target.value)}
                      maxLength={240}
                      aria-label="Block note"
                      className="rounded-md border border-zinc-700 bg-zinc-900 px-2 py-1.5 text-[12.5px] font-normal normal-case tracking-normal text-zinc-100 focus-visible:outline-2 focus-visible:outline-cyan-400"
                    />
                  </label>
                  <SpecForm spec={draft} onPatch={(patch) => setDraft((d) => (d ? { ...d, ...patch } : d))} plain={plain} showMath={showMath} />
                  <div className="flex justify-end gap-2">
                    <GhostButton onClick={cancel}>Cancel</GhostButton>
                    <PrimaryButton onClick={save}>Save changes</PrimaryButton>
                  </div>
                </div>
              )}
            </li>
          );
        })}
      </ul>
      {limit && rows.length > limit && (
        <button type="button" onClick={() => setShowAll((o) => !o)} aria-expanded={showAll} className="text-[11.5px] text-zinc-400 hover:text-zinc-200 focus-visible:outline-2 focus-visible:outline-cyan-400">
          {showAll ? 'Show fewer' : `Show all ${rows.length}`}
        </button>
      )}
    </div>
  );
}

export function CapacityControllerScreen({ frameless = false }: { frameless?: boolean }) {
  const [store, setStore] = useState<PersistedState>(loadPersisted);
  const [modal, setModal] = useState<'override' | 'constants' | 'sleep' | null>(null);
  const [sleepHours, setSleepHours] = useState(7.5);
  const [notice, setNotice] = useState<string | null>(null);
  const [hidden, setHidden] = useState<ReadonlySet<StateKey>>(() => new Set());
  const [logOpen, setLogOpen] = useState(true);
  const [showFullForm, setShowFullForm] = useState(false);
  const [described, setDescribed] = useState<{ text: string; result: DescribedBlock } | null>(null);
  const [adjustField, setAdjustField] = useState<DescribedField | null>(null);
  const [adjusted, setAdjusted] = useState<ReadonlySet<DescribedField>>(() => new Set());
  const formRef = useRef<HTMLDivElement>(null);

  const { x, hoursAwake, constants: k, spec, history, blockIndex, backlogLatch, presets, showMath, blockLength, listeningSpeed, suggested } = store;

  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, encodePersisted(store));
    } catch {
      // Storage unavailable (private mode, quota) — the session still works in memory.
    }
  }, [store]);

  useEffect(() => {
    if (!notice) return;
    const t = window.setTimeout(() => setNotice(null), 4500);
    return () => window.clearTimeout(t);
  }, [notice]);

  const inputs = useMemo(() => resolveSpec(spec), [spec]);
  const dt = blockMinutes(spec);
  const diag: Diagnostics = useMemo(() => diagnose(x, hoursAwake, inputs.theta.Cin, k, backlogLatch), [x, hoursAwake, inputs.theta.Cin, k, backlogLatch]);
  const routing = useMemo(() => route(x, diag, k), [x, diag, k]);
  const prescriptions = useMemo(() => prescribe(x, hoursAwake, diag, routing, k, blockLength), [x, hoursAwake, diag, routing, k, blockLength]);
  const preview = useMemo(() => integrateBlock(x, hoursAwake, inputs, dt, k), [x, hoursAwake, inputs, dt, k]);
  const previewRouting = useMemo(
    () => route(preview.x, diagnose(preview.x, preview.hoursAwake, inputs.theta.Cin, k, nextBacklogLatch(backlogLatch, preview.x, inputs, k)), k),
    [preview, inputs, k, backlogLatch],
  );
  const report = useMemo(() => (lastDeltaEntry(history) ? buildMarkdownReport(lastDeltaEntry(history)!, diag, routing, prescriptions, k) : null), [history, diag, routing, prescriptions, k]);
  const catalog = useMemo(() => gradeCatalog(x, hoursAwake, diag, routing, k, presets, blockLength, listeningSpeed), [x, hoursAwake, diag, routing, k, presets, blockLength, listeningSpeed]);
  const plainQ = plainQuadrant(routing);
  const plainR = plainRegime(diag);
  const recommended: GradedBlock | null = catalog[0] ?? null;
  const armedGrade = useMemo(() => gradeBlock(spec, x, hoursAwake, diag, routing, k, 'Your block', catalog[0]), [spec, x, hoursAwake, diag, routing, k, catalog]);
  const pillars = useMemo(
    () => evaluatePillars(spec, x, diag, k, { relational: described?.result.relational, relationalCue: described?.result.relationalCue, predicted: preview.x }),
    [spec, x, diag, k, described, preview.x],
  );

  const lastBlock = useMemo(() => [...history].reverse().find((h) => h.kind === 'block') ?? null, [history]);
  const lastEntry = history.length ? history[history.length - 1] : null;
  const lastDelta = lastEntry && lastEntry === lastBlock ? lastBlock : null;

  const trajectory = useMemo(() => {
    const blocks = history.slice(-10);
    if (blocks.length === 0) return { points: [x], firstIndex: blockIndex };
    const pts = [blocks[0].xBefore, ...blocks.map((h) => h.xAfter)];
    // The trajectory should end at the live state (an override after the last block moves it).
    pts[pts.length - 1] = x;
    const firstK = blocks[0].kind === 'block' ? blocks[0].k - 1 : blocks[0].k;
    return { points: pts, firstIndex: Math.max(0, firstK) };
  }, [history, x, blockIndex]);

  const valuation = VALUATIONS.find((o) => o.key === spec.valuation)!;
  const zeroVector = spec.modality === 'zero';
  const armedI1 = inputs.u.Ivis + inputs.u.Iaud;
  const inputProhibited = diag.regime === 'singularity' && diag.singularityMode !== 'somatic' && armedI1 > 0;
  const opticalViolation = diag.guardrails.opticalCutoff && inputs.u.Ivis > 0;
  const backlogViolation = diag.guardrails.backlogSaturated && armedI1 > 0;
  const maskingActive = inputs.u.O1 >= 0.8;
  const topPrescription = prescriptions[0] ?? null;
  const simple = store.uiMode !== 'advanced';

  const update = (patch: Partial<PersistedState>) => setStore((s) => ({ ...s, ...patch, updatedAt: new Date().toISOString() }));
  const setSpec = (patch: Partial<BlockSpec>) => update({ spec: { ...spec, ...patch } });

  const pushHistory = (entry: HistoryEntry, patch: Partial<PersistedState>) =>
    update({ ...patch, history: [...history, entry].slice(-HISTORY_LIMIT) });

  const integrate = (note?: string) => {
    const result = integrateBlock(x, hoursAwake, inputs, dt, k);
    const nextIndex = blockIndex + 1;
    const latch = nextBacklogLatch(backlogLatch, result.x, inputs, k);
    const q = route(result.x, diagnose(result.x, result.hoursAwake, inputs.theta.Cin, k, latch), k).quadrant;
    pushHistory(
      {
        k: nextIndex,
        at: new Date().toISOString(),
        kind: 'block',
        note: note?.trim() || undefined,
        dtMinutes: dt,
        spec,
        xBefore: x,
        xAfter: result.x,
        hoursAwakeBefore: hoursAwake,
        hoursAwakeAfter: result.hoursAwake,
        mean: result.mean,
        quadrant: q,
      },
      { x: result.x, hoursAwake: result.hoursAwake, blockIndex: nextIndex, backlogLatch: latch, suggested: false },
    );
    setNotice(
      `Block k${nextIndex} logged (Δt ${dt} m) · E ${fmt(x.E)}→${fmt(result.x.E)} · B ${fmt(x.B)}→${fmt(result.x.B)} · F ${fmt(compositeStrain(x))}→${fmt(compositeStrain(result.x))} → ${q}`,
    );
    setDescribed(null);
    setAdjusted(new Set());
    setAdjustField(null);
  };

  const applyDescribed = (text: string, result: DescribedBlock) => {
    setDescribed({ text, result });
    setAdjusted(new Set());
    setAdjustField(result.confidence < 0.5 ? 'modality' : null);
    setShowFullForm(false);
    update({ spec: result.spec, suggested: true });
  };

  const clearSuggestion = () => {
    setDescribed(null);
    setAdjusted(new Set());
    setAdjustField(null);
    setShowFullForm(false);
    update({ suggested: false });
  };

  const adjust = (field: DescribedField, patch: Partial<BlockSpec>) => {
    setSpec(patch);
    setAdjusted((a) => new Set([...a, field]));
  };

  const fieldEditor = (field: DescribedField): ReactNode => {
    const common = { plain: true, showParams: showMath } as const;
    switch (field) {
      case 'modality':
        return <Segmented legend="Activity" symbol={showMath ? 'T₁' : ''} options={MODALITIES} value={spec.modality} onChange={(v) => adjust(field, { modality: v })} {...common} />;
      case 'anchor':
        return <Segmented legend="Background" symbol={showMath ? 'T₂' : ''} options={ANCHORS} value={spec.anchor} onChange={(v) => adjust(field, { anchor: v })} {...common} />;
      case 'valuation':
        return <Segmented legend="Kind of thing" symbol={showMath ? 'V_target' : ''} options={VALUATIONS} value={spec.valuation} onChange={(v) => adjust(field, { valuation: v })} {...common} />;
      case 'density':
        return <Segmented legend="Density" symbol={showMath ? 'C_in' : ''} options={DENSITIES} value={spec.density} onChange={(v) => adjust(field, { density: v })} {...common} />;
      case 'context':
        return <Segmented legend="Pressure" symbol={showMath ? 'P, S_agency' : ''} options={CONTEXTS} value={spec.context} onChange={(v) => adjust(field, { context: v })} {...common} />;
      case 'scratchpad':
        return <Segmented legend="Tangents" symbol={showMath ? 'γ_assoc, Ω' : ''} options={SCRATCHPADS} value={spec.scratchpad} onChange={(v) => adjust(field, { scratchpad: v })} {...common} />;
      case 'novelty':
        return <Segmented legend="Novelty" symbol={showMath ? 'ξ' : ''} options={NOVELTIES} value={spec.novelty} onChange={(v) => adjust(field, { novelty: v })} columns={3} {...common} />;
      case 'somatic':
        return <Segmented legend="Body" symbol={showMath ? 'F' : ''} options={SOMATICS} value={spec.somatic} onChange={(v) => adjust(field, { somatic: v })} {...common} />;
      case 'intensity':
        return <Segmented legend="Intensity" symbol={showMath ? '×(I, O₁)' : ''} options={INTENSITIES} value={spec.intensity ?? 'standard'} onChange={(v) => adjust(field, { intensity: v })} columns={3} {...common} />;
      case 'speed':
        return <Segmented legend="Playback speed" symbol={showMath ? '×I₁' : ''} options={PLAYBACK_SPEEDS} value={spec.speed ?? 'x1'} onChange={(v) => adjust(field, { speed: v })} columns={4} {...common} />;
      case 'duration':
        return <DurationControl spec={spec} onChange={(patch) => adjust(field, patch)} legend="Length" symbol={showMath ? 'Δt' : ''} showParams={showMath} />;
      default:
        return null;
    }
  };

  const undo = () => {
    if (!lastEntry) return;
    update({
      x: lastEntry.xBefore,
      hoursAwake: lastEntry.hoursAwakeBefore,
      backlogLatch: nextBacklogLatch(false, lastEntry.xBefore, null, k),
      blockIndex: lastEntry.kind === 'block' ? Math.max(0, blockIndex - 1) : blockIndex,
      history: history.slice(0, -1),
    });
    setNotice(`Reverted ${lastEntry.kind === 'block' ? `block k${lastEntry.k}` : lastEntry.kind}`);
  };

  const applyOverride = (nx: StateVector, nh: number) => {
    pushHistory(
      { k: blockIndex, at: new Date().toISOString(), kind: 'override', dtMinutes: 0, spec: null, xBefore: x, xAfter: nx, hoursAwakeBefore: hoursAwake, hoursAwakeAfter: nh, mean: null, quadrant: route(nx, diagnose(nx, nh, inputs.theta.Cin, k, nextBacklogLatch(backlogLatch, nx, null, k)), k).quadrant },
      { x: nx, hoursAwake: nh, backlogLatch: nextBacklogLatch(backlogLatch, nx, null, k) },
    );
    setModal(null);
    setNotice('State vector calibrated');
  };

  const confirmSleep = (hours: number) => {
    const nx = applySleepReset(x, hours);
    pushHistory(
      { k: blockIndex, at: new Date().toISOString(), kind: 'sleep', dtMinutes: 0, spec: null, sleepHours: hours, xBefore: x, xAfter: nx, hoursAwakeBefore: hoursAwake, hoursAwakeAfter: 0, mean: null, quadrant: route(nx, diagnose(nx, 0, inputs.theta.Cin, k, false), k).quadrant },
      { x: nx, hoursAwake: 0, backlogLatch: false },
    );
    setModal(null);
    setNotice(`Sleep reset logged (${hours} h) · t_awake = 0`);
  };

  const clearAll = () => {
    setStore(defaultPersisted());
    setModal(null);
    setNotice('All data cleared');
  };

  const savePreset = (name: string) => {
    const preset: UserPreset = { id: `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`, name, spec, createdAt: new Date().toISOString() };
    update({ presets: [...presets, preset].slice(-50) });
    setNotice(`Saved preset “${name}”`);
  };
  const deletePreset = (id: string) => update({ presets: presets.filter((p) => p.id !== id) });

  // Editing or deleting a past entry replays everything after it from the state it now follows.
  const rewriteHistory = (next: HistoryEntry[], notice: string) => {
    const origin = history.length ? { x: history[0].xBefore, hoursAwake: history[0].hoursAwakeBefore } : { x, hoursAwake };
    const r = replayHistory(next, k, origin);
    update({ history: r.history, x: r.x, hoursAwake: r.hoursAwake, blockIndex: r.blockIndex, backlogLatch: r.backlogLatch });
    setNotice(notice);
  };
  const deleteEntry = (index: number) => {
    const h = history[index];
    if (!h) return;
    const what = h.kind === 'block' ? `block k${h.k}` : h.kind === 'sleep' ? 'the sleep reset' : 'the calibration';
    rewriteHistory(
      history.filter((_, i) => i !== index),
      `Deleted ${what} · everything after it recomputed`,
    );
  };
  const editEntry = (index: number, patch: { spec: BlockSpec; note?: string }) => {
    const h = history[index];
    if (!h) return;
    rewriteHistory(
      history.map((e, i) => (i === index ? { ...e, spec: patch.spec, dtMinutes: blockMinutes(patch.spec), note: patch.note } : e)),
      `Updated block k${h.k} · everything after it recomputed`,
    );
  };

  const arm = (raw: BlockSpec) => {
    const s = simple ? withMinutes(raw, blockLength) : raw;
    setShowFullForm(false);
    setDescribed(null);
    setAdjusted(new Set());
    setAdjustField(null);
    update({ spec: s, suggested: true });
    setNotice(simple ? `Using the recommended block · ${blockMinutes(s)} min · adjust anything, then log it` : `Armed: ${describeSpec(s)} · Δt = ${blockMinutes(s)} m`);
    formRef.current?.scrollIntoView?.({ behavior: 'smooth', block: 'start' });
  };

  const copyText = (text: string, done: string) => {
    if (typeof navigator !== 'undefined' && navigator.clipboard?.writeText) {
      navigator.clipboard
        .writeText(text)
        .then(() => setNotice(done))
        .catch(() => setNotice('Clipboard unavailable — copy blocked by the browser'));
    } else {
      setNotice('Clipboard unavailable — copy blocked by the browser');
    }
  };
  const exportJson = () => copyText(encodePersisted(store), `Copied ${history.length} entries to the clipboard`);
  const copyReport = () => report && copyText(report, 'Copied the block report as Markdown');

  const toggleSeries = (key: StateKey) =>
    setHidden((h) => {
      const next = new Set(h);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });

  const rateFor = (key: StateKey): number | null => {
    if (!lastDelta?.mean) return null;
    const map: Record<StateKey, number> = { E: lastDelta.mean.dE, B: lastDelta.mean.dB, Fvis: lastDelta.mean.dFvis, Fbody: lastDelta.mean.dFbody, A: lastDelta.mean.dA, V: lastDelta.mean.dV };
    return map[key];
  };

  const auditFormGroups = (plain: boolean) => <SpecForm spec={spec} onPatch={setSpec} plain={plain} showMath={showMath} />;

  const integratePanel = (label: string) => (
    <>
          <PillarRow verdicts={pillars} showMath />
          <div className="rounded-md border border-zinc-800 bg-zinc-950 p-2.5">
            <div className="flex items-center justify-between gap-2">
              <span className="flex items-center gap-2 text-[10px] font-semibold uppercase tracking-[0.14em] text-zinc-400">
                Predicted Δx over {dt} m
                <StandingPill standing={armedGrade.comparison.standing} size="sm" />
                <span className="font-mono text-[10px] normal-case tracking-normal text-zinc-500">
                  {armedGrade.score} ({fmtSigned(armedGrade.comparison.margin, 0)} vs {armedGrade.comparison.against.name})
                </span>
              </span>
              <span className={`rounded border px-1.5 py-0.5 font-mono text-[10px] ${QUADRANT_TONE[previewRouting.quadrant]}`}>→ {previewRouting.quadrant}</span>
            </div>
            <div className="mt-1.5 flex flex-wrap gap-1.5">
              {STATE_KEYS.map((key) => (
                <span key={key} className="inline-flex items-center gap-1 rounded border border-zinc-800 px-1.5 py-0.5 font-mono text-[10.5px]">
                  <span className={SERIES_BY_KEY[key].text}>{SERIES_BY_KEY[key].symbol}</span>
                  <span className={deltaTone(key, preview.delta[key], x[key], k)}>{fmtSigned(preview.delta[key], 3)}</span>
                </span>
              ))}
            </div>
            <div className="mt-1.5 font-mono text-[10.5px] text-zinc-500">
              A_inst {fmt(arousalPotential(inputs.u, inputs.xiNovelty))} · I₁ {fmt(armedI1)} vs I* {fmt(diag.Istar)} ·{' '}
              {armedI1 === 0 ? 'no intake' : armedI1 < diag.Istar ? 'restorative zone' : 'depleting zone'}
            </div>
          </div>

          {!valuation.admissible && !zeroVector && (
            <p className="flex items-start gap-2 rounded-md border border-rose-500/40 bg-rose-500/10 px-2.5 py-2 text-[11px] text-rose-200">
              <TriangleAlert className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
              V_target = {fmt(valuation.V)} &lt; V_min = {fmt(k.Vmin)}: this block violates the admissibility constraint. Logging it records the churn; it does not authorise it.
            </p>
          )}
          {inputProhibited && (
            <p className="flex items-start gap-2 rounded-md border border-red-500/50 bg-red-500/10 px-2.5 py-2 text-[11px] text-red-200">
              <TriangleAlert className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
              I*(t) ≤ 0: input is prohibited in this regime. The armed block carries I₁ = {fmt(armedI1)}.
            </p>
          )}
          {opticalViolation && (
            <p className="flex items-start gap-2 rounded-md border border-rose-500/50 bg-rose-500/10 px-2.5 py-2 text-[11px] text-rose-200">
              <TriangleAlert className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
              Optical cutoff: F_vis = {fmt(x.Fvis)} ≥ {fmt(k.FvisCutoff)} forces I_vis = 0. The armed block carries I_vis = {fmt(inputs.u.Ivis)}; switch to audio narrative or darkness.
            </p>
          )}
          {backlogViolation && (
            <p className="flex items-start gap-2 rounded-md border border-amber-400/50 bg-amber-400/10 px-2.5 py-2 text-[11px] text-amber-100">
              <TriangleAlert className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
              Backlog saturated (B = {fmt(x.B)}, lock at {fmt(k.BsatLock)}): I₁ &gt; 0 is prohibited until an expressive digestion block runs. The armed block carries I₁ = {fmt(armedI1)}.
            </p>
          )}
          {maskingActive && (
            <p className="flex items-start gap-2 rounded-md border border-zinc-700 bg-zinc-900 px-2.5 py-2 text-[11px] text-zinc-300">
              <Info className="mt-0.5 h-3.5 w-3.5 shrink-0 text-purple-300" aria-hidden="true" />
              Hyper-focus somatic masking: at O₁ = {fmt(inputs.u.O1)} ≥ 0.80 felt strain under-reports F. The marker is logged, but the boundary is enforced by the integrated F = {fmt(compositeStrain(preview.x))} at Δt.
            </p>
          )}

          <button
            type="button"
            onClick={() => integrate()}
            className="w-full rounded-md border border-cyan-400/70 bg-cyan-400/15 px-3 py-3 text-[12px] font-bold uppercase tracking-[0.18em] text-cyan-100 transition-colors hover:bg-cyan-400/25 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-cyan-300"
          >
            {label}
          </button>
    </>
  );

  return (
    <div className={`min-h-full bg-zinc-950 text-zinc-200 ${frameless ? 'py-2' : 'rounded-2xl border border-zinc-800 p-3 sm:p-4 lg:p-5'}`} style={{ colorScheme: 'dark' }}>
      {/* Header */}
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <h1 className="flex items-center gap-2 text-base font-semibold tracking-tight text-zinc-50">
            <Gauge className="h-5 w-5 text-cyan-400" aria-hidden="true" />
            6D Capacity Controller
          </h1>
          {simple && !showMath ? (
            <p className="mt-0.5 text-[11.5px] text-zinc-500">
              Your capacity, block by block · Block k{blockIndex} · {hoursAwake.toFixed(1)} h awake
            </p>
          ) : (
            <p className="mt-0.5 font-mono text-[11px] text-zinc-500">
              x = [E, B, F_vis, F_body, A, V]ᵀ ∈ [0,1]⁶ · discrete-time diagnostic engine · block k{blockIndex} · t_awake {hoursAwake.toFixed(1)} h
            </p>
          )}
        </div>
        <div className="flex flex-wrap items-center gap-1.5">
          <div role="group" aria-label="Interface mode" className="mr-1 inline-flex overflow-hidden rounded-md border border-zinc-800 text-xs font-medium">
            {(['simple', 'advanced'] as const).map((m) => (
              <button
                key={m}
                type="button"
                aria-pressed={(store.uiMode !== 'advanced') === (m === 'simple')}
                onClick={() => update({ uiMode: m })}
                className={`px-2.5 py-1.5 capitalize transition-colors focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-cyan-400 ${
                  (store.uiMode !== 'advanced') === (m === 'simple') ? 'bg-zinc-800 text-zinc-50' : 'text-zinc-400 hover:text-zinc-200'
                }`}
              >
                {m}
              </button>
            ))}
          </div>
          {simple && (
            <button
              type="button"
              aria-pressed={showMath}
              onClick={() => update({ showMath: !showMath })}
              className={`rounded-md border px-2.5 py-1.5 text-xs font-medium transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-cyan-400 ${
                showMath ? 'border-cyan-400/60 bg-cyan-400/10 text-cyan-100' : 'border-zinc-800 text-zinc-300 hover:border-zinc-600 hover:text-zinc-100'
              }`}
            >
              Show math
            </button>
          )}
          <IconButton icon={SlidersHorizontal} label={simple ? 'Calibrate' : 'Override'} onClick={() => setModal('override')} />
          {!simple && <IconButton icon={Settings2} label="Constants" onClick={() => setModal('constants')} />}
          <IconButton
            icon={Moon}
            label="Sleep Reset"
            onClick={() => {
              setSleepHours(7.5);
              setModal('sleep');
            }}
          />
          <IconButton icon={Undo2} label="Undo" onClick={undo} disabled={!lastEntry} />
          {!simple && <IconButton icon={ClipboardCopy} label="Copy JSON" onClick={exportJson} />}
        </div>
      </header>
      <div className="mt-2 min-h-[1.25rem] font-mono text-[11px] text-cyan-300" aria-live="polite">
        {notice ?? ''}
      </div>

      {simple && (
        <div className="mx-auto mt-3 grid max-w-3xl gap-4">
          <details className="rounded-xl border border-zinc-800 bg-zinc-900/30 px-4 py-2.5 text-[12.5px] text-zinc-300">
            <summary className="flex cursor-pointer list-none items-center gap-2 text-[11px] font-semibold uppercase tracking-[0.16em] text-zinc-400">
              <HelpCircle className="h-3.5 w-3.5" aria-hidden="true" /> How this works
            </summary>
            <ol className="mt-2 grid list-decimal gap-1 pl-5 leading-relaxed">
              <li>The six meters are a model of your current capacity, updated every time you log a block of work or rest.</li>
              <li>“Recommended now” is the single block that fits this exact state best, chosen from every kind of block the model knows.</li>
              <li>Describe the block you did or plan to do. It is compared with the recommendation (Best now, Nearly as good, A step behind, Well behind, or Not now when a rule locks it out) and every meter is shown now → after, before you log it. Nothing is assumed until you describe something or use the recommendation.</li>
            </ol>
            <p className="mt-2 text-[11.5px] text-zinc-500">Calibrate sets the meters by hand when the model drifts from how you feel. Show math reveals the symbols and the rules behind every number.</p>
            <label className="mt-2 flex flex-wrap items-center gap-2 text-[11.5px] text-zinc-400">
              <span>Every block is</span>
              <input
                id="cc-block-length"
                type="number"
                min={MIN_CUSTOM_MINUTES}
                max={MAX_CUSTOM_MINUTES}
                step={5}
                value={blockLength}
                onChange={(e) => {
                  const v = Math.round(Number(e.target.value));
                  if (Number.isFinite(v)) update({ blockLength: Math.min(MAX_CUSTOM_MINUTES, Math.max(MIN_CUSTOM_MINUTES, v)) });
                }}
                aria-label="Block length in minutes"
                className="w-16 rounded border border-zinc-700 bg-zinc-900 px-2 py-1 font-mono text-xs text-zinc-100 focus-visible:outline-2 focus-visible:outline-cyan-400"
              />
              <span>minutes long. Comparisons, boundaries and one-click logging all use this length.</span>
            </label>
            <label className="mt-2 flex flex-wrap items-center gap-2 text-[11.5px] text-zinc-400">
              <span>Audiobooks and podcasts usually play at</span>
              <select
                id="cc-listening-speed"
                value={listeningSpeed}
                onChange={(e) => update({ listeningSpeed: e.target.value as SpeedKey })}
                aria-label="Usual listening speed"
                className="rounded border border-zinc-700 bg-zinc-900 px-2 py-1 font-mono text-xs text-zinc-100 focus-visible:outline-2 focus-visible:outline-cyan-400"
              >
                {PLAYBACK_SPEEDS.map((o) => (
                  <option key={o.key} value={o.key}>
                    {o.label}
                  </option>
                ))}
              </select>
              <span>— listening blocks in the list and in your descriptions use this unless you say a speed.</span>
            </label>
          </details>

          <section aria-label="Status" className="rounded-xl border border-zinc-800 bg-zinc-900/50 p-4">
            <div className="flex flex-wrap items-start justify-between gap-2">
              <div className="min-w-0">
                <h2 className="text-[19px] font-semibold leading-tight text-zinc-50">{plainQ.headline}</h2>
                {showMath && (
                  <div className="mt-1 flex flex-wrap items-center gap-1.5 font-mono text-[10.5px] text-zinc-500">
                    <span className={`rounded border px-1.5 py-0.5 font-bold ${QUADRANT_TONE[routing.quadrant]}`}>{routing.quadrant}</span>
                    <span>{routing.title}</span>
                    <span>· {routing.trigger}</span>
                  </div>
                )}
              </div>
              <span
                role={plainR.tone === 'bad' ? 'alert' : 'status'}
                title={plainR.detail}
                className={`inline-flex items-center gap-1 rounded-full border px-2.5 py-1 text-[11px] font-semibold ${
                  plainR.tone === 'good'
                    ? 'border-emerald-400/50 bg-emerald-400/10 text-emerald-200'
                    : plainR.tone === 'warn'
                      ? 'border-amber-400/50 bg-amber-400/10 text-amber-200'
                      : 'border-red-500/60 bg-red-500/15 text-red-200 motion-safe:animate-pulse'
                }`}
              >
                {plainR.tone !== 'good' && <TriangleAlert className="h-3 w-3" aria-hidden="true" />}
                {plainR.label}
              </span>
            </div>
            <p className="mt-2 text-[13.5px] leading-relaxed text-zinc-300">{plainQ.guidance}</p>
            {showMath && <p className="mt-1.5 text-[12px] leading-relaxed text-zinc-400">{routing.summary}</p>}
            {(showMath ? routing.flags : []).map((f) => (
              <p key={f} className="mt-1.5 flex items-start gap-1.5 text-[11.5px] leading-snug text-zinc-400">
                <Info className="mt-0.5 h-3.5 w-3.5 shrink-0 text-amber-300" aria-hidden="true" />
                <span>{f}</span>
              </p>
            ))}
            <div className="mt-3 grid grid-cols-2 gap-2 sm:grid-cols-3 md:grid-cols-6">
              {SERIES.map((sm) => {
                const pl = PLAIN_BY_KEY[sm.key];
                return (
                  <div key={sm.key} className="rounded-md border border-zinc-800 bg-zinc-950/60 px-2 py-1.5" title={pl.meaning}>
                    <div className="flex items-baseline justify-between gap-1">
                      <span className="truncate text-[11px] font-medium text-zinc-300">
                        {pl.name}
                        {showMath && <span className={`ml-1 font-mono ${sm.text}`}>{sm.symbol}</span>}
                      </span>
                      <span className="font-mono text-[12px] text-zinc-100">{fmt(x[sm.key])}</span>
                    </div>
                    <div className="mt-1 h-1 w-full overflow-hidden rounded-full bg-zinc-800" role="meter" aria-valuemin={0} aria-valuemax={1} aria-valuenow={Number(x[sm.key].toFixed(2))} aria-label={pl.name}>
                      <div className={`h-full rounded-full ${sm.bg}`} style={{ width: `${(x[sm.key] * 100).toFixed(1)}%` }} />
                    </div>
                    <div className="mt-0.5 truncate text-[9.5px] text-zinc-600">{pl.better === 'high' ? 'higher is better' : pl.better === 'low' ? 'lower is better' : '0.50 is the sweet spot'}</div>
                  </div>
                );
              })}
            </div>
            <div className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-[10.5px] text-zinc-500">
              <span>{hoursAwake.toFixed(1)} h awake</span>
              <span>·</span>
              <span>{blockIndex} blocks logged</span>
              {showMath && (
                <>
                  <span>·</span>
                  <span className="font-mono">
                    I* {fmt(diag.Istar)} · Γ {fmt(diag.gamma)} · ψ {fmt(diag.psi)} · regime {diag.regime}
                  </span>
                </>
              )}
            </div>
          </section>

          <section aria-label="Recommended block" className="rounded-xl border border-cyan-400/40 bg-cyan-400/5 p-4">
            <div className="flex flex-wrap items-baseline justify-between gap-2">
              <h2 className="text-[11px] font-semibold uppercase tracking-[0.16em] text-cyan-200">Recommended now</h2>
              <span className="text-[10.5px] text-zinc-500">the one block that fits your state best</span>
            </div>
            {recommended && (
              <div className="mt-2 flex items-start gap-3">
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-x-2 gap-y-0.5">
                    <span className="text-[15px] font-semibold text-zinc-50">{recommended.entry.name}</span>
                    {recommended.entry.presetId && (
                      <>
                        <span className="rounded bg-indigo-400/15 px-1.5 py-0.5 text-[9.5px] font-semibold uppercase tracking-wider text-indigo-200">your preset</span>
                        <button type="button" onClick={() => deletePreset(recommended.entry.presetId!)} aria-label={`Delete preset ${recommended.entry.name}`} className="rounded p-0.5 text-zinc-500 hover:text-rose-300 focus-visible:outline-2 focus-visible:outline-cyan-400">
                          <Trash2 className="h-3.5 w-3.5" aria-hidden="true" />
                        </button>
                      </>
                    )}
                  </div>
                  <div className="mt-0.5 text-[11.5px] text-zinc-500">{recommended.entry.detail}</div>
                  <div className="mt-1 text-[12px] leading-snug text-zinc-200">{plainReason(recommended, x)}</div>
                  <GuardrailList warnings={recommended.guardrails} showMath={showMath} compact />
                  {showMath && (
                    <div className="mt-1 font-mono text-[10px] text-zinc-500">
                      score {recommended.score} · fit {recommended.fit} · outcome {recommended.outcome} · horizon {recommended.horizon} · stop: {recommended.stopReason}
                    </div>
                  )}
                </div>
                <div className="flex shrink-0 flex-col items-end gap-1.5">
                  <span className="font-mono text-lg font-semibold leading-none text-zinc-100">{recommended.entry.kind === 'sleep' ? 'sleep' : recommended.boundMinutes < 15 ? '<15 m' : `${recommended.boundMinutes} m`}</span>
                  {recommended.entry.kind === 'sleep' ? (
                    <PrimaryButton
                      onClick={() => {
                        setSleepHours(7.5);
                        setModal('sleep');
                      }}
                    >
                      Log Sleep
                    </PrimaryButton>
                  ) : (
                    <PrimaryButton onClick={() => recommended.spec && arm(recommended.spec)}>Use this</PrimaryButton>
                  )}
                </div>
              </div>
            )}
          </section>

          <section aria-label="Your block" className="rounded-xl border border-zinc-800 bg-zinc-900/50 p-4" ref={formRef}>
            <div className="flex flex-wrap items-baseline justify-between gap-2">
              <h2 className="text-[11px] font-semibold uppercase tracking-[0.16em] text-zinc-400">Your block</h2>
              {suggested ? (
                <button type="button" onClick={clearSuggestion} className="text-[11px] text-zinc-500 hover:text-zinc-300 focus-visible:outline-2 focus-visible:outline-cyan-400">
                  Clear
                </button>
              ) : (
                <span className="text-[10.5px] text-zinc-500">{blockLength} min unless you say otherwise</span>
              )}
            </div>
            <div className="mt-3">
              <DescribeBox defaultMinutes={blockLength} defaultSpeed={listeningSpeed} onDescribed={applyDescribed} />
            </div>
            {!suggested && (
              <p className="mt-2 text-[12px] leading-snug text-zinc-500">
                Describe a block you did or plan to do, or use the recommendation above. It is compared with the recommended block and every meter is shown now → after before you log it. Nothing is assumed until you do.
              </p>
            )}
            {suggested && (
            <>
            <div className={`mt-2 rounded-md border px-3 py-2 ${described ? 'border-cyan-400/30 bg-cyan-400/5' : 'border-zinc-800 bg-zinc-950/60'}`} aria-label={described ? 'Understood as' : 'Block details'}>
              <div className="flex flex-wrap items-baseline justify-between gap-2">
                <span className={`text-[11px] font-semibold uppercase tracking-[0.14em] ${described ? 'text-cyan-200' : 'text-zinc-400'}`}>{described ? 'Understood as' : 'Details'}</span>
                <span className="text-[10.5px] text-zinc-500">
                  {described
                    ? described.result.confidence >= 0.75
                      ? 'Fairly sure. Tap anything to adjust it.'
                      : described.result.confidence >= 0.5
                        ? 'Best guess. Tap anything to adjust it.'
                        : 'Could not tell what this was. Pick the activity, then adjust the rest.'
                    : 'Tap anything to adjust it.'}
                </span>
              </div>
              <ul className="mt-1.5 flex flex-wrap gap-1.5">
                {(['modality', 'duration', 'anchor', 'somatic', 'valuation', 'density', 'context', 'scratchpad', 'novelty', 'intensity', ...(modalityIsPlayback(spec.modality) ? (['speed'] as DescribedField[]) : [])] as DescribedField[]).map((field) => {
                  const cue = described?.result.cues.find((c) => c.field === field);
                  const isAdjusted = adjusted.has(field);
                  const assumed = !!described && !cue && !isAdjusted;
                  const open = adjustField === field;
                  return (
                    <li key={field}>
                      <button
                        type="button"
                        aria-expanded={open}
                        onClick={() => setAdjustField(open ? null : field)}
                        title={isAdjusted ? 'adjusted by you' : cue ? `from “${cue.word}”` : described ? 'assumed' : 'tap to adjust'}
                        className={`rounded border px-1.5 py-0.5 text-left text-[11px] transition-colors focus-visible:outline-2 focus-visible:outline-cyan-400 ${
                          open ? 'border-cyan-400 bg-cyan-400/15 text-cyan-50' : assumed ? 'border-zinc-800 text-zinc-500 hover:border-zinc-600' : 'border-zinc-700 text-zinc-200 hover:border-zinc-500'
                        }`}
                      >
                        <span className="text-zinc-500">{FIELD_LABEL[field]}:</span> {plainChoice(spec, field)}
                        {isAdjusted ? (
                          <>
                            {' '}
                            <span className="text-cyan-300">← you</span>
                          </>
                        ) : cue ? (
                          <>
                            {' '}
                            <span className="text-zinc-500">← “{cue.word}”</span>
                          </>
                        ) : assumed ? (
                          <>
                            {' '}
                            <span className="text-zinc-600">(assumed)</span>
                          </>
                        ) : null}
                      </button>
                    </li>
                  );
                })}
              </ul>
              {adjustField && (
                <div role="group" aria-label={`Adjust ${FIELD_LABEL[adjustField]}`} className="mt-2 grid gap-2 border-t border-zinc-800 pt-2">
                  {fieldEditor(adjustField)}
                  <div className="flex justify-end">
                    <GhostButton onClick={() => setAdjustField(null)}>Done</GhostButton>
                  </div>
                </div>
              )}
              <div className="mt-2 flex flex-wrap items-center justify-between gap-2">
                <span className="text-[11px] text-zinc-500">{describeSpec(spec).length > 0 && showMath ? <span className="font-mono">{describeSpec(spec)}</span> : `${blockMinutes(spec)} min · ${MODALITIES.find((o) => o.key === spec.modality)!.plain}`}</span>
                <button
                  type="button"
                  aria-expanded={showFullForm}
                  onClick={() => setShowFullForm((o) => !o)}
                  className="text-[11.5px] font-medium text-cyan-200 hover:text-cyan-100 focus-visible:outline-2 focus-visible:outline-cyan-400"
                >
                  {showFullForm ? 'Hide the full form' : 'Open the full form'}
                </button>
              </div>
            </div>
            <div className="mt-3 grid gap-3">
              {showFullForm && <div className="grid gap-4">{auditFormGroups(true)}</div>}
              <PillarRow verdicts={pillars} showMath={showMath} />
              <BlockPreview graded={armedGrade} x={x} minutes={dt} thenHeadline={`${plainQuadrant(previewRouting).headline} — ${joinEffects(plainEffect(x, preview.x))}`} showMath={showMath} k={k} />
              {!valuation.admissible && !zeroVector && (
                <p className="flex items-start gap-2 rounded-md border border-rose-500/40 bg-rose-500/10 px-2.5 py-2 text-[11.5px] text-rose-200">
                  <TriangleAlert className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                  This counts as churn. Logging it records what happened; it does not make it a good idea.
                </p>
              )}
              {maskingActive && (
                <p className="flex items-start gap-2 rounded-md border border-zinc-700 bg-zinc-900 px-2.5 py-2 text-[11.5px] text-zinc-300">
                  <Info className="mt-0.5 h-3.5 w-3.5 shrink-0 text-purple-300" aria-hidden="true" />
                  Deep focus hides strain. Trust the boundary, not how your body feels mid-sprint.
                </p>
              )}
              <button
                type="button"
                onClick={() => integrate(described?.text)}
                className="w-full rounded-md border border-cyan-400/70 bg-cyan-400/15 px-3 py-3 text-[12px] font-bold uppercase tracking-[0.18em] text-cyan-100 transition-colors hover:bg-cyan-400/25 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-cyan-300"
              >
                Log Block
              </button>
              <PresetSaver onSave={savePreset} />
            </div>
            </>
            )}
          </section>

          <section aria-label="Block log" className="rounded-xl border border-zinc-800 bg-zinc-900/50 p-4">
            <div className="flex flex-wrap items-baseline justify-between gap-2">
              <h2 className="text-[11px] font-semibold uppercase tracking-[0.16em] text-zinc-400">Past blocks</h2>
              <span className="text-[10.5px] text-zinc-500">{history.length === 0 ? 'nothing logged yet' : 'edit or delete any entry; everything after it is recomputed'}</span>
            </div>
            <div className="mt-2">
              <BlockLog history={history} k={k} plain showMath={showMath} limit={6} onEdit={editEntry} onDelete={deleteEntry} />
            </div>
          </section>
        </div>
      )}

      {!simple && (
      <>
      {/* Diagnostic status strip */}
      <section aria-label="Diagnostic status" className="mt-2 grid grid-cols-2 gap-px overflow-hidden rounded-xl border border-zinc-800 bg-zinc-800 md:grid-cols-3 xl:grid-cols-6">
        <StatCell
          label={`I*(t) · C_in ${fmt(inputs.theta.Cin)}`}
          value={fmt(diag.Istar)}
          sub={`num ${fmtSigned(diag.numerator, 3)} · fiction cap ${fmt(diag.IstarFiction)}`}
          tone={diag.numerator <= 0 ? 'text-red-300' : 'text-emerald-300'}
        />
        <StatCell label="Γ_arousal(A)" value={fmt(diag.gamma, 3)} sub={`A ${fmt(x.A)} · A* ${fmt(k.Astar)} · σ_A ${fmt(k.sigmaA)}`} tone="text-cyan-300" />
        <StatCell label="ψ(t) circadian drag" value={fmt(diag.psi, 3)} sub={`${hoursAwake.toFixed(1)} h awake · late at ${k.lateHours} h`} tone={diag.psi > k.psi0 * 1.5 ? 'text-amber-300' : 'text-zinc-100'} />
        <StatCell label={`Φ_in · armed ${dt} m`} value={fmtSigned(preview.mean.phiIn, 3)} sub="mean net input flux / h" tone={preview.mean.phiIn < 0 ? 'text-rose-300' : 'text-emerald-300'} />
        <StatCell label={`Φ_out · armed ${dt} m`} value={fmtSigned(preview.mean.phiOut, 3)} sub="mean net output yield / h" tone={preview.mean.phiOut < 0 ? 'text-rose-300' : 'text-emerald-300'} />
        <RegimeBadge regime={diag.regime} Istar={diag.Istar} mode={diag.singularityMode} />
      </section>

      <div className="mt-4 grid gap-4 xl:grid-cols-[minmax(0,1.05fr)_minmax(0,1.15fr)_minmax(0,1fr)]">
        {/* HUD */}
        <section aria-label="State-space HUD" className="min-w-0">
          <SectionTitle icon={Activity} aside={<span className="font-mono text-[10.5px] text-zinc-500">F = max(F_vis, F_body) = {fmt(compositeStrain(x))}</span>}>
            6D State-Space HUD
          </SectionTitle>
          <div className="grid gap-2 sm:grid-cols-2 xl:grid-cols-1 2xl:grid-cols-2">
            {SERIES.map((s) => (
              <StateTile
                key={s.key}
                meta={s}
                value={x[s.key]}
                delta={lastDelta ? lastDelta.xAfter[s.key] - lastDelta.xBefore[s.key] : null}
                rate={rateFor(s.key)}
                history={trajectory.points.map((p) => p[s.key])}
                k={k}
              />
            ))}
          </div>
          <div className="mt-4 rounded-lg border border-zinc-800 bg-zinc-900/40 p-3">
            <div className="mb-2 flex items-center justify-between">
              <h3 className="text-[11px] font-semibold uppercase tracking-[0.14em] text-zinc-400">Trajectory · last {Math.max(0, trajectory.points.length - 1)} blocks</h3>
              <span className="font-mono text-[10px] text-zinc-500">{history.length === 0 ? 'integrate a block to start' : 'hover or arrow keys to inspect'}</span>
            </div>
            <TrajectoryChart points={trajectory.points} firstIndex={trajectory.firstIndex} hidden={hidden} onToggle={toggleSeries} />
          </div>
        </section>

        {/* Telemetry audit form */}
        <section aria-label="Telemetry ingestion audit" className="min-w-0" ref={formRef}>
          <SectionTitle icon={Play} aside={<span className="font-mono text-[10.5px] text-zinc-500">u = [I_vis, I_aud, I_anchor, O₁, O_anchor]ᵀ</span>}>
            Telemetry Ingestion Audit
          </SectionTitle>
          <div className="grid gap-4 rounded-lg border border-zinc-800 bg-zinc-900/40 p-3">
            {auditFormGroups(false)}

            {integratePanel('Integrate Discrete Flux & Advance Block')}
          </div>
        </section>

        {/* Prescription engine */}
        <section aria-label="Dynamic prescription engine" className="min-w-0">
          {lastDelta && lastDelta.mean && (
            <div className="mb-4 rounded-lg border border-zinc-800 bg-zinc-900/40 p-3" aria-label="State vector update">
              <div className="mb-2 flex items-center justify-between gap-2">
                <h3 className="flex items-center gap-2 text-[11px] font-semibold uppercase tracking-[0.14em] text-zinc-400">
                  <Sigma className="h-3.5 w-3.5 text-zinc-500" aria-hidden="true" />
                  State Vector Update · block k{lastDelta.k} · Δt {lastDelta.dtMinutes} m
                </h3>
                <button type="button" onClick={copyReport} className="inline-flex items-center gap-1 rounded border border-zinc-800 px-2 py-1 text-[10.5px] text-zinc-300 hover:border-zinc-600 hover:text-zinc-100 focus-visible:outline-2 focus-visible:outline-cyan-400">
                  <ClipboardCopy className="h-3 w-3" aria-hidden="true" /> Copy Report
                </button>
              </div>
              <dl className="grid gap-1 font-mono text-[11px]">
                {STATE_KEYS.map((key) => {
                  const m = lastDelta.mean!;
                  const s = SERIES_BY_KEY[key];
                  const before = lastDelta.xBefore[key];
                  const after = lastDelta.xAfter[key];
                  const note =
                    key === 'E'
                      ? `ΔE ${fmtSigned(m.dE)} /h · Φ_in ${fmtSigned(m.phiIn)} · Φ_out ${fmtSigned(m.phiOut)}`
                      : key === 'B'
                        ? `load +${fmt(m.accrual)} · digested −${fmt(m.digestion)} · decay −${fmt(m.decay)} /h`
                        : key === 'A'
                          ? `Γ̄ = ${fmt(m.gamma, 3)}`
                          : `${fmtSigned(key === 'Fvis' ? m.dFvis : key === 'Fbody' ? m.dFbody : m.dV)} /h`;
                  return (
                    <div key={key} className="grid grid-cols-[3.2rem_7.5rem_1fr] items-baseline gap-2">
                      <dt className={s.text}>{s.symbol}</dt>
                      <dd className="text-zinc-200">
                        {fmt(before, 3)} → {fmt(after, 3)}
                      </dd>
                      <dd className={`truncate ${deltaTone(key, after - before, before, k)}`} title={note}>
                        {note}
                      </dd>
                    </div>
                  );
                })}
              </dl>
              <div className="mt-2 font-mono text-[10.5px] text-zinc-500">
                I*(t) {fmt(diag.Istar)} · Γ {fmt(diag.gamma, 3)} · ψ {fmt(diag.psi, 3)} · regime {diag.regime}
                {diag.singularityMode ? ` (${diag.singularityMode})` : ''} · guardrails:{' '}
                {Object.entries(diag.guardrails)
                  .filter(([, v]) => v)
                  .map(([g]) => g)
                  .join(', ') || 'none'}
              </div>
            </div>
          )}
          <SectionTitle icon={Zap} aside={<span className="font-mono text-[10.5px] text-zinc-500">x_k+1 → quadrant routing</span>}>
            Dynamic Prescription Engine
          </SectionTitle>
          <div className={`rounded-lg border p-3 ${QUADRANT_TONE[routing.quadrant]}`}>
            <div className="flex items-center gap-2">
              <span className="rounded border border-current px-1.5 py-0.5 font-mono text-[11px] font-bold">{routing.quadrant}</span>
              <span className="text-[13px] font-semibold">{routing.title}</span>
            </div>
            <div className="mt-2 font-mono text-[10.5px] opacity-90">{routing.trigger}</div>
            <p className="mt-2 text-[12px] leading-relaxed text-zinc-200">{routing.summary}</p>
            {routing.flags.length > 0 && (
              <ul className="mt-2 grid gap-1">
                {routing.flags.map((f) => (
                  <li key={f} className="flex items-start gap-1.5 text-[11px] leading-snug text-zinc-300">
                    <Info className="mt-0.5 h-3.5 w-3.5 shrink-0 text-amber-300" aria-hidden="true" />
                    <span>{f}</span>
                  </li>
                ))}
              </ul>
            )}
          </div>
          <div className="mt-3 grid gap-2">
            {prescriptions.map((p) => (
              <PrescriptionCard
                key={p.name}
                p={p}
                x={x}
                k={k}
                onArm={arm}
                onSleep={(h) => {
                  setSleepHours(h);
                  setModal('sleep');
                }}
              />
            ))}
          </div>

          {topPrescription && (
            <div className="mt-3 flex flex-wrap items-center justify-between gap-2 rounded-lg border border-cyan-400/40 bg-cyan-400/5 px-3 py-2.5" aria-label="Operational prompt">
              <span className="text-[12px] text-cyan-100">
                {topPrescription.kind === 'sleep'
                  ? `Terminate the session and log the ${topPrescription.sleepHours ?? 7.5} h sleep reset on waking?`
                  : `Lock in «${topPrescription.name}» with the boundary at ${topPrescription.boundMinutes} m?`}
              </span>
              {topPrescription.kind === 'sleep' ? (
                <PrimaryButton
                  onClick={() => {
                    setSleepHours(topPrescription.sleepHours ?? 7.5);
                    setModal('sleep');
                  }}
                >
                  Log Sleep Reset
                </PrimaryButton>
              ) : (
                <PrimaryButton onClick={() => topPrescription.spec && arm(topPrescription.spec)}>Lock In</PrimaryButton>
              )}
            </div>
          )}

          {/* Block log */}
          <div className="mt-4 rounded-lg border border-zinc-800 bg-zinc-900/40">
            <button
              type="button"
              onClick={() => setLogOpen((o) => !o)}
              aria-expanded={logOpen}
              className="flex w-full items-center justify-between px-3 py-2 text-left focus-visible:outline-2 focus-visible:outline-cyan-400"
            >
              <span className="text-[11px] font-semibold uppercase tracking-[0.14em] text-zinc-400">Block Log · {history.length} entries</span>
              {logOpen ? <ChevronUp className="h-4 w-4 text-zinc-500" aria-hidden="true" /> : <ChevronDown className="h-4 w-4 text-zinc-500" aria-hidden="true" />}
            </button>
            {logOpen && (
              <div className="border-t border-zinc-800 p-3">
                <BlockLog history={history} k={k} plain={false} showMath limit={12} onEdit={editEntry} onDelete={deleteEntry} />
              </div>
            )}
          </div>
        </section>
      </div>

      {/* Graded block catalog */}
      <section aria-label="Block catalog" className="mt-5">
        <SectionTitle
          icon={Grid2x2}
          aside={
            <span className="font-mono text-[10.5px] text-zinc-500">
              score = 0.45·fit + 0.40·outcome + 0.15·horizon, capped by guardrails · margin vs the best: ≥ −8 nearly as good · ≥ −25 a step behind · else well behind · capped = not now
            </span>
          }
        >
          Block Catalog · compared for x_k
        </SectionTitle>
        <div className="grid gap-2 sm:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-4">
          {catalog.map((g) => (
            <CatalogCard
              key={g.entry.id}
              g={g}
              x={x}
              k={k}
              onArm={arm}
              onSleep={() => {
                setSleepHours(7.5);
                setModal('sleep');
              }}
              onDelete={g.entry.presetId ? () => deletePreset(g.entry.presetId!) : undefined}
            />
          ))}
        </div>
      </section>

      </>
      )}

      {modal === 'override' && <OverrideModal x={x} hoursAwake={hoursAwake} k={k} onApply={applyOverride} onClear={clearAll} onClose={() => setModal(null)} />}
      {modal === 'constants' && (
        <ConstantsModal constants={k} onChange={(c) => update({ constants: c })} onReset={() => update({ constants: { ...DEFAULT_CONSTANTS } })} onClose={() => setModal(null)} />
      )}
      {modal === 'sleep' && <SleepModal x={x} initialHours={sleepHours} onConfirm={confirmSleep} onClose={() => setModal(null)} />}
    </div>
  );
}

export default CapacityControllerScreen;
