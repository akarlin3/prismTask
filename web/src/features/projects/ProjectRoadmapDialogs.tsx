import { useState } from 'react';
import { Modal } from '@/components/ui/Modal';
import { Button } from '@/components/ui/Button';
import { Select } from '@/components/ui/Select';
import type {
  ProjectPhase,
  ProjectPhaseCreate,
  ProjectPhaseUpdate,
} from '@/types/projectPhase';
import type {
  ProjectRisk,
  ProjectRiskCreate,
  ProjectRiskUpdate,
  RiskLevel,
} from '@/types/projectRisk';
import type {
  ExternalAnchorRecord,
  ExternalAnchor,
  ExternalAnchorVariant,
  ComparisonOpSymbol,
} from '@/types/externalAnchor';
import { COMPARISON_OPS } from '@/types/externalAnchor';
import type { Task } from '@/types/task';
import { wouldCreateCycle } from '@/utils/dependencyCycleGuard';
import type { TaskDependency } from '@/types/taskDependency';

/**
 * Editor dialogs for the project-roadmap surface — TypeScript port of
 * the Android `ProjectRoadmapEditDialogs.kt` (PR #1094 PR-B). Each
 * dialog is a thin form over the parent screen's repo-write callback;
 * validation lives in the caller so these stay presentational.
 */

// ── Phase ─────────────────────────────────────────────────────

interface PhaseEditDialogProps {
  isOpen: boolean;
  existing: ProjectPhase | null;
  onClose: () => void;
  onSaveCreate: (data: ProjectPhaseCreate) => Promise<void>;
  onSaveUpdate: (id: string, data: ProjectPhaseUpdate) => Promise<void>;
}

export function PhaseEditDialog({
  isOpen,
  existing,
  onClose,
  onSaveCreate,
  onSaveUpdate,
}: PhaseEditDialogProps) {
  const [title, setTitle] = useState(existing?.title ?? '');
  const [description, setDescription] = useState(existing?.description ?? '');
  const [versionAnchor, setVersionAnchor] = useState(existing?.version_anchor ?? '');
  const [saving, setSaving] = useState(false);

  // Reset buffer when the dialog opens for a new target.
  const [openedFor, setOpenedFor] = useState<string | null>(existing?.id ?? null);
  const currentKey = existing?.id ?? null;
  if (currentKey !== openedFor) {
    setOpenedFor(currentKey);
    setTitle(existing?.title ?? '');
    setDescription(existing?.description ?? '');
    setVersionAnchor(existing?.version_anchor ?? '');
  }

  const trimmed = title.trim();
  const canSave = trimmed.length > 0 && !saving;

  const handleSave = async () => {
    if (!canSave) return;
    setSaving(true);
    try {
      const desc = description.trim() ? description.trim() : null;
      const anchor = versionAnchor.trim() ? versionAnchor.trim() : null;
      if (existing) {
        await onSaveUpdate(existing.id, {
          title: trimmed,
          description: desc,
          version_anchor: anchor,
        });
      } else {
        await onSaveCreate({
          title: trimmed,
          description: desc,
          version_anchor: anchor,
        });
      }
      onClose();
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={existing ? 'Edit Phase' : 'Add Phase'}
      size="sm"
      footer={
        <div className="flex justify-end gap-2">
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button onClick={handleSave} disabled={!canSave} loading={saving}>
            Save
          </Button>
        </div>
      }
    >
      <div className="flex flex-col gap-4">
        <Field label="Title">
          <input
            type="text"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            className="w-full rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-3 py-2 text-sm text-[var(--color-text-primary)] outline-none focus:border-[var(--color-accent)]"
            autoFocus
          />
        </Field>
        <Field label="Description (Optional)">
          <textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            rows={2}
            className="w-full rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-3 py-2 text-sm text-[var(--color-text-primary)] outline-none focus:border-[var(--color-accent)]"
          />
        </Field>
        <Field label="Version Anchor (e.g. v1.9.0)">
          <input
            type="text"
            value={versionAnchor}
            onChange={(e) => setVersionAnchor(e.target.value)}
            className="w-full rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-3 py-2 text-sm text-[var(--color-text-primary)] outline-none focus:border-[var(--color-accent)]"
          />
        </Field>
      </div>
    </Modal>
  );
}

// ── Risk ──────────────────────────────────────────────────────

interface RiskEditDialogProps {
  isOpen: boolean;
  existing: ProjectRisk | null;
  onClose: () => void;
  onSaveCreate: (data: ProjectRiskCreate) => Promise<void>;
  onSaveUpdate: (id: string, data: ProjectRiskUpdate) => Promise<void>;
}

export function RiskEditDialog({
  isOpen,
  existing,
  onClose,
  onSaveCreate,
  onSaveUpdate,
}: RiskEditDialogProps) {
  const [title, setTitle] = useState(existing?.title ?? '');
  const [level, setLevel] = useState<RiskLevel>(existing?.level ?? 'MEDIUM');
  const [mitigation, setMitigation] = useState(existing?.mitigation ?? '');
  const [saving, setSaving] = useState(false);

  const [openedFor, setOpenedFor] = useState<string | null>(existing?.id ?? null);
  const currentKey = existing?.id ?? null;
  if (currentKey !== openedFor) {
    setOpenedFor(currentKey);
    setTitle(existing?.title ?? '');
    setLevel(existing?.level ?? 'MEDIUM');
    setMitigation(existing?.mitigation ?? '');
  }

  const trimmed = title.trim();
  const canSave = trimmed.length > 0 && !saving;

  const handleSave = async () => {
    if (!canSave) return;
    setSaving(true);
    try {
      const mit = mitigation.trim() ? mitigation.trim() : null;
      if (existing) {
        await onSaveUpdate(existing.id, { title: trimmed, level, mitigation: mit });
      } else {
        await onSaveCreate({ title: trimmed, level, mitigation: mit });
      }
      onClose();
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={existing ? 'Edit Risk' : 'Add Risk'}
      size="sm"
      footer={
        <div className="flex justify-end gap-2">
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button onClick={handleSave} disabled={!canSave} loading={saving}>
            Save
          </Button>
        </div>
      }
    >
      <div className="flex flex-col gap-4">
        <Field label="Title">
          <input
            type="text"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            className="w-full rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-3 py-2 text-sm text-[var(--color-text-primary)] outline-none focus:border-[var(--color-accent)]"
            autoFocus
          />
        </Field>
        <Select
          label="Severity"
          value={level}
          onChange={(v) => v && setLevel(v as RiskLevel)}
          options={[
            { value: 'LOW', label: 'Low' },
            { value: 'MEDIUM', label: 'Medium' },
            { value: 'HIGH', label: 'High' },
          ]}
        />
        <Field label="Mitigation (Optional)">
          <textarea
            value={mitigation}
            onChange={(e) => setMitigation(e.target.value)}
            rows={2}
            className="w-full rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-3 py-2 text-sm text-[var(--color-text-primary)] outline-none focus:border-[var(--color-accent)]"
          />
        </Field>
      </div>
    </Modal>
  );
}

// ── External Anchor ───────────────────────────────────────────

interface AnchorEditDialogProps {
  isOpen: boolean;
  existing: ExternalAnchorRecord | null;
  onClose: () => void;
  onSaveCreate: (label: string, anchor: ExternalAnchor) => Promise<void>;
  onSaveUpdate: (id: string, label: string, anchor: ExternalAnchor) => Promise<void>;
}

export function AnchorEditDialog({
  isOpen,
  existing,
  onClose,
  onSaveCreate,
  onSaveUpdate,
}: AnchorEditDialogProps) {
  const initialAnchor = existing?.anchor;
  const [label, setLabel] = useState(existing?.label ?? '');
  const [variant, setVariant] = useState<ExternalAnchorVariant>(
    initialAnchor?.type ?? 'calendar_deadline',
  );
  // Per-variant buffers retained so the user can flip variants without
  // losing prior state — mirrors Android dialog's behavior.
  const [dateMs, setDateMs] = useState<string>(
    initialAnchor?.type === 'calendar_deadline' ? String(initialAnchor.epochMs) : '',
  );
  const [metric, setMetric] = useState<string>(
    initialAnchor?.type === 'numeric_threshold' ? initialAnchor.metric : '',
  );
  const [op, setOp] = useState<ComparisonOpSymbol>(
    initialAnchor?.type === 'numeric_threshold' ? initialAnchor.op : '<',
  );
  const [value, setValue] = useState<string>(
    initialAnchor?.type === 'numeric_threshold' ? String(initialAnchor.value) : '',
  );
  const [gateKey, setGateKey] = useState<string>(
    initialAnchor?.type === 'boolean_gate' ? initialAnchor.gateKey : '',
  );
  const [expectedState, setExpectedState] = useState<boolean>(
    initialAnchor?.type === 'boolean_gate' ? initialAnchor.expectedState : true,
  );
  const [saving, setSaving] = useState(false);

  const [openedFor, setOpenedFor] = useState<string | null>(existing?.id ?? null);
  const currentKey = existing?.id ?? null;
  if (currentKey !== openedFor) {
    setOpenedFor(currentKey);
    const a = existing?.anchor;
    setLabel(existing?.label ?? '');
    setVariant(a?.type ?? 'calendar_deadline');
    setDateMs(a?.type === 'calendar_deadline' ? String(a.epochMs) : '');
    setMetric(a?.type === 'numeric_threshold' ? a.metric : '');
    setOp(a?.type === 'numeric_threshold' ? a.op : '<');
    setValue(a?.type === 'numeric_threshold' ? String(a.value) : '');
    setGateKey(a?.type === 'boolean_gate' ? a.gateKey : '');
    setExpectedState(a?.type === 'boolean_gate' ? a.expectedState : true);
  }

  const trimmedLabel = label.trim();
  const builtAnchor = buildAnchor(variant, {
    dateMs,
    metric,
    op,
    value,
    gateKey,
    expectedState,
  });
  const canSave = trimmedLabel.length > 0 && builtAnchor !== null && !saving;

  const handleSave = async () => {
    if (!canSave || !builtAnchor) return;
    setSaving(true);
    try {
      if (existing) {
        await onSaveUpdate(existing.id, trimmedLabel, builtAnchor);
      } else {
        await onSaveCreate(trimmedLabel, builtAnchor);
      }
      onClose();
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={existing ? 'Edit Anchor' : 'Add External Anchor'}
      size="sm"
      footer={
        <div className="flex justify-end gap-2">
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button onClick={handleSave} disabled={!canSave} loading={saving}>
            Save
          </Button>
        </div>
      }
    >
      <div className="flex flex-col gap-4">
        <Field label="Label">
          <input
            type="text"
            value={label}
            onChange={(e) => setLabel(e.target.value)}
            className="w-full rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-3 py-2 text-sm text-[var(--color-text-primary)] outline-none focus:border-[var(--color-accent)]"
            autoFocus
          />
        </Field>
        <Select
          label="Type"
          value={variant}
          onChange={(v) => v && setVariant(v as ExternalAnchorVariant)}
          options={[
            { value: 'calendar_deadline', label: 'Calendar Deadline' },
            { value: 'numeric_threshold', label: 'Numeric Threshold' },
            { value: 'boolean_gate', label: 'Boolean Gate' },
          ]}
        />
        {variant === 'calendar_deadline' && (
          <Field label="Deadline (Epoch Ms)">
            <input
              type="number"
              value={dateMs}
              onChange={(e) => setDateMs(e.target.value)}
              className="w-full rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-3 py-2 text-sm text-[var(--color-text-primary)] outline-none focus:border-[var(--color-accent)]"
            />
          </Field>
        )}
        {variant === 'numeric_threshold' && (
          <>
            <Field label="Metric Name">
              <input
                type="text"
                value={metric}
                onChange={(e) => setMetric(e.target.value)}
                className="w-full rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-3 py-2 text-sm text-[var(--color-text-primary)] outline-none focus:border-[var(--color-accent)]"
              />
            </Field>
            <Select
              label="Operator"
              value={op}
              onChange={(v) => v && setOp(v as ComparisonOpSymbol)}
              options={COMPARISON_OPS.map((sym) => ({ value: sym, label: sym }))}
            />
            <Field label="Threshold Value">
              <input
                type="number"
                step="any"
                value={value}
                onChange={(e) => setValue(e.target.value)}
                className="w-full rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-3 py-2 text-sm text-[var(--color-text-primary)] outline-none focus:border-[var(--color-accent)]"
              />
            </Field>
          </>
        )}
        {variant === 'boolean_gate' && (
          <>
            <Field label="Gate Key">
              <input
                type="text"
                value={gateKey}
                onChange={(e) => setGateKey(e.target.value)}
                className="w-full rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-3 py-2 text-sm text-[var(--color-text-primary)] outline-none focus:border-[var(--color-accent)]"
              />
            </Field>
            <Field label="Expected State">
              <button
                type="button"
                onClick={() => setExpectedState((s) => !s)}
                className="rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-3 py-2 text-sm text-[var(--color-text-primary)] hover:bg-[var(--color-bg-card)]"
              >
                Expected: {String(expectedState)} (Tap to flip)
              </button>
            </Field>
          </>
        )}
      </div>
    </Modal>
  );
}

function buildAnchor(
  variant: ExternalAnchorVariant,
  buf: {
    dateMs: string;
    metric: string;
    op: ComparisonOpSymbol;
    value: string;
    gateKey: string;
    expectedState: boolean;
  },
): ExternalAnchor | null {
  switch (variant) {
    case 'calendar_deadline': {
      const ms = Number(buf.dateMs);
      if (!Number.isFinite(ms) || buf.dateMs.trim() === '') return null;
      return { type: 'calendar_deadline', epochMs: ms };
    }
    case 'numeric_threshold': {
      const v = Number(buf.value);
      if (!Number.isFinite(v) || buf.value.trim() === '') return null;
      if (buf.metric.trim() === '') return null;
      return { type: 'numeric_threshold', metric: buf.metric.trim(), op: buf.op, value: v };
    }
    case 'boolean_gate': {
      if (buf.gateKey.trim() === '') return null;
      return { type: 'boolean_gate', gateKey: buf.gateKey.trim(), expectedState: buf.expectedState };
    }
  }
}

// ── Dependency picker ─────────────────────────────────────────

interface DependencyAddDialogProps {
  isOpen: boolean;
  projectTasks: Task[];
  existingDependencies: TaskDependency[];
  onClose: () => void;
  onSave: (blockerTaskId: string, blockedTaskId: string) => Promise<void>;
}

export function DependencyAddDialog({
  isOpen,
  projectTasks,
  existingDependencies,
  onClose,
  onSave,
}: DependencyAddDialogProps) {
  const [blockerId, setBlockerId] = useState<string | null>(
    projectTasks[0]?.id ?? null,
  );
  const [blockedId, setBlockedId] = useState<string | null>(
    projectTasks[1]?.id ?? null,
  );
  const [saving, setSaving] = useState(false);

  const cycleViolation =
    blockerId && blockedId
      ? wouldCreateCycle(existingDependencies, blockerId, blockedId)
      : false;
  const sameTask = blockerId !== null && blockerId === blockedId;
  const canSave =
    !!blockerId && !!blockedId && !sameTask && !cycleViolation && !saving;

  const handleSave = async () => {
    if (!canSave || !blockerId || !blockedId) return;
    setSaving(true);
    try {
      await onSave(blockerId, blockedId);
      onClose();
    } finally {
      setSaving(false);
    }
  };

  const taskOptions = projectTasks.map((t) => ({ value: t.id, label: t.title }));

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="Add Dependency"
      size="sm"
      footer={
        <div className="flex justify-end gap-2">
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button onClick={handleSave} disabled={!canSave} loading={saving}>
            Save
          </Button>
        </div>
      }
    >
      <div className="flex flex-col gap-4">
        <Select
          label="Blocker (Must Finish First)"
          value={blockerId}
          onChange={(v) => setBlockerId(v)}
          options={taskOptions}
          searchable={taskOptions.length > 8}
        />
        <Select
          label="Blocked (Waits On Blocker)"
          value={blockedId}
          onChange={(v) => setBlockedId(v)}
          options={taskOptions}
          searchable={taskOptions.length > 8}
        />
        {sameTask && (
          <p className="text-xs text-red-500">A task can&apos;t block itself.</p>
        )}
        {cycleViolation && !sameTask && (
          <p className="text-xs text-red-500">
            That edge would close a cycle in the dependency graph.
          </p>
        )}
      </div>
    </Modal>
  );
}

// ── Shared form helper ────────────────────────────────────────

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <label className="mb-1 block text-sm font-medium text-[var(--color-text-primary)]">
        {label}
      </label>
      {children}
    </div>
  );
}
