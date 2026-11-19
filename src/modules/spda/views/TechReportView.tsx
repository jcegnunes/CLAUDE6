/**
 * Menu Relatório: escolhe a inspeção, o tipo de relatório (técnico, laudo, simplificado, fotográfico, checklist
 * preenchido ou em branco), o subsistema e a abrangência (por edificação ou agrupado). No relatório técnico
 * também edita os textos (introdução, metodologia e conclusão — com texto padrão gerado pelo app).
 */
import React, { useCallback, useState } from 'react';
import { BookOpenText, CheckCircle2, FileText, AlertTriangle, Building2, Layers } from 'lucide-react';
import { canEditSpda } from '../repository';
import { buildingsOf, listNonConformities } from '../rules';
import type { SpdaInspection } from '../types';
import { RESULT_LABEL, TECH_SECTIONS, finalResult } from '../techReport';
import { TechReportEditor } from './TechReportEditor';
import { InspectionPicker, useSelectedInspection } from './InspectionPicker';
import { CONTINUITY_GROUP_INFO } from '../rules';
import { GROUPS, REPORT_KINDS, ReportOutput, type ReportGroup, type ReportKind } from './ReportTypes';
import { photoEntries } from './PhotoReport';
import { EmptyState, btnPrimary, cardCls, inputCls } from './ui';

/** Situação de cada seção do relatório (o que já tem dados). */
function sectionStatus(v: SpdaInspection): Record<string, { ok: boolean; note: string }> {
  const bs = buildingsOf(v);
  const nPhotos = (['captacao', 'descida', 'aterramento', 'dps'] as const).reduce((n, g) => n + (bs.length ? bs : [undefined]).reduce((m, b) => m + photoEntries(v, b, g).length, 0), 0);
  const located = bs.filter(b => (typeof b.latitude === 'number' && typeof b.longitude === 'number') || b.aerialImage || b.mapsLink).length;
  const ncs = listNonConformities(v).length;
  const sel = bs.filter(b => b.airTermination?.types?.length || b.downConductor?.types?.length || b.groundingSystem?.types?.length).length;
  const chosen = Object.keys(v.techReport?.results || {}).length;
  return {
    '1': { ok: v.checklist.length > 0 || v.continuity.length > 0, note: `parecer ${RESULT_LABEL[finalResult(v)].toLowerCase()} · ${ncs} NC` },
    '2': { ok: true, note: v.techReport?.introducao?.trim() ? 'texto editado' : 'texto padrão' },
    '3': { ok: true, note: 'NBR 5419-1 a 4, NBR 15749, NBR IEC 61643-11, NR-10' },
    '4': { ok: bs.length > 0, note: `${bs.length} edificação(ões) · ${located} com localização` },
    '5': { ok: sel > 0, note: `sistemas selecionados em ${sel} de ${bs.length} edificação(ões)` },
    '6': { ok: v.instruments.length > 0, note: `${v.instruments.length} instrumento(s) · condições do ensaio` },
    '7': { ok: v.checklist.length > 0 || v.continuity.length > 0, note: `checklist ${v.checklist.length} · continuidade ${v.continuity.length} · aterramento ${v.grounding.length} · ${nPhotos} foto(s)` },
    '8': { ok: true, note: `${ncs} NC` },
    '9': { ok: true, note: `${chosen ? `${chosen} resultado(s) escolhido(s)` : 'resultados automáticos'} · ${v.techReport?.conclusao?.trim() ? 'texto editado' : 'texto padrão'}` },
    '10': { ok: true, note: 'ART, anexos e assinaturas' }
  };
}

export const TechReportView: React.FC = () => {
  const { inspections, selected, select } = useSelectedInspection();
  const [show, setShow] = useState(false);
  const close = useCallback(() => setShow(false), []);
  const [kind, setKind] = useState<ReportKind>('tecnico');
  const [group, setGroup] = useState<ReportGroup>('todos');
  const [scope, setScope] = useState<'agrupado' | 'edificacao'>('agrupado');
  const [buildingId, setBuildingId] = useState('');
  const bs = selected ? buildingsOf(selected) : [];
  const pickedBuilding = scope === 'edificacao' ? (bs.find(b => b.id === buildingId) || bs[0])?.id || '' : '';
  const kindInfo = REPORT_KINDS.find(k => k.v === kind)!;
  const pill = (on: boolean) => `px-3 py-1.5 rounded-lg border text-xs font-semibold ${on ? 'bg-[#0A2540] text-white border-[#0A2540]' : 'bg-white text-slate-700 border-slate-300 hover:bg-slate-50'}`;
  const readOnly = !canEditSpda() || selected?.status === 'concluido';


  const st = selected ? sectionStatus(selected) : null;

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-[#0A2540] text-white flex items-center justify-center"><BookOpenText className="w-5 h-5" /></div>
          <div>
            <h2 className="text-lg font-black text-slate-900">Relatórios</h2>
            <p className="text-xs text-slate-500">Relatório técnico, laudo, relatório simplificado das medições, relatório fotográfico e checklist — por edificação ou agrupado</p>
          </div>
        </div>
        {selected && <button type="button" className={btnPrimary} onClick={() => setShow(true)}><FileText className="w-3.5 h-3.5" /> Gerar relatório</button>}
      </div>

      {inspections.length === 0 ? (
        <EmptyState icon={BookOpenText} title="Nenhuma inspeção de SPDA" text="Crie uma inspeção em Inspeções e laudos para gerar o relatório técnico." />
      ) : (
        <>
          <InspectionPicker inspections={inspections} selected={selected} select={select} />

          {selected && (
            <section className={`${cardCls} p-4 space-y-4`}>
              <div>
                <h3 className="font-bold text-sm text-slate-900 mb-2">Tipo de relatório</h3>
                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2" role="radiogroup" aria-label="Tipo de relatório">
                  {REPORT_KINDS.map(k => (
                    <button key={k.v} type="button" role="radio" aria-checked={kind === k.v} onClick={() => setKind(k.v)}
                      className={`text-left border-2 rounded-xl px-3 py-2 ${kind === k.v ? 'border-blue-600 bg-blue-50' : 'border-slate-200 bg-white hover:bg-slate-50'}`}>
                      <span className="block text-xs font-bold text-slate-900">{k.label}</span>
                      <span className="block text-[10px] text-slate-500 mt-0.5">{k.hint}</span>
                    </button>
                  ))}
                </div>
              </div>
              {kindInfo.groups && (
                <div>
                  <p className="font-bold text-slate-700 text-xs mb-1.5 flex items-center gap-1.5"><Layers className="w-3.5 h-3.5" /> Subsistema</p>
                  <div className="flex flex-wrap gap-1.5">
                    <button type="button" className={pill(group === 'todos')} onClick={() => setGroup('todos')}>Todos</button>
                    {GROUPS.map(g => <button key={g} type="button" className={pill(group === g)} onClick={() => setGroup(g)}>{CONTINUITY_GROUP_INFO[g].label}</button>)}
                  </div>
                </div>
              )}
              <div>
                <p className="font-bold text-slate-700 text-xs mb-1.5 flex items-center gap-1.5"><Building2 className="w-3.5 h-3.5" /> Abrangência</p>
                <div className="flex flex-wrap items-center gap-1.5">
                  <button type="button" className={pill(scope === 'agrupado')} onClick={() => setScope('agrupado')}>Agrupado — {bs.length > 1 ? `todas as ${bs.length} edificações` : 'a edificação'}</button>
                  <button type="button" className={pill(scope === 'edificacao')} disabled={bs.length === 0} onClick={() => setScope('edificacao')}>Por edificação</button>
                  {scope === 'edificacao' && bs.length > 0 && (
                    <select className={`${inputCls} w-auto`} aria-label="Edificação" value={pickedBuilding} onChange={e => setBuildingId(e.target.value)}>
                      {bs.map(b => <option key={b.id} value={b.id}>{b.name || 'Edificação'}</option>)}
                    </select>
                  )}
                </div>
              </div>
              <div className="flex justify-end">
                <button type="button" className={btnPrimary} onClick={() => setShow(true)}><FileText className="w-3.5 h-3.5" /> Gerar {kindInfo.label.toLowerCase()}</button>
              </div>
            </section>
          )}

          {selected && st && kind === 'tecnico' && (
            <>
              <section className={`${cardCls} p-4`}>
                <h3 className="font-bold text-sm text-slate-900 mb-2">Conteúdo do relatório técnico</h3>
                <ol className="grid grid-cols-1 sm:grid-cols-2 gap-1.5 text-xs">
                  <li className="flex items-center gap-2 text-slate-700"><CheckCircle2 className="w-3.5 h-3.5 text-emerald-600" /> Capa, controle do documento e sumário</li>
                  {TECH_SECTIONS.map(s => (
                    <li key={s.n} className="flex items-center gap-2 text-slate-700">
                      {st[s.n].ok ? <CheckCircle2 className="w-3.5 h-3.5 text-emerald-600 shrink-0" /> : <AlertTriangle className="w-3.5 h-3.5 text-amber-600 shrink-0" />}
                      <span><b>{s.n}{/^[A-Z]$/.test(s.n) ? '' : '.'}</b> {s.title} <span className="text-slate-400">— {st[s.n].note}</span></span>
                    </li>
                  ))}
                </ol>
                <p className="text-[11px] text-slate-500 mt-2">
                  Os dados vêm da inspeção: edificações e localização (Edificações), checklist, ensaios e croquis (Captação, Descida, Aterramento e DPS)
                  e recomendações (conclusão da inspeção). Abaixo, todos os textos podem ser ajustados e o resultado de cada subsistema e o parecer final podem ser escolhidos.
                </p>
              </section>

              <TechReportEditor inspection={selected} readOnly={readOnly} onGenerate={() => setShow(true)} />
            </>
          )}
        </>
      )}

      {show && selected && (
        <ReportOutput inspection={selected} kind={kind} group={kindInfo.groups ? group : 'todos'} buildingId={pickedBuilding} onClose={close} />
      )}
    </div>
  );
};
