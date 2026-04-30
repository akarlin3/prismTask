import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  CheckCircle2,
  CircleSlash,
  Loader2,
  PartyPopper,
  Pause,
  Play,
  Rocket,
  SkipForward,
  Sparkles,
  Target,
} from 'lucide-react';
import { format, parseISO } from 'date-fns';
import { toast } from 'sonner';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import {
  createLog,
  getRecentLogs,
  type FocusReleaseLog,
  type ReleaseState,
} from '@/api/firestore/focusReleaseLogs';
import { getFirebaseUid } from '@/stores/firebaseUid';
import { useTaskStore } from '@/stores/taskStore';
import { computeTimerStatus, formatClock } from '@/utils/goodEnoughTimer';

/**
 * Focus Release — ND-friendly focus timer with a "good enough"
 * release hatch at 80% elapsed and a little celebration on ship.
 * Mirrors Android's GoodEnoughTimerManager + ShipItCelebrationManager
 * at capability level without any backend round-trip.
 */
export function FocusReleaseScreen() {
  const todayTasks = useTaskStore((s) => s.todayTasks);
  const overdueTasks = useTaskStore((s) => s.overdueTasks);
  const tasksPool = useMemo(
    () => [...todayTasks, ...overdueTasks],
    [todayTasks, overdueTasks],
  );

  const [selectedTaskId, setSelectedTaskId] = useState<string>('');
  const [customTitle, setCustomTitle] = useState('');
  const [plannedMinutes, setPlannedMinutes] = useState(20);
  const [startedAt, setStartedAt] = useState<number | null>(null);
  const [elapsedSeconds, setElapsedSeconds] = useState(0);
  const [running, setRunning] = useState(false);
  const [celebration, setCelebration] = useState<ReleaseState | null>(null);

  const [recent, setRecent] = useState<FocusReleaseLog[]>([]);
  const [loadingHistory, setLoadingHistory] = useState(false);

  const tickRef = useRef<ReturnType<typeof setInterval>>(undefined);

  const loadHistory = useCallback(async () => {
    try {
      const uid = getFirebaseUid();
      setLoadingHistory(true);
      const rows = await getRecentLogs(uid, 30);
      setRecent(rows);
    } catch {
      // non-fatal
    } finally {
      setLoadingHistory(false);
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- data-fetch effect: load history on mount
    loadHistory();
  }, [loadHistory]);

  // Ticking effect — 1-second granularity is fine for the UX. Timer
  // keeps running even when the tab is throttled; elapsed is
  // computed from wall-clock on resume.
  useEffect(() => {
    if (!running || startedAt == null) return;
    tickRef.current = setInterval(() => {
      setElapsedSeconds(Math.floor((Date.now() - startedAt) / 1000));
    }, 1000);
    return () => clearInterval(tickRef.current);
  }, [running, startedAt]);

  const plannedSeconds = plannedMinutes * 60;
  const status = computeTimerStatus({
    planned_seconds: plannedSeconds,
    elapsed_seconds: elapsedSeconds,
  });

  const selectedTask = tasksPool.find((t) => t.id === selectedTaskId) ?? null;
  const titleSnapshot = selectedTask?.title ?? customTitle.trim();

  const canStart = titleSnapshot.length > 0 && plannedMinutes > 0;

  const handleStart = () => {
    if (!canStart) return;
    setStartedAt(Date.now());
    setElapsedSeconds(0);
    setRunning(true);
    setCelebration(null);
  };

  const handleToggle = () => {
    if (running) {
      setRunning(false);
    } else if (startedAt != null) {
      // Resume by rebasing the start to account for the pause.
      setStartedAt(Date.now() - elapsedSeconds * 1000);
      setRunning(true);
    }
  };

  const handleRelease = async (release_state: ReleaseState) => {
    if (startedAt == null) return;
    setRunning(false);
    try {
      const uid = getFirebaseUid();
      await createLog(uid, {
        task_id: selectedTask?.id ?? null,
        task_title_snapshot: titleSnapshot,
        planned_minutes: plannedMinutes,
        actual_minutes: Math.max(1, Math.round(elapsedSeconds / 60)),
        release_state,
        started_at: startedAt,
        ended_at: Date.now(),
      });
      setCelebration(release_state);
      toast.success(celebrationToast(release_state));
      setStartedAt(null);
      setElapsedSeconds(0);
      loadHistory();
    } catch (e) {
      toast.error((e as Error).message || 'Failed to save session');
    }
  };

  return (
    <div className="mx-auto max-w-3xl pb-16">
      <header className="mb-6">
        <h1 className="flex items-center gap-2 text-xl font-semibold text-[var(--color-text-primary)]">
          <Target
            className="h-5 w-5 text-[var(--color-accent)]"
            aria-hidden="true"
          />
          Focus Release
        </h1>
        <p className="mt-1 text-sm text-[var(--color-text-secondary)]">
          ND-friendly focus timer. Declare a planned duration; once
          you've hit 80%, the "good enough" escape hatch unlocks so
          you don't need to muscle through to the wall.
        </p>
      </header>

      <section className="mb-6 rounded-xl border border-[var(--color-border)] bg-[var(--color-bg-card)] p-4">
        <div className="mb-3 grid grid-cols-1 gap-3 sm:grid-cols-[1fr_auto]">
          <label className="text-sm">
            <span className="mb-1 block text-xs font-semibold uppercase tracking-wide text-[var(--color-text-secondary)]">
              Task
            </span>
            <select
              value={selectedTaskId}
              onChange={(e) => setSelectedTaskId(e.target.value)}
              disabled={running}
              className="w-full rounded-md border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-3 py-2 text-sm text-[var(--color-text-primary)] outline-none focus:border-[var(--color-accent)]"
            >
              <option value="">Untethered session (free text below)</option>
              {tasksPool.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.title}
                </option>
              ))}
            </select>
          </label>
          <label className="text-sm">
            <span className="mb-1 block text-xs font-semibold uppercase tracking-wide text-[var(--color-text-secondary)]">
              Minutes
            </span>
            <input
              type="number"
              min={1}
              max={180}
              value={plannedMinutes}
              onChange={(e) =>
                setPlannedMinutes(
                  Math.max(1, Math.min(180, Number(e.target.value) || 1)),
                )
              }
              disabled={running}
              className="w-24 rounded-md border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-3 py-2 text-sm text-[var(--color-text-primary)] outline-none focus:border-[var(--color-accent)]"
            />
          </label>
        </div>

        {!selectedTaskId && (
          <label className="mb-3 block text-sm">
            <span className="mb-1 block text-xs font-semibold uppercase tracking-wide text-[var(--color-text-secondary)]">
              What's the session about?
            </span>
            <input
              type="text"
              value={customTitle}
              onChange={(e) => setCustomTitle(e.target.value)}
              placeholder="Draft the proposal intro"
              disabled={running}
              className="w-full rounded-md border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-3 py-2 text-sm text-[var(--color-text-primary)] outline-none focus:border-[var(--color-accent)]"
            />
          </label>
        )}

        <div className="mb-4 flex items-center justify-center">
          <div className="relative flex h-36 w-36 items-center justify-center">
            <svg className="absolute inset-0 h-full w-full -rotate-90" viewBox="0 0 100 100">
              <circle
                cx="50"
                cy="50"
                r="46"
                fill="none"
                stroke="var(--color-bg-secondary)"
                strokeWidth="6"
              />
              <circle
                cx="50"
                cy="50"
                r="46"
                fill="none"
                stroke="var(--color-accent)"
                strokeWidth="6"
                strokeLinecap="round"
                strokeDasharray={`${status.progress_ratio * 289} 289`}
                className="transition-all"
              />
            </svg>
            <div className="flex flex-col items-center">
              <span className="font-mono text-2xl font-semibold text-[var(--color-text-primary)]">
                {formatClock(
                  startedAt == null
                    ? plannedSeconds
                    : status.remaining_seconds,
                )}
              </span>
              <span className="text-[10px] uppercase tracking-wide text-[var(--color-text-secondary)]">
                {startedAt == null
                  ? 'Ready'
                  : running
                  ? 'Running'
                  : 'Paused'}
              </span>
            </div>
          </div>
        </div>

        <div className="flex flex-wrap items-center justify-center gap-2">
          {startedAt == null ? (
            <Button onClick={handleStart} disabled={!canStart}>
              <Play className="mr-1 h-4 w-4" />
              Start
            </Button>
          ) : (
            <>
              <Button variant="secondary" onClick={handleToggle}>
                {running ? (
                  <>
                    <Pause className="mr-1 h-4 w-4" />
                    Pause
                  </>
                ) : (
                  <>
                    <Play className="mr-1 h-4 w-4" />
                    Resume
                  </>
                )}
              </Button>
              <Button
                onClick={() => handleRelease('shipped')}
                disabled={!status.good_enough_unlocked && !status.fully_elapsed}
                title={
                  status.good_enough_unlocked
                    ? 'Mark the session as shipped'
                    : 'Unlocks at 80% elapsed'
                }
              >
                <Rocket className="mr-1 h-4 w-4" />
                Ship it
              </Button>
              <Button
                variant="secondary"
                onClick={() => handleRelease('good_enough')}
                disabled={!status.good_enough_unlocked}
                title={
                  status.good_enough_unlocked
                    ? 'Call it good enough and release'
                    : 'Unlocks at 80% elapsed'
                }
              >
                <CheckCircle2 className="mr-1 h-4 w-4" />
                Good enough
              </Button>
              <Button
                variant="ghost"
                onClick={() => handleRelease('partial')}
              >
                <SkipForward className="mr-1 h-4 w-4" />
                Save partial
              </Button>
              <Button
                variant="ghost"
                onClick={() => handleRelease('abandoned')}
              >
                <CircleSlash className="mr-1 h-4 w-4" />
                Abandon
              </Button>
            </>
          )}
        </div>

        {startedAt != null && !status.good_enough_unlocked && (
          <p className="mt-3 text-center text-xs text-[var(--color-text-secondary)]">
            <Sparkles className="mr-1 inline h-3 w-3" aria-hidden="true" />
            The "good enough" hatch unlocks at 80% elapsed.
          </p>
        )}

        {celebration && (
          <div
            className="mt-4 flex items-center gap-2 rounded-lg border border-emerald-500/40 bg-emerald-500/5 p-3 text-sm text-emerald-600 dark:text-emerald-400"
            role="status"
          >
            <PartyPopper className="h-4 w-4" aria-hidden="true" />
            {celebration === 'shipped'
              ? "Shipped — that's a win."
              : celebration === 'good_enough'
              ? 'Good enough is done. Nice work.'
              : celebration === 'partial'
              ? 'Progress saved. Come back when you can.'
              : 'Logged the abandon. No guilt — sometimes it goes.'}
          </div>
        )}
      </section>

      <section>
        <h2 className="mb-2 text-sm font-semibold text-[var(--color-text-primary)]">
          Recent sessions
        </h2>
        {loadingHistory ? (
          <div className="flex items-center gap-2 text-sm text-[var(--color-text-secondary)]">
            <Loader2 className="h-4 w-4 animate-spin" /> Loading…
          </div>
        ) : recent.length === 0 ? (
          <EmptyState
            title="No sessions yet"
            description="Start a timer above. Every release — shipped, good enough, partial, even abandon — gets logged here."
          />
        ) : (
          <ul className="flex flex-col gap-1.5">
            {recent.map((log) => {
              const startIso = new Date(log.started_at).toISOString();
              return (
                <li
                  key={log.id}
                  className="flex items-start justify-between gap-3 rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-secondary)] p-3"
                >
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium text-[var(--color-text-primary)]">
                      {log.task_title_snapshot}
                    </p>
                    <p className="mt-0.5 text-xs text-[var(--color-text-secondary)]">
                      {format(parseISO(startIso), 'MMM d · HH:mm')} ·{' '}
                      {log.actual_minutes}m of {log.planned_minutes}m
                    </p>
                  </div>
                  <span
                    className={`rounded-full px-2 py-0.5 text-[10px] font-medium uppercase ${
                      log.release_state === 'shipped'
                        ? 'bg-emerald-500 text-white'
                        : log.release_state === 'good_enough'
                        ? 'bg-blue-500 text-white'
                        : log.release_state === 'partial'
                        ? 'bg-[var(--color-border)] text-[var(--color-text-primary)]'
                        : 'bg-red-500/20 text-red-500'
                    }`}
                  >
                    {log.release_state.replace(/_/g, ' ')}
                  </span>
                </li>
              );
            })}
          </ul>
        )}
      </section>
    </div>
  );
}

function celebrationToast(state: ReleaseState): string {
  switch (state) {
    case 'shipped':
      return 'Shipped — nice one.';
    case 'good_enough':
      return 'Good enough is done.';
    case 'partial':
      return 'Partial progress saved.';
    case 'abandoned':
      return 'Logged, no guilt.';
  }
}
