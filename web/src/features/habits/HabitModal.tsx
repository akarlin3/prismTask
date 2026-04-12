import { useState, useEffect } from 'react';
import { toast } from 'sonner';
import { useHabitStore } from '@/stores/habitStore';
import { Modal } from '@/components/ui/Modal';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Toggle } from '@/components/ui/Toggle';
import type { Habit, HabitFrequency } from '@/types/habit';

const EMOJI_OPTIONS = [
  '💪', '📚', '🧘', '💧', '🏃', '🎯', '📝', '🎨',
  '🎸', '💤', '🍎', '🧠', '❤️', '🌟', '🔥', '✨',
];

const COLOR_OPTIONS = [
  '#6366f1', '#8b5cf6', '#ec4899', '#ef4444',
  '#f97316', '#f59e0b', '#22c55e', '#10b981',
  '#14b8a6', '#06b6d4', '#3b82f6', '#6b7280',
];

const DAY_LABELS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
const DAY_VALUES = [1, 2, 3, 4, 5, 6, 7]; // ISO day of week

interface HabitModalProps {
  habit: Habit | null;
  onClose: () => void;
}

export function HabitModal({ habit, onClose }: HabitModalProps) {
  const { createHabit, updateHabit } = useHabitStore();
  const isEditing = !!habit;

  const [name, setName] = useState(habit?.name || '');
  const [description, setDescription] = useState(habit?.description || '');
  const [icon, setIcon] = useState(habit?.icon || '🎯');
  const [color, setColor] = useState(habit?.color || '#6366f1');
  const [category, setCategory] = useState(habit?.category || '');
  const [frequency, setFrequency] = useState<HabitFrequency>(
    habit?.frequency || 'daily',
  );
  const [targetCount, setTargetCount] = useState(habit?.target_count || 1);
  const [activeDays, setActiveDays] = useState<number[]>(() => {
    if (!habit?.active_days_json) return [1, 2, 3, 4, 5, 6, 7];
    try {
      const parsed = JSON.parse(habit.active_days_json);
      return Array.isArray(parsed) ? parsed : [1, 2, 3, 4, 5, 6, 7];
    } catch {
      return [1, 2, 3, 4, 5, 6, 7];
    }
  });
  const [saving, setSaving] = useState(false);

  // Get unique categories from existing habits for autocomplete
  const existingCategories = useHabitStore(
    (s) => [...new Set(s.habits.map((h) => h.category).filter(Boolean))] as string[],
  );

  const toggleDay = (day: number) => {
    setActiveDays((prev) =>
      prev.includes(day) ? prev.filter((d) => d !== day) : [...prev, day],
    );
  };

  const handleSubmit = async () => {
    if (!name.trim()) {
      toast.error('Habit name is required');
      return;
    }

    setSaving(true);
    try {
      const data = {
        name: name.trim(),
        description: description.trim() || undefined,
        icon,
        color,
        category: category.trim() || undefined,
        frequency,
        target_count: targetCount,
        active_days_json:
          frequency === 'daily' && activeDays.length < 7
            ? JSON.stringify(activeDays.sort((a, b) => a - b))
            : undefined,
      };

      if (isEditing) {
        await updateHabit(habit.id, data);
        toast.success('Habit updated');
      } else {
        await createHabit(data);
        toast.success('Habit created');
      }
      onClose();
    } catch {
      toast.error(isEditing ? 'Failed to update habit' : 'Failed to create habit');
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      isOpen
      onClose={onClose}
      title={isEditing ? 'Edit Habit' : 'New Habit'}
      size="md"
      footer={
        <div className="flex justify-end gap-3">
          <Button variant="ghost" onClick={onClose} disabled={saving}>
            Cancel
          </Button>
          <Button onClick={handleSubmit} loading={saving}>
            {isEditing ? 'Save Changes' : 'Create Habit'}
          </Button>
        </div>
      }
    >
      <div className="flex flex-col gap-5">
        {/* Name */}
        <Input
          label="Name"
          placeholder="e.g., Morning Exercise"
          value={name}
          onChange={(e) => setName(e.target.value)}
          autoFocus
        />

        {/* Description */}
        <div className="flex flex-col gap-1.5">
          <label className="text-sm font-medium text-[var(--color-text-primary)]">
            Description
          </label>
          <textarea
            className="w-full rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-card)] px-3 py-2 text-sm text-[var(--color-text-primary)] placeholder-[var(--color-text-secondary)] outline-none transition-colors focus:border-[var(--color-accent)] focus:ring-1 focus:ring-[var(--color-accent)]"
            placeholder="Optional description..."
            rows={2}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
          />
        </div>

        {/* Icon Picker */}
        <div className="flex flex-col gap-1.5">
          <label className="text-sm font-medium text-[var(--color-text-primary)]">
            Icon
          </label>
          <div className="flex flex-wrap gap-2">
            {EMOJI_OPTIONS.map((emoji) => (
              <button
                key={emoji}
                type="button"
                onClick={() => setIcon(emoji)}
                className={`flex h-9 w-9 items-center justify-center rounded-lg text-lg transition-all ${
                  icon === emoji
                    ? 'bg-[var(--color-accent)]/15 ring-2 ring-[var(--color-accent)]'
                    : 'bg-[var(--color-bg-secondary)] hover:bg-[var(--color-bg-primary)]'
                }`}
              >
                {emoji}
              </button>
            ))}
          </div>
        </div>

        {/* Color Picker */}
        <div className="flex flex-col gap-1.5">
          <label className="text-sm font-medium text-[var(--color-text-primary)]">
            Color
          </label>
          <div className="flex flex-wrap gap-2">
            {COLOR_OPTIONS.map((c) => (
              <button
                key={c}
                type="button"
                onClick={() => setColor(c)}
                className={`h-8 w-8 rounded-full transition-all ${
                  color === c
                    ? 'ring-2 ring-offset-2 ring-offset-[var(--color-bg-card)]'
                    : 'hover:scale-110'
                }`}
                style={{
                  backgroundColor: c,
                  ringColor: color === c ? c : undefined,
                }}
              />
            ))}
          </div>
        </div>

        {/* Category */}
        <div className="flex flex-col gap-1.5">
          <label className="text-sm font-medium text-[var(--color-text-primary)]">
            Category
          </label>
          <input
            className="w-full rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-card)] px-3 py-2 text-sm text-[var(--color-text-primary)] placeholder-[var(--color-text-secondary)] outline-none transition-colors focus:border-[var(--color-accent)] focus:ring-1 focus:ring-[var(--color-accent)]"
            placeholder="e.g., Health, Fitness, Learning"
            value={category}
            onChange={(e) => setCategory(e.target.value)}
            list="habit-categories"
          />
          <datalist id="habit-categories">
            {existingCategories.map((cat) => (
              <option key={cat} value={cat} />
            ))}
          </datalist>
        </div>

        {/* Frequency */}
        <div className="flex flex-col gap-1.5">
          <label className="text-sm font-medium text-[var(--color-text-primary)]">
            Frequency
          </label>
          <div className="flex gap-2">
            {(['daily', 'weekly'] as const).map((f) => (
              <button
                key={f}
                type="button"
                onClick={() => setFrequency(f)}
                className={`rounded-lg px-4 py-2 text-sm font-medium transition-colors ${
                  frequency === f
                    ? 'bg-[var(--color-accent)] text-white'
                    : 'bg-[var(--color-bg-secondary)] text-[var(--color-text-secondary)] hover:text-[var(--color-text-primary)]'
                }`}
              >
                {f === 'daily' ? 'Daily' : 'Weekly'}
              </button>
            ))}
          </div>
        </div>

        {/* Target Count */}
        <div className="flex flex-col gap-1.5">
          <label className="text-sm font-medium text-[var(--color-text-primary)]">
            How many times per {frequency === 'daily' ? 'day' : 'week'}?
          </label>
          <div className="flex items-center gap-3">
            <button
              type="button"
              onClick={() => setTargetCount(Math.max(1, targetCount - 1))}
              className="flex h-8 w-8 items-center justify-center rounded-lg bg-[var(--color-bg-secondary)] text-[var(--color-text-primary)] hover:bg-[var(--color-border)] transition-colors"
            >
              -
            </button>
            <span className="w-8 text-center text-sm font-medium text-[var(--color-text-primary)]">
              {targetCount}
            </span>
            <button
              type="button"
              onClick={() => setTargetCount(Math.min(99, targetCount + 1))}
              className="flex h-8 w-8 items-center justify-center rounded-lg bg-[var(--color-bg-secondary)] text-[var(--color-text-primary)] hover:bg-[var(--color-border)] transition-colors"
            >
              +
            </button>
          </div>
        </div>

        {/* Active Days (daily only) */}
        {frequency === 'daily' && (
          <div className="flex flex-col gap-1.5">
            <label className="text-sm font-medium text-[var(--color-text-primary)]">
              Active Days
            </label>
            <div className="flex gap-2">
              {DAY_LABELS.map((label, idx) => {
                const dayValue = DAY_VALUES[idx];
                const isActive = activeDays.includes(dayValue);
                return (
                  <button
                    key={dayValue}
                    type="button"
                    onClick={() => toggleDay(dayValue)}
                    className={`flex h-9 w-9 items-center justify-center rounded-full text-xs font-medium transition-colors ${
                      isActive
                        ? 'bg-[var(--color-accent)] text-white'
                        : 'bg-[var(--color-bg-secondary)] text-[var(--color-text-secondary)] hover:text-[var(--color-text-primary)]'
                    }`}
                  >
                    {label}
                  </button>
                );
              })}
            </div>
          </div>
        )}
      </div>
    </Modal>
  );
}
