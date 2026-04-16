import { useState } from 'react';
import { Pencil, Check } from 'lucide-react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/Button';
import { Checkbox } from '@/components/ui/Checkbox';
import { confirmSyllabus } from './syllabusApi';
import type {
  SyllabusParseResult,
  SyllabusTask,
  SyllabusEvent,
  SyllabusRecurringItem,
} from './syllabusTypes';

interface SyllabusReviewPanelProps {
  result: SyllabusParseResult;
  onDone: () => void;
}

const TYPE_COLORS: Record<string, string> = {
  exam: 'bg-red-100 text-red-700',
  quiz: 'bg-amber-100 text-amber-700',
  project: 'bg-purple-100 text-purple-700',
  assignment: 'bg-blue-100 text-blue-700',
  reading: 'bg-emerald-100 text-emerald-700',
  other: 'bg-gray-100 text-gray-600',
};

export function SyllabusReviewPanel({ result, onDone }: SyllabusReviewPanelProps) {
  const [checkedTasks, setCheckedTasks] = useState<Set<number>>(
    () => new Set(result.tasks.map((_, i) => i)),
  );
  const [checkedEvents, setCheckedEvents] = useState<Set<number>>(
    () => new Set(result.events.map((_, i) => i)),
  );
  const [checkedRecurring, setCheckedRecurring] = useState<Set<number>>(
    () => new Set(result.recurring_schedule.map((_, i) => i)),
  );

  const [editedTasks, setEditedTasks] = useState<Map<number, SyllabusTask>>(new Map());
  const [editedEvents, setEditedEvents] = useState<Map<number, SyllabusEvent>>(new Map());
  const [editedRecurring, setEditedRecurring] = useState<Map<number, SyllabusRecurringItem>>(
    new Map(),
  );

  const [confirming, setConfirming] = useState(false);

  const totalChecked = checkedTasks.size + checkedEvents.size + checkedRecurring.size;

  const getTask = (i: number) => editedTasks.get(i) ?? result.tasks[i];
  const getEvent = (i: number) => editedEvents.get(i) ?? result.events[i];
  const getRecurring = (i: number) => editedRecurring.get(i) ?? result.recurring_schedule[i];

  const toggleTask = (i: number) => {
    setCheckedTasks((prev) => {
      const next = new Set(prev);
      if (next.has(i)) { next.delete(i); } else { next.add(i); }
      return next;
    });
  };
  const toggleEvent = (i: number) => {
    setCheckedEvents((prev) => {
      const next = new Set(prev);
      if (next.has(i)) { next.delete(i); } else { next.add(i); }
      return next;
    });
  };
  const toggleRecurring = (i: number) => {
    setCheckedRecurring((prev) => {
      const next = new Set(prev);
      if (next.has(i)) { next.delete(i); } else { next.add(i); }
      return next;
    });
  };

  const handleConfirm = async () => {
    setConfirming(true);
    try {
      const tasks = result.tasks
        .map((_t, i) => ({ item: getTask(i), i }))
        .filter(({ i }) => checkedTasks.has(i))
        .map(({ item }) => item);
      const events = result.events
        .map((_e, i) => ({ item: getEvent(i), i }))
        .filter(({ i }) => checkedEvents.has(i))
        .map(({ item }) => item);
      const recurring = result.recurring_schedule
        .map((_r, i) => ({ item: getRecurring(i), i }))
        .filter(({ i }) => checkedRecurring.has(i))
        .map(({ item }) => item);

      const res = await confirmSyllabus({
        course_name: result.course_name,
        tasks,
        events,
        recurring_schedule: recurring,
      });

      const parts: string[] = [];
      if (res.tasks_created > 0) parts.push(`${res.tasks_created} tasks`);
      if (res.events_created > 0) parts.push(`${res.events_created} events`);
      if (res.recurring_created > 0) parts.push(`${res.recurring_created} recurring schedule`);
      toast.success(`Added ${parts.join(', ')}`);
      onDone();
    } catch {
      toast.error('Failed to add items - please try again');
    } finally {
      setConfirming(false);
    }
  };

  return (
    <div className="flex flex-col gap-6">
      {/* Header */}
      <div>
        <h2 className="text-xl font-bold text-[var(--color-text-primary)]">
          {result.course_name}
        </h2>
        <p className="text-sm text-[var(--color-text-secondary)]">
          Review and select items to add to PrismTask
        </p>
      </div>

      {/* Tasks */}
      {result.tasks.length > 0 && (
        <Section title={`Tasks (${result.tasks.length})`} icon="pencil">
          {result.tasks.map((_, i) => {
            const task = getTask(i);
            return (
              <EditableTaskRow
                key={i}
                task={task}
                checked={checkedTasks.has(i)}
                onToggle={() => toggleTask(i)}
                onEdit={(updated) =>
                  setEditedTasks((prev) => new Map(prev).set(i, updated))
                }
              />
            );
          })}
        </Section>
      )}

      {/* Events */}
      {result.events.length > 0 && (
        <Section title={`Calendar Events (${result.events.length})`} icon="calendar">
          {result.events.map((_, i) => {
            const event = getEvent(i);
            return (
              <EditableEventRow
                key={i}
                event={event}
                checked={checkedEvents.has(i)}
                onToggle={() => toggleEvent(i)}
                onEdit={(updated) =>
                  setEditedEvents((prev) => new Map(prev).set(i, updated))
                }
              />
            );
          })}
        </Section>
      )}

      {/* Recurring */}
      {result.recurring_schedule.length > 0 && (
        <Section
          title={`Recurring Schedule (${result.recurring_schedule.length})`}
          icon="repeat"
        >
          {result.recurring_schedule.map((_, i) => {
            const item = getRecurring(i);
            return (
              <EditableRecurringRow
                key={i}
                item={item}
                checked={checkedRecurring.has(i)}
                onToggle={() => toggleRecurring(i)}
                onEdit={(updated) =>
                  setEditedRecurring((prev) => new Map(prev).set(i, updated))
                }
              />
            );
          })}
        </Section>
      )}

      {/* Confirm button */}
      <div className="sticky bottom-0 border-t border-[var(--color-border)] bg-[var(--color-bg-primary)] pt-4 pb-2">
        <Button
          onClick={handleConfirm}
          disabled={totalChecked === 0 || confirming}
          loading={confirming}
          className="w-full"
        >
          {confirming
            ? 'Adding Items...'
            : `Add ${totalChecked} Items to PrismTask`}
        </Button>
      </div>
    </div>
  );
}

/* ─── Section ─── */

function Section({
  title,
  icon,
  children,
}: {
  title: string;
  icon: string;
  children: React.ReactNode;
}) {
  const iconMap: Record<string, string> = {
    pencil: '\uD83D\uDCDD',
    calendar: '\uD83D\uDCC5',
    repeat: '\uD83D\uDD01',
  };
  return (
    <div>
      <h3 className="mb-2 flex items-center gap-1.5 text-xs font-bold uppercase tracking-wider text-[var(--color-text-secondary)]">
        <span>{iconMap[icon] ?? ''}</span> {title}
      </h3>
      <div className="flex flex-col gap-2">{children}</div>
    </div>
  );
}

/* ─── Editable rows ─── */

function EditableTaskRow({
  task,
  checked,
  onToggle,
  onEdit,
}: {
  task: SyllabusTask;
  checked: boolean;
  onToggle: () => void;
  onEdit: (t: SyllabusTask) => void;
}) {
  const [editing, setEditing] = useState(false);
  const [title, setTitle] = useState(task.title);
  const [dueDate, setDueDate] = useState(task.due_date ?? '');

  const save = () => {
    onEdit({ ...task, title, due_date: dueDate || null });
    setEditing(false);
  };

  return (
    <div
      className={`flex items-center gap-3 rounded-lg border p-3 transition-colors ${
        checked
          ? 'border-amber-200 bg-[var(--color-bg-card)]'
          : 'border-[var(--color-border)] bg-[var(--color-bg-secondary)] opacity-60'
      }`}
    >
      <Checkbox checked={checked} onChange={onToggle} />

      {editing ? (
        <div className="flex flex-1 flex-col gap-1.5">
          <input
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            className="rounded border border-[var(--color-border)] bg-[var(--color-bg-primary)] px-2 py-1 text-sm text-[var(--color-text-primary)]"
            placeholder="Title"
          />
          <input
            type="date"
            value={dueDate}
            onChange={(e) => setDueDate(e.target.value)}
            className="rounded border border-[var(--color-border)] bg-[var(--color-bg-primary)] px-2 py-1 text-sm text-[var(--color-text-primary)]"
          />
        </div>
      ) : (
        <div className="flex flex-1 flex-col">
          <span className="text-sm font-medium text-[var(--color-text-primary)]">{task.title}</span>
          <div className="flex items-center gap-2">
            <span className="text-xs text-[var(--color-text-secondary)]">
              {task.due_date ? `Due: ${task.due_date}` : 'No Date'}
            </span>
            <span
              className={`rounded px-1.5 py-0.5 text-[10px] font-bold uppercase ${TYPE_COLORS[task.type] ?? TYPE_COLORS.other}`}
            >
              {task.type}
            </span>
          </div>
        </div>
      )}

      <button
        onClick={editing ? save : () => setEditing(true)}
        className="shrink-0 rounded p-1 text-[var(--color-text-secondary)] hover:bg-[var(--color-bg-secondary)]"
      >
        {editing ? <Check className="h-4 w-4" /> : <Pencil className="h-4 w-4" />}
      </button>
    </div>
  );
}

function EditableEventRow({
  event,
  checked,
  onToggle,
  onEdit,
}: {
  event: SyllabusEvent;
  checked: boolean;
  onToggle: () => void;
  onEdit: (e: SyllabusEvent) => void;
}) {
  const [editing, setEditing] = useState(false);
  const [title, setTitle] = useState(event.title);
  const [date, setDate] = useState(event.date ?? '');

  const save = () => {
    onEdit({ ...event, title, date: date || null });
    setEditing(false);
  };

  const timeStr = [
    event.date,
    event.start_time && event.end_time
      ? `${event.start_time}-${event.end_time}`
      : event.start_time,
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <div
      className={`flex items-center gap-3 rounded-lg border p-3 transition-colors ${
        checked
          ? 'border-amber-200 bg-[var(--color-bg-card)]'
          : 'border-[var(--color-border)] bg-[var(--color-bg-secondary)] opacity-60'
      }`}
    >
      <Checkbox checked={checked} onChange={onToggle} />

      {editing ? (
        <div className="flex flex-1 flex-col gap-1.5">
          <input
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            className="rounded border border-[var(--color-border)] bg-[var(--color-bg-primary)] px-2 py-1 text-sm text-[var(--color-text-primary)]"
            placeholder="Title"
          />
          <input
            type="date"
            value={date}
            onChange={(e) => setDate(e.target.value)}
            className="rounded border border-[var(--color-border)] bg-[var(--color-bg-primary)] px-2 py-1 text-sm text-[var(--color-text-primary)]"
          />
        </div>
      ) : (
        <div className="flex flex-1 flex-col">
          <span className="text-sm font-medium text-[var(--color-text-primary)]">
            {event.title}
          </span>
          <div className="flex items-center gap-2">
            {timeStr && (
              <span className="text-xs text-[var(--color-text-secondary)]">{timeStr}</span>
            )}
            {event.location && (
              <span className="text-xs text-[var(--color-text-secondary)]">
                \uD83D\uDCCD {event.location}
              </span>
            )}
          </div>
        </div>
      )}

      <button
        onClick={editing ? save : () => setEditing(true)}
        className="shrink-0 rounded p-1 text-[var(--color-text-secondary)] hover:bg-[var(--color-bg-secondary)]"
      >
        {editing ? <Check className="h-4 w-4" /> : <Pencil className="h-4 w-4" />}
      </button>
    </div>
  );
}

function EditableRecurringRow({
  item,
  checked,
  onToggle,
  onEdit,
}: {
  item: SyllabusRecurringItem;
  checked: boolean;
  onToggle: () => void;
  onEdit: (r: SyllabusRecurringItem) => void;
}) {
  const [editing, setEditing] = useState(false);
  const [title, setTitle] = useState(item.title);

  const save = () => {
    onEdit({ ...item, title });
    setEditing(false);
  };

  const timeStr = [
    item.start_time && item.end_time
      ? `${item.start_time}-${item.end_time}`
      : item.start_time,
  ]
    .filter(Boolean)
    .join('');

  return (
    <div
      className={`flex items-center gap-3 rounded-lg border p-3 transition-colors ${
        checked
          ? 'border-amber-200 bg-[var(--color-bg-card)]'
          : 'border-[var(--color-border)] bg-[var(--color-bg-secondary)] opacity-60'
      }`}
    >
      <Checkbox checked={checked} onChange={onToggle} />

      {editing ? (
        <input
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          className="flex-1 rounded border border-[var(--color-border)] bg-[var(--color-bg-primary)] px-2 py-1 text-sm text-[var(--color-text-primary)]"
          placeholder="Title"
          onKeyDown={(e) => e.key === 'Enter' && save()}
        />
      ) : (
        <div className="flex flex-1 flex-col">
          <span className="text-sm font-medium text-[var(--color-text-primary)]">
            {item.title}
          </span>
          <div className="flex items-center gap-2">
            <span className="text-xs font-bold text-amber-600">
              {item.day_of_week.charAt(0).toUpperCase() + item.day_of_week.slice(1)}
            </span>
            {timeStr && (
              <span className="text-xs text-[var(--color-text-secondary)]">{timeStr}</span>
            )}
            {item.location && (
              <span className="text-xs text-[var(--color-text-secondary)]">
                \uD83D\uDCCD {item.location}
              </span>
            )}
          </div>
        </div>
      )}

      <button
        onClick={editing ? save : () => setEditing(true)}
        className="shrink-0 rounded p-1 text-[var(--color-text-secondary)] hover:bg-[var(--color-bg-secondary)]"
      >
        {editing ? <Check className="h-4 w-4" /> : <Pencil className="h-4 w-4" />}
      </button>
    </div>
  );
}
