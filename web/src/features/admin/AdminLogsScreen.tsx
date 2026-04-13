import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Bug,
  Trash2,
  Eye,
  ChevronLeft,
  ChevronRight,
  ArrowUpDown,
  Users,
  HardDrive,
  BarChart3,
  Calendar,
  ArrowLeft,
  Search,
  X,
} from 'lucide-react';
import { toast } from 'sonner';
import { useAuthStore } from '@/stores/authStore';
import {
  adminDebugLogsApi,
  type DebugLogSummary,
  type DebugLogDetail,
  type DebugLogStats,
} from '@/api/adminDebugLogs';

function formatBytes(bytes: number): string {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return `${parseFloat((bytes / Math.pow(k, i)).toFixed(1))} ${sizes[i]}`;
}

function formatDate(iso: string): string {
  if (!iso) return '—';
  const d = new Date(iso);
  return d.toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

function severityColor(severity: string): string {
  switch (severity) {
    case 'CRITICAL':
      return 'bg-red-500/20 text-red-400';
    case 'MAJOR':
      return 'bg-orange-500/20 text-orange-400';
    case 'MINOR':
      return 'bg-yellow-500/20 text-yellow-400';
    default:
      return 'bg-[var(--color-bg-secondary)] text-[var(--color-text-secondary)]';
  }
}

function statusColor(status: string): string {
  switch (status) {
    case 'SUBMITTED':
      return 'bg-blue-500/20 text-blue-400';
    case 'ACKNOWLEDGED':
      return 'bg-purple-500/20 text-purple-400';
    case 'FIXED':
      return 'bg-green-500/20 text-green-400';
    case 'WONT_FIX':
      return 'bg-gray-500/20 text-gray-400';
    default:
      return 'bg-[var(--color-bg-secondary)] text-[var(--color-text-secondary)]';
  }
}

// ---------------------------------------------------------------------------
// Stats Card
// ---------------------------------------------------------------------------

function StatsCard({ stats }: { stats: DebugLogStats | null }) {
  if (!stats) return null;
  const cards = [
    { label: 'Total Logs', value: stats.total_logs, icon: Bug },
    { label: 'This Week', value: stats.logs_this_week, icon: Calendar },
    { label: 'Unique Users', value: stats.unique_users, icon: Users },
    { label: 'Storage Used', value: formatBytes(stats.storage_used_bytes), icon: HardDrive },
  ];
  return (
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
  );
}

// ---------------------------------------------------------------------------
// Confirm Dialog (inline)
// ---------------------------------------------------------------------------

function ConfirmDeleteDialog({
  isOpen,
  onConfirm,
  onCancel,
}: {
  isOpen: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}) {
  if (!isOpen) return null;
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="absolute inset-0 bg-black/50" onClick={onCancel} />
      <div className="relative z-10 mx-4 w-full max-w-sm rounded-xl border border-[var(--color-border)] bg-[var(--color-bg-card)] p-6 shadow-2xl">
        <h3 className="text-lg font-semibold text-[var(--color-text-primary)]">
          Delete Log
        </h3>
        <p className="mt-2 text-sm text-[var(--color-text-secondary)]">
          Are you sure you want to permanently delete this log? This action
          cannot be undone.
        </p>
        <div className="mt-4 flex justify-end gap-2">
          <button
            onClick={onCancel}
            className="rounded-lg px-4 py-2 text-sm font-medium text-[var(--color-text-secondary)] hover:bg-[var(--color-bg-secondary)] transition-colors"
          >
            Cancel
          </button>
          <button
            onClick={onConfirm}
            className="rounded-lg bg-red-600 px-4 py-2 text-sm font-medium text-white hover:bg-red-700 transition-colors"
          >
            Delete
          </button>
        </div>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Log Detail View
// ---------------------------------------------------------------------------

function LogDetailView({
  log,
  onBack,
  onDelete,
}: {
  log: DebugLogDetail;
  onBack: () => void;
  onDelete: (id: string) => void;
}) {
  const [confirmDelete, setConfirmDelete] = useState(false);

  const metaEntries = Object.entries(log.metadata).filter(
    ([, v]) => v != null && v !== '',
  );

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
          <div className="mb-3 flex items-center justify-between">
            <h2 className="text-lg font-semibold text-[var(--color-text-primary)]">
              Log Content
            </h2>
            <button
              onClick={() => setConfirmDelete(true)}
              className="flex items-center gap-1 rounded-lg bg-red-600/10 px-3 py-1.5 text-sm font-medium text-red-400 hover:bg-red-600/20 transition-colors"
            >
              <Trash2 className="h-4 w-4" />
              Delete
            </button>
          </div>
          <pre className="max-h-[60vh] overflow-auto whitespace-pre-wrap rounded-lg bg-[var(--color-bg-primary)] p-4 font-mono text-xs leading-relaxed text-[var(--color-text-primary)] border border-[var(--color-border)]">
            {log.content || '(empty)'}
          </pre>
        </div>

        {/* Metadata sidebar */}
        <div className="w-full shrink-0 rounded-xl border border-[var(--color-border)] bg-[var(--color-bg-card)] p-4 lg:w-72">
          <h3 className="mb-3 text-sm font-semibold text-[var(--color-text-primary)]">
            Metadata
          </h3>
          <dl className="space-y-2 text-sm">
            <div>
              <dt className="text-[var(--color-text-secondary)]">User</dt>
              <dd className="font-medium text-[var(--color-text-primary)]">
                {log.user_email || log.user_id || 'Anonymous'}
              </dd>
            </div>
            <div>
              <dt className="text-[var(--color-text-secondary)]">Timestamp</dt>
              <dd className="font-medium text-[var(--color-text-primary)]">
                {formatDate(log.timestamp)}
              </dd>
            </div>
            {metaEntries.map(([key, value]) => (
              <div key={key}>
                <dt className="text-[var(--color-text-secondary)]">
                  {key.replace(/_/g, ' ')}
                </dt>
                <dd className="font-medium text-[var(--color-text-primary)] break-all">
                  {String(value)}
                </dd>
              </div>
            ))}
          </dl>
        </div>
      </div>

      <ConfirmDeleteDialog
        isOpen={confirmDelete}
        onConfirm={() => {
          setConfirmDelete(false);
          onDelete(log.id);
        }}
        onCancel={() => setConfirmDelete(false)}
      />
    </div>
  );
}

// ---------------------------------------------------------------------------
// Main Screen
// ---------------------------------------------------------------------------

export function AdminLogsScreen() {
  const navigate = useNavigate();
  const user = useAuthStore((s) => s.user);

  const [logs, setLogs] = useState<DebugLogSummary[]>([]);
  const [stats, setStats] = useState<DebugLogStats | null>(null);
  const [detail, setDetail] = useState<DebugLogDetail | null>(null);
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [total, setTotal] = useState(0);
  const [sort, setSort] = useState<'newest' | 'oldest'>('newest');
  const [filterUserId, setFilterUserId] = useState('');
  const [loading, setLoading] = useState(true);
  const [confirmDeleteId, setConfirmDeleteId] = useState<string | null>(null);

  // Redirect non-admins
  useEffect(() => {
    if (user && !user.is_admin) {
      toast.error("You don't have admin access.");
      navigate('/', { replace: true });
    }
  }, [user, navigate]);

  const fetchLogs = useCallback(async () => {
    setLoading(true);
    try {
      const params: Record<string, unknown> = { page, per_page: 20, sort };
      if (filterUserId.trim()) {
        params.user_id = parseInt(filterUserId, 10);
      }
      const data = await adminDebugLogsApi.list(params as never);
      setLogs(data.items);
      setTotalPages(data.total_pages);
      setTotal(data.total);
    } catch {
      toast.error('Failed to load debug logs.');
    } finally {
      setLoading(false);
    }
  }, [page, sort, filterUserId]);

  const fetchStats = useCallback(async () => {
    try {
      const data = await adminDebugLogsApi.stats();
      setStats(data);
    } catch {
      // Non-critical
    }
  }, []);

  useEffect(() => {
    fetchLogs();
  }, [fetchLogs]);

  useEffect(() => {
    fetchStats();
  }, [fetchStats]);

  const handleView = async (logId: string) => {
    try {
      const data = await adminDebugLogsApi.get(logId);
      setDetail(data);
    } catch {
      toast.error('Failed to load log details.');
    }
  };

  const handleDelete = async (logId: string) => {
    try {
      await adminDebugLogsApi.delete(logId);
      toast.success('Log deleted.');
      setDetail(null);
      setConfirmDeleteId(null);
      fetchLogs();
      fetchStats();
    } catch {
      toast.error('Failed to delete log.');
    }
  };

  // Detail view
  if (detail) {
    return (
      <div className="mx-auto max-w-6xl">
        <LogDetailView
          log={detail}
          onBack={() => setDetail(null)}
          onDelete={handleDelete}
        />
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-6xl space-y-4">
      {/* Header */}
      <div className="flex items-center gap-3">
        <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-[var(--color-accent)]/10">
          <BarChart3 className="h-5 w-5 text-[var(--color-accent)]" />
        </div>
        <div>
          <h1 className="text-xl font-bold text-[var(--color-text-primary)]">
            Debug Log Viewer
          </h1>
          <p className="text-sm text-[var(--color-text-secondary)]">
            {total} total log{total !== 1 ? 's' : ''}
          </p>
        </div>
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
              <th className="px-4 py-3 font-medium text-[var(--color-text-secondary)] hidden sm:table-cell">Category</th>
              <th className="px-4 py-3 font-medium text-[var(--color-text-secondary)] hidden md:table-cell">Severity</th>
              <th className="px-4 py-3 font-medium text-[var(--color-text-secondary)] hidden md:table-cell">Status</th>
              <th className="px-4 py-3 font-medium text-[var(--color-text-secondary)] hidden lg:table-cell">Device</th>
              <th className="px-4 py-3 font-medium text-[var(--color-text-secondary)] hidden lg:table-cell">Version</th>
              <th className="px-4 py-3 font-medium text-[var(--color-text-secondary)] hidden lg:table-cell">Size</th>
              <th className="px-4 py-3 font-medium text-[var(--color-text-secondary)]">Actions</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              Array.from({ length: 5 }).map((_, i) => (
                <tr key={i} className="border-b border-[var(--color-border)]">
                  {Array.from({ length: 9 }).map((_, j) => (
                    <td key={j} className="px-4 py-3">
                      <div className="h-4 w-20 animate-pulse rounded bg-[var(--color-bg-secondary)]" />
                    </td>
                  ))}
                </tr>
              ))
            ) : logs.length === 0 ? (
              <tr>
                <td
                  colSpan={9}
                  className="px-4 py-12 text-center text-[var(--color-text-secondary)]"
                >
                  No debug logs found.
                </td>
              </tr>
            ) : (
              logs.map((log) => (
                <tr
                  key={log.id}
                  className="border-b border-[var(--color-border)] last:border-b-0 hover:bg-[var(--color-bg-secondary)]/50 transition-colors"
                >
                  <td className="px-4 py-3 text-[var(--color-text-primary)] whitespace-nowrap">
                    {formatDate(log.timestamp)}
                  </td>
                  <td className="px-4 py-3 text-[var(--color-text-primary)]">
                    {log.user_email || log.user_id || '—'}
                  </td>
                  <td className="px-4 py-3 hidden sm:table-cell">
                    <span className="rounded-md bg-[var(--color-bg-secondary)] px-2 py-0.5 text-xs text-[var(--color-text-secondary)]">
                      {log.category}
                    </span>
                  </td>
                  <td className="px-4 py-3 hidden md:table-cell">
                    <span className={`rounded-md px-2 py-0.5 text-xs font-medium ${severityColor(log.severity)}`}>
                      {log.severity}
                    </span>
                  </td>
                  <td className="px-4 py-3 hidden md:table-cell">
                    <span className={`rounded-md px-2 py-0.5 text-xs font-medium ${statusColor(log.status)}`}>
                      {log.status}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-[var(--color-text-secondary)] hidden lg:table-cell whitespace-nowrap">
                    {log.device_info || '—'}
                  </td>
                  <td className="px-4 py-3 text-[var(--color-text-secondary)] hidden lg:table-cell">
                    {log.app_version || '—'}
                  </td>
                  <td className="px-4 py-3 text-[var(--color-text-secondary)] hidden lg:table-cell whitespace-nowrap">
                    {formatBytes(log.size_bytes)}
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex items-center gap-1">
                      <button
                        onClick={() => handleView(log.id)}
                        className="rounded-md p-1.5 text-[var(--color-text-secondary)] hover:bg-[var(--color-accent)]/10 hover:text-[var(--color-accent)] transition-colors"
                        title="View"
                      >
                        <Eye className="h-4 w-4" />
                      </button>
                      <button
                        onClick={() => setConfirmDeleteId(log.id)}
                        className="rounded-md p-1.5 text-[var(--color-text-secondary)] hover:bg-red-500/10 hover:text-red-400 transition-colors"
                        title="Delete"
                      >
                        <Trash2 className="h-4 w-4" />
                      </button>
                    </div>
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

      {/* Delete confirmation */}
      <ConfirmDeleteDialog
        isOpen={confirmDeleteId !== null}
        onConfirm={() => {
          if (confirmDeleteId) handleDelete(confirmDeleteId);
        }}
        onCancel={() => setConfirmDeleteId(null)}
      />
    </div>
  );
}
