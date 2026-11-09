/**
 * Tela "Edificações" do módulo SPDA (menu, logo abaixo de Ordens de Serviço):
 * escolhe a OS / inspeção e cadastra as edificações dela; abaixo, todas as
 * edificações já lançadas. Os dados ficam dentro da inspeção (mesmo registro).
 */
import React from 'react';
import { Building2, MapPin, Plus } from 'lucide-react';
import { canEditSpda, createInspection, saveInspection } from '../repository';
import {
  BUILDING_TYPE_LABEL, SPDA_KIND_LABEL, buildingArea, buildingsOf, formatCoordinates, levelEfficiencyPct, mapUrl, withBuildings
} from '../rules';
import type { SpdaInspection } from '../types';
import { BuildingsSection } from './BuildingsSection';
import { InspectionPicker, useSelectedInspection } from './InspectionPicker';
import { EmptyState, alertError, btnPrimary, btnSecondary, cardCls } from './ui';

export const SpdaBuildingsView: React.FC = () => {
  const { inspections, selected, select: setSelectedId } = useSelectedInspection();
  const selectedId = selected?.id || '';
  const canEdit = canEditSpda();
  const readOnly = !canEdit || selected?.status === 'concluido';

  const save = (insp: SpdaInspection) => {
    try { saveInspection(insp); } catch (err) { alertError(err, 'Não foi possível salvar'); }
  };

  const create = () => {
    try {
      const saved = saveInspection(createInspection());
      setSelectedId(saved.id);
    } catch (err) {
      alertError(err, 'Não foi possível criar a inspeção');
    }
  };

  const all = inspections.flatMap(i => buildingsOf(i).map(b => ({ b, insp: i })));

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-teal-600 text-white flex items-center justify-center"><Building2 className="w-5 h-5" /></div>
          <div>
            <h2 className="text-lg font-black text-slate-900 flex items-center gap-2">Edificações<span className="text-[10px] font-bold px-1.5 py-0.5 rounded bg-amber-100 text-amber-800 border border-amber-300">DEMO</span></h2>
            <p className="text-xs text-slate-500">Edificações de cada OS de SPDA: tipo, dimensões, SPDA existente, nível de proteção, coordenadas e imagem aérea</p>
          </div>
        </div>
        {canEdit && <button type="button" className={btnSecondary} onClick={create}><Plus className="w-3.5 h-3.5" /> Nova inspeção / OS</button>}
      </div>

      {inspections.length === 0 ? (
        <EmptyState
          icon={Building2}
          title="Nenhuma inspeção de SPDA"
          text="Crie uma inspeção (com a OS) para lançar as edificações."
          action={canEdit ? <button type="button" className={btnPrimary} onClick={create}><Plus className="w-3.5 h-3.5" /> Nova inspeção</button> : undefined}
        />
      ) : (
        <>
          <InspectionPicker inspections={inspections} selected={selected} select={setSelectedId} />

          {selected && (
            <BuildingsSection
              key={selected.id}
              buildings={buildingsOf(selected)}
              readOnly={readOnly}
              onChange={next => save(withBuildings(selected, next))}
            />
          )}

          <section className={cardCls}>
            <div className="px-4 py-3 border-b border-slate-200">
              <h3 className="font-bold text-sm text-slate-900">Todas as edificações <span className="text-slate-400 font-normal">({all.length})</span></h3>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full text-xs min-w-[820px]">
                <thead className="bg-slate-50 text-slate-600 text-[10px] uppercase">
                  <tr>
                    <th className="px-3 py-2 text-left">Edificação</th>
                    <th className="px-3 py-2 text-left">Tipo</th>
                    <th className="px-3 py-2 text-left">Área</th>
                    <th className="px-3 py-2 text-left">SPDA existente</th>
                    <th className="px-3 py-2 text-left">NP</th>
                    <th className="px-3 py-2 text-left">OS / cliente</th>
                    <th className="px-3 py-2 text-left">Local</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {all.map(({ b, insp }) => {
                    const area = buildingArea(b);
                    const url = mapUrl(b.latitude, b.longitude);
                    return (
                      <tr key={`${insp.id}_${b.id}`} className={insp.id === selectedId ? 'bg-teal-50/50' : ''}>
                        <td className="px-3 py-2">
                          <button type="button" className="font-bold text-slate-900 hover:text-teal-700 text-left" onClick={() => setSelectedId(insp.id)}>{b.name || '—'}</button>
                          {b.aerialImage && <span className="ml-1 text-[9px] text-teal-700 font-bold">• imagem aérea</span>}
                        </td>
                        <td className="px-3 py-2">{BUILDING_TYPE_LABEL[b.type]}{b.activity ? ` — ${b.activity}` : ''}</td>
                        <td className="px-3 py-2">{area !== null ? `${area.toLocaleString('pt-BR', { maximumFractionDigits: 1 })} m²` : '—'}</td>
                        <td className="px-3 py-2">{SPDA_KIND_LABEL[b.spdaKind].label}</td>
                        <td className="px-3 py-2">{b.protectionLevel} ({levelEfficiencyPct(b.protectionLevel)} %)</td>
                        <td className="px-3 py-2">
                          <span className="font-mono text-blue-700">{insp.serviceOrderNumber || insp.number}</span>
                          <span className="block text-[10px] text-slate-500">{insp.clientName || '—'}</span>
                        </td>
                        <td className="px-3 py-2">
                          {url ? (
                            <a href={url} target="_blank" rel="noopener noreferrer" className="inline-flex items-center gap-1 text-teal-700 hover:underline text-[10px]">
                              <MapPin className="w-3 h-3" /> {formatCoordinates(b.latitude, b.longitude)}
                            </a>
                          ) : <span className="text-slate-400">—</span>}
                        </td>
                      </tr>
                    );
                  })}
                  {all.length === 0 && <tr><td colSpan={7} className="px-4 py-4 text-center text-slate-400">Nenhuma edificação lançada.</td></tr>}
                </tbody>
              </table>
            </div>
          </section>
        </>
      )}
    </div>
  );
};

export default SpdaBuildingsView;
