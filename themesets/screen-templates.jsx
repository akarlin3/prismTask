// TemplatesScreen — template picker / library.

function TemplateCard({ theme, title, subtitle, tags, count, color, featured }) {
  const isMatrix = theme.terminal;
  const isVoid = theme.editorial;
  const isCyber = theme.brackets;
  const c = color || theme.colors.primary;
  return (
    <div style={{
      padding: isVoid ? '18px 20px' : '14px 16px',
      background: theme.colors.surface,
      border: `1px solid ${theme.colors.border}`,
      borderRadius: theme.cardRadius,
      marginBottom: isVoid ? 14 : 10,
      position: 'relative',
      overflow: 'hidden',
      ...(isCyber ? { borderLeft: `3px solid ${c}` } : {}),
      ...(featured && theme.glow !== 'none' ? { boxShadow: `0 0 18px ${c}30` } : {}),
    }}>
      {featured && (
        <div style={{
          position: 'absolute', top: 10, right: 12,
          fontSize: 9, letterSpacing: 1.6, textTransform: 'uppercase',
          color: c, fontWeight: 700, fontFamily: theme.fonts.body,
          padding: '2px 6px',
          border: `1px solid ${c}60`,
          background: `${c}15`,
          borderRadius: theme.chipShape === 'sharp' ? 0 : 4,
        }}>{isMatrix ? '★ pro' : '★ Pro'}</div>
      )}
      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <div style={{
          width: 44, height: 44,
          borderRadius: theme.chipShape === 'sharp' ? 6 : 10,
          background: `${c}1F`, border: `1px solid ${c}55`,
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          fontFamily: isVoid ? theme.fonts.display : theme.fonts.body,
          fontSize: 20, fontWeight: 700, color: c,
          flexShrink: 0,
        }}>{title[0]}</div>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{
            fontSize: isVoid ? 16 : 15, fontWeight: isVoid ? 500 : 600,
            color: theme.colors.onBackground,
            letterSpacing: isVoid ? -0.2 : 0.1,
            fontFamily: isVoid ? theme.fonts.display : theme.fonts.body,
          }}>{title}</div>
          <div style={{
            marginTop: 2, fontSize: 12, color: theme.colors.muted,
            fontFamily: theme.fonts.body, letterSpacing: 0.2,
          }}>{subtitle}</div>
        </div>
      </div>
      {tags && (
        <div style={{ marginTop: 10, display: 'flex', gap: 6, flexWrap: 'wrap' }}>
          {tags.map(t => (
            <span key={t} style={{
              fontSize: 10, letterSpacing: 0.8, textTransform: 'uppercase',
              color: theme.colors.muted, fontFamily: theme.fonts.body,
              padding: '2px 8px',
              borderRadius: theme.chipShape === 'sharp' ? 2 : 999,
              background: theme.colors.tagSurface,
              border: `1px solid ${theme.colors.border}`,
            }}>{isMatrix ? `#${t.toLowerCase()}` : t}</span>
          ))}
          <span style={{
            marginLeft: 'auto',
            fontSize: 10, letterSpacing: 1.2, textTransform: 'uppercase',
            color: theme.colors.primary, fontFamily: theme.fonts.body,
          }}>{isMatrix ? `${count} tasks →` : `${count} tasks →`}</span>
        </div>
      )}
    </div>
  );
}

function TemplatesScreen({ theme }) {
  const up = theme.displayUpper ? 'uppercase' : 'none';
  const isMatrix = theme.terminal;
  const isVoid = theme.editorial;
  const isCyber = theme.brackets;
  const isSynth = theme.sunset;

  const categories = ['All', 'Work', 'Personal', 'Health', 'Study'];

  return (
    <div style={{ height: '100%', display: 'flex', flexDirection: 'column', position: 'relative' }}>
      {/* Top bar */}
      <div style={{ padding: '8px 20px 0' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none"
            stroke={theme.colors.onBackground} strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round">
            <path d="M19 12H5M12 19l-7-7 7-7"/>
          </svg>
          <div style={{
            fontSize: 10, letterSpacing: 2, color: theme.colors.muted,
            fontFamily: theme.fonts.body, textTransform: up,
          }}>{isMatrix ? '◉ templates --list' : isCyber ? '// TEMPLATES.LIB' : isSynth ? '◆ TEMPLATES' : 'Library'}</div>
          <Icon name="plus" size={22} color={theme.colors.onSurface}/>
        </div>

        <div style={{
          marginTop: isVoid ? 26 : 22, marginBottom: 6,
          fontFamily: theme.fonts.display,
          fontSize: isVoid ? 44 : 32, fontWeight: isVoid ? 500 : 700,
          color: theme.colors.onBackground,
          textTransform: up, letterSpacing: theme.displayTracking,
          lineHeight: 1,
          ...(isSynth ? { textShadow: `0 0 22px ${theme.colors.primary}70` } : {}),
          ...(isCyber ? { textShadow: `0 0 8px ${theme.colors.primary}70` } : {}),
        }}>
          {isMatrix ? 'TEMPLATES' : isVoid ? <span>Templates<span style={{ color: theme.colors.primary }}>.</span></span> : 'Templates'}
        </div>
        <div style={{
          fontSize: 12, color: theme.colors.muted,
          fontFamily: theme.fonts.body, letterSpacing: isVoid ? 1.5 : 0.3,
          textTransform: isVoid ? 'uppercase' : 'none', marginBottom: 14,
        }}>{isMatrix ? '// 24 in library · 4 featured' : '24 in library · 4 featured'}</div>

        {/* Category pills */}
        <div className="no-scrollbar" style={{ display: 'flex', gap: 6, overflowX: 'auto', paddingBottom: 12 }}>
          {categories.map((c, i) => (
            <div key={c} style={{
              padding: '7px 14px',
              borderRadius: theme.chipShape === 'sharp' ? 3 : 999,
              border: `1px solid ${i === 0 ? theme.colors.primary + '60' : theme.colors.border}`,
              background: i === 0 ? theme.colors.surfaceVariant : 'transparent',
              color: i === 0 ? theme.colors.primary : theme.colors.onSurface,
              fontSize: 12, fontWeight: 600, letterSpacing: 0.5,
              textTransform: up, fontFamily: theme.fonts.body,
              whiteSpace: 'nowrap',
            }}>{isMatrix ? c.toLowerCase() : c}</div>
          ))}
        </div>
      </div>

      <div className="no-scrollbar" style={{ flex: 1, overflow: 'auto', padding: '4px 18px 40px' }}>
        {/* Featured section */}
        <div style={{
          marginTop: 4, marginBottom: 10,
          fontSize: 11, letterSpacing: 1.6, textTransform: 'uppercase',
          fontWeight: 600, color: theme.colors.primary, fontFamily: theme.fonts.body,
        }}>{isMatrix ? '# featured' : 'Featured'}</div>

        <TemplateCard theme={theme} title="Weekly planning ritual"
          subtitle="Sunday evening reset · agenda + priorities"
          tags={['Work', 'Sun']} count={8} color={theme.colors.primary} featured/>
        <TemplateCard theme={theme} title="Deep work sprint"
          subtitle="90-min focus block w/ warmup + debrief"
          tags={['Focus', 'Study']} count={5} color={theme.colors.secondary} featured/>

        {/* Library section */}
        <div style={{
          marginTop: 22, marginBottom: 10,
          fontSize: 11, letterSpacing: 1.6, textTransform: 'uppercase',
          fontWeight: 600, color: theme.colors.primary, fontFamily: theme.fonts.body,
        }}>{isMatrix ? '# my library' : 'My Library'}</div>

        <TemplateCard theme={theme} title="Morning routine"
          subtitle="Wake · hydrate · journal · plan"
          tags={['Health', 'Daily']} count={6}/>
        <TemplateCard theme={theme} title="1:1 prep checklist"
          subtitle="Talking points + follow-ups"
          tags={['Work']} count={4}/>
        <TemplateCard theme={theme} title="Pre-flight travel kit"
          subtitle="24h before departure"
          tags={['Travel']} count={12}/>
        <TemplateCard theme={theme} title="Weekly grocery run"
          subtitle="Staples + this week's recipes"
          tags={['Home']} count={18}/>
      </div>
    </div>
  );
}

window.TemplatesScreen = TemplatesScreen;
