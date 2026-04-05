import { useState, useEffect } from 'react';
import { bridge } from '../bridge';

const accentColors = [
  '#4A90D9', '#5B6ABF', '#9B59B6', '#E91E63',
  '#EF4444', '#F97316', '#F59E0B', '#EAB308',
  '#22C55E', '#10B981', '#06B6D4', '#6B7280',
];

const themeOptions = ['Light', 'Dark', 'System'];

export function SettingsTab() {
  const [theme, setTheme] = useState('Dark');
  const [accent, setAccent] = useState('#4A90D9');
  const [auth, setAuth] = useState<{ signedIn: boolean; email?: string }>({ signedIn: false });

  useEffect(() => {
    setTheme(bridge.getTheme());
    setAccent(bridge.getAccentColor());
    setAuth(bridge.getAuthState());
  }, []);

  const handleThemeChange = (mode: string) => {
    setTheme(mode);
    bridge.setTheme(mode);
  };

  const handleAccentChange = (color: string) => {
    setAccent(color);
    bridge.setAccentColor(color);
    document.documentElement.style.setProperty('--accent', color);
  };

  return (
    <div className="tab-content settings-tab">
      <div className="settings-group">
        <h3 className="settings-group-title">Appearance</h3>

        <div className="setting-item">
          <span className="setting-label">Theme</span>
          <div className="theme-selector">
            {themeOptions.map(opt => (
              <button
                key={opt}
                className={`theme-btn ${theme.toLowerCase() === opt.toLowerCase() ? 'active' : ''}`}
                onClick={() => handleThemeChange(opt.toLowerCase())}
              >
                {opt}
              </button>
            ))}
          </div>
        </div>

        <div className="setting-item">
          <span className="setting-label">Accent Color</span>
          <div className="color-grid">
            {accentColors.map(color => (
              <button
                key={color}
                className={`color-swatch ${accent === color ? 'selected' : ''}`}
                style={{ backgroundColor: color }}
                onClick={() => handleAccentChange(color)}
              />
            ))}
          </div>
        </div>
      </div>

      <div className="settings-group">
        <h3 className="settings-group-title">Data</h3>
        <button className="setting-row" onClick={() => bridge.navigate('tag_management')}>
          <span>Manage Tags</span>
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <polyline points="9 18 15 12 9 6" />
          </svg>
        </button>
        <button className="setting-row" onClick={() => bridge.navigate('archive')}>
          <span>Archive</span>
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <polyline points="9 18 15 12 9 6" />
          </svg>
        </button>
      </div>

      <div className="settings-group">
        <h3 className="settings-group-title">Backup & Export</h3>
        <button className="setting-row" onClick={() => bridge.exportJson()}>
          <span>Export as JSON</span>
        </button>
        <button className="setting-row" onClick={() => bridge.exportCsv()}>
          <span>Export as CSV</span>
        </button>
        <button className="setting-row" onClick={() => bridge.importJson()}>
          <span>Import from JSON</span>
        </button>
      </div>

      <div className="settings-group">
        <h3 className="settings-group-title">Account & Sync</h3>
        {auth.signedIn ? (
          <>
            <div className="setting-item">
              <span className="setting-label">Signed in as</span>
              <span className="setting-value">{auth.email}</span>
            </div>
            <button className="setting-row" onClick={() => bridge.syncNow()}>
              <span>Sync Now</span>
            </button>
            <button className="setting-row danger" onClick={() => { bridge.signOut(); setAuth({ signedIn: false }); }}>
              <span>Sign Out</span>
            </button>
          </>
        ) : (
          <button className="setting-row" onClick={() => bridge.signIn()}>
            <span>Sign in with Google</span>
          </button>
        )}
      </div>

      <div className="settings-group">
        <h3 className="settings-group-title">About</h3>
        <div className="setting-item">
          <span className="setting-label">Version</span>
          <span className="setting-value">{bridge.getVersion()}</span>
        </div>
        <div className="setting-item">
          <span className="setting-label">Author</span>
          <span className="setting-value">Made by Avery Karlin</span>
        </div>
      </div>
    </div>
  );
}
