/**
 * Four named PrismTask themes, ported from `themesets/themes.js`. Mirrors
 * the Kotlin `ThemeColors` set in
 * `app/src/main/java/com/averycorp/prismtask/ui/theme/ThemeColors.kt`.
 *
 * Scope of this file (web parity slice 2):
 *   - core colors (13 keys) — wired into the existing CSS custom-property
 *     system (background, surface, text, border, accent)
 *   - semantic state colors (success / warning / destructive / info)
 *   - the 8-slot data-visualization palette
 *
 * NOT in scope for this slice (deferred; matches THEME_SPEC.md's "not
 * just colors" warning): typography (font families), shape (radius,
 * chipShape), density, decorative personality flags (brackets / terminal /
 * editorial / sunset), glow. Those require per-component branching the
 * current web components don't yet have. Tracked in docs/WEB_PARITY_GAP_ANALYSIS.md.
 */

export type ThemeKey = 'CYBERPUNK' | 'SYNTHWAVE' | 'MATRIX' | 'VOID';

export const THEME_ORDER: ThemeKey[] = ['CYBERPUNK', 'SYNTHWAVE', 'MATRIX', 'VOID'];

/** Default when a user's theme is unset (new account OR migrating an
 *  existing light/dark + accent pick). Void is the least visually loud
 *  of the four themes and is the safest auto-pick for users who never
 *  explicitly chose one of the expressive themes. */
export const DEFAULT_THEME_KEY: ThemeKey = 'VOID';

export interface ThemeTokens {
  id: ThemeKey;
  label: string;
  tagline: string;

  // Core surface + brand
  background: string;
  surface: string;
  surfaceVariant: string;
  border: string;
  primary: string;
  secondary: string;
  onBackground: string;
  onSurface: string;
  muted: string;
  urgentAccent: string;
  urgentSurface: string;
  tagSurface: string;
  tagText: string;

  // Semantic state
  successColor: string;
  warningColor: string;
  destructiveColor: string;
  infoColor: string;

  // Eisenhower quadrants
  quadrantQ1: string;
  quadrantQ2: string;
  quadrantQ3: string;
  quadrantQ4: string;

  // 8-slot data-viz palette
  dataVisualizationPalette: [
    string,
    string,
    string,
    string,
    string,
    string,
    string,
    string,
  ];

  // Typography — per-theme fonts ported from `themesets/themes.js`.
  // Body is the default for all app text; display is used for hero
  // headlines (h1/h2) via the `.prism-display` utility class; mono
  // is the code / timestamp font.
  fontBody: string;
  fontDisplay: string;
  fontMono: string;
  displayUpper: boolean;
  displayTracking: string;
}

export const THEMES: Record<ThemeKey, ThemeTokens> = {
  CYBERPUNK: {
    id: 'CYBERPUNK',
    label: 'Cyberpunk',
    tagline: 'Neon terminal · cyan / magenta',
    background: '#0A0A0F',
    surface: '#0D0D18',
    surfaceVariant: '#111120',
    border: 'rgba(0, 245, 255, 0.10)',
    primary: '#00F5FF',
    secondary: '#FF00AA',
    onBackground: '#E0F8FF',
    onSurface: '#A0CCD4',
    muted: '#4A8A9A',
    urgentAccent: '#FF00AA',
    urgentSurface: '#1A0010',
    tagSurface: '#001A1A',
    tagText: '#00F5FF',
    successColor: '#00FFB3',
    warningColor: '#FFD100',
    destructiveColor: '#FF2E6C',
    infoColor: '#66E0FF',
    quadrantQ1: '#FF2E6C',
    quadrantQ2: '#00F5FF',
    quadrantQ3: '#FFD100',
    quadrantQ4: '#4A8A9A',
    dataVisualizationPalette: [
      '#00F5FF',
      '#FF00AA',
      '#FFD100',
      '#00FFB3',
      '#B84DFF',
      '#FF7A00',
      '#66E0FF',
      '#FF2E6C',
    ],
    fontBody: '"Chakra Petch", system-ui, sans-serif',
    fontDisplay: '"Audiowide", "Chakra Petch", sans-serif',
    fontMono: '"Chakra Petch", ui-monospace, monospace',
    displayUpper: true,
    displayTracking: '0.06em',
  },
  SYNTHWAVE: {
    id: 'SYNTHWAVE',
    label: 'Synthwave',
    tagline: 'Neon sunset · pink / purple',
    background: '#0D0717',
    surface: '#130820',
    surfaceVariant: '#1A0F2E',
    border: 'rgba(110, 63, 255, 0.18)',
    primary: '#FF2D87',
    secondary: '#6E3FFF',
    onBackground: '#F0D0FF',
    onSurface: '#B080D0',
    muted: '#5E3A7A',
    urgentAccent: '#FF2D87',
    urgentSurface: '#1F0015',
    tagSurface: '#12082A',
    tagText: '#6E3FFF',
    successColor: '#3EE8B8',
    warningColor: '#FFB347',
    destructiveColor: '#FF3D5A',
    infoColor: '#8ED1FF',
    quadrantQ1: '#FF3D5A',
    quadrantQ2: '#6E3FFF',
    quadrantQ3: '#FFB347',
    quadrantQ4: '#5E3A7A',
    dataVisualizationPalette: [
      '#FF2D87',
      '#6E3FFF',
      '#FFB347',
      '#3EE8B8',
      '#FF6AC8',
      '#8ED1FF',
      '#FF3D5A',
      '#B080D0',
    ],
    fontBody: '"Rajdhani", system-ui, sans-serif',
    fontDisplay: '"Monoton", "Rajdhani", sans-serif',
    fontMono: '"Rajdhani", ui-monospace, monospace',
    displayUpper: true,
    displayTracking: '0.08em',
  },
  MATRIX: {
    id: 'MATRIX',
    label: 'Matrix',
    tagline: 'Terminal green · monospace CRT',
    background: '#010D03',
    surface: '#010F04',
    surfaceVariant: '#021206',
    border: 'rgba(0, 255, 65, 0.14)',
    primary: '#00FF41',
    secondary: '#AAFF00',
    onBackground: '#B0FFB8',
    onSurface: '#70CC80',
    muted: '#1A5E25',
    urgentAccent: '#AAFF00',
    urgentSurface: '#0A1400',
    tagSurface: '#001A06',
    tagText: '#00FF41',
    successColor: '#00FF41',
    warningColor: '#E6FF3C',
    destructiveColor: '#FF3C3C',
    infoColor: '#7FFFB2',
    quadrantQ1: '#FF3C3C',
    quadrantQ2: '#00FF41',
    quadrantQ3: '#AAFF00',
    quadrantQ4: '#1A5E25',
    dataVisualizationPalette: [
      '#00FF41',
      '#AAFF00',
      '#00B82D',
      '#7FFFB2',
      '#E6FF3C',
      '#008F24',
      '#00FFAA',
      '#FF3C3C',
    ],
    fontBody: '"Share Tech Mono", ui-monospace, monospace',
    fontDisplay: '"VT323", "Share Tech Mono", monospace',
    fontMono: '"Share Tech Mono", ui-monospace, monospace',
    displayUpper: false,
    displayTracking: '0.02em',
  },
  VOID: {
    id: 'VOID',
    label: 'Void',
    tagline: 'Editorial minimal · serif + sans',
    background: '#111113',
    surface: '#161618',
    surfaceVariant: '#1E1E22',
    border: 'rgba(46, 46, 52, 0.5)',
    primary: '#C8B8FF',
    secondary: '#8888CC',
    onBackground: '#DCDCE4',
    onSurface: '#A0A0AB',
    muted: '#3E3E4A',
    urgentAccent: '#E8A0A0',
    urgentSurface: '#261616',
    tagSurface: '#1A1A26',
    tagText: '#8888CC',
    successColor: '#8FB896',
    warningColor: '#D4A87A',
    destructiveColor: '#C68888',
    infoColor: '#8A9CC0',
    quadrantQ1: '#C68888',
    quadrantQ2: '#C8B8FF',
    quadrantQ3: '#D4A87A',
    quadrantQ4: '#3E3E4A',
    dataVisualizationPalette: [
      '#C8B8FF',
      '#8FB896',
      '#D4A87A',
      '#8A9CC0',
      '#C68888',
      '#B8A4D4',
      '#7FA89E',
      '#A89876',
    ],
    fontBody: '"Space Grotesk", system-ui, sans-serif',
    fontDisplay: '"Fraunces", "Space Grotesk", serif',
    fontMono: '"Space Grotesk", ui-monospace, monospace',
    displayUpper: false,
    displayTracking: '-0.02em',
  },
};

/**
 * Apply a theme's tokens to the document via CSS custom properties and
 * a `data-theme` attribute. Keeps the legacy `.dark` class on for
 * Tailwind's `dark:` utilities because all four themes are dark-first
 * (matches Android's model — the four themes are expressive and do not
 * have light variants).
 */
export function applyThemeToDocument(themeKey: ThemeKey): void {
  if (typeof document === 'undefined') return;
  const root = document.documentElement;
  const tokens = THEMES[themeKey];

  root.setAttribute('data-theme', themeKey);
  root.classList.add('dark');

  // Map the new 20+ tokens onto the existing `--color-*` custom-property
  // names so every component keeps rendering without a sweep.
  root.style.setProperty('--color-bg-primary', tokens.background);
  root.style.setProperty('--color-bg-secondary', tokens.surfaceVariant);
  root.style.setProperty('--color-bg-card', tokens.surface);
  root.style.setProperty('--color-text-primary', tokens.onBackground);
  root.style.setProperty('--color-text-secondary', tokens.onSurface);
  root.style.setProperty('--color-border', tokens.border);
  root.style.setProperty('--color-accent', tokens.primary);
  root.style.setProperty('--color-priority-urgent', tokens.destructiveColor);
  root.style.setProperty('--color-priority-high', tokens.warningColor);
  root.style.setProperty('--color-priority-medium', tokens.infoColor);
  root.style.setProperty('--color-priority-low', tokens.muted);
  root.style.setProperty('--color-priority-none', tokens.muted);

  // Additional tokens namespaced under `--prism-*` so they don't collide
  // with the legacy set. Components gain access to them gradually as
  // follow-up polish ports more Android surfaces across.
  root.style.setProperty('--prism-surface', tokens.surface);
  root.style.setProperty('--prism-surface-variant', tokens.surfaceVariant);
  root.style.setProperty('--prism-secondary', tokens.secondary);
  root.style.setProperty('--prism-muted', tokens.muted);
  root.style.setProperty('--prism-urgent-accent', tokens.urgentAccent);
  root.style.setProperty('--prism-urgent-surface', tokens.urgentSurface);
  root.style.setProperty('--prism-tag-surface', tokens.tagSurface);
  root.style.setProperty('--prism-tag-text', tokens.tagText);
  root.style.setProperty('--prism-success', tokens.successColor);
  root.style.setProperty('--prism-warning', tokens.warningColor);
  root.style.setProperty('--prism-destructive', tokens.destructiveColor);
  root.style.setProperty('--prism-info', tokens.infoColor);
  root.style.setProperty('--prism-quadrant-q1', tokens.quadrantQ1);
  root.style.setProperty('--prism-quadrant-q2', tokens.quadrantQ2);
  root.style.setProperty('--prism-quadrant-q3', tokens.quadrantQ3);
  root.style.setProperty('--prism-quadrant-q4', tokens.quadrantQ4);
  tokens.dataVisualizationPalette.forEach((color, idx) => {
    root.style.setProperty(`--prism-dataviz-${idx}`, color);
  });

  // Typography — per-theme font stacks. Body drives the default app
  // font via index.css; display is picked up by `.prism-display` for
  // hero headlines. `--prism-display-upper` + `--prism-display-tracking`
  // drive CSS variable-based text transformation / letter-spacing.
  root.style.setProperty('--prism-font-body', tokens.fontBody);
  root.style.setProperty('--prism-font-display', tokens.fontDisplay);
  root.style.setProperty('--prism-font-mono', tokens.fontMono);
  root.style.setProperty(
    '--prism-display-upper',
    tokens.displayUpper ? 'uppercase' : 'none',
  );
  root.style.setProperty('--prism-display-tracking', tokens.displayTracking);
}

/**
 * Map a legacy stored accent-color hex (from the old `prismtask_accent_color`
 * localStorage key) to one of the four named themes. This runs once per
 * device on first load after upgrade so returning users land on a
 * reasonable theme without being prompted. All options map to VOID
 * unless the stored hex falls into one of the other themes' primary
 * color families; the matching is coarse on purpose — this is a
 * migration, not a picker.
 */
export function migrateLegacyAccentToThemeKey(
  legacyAccent: string | null,
): ThemeKey {
  if (!legacyAccent) return DEFAULT_THEME_KEY;
  const hex = legacyAccent.toLowerCase();
  // Cyan / teal / blue family → Cyberpunk
  if (/^#(0|1|2|3)[0-9a-f]{1}(f|e)[0-9a-f]/.test(hex) || hex === '#0ea5e9' || hex === '#14b8a6') {
    return 'CYBERPUNK';
  }
  // Green / lime family → Matrix
  if (hex === '#22c55e' || hex === '#84cc16' || hex === '#16a34a') {
    return 'MATRIX';
  }
  // Pink / magenta / rose / orange family → Synthwave
  if (
    hex === '#ec4899' ||
    hex === '#f43f5e' ||
    hex === '#f97316' ||
    hex === '#ef4444' ||
    hex === '#eab308'
  ) {
    return 'SYNTHWAVE';
  }
  // Everything else (indigo default, blue, purple, yellow) → Void.
  return DEFAULT_THEME_KEY;
}
