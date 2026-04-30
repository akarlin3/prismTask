import { useCallback, useEffect, useState } from 'react';
import {
  ChevronLeft,
  ChevronRight,
  ArrowUpDown,
  Users,
  Calendar,
  Activity,
  TrendingUp,
  Search,
  X,
  Eye,
  ArrowLeft,
} from 'lucide-react';
import { toast } from 'sonner';
import {
  adminActivityLogsApi,
  type ActivityLogSummary,
  type ActivityLogStats,
} from '@/api/adminActivityLogs';

function formatDate(iso: string): string {
  if (!iso) return '\u2014';
  const d = new Date(iso);
  return d.toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

function actionColor(action: string): string {
  if (action.startsWith('create') || action.startsWith('add')) {
    return 'bg-green-500/20 text-green-400';
  }
  if (action.startsWith('delete') || action.startsWith('remove')) {
    return 'bg-red-500/20 text-red-400';
  }
  if (action.startsWith('update') || action.startsWith('edit')) {
    return 'bg-blue-500/20 text-blue-400';
  }
  if (action.startsWith('complete') || action.startsWith('finish')) {
    return 'bg-purple-500/20 text-purple-400';
  }
  return 'bg-[var(--color-bg-secondary)] text-[var(--color-text-secondary)]';
}

function entityTypeColor(type: string | null): string {
  switch (type) {
    case 'task':
      return 'bg-blue-500/20 text-blue-400';
    case 'member':
      return 'bg-orange-500/20 text-orange-400';
    case 'comment':
      return 'bg-purple-500/20 text-purple-400';
    default:
      return 'bg-[var(--color-bg-secondary)] text-[var(--color-text-secondary)]';
  }
}

// ---------------------------------------------------------------------------
// Stats Card
// ---------------------------------------------------------------------------

function StatsCard({ stats }: { stats: ActivityLogStats | null }) {
  if (!stats) return null;
  const cards = [
    { label: 'Total Events', value: stats.total_logs, icon: Activity },
    { label: 'Today', value: stats.logs_today, icon: Calendar },
    { label: 'This Week', value: stats.logs_this_week, icon: TrendingUp },
    { label: 'Unique Users', value: stats.unique_users, icon: Users },
  ];
  return (
    <div className="space-y-3">
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        {cards.map(({ label, value, icon: Icon }) => (
          <div
            key={label}
            className="flex items-center gap-3 rounded-xl border border-[var(--color-border)] bg-[var(--color-bg-card)] p-4"
          >
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-[var(--color-accent)]/10">
              <Icon className="h-5 w-5 text-[var(--color-accent)]" />
            </div>
            <div>
              <p className="text-xs text-[var(--color-text-secondary)]">{label}</p>
              <p className="text-lg font-semibold text-[var(--color-text-primary)]">{value}</p>
            </div>
          </div>
        ))}
      </div>

      {/* Top actions */}
      {stats.top_actions.length > 0 && (
        <div className="rounded-xl border border-[var(--color-border)] bg-[var(--color-bg-card)] p-4">
          <h3 className="mb-2 text-sm font-semibold text-[var(--color-text-primary)]">
            Top Actions
          </h3>
          <div className="flex flex-wrap gap-2">
            {stats.top_actions.map(({ action, count }) => (
              <span
                key={action}
                className={`rounded-md px-2 py-1 text-xs font-medium ${actionColor(action)}`}
              >
                {action} ({count})
              </span>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Detail View
// ---------------------------------------------------------------------------

function LogDetailView({
  log,
  onBack,
}: {
  log: ActivityLogSummary;
  onBack: () => void;
}) {
  let parsedMeta: Record<string, unknown> | null = null;
  if (log.metadata_json) {
    try {
      parsedMeta = JSON.parse(log.metadata_json);
    } catch {
      // Not JSON — show raw
    }
  }

  return (
    <div>
      <button
        onClick={onBack}
        className="mb-4 flex items-center gap-1 text-sm text-[var(--color-accent)] hover:underline"
      >
        <ArrowLeft className="h-4 w-4" />
        Back To List
      </button>

      <div className="flex flex-col gap-4 lg:flex-row">
        {/* Main content */}
        <div className="flex-1 rounded-xl border border-[var(--color-border)] bg-[var(--color-bg-card)] p-4">
          <h2 className="mb-3 text-lg font-semibold text-[var(--color-text-primary)]">
            Activity Detail
          </h2>
          <dl className="space-y-3 text-sm">
            <div>
              <dt className="text-[var(--color-text-secondary)]">Action</dt>
              <dd>
                <span className={`rounded-md px-2 py-0.5 text-xs font-medium ${actionColor(log.action)}`}>
                  {log.action}
                </span>
              </dd>
            </div>
            {log.entity_title && (
              <div>
                <dt className="text-[var(--color-text-secondary)]">Entity Title</dt>
                <dd className="font-medium text-[var(--color-text-primary)]">{log.entity_title}</dd>
              </div>
            )}
            {log.entity_type && (
              <div>
                <dt className="text-[var(--color-text-secondary)]">Entity Type</dt>
                <dd>
                  <span className={`rounded-md px-2 py-0.5 text-xs font-medium ${entityTypeColor(log.entity_type)}`}>
                    {log.entity_type}
                  </span>
                </dd>
              </div>
            )}
            {log.entity_id != null && (
              <div>
                <dt className="text-[var(--color-text-secondary)]">Entity ID</dt>
                <dd className="font-medium text-[var(--color-text-primary)]">{log.entity_id}</dd>
              </div>
            )}
            <div>
              <dt className="text-[var(--color-text-secondary)]">Project ID</dt>
              <dd className="font-medium text-[var(--color-text-primary)]">{log.project_id}</dd>
            </div>
          </dl>

          {/* Metadata */}
          {log.metadata_json && (
            <div className="mt-4">
              <h3 className="mb-2 text-sm font-semibold text-[var(--color-text-primary)]">Metadata</h3>
              <pre className="max-h-[40vh] overflow-auto whitespace-pre-wrap rounded-lg bg-[var(--color-bg-primary)] p-4 font-mono text-xs leading-relaxed text-[var(--color-text-primary)] border border-[var(--color-border)]">
                {parsedMeta ? JSON.stringify(parsedMeta, null, 2) : log.metadata_json}
              </pre>
            </div>
          )}
        </div>

        {/* Sidebar */}
        <div className="w-full shrink-0 rounded-xl border border-[var(--color-border)] bg-[var(--color-bg-card)] p-4 lg:w-72">
          <h3 className="mb-3 text-sm font-semibold text-[var(--color-text-primary)]">
            Info
          </h3>
          <dl className="space-y-2 text-sm">
            <div>
              <dt className="text-[var(--color-text-secondary)]">User</dt>
              <dd className="font-medium text-[var(--color-text-primary)]">
                {log.user_email || `User #${log.user_id}`}
              </dd>
            </div>
            <div>
              <dt className="text-[var(--color-text-secondary)]">Timestamp</dt>
              <dd className="font-medium text-[var(--color-text-primary)]">
                {formatDate(log.created_at)}
              </dd>
            </div>
            <div>
              <dt className="text-[var(--color-text-secondary)]">Log ID</dt>
              <dd className="font-medium text-[var(--color-text-primary)]">
                {log.id}
              </dd>
            </div>
          </dl>
        </div>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Main Panel
// ---------------------------------------------------------------------------

export function ActivityLogsPanel() {
  const [logs, setLogs] = useState<ActivityLogSummary[]>([]);
  const [stats, setStats] = useState<ActivityLogStats | null>(null);
  const [detail, setDetail] = useState<ActivityLogSummary | null>(null);
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [total, setTotal] = useState(0);
  const [sort, setSort] = useState<'newest' | 'oldest'>('newest');
  const [filterUserId, setFilterUserId] = useState('');
  const [filterAction, setFilterAction] = useState('');
  const [loading, setLoading] = useState(true);

  const fetchLogs = useCallback(async () => {
    setLoading(true);
    try {
      const params: Record<string, unknown> = { page, per_page: 20, sort };
      if (filterUserId.trim()) {
        params.user_id = parseInt(filterUserId, 10);
      }
      if (filterAction.trim()) {
        params.action = filterAction.trim();
      }
      const data = await adminActivityLogsApi.list(params as never);
      setLogs(data.items);
      setTotalPages(data.total_pages);
      setTotal(data.total);
    } catch {
      toast.error('Failed to load activity logs.');
    } finally {
      setLoading(false);
    }
  }, [page, sort, filterUserId, filterAction]);

  const fetchStats = useCallback(async () => {
    try {
      const data = await adminActivityLogsApi.stats();
      setStats(data);
    } catch {
      // Non-critical
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- data-fetch effect: load logs on mount and when filters change
    fetchLogs();
  }, [fetchLogs]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- data-fetch effect: load stats on mount
    fetchStats();
  }, [fetchStats]);

  const handleView = async (logId: number) => {
    try {
      const data = await adminActivityLogsApi.get(logId);
      setDetail(data);
    } catch {
      toast.error('Failed to load activity log details.');
    }
  };

  // Detail view
  if (detail) {
    return (
      <LogDetailView
        log={detail}
        onBack={() => setDetail(null)}
      />
    );
  }

  return (
    <div className="space-y-4">
      {/* Summary */}
      <div className="flex items-center gap-2">
        <Activity className="h-5 w-5 text-[var(--color-text-secondary)]" />
        <p className="text-sm text-[var(--color-text-secondary)]">
          {total} total event{total !== 1 ? 's' : ''}
        </p>
      </div>

      {/* Stats */}
      <StatsCard stats={stats} />

      {/* Filters */}
      <div className="flex flex-wrap items-center gap-2">
        <div className="relative flex-1 min-w-[200px] max-w-xs">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-[var(--color-text-secondary)]" />
          <input
            type="text"
            placeholder="Filter by user ID..."
            value={filterUserId}
            onChange={(e) => {
              setFilterUserId(e.target.value);
              setPage(1);
            }}
            className="w-full rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-card)] py-2 pl-9 pr-8 text-sm text-[var(--color-text-primary)] placeholder:text-[var(--color-text-secondary)] focus:border-[var(--color-accent)] focus:outline-none"
          />
          {filterUserId && (
            <button
              onClick={() => {
                setFilterUserId('');
                setPage(1);
              }}
              className="absolute right-2 top-1/2 -translate-y-1/2 text-[var(--color-text-secondary)] hover:text-[var(--color-text-primary)]"
            >
              <X className="h-4 w-4" />
            </button>
          )}
        </div>

        <div className="relative min-w-[180px] max-w-xs">
          <input
            type="text"
            placeholder="Filter by action..."
            value={filterAction}
            onChange={(e) => {
              setFilterAction(e.target.value);
              setPage(1);
            }}
            className="w-full rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-card)] py-2 px-3 text-sm text-[var(--color-text-primary)] placeholder:text-[var(--color-text-secondary)] focus:border-[var(--color-accent)] focus:outline-none"
          />
          {filterAction && (
            <button
              onClick={() => {
                setFilterAction('');
                setPage(1);
              }}
              className="absolute right-2 top-1/2 -translate-y-1/2 text-[var(--color-text-secondary)] hover:text-[var(--color-text-primary)]"
            >
              <X className="h-4 w-4" />
            </button>
          )}
        </div>

        <button
          onClick={() => {
            setSort(sort === 'newest' ? 'oldest' : 'newest');
            setPage(1);
          }}
          className="flex items-center gap-1.5 rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-card)] px-3 py-2 text-sm text-[var(--color-text-secondary)] hover:text-[var(--color-text-primary)] transition-colors"
        >
          <ArrowUpDown className="h-4 w-4" />
          {sort === 'newest' ? 'Newest First' : 'Oldest First'}
        </button>
      </div>

      {/* Table */}
      <div className="overflow-x-auto rounded-xl border border-[var(--color-border)] bg-[var(--color-bg-card)]">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-[var(--color-border)] text-left">
              <th className="px-4 py-3 font-medium text-[var(--color-text-secondary)]">Date</th>
              <th className="px-4 py-3 font-medium text-[var(--color-text-secondary)]">User</th>
              <th className="px-4 py-3 font-medium text-[var(--color-text-secondary)]">Action</th>
              <th className="px-4 py-3 font-medium text-[var(--color-text-secondary)] hidden sm:table-cell">Entity Type</th>
              <th className="px-4 py-3 font-medium text-[var(--color-text-secondary)] hidden md:table-cell">Entity</th>
              <th className="px-4 py-3 font-medium text-[var(--color-text-secondary)] hidden lg:table-cell">Project</th>
              <th className="px-4 py-3 font-medium text-[var(--color-text-secondary)]">Details</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              Array.from({ length: 5 }).map((_, i) => (
                <tr key={i} className="border-b border-[var(--color-border)]">
                  {Array.from({ length: 7 }).map((_, j) => (
                    <td key={j} className="px-4 py-3">
                      <div className="h-4 w-20 animate-pulse rounded bg-[var(--color-bg-secondary)]" />
                    </td>
                  ))}
                </tr>
              ))
            ) : logs.length === 0 ? (
              <tr>
                <td
                  colSpan={7}
                  className="px-4 py-12 text-center text-[var(--color-text-secondary)]"
                >
                  No activity logs found.
                </td>
              </tr>
            ) : (
              logs.map((log) => (
                <tr
                  key={log.id}
                  className="border-b border-[var(--color-border)] last:border-b-0 hover:bg-[var(--color-bg-secondary)]/50 transition-colors"
                >
                  <td className="px-4 py-3 text-[var(--color-text-primary)] whitespace-nowrap">
                    {formatDate(log.created_at)}
                  </td>
                  <td className="px-4 py-3 text-[var(--color-text-primary)]">
                    {log.user_email || `#${log.user_id}`}
                  </td>
                  <td className="px-4 py-3">
                    <span className={`rounded-md px-2 py-0.5 text-xs font-medium ${actionColor(log.action)}`}>
                      {log.action}
                    </span>
                  </td>
                  <td className="px-4 py-3 hidden sm:table-cell">
                    {log.entity_type ? (
                      <span className={`rounded-md px-2 py-0.5 text-xs font-medium ${entityTypeColor(log.entity_type)}`}>
                        {log.entity_type}
                      </span>
                    ) : (
                      <span className="text-[var(--color-text-secondary)]">{'\u2014'}</span>
                    )}
                  </td>
                  <td className="px-4 py-3 text-[var(--color-text-primary)] hidden md:table-cell max-w-[200px] truncate">
                    {log.entity_title || (log.entity_id != null ? `#${log.entity_id}` : '\u2014')}
                  </td>
                  <td className="px-4 py-3 text-[var(--color-text-secondary)] hidden lg:table-cell">
                    #{log.project_id}
                  </td>
                  <td className="px-4 py-3">
                    <button
                      onClick={() => handleView(log.id)}
                      className="rounded-md p-1.5 text-[var(--color-text-secondary)] hover:bg-[var(--color-accent)]/10 hover:text-[var(--color-accent)] transition-colors"
                      title="View Details"
                    >
                      <Eye className="h-4 w-4" />
                    </button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex items-center justify-between">
          <p className="text-sm text-[var(--color-text-secondary)]">
            Page {page} of {totalPages}
          </p>
          <div className="flex items-center gap-1">
            <button
              disabled={page <= 1}
              onClick={() => setPage((p) => Math.max(1, p - 1))}
              className="rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-card)] p-2 text-[var(--color-text-secondary)] hover:text-[var(--color-text-primary)] disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
            >
              <ChevronLeft className="h-4 w-4" />
            </button>
            <button
              disabled={page >= totalPages}
              onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
              className="rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-card)] p-2 text-[var(--color-text-secondary)] hover:text-[var(--color-text-primary)] disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
            >
              <ChevronRight className="h-4 w-4" />
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
