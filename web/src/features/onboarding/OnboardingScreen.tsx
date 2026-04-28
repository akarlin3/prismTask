import { useState, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Calendar,
  Check,
  ChevronLeft,
  ChevronRight,
  FileText,
  LayoutGrid,
  Sparkles,
  Sprout,
  Target,
  Wand2,
} from 'lucide-react';
import { toast } from 'sonner';
import { useAuthStore } from '@/stores/authStore';
import { useOnboardingStore } from '@/stores/onboardingStore';
import { useThemeStore } from '@/stores/themeStore';
import { Button } from '@/components/ui/Button';
import { THEME_ORDER, THEMES } from '@/theme/themes';

/**
 * 9-page onboarding flow, mirroring the Android page order from
 * `ui/screens/onboarding/OnboardingScreen.kt`:
 *
 *   0. Welcome
 *   1. ThemePicker
 *   2. SmartTasks
 *   3. NaturalLanguage
 *   4. Habits
 *   5. Templates
 *   6. Views
 *   7. BrainMode (light intro — ND features are backend-gated on web)
 *   8. Setup (completion)
 *
 * Unlike Android, the web flow does not auto-create template tasks or
 * configure ND-mode preferences — those require backend endpoints we
 * intentionally aren't touching in this slice. The flow is presentation
 * + theme-picker + completion marker only.
 */

interface OnboardingPage {
  title: string;
  subtitle: string;
  icon: React.ReactNode;
  body: React.ReactNode;
}

function PageShell({
  icon,
  title,
  subtitle,
  children,
}: {
  icon: React.ReactNode;
  title: string;
  subtitle: string;
  children?: React.ReactNode;
}) {
  return (
    <div className="flex min-h-full flex-col items-center px-6 pt-16 text-center">
      <div className="mb-6 flex h-16 w-16 items-center justify-center rounded-2xl bg-[var(--color-accent)]/10 text-[var(--color-accent)]">
        {icon}
      </div>
      <h1 className="mb-2 text-2xl font-semibold text-[var(--color-text-primary)]">
        {title}
      </h1>
      <p className="mb-8 max-w-md text-sm text-[var(--color-text-secondary)]">
        {subtitle}
      </p>
      {children && (
        <div className="w-full max-w-xl text-left">{children}</div>
      )}
    </div>
  );
}

function ThemePickerBody() {
  const themeKey = useThemeStore((s) => s.themeKey);
  const setThemeKey = useThemeStore((s) => s.setThemeKey);
  return (
    <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
      {THEME_ORDER.map((key) => {
        const tokens = THEMES[key];
        const selected = themeKey === key;
        return (
          <button
            key={key}
            onClick={() => setThemeKey(key)}
            role="radio"
            aria-checked={selected}
            className={`flex flex-col items-start gap-2 rounded-xl border p-3 text-left transition-colors ${
              selected
                ? 'border-[var(--color-accent)] bg-[var(--color-bg-secondary)]'
                : 'border-[var(--color-border)] hover:border-[var(--color-accent)]/60'
            }`}
          >
            <div className="flex w-full items-center justify-between">
              <span className="text-sm font-semibold text-[var(--color-text-primary)]">
                {tokens.label}
              </span>
              {selected && (
                <Check
                  className="h-4 w-4 text-[var(--color-accent)]"
                  aria-hidden="true"
                />
              )}
            </div>
            <span className="text-xs text-[var(--color-text-secondary)]">
              {tokens.tagline}
            </span>
            <div className="flex h-8 w-full items-stretch overflow-hidden rounded-md" aria-hidden="true">
              <span className="flex-1" style={{ backgroundColor: tokens.background }} />
              <span className="flex-1" style={{ backgroundColor: tokens.surface }} />
              <span className="flex-1" style={{ backgroundColor: tokens.primary }} />
              <span className="flex-1" style={{ backgroundColor: tokens.secondary }} />
              <span className="flex-1" style={{ backgroundColor: tokens.destructiveColor }} />
            </div>
          </button>
        );
      })}
    </div>
  );
}

function BulletList({ items }: { items: string[] }) {
  return (
    <ul className="flex flex-col gap-2">
      {items.map((item) => (
        <li
          key={item}
          className="flex items-start gap-2 rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-secondary)] p-3 text-sm text-[var(--color-text-primary)]"
        >
          <Check
            className="mt-0.5 h-4 w-4 shrink-0 text-[var(--color-accent)]"
            aria-hidden="true"
          />
          <span>{item}</span>
        </li>
      ))}
    </ul>
  );
}

function buildPages(themeLabel: string): OnboardingPage[] {
  return [
    {
      title: 'Welcome to PrismTask',
      subtitle:
        'A focused task manager with AI-powered natural language, habit tracking, and productivity analytics. Sync seamlessly between your web client and the Android app.',
      icon: <Sparkles className="h-8 w-8" aria-hidden="true" />,
      body: null,
    },
    {
      title: 'Pick a Theme',
      subtitle: `Each theme is its own visual system. You're currently using ${themeLabel} — swap any time from Settings.`,
      icon: <Wand2 className="h-8 w-8" aria-hidden="true" />,
      body: <ThemePickerBody />,
    },
    {
      title: 'Smart Task Management',
      subtitle: 'Capture, schedule, and review with the same shape across web and mobile.',
      icon: <Target className="h-8 w-8" aria-hidden="true" />,
      body: (
        <BulletList
          items={[
            'Projects, subtasks, tags, and priorities',
            'Recurrence: daily, weekly, biweekly, custom month days, after-completion',
            'Flags, scheduled start times, and a dedicated archive',
          ]}
        />
      ),
    },
    {
      title: 'Natural Language Quick Add',
      subtitle:
        'Type tasks the way you think. The parser extracts due dates, tags (#), projects (@), and priority (!) automatically.',
      icon: <Sparkles className="h-8 w-8" aria-hidden="true" />,
      body: (
        <BulletList
          items={[
            '"Call dentist tomorrow !high #health"',
            '"Ship parity PR Friday @work"',
            'Multi-line or bulk commands route to a preview you can approve',
          ]}
        />
      ),
    },
    {
      title: 'Habits with Forgiveness Streaks',
      subtitle:
        'Build streaks that are kind to human schedules. Miss a day? The streak bends, it does not break.',
      icon: <Sprout className="h-8 w-8" aria-hidden="true" />,
      body: (
        <BulletList
          items={[
            'Daily or weekly frequencies with optional active-day filters',
            'Contribution grid + day-of-week + streak analytics',
            'Bookable habits with history',
          ]}
        />
      ),
    },
    {
      title: 'Templates',
      subtitle: 'Reuse task, habit, and project blueprints. Great for repeating routines and starter projects.',
      icon: <FileText className="h-8 w-8" aria-hidden="true" />,
      body: (
        <BulletList
          items={[
            'Quick-add via "/templatename" in the quick-add bar',
            'Built-in templates seeded on first run',
            'Manage from Templates in the sidebar',
          ]}
        />
      ),
    },
    {
      title: 'Calendar, Matrix, and Planner Views',
      subtitle: 'See your work the way it fits the moment — week, month, timeline, Eisenhower, Pomodoro, or time-block.',
      icon: <LayoutGrid className="h-8 w-8" aria-hidden="true" />,
      body: (
        <BulletList
          items={[
            'Week / month / timeline in Calendar',
            'AI Eisenhower auto-classification + time-block planning',
            'Pomodoro planner with session suggestions',
          ]}
        />
      ),
    },
    {
      title: 'Brain Mode (on Android)',
      subtitle:
        'Android adds ND-friendly modes (Brain Mode, Focus Release, forgiveness streaks). Web support is coming — your Android preferences will carry over once it lands.',
      icon: <Calendar className="h-8 w-8" aria-hidden="true" />,
      body: null,
    },
    {
      title: "You're Set",
      subtitle: "Finish onboarding to dive in. You won't see this flow again on any device for this account.",
      icon: <Check className="h-8 w-8" aria-hidden="true" />,
      body: null,
    },
  ];
}

export function OnboardingScreen() {
  const navigate = useNavigate();
  const uid = useAuthStore((s) => s.firebaseUser?.uid);
  const markCompleted = useOnboardingStore((s) => s.markCompleted);
  const themeKey = useThemeStore((s) => s.themeKey);

  const [pageIdx, setPageIdx] = useState(0);
  const [finishing, setFinishing] = useState(false);

  const pages = useMemo(() => buildPages(THEMES[themeKey].label), [themeKey]);
  const isLastPage = pageIdx === pages.length - 1;
  const page = pages[pageIdx];

  const finish = async () => {
    if (!uid) {
      toast.error('Sign-in state lost. Please sign in again.');
      navigate('/login', { replace: true });
      return;
    }
    setFinishing(true);
    await markCompleted(uid);
    navigate('/', { replace: true });
  };

  const next = () => {
    if (isLastPage) {
      finish();
      return;
    }
    setPageIdx((i) => Math.min(i + 1, pages.length - 1));
  };

  const back = () => {
    setPageIdx((i) => Math.max(i - 1, 0));
  };

  const skip = () => {
    setPageIdx(pages.length - 1);
  };

  return (
    <div className="flex min-h-screen flex-col bg-[var(--color-bg-primary)] text-[var(--color-text-primary)]">
      {!isLastPage && (
        <div className="flex justify-end px-4 pt-4">
          <Button variant="ghost" size="sm" onClick={skip}>
            Skip
          </Button>
        </div>
      )}

      <main className="flex-1 overflow-y-auto">
        <PageShell
          icon={page.icon}
          title={page.title}
          subtitle={page.subtitle}
        >
          {page.body}
        </PageShell>
      </main>

      <footer className="flex items-center justify-between gap-3 border-t border-[var(--color-border)] bg-[var(--color-bg-card)] px-6 py-4">
        <Button
          variant="ghost"
          onClick={back}
          disabled={pageIdx === 0}
          aria-label="Previous page"
        >
          <ChevronLeft className="h-4 w-4" />
          Back
        </Button>

        <div
          className="flex items-center gap-1.5"
          role="tablist"
          aria-label="Onboarding progress"
        >
          {pages.map((_, i) => (
            <button
              key={i}
              role="tab"
              aria-selected={i === pageIdx}
              aria-label={`Go to step ${i + 1}`}
              onClick={() => setPageIdx(i)}
              className={`h-1.5 rounded-full transition-all ${
                i === pageIdx
                  ? 'w-6 bg-[var(--color-accent)]'
                  : 'w-1.5 bg-[var(--color-border)] hover:bg-[var(--color-text-secondary)]'
              }`}
            />
          ))}
        </div>

        <Button onClick={next} disabled={finishing}>
          {isLastPage ? (finishing ? 'Starting…' : 'Get Started') : (
            <>
              Next
              <ChevronRight className="h-4 w-4" />
            </>
          )}
        </Button>
      </footer>
    </div>
  );
}
