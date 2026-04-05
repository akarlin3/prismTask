import { useState, useEffect, useCallback } from 'react';
import type { Task, HabitWithStatus, Tag } from '../types';
import { bridge } from '../bridge';
import { ProgressRing } from '../components/ProgressRing';
import { TaskCard } from '../components/TaskCard';
import { Section } from '../components/Section';

export function TodayTab() {
  const [overdue, setOverdue] = useState<Task[]>([]);
  const [today, setToday] = useState<Task[]>([]);
  const [planned, setPlanned] = useState<Task[]>([]);
  const [completed, setCompleted] = useState<Task[]>([]);
  const [habits, setHabits] = useState<HabitWithStatus[]>([]);
  const [taskTags, setTaskTags] = useState<Record<number, Tag[]>>({});
  const [showPlanSheet, setShowPlanSheet] = useState(false);
  const [availableTasks, setAvailableTasks] = useState<Task[]>([]);

  const loadData = useCallback(() => {
    setOverdue(bridge.getOverdueTasks());
    setToday(bridge.getTodayTasks());
    setPlanned(bridge.getPlannedTasks());
    setCompleted(bridge.getCompletedToday());
    setHabits(bridge.getHabitsWithStatus());
  }, []);

  useEffect(() => {
    loadData();
    // Load tags for all visible tasks
    const allTasks = [...bridge.getOverdueTasks(), ...bridge.getTodayTasks(), ...bridge.getPlannedTasks()];
    const tagsMap: Record<number, Tag[]> = {};
    for (const t of allTasks) {
      tagsMap[t.id] = bridge.getTaskTags(t.id);
    }
    setTaskTags(tagsMap);

    // Listen for data updates from Android
    window.updateData = (type: string) => {
      if (type === 'today' || type === 'all') loadData();
    };
  }, [loadData]);

  const totalTasks = overdue.length + today.length + planned.length;
  const completedHabits = habits.filter(h => h.isCompletedToday).length;
  const total = totalTasks + habits.length + completed.length;
  const done = completed.length + completedHabits;

  const handleComplete = (taskId: number) => {
    bridge.completeTask(taskId);
    loadData();
  };

  const handleUncomplete = (taskId: number) => {
    bridge.uncompleteTask(taskId);
    loadData();
  };

  const handleToggleHabit = (habitId: number, isCompleted: boolean) => {
    bridge.toggleHabitCompletion(habitId, isCompleted);
    loadData();
  };

  const handlePlanMore = () => {
    setAvailableTasks(bridge.getTasksNotInToday());
    setShowPlanSheet(true);
  };

  const handlePlanTask = (taskId: number) => {
    bridge.planForToday(taskId);
    setAvailableTasks(prev => prev.filter(t => t.id !== taskId));
    loadData();
  };

  return (
    <div className="tab-content today-tab">
      <div className="today-header">
        <ProgressRing completed={done} total={total} size={140} />
        <p className="today-subtitle">
          {totalTasks - 0} tasks · {habits.length - completedHabits} habits remaining
        </p>
      </div>

      {habits.length > 0 && (
        <Section title="Habits" count={habits.length}>
          <div className="habit-list-compact">
            {habits.map(h => (
              <div key={h.habit.id} className="habit-card-compact">
                <span className="habit-icon" style={{ backgroundColor: h.habit.color + '22' }}>
                  {h.habit.icon}
                </span>
                <div className="habit-info">
                  <span className="habit-name">{h.habit.name}</span>
                  {h.currentStreak > 0 && (
                    <span className="streak-badge">🔥 {h.currentStreak}</span>
                  )}
                </div>
                <button
                  className={`habit-check ${h.isCompletedToday ? 'checked' : ''}`}
                  onClick={() => handleToggleHabit(h.habit.id, h.isCompletedToday)}
                >
                  {h.isCompletedToday && (
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3">
                      <polyline points="20 6 9 17 4 12" />
                    </svg>
                  )}
                </button>
              </div>
            ))}
          </div>
        </Section>
      )}

      {overdue.length > 0 && (
        <Section title="Overdue" count={overdue.length} color="#EF4444">
          {overdue.map(task => (
            <TaskCard
              key={task.id}
              task={task}
              tags={taskTags[task.id]}
              onComplete={handleComplete}
              onClick={(id) => bridge.navigate(`add_edit_task?taskId=${id}`)}
            />
          ))}
        </Section>
      )}

      {(today.length > 0 || planned.length > 0) && (
        <Section title="Today" count={today.length + planned.length}>
          {today.map(task => (
            <TaskCard
              key={task.id}
              task={task}
              tags={taskTags[task.id]}
              onComplete={handleComplete}
              showDate={false}
              onClick={(id) => bridge.navigate(`add_edit_task?taskId=${id}`)}
            />
          ))}
          {planned.map(task => (
            <TaskCard
              key={task.id}
              task={task}
              tags={taskTags[task.id]}
              onComplete={handleComplete}
              onClick={(id) => bridge.navigate(`add_edit_task?taskId=${id}`)}
            />
          ))}
        </Section>
      )}

      {completed.length > 0 && (
        <Section title="Completed" count={completed.length} defaultOpen={false}>
          {completed.map(task => (
            <TaskCard
              key={task.id}
              task={task}
              onUncomplete={handleUncomplete}
              showDate={false}
            />
          ))}
        </Section>
      )}

      <button className="plan-more-btn" onClick={handlePlanMore}>
        + Plan more for today
      </button>

      {showPlanSheet && (
        <div className="bottom-sheet-overlay" onClick={() => setShowPlanSheet(false)}>
          <div className="bottom-sheet" onClick={e => e.stopPropagation()}>
            <div className="sheet-handle" />
            <h3>Plan for Today</h3>
            {availableTasks.length === 0 ? (
              <p className="sheet-empty">No more tasks to plan</p>
            ) : (
              <div className="sheet-tasks">
                {availableTasks.map(task => (
                  <div key={task.id} className="plan-task-row" onClick={() => handlePlanTask(task.id)}>
                    <div className="plan-task-info">
                      {task.priority > 0 && (
                        <span className="priority-dot" style={{ backgroundColor: `var(--priority-${task.priority})` }} />
                      )}
                      <span>{task.title}</span>
                    </div>
                    <span className="plan-task-date">
                      {task.dueDate ? new Date(task.dueDate).toLocaleDateString('en-US', { month: 'short', day: 'numeric' }) : 'No date'}
                    </span>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
