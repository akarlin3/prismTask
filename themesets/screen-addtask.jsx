// AddTaskSheet — bottom-sheet modal over the Tasks screen.
// Presented as the full phone frame with the sheet overlaid.

function AddTaskSheet({ theme }) {
  const up = theme.displayUpper ? 'uppercase' : 'none';
  const isMatrix = theme.terminal;
  const isVoid = theme.editorial;
  const isCyber = theme.brackets;
  const isSynth = theme.sunset;

  const PriBtn = ({ label, color, active }) => (
    <div style={{
      flex: 1, textAlign: 'center',
      padding: '10px 6px',
      border: `1px solid ${active ? color : theme.colors.border}`,
      background: active ? `${color}22` : 'transparent',
      color: active ? color : theme.colors.muted,
      borderRadius: theme.chipShape === 'sharp' ? 3 : 8,
      fontSize: 11, fontWeight: 600, letterSpacing: 1.2,
      textTransform: 'uppercase',
      fontFamily: theme.fonts.body,
    }}>{label}</div>
  );

  const QuickChip = ({ icon, label, color }) => (
    <div style={{
      display: 'inline-flex', alignItems: 'center', gap: 6,
      padding: '7px 11px',
      borderRadius: theme.chipShape === 'sharp' ? 3 : 999,
      border: `1px solid ${theme.colors.border}`,
      background: theme.colors.surface,
      color: color || theme.colors.onSurface,
      fontSize: 12, fontWeight: 500, letterSpacing: 0.3,
      fontFamily: theme.fonts.body,
    }}>
      {icon && <Icon name={icon} size={12} color={color || theme.colors.onSurface}/>}
      {label}
    </div>
  );

  return (
    <div style={{ height: '100%', position: 'relative', background: theme.colors.background }}>
      {/* Dim underlying screen */}
      <div style={{
        position: 'absolute', inset: 0,
        background: 'rgba(0,0,0,0.55)',
        backdropFilter: 'blur(2px)',
      }}/>
      {/* Header preview */}
      <div style={{ padding: '4px 20px 0', opacity: 0.35 }}>
        <div style={{
          marginTop: 26, fontFamily: theme.fonts.display,
          fontSize: isVoid ? 44 : 30, fontWeight: isVoid ? 500 : 700,
          color: theme.colors.onBackground, textTransform: up,
          letterSpacing: theme.displayTracking,
        }}>
          {isMatrix ? 'TASKS' : 'Tasks'}
        </div>
      </div>

      {/* Bottom sheet */}
      <div style={{
        position: 'absolute', left: 0, right: 0, bottom: 0,
        background: theme.colors.surface,
        borderTop: `1px solid ${theme.colors.primary}55`,
        borderTopLeftRadius: isMatrix ? 0 : 22,
        borderTopRightRadius: isMatrix ? 0 : 22,
        padding: '12px 20px 28px',
        boxShadow: `0 -20px 50px rgba(0,0,0,0.7)`,
        ...(isCyber ? { boxShadow: `0 -20px 50px rgba(0,0,0,0.7), 0 0 0 1px ${theme.colors.primary}25` } : {}),
      }}>
        {/* drag handle */}
        <div style={{
          width: 40, height: 4, margin: '0 auto 14px',
          borderRadius: 2, background: theme.colors.muted, opacity: 0.5,
        }}/>

        {/* Title row */}
        <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', marginBottom: 14 }}>
          <div style={{
            fontFamily: isVoid ? theme.fonts.display : theme.fonts.body,
            fontSize: isVoid ? 22 : 17, fontWeight: isVoid ? 500 : 700,
            color: theme.colors.onBackground, textTransform: up,
            letterSpacing: isVoid ? -0.2 : 1,
          }}>
            {isMatrix ? 'task --new' : 'New Task'}
          </div>
          <div style={{
            fontSize: 11, letterSpacing: 1.4, textTransform: 'uppercase',
            color: theme.colors.muted, fontFamily: theme.fonts.body,
          }}>{isMatrix ? 'esc' : 'Cancel'}</div>
        </div>

        {/* Title input */}
        <div style={{
          padding: '14px 14px',
          background: theme.colors.background,
          border: `1px solid ${theme.colors.primary}40`,
          borderRadius: theme.chipShape === 'sharp' ? (isMatrix ? 0 : 6) : 10,
          fontSize: 16, color: theme.colors.onBackground,
          fontFamily: theme.fonts.body,
          display: 'flex', alignItems: 'center', gap: 6,
          ...(theme.glow !== 'none' ? { boxShadow: `inset 0 0 18px ${theme.colors.primary}15` } : {}),
        }}>
          {isMatrix && <span style={{ color: theme.colors.primary, opacity: 0.7 }}>$</span>}
          Prep slides for Thursday review
          <span style={{
            width: 2, height: 18, background: theme.colors.primary,
            animation: 'blink 1s steps(2) infinite', marginLeft: 2,
          }}/>
        </div>

        {/* Parsed chips */}
        <div style={{
          marginTop: 10, display: 'flex', gap: 6, flexWrap: 'wrap',
        }}>
          <QuickChip icon="today" label="Thu 9:00 AM" color={theme.colors.primary}/>
          <QuickChip icon="recurring" label={isMatrix ? 'weekly' : 'Repeats weekly'} color={theme.colors.secondary}/>
          <QuickChip label="#work" color={theme.colors.primary}/>
          <QuickChip label="@planning" color={theme.colors.secondary}/>
        </div>

        {/* Priority */}
        <div style={{
          marginTop: 18, fontSize: 10, letterSpacing: 2,
          textTransform: 'uppercase', color: theme.colors.muted,
          fontFamily: theme.fonts.body, marginBottom: 8,
        }}>{isMatrix ? '# priority' : 'Priority'}</div>
        <div style={{ display: 'flex', gap: 6 }}>
          <PriBtn label="None" color={theme.colors.muted}/>
          <PriBtn label="Low" color={theme.colors.secondary}/>
          <PriBtn label="Med" color={theme.colors.primary}/>
          <PriBtn label="High" color={theme.colors.primary} active/>
          <PriBtn label="Urgent" color={theme.colors.urgentAccent}/>
        </div>

        {/* Quick actions row */}
        <div style={{ marginTop: 16, display: 'flex', gap: 10, flexWrap: 'wrap' }}>
          <QuickChip icon="today" label={isMatrix ? 'pick date' : 'Due date'}/>
          <QuickChip icon="pill" label={isMatrix ? 'remind' : 'Reminder'}/>
          <QuickChip icon="tasks" label={isMatrix ? 'project' : 'Project'}/>
          <QuickChip icon="plus" label={isMatrix ? 'subtasks' : 'Subtasks'}/>
        </div>

        {/* Create button */}
        <div style={{
          marginTop: 18, display: 'flex', gap: 10,
        }}>
          <div style={{
            flex: 1, textAlign: 'center', padding: '14px',
            background: theme.colors.primary,
            color: theme.colors.background,
            borderRadius: theme.chipShape === 'sharp' ? (isMatrix ? 0 : 6) : 12,
            fontSize: 13, fontWeight: 700, letterSpacing: 1.2,
            textTransform: up, fontFamily: theme.fonts.body,
            boxShadow: theme.glow === 'none' ? 'none' : `0 0 20px ${theme.colors.primary}80`,
          }}>{isMatrix ? 'create' : 'Create Task'}</div>
          <div style={{
            padding: '14px 16px',
            border: `1px solid ${theme.colors.border}`,
            color: theme.colors.onSurface,
            borderRadius: theme.chipShape === 'sharp' ? (isMatrix ? 0 : 6) : 12,
            fontSize: 13, fontWeight: 600, fontFamily: theme.fonts.body,
            display: 'flex', alignItems: 'center', gap: 6,
          }}>
            <Icon name="music" size={14} color={theme.colors.onSurface}/>
          </div>
        </div>
      </div>
    </div>
  );
}

window.AddTaskSheet = AddTaskSheet;
