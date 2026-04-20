// SyncScreen — Settings → Sync / Account subpage.

function SyncScreen({ theme }) {
  const isMatrix = theme.terminal;
  const isVoid = theme.editorial;
  const isCyber = theme.brackets;

  const DevicePill = ({ name, os, current }) => (
    <div style={{
      display: 'flex', alignItems: 'center', gap: 10,
      padding: '12px 14px',
      background: theme.colors.surface,
      border: `1px solid ${current ? theme.colors.primary + '88' : theme.colors.border}`,
      borderRadius: theme.cardRadius,
      marginBottom: 8,
      ...(isCyber && current ? { borderLeft: `3px solid ${theme.colors.primary}` } : {}),
    }}>
      <div style={{
        width: 34, height: 34, flexShrink: 0,
        borderRadius: theme.chipShape === 'sharp' ? 4 : 8,
        background: `${theme.colors.primary}1F`,
        border: `1px solid ${theme.colors.primary}55`,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        color: theme.colors.primary,
        fontFamily: theme.fonts.display, fontWeight: 700, fontSize: 15,
      }}>{name[0]}</div>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{
          fontSize: 14, fontWeight: 600,
          color: theme.colors.onBackground,
          fontFamily: isVoid ? theme.fonts.display : theme.fonts.body,
        }}>{name}</div>
        <div style={{
          marginTop: 2, fontSize: 11, color: theme.colors.muted,
          fontFamily: theme.fonts.body, letterSpacing: 0.3,
        }}>{os}</div>
      </div>
      {current && (
        <span style={{
          fontSize: 9, letterSpacing: 1.4, fontWeight: 700,
          color: theme.colors.primary, textTransform: 'uppercase',
          padding: '3px 8px',
          whiteSpace: 'nowrap',
          border: `1px solid ${theme.colors.primary}60`,
          borderRadius: theme.chipShape === 'sharp' ? 0 : 4,
          fontFamily: theme.fonts.body,
        }}>{isMatrix ? 'this' : 'This device'}</span>
      )}
    </div>
  );

  return (
    <div style={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <SubHeader theme={theme} title="Sync & Account"
        subtitle="Cross-device sync, backup, and sign-in."/>

      <div className="no-scrollbar" style={{ flex: 1, overflow: 'auto', padding: '4px 18px 40px' }}>
        {/* Account card */}
        <div style={{
          display: 'flex', alignItems: 'center', gap: 14,
          padding: isVoid ? '16px 18px' : '16px',
          background: `linear-gradient(135deg, ${theme.colors.primary}14, ${theme.colors.secondary}14)`,
          border: `1px solid ${theme.colors.primary}40`,
          borderRadius: theme.cardRadius,
          marginBottom: 14,
          ...(theme.glow !== 'none' ? { boxShadow: `0 0 18px ${theme.colors.primary}30` } : {}),
        }}>
          <div style={{
            width: 52, height: 52,
            borderRadius: theme.chipShape === 'sharp' ? 8 : 26,
            background: `linear-gradient(135deg, ${theme.colors.primary}, ${theme.colors.secondary})`,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            color: theme.colors.background, fontFamily: theme.fonts.display,
            fontSize: 22, fontWeight: 700,
          }}>AR</div>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{
              fontSize: isVoid ? 17 : 16, fontWeight: 600,
              color: theme.colors.onBackground,
              fontFamily: isVoid ? theme.fonts.display : theme.fonts.body,
            }}>Alex Rivera</div>
            <div style={{
              fontSize: 11, color: theme.colors.muted,
              fontFamily: theme.fonts.body, letterSpacing: 0.3,
            }}>alex@averycorp.com</div>
            <div style={{
              marginTop: 5, fontSize: 10, letterSpacing: 1.4,
              fontWeight: 700, color: theme.colors.primary,
              textTransform: 'uppercase', fontFamily: theme.fonts.body,
            }}>{isMatrix ? '★ pro · annual' : '★ Pro · Annual'}</div>
          </div>
        </div>

        <SubSectionLabel theme={theme} label="Sync status"/>
        <div style={{
          padding: '14px 16px',
          background: theme.colors.surface,
          border: `1px solid ${theme.colors.border}`,
          borderRadius: theme.cardRadius,
          marginBottom: 14,
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 10 }}>
            <div style={{
              width: 10, height: 10, borderRadius: '50%',
              background: '#00E5A0',
              boxShadow: theme.glow !== 'none' ? `0 0 8px #00E5A0` : 'none',
            }}/>
            <span style={{
              fontSize: 13, fontWeight: 600,
              color: theme.colors.onBackground, fontFamily: theme.fonts.body,
            }}>{isMatrix ? 'synced · 2m ago' : 'Synced · 2 min ago'}</span>
            <span style={{ flex: 1 }}/>
            <span style={{
              fontSize: 10, letterSpacing: 1.2, textTransform: 'uppercase',
              color: theme.colors.primary, fontFamily: theme.fonts.body,
            }}>{isMatrix ? 'now' : 'Sync now'}</span>
          </div>
          <div style={{
            fontSize: 11.5, color: theme.colors.muted,
            fontFamily: theme.fonts.body, lineHeight: 1.5,
          }}>
            {isMatrix
              ? '// 247 tasks · 38 habits · 12 templates · 3 devices'
              : '247 tasks · 38 habits · 12 templates · 3 devices'}
          </div>
        </div>

        <SubSectionLabel theme={theme} label="What to sync"/>
        <div style={{
          padding: '6px 14px 10px',
          background: theme.colors.surface,
          border: `1px solid ${theme.colors.border}`,
          borderRadius: theme.cardRadius,
          marginBottom: 14,
        }}>
          {[
            { label: 'Tasks & projects', on: true },
            { label: 'Daily habits', on: true },
            { label: 'Templates', on: true },
            { label: 'Focus session history', on: true },
            { label: 'Appearance preferences', on: false },
          ].map((r, i, arr) => (
            <div key={r.label} style={{
              display: 'flex', alignItems: 'center',
              padding: '10px 0',
              borderBottom: i === arr.length - 1 ? 'none' : `1px solid ${theme.colors.border}`,
            }}>
              <span style={{
                flex: 1, fontSize: 13.5,
                color: theme.colors.onBackground, fontFamily: theme.fonts.body,
              }}>{isMatrix ? r.label.toLowerCase() : r.label}</span>
              <window.PTToggle theme={theme} on={r.on}/>
            </div>
          ))}
        </div>

        <SubSectionLabel theme={theme} label="Devices"/>
        <DevicePill name="Pixel 8" os={isMatrix ? 'android 15 · /data' : 'Android 15 · this device'} current/>
        <DevicePill name="iPad Air" os="iPadOS 18 · last seen 3h ago"/>
        <DevicePill name="MacBook Pro" os="macOS 15 · last seen yesterday"/>

        <SubSectionLabel theme={theme} label="Danger zone"/>
        <div style={{
          padding: '12px 14px',
          background: `${theme.colors.urgentAccent}10`,
          border: `1px solid ${theme.colors.urgentAccent}55`,
          borderRadius: theme.cardRadius,
          ...(isCyber ? { borderLeft: `3px solid ${theme.colors.urgentAccent}` } : {}),
        }}>
          <div style={{
            fontSize: 13, fontWeight: 600,
            color: theme.colors.urgentAccent, fontFamily: theme.fonts.body, marginBottom: 4,
          }}>{isMatrix ? 'sign out · reset sync · delete account' : 'Sign out · Reset sync · Delete account'}</div>
          <div style={{
            fontSize: 11, color: theme.colors.muted,
            fontFamily: theme.fonts.body, lineHeight: 1.5,
          }}>
            {isMatrix
              ? '// destructive. a backup is exported first when available.'
              : 'Destructive actions. A backup is exported first where possible.'}
          </div>
        </div>
      </div>
    </div>
  );
}

window.SyncScreen = SyncScreen;
