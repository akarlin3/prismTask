// AppearanceScreen — Settings → Appearance subpage.

function AppearanceScreen({ theme }) {
  const isMatrix = theme.terminal;
  const isVoid = theme.editorial;
  const isCyber = theme.brackets;

  // Accent swatches (the 8 accent colors from ThemePreferences)
  const accents = [
    { c: theme.colors.primary, name: 'Signal' },
    { c: theme.colors.secondary, name: 'Aux' },
    { c: theme.colors.urgentAccent, name: 'Flag' },
    { c: '#00E5A0', name: 'Mint' },
    { c: '#FFB34D', name: 'Amber' },
    { c: '#7B9BFF', name: 'Ice' },
  ];

  // Priority swatches
  const priorities = [
    { label: 'None', c: theme.colors.muted },
    { label: 'Low', c: '#4DA6FF' },
    { label: 'Med', c: theme.colors.secondary },
    { label: 'High', c: theme.colors.primary },
    { label: 'Urgent', c: theme.colors.urgentAccent },
  ];

  return (
    <div style={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <SubHeader theme={theme} title="Appearance"
        subtitle="Theme, accent, priority colors, and type scale."/>

      <div className="no-scrollbar" style={{ flex: 1, overflow: 'auto', padding: '4px 18px 40px' }}>
        {/* Theme picker as big previews */}
        <SubSectionLabel theme={theme} label="Theme"/>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 8 }}>
          {['CYBERPUNK','SYNTHWAVE','MATRIX','VOID'].map(id => {
            const t = window.PRISM_THEMES[id];
            const active = id === theme.id;
            return (
              <div key={id} style={{
                padding: 6,
                border: `1px solid ${active ? theme.colors.primary : theme.colors.border}`,
                background: active ? `${theme.colors.primary}15` : theme.colors.surface,
                borderRadius: theme.chipShape === 'sharp' ? 3 : 8,
                ...(active && theme.glow !== 'none' ? { boxShadow: `0 0 14px ${theme.colors.primary}60` } : {}),
              }}>
                <div style={{
                  height: 44, borderRadius: theme.chipShape === 'sharp' ? 2 : 4,
                  background: t.colors.background, position: 'relative', overflow: 'hidden',
                  border: `1px solid ${t.colors.border}`,
                }}>
                  <div style={{ position: 'absolute', top: 6, left: 6, width: 14, height: 3, background: t.colors.primary }}/>
                  <div style={{ position: 'absolute', top: 14, left: 6, width: 22, height: 2, background: t.colors.onSurface, opacity: 0.5 }}/>
                  <div style={{ position: 'absolute', bottom: 6, right: 6, width: 12, height: 12, borderRadius: '50%', background: t.colors.primary, opacity: 0.7 }}/>
                  <div style={{ position: 'absolute', bottom: 8, left: 6, width: 10, height: 2, background: t.colors.secondary }}/>
                </div>
                <div style={{
                  marginTop: 6, textAlign: 'center',
                  fontSize: 9, letterSpacing: 1.2, fontWeight: 600,
                  color: active ? t.colors.primary : theme.colors.muted,
                  textTransform: 'uppercase', fontFamily: theme.fonts.body,
                }}>{isMatrix ? id.toLowerCase() : t.label}</div>
              </div>
            );
          })}
        </div>

        {/* Mode */}
        <SubSectionLabel theme={theme} label="Appearance mode"/>
        <Segments theme={theme} items={['Light', 'Dark', 'System']} active="Dark"/>

        {/* Accent */}
        <SubSectionLabel theme={theme} label="Accent color"/>
        <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8, flexWrap: 'wrap' }}>
          {accents.map((a, i) => (
            <Swatch key={a.c} theme={theme} color={a.c} label={a.name} active={i === 0}/>
          ))}
        </div>

        {/* Priority colors */}
        <SubSectionLabel theme={theme} label="Priority colors"/>
        <div style={{
          background: theme.colors.surface,
          border: `1px solid ${theme.colors.border}`,
          borderRadius: theme.cardRadius,
          padding: '14px 14px',
        }}>
          {priorities.map(p => (
            <div key={p.label} style={{
              display: 'flex', alignItems: 'center', gap: 14,
              padding: '10px 0',
              borderBottom: `1px solid ${theme.colors.border}`,
            }}>
              <div style={{ width: 3, alignSelf: 'stretch', background: p.c,
                borderRadius: theme.chipShape === 'sharp' ? 0 : 2,
                boxShadow: theme.glow !== 'none' ? `0 0 6px ${p.c}90` : 'none' }}/>
              <span style={{
                flex: 1, fontSize: 14, fontWeight: 500,
                color: theme.colors.onBackground, fontFamily: theme.fonts.body,
              }}>{isMatrix ? p.label.toLowerCase() : p.label}</span>
              <div style={{
                width: 26, height: 26,
                borderRadius: theme.chipShape === 'sharp' ? 4 : '50%',
                background: p.c,
                boxShadow: theme.glow !== 'none' ? `0 0 10px ${p.c}60` : 'none',
              }}/>
            </div>
          ))}
          <div style={{
            marginTop: 6, fontSize: 11, color: theme.colors.muted,
            fontFamily: theme.fonts.body, letterSpacing: 0.3,
          }}>
            {isMatrix ? '// tap a swatch to customize' : 'Tap a swatch to customize.'}
          </div>
        </div>

        {/* Font scale slider */}
        <SubSectionLabel theme={theme} label="Font size"/>
        <div style={{
          padding: '16px 16px 10px',
          background: theme.colors.surface,
          border: `1px solid ${theme.colors.border}`,
          borderRadius: theme.cardRadius,
          marginBottom: 12,
        }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 12, alignItems: 'baseline' }}>
            <span style={{ fontSize: 10, color: theme.colors.muted, fontFamily: theme.fonts.body, letterSpacing: 1.5 }}>A</span>
            <span style={{
              fontFamily: theme.fonts.display,
              fontSize: 22, fontWeight: isVoid ? 500 : 700,
              color: theme.colors.primary,
            }}>{isMatrix ? '1.10x' : '110%'}</span>
            <span style={{ fontSize: 18, color: theme.colors.muted, fontFamily: theme.fonts.body, letterSpacing: 1.5 }}>A</span>
          </div>
          <Slider theme={theme} pct={60} label={isMatrix ? 'scale = 1.10' : 'Slightly larger'}/>
        </div>

        {/* Density & corners */}
        <SubSectionLabel theme={theme} label="Layout"/>
        <div style={{
          background: theme.colors.surface,
          border: `1px solid ${theme.colors.border}`,
          borderRadius: theme.cardRadius,
          padding: '14px 14px',
        }}>
          <div style={{ fontSize: 10, letterSpacing: 1.5, textTransform: 'uppercase', color: theme.colors.muted, fontFamily: theme.fonts.body, marginBottom: 6 }}>Density</div>
          <Segments theme={theme} items={['Compact', 'Normal', 'Relaxed']} active="Normal"/>
          <div style={{ fontSize: 10, letterSpacing: 1.5, textTransform: 'uppercase', color: theme.colors.muted, fontFamily: theme.fonts.body, marginTop: 14, marginBottom: 6 }}>Card corners</div>
          <Segments theme={theme} items={['Sharp', 'Soft', 'Round']} active={isVoid ? 'Soft' : isCyber || isMatrix ? 'Sharp' : 'Round'}/>
        </div>
      </div>
    </div>
  );
}

window.AppearanceScreen = AppearanceScreen;
