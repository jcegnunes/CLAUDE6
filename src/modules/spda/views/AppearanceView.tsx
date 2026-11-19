/**
 * Configurações → Cores do sistema: temas prontos e cor por cor (barra superior, menu, destaque, botões e fundo),
 * com prévia. Aplica na hora em todo o app; laudos e relatórios mantêm as cores do documento.
 */
import React, { useState } from 'react';
import { Check, Palette, RotateCcw, Zap } from 'lucide-react';
import { DEFAULT_THEME, THEME_PRESETS, readTheme, resetTheme, saveTheme, textOn, type ThemeColors } from '../theme';
import { btnSecondary, cardCls } from './ui';

const FIELDS: Array<{ key: keyof ThemeColors; label: string; hint: string }> = [
  { key: 'header', label: 'Barra superior e títulos', hint: 'Topo do app, ícones das telas e títulos' },
  { key: 'menu', label: 'Menu lateral', hint: 'Fundo do menu (o texto se ajusta sozinho)' },
  { key: 'accent', label: 'Destaque', hint: 'Item ativo do menu e logotipo' },
  { key: 'primary', label: 'Botões principais', hint: 'Salvar, Nova inspeção, Gerar relatório…' },
  { key: 'background', label: 'Fundo das telas', hint: 'Cor atrás dos cartões' }
];

const same = (a: ThemeColors, b: ThemeColors) => (Object.keys(a) as Array<keyof ThemeColors>).every(k => a[k].toLowerCase() === b[k].toLowerCase());

/** Prévia em miniatura do app com as cores escolhidas. */
const Preview: React.FC<{ t: ThemeColors }> = ({ t }) => (
  <div className="rounded-xl overflow-hidden border border-slate-300 text-[10px] shadow-sm" aria-label="Prévia das cores">
    <div className="flex items-center gap-1.5 px-2 h-7" style={{ background: t.header, color: textOn(t.header) }}>
      <span className="w-4 h-4 rounded flex items-center justify-center" style={{ background: t.accent, color: textOn(t.accent) }}><Zap className="w-2.5 h-2.5" /></span>
      <b>JVM SPDA</b>
    </div>
    <div className="flex h-24">
      <div className="w-20 p-1.5 space-y-1" style={{ background: t.menu }}>
        <div className="rounded px-1 py-0.5 font-bold" style={{ background: t.accent, color: textOn(t.accent) }}>Inspeções</div>
        <div className="px-1" style={{ color: textOn(t.menu), opacity: 0.75 }}>Relatório</div>
        <div className="px-1" style={{ color: textOn(t.menu), opacity: 0.75 }}>Dashboard</div>
      </div>
      <div className="flex-1 p-2 space-y-1.5" style={{ background: t.background }}>
        <b style={{ color: t.header }}>Inspeções e laudos</b>
        <div className="bg-white rounded border border-slate-200 h-6" />
        <span className="inline-block rounded px-2 py-0.5 font-bold" style={{ background: t.primary, color: textOn(t.primary) }}>Salvar</span>
      </div>
    </div>
  </div>
);

export const AppearanceView: React.FC = () => {
  const [t, setT] = useState<ThemeColors>(readTheme);
  const set = (next: ThemeColors) => { setT(next); saveTheme(next); };

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-3">
        <div className="w-10 h-10 rounded-xl bg-[#0A2540] text-white flex items-center justify-center"><Palette className="w-5 h-5" /></div>
        <div>
          <h2 className="text-lg font-black text-slate-900">Cores do sistema</h2>
          <p className="text-xs text-slate-500">Cores de visualização do app neste aparelho. Laudos e relatórios mantêm as cores do documento.</p>
        </div>
      </div>

      <section className={`${cardCls} p-4 space-y-3`}>
        <h3 className="font-bold text-sm text-slate-900">Temas prontos</h3>
        <div className="grid grid-cols-2 sm:grid-cols-3 gap-2" role="radiogroup" aria-label="Temas prontos">
          {THEME_PRESETS.map(p => {
            const on = same(p.colors, t);
            return (
              <button key={p.id} type="button" role="radio" aria-checked={on} onClick={() => set(p.colors)}
                className={`relative text-left rounded-xl border-2 p-2 ${on ? 'border-slate-900' : 'border-slate-200 hover:border-slate-300'}`}>
                <span className="flex h-6 rounded-md overflow-hidden mb-1.5">
                  {(['header', 'menu', 'accent', 'primary', 'background'] as const).map(k => <span key={k} className="flex-1" style={{ background: p.colors[k] }} />)}
                </span>
                <span className="text-xs font-bold text-slate-800">{p.label}</span>
                {on && <Check className="absolute top-2 right-2 w-4 h-4 text-slate-900 bg-white rounded-full" />}
              </button>
            );
          })}
        </div>
      </section>

      <section className={`${cardCls} p-4`}>
        <div className="flex flex-wrap items-center justify-between gap-2 mb-3">
          <h3 className="font-bold text-sm text-slate-900">Personalizar cor por cor</h3>
          <button type="button" className={btnSecondary} disabled={same(t, DEFAULT_THEME)} onClick={() => { resetTheme(); setT({ ...DEFAULT_THEME }); }}>
            <RotateCcw className="w-3.5 h-3.5" /> Voltar ao padrão
          </button>
        </div>
        <div className="grid grid-cols-1 lg:grid-cols-[1fr_260px] gap-4 items-start">
          <ul className="space-y-2">
            {FIELDS.map(f => (
              <li key={f.key} className="flex items-center gap-3">
                <input type="color" value={t[f.key]} onChange={e => set({ ...t, [f.key]: e.target.value })}
                  className="w-11 h-11 rounded-lg border border-slate-300 bg-white p-0.5 cursor-pointer shrink-0" aria-label={f.label} />
                <span className="min-w-0">
                  <span className="block text-xs font-bold text-slate-800">{f.label} <span className="font-mono font-normal text-slate-400">{t[f.key]}</span></span>
                  <span className="block text-[11px] text-slate-500">{f.hint}</span>
                </span>
              </li>
            ))}
          </ul>
          <div>
            <p className="text-[11px] font-bold text-slate-600 mb-1">Prévia</p>
            <Preview t={t} />
          </div>
        </div>
        <p className="text-[11px] text-slate-500 mt-3">As cores são aplicadas na hora e ficam salvas neste aparelho. Os textos do menu e da barra mudam para claro ou escuro sozinhos, para continuar legíveis.</p>
      </section>
    </div>
  );
};
