// A11yScreen — Settings → Accessibility subpage.

function A11yScreen({ theme }) {
  const isMatrix = theme.terminal;
  const isVoid = theme.editorial;

  const Row = ({ icon, iconColor, label, desc, on, first, last, control }) => (
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
      <div style={{
        width: 30, height: 30, flexShrink: 0, marginTop: 2,
        borderRadius: theme.chipShape === 'sharp' ? 4 : 8,
        background: `${iconColor}1F`, border: `1px solid ${iconColor}55`,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
      }}>
        <Icon name={icon} size={16} color={iconColor}/>
      </div>
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
      <SubHeader theme={theme} title="Accessibility"
        subtitle="Motion, contrast, and touch target adjustments."/>

      <div className="no-scrollbar" style={{ flex: 1, overflow: 'auto', padding: '4px 18px 40px' }}>
        <SubSectionLabel theme={theme} label="Motion"/>
        <Row icon="music" iconColor={theme.colors.primary}
          label="Reduce motion" on first
          desc="Disable progress ring sweep, sunset pulse, and sheet transitions."/>
        <Row icon="timer" iconColor={theme.colors.secondary}
          label="Slower transitions" last
          desc="Scale animation durations by 1.5× for easier tracking."/>

        <SubSectionLabel theme={theme} label="Vision"/>
        <Row icon="sun" iconColor={theme.colors.primary}
          label="High contrast" on first
          desc="Increase border contrast and disable glow effects."/>
        <Row icon="grad" iconColor={theme.colors.secondary}
          label="Bold text"
          desc="Use semibold throughout body copy."/>
        <Row icon="moon" iconColor="#00E5A0"
          label="Dim hero glow" last
          desc="Cap the intensity of bloom effects on primary elements."/>

        <SubSectionLabel theme={theme} label="Input"/>
        <Row icon="plus" iconColor={theme.colors.primary}
          label="Large touch targets" on first
          desc="Expand interactive areas to 48dp minimum."/>
        <Row icon="check" iconColor={theme.colors.secondary}
          label="Haptic confirmation" on
          desc="Tiny buzz when checking off tasks or habits."/>
        <Row icon="home" iconColor={theme.colors.urgentAccent}
          label="Confirm swipe-to-delete" last
          desc="Prevent accidental deletion with a second tap."/>

        <SubSectionLabel theme={theme} label="Screen reader"/>
        <div style={{
          padding: '12px 14px',
          background: theme.colors.surface,
          border: `1px solid ${theme.colors.border}`,
          borderRadius: theme.cardRadius,
          fontSize: 12, lineHeight: 1.5,
          color: theme.colors.onSurface, fontFamily: theme.fonts.body,
        }}>
          {isMatrix
            ? '// talkback verbosity: [detailed]. swipe right for actions.'
            : 'TalkBack verbosity: Detailed. Swipe right on any task for quick actions (complete, snooze, reschedule).'}
        </div>
      </div>
    </div>
  );
}

// Reused toggle matching the main Settings style
(function(){
  function PTToggle({ theme, on, tone }) {
    const isMatrix = theme.terminal;
    const c = tone || theme.colors.primary;
    if (isMatrix) {
      return (
        <span style={{
          fontFamily: theme.fonts.body, fontSize: 12, fontWeight: 700, letterSpacing: 1.4,
          color: on ? c : theme.colors.muted,
          border: `1px solid ${on ? c : theme.colors.border}`,
          background: on ? `${c}18` : 'transparent',
          padding: '3px 8px',
        }}>{on ? '[ON]' : '[OFF]'}</span>
      );
    }
    return (
      <div style={{
        width: 40, height: 22, borderRadius: 11,
        background: on ? c : theme.colors.surfaceVariant,
        border: `1px solid ${on ? c : theme.colors.border}`,
        position: 'relative',
        ...(on && theme.glow !== 'none' ? { boxShadow: `0 0 10px ${c}80` } : {}),
      }}>
        <div style={{
          position: 'absolute', top: 2, left: on ? 20 : 2,
          width: 16, height: 16, borderRadius: 8,
          background: on ? theme.colors.background : theme.colors.onSurface,
        }}/>
      </div>
    );
  }
  window.PTToggle = PTToggle;
})();

window.A11yScreen = A11yScreen;
