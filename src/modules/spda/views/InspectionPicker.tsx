/**
 * Seletor de OS / inspeção usado pelas telas do menu SPDA (Edificações,
 * Captação, Descida, Aterramento, DPS). A escolha é lembrada entre as telas.
 */
import React, { useEffect, useState } from 'react';
import { Building2, ExternalLink } from 'lucide-react';
import { navigateTo } from '../../registry';
import { getInspection, listInspections, subscribeSpda } from '../repository';
import { VERDICT_LABEL, buildingVerdict, buildingsOf, type Verdict } from '../rules';
import type { SpdaBuilding, SpdaInspection } from '../types';
import { openInspectionOnNextVisit } from './navigation';
import { Field, btnSecondary, cardCls, inputCls } from './ui';

let lastSelectedId = '';

export function inspectionLabel(i: SpdaInspection): string {
  return [i.serviceOrderNumber ? `OS ${i.serviceOrderNumber}` : 'Sem OS', i.number, i.clientName || 'Cliente não informado'].join(' · ');
}

/** Inspeção selecionada (lembrada entre telas) + lista atualizada a cada gravação. */
export function useSelectedInspection(): { inspections: SpdaInspection[]; selected?: SpdaInspection; select: (id: string) => void } {
  const [, setVersion] = useState(0);
  const [selectedId, setSelectedId] = useState<string>(() => {
    const list = listInspections();
    return list.some(i => i.id === lastSelectedId) ? lastSelectedId : list[0]?.id || '';
  });
  useEffect(() => subscribeSpda(() => setVersion(v => v + 1)), []);
  const select = (id: string) => { lastSelectedId = id; setSelectedId(id); };
  const inspections = listInspections();
  const selected = selectedId ? getInspection(selectedId) : undefined;
  return { inspections, selected, select };
}

// ------------------------------------------------------------ edificação
let lastBuildingId = '';

/** Edificação selecionada dentro da inspeção (lembrada entre as telas). */
export function useSelectedBuilding(insp?: SpdaInspection): { buildings: SpdaBuilding[]; building?: SpdaBuilding; selectBuilding: (id: string) => void } {
  const [id, setId] = useState(lastBuildingId);
  const buildings = insp ? buildingsOf(insp) : [];
  const building = buildings.find(b => b.id === id) || buildings[0];
  const selectBuilding = (next: string) => { lastBuildingId = next; setId(next); };
  return { buildings, building, selectBuilding };
}

const VERDICT_DOT: Record<Verdict, string> = { conforme: 'bg-emerald-500', nao_conforme: 'bg-red-500', incompleto: 'bg-amber-400' };

/** Botões das edificações da OS, com o parecer de cada uma. */
export const BuildingTabs: React.FC<{ insp: SpdaInspection; buildings: SpdaBuilding[]; building?: SpdaBuilding; select: (id: string) => void }> = ({ insp, buildings, building, select }) => {
  if (!buildings.length) {
    return (
      <p className="text-[11px] text-amber-700 bg-amber-50 border border-amber-200 rounded-xl px-3 py-2 flex items-center gap-1.5">
        <Building2 className="w-3.5 h-3.5" /> Cadastre as edificações da OS (menu Edificações ou aba Dados) para inspecionar cada uma.
      </p>
    );
  }
  return (
    <div className={`${cardCls} p-3`}>
      <p className="text-[11px] font-bold text-slate-600 mb-2 flex items-center gap-1.5"><Building2 className="w-3.5 h-3.5" /> Edificação inspecionada</p>
      <div className="flex flex-wrap gap-2" role="tablist" aria-label="Edificação inspecionada">
        {buildings.map((b, i) => {
          const on = b.id === building?.id;
          const verdict = buildingVerdict({ ...insp, buildings }, b.id);
          return (
            <button key={b.id} type="button" role="tab" aria-selected={on} onClick={() => select(b.id)}
              title={VERDICT_LABEL[verdict]}
              className={`inline-flex items-center gap-2 px-3 py-1.5 rounded-xl border text-xs font-bold transition-colors ${on ? 'border-teal-600 bg-teal-50 text-teal-800 ring-1 ring-teal-600' : 'border-slate-200 bg-white text-slate-600 hover:bg-slate-50'}`}>
              <span className={`w-2 h-2 rounded-full ${VERDICT_DOT[verdict]}`} />
              {b.name || `Edificação ${i + 1}`}
              <span className="text-[10px] font-normal text-slate-400">NP {b.protectionLevel}</span>
            </button>
          );
        })}
      </div>
    </div>
  );
};

export const InspectionPicker: React.FC<{ inspections: SpdaInspection[]; selected?: SpdaInspection; select: (id: string) => void }> = ({ inspections, selected, select }) => (
  <>
    <div className={`${cardCls} p-4 flex flex-col sm:flex-row sm:items-end gap-3`}>
      <Field label="Ordem de serviço / inspeção" className="flex-1">
        <select className={inputCls} value={selected?.id || ''} onChange={e => select(e.target.value)} aria-label="Ordem de serviço / inspeção">
          {inspections.map(i => <option key={i.id} value={i.id}>{inspectionLabel(i)}</option>)}
        </select>
      </Field>
      {selected && (
        <button type="button" className={btnSecondary} onClick={() => { openInspectionOnNextVisit(selected.id); navigateTo('spda'); }}>
          <ExternalLink className="w-3.5 h-3.5" /> Abrir inspeção
        </button>
      )}
    </div>
    {selected?.status === 'concluido' && (
      <p className="text-[11px] text-slate-500">Inspeção concluída: somente leitura (reabra a inspeção para editar).</p>
    )}
    {selected && !selected.serviceOrderNumber && (
      <p className="text-[11px] text-amber-700">Esta inspeção ainda não tem OS vinculada — vincule ou gere a OS na aba Dados da inspeção.</p>
    )}
  </>
);
