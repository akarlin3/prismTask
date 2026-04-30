import { useEffect, useState, useMemo, useCallback } from 'react';
import {
  Archive,
  Search,
  RotateCcw,
  Trash2,
  AlertTriangle,
  CheckSquare,
  XCircle,
} from 'lucide-react';
import { toast } from 'sonner';
import { Spinner } from '@/components/ui/Spinner';
import { EmptyState } from '@/components/ui/EmptyState';
import { Button } from '@/components/ui/Button';
import { Checkbox } from '@/components/ui/Checkbox';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';
import { Modal } from '@/components/ui/Modal';
import { useTaskStore } from '@/stores/taskStore';
import { useProjectStore } from '@/stores/projectStore';
import { PRIORITY_CONFIG } from '@/utils/priority';
import { formatRelative } from '@/utils/dates';
import type { Task, TaskPriority } from '@/types/task';
import apiClient from '@/api/client';

type SortField = 'completed_at' | 'title' | 'priority' | 'project';

export function ArchiveScreen() {
  const { updateTask, deleteTask } = useTaskStore();
  const { projects, fetchAllProjects } = useProjectStore();

  const [archivedTasks, setArchivedTasks] = useState<Task[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  const [sortBy, setSortBy] = useState<SortField>('completed_at');
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [deleteTarget, setDeleteTarget] = useState<Task | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [bulkDeleteOpen, setBulkDeleteOpen] = useState(false);
  const [deleteAllOpen, setDeleteAllOpen] = useState(false);
  const [deleteAllConfirmText, setDeleteAllConfirmText] = useState('');
  const [bulkAction, setBulkAction] = useState(false);

  const fetchArchived = useCallback(async () => {
    setIsLoading(true);
    try {
      // Fetch all projects first, then get their tasks
      await fetchAllProjects();
      const currentProjects = useProjectStore.getState().projects;
      const allTasks: Task[] = [];
      for (const project of currentProjects) {
        try {
          const resp = await apiClient.get(`/projects/${project.id}/tasks`);
          const tasks: Task[] = resp.data;
          const archived = tasks.filter(
            (t: Task) => t.status === 'done' || t.status === 'cancelled',
          );
          allTasks.push(...archived);
        } catch {
          // Skip projects that fail
        }
      }
      setArchivedTasks(allTasks);
    } catch {
      toast.error('Failed to load archived tasks');
    } finally {
      setIsLoading(false);
    }
  }, [fetchAllProjects]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- data-fetch effect: load archived items on mount
    fetchArchived();
  }, [fetchArchived]);

  const projectMap = useMemo(() => {
    const map: Record<string, string> = {};
    projects.forEach((p) => {
      map[p.id] = p.title;
    });
    return map;
  }, [projects]);

  const filtered = useMemo(() => {
    let result = archivedTasks;
    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase();
      result = result.filter(
        (t) =>
          t.title.toLowerCase().includes(q) ||
          t.description?.toLowerCase().includes(q) ||
          projectMap[t.project_id]?.toLowerCase().includes(q),
      );
    }
    return [...result].sort((a, b) => {
      switch (sortBy) {
        case 'completed_at':
          return (
            new Date(b.completed_at || b.updated_at).getTime() -
            new Date(a.completed_at || a.updated_at).getTime()
          );
        case 'title':
          return a.title.localeCompare(b.title);
        case 'priority':
          return a.priority - b.priority;
        case 'project':
          return (projectMap[a.project_id] || '').localeCompare(
            projectMap[b.project_id] || '',
          );
        default:
          return 0;
      }
    });
  }, [archivedTasks, searchQuery, sortBy, projectMap]);

  const toggleSelect = (id: string) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const selectAll = () => {
    if (selectedIds.size === filtered.length) {
      setSelectedIds(new Set());
    } else {
      setSelectedIds(new Set(filtered.map((t) => t.id)));
    }
  };

  const handleRestore = async (task: Task) => {
    try {
      await updateTask(task.id, { status: 'todo' });
      setArchivedTasks((prev) => prev.filter((t) => t.id !== task.id));
      toast.success(`"${task.title}" restored`);
    } catch {
      toast.error('Failed to restore task');
    }
  };

  const handleDelete = async () => {
    if (!deleteTarget) return;
    setDeleting(true);
    try {
      await deleteTask(deleteTarget.id);
      setArchivedTasks((prev) => prev.filter((t) => t.id !== deleteTarget.id));
      toast.success('Task permanently deleted');
    } catch {
      toast.error('Failed to delete task');
    } finally {
      setDeleting(false);
      setDeleteTarget(null);
    }
  };

  const handleBulkRestore = async () => {
    setBulkAction(true);
    try {
      const ids = Array.from(selectedIds);
      await Promise.all(ids.map((id) => updateTask(id, { status: 'todo' })));
      setArchivedTasks((prev) => prev.filter((t) => !selectedIds.has(t.id)));
      setSelectedIds(new Set());
      toast.success(`${ids.length} task(s) restored`);
    } catch {
      toast.error('Failed to restore tasks');
    } finally {
      setBulkAction(false);
    }
  };

  const handleBulkDelete = async () => {
    setBulkAction(true);
    try {
      const ids = Array.from(selectedIds);
      await Promise.all(ids.map((id) => deleteTask(id)));
      setArchivedTasks((prev) => prev.filter((t) => !selectedIds.has(t.id)));
      setSelectedIds(new Set());
      toast.success(`${ids.length} task(s) permanently deleted`);
    } catch {
      toast.error('Failed to delete tasks');
    } finally {
      setBulkAction(false);
      setBulkDeleteOpen(false);
    }
  };

  const handleDeleteAll = async () => {
    if (deleteAllConfirmText !== 'DELETE') return;
    setBulkAction(true);
    try {
      await Promise.all(archivedTasks.map((t) => deleteTask(t.id)));
      setArchivedTasks([]);
      setSelectedIds(new Set());
      toast.success('All archived tasks deleted');
    } catch {
      toast.error('Failed to delete all tasks');
    } finally {
      setBulkAction(false);
      setDeleteAllOpen(false);
      setDeleteAllConfirmText('');
    }
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-20">
        <Spinner size="lg" text="Loading archive..." />
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-4xl">
      {/* Header */}
      <div className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-center gap-3">
          <Archive className="h-7 w-7 text-[var(--color-accent)]" />
          <h1 className="text-2xl font-bold text-[var(--color-text-primary)]">
            Archive
          </h1>
          <span className="rounded-full bg-[var(--color-bg-secondary)] px-2 py-0.5 text-xs font-medium text-[var(--color-text-secondary)]">
            {archivedTasks.length} task{archivedTasks.length !== 1 ? 's' : ''}
          </span>
        </div>
        {archivedTasks.length > 0 && (
          <Button
            variant="danger"
            size="sm"
            onClick={() => setDeleteAllOpen(true)}
          >
            <Trash2 className="h-4 w-4" />
            Delete All Archived
          </Button>
        )}
      </div>

      {/* Search & Sort */}
      <div className="mb-4 flex flex-col gap-3 sm:flex-row">
        <div className="relative flex-1">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-[var(--color-text-secondary)]" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search archived tasks..."
            className="w-full rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-card)] py-2 pl-10 pr-4 text-sm text-[var(--color-text-primary)] outline-none focus:border-[var(--color-accent)]"
          />
        </div>
        <select
          value={sortBy}
          onChange={(e) => setSortBy(e.target.value as SortField)}
          className="rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-card)] px-3 py-2 text-sm text-[var(--color-text-primary)] outline-none focus:border-[var(--color-accent)]"
        >
          <option value="completed_at">Sort by Completion Date</option>
          <option value="title">Sort by Title</option>
          <option value="priority">Sort by Priority</option>
          <option value="project">Sort by Project</option>
        </select>
      </div>

      {/* Bulk Actions */}
      {selectedIds.size > 0 && (
        <div className="mb-4 flex items-center gap-3 rounded-lg border border-[var(--color-accent)]/30 bg-[var(--color-accent)]/5 px-4 py-2">
          <span className="text-sm font-medium text-[var(--color-text-primary)]">
            {selectedIds.size} selected
          </span>
          <div className="flex gap-2">
            <Button
              variant="secondary"
              size="sm"
              onClick={handleBulkRestore}
              loading={bulkAction}
            >
              <RotateCcw className="h-3.5 w-3.5" />
              Restore
            </Button>
            <Button
              variant="danger"
              size="sm"
              onClick={() => setBulkDeleteOpen(true)}
              loading={bulkAction}
            >
              <Trash2 className="h-3.5 w-3.5" />
              Delete
            </Button>
          </div>
          <button
            onClick={() => setSelectedIds(new Set())}
            className="ml-auto text-xs text-[var(--color-text-secondary)] hover:text-[var(--color-text-primary)]"
          >
            Clear Selection
          </button>
        </div>
      )}

      {/* Task List */}
      {archivedTasks.length === 0 ? (
        <EmptyState
          icon={<Archive className="h-8 w-8" />}
          title="Archive is Empty"
          description="Completed and cancelled tasks will appear here."
        />
      ) : filtered.length === 0 ? (
        <EmptyState
          icon={<Search className="h-8 w-8" />}
          title="No Matching Tasks"
          description="Try a different search term."
        />
      ) : (
        <div className="rounded-xl border border-[var(--color-border)] bg-[var(--color-bg-card)] divide-y divide-[var(--color-border)]">
          {/* Select All header */}
          <div className="flex items-center gap-3 px-4 py-2 bg-[var(--color-bg-secondary)]">
            <Checkbox
              checked={selectedIds.size === filtered.length && filtered.length > 0}
              onChange={selectAll}
            />
            <span className="text-xs font-medium text-[var(--color-text-secondary)]">
              Select All
            </span>
          </div>

          {filtered.map((task) => (
            <div
              key={task.id}
              className="flex items-center gap-3 px-4 py-3 hover:bg-[var(--color-bg-secondary)] transition-colors"
            >
              <Checkbox
                checked={selectedIds.has(task.id)}
                onChange={() => toggleSelect(task.id)}
              />

              {/* Status icon */}
              {task.status === 'done' ? (
                <CheckSquare className="h-4 w-4 shrink-0 text-green-500" />
              ) : (
                <XCircle className="h-4 w-4 shrink-0 text-[var(--color-text-secondary)]" />
              )}

              {/* Task info */}
              <div className="flex-1 min-w-0">
                <p className="truncate text-sm text-[var(--color-text-secondary)] line-through">
                  {task.title}
                </p>
                <div className="flex items-center gap-2 mt-0.5">
                  {task.completed_at && (
                    <span className="text-xs text-[var(--color-text-secondary)]">
                      {formatRelative(task.completed_at)}
                    </span>
                  )}
                  {projectMap[task.project_id] && (
                    <span className="text-xs text-[var(--color-text-secondary)]">
                      {projectMap[task.project_id]}
                    </span>
                  )}
                </div>
              </div>

              {/* Priority */}
              {task.priority <= 2 && (
                <span
                  className="rounded px-1.5 py-0.5 text-xs font-medium opacity-60"
                  style={{
                    color: PRIORITY_CONFIG[task.priority as TaskPriority]?.color,
                    backgroundColor: PRIORITY_CONFIG[task.priority as TaskPriority]?.bgColor,
                  }}
                >
                  {PRIORITY_CONFIG[task.priority as TaskPriority]?.label}
                </span>
              )}

              {/* Actions */}
              <div className="flex gap-1 shrink-0">
                <button
                  onClick={() => handleRestore(task)}
                  className="rounded-md p-1.5 text-[var(--color-text-secondary)] hover:bg-[var(--color-bg-secondary)] hover:text-[var(--color-accent)]"
                  title="Restore"
                >
                  <RotateCcw className="h-4 w-4" />
                </button>
                <button
                  onClick={() => setDeleteTarget(task)}
                  className="rounded-md p-1.5 text-[var(--color-text-secondary)] hover:bg-red-50 hover:text-red-500"
                  title="Permanently Delete"
                >
                  <Trash2 className="h-4 w-4" />
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Single Delete */}
      <ConfirmDialog
        isOpen={!!deleteTarget}
        onClose={() => setDeleteTarget(null)}
        onConfirm={handleDelete}
        title="Permanently Delete Task"
        message={`Are you sure you want to permanently delete "${deleteTarget?.title}"? This cannot be undone.`}
        confirmLabel="Delete Forever"
        variant="danger"
        loading={deleting}
      />

      {/* Bulk Delete */}
      <ConfirmDialog
        isOpen={bulkDeleteOpen}
        onClose={() => setBulkDeleteOpen(false)}
        onConfirm={handleBulkDelete}
        title="Delete Selected Tasks"
        message={`Permanently delete ${selectedIds.size} selected task(s)? This cannot be undone.`}
        confirmLabel="Delete All Selected"
        variant="danger"
        loading={bulkAction}
      />

      {/* Delete All */}
      <Modal
        isOpen={deleteAllOpen}
        onClose={() => {
          setDeleteAllOpen(false);
          setDeleteAllConfirmText('');
        }}
        title="Delete All Archived Tasks"
        size="sm"
        footer={
          <div className="flex justify-end gap-3">
            <Button
              variant="ghost"
              onClick={() => {
                setDeleteAllOpen(false);
                setDeleteAllConfirmText('');
              }}
            >
              Cancel
            </Button>
            <Button
              variant="danger"
              onClick={handleDeleteAll}
              disabled={deleteAllConfirmText !== 'DELETE'}
              loading={bulkAction}
            >
              Delete Everything
            </Button>
          </div>
        }
      >
        <div className="flex flex-col gap-4">
          <div className="flex items-start gap-3 rounded-lg border border-red-200 bg-red-50 p-3">
            <AlertTriangle className="h-5 w-5 shrink-0 text-red-500" />
            <p className="text-sm text-red-700">
              This will permanently delete all {archivedTasks.length} archived task(s).
              This action cannot be undone.
            </p>
          </div>
          <div>
            <label className="mb-1 block text-sm font-medium text-[var(--color-text-primary)]">
              Type DELETE to confirm
            </label>
            <input
              type="text"
              value={deleteAllConfirmText}
              onChange={(e) => setDeleteAllConfirmText(e.target.value)}
              placeholder="DELETE"
              className="w-full rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-3 py-2 text-sm text-[var(--color-text-primary)] outline-none focus:border-red-500"
              autoFocus
            />
          </div>
        </div>
      </Modal>
    </div>
  );
}
