// BugReportScreen — post-shake "report a bug" form with screenshot preview.

function BugReportScreen({ theme }) {
  const up = theme.displayUpper ? 'uppercase' : 'none';
  const isMatrix = theme.terminal;
  const isVoid = theme.editorial;
  const isCyber = theme.brackets;
  const isSynth = theme.sunset;

  const SeverityChip = ({ label, color, active }) => (
    <div style={{
      flex: 1, textAlign: 'center', padding: '10px 8px',
      border: `1px solid ${active ? color : theme.colors.border}`,
      background: active ? `${color}22` : 'transparent',
      color: active ? color : theme.colors.muted,
      borderRadius: theme.chipShape === 'sharp' ? 3 : 8,
      fontSize: 11, fontWeight: 600, letterSpacing: 1.2,
      textTransform: 'uppercase', fontFamily: theme.fonts.body,
    }}>{label}</div>
  );

  const DiagRow = ({ k, v }) => (
    <div style={{
      display: 'flex', justifyContent: 'space-between',
      padding: '6px 0', fontSize: 12,
      fontFamily: theme.fonts.body,
      borderBottom: `1px solid ${theme.colors.border}`,
    }}>
      <span style={{ color: theme.colors.muted, letterSpacing: 1.2, textTransform: 'uppercase', fontSize: 10 }}>
        {isMatrix ? k.toLowerCase() : k}
      </span>
      <span style={{ color: theme.colors.onBackground }}>{v}</span>
    </div>
  );

  // Fake screenshot thumbnail — sketch of Today screen
  const Thumbnail = () => (
    <div style={{
      width: 96, height: 180, flexShrink: 0,
      borderRadius: theme.chipShape === 'sharp' ? 4 : 12,
      background: theme.colors.background,
      border: `2px solid ${theme.colors.primary}55`,
      overflow: 'hidden', position: 'relative',
      ...(theme.glow !== 'none' ? { boxShadow: `0 0 14px ${theme.colors.primary}40` } : {}),
    }}>
      <div style={{ height: 8, background: theme.colors.surface }}/>
      <div style={{ padding: 8 }}>
        <div style={{ height: 3, width: '40%', background: theme.colors.muted, marginBottom: 6 }}/>
        <div style={{ height: 14, width: '70%', background: theme.colors.primary, marginBottom: 8, opacity: 0.7 }}/>
        <div style={{ width: 60, height: 60, borderRadius: '50%', border: `4px solid ${theme.colors.primary}`, margin: '8px auto', opacity: 0.6 }}/>
        <div style={{ height: 2, width: '80%', background: theme.colors.border, margin: '10px 0 4px' }}/>
        <div style={{ height: 10, background: theme.colors.surface, marginBottom: 4, borderRadius: 2 }}/>
        <div style={{ height: 10, background: theme.colors.surface, marginBottom: 4, borderRadius: 2 }}/>
        <div style={{ height: 10, background: theme.colors.surface, borderRadius: 2 }}/>
      </div>
      {/* marker pin */}
      <div style={{
        position: 'absolute', top: 74, right: 14,
        width: 16, height: 16, borderRadius: '50%',
        background: theme.colors.urgentAccent,
        border: `2px solid ${theme.colors.background}`,
        boxShadow: `0 0 10px ${theme.colors.urgentAccent}90`,
      }}/>
    </div>
  );

  return (
    <div style={{ height: '100%', display: 'flex', flexDirection: 'column', position: 'relative' }}>
      {/* Top bar */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '8px 16px 0' }}>
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none"
          stroke={theme.colors.onBackground} strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round">
          <path d="M18 6L6 18M6 6l12 12"/>
        </svg>
        <div style={{
          fontSize: 10, letterSpacing: 2, textTransform: 'uppercase',
          color: theme.colors.muted, fontFamily: theme.fonts.body,
        }}>{isMatrix ? '// shake detected' : 'Shake detected'}</div>
      </div>

      <div className="no-scrollbar" style={{ flex: 1, overflow: 'auto', padding: '18px 20px 100px' }}>
        {/* Title */}
        <div style={{
          fontFamily: theme.fonts.display,
          fontSize: isVoid ? 34 : 26, fontWeight: isVoid ? 500 : 700,
          color: theme.colors.onBackground, textTransform: up,
          letterSpacing: theme.displayTracking, lineHeight: 1.1,
          marginBottom: 6,
          ...(isSynth ? { textShadow: `0 0 16px ${theme.colors.primary}60` } : {}),
        }}>{isMatrix ? 'REPORT A BUG' : isVoid ? <>Report a <em style={{ color: theme.colors.primary }}>bug</em></> : 'Report a Bug'}</div>
        <div style={{
          fontSize: 12.5, color: theme.colors.onSurface,
          fontFamily: theme.fonts.body, lineHeight: 1.5, marginBottom: 18,
        }}>
          {isMatrix
            ? '// screenshot captured. describe what went wrong.'
            : 'We captured a screenshot. Tell us what went wrong — your diagnostics will be attached.'}
        </div>

        {/* Thumbnail + what went wrong */}
        <div style={{ display: 'flex', gap: 12, marginBottom: 18 }}>
          <Thumbnail/>
          <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 10 }}>
            <div style={{
              fontSize: 10, letterSpacing: 1.8, textTransform: 'uppercase',
              color: theme.colors.muted, fontFamily: theme.fonts.body,
            }}>{isMatrix ? '# captured 14:22:03' : 'Screenshot · 14:22'}</div>
            <div style={{
              padding: '7px 11px',
              border: `1px solid ${theme.colors.border}`,
              background: theme.colors.surface,
              color: theme.colors.onSurface,
              fontSize: 11, fontWeight: 500, letterSpacing: 0.4,
              borderRadius: theme.chipShape === 'sharp' ? 3 : 8,
              fontFamily: theme.fonts.body, textAlign: 'center',
            }}>{isMatrix ? 'annotate' : 'Annotate'}</div>
            <div style={{
              padding: '7px 11px',
              border: `1px solid ${theme.colors.border}`,
              color: theme.colors.onSurface,
              fontSize: 11, fontWeight: 500, letterSpacing: 0.4,
              borderRadius: theme.chipShape === 'sharp' ? 3 : 8,
              fontFamily: theme.fonts.body, textAlign: 'center',
            }}>{isMatrix ? 'retake' : 'Retake'}</div>
            <div style={{
              padding: '7px 11px',
              border: `1px dashed ${theme.colors.border}`,
              color: theme.colors.muted,
              fontSize: 11, fontWeight: 500, letterSpacing: 0.4,
              borderRadius: theme.chipShape === 'sharp' ? 3 : 8,
              fontFamily: theme.fonts.body, textAlign: 'center',
            }}>{isMatrix ? 'remove' : 'Remove'}</div>
          </div>
        </div>

        {/* Severity */}
        <div style={{
          fontSize: 10, letterSpacing: 2, textTransform: 'uppercase',
          color: theme.colors.muted, fontFamily: theme.fonts.body, marginBottom: 8,
        }}>{isMatrix ? '# severity' : 'Severity'}</div>
        <div style={{ display: 'flex', gap: 6, marginBottom: 18 }}>
          <SeverityChip label="Low" color={theme.colors.secondary}/>
          <SeverityChip label="Med" color={theme.colors.primary} active/>
          <SeverityChip label="High" color={theme.colors.urgentAccent}/>
          <SeverityChip label="Crash" color={theme.colors.urgentAccent}/>
        </div>

        {/* Description textarea */}
        <div style={{
          fontSize: 10, letterSpacing: 2, textTransform: 'uppercase',
          color: theme.colors.muted, fontFamily: theme.fonts.body, marginBottom: 8,
        }}>{isMatrix ? '# what happened' : 'What happened?'}</div>
        <div style={{
          padding: '14px',
          background: theme.colors.surface,
          border: `1px solid ${theme.colors.border}`,
          borderRadius: theme.chipShape === 'sharp' ? (isMatrix ? 0 : 6) : 10,
          minHeight: 100,
          fontSize: 13.5, lineHeight: 1.5,
          color: theme.colors.onBackground, fontFamily: theme.fonts.body,
          marginBottom: 18,
        }}>
          <span>Timer kept running after I left the app. When I came back the session</span>
          <span style={{ color: theme.colors.muted }}> was stuck at 00:01 and wouldn't reset...</span>
          <span style={{
            display: 'inline-block', width: 2, height: 15,
            background: theme.colors.primary, marginLeft: 2, verticalAlign: 'middle',
            animation: 'blink 1s steps(2) infinite',
          }}/>
        </div>

        {/* Diagnostics */}
        <div style={{
          display: 'flex', alignItems: 'center', justifyContent: 'space-between',
          marginBottom: 6,
        }}>
          <div style={{
            fontSize: 10, letterSpacing: 2, textTransform: 'uppercase',
            color: theme.colors.muted, fontFamily: theme.fonts.body,
          }}>{isMatrix ? '# diagnostics' : 'Diagnostics attached'}</div>
          <div style={{
            fontSize: 10, letterSpacing: 1.4, color: theme.colors.primary,
            textTransform: 'uppercase', fontFamily: theme.fonts.body,
          }}>{isMatrix ? '[attached]' : 'Included'}</div>
        </div>
        <ThemedCard theme={theme} padding="4px 14px 4px">
          <DiagRow k="App version" v="v2.4.0 (2026.04)"/>
          <DiagRow k="Device" v="Pixel 8 · Android 15"/>
          <DiagRow k="Theme" v={theme.id[0] + theme.id.slice(1).toLowerCase()}/>
          <DiagRow k="Screen" v="Timer"/>
          <div style={{ borderBottom: 'none' }}>
            <DiagRow k="Last sync" v="2m ago"/>
          </div>
        </ThemedCard>
      </div>

      {/* Submit bar */}
      <div style={{
        position: 'absolute', left: 0, right: 0, bottom: 0,
        padding: '12px 18px 22px',
        background: theme.colors.background,
        borderTop: `1px solid ${theme.colors.border}`,
        display: 'flex', gap: 10,
      }}>
        <div style={{
          padding: '13px 18px',
          border: `1px solid ${theme.colors.border}`,
          color: theme.colors.onSurface,
          borderRadius: theme.chipShape === 'sharp' ? (isMatrix ? 0 : 6) : 12,
          fontSize: 13, fontWeight: 600, letterSpacing: 0.4,
          fontFamily: theme.fonts.body, textTransform: up,
        }}>{isMatrix ? 'cancel' : 'Cancel'}</div>
        <div style={{
          flex: 1, textAlign: 'center', padding: '13px 16px',
          background: theme.colors.primary,
          color: theme.colors.background,
          borderRadius: theme.chipShape === 'sharp' ? (isMatrix ? 0 : 6) : 12,
          fontSize: 13, fontWeight: 700, letterSpacing: 1.2,
          textTransform: up, fontFamily: theme.fonts.body,
          boxShadow: theme.glow === 'none' ? 'none' : `0 0 18px ${theme.colors.primary}80`,
        }}>{isMatrix ? 'submit report →' : 'Submit Report'}</div>
      </div>
    </div>
  );
}

window.BugReportScreen = BugReportScreen;
