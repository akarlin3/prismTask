import { lazy, Suspense, type ComponentType } from 'react';
import {
  createBrowserRouter,
  Navigate,
  type RouteObject,
} from 'react-router-dom';
import { AppShell } from '@/components/layout/AppShell';
import { ProtectedRoute } from './ProtectedRoute';
import { TaskListSkeleton, ProjectListSkeleton, HabitListSkeleton, SettingsSkeleton } from '@/components/shared/SkeletonLoader';

// Auth screens (eagerly loaded — first screens users see)
import { LoginScreen } from '@/features/auth/LoginScreen';
import { RegisterScreen } from '@/features/auth/RegisterScreen';

// Core screens (eagerly loaded — most common)
import { TodayScreen } from '@/features/today/TodayScreen';
import { TaskListScreen } from '@/features/tasks/TaskListScreen';
import { ProjectListScreen } from '@/features/projects/ProjectListScreen';

// Lazy-loaded screens (loaded on demand)
/* eslint-disable react-refresh/only-export-components */
const TaskDetailScreen = lazy(() => import('@/features/tasks/TaskDetailScreen').then(m => ({ default: m.TaskDetailScreen })));
const ProjectDetailScreen = lazy(() => import('@/features/projects/ProjectDetailScreen').then(m => ({ default: m.ProjectDetailScreen })));
const HabitListScreen = lazy(() => import('@/features/habits/HabitListScreen').then(m => ({ default: m.HabitListScreen })));
const HabitAnalyticsScreen = lazy(() => import('@/features/habits/HabitAnalyticsScreen').then(m => ({ default: m.HabitAnalyticsScreen })));
const WeekViewScreen = lazy(() => import('@/features/calendar/WeekViewScreen').then(m => ({ default: m.WeekViewScreen })));
const MonthViewScreen = lazy(() => import('@/features/calendar/MonthViewScreen').then(m => ({ default: m.MonthViewScreen })));
const TimelineScreen = lazy(() => import('@/features/calendar/TimelineScreen').then(m => ({ default: m.TimelineScreen })));
const CalendarRedirect = lazy(() => import('@/features/calendar/CalendarRedirect').then(m => ({ default: m.CalendarRedirect })));
const EisenhowerScreen = lazy(() => import('@/features/eisenhower/EisenhowerScreen').then(m => ({ default: m.EisenhowerScreen })));
const PomodoroScreen = lazy(() => import('@/features/pomodoro/PomodoroScreen').then(m => ({ default: m.PomodoroScreen })));
const TemplateListScreen = lazy(() => import('@/features/templates/TemplateListScreen').then(m => ({ default: m.TemplateListScreen })));
const ArchiveScreen = lazy(() => import('@/features/archive/ArchiveScreen').then(m => ({ default: m.ArchiveScreen })));
const SettingsScreen = lazy(() => import('@/features/settings/SettingsScreen').then(m => ({ default: m.SettingsScreen })));

function LazyRoute({ Component, fallback }: { Component: ComponentType; fallback?: React.ReactNode }) {
  return (
    <Suspense fallback={fallback || <LoadingFallback />}>
      <Component />
    </Suspense>
  );
}

function LoadingFallback() {
  return (
    <div className="animate-pulse p-4">
      <div className="h-7 w-48 rounded bg-[var(--color-bg-secondary)] mb-4" />
      <TaskListSkeleton count={5} />
    </div>
  );
}

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
      { path: 'tasks/:id', element: <LazyRoute Component={TaskDetailScreen} fallback={<TaskListSkeleton />} /> },
      { path: 'projects', element: <ProjectListScreen /> },
      { path: 'projects/:id', element: <LazyRoute Component={ProjectDetailScreen} fallback={<ProjectListSkeleton />} /> },
      { path: 'habits', element: <LazyRoute Component={HabitListScreen} fallback={<HabitListSkeleton />} /> },
      { path: 'habits/:id/analytics', element: <LazyRoute Component={HabitAnalyticsScreen} /> },
      { path: 'calendar', element: <LazyRoute Component={CalendarRedirect} /> },
      { path: 'calendar/week', element: <LazyRoute Component={WeekViewScreen} /> },
      { path: 'calendar/month', element: <LazyRoute Component={MonthViewScreen} /> },
      { path: 'calendar/timeline', element: <LazyRoute Component={TimelineScreen} /> },
      { path: 'eisenhower', element: <LazyRoute Component={EisenhowerScreen} /> },
      { path: 'pomodoro', element: <LazyRoute Component={PomodoroScreen} /> },
      { path: 'templates', element: <LazyRoute Component={TemplateListScreen} /> },
      { path: 'archive', element: <LazyRoute Component={ArchiveScreen} /> },
      { path: 'settings', element: <LazyRoute Component={SettingsScreen} fallback={<SettingsSkeleton />} /> },
    ],
  },

  // Catch-all redirect
  { path: '*', element: <Navigate to="/" replace /> },
];

export const router = createBrowserRouter(routes);
