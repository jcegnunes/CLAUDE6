/**
 * Pergunta "Tipos de descida existentes" (antes dos ensaios de continuidade da Descida):
 * seleção múltipla com ilustração — descida externa (cobre ou alumínio), descida interna ao pilar
 * estrutural e descida natural em pilar de ferro — e registro fotográfico por tipo selecionado.
 * Gravado na edificação (SpdaBuilding.downConductor).
 */
import React from 'react';
import { Check } from 'lucide-react';
import { DOWN_CONDUCTOR_LABEL, DOWN_CONDUCTOR_ORDER, downConductorName } from '../rules';
import type { DownConductorInfo, DownConductorType, SpdaBuilding } from '../types';
import { DownConductorArt } from './DownConductorArt';
import { TestPhotos } from './TestPhotos';
import { cardCls, inputCls } from './ui';
import { confirmAsk } from './dialogs';

export const DownConductorSection: React.FC<{
  building: SpdaBuilding;
  readOnly: boolean;
  onChange: (next: DownConductorInfo) => void;
}> = ({ building, readOnly, onChange }) => {
  const info: DownConductorInfo = building.downConductor || { types: [] };
  const sel = info.types || [];
  const set = (p: Partial<DownConductorInfo>) => onChange({ ...info, types: sel, ...p });

  const toggle = async (t: DownConductorType) => {
    if (readOnly) return;
    if (sel.includes(t)) {
      const n = info.photos?.[t]?.length || 0;
      if (n && !(await confirmAsk(`Desmarcar "${DOWN_CONDUCTOR_LABEL[t].label}" e excluir as ${n} foto(s) desse tipo?`))) return;
      const photos = { ...info.photos };
      delete photos[t];
      set({ types: sel.filter(x => x !== t), photos, ...(t === 'externa' ? { material: undefined } : {}) });
    } else {
      set({ types: [...sel, t] });
    }
  };

  const chosen = DOWN_CONDUCTOR_ORDER.filter(t => sel.includes(t));

  return (
    <section className="space-y-2" aria-label="Tipos de descida existentes">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 className="font-bold text-sm text-slate-900">
          Tipos de descida existentes
          <span className="font-normal text-teal-700"> · {building.name || 'Edificação'}</span>
        </h3>
        <span className="text-[11px] text-slate-500">{sel.length ? `${sel.length} selecionado(s)` : 'Pode marcar mais de um'}</span>
      </div>

      <div className={`${cardCls} p-3 space-y-3`}>
        <p className="text-xs text-slate-600">Como são as descidas desta edificação? Marque todos os tipos que existem.</p>
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-2" role="group" aria-label="Tipos de descida">
          {DOWN_CONDUCTOR_ORDER.map(t => {
            const on = sel.includes(t);
            const L = DOWN_CONDUCTOR_LABEL[t];
            return (
              <button
                key={t}
                type="button"
                role="checkbox"
                aria-checked={on}
                disabled={readOnly && !on}
                onClick={() => toggle(t)}
                className={`relative text-left rounded-xl border-2 p-2 transition-colors ${on ? 'border-teal-600 bg-teal-50' : 'border-slate-200 bg-white hover:border-slate-300'} ${readOnly ? 'cursor-default' : ''} disabled:opacity-40`}
              >
                <span className={`absolute top-2 right-2 w-5 h-5 rounded-md border-2 flex items-center justify-center ${on ? 'bg-teal-600 border-teal-600 text-white' : 'bg-white border-slate-300'}`}>
                  {on && <Check className="w-3.5 h-3.5" />}
                </span>
                <DownConductorArt type={t} label={L.label} className="w-full h-auto rounded-lg" />
                <span className={`block mt-1.5 text-xs font-bold ${on ? 'text-teal-800' : 'text-slate-800'}`}>{t === 'externa' ? 'Descida externa (cobre ou alumínio)' : L.label}</span>
                <span className="block text-[10px] text-slate-500 leading-snug">{L.hint}</span>
              </button>
            );
          })}
        </div>

        {sel.includes('externa') && (
          <div className="flex flex-wrap items-center gap-2 text-xs">
            <span className="font-bold text-slate-700">Material da descida externa:</span>
            {(['cobre', 'aluminio'] as const).map(m => (
              <button key={m} type="button" disabled={readOnly} aria-pressed={info.material === m}
                onClick={() => set({ material: info.material === m ? undefined : m })}
                className={`px-3 py-1 rounded-full border text-[11px] font-bold ${info.material === m ? 'bg-teal-600 border-teal-600 text-white' : 'bg-white border-slate-300 text-slate-700'}`}>
                {m === 'cobre' ? 'Cobre' : 'Alumínio'}
              </button>
            ))}
          </div>
        )}
        <label className="block">
          <span className="block text-[11px] font-bold text-slate-700 mb-1">Observações sobre as descidas existentes</span>
          <textarea className={inputCls} rows={2} value={info.notes || ''} disabled={readOnly} onChange={e => set({ notes: e.target.value })} placeholder="Ex.: 6 descidas externas em cabo de cobre nu 35 mm² nas fachadas norte e sul…" aria-label="Observações sobre as descidas existentes" />
        </label>
      </div>

      {chosen.map(t => (
        <div key={t} className={`${cardCls} p-3 flex gap-3`}>
          <div className="w-20 shrink-0 hidden sm:block"><DownConductorArt type={t} label={DOWN_CONDUCTOR_LABEL[t].label} className="w-full h-auto rounded-md" /></div>
          <div className="flex-1 min-w-0">
            <p className="text-xs font-bold text-slate-800 mb-1">{downConductorName(building, t)}</p>
            <TestPhotos
              title="Registro fotográfico da descida existente"
              photos={info.photos?.[t]}
              readOnly={readOnly}
              defaultCaption={`Descida existente — ${downConductorName(building, t)}`}
              onChange={photos => set({ photos: { ...info.photos, [t]: photos } })}
            />
          </div>
        </div>
      ))}
    </section>
  );
};
