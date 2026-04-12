import {
  createBrowserRouter,
  Navigate,
  type RouteObject,
} from 'react-router-dom';
import { AppShell } from '@/components/layout/AppShell';
import { ProtectedRoute } from './ProtectedRoute';

// Auth screens
import { LoginScreen } from '@/features/auth/LoginScreen';
import { RegisterScreen } from '@/features/auth/RegisterScreen';

// Feature screens
import { TodayScreen } from '@/features/today/TodayScreen';
import { TaskListScreen } from '@/features/tasks/TaskListScreen';
import { TaskDetailScreen } from '@/features/tasks/TaskDetailScreen';
import { ProjectListScreen } from '@/features/projects/ProjectListScreen';
import { ProjectDetailScreen } from '@/features/projects/ProjectDetailScreen';
import { HabitListScreen } from '@/features/habits/HabitListScreen';
import { HabitAnalyticsScreen } from '@/features/habits/HabitAnalyticsScreen';
import { WeekViewScreen } from '@/features/calendar/WeekViewScreen';
import { MonthViewScreen } from '@/features/calendar/MonthViewScreen';
import { TimelineScreen } from '@/features/calendar/TimelineScreen';
import { EisenhowerScreen } from '@/features/eisenhower/EisenhowerScreen';
import { PomodoroScreen } from '@/features/pomodoro/PomodoroScreen';
import { TemplateListScreen } from '@/features/templates/TemplateListScreen';
import { ArchiveScreen } from '@/features/archive/ArchiveScreen';
import { SettingsScreen } from '@/features/settings/SettingsScreen';

const routes: RouteObject[] = [
  // Public routes
  { path: '/login', element: <LoginScreen /> },
  { path: '/register', element: <RegisterScreen /> },

  // Protected routes inside AppShell
  {
    element: (
      <ProtectedRoute>
        <AppShell />
      </ProtectedRoute>
    ),
    children: [
      { index: true, element: <TodayScreen /> },
      { path: 'tasks', element: <TaskListScreen /> },
      { path: 'tasks/:id', element: <TaskDetailScreen /> },
      { path: 'projects', element: <ProjectListScreen /> },
      { path: 'projects/:id', element: <ProjectDetailScreen /> },
      { path: 'habits', element: <HabitListScreen /> },
      { path: 'habits/:id/analytics', element: <HabitAnalyticsScreen /> },
      { path: 'calendar/week', element: <WeekViewScreen /> },
      { path: 'calendar/month', element: <MonthViewScreen /> },
      { path: 'calendar/timeline', element: <TimelineScreen /> },
      { path: 'eisenhower', element: <EisenhowerScreen /> },
      { path: 'pomodoro', element: <PomodoroScreen /> },
      { path: 'templates', element: <TemplateListScreen /> },
      { path: 'archive', element: <ArchiveScreen /> },
      { path: 'settings', element: <SettingsScreen /> },
    ],
  },

  // Catch-all redirect
  { path: '*', element: <Navigate to="/" replace /> },
];

export const router = createBrowserRouter(routes);
