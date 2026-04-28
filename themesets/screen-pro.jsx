// ProScreen — upgrade / billing paywall.

function ProScreen({ theme }) {
  const up = theme.displayUpper ? 'uppercase' : 'none';
  const isMatrix = theme.terminal;
  const isVoid = theme.editorial;
  const isCyber = theme.brackets;
  const isSynth = theme.sunset;

  const Feature = ({ label, pro }) => (
    <div style={{
      display: 'flex', alignItems: 'center', gap: 10,
      padding: '10px 0',
      borderBottom: `1px solid ${theme.colors.border}`,
      fontSize: 13, fontFamily: theme.fonts.body,
      color: theme.colors.onBackground,
    }}>
      <div style={{
        width: 18, height: 18, flexShrink: 0,
        borderRadius: theme.chipShape === 'sharp' ? 3 : '50%',
        background: pro ? theme.colors.primary : 'transparent',
        border: `1.5px solid ${pro ? theme.colors.primary : theme.colors.muted}`,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
      }}>
        {pro && <Icon name="check" size={10} color={theme.colors.background} strokeWidth={3}/>}
      </div>
      <span style={{ flex: 1 }}>{label}</span>
      {pro && (
        <span style={{
          fontSize: 9, letterSpacing: 1.4, fontWeight: 700,
          color: theme.colors.primary, textTransform: 'uppercase',
        }}>{isMatrix ? 'pro' : 'Pro'}</span>
      )}
    </div>
  );

  const PlanCard = ({ title, price, period, savings, highlight }) => (
    <div style={{
      flex: 1, padding: isVoid ? '18px 16px' : '16px 14px',
      background: highlight ? `${theme.colors.primary}14` : theme.colors.surface,
      border: `1px solid ${highlight ? theme.colors.primary : theme.colors.border}`,
      borderRadius: theme.cardRadius,
      position: 'relative',
      ...(highlight && theme.glow !== 'none' ? { boxShadow: `0 0 20px ${theme.colors.primary}40` } : {}),
      ...(isCyber && highlight ? { borderLeft: `3px solid ${theme.colors.primary}` } : {}),
    }}>
      {savings && (
        <div style={{
          position: 'absolute', top: -10, right: 10,
          fontSize: 9, letterSpacing: 1.4, fontWeight: 700,
          padding: '3px 8px', textTransform: 'uppercase',
          color: theme.colors.background, background: theme.colors.primary,
          borderRadius: theme.chipShape === 'sharp' ? 2 : 4,
          fontFamily: theme.fonts.body,
        }}>{savings}</div>
      )}
      <div style={{
        fontSize: 10, letterSpacing: 1.8, textTransform: 'uppercase',
        color: highlight ? theme.colors.primary : theme.colors.muted,
        fontFamily: theme.fonts.body, fontWeight: 600,
      }}>{isMatrix ? title.toLowerCase() : title}</div>
      <div style={{
        marginTop: 6, fontFamily: theme.fonts.display,
        fontSize: isVoid ? 32 : 26,
        fontWeight: isVoid ? 500 : 700,
        color: theme.colors.onBackground,
        letterSpacing: theme.displayTracking,
        lineHeight: 1,
      }}>{price}</div>
      <div style={{
        marginTop: 4, fontSize: 11, color: theme.colors.muted,
        letterSpacing: 0.4, fontFamily: theme.fonts.body,
      }}>{period}</div>
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
        }}>{isMatrix ? '// restore purchase' : 'Restore'}</div>
      </div>

      <div className="no-scrollbar" style={{ flex: 1, overflow: 'auto', padding: '20px 20px 100px' }}>
        {/* Pro badge */}
        <div style={{ display: 'flex', justifyContent: 'center', marginBottom: 18 }}>
          <div style={{
            padding: '8px 18px',
            borderRadius: theme.chipShape === 'sharp' ? 3 : 999,
            background: `${theme.colors.primary}1F`,
            border: `1px solid ${theme.colors.primary}70`,
            color: theme.colors.primary,
            fontSize: 11, fontWeight: 700, letterSpacing: 2,
            textTransform: 'uppercase', fontFamily: theme.fonts.body,
            whiteSpace: 'nowrap',
            ...(theme.glow !== 'none' ? { boxShadow: `0 0 20px ${theme.colors.primary}70` } : {}),
          }}>{isMatrix ? '★ prismtask pro' : '★ PrismTask Pro'}</div>
        </div>

        <div style={{
          fontFamily: theme.fonts.display,
          fontSize: isVoid ? 36 : 30, fontWeight: isVoid ? 500 : 700,
          color: theme.colors.onBackground,
          textTransform: up, letterSpacing: theme.displayTracking,
          lineHeight: 1.08, textAlign: 'center', marginBottom: 10,
          ...(isSynth ? { textShadow: `0 0 20px ${theme.colors.primary}70` } : {}),
          ...(isCyber ? { textShadow: `0 0 8px ${theme.colors.primary}80` } : {}),
        }}>
          {isMatrix ? 'UNLOCK EVERYTHING' : isVoid
            ? <>Unlock the <em style={{ color: theme.colors.primary }}>full prism</em></>
            : 'Unlock the full Prism'}
        </div>
        <div style={{
          fontSize: 13, lineHeight: 1.5, color: theme.colors.onSurface,
          textAlign: 'center', marginBottom: 20,
          fontFamily: theme.fonts.body,
        }}>
          {isMatrix
            ? '// advanced recurrence, unlimited templates, cross-device sync, all 4 themes.'
            : 'Advanced recurrence, unlimited templates, cross-device sync, and all 4 themes.'}
        </div>

        {/* Plans */}
        <div style={{ display: 'flex', gap: 10, marginBottom: 20, marginTop: 8 }}>
          <PlanCard title="Monthly" price="$7.99" period={isMatrix ? '/ mo · billed monthly' : 'per month'}/>
          <PlanCard title="Yearly" price="$59.99" period={isMatrix ? '/ yr · $5/mo · 7-day trial' : 'per year · $5/mo · 7-day trial'} savings={isMatrix ? '-37%' : 'Save 37%'} highlight/>
        </div>

        <div style={{
          fontSize: 11, letterSpacing: 1.6, textTransform: 'uppercase',
          fontWeight: 600, color: theme.colors.primary,
          fontFamily: theme.fonts.body, marginBottom: 4,
        }}>{isMatrix ? '# what you get' : "What you get"}</div>

        <Feature label="All 4 premium themes (Cyberpunk, Synthwave, Matrix, Void)" pro/>
        <Feature label="Unlimited templates & custom recurrence rules" pro/>
        <Feature label="Cross-device sync via iCloud / Google" pro/>
        <Feature label="Shake-to-report bug capture" pro/>
        <Feature label="Home-screen widgets + quick-add tile" pro/>
        <Feature label="Export to CSV / Markdown / iCal" pro/>
        <Feature label="Priority support + early access features" pro/>

        <div style={{
          marginTop: 18, textAlign: 'center',
          fontSize: 10, letterSpacing: 1.2,
          color: theme.colors.muted, fontFamily: theme.fonts.body, lineHeight: 1.6,
        }}>
          {isMatrix
            ? '// auto-renews. cancel anytime in app store settings.'
            : 'Subscription auto-renews. Cancel anytime in App Store settings.'}
        </div>
      </div>

      {/* CTA bar */}
      <div style={{
        position: 'absolute', left: 0, right: 0, bottom: 0,
        padding: '12px 18px 22px',
        background: theme.colors.background,
        borderTop: `1px solid ${theme.colors.border}`,
      }}>
        <div style={{
          padding: '14px 16px', textAlign: 'center',
          background: theme.colors.primary,
          color: theme.colors.background,
          borderRadius: theme.chipShape === 'sharp' ? (isMatrix ? 0 : 6) : 12,
          fontSize: 14, fontWeight: 700, letterSpacing: 1.4,
          textTransform: up, fontFamily: theme.fonts.body,
          boxShadow: theme.glow === 'none' ? 'none' : `0 0 24px ${theme.colors.primary}90`,
        }}>{isMatrix ? 'start 7-day trial' : 'Start 7-Day Free Trial'}</div>
      </div>
    </div>
  );
}

window.ProScreen = ProScreen;
