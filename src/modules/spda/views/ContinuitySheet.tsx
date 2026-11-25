/**
 * Relatório simplificado das medições de cada componente do SPDA (captação, descidas, aterramento e
 * equipotencialização/DPS), uma página A4 por edificação, no padrão visual do modelo de tabulação da empresa.
 * Todos os dados vêm da inspeção: medições lançadas, checklist do subsistema, equipamentos usados nos ensaios,
 * edificação (nível de proteção) e o croqui do próprio subsistema. Nada é digitado na folha.
 */
import React from 'react';
import { CHECKLIST_TEMPLATE } from '../checklist';
import { readCompanyInfo } from '../repository';
import {
  CONTINUITY_GROUP_INFO, levelParams, protectionLabel, RESISTIVITY, answerOf, buildingsOf, continuityCode, continuityGroupOf,
  continuityStatus, fmtMilliOhm, fmtOhm, fop3Plateau, groundingValue, isPerBuilding, type ContinuityGroup
} from '../rules';
import type { SketchKind, SpdaBuilding, SpdaInspection, SpdaSubsystem } from '../types';
import { formatDateBR } from '../../../utils/dateUtils';
import { Info, PrintOverlay, Section, td, th } from './ReportView';

export const SHEET_TITLE: Record<ContinuityGroup, string> = {
  captacao: 'Ensaio de Continuidade da Captação do SPDA',
  descida: 'Ensaio de Continuidade das Descidas do SPDA',
  aterramento: 'Ensaios do Subsistema de Aterramento do SPDA',
  dps: 'Ensaio de Continuidade da Equipotencialização (BEP / DPS)'
};
/** Croqui do próprio subsistema (DPS não tem bloco de croqui). */
const SKETCH_OF: Record<ContinuityGroup, SketchKind | null> = { captacao: 'captacao', descida: 'descida', aterramento: 'aterramento', dps: null };
/** Blocos do checklist de cada componente. */
export const SUBSYSTEMS_OF: Record<ContinuityGroup, SpdaSubsystem[]> = {
  captacao: ['captacao'], descida: ['descida'], aterramento: ['aterramento'], dps: ['equipotencializacao']
};
/** Item do checklist que trata do material de cada componente. */
const MATERIAL_ITEM: Record<ContinuityGroup, string | null> = { captacao: 'CAP-03', descida: 'DES-07', aterramento: 'ATE-05', dps: null };

/** Medições de continuidade da folha: do componente e da edificação, na ordem de lançamento. */
export function sheetTests(v: SpdaInspection, b: SpdaBuilding | undefined, group: ContinuityGroup) {
  // só ensaios realizados (com valor medido)
  return v.continuity.filter(t => continuityGroupOf(t) === group && (!b || t.buildingId === b.id) && continuityStatus(t) !== 'pendente');
}

/** Itens do checklist do componente com resposta NC (para as observações). */
function checklistNcs(v: SpdaInspection, b: SpdaBuilding | undefined, group: ContinuityGroup) {
  return CHECKLIST_TEMPLATE.filter(i => SUBSYSTEMS_OF[group].includes(i.subsystem))
    .map(i => ({ item: i, a: answerOf(v.checklist, i.id, isPerBuilding(i.subsystem) ? b?.id : undefined) }))
    .filter(x => x.a.status === 'NC');
}

/** Observações montadas a partir dos resultados, das observações dos ensaios e das NC do checklist do componente. */
export function defaultSheetObs(v: SpdaInspection, b: SpdaBuilding | undefined, group: ContinuityGroup): string {
  const tests = sheetTests(v, b, group);
  const measured = tests.filter(t => continuityStatus(t) !== 'pendente');
  const nc = measured.filter(t => continuityStatus(t) === 'nao_conforme');
  const lines: string[] = [];
  if (!measured.length) lines.push('Medições de continuidade não realizadas ou sem valor registrado.');
  else if (!nc.length) lines.push('Todos os pontos ensaiados apresentaram continuidade com valor inferior ou igual a 200 mΩ, conforme ABNT NBR 5419-3:2026 (Anexo F).');
  else lines.push(`${nc.length} trecho(s) acima de 200 mΩ: ${nc.map(t => `${continuityCode(v.continuity, t)} (${t.from} → ${t.to}: ${fmtMilliOhm(t.resistanceOhm)})`).join('; ')}. Verificar conexões/emendas e repetir o ensaio após a correção.`);
  tests.filter(t => t.obs?.trim()).forEach(t => lines.push(`${continuityCode(v.continuity, t)}: ${t.obs!.trim()}`));
  checklistNcs(v, b, group).forEach(({ item, a }) => lines.push(`Não conformidade ${item.id} — ${item.title}${a.obs?.trim() ? `: ${a.obs.trim()}` : ''}.`));
  return lines.join('\n');
}

/** Tipo de material: das medições (quando informado) ou da observação/resposta do item de material do checklist. */
function materialText(v: SpdaInspection, b: SpdaBuilding | undefined, group: ContinuityGroup): string {
  const fromTests = [...new Set(sheetTests(v, b, group).map(t => t.material).filter(Boolean))].map(m => RESISTIVITY[m!]?.label).filter(Boolean);
  if (fromTests.length) return fromTests.join(', ');
  const id = MATERIAL_ITEM[group];
  if (!id) return '';
  const a = answerOf(v.checklist, id, b?.id);
  if (a.obs?.trim()) return a.obs.trim();
  if (a.status === 'C') return `Conforme a norma (item ${id})`;
  if (a.status === 'NC') return `Não conforme (item ${id})`;
  return '';
}

type SheetRow = { key: string; code: string; label: string; value: string; unit: string; result: string; tone: 'ok' | 'bad' | 'muted' | 'info' };
const TONE: Record<SheetRow['tone'], string> = { ok: 'text-emerald-700', bad: 'text-red-700 font-bold', muted: 'text-slate-500', info: 'text-sky-700' };
const SOIL_LABEL_FULL: Record<string, string> = { seco: 'Solo seco', umido: 'Solo úmido', encharcado: 'Solo encharcado' };

export const ContinuitySheet: React.FC<{
  inspection: SpdaInspection; building?: SpdaBuilding; group: ContinuityGroup;
  /** dentro do relatório técnico: sem cabeçalho/nº, sem equipamentos e condições e sem assinaturas; identificação só com a edificação */
  embedded?: boolean;
}> = ({ inspection: v, building: b, group, embedded = false }) => {
  const company = readCompanyInfo();
  const rt = company?.technicalResponsible;
  const info = CONTINUITY_GROUP_INFO[group];
  const tests = sheetTests(v, b, group);
  const grounding = group === 'aterramento' ? v.grounding.filter(g => (!b || g.buildingId === b.id) && groundingValue(g) !== null) : [];
  const instruments = v.instruments.filter(i => i.use === 'continuidade' || (grounding.length > 0 && i.use === 'aterramento'));
  const level = b?.protectionLevel || v.protectionLevel;
  const material = materialText(v, b, group);
  const sketchKind = SKETCH_OF[group];
  const sketch = sketchKind ? b?.sketches?.[sketchKind] : undefined;
  const sketchImgs = sketch?.images || [];
  const local = [v.address, v.city && `${v.city}${v.state ? `/${v.state}` : ''}`].filter(Boolean).join(' — ');

  const rows: SheetRow[] = [
    ...tests.map(t => {
      const st = continuityStatus(t);
      return {
        key: t.id, code: continuityCode(v.continuity, t), label: `${t.from || '?'} → ${t.to || '?'}`,
        value: t.resistanceOhm != null ? fmtMilliOhm(t.resistanceOhm).replace(' mΩ', '') : '—', unit: 'mΩ',
        result: st === 'conforme' ? 'Aprovado' : st === 'nao_conforme' ? 'Reprovado' : 'Pendente',
        tone: st === 'conforme' ? 'ok' : st === 'nao_conforme' ? 'bad' : 'muted'
      } as SheetRow;
    }),
    ...grounding.map(g => {
      const pl = g.method === 'queda_potencial' ? fop3Plateau(g) : null;
      const val = groundingValue(g);
      return {
        key: g.id, code: 'RA', label: `Resistência de aterramento — ${g.point || 'ponto'}`,
        value: val != null ? fmtOhm(val, 2).replace(' Ω', '') : '—', unit: 'Ω',
        result: val == null ? 'Pendente' : pl ? (pl.ok ? 'Patamar OK' : 'Sem patamar') : 'Informativo',
        tone: val == null ? 'muted' : 'info'
      } as SheetRow;
    })
  ];
  const nOk = rows.filter(r => r.tone === 'ok').length;
  const nBad = rows.filter(r => r.tone === 'bad').length;

  return (
    <div className="space-y-4 text-slate-800">
      {/* cabeçalho (mesmo padrão do laudo) */}
      {!embedded && <div className="border-b-2 border-[#0A2540] pb-3 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
        <div className="flex items-center gap-3 min-h-[48px]">
          {company?.logoUrl && <img src={company.logoUrl} alt="Logotipo" className="max-h-14 max-w-[150px] object-contain" />}
        </div>
        <div className="sm:text-right">
          <span className="text-[10px] font-bold uppercase tracking-widest text-slate-400 block">Relatório simplificado nº</span>
          <span className="text-base font-extrabold text-blue-700 font-mono block">{v.number}-{info.code}</span>
          {v.serviceOrderNumber && <span className="text-xs font-semibold text-slate-700 font-mono block">OS: {v.serviceOrderNumber}</span>}
          <span className="text-[11px] text-slate-500 block">Medições: {formatDateBR(v.inspectionDate)}</span>
        </div>
      </div>}

      <div className="text-center">
        <h1 className="font-black text-sm sm:text-base text-[#0A2540] uppercase tracking-wide">{SHEET_TITLE[group]}</h1>
        {!embedded && <p className="text-[11px] text-slate-500">Relatório simplificado das medições — ABNT NBR 5419-3:2026, Anexo F{grounding.length ? ' · ABNT NBR 15749' : ''}</p>}
      </div>

      {embedded ? (
        <Section n={1} title="Identificação">
          <Info label="Edificação" value={b?.name || buildingsOf(v)[0]?.name} />
        </Section>
      ) : <>
      <Section n={1} title="Identificação">
        <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
          <Info label="Cliente" value={v.clientName} />
          <Info label="Edificação" value={b?.name || buildingsOf(v)[0]?.name} />
          <Info label="Local" value={local} />
          <Info label="Ordem de serviço" value={v.serviceOrderNumber} />
          <Info label="Inspeção" value={v.number} />
          <Info label="Data das medições" value={formatDateBR(v.inspectionDate)} />
        </div>
      </Section>

      <Section n={2} title="Equipamentos e condições do ensaio">
        <table className="w-full border-collapse mb-3">
          <thead><tr><th className={th}>Uso</th><th className={th}>Equipamento</th><th className={th}>Modelo / nº de série</th><th className={th}>Certificado de calibração</th><th className={th}>Validade</th></tr></thead>
          <tbody>
            {instruments.length ? instruments.map((i, k) => (
              <tr key={k}>
                <td className={td}>{i.use === 'continuidade' ? 'Continuidade' : 'Aterramento'}</td>
                <td className={td}>{i.description || '—'}</td>
                <td className={td}>{[i.model, i.serialNumber].filter(Boolean).join(' / ') || '—'}</td>
                <td className={td}>{i.calibrationCert || '—'}{i.calibrationDate ? ` (${formatDateBR(i.calibrationDate)})` : ''}</td>
                <td className={td}>{i.calibrationExpiry ? formatDateBR(i.calibrationExpiry) : '—'}</td>
              </tr>
            )) : <tr><td className={td} colSpan={5}>Equipamento não informado na inspeção.</td></tr>}
          </tbody>
        </table>
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
          <Info label="Condição do solo / tempo" value={[v.soilCondition && SOIL_LABEL_FULL[v.soilCondition], v.weather?.trim()].filter(Boolean).join(' — ')} />
          <Info label="Nível de proteção" value={level ? `${protectionLabel(level)} (malha ${levelParams(level)!.meshM} m)` : 'Não definido'} />
          <Info label="Pontos medidos" value={rows.length ? String(rows.length) : undefined} />
          <Info label="Tipo de material" value={material} />
        </div>
      </Section>
      </>}

      <Section n={embedded ? 2 : 3} title={sketchImgs.length ? 'Medições e croqui dos pontos' : 'Medições'} right={rows.length ? `${nOk} aprovada(s) · ${nBad} reprovada(s)` : undefined}>
        <div className={sketchImgs.length ? 'grid grid-cols-1 sm:grid-cols-[minmax(0,1.05fr)_minmax(0,1fr)] gap-3 items-start' : ''}>
          <div>
            <table className="w-full border-collapse">
              <thead><tr><th className={`${th} w-14`}>Código</th><th className={th}>Pontos de medição</th><th className={`${th} w-16 text-right`}>Valor</th><th className={`${th} w-20`}>Resultado</th></tr></thead>
              <tbody>
                {rows.length ? rows.map(r => (
                  <tr key={r.key}>
                    <td className={`${td} font-mono text-[10px]`}>{r.code}</td>
                    <td className={td}>{r.label}</td>
                    <td className={`${td} font-mono text-right whitespace-nowrap`}>{r.value} <span className="text-slate-500">{r.unit}</span></td>
                    <td className={`${td} ${TONE[r.tone]}`}>{r.result}</td>
                  </tr>
                )) : <tr><td className={td} colSpan={4}>Nenhuma medição lançada para este componente.</td></tr>}
              </tbody>
            </table>
            <p className="text-[10px] text-slate-500 mt-1">
              Critério de aceitação da continuidade: ≤ 200 mΩ (0,2 Ω — NBR 5419-3:2026, F.4.4). Ensaio a 4 fios, 1 A a 5 A (F.1.3).
              {grounding.length > 0 && ' Resistência de aterramento: informativa (7.1.4), patamar ±5 % da leitura a 62 %.'}
            </p>
          </div>
          {sketchImgs.length > 0 && <figure className="spda-avoid border border-slate-200 rounded bg-white p-1">
            {sketchImgs.map(p => <img key={p.id} src={p.url} alt={p.caption} className="w-full max-h-[110mm] object-contain rounded mb-1 last:mb-0" />)}
            <figcaption className="text-[9px] text-slate-600 mt-0.5">Croqui {group === 'aterramento' ? 'da malha / pontos de aterramento' : `dos pontos — ${info.label}`}{sketch?.notes?.trim() ? ` · ${sketch.notes.trim()}` : ''}</figcaption>
          </figure>}
        </div>
      </Section>

      <Section n={embedded ? 3 : 4} title="Observações">
        <p className="whitespace-pre-line leading-relaxed">{defaultSheetObs(v, b, group)}</p>
      </Section>

      {!embedded && <div className="spda-avoid grid grid-cols-2 gap-6 pt-8 text-center text-[11px]">
        <div>
          <div className="border-t border-slate-500 pt-1 font-bold">{v.technicianName || 'Responsável pela inspeção'}</div>
          <div className="text-slate-500">{[v.technicianTitle, v.technicianCrea].filter(Boolean).join(' — ') || 'Executado por'}</div>
        </div>
        <div>
          {rt?.signatureUrl && <img src={rt.signatureUrl} alt="Assinatura do responsável técnico" className="h-12 mx-auto object-contain -mb-1" />}
          <div className="border-t border-slate-500 pt-1 font-bold">{rt?.name || 'Responsável técnico'}</div>
          <div className="text-slate-500">{[rt?.title, rt?.creaNumber && `CREA ${rt.creaNumber}`].filter(Boolean).join(' — ') || 'Responsável técnico'}</div>
        </div>
      </div>}
    </div>
  );
};

/** Relatório simplificado de um componente: uma folha por edificação (a selecionada ou todas). */
export const ContinuitySheetOverlay: React.FC<{
  inspection: SpdaInspection; buildings: SpdaBuilding[]; group: ContinuityGroup; onClose: () => void;
}> = ({ inspection, buildings, group, onClose }) => (
  <PrintOverlay
    title={`Relatório simplificado — ${CONTINUITY_GROUP_INFO[group].label}${buildings.length === 1 ? ` — ${buildings[0].name}` : ''}`}
    documentTitle={`${inspection.number}-${CONTINUITY_GROUP_INFO[group].code} - Relatório simplificado ${CONTINUITY_GROUP_INFO[group].label}${buildings.length === 1 ? ` - ${buildings[0].name}` : ''}`}
    onClose={onClose}
  >
    {(buildings.length ? buildings : [undefined]).map((b, i) => (
      <div key={b?.id || 'geral'} className={i > 0 ? 'spda-break pt-4' : ''}>
        <ContinuitySheet inspection={inspection} building={b} group={group} />
      </div>
    ))}
  </PrintOverlay>
);
