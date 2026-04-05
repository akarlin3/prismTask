import { useState, useEffect, useCallback } from 'react';
import type { HabitWithStatus } from '../types';
import { bridge } from '../bridge';
import { EmptyState } from '../components/EmptyState';

function WeeklyDots({ completionsThisWeek }: { completionsThisWeek: number; target: number }) {
  const days = ['M', 'T', 'W', 'T', 'F', 'S', 'S'];
  return (
    <div className="weekly-dots">
      {days.map((day, i) => (
        <div key={i} className="dot-container">
          <div className={`dot ${i < completionsThisWeek ? 'filled' : ''}`} />
          <span className="dot-label">{day}</span>
        </div>
      ))}
    </div>
  );
}

export function HabitsTab() {
  const [habits, setHabits] = useState<HabitWithStatus[]>([]);

  const loadData = useCallback(() => {
    setHabits(bridge.getHabitsWithStatus());
  }, []);

  useEffect(() => {
    loadData();
    window.updateData = (type: string) => {
      if (type === 'habits' || type === 'all') loadData();
    };
  }, [loadData]);

  const handleToggle = (habitId: number, isCompleted: boolean) => {
    bridge.toggleHabitCompletion(habitId, isCompleted);
    loadData();
  };

  if (habits.length === 0) {
    return (
      <div className="tab-content habits-tab">
        <EmptyState icon="💪" title="Build better habits!" subtitle="Start tracking your daily routines" />
        <button className="fab" onClick={() => bridge.navigate('add_edit_habit')} aria-label="Add habit">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" />
          </svg>
        </button>
      </div>
    );
  }

  return (
    <div className="tab-content habits-tab">
      <div className="habit-list">
        {habits.map(({ habit, isCompletedToday, currentStreak, completionsThisWeek }) => (
          <div
            key={habit.id}
            className="habit-card"
            onClick={() => bridge.navigate(`habit_analytics?habitId=${habit.id}`)}
          >
            <div className="habit-card-left">
              <div className="habit-icon-circle" style={{ backgroundColor: habit.color + '22' }}>
                <span>{habit.icon}</span>
              </div>
              <div className="habit-card-info">
                <div className="habit-name-row">
                  <span className="habit-name">{habit.name}</span>
                  {currentStreak > 0 && (
                    <span className="streak-badge">🔥 {currentStreak}</span>
                  )}
                </div>
                <WeeklyDots completionsThisWeek={completionsThisWeek} target={habit.targetFrequency} />
              </div>
            </div>
            <button
              className={`habit-check-circle ${isCompletedToday ? 'checked' : ''}`}
              style={isCompletedToday ? { backgroundColor: habit.color, borderColor: habit.color } : { borderColor: habit.color }}
              onClick={(e) => { e.stopPropagation(); handleToggle(habit.id, isCompletedToday); }}
            >
              {isCompletedToday && (
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="3">
                  <polyline points="20 6 9 17 4 12" />
                </svg>
              )}
            </button>
          </div>
        ))}
      </div>

      <button className="fab" onClick={() => bridge.navigate('add_edit_habit')} aria-label="Add habit">
        <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" />
        </svg>
      </button>
    </div>
  );
}
