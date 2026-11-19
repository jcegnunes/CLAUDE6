/**
 * Pergunta "Tipo de malha de aterramento" (antes dos ensaios da tela Aterramento):
 * seleção múltipla com ilustração — anel de aterramento em cobre afastado da edificação ou
 * malha estrutural na própria ferragem — e registro fotográfico por tipo selecionado.
 * Gravado na edificação (SpdaBuilding.groundingSystem).
 */
import React from 'react';
import { Check } from 'lucide-react';
import { GROUNDING_SYSTEM_LABEL, GROUNDING_SYSTEM_ORDER, groundingSystemName } from '../rules';
import type { GroundingSystemInfo, GroundingSystemType, SpdaBuilding } from '../types';
import { GroundingSystemArt } from './GroundingSystemArt';
import { TestPhotos } from './TestPhotos';
import { cardCls, inputCls } from './ui';
import { confirmAsk } from './dialogs';

const CARD_TITLE: Record<GroundingSystemType, string> = {
  anel_cobre: 'Anel de aterramento em cobre afastado da edificação, com hastes',
  anel_sem_hastes: 'Só o anel de aterramento em cobre (sem hastes)',
  hastes_linha: 'Só as hastes de aterramento em linha (sem anel)',
  estrutural: 'Malha de aterramento estrutural na própria ferragem da edificação'
};

const parseM = (s: string): number | null => {
  const n = Number(s.replace(',', '.'));
  return s.trim() === '' || !Number.isFinite(n) ? null : n;
};

export const GroundingSystemSection: React.FC<{
  building: SpdaBuilding;
  readOnly: boolean;
  onChange: (next: GroundingSystemInfo) => void;
}> = ({ building, readOnly, onChange }) => {
  const info: GroundingSystemInfo = building.groundingSystem || { types: [] };
  const sel = info.types || [];
  const set = (p: Partial<GroundingSystemInfo>) => onChange({ ...info, types: sel, ...p });

  const toggle = async (t: GroundingSystemType) => {
    if (readOnly) return;
    if (sel.includes(t)) {
      const n = info.photos?.[t]?.length || 0;
      if (n && !(await confirmAsk(`Desmarcar "${GROUNDING_SYSTEM_LABEL[t].label}" e excluir as ${n} foto(s) desse tipo?`))) return;
      const photos = { ...info.photos };
      delete photos[t];
      set({ types: sel.filter(x => x !== t), photos });
    } else {
      set({ types: [...sel, t] });
    }
  };

  const chosen = GROUNDING_SYSTEM_ORDER.filter(t => sel.includes(t));
  const numField = (label: string, value: number | null | undefined, key: 'ringDistanceM' | 'ringDepthM', hint: string) => (
    <label className="block">
      <span className="block text-[11px] font-bold text-slate-700 mb-1">{label}</span>
      <input
        className={inputCls}
        inputMode="decimal"
        defaultValue={value != null ? String(value).replace('.', ',') : ''}
        disabled={readOnly}
        placeholder={hint}
        onBlur={e => set({ [key]: parseM(e.target.value) })}
        aria-label={label}
      />
    </label>
  );

  return (
    <section className="space-y-2" aria-label="Tipo de malha de aterramento">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 className="font-bold text-sm text-slate-900">
          Tipo de malha de aterramento
          <span className="font-normal text-teal-700"> · {building.name || 'Edificação'}</span>
        </h3>
        <span className="text-[11px] text-slate-500">{sel.length ? `${sel.length} selecionado(s)` : 'Pode marcar mais de um'}</span>
      </div>

      <div className={`${cardCls} p-3 space-y-3`}>
        <p className="text-xs text-slate-600">Qual é o aterramento desta edificação? Marque todos os tipos que existem.</p>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-2" role="group" aria-label="Tipos de malha de aterramento">
          {GROUNDING_SYSTEM_ORDER.map(t => {
            const on = sel.includes(t);
            const L = GROUNDING_SYSTEM_LABEL[t];
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
                <GroundingSystemArt type={t} label={L.label} className="w-full h-auto rounded-lg" />
                <span className={`block mt-1.5 text-xs font-bold ${on ? 'text-teal-800' : 'text-slate-800'}`}>
                  {CARD_TITLE[t]}
                </span>
                <span className="block text-[10px] text-slate-500 leading-snug">{L.hint}</span>
              </button>
            );
          })}
        </div>

        {(sel.includes('anel_cobre') || sel.includes('anel_sem_hastes')) && (
          <div className="grid grid-cols-2 gap-2">
            {numField('Afastamento do anel até a edificação (m)', info.ringDistanceM, 'ringDistanceM', 'Ex.: 1,0')}
            {numField('Profundidade do anel (m)', info.ringDepthM, 'ringDepthM', 'Ex.: 0,5')}
          </div>
        )}
        <label className="block">
          <span className="block text-[11px] font-bold text-slate-700 mb-1">Observações sobre o aterramento existente</span>
          <textarea className={inputCls} rows={2} value={info.notes || ''} disabled={readOnly} onChange={e => set({ notes: e.target.value })} placeholder="Ex.: anel em cabo de cobre nu 50 mm² com hastes nas caixas de inspeção…" aria-label="Observações sobre o aterramento existente" />
        </label>
      </div>

      {chosen.map(t => (
        <div key={t} className={`${cardCls} p-3 flex gap-3`}>
          <div className="w-20 shrink-0 hidden sm:block"><GroundingSystemArt type={t} label={GROUNDING_SYSTEM_LABEL[t].label} className="w-full h-auto rounded-md" /></div>
          <div className="flex-1 min-w-0">
            <p className="text-xs font-bold text-slate-800 mb-1">{groundingSystemName(building, t)}</p>
            <TestPhotos
              title="Registro fotográfico do aterramento existente"
              photos={info.photos?.[t]}
              readOnly={readOnly}
              defaultCaption={`Aterramento existente — ${GROUNDING_SYSTEM_LABEL[t].label}`}
              onChange={photos => set({ photos: { ...info.photos, [t]: photos } })}
            />
          </div>
        </div>
      ))}
    </section>
  );
};
