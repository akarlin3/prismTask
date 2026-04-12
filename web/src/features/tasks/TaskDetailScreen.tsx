import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { useTaskStore } from '@/stores/taskStore';
import { Spinner } from '@/components/ui/Spinner';
import TaskEditor from '@/features/tasks/TaskEditor';

export function TaskDetailScreen() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { fetchTask, setSelectedTask } = useTaskStore();
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!id) return;
    setLoading(true);
    fetchTask(Number(id))
      .then(() => setLoading(false))
      .catch(() => {
        setLoading(false);
        navigate('/tasks');
      });
  }, [id, fetchTask, navigate]);

  if (loading) {
    return (
      <div className="flex h-64 items-center justify-center">
        <Spinner size="lg" />
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-4xl">
      <button
        onClick={() => navigate('/tasks')}
        className="mb-4 flex items-center gap-1 text-sm text-[var(--color-text-secondary)] hover:text-[var(--color-text-primary)] transition-colors"
      >
        <ArrowLeft className="h-4 w-4" />
        All Tasks
      </button>

      <TaskEditor
        onClose={() => {
          setSelectedTask(null);
          navigate('/tasks');
        }}
        onUpdate={() => {
          if (id) fetchTask(Number(id));
        }}
      />
    </div>
  );
}
