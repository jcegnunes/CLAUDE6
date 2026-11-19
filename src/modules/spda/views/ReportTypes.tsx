/**
 * Tipos de relatório do menu Relatório: técnico completo, laudo, relatório simplificado das medições,
 * relatório fotográfico e checklist (preenchido ou em branco para campo) — por edificação ou agrupado.
 */
import React from 'react';
import { CHECKLIST_TEMPLATE, SUBSYSTEM_LABEL } from '../checklist';
import { readCompanyInfo } from '../repository';
import { CONTINUITY_GROUP_INFO, answerOf, buildingsOf, groundingValue, isPerBuilding, type ContinuityGroup } from '../rules';
import type { SpdaBuilding, SpdaInspection, SpdaSubsystem } from '../types';
import { formatDateBR } from '../../../utils/dateUtils';
import { Info, PrintOverlay, Section, SpdaReport, td, th } from './ReportView';
import { TechReport } from './TechReport';
import { ContinuitySheet, SUBSYSTEMS_OF, sheetTests } from './ContinuitySheet';
import { PhotoReport, photoEntries } from './PhotoReport';

export type ReportKind = 'tecnico' | 'laudo' | 'simplificado' | 'fotografico' | 'checklist' | 'checklist_branco';
export type ReportGroup = 'todos' | ContinuityGroup;

export const REPORT_KINDS: Array<{ v: ReportKind; label: string; hint: string; groups: boolean }> = [
  { v: 'tecnico', label: 'Relatório técnico completo', hint: 'Capa, sumário, metodologia, inspeção, medições e fotos por edificação, NC e conclusão', groups: false },
  { v: 'laudo', label: 'Laudo de inspeção', hint: 'Laudo resumido com checklist avaliado, ensaios, NC, recomendações e parecer', groups: false },
  { v: 'simplificado', label: 'Relatório simplificado das medições', hint: 'Tabela das medições com o croqui dos pontos, por subsistema', groups: true },
  { v: 'fotografico', label: 'Relatório fotográfico', hint: 'Fotos da inspeção visual e dos ensaios, numeradas, por subsistema', groups: true },
  { v: 'checklist', label: 'Checklist preenchido', hint: 'Todos os itens do checklist com a situação (C / NC / NA) e observações', groups: true },
  { v: 'checklist_branco', label: 'Checklist em branco', hint: 'Formulário para preenchimento em campo, com os itens da norma', groups: true }
];

export const GROUPS: ContinuityGroup[] = ['captacao', 'descida', 'aterramento', 'dps'];

/** Inspeção restrita a uma edificação (relatório por edificação). Respostas gerais (documentação) continuam. */
export function scopeInspection(v: SpdaInspection, b?: SpdaBuilding): SpdaInspection {
  if (!b) return v;
  return {
    ...v,
    buildings: [b],
    checklist: v.checklist.filter(a => !a.buildingId || a.buildingId === b.id),
    continuity: v.continuity.filter(t => t.buildingId === b.id),
    grounding: v.grounding.filter(g => g.buildingId === b.id)
  };
}

// ------------------------------------------------------------------ checklist
const STATUS_COLS = ['C', 'NC', 'NA'] as const;

export const ChecklistReport: React.FC<{ inspection: SpdaInspection; building?: SpdaBuilding; groups: ContinuityGroup[]; blank?: boolean; withDocs?: boolean }> = ({ inspection: v, building: b, groups, blank, withDocs }) => {
  const company = readCompanyInfo();
  const subs: SpdaSubsystem[] = [...(withDocs ? ['documentacao' as SpdaSubsystem] : []), ...groups.flatMap(g => SUBSYSTEMS_OF[g])];
  const local = [v.address, v.city && `${v.city}${v.state ? `/${v.state}` : ''}`].filter(Boolean).join(' — ');
  let nc = 0;
  let evaluated = 0;
  if (!blank) {
    subs.forEach(sub => CHECKLIST_TEMPLATE.filter(i => i.subsystem === sub).forEach(item => {
      const a = answerOf(v.checklist, item.id, isPerBuilding(sub) ? b?.id : undefined);
      if (a.status) evaluated += 1;
      if (a.status === 'NC') nc += 1;
    }));
  }
  return (
    <div className="space-y-4 text-slate-800">
      <div className="border-b-2 border-[#0A2540] pb-3 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
        <div className="flex items-center gap-3 min-h-[48px]">
          {company?.logoUrl && <img src={company.logoUrl} alt="Logotipo" className="max-h-14 max-w-[150px] object-contain" />}
        </div>
        <div className="sm:text-right">
          <span className="text-[10px] font-bold uppercase tracking-widest text-slate-400 block">{blank ? 'Checklist de campo' : 'Checklist da inspeção'}</span>
          <span className="text-base font-extrabold text-blue-700 font-mono block">{v.number}-CHK</span>
          {v.serviceOrderNumber && <span className="text-xs font-semibold text-slate-700 font-mono block">OS: {v.serviceOrderNumber}</span>}
          <span className="text-[11px] text-slate-500 block">{blank ? 'Data: ____/____/______' : `Inspeção: ${formatDateBR(v.inspectionDate)}`}</span>
        </div>
      </div>
      <div className="text-center">
        <h1 className="font-black text-sm sm:text-base text-[#0A2540] uppercase tracking-wide">Checklist de inspeção visual do SPDA</h1>
        <p className="text-[11px] text-slate-500">ABNT NBR 5419-3:2026 (Seção 7) e NBR 5419-4:2026 · {groups.length === GROUPS.length ? 'todos os subsistemas' : groups.map(g => CONTINUITY_GROUP_INFO[g].label).join(', ')}</p>
      </div>
      <Section n={1} title="Identificação" right={blank ? undefined : `${evaluated} item(ns) avaliado(s) · ${nc} NC`}>
        <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
          <Info label="Cliente" value={v.clientName} />
          <Info label="Edificação" value={b?.name || buildingsOf(v).map(x => x.name).join(', ')} />
          <Info label="Local" value={local} />
          <Info label="Ordem de serviço" value={v.serviceOrderNumber} />
          <Info label="Inspeção" value={v.number} />
          <Info label="Inspetor" value={blank ? undefined : v.technicianName} />
        </div>
      </Section>
      {subs.map((sub, si) => {
        const items = CHECKLIST_TEMPLATE.filter(i => i.subsystem === sub);
        if (!items.length) return null;
        return (
          <Section key={sub} n={si + 2} title={SUBSYSTEM_LABEL[sub]}>
            <table className="w-full border-collapse">
              <thead>
                <tr>
                  <th className={`${th} w-14`}>Item</th><th className={th}>Verificação</th><th className={`${th} w-24`}>Referência</th>
                  {STATUS_COLS.map(c => <th key={c} className={`${th} w-8 text-center`}>{c}</th>)}
                  <th className={`${th} ${blank ? 'w-40' : 'w-36'}`}>Observação</th>
                </tr>
              </thead>
              <tbody>
                {items.map(item => {
                  const a = blank ? undefined : answerOf(v.checklist, item.id, isPerBuilding(sub) ? b?.id : undefined);
                  return (
                    <tr key={item.id} className="spda-avoid">
                      <td className={`${td} font-mono text-[10px]`}>{item.id}</td>
                      <td className={td}>{item.title}</td>
                      <td className={`${td} text-[9px] text-slate-600`}>{item.reference}</td>
                      {STATUS_COLS.map(c => (
                        <td key={c} className={`${td} text-center font-black ${c === 'NC' ? 'text-red-700' : c === 'C' ? 'text-emerald-700' : 'text-slate-600'}`}>
                          {blank ? <span className="inline-block w-3 h-3 border border-slate-500" /> : a?.status === c ? 'X' : ''}
                        </td>
                      ))}
                      <td className={`${td} text-[10px]`}>{blank ? '' : a?.obs || ''}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </Section>
        );
      })}
      <p className="text-[10px] text-slate-500">C = conforme · NC = não conforme · NA = não aplicável{blank ? '' : ' · em branco = não avaliado'}</p>
      <div className="spda-avoid grid grid-cols-2 gap-6 pt-8 text-center text-[11px]">
        <div>
          <div className="border-t border-slate-500 pt-1 font-bold">{blank ? 'Inspetor' : v.technicianName || 'Responsável pela inspeção'}</div>
          <div className="text-slate-500">{blank ? 'Nome / registro' : [v.technicianTitle, v.technicianCrea].filter(Boolean).join(' — ') || 'Executado por'}</div>
        </div>
        <div>
          <div className="border-t border-slate-500 pt-1 font-bold">{blank ? 'Acompanhante (cliente)' : company?.technicalResponsible?.name || 'Responsável técnico'}</div>
          <div className="text-slate-500">{blank ? 'Nome / cargo' : 'Responsável técnico'}</div>
        </div>
      </div>
    </div>
  );
};

// ------------------------------------------------------------------ saída
const KIND_SHORT: Record<ReportKind, string> = {
  tecnico: 'Relatório técnico', laudo: 'Laudo', simplificado: 'Relatório simplificado', fotografico: 'Relatório fotográfico',
  checklist: 'Checklist', checklist_branco: 'Checklist em branco'
};

/** Abre o relatório escolhido (tipo, subsistema e abrangência) para ver, imprimir, salvar PDF ou Word. */
export const ReportOutput: React.FC<{ inspection: SpdaInspection; kind: ReportKind; group: ReportGroup; buildingId: string; onClose: () => void }> = ({ inspection, kind, group, buildingId, onClose }) => {
  const all = buildingsOf(inspection);
  const one = buildingId ? all.find(b => b.id === buildingId) : undefined;
  const list: Array<SpdaBuilding | undefined> = one ? [one] : all.length ? all : [undefined];
  const groups = group === 'todos' ? GROUPS : [group];
  const v = one ? scopeInspection(inspection, one) : inspection;
  const scopeTxt = one ? ` — ${one.name}` : all.length > 1 ? ' — todas as edificações' : '';
  const groupTxt = KIND_SHORT[kind] && REPORT_KINDS.find(k => k.v === kind)?.groups && group !== 'todos' ? ` — ${CONTINUITY_GROUP_INFO[group].label}` : '';
  const title = `${KIND_SHORT[kind]}${groupTxt}${scopeTxt} — ${inspection.number}`;

  let body: React.ReactNode;
  if (kind === 'tecnico') body = <TechReport inspection={v} />;
  else if (kind === 'laudo') body = <SpdaReport inspection={v} />;
  else if (kind === 'checklist' || kind === 'checklist_branco') {
    body = list.map((b, i) => (
      <div key={b?.id || 'geral'} className={i > 0 ? 'spda-break pt-4' : ''}>
        <ChecklistReport inspection={inspection} building={b} groups={groups} blank={kind === 'checklist_branco'} withDocs={group === 'todos' && i === 0} />
      </div>
    ));
  } else {
    // simplificado / fotográfico: por edificação, um subsistema após o outro (só o que tem conteúdo)
    let photoNo = 1;
    const parts: React.ReactNode[] = [];
    list.forEach(b => groups.forEach(g => {
      if (kind === 'simplificado') {
        const has = sheetTests(inspection, b, g).length > 0 || (g === 'aterramento' && inspection.grounding.some(x => (!b || x.buildingId === b.id) && groundingValue(x) !== null));
        if (has || groups.length === 1) parts.push(<ContinuitySheet key={`${b?.id}_${g}`} inspection={inspection} building={b} group={g} />);
      } else {
        const n = photoEntries(inspection, b, g).length;
        if (n || groups.length === 1) parts.push(<PhotoReport key={`${b?.id}_${g}`} inspection={inspection} building={b} group={g} startAt={photoNo} />);
        photoNo += n;
      }
    }));
    body = parts.length
      ? parts.map((p, i) => <div key={i} className={i > 0 ? 'spda-break pt-4' : ''}>{p}</div>)
      : <p className="text-slate-500 text-sm">{kind === 'simplificado' ? 'Nenhuma medição lançada' : 'Nenhuma foto lançada'} para a seleção.</p>;
  }

  return (
    <PrintOverlay title={title} documentTitle={title.replace(/ — /g, ' - ')} onClose={onClose}>
      {body}
    </PrintOverlay>
  );
};
