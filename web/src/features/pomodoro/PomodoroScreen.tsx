import { useEffect, useState, useCallback, useRef } from 'react';
import {
  Timer,
  Play,
  Pause,
  SkipForward,
  RotateCcw,
  Sparkles,
  Lock,
  Check,
  Coffee,
  ChevronRight,
} from 'lucide-react';
import { toast } from 'sonner';

import { useAuthStore } from '@/stores/authStore';
import { useTaskStore } from '@/stores/taskStore';
import { goalsApi } from '@/api/goals';
import { projectsApi } from '@/api/projects';
import { tasksApi } from '@/api/tasks';
import { aiApi, type PomodoroSession } from '@/api/ai';
import { Button } from '@/components/ui/Button';
import { Checkbox } from '@/components/ui/Checkbox';
import { Spinner } from '@/components/ui/Spinner';
import type { Task } from '@/types/task';

type WorkStyle = 'balanced' | 'deep_work' | 'quick_wins' | 'deadline_driven';
type SessionPhase = 'idle' | 'planning' | 'work' | 'break' | 'done';

const WORK_STYLES: { value: WorkStyle; label: string; desc: string }[] = [
  {
    value: 'balanced',
    label: 'Balanced',
    desc: 'Mix of focused and quick tasks',
  },
  {
    value: 'deep_work',
    label: 'Deep Work',
    desc: 'Long blocks on complex tasks',
  },
  {
    value: 'quick_wins',
    label: 'Quick Wins',
    desc: 'Knock out small tasks first',
  },
  {
    value: 'deadline_driven',
    label: 'Deadline Driven',
    desc: 'Prioritize by due date',
  },
];

function formatTimer(seconds: number): string {
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
}

export function PomodoroScreen() {
  const user = useAuthStore((s) => s.user);
  const { completeTask } = useTaskStore();
  const isPro =
    !!user &&
    ((user.effective_tier ?? user.tier) !== 'FREE' || user.is_admin === true);

  // Planning state
  const [allTasks, setAllTasks] = useState<Task[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedTaskIds, setSelectedTaskIds] = useState<Set<string>>(new Set());
  const [availableMinutes, setAvailableMinutes] = useState(60);
  const [workStyle, setWorkStyle] = useState<WorkStyle>('balanced');
  const [generating, setGenerating] = useState(false);

  // Session state
  const [phase, setPhase] = useState<SessionPhase>('idle');
  const [sessions, setSessions] = useState<PomodoroSession[]>([]);
  const [currentSessionIdx, setCurrentSessionIdx] = useState(0);
  const [currentTaskIdx, setCurrentTaskIdx] = useState(0);
  const [timeLeft, setTimeLeft] = useState(0);
  const [isRunning, setIsRunning] = useState(false);
  const [sessionLength] = useState(25); // minutes
  const [breakLength] = useState(5); // minutes
  const [completedTaskIds, setCompletedTaskIds] = useState<Set<string>>(new Set());

  const timerRef = useRef<ReturnType<typeof setInterval>>(undefined);

  const loadTasks = useCallback(async () => {
    setLoading(true);
    try {
      const goals = await goalsApi.list();
      const all: Task[] = [];
      for (const goal of goals) {
        const projs = await projectsApi.getByGoal(goal.id);
        for (const proj of projs) {
          const projTasks = await tasksApi.getByProject(proj.id);
          all.push(...projTasks);
        }
      }
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
  }, []);

  useEffect(() => {
    loadTasks();
  }, [loadTasks]);

  // Timer logic
  useEffect(() => {
    if (isRunning && timeLeft > 0) {
      timerRef.current = setInterval(() => {
        setTimeLeft((prev) => {
          if (prev <= 1) {
            clearInterval(timerRef.current);
            setIsRunning(false);
            // Auto-transition
            if (phase === 'work') {
              toast('Work session complete! Take a break.', { duration: 3000 });
              setPhase('break');
              return breakLength * 60;
            } else if (phase === 'break') {
              // Move to next session
              const nextIdx = currentSessionIdx + 1;
              if (nextIdx < sessions.length) {
                setCurrentSessionIdx(nextIdx);
                setCurrentTaskIdx(0);
                toast('Break over! Starting next session.', { duration: 3000 });
                setPhase('work');
                return sessionLength * 60;
              } else {
                setPhase('done');
                toast.success('Focus session complete!');
                return 0;
              }
            }
            return 0;
          }
          return prev - 1;
        });
      }, 1000);
    }
    return () => clearInterval(timerRef.current);
  }, [isRunning, timeLeft, phase, currentSessionIdx, sessions.length, breakLength, sessionLength]);

  const toggleTaskSelection = (taskId: string) => {
    setSelectedTaskIds((prev) => {
      const next = new Set(prev);
      if (next.has(taskId)) next.delete(taskId);
      else next.add(taskId);
      return next;
    });
  };

  const handleGeneratePlan = async () => {
    if (!isPro) {
      toast.error('Smart Pomodoro is a Pro feature. Upgrade to use AI features.');
      return;
    }
    setGenerating(true);
    try {
      const response = await aiApi.pomodoroPlan({
        available_minutes: availableMinutes,
        session_length: sessionLength,
        break_length: breakLength,
        focus_preference: workStyle,
      });
      setSessions(response.sessions);
      setPhase('planning');
      if (response.skipped_tasks.length > 0) {
        toast(`${response.skipped_tasks.length} task(s) skipped`, {
          duration: 4000,
        });
      }
    } catch {
      toast.error('Failed to generate plan. Try again later.');
    } finally {
      setGenerating(false);
    }
  };

  const handleStartSession = () => {
    if (sessions.length === 0) return;
    setCurrentSessionIdx(0);
    setCurrentTaskIdx(0);
    setPhase('work');
    setTimeLeft(sessionLength * 60);
    setIsRunning(true);
    setCompletedTaskIds(new Set());
  };

  const handleToggleTimer = () => {
    setIsRunning((prev) => !prev);
  };

  const handleSkipBreak = () => {
    clearInterval(timerRef.current);
    setIsRunning(false);
    const nextIdx = currentSessionIdx + 1;
    if (nextIdx < sessions.length) {
      setCurrentSessionIdx(nextIdx);
      setCurrentTaskIdx(0);
      setPhase('work');
      setTimeLeft(sessionLength * 60);
      setIsRunning(true);
    } else {
      setPhase('done');
    }
  };

  const handleCompleteCurrentTask = async () => {
    const session = sessions[currentSessionIdx];
    if (!session) return;
    const taskItem = session.tasks[currentTaskIdx];
    if (!taskItem) return;

    try {
      await completeTask(taskItem.task_id);
      setCompletedTaskIds((prev) => new Set([...prev, taskItem.task_id]));
      toast.success(`Completed: ${taskItem.title}`);

      // Advance to next task in session
      if (currentTaskIdx < session.tasks.length - 1) {
        setCurrentTaskIdx((prev) => prev + 1);
      }
    } catch {
      toast.error('Failed to complete task');
    }
  };

  const handleReset = () => {
    clearInterval(timerRef.current);
    setIsRunning(false);
    setPhase('idle');
    setSessions([]);
    setCurrentSessionIdx(0);
    setCurrentTaskIdx(0);
    setTimeLeft(0);
    setCompletedTaskIds(new Set());
  };

  const currentSession = sessions[currentSessionIdx];
  const currentTask = currentSession?.tasks[currentTaskIdx];

  // Timer progress
  const totalSeconds =
    phase === 'work' ? sessionLength * 60 : breakLength * 60;
  const progress = totalSeconds > 0 ? ((totalSeconds - timeLeft) / totalSeconds) * 100 : 0;

  if (loading) {
    return (
      <div className="flex h-64 items-center justify-center">
        <Spinner size="lg" />
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-3xl">
      {/* Header */}
      <div className="mb-6 flex items-center gap-3">
        <Timer className="h-7 w-7 text-[var(--color-accent)]" />
        <h1 className="text-2xl font-bold text-[var(--color-text-primary)]">
          Smart Pomodoro
        </h1>
        {!isPro && (
          <span className="flex items-center gap-1 rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-700">
            <Lock className="h-3 w-3" />
            Pro
          </span>
        )}
      </div>

      {/* IDLE: Setup panel */}
      {phase === 'idle' && (
        <div className="flex flex-col gap-6">
          {/* Configuration */}
          <div className="rounded-xl border border-[var(--color-border)] bg-[var(--color-bg-card)] p-6">
            <h2 className="mb-4 text-lg font-semibold text-[var(--color-text-primary)]">
              Plan Focus Session
            </h2>

            {/* Available time */}
            <div className="mb-4">
              <label className="mb-1.5 block text-sm font-medium text-[var(--color-text-secondary)]">
                Available Time
              </label>
              <div className="flex items-center gap-3">
                <input
                  type="range"
                  min={15}
                  max={240}
                  step={15}
                  value={availableMinutes}
                  onChange={(e) =>
                    setAvailableMinutes(parseInt(e.target.value))
                  }
                  className="flex-1"
                />
                <span className="w-16 text-right text-sm font-bold text-[var(--color-text-primary)]">
                  {availableMinutes >= 60
                    ? `${Math.floor(availableMinutes / 60)}h${availableMinutes % 60 ? ` ${availableMinutes % 60}m` : ''}`
                    : `${availableMinutes}m`}
                </span>
              </div>
            </div>

            {/* Work style */}
            <div className="mb-4">
              <label className="mb-1.5 block text-sm font-medium text-[var(--color-text-secondary)]">
                Work Style
              </label>
              <div className="grid grid-cols-2 gap-2">
                {WORK_STYLES.map((ws) => (
                  <button
                    key={ws.value}
                    onClick={() => setWorkStyle(ws.value)}
                    className={`rounded-lg border p-3 text-left transition-colors ${
                      workStyle === ws.value
                        ? 'border-[var(--color-accent)] bg-[var(--color-accent)]/5'
                        : 'border-[var(--color-border)] hover:border-[var(--color-accent)]/50'
                    }`}
                  >
                    <span
                      className={`block text-sm font-medium ${
                        workStyle === ws.value
                          ? 'text-[var(--color-accent)]'
                          : 'text-[var(--color-text-primary)]'
                      }`}
                    >
                      {ws.label}
                    </span>
                    <span className="block text-xs text-[var(--color-text-secondary)]">
                      {ws.desc}
                    </span>
                  </button>
                ))}
              </div>
            </div>

            <Button
              onClick={handleGeneratePlan}
              loading={generating}
              disabled={generating || !isPro}
              className="w-full"
            >
              <Sparkles className="h-4 w-4" />
              Generate Plan
            </Button>
          </div>

          {/* Task selection (optional) */}
          <div className="rounded-xl border border-[var(--color-border)] bg-[var(--color-bg-card)] p-4">
            <h3 className="mb-3 text-sm font-semibold text-[var(--color-text-primary)]">
              Your Tasks ({allTasks.length})
            </h3>
            <div className="max-h-64 overflow-y-auto">
              {allTasks.slice(0, 20).map((task) => (
                <label
                  key={task.id}
                  className="flex items-center gap-3 rounded-lg px-2 py-2 hover:bg-[var(--color-bg-secondary)] cursor-pointer"
                >
                  <Checkbox
                    checked={selectedTaskIds.has(task.id)}
                    onChange={() => toggleTaskSelection(task.id)}
                  />
                  <span className="flex-1 truncate text-sm text-[var(--color-text-primary)]">
                    {task.title}
                  </span>
                  {task.estimated_duration && (
                    <span className="text-xs text-[var(--color-text-secondary)]">
                      {task.estimated_duration}m
                    </span>
                  )}
                </label>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* PLANNING: Show generated plan */}
      {phase === 'planning' && (
        <div className="flex flex-col gap-4">
          <div className="rounded-xl border border-[var(--color-border)] bg-[var(--color-bg-card)] p-6">
            <div className="mb-4 flex items-center justify-between">
              <h2 className="text-lg font-semibold text-[var(--color-text-primary)]">
                Your Focus Plan
              </h2>
              <span className="text-sm text-[var(--color-text-secondary)]">
                {sessions.length} session{sessions.length !== 1 ? 's' : ''}
              </span>
            </div>

            <div className="flex flex-col gap-3">
              {sessions.map((session, idx) => (
                <div
                  key={idx}
                  className="rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-secondary)] p-4"
                >
                  <div className="mb-2 flex items-center justify-between">
                    <span className="text-sm font-semibold text-[var(--color-text-primary)]">
                      Session {session.session_number}
                    </span>
                    <span className="text-xs text-[var(--color-text-secondary)]">
                      {session.tasks.reduce(
                        (sum, t) => sum + t.allocated_minutes,
                        0,
                      )}
                      m
                    </span>
                  </div>
                  {session.tasks.map((task, tIdx) => (
                    <div
                      key={tIdx}
                      className="flex items-center gap-2 py-1"
                    >
                      <ChevronRight className="h-3 w-3 text-[var(--color-accent)]" />
                      <span className="flex-1 text-sm text-[var(--color-text-primary)]">
                        {task.title}
                      </span>
                      <span className="text-xs text-[var(--color-text-secondary)]">
                        {task.allocated_minutes}m
                      </span>
                    </div>
                  ))}
                  {session.rationale && (
                    <p className="mt-2 text-xs italic text-[var(--color-text-secondary)]">
                      {session.rationale}
                    </p>
                  )}
                </div>
              ))}
            </div>

            <div className="mt-4 flex gap-2">
              <Button onClick={handleStartSession} className="flex-1">
                <Play className="h-4 w-4" />
                Start Session
              </Button>
              <Button variant="ghost" onClick={handleReset}>
                <RotateCcw className="h-4 w-4" />
                Cancel
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* WORK / BREAK: Active timer */}
      {(phase === 'work' || phase === 'break') && (
        <div className="flex flex-col items-center gap-6">
          {/* Timer circle */}
          <div className="relative h-48 w-48">
            <svg className="h-48 w-48 -rotate-90" viewBox="0 0 200 200">
              <circle
                cx="100"
                cy="100"
                r="88"
                fill="none"
                stroke="var(--color-bg-secondary)"
                strokeWidth="8"
              />
              <circle
                cx="100"
                cy="100"
                r="88"
                fill="none"
                stroke={phase === 'work' ? 'var(--color-accent)' : '#22c55e'}
                strokeWidth="8"
                strokeLinecap="round"
                strokeDasharray={`${(progress / 100) * 553} 553`}
                className="transition-all duration-1000"
              />
            </svg>
            <div className="absolute inset-0 flex flex-col items-center justify-center">
              <span className="text-4xl font-bold text-[var(--color-text-primary)] tabular-nums">
                {formatTimer(timeLeft)}
              </span>
              <span className="mt-1 text-sm font-medium" style={{ color: phase === 'work' ? 'var(--color-accent)' : '#22c55e' }}>
                {phase === 'work' ? 'Focus' : 'Break'}
              </span>
            </div>
          </div>

          {/* Controls */}
          <div className="flex items-center gap-3">
            <Button
              variant="secondary"
              size="lg"
              onClick={handleToggleTimer}
              className="h-14 w-14 rounded-full !p-0"
            >
              {isRunning ? (
                <Pause className="h-6 w-6" />
              ) : (
                <Play className="h-6 w-6 ml-0.5" />
              )}
            </Button>

            {phase === 'break' && (
              <Button variant="ghost" size="sm" onClick={handleSkipBreak}>
                <SkipForward className="h-4 w-4" />
                Skip Break
              </Button>
            )}

            <Button variant="ghost" size="sm" onClick={handleReset}>
              <RotateCcw className="h-4 w-4" />
              End
            </Button>
          </div>

          {/* Current task */}
          {phase === 'work' && currentSession && (
            <div className="w-full max-w-md rounded-xl border border-[var(--color-border)] bg-[var(--color-bg-card)] p-4">
              <div className="mb-2 flex items-center justify-between">
                <span className="text-xs font-medium text-[var(--color-text-secondary)]">
                  Session {currentSession.session_number} · Task{' '}
                  {currentTaskIdx + 1}/{currentSession.tasks.length}
                </span>
              </div>

              {currentSession.tasks.map((taskItem, idx) => (
                <div
                  key={idx}
                  className={`flex items-center gap-3 rounded-lg px-3 py-2 ${
                    idx === currentTaskIdx
                      ? 'bg-[var(--color-accent)]/5 border border-[var(--color-accent)]/20'
                      : ''
                  }`}
                >
                  {completedTaskIds.has(taskItem.task_id) ? (
                    <Check className="h-4 w-4 shrink-0 text-green-500" />
                  ) : idx === currentTaskIdx ? (
                    <div className="h-4 w-4 shrink-0 rounded-full border-2 border-[var(--color-accent)]" />
                  ) : (
                    <div className="h-4 w-4 shrink-0 rounded-full border-2 border-[var(--color-border)]" />
                  )}
                  <span
                    className={`flex-1 text-sm ${
                      completedTaskIds.has(taskItem.task_id)
                        ? 'text-[var(--color-text-secondary)] line-through'
                        : idx === currentTaskIdx
                          ? 'font-medium text-[var(--color-text-primary)]'
                          : 'text-[var(--color-text-secondary)]'
                    }`}
                  >
                    {taskItem.title}
                  </span>
                  <span className="text-xs text-[var(--color-text-secondary)]">
                    {taskItem.allocated_minutes}m
                  </span>
                </div>
              ))}

              {currentTask && !completedTaskIds.has(currentTask.task_id) && (
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={handleCompleteCurrentTask}
                  className="mt-3 w-full"
                >
                  <Check className="h-4 w-4" />
                  Complete Current Task
                </Button>
              )}
            </div>
          )}

          {/* Break message */}
          {phase === 'break' && (
            <div className="flex flex-col items-center gap-2 text-center">
              <Coffee className="h-8 w-8 text-green-500" />
              <p className="text-sm text-[var(--color-text-secondary)]">
                Take a break! Stretch, hydrate, rest your eyes.
              </p>
            </div>
          )}
        </div>
      )}

      {/* DONE: Summary */}
      {phase === 'done' && (
        <div className="rounded-xl border border-[var(--color-border)] bg-[var(--color-bg-card)] p-6 text-center">
          <div className="mb-4 flex justify-center">
            <div className="flex h-16 w-16 items-center justify-center rounded-full bg-green-100">
              <Check className="h-8 w-8 text-green-600" />
            </div>
          </div>
          <h2 className="text-xl font-bold text-[var(--color-text-primary)]">
            Session Complete!
          </h2>
          <p className="mt-2 text-sm text-[var(--color-text-secondary)]">
            You completed {completedTaskIds.size} task{completedTaskIds.size !== 1 ? 's' : ''} across{' '}
            {sessions.length} session{sessions.length !== 1 ? 's' : ''}.
          </p>
          <Button onClick={handleReset} className="mt-4">
            <RotateCcw className="h-4 w-4" />
            New Session
          </Button>
        </div>
      )}
    </div>
  );
}
