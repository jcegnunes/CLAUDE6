import { describe, expect, it } from 'vitest';
import { DEFAULT_THEME, THEME_CSS, mix, sanitizeTheme, textOn, themeVars } from '../theme';

describe('Cores do sistema', () => {
  it('texto legível sobre qualquer fundo', () => {
    expect(textOn('#0a2540')).toBe('#ffffff');
    expect(textOn('#ffffff')).toBe('#0f172a');
    expect(textOn('#f59e0b')).toBe('#0f172a');
  });

  it('menu claro usa texto escuro; cores inválidas voltam ao padrão', () => {
    const v = themeVars({ ...DEFAULT_THEME, menu: '#ffffff' });
    expect(v['--t-menu-text']).toBe('#334155');
    expect(themeVars(DEFAULT_THEME)['--t-menu-text']).toBe('#cbd5e1');
    expect(sanitizeTheme({ header: 'azul', accent: '#ABCDEF' })).toEqual({ ...DEFAULT_THEME, accent: '#abcdef' });
    expect(mix('#000000', '#ffffff', 0.5)).toBe('#808080');
  });

  it('laudos e relatórios (.spda-sheet) ficam fora do tema', () => {
    expect(THEME_CSS).toContain(':not(.spda-sheet *)');
  });
});
