// TaskDetailScreen — full detail/edit view for a single task.
// Shown as a pushed screen, not a sheet.

function DetailChip({ theme, label, value, icon, color }) {
  const isMatrix = theme.terminal;
  const isVoid = theme.editorial;
  return (
    <div style={{
      display: 'flex', alignItems: 'center', gap: 10,
      padding: isVoid ? '14px 0' : '12px 14px',
      background: isVoid ? 'transparent' : theme.colors.surface,
      border: isVoid ? 'none' : `1px solid ${theme.colors.border}`,
      borderTop: isVoid ? `1px solid ${theme.colors.border}` : undefined,
      borderRadius: isVoid ? 0 : theme.cardRadius,
      marginBottom: isVoid ? 0 : 8,
    }}>
      {icon && (
        <div style={{
          width: 28, height: 28,
          borderRadius: theme.chipShape === 'sharp' ? 3 : 8,
          background: `${color || theme.colors.primary}1F`,
          border: `1px solid ${color || theme.colors.primary}55`,
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          flexShrink: 0,
        }}>
          <Icon name={icon} size={14} color={color || theme.colors.primary}/>
        </div>
      )}
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{
          fontSize: isVoid ? 10 : 10,
          color: theme.colors.muted,
          letterSpacing: isVoid ? 2 : 1.5,
          textTransform: 'uppercase',
          fontFamily: theme.fonts.body,
        }}>{isMatrix ? label.toLowerCase() : label}</div>
        <div style={{
          marginTop: 2,
          fontSize: isVoid ? 15 : 14,
          fontWeight: 500,
          color: theme.colors.onBackground,
          fontFamily: isVoid ? theme.fonts.display : theme.fonts.body,
        }}>{value}</div>
      </div>
    </div>
  );
}

function SubtaskRow({ theme, label, done }) {
  const isMatrix = theme.terminal;
  return (
    <div style={{
      display: 'flex', alignItems: 'center', gap: 12,
      padding: '10px 0',
      borderBottom: `1px solid ${theme.colors.border}`,
    }}>
      {isMatrix ? (
        <span style={{ fontFamily: theme.fonts.body, color: done ? theme.colors.primary : theme.colors.muted, fontSize: 14 }}>
          [{done ? 'x' : ' '}]
        </span>
      ) : (
        <div style={{
          width: 18, height: 18, flexShrink: 0,
          border: `1.5px solid ${done ? theme.colors.primary : theme.colors.muted}`,
          borderRadius: theme.chipShape === 'sharp' ? 3 : '50%',
          background: done ? theme.colors.primary : 'transparent',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
        }}>
          {done && <Icon name="check" size={10} color={theme.colors.background} strokeWidth={3}/>}
        </div>
      )}
      <div style={{
        flex: 1,
        fontSize: 14,
        color: done ? theme.colors.muted : theme.colors.onBackground,
        textDecoration: done ? 'line-through' : 'none',
        fontFamily: theme.fonts.body,
      }}>{label}</div>
    </div>
  );
}

function TaskDetailScreen({ theme }) {
  const up = theme.displayUpper ? 'uppercase' : 'none';
  const isMatrix = theme.terminal;
  const isVoid = theme.editorial;
  const isCyber = theme.brackets;
  const isSynth = theme.sunset;

  return (
    <div style={{ height: '100%', display: 'flex', flexDirection: 'column', position: 'relative' }}>
      {/* Top app bar */}
      <div style={{
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        padding: '8px 16px 0',
      }}>
        <svg width="24" height="24" viewBox="0 0 24 24" fill="none"
          stroke={theme.colors.onBackground} strokeWidth="1.75"
          strokeLinecap="round" strokeLinejoin="round">
          <path d="M19 12H5M12 19l-7-7 7-7"/>
        </svg>
        <div style={{ display: 'flex', alignItems: 'center', gap: 18 }}>
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none"
            stroke={theme.colors.onSurface} strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round">
            <path d="M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z"/>
          </svg>
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none"
            stroke={theme.colors.onSurface} strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="5" cy="12" r="1.5"/><circle cx="12" cy="12" r="1.5"/><circle cx="19" cy="12" r="1.5"/>
          </svg>
        </div>
      </div>

      <div className="no-scrollbar" style={{ flex: 1, overflow: 'auto', padding: '12px 20px 100px' }}>
        {/* Breadcrumb */}
        <div style={{
          fontSize: 10, letterSpacing: 1.8, textTransform: 'uppercase',
          color: theme.colors.muted, fontFamily: theme.fonts.body,
        }}>
          {isMatrix ? '// tasks / planning / edit' : 'Tasks · Planning'}
        </div>

        {/* Title */}
        <div style={{
          marginTop: 10, marginBottom: 14,
          fontFamily: isVoid ? theme.fonts.display : theme.fonts.body,
          fontSize: isVoid ? 30 : 24,
          fontWeight: isVoid ? 500 : 700,
          lineHeight: 1.15,
          color: theme.colors.onBackground,
          letterSpacing: isVoid ? -0.3 : 0,
          ...(isSynth ? { textShadow: `0 0 14px ${theme.colors.primary}60` } : {}),
        }}>
          Review Q2 roadmap with team
        </div>

        {/* Priority + tag row */}
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 18 }}>
          <div style={{
            display: 'inline-flex', alignItems: 'center', gap: 6,
            padding: '5px 10px',
            borderRadius: theme.chipShape === 'sharp' ? 3 : 999,
            background: `${theme.colors.primary}1F`,
            border: `1px solid ${theme.colors.primary}55`,
            color: theme.colors.primary,
            fontSize: 11, fontWeight: 600, letterSpacing: 1,
            textTransform: 'uppercase', fontFamily: theme.fonts.body,
            whiteSpace: 'nowrap',
          }}>
            <span style={{ width: 6, height: 6, borderRadius: theme.chipShape === 'sharp' ? 0 : '50%', background: theme.colors.primary }}/>
            High priority
          </div>
          <div style={{
            display: 'inline-flex', alignItems: 'center', gap: 6,
            padding: '5px 10px',
            borderRadius: theme.chipShape === 'sharp' ? 3 : 999,
            background: theme.colors.tagSurface,
            border: `1px solid ${theme.colors.border}`,
            color: theme.colors.tagText,
            fontSize: 11, fontWeight: 500, letterSpacing: 0.6,
            fontFamily: theme.fonts.body,
          }}>
            {isMatrix ? '#work' : 'Work'}
          </div>
          <div style={{
            display: 'inline-flex', alignItems: 'center', gap: 6,
            padding: '5px 10px',
            borderRadius: theme.chipShape === 'sharp' ? 3 : 999,
            background: theme.colors.tagSurface,
            border: `1px solid ${theme.colors.border}`,
            color: theme.colors.tagText,
            fontSize: 11, fontWeight: 500, letterSpacing: 0.6,
            fontFamily: theme.fonts.body,
          }}>
            {isMatrix ? '#planning' : 'Planning'}
          </div>
        </div>

        {/* Notes */}
        <ThemedCard theme={theme} padding={isVoid ? '18px 20px' : '14px 16px'} style={{ marginBottom: 18 }}>
          <div style={{
            fontSize: 10, letterSpacing: 2, textTransform: 'uppercase',
            color: theme.colors.muted, fontFamily: theme.fonts.body, marginBottom: 8,
          }}>{isMatrix ? '// notes' : 'Notes'}</div>
          <div style={{
            fontSize: 13.5, lineHeight: 1.55, color: theme.colors.onSurface,
            fontFamily: theme.fonts.body, whiteSpace: 'pre-line',
          }}>
            {`Align on North Star metrics before the board sync.
Pull current OKR progress from the tracker.
Flag gaps where engineering capacity < roadmap ask.`}
          </div>
        </ThemedCard>

        {/* Metadata grid */}
        <div style={{ marginBottom: 20 }}>
          <DetailChip theme={theme} label="Due" value="Wed, Apr 9 · 2:00 PM" icon="today" color={theme.colors.primary}/>
          <DetailChip theme={theme} label="Project" value="Planning" icon="tasks" color={theme.colors.secondary}/>
          <DetailChip theme={theme} label="Reminder" value={isMatrix ? '30m before' : '30 min before'} icon="pill" color={theme.colors.primary}/>
          <DetailChip theme={theme} label="Repeat" value={isMatrix ? 'weekly · wed' : 'Weekly · Wednesdays'} icon="recurring" color={theme.colors.secondary}/>
        </div>

        {/* Subtasks */}
        <div style={{
          display: 'flex', alignItems: 'center', justifyContent: 'space-between',
          marginBottom: 8,
        }}>
          <div style={{
            fontSize: 11, letterSpacing: 1.6, textTransform: 'uppercase',
            fontWeight: 600, color: theme.colors.primary, fontFamily: theme.fonts.body,
          }}>
            {isMatrix ? '# subtasks [5]' : 'Subtasks · 3 of 5'}
          </div>
          <div style={{
            fontSize: 11, letterSpacing: 0.6, color: theme.colors.muted,
            fontFamily: theme.fonts.body,
          }}>{isMatrix ? '+ add' : '+ Add'}</div>
        </div>
        <ThemedCard theme={theme} padding={isVoid ? '4px 20px' : '4px 14px'} style={{ marginBottom: 18 }}>
          <SubtaskRow theme={theme} label="Pull OKR progress from tracker" done/>
          <SubtaskRow theme={theme} label="Sync with Design on Q2 priorities" done/>
          <SubtaskRow theme={theme} label="Draft agenda doc" done/>
          <SubtaskRow theme={theme} label="Send pre-read to stakeholders"/>
          <div style={{ borderBottom: 'none' }}><SubtaskRow theme={theme} label="Book follow-up slot for Friday"/></div>
        </ThemedCard>

        {/* Activity */}
        <div style={{
          fontSize: 11, letterSpacing: 1.6, textTransform: 'uppercase',
          fontWeight: 600, color: theme.colors.primary, fontFamily: theme.fonts.body,
          marginBottom: 8,
        }}>{isMatrix ? '# activity' : 'Activity'}</div>
        <div style={{
          display: 'flex', flexDirection: 'column', gap: 10,
          paddingLeft: 10, borderLeft: `1px dashed ${theme.colors.border}`,
        }}>
          {[
            { t: '2h ago', line: 'Priority raised to high', by: 'you' },
            { t: 'Yesterday', line: 'Subtask completed · Sync with Design', by: 'you' },
            { t: '2 days ago', line: 'Due date moved to Wed 2 PM', by: 'you' },
            { t: '3 days ago', line: 'Created from template · “Weekly planning”', by: 'system' },
          ].map((e, i) => (
            <div key={i} style={{ fontSize: 12, color: theme.colors.onSurface, fontFamily: theme.fonts.body }}>
              <span style={{ color: theme.colors.muted, letterSpacing: 1.2, textTransform: 'uppercase', fontSize: 10, marginRight: 8 }}>
                {e.t}
              </span>
              {e.line}
              <span style={{ color: theme.colors.muted, marginLeft: 6 }}>· {e.by}</span>
            </div>
          ))}
        </div>
      </div>

      {/* Bottom action bar */}
      <div style={{
        position: 'absolute', left: 0, right: 0, bottom: 0,
        padding: '12px 16px',
        background: theme.colors.background,
        borderTop: `1px solid ${theme.colors.border}`,
        display: 'flex', gap: 10, alignItems: 'center',
      }}>
        <div style={{
          flex: 1, padding: '13px 18px',
          textAlign: 'center',
          background: theme.colors.primary,
          color: theme.colors.background,
          borderRadius: theme.chipShape === 'sharp' ? (isMatrix ? 0 : 6) : 12,
          fontSize: 13, fontWeight: 700, letterSpacing: isMatrix || isCyber || isSynth ? 1.4 : 0.4,
          textTransform: up,
          fontFamily: theme.fonts.body,
          boxShadow: theme.glow === 'none' ? 'none' : `0 0 18px ${theme.colors.primary}70`,
        }}>
          {isMatrix ? 'mark complete' : 'Mark Complete'}
        </div>
        <div style={{
          padding: '13px 16px',
          border: `1px solid ${theme.colors.border}`,
          color: theme.colors.onSurface,
          borderRadius: theme.chipShape === 'sharp' ? (isMatrix ? 0 : 6) : 12,
          fontSize: 13, fontWeight: 600, letterSpacing: 0.4,
          fontFamily: theme.fonts.body,
        }}>
          {isMatrix ? 'snooze' : 'Snooze'}
        </div>
      </div>
    </div>
  );
}

window.TaskDetailScreen = TaskDetailScreen;
