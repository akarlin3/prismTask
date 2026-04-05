import { useState, useRef, useEffect } from 'react';

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

  const [elapsed, setElapsed] = useState(0);
  const [running, setRunning] = useState(false);
  const intervalRef = useRef<number | null>(null);

  useEffect(() => {
    if (running) {
      intervalRef.current = window.setInterval(() => {
        setElapsed(prev => prev + 10);
      }, 10);
    } else if (intervalRef.current !== null) {
      clearInterval(intervalRef.current);
      intervalRef.current = null;
    }
    return () => {
      if (intervalRef.current !== null) clearInterval(intervalRef.current);
    };
  }, [running]);

  const hours = Math.floor(elapsed / 3600000);
  const minutes = Math.floor((elapsed % 3600000) / 60000);
  const seconds = Math.floor((elapsed % 60000) / 1000);
  const centiseconds = Math.floor((elapsed % 1000) / 10);
  const pad = (n: number, d = 2) => String(n).padStart(d, '0');

  const completedCount = (musicDone ? 1 : 0) + (flexDone ? 1 : 0);
  const progress = completedCount / 2;

  return (
    <div className="tab-content leisure-tab">
      <div className="stopwatch-card">
        <div className="stopwatch-display">
          {hours > 0 && <><span className="stopwatch-digit">{pad(hours)}</span><span className="stopwatch-sep">:</span></>}
          <span className="stopwatch-digit">{pad(minutes)}</span>
          <span className="stopwatch-sep">:</span>
          <span className="stopwatch-digit">{pad(seconds)}</span>
          <span className="stopwatch-sep">.</span>
          <span className="stopwatch-digit stopwatch-cs">{pad(centiseconds)}</span>
        </div>
        <div className="stopwatch-controls">
          <button className="stopwatch-btn reset" onClick={() => { setRunning(false); setElapsed(0); }}>Reset</button>
          {running ? (
            <button className="stopwatch-btn stop" onClick={() => setRunning(false)}>Stop</button>
          ) : (
            <button className="stopwatch-btn start" onClick={() => setRunning(true)}>Start</button>
          )}
        </div>
      </div>

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
