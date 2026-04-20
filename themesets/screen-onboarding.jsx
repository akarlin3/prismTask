// OnboardingScreen — welcome flow, step 2 of 4.

function OnboardingScreen({ theme }) {
  const up = theme.displayUpper ? 'uppercase' : 'none';
  const isMatrix = theme.terminal;
  const isVoid = theme.editorial;
  const isCyber = theme.brackets;
  const isSynth = theme.sunset;

  const STEPS = 4;
  const CUR = 2;

  const FeatureRow = ({ icon, color, title, desc, selected }) => (
    <div style={{
      display: 'flex', alignItems: 'flex-start', gap: 14,
      padding: isVoid ? '16px 18px' : '14px 16px',
      background: selected ? `${color}12` : theme.colors.surface,
      border: `1px solid ${selected ? color + '88' : theme.colors.border}`,
      borderRadius: theme.cardRadius,
      marginBottom: 10,
      ...(isCyber && selected ? { borderLeft: `3px solid ${color}` } : {}),
      ...(selected && theme.glow !== 'none' ? { boxShadow: `0 0 18px ${color}40` } : {}),
    }}>
      <div style={{
        width: 40, height: 40, flexShrink: 0,
        borderRadius: theme.chipShape === 'sharp' ? 6 : 10,
        background: `${color}1F`, border: `1px solid ${color}60`,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
      }}>
        <Icon name={icon} size={20} color={color}/>
      </div>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{
          fontSize: isVoid ? 16 : 15, fontWeight: 600,
          color: theme.colors.onBackground,
          fontFamily: isVoid ? theme.fonts.display : theme.fonts.body,
          letterSpacing: isVoid ? -0.2 : 0.1,
        }}>{title}</div>
        <div style={{
          marginTop: 3, fontSize: 12.5, lineHeight: 1.45,
          color: theme.colors.onSurface, fontFamily: theme.fonts.body,
        }}>{desc}</div>
      </div>
      <div style={{
        width: 20, height: 20, borderRadius: '50%',
        border: `1.5px solid ${selected ? color : theme.colors.muted}`,
        background: selected ? color : 'transparent',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        marginTop: 2, flexShrink: 0,
      }}>
        {selected && <Icon name="check" size={11} color={theme.colors.background} strokeWidth={3}/>}
      </div>
    </div>
  );

  return (
    <div style={{ height: '100%', display: 'flex', flexDirection: 'column', position: 'relative' }}>
      {/* Progress dots */}
      <div style={{
        display: 'flex', justifyContent: 'center', gap: 6,
        padding: '14px 20px 0',
      }}>
        {Array.from({ length: STEPS }).map((_, i) => (
          <div key={i} style={{
            width: i === CUR ? 22 : 6, height: 6,
            borderRadius: 3,
            background: i <= CUR ? theme.colors.primary : theme.colors.muted,
            opacity: i <= CUR ? 1 : 0.4,
            transition: 'all 0.3s',
            ...(i === CUR && theme.glow !== 'none' ? { boxShadow: `0 0 8px ${theme.colors.primary}90` } : {}),
          }}/>
        ))}
      </div>
      <div style={{
        textAlign: 'center', marginTop: 6,
        fontSize: 10, letterSpacing: 2, textTransform: 'uppercase',
        color: theme.colors.muted, fontFamily: theme.fonts.body,
      }}>
        {isMatrix ? `// step ${CUR + 1} of ${STEPS}` : `Step ${CUR + 1} of ${STEPS}`}
      </div>

      <div className="no-scrollbar" style={{ flex: 1, overflow: 'auto', padding: '28px 22px 120px' }}>
        {/* Hero art — stylized prism */}
        <div style={{
          height: 140, marginBottom: 24,
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          position: 'relative',
        }}>
          <svg width="120" height="120" viewBox="0 0 120 120"
            style={{ filter: theme.glow === 'none' ? 'none' : `drop-shadow(0 0 16px ${theme.colors.primary}80)` }}>
            <polygon points="60,10 110,95 10,95" fill="none"
              stroke={theme.colors.primary} strokeWidth="1.5"/>
            <polygon points="60,30 92,84 28,84" fill="none"
              stroke={theme.colors.secondary} strokeWidth="1" opacity="0.8"/>
            <polygon points="60,50 74,72 46,72" fill="none"
              stroke={theme.colors.urgentAccent} strokeWidth="1" opacity="0.7"/>
            {/* light ray splitting into 3 colors */}
            <line x1="0" y1="60" x2="35" y2="60" stroke={theme.colors.onSurface} strokeWidth="1"/>
            <line x1="75" y1="50" x2="120" y2="30" stroke={theme.colors.primary} strokeWidth="1.5" opacity="0.9"/>
            <line x1="80" y1="60" x2="120" y2="60" stroke={theme.colors.secondary} strokeWidth="1.5" opacity="0.9"/>
            <line x1="75" y1="72" x2="120" y2="92" stroke={theme.colors.urgentAccent} strokeWidth="1.5" opacity="0.9"/>
          </svg>
        </div>

        <div style={{
          fontFamily: theme.fonts.display,
          fontSize: isVoid ? 32 : 28, fontWeight: isVoid ? 500 : 700,
          color: theme.colors.onBackground,
          textTransform: up, letterSpacing: theme.displayTracking,
          lineHeight: 1.1, textAlign: 'center', marginBottom: 10,
          ...(isSynth ? { textShadow: `0 0 16px ${theme.colors.primary}70` } : {}),
        }}>
          {isMatrix ? 'CHOOSE YOUR STACK' : isVoid ? <>What brings you <em style={{ color: theme.colors.primary }}>here?</em></> : 'What brings you here?'}
        </div>
        <div style={{
          fontSize: 13, lineHeight: 1.5, color: theme.colors.onSurface,
          textAlign: 'center', marginBottom: 24,
          fontFamily: theme.fonts.body, letterSpacing: isVoid ? 0.3 : 0,
          padding: '0 10px',
        }}>
          {isMatrix
            ? '// pick the features you want enabled. you can change this later.'
            : 'Pick the features you want enabled. You can always change this later in Settings.'}
        </div>

        <FeatureRow icon="tasks" color={theme.colors.primary}
          title="Tasks & projects"
          desc="Plan your day, track projects, manage priorities."
          selected/>
        <FeatureRow icon="daily" color={theme.colors.secondary}
          title="Daily habits"
          desc="Check off routines, build streaks, see progress rings."
          selected/>
        <FeatureRow icon="timer" color="#00E5A0"
          title="Focus timer"
          desc="Pomodoro sessions with session notes + stats."
          />
        <FeatureRow icon="recurring" color={theme.colors.urgentAccent}
          title="Recurring reminders"
          desc="Weekly, monthly, or custom repeat schedules."
          selected/>
      </div>

      {/* Bottom action bar */}
      <div style={{
        position: 'absolute', left: 0, right: 0, bottom: 0,
        padding: '12px 18px 22px',
        background: theme.colors.background,
        borderTop: `1px solid ${theme.colors.border}`,
        display: 'flex', gap: 10, alignItems: 'center',
      }}>
        <div style={{
          padding: '13px 18px',
          border: `1px solid ${theme.colors.border}`,
          color: theme.colors.onSurface,
          borderRadius: theme.chipShape === 'sharp' ? (isMatrix ? 0 : 6) : 12,
          fontSize: 13, fontWeight: 600, letterSpacing: 0.4,
          fontFamily: theme.fonts.body, textTransform: up,
        }}>{isMatrix ? 'back' : 'Back'}</div>
        <div style={{
          flex: 1, padding: '13px 16px',
          textAlign: 'center',
          background: theme.colors.primary,
          color: theme.colors.background,
          borderRadius: theme.chipShape === 'sharp' ? (isMatrix ? 0 : 6) : 12,
          fontSize: 13, fontWeight: 700, letterSpacing: 1.2,
          textTransform: up, fontFamily: theme.fonts.body,
          boxShadow: theme.glow === 'none' ? 'none' : `0 0 20px ${theme.colors.primary}80`,
        }}>{isMatrix ? 'continue →' : 'Continue'}</div>
      </div>
    </div>
  );
}

window.OnboardingScreen = OnboardingScreen;
