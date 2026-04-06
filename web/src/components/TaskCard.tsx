import type { Task, Tag } from '../types';
import { priorityColors, formatRelativeDate } from '../utils';

interface TaskCardProps {
  task: Task;
  tags?: Tag[];
  onComplete?: (taskId: number) => void;
  onUncomplete?: (taskId: number) => void;
  onClick?: (taskId: number) => void;
  showDate?: boolean;
}

export function TaskCard({ task, tags = [], onComplete, onUncomplete, onClick, showDate = true }: TaskCardProps) {
  const handleCheck = (e: React.MouseEvent) => {
    e.stopPropagation();
    if (task.isCompleted) {
      onUncomplete?.(task.id);
    } else {
      onComplete?.(task.id);
    }
  };

  const isOverdue = task.dueDate && task.dueDate < new Date().setHours(0, 0, 0, 0) && !task.isCompleted;

  return (
    <div
      className={`task-card ${task.isCompleted ? 'completed' : ''}`}
      onClick={() => onClick?.(task.id)}
    >
      <button
        className={`checkbox ${task.isCompleted ? 'checked' : ''}`}
        onClick={handleCheck}
        aria-label={task.isCompleted ? 'Mark incomplete' : 'Mark complete'}
      >
        {task.isCompleted && (
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3">
            <polyline points="20 6 9 17 4 12" />
          </svg>
        )}
      </button>
      <div className="task-content">
        <div className="task-title-row">
          {task.priority > 0 && (
            <span
              className="priority-dot"
              style={{ backgroundColor: priorityColors[task.priority] }}
            />
          )}
          <span className={`task-title ${task.isCompleted ? 'strikethrough' : ''}`}>
            {task.title}
          </span>
        </div>
        <div className="task-meta">
          {showDate && task.dueDate && (
            <span className={`due-date ${isOverdue ? 'overdue' : ''}`}>
              {formatRelativeDate(task.dueDate)}
            </span>
          )}
          {tags.slice(0, 3).map(tag => (
            <span key={tag.id} className="tag" style={{ color: tag.color }}>
              #{tag.name}
            </span>
          ))}
        </div>
      </div>
    </div>
  );
}
