import { useEffect, useState, useCallback, useMemo } from 'react';
import {
  LayoutGrid,
  Sparkles,
  Plus,
  GripVertical,
  Lock,
} from 'lucide-react';
import {
  DndContext,
  DragOverlay,
  closestCenter,
  PointerSensor,
  useSensor,
  useSensors,
  type DragStartEvent,
  type DragEndEvent,
} from '@dnd-kit/core';
import { useDroppable } from '@dnd-kit/core';
import { useSortable } from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import { toast } from 'sonner';

import { useTaskStore } from '@/stores/taskStore';
import { useProjectStore } from '@/stores/projectStore';
import { useAuthStore } from '@/stores/authStore';
import { goalsApi } from '@/api/goals';
import { projectsApi } from '@/api/projects';
import { tasksApi } from '@/api/tasks';
import { aiApi } from '@/api/ai';
import { Checkbox } from '@/components/ui/Checkbox';
import { Button } from '@/components/ui/Button';
import { Spinner } from '@/components/ui/Spinner';
import { DueDateLabel } from '@/components/shared/DueDateLabel';
import { getPriorityColor } from '@/utils/priority';
import TaskEditor from '@/features/tasks/TaskEditor';
import type { Task, TaskPriority } from '@/types/task';

type Quadrant = 'Q1' | 'Q2' | 'Q3' | 'Q4';

interface QuadrantConfig {
  id: Quadrant;
  title: string;
  subtitle: string;
  accentColor: string;
  bgColor: string;
  borderColor: string;
}

const QUADRANTS: QuadrantConfig[] = [
  {
    id: 'Q1',
    title: 'Do First',
    subtitle: 'Urgent & Important',
    accentColor: '#ef4444',
    bgColor: 'rgba(239, 68, 68, 0.04)',
    borderColor: 'rgba(239, 68, 68, 0.2)',
  },
  {
    id: 'Q2',
    title: 'Schedule',
    subtitle: 'Not Urgent & Important',
    accentColor: '#3b82f6',
    bgColor: 'rgba(59, 130, 246, 0.04)',
    borderColor: 'rgba(59, 130, 246, 0.2)',
  },
  {
    id: 'Q3',
    title: 'Delegate',
    subtitle: 'Urgent & Not Important',
    accentColor: '#f59e0b',
    bgColor: 'rgba(245, 158, 11, 0.04)',
    borderColor: 'rgba(245, 158, 11, 0.2)',
  },
  {
    id: 'Q4',
    title: 'Eliminate',
    subtitle: 'Not Urgent & Not Important',
    accentColor: '#6b7280',
    bgColor: 'rgba(107, 114, 128, 0.04)',
    borderColor: 'rgba(107, 114, 128, 0.2)',
  },
];

/**
 * Determine the Eisenhower quadrant for a task based on urgency and importance.
 * Urgency: derived from due_date proximity + priority
 * Importance: derived from priority level
 */
function classifyTask(task: Task): Quadrant {
  // If already classified by AI, use that
  if (task.eisenhower_quadrant) {
    return task.eisenhower_quadrant as Quadrant;
  }

  const isUrgent = getIsUrgent(task);
  const isImportant = task.priority <= 2; // 1=urgent, 2=high

  if (isUrgent && isImportant) return 'Q1';
  if (!isUrgent && isImportant) return 'Q2';
  if (isUrgent && !isImportant) return 'Q3';
  return 'Q4';
}

function getIsUrgent(task: Task): boolean {
  if (!task.due_date) return false;
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const due = new Date(task.due_date);
  due.setHours(0, 0, 0, 0);
  const daysUntil = Math.ceil(
    (due.getTime() - today.getTime()) / (1000 * 60 * 60 * 24),
  );

  // Overdue or due today = always urgent
  if (daysUntil <= 0) return true;
  // Due tomorrow with priority 1 or 2 = urgent
  if (daysUntil === 1 && task.priority <= 2) return true;
  return false;
}

/**
 * Get the priority/date updates needed when moving a task to a quadrant.
 */
function getQuadrantUpdates(
  quadrant: Quadrant,
): { priority?: TaskPriority; due_date?: string } {
  const today = new Date().toISOString().split('T')[0];
  switch (quadrant) {
    case 'Q1':
      return { priority: 2, due_date: today };
    case 'Q2':
      return { priority: 2 };
    case 'Q3':
      return { priority: 3, due_date: today };
    case 'Q4':
      return { priority: 4 };
  }
}

// --- Draggable task item ---
function DraggableTaskItem({
  task,
  accentColor,
  projectName,
  onComplete,
  onClick,
}: {
  task: Task;
  accentColor: string;
  projectName?: string;
  onComplete: (taskId: number) => void;
  onClick: (task: Task) => void;
}) {
  const {
    attributes,
    listeners,
    setNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({ id: task.id.toString() });

  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.4 : 1,
  };

  const isDone = task.status === 'done';
  const priorityColor = getPriorityColor(task.priority);

  return (
    <div
      ref={setNodeRef}
      style={style}
      className="group flex items-center gap-2 rounded-lg px-2 py-1.5 transition-colors hover:bg-black/5 dark:hover:bg-white/5"
    >
      <button
        {...attributes}
        {...listeners}
        className="shrink-0 cursor-grab touch-none text-[var(--color-text-secondary)] opacity-0 group-hover:opacity-100 transition-opacity"
        aria-label="Drag to reorder"
      >
        <GripVertical className="h-3.5 w-3.5" />
      </button>

      <Checkbox
        checked={isDone}
        onChange={(checked) => {
          if (checked) onComplete(task.id);
        }}
        priorityColor={priorityColor}
        className="shrink-0"
      />

      <button
        type="button"
        className="flex min-w-0 flex-1 items-center gap-1.5 text-left"
        onClick={() => onClick(task)}
      >
        <span
          className={`flex-1 truncate text-xs font-medium ${
            isDone
              ? 'text-[var(--color-text-secondary)] line-through opacity-60'
              : 'text-[var(--color-text-primary)]'
          }`}
        >
          {task.title}
        </span>
        <span className="flex shrink-0 items-center gap-1.5">
          <DueDateLabel date={task.due_date} />
          {projectName && (
            <span
              className="inline-flex items-center rounded-full px-1.5 py-0.5 text-[10px] font-medium"
              style={{
                color: accentColor,
                backgroundColor: `${accentColor}15`,
              }}
            >
              {projectName}
            </span>
          )}
        </span>
      </button>
    </div>
  );
}

// --- Droppable quadrant ---
function QuadrantPanel({
  config,
  tasks,
  projectMap,
  onComplete,
  onTaskClick,
  onAddTask,
}: {
  config: QuadrantConfig;
  tasks: Task[];
  projectMap: Map<number, { title: string }>;
  onComplete: (taskId: number) => void;
  onTaskClick: (task: Task) => void;
  onAddTask: (quadrant: Quadrant) => void;
}) {
  const { setNodeRef, isOver } = useDroppable({ id: config.id });

  return (
    <div
      ref={setNodeRef}
      className="flex flex-col rounded-xl border transition-all duration-150"
      style={{
        backgroundColor: isOver ? `${config.accentColor}10` : config.bgColor,
        borderColor: isOver ? config.accentColor : config.borderColor,
        minHeight: 200,
      }}
    >
      {/* Header */}
      <div className="flex items-center justify-between border-b px-3 py-2" style={{ borderColor: config.borderColor }}>
        <div className="flex items-center gap-2">
          <span
            className="h-2.5 w-2.5 rounded-full"
            style={{ backgroundColor: config.accentColor }}
          />
          <span className="text-sm font-semibold text-[var(--color-text-primary)]">
            {config.title}
          </span>
          <span className="rounded-full bg-[var(--color-bg-secondary)] px-1.5 py-0.5 text-[10px] font-medium text-[var(--color-text-secondary)]">
            {tasks.length}
          </span>
        </div>
        <span className="text-[10px] text-[var(--color-text-secondary)]">
          {config.subtitle}
        </span>
      </div>

      {/* Tasks */}
      <div className="flex-1 overflow-y-auto px-1 py-1" style={{ maxHeight: 320 }}>
        {tasks.length === 0 ? (
          <div className="flex h-20 items-center justify-center">
            <span className="text-xs text-[var(--color-text-secondary)]">
              No tasks
            </span>
          </div>
        ) : (
          tasks.map((task) => (
            <DraggableTaskItem
              key={task.id}
              task={task}
              accentColor={config.accentColor}
              projectName={projectMap.get(task.project_id)?.title}
              onComplete={onComplete}
              onClick={onTaskClick}
            />
          ))
        )}
      </div>

      {/* Add task */}
      <button
        onClick={() => onAddTask(config.id)}
        className="flex items-center gap-1.5 border-t px-3 py-2 text-xs text-[var(--color-text-secondary)] transition-colors hover:text-[var(--color-text-primary)]"
        style={{ borderColor: config.borderColor }}
      >
        <Plus className="h-3.5 w-3.5" />
        Add Task
      </button>
    </div>
  );
}

// --- Drag overlay task card ---
function DragOverlayCard({ task }: { task: Task }) {
  return (
    <div className="flex items-center gap-2 rounded-lg border border-[var(--color-accent)] bg-[var(--color-bg-card)] px-3 py-2 shadow-lg">
      <GripVertical className="h-3.5 w-3.5 text-[var(--color-text-secondary)]" />
      <span className="text-xs font-medium text-[var(--color-text-primary)]">
        {task.title}
      </span>
    </div>
  );
}

// --- Main screen ---
export function EisenhowerScreen() {
  const { completeTask, updateTask, setSelectedTask } = useTaskStore();
  const { projects, fetchAllProjects } = useProjectStore();
  const user = useAuthStore((s) => s.user);

  const [allTasks, setAllTasks] = useState<Task[]>([]);
  const [loading, setLoading] = useState(true);
  const [categorizing, setCategorizing] = useState(false);
  const [editorOpen, setEditorOpen] = useState(false);
  const [createQuadrant, setCreateQuadrant] = useState<Quadrant | null>(null);
  const [activeId, setActiveId] = useState<string | null>(null);

  const projectMap = useMemo(
    () => new Map(projects.map((p) => [p.id, { title: p.title }])),
    [projects],
  );

  const isPro = user && user.tier !== 'FREE';

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 5 } }),
  );

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      await fetchAllProjects();
      const goals = await goalsApi.list();
      const all: Task[] = [];
      for (const goal of goals) {
        const projs = await projectsApi.getByGoal(goal.id);
        for (const proj of projs) {
          const projTasks = await tasksApi.getByProject(proj.id);
          all.push(...projTasks);
        }
      }
      // Only show incomplete root-level tasks
      setAllTasks(
        all.filter(
          (t) =>
            t.status !== 'done' &&
            t.status !== 'cancelled' &&
            t.parent_id === null,
        ),
      );
    } catch {
      toast.error('Failed to load tasks');
    } finally {
      setLoading(false);
    }
  }, [fetchAllProjects]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  // Classify tasks into quadrants
  const quadrantTasks = useMemo(() => {
    const result: Record<Quadrant, Task[]> = {
      Q1: [],
      Q2: [],
      Q3: [],
      Q4: [],
    };
    for (const task of allTasks) {
      const q = classifyTask(task);
      result[q].push(task);
    }
    // Sort each quadrant by urgency score desc
    for (const q of Object.keys(result) as Quadrant[]) {
      result[q].sort((a, b) => (b.urgency_score ?? 0) - (a.urgency_score ?? 0));
    }
    return result;
  }, [allTasks]);

  const handleComplete = useCallback(
    async (taskId: number) => {
      try {
        await completeTask(taskId);
        setAllTasks((prev) => prev.filter((t) => t.id !== taskId));
        toast.success('Task completed');
      } catch {
        toast.error('Failed to complete task');
      }
    },
    [completeTask],
  );

  const handleTaskClick = useCallback(
    (task: Task) => {
      setSelectedTask(task);
      setCreateQuadrant(null);
      setEditorOpen(true);
    },
    [setSelectedTask],
  );

  const handleAddTask = useCallback((quadrant: Quadrant) => {
    setSelectedTask(null);
    setCreateQuadrant(quadrant);
    setEditorOpen(true);
  }, [setSelectedTask]);

  // AI Auto-Categorize
  const handleAutoCategorize = useCallback(async () => {
    if (!isPro) {
      toast.error('Auto-Categorize is a Pro feature. Upgrade to use AI features.');
      return;
    }
    setCategorizing(true);
    try {
      const taskIds = allTasks.map((t) => t.id);
      const response = await aiApi.eisenhowerCategorize(taskIds);

      // Apply categorizations to local state
      const catMap = new Map(
        response.categorizations.map((c) => [c.task_id, c.quadrant]),
      );

      setAllTasks((prev) =>
        prev.map((t) => {
          const quadrant = catMap.get(t.id);
          if (quadrant) {
            return { ...t, eisenhower_quadrant: quadrant };
          }
          return t;
        }),
      );

      // Also persist to backend
      await Promise.all(
        response.categorizations.map((c) =>
          updateTask(c.task_id, { eisenhower_quadrant: c.quadrant }),
        ),
      );

      const summary = response.summary;
      toast.success(
        `Categorized: Q1:${summary.Q1 ?? 0} Q2:${summary.Q2 ?? 0} Q3:${summary.Q3 ?? 0} Q4:${summary.Q4 ?? 0}`,
      );
    } catch {
      toast.error('AI categorization failed. Try again later.');
    } finally {
      setCategorizing(false);
    }
  }, [allTasks, isPro, updateTask]);

  // Drag between quadrants
  const handleDragStart = useCallback((event: DragStartEvent) => {
    setActiveId(event.active.id as string);
  }, []);

  const handleDragEnd = useCallback(
    async (event: DragEndEvent) => {
      setActiveId(null);
      const { active, over } = event;
      if (!over) return;

      const taskId = parseInt(active.id as string, 10);
      const targetQuadrant = over.id as Quadrant;

      // Find which quadrant the task is currently in
      const task = allTasks.find((t) => t.id === taskId);
      if (!task) return;
      const currentQuadrant = classifyTask(task);
      if (currentQuadrant === targetQuadrant) return;

      // Optimistic update
      const updates = getQuadrantUpdates(targetQuadrant);
      setAllTasks((prev) =>
        prev.map((t) =>
          t.id === taskId
            ? {
                ...t,
                ...updates,
                eisenhower_quadrant: targetQuadrant,
              }
            : t,
        ),
      );

      try {
        await updateTask(taskId, {
          ...updates,
          eisenhower_quadrant: targetQuadrant,
        });
        toast.success(`Moved to ${QUADRANTS.find((q) => q.id === targetQuadrant)?.title}`);
      } catch {
        // Revert
        setAllTasks((prev) =>
          prev.map((t) => (t.id === taskId ? task : t)),
        );
        toast.error('Failed to move task');
      }
    },
    [allTasks, updateTask],
  );

  const activeTask = activeId
    ? allTasks.find((t) => t.id === parseInt(activeId, 10))
    : null;

  if (loading) {
    return (
      <div className="flex h-64 items-center justify-center">
        <Spinner size="lg" />
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-6xl">
      {/* Header */}
      <div className="mb-6 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <LayoutGrid className="h-7 w-7 text-[var(--color-accent)]" />
          <h1 className="text-2xl font-bold text-[var(--color-text-primary)]">
            Eisenhower Matrix
          </h1>
          <span className="rounded-full bg-[var(--color-bg-secondary)] px-2.5 py-0.5 text-xs font-medium text-[var(--color-text-secondary)]">
            {allTasks.length} tasks
          </span>
        </div>

        <Button
          variant={isPro ? 'primary' : 'secondary'}
          size="sm"
          onClick={handleAutoCategorize}
          loading={categorizing}
          disabled={categorizing || allTasks.length === 0}
        >
          {isPro ? (
            <Sparkles className="h-4 w-4" />
          ) : (
            <Lock className="h-4 w-4" />
          )}
          Auto-Categorize
        </Button>
      </div>

      {/* 2x2 Grid */}
      <DndContext
        sensors={sensors}
        collisionDetection={closestCenter}
        onDragStart={handleDragStart}
        onDragEnd={handleDragEnd}
      >
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          {QUADRANTS.map((config) => (
            <QuadrantPanel
              key={config.id}
              config={config}
              tasks={quadrantTasks[config.id]}
              projectMap={projectMap}
              onComplete={handleComplete}
              onTaskClick={handleTaskClick}
              onAddTask={handleAddTask}
            />
          ))}
        </div>

        <DragOverlay>
          {activeTask ? <DragOverlayCard task={activeTask} /> : null}
        </DragOverlay>
      </DndContext>

      {/* Legend */}
      <div className="mt-6 flex flex-wrap items-center gap-4 rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-card)] px-4 py-3">
        <span className="text-xs font-medium text-[var(--color-text-secondary)]">
          Placement Logic:
        </span>
        <span className="text-xs text-[var(--color-text-secondary)]">
          <strong>Urgent</strong> = overdue, due today, or due tomorrow with high priority
        </span>
        <span className="text-xs text-[var(--color-text-secondary)]">
          <strong>Important</strong> = priority Urgent or High
        </span>
        <span className="text-xs text-[var(--color-text-secondary)]">
          Drag tasks between quadrants to reclassify
        </span>
      </div>

      {/* Task Editor */}
      {editorOpen && (
        <TaskEditor
          mode={createQuadrant ? 'create' : 'edit'}
          onClose={() => {
            setEditorOpen(false);
            setSelectedTask(null);
            setCreateQuadrant(null);
          }}
          onUpdate={() => loadData()}
        />
      )}
    </div>
  );
}
