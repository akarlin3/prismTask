import { create } from 'zustand';

interface SettingsState {
  // Task Defaults
  defaultPriority: number;
  showCompletedTasks: boolean;
  confirmBeforeDelete: boolean;

  // Today View
  showOverdueSection: boolean;
  showUpcomingSection: boolean;
  showHabitChips: boolean;
  showBriefingCard: boolean;
  upcomingDays: number;
  /** Hour (0–23) at which the "logical day" rolls over. Matches
   *  Android's `DayBoundary` / `startOfDay` preference. 0 = midnight. */
  startOfDayHour: number;

  // Calendar
  weekStartsOn: 'sunday' | 'monday';
  timeFormat: '12h' | '24h';
  showWeekends: boolean;

  // Compact
  compactMode: boolean;

  // Actions
  setSetting: <K extends keyof SettingsState>(key: K, value: SettingsState[K]) => void;
  loadFromStorage: () => void;
}

const STORAGE_KEY = 'prismtask_settings';

function loadSettings(): Partial<SettingsState> {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? JSON.parse(raw) : {};
  } catch {
    return {};
  }
}

function saveSettings(state: Partial<SettingsState>) {
  const {
    defaultPriority,
    showCompletedTasks,
    confirmBeforeDelete,
    showOverdueSection,
    showUpcomingSection,
    showHabitChips,
    showBriefingCard,
    upcomingDays,
    startOfDayHour,
    weekStartsOn,
    timeFormat,
    showWeekends,
    compactMode,
  } = state as SettingsState;

  localStorage.setItem(
    STORAGE_KEY,
    JSON.stringify({
      defaultPriority,
      showCompletedTasks,
      confirmBeforeDelete,
      showOverdueSection,
      showUpcomingSection,
      showHabitChips,
      showBriefingCard,
      upcomingDays,
      startOfDayHour,
      weekStartsOn,
      timeFormat,
      showWeekends,
      compactMode,
    }),
  );
}

const defaults = {
  defaultPriority: 3,
  showCompletedTasks: true,
  confirmBeforeDelete: true,
  showOverdueSection: true,
  showUpcomingSection: true,
  showHabitChips: true,
  showBriefingCard: true,
  upcomingDays: 7,
  startOfDayHour: 0,
  weekStartsOn: 'sunday' as const,
  timeFormat: '12h' as const,
  showWeekends: true,
  compactMode: false,
};

const stored = loadSettings();

export const useSettingsStore = create<SettingsState>((set, get) => ({
  ...defaults,
  ...stored,

  setSetting: (key, value) => {
    set({ [key]: value } as Partial<SettingsState>);
    saveSettings({ ...get(), [key]: value });

    // Apply compact mode immediately
    if (key === 'compactMode') {
      document.documentElement.classList.toggle('compact', value as boolean);
    }
  },

  loadFromStorage: () => {
    const stored = loadSettings();
    set(stored);
    if (stored.compactMode) {
      document.documentElement.classList.add('compact');
    }
  },
}));
