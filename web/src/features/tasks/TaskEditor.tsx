import { useState, useEffect, useCallback, useRef } from 'react';
import {
  FileText,
  CalendarDays,
  FolderKanban,
  Trash2,
  Copy,
  Loader2,
  Check,
  Plus,
  GripVertical,
  X,
} from 'lucide-react';
import { toast } from 'sonner';
import { Drawer } from '@/components/ui/Drawer';
import { Tabs } from '@/components/ui/Tabs';
import { Button } from '@/components/ui/Button';
import { Checkbox } from '@/components/ui/Checkbox';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';
import { useTaskStore } from '@/stores/taskStore';
import { useProjectStore } from '@/stores/projectStore';
import { useTagStore } from '@/stores/tagStore';
import { PRIORITY_CONFIG } from '@/utils/priority';
import { formatRelative } from '@/utils/dates';
import type { Task, TaskPriority, TaskStatus, TaskUpdate } from '@/types/task';

interface TaskEditorProps {
  onClose: () => void;
  onUpdate?: () => void;
  mode?: 'edit' | 'create';
  defaultProjectId?: number;
}

const TABS = [
  { key: 'details', label: 'Details', icon: <FileText className="h-4 w-4" /> },
  {
    key: 'schedule',
    label: 'Schedule',
    icon: <CalendarDays className="h-4 w-4" />,
  },
  {
    key: 'organize',
    label: 'Organize',
    icon: <FolderKanban className="h-4 w-4" />,
  },
];

const STATUS_OPTIONS: { value: TaskStatus; label: string }[] = [
  { value: 'todo', label: 'To Do' },
  { value: 'in_progress', label: 'In Progress' },
  { value: 'done', label: 'Done' },
  { value: 'cancelled', label: 'Cancelled' },
];

const REMINDER_OPTIONS = [
  { value: '', label: 'No Reminder' },
  { value: '15', label: '15 Minutes Before' },
  { value: '30', label: '30 Minutes Before' },
  { value: '60', label: '1 Hour Before' },
  { value: '120', label: '2 Hours Before' },
  { value: '1440', label: '1 Day Before' },
];

const RECURRENCE_TYPES = [
  { value: '', label: 'None' },
  { value: 'daily', label: 'Daily' },
  { value: 'weekly', label: 'Weekly' },
  { value: 'monthly', label: 'Monthly' },
  { value: 'yearly', label: 'Yearly' },
];

const TAG_COLORS = [
  '#ef4444', '#f97316', '#f59e0b', '#eab308',
  '#84cc16', '#22c55e', '#14b8a6', '#06b6d4',
  '#3b82f6', '#6366f1', '#a855f7', '#ec4899',
];

export default function TaskEditor({
  onClose,
  onUpdate,
  mode = 'edit',
  defaultProjectId,
}: TaskEditorProps) {
  const {
    selectedTask,
    updateTask,
    deleteTask,
    createTask,
    createSubtask,
    fetchTask,
  } = useTaskStore();
  const { projects, fetchAllProjects } = useProjectStore();
  const { tags, fetchTags, createTag } = useTagStore();

  const [activeTab, setActiveTab] = useState('details');
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [deleting, setDeleting] = useState(false);

  // Form state
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [priority, setPriority] = useState<TaskPriority>(3);
  const [status, setStatus] = useState<TaskStatus>('todo');
  const [dueDate, setDueDate] = useState('');
  const [dueTime, setDueTime] = useState('');
  const [projectId, setProjectId] = useState<number | null>(null);
  const [notes, setNotes] = useState('');
  const [duration, setDuration] = useState('');
  const [recurrenceType, setRecurrenceType] = useState('');
  const [recurrenceInterval, setRecurrenceInterval] = useState(1);

  // Subtasks
  const [subtasks, setSubtasks] = useState<Task[]>([]);
  const [newSubtaskTitle, setNewSubtaskTitle] = useState('');

  // Tags
  const [taskTagIds, setTaskTagIds] = useState<number[]>([]);
  const [showNewTag, setShowNewTag] = useState(false);
  const [newTagName, setNewTagName] = useState('');
  const [newTagColor, setNewTagColor] = useState(TAG_COLORS[0]);

  const saveTimerRef = useRef<ReturnType<typeof setTimeout>>(undefined);
  const isCreate = mode === 'create';
  const task = selectedTask;

  // Load project and tag data
  useEffect(() => {
    fetchAllProjects();
    fetchTags();
  }, [fetchAllProjects, fetchTags]);

  // Initialize form from task
  useEffect(() => {
    if (isCreate) {
      setTitle('');
      setDescription('');
      setPriority(3);
      setStatus('todo');
      setDueDate('');
      setDueTime('');
      setProjectId(defaultProjectId ?? null);
      setNotes('');
      setSubtasks([]);
      setTaskTagIds([]);
      return;
    }
    if (!task) return;
    setTitle(task.title);
    setDescription(task.description || '');
    setPriority(task.priority);
    setStatus(task.status);
    setDueDate(task.due_date || '');
    setDueTime(task.due_time || '');
    setProjectId(task.project_id);
    setNotes(task.notes || '');
    setDuration(task.estimated_duration?.toString() || '');
    setSubtasks(task.subtasks || []);
    setTaskTagIds(task.tags?.map((t) => t.id) || []);

    if (task.recurrence_json) {
      try {
        const rule = JSON.parse(task.recurrence_json);
        setRecurrenceType(rule.type || '');
        setRecurrenceInterval(rule.interval || 1);
      } catch {
        // ignore parse errors
      }
    }
  }, [task, isCreate, defaultProjectId]);

  // Auto-save debounced (edit mode only)
  const autoSave = useCallback(
    (data: TaskUpdate) => {
      if (isCreate || !task) return;
      if (saveTimerRef.current) clearTimeout(saveTimerRef.current);
      saveTimerRef.current = setTimeout(async () => {
        setSaving(true);
        try {
          await updateTask(task.id, data);
          setSaved(true);
          setTimeout(() => setSaved(false), 2000);
          onUpdate?.();
        } catch {
          toast.error('Failed to save changes');
        } finally {
          setSaving(false);
        }
      }, 1000);
    },
    [isCreate, task, updateTask, onUpdate],
  );

  useEffect(() => {
    return () => {
      if (saveTimerRef.current) clearTimeout(saveTimerRef.current);
    };
  }, []);

  const handleTitleChange = (v: string) => {
    setTitle(v);
    autoSave({ title: v });
  };

  const handleDescriptionChange = (v: string) => {
    setDescription(v);
    autoSave({ description: v });
  };

  const handlePriorityChange = (p: TaskPriority) => {
    setPriority(p);
    autoSave({ priority: p });
  };

  const handleStatusChange = (s: TaskStatus) => {
    setStatus(s);
    autoSave({ status: s });
  };

  const handleDueDateChange = (v: string) => {
    setDueDate(v);
    autoSave({ due_date: v || undefined });
  };

  const handleDueTimeChange = (v: string) => {
    setDueTime(v);
    // due_time not in TaskUpdate type yet, save as part of auto-save
  };

  const handleCreate = async () => {
    if (!title.trim()) {
      toast.error('Title is required');
      return;
    }
    const targetProjectId = projectId || projects[0]?.id;
    if (!targetProjectId) {
      toast.error('No project available. Create a project first.');
      return;
    }
    setSaving(true);
    try {
      await createTask(targetProjectId, {
        title: title.trim(),
        description: description || undefined,
        priority,
        status,
        due_date: dueDate || undefined,
      });
      toast.success('Task created');
      onUpdate?.();
      onClose();
    } catch {
      toast.error('Failed to create task');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async () => {
    if (!task) return;
    setDeleting(true);
    try {
      await deleteTask(task.id);
      toast.success('Task deleted');
      onUpdate?.();
      onClose();
    } catch {
      toast.error('Failed to delete task');
    } finally {
      setDeleting(false);
      setDeleteOpen(false);
    }
  };

  const handleDuplicate = async () => {
    if (!task) return;
    const targetProjectId = task.project_id || projects[0]?.id;
    if (!targetProjectId) return;
    try {
      await createTask(targetProjectId, {
        title: `${task.title} (copy)`,
        description: task.description || undefined,
        priority: task.priority,
        due_date: task.due_date || undefined,
      });
      toast.success('Task duplicated');
      onUpdate?.();
    } catch {
      toast.error('Failed to duplicate task');
    }
  };

  const handleAddSubtask = async () => {
    if (!newSubtaskTitle.trim() || !task) return;
    try {
      const subtask = await createSubtask(task.id, {
        title: newSubtaskTitle.trim(),
      });
      setSubtasks((prev) => [...prev, subtask]);
      setNewSubtaskTitle('');
      // Re-fetch to get updated parent
      await fetchTask(task.id);
    } catch {
      toast.error('Failed to add subtask');
    }
  };

  const handleToggleSubtask = async (subtask: Task) => {
    const newStatus = subtask.status === 'done' ? 'todo' : 'done';
    try {
      await updateTask(subtask.id, { status: newStatus });
      setSubtasks((prev) =>
        prev.map((s) =>
          s.id === subtask.id ? { ...s, status: newStatus } : s,
        ),
      );
    } catch {
      toast.error('Failed to update subtask');
    }
  };

  const handleDeleteSubtask = async (subtaskId: number) => {
    try {
      await deleteTask(subtaskId);
      setSubtasks((prev) => prev.filter((s) => s.id !== subtaskId));
    } catch {
      toast.error('Failed to delete subtask');
    }
  };

  const handleCreateTag = async () => {
    if (!newTagName.trim()) return;
    try {
      const tag = await createTag({
        name: newTagName.trim(),
        color: newTagColor,
      });
      setTaskTagIds((prev) => [...prev, tag.id]);
      setNewTagName('');
      setShowNewTag(false);
    } catch {
      toast.error('Failed to create tag');
    }
  };

  const toggleTag = (tagId: number) => {
    setTaskTagIds((prev) =>
      prev.includes(tagId) ? prev.filter((id) => id !== tagId) : [...prev, tagId],
    );
  };

  const drawerTitle = (
    <div className="flex items-center gap-2">
      <span>{isCreate ? 'New Task' : 'Edit Task'}</span>
      {saving && (
        <span className="flex items-center gap-1 text-xs text-[var(--color-text-secondary)]">
          <Loader2 className="h-3 w-3 animate-spin" />
          Saving...
        </span>
      )}
      {saved && !saving && (
        <span className="flex items-center gap-1 text-xs text-green-500">
          <Check className="h-3 w-3" />
          Saved
        </span>
      )}
    </div>
  );

  const subtasksDone = subtasks.filter((s) => s.status === 'done').length;
  const subtaskProgress =
    subtasks.length > 0
      ? Math.round((subtasksDone / subtasks.length) * 100)
      : 0;

  return (
    <>
      <Drawer isOpen onClose={onClose} title={drawerTitle as unknown as string}>
        <div className="flex h-full flex-col">
          <Tabs
            tabs={TABS}
            activeTab={activeTab}
            onChange={setActiveTab}
            className="mb-4 shrink-0"
          />

          <div className="flex-1 overflow-y-auto">
            {/* Details Tab */}
            {activeTab === 'details' && (
              <div className="flex flex-col gap-4">
                {/* Title */}
                <div>
                  <input
                    type="text"
                    value={title}
                    onChange={(e) => handleTitleChange(e.target.value)}
                    placeholder="Task title..."
                    className="w-full border-none bg-transparent text-lg font-semibold text-[var(--color-text-primary)] outline-none placeholder-[var(--color-text-secondary)]"
                    autoFocus={isCreate}
                  />
                </div>

                {/* Description */}
                <div>
                  <label className="mb-1 block text-xs font-medium text-[var(--color-text-secondary)]">
                    Description
                  </label>
                  <textarea
                    value={description}
                    onChange={(e) => handleDescriptionChange(e.target.value)}
                    placeholder="Add description..."
                    rows={3}
                    className="w-full rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-3 py-2 text-sm text-[var(--color-text-primary)] outline-none focus:border-[var(--color-accent)]"
                  />
                </div>

                {/* Priority */}
                <div>
                  <label className="mb-1 block text-xs font-medium text-[var(--color-text-secondary)]">
                    Priority
                  </label>
                  <div className="flex gap-2">
                    {([1, 2, 3, 4] as TaskPriority[]).map((p) => (
                      <button
                        key={p}
                        onClick={() => handlePriorityChange(p)}
                        className={`flex-1 rounded-lg border px-3 py-2 text-xs font-medium transition-colors ${
                          priority === p
                            ? 'border-current'
                            : 'border-[var(--color-border)]'
                        }`}
                        style={{
                          color:
                            priority === p
                              ? PRIORITY_CONFIG[p].color
                              : 'var(--color-text-secondary)',
                          backgroundColor:
                            priority === p
                              ? PRIORITY_CONFIG[p].bgColor
                              : 'transparent',
                        }}
                      >
                        {PRIORITY_CONFIG[p].label}
                      </button>
                    ))}
                  </div>
                </div>

                {/* Status */}
                <div>
                  <label className="mb-1 block text-xs font-medium text-[var(--color-text-secondary)]">
                    Status
                  </label>
                  <select
                    value={status}
                    onChange={(e) =>
                      handleStatusChange(e.target.value as TaskStatus)
                    }
                    className="w-full rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-3 py-2 text-sm text-[var(--color-text-primary)] outline-none focus:border-[var(--color-accent)]"
                  >
                    {STATUS_OPTIONS.map((opt) => (
                      <option key={opt.value} value={opt.value}>
                        {opt.label}
                      </option>
                    ))}
                  </select>
                </div>

                {/* Subtasks */}
                {!isCreate && (
                  <div>
                    <div className="mb-2 flex items-center justify-between">
                      <label className="text-xs font-medium text-[var(--color-text-secondary)]">
                        Subtasks
                      </label>
                      {subtasks.length > 0 && (
                        <span className="text-xs text-[var(--color-text-secondary)]">
                          {subtasksDone}/{subtasks.length}
                        </span>
                      )}
                    </div>

                    {/* Subtask progress bar */}
                    {subtasks.length > 0 && (
                      <div className="mb-2 h-1.5 w-full overflow-hidden rounded-full bg-[var(--color-bg-secondary)]">
                        <div
                          className="h-full rounded-full bg-[var(--color-accent)] transition-all duration-300"
                          style={{ width: `${subtaskProgress}%` }}
                        />
                      </div>
                    )}

                    {/* Subtask list */}
                    <div className="flex flex-col gap-1">
                      {subtasks.map((subtask) => (
                        <div
                          key={subtask.id}
                          className="group flex items-center gap-2 rounded-md px-2 py-1.5 hover:bg-[var(--color-bg-secondary)]"
                        >
                          <GripVertical className="h-3 w-3 shrink-0 cursor-grab text-[var(--color-text-secondary)] opacity-0 group-hover:opacity-100" />
                          <Checkbox
                            checked={subtask.status === 'done'}
                            onChange={() => handleToggleSubtask(subtask)}
                          />
                          <span
                            className={`flex-1 text-sm ${
                              subtask.status === 'done'
                                ? 'text-[var(--color-text-secondary)] line-through'
                                : 'text-[var(--color-text-primary)]'
                            }`}
                          >
                            {subtask.title}
                          </span>
                          <button
                            onClick={() => handleDeleteSubtask(subtask.id)}
                            className="shrink-0 text-[var(--color-text-secondary)] opacity-0 hover:text-red-500 group-hover:opacity-100 transition-colors"
                          >
                            <X className="h-3.5 w-3.5" />
                          </button>
                        </div>
                      ))}
                    </div>

                    {/* Add subtask input */}
                    <div className="mt-1 flex items-center gap-2">
                      <Plus className="h-4 w-4 shrink-0 text-[var(--color-text-secondary)]" />
                      <input
                        type="text"
                        value={newSubtaskTitle}
                        onChange={(e) => setNewSubtaskTitle(e.target.value)}
                        onKeyDown={(e) => {
                          if (e.key === 'Enter') handleAddSubtask();
                        }}
                        placeholder="Add subtask..."
                        className="flex-1 border-none bg-transparent py-1 text-sm text-[var(--color-text-primary)] outline-none placeholder-[var(--color-text-secondary)]"
                      />
                    </div>
                  </div>
                )}
              </div>
            )}

            {/* Schedule Tab */}
            {activeTab === 'schedule' && (
              <div className="flex flex-col gap-4">
                {/* Due Date */}
                <div>
                  <label className="mb-1 block text-xs font-medium text-[var(--color-text-secondary)]">
                    Due Date
                  </label>
                  <input
                    type="date"
                    value={dueDate}
                    onChange={(e) => handleDueDateChange(e.target.value)}
                    className="w-full rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-3 py-2 text-sm text-[var(--color-text-primary)] outline-none focus:border-[var(--color-accent)]"
                  />
                </div>

                {/* Due Time */}
                <div>
                  <label className="mb-1 block text-xs font-medium text-[var(--color-text-secondary)]">
                    Due Time (Optional)
                  </label>
                  <input
                    type="time"
                    value={dueTime}
                    onChange={(e) => handleDueTimeChange(e.target.value)}
                    className="w-full rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-3 py-2 text-sm text-[var(--color-text-primary)] outline-none focus:border-[var(--color-accent)]"
                  />
                </div>

                {/* Reminder */}
                <div>
                  <label className="mb-1 block text-xs font-medium text-[var(--color-text-secondary)]">
                    Reminder
                  </label>
                  <select
                    className="w-full rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-3 py-2 text-sm text-[var(--color-text-primary)] outline-none focus:border-[var(--color-accent)]"
                  >
                    {REMINDER_OPTIONS.map((opt) => (
                      <option key={opt.value} value={opt.value}>
                        {opt.label}
                      </option>
                    ))}
                  </select>
                </div>

                {/* Recurrence */}
                <div>
                  <label className="mb-1 block text-xs font-medium text-[var(--color-text-secondary)]">
                    Recurrence
                  </label>
                  <select
                    value={recurrenceType}
                    onChange={(e) => setRecurrenceType(e.target.value)}
                    className="w-full rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-3 py-2 text-sm text-[var(--color-text-primary)] outline-none focus:border-[var(--color-accent)]"
                  >
                    {RECURRENCE_TYPES.map((opt) => (
                      <option key={opt.value} value={opt.value}>
                        {opt.label}
                      </option>
                    ))}
                  </select>

                  {recurrenceType && (
                    <div className="mt-2">
                      <label className="mb-1 block text-xs font-medium text-[var(--color-text-secondary)]">
                        Every
                      </label>
                      <div className="flex items-center gap-2">
                        <input
                          type="number"
                          min={1}
                          value={recurrenceInterval}
                          onChange={(e) =>
                            setRecurrenceInterval(parseInt(e.target.value) || 1)
                          }
                          className="w-20 rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-3 py-2 text-sm text-[var(--color-text-primary)] outline-none focus:border-[var(--color-accent)]"
                        />
                        <span className="text-sm text-[var(--color-text-secondary)]">
                          {recurrenceType === 'daily'
                            ? 'day(s)'
                            : recurrenceType === 'weekly'
                              ? 'week(s)'
                              : recurrenceType === 'monthly'
                                ? 'month(s)'
                                : 'year(s)'}
                        </span>
                      </div>

                      {recurrenceType === 'weekly' && (
                        <div className="mt-2">
                          <label className="mb-1 block text-xs font-medium text-[var(--color-text-secondary)]">
                            Days
                          </label>
                          <div className="flex gap-1">
                            {['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'].map(
                              (day) => (
                                <button
                                  key={day}
                                  className="rounded-md border border-[var(--color-border)] px-2 py-1 text-xs text-[var(--color-text-secondary)] hover:border-[var(--color-accent)] hover:text-[var(--color-accent)]"
                                >
                                  {day}
                                </button>
                              ),
                            )}
                          </div>
                        </div>
                      )}
                    </div>
                  )}
                </div>

                {/* Duration */}
                <div>
                  <label className="mb-1 block text-xs font-medium text-[var(--color-text-secondary)]">
                    Estimated Duration (Minutes)
                  </label>
                  <input
                    type="number"
                    min={0}
                    value={duration}
                    onChange={(e) => setDuration(e.target.value)}
                    placeholder="e.g. 30"
                    className="w-full rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-3 py-2 text-sm text-[var(--color-text-primary)] outline-none focus:border-[var(--color-accent)]"
                  />
                </div>
              </div>
            )}

            {/* Organize Tab */}
            {activeTab === 'organize' && (
              <div className="flex flex-col gap-4">
                {/* Project */}
                <div>
                  <label className="mb-1 block text-xs font-medium text-[var(--color-text-secondary)]">
                    Project
                  </label>
                  <select
                    value={projectId || ''}
                    onChange={(e) =>
                      setProjectId(e.target.value ? Number(e.target.value) : null)
                    }
                    className="w-full rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-3 py-2 text-sm text-[var(--color-text-primary)] outline-none focus:border-[var(--color-accent)]"
                  >
                    <option value="">No Project</option>
                    {projects.map((p) => (
                      <option key={p.id} value={p.id}>
                        {p.title}
                      </option>
                    ))}
                  </select>
                </div>

                {/* Tags */}
                <div>
                  <label className="mb-1 block text-xs font-medium text-[var(--color-text-secondary)]">
                    Tags
                  </label>
                  <div className="flex flex-wrap gap-1.5">
                    {tags.map((tag) => (
                      <button
                        key={tag.id}
                        onClick={() => toggleTag(tag.id)}
                        className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-medium transition-colors ${
                          taskTagIds.includes(tag.id)
                            ? 'border-[var(--color-accent)] bg-[var(--color-accent)]/10 text-[var(--color-accent)]'
                            : 'border-[var(--color-border)] text-[var(--color-text-secondary)] hover:border-[var(--color-accent)]'
                        }`}
                      >
                        <span
                          className="h-2 w-2 rounded-full"
                          style={{
                            backgroundColor: tag.color || 'var(--color-accent)',
                          }}
                        />
                        {tag.name}
                      </button>
                    ))}
                    <button
                      onClick={() => setShowNewTag(!showNewTag)}
                      className="inline-flex items-center gap-1 rounded-full border border-dashed border-[var(--color-border)] px-2.5 py-1 text-xs text-[var(--color-text-secondary)] hover:border-[var(--color-accent)] hover:text-[var(--color-accent)]"
                    >
                      <Plus className="h-3 w-3" />
                      New Tag
                    </button>
                  </div>

                  {/* New tag form */}
                  {showNewTag && (
                    <div className="mt-2 rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-secondary)] p-3">
                      <input
                        type="text"
                        value={newTagName}
                        onChange={(e) => setNewTagName(e.target.value)}
                        placeholder="Tag name..."
                        className="mb-2 w-full rounded-md border border-[var(--color-border)] bg-[var(--color-bg-card)] px-2 py-1.5 text-sm text-[var(--color-text-primary)] outline-none focus:border-[var(--color-accent)]"
                        autoFocus
                        onKeyDown={(e) => {
                          if (e.key === 'Enter') handleCreateTag();
                        }}
                      />
                      <div className="mb-2 flex flex-wrap gap-1.5">
                        {TAG_COLORS.map((c) => (
                          <button
                            key={c}
                            onClick={() => setNewTagColor(c)}
                            className={`h-6 w-6 rounded-full transition-transform ${
                              newTagColor === c
                                ? 'scale-110 ring-2 ring-offset-1'
                                : ''
                            }`}
                            style={{
                              backgroundColor: c,
                              outlineColor: c,
                            }}
                          />
                        ))}
                      </div>
                      <div className="flex justify-end gap-2">
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => setShowNewTag(false)}
                        >
                          Cancel
                        </Button>
                        <Button size="sm" onClick={handleCreateTag}>
                          Create
                        </Button>
                      </div>
                    </div>
                  )}
                </div>

                {/* Notes */}
                <div>
                  <label className="mb-1 block text-xs font-medium text-[var(--color-text-secondary)]">
                    Notes
                  </label>
                  <textarea
                    value={notes}
                    onChange={(e) => setNotes(e.target.value)}
                    placeholder="Add notes..."
                    rows={4}
                    className="w-full rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-3 py-2 text-sm text-[var(--color-text-primary)] outline-none focus:border-[var(--color-accent)]"
                  />
                </div>
              </div>
            )}
          </div>

          {/* Footer */}
          <div className="mt-4 shrink-0 border-t border-[var(--color-border)] pt-4">
            {isCreate ? (
              <div className="flex justify-end gap-2">
                <Button variant="ghost" onClick={onClose}>
                  Cancel
                </Button>
                <Button onClick={handleCreate} loading={saving}>
                  Create Task
                </Button>
              </div>
            ) : (
              <>
                <div className="flex items-center gap-2">
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={handleDuplicate}
                  >
                    <Copy className="h-4 w-4" />
                    Duplicate
                  </Button>
                  <Button
                    variant="danger"
                    size="sm"
                    onClick={() => setDeleteOpen(true)}
                  >
                    <Trash2 className="h-4 w-4" />
                    Delete
                  </Button>
                </div>
                {task && (
                  <p className="mt-3 text-xs text-[var(--color-text-secondary)]">
                    Created {formatRelative(task.created_at)} · Updated{' '}
                    {formatRelative(task.updated_at)}
                  </p>
                )}
              </>
            )}
          </div>
        </div>
      </Drawer>

      <ConfirmDialog
        isOpen={deleteOpen}
        onClose={() => setDeleteOpen(false)}
        onConfirm={handleDelete}
        title="Delete Task"
        message="Are you sure you want to delete this task? This action cannot be undone."
        confirmLabel="Delete"
        variant="danger"
        loading={deleting}
      />
    </>
  );
}
