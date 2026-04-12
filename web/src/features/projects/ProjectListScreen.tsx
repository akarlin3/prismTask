import { useEffect, useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  FolderKanban,
  Plus,
  Target,
  MoreVertical,
  Edit2,
  Trash2,
  ChevronDown,
  ChevronRight,
} from 'lucide-react';
import { toast } from 'sonner';
import { useProjectStore } from '@/stores/projectStore';
import { Button } from '@/components/ui/Button';
import { Modal } from '@/components/ui/Modal';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';
import { Spinner } from '@/components/ui/Spinner';
import { EmptyState } from '@/components/ui/EmptyState';
import type { Project } from '@/types/project';
import type { Goal } from '@/types/goal';

const PROJECT_COLORS = [
  '#ef4444', '#f97316', '#f59e0b', '#eab308',
  '#84cc16', '#22c55e', '#14b8a6', '#06b6d4',
  '#3b82f6', '#6366f1', '#a855f7', '#ec4899',
];

export function ProjectListScreen() {
  const navigate = useNavigate();
  const {
    goals,
    projects,
    fetchAllProjects,
    createGoal,
    updateGoal,
    deleteGoal,
    createProject,
    deleteProject,
  } = useProjectStore();

  const [loading, setLoading] = useState(true);

  // Goal modal
  const [goalModalOpen, setGoalModalOpen] = useState(false);
  const [editingGoal, setEditingGoal] = useState<Goal | null>(null);
  const [goalTitle, setGoalTitle] = useState('');
  const [goalDescription, setGoalDescription] = useState('');
  const [goalColor, setGoalColor] = useState(PROJECT_COLORS[0]);

  // Project modal
  const [projectModalOpen, setProjectModalOpen] = useState(false);
  const [projectGoalId, setProjectGoalId] = useState<string | null>(null);
  const [projectTitle, setProjectTitle] = useState('');
  const [projectDescription, setProjectDescription] = useState('');

  // Delete confirm
  const [deleteTarget, setDeleteTarget] = useState<{
    type: 'goal' | 'project';
    id: string;
    name: string;
  } | null>(null);

  // Collapsed goals
  const [collapsedGoals, setCollapsedGoals] = useState<Set<string>>(new Set());

  // Dropdown menus
  const [openMenu, setOpenMenu] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    try {
      await fetchAllProjects();
    } finally {
      setLoading(false);
    }
  }, [fetchAllProjects]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const toggleGoalCollapse = (goalId: string) => {
    setCollapsedGoals((prev) => {
      const next = new Set(prev);
      if (next.has(goalId)) next.delete(goalId);
      else next.add(goalId);
      return next;
    });
  };

  // Goal CRUD
  const handleSaveGoal = async () => {
    if (!goalTitle.trim()) return;
    try {
      if (editingGoal) {
        await updateGoal(editingGoal.id, {
          title: goalTitle,
          description: goalDescription || undefined,
          color: goalColor,
        });
        toast.success('Goal updated');
      } else {
        await createGoal({
          title: goalTitle,
          description: goalDescription || undefined,
          color: goalColor,
        });
        toast.success('Goal created');
      }
      setGoalModalOpen(false);
      resetGoalForm();
      loadData();
    } catch {
      toast.error('Failed to save goal');
    }
  };

  const resetGoalForm = () => {
    setGoalTitle('');
    setGoalDescription('');
    setGoalColor(PROJECT_COLORS[0]);
    setEditingGoal(null);
  };

  const openEditGoal = (goal: Goal) => {
    setEditingGoal(goal);
    setGoalTitle(goal.title);
    setGoalDescription(goal.description || '');
    setGoalColor(goal.color || PROJECT_COLORS[0]);
    setGoalModalOpen(true);
    setOpenMenu(null);
  };

  // Project CRUD
  const handleSaveProject = async () => {
    if (!projectTitle.trim() || !projectGoalId) return;
    try {
      await createProject(projectGoalId, {
        title: projectTitle,
        description: projectDescription || undefined,
      });
      toast.success('Project created');
      setProjectModalOpen(false);
      setProjectTitle('');
      setProjectDescription('');
      setProjectGoalId(null);
      loadData();
    } catch {
      toast.error('Failed to create project');
    }
  };

  // Delete
  const handleDelete = async () => {
    if (!deleteTarget) return;
    try {
      if (deleteTarget.type === 'goal') {
        await deleteGoal(Number(deleteTarget.id));
        toast.success('Goal deleted');
      } else {
        await deleteProject(deleteTarget.id);
        toast.success('Project deleted');
      }
      setDeleteTarget(null);
      loadData();
    } catch {
      toast.error('Failed to delete');
    }
  };

  const getProjectsForGoal = (goalId: string) =>
    projects.filter((p) => p.goal_id === goalId);

  if (loading) {
    return (
      <div className="flex h-64 items-center justify-center">
        <Spinner size="lg" />
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-4xl">
      {/* Header */}
      <div className="mb-6 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <FolderKanban className="h-7 w-7 text-[var(--color-accent)]" />
          <h1 className="text-2xl font-bold text-[var(--color-text-primary)]">
            Projects
          </h1>
        </div>
        <div className="flex items-center gap-2">
          <Button
            variant="secondary"
            size="sm"
            onClick={() => {
              resetGoalForm();
              setGoalModalOpen(true);
            }}
          >
            <Target className="h-4 w-4" />
            New Goal
          </Button>
        </div>
      </div>

      {goals.length === 0 ? (
        <EmptyState
          icon={<FolderKanban className="h-8 w-8" />}
          title="No Projects Yet"
          description="Create a goal first, then add projects under it."
          actionLabel="New Goal"
          onAction={() => {
            resetGoalForm();
            setGoalModalOpen(true);
          }}
        />
      ) : (
        <div className="flex flex-col gap-4">
          {goals.map((goal) => {
            const goalProjects = getProjectsForGoal(String(goal.id));
            const isCollapsed = collapsedGoals.has(String(goal.id));

            return (
              <div
                key={goal.id}
                className="overflow-hidden rounded-xl border border-[var(--color-border)] bg-[var(--color-bg-card)]"
              >
                {/* Goal header */}
                <div className="flex items-center gap-2 px-4 py-3 border-b border-[var(--color-border)]">
                  <button
                    onClick={() => toggleGoalCollapse(String(goal.id))}
                    className="shrink-0 text-[var(--color-text-secondary)] hover:text-[var(--color-text-primary)]"
                  >
                    {isCollapsed ? (
                      <ChevronRight className="h-4 w-4" />
                    ) : (
                      <ChevronDown className="h-4 w-4" />
                    )}
                  </button>
                  <span
                    className="h-3 w-3 rounded-full shrink-0"
                    style={{
                      backgroundColor: goal.color || 'var(--color-accent)',
                    }}
                  />
                  <div className="flex-1 min-w-0">
                    <h3 className="text-sm font-semibold text-[var(--color-text-primary)] truncate">
                      {goal.title}
                    </h3>
                    {goal.description && (
                      <p className="text-xs text-[var(--color-text-secondary)] truncate">
                        {goal.description}
                      </p>
                    )}
                  </div>
                  <span className="rounded-full bg-[var(--color-bg-secondary)] px-2 py-0.5 text-xs text-[var(--color-text-secondary)]">
                    {goalProjects.length} project
                    {goalProjects.length !== 1 ? 's' : ''}
                  </span>
                  <div className="relative">
                    <button
                      onClick={() =>
                        setOpenMenu(
                          openMenu === `goal-${goal.id}`
                            ? null
                            : `goal-${goal.id}`,
                        )
                      }
                      className="rounded-md p-1 text-[var(--color-text-secondary)] hover:bg-[var(--color-bg-secondary)] hover:text-[var(--color-text-primary)]"
                    >
                      <MoreVertical className="h-4 w-4" />
                    </button>
                    {openMenu === `goal-${goal.id}` && (
                      <>
                        <div
                          className="fixed inset-0 z-40"
                          onClick={() => setOpenMenu(null)}
                        />
                        <div className="absolute right-0 top-full z-50 mt-1 w-44 rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-card)] p-1 shadow-lg">
                          <button
                            onClick={() => {
                              setProjectGoalId(String(goal.id));
                              setProjectModalOpen(true);
                              setOpenMenu(null);
                            }}
                            className="flex w-full items-center gap-2 rounded-md px-3 py-2 text-sm text-[var(--color-text-primary)] hover:bg-[var(--color-bg-secondary)]"
                          >
                            <Plus className="h-4 w-4" />
                            Add Project
                          </button>
                          <button
                            onClick={() => openEditGoal(goal)}
                            className="flex w-full items-center gap-2 rounded-md px-3 py-2 text-sm text-[var(--color-text-primary)] hover:bg-[var(--color-bg-secondary)]"
                          >
                            <Edit2 className="h-4 w-4" />
                            Edit Goal
                          </button>
                          <button
                            onClick={() => {
                              setDeleteTarget({
                                type: 'goal',
                                id: String(goal.id),
                                name: goal.title,
                              });
                              setOpenMenu(null);
                            }}
                            className="flex w-full items-center gap-2 rounded-md px-3 py-2 text-sm text-red-500 hover:bg-[var(--color-bg-secondary)]"
                          >
                            <Trash2 className="h-4 w-4" />
                            Delete Goal
                          </button>
                        </div>
                      </>
                    )}
                  </div>
                </div>

                {/* Projects grid */}
                {!isCollapsed && (
                  <div className="grid grid-cols-1 gap-3 p-4 sm:grid-cols-2 lg:grid-cols-3">
                    {goalProjects.map((project) => (
                      <ProjectCard
                        key={project.id}
                        project={project}
                        goalColor={goal.color || 'var(--color-accent)'}
                        onClick={() => navigate(`/projects/${project.id}`)}
                        onDelete={() =>
                          setDeleteTarget({
                            type: 'project',
                            id: project.id,
                            name: project.title,
                          })
                        }
                      />
                    ))}
                    <button
                      onClick={() => {
                        setProjectGoalId(String(goal.id));
                        setProjectModalOpen(true);
                      }}
                      className="flex min-h-[120px] items-center justify-center rounded-lg border-2 border-dashed border-[var(--color-border)] text-[var(--color-text-secondary)] transition-colors hover:border-[var(--color-accent)] hover:text-[var(--color-accent)]"
                    >
                      <div className="flex flex-col items-center gap-1">
                        <Plus className="h-6 w-6" />
                        <span className="text-xs font-medium">New Project</span>
                      </div>
                    </button>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}

      {/* Goal Modal */}
      <Modal
        isOpen={goalModalOpen}
        onClose={() => {
          setGoalModalOpen(false);
          resetGoalForm();
        }}
        title={editingGoal ? 'Edit Goal' : 'New Goal'}
        size="sm"
        footer={
          <div className="flex justify-end gap-2">
            <Button
              variant="ghost"
              onClick={() => {
                setGoalModalOpen(false);
                resetGoalForm();
              }}
            >
              Cancel
            </Button>
            <Button onClick={handleSaveGoal} disabled={!goalTitle.trim()}>
              {editingGoal ? 'Save' : 'Create'}
            </Button>
          </div>
        }
      >
        <div className="flex flex-col gap-4">
          <div>
            <label className="mb-1 block text-sm font-medium text-[var(--color-text-primary)]">
              Title
            </label>
            <input
              type="text"
              value={goalTitle}
              onChange={(e) => setGoalTitle(e.target.value)}
              placeholder="Goal name..."
              className="w-full rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-3 py-2 text-sm text-[var(--color-text-primary)] outline-none focus:border-[var(--color-accent)]"
              autoFocus
            />
          </div>
          <div>
            <label className="mb-1 block text-sm font-medium text-[var(--color-text-primary)]">
              Description
            </label>
            <textarea
              value={goalDescription}
              onChange={(e) => setGoalDescription(e.target.value)}
              placeholder="Optional description..."
              rows={2}
              className="w-full rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-3 py-2 text-sm text-[var(--color-text-primary)] outline-none focus:border-[var(--color-accent)]"
            />
          </div>
          <div>
            <label className="mb-1 block text-sm font-medium text-[var(--color-text-primary)]">
              Color
            </label>
            <div className="flex flex-wrap gap-2">
              {PROJECT_COLORS.map((c) => (
                <button
                  key={c}
                  onClick={() => setGoalColor(c)}
                  className={`h-7 w-7 rounded-full transition-transform ${
                    goalColor === c ? 'scale-110 ring-2 ring-offset-1' : ''
                  }`}
                  style={{ backgroundColor: c, outlineColor: c }}
                />
              ))}
            </div>
          </div>
        </div>
      </Modal>

      {/* Project Modal */}
      <Modal
        isOpen={projectModalOpen}
        onClose={() => {
          setProjectModalOpen(false);
          setProjectTitle('');
          setProjectDescription('');
        }}
        title="New Project"
        size="sm"
        footer={
          <div className="flex justify-end gap-2">
            <Button
              variant="ghost"
              onClick={() => setProjectModalOpen(false)}
            >
              Cancel
            </Button>
            <Button
              onClick={handleSaveProject}
              disabled={!projectTitle.trim()}
            >
              Create
            </Button>
          </div>
        }
      >
        <div className="flex flex-col gap-4">
          <div>
            <label className="mb-1 block text-sm font-medium text-[var(--color-text-primary)]">
              Title
            </label>
            <input
              type="text"
              value={projectTitle}
              onChange={(e) => setProjectTitle(e.target.value)}
              placeholder="Project name..."
              className="w-full rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-3 py-2 text-sm text-[var(--color-text-primary)] outline-none focus:border-[var(--color-accent)]"
              autoFocus
            />
          </div>
          <div>
            <label className="mb-1 block text-sm font-medium text-[var(--color-text-primary)]">
              Description
            </label>
            <textarea
              value={projectDescription}
              onChange={(e) => setProjectDescription(e.target.value)}
              placeholder="Optional description..."
              rows={2}
              className="w-full rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-3 py-2 text-sm text-[var(--color-text-primary)] outline-none focus:border-[var(--color-accent)]"
            />
          </div>
        </div>
      </Modal>

      {/* Delete Confirm */}
      <ConfirmDialog
        isOpen={!!deleteTarget}
        onClose={() => setDeleteTarget(null)}
        onConfirm={handleDelete}
        title={`Delete ${deleteTarget?.type === 'goal' ? 'Goal' : 'Project'}`}
        message={`Are you sure you want to delete "${deleteTarget?.name}"? ${
          deleteTarget?.type === 'goal'
            ? 'All projects and tasks under this goal will be deleted.'
            : 'All tasks in this project will be deleted.'
        }`}
        confirmLabel="Delete"
        variant="danger"
      />
    </div>
  );
}

// Project Card
function ProjectCard({
  project,
  goalColor,
  onClick,
  onDelete,
}: {
  project: Project;
  goalColor: string;
  onClick: () => void;
  onDelete: () => void;
}) {
  const statusColors: Record<string, string> = {
    active: '#22c55e',
    completed: '#3b82f6',
    on_hold: '#f59e0b',
    archived: '#6b7280',
  };

  return (
    <div
      className="group relative flex cursor-pointer flex-col rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-primary)] p-4 transition-colors hover:border-[var(--color-accent)]/30 hover:bg-[var(--color-bg-secondary)]"
      onClick={onClick}
    >
      {/* Color bar */}
      <div
        className="absolute left-0 top-0 h-full w-1 rounded-l-lg"
        style={{ backgroundColor: goalColor }}
      />

      <div className="flex items-start justify-between">
        <h4 className="text-sm font-semibold text-[var(--color-text-primary)] truncate pr-2">
          {project.title}
        </h4>
        <span
          className="shrink-0 rounded-full px-2 py-0.5 text-xs font-medium"
          style={{
            color: statusColors[project.status] || '#6b7280',
            backgroundColor: `${statusColors[project.status] || '#6b7280'}15`,
          }}
        >
          {project.status.charAt(0).toUpperCase() +
            project.status.slice(1).replace('_', ' ')}
        </span>
      </div>

      {project.description && (
        <p className="mt-1 text-xs text-[var(--color-text-secondary)] line-clamp-2">
          {project.description}
        </p>
      )}

      <div className="mt-auto pt-3">
        <button
          onClick={(e) => {
            e.stopPropagation();
            onDelete();
          }}
          className="text-xs text-[var(--color-text-secondary)] opacity-0 hover:text-red-500 group-hover:opacity-100 transition-all"
        >
          Delete
        </button>
      </div>
    </div>
  );
}
