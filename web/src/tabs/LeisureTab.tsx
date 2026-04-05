import { useState } from 'react';

interface ActivityOption {
  id: string;
  label: string;
  icon: string;
}

const musicOptions: ActivityOption[] = [
  { id: 'bass', label: 'Bass', icon: '🎸' },
  { id: 'guitar', label: 'Guitar', icon: '🎵' },
  { id: 'drums', label: 'Drums', icon: '🥁' },
  { id: 'piano', label: 'Piano', icon: '🎹' },
  { id: 'singing', label: 'Singing', icon: '🎤' },
];

const flexOptions: ActivityOption[] = [
  { id: 'read', label: 'Read', icon: '📖' },
  { id: 'gaming', label: 'Gaming', icon: '🎮' },
  { id: 'cook', label: 'Cook', icon: '🍳' },
  { id: 'watch', label: 'Watch', icon: '📺' },
  { id: 'boardgame', label: 'Board Game', icon: '🎲' },
];

export function LeisureTab() {
  const [musicPick, setMusicPick] = useState<string | null>(null);
  const [flexPick, setFlexPick] = useState<string | null>(null);
  const [musicDone, setMusicDone] = useState(false);
  const [flexDone, setFlexDone] = useState(false);

  const completedCount = (musicDone ? 1 : 0) + (flexDone ? 1 : 0);
  const progress = completedCount / 2;

  return (
    <div className="tab-content leisure-tab">
      <div className="leisure-progress-card">
        <div className="leisure-progress-header">
          <span className="leisure-progress-text">
            {completedCount} / 2 daily minimum
          </span>
          <span className="leisure-progress-pct">{Math.round(progress * 100)}%</span>
        </div>
        <div className="leisure-progress-bar">
          <div className="leisure-progress-fill" style={{ width: `${progress * 100}%` }} />
        </div>
        {completedCount >= 2 && (
          <p className="leisure-success">Great job taking time for yourself today!</p>
        )}
      </div>

      <div className="leisure-section">
        <h3>🎵 Music Practice <span className="duration-badge">15 min</span></h3>
        {!musicPick ? (
          <div className="activity-grid three-col">
            {musicOptions.map(opt => (
              <button key={opt.id} className="activity-card" onClick={() => setMusicPick(opt.id)}>
                <span className="activity-icon">{opt.icon}</span>
                <span className="activity-label">{opt.label}</span>
              </button>
            ))}
          </div>
        ) : (
          <div className="activity-selected">
            <span className="activity-icon-large">
              {musicOptions.find(o => o.id === musicPick)?.icon}
            </span>
            <span className="activity-name">
              {musicOptions.find(o => o.id === musicPick)?.label}
            </span>
            <button
              className={`complete-btn ${musicDone ? 'done' : ''}`}
              onClick={() => setMusicDone(!musicDone)}
            >
              {musicDone ? '✓ Done' : 'Mark Done'}
            </button>
            {!musicDone && (
              <button className="change-btn" onClick={() => setMusicPick(null)}>Change</button>
            )}
          </div>
        )}
      </div>

      <div className="leisure-section">
        <h3>🎯 Flexible Activity <span className="duration-badge">30 min</span></h3>
        {!flexPick ? (
          <div className="activity-grid two-col">
            {flexOptions.map(opt => (
              <button key={opt.id} className="activity-card" onClick={() => setFlexPick(opt.id)}>
                <span className="activity-icon">{opt.icon}</span>
                <span className="activity-label">{opt.label}</span>
              </button>
            ))}
          </div>
        ) : (
          <div className="activity-selected">
            <span className="activity-icon-large">
              {flexOptions.find(o => o.id === flexPick)?.icon}
            </span>
            <span className="activity-name">
              {flexOptions.find(o => o.id === flexPick)?.label}
            </span>
            <button
              className={`complete-btn ${flexDone ? 'done' : ''}`}
              onClick={() => setFlexDone(!flexDone)}
            >
              {flexDone ? '✓ Done' : 'Mark Done'}
            </button>
            {!flexDone && (
              <button className="change-btn" onClick={() => setFlexPick(null)}>Change</button>
            )}
          </div>
        )}
      </div>

      <p className="leisure-quote">
        Work can wait. This can't. No optimizing — just pick one and do it.
      </p>
    </div>
  );
}
