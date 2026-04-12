import { useState, useRef, useEffect } from 'react';
import { Plus } from 'lucide-react';
import { toast } from 'sonner';
import { useTaskStore } from '@/stores/taskStore';
import { useProjectStore } from '@/stores/projectStore';

interface QuickCreateInputProps {
  date: string; // ISO date string (YYYY-MM-DD)
  time?: string; // HH:MM format (optional, for timeline)
  onCreated: () => void;
  onCancel: () => void;
  autoFocus?: boolean;
  className?: string;
}

export function QuickCreateInput({
  date,
  onCreated,
  onCancel,
  autoFocus = true,
  className = '',
}: QuickCreateInputProps) {
  const [title, setTitle] = useState('');
  const [isCreating, setIsCreating] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);
  const { createTask } = useTaskStore();
  const { projects } = useProjectStore();

  useEffect(() => {
    if (autoFocus && inputRef.current) {
      inputRef.current.focus();
    }
  }, [autoFocus]);

  const handleSubmit = async () => {
    if (!title.trim() || isCreating) return;

    const projectId = projects[0]?.id;
    if (!projectId) {
      toast.error('No project available. Create a project first.');
      return;
    }

    setIsCreating(true);
    try {
      await createTask(projectId, {
        title: title.trim(),
        due_date: date,
      });
      toast.success('Task created');
      setTitle('');
      onCreated();
    } catch {
      toast.error('Failed to create task');
    } finally {
      setIsCreating(false);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      handleSubmit();
    } else if (e.key === 'Escape') {
      e.preventDefault();
      onCancel();
    }
  };

  return (
    <div
      className={`flex items-center gap-1.5 rounded-md border border-[var(--color-accent)]/40 bg-[var(--color-bg-card)] px-2 py-1 ${className}`}
    >
      <Plus className="h-3 w-3 shrink-0 text-[var(--color-accent)]" />
      <input
        ref={inputRef}
        type="text"
        value={title}
        onChange={(e) => setTitle(e.target.value)}
        onKeyDown={handleKeyDown}
        onBlur={() => {
          if (!title.trim()) onCancel();
        }}
        placeholder="New task..."
        disabled={isCreating}
        className="flex-1 border-none bg-transparent text-xs text-[var(--color-text-primary)] outline-none placeholder-[var(--color-text-secondary)]"
      />
    </div>
  );
}
