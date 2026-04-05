import { useState, useEffect, useCallback } from 'react';
import type { Project } from '../types';
import { bridge } from '../bridge';
import { EmptyState } from '../components/EmptyState';

export function ProjectsTab() {
  const [projects, setProjects] = useState<Project[]>([]);
  const [menuOpen, setMenuOpen] = useState<number | null>(null);
  const [deleteConfirm, setDeleteConfirm] = useState<number | null>(null);

  const loadData = useCallback(() => {
    setProjects(bridge.getProjects());
  }, []);

  useEffect(() => {
    loadData();
    window.updateData = (type: string) => {
      if (type === 'projects' || type === 'all') loadData();
    };
  }, [loadData]);

  const handleDelete = (projectId: number) => {
    bridge.deleteProject(projectId);
    setDeleteConfirm(null);
    loadData();
  };

  if (projects.length === 0) {
    return (
      <div className="tab-content projects-tab">
        <EmptyState icon="📁" title="No projects yet" subtitle="Create a project to organize your tasks" />
        <button className="fab" onClick={() => bridge.navigate('add_edit_project')} aria-label="Add project">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" />
          </svg>
        </button>
      </div>
    );
  }

  return (
    <div className="tab-content projects-tab">
      <div className="project-list">
        {projects.map(project => (
          <div key={project.id} className="project-card" onClick={() => bridge.navigate(`add_edit_project?projectId=${project.id}`)}>
            <div className="project-icon" style={{ backgroundColor: project.color + '22' }}>
              <span>{project.icon}</span>
            </div>
            <div className="project-info">
              <span className="project-name">{project.name}</span>
              <span className="project-count">{project.taskCount ?? 0} tasks</span>
            </div>
            <button
              className="more-btn"
              onClick={(e) => { e.stopPropagation(); setMenuOpen(menuOpen === project.id ? null : project.id); }}
            >
              <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
                <circle cx="12" cy="5" r="2" /><circle cx="12" cy="12" r="2" /><circle cx="12" cy="19" r="2" />
              </svg>
            </button>
            {menuOpen === project.id && (
              <div className="context-menu" onClick={() => setMenuOpen(null)}>
                <button onClick={(e) => { e.stopPropagation(); bridge.navigate(`add_edit_project?projectId=${project.id}`); setMenuOpen(null); }}>
                  Edit
                </button>
                <button className="danger" onClick={(e) => { e.stopPropagation(); setDeleteConfirm(project.id); setMenuOpen(null); }}>
                  Delete
                </button>
              </div>
            )}
          </div>
        ))}
      </div>

      {deleteConfirm !== null && (
        <div className="dialog-overlay" onClick={() => setDeleteConfirm(null)}>
          <div className="dialog" onClick={e => e.stopPropagation()}>
            <h3>Delete Project?</h3>
            <p>This will remove the project but keep its tasks.</p>
            <div className="dialog-actions">
              <button onClick={() => setDeleteConfirm(null)}>Cancel</button>
              <button className="danger" onClick={() => handleDelete(deleteConfirm)}>Delete</button>
            </div>
          </div>
        </div>
      )}

      <button className="fab" onClick={() => bridge.navigate('add_edit_project')} aria-label="Add project">
        <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" />
        </svg>
      </button>
    </div>
  );
}
