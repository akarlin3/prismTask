// Shared helpers for Settings subpages (Appearance / Accessibility / Sync / Shake).
// Reuses the same visual vocabulary as screen-settings.jsx.

function SubHeader({ theme, title, subtitle }) {
  const up = theme.displayUpper ? 'uppercase' : 'none';
  const isMatrix = theme.terminal;
  const isVoid = theme.editorial;
  const isCyber = theme.brackets;
  const isSynth = theme.sunset;
  return (
    <div style={{ padding: '8px 20px 0' }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', paddingTop: 6 }}>
        <svg width="24" height="24" viewBox="0 0 24 24" fill="none"
          stroke={theme.colors.onBackground} strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round">
          <path d="M19 12H5M12 19l-7-7 7-7"/>
        </svg>
        <div style={{
          fontSize: 10, letterSpacing: 2, color: theme.colors.muted,
          fontFamily: theme.fonts.body, textTransform: up,
        }}>
          {isMatrix ? `// settings / ${title.toLowerCase()}` : isCyber ? `// SETTINGS / ${title.toUpperCase()}` : 'Settings'}
        </div>
        <div style={{ width: 22 }}/>
      </div>
      <div style={{
        marginTop: isVoid ? 24 : 20, marginBottom: 4,
        fontFamily: theme.fonts.display,
        fontSize: isVoid ? 38 : 28, fontWeight: isVoid ? 500 : 700,
        color: theme.colors.onBackground,
        textTransform: up, letterSpacing: theme.displayTracking,
        lineHeight: 1,
        ...(isSynth ? { textShadow: `0 0 18px ${theme.colors.primary}60` } : {}),
        ...(isCyber ? { textShadow: `0 0 8px ${theme.colors.primary}70` } : {}),
      }}>
        {isMatrix ? title.toUpperCase() : isVoid ? <span>{title}<span style={{ color: theme.colors.primary }}>.</span></span> : title}
      </div>
      {subtitle && (
        <div style={{
          fontSize: 12, color: theme.colors.muted,
          fontFamily: theme.fonts.body, letterSpacing: isVoid ? 1.2 : 0.3,
          textTransform: isVoid ? 'uppercase' : 'none', marginBottom: 12,
          lineHeight: 1.45,
        }}>
          {isMatrix ? `// ${subtitle.toLowerCase()}` : subtitle}
        </div>
      )}
    </div>
  );
}

// Slider with a labeled thumb position
function Slider({ theme, pct, label, color }) {
  const c = color || theme.colors.primary;
  const isMatrix = theme.terminal;
  return (
    <div>
      <div style={{
        height: isMatrix ? 6 : 4,
        background: theme.colors.surfaceVariant,
        border: `1px solid ${theme.colors.border}`,
        borderRadius: isMatrix ? 0 : 2,
        position: 'relative',
      }}>
        <div style={{
          position: 'absolute', left: 0, top: 0, bottom: 0,
          width: `${pct}%`, background: c,
          borderRadius: isMatrix ? 0 : 2,
          ...(theme.glow !== 'none' ? { boxShadow: `0 0 10px ${c}90` } : {}),
        }}/>
        <div style={{
          position: 'absolute', left: `calc(${pct}% - 8px)`,
          top: isMatrix ? -4 : -6, width: 16, height: isMatrix ? 14 : 16,
          background: c, border: `1px solid ${theme.colors.background}`,
          borderRadius: isMatrix ? 0 : '50%',
          ...(theme.glow !== 'none' ? { boxShadow: `0 0 10px ${c}` } : {}),
        }}/>
      </div>
      {label && (
        <div style={{
          marginTop: 6, fontSize: 10, letterSpacing: 1.2,
          textTransform: 'uppercase', color: theme.colors.muted,
          fontFamily: theme.fonts.body, textAlign: 'right',
        }}>{label}</div>
      )}
    </div>
  );
}

// Chip row for segmented picks (used in A11y, Shake, Appearance)
function Segments({ theme, items, active, color }) {
  const c = color || theme.colors.primary;
  const isMatrix = theme.terminal;
  return (
    <div style={{ display: 'flex', gap: 6 }}>
      {items.map(it => {
        const on = it === active;
        return (
          <div key={it} style={{
            flex: 1, textAlign: 'center', padding: '9px 6px',
            border: `1px solid ${on ? c : theme.colors.border}`,
            background: on ? `${c}22` : 'transparent',
            color: on ? c : theme.colors.muted,
            borderRadius: theme.chipShape === 'sharp' ? 3 : 8,
            fontSize: 11, fontWeight: 600, letterSpacing: 1.2,
            textTransform: 'uppercase', fontFamily: theme.fonts.body,
          }}>{isMatrix ? it.toLowerCase() : it}</div>
        );
      })}
    </div>
  );
}

// Color swatch button
function Swatch({ theme, color, active, label }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 4, minWidth: 48 }}>
      <div style={{
        width: 36, height: 36,
        borderRadius: theme.chipShape === 'sharp' ? 4 : '50%',
        background: color,
        border: `2px solid ${active ? theme.colors.onBackground : 'transparent'}`,
        boxShadow: active && theme.glow !== 'none' ? `0 0 14px ${color}` : `0 0 0 1px ${theme.colors.border}`,
      }}/>
      {label && (
        <div style={{
          fontSize: 9, letterSpacing: 0.8, textTransform: 'uppercase',
          color: active ? theme.colors.onBackground : theme.colors.muted,
          fontFamily: theme.fonts.body,
        }}>{label}</div>
      )}
    </div>
  );
}

function SubSectionLabel({ theme, label }) {
  const isVoid = theme.editorial;
  const isMatrix = theme.terminal;
  return (
    <div style={{
      display: 'flex', alignItems: 'center', gap: 10,
      marginTop: isVoid ? 24 : 18, marginBottom: 10,
      fontSize: isVoid ? 10 : 11, fontWeight: 600,
      fontFamily: theme.fonts.body,
      letterSpacing: isVoid ? 2.4 : 1.6,
      textTransform: 'uppercase',
      color: theme.colors.onSurface,
    }}>
      {isVoid && <span style={{ width: 18, height: 1, background: theme.colors.onSurface, display: 'inline-block' }}/>}
      <span style={{ color: theme.colors.primary, whiteSpace: 'nowrap' }}>
        {isMatrix ? `# ${label.toLowerCase()}` : label}
      </span>
      <span style={{ flex: 1, height: 1, background: theme.colors.border, marginLeft: 6 }}/>
    </div>
  );
}

Object.assign(window, { SubHeader, Slider, Segments, Swatch, SubSectionLabel });
