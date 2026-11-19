/**
 * Pergunta "Sistemas de captação existentes" (antes dos ensaios de continuidade da Captação):
 * seleção múltipla com ilustração de cada sistema e registro fotográfico do sistema existente,
 * por tipo selecionado. Gravado na edificação (SpdaBuilding.airTermination).
 */
import React from 'react';
import { Check } from 'lucide-react';
import { AIR_TERMINATION_LABEL, AIR_TERMINATION_ORDER, airTerminationName } from '../rules';
import type { AirTerminationInfo, AirTerminationType, SpdaBuilding } from '../types';
import { AirTerminationArt } from './AirTerminationArt';
import { TestPhotos } from './TestPhotos';
import { cardCls, inputCls } from './ui';
import { confirmAsk } from './dialogs';

export const AirTerminationSection: React.FC<{
  building: SpdaBuilding;
  readOnly: boolean;
  onChange: (next: AirTerminationInfo) => void;
}> = ({ building, readOnly, onChange }) => {
  const info: AirTerminationInfo = building.airTermination || { types: [] };
  const sel = info.types || [];
  const set = (p: Partial<AirTerminationInfo>) => onChange({ ...info, types: sel, ...p });

  const toggle = async (t: AirTerminationType) => {
    if (readOnly) return;
    if (sel.includes(t)) {
      const n = info.photos?.[t]?.length || 0;
      if (n && !(await confirmAsk(`Desmarcar "${AIR_TERMINATION_LABEL[t].label}" e excluir as ${n} foto(s) desse sistema?`))) return;
      const photos = { ...info.photos };
      delete photos[t];
      set({ types: sel.filter(x => x !== t), photos });
    } else {
      set({ types: [...sel, t] });
    }
  };

  const chosen = AIR_TERMINATION_ORDER.filter(t => sel.includes(t));

  return (
    <section className="space-y-2" aria-label="Sistemas de captação existentes">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 className="font-bold text-sm text-slate-900">
          Sistemas de captação existentes
          <span className="font-normal text-teal-700"> · {building.name || 'Edificação'}</span>
        </h3>
        <span className="text-[11px] text-slate-500">{sel.length ? `${sel.length} selecionado(s)` : 'Pode marcar mais de um'}</span>
      </div>

      <div className={`${cardCls} p-3 space-y-3`}>
        <p className="text-xs text-slate-600">Quais sistemas de captação existem na cobertura? Marque todos os que se aplicam.</p>
        <div className="grid grid-cols-2 sm:grid-cols-3 gap-2" role="group" aria-label="Tipos de sistema de captação">
          {AIR_TERMINATION_ORDER.map(t => {
            const on = sel.includes(t);
            const L = AIR_TERMINATION_LABEL[t];
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
                <AirTerminationArt type={t} label={L.label} className="w-full h-auto rounded-lg" />
                <span className={`block mt-1.5 text-xs font-bold ${on ? 'text-teal-800' : 'text-slate-800'}`}>{t === 'anel_superior' ? 'Anel superior (cobre ou alumínio)' : L.label}</span>
                <span className="block text-[10px] text-slate-500 leading-snug">{L.hint}</span>
              </button>
            );
          })}
        </div>

        {sel.includes('anel_superior') && (
          <div className="flex flex-wrap items-center gap-2 text-xs">
            <span className="font-bold text-slate-700">Material do anel superior:</span>
            {(['cobre', 'aluminio'] as const).map(m => (
              <button key={m} type="button" disabled={readOnly} aria-pressed={info.ringMaterial === m}
                onClick={() => set({ ringMaterial: info.ringMaterial === m ? undefined : m })}
                className={`px-3 py-1 rounded-full border text-[11px] font-bold ${info.ringMaterial === m ? 'bg-teal-600 border-teal-600 text-white' : 'bg-white border-slate-300 text-slate-700'}`}>
                {m === 'cobre' ? 'Cobre' : 'Alumínio'}
              </button>
            ))}
          </div>
        )}
        {sel.includes('outro') && (
          <label className="block">
            <span className="block text-[11px] font-bold text-slate-700 mb-1">Descreva o outro sistema</span>
            <input className={inputCls} value={info.otherText || ''} disabled={readOnly} onChange={e => set({ otherText: e.target.value })} placeholder="Ex.: captor tipo haste em antena, estrutura metálica da caixa d'água…" aria-label="Descrição do outro sistema de captação" />
          </label>
        )}
        <label className="block">
          <span className="block text-[11px] font-bold text-slate-700 mb-1">Observações sobre a captação existente</span>
          <textarea className={inputCls} rows={2} value={info.notes || ''} disabled={readOnly} onChange={e => set({ notes: e.target.value })} placeholder="Ex.: 4 mastros Franklin nos cantos, malha em cabo de cobre nu 35 mm²…" aria-label="Observações sobre a captação existente" />
        </label>
      </div>

      {chosen.map(t => (
        <div key={t} className={`${cardCls} p-3 flex gap-3`}>
          <div className="w-20 shrink-0 hidden sm:block"><AirTerminationArt type={t} label={AIR_TERMINATION_LABEL[t].label} className="w-full h-auto rounded-md" /></div>
          <div className="flex-1 min-w-0">
            <p className="text-xs font-bold text-slate-800 mb-1">{airTerminationName(building, t)}</p>
            <TestPhotos
              title="Registro fotográfico do sistema existente"
              photos={info.photos?.[t]}
              readOnly={readOnly}
              defaultCaption={`Captação existente — ${airTerminationName(building, t)}`}
              onChange={photos => set({ photos: { ...info.photos, [t]: photos } })}
            />
          </div>
        </div>
      ))}
    </section>
  );
};
