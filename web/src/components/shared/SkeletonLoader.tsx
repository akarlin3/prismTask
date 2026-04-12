interface SkeletonProps {
  className?: string;
}

export function Skeleton({ className = '' }: SkeletonProps) {
  return (
    <div
      className={`animate-pulse rounded bg-[var(--color-bg-secondary)] ${className}`}
      aria-hidden="true"
    />
  );
}

export function TaskRowSkeleton() {
  return (
    <div className="flex items-center gap-3 px-3 py-2.5">
      <Skeleton className="h-5 w-5 rounded" />
      <div className="flex-1 space-y-1.5">
        <Skeleton className="h-4 w-3/4" />
        <Skeleton className="h-3 w-1/3" />
      </div>
      <Skeleton className="h-5 w-16 rounded-full" />
    </div>
  );
}

export function TaskListSkeleton({ count = 6 }: { count?: number }) {
  return (
    <div className="space-y-1">
      {Array.from({ length: count }, (_, i) => (
        <TaskRowSkeleton key={i} />
      ))}
    </div>
  );
}

export function ProjectCardSkeleton() {
  return (
    <div className="rounded-xl border border-[var(--color-border)] bg-[var(--color-bg-card)] p-4">
      <div className="flex items-center gap-3 mb-3">
        <Skeleton className="h-10 w-10 rounded-lg" />
        <div className="flex-1 space-y-1.5">
          <Skeleton className="h-4 w-1/2" />
          <Skeleton className="h-3 w-1/4" />
        </div>
      </div>
      <Skeleton className="h-2 w-full rounded-full" />
    </div>
  );
}

export function ProjectListSkeleton({ count = 4 }: { count?: number }) {
  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {Array.from({ length: count }, (_, i) => (
        <ProjectCardSkeleton key={i} />
      ))}
    </div>
  );
}

export function HabitRowSkeleton() {
  return (
    <div className="flex items-center gap-3 rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-card)] p-4">
      <Skeleton className="h-10 w-10 rounded-full" />
      <div className="flex-1 space-y-1.5">
        <Skeleton className="h-4 w-1/2" />
        <Skeleton className="h-3 w-1/3" />
      </div>
      <div className="flex gap-1">
        {Array.from({ length: 7 }, (_, i) => (
          <Skeleton key={i} className="h-6 w-6 rounded" />
        ))}
      </div>
    </div>
  );
}

export function HabitListSkeleton({ count = 4 }: { count?: number }) {
  return (
    <div className="space-y-3">
      {Array.from({ length: count }, (_, i) => (
        <HabitRowSkeleton key={i} />
      ))}
    </div>
  );
}

export function TodaySkeleton() {
  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="space-y-1.5">
          <Skeleton className="h-7 w-48" />
          <Skeleton className="h-4 w-32" />
        </div>
        <Skeleton className="h-16 w-16 rounded-full" />
      </div>

      {/* Sections */}
      {['Overdue', 'Today', 'Upcoming'].map((section) => (
        <div key={section} className="space-y-2">
          <Skeleton className="h-5 w-24" />
          <TaskListSkeleton count={3} />
        </div>
      ))}
    </div>
  );
}

export function SettingsSkeleton() {
  return (
    <div className="max-w-2xl space-y-6">
      <Skeleton className="h-7 w-32" />
      {Array.from({ length: 5 }, (_, i) => (
        <div key={i} className="space-y-3 rounded-xl border border-[var(--color-border)] p-4">
          <Skeleton className="h-5 w-40" />
          <Skeleton className="h-4 w-full" />
          <Skeleton className="h-10 w-full rounded-lg" />
        </div>
      ))}
    </div>
  );
}
