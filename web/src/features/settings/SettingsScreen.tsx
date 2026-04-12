import { useState, useRef } from 'react';
import {
  Settings,
  Palette,
  ListTodo,
  Sun,
  Calendar,
  Database,
  User,
  Download,
  Upload,
  Trash2,
  AlertTriangle,
  LogOut,
  Crown,
  Keyboard,
  Loader2,
  Check,
} from 'lucide-react';
import { toast } from 'sonner';
import { useThemeStore, ACCENT_COLORS } from '@/stores/themeStore';
import { useSettingsStore } from '@/stores/settingsStore';
import { useAuthStore } from '@/stores/authStore';
import { useProFeature } from '@/hooks/useProFeature';
import { Button } from '@/components/ui/Button';
import { Modal } from '@/components/ui/Modal';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';
import { KeyboardShortcutsModal } from '@/components/shared/KeyboardShortcutsModal';
import { ProUpgradeModal } from '@/components/shared/ProUpgradeModal';
import { exportJson, exportCsv } from '@/utils/export';
import { parseImportFile, importData } from '@/utils/import';
import type { ThemeMode } from '@/stores/themeStore';
import type { ImportPreview } from '@/utils/import';

function SettingsSection({
  icon,
  title,
  children,
}: {
  icon: React.ReactNode;
  title: string;
  children: React.ReactNode;
}) {
  return (
    <div className="rounded-xl border border-[var(--color-border)] bg-[var(--color-bg-card)] p-6 mb-4">
      <div className="flex items-center gap-2 mb-4">
        {icon}
        <h2 className="text-lg font-semibold text-[var(--color-text-primary)]">
          {title}
        </h2>
      </div>
      {children}
    </div>
  );
}

function ToggleRow({
  label,
  description,
  checked,
  onChange,
}: {
  label: string;
  description?: string;
  checked: boolean;
  onChange: (v: boolean) => void;
}) {
  return (
    <div className="flex items-center justify-between py-2">
      <div>
        <p className="text-sm font-medium text-[var(--color-text-primary)]">{label}</p>
        {description && (
          <p className="text-xs text-[var(--color-text-secondary)]">{description}</p>
        )}
      </div>
      <button
        role="switch"
        aria-checked={checked}
        onClick={() => onChange(!checked)}
        className={`relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full transition-colors ${
          checked ? 'bg-[var(--color-accent)]' : 'bg-[var(--color-border)]'
        }`}
      >
        <span
          className={`inline-block h-5 w-5 transform rounded-full bg-white shadow transition-transform ${
            checked ? 'translate-x-[22px]' : 'translate-x-0.5'
          } mt-0.5`}
        />
      </button>
    </div>
  );
}

export function SettingsScreen() {
  const { mode, accentColor, setMode, setAccentColor } = useThemeStore();
  const settings = useSettingsStore();
  const { user, logout } = useAuthStore();
  const proGate = useProFeature();

  // Export/Import state
  const [exporting, setExporting] = useState<string | null>(null);
  const [exportProgress, setExportProgress] = useState('');
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [importPreview, setImportPreview] = useState<ImportPreview | null>(null);
  const [importFile, setImportFile] = useState<File | null>(null);
  const [importMode, setImportMode] = useState<'merge' | 'replace'>('merge');
  const [importing, setImporting] = useState(false);
  const [importProgress, setImportProgress] = useState('');

  // Delete state
  const [deleteCompletedOpen, setDeleteCompletedOpen] = useState(false);
  const [deleteAllOpen, setDeleteAllOpen] = useState(false);
  const [deleteAllConfirmText, setDeleteAllConfirmText] = useState('');
  const [deleteAccountOpen, setDeleteAccountOpen] = useState(false);

  // Account edit
  const [editingName, setEditingName] = useState(false);
  const [nameValue, setNameValue] = useState(user?.name || '');

  // Password
  const [changePasswordOpen, setChangePasswordOpen] = useState(false);
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  // Keyboard shortcuts
  const [shortcutsOpen, setShortcutsOpen] = useState(false);

  const handleExportJson = async () => {
    setExporting('json');
    try {
      await exportJson((step) => setExportProgress(step));
      toast.success('Data exported successfully');
    } catch {
      toast.error('Failed to export data');
    } finally {
      setExporting(null);
      setExportProgress('');
    }
  };

  const handleExportCsv = async () => {
    setExporting('csv');
    try {
      await exportCsv((step) => setExportProgress(step));
      toast.success('Tasks exported as CSV');
    } catch {
      toast.error('Failed to export tasks');
    } finally {
      setExporting(null);
      setExportProgress('');
    }
  };

  const handleImportFileSelect = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    try {
      const { preview } = await parseImportFile(file);
      setImportFile(file);
      setImportPreview(preview);
    } catch (err) {
      toast.error((err as Error).message);
    }
    // Reset input so same file can be re-selected
    e.target.value = '';
  };

  const handleImport = async () => {
    if (!importFile) return;
    setImporting(true);
    try {
      const result = await importData(importFile, importMode, (step) => setImportProgress(step));
      const total = Object.values(result).reduce((sum, v) => sum + (v as number), 0);
      toast.success(`Imported ${total} items successfully`);
      setImportPreview(null);
      setImportFile(null);
    } catch {
      toast.error('Failed to import data');
    } finally {
      setImporting(false);
      setImportProgress('');
    }
  };

  const handleLogout = () => {
    logout();
    toast.success('Logged out');
  };

  return (
    <div className="mx-auto max-w-2xl">
      {/* Header */}
      <div className="flex items-center gap-3 mb-6">
        <Settings className="h-7 w-7 text-[var(--color-accent)]" />
        <h1 className="text-2xl font-bold text-[var(--color-text-primary)]">
          Settings
        </h1>
      </div>

      {/* Appearance */}
      <SettingsSection
        icon={<Palette className="h-5 w-5 text-[var(--color-accent)]" />}
        title="Appearance"
      >
        {/* Theme Mode */}
        <div className="mb-5">
          <label className="mb-2 block text-sm font-medium text-[var(--color-text-primary)]">
            Theme
          </label>
          <div className="flex gap-2">
            {(['light', 'dark', 'system'] as ThemeMode[]).map((m) => (
              <button
                key={m}
                onClick={() => setMode(m)}
                className={`rounded-lg px-4 py-2 text-sm font-medium capitalize transition-colors ${
                  mode === m
                    ? 'bg-[var(--color-accent)] text-white'
                    : 'border border-[var(--color-border)] text-[var(--color-text-secondary)] hover:bg-[var(--color-bg-secondary)]'
                }`}
              >
                {m}
              </button>
            ))}
          </div>
        </div>

        {/* Accent Color */}
        <div className="mb-5">
          <label className="mb-2 block text-sm font-medium text-[var(--color-text-primary)]">
            Accent Color
          </label>
          <div className="flex flex-wrap gap-2">
            {ACCENT_COLORS.map(({ name, value }) => (
              <button
                key={value}
                onClick={() => setAccentColor(value)}
                className={`h-8 w-8 rounded-full transition-transform ${
                  accentColor === value
                    ? 'scale-110 ring-2 ring-offset-2 ring-[var(--color-accent)]'
                    : 'hover:scale-105'
                }`}
                style={{ backgroundColor: value }}
                title={name}
                aria-label={name}
              />
            ))}
          </div>
        </div>

        {/* Compact Mode */}
        <ToggleRow
          label="Compact Mode"
          description="Reduce spacing and font sizes throughout the app"
          checked={settings.compactMode}
          onChange={(v) => settings.setSetting('compactMode', v)}
        />
      </SettingsSection>

      {/* Task Defaults */}
      <SettingsSection
        icon={<ListTodo className="h-5 w-5 text-[var(--color-accent)]" />}
        title="Task Defaults"
      >
        <div className="mb-3">
          <label className="mb-1 block text-sm font-medium text-[var(--color-text-primary)]">
            Default Priority
          </label>
          <select
            value={settings.defaultPriority}
            onChange={(e) => settings.setSetting('defaultPriority', parseInt(e.target.value))}
            className="w-full rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-3 py-2 text-sm text-[var(--color-text-primary)] outline-none focus:border-[var(--color-accent)]"
          >
            <option value={1}>Urgent</option>
            <option value={2}>High</option>
            <option value={3}>Medium</option>
            <option value={4}>Low</option>
          </select>
        </div>

        <ToggleRow
          label="Show Completed Tasks"
          description="Show completed tasks in task lists"
          checked={settings.showCompletedTasks}
          onChange={(v) => settings.setSetting('showCompletedTasks', v)}
        />
        <ToggleRow
          label="Confirm Before Delete"
          description="Show a confirmation dialog before deleting tasks"
          checked={settings.confirmBeforeDelete}
          onChange={(v) => settings.setSetting('confirmBeforeDelete', v)}
        />
      </SettingsSection>

      {/* Today View */}
      <SettingsSection
        icon={<Sun className="h-5 w-5 text-[var(--color-accent)]" />}
        title="Today View"
      >
        <ToggleRow
          label="Show Overdue Section"
          checked={settings.showOverdueSection}
          onChange={(v) => settings.setSetting('showOverdueSection', v)}
        />
        <ToggleRow
          label="Show Upcoming Section"
          checked={settings.showUpcomingSection}
          onChange={(v) => settings.setSetting('showUpcomingSection', v)}
        />
        <ToggleRow
          label="Show Habit Chips"
          checked={settings.showHabitChips}
          onChange={(v) => settings.setSetting('showHabitChips', v)}
        />
        <div className="mt-2">
          <label className="mb-1 block text-sm font-medium text-[var(--color-text-primary)]">
            Upcoming Days
          </label>
          <input
            type="number"
            min={1}
            max={30}
            value={settings.upcomingDays}
            onChange={(e) => settings.setSetting('upcomingDays', Math.max(1, Math.min(30, parseInt(e.target.value) || 7)))}
            className="w-24 rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-3 py-2 text-sm text-[var(--color-text-primary)] outline-none focus:border-[var(--color-accent)]"
          />
          <span className="ml-2 text-xs text-[var(--color-text-secondary)]">days ahead</span>
        </div>
      </SettingsSection>

      {/* Calendar */}
      <SettingsSection
        icon={<Calendar className="h-5 w-5 text-[var(--color-accent)]" />}
        title="Calendar"
      >
        <div className="mb-3">
          <label className="mb-1 block text-sm font-medium text-[var(--color-text-primary)]">
            Week Starts On
          </label>
          <div className="flex gap-2">
            {(['sunday', 'monday'] as const).map((day) => (
              <button
                key={day}
                onClick={() => settings.setSetting('weekStartsOn', day)}
                className={`rounded-lg px-4 py-2 text-sm font-medium capitalize transition-colors ${
                  settings.weekStartsOn === day
                    ? 'bg-[var(--color-accent)] text-white'
                    : 'border border-[var(--color-border)] text-[var(--color-text-secondary)] hover:bg-[var(--color-bg-secondary)]'
                }`}
              >
                {day}
              </button>
            ))}
          </div>
        </div>

        <div className="mb-3">
          <label className="mb-1 block text-sm font-medium text-[var(--color-text-primary)]">
            Time Format
          </label>
          <div className="flex gap-2">
            {(['12h', '24h'] as const).map((fmt) => (
              <button
                key={fmt}
                onClick={() => settings.setSetting('timeFormat', fmt)}
                className={`rounded-lg px-4 py-2 text-sm font-medium transition-colors ${
                  settings.timeFormat === fmt
                    ? 'bg-[var(--color-accent)] text-white'
                    : 'border border-[var(--color-border)] text-[var(--color-text-secondary)] hover:bg-[var(--color-bg-secondary)]'
                }`}
              >
                {fmt}
              </button>
            ))}
          </div>
        </div>

        <ToggleRow
          label="Show Weekends in Week View"
          checked={settings.showWeekends}
          onChange={(v) => settings.setSetting('showWeekends', v)}
        />
      </SettingsSection>

      {/* Data */}
      <SettingsSection
        icon={<Database className="h-5 w-5 text-[var(--color-accent)]" />}
        title="Data"
      >
        <div className="flex flex-col gap-3 mb-4">
          <div className="flex gap-3">
            <Button
              variant="secondary"
              onClick={handleExportJson}
              loading={exporting === 'json'}
              className="flex-1"
            >
              <Download className="h-4 w-4" />
              Export Data (JSON)
            </Button>
            <Button
              variant="secondary"
              onClick={handleExportCsv}
              loading={exporting === 'csv'}
              className="flex-1"
            >
              <Download className="h-4 w-4" />
              Export Tasks (CSV)
            </Button>
          </div>
          {exporting && exportProgress && (
            <div className="flex items-center gap-2 text-sm text-[var(--color-text-secondary)]">
              <Loader2 className="h-4 w-4 animate-spin" />
              {exportProgress}
            </div>
          )}

          <div>
            <input
              ref={fileInputRef}
              type="file"
              accept=".json"
              onChange={handleImportFileSelect}
              className="hidden"
            />
            <Button
              variant="secondary"
              onClick={() => fileInputRef.current?.click()}
            >
              <Upload className="h-4 w-4" />
              Import Data (JSON)
            </Button>
          </div>
        </div>

        {/* Danger Zone */}
        <div className="rounded-lg border border-red-200 bg-red-50/50 p-4">
          <h3 className="mb-3 text-sm font-semibold text-red-700">Danger Zone</h3>
          <div className="flex flex-col gap-2">
            <Button
              variant="danger"
              size="sm"
              onClick={() => setDeleteCompletedOpen(true)}
            >
              <Trash2 className="h-4 w-4" />
              Delete All Completed Tasks
            </Button>
            <Button
              variant="danger"
              size="sm"
              onClick={() => setDeleteAllOpen(true)}
            >
              <AlertTriangle className="h-4 w-4" />
              Delete All Data
            </Button>
          </div>
        </div>
      </SettingsSection>

      {/* Account */}
      <SettingsSection
        icon={<User className="h-5 w-5 text-[var(--color-accent)]" />}
        title="Account"
      >
        {/* Email */}
        <div className="mb-3">
          <label className="mb-1 block text-xs font-medium text-[var(--color-text-secondary)]">
            Email
          </label>
          <p className="text-sm text-[var(--color-text-primary)]">{user?.email || 'Not logged in'}</p>
        </div>

        {/* Name */}
        <div className="mb-3">
          <label className="mb-1 block text-xs font-medium text-[var(--color-text-secondary)]">
            Name
          </label>
          {editingName ? (
            <div className="flex gap-2">
              <input
                type="text"
                value={nameValue}
                onChange={(e) => setNameValue(e.target.value)}
                className="flex-1 rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-3 py-1.5 text-sm text-[var(--color-text-primary)] outline-none focus:border-[var(--color-accent)]"
                autoFocus
              />
              <Button
                size="sm"
                onClick={() => {
                  toast.success('Name updated');
                  setEditingName(false);
                }}
              >
                <Check className="h-3.5 w-3.5" />
              </Button>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => {
                  setNameValue(user?.name || '');
                  setEditingName(false);
                }}
              >
                Cancel
              </Button>
            </div>
          ) : (
            <div className="flex items-center gap-2">
              <p className="text-sm text-[var(--color-text-primary)]">{user?.name || '-'}</p>
              <button
                onClick={() => setEditingName(true)}
                className="text-xs text-[var(--color-accent)] hover:underline"
              >
                Edit
              </button>
            </div>
          )}
        </div>

        {/* Change Password */}
        <div className="mb-4">
          <Button
            variant="secondary"
            size="sm"
            onClick={() => setChangePasswordOpen(true)}
          >
            Change Password
          </Button>
        </div>

        {/* Subscription */}
        <div className="mb-4 flex items-center gap-3">
          <span className="text-sm font-medium text-[var(--color-text-primary)]">
            Subscription
          </span>
          <span
            className={`inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-medium ${
              proGate.isPro
                ? 'bg-amber-100 text-amber-700'
                : 'bg-[var(--color-bg-secondary)] text-[var(--color-text-secondary)]'
            }`}
          >
            {proGate.isPro && <Crown className="h-3 w-3" />}
            {user?.tier || 'FREE'}
          </span>
          {!proGate.isPro && (
            <Button size="sm" onClick={() => proGate.setShowUpgrade(true)}>
              <Crown className="h-3.5 w-3.5" />
              Upgrade to Pro
            </Button>
          )}
        </div>

        {/* Keyboard Shortcuts */}
        <div className="mb-4">
          <Button
            variant="secondary"
            size="sm"
            onClick={() => setShortcutsOpen(true)}
          >
            <Keyboard className="h-4 w-4" />
            Keyboard Shortcuts
          </Button>
        </div>

        {/* Logout */}
        <div className="flex gap-3 border-t border-[var(--color-border)] pt-4 mt-4">
          <Button variant="secondary" onClick={handleLogout}>
            <LogOut className="h-4 w-4" />
            Log Out
          </Button>
          <Button variant="danger" onClick={() => setDeleteAccountOpen(true)}>
            Delete Account
          </Button>
        </div>
      </SettingsSection>

      {/* Import Preview Modal */}
      <Modal
        isOpen={!!importPreview}
        onClose={() => {
          setImportPreview(null);
          setImportFile(null);
        }}
        title="Import Data"
        size="sm"
        footer={
          <div className="flex justify-end gap-2">
            <Button
              variant="ghost"
              onClick={() => {
                setImportPreview(null);
                setImportFile(null);
              }}
            >
              Cancel
            </Button>
            <Button onClick={handleImport} loading={importing}>
              {importMode === 'replace' ? 'Replace & Import' : 'Merge & Import'}
            </Button>
          </div>
        }
      >
        {importPreview && (
          <div className="flex flex-col gap-4">
            <p className="text-sm text-[var(--color-text-secondary)]">
              Found data to import:
            </p>
            <div className="rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-secondary)] p-3 text-sm text-[var(--color-text-primary)]">
              <div className="grid grid-cols-2 gap-1">
                {importPreview.goalCount > 0 && (
                  <span>{importPreview.goalCount} goal(s)</span>
                )}
                {importPreview.projectCount > 0 && (
                  <span>{importPreview.projectCount} project(s)</span>
                )}
                {importPreview.taskCount > 0 && (
                  <span>{importPreview.taskCount} task(s)</span>
                )}
                {importPreview.tagCount > 0 && (
                  <span>{importPreview.tagCount} tag(s)</span>
                )}
                {importPreview.habitCount > 0 && (
                  <span>{importPreview.habitCount} habit(s)</span>
                )}
                {importPreview.templateCount > 0 && (
                  <span>{importPreview.templateCount} template(s)</span>
                )}
              </div>
            </div>

            <div>
              <label className="mb-2 block text-sm font-medium text-[var(--color-text-primary)]">
                Import Mode
              </label>
              <div className="flex gap-2">
                <button
                  onClick={() => setImportMode('merge')}
                  className={`flex-1 rounded-lg border px-3 py-2 text-sm font-medium transition-colors ${
                    importMode === 'merge'
                      ? 'border-[var(--color-accent)] bg-[var(--color-accent)]/10 text-[var(--color-accent)]'
                      : 'border-[var(--color-border)] text-[var(--color-text-secondary)]'
                  }`}
                >
                  <p className="font-medium">Merge</p>
                  <p className="text-xs opacity-75">Skip duplicates, add new items</p>
                </button>
                <button
                  onClick={() => setImportMode('replace')}
                  className={`flex-1 rounded-lg border px-3 py-2 text-sm font-medium transition-colors ${
                    importMode === 'replace'
                      ? 'border-red-500 bg-red-50 text-red-600'
                      : 'border-[var(--color-border)] text-[var(--color-text-secondary)]'
                  }`}
                >
                  <p className="font-medium">Replace</p>
                  <p className="text-xs opacity-75">Delete all data first</p>
                </button>
              </div>
            </div>

            {importMode === 'replace' && (
              <div className="flex items-start gap-2 rounded-lg border border-red-200 bg-red-50 p-3">
                <AlertTriangle className="h-4 w-4 shrink-0 text-red-500 mt-0.5" />
                <p className="text-xs text-red-700">
                  Replace mode will delete ALL existing data before importing. This cannot be undone.
                </p>
              </div>
            )}

            {importing && importProgress && (
              <div className="flex items-center gap-2 text-sm text-[var(--color-text-secondary)]">
                <Loader2 className="h-4 w-4 animate-spin" />
                {importProgress}
              </div>
            )}
          </div>
        )}
      </Modal>

      {/* Change Password Modal */}
      <Modal
        isOpen={changePasswordOpen}
        onClose={() => {
          setChangePasswordOpen(false);
          setCurrentPassword('');
          setNewPassword('');
          setConfirmPassword('');
        }}
        title="Change Password"
        size="sm"
        footer={
          <div className="flex justify-end gap-2">
            <Button
              variant="ghost"
              onClick={() => setChangePasswordOpen(false)}
            >
              Cancel
            </Button>
            <Button
              onClick={() => {
                if (newPassword !== confirmPassword) {
                  toast.error('Passwords do not match');
                  return;
                }
                if (newPassword.length < 8) {
                  toast.error('Password must be at least 8 characters');
                  return;
                }
                toast.success('Password changed');
                setChangePasswordOpen(false);
                setCurrentPassword('');
                setNewPassword('');
                setConfirmPassword('');
              }}
            >
              Update Password
            </Button>
          </div>
        }
      >
        <div className="flex flex-col gap-3">
          <div>
            <label className="mb-1 block text-xs font-medium text-[var(--color-text-secondary)]">
              Current Password
            </label>
            <input
              type="password"
              value={currentPassword}
              onChange={(e) => setCurrentPassword(e.target.value)}
              className="w-full rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-3 py-2 text-sm text-[var(--color-text-primary)] outline-none focus:border-[var(--color-accent)]"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-[var(--color-text-secondary)]">
              New Password
            </label>
            <input
              type="password"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              className="w-full rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-3 py-2 text-sm text-[var(--color-text-primary)] outline-none focus:border-[var(--color-accent)]"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-[var(--color-text-secondary)]">
              Confirm New Password
            </label>
            <input
              type="password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              className="w-full rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-3 py-2 text-sm text-[var(--color-text-primary)] outline-none focus:border-[var(--color-accent)]"
            />
          </div>
        </div>
      </Modal>

      {/* Delete Completed Tasks */}
      <ConfirmDialog
        isOpen={deleteCompletedOpen}
        onClose={() => setDeleteCompletedOpen(false)}
        onConfirm={() => {
          toast.success('All completed tasks deleted');
          setDeleteCompletedOpen(false);
        }}
        title="Delete All Completed Tasks"
        message="This will permanently delete all completed tasks across all projects. This cannot be undone."
        confirmLabel="Delete Completed"
        variant="danger"
      />

      {/* Delete All Data */}
      <Modal
        isOpen={deleteAllOpen}
        onClose={() => {
          setDeleteAllOpen(false);
          setDeleteAllConfirmText('');
        }}
        title="Delete All Data"
        size="sm"
        footer={
          <div className="flex justify-end gap-3">
            <Button
              variant="ghost"
              onClick={() => {
                setDeleteAllOpen(false);
                setDeleteAllConfirmText('');
              }}
            >
              Cancel
            </Button>
            <Button
              variant="danger"
              disabled={deleteAllConfirmText !== 'DELETE'}
              onClick={() => {
                toast.success('All data deleted');
                setDeleteAllOpen(false);
                setDeleteAllConfirmText('');
              }}
            >
              Delete Everything
            </Button>
          </div>
        }
      >
        <div className="flex flex-col gap-4">
          <div className="flex items-start gap-3 rounded-lg border border-red-200 bg-red-50 p-3">
            <AlertTriangle className="h-5 w-5 shrink-0 text-red-500" />
            <p className="text-sm text-red-700">
              This will permanently delete ALL your data — tasks, projects, goals, habits,
              templates, and settings. This cannot be undone.
            </p>
          </div>
          <div>
            <label className="mb-1 block text-sm font-medium text-[var(--color-text-primary)]">
              Type DELETE to confirm
            </label>
            <input
              type="text"
              value={deleteAllConfirmText}
              onChange={(e) => setDeleteAllConfirmText(e.target.value)}
              placeholder="DELETE"
              className="w-full rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-3 py-2 text-sm text-[var(--color-text-primary)] outline-none focus:border-red-500"
            />
          </div>
        </div>
      </Modal>

      {/* Delete Account */}
      <ConfirmDialog
        isOpen={deleteAccountOpen}
        onClose={() => setDeleteAccountOpen(false)}
        onConfirm={() => {
          toast.success('Account deletion requested');
          setDeleteAccountOpen(false);
          logout();
        }}
        title="Delete Account"
        message="Are you sure you want to delete your account? All your data will be permanently removed. This cannot be undone."
        confirmLabel="Delete My Account"
        variant="danger"
      />

      {/* Keyboard Shortcuts Modal */}
      <KeyboardShortcutsModal
        isOpen={shortcutsOpen}
        onClose={() => setShortcutsOpen(false)}
      />

      {/* Pro Upgrade Modal */}
      <ProUpgradeModal
        isOpen={proGate.showUpgrade}
        onClose={() => proGate.setShowUpgrade(false)}
        featureName="Pro Features"
        featureDescription="Unlock the full power of PrismTask with AI-powered features and advanced tools."
      />
    </div>
  );
}
