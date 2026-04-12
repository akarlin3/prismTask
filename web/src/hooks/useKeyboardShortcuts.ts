import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';

interface ShortcutActions {
  onSearch: () => void;
  onNewTask: () => void;
}

function isInputFocused(): boolean {
  const active = document.activeElement;
  if (!active) return false;
  const tag = active.tagName;
  if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return true;
  if (active.getAttribute('contenteditable')) return true;
  return false;
}

export function useKeyboardShortcuts({ onSearch, onNewTask }: ShortcutActions) {
  const navigate = useNavigate();

  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      // Cmd/Ctrl+K — search (always active)
      if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
        e.preventDefault();
        onSearch();
        return;
      }

      // Only handle other shortcuts when not in an input
      if (isInputFocused()) return;

      switch (e.key) {
        // `/` — focus NLP bar (already handled in NLPInput)
        case 'n':
          e.preventDefault();
          onNewTask();
          break;
        case '1':
          e.preventDefault();
          navigate('/');
          break;
        case '2':
          e.preventDefault();
          navigate('/tasks');
          break;
        case '3':
          e.preventDefault();
          navigate('/projects');
          break;
        case '4':
          e.preventDefault();
          navigate('/habits');
          break;
        case '5':
          e.preventDefault();
          navigate('/calendar/week');
          break;
        case 'Escape':
          // Close any open modals — handled by individual components
          break;
      }
    };

    document.addEventListener('keydown', handler);
    return () => document.removeEventListener('keydown', handler);
  }, [navigate, onSearch, onNewTask]);
}
