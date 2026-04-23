import { useCallback, useEffect, useState } from 'react';
import { Coffee, Loader2, Sparkles, Target, X } from 'lucide-react';
import { toast } from 'sonner';
import { aiApi } from '@/api/ai';
import { Button } from '@/components/ui/Button';
import { ProUpgradeModal } from '@/components/shared/ProUpgradeModal';
import { useProFeature } from '@/hooks/useProFeature';
import type {
  PomodoroCoachingRequest,
  PomodoroCoachingTrigger,
  PomodoroCoachingTask,
} from '@/types/pomodoroCoaching';

type PhaseLabel = 'idle' | 'planning' | 'empty' | 'work' | 'break' | 'done';

const TRIGGER_BY_PHASE: Partial<Record<PhaseLabel, PomodoroCoachingTrigger>> = {
  idle: 'pre_session',
  planning: 'pre_session',
  work: 'pre_session',
  break: 'break_activity',
  done: 'session_recap',
};

const HEADINGS: Record<PomodoroCoachingTrigger, { icon: typeof Coffee; title: string }> =
  {
    pre_session: { icon: Target, title: 'Before you start' },
    break_activity: { icon: Coffee, title: 'Break suggestion' },
    session_recap: { icon: Sparkles, title: 'Session recap' },
  };

export interface PomodoroCoachPanelProps {
  phase: PhaseLabel;
  sessionLengthMinutes: number;
  breakLengthMinutes: number;
  elapsedBreakMinutes?: number;
  breakType?: 'short' | 'long';
  upcomingTasks?: PomodoroCoachingTask[];
  completedTasks?: PomodoroCoachingTask[];
  startedTasks?: PomodoroCoachingTask[];
  sessionDurationMinutes?: number;
  /** Past tips to avoid repeating during break_activity. */
  recentSuggestions?: string[];
}

/**
 * Self-contained panel that wraps `POST /ai/pomodoro-coaching`. The
 * trigger is inferred from `phase`; if the phase doesn't map to a
 * trigger (empty / generating) the panel renders nothing.
 *
 * Kept as a standalone component so the existing PomodoroScreen needs
 * only a one-line import + one-line render to get coaching across all
 * three surfaces (pre_session / break_activity / session_recap).
 */
export function PomodoroCoachPanel(props: PomodoroCoachPanelProps) {
  const { isPro, showUpgrade, setShowUpgrade } = useProFeature();
  const trigger = TRIGGER_BY_PHASE[props.phase];

  const [message, setMessage] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [dismissed, setDismissed] = useState(false);

  // Clear the message when the trigger switches (phase change) so we
  // don't show a stale tip from a prior phase.
  useEffect(() => {
    setMessage(null);
    setDismissed(false);
  }, [trigger]);

  const buildRequest = useCallback((): PomodoroCoachingRequest | null => {
    if (!trigger) return null;
    switch (trigger) {
      case 'pre_session':
        return {
          trigger,
          upcoming_tasks: props.upcomingTasks ?? [],
          session_length_minutes: props.sessionLengthMinutes,
        };
      case 'break_activity':
        return {
          trigger,
          elapsed_minutes: props.elapsedBreakMinutes ?? 0,
          break_type: props.breakType ?? 'short',
          recent_suggestions: props.recentSuggestions ?? [],
        };
      case 'session_recap':
        return {
          trigger,
          completed_tasks: props.completedTasks ?? [],
          started_tasks: props.startedTasks ?? [],
          session_duration_minutes:
            props.sessionDurationMinutes ?? props.sessionLengthMinutes,
        };
    }
  }, [
    trigger,
    props.upcomingTasks,
    props.sessionLengthMinutes,
    props.elapsedBreakMinutes,
    props.breakType,
    props.recentSuggestions,
    props.completedTasks,
    props.startedTasks,
    props.sessionDurationMinutes,
  ]);

  const fetchTip = useCallback(async () => {
    if (!isPro) {
      setShowUpgrade(true);
      return;
    }
    const req = buildRequest();
    if (!req) return;
    setLoading(true);
    try {
      const res = await aiApi.pomodoroCoaching(req);
      setMessage(res.message);
      setDismissed(false);
    } catch {
      toast.error('Coach is offline for a moment — try again shortly.');
    } finally {
      setLoading(false);
    }
  }, [isPro, buildRequest, setShowUpgrade]);

  if (!trigger || dismissed) return null;

  const heading = HEADINGS[trigger];
  const Icon = heading.icon;

  return (
    <div className="my-4 rounded-xl border border-[var(--color-border)] bg-[var(--color-bg-card)] p-4">
      <div className="flex items-start gap-3">
        <Icon
          className="mt-0.5 h-4 w-4 shrink-0 text-[var(--color-accent)]"
          aria-hidden="true"
        />
        <div className="min-w-0 flex-1">
          <h3 className="text-sm font-semibold text-[var(--color-text-primary)]">
            {heading.title}
          </h3>
          {message ? (
            <p className="mt-1 whitespace-pre-line text-sm text-[var(--color-text-primary)]">
              {message}
            </p>
          ) : (
            <p className="mt-1 text-xs text-[var(--color-text-secondary)]">
              {trigger === 'pre_session'
                ? 'Get a one-line focus prompt before diving in.'
                : trigger === 'break_activity'
                ? 'Suggest a movement or breath reset for this break.'
                : 'Turn this session into a short recap you can act on next time.'}
            </p>
          )}
        </div>
        <div className="flex items-center gap-1.5">
          <Button
            variant="secondary"
            size="sm"
            onClick={fetchTip}
            disabled={loading}
          >
            {loading ? (
              <Loader2 className="mr-1.5 h-3.5 w-3.5 animate-spin" />
            ) : (
              <Sparkles className="mr-1.5 h-3.5 w-3.5" />
            )}
            {message ? 'Regenerate' : 'Get tip'}
          </Button>
          {message && (
            <button
              onClick={() => setDismissed(true)}
              aria-label="Dismiss coaching tip"
              className="rounded-md p-1 text-[var(--color-text-secondary)] hover:bg-[var(--color-bg-secondary)] hover:text-[var(--color-text-primary)]"
            >
              <X className="h-3.5 w-3.5" aria-hidden="true" />
            </button>
          )}
        </div>
      </div>

      <ProUpgradeModal
        isOpen={showUpgrade}
        onClose={() => setShowUpgrade(false)}
        featureName="Pomodoro+ Coaching"
        featureDescription="AI prompts before sessions, break-activity suggestions, and session recaps tailored to what you worked on."
      />
    </div>
  );
}
