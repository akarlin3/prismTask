import { useEffect, useState } from 'react';
import { TodayTab } from './tabs/TodayTab';
import { TasksTab } from './tabs/TasksTab';
import { ProjectsTab } from './tabs/ProjectsTab';
import { HabitsTab } from './tabs/HabitsTab';
import { LeisureTab } from './tabs/LeisureTab';
import { SchoolworkTab } from './tabs/SchoolworkTab';
import { SettingsTab } from './tabs/SettingsTab';

const tabs: Record<string, () => React.JSX.Element> = {
  today: TodayTab,
  tasks: TasksTab,
  projects: ProjectsTab,
  habits: HabitsTab,
  leisure: LeisureTab,
  schoolwork: SchoolworkTab,
  settings: SettingsTab,
};

function getTabFromHash(): string {
  const hash = window.location.hash.replace('#', '');
  return hash && tabs[hash] ? hash : 'today';
}

export default function App() {
  const [activeTab, setActiveTab] = useState(getTabFromHash);

  useEffect(() => {
    const onHashChange = () => setActiveTab(getTabFromHash());
    window.addEventListener('hashchange', onHashChange);
    return () => window.removeEventListener('hashchange', onHashChange);
  }, []);

  // Allow Android to switch tabs programmatically
  useEffect(() => {
    (window as unknown as Record<string, unknown>).setActiveTab = (tab: string) => {
      if (tabs[tab]) {
        window.location.hash = tab;
        setActiveTab(tab);
      }
    };
  }, []);

  const TabComponent = tabs[activeTab];

  return (
    <div className="app">
      <TabComponent />
    </div>
  );
}
