import { useState } from 'react';
import { Loader2, Plus } from 'lucide-react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/Button';
import * as firestoreHabits from '@/api/firestore/habits';
import * as firestoreProjects from '@/api/firestore/projects';
import * as firestoreTasks from '@/api/firestore/tasks';
import { getFirebaseUid } from '@/stores/firebaseUid';
import { useHabitStore } from '@/stores/habitStore';
import { useProjectStore } from '@/stores/projectStore';
import {
  STARTER_HABITS,
  STARTER_PROJECTS,
  type HabitTemplate,
  type ProjectTemplate,
} from './starterLibrary';

function currentUid(): string | null {
  try {
    return getFirebaseUid();
  } catch {
    return null;
  }
}

async function applyHabitTemplate(template: HabitTemplate): Promise<void> {
  const uid = currentUid();
  if (!uid) throw new Error('Not signed in');
  await firestoreHabits.createHabit(uid, {
    name: template.name,
    description: template.description,
    icon: template.icon,
    color: template.color,
    frequency: template.frequency,
    target_count: template.target_count,
  });
}

async function applyProjectTemplate(template: ProjectTemplate): Promise<void> {
  const uid = currentUid();
  if (!uid) throw new Error('Not signed in');
  const project = await firestoreProjects.createProject(uid, {
    title: template.name,
    description: template.description,
    color: template.color,
    icon: template.icon,
  });
  for (const t of template.tasks) {
    await firestoreTasks.createTask(uid, {
      title: t.title,
      description: t.description ?? null,
      project_id: project.id,
    });
  }
}

export function HabitStarterList() {
  const fetchHabits = useHabitStore((s) => s.fetchHabits);
  const [applying, setApplying] = useState<string | null>(null);

  const handleApply = async (template: HabitTemplate) => {
    setApplying(template.id);
    try {
      await applyHabitTemplate(template);
      await fetchHabits();
      toast.success(`Created habit "${template.name}"`);
    } catch (e) {
      toast.error((e as Error).message || 'Failed to create habit');
    } finally {
      setApplying(null);
    }
  };

  return (
    <ul className="grid grid-cols-1 gap-3 sm:grid-cols-2">
      {STARTER_HABITS.map((template) => (
        <li
          key={template.id}
          className="flex items-start gap-3 rounded-xl border border-[var(--color-border)] bg-[var(--color-bg-card)] p-4"
        >
          <span
            aria-hidden="true"
            className="text-2xl"
            style={{ filter: 'saturate(1.1)' }}
          >
            {template.icon}
          </span>
          <div className="min-w-0 flex-1">
            <p className="text-sm font-semibold text-[var(--color-text-primary)]">
              {template.name}
            </p>
            <p className="mt-0.5 text-xs text-[var(--color-text-secondary)]">
              {template.description}
            </p>
            <p className="mt-1 text-[11px] font-medium uppercase tracking-wide text-[var(--color-text-secondary)]">
              {template.frequency === 'daily' ? 'Daily' : 'Weekly'} ·{' '}
              {template.target_count}×
            </p>
          </div>
          <Button
            size="sm"
            onClick={() => handleApply(template)}
            disabled={applying === template.id}
          >
            {applying === template.id ? (
              <Loader2 className="mr-1 h-3.5 w-3.5 animate-spin" />
            ) : (
              <Plus className="mr-1 h-3.5 w-3.5" />
            )}
            Use
          </Button>
        </li>
      ))}
    </ul>
  );
}

export function ProjectStarterList() {
  const fetchAllProjects = useProjectStore((s) => s.fetchAllProjects);
  const [applying, setApplying] = useState<string | null>(null);

  const handleApply = async (template: ProjectTemplate) => {
    setApplying(template.id);
    try {
      await applyProjectTemplate(template);
      await fetchAllProjects();
      toast.success(
        `Created project "${template.name}" with ${template.tasks.length} tasks`,
      );
    } catch (e) {
      toast.error((e as Error).message || 'Failed to create project');
    } finally {
      setApplying(null);
    }
  };

  return (
    <ul className="grid grid-cols-1 gap-3 sm:grid-cols-2">
      {STARTER_PROJECTS.map((template) => (
        <li
          key={template.id}
          className="flex flex-col gap-2 rounded-xl border border-[var(--color-border)] bg-[var(--color-bg-card)] p-4"
        >
          <div className="flex items-start gap-3">
            <span aria-hidden="true" className="text-2xl">
              {template.icon}
            </span>
            <div className="min-w-0 flex-1">
              <p className="text-sm font-semibold text-[var(--color-text-primary)]">
                {template.name}
              </p>
              <p className="mt-0.5 text-xs text-[var(--color-text-secondary)]">
                {template.description}
              </p>
            </div>
            <Button
              size="sm"
              onClick={() => handleApply(template)}
              disabled={applying === template.id}
            >
              {applying === template.id ? (
                <Loader2 className="mr-1 h-3.5 w-3.5 animate-spin" />
              ) : (
                <Plus className="mr-1 h-3.5 w-3.5" />
              )}
              Use
            </Button>
          </div>
          <ul className="ml-10 flex flex-col gap-0.5 text-xs text-[var(--color-text-secondary)]">
            {template.tasks.map((t, i) => (
              <li key={i} className="flex gap-1.5">
                <span className="text-[var(--color-text-secondary)]/50">•</span>
                <span>{t.title}</span>
              </li>
            ))}
          </ul>
        </li>
      ))}
    </ul>
  );
}
