/**
 * Relatório técnico do SPDA (menu Relatório), no padrão "RT" de folhas numeradas:
 * capa · controle do documento e sumário · 1 resumo executivo · 2 objetivo e escopo · 3 referências normativas ·
 * 4 caracterização da edificação · 5 metodologia (inspeção, continuidade, queda de potencial, critérios,
 * instrumentos e condições) · 6 folhas de ensaio e relatórios fotográficos por subsistema (por edificação) ·
 * 7 não conformidades e recomendações · 8 conclusão e parecer · 9 responsabilidade técnica e anexos.
 * Todas as folhas têm o cabeçalho (empresa, documento, revisão, folha) e o rodapé do documento.
 */
import React from 'react';
import { CHECKLIST_TEMPLATE, SUBSYSTEM_LABEL } from '../checklist';
import { readCompanyInfo } from '../repository';
import {
  BUILDING_TYPE_LABEL, INSPECTION_KIND_LABEL, levelParams, protectionLabel, answerOf, buildingArea,
  buildingPerimeter, buildingsOf, continuityCode, continuityStatus, fmtMilliOhm, fmtOhm, formatCoordinates, fop3Plateau, groundingValue,
  isPerBuilding, listImprovements, airTerminationText, downConductorText, groundingSystemText, listNonConformities, mapUrl, minDownConductors, nextInspectionDate, probeDistances, spdaKindText,
  type ContinuityGroup, type Improvement, type NonConformity, type Verdict
} from '../rules';
import type { GroundingTest, SpdaBuilding, SpdaInspection } from '../types';
import { laudoIssued, laudoNumber, RESULT_LABEL, defaultSubsystemResultText, finalResult, subsystemResult, subsystemText, techText, worstResult, type ShownResult } from '../techReport';
import { formatDateBR } from '../../../utils/dateUtils';
import { docLabel } from '../cnpjLookup';
import { readServiceOrders as listOrders } from '../repository';
import { ContinuityDiagram, FallOfPotentialDiagram } from './MethodDiagrams';
import { SUBSYSTEMS_OF, sheetTests } from './ContinuitySheet';
import { cleanCaption, photoEntries, type PhotoEntry } from './PhotoReport';
import { PlateauChart } from './PlateauChart';
import { describeSubsystems, type SubsystemKey } from '../systemDescriptions';
import { AirTerminationArt } from './AirTerminationArt';
import { DownConductorArt } from './DownConductorArt';
import { GroundingSystemArt } from './GroundingSystemArt';
import type { AirTerminationType, DownConductorType, GroundingSystemType } from '../types';

// ------------------------------------------------------------------ estilos (padrão do modelo RT)
const NAVY = '#0A2540';
const ACC = '#9a3412'; // laranja-terra dos números de seção e dos títulos auxiliares
const th = 'border border-slate-300 px-2 py-1 text-left font-bold bg-slate-100 text-[10px] uppercase tracking-[0.05em] text-[#0A2540] align-bottom';
const td = 'border border-slate-300 px-2 py-[3px] align-top text-[10.5px] leading-[1.3] break-words';
const lbl = 'text-[9px] font-normal uppercase tracking-[0.12em] text-slate-500';

const VERDICT_SHORT: Record<ShownResult, string> = RESULT_LABEL;
/** Painel claro (fundo + borda) */
const VERDICT_SOFT: Record<ShownResult, string> = {
  aprovado: 'bg-emerald-50 border-emerald-600 text-emerald-800', reprovado: 'bg-red-50 border-red-600 text-red-800',
  parcial: 'bg-amber-50 border-amber-500 text-amber-800', incompleto: 'bg-slate-50 border-slate-400 text-slate-700'
};
/** Selo contornado (fundo branco) */
const VERDICT_BADGE: Record<ShownResult, string> = {
  aprovado: 'border-emerald-700 text-emerald-800', reprovado: 'border-red-700 text-red-700', parcial: 'border-amber-600 text-amber-700', incompleto: 'border-slate-500 text-slate-600'
};
/** Etiqueta pequena (tabelas) */
const CHIP: Record<'ok' | 'bad' | 'na' | 'warn' | 'info', string> = {
  ok: 'bg-emerald-50 border-emerald-400 text-emerald-800', bad: 'bg-red-50 border-red-400 text-red-700', na: 'bg-slate-50 border-slate-300 text-slate-600',
  warn: 'bg-amber-50 border-amber-400 text-amber-800', info: 'bg-sky-50 border-sky-300 text-sky-800'
};
const verdictTone = (v: ShownResult) => (v === 'aprovado' ? 'ok' : v === 'reprovado' ? 'bad' : v === 'parcial' ? 'warn' : 'na') as keyof typeof CHIP;
const Chip: React.FC<{ tone: keyof typeof CHIP; children: React.ReactNode }> = ({ tone, children }) => (
  <span className={`inline-block border rounded px-1.5 py-px text-[10px] font-bold tracking-wide whitespace-nowrap ${CHIP[tone]}`}>{children}</span>
);

const GROUPS: ContinuityGroup[] = ['captacao', 'descida', 'aterramento', 'dps'];
const GROUP_TITLE: Record<ContinuityGroup, string> = {
  captacao: 'Captação', descida: 'Descida', aterramento: 'Aterramento', dps: 'Equipotencialização e DPS'
};
const GROUP_SUB: Record<ContinuityGroup, string> = {
  captacao: 'Captação (NBR 5419-3, 5.3, 5.6, 5.7)',
  descida: 'Descida e conexões (NBR 5419-3, 5.4, 5.6.3)',
  aterramento: 'Aterramento (NBR 5419-3, 5.5)',
  dps: 'Equipotencialização / DPS (NBR 5419-3, 6.2; NBR 5419-4)'
};
const SKETCH_KIND: Record<ContinuityGroup, 'captacao' | 'descida' | 'aterramento' | null> = { captacao: 'captacao', descida: 'descida', aterramento: 'aterramento', dps: null };
const SOIL: Record<string, string> = { seco: 'Seco', umido: 'Úmido', encharcado: 'Encharcado' };
const LIMIT_OHM = 0.2;

const num = (x: number, d = 1) => x.toLocaleString('pt-BR', { minimumFractionDigits: d, maximumFractionDigits: d });
const pctLim = (ohm: number | null | undefined) => (typeof ohm === 'number' ? `${num((ohm / LIMIT_OHM) * 100)} %` : '—');
const mOhm = (ohm: number) => fmtMilliOhm(ohm, 1);
const hhmm = (iso?: string) => (iso ? new Date(iso).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' }) : '');
const pad2 = (n: number) => String(n).padStart(2, '0');
const listPt = (xs: string[]) => (xs.length <= 1 ? xs.join('') : `${xs.slice(0, -1).join(', ')} e ${xs[xs.length - 1]}`);
const photoRefs = (ns: number[]) => (ns.length === 0 ? '' : ns.length === 1 ? `Foto ${ns[0]}` : `Fotos ${listPt(ns.map(String))}`);
/** Valor ainda não informado ("a preencher"): itálico com sublinhado tracejado, como no modelo. */
const Fill: React.FC<{ text: string }> = ({ text }) => (/a preencher/.test(text)
  ? <span className="italic text-slate-600 border-b border-dashed border-slate-400">{text}</span>
  : <>{text}</>);

const Para: React.FC<{ text: string }> = ({ text }) => (
  <div className="space-y-2 text-[11px] leading-[1.5] text-slate-800 text-justify hyphens-auto" lang="pt-BR">
    {text.split(/\n{2,}/).filter(p => p.trim()).map((p, i) => <p key={i} className="whitespace-pre-line">{p}</p>)}
  </div>
);
const H1: React.FC<{ n: string; children: React.ReactNode; right?: React.ReactNode }> = ({ n, children, right }) => (
  <div className="flex items-end justify-between gap-3 border-b border-slate-300 pb-1.5 mb-2.5 mt-3 first:mt-0">
    <h2 className="flex items-baseline gap-5 font-bold text-[14px] uppercase tracking-[0.06em]" style={{ color: NAVY }}>
      {n && <span className="min-w-[1.2em]" style={{ color: ACC }}>{n}</span>}{children}
    </h2>
    {right && <span className="text-[10.5px] text-slate-600">{right}</span>}
  </div>
);
const H2: React.FC<{ n: string; children: React.ReactNode }> = ({ n, children }) => (
  <h3 className="font-bold text-[12px] mt-3 mb-1" style={{ color: NAVY }}><span className="mr-2" style={{ color: ACC }}>{n}</span>{children}</h3>
);
const Cap: React.FC<{ children: React.ReactNode }> = ({ children }) => <p className="text-[10px] text-slate-500 mt-1.5 leading-snug">{children}</p>;
const TableTitle: React.FC<{ n: string; children: React.ReactNode }> = ({ n, children }) => (
  <p className="text-[11px] text-slate-700 mt-3 mb-1"><b className="text-slate-900">{n} —</b> {children}</p>
);
/** Barra azul com letra (A, B, C…) e o conteúdo em moldura. */
const Block: React.FC<{ letter?: string; title: string; right?: React.ReactNode; children: React.ReactNode }> = ({ letter, title, right, children }) => (
  <div className="mt-2 spda-block">
    <div className="flex items-center justify-between gap-2 text-white px-3 py-1.5 rounded-t" style={{ background: NAVY }}>
      <span className="flex items-center gap-2.5 text-[11.5px] font-semibold uppercase tracking-[0.08em]">
        {letter && <span className="inline-flex items-center justify-center w-[18px] h-[18px] rounded-sm bg-white text-[10px] font-black" style={{ color: NAVY }}>{letter}</span>}
        {title}
      </span>
      {right && <span className="text-[10.5px] tracking-wide text-white/90">{right}</span>}
    </div>
    <div className="border border-t-0 border-slate-300 rounded-b p-2">{children}</div>
  </div>
);
/** Grade de campos com linhas internas finas e moldura azul (cabeçalho das folhas e capa). */
const FieldGrid: React.FC<{ cols: number; children: React.ReactNode; className?: string }> = ({ cols, children, className }) => (
  <div className={`grid gap-px bg-slate-300 border rounded-sm overflow-hidden ${className || ''}`} style={{ gridTemplateColumns: `repeat(${cols}, minmax(0, 1fr))`, borderColor: NAVY }}>{children}</div>
);
const Field: React.FC<{ label: string; value?: React.ReactNode; className?: string; mono?: boolean }> = ({ label, value, className, mono }) => (
  <div className={`bg-white px-2.5 py-1 ${className || ''}`}>
    <p className={lbl}>{label}</p>
    <p className={`text-[11.5px] font-semibold text-slate-900 leading-snug ${mono ? 'font-mono' : ''}`}>{typeof value === 'string' ? <Fill text={value} /> : value || '—'}</p>
  </div>
);
/** Painel do parecer: selo contornado + texto, em fundo claro. */
const VerdictPanel: React.FC<{ verdict: ShownResult; label?: string; children: React.ReactNode }> = ({ verdict, label, children }) => (
  <div className={`flex items-center gap-5 rounded border px-5 py-3 spda-avoid ${VERDICT_SOFT[verdict]}`}>
    <span className={`shrink-0 bg-white border-2 rounded-sm px-4 py-2 text-[19px] font-black tracking-[0.08em] text-center ${VERDICT_BADGE[verdict]}`}>{label || VERDICT_SHORT[verdict]}</span>
    <div className="text-slate-800">{children}</div>
  </div>
);

/* Impressão: margens do modelo, fundo branco e rodapé no pé de cada folha. */
const RT_PRINT_CSS = `
@media print {
  @page { size: A4 portrait; margin: 11mm 13mm 9mm 13mm; }
  html, body { background: #ffffff !important; }
  .rt-page { min-height: 274mm; }
  .rt-page.rt-cover { min-height: 275mm; }
}`;

type Ctx = {
  v: SpdaInspection; company: ReturnType<typeof readCompanyInfo>; docNo: string; rev: string; total: number; buildingsTxt: string;
};
/** Folha: cabeçalho (empresa · título · documento/rev./folha), conteúdo e rodapé no pé da folha. */
const Sheet: React.FC<{ ctx: Ctx; folha: number; subtitle?: string; first?: boolean; children: React.ReactNode }> = ({ ctx, folha, subtitle, first, children }) => {
  const c = ctx.company;
  return (
    <section className={`rt-page ${first ? 'rt-cover' : 'spda-break'} flex flex-col pt-1 pb-1`}>
      {/* a capa (folha 01) não leva cabeçalho nem rodapé */}
      {!first && (
        <div className="flex items-start justify-between gap-4 pb-2 mb-3 border-b" style={{ borderColor: NAVY }}>
          <div className="flex items-center gap-2.5 shrink-0 whitespace-nowrap">
            {c?.logoUrl
              ? <img src={c.logoUrl} alt={c.name} className="max-h-10 max-w-[80px] object-contain" />
              : <span className="w-10 h-10 rounded-md flex items-center justify-center text-white font-black text-[11px] shrink-0" style={{ background: NAVY }}>JVM</span>}
            <div className="leading-tight">
              <p className="text-[12.5px] font-bold" style={{ color: NAVY }}>{c?.name || 'Empresa'}</p>
              {c?.cnpj && <p className="text-[10px] text-slate-500">{docLabel(c.cnpj)} {c.cnpj}</p>}
            </div>
          </div>
          <div className="text-center text-[10.5px] text-slate-500 leading-snug pt-0.5 min-w-0">
            <p>Relatório técnico de inspeção e ensaios do SPDA</p>
            <p>{subtitle || ctx.buildingsTxt}</p>
          </div>
          <table className="border-collapse shrink-0 text-[10.5px]">
            <tbody>
              <tr className="bg-slate-100 text-slate-600 uppercase tracking-[0.08em] text-[9.5px]">
                <td className="border border-slate-300 px-2.5 py-0.5">Documento</td><td className="border border-slate-300 px-2.5">Rev.</td><td className="border border-slate-300 px-2.5">Folha</td>
              </tr>
              <tr className="font-mono text-slate-800">
                <td className="border border-slate-300 px-2.5 py-1 whitespace-nowrap">{ctx.docNo}</td><td className="border border-slate-300 px-2.5">{ctx.rev}</td>
                <td className="border border-slate-300 px-2.5 whitespace-nowrap">{pad2(folha)}/{pad2(ctx.total)}</td>
              </tr>
            </tbody>
          </table>
        </div>
      )}
      <div className="flex-1">{children}</div>
      {!first && (
        <div className="flex justify-between gap-3 border-t border-slate-300 pt-1 mt-3 text-[10px] text-slate-500">
          <span>{[ctx.v.serviceOrderNumber, `Inspeção ${ctx.v.number}`].filter(Boolean).join(' · ')}</span>
          <span>Este documento só pode ser reproduzido na íntegra.</span>
        </div>
      )}
    </section>
  );
};

// ------------------------------------------------------------------ dados por subsistema
export type GroupData = {
  group: ContinuityGroup; b?: SpdaBuilding;
  items: Array<{ id: string; title: string; reference: string; status: string; obs?: string; photos: number[] }>;
  c: number; nc: number; na: number;
  tests: Array<{ code: string; label: string; ohm: number | null; ok: boolean; photos: number[] }>;
  grounding: Array<{ code: string; g: GroundingTest; photos: number[] }>;
  ncs: NonConformity[]; improvements: Improvement[]; verdict: Verdict; result: ShownResult; resultText: string; photos: Array<PhotoEntry & { n: number }>; when: string;
  /** subsistema avaliado: item do checklist avaliado, medição com valor, aterramento medido, NC ou melhoria registrada */
  hasData: boolean;
};

export function groupData(v: SpdaInspection, b: SpdaBuilding | undefined, group: ContinuityGroup, startPhoto: number, ncsAll: NonConformity[]): GroupData {
  // fotos dos sistemas existentes ficam só no capítulo 5 (descrição dos subsistemas)
  const photos = photoEntries(v, b, group).filter(p => p.source !== 'sistema').map((p, i) => ({ ...p, n: startPhoto + i }));
  const numOf = (pred: (p: PhotoEntry) => boolean) => photos.filter(pred).map(p => p.n);
  const items = CHECKLIST_TEMPLATE.filter(i => SUBSYSTEMS_OF[group].includes(i.subsystem)).map(i => {
    const a = answerOf(v.checklist, i.id, isPerBuilding(i.subsystem) ? b?.id : undefined);
    return { id: i.id, title: i.title, reference: i.reference.replace(/^ABNT /, '').replace(/^NBR 5419-3:2026,\s*/, ''), status: a.status, obs: a.obs, photos: numOf(p => p.source === 'inspecao' && p.key.startsWith(`${i.id}_`)) };
  }).filter(x => x.status);
  const tests = sheetTests(v, b, group).map(t => ({
    code: continuityCode(v.continuity, t), label: `${t.from || '?'} → ${t.to || '?'}`, ohm: t.resistanceOhm ?? null,
    ok: continuityStatus(t) === 'conforme', photos: numOf(p => p.source === 'continuidade' && (t.photos || []).some(x => x.id === p.key))
  }));
  const grounding = group === 'aterramento'
    ? v.grounding.filter(g => (!b || g.buildingId === b.id) && groundingValue(g) !== null).map((g, i) => ({ code: `ATE-R${pad2(i + 1)}`, g, photos: numOf(p => p.source === 'aterramento' && (g.photos || []).some(x => x.id === p.key)) }))
    : [];
  const subs = SUBSYSTEMS_OF[group];
  const ncs = ncsAll.filter(n => subs.includes(n.subsystem) && (!b || !n.buildingId || n.buildingId === b.id));
  const nc = items.filter(i => i.status === 'NC').length;
  const testsNc = tests.filter(t => !t.ok).length;
  const registered = ncs.filter(n => n.source === 'registro').length;
  const improvements = listImprovements(v, b?.id, subs);
  const verdict: Verdict = nc + testsNc + registered > 0 ? 'nao_conforme' : items.length || tests.length || grounding.length || improvements.length ? 'conforme' : 'incompleto';
  const stamps = [
    ...v.continuity.filter(t => sheetTests(v, b, group).some(x => x.id === t.id)).flatMap(t => (t.photos || []).map(p => p.timestamp)),
    ...grounding.flatMap(x => (x.g.photos || []).map(p => p.timestamp))
  ].filter(Boolean).sort();
  return {
    group, b, items, c: items.filter(i => i.status === 'C').length, nc, na: items.filter(i => i.status === 'NA').length,
    tests, grounding, ncs, improvements, verdict,
    result: subsystemResult(v, b?.id, group, verdict),
    resultText: subsystemText(v, 'res', b?.id, group, defaultSubsystemResultText(group, subsystemResult(v, b?.id, group, verdict), ncs.length)),
    photos, when: stamps[0] || '',
    hasData: items.length > 0 || tests.some(t => t.ohm !== null) || grounding.length > 0 || ncs.length > 0 || improvements.length > 0
  };
}

// ------------------------------------------------------------------ relatório
export const TechReport: React.FC<{ inspection: SpdaInspection }> = ({ inspection: v }) => {
  const company = readCompanyInfo();
  const rt = company?.technicalResponsible;
  const buildings = buildingsOf(v);
  const multi = buildings.length > 1;
  const ncsAll = listNonConformities(v);
  const improvementsAll = listImprovements(v);
  const verdict = finalResult(v);
  const order = (() => { try { return listOrders().find(o => o.id === v.serviceOrderId); } catch { return undefined; } })();
  const art = order?.artNumber?.trim() || 'nº a preencher';
  const local = [v.address, v.city && `${v.city}${v.state ? `/${v.state}` : ''}`].filter(Boolean).join(' — ');
  const docNo = `RT-${laudoNumber(v)}`;
  const rev = '00';
  const emissao = laudoIssued(v).date;
  const proxima = formatDateBR(nextInspectionDate(v));
  const kindTxt = (INSPECTION_KIND_LABEL[v.inspectionKind] || 'Periódica').split(' — ')[0];
  const techShort = v.technicianName || 'Técnico de campo';
  const rtLine = [rt?.title || 'Engenheiro Eletricista', `CREA nº ${rt?.creaNumber || 'a preencher'}`].join(' · ');
  const buildingsTxt = buildings.map(b => b.name).filter(Boolean).join(' · ') || v.clientName || '';

  // dados de cada edificação × subsistema, com a numeração contínua das fotos
  let photoNo = 1;
  const perBuilding = (buildings.length ? buildings : [undefined]).map(b => ({
    b,
    groups: GROUPS.flatMap(g => {
      const d = groupData(v, b, g, photoNo, ncsAll);
      if (!d.hasData) return [];
      photoNo += d.photos.length;
      return [d];
    })
  }));
  const allGroups = perBuilding.flatMap(x => x.groups);
  const sumItems = allGroups.reduce((a, d) => ({ n: a.n + d.items.length, c: a.c + d.c, nc: a.nc + d.nc, na: a.na + d.na }), { n: 0, c: 0, nc: 0, na: 0 });
  const docItems = CHECKLIST_TEMPLATE.filter(i => i.subsystem === 'documentacao').map(i => answerOf(v.checklist, i.id)).filter(a => a.status);
  const allTests = allGroups.flatMap(d => d.tests.map(t => ({ ...t, group: d.group })));
  const okTests = allTests.filter(t => t.ok).length;
  const maxTest = allTests.filter(t => t.ohm !== null).sort((a, b) => (b.ohm! - a.ohm!))[0];
  const allGround = allGroups.flatMap(d => d.grounding);
  const gVals = allGround.map(x => groundingValue(x.g)!).sort((a, b) => a - b);
  const allPlateau = allGround.length > 0 && allGround.every(x => x.g.method !== 'queda_potencial' || fop3Plateau(x.g)?.ok);
  const instrCont = v.instruments.find(i => i.use === 'continuidade');
  const instrAte = v.instruments.find(i => i.use === 'aterramento');
  const instrTxt = (i?: typeof instrCont) => (i ? [i.description?.split(' — ')[0], [i.model, i.serialNumber].filter(Boolean).join(' · '), i.calibrationCert && `cal. ${i.calibrationCert}`].filter(Boolean).join(' · ') : 'não informado');
  const firstStamp = allGroups.map(d => d.when).filter(Boolean).sort()[0];
  const dataHora = `${formatDateBR(v.inspectionDate)}${firstStamp ? ` · ${hhmm(firstStamp)}` : ''}`;
  const temp = v.weather?.match(/-?\d+(?:[.,]\d+)?\s*°\s*C/)?.[0];

  // ---------------------------------------------- monta as folhas (para numerar e montar o sumário)
  type PageDef = { key: string; subtitle?: string; alias?: string; render: (folha: number) => React.ReactNode };
  const pages: PageDef[] = [];
  const toc: Array<{ n: string; title: string; key: string; level: 1 | 2; until?: string }> = [];
  const add = (p: PageDef) => pages.push(p);

  add({ key: 'capa', render: () => <Cover /> });
  add({ key: 'controle', render: () => <Control /> });
  toc.push({ n: '1', title: 'Resumo executivo', key: 'resumo', level: 1 });
  add({ key: 'resumo', render: () => <Summary /> });
  toc.push({ n: '2', title: 'Objetivo e escopo', key: 'escopo', level: 1 }, { n: '3', title: 'Referências normativas', key: 'escopo', level: 1 },
    { n: '4', title: multi ? 'Caracterização das edificações' : 'Caracterização da edificação', key: 'escopo', level: 1 });
  add({ key: 'escopo', render: () => <Scope /> });
  // capítulo 5 — subsistemas evidenciados na inspeção (após a caracterização)
  toc.push({ n: '5', title: 'Descrição dos subsistemas evidenciados na inspeção', key: 'subsistemas', level: 1 });
  add({ key: 'subsistemas', render: () => <Subsystems /> });
  toc.push({ n: '6', title: 'Metodologia', key: 'metodo1', level: 1 },
    { n: '6.1', title: 'Inspeção visual', key: 'metodo1', level: 2 },
    { n: '6.2', title: 'Continuidade elétrica — descidas e eletrodo de aterramento', key: 'metodo1', level: 2 },
    { n: '6.3', title: 'Resistência de aterramento — queda de potencial', key: 'metodo1', level: 2 },
    { n: '6.4', title: 'Critérios de aceitação', key: 'metodo2', level: 2 },
    { n: '6.5', title: 'Instrumentos utilizados', key: 'metodo2', level: 2 },
    { n: '6.6', title: 'Condições do ensaio', key: 'metodo2', level: 2 });
  add({ key: 'metodo1', render: () => <Method1 /> });
  add({ key: 'metodo2', render: () => <Method2 /> });
  toc.push({ n: '7', title: 'Folhas de ensaio e relatórios fotográficos', key: 'sec6', level: 1 });
  if (allGroups.length === 0) {
    add({ key: 'sec6', render: () => (
      <>
        <H1 n="7">Folhas de ensaio e relatórios fotográficos</H1>
        <p className="text-[11px] text-slate-600">Nenhum subsistema foi avaliado ou teve medições registradas nesta inspeção.</p>
      </>
    ) });
  }
  perBuilding.forEach(({ b, groups }, bi) => {
    const base = multi ? `7.${bi + 1}` : '7';
    const bName = b?.name || `Edificação ${bi + 1}`;
    if (multi && b) {
      const key = `ed_${b.id}`;
      if (bi === 0) toc[toc.length - 1].key = key;
      toc.push({ n: base, title: `${bName} — caracterização, captação, descida, aterramento e DPS`, key, level: 2, until: `__last_${b.id}` });
      add({ key, subtitle: bName, render: () => <BuildingIntro b={b} n={base} /> });
    }
    groups.forEach((d, gi) => {
      const n = `${base}.${gi + 1}`;
      const key = `f_${b?.id || 'g'}_${d.group}`;
      if (!multi) {
        if (gi === 0) toc[toc.length - 1].key = key;
        toc.push({ n, title: `${GROUP_TITLE[d.group]} — ${d.group === 'aterramento' ? 'continuidade · resistência · fotos' : 'folha de ensaio · fotos'}`, key, level: 2,
          until: d.photos.length ? `${key}_f` : d.group === 'aterramento' && d.grounding.length ? `${key}_r` : undefined });
      }
      add({ key, subtitle: bName, render: () => <TestSheet d={d} n={n} part={d.group === 'aterramento' ? 'continuidade' : undefined} /> });
      if (d.group === 'aterramento' && d.grounding.length) add({ key: `${key}_r`, subtitle: bName, render: () => <TestSheet d={d} n={n} part="resistencia" /> });
      if (d.photos.length) add({ key: `${key}_f`, subtitle: bName, render: () => <PhotoSheet d={d} n={n} /> });
    });
    if (multi && b) pages[pages.length - 1] = { ...pages[pages.length - 1], alias: `__last_${b.id}` };
  });
  toc.push({ n: '8', title: 'Não conformidades e recomendações', key: 'final', level: 1 },
    { n: '9', title: 'Conclusão e parecer técnico', key: 'final', level: 1 },
    { n: '10', title: 'Responsabilidade técnica e anexos', key: 'final', level: 1 });
  add({ key: 'final', render: () => <Final /> });
  const folhaOf = (key: string) => pages.findIndex(p => p.key === key || p.alias === key) + 1;
  const folhaTxt = (t: { key: string; until?: string }) => {
    const a = folhaOf(t.key), b = t.until ? folhaOf(t.until) : a;
    return b > a ? `${a}–${b}` : String(a);
  };
  const ctx: Ctx = { v, company, docNo, rev, total: pages.length, buildingsTxt };

  // ---------------------------------------------- folhas
  function Cover() {
    return (
      <div className="flex flex-col min-h-[255mm] relative">
        <div className="-mx-1 h-2 rounded-sm" style={{ background: NAVY, boxShadow: `0 3px 0 ${ACC}` }} />
        <div className="flex items-start justify-between gap-3 mt-9">
          <div className="flex items-center gap-3">
            {company?.logoUrl
              ? <img src={company.logoUrl} alt={company.name} className="max-h-14 max-w-[150px] object-contain" />
              : <span className="w-14 h-14 rounded-lg flex items-center justify-center text-white font-black text-[15px]" style={{ background: NAVY }}>JVM</span>}
            <div>
              <p className="font-bold text-[17px]" style={{ color: NAVY }}>{company?.name || 'Empresa'}</p>
              {company?.cnpj && <p className="text-[11px] text-slate-500">{docLabel(company.cnpj)} {company.cnpj}</p>}
            </div>
          </div>
        </div>
        <div className="mt-24">
          <p className="text-[11.5px] font-bold uppercase tracking-[0.3em]" style={{ color: ACC }}>Relatório técnico · {docNo}</p>
          <h1 className="mt-3 font-black text-[33px] leading-[1.15]" style={{ color: NAVY }}>{v.reportTitle?.trim() || <>Inspeção e ensaios do Sistema de<br />Proteção contra Descargas<br />Atmosféricas — SPDA</>}</h1>
          <p className="mt-3 text-[12px] text-slate-600">ABNT NBR 5419:2026, Partes 1 a 4 · ABNT NBR 15749:2009</p>
          <div className="mt-9">
            <p className="font-bold text-[18px]" style={{ color: NAVY }}>{buildings.map(b => b.name).filter(Boolean).join(' · ') || '—'}</p>
            <p className="text-[13px] text-slate-800 mt-1">{v.clientName}</p>
            <p className="text-[13px] text-slate-800">{local}</p>
          </div>
        </div>
        <div className="flex-1" />
        <FieldGrid cols={4} className="mt-10 border-slate-300" >
          <Field label="Cliente" value={v.clientName} className="col-span-2" />
          <Field label={v.clientCnpj ? `${docLabel(v.clientCnpj)} do cliente` : 'CNPJ do cliente'} value={v.clientCnpj} mono />
          <div className={`row-span-2 px-2.5 py-1.5 flex flex-col items-center justify-center text-center ${VERDICT_SOFT[verdict]}`}>
            <p className="text-[9.5px] uppercase tracking-[0.12em] text-slate-600">Parecer</p>
            <p className="text-[17px] font-black tracking-[0.06em]">{VERDICT_SHORT[verdict]}</p>
          </div>
          <Field label="Ordem de serviço" value={v.serviceOrderNumber} mono />
          <Field label="Inspeção" value={v.number} mono />
          <Field label="Tipo de inspeção" value={kindTxt} />
          <Field label="Data da inspeção" value={formatDateBR(v.inspectionDate)} mono />
          <Field label="Data de emissão" value={formatDateBR(emissao)} mono />
          <Field label="Próxima inspeção" value={proxima} mono />
          <Field label="Revisão · folhas" value={`${rev} · ${pages.length}`} mono />
          <Field label="Responsável técnico" value={rt?.name ? `${rt.name} · ${rtLine}` : rtLine} className="col-span-2" />
          <Field label="ART" value={art} className="col-span-2" />
        </FieldGrid>
      </div>
    );
  }

  function Control() {
    return (
      <>
        <H1 n="">Controle do documento</H1>
        <p className="text-[11.5px] font-semibold text-slate-800 mb-1.5">Histórico de revisões</p>
        <table className="w-full border-collapse mb-3">
          <thead><tr><th className={`${th} w-12`}>Rev.</th><th className={th}>Data</th><th className={th}>Descrição</th><th className={th}>Elaborado</th><th className={th}>Verificado</th><th className={th}>Aprovado</th></tr></thead>
          <tbody>
            <tr><td className={`${td} font-mono`}>{rev}</td><td className={`${td} font-mono`}>{formatDateBR(emissao)}</td><td className={td}>Emissão inicial</td><td className={td}>{techShort}</td><td className={td}>—</td><td className={td}>{rt?.name || 'Resp. técnico'}</td></tr>
            <tr><td className={`${td} h-7`} /><td className={td} /><td className={td} /><td className={td} /><td className={td} /><td className={td} /></tr>
          </tbody>
        </table>
        <p className="text-[11.5px] font-semibold text-slate-800 mb-1.5">Distribuição</p>
        <table className="w-full border-collapse mb-5">
          <thead><tr><th className={th}>Destinatário</th><th className={th}>Meio</th><th className={`${th} w-16 text-center`}>Cópias</th></tr></thead>
          <tbody>
            <tr><td className={td}>{v.clientName || 'Cliente'} — contratante</td><td className={td}>PDF assinado digitalmente</td><td className={`${td} text-center`}>1</td></tr>
            <tr><td className={td}>{company?.name || 'Empresa'} — arquivo técnico</td><td className={td}>PDF</td><td className={`${td} text-center`}>1</td></tr>
          </tbody>
        </table>
        <H1 n="">Sumário</H1>
        <ol className="text-[12px] mt-1">
          {toc.map((t, i) => (
            <li key={i} className={`flex items-baseline gap-3 ${t.level === 2 ? 'pl-10 py-[3px] text-slate-700' : 'py-[5px] font-semibold'}`} style={t.level === 1 ? { color: NAVY } : undefined}>
              <span className={t.level === 1 ? 'w-12 shrink-0 font-bold' : 'w-12 shrink-0 font-mono text-slate-500'} style={t.level === 1 ? { color: ACC } : undefined}>{t.n}</span>
              <span>{t.title}</span>
              <span className="flex-1 border-b border-dotted border-slate-400 translate-y-[-3px]" /><span className={t.level === 1 ? 'font-bold' : ''}>{folhaTxt(t)}</span>
            </li>
          ))}
        </ol>
      </>
    );
  }

  function Summary() {
    const action = verdict === 'reprovado' || verdict === 'parcial'
      ? `Executar as ações corretivas das ${ncsAll.length} não conformidade(s) listadas na Seção 8, nos prazos indicados, e reinspecionar o SPDA após a correção. Próxima inspeção periódica até ${proxima} (ABNT NBR 5419-3:2026, 7.3.2 f).`
      : `Nenhuma ação corretiva. Manter o plano de manutenção preventiva e realizar a próxima inspeção periódica até ${proxima} (ABNT NBR 5419-3:2026, 7.3.2 f). Inspeções extraordinárias são necessárias após descarga atmosférica na estrutura, reformas ou alterações de uso.`;
    const intro = techText(v, 'resumo');
    const groupRows = GROUPS.filter(g => allGroups.some(d => d.group === g)).map(g => {
      const ds = allGroups.filter(d => d.group === g);
      const ts = ds.flatMap(d => d.tests).filter(t => t.ohm !== null);
      const vals = ts.map(t => t.ohm! * 1000).sort((a, b) => a - b);
      const ver = worstResult(ds.map(d => d.result));
      return {
        g, n: ds.reduce((a, d) => a + d.items.length, 0), c: ds.reduce((a, d) => a + d.c, 0), nc: ds.reduce((a, d) => a + d.nc, 0), na: ds.reduce((a, d) => a + d.na, 0),
        ok: ts.filter(t => t.ok).length, tot: ts.length, range: vals.length ? `${num(vals[0])}–${num(vals[vals.length - 1])} mΩ` : '—', ver
      };
    });
    return (
      <>
        <H1 n="1">Resumo executivo</H1>
        <VerdictPanel verdict={verdict}><p className="text-[12px] leading-[1.6]">{intro}</p></VerdictPanel>
        <div className="grid grid-cols-4 mt-3 border border-slate-300 rounded divide-x divide-slate-300 spda-avoid">
          <Kpi label="Itens inspecionados" value={String(sumItems.n)} sub={`${sumItems.c} C · ${sumItems.nc} NC · ${sumItems.na} NA${docItems.length ? ` (+${docItems.length} DOC)` : ''}`} />
          <Kpi label="Ensaios de continuidade" value={`${okTests}/${allTests.length}`} sub="aprovados (≤ 200 mΩ)" />
          <Kpi label="Maior continuidade" value={maxTest ? mOhm(maxTest.ohm!) : '—'} sub={maxTest ? `${maxTest.code} · ${pctLim(maxTest.ohm)} do limite` : 'sem ensaios'} />
          <Kpi label="Resist. de aterramento" value={gVals.length ? (gVals.length > 1 ? `${num(gVals[0], 2)}–${num(gVals[gVals.length - 1], 2)} Ω` : fmtOhm(gVals[0], 2)) : '—'} sub={gVals.length ? `${allPlateau ? 'patamar verificado' : 'patamar não verificado em todos'} · informativo` : 'não medida'} />
        </div>
        <TableTitle n="Tabela 1">Quadro-resumo por subsistema</TableTitle>
        <table className="w-full border-collapse">
          <thead><tr><th className={th}>Subsistema</th><th className={`${th} text-center`}>Itens</th><th className={`${th} text-center`}>C</th><th className={`${th} text-center`}>NC</th><th className={`${th} text-center`}>NA</th><th className={`${th} text-center`}>Ensaios</th><th className={th}>Faixa medida</th><th className={th}>Situação</th></tr></thead>
          <tbody>
            {groupRows.map((r, i) => (
              <tr key={r.g}>
                <td className={td}>{multi ? '' : `7.${i + 1} `}{GROUP_TITLE[r.g]}</td>
                <td className={`${td} text-center`}>{r.n}</td><td className={`${td} text-center`}>{r.c}</td><td className={`${td} text-center ${r.nc ? 'text-red-700 font-bold' : ''}`}>{r.nc}</td><td className={`${td} text-center`}>{r.na}</td>
                <td className={`${td} text-center`}>{r.tot ? `${r.ok}/${r.tot}` : '—'}</td><td className={`${td} font-mono text-right whitespace-nowrap`}>{r.range}</td>
                <td className={td}><Chip tone={verdictTone(r.ver)}>{VERDICT_SHORT[r.ver]}</Chip></td>
              </tr>
            ))}
            <tr className="font-bold bg-slate-50">
              <td className={td}>Total</td><td className={`${td} text-center`}>{sumItems.n}</td><td className={`${td} text-center`}>{sumItems.c}</td><td className={`${td} text-center`}>{sumItems.nc}</td><td className={`${td} text-center`}>{sumItems.na}</td>
              <td className={`${td} text-center`}>{allTests.length ? `${okTests}/${allTests.length}` : '—'}</td><td className={`${td} text-right`}>—</td><td className={td}><Chip tone={verdictTone(verdict)}>{VERDICT_SHORT[verdict]}</Chip></td>
            </tr>
          </tbody>
        </table>
        <Cap>C = conforme · NC = não conforme · NA = não aplicável. Ensaios = ensaios de continuidade aprovados / realizados.{multi ? ' Valores somados de todas as edificações.' : ''} A documentação (itens DOC) é geral da OS e não entra no quadro.</Cap>
        {allGround.length > 0 && (
          <>
            <TableTitle n="Tabela 2">Resistência de aterramento (queda de potencial, ABNT NBR 15749)</TableTitle>
            <GroundTable list={allGround} withBuilding={multi} />
            <Cap>Valor adotado = leitura a 62 %. Medição informativa (ABNT NBR 5419-3:2026, 7.1.4); a integridade do eletrodo é comprovada pelo ensaio de continuidade.</Cap>
          </>
        )}
        <p className="font-bold text-[12px] text-slate-900 mt-6 mb-1.5">Ações requeridas</p>
        <Para text={action} />
      </>
    );
  }

  function Scope() {
    return (
      <>
        <H1 n="2">Objetivo e escopo</H1>
        <Para text={techText(v, 'introducao')} />
        <H1 n="3">Referências normativas</H1>
        <table className="w-full border-collapse">
          <tbody>
            {[
              ['ABNT NBR 5419-1:2026', 'Proteção contra descargas atmosféricas — Parte 1: Princípios gerais'],
              ['ABNT NBR 5419-2:2026', 'Parte 2: Gerenciamento de risco'],
              ['ABNT NBR 5419-3:2026', 'Parte 3: Danos físicos a estruturas e perigos à vida — inspeção (Seção 7) e ensaios (Anexo F)'],
              ['ABNT NBR 5419-4:2026', 'Parte 4: Sistemas elétricos e eletrônicos internos na estrutura — DPS'],
              ['ABNT NBR 15749:2009', 'Medição de resistência de aterramento e de potenciais na superfície do solo em sistemas de aterramento'],
              ['ABNT NBR IEC 61643-11', 'Dispositivos de proteção contra surtos de baixa tensão — Parte 11: requisitos e ensaios'],
              ['NR-10', 'Segurança em instalações e serviços em eletricidade']
            ].map(([a, b]) => <tr key={a}><td className={`${td} font-bold w-40 whitespace-nowrap`}>{a}</td><td className={td}>{b}</td></tr>)}
          </tbody>
        </table>
        <H1 n="4">{multi ? 'Caracterização das edificações' : 'Caracterização da edificação'}</H1>
        {multi ? (
          <>
            <table className="w-full border-collapse">
              <thead><tr><th className={th}>Edificação</th><th className={th}>Tipo / ocupação</th><th className={th}>Dimensões (A × C × L)</th><th className={th}>Nível</th><th className={`${th} text-center`}>Descidas</th></tr></thead>
              <tbody>
                {buildings.map(b => (
                  <tr key={b.id}>
                    <td className={`${td} font-semibold`}>{b.name}</td><td className={td}>{BUILDING_TYPE_LABEL[b.type]}{b.activity ? ` — ${b.activity}` : ''}</td>
                    <td className={td}>{dims(b)}</td><td className={td}>{b.protectionLevel ? protectionLabel(b.protectionLevel) : "—"}</td><td className={`${td} text-center`}>{b.downConductorsCount ?? '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <Cap>A caracterização completa, a localização e a imagem aérea de cada edificação estão no início da respectiva parte da Seção 7.</Cap>
          </>
        ) : buildings[0] ? <Characterization b={buildings[0]} fig="Figura 3" /> : <p className="text-slate-500 text-[10px]">Nenhuma edificação cadastrada.</p>}
      </>
    );
  }

  function dims(b: SpdaBuilding) {
    const m = (x?: number | null) => (typeof x === 'number' ? `${x.toLocaleString('pt-BR')} m` : '—');
    return `${m(b.heightM)} × ${m(b.lengthM)} × ${m(b.widthM)}${b.floors ? ` · ${b.floors} pavimento(s)` : ''}`;
  }

  function Characterization({ b, fig }: { b: SpdaBuilding; fig: string }) {
    const lp = levelParams(b.protectionLevel);
    const area = buildingArea(b);
    const min = minDownConductors(b);
    const per = buildingPerimeter(b);
    const url = b.mapsLink || mapUrl(b.latitude, b.longitude);
    const ateObs = answerOf(v.checklist, 'ATE-05', b.id).obs;
    const capObs = answerOf(v.checklist, 'CAP-03', b.id).obs;
    const rows: Array<[string, React.ReactNode]> = [
      ['Tipo / ocupação', `${BUILDING_TYPE_LABEL[b.type]}${b.activity ? ` — ${b.activity}` : ''}`],
      ['Dimensões (A × C × L)', dims(b)],
      ['Área em planta', area !== null ? `${area.toLocaleString('pt-BR', { maximumFractionDigits: 1 })} m²` : '—'],
      ['SPDA existente', `${spdaKindText(b)}${capObs ? ` — ${capObs}` : ''}`],
      ['Sistemas de captação', `${airTerminationText(b) || '—'}${b.airTermination?.notes?.trim() ? ` — ${b.airTermination.notes.trim()}` : ''}`],
      ['Tipos de descida', `${downConductorText(b) || '—'}${b.downConductor?.notes?.trim() ? ` — ${b.downConductor.notes.trim()}` : ''}`],
      ['Nível de proteção', b.protectionLevel && lp ? `${protectionLabel(b.protectionLevel)} · esfera rolante R = ${lp.sphereRadiusM} m · malha ${lp.meshM} m · espaçamento das descidas ${lp.downSpacingM} m` : 'Não definido'],
      ['Descidas', `${b.downConductorsCount ?? '—'} existente(s)${min !== null ? ` · mínimo exigido: ${min}${per !== null ? ` (perímetro ${per.toLocaleString('pt-BR')} m ÷ ${lp.downSpacingM} m)` : ''}` : ''}${min !== null && b.downConductorsCount != null && b.downConductorsCount < min ? ' — abaixo do mínimo' : ''}`],
      ['Aterramento', [groundingSystemText(b), b.groundingSystem?.notes?.trim(), ateObs].filter(Boolean).join(' — ') || '—'],
      ['Endereço', local || '—'],
      ['Coordenadas (WGS 84)', formatCoordinates(b.latitude, b.longitude) || '—']
    ];
    return (
      <>
        <table className="w-full border-collapse">
          <tbody>{rows.map(([k, val]) => <tr key={k}><th className={`${th} w-44 normal-case text-[10px]`}>{k}</th><td className={td}>{val}</td></tr>)}</tbody>
        </table>
        {b.aerialImage && (
          <figure className="spda-avoid mt-2">
            <img src={b.aerialImage} alt={`Imagem aérea — ${b.name}`} className="w-full max-h-[72mm] object-contain border border-slate-300 rounded bg-white" />
            <Cap><b>{fig}</b> — Imagem aérea da edificação{formatCoordinates(b.latitude, b.longitude) ? ` · ${formatCoordinates(b.latitude, b.longitude)} (WGS 84)` : ''}{url ? <> · <a href={url} className="text-blue-700 underline">Abrir no mapa</a></> : null}</Cap>
          </figure>
        )}
      </>
    );
  }

  function BuildingIntro({ b, n }: { b: SpdaBuilding; n: string }) {
    return (
      <>
        <H1 n={n}>{b.name}</H1>
        <p className="font-bold text-[11px] mb-1" style={{ color: NAVY }}>Caracterização e localização</p>
        <Characterization b={b} fig={`Figura ${n}`} />
      </>
    );
  }

  function Subsystems() {
    let photoN = 0;
    const art = (key: SubsystemKey, type: string, label: string) => (
      key === 'captacao' ? <AirTerminationArt type={type as AirTerminationType} label={label} className="w-full h-auto" />
        : key === 'descida' ? <DownConductorArt type={type as DownConductorType} label={label} className="w-full h-auto" />
          : key === 'aterramento' ? <GroundingSystemArt type={type as GroundingSystemType} label={label} className="w-full h-auto" /> : null
    );
    return (
      <>
        <H1 n="5">Descrição dos subsistemas evidenciados na inspeção</H1>
        <p className="text-[11px] leading-[1.5] text-slate-800 text-justify mb-1">
          Descrição dos sistemas de captação, descida, aterramento e equipotencialização encontrados em campo, conforme a seleção feita
          pelo inspetor durante a inspeção, com as características de cada tipo e o registro fotográfico do sistema existente.
        </p>
        {buildings.map((b, bi) => (
          <div key={b.id}>
            {multi && <H2 n={`5.${bi + 1}`}>{b.name || `Edificação ${bi + 1}`}</H2>}
            {describeSubsystems(v, b).filter(sd => !sd.empty || allGroups.some(d => d.b?.id === b.id && d.group === sd.key)).map((sd, si) => {
              const n = multi ? `5.${bi + 1}.${si + 1}` : `5.${si + 1}`;
              return (
                <div key={sd.key} className="mt-2">
                  <H2 n={n}>{sd.title}</H2>
                  <p className={`text-[11px] leading-[1.5] text-justify ${sd.empty ? 'text-slate-500 italic' : 'text-slate-800'}`}>{subsystemText(v, 'desc', b.id, sd.key, sd.intro)}</p>
                  {sd.items.map(it => (
                    <div key={it.type} className="mt-2 border border-slate-300 rounded">
                      <div className="spda-avoid flex gap-3 p-2.5">
                        <div className="w-[34mm] shrink-0 border border-slate-200 rounded bg-slate-50 overflow-hidden">{art(sd.key, it.type, it.name)}</div>
                        <div className="flex-1 min-w-0">
                          <p className="font-bold text-[11.5px]" style={{ color: NAVY }}>{it.name}</p>
                          <p className="text-[10.5px] leading-[1.45] text-slate-800 text-justify mt-0.5">{it.text}</p>
                          {!it.photos.length && <p className="text-[10px] text-slate-500 italic mt-1">Sem registro fotográfico deste sistema.</p>}
                        </div>
                      </div>
                      {it.photos.length > 0 && (
                        <div className="grid grid-cols-2 gap-3 px-2.5 pb-2.5">
                          {it.photos.map(p => {
                            photoN += 1;
                            return (
                              <figure key={p.id} className="spda-avoid border border-slate-300 rounded p-2 bg-white">
                                <div className="h-[48mm] flex items-center justify-center bg-white overflow-hidden">
                                  <img src={p.url} alt={p.caption} className="max-w-full max-h-full object-contain" />
                                </div>
                                <figcaption className="mt-1.5 text-[10.5px] leading-snug text-slate-800">
                                  <b className="text-slate-900">Foto 5.{photoN} —</b> {cleanCaption(p.caption) || it.name}
                                </figcaption>
                              </figure>
                            );
                          })}
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              );
            })}
          </div>
        ))}
      </>
    );
  }

  function Method1() {
    const step: 5 | 10 = v.grounding.some(g => g.probeStepPct === 5) ? 5 : 10;
    const extra = techText(v, 'metodoContinuidadeAterramento');
    return (
      <>
        <H1 n="6">Metodologia</H1>
        <H2 n="6.1">Inspeção visual</H2>
        <Para text={techText(v, 'metodoInspecao')} />
        <H2 n="6.2">Continuidade elétrica — descidas e eletrodo de aterramento</H2>
        <Para text={techText(v, 'metodoContinuidade')} />
        {extra && <div className="mt-1.5"><Para text={extra} /></div>}
        <figure className="spda-avoid mt-2">
          <div className="grid grid-cols-2 gap-2">
            <div className="border border-slate-300 rounded p-1 bg-white"><ContinuityDiagram target="descida" /><p className="text-[8.5px] text-center text-slate-600">(a) Descidas — trecho entre descidas adjacentes pela captação</p></div>
            <div className="border border-slate-300 rounded p-1 bg-white"><ContinuityDiagram target="aterramento" /><p className="text-[8.5px] text-center text-slate-600">(b) Malha de aterramento — trecho entre caixas de inspeção adjacentes</p></div>
          </div>
          <Cap><b>Figura 1</b> — Ensaio de continuidade a 4 fios (ABNT NBR 5419-3:2026, Anexo F): (a) descidas, medidas a partir do terminal superior do conector de ensaio; (b) malha de aterramento, medida a partir do terminal inferior, entre caixas de inspeção adjacentes. R = V/I ≤ 200 mΩ. Esquema ilustrativo.</Cap>
        </figure>
        <H2 n="6.3">Resistência de aterramento — queda de potencial</H2>
        <Para text={techText(v, 'metodoAterramento')} />
        <figure className="spda-avoid mt-2">
          <div className="border border-slate-300 rounded p-1 bg-white max-w-[118mm] mx-auto"><FallOfPotentialDiagram step={step} /></div>
          <Cap><b>Figura 2</b> — Método da queda de potencial (ABNT NBR 15749): corrente injetada entre E e C; tensão medida entre E e P. Esquema ilustrativo.</Cap>
        </figure>
      </>
    );
  }

  function Method2() {
    return (
      <>
        <H2 n="6.4">Critérios de aceitação</H2>
        <table className="w-full border-collapse">
          <thead><tr><th className={th}>Grandeza</th><th className={th}>Critério</th><th className={th}>Referência</th></tr></thead>
          <tbody>
            <tr><td className={td}>Continuidade elétrica (captação, descidas, eletrodo, equipotencialização)</td><td className={td}>R ≤ 200 mΩ (0,2 Ω)</td><td className={td}>NBR 5419-3:2026, F.4.4</td></tr>
            <tr><td className={td}>Corrente de ensaio de continuidade</td><td className={td}>1 A a 5 A, 4 fios</td><td className={td}>NBR 5419-3:2026, F.1.3</td></tr>
            <tr><td className={td}>Patamar da queda de potencial</td><td className={td}>R52, R72 dentro de ±5 % de R62</td><td className={td}>NBR 15749:2009</td></tr>
            <tr><td className={td}>Resistência de aterramento</td><td className={td}>Informativa — sem limite normativo</td><td className={td}>NBR 5419-3:2026, 7.1.4</td></tr>
            <tr><td className={td}>Itens de inspeção visual</td><td className={td}>Atendimento ao requisito citado em cada item</td><td className={td}>NBR 5419-3:2026, Seção 7</td></tr>
          </tbody>
        </table>
        <H2 n="6.5">Instrumentos utilizados</H2>
        <table className="w-full border-collapse">
          <thead><tr><th className={th}>Uso</th><th className={th}>Instrumento</th><th className={th}>Modelo / nº de série</th><th className={th}>Certificado de calibração</th><th className={th}>Validade</th></tr></thead>
          <tbody>
            {v.instruments.length ? v.instruments.map((i, k) => (
              <tr key={k}>
                <td className={td}>{i.use === 'continuidade' ? 'Continuidade' : i.use === 'aterramento' ? 'Aterramento' : i.use}</td>
                <td className={td}>{i.description || '—'}</td>
                <td className={td}>{[i.model, i.serialNumber].filter(Boolean).join(' · ') || '—'}</td>
                <td className={td}>{[i.calibrationCert, i.calibrationDate && formatDateBR(i.calibrationDate)].filter(Boolean).join(' · ') || '—'}</td>
                <td className={td}>{i.calibrationExpiry ? formatDateBR(i.calibrationExpiry) : '—'}</td>
              </tr>
            )) : <tr><td className={td} colSpan={5}>Instrumentos não informados na inspeção.</td></tr>}
          </tbody>
        </table>
        <Cap>Certificados de calibração no Anexo B.</Cap>
        <H2 n="6.6">Condições do ensaio</H2>
        <table className="w-full border-collapse">
          <thead><tr><th className={th}>Data / horário</th><th className={th}>Condição do tempo</th><th className={th}>Condição do solo</th><th className={th}>Temperatura</th><th className={th}>Equipe</th></tr></thead>
          <tbody><tr>
            <td className={td}>{dataHora}</td><td className={td}>{v.weather?.trim() || 'a preencher'}</td><td className={td}>{(v.soilCondition && SOIL[v.soilCondition]) || 'a preencher'}</td>
            <td className={td}>{temp || '—'}</td><td className={td}>{techShort} + RT</td>
          </tr></tbody>
        </table>
      </>
    );
  }

  function SheetHead({ d, n, title, crit, method, instrument }: { d: GroupData; n: string; title: string; crit: string; method: string; instrument: string }) {
    return (
      <>
        <div className="flex items-end justify-between gap-3 mb-2">
          <div>
            <p className="text-[11px] font-bold uppercase tracking-[0.18em]" style={{ color: ACC }}>{n} · Folha de ensaio</p>
            <p className="font-bold text-[21px] uppercase tracking-[0.03em] leading-tight" style={{ color: NAVY }}>{title}</p>
          </div>
          <span className={`text-[13px] font-bold tracking-[0.08em] px-4 py-1.5 rounded border ${VERDICT_SOFT[d.result]}`}>{VERDICT_SHORT[d.result]}</span>
        </div>
        <FieldGrid cols={4}>
          <Field label="Edificação" value={d.b?.name} />
          <Field label="Subsistema" value={GROUP_SUB[d.group]} />
          <Field label="Data / hora" value={`${formatDateBR(v.inspectionDate)}${d.when ? ` · ${hhmm(d.when)}` : ''}`} mono />
          <Field label="Executante" value={techShort} />
          <Field label="Instrumento" value={instrument} className="col-span-2" />
          <Field label="Método" value={method} />
          <Field label="Critério" value={crit} />
        </FieldGrid>
      </>
    );
  }

  function Croqui({ d, caption }: { d: GroupData; caption: string }) {
    const kind = SKETCH_KIND[d.group];
    const imgs = kind && d.b ? d.b.sketches?.[kind]?.images || [] : [];
    const notes = kind && d.b ? d.b.sketches?.[kind]?.notes?.trim() : '';
    if (!imgs.length) return null; // sem arquivo de croqui: nada no lugar
    return (
      <figure className="spda-avoid">
        {imgs.map(p => <img key={p.id} src={p.url} alt={p.caption} className="w-full max-h-[40mm] object-contain border border-slate-300 rounded bg-white mb-1" />)}
        <Cap>{caption}{notes ? ` · ${notes}` : ''}</Cap>
      </figure>
    );
  }

  function NcBlock({ d, letter, extra }: { d: GroupData; letter: string; extra?: string }) {
    const okTxt = d.group === 'aterramento'
      ? `Continuidade do eletrodo ≤ 200 mΩ em todos os trechos${d.grounding.length ? `; patamar de resistência ${d.grounding.every(x => x.g.method !== 'queda_potencial' || fop3Plateau(x.g)?.ok) ? 'verificado' : 'não verificado em todos os pontos'} em ${listPt(d.grounding.map(x => x.g.point.split(' (')[0]))}` : ''}.`
      : d.group === 'dps' ? 'Ligações de equipotencialização com continuidade ≤ 200 mΩ.' : `Todos os ${d.group === 'descida' ? 'trechos' : 'pontos'} com continuidade ≤ 200 mΩ (Anexo F).`;
    return (
      <>
        <Block letter={letter} title="Não conformidades e observações">
          {d.ncs.length === 0
            ? <p className="text-[12px] text-slate-800">Nenhuma não conformidade.{d.tests.length ? ` ${okTxt}` : ''}{extra ? ` ${extra}` : ''}</p>
            : (
              <ul className="list-disc pl-5 text-[10.5px] leading-snug space-y-0.5">
                {d.ncs.map((x, i) => <li key={i} className="text-red-800"><b>{x.description}</b>{x.obs ? ` — ${x.obs}` : ''} <span className="text-slate-500">({x.reference})</span>{x.correctiveAction?.trim() && <span className="block text-slate-700"><b>Ação de correção:</b> {x.correctiveAction.trim()}</span>}</li>)}
              </ul>
            )}
          {d.improvements.length > 0 && (
            <>
              <p className="text-[11px] font-bold text-slate-900 mt-2">Melhorias apontadas</p>
              <ul className="list-disc pl-5 text-[10.5px] leading-snug space-y-0.5">
                {d.improvements.map(m => <li key={m.code} className="text-sky-900"><b>{m.code}</b>{m.comment ? ` — ${m.comment}` : ''}{m.normClause ? <span className="text-slate-500"> ({m.normClause})</span> : null}{m.photos ? <span className="text-slate-500"> ({m.photos} foto(s))</span> : null}{m.correctiveAction && <span className="block text-slate-700"><b>Ação recomendada:</b> {m.correctiveAction}</span>}</li>)}
              </ul>
            </>
          )}
        </Block>
        <div className={`mt-3 border rounded px-4 py-2.5 flex items-center gap-5 spda-avoid ${VERDICT_SOFT[d.result]}`}>
          <span className="text-[10px] uppercase tracking-[0.12em] text-slate-600 shrink-0">Resultado do subsistema</span>
          <span className="font-black text-[18px] tracking-[0.08em] shrink-0">{VERDICT_SHORT[d.result]}</span>
          <span className="text-[11.5px] text-slate-800 whitespace-pre-line">{d.resultText}</span>
        </div>
      </>
    );
  }

  function TestSheet({ d, n, part }: { d: GroupData; n: string; part?: 'continuidade' | 'resistencia' }) {
    const isRes = part === 'resistencia';
    const title = d.group === 'aterramento' ? `Aterramento — ${isRes ? 'resistência' : 'continuidade'}` : GROUP_TITLE[d.group];
    const testPhotos = d.tests.flatMap(t => t.photos);
    const sk = SKETCH_KIND[d.group];
    const hasSketch = !!(sk && d.b?.sketches?.[sk]?.images?.length);
    if (isRes) {
      const step: 5 | 10 = d.grounding.some(x => x.g.probeStepPct === 5) ? 5 : 10;
      const gPhotos = d.grounding.flatMap(x => x.photos);
      return (
        <>
          <SheetHead d={d} n={n} title={title} crit="Patamar ±5 % de R62 · informativo" method={`Queda de potencial · ${62 - step}/62/${62 + step} %`} instrument={instrTxt(instrAte)} />
          <Block letter="B" title="Medições e croqui dos pontos" right={`${d.grounding.length} ponto(s) · patamar ${d.grounding.every(x => x.g.method !== 'queda_potencial' || fop3Plateau(x.g)?.ok) ? 'verificado' : 'não verificado em todos'}`}>
            <GroundTable list={d.grounding} />
            <Cap>Valor adotado = leitura a 62 % (negrito). Patamar: leituras a {62 - step} % e {62 + step} % dentro de ±5 % de R62. Medição informativa (NBR 5419-3, 7.1.4).{gPhotos.length ? ` ${photoRefs(gPhotos)}.` : ''}</Cap>
            <figure className="spda-avoid border border-slate-300 rounded p-1 bg-white mt-2 max-w-[84mm] mx-auto">
              <FallOfPotentialDiagram step={step} />
              <Cap>Croqui — posição dos eletrodos E (ensaiado), P (potencial) e C (corrente). Eletrodos auxiliares alinhados, afastados da edificação e de outras malhas enterradas. Esquema fora de escala.</Cap>
            </figure>
          </Block>
          {d.grounding.some(x => x.g.method === 'queda_potencial') && (
            <Block letter="C" title="Curvas da queda de potencial">
              <div className={`grid gap-2 ${d.grounding.length > 1 ? 'grid-cols-2' : 'grid-cols-1'}`}>
                {d.grounding.filter(x => x.g.method === 'queda_potencial').map(x => (
                  <div key={x.g.id} className="spda-avoid border border-slate-300 rounded p-1 bg-white">
                    <p className="text-[9.5px] font-bold" style={{ color: NAVY }}>{x.code} · {x.g.point} · R62 = {fmtOhm(x.g.r62, 2)}</p>
                    <div className={`${d.grounding.length > 1 ? 'max-w-[80%]' : 'max-w-[55%]'} mx-auto`}><PlateauChart t={x.g} print bare /></div>
                  </div>
                ))}
              </div>
              <Cap><b>Figura {multi ? `${n}.R` : '4'}</b> — Curvas R(x); pontos medidos sobre a curva teórica ajustada à leitura a 62 %.</Cap>
            </Block>
          )}
          <NcBlock d={d} letter="D" />
        </>
      );
    }
    return (
      <>
        <SheetHead d={d} n={n} title={title} crit="R ≤ 200 mΩ (F.4.4)" method="4 fios · 1 A a 5 A (Anexo F)" instrument={instrTxt(instrCont)} />
        <Block letter="A" title="Inspeção visual" right={`${d.items.length} itens · ${d.c} C · ${d.nc} NC · ${d.na} NA`}>
          {d.items.length === 0 ? <p className="text-[10px] text-slate-500">Nenhum item avaliado.</p> : (
            <table className="w-full border-collapse">
              <thead><tr><th className={`${th} w-[54px]`}>Item</th><th className={th}>Verificação</th><th className={`${th} w-[84px]`}>Ref. NBR 5419-3</th><th className={`${th} w-[42px] text-center`}>Sit.</th><th className={`${th} w-[160px]`}>Observação</th></tr></thead>
              <tbody>
                {d.items.map(i => (
                  <tr key={i.id} className={i.status === 'NC' ? 'bg-red-50' : ''}>
                    <td className={`${td} font-mono whitespace-nowrap`}>{i.id}</td><td className={`${td} !text-[10px]`}>{i.title}</td><td className={`${td} !text-[9.5px] text-slate-500`}>{i.reference}</td>
                    <td className={`${td} text-center`}><Chip tone={i.status === 'NC' ? 'bad' : i.status === 'C' ? 'ok' : 'na'}>{i.status}</Chip></td>
                    <td className={td}>{[i.obs, photoRefs(i.photos)].filter(Boolean).join('. ')}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </Block>
        <Block letter="B" title={hasSketch ? (d.group === 'aterramento' ? 'Continuidade do eletrodo e croqui dos pontos' : 'Medições e croqui dos pontos') : (d.group === 'aterramento' ? 'Continuidade do eletrodo' : 'Medições')} right={d.tests.length ? `${d.tests.filter(t => t.ok).length} aprovados · ${d.tests.filter(t => !t.ok).length} reprovados` : undefined}>
          <div className={hasSketch ? 'grid grid-cols-[1.55fr_1fr] gap-2.5 items-start' : ''}>
            <div>
              {d.tests.length === 0 ? <p className="text-[10px] text-slate-500">Nenhuma medição de continuidade lançada.</p> : (
                <table className="w-full border-collapse">
                  <thead><tr><th className={th}>Código</th><th className={th}>Pontos de medição</th><th className={`${th} text-right`}>Valor</th><th className={th}>Result.</th></tr></thead>
                  <tbody>
                    {d.tests.map(t => (
                      <tr key={t.code} className={t.ok ? '' : 'bg-red-50'}>
                        <td className={`${td} font-mono whitespace-nowrap`}>{t.code}</td><td className={`${td} !text-[10px]`}>{t.label}</td>
                        <td className={`${td} text-right font-mono whitespace-nowrap`}>{t.ohm !== null ? mOhm(t.ohm) : '—'}</td>
                        <td className={`${td} font-bold ${t.ok ? 'text-emerald-700' : 'text-red-700'}`}>{t.ok ? 'Aprovado' : 'Reprovado'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
              <Cap>Limite 200 mΩ.{testPhotos.length ? ` ${photoRefs(testPhotos)} (relatório fotográfico).` : ''}</Cap>
            </div>
            <Croqui d={d} caption={`Croqui — ${GROUP_TITLE[d.group].toLowerCase()}: pontos e trechos medidos`} />
          </div>
        </Block>
        {d.group === 'aterramento' && d.grounding.length > 0
          ? <p className="text-[9.5px] text-slate-500 mt-2">Resistência de aterramento, curvas e resultado do subsistema na folha seguinte.</p>
          : <NcBlock d={d} letter="C" />}
      </>
    );
  }

  function PhotoSheet({ d, n }: { d: GroupData; n: string }) {
    const groups: Array<{ title: string; list: GroupData['photos'] }> = d.group === 'aterramento'
      ? [{ title: 'Inspeção visual e continuidade', list: d.photos.filter(p => p.source !== 'aterramento') }, { title: 'Resistência de aterramento', list: d.photos.filter(p => p.source === 'aterramento') }]
      : [{ title: 'Inspeção visual', list: d.photos.filter(p => p.source === 'inspecao') }, { title: 'Ensaios de continuidade', list: d.photos.filter(p => p.source === 'continuidade') }];
    // não conformidades e melhorias registradas (fotos + comentário); sistemas existentes ficam no capítulo 5
    if (d.group === 'aterramento') groups[0] = { ...groups[0], list: groups[0].list.filter(p => p.source !== 'registro') };
    groups.push({ title: 'Não conformidades e melhorias', list: d.photos.filter(p => p.source === 'registro') });
    return (
      <>
        <div className="flex items-end justify-between gap-3 mb-2">
          <div>
            <p className="text-[11px] font-bold uppercase tracking-[0.18em]" style={{ color: ACC }}>{n} · Relatório fotográfico</p>
            <p className="font-bold text-[21px] uppercase tracking-[0.03em] leading-tight" style={{ color: NAVY }}>{GROUP_TITLE[d.group]}</p>
          </div>
          <span className="text-[12px] font-bold text-slate-800 border border-slate-300 bg-slate-50 rounded px-3 py-1">{d.photos.length} foto(s)</span>
        </div>
        <FieldGrid cols={3} className="mb-1">
          <Field label="Edificação" value={d.b?.name} />
          <Field label="Subsistema" value={GROUP_TITLE[d.group]} />
          <Field label="Data do registro" value={formatDateBR(v.inspectionDate)} mono />
        </FieldGrid>
        {groups.filter(g => g.list.length).map(g => (
          <div key={g.title} className="mt-4">
            <div className="flex justify-between items-center text-white px-3 py-1.5 rounded-sm mb-2" style={{ background: NAVY }}>
              <p className="text-[11.5px] font-semibold uppercase tracking-[0.08em]">{g.title}</p>
              <span className="text-[10.5px] tracking-wide text-white/90">{g.list.length} foto(s)</span>
            </div>
            <div className="grid grid-cols-2 gap-3">
              {g.list.map(p => {
                const code = p.source === 'inspecao' ? p.key.split('_')[0] : p.source === 'registro' ? p.origin.match(/[A-Z]{3}-(?:NC|M)-\d+/)?.[0] : p.origin.match(/[A-Z]{3}-[CR]\d+/)?.[0] || (p.source === 'aterramento' ? d.grounding.find(x => (x.g.photos || []).some(y => y.id === p.key))?.code : '');
                return (
                  <figure key={p.key} className="spda-avoid border border-slate-300 rounded p-2.5 bg-white">
                    {code && <span className="inline-block text-[10.5px] font-mono font-bold text-white px-1.5 py-0.5 rounded-sm mb-1.5" style={{ background: NAVY }}>{code}</span>}
                    <div className="h-[52mm] flex items-center justify-center bg-white overflow-hidden">
                      <img src={p.url} alt={p.caption} className="max-w-full max-h-full object-contain" />
                    </div>
                    <figcaption className="mt-2 text-[11px] leading-snug">
                      <p className="text-slate-800"><b className="text-slate-900">Foto {p.n} —</b> {p.caption || p.origin}</p>
                      <p className="text-slate-500">{!p.caption ? (p.when || '') : p.source === 'inspecao' ? `Inspeção visual · ${code}` : p.source === 'registro' || p.source === 'sistema' ? `${p.origin}${p.when ? ` · ${p.when}` : ''}` : p.source === 'continuidade' ? `Continuidade · ${code}${p.when ? ` · ${p.when}` : ''}` : `Resistência · ${code}${p.when ? ` · ${p.when}` : ''}`}</p>
                      {p.detail && <p className={`font-semibold ${p.tone === 'bad' ? 'text-red-700' : p.tone === 'ok' ? 'text-emerald-700' : 'text-sky-700'}`}>{p.detail}</p>}
                    </figcaption>
                  </figure>
                );
              })}
            </div>
          </div>
        ))}
      </>
    );
  }

  function Final() {
    const actionOf = (x: NonConformity) => {
      const base = x.source === 'continuidade'
        ? { a: 'Revisar conexões, emendas e conectores do trecho; corrigir e repetir o ensaio de continuidade.', p: 'Alta', z: '30 dias' }
        : x.source === 'registro'
        ? { a: 'Corrigir conforme descrito e mostrado no registro fotográfico; registrar a correção com nova foto.', p: 'Média', z: '90 dias' }
        : { a: `Adequar ao requisito (${x.reference.replace(/^ABNT /, '')}) e registrar a correção.`, p: 'Média', z: '90 dias' };
      // ação de correção informada pelo inspetor tem prioridade sobre o texto automático
      return x.correctiveAction?.trim() ? { ...base, a: x.correctiveAction.trim() } : base;
    };
    return (
      <>
        <H1 n="8" right={`${ncsAll.length} não conformidade(s)`}>Não conformidades e recomendações</H1>
        <table className="w-full border-collapse">
          <thead><tr><th className={`${th} w-14`}>Item</th><th className={th}>Não conformidade</th><th className={th}>Ação recomendada</th><th className={`${th} w-16`}>Prioridade</th><th className={`${th} w-16`}>Prazo</th></tr></thead>
          <tbody>
            {ncsAll.length === 0
              ? <tr><td className={`${td} text-center`} colSpan={5}>Nenhuma não conformidade registrada.</td></tr>
              : ncsAll.map((x, i) => {
                const ac = actionOf(x);
                return (
                  <tr key={i}>
                    <td className={`${td} font-mono text-[9px]`}>{x.description.split(' — ')[0].split(' ')[0]}</td>
                    <td className={td}>{x.description}{x.obs ? ` — ${x.obs}` : ''}{multi && x.buildingName ? <span className="text-slate-500"> ({x.buildingName})</span> : null}</td>
                    <td className={td}>{ac.a}</td><td className={td}><Chip tone={ac.p === 'Alta' ? 'bad' : 'warn'}>{ac.p}</Chip></td><td className={td}>{ac.z}</td>
                  </tr>
                );
              })}
          </tbody>
        </table>
        {improvementsAll.length > 0 && (
          <>
            <p className="font-bold text-[12px] text-slate-900 mt-4 mb-1.5">Melhorias apontadas na inspeção</p>
            <table className="w-full border-collapse">
              <thead><tr><th className={`${th} w-20`}>Item</th>{multi && <th className={`${th} w-28`}>Edificação</th>}<th className={`${th} w-28`}>Subsistema</th><th className={th}>Melhoria recomendada</th></tr></thead>
              <tbody>
                {improvementsAll.map(m => (
                  <tr key={m.code + (m.buildingId || '')}>
                    <td className={`${td} font-mono text-[9px]`}>{m.code}</td>
                    {multi && <td className={td}>{m.buildingName || '—'}</td>}
                    <td className={td}>{SUBSYSTEM_LABEL[m.subsystem]}</td>
                    <td className={td}>{m.comment || 'Ver registro fotográfico.'}{m.normClause ? <span className="text-slate-500"> ({m.normClause})</span> : null}{m.photos ? <span className="text-slate-500"> ({m.photos} foto(s))</span> : null}{m.correctiveAction && <span className="block text-slate-700"><b>Ação recomendada:</b> {m.correctiveAction}</span>}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </>
        )}
        <p className="font-bold text-[12px] text-slate-900 mt-4 mb-1.5">Recomendações gerais</p>
        <Para text={techText(v, 'recomendacoes')} />

        <H1 n="9">Conclusão e parecer técnico</H1>
        <VerdictPanel verdict={verdict} label={`SPDA ${VERDICT_SHORT[verdict]}`}><Para text={techText(v, 'conclusao')} /></VerdictPanel>
        <Cap>Os resultados referem-se exclusivamente às condições observadas na data da inspeção. Partes ocultas ou inacessíveis (eletrodos enterrados, armaduras) foram avaliadas indiretamente pelos ensaios de continuidade.</Cap>

        <H1 n="10">Responsabilidade técnica e anexos</H1>
        <div className="grid grid-cols-2 gap-3">
          <table className="w-full border-collapse self-start">
            <tbody>
              <tr><th className={`${th} w-24`}>ART</th><td className={td}><Fill text={art} /></td></tr>
              <tr><th className={th}>Local e data</th><td className={td}>{[v.city && `${v.city}${v.state ? `/${v.state}` : ''}`, formatDateBR(emissao)].filter(Boolean).join(', ')}</td></tr>
            </tbody>
          </table>
          <table className="w-full border-collapse self-start">
            <tbody>
              <tr><th className={`${th} w-20`}>Anexo A</th><td className={td}>ART do responsável técnico</td></tr>
              <tr><th className={th}>Anexo B</th><td className={td}>Certificados de calibração dos instrumentos</td></tr>
              <tr><th className={th}>Anexo C</th><td className={td}>Planilhas de campo das medições</td></tr>
            </tbody>
          </table>
        </div>
        <div className="spda-avoid grid grid-cols-2 gap-10 pt-14 text-center text-[11px]">
          <div>
            <div className="border-t border-slate-500 pt-1 font-bold">{v.technicianName || 'Técnico de campo'}</div>
            <div className="text-slate-600">{[v.technicianTitle, v.technicianCrea].filter(Boolean).join(' — ')}</div>
            <div className="text-slate-500">Execução da inspeção e dos ensaios</div>
          </div>
          <div>
            {rt?.signatureUrl && <img src={rt.signatureUrl} alt="Assinatura do responsável técnico" className="h-12 mx-auto object-contain -mb-1" />}
            <div className="border-t border-slate-500 pt-1 font-bold">{rt?.name || 'Responsável técnico'}</div>
            <div className="text-slate-600">{rtLine}</div>
            <div className="text-slate-500">{company?.name}</div>
          </div>
        </div>
      </>
    );
  }

  function Kpi({ label, value, sub }: { label: string; value: string; sub: string }) {
    return (
      <div className="px-3 py-2.5">
        <p className={lbl}>{label}</p>
        <p className="text-[20px] font-bold font-mono leading-tight mt-0.5" style={{ color: NAVY }}>{value}</p>
        <p className="text-[10.5px] text-slate-600 mt-0.5">{sub}</p>
      </div>
    );
  }

  function GroundTable({ list, withBuilding }: { list: GroupData['grounding']; withBuilding?: boolean }) {
    const st = list.some(x => x.g.probeStepPct === 5) && list.every(x => x.g.probeStepPct === 5) ? 5 : 10;
    return (
      <table className="w-full border-collapse">
        <thead><tr>
          <th className={th}>{withBuilding ? 'Edificação · ponto' : 'Ponto'}</th><th className={`${th} text-right`}>D (EC)</th>
          <th className={`${th} text-right`}>R {62 - st} %</th><th className={`${th} text-right`}>R 62 %</th><th className={`${th} text-right`}>R {62 + st} %</th>
          <th className={`${th} text-right`}>Desvio máx.</th><th className={th}>Patamar</th>
        </tr></thead>
        <tbody>
          {list.map(({ code, g }) => {
            const pl = g.method === 'queda_potencial' ? fop3Plateau(g) : null;
            const step = g.probeStepPct || 10;
            const dist = probeDistances(g.currentProbeDistanceM, step);
            const bName = withBuilding ? buildings.find(b => b.id === g.buildingId)?.name : '';
            const cell = (r?: number | null, i?: number) => (
              <td className={`${td} text-right font-mono ${i === 1 ? 'font-black' : ''}`}>{typeof r === 'number' ? num(r, 2) : '—'}{dist && i !== undefined ? <span className="block text-[8px] text-slate-500 font-normal">{num(dist.m[i], 0)} m</span> : null}</td>
            );
            return (
              <tr key={g.id}>
                <td className={td}><span className="font-mono text-[10px] text-slate-500 mr-1.5">{code}</span>{bName ? `${bName} · ` : ''}{g.point}</td>
                <td className={`${td} text-right font-mono`}>{typeof g.currentProbeDistanceM === 'number' ? `${num(g.currentProbeDistanceM, 0)} m` : '—'}</td>
                {g.method === 'queda_potencial' ? <>{cell(g.r52, 0)}{cell(g.r62, 1)}{cell(g.r72, 2)}</> : <><td className={td} /><td className={`${td} text-right font-mono font-black`}>{num(groundingValue(g)!, 2)}</td><td className={td} /></>}
                <td className={`${td} text-right font-mono`}>{pl ? `${num(pl.maxDeviation * 100)} %` : '—'}</td>
                <td className={`${td} font-semibold ${pl ? (pl.ok ? 'text-sky-700' : 'text-amber-700') : 'text-slate-500'}`}>{pl ? (pl.ok ? 'Verificado' : 'Não verificado') : 'Leitura direta'}</td>
              </tr>
            );
          })}
        </tbody>
      </table>
    );
  }

  return (
    <div className="text-slate-800 text-[11.5px]">
      <style>{RT_PRINT_CSS}</style>
      {pages.map((p, i) => (
        <Sheet key={p.key} ctx={ctx} folha={i + 1} subtitle={p.subtitle} first={i === 0}>
          {p.render(i + 1)}
        </Sheet>
      ))}
    </div>
  );
};

