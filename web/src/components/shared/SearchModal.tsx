import { useState, useEffect, useRef, useCallback } from 'react';
import { Search, X, FileText, Loader2 } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { searchApi } from '@/api/search';
import { DueDateLabel } from './DueDateLabel';
import { StatusBadge } from './StatusBadge';
import { PriorityBadge } from './PriorityBadge';
import { useTaskStore } from '@/stores/taskStore';
import type { Task } from '@/types/task';

interface SearchModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export function SearchModal({ isOpen, onClose }: SearchModalProps) {
  const navigate = useNavigate();
  const { setSelectedTask } = useTaskStore();
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<Task[]>([]);
  const [loading, setLoading] = useState(false);
  const [highlightIndex, setHighlightIndex] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);
  const searchTimerRef = useRef<ReturnType<typeof setTimeout>>(undefined);

  // Focus input on open
  useEffect(() => {
    if (isOpen) {
      setQuery('');
      setResults([]);
      setHighlightIndex(0);
      setTimeout(() => inputRef.current?.focus(), 50);
    }
  }, [isOpen]);

  // Escape to close
  useEffect(() => {
    if (!isOpen) return;
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', handler);
    return () => document.removeEventListener('keydown', handler);
  }, [isOpen, onClose]);

  // Lock body scroll
  useEffect(() => {
    if (isOpen) {
      document.body.style.overflow = 'hidden';
    } else {
      document.body.style.overflow = '';
    }
    return () => {
      document.body.style.overflow = '';
    };
  }, [isOpen]);

  // Debounced search
  useEffect(() => {
    if (searchTimerRef.current) clearTimeout(searchTimerRef.current);
    if (!query.trim()) {
      setResults([]);
      return;
    }
    setLoading(true);
    searchTimerRef.current = setTimeout(async () => {
      try {
        const data = await searchApi.search(query);
        setResults(Array.isArray(data) ? data : []);
        setHighlightIndex(0);
      } catch {
        setResults([]);
      } finally {
        setLoading(false);
      }
    }, 300);
    return () => {
      if (searchTimerRef.current) clearTimeout(searchTimerRef.current);
    };
  }, [query]);

  const handleSelect = useCallback(
    (task: Task) => {
      setSelectedTask(task);
      onClose();
      navigate(`/tasks/${task.id}`);
    },
    [navigate, onClose, setSelectedTask],
  );

  const handleKeyDown = (e: React.KeyboardEvent) => {
    switch (e.key) {
      case 'ArrowDown':
        e.preventDefault();
        setHighlightIndex((i) => Math.min(i + 1, results.length - 1));
        break;
      case 'ArrowUp':
        e.preventDefault();
        setHighlightIndex((i) => Math.max(i - 1, 0));
        break;
      case 'Enter':
        e.preventDefault();
        if (results[highlightIndex]) {
          handleSelect(results[highlightIndex]);
        }
        break;
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center pt-[15vh]">
      {/* Backdrop */}
      <div
        className="absolute inset-0 bg-black/50"
        onClick={onClose}
      />

      {/* Modal */}
      <div className="relative z-10 w-full max-w-xl rounded-xl border border-[var(--color-border)] bg-[var(--color-bg-card)] shadow-2xl">
        {/* Search input */}
        <div className="flex items-center gap-3 border-b border-[var(--color-border)] px-4 py-3">
          <Search className="h-5 w-5 shrink-0 text-[var(--color-text-secondary)]" />
          <input
            ref={inputRef}
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="Search tasks..."
            className="flex-1 border-none bg-transparent text-sm text-[var(--color-text-primary)] outline-none placeholder-[var(--color-text-secondary)]"
          />
          {loading && <Loader2 className="h-4 w-4 animate-spin text-[var(--color-accent)]" />}
          <button
            onClick={onClose}
            className="shrink-0 rounded-md p-1 text-[var(--color-text-secondary)] hover:text-[var(--color-text-primary)]"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        {/* Results */}
        <div className="max-h-80 overflow-y-auto p-2">
          {query && results.length === 0 && !loading && (
            <div className="px-4 py-8 text-center text-sm text-[var(--color-text-secondary)]">
              No results found for "{query}"
            </div>
          )}
          {results.map((task, index) => (
            <button
              key={task.id}
              onClick={() => handleSelect(task)}
              onMouseEnter={() => setHighlightIndex(index)}
              className={`flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left transition-colors ${
                highlightIndex === index
                  ? 'bg-[var(--color-bg-secondary)]'
                  : ''
              }`}
            >
              <FileText className="h-4 w-4 shrink-0 text-[var(--color-text-secondary)]" />
              <div className="flex-1 min-w-0">
                <p className="truncate text-sm font-medium text-[var(--color-text-primary)]">
                  {task.title}
                </p>
              </div>
              <div className="flex shrink-0 items-center gap-2">
                <StatusBadge status={task.status} />
                <PriorityBadge priority={task.priority} iconOnly />
                <DueDateLabel date={task.due_date} />
              </div>
            </button>
          ))}
        </div>

        {/* Footer hint */}
        <div className="flex items-center gap-4 border-t border-[var(--color-border)] px-4 py-2 text-xs text-[var(--color-text-secondary)]">
          <span>
            <kbd className="rounded bg-[var(--color-bg-secondary)] px-1.5 py-0.5 font-mono">
              ↑↓
            </kbd>{' '}
            Navigate
          </span>
          <span>
            <kbd className="rounded bg-[var(--color-bg-secondary)] px-1.5 py-0.5 font-mono">
              ↵
            </kbd>{' '}
            Open
          </span>
          <span>
            <kbd className="rounded bg-[var(--color-bg-secondary)] px-1.5 py-0.5 font-mono">
              Esc
            </kbd>{' '}
            Close
          </span>
        </div>
      </div>
    </div>
  );
}
