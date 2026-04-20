// ShakeScreen — Settings → Shake to Report subpage.

function ShakeScreen({ theme }) {
  const isMatrix = theme.terminal;
  const isVoid = theme.editorial;
  const isCyber = theme.brackets;
  const isSynth = theme.sunset;

  const Row = ({ label, desc, on, first, last, control }) => (
    <div style={{
      display: 'flex', alignItems: 'flex-start', gap: 12,
      padding: isVoid ? '16px 0' : '14px 14px',
      background: isVoid ? 'transparent' : theme.colors.surface,
      borderTop: isVoid ? (first ? `1px solid ${theme.colors.border}` : 'none') : 'none',
      borderBottom: `1px solid ${theme.colors.border}`,
      borderLeft: isVoid ? 'none' : `1px solid ${theme.colors.border}`,
      borderRight: isVoid ? 'none' : `1px solid ${theme.colors.border}`,
      ...(!isVoid && first ? { borderTopLeftRadius: theme.cardRadius, borderTopRightRadius: theme.cardRadius, borderTop: `1px solid ${theme.colors.border}` } : {}),
      ...(!isVoid && last ? { borderBottomLeftRadius: theme.cardRadius, borderBottomRightRadius: theme.cardRadius } : {}),
    }}>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{
          fontSize: 14.5, fontWeight: 500,
          color: theme.colors.onBackground,
          fontFamily: isVoid ? theme.fonts.display : theme.fonts.body,
        }}>{isMatrix ? label.toLowerCase() : label}</div>
        {desc && (
          <div style={{
            marginTop: 3, fontSize: 11.5, lineHeight: 1.45,
            color: theme.colors.muted, fontFamily: theme.fonts.body,
          }}>{desc}</div>
        )}
      </div>
      <div style={{ flexShrink: 0, marginTop: 2 }}>
        {control || <window.PTToggle theme={theme} on={on}/>}
      </div>
    </div>
  );

  return (
    <div style={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <SubHeader theme={theme} title="Shake to Report"
        subtitle="Shake the device to capture a screenshot and file a bug."/>

      <div className="no-scrollbar" style={{ flex: 1, overflow: 'auto', padding: '4px 18px 40px' }}>
        {/* Hero illustration: phone with motion lines */}
        <div style={{
          height: 120, marginBottom: 10,
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          position: 'relative',
        }}>
          <svg width="220" height="110" viewBox="0 0 220 110"
            style={{ filter: theme.glow === 'none' ? 'none' : `drop-shadow(0 0 14px ${theme.colors.primary}80)` }}>
            {/* motion lines */}
            {[-1, 0, 1].map(i => (
              <g key={i}>
                <path d={`M 28 ${45 + i * 12} L 52 ${45 + i * 12}`} stroke={theme.colors.primary} strokeWidth="2" strokeLinecap="round" opacity={1 - Math.abs(i) * 0.4}/>
                <path d={`M 168 ${45 + i * 12} L 192 ${45 + i * 12}`} stroke={theme.colors.primary} strokeWidth="2" strokeLinecap="round" opacity={1 - Math.abs(i) * 0.4}/>
              </g>
            ))}
            {/* phone */}
            <rect x="88" y="20" width="44" height="70" rx="8" fill={theme.colors.surface} stroke={theme.colors.primary} strokeWidth="2"/>
            <rect x="94" y="30" width="32" height="44" rx="3" fill={theme.colors.background} stroke={theme.colors.border}/>
            <circle cx="110" cy="82" r="3" fill={theme.colors.primary}/>
            {/* impact star */}
            <g transform="translate(152 24)">
              <path d="M0 -12 L3 -3 L12 0 L3 3 L0 12 L-3 3 L-12 0 L-3 -3 Z" fill={theme.colors.urgentAccent}/>
            </g>
          </svg>
        </div>

        {/* Master toggle */}
        <div style={{
          padding: '14px 16px',
          background: `${theme.colors.primary}10`,
          border: `1px solid ${theme.colors.primary}55`,
          borderRadius: theme.cardRadius,
          marginBottom: 14,
          display: 'flex', alignItems: 'center', gap: 12,
          ...(isCyber ? { borderLeft: `3px solid ${theme.colors.primary}` } : {}),
          ...(theme.glow !== 'none' ? { boxShadow: `0 0 14px ${theme.colors.primary}30` } : {}),
        }}>
          <div style={{ flex: 1 }}>
            <div style={{
              fontSize: isVoid ? 16 : 15, fontWeight: 600,
              color: theme.colors.onBackground,
              fontFamily: isVoid ? theme.fonts.display : theme.fonts.body,
            }}>{isMatrix ? 'enable shake-to-report' : 'Enable shake-to-report'}</div>
            <div style={{
              marginTop: 3, fontSize: 11.5,
              color: theme.colors.muted, fontFamily: theme.fonts.body, lineHeight: 1.5,
            }}>
              {isMatrix
                ? '// accelerometer active in foreground only.'
                : 'Active only while the app is in the foreground.'}
            </div>
          </div>
          <window.PTToggle theme={theme} on/>
        </div>

        {/* Sensitivity */}
        <SubSectionLabel theme={theme} label="Sensitivity"/>
        <div style={{
          padding: '14px 16px',
          background: theme.colors.surface,
          border: `1px solid ${theme.colors.border}`,
          borderRadius: theme.cardRadius,
          marginBottom: 14,
        }}>
          <Segments theme={theme} items={['Low', 'Medium', 'High']} active="Medium"/>
          <div style={{
            marginTop: 12, fontSize: 11.5, lineHeight: 1.5,
            color: theme.colors.muted, fontFamily: theme.fonts.body,
          }}>
            {isMatrix
              ? '// medium: ~15 m/s². recommended for most devices.'
              : 'Medium — ~15 m/s². Recommended for most devices.'}
          </div>
          <div style={{
            marginTop: 10, height: 40,
            border: `1px dashed ${theme.colors.border}`,
            borderRadius: theme.chipShape === 'sharp' ? 0 : 6,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            fontSize: 11, letterSpacing: 1.4, textTransform: 'uppercase',
            color: theme.colors.primary, fontFamily: theme.fonts.body,
          }}>
            {isMatrix ? '◉ tap and shake to test' : '◉ Tap and shake to test'}
          </div>
        </div>

        {/* Options */}
        <SubSectionLabel theme={theme} label="Options"/>
        <Row label="Include screenshot" on first
          desc="Attach the current screen automatically."/>
        <Row label="Include diagnostics" on
          desc="App version, device, last sync, active screen."/>
        <Row label="Haptic confirmation" on
          desc="Short buzz when a shake is registered."/>
        <Row label="Only in debug builds" last
          desc="Disable entirely in production builds."/>

        <SubSectionLabel theme={theme} label="Recent reports"/>
        <div style={{
          padding: '4px 14px',
          background: theme.colors.surface,
          border: `1px solid ${theme.colors.border}`,
          borderRadius: theme.cardRadius,
        }}>
          {[
            { t: 'Today · 14:22', s: 'Timer stuck at 00:01', pri: theme.colors.primary },
            { t: 'Yesterday', s: 'Widget not refreshing', pri: theme.colors.secondary },
            { t: 'Apr 3', s: 'Sync conflict on habits', pri: theme.colors.urgentAccent },
          ].map((r, i, arr) => (
            <div key={i} style={{
              display: 'flex', alignItems: 'center', gap: 10,
              padding: '10px 0',
              borderBottom: i === arr.length - 1 ? 'none' : `1px solid ${theme.colors.border}`,
            }}>
              <div style={{ width: 6, height: 6, borderRadius: '50%', background: r.pri, flexShrink: 0 }}/>
              <span style={{ flex: 1, fontSize: 13, color: theme.colors.onBackground, fontFamily: theme.fonts.body }}>
                {r.s}
              </span>
              <span style={{
                fontSize: 10, letterSpacing: 1.2, textTransform: 'uppercase',
                color: theme.colors.muted, fontFamily: theme.fonts.body,
              }}>{r.t}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

window.ShakeScreen = ShakeScreen;
