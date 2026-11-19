/**
 * Cores do sistema (Configurações → Cores do sistema): barra superior/títulos, menu lateral, destaque, botões e fundo.
 * Aplicadas por variáveis CSS sobre as classes de cor da interface; laudos e relatórios (.spda-sheet) não mudam.
 * Preferência do aparelho (localStorage).
 */

export interface ThemeColors {
  /** barra superior, títulos e ícones das telas */
  header: string;
  /** menu lateral */
  menu: string;
  /** item ativo do menu, logotipo e destaques */
  accent: string;
  /** botões principais */
  primary: string;
  /** fundo das telas */
  background: string;
}

export const THEME_PRESETS: Array<{ id: string; label: string; colors: ThemeColors }> = [
  { id: 'padrao', label: 'Padrão JVM', colors: { header: '#0A2540', menu: '#0f172a', accent: '#f59e0b', primary: '#2563eb', background: '#f1f5f9' } },
  { id: 'verde', label: 'Verde', colors: { header: '#064e3b', menu: '#022c22', accent: '#34d399', primary: '#059669', background: '#f0fdf4' } },
  { id: 'vinho', label: 'Vinho', colors: { header: '#4c0519', menu: '#2a0a12', accent: '#fb7185', primary: '#be123c', background: '#fff1f2' } },
  { id: 'grafite', label: 'Grafite e amarelo', colors: { header: '#1f2937', menu: '#111827', accent: '#facc15', primary: '#374151', background: '#f3f4f6' } },
  { id: 'laranja', label: 'Laranja', colors: { header: '#9a3412', menu: '#1c1917', accent: '#fb923c', primary: '#ea580c', background: '#fafaf9' } },
  { id: 'claro', label: 'Azul com menu claro', colors: { header: '#1d4ed8', menu: '#ffffff', accent: '#38bdf8', primary: '#1d4ed8', background: '#f8fafc' } }
];
export const DEFAULT_THEME = THEME_PRESETS[0].colors;
const KEY = 'jvm_spda_theme';
const HEX = /^#[0-9a-f]{6}$/i;

/** Luminância relativa (WCAG) de uma cor #rrggbb. */
export function luminance(hex: string): number {
  const c = [1, 3, 5].map(i => parseInt(hex.slice(i, i + 2), 16) / 255).map(v => (v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4));
  return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
}

/** Cor de texto legível sobre o fundo: branco ou grafite, o de maior contraste (WCAG). */
export function textOn(bg: string): string {
  const l = luminance(bg);
  const withWhite = 1.05 / (l + 0.05);
  const withDark = (l + 0.05) / (luminance('#0f172a') + 0.05);
  return withDark > withWhite ? '#0f172a' : '#ffffff';
}

/** Mistura duas cores (t de 0 a 1). */
export function mix(a: string, b: string, t: number): string {
  const ch = (h: string, i: number) => parseInt(h.slice(i, i + 2), 16);
  return '#' + [1, 3, 5].map(i => Math.round(ch(a, i) * (1 - t) + ch(b, i) * t).toString(16).padStart(2, '0')).join('');
}

export function sanitizeTheme(t: Partial<ThemeColors> | null | undefined): ThemeColors {
  const out = { ...DEFAULT_THEME };
  (Object.keys(out) as Array<keyof ThemeColors>).forEach(k => { const v = t?.[k]; if (typeof v === 'string' && HEX.test(v)) out[k] = v.toLowerCase(); });
  return out;
}

export function readTheme(): ThemeColors {
  try { return sanitizeTheme(JSON.parse(localStorage.getItem(KEY) || 'null')); } catch { return { ...DEFAULT_THEME }; }
}

/** Variáveis CSS do tema (inclui as cores de texto calculadas para manter a leitura). */
export function themeVars(t: ThemeColors): Record<string, string> {
  const menuLight = textOn(t.menu) !== '#ffffff';
  return {
    '--t-header': t.header,
    '--t-header-text': textOn(t.header),
    '--t-menu': t.menu,
    '--t-menu-text': menuLight ? '#334155' : '#cbd5e1',
    '--t-menu-strong': menuLight ? '#0f172a' : '#e2e8f0',
    '--t-menu-muted': menuLight ? '#64748b' : '#64748b',
    '--t-menu-hover': mix(t.menu, menuLight ? '#000000' : '#ffffff', 0.08),
    '--t-menu-line': mix(t.menu, menuLight ? '#000000' : '#ffffff', 0.12),
    '--t-accent': t.accent,
    '--t-accent-text': textOn(t.accent),
    '--t-accent-on-menu': (textOn(t.accent) !== '#ffffff') === menuLight ? t.header : t.accent,
    '--t-primary': t.primary,
    '--t-primary-text': textOn(t.primary),
    '--t-bg': t.background
  };
}

const NS = ':not(.spda-sheet *)';
/** Regras que trocam as cores fixas da interface pelas do tema (não afetam .spda-sheet = laudos/relatórios). */
export const THEME_CSS = `
html.jvm-theme .bg-\\[\\#0A2540\\]${NS}{background-color:var(--t-header)!important;color:var(--t-header-text)!important}
html.jvm-theme .text-\\[\\#0A2540\\]${NS}{color:var(--t-header)!important}
html.jvm-theme header.bg-\\[\\#0A2540\\] .text-slate-300{color:var(--t-header-text)!important;opacity:.8}
html.jvm-theme aside.bg-slate-900{background-color:var(--t-menu)!important}
html.jvm-theme aside .border-slate-800,html.jvm-theme aside.border-slate-800{border-color:var(--t-menu-line)!important}
html.jvm-theme aside .text-slate-300{color:var(--t-menu-text)!important}
html.jvm-theme aside .text-slate-200,html.jvm-theme aside .text-white{color:var(--t-menu-strong)!important}
html.jvm-theme aside .text-slate-500{color:var(--t-menu-muted)!important}
html.jvm-theme aside .hover\\:bg-slate-800:hover{background-color:var(--t-menu-hover)!important}
html.jvm-theme aside .hover\\:text-white:hover{color:var(--t-menu-strong)!important}
html.jvm-theme aside .text-amber-400{color:var(--t-accent-on-menu)!important}
html.jvm-theme .bg-amber-500${NS}{background-color:var(--t-accent)!important;color:var(--t-accent-text)!important}
html.jvm-theme .bg-blue-600${NS}{background-color:var(--t-primary)!important;color:var(--t-primary-text)!important}
html.jvm-theme .hover\\:bg-blue-700${NS}:hover,html.jvm-theme .hover\\:bg-blue-500${NS}:hover{background-color:var(--t-primary)!important;filter:brightness(.9)}
html.jvm-theme .bg-slate-100.min-h-screen{background-color:var(--t-bg)!important}
`;

/** Aplica o tema na página (variáveis + regras). */
export function applyTheme(t: ThemeColors = readTheme()): void {
  if (typeof document === 'undefined') return;
  const root = document.documentElement;
  root.classList.add('jvm-theme');
  Object.entries(themeVars(t)).forEach(([k, v]) => root.style.setProperty(k, v));
  if (!document.getElementById('jvm-theme-css')) {
    const st = document.createElement('style');
    st.id = 'jvm-theme-css';
    st.textContent = THEME_CSS;
    document.head.appendChild(st);
  }
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', t.header);
}

export function saveTheme(t: ThemeColors): void {
  const clean = sanitizeTheme(t);
  try { localStorage.setItem(KEY, JSON.stringify(clean)); } catch { /* sem armazenamento */ }
  applyTheme(clean);
}

export function resetTheme(): void {
  try { localStorage.removeItem(KEY); } catch { /* sem armazenamento */ }
  applyTheme({ ...DEFAULT_THEME });
}
