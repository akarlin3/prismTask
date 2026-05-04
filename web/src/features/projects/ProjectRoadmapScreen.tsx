import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, Plus, Pencil, Trash2, Circle } from 'lucide-react';
import { toast } from 'sonner';

import { Button } from '@/components/ui/Button';
import { Spinner } from '@/components/ui/Spinner';
import { EmptyState } from '@/components/ui/EmptyState';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';

import { getFirebaseUid } from '@/stores/firebaseUid';
import * as firestoreProjects from '@/api/firestore/projects';
import * as firestoreTasks from '@/api/firestore/tasks';
import * as firestorePhases from '@/api/firestore/projectPhases';
import * as firestoreRisks from '@/api/firestore/projectRisks';
import * as firestoreAnchors from '@/api/firestore/externalAnchors';
import * as firestoreDeps from '@/api/firestore/taskDependencies';
import { DependencyCycleError } from '@/api/firestore/taskDependencies';

import type { Project } from '@/types/project';
import type { Task } from '@/types/task';
import type { ProjectPhase, ProjectPhaseCreate, ProjectPhaseUpdate } from '@/types/projectPhase';
import type { ProjectRisk, ProjectRiskCreate, ProjectRiskUpdate } from '@/types/projectRisk';
import type {
  ExternalAnchor,
  ExternalAnchorRecord,
} from '@/types/externalAnchor';
import type { TaskDependency } from '@/types/taskDependency';

import {
  PhaseEditDialog,
  RiskEditDialog,
  AnchorEditDialog,
  DependencyAddDialog,
} from './ProjectRoadmapDialogs';

/**
 * Web port of Android `ProjectRoadmapScreen.kt` (PR #1085 + PR #1094
 * PR-B). Renders phases-with-tasks, the project's unphased tasks, the
 * risk register, external anchors, and the dependency edge set, with
 * edit/delete affordances on each row and an "Add" action per section.
 *
 * Phase 2 of `WEB_PROJECT_ROADMAP_PORT_AUDIT.md`.
 */
export function ProjectRoadmapScreen() {
  const { id: projectId } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const uid = getFirebaseUid();

  const [project, setProject] = useState<Project | null>(null);
  const [tasks, setTasks] = useState<Task[]>([]);
  const [phases, setPhases] = useState<ProjectPhase[]>([]);
  const [risks, setRisks] = useState<ProjectRisk[]>([]);
  const [anchors, setAnchors] = useState<ExternalAnchorRecord[]>([]);
  const [dependencies, setDependencies] = useState<TaskDependency[]>([]);
  const [loading, setLoading] = useState(true);

  // Editor state — exactly one editor open at a time, mirroring Android.
  const [phaseEditor, setPhaseEditor] = useState<{ open: boolean; existing: ProjectPhase | null }>(
    { open: false, existing: null },
  );
  const [riskEditor, setRiskEditor] = useState<{ open: boolean; existing: ProjectRisk | null }>(
    { open: false, existing: null },
  );
  const [anchorEditor, setAnchorEditor] = useState<{
    open: boolean;
    existing: ExternalAnchorRecord | null;
  }>({ open: false, existing: null });
  const [depEditorOpen, setDepEditorOpen] = useState(false);

  // Per-row delete confirmations — kept in a single union state to
  // avoid five parallel pieces of state.
  const [pendingDelete, setPendingDelete] = useState<
    | { kind: 'phase'; row: ProjectPhase }
    | { kind: 'risk'; row: ProjectRisk }
    | { kind: 'anchor'; row: ExternalAnchorRecord }
    | { kind: 'dependency'; row: TaskDependency }
    | null
  >(null);
  const [deleting, setDeleting] = useState(false);

  const loadAll = useCallback(async () => {
    if (!projectId) return;
    setLoading(true);
    try {
      const [proj, projectTasks, phaseList, riskList, anchorList, allDeps] =
        await Promise.all([
          firestoreProjects.getProject(uid, projectId),
          firestoreTasks.getTasksByProject(uid, projectId),
          firestorePhases.getPhasesByProject(uid, projectId),
          firestoreRisks.getRisksByProject(uid, projectId),
          firestoreAnchors.getAnchorsByProject(uid, projectId),
          firestoreDeps.getAllDependencies(uid),
        ]);
      setProject(proj);
      setTasks(projectTasks);
      setPhases(phaseList);
      setRisks(riskList);
      setAnchors(anchorList);
      // Filter the global edge set down to ones whose endpoints are
      // both inside this project — mirrors Android ViewModel behavior.
      const taskIds = new Set(projectTasks.map((t) => t.id));
      setDependencies(
        allDeps.filter(
          (e) => taskIds.has(e.blocker_task_id) && taskIds.has(e.blocked_task_id),
        ),
      );
    } catch {
      toast.error('Failed to load roadmap');
    } finally {
      setLoading(false);
    }
  }, [projectId, uid]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- initial fetch on mount + projectId change
    loadAll();
  }, [loadAll]);

  // ── Section grouping ──────────────────────────────────────

  const phaseToTasks = useMemo(() => {
    const map = new Map<string, Task[]>();
    for (const t of tasks) {
      const pid = t.phase_id;
      if (!pid) continue;
      const list = map.get(pid);
      if (list) list.push(t);
      else map.set(pid, [t]);
    }
    return map;
  }, [tasks]);

  const unphasedTasks = useMemo(
    () => tasks.filter((t) => !t.phase_id),
    [tasks],
  );

  // ── Save callbacks ───────────────────────────────────────

  const handleSavePhaseCreate = useCallback(
    async (data: ProjectPhaseCreate) => {
      if (!projectId) return;
      try {
        await firestorePhases.createPhase(uid, projectId, data);
        toast.success('Phase added');
        await loadAll();
      } catch {
        toast.error('Failed to add phase');
      }
    },
    [projectId, uid, loadAll],
  );

  const handleSavePhaseUpdate = useCallback(
    async (id: string, data: ProjectPhaseUpdate) => {
      try {
        await firestorePhases.updatePhase(uid, id, data);
        toast.success('Phase updated');
        await loadAll();
      } catch {
        toast.error('Failed to update phase');
      }
    },
    [uid, loadAll],
  );

  const handleSaveRiskCreate = useCallback(
    async (data: ProjectRiskCreate) => {
      if (!projectId) return;
      try {
        await firestoreRisks.createRisk(uid, projectId, data);
        toast.success('Risk added');
        await loadAll();
      } catch {
        toast.error('Failed to add risk');
      }
    },
    [projectId, uid, loadAll],
  );

  const handleSaveRiskUpdate = useCallback(
    async (id: string, data: ProjectRiskUpdate) => {
      try {
        await firestoreRisks.updateRisk(uid, id, data);
        toast.success('Risk updated');
        await loadAll();
      } catch {
        toast.error('Failed to update risk');
      }
    },
    [uid, loadAll],
  );

  const handleSaveAnchorCreate = useCallback(
    async (label: string, anchor: ExternalAnchor) => {
      if (!projectId) return;
      try {
        await firestoreAnchors.createAnchor(uid, projectId, { label, anchor });
        toast.success('Anchor added');
        await loadAll();
      } catch {
        toast.error('Failed to add anchor');
      }
    },
    [projectId, uid, loadAll],
  );

  const handleSaveAnchorUpdate = useCallback(
    async (id: string, label: string, anchor: ExternalAnchor) => {
      try {
        await firestoreAnchors.updateAnchor(uid, id, { label, anchor });
        toast.success('Anchor updated');
        await loadAll();
      } catch {
        toast.error('Failed to update anchor');
      }
    },
    [uid, loadAll],
  );

  const handleAddDependency = useCallback(
    async (blockerId: string, blockedId: string) => {
      try {
        await firestoreDeps.addDependency(uid, {
          blocker_task_id: blockerId,
          blocked_task_id: blockedId,
        });
        toast.success('Dependency added');
        await loadAll();
      } catch (err) {
        if (err instanceof DependencyCycleError) {
          toast.error('That edge would close a cycle.');
        } else {
          toast.error("Couldn't add dependency.");
        }
        throw err;
      }
    },
    [uid, loadAll],
  );

  const handleConfirmDelete = useCallback(async () => {
    if (!pendingDelete) return;
    setDeleting(true);
    try {
      switch (pendingDelete.kind) {
        case 'phase':
          await firestorePhases.deletePhase(uid, pendingDelete.row.id);
          toast.success('Phase deleted');
          break;
        case 'risk':
          await firestoreRisks.deleteRisk(uid, pendingDelete.row.id);
          toast.success('Risk deleted');
          break;
        case 'anchor':
          await firestoreAnchors.deleteAnchor(uid, pendingDelete.row.id);
          toast.success('Anchor deleted');
          break;
        case 'dependency':
          await firestoreDeps.deleteDependency(uid, pendingDelete.row.id);
          toast.success('Dependency deleted');
          break;
      }
      await loadAll();
    } catch {
      toast.error('Failed to delete');
    } finally {
      setDeleting(false);
      setPendingDelete(null);
    }
  }, [pendingDelete, uid, loadAll]);

  // ── Render ───────────────────────────────────────────────

  if (loading) {
    return (
      <div className="flex h-64 items-center justify-center">
        <Spinner size="lg" />
      </div>
    );
  }

  if (!project) {
    return (
      <div className="mx-auto max-w-4xl">
        <button
          onClick={() => navigate('/projects')}
          className="mb-4 flex items-center gap-1 text-sm text-[var(--color-text-secondary)] hover:text-[var(--color-text-primary)]"
        >
          <ArrowLeft className="h-4 w-4" />
          All Projects
        </button>
        <EmptyState
          title="Project Not Found"
          description="This project may have been deleted or moved."
        />
      </div>
    );
  }

  return (
    <div className="mx-auto flex max-w-4xl flex-col gap-6">
      {/* Back nav + title */}
      <div>
        <button
          onClick={() => navigate(`/projects/${project.id}`)}
          className="mb-2 flex items-center gap-1 text-sm text-[var(--color-text-secondary)] hover:text-[var(--color-text-primary)] transition-colors"
        >
          <ArrowLeft className="h-4 w-4" />
          Back to Project
        </button>
        <h1 className="text-2xl font-bold text-[var(--color-text-primary)]">
          {project.title} — Roadmap
        </h1>
      </div>

      {/* Phases */}
      <Section
        title={`Phases (${phases.length})`}
        onAdd={() => setPhaseEditor({ open: true, existing: null })}
      >
        {phases.length === 0 ? (
          <EmptySectionLabel text="No phases yet — Tap + to add one." />
        ) : (
          phases.map((phase) => (
            <PhaseCard
              key={phase.id}
              phase={phase}
              tasks={phaseToTasks.get(phase.id) ?? []}
              onEdit={() => setPhaseEditor({ open: true, existing: phase })}
              onDelete={() => setPendingDelete({ kind: 'phase', row: phase })}
            />
          ))
        )}
      </Section>

      {/* Unphased tasks */}
      {unphasedTasks.length > 0 && (
        <Section title={`Unphased Tasks (${unphasedTasks.length})`}>
          <div className="rounded-xl border border-[var(--color-border)] bg-[var(--color-bg-card)] p-3">
            {unphasedTasks.map((t) => (
              <RoadmapTaskRow key={t.id} task={t} />
            ))}
          </div>
        </Section>
      )}

      {/* Risks */}
      <Section
        title={`Risks (${risks.length})`}
        onAdd={() => setRiskEditor({ open: true, existing: null })}
      >
        {risks.length === 0 ? (
          <EmptySectionLabel text="No risks logged." />
        ) : (
          risks.map((risk) => (
            <RiskRow
              key={risk.id}
              risk={risk}
              onEdit={() => setRiskEditor({ open: true, existing: risk })}
              onDelete={() => setPendingDelete({ kind: 'risk', row: risk })}
            />
          ))
        )}
      </Section>

      {/* External anchors */}
      <Section
        title={`External Anchors (${anchors.length})`}
        onAdd={() => setAnchorEditor({ open: true, existing: null })}
      >
        {anchors.length === 0 ? (
          <EmptySectionLabel text="No external anchors." />
        ) : (
          anchors.map((anchor) => (
            <AnchorRow
              key={anchor.id}
              anchor={anchor}
              onEdit={() => setAnchorEditor({ open: true, existing: anchor })}
              onDelete={() => setPendingDelete({ kind: 'anchor', row: anchor })}
            />
          ))
        )}
      </Section>

      {/* Dependencies */}
      <Section
        title={`Dependencies (${dependencies.length})`}
        onAdd={() => setDepEditorOpen(true)}
        addDisabled={tasks.length < 2}
      >
        {dependencies.length === 0 ? (
          <EmptySectionLabel
            text={
              tasks.length < 2
                ? 'Add at least two tasks to this project to define dependencies.'
                : 'No task dependencies in this project.'
            }
          />
        ) : (
          dependencies.map((edge) => (
            <DependencyRow
              key={edge.id}
              edge={edge}
              tasks={tasks}
              onDelete={() => setPendingDelete({ kind: 'dependency', row: edge })}
            />
          ))
        )}
      </Section>

      {/* Editor modals */}
      <PhaseEditDialog
        isOpen={phaseEditor.open}
        existing={phaseEditor.existing}
        onClose={() => setPhaseEditor({ open: false, existing: null })}
        onSaveCreate={handleSavePhaseCreate}
        onSaveUpdate={handleSavePhaseUpdate}
      />
      <RiskEditDialog
        isOpen={riskEditor.open}
        existing={riskEditor.existing}
        onClose={() => setRiskEditor({ open: false, existing: null })}
        onSaveCreate={handleSaveRiskCreate}
        onSaveUpdate={handleSaveRiskUpdate}
      />
      <AnchorEditDialog
        isOpen={anchorEditor.open}
        existing={anchorEditor.existing}
        onClose={() => setAnchorEditor({ open: false, existing: null })}
        onSaveCreate={handleSaveAnchorCreate}
        onSaveUpdate={handleSaveAnchorUpdate}
      />
      <DependencyAddDialog
        isOpen={depEditorOpen}
        projectTasks={tasks}
        existingDependencies={dependencies}
        onClose={() => setDepEditorOpen(false)}
        onSave={handleAddDependency}
      />

      <ConfirmDialog
        isOpen={pendingDelete !== null}
        onClose={() => setPendingDelete(null)}
        onConfirm={handleConfirmDelete}
        title={`Delete ${pendingDelete?.kind ?? 'item'}?`}
        message="This action cannot be undone."
        confirmLabel="Delete"
        variant="danger"
        loading={deleting}
      />
    </div>
  );
}

// ── Section primitives ─────────────────────────────────────────

function Section({
  title,
  onAdd,
  addDisabled = false,
  children,
}: {
  title: string;
  onAdd?: () => void;
  addDisabled?: boolean;
  children: React.ReactNode;
}) {
  return (
    <div className="flex flex-col gap-3">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-semibold text-[var(--color-text-primary)]">
          {title}
        </h2>
        {onAdd && (
          <Button
            size="sm"
            variant="secondary"
            onClick={onAdd}
            disabled={addDisabled}
            aria-label={`Add to ${title}`}
          >
            <Plus className="h-4 w-4" />
            Add
          </Button>
        )}
      </div>
      <div className="flex flex-col gap-2">{children}</div>
    </div>
  );
}

function EmptySectionLabel({ text }: { text: string }) {
  return (
    <p className="text-sm text-[var(--color-text-secondary)]">{text}</p>
  );
}

// ── Row components ────────────────────────────────────────────

function PhaseCard({
  phase,
  tasks,
  onEdit,
  onDelete,
}: {
  phase: ProjectPhase;
  tasks: Task[];
  onEdit: () => void;
  onDelete: () => void;
}) {
  const dateRange = formatDateRange(phase.start_date, phase.end_date);
  return (
    <div className="rounded-xl border border-[var(--color-border)] bg-[var(--color-bg-card)] p-4">
      <div className="flex items-start gap-2">
        <div className="flex-1">
          <div className="flex items-center gap-2">
            <h3 className="font-semibold text-[var(--color-text-primary)]">
              {phase.title}
            </h3>
            {phase.version_anchor && (
              <span className="rounded-md bg-[var(--color-bg-secondary)] px-2 py-0.5 text-xs font-medium text-[var(--color-accent)]">
                {phase.version_anchor}
              </span>
            )}
          </div>
          {dateRange && (
            <p className="mt-1 text-xs text-[var(--color-text-secondary)]">
              {dateRange}
            </p>
          )}
          {phase.description && (
            <p className="mt-2 text-sm text-[var(--color-text-secondary)]">
              {phase.description}
            </p>
          )}
        </div>
        <button
          onClick={onEdit}
          aria-label="Edit phase"
          className="rounded-md p-1.5 text-[var(--color-text-secondary)] hover:bg-[var(--color-bg-secondary)] hover:text-[var(--color-text-primary)]"
        >
          <Pencil className="h-4 w-4" />
        </button>
        <button
          onClick={onDelete}
          aria-label="Delete phase"
          className="rounded-md p-1.5 text-[var(--color-text-secondary)] hover:bg-red-500/10 hover:text-red-500"
        >
          <Trash2 className="h-4 w-4" />
        </button>
      </div>
      <div className="mt-3 border-t border-[var(--color-border)] pt-3">
        {tasks.length === 0 ? (
          <p className="text-xs text-[var(--color-text-secondary)]">
            No tasks in this phase.
          </p>
        ) : (
          tasks.map((t) => <RoadmapTaskRow key={t.id} task={t} />)
        )}
      </div>
    </div>
  );
}

function RoadmapTaskRow({ task }: { task: Task }) {
  const fraction = computeProgressFraction(task);
  const percent = Math.round(fraction * 100);
  return (
    <div className="flex items-center gap-3 py-1.5">
      <span
        className={`flex-1 text-sm ${
          task.status === 'done'
            ? 'text-[var(--color-text-secondary)] line-through'
            : 'text-[var(--color-text-primary)]'
        }`}
      >
        {task.title}
      </span>
      <div className="h-2 w-20 overflow-hidden rounded-full bg-[var(--color-bg-secondary)]">
        <div
          className={`h-full rounded-full ${
            task.status === 'done' ? 'bg-emerald-500' : 'bg-[var(--color-accent)]'
          }`}
          style={{ width: `${percent}%` }}
          role="progressbar"
          aria-valuenow={percent}
          aria-valuemin={0}
          aria-valuemax={100}
        />
      </div>
      <span className="w-10 text-right text-xs text-[var(--color-text-secondary)]">
        {percent}%
      </span>
    </div>
  );
}

function RiskRow({
  risk,
  onEdit,
  onDelete,
}: {
  risk: ProjectRisk;
  onEdit: () => void;
  onDelete: () => void;
}) {
  const { label, color } = riskLevelStyle(risk.level);
  return (
    <div className="flex items-center gap-2 rounded-xl border border-[var(--color-border)] bg-[var(--color-bg-card)] p-3">
      <Circle className={`h-3 w-3 shrink-0 ${color}`} fill="currentColor" />
      <div className="flex-1">
        <p
          className={`text-sm font-medium ${
            risk.resolved_at
              ? 'text-[var(--color-text-secondary)] line-through'
              : 'text-[var(--color-text-primary)]'
          }`}
        >
          {risk.title}
        </p>
        {risk.mitigation && (
          <p className="text-xs text-[var(--color-text-secondary)]">
            {risk.mitigation}
          </p>
        )}
      </div>
      <span className="rounded-md bg-[var(--color-bg-secondary)] px-2 py-0.5 text-xs font-medium text-[var(--color-text-primary)]">
        {label}
      </span>
      <button
        onClick={onEdit}
        aria-label="Edit risk"
        className="rounded-md p-1.5 text-[var(--color-text-secondary)] hover:bg-[var(--color-bg-secondary)] hover:text-[var(--color-text-primary)]"
      >
        <Pencil className="h-4 w-4" />
      </button>
      <button
        onClick={onDelete}
        aria-label="Delete risk"
        className="rounded-md p-1.5 text-[var(--color-text-secondary)] hover:bg-red-500/10 hover:text-red-500"
      >
        <Trash2 className="h-4 w-4" />
      </button>
    </div>
  );
}

function AnchorRow({
  anchor,
  onEdit,
  onDelete,
}: {
  anchor: ExternalAnchorRecord;
  onEdit: () => void;
  onDelete: () => void;
}) {
  const { typeLabel, body } = describeAnchor(anchor.anchor);
  return (
    <div className="flex items-center gap-2 rounded-xl border border-[var(--color-border)] bg-[var(--color-bg-card)] p-3">
      <span className="rounded-md bg-[var(--color-bg-secondary)] px-2 py-0.5 text-xs font-semibold text-[var(--color-text-primary)]">
        {typeLabel}
      </span>
      <div className="flex-1">
        <p className="text-sm font-medium text-[var(--color-text-primary)]">
          {anchor.label}
        </p>
        <p className="text-xs text-[var(--color-text-secondary)]">{body}</p>
      </div>
      <button
        onClick={onEdit}
        aria-label="Edit anchor"
        className="rounded-md p-1.5 text-[var(--color-text-secondary)] hover:bg-[var(--color-bg-secondary)] hover:text-[var(--color-text-primary)]"
      >
        <Pencil className="h-4 w-4" />
      </button>
      <button
        onClick={onDelete}
        aria-label="Delete anchor"
        className="rounded-md p-1.5 text-[var(--color-text-secondary)] hover:bg-red-500/10 hover:text-red-500"
      >
        <Trash2 className="h-4 w-4" />
      </button>
    </div>
  );
}

function DependencyRow({
  edge,
  tasks,
  onDelete,
}: {
  edge: TaskDependency;
  tasks: Task[];
  onDelete: () => void;
}) {
  const blocker =
    tasks.find((t) => t.id === edge.blocker_task_id)?.title ??
    `task ${edge.blocker_task_id.slice(0, 6)}…`;
  const blocked =
    tasks.find((t) => t.id === edge.blocked_task_id)?.title ??
    `task ${edge.blocked_task_id.slice(0, 6)}…`;
  return (
    <div className="flex items-center gap-2 rounded-xl border border-[var(--color-border)] bg-[var(--color-bg-card)] p-3">
      <div className="flex-1">
        <p className="text-sm text-[var(--color-text-primary)]">
          <span className="font-medium">{blocker}</span>
          {'  →  '}
          <span className="font-medium">{blocked}</span>
        </p>
        <p className="text-xs text-[var(--color-text-secondary)]">
          Blocker must finish before blocked starts.
        </p>
      </div>
      <button
        onClick={onDelete}
        aria-label="Delete dependency"
        className="rounded-md p-1.5 text-[var(--color-text-secondary)] hover:bg-red-500/10 hover:text-red-500"
      >
        <Trash2 className="h-4 w-4" />
      </button>
    </div>
  );
}

// ── Helpers ────────────────────────────────────────────────────

function computeProgressFraction(task: Task): number {
  const pct = task.progress_percent;
  if (pct != null) return Math.max(0, Math.min(100, pct)) / 100;
  return task.status === 'done' ? 1 : 0;
}

function formatDateRange(start: number | null, end: number | null): string | null {
  const fmt = (ms: number) =>
    new Date(ms).toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
  if (start && end) return `${fmt(start)} → ${fmt(end)}`;
  if (start) return fmt(start);
  if (end) return fmt(end);
  return null;
}

function riskLevelStyle(level: ProjectRisk['level']): { label: string; color: string } {
  switch (level) {
    case 'LOW':
      return { label: 'LOW', color: 'text-emerald-500' };
    case 'HIGH':
      return { label: 'HIGH', color: 'text-red-500' };
    case 'MEDIUM':
    default:
      return { label: 'MED', color: 'text-amber-500' };
  }
}

function describeAnchor(anchor: ExternalAnchor): { typeLabel: string; body: string } {
  switch (anchor.type) {
    case 'calendar_deadline':
      return {
        typeLabel: 'DATE',
        body: new Date(anchor.epochMs).toLocaleDateString(undefined, {
          year: 'numeric',
          month: 'short',
          day: 'numeric',
        }),
      };
    case 'numeric_threshold':
      return {
        typeLabel: 'METRIC',
        body: `${anchor.metric} ${anchor.op} ${anchor.value}`,
      };
    case 'boolean_gate':
      return {
        typeLabel: 'GATE',
        body: `${anchor.gateKey} = ${String(anchor.expectedState)}`,
      };
  }
}
