import { useState, useEffect, useCallback } from 'react';
import type { Task, Project, Tag, SortOption } from '../types';
import { bridge } from '../bridge';
import { TaskCard } from '../components/TaskCard';
import { Section } from '../components/Section';
import { EmptyState } from '../components/EmptyState';
import { groupTasksByDate, calculateUrgency } from '../utils';

const sortOptions: { value: SortOption; label: string }[] = [
  { value: 'DUE_DATE', label: 'Due Date' },
  { value: 'PRIORITY', label: 'Priority' },
  { value: 'URGENCY', label: 'Urgency' },
  { value: 'CREATED', label: 'Created' },
  { value: 'ALPHABETICAL', label: 'A-Z' },
];

const groupOrder = ['Overdue', 'Today', 'Tomorrow', 'This Week', 'Later', 'No Date'];

export function TasksTab() {
  const [tasks, setTasks] = useState<Task[]>([]);
  const [projects, setProjects] = useState<Project[]>([]);
  const [_tags, setTags] = useState<Tag[]>([]);
  const [taskTags, setTaskTags] = useState<Record<number, Tag[]>>({});
  const [sort, setSort] = useState<SortOption>('DUE_DATE');
  const [selectedProject, setSelectedProject] = useState<number | null>(null);
  const [showSortMenu, setShowSortMenu] = useState(false);

  const loadData = useCallback(() => {
    const allTasks = bridge.getTasks().filter(t => !t.isCompleted && !t.archivedAt && !t.parentTaskId);
    setTasks(allTasks);
    setProjects(bridge.getProjects());
    setTags(bridge.getTags());

    const tagsMap: Record<number, Tag[]> = {};
    for (const t of allTasks) {
      tagsMap[t.id] = bridge.getTaskTags(t.id);
    }
    setTaskTags(tagsMap);
  }, []);

  useEffect(() => {
    loadData();
    window.updateData = (type: string) => {
      if (type === 'tasks' || type === 'all') loadData();
    };
  }, [loadData]);

  const filteredTasks = selectedProject
    ? tasks.filter(t => t.projectId === selectedProject)
    : tasks;

  const sortedTasks = [...filteredTasks].sort((a, b) => {
    switch (sort) {
      case 'PRIORITY': return b.priority - a.priority;
      case 'URGENCY': return calculateUrgency(b) - calculateUrgency(a);
      case 'CREATED': return b.createdAt - a.createdAt;
      case 'ALPHABETICAL': return a.title.localeCompare(b.title);
      case 'DUE_DATE':
      default:
        if (!a.dueDate && !b.dueDate) return 0;
        if (!a.dueDate) return 1;
        if (!b.dueDate) return -1;
        return a.dueDate - b.dueDate;
    }
  });

  const grouped = groupTasksByDate(sortedTasks);

  const handleComplete = (taskId: number) => {
    bridge.completeTask(taskId);
    loadData();
  };

  return (
    <div className="tab-content tasks-tab">
      <div className="tasks-toolbar">
        <div className="sort-dropdown">
          <button className="sort-btn" onClick={() => setShowSortMenu(!showSortMenu)}>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M3 6h18M3 12h12M3 18h6" />
            </svg>
            {sortOptions.find(s => s.value === sort)?.label}
          </button>
          {showSortMenu && (
            <div className="dropdown-menu" onClick={() => setShowSortMenu(false)}>
              {sortOptions.map(opt => (
                <button
                  key={opt.value}
                  className={`dropdown-item ${sort === opt.value ? 'active' : ''}`}
                  onClick={() => setSort(opt.value)}
                >
                  {opt.label}
                </button>
              ))}
            </div>
          )}
        </div>
      </div>

      <div className="project-chips">
        <button
          className={`chip ${selectedProject === null ? 'active' : ''}`}
          onClick={() => setSelectedProject(null)}
        >
          All
        </button>
        {projects.map(p => (
          <button
            key={p.id}
            className={`chip ${selectedProject === p.id ? 'active' : ''}`}
            style={selectedProject === p.id ? { backgroundColor: p.color + '33', borderColor: p.color } : undefined}
            onClick={() => setSelectedProject(p.id === selectedProject ? null : p.id)}
          >
            {p.icon} {p.name}
          </button>
        ))}
      </div>

      {sortedTasks.length === 0 ? (
        <EmptyState icon="📋" title="No tasks" subtitle="All caught up!" />
      ) : sort === 'DUE_DATE' ? (
        groupOrder.filter(g => grouped[g]).map(group => (
          <Section key={group} title={group} count={grouped[group].length} color={group === 'Overdue' ? '#EF4444' : undefined}>
            {(grouped[group] as Task[]).map(task => (
              <TaskCard
                key={task.id}
                task={task}
                tags={taskTags[task.id]}
                onComplete={handleComplete}
                showDate={group !== 'Today'}
                onClick={(id) => bridge.navigate(`add_edit_task?taskId=${id}`)}
              />
            ))}
          </Section>
        ))
      ) : (
        <div className="task-list-flat">
          {sortedTasks.map(task => (
            <TaskCard
              key={task.id}
              task={task}
              tags={taskTags[task.id]}
              onComplete={handleComplete}
              onClick={(id) => bridge.navigate(`add_edit_task?taskId=${id}`)}
            />
          ))}
        </div>
      )}

      <button
        className="fab"
        onClick={() => bridge.navigate('add_edit_task')}
        aria-label="Add task"
      >
        <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <line x1="12" y1="5" x2="12" y2="19" />
          <line x1="5" y1="12" x2="19" y2="12" />
        </svg>
      </button>
    </div>
  );
}
