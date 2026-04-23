/**
 * Web-side "starter library" of habit and project templates, matching
 * the user-visible concept on Android (HabitTemplateEntity /
 * ProjectTemplateEntity) without a backend endpoint.
 *
 * Android stores user-created templates in Room; on web we don't have
 * backend endpoints for those, so this curated list is the source for
 * the Habit + Project tabs on TemplateListScreen. Users apply a
 * starter to create a live Firestore habit / project-with-tasks.
 *
 * This is intentionally small — the full Android template-authoring UX
 * needs backend tables and is out of scope for the parity push.
 */

export interface HabitTemplate {
  id: string;
  name: string;
  description: string;
  icon: string;
  color: string;
  frequency: 'daily' | 'weekly';
  target_count: number;
}

export interface ProjectTaskTemplate {
  title: string;
  description?: string;
}

export interface ProjectTemplate {
  id: string;
  name: string;
  description: string;
  color: string;
  icon: string;
  tasks: ProjectTaskTemplate[];
}

export const STARTER_HABITS: HabitTemplate[] = [
  {
    id: 'drink-water',
    name: 'Drink Water',
    description: 'Hit a daily hydration target.',
    icon: '💧',
    color: '#06b6d4',
    frequency: 'daily',
    target_count: 8,
  },
  {
    id: 'morning-meditation',
    name: 'Morning Meditation',
    description: 'Start the day with 10 minutes of breath practice.',
    icon: '🧘',
    color: '#a855f7',
    frequency: 'daily',
    target_count: 1,
  },
  {
    id: 'read-20',
    name: 'Read 20 Minutes',
    description: 'Short daily reading block.',
    icon: '📖',
    color: '#f59e0b',
    frequency: 'daily',
    target_count: 1,
  },
  {
    id: 'workout',
    name: 'Workout',
    description: 'Dedicated movement block — weights, run, or class.',
    icon: '🏋️',
    color: '#ef4444',
    frequency: 'weekly',
    target_count: 3,
  },
  {
    id: 'journal',
    name: 'Journal',
    description: 'Reflective journaling — reminds you to write.',
    icon: '✍️',
    color: '#8b5cf6',
    frequency: 'daily',
    target_count: 1,
  },
  {
    id: 'walk-outside',
    name: 'Walk Outside',
    description: '20-minute outdoor walk.',
    icon: '🚶',
    color: '#22c55e',
    frequency: 'daily',
    target_count: 1,
  },
];

export const STARTER_PROJECTS: ProjectTemplate[] = [
  {
    id: 'ship-feature',
    name: 'Ship a Feature',
    description: 'Plan, build, review, release.',
    color: '#6366f1',
    icon: '🚀',
    tasks: [
      { title: 'Write a one-pager' },
      { title: 'Draft the technical plan' },
      { title: 'Break plan into implementation tasks' },
      { title: 'Code review + merge' },
      { title: 'Release + monitor' },
    ],
  },
  {
    id: 'trip-planning',
    name: 'Trip Planning',
    description: 'Book, pack, confirm, enjoy.',
    color: '#14b8a6',
    icon: '✈️',
    tasks: [
      { title: 'Book transportation' },
      { title: 'Book lodging' },
      { title: 'Build a day-by-day itinerary' },
      { title: 'Pack checklist' },
      { title: 'Out-of-office + autoresponders' },
    ],
  },
  {
    id: 'home-move',
    name: 'Home Move',
    description: 'End-to-end checklist for moving houses.',
    color: '#f97316',
    icon: '📦',
    tasks: [
      { title: 'Choose moving date' },
      { title: 'Get 3 mover quotes' },
      { title: 'Change-of-address forms' },
      { title: 'Pack by room' },
      { title: 'Schedule utilities turn-off / turn-on' },
      { title: 'Clean old place' },
    ],
  },
  {
    id: 'learn-new-skill',
    name: 'Learn a New Skill',
    description: 'Structured 30-day learning sprint.',
    color: '#a855f7',
    icon: '🎓',
    tasks: [
      { title: 'Pick the skill + a measurable goal' },
      { title: 'Find 2–3 resources (book, course, tutorial)' },
      { title: 'Block 30 min/day on the calendar' },
      { title: 'Week 1: fundamentals' },
      { title: 'Week 2: first project' },
      { title: 'Week 3: feedback + iterate' },
      { title: 'Week 4: share what you learned' },
    ],
  },
];
