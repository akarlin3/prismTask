import { useState, useCallback } from 'react';
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
import {
  SortableContext,
  verticalListSortingStrategy,
  useSortable,
} from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import { GripVertical } from 'lucide-react';
import { toast } from 'sonner';

import { TaskRow } from './TaskRow';
import { tasksApi } from '@/api/tasks';
import type { Task } from '@/types/task';

interface SortableTaskListProps {
  tasks: Task[];
  onReorder: (reorderedTasks: Task[]) => void;
  onComplete: (taskId: number) => void;
  onUncomplete?: (taskId: number) => void;
  onClick: (task: Task) => void;
  onReschedule?: (taskId: number, date: string) => void;
  showProject?: boolean;
  projectMap?: Map<number, { title: string; color?: string }>;
  showSelection?: boolean;
  selectedTaskIds?: Set<number>;
  onToggleSelect?: (taskId: number) => void;
  disabled?: boolean;
}

/**
 * Calculate new sort_order values using fractional indexing.
 * Returns only the items whose sort_order changed.
 */
function calculateNewSortOrders(
  items: Task[],
  oldIndex: number,
  newIndex: number,
): { id: number; sort_order: number }[] {
  const reordered = [...items];
  const [moved] = reordered.splice(oldIndex, 1);
  reordered.splice(newIndex, 0, moved);

  // Calculate sort_order for the moved item using neighboring values
  const prev = newIndex > 0 ? reordered[newIndex - 1].sort_order : 0;
  const next =
    newIndex < reordered.length - 1
      ? reordered[newIndex + 1].sort_order
      : prev + 200;

  let newOrder = Math.floor((prev + next) / 2);

  // If gap is too small, rebalance all items
  if (newOrder === prev || newOrder === next) {
    return reordered.map((task, i) => ({
      id: task.id,
      sort_order: (i + 1) * 100,
    }));
  }

  return [{ id: moved.id, sort_order: newOrder }];
}

// --- Sortable wrapper for TaskRow ---
function SortableTaskItem({
  task,
  onComplete,
  onUncomplete,
  onClick,
  onReschedule,
  showProject,
  projectName,
  projectColor,
  showSelection,
  selected,
  onToggleSelect,
  dragDisabled,
}: {
  task: Task;
  onComplete: (taskId: number) => void;
  onUncomplete?: (taskId: number) => void;
  onClick: (task: Task) => void;
  onReschedule?: (taskId: number, date: string) => void;
  showProject?: boolean;
  projectName?: string;
  projectColor?: string;
  showSelection?: boolean;
  selected?: boolean;
  onToggleSelect?: () => void;
  dragDisabled?: boolean;
}) {
  const {
    attributes,
    listeners,
    setNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({ id: task.id.toString(), disabled: dragDisabled });

  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.4 : 1,
    position: 'relative' as const,
    zIndex: isDragging ? 50 : undefined,
  };

  return (
    <div ref={setNodeRef} style={style} className="flex items-center">
      {!dragDisabled && (
        <button
          {...attributes}
          {...listeners}
          className="shrink-0 cursor-grab touch-none px-1 py-2 text-[var(--color-text-secondary)] opacity-0 hover:opacity-100 group-hover:opacity-100 transition-opacity"
          aria-label="Drag to reorder"
          style={{ opacity: isDragging ? 1 : undefined }}
        >
          <GripVertical className="h-4 w-4" />
        </button>
      )}
      <div className="flex-1">
        <TaskRow
          task={task}
          selected={selected}
          onToggleSelect={onToggleSelect}
          onComplete={onComplete}
          onUncomplete={onUncomplete}
          onClick={onClick}
          onReschedule={onReschedule}
          showProject={showProject}
          projectName={projectName}
          projectColor={projectColor}
          showSelection={showSelection}
        />
      </div>
    </div>
  );
}

// --- Drag overlay ---
function TaskDragOverlay({ task }: { task: Task }) {
  return (
    <div className="flex items-center gap-2 rounded-lg border border-[var(--color-accent)] bg-[var(--color-bg-card)] px-3 py-2.5 shadow-lg">
      <GripVertical className="h-4 w-4 text-[var(--color-text-secondary)]" />
      <span className="text-sm font-medium text-[var(--color-text-primary)]">
        {task.title}
      </span>
    </div>
  );
}

export function SortableTaskList({
  tasks,
  onReorder,
  onComplete,
  onUncomplete,
  onClick,
  onReschedule,
  showProject = false,
  projectMap,
  showSelection = false,
  selectedTaskIds,
  onToggleSelect,
  disabled = false,
}: SortableTaskListProps) {
  const [activeId, setActiveId] = useState<string | null>(null);

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 5 } }),
  );

  const handleDragStart = useCallback((event: DragStartEvent) => {
    setActiveId(event.active.id as string);
  }, []);

  const handleDragEnd = useCallback(
    async (event: DragEndEvent) => {
      setActiveId(null);
      const { active, over } = event;
      if (!over || active.id === over.id) return;

      const oldIndex = tasks.findIndex(
        (t) => t.id.toString() === active.id,
      );
      const newIndex = tasks.findIndex(
        (t) => t.id.toString() === over.id,
      );
      if (oldIndex === -1 || newIndex === -1) return;

      // Optimistic reorder
      const reordered = [...tasks];
      const [moved] = reordered.splice(oldIndex, 1);
      reordered.splice(newIndex, 0, moved);
      onReorder(reordered);

      // Calculate and persist new sort_order
      const changes = calculateNewSortOrders(tasks, oldIndex, newIndex);

      try {
        await Promise.all(
          changes.map((c) =>
            tasksApi.update(c.id, { sort_order: c.sort_order }),
          ),
        );
      } catch {
        // Revert on failure
        onReorder(tasks);
        toast.error('Failed to reorder tasks');
      }
    },
    [tasks, onReorder],
  );

  const activeTask = activeId
    ? tasks.find((t) => t.id.toString() === activeId)
    : null;

  if (disabled) {
    // Non-draggable mode: just render TaskRows
    return (
      <div>
        {tasks.map((task) => (
          <TaskRow
            key={task.id}
            task={task}
            selected={selectedTaskIds?.has(task.id)}
            onToggleSelect={
              onToggleSelect ? () => onToggleSelect(task.id) : undefined
            }
            onComplete={onComplete}
            onUncomplete={onUncomplete}
            onClick={onClick}
            onReschedule={onReschedule}
            showProject={showProject}
            projectName={projectMap?.get(task.project_id)?.title}
            projectColor={projectMap?.get(task.project_id)?.color}
            showSelection={showSelection}
          />
        ))}
      </div>
    );
  }

  return (
    <DndContext
      sensors={sensors}
      collisionDetection={closestCenter}
      onDragStart={handleDragStart}
      onDragEnd={handleDragEnd}
    >
      <SortableContext
        items={tasks.map((t) => t.id.toString())}
        strategy={verticalListSortingStrategy}
      >
        {tasks.map((task) => (
          <SortableTaskItem
            key={task.id}
            task={task}
            onComplete={onComplete}
            onUncomplete={onUncomplete}
            onClick={onClick}
            onReschedule={onReschedule}
            showProject={showProject}
            projectName={projectMap?.get(task.project_id)?.title}
            projectColor={projectMap?.get(task.project_id)?.color}
            showSelection={showSelection}
            selected={selectedTaskIds?.has(task.id)}
            onToggleSelect={
              onToggleSelect ? () => onToggleSelect(task.id) : undefined
            }
            dragDisabled={false}
          />
        ))}
      </SortableContext>

      <DragOverlay>
        {activeTask ? <TaskDragOverlay task={activeTask} /> : null}
      </DragOverlay>
    </DndContext>
  );
}
