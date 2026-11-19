/**
 * Laudo de SPDA: visualização em tela e impressão (Imprimir → Salvar como PDF).
 * Mesmo visual dos laudos de ensaios (cabeçalho da empresa, faixas #0A2540).
 *
 * A impressão usa um estilo próprio (injetado só enquanto o laudo está aberto)
 * que esconde o restante do app — nenhum CSS global é alterado.
 */
import React, { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { FileText, FileType2, Printer, RefreshCw, Share2, X } from 'lucide-react';
import { CHECKLIST_TEMPLATE, SUBSYSTEM_LABEL, SUBSYSTEM_ORDER } from '../checklist';
import { readCompanyInfo } from '../repository';
import { cleanCaption } from './PhotoReport';
import { laudoIssued, laudoNumber, LAUDO_RESULT_LABEL, RESULT_LABEL, finalResult, resultFromVerdict, techText, type ShownResult } from '../techReport';
import {
  INSPECTION_KIND_LABEL, LEVEL_PARAMS, METHOD_LABEL, PROTECTION_LEVEL_LABEL, TEST_STATUS_LABEL,
  CONTINUITY_KIND_LABEL, RESISTIVITY, fmtMilliOhm, aboveDesign, currentOutOfRange, designValue, effectiveLimit, kindOf, theoreticalResistance,
  answerOf, checklistProgress, continuityStatus, fallOfPotentialPlateau, fmtOhm, groundingStatus, groundingValue,
  validityText, inspectionVerdict, listImprovements, listNonConformities, nextInspectionDate,
  BUILDING_TYPE_LABEL, SPDA_KIND_LABEL, spdaKindText, airTerminationText, downConductorText, groundingSystemText, buildingNameOf, buildingVerdict, isPerBuilding, type Verdict, buildingArea, buildingsOf, formatCoordinates, levelEfficiencyPct, minDownConductors,
  CONTINUITY_GROUP_INFO, continuityCode, continuityGroupOf, probeDistances, fmtMeters, fopPcts, fopStep, isEvaluated, isKnownItem, isMeasured
} from '../rules';
import { docLabel } from '../cnpjLookup';
import type { SpdaInspection, SpdaPhoto, SpdaSubsystem, TestStatus } from '../types';
import { formatDateBR } from '../../../utils/dateUtils';
import { exportWord, sharePdf, shareWord, viewPdf } from './documentActions';

type DocAction = 'view_pdf' | 'share_pdf' | 'export_word' | 'share_word';
import { SketchPrint, sketchOf } from './SketchSection';
import { FopPrintBlock } from './PlateauChart';

const PRINT_ROOT_ID = 'spda-print-root';
const PRINT_CSS = `
@media print {
  @page { size: A4 portrait; margin: 11mm 13mm 9mm 13mm; }
  html, body { background: #ffffff !important; }
  body > *:not(#${PRINT_ROOT_ID}) { display: none !important; }
  #${PRINT_ROOT_ID} .spda-overlay { position: static !important; background: transparent !important; overflow: visible !important; padding: 0 !important; display: block !important; }
  #${PRINT_ROOT_ID} .spda-sheet { box-shadow: none !important; margin: 0 !important; max-width: 100% !important; padding: 0 !important; }
  #${PRINT_ROOT_ID} .spda-avoid { break-inside: avoid; page-break-inside: avoid; }
  #${PRINT_ROOT_ID} .spda-break { break-before: page; page-break-before: always; }
}`;

const SOIL_LABEL: Record<NonNullable<SpdaInspection['soilCondition']>, string> = { seco: 'Solo seco', umido: 'Solo úmido', encharcado: 'Solo encharcado' };

export const STATUS_TXT_CLS: Record<TestStatus, string> = {
  conforme: 'text-emerald-700', nao_conforme: 'text-red-700 font-bold', pendente: 'text-slate-500', informativo: 'text-sky-700'
};

export const Section: React.FC<{ n: number | string; title: string; right?: React.ReactNode; children: React.ReactNode }> = ({ n, title, right, children }) => (
  <div className="spda-avoid">
    <div className="bg-[#0A2540] text-white text-xs font-bold px-3 py-1.5 rounded-t-md uppercase tracking-wider flex flex-wrap items-center justify-between gap-2">
      <span>{n}{typeof n === 'number' ? '.' : ''} {title}</span>{right && <span>{right}</span>}
    </div>
    <div className="border border-slate-300 rounded-b-md p-3 text-xs bg-slate-50/50">{children}</div>
  </div>
);

export const Info: React.FC<{ label: string; value?: React.ReactNode }> = ({ label, value }) => (
  <div><span className="text-slate-500 block text-[10px]">{label}</span><span className="font-semibold text-slate-900">{value || '—'}</span></div>
);

export const th = 'border border-slate-300 px-1.5 py-1 text-left font-bold bg-slate-100 text-[10px] uppercase';
export const td = 'border border-slate-300 px-1.5 py-1 align-top';

export const SpdaReport: React.FC<{ inspection: SpdaInspection }> = ({ inspection: v }) => {
  const company = readCompanyInfo();
  const ncs = listNonConformities(v);
  const improvements = listImprovements(v);
  const buildingsList = buildingsOf(v);
  const prog = checklistProgress(v.checklist, buildingsList);
  // Documentação (geral da OS) + um bloco por edificação; sem edificações, um bloco único
  const perBuildingSubs = SUBSYSTEM_ORDER.filter(isPerBuilding);
  const checklistBlocks: Array<{ key: string; title?: string; buildingId?: string; subsystems: typeof SUBSYSTEM_ORDER; verdict?: Verdict }> = buildingsList.length
    ? [
        { key: 'doc', title: 'Documentação (geral da OS)', subsystems: ['documentacao'] },
        ...buildingsList.map((b, i) => ({
          key: b.id, buildingId: b.id, subsystems: perBuildingSubs,
          title: `Edificação ${i + 1} — ${b.name || 'sem nome'}`, verdict: buildingVerdict({ ...v, buildings: buildingsList }, b.id)
        }))
      ]
    : [{ key: 'all', subsystems: SUBSYSTEM_ORDER }];
  const bName = (id?: string) => buildingNameOf({ buildings: buildingsList }, id) || '—';
  const order = (id?: string) => { const i = buildingsList.findIndex(b => b.id === id); return i < 0 ? 999 : i; };
  const byBuilding = (a: { buildingId?: string }, b: { buildingId?: string }) => order(a.buildingId) - order(b.buildingId);
  // só o que foi ensaiado: continuidade com valor medido e aterramento com leitura
  const continuitySorted = [...v.continuity].filter(isMeasured).sort(byBuilding);
  const groundingSorted = [...v.grounding].filter(g => groundingValue(g) !== null).sort(byBuilding);
  const evaluatedOf = (sub: SpdaSubsystem, buildingId?: string) => CHECKLIST_TEMPLATE.filter(i => i.subsystem === sub)
    .map(item => ({ item, a: answerOf(v.checklist, item.id, isPerBuilding(sub) ? buildingId : undefined) }))
    .filter(x => isEvaluated(x.a));
  const nEvaluated = v.checklist.filter(a => isKnownItem(a) && isEvaluated(a)).length;
  const lp = LEVEL_PARAMS[v.protectionLevel];
  const buildings = buildingsOf(v);
  const aerials = buildings.filter(b => !!b.aerialImage);
  const photos = v.checklist.flatMap(a => {
    const item = CHECKLIST_TEMPLATE.find(i => i.id === a.itemId);
    if (!item) return [];
    const where = a.buildingId ? ` · ${bName(a.buildingId)}` : '';
    return (a.photos || []).map((src, i) => ({ src, caption: `${item.id} — ${item.title}${where}`, key: `${a.itemId}_${a.buildingId || 'geral'}_${i}` }));
  });
  // fotos dos ensaios (continuidade e aterramento), na ordem das edificações
  const photoMeta = (ph: SpdaPhoto) => [new Date(ph.timestamp).toLocaleString('pt-BR'), ph.editedAt ? 'editada' : ''].filter(Boolean).join(' · ');
  const testPhotos = [
    ...continuitySorted.flatMap(t => (t.photos || []).map(ph => ({
      key: ph.id, src: ph.url, meta: photoMeta(ph),
      caption: `Continuidade — ${t.from || 'ponto'} → ${t.to || 'ponto'}${cleanCaption(ph.caption) ? ` (${cleanCaption(ph.caption)})` : ''} — ${fmtMilliOhm(t.resistanceOhm)}${buildingsList.length ? ` · ${bName(t.buildingId)}` : ''}`
    }))),
    ...groundingSorted.flatMap(t => (t.photos || []).map(ph => ({
      key: ph.id, src: ph.url, meta: photoMeta(ph),
      caption: `Aterramento — ${t.point || 'ponto'}${cleanCaption(ph.caption) ? ` (${cleanCaption(ph.caption)})` : ''} — ${fmtOhm(groundingValue(t), 2)}${buildingsList.length ? ` · ${bName(t.buildingId)}` : ''}`
    })))
  ];
  // parecer do laudo: o escolhido (Conclusão da inspeção / relatório técnico) ou o calculado
  const result = finalResult(v);
  const RESULT_CLS: Record<ShownResult, string> = {
    aprovado: 'bg-emerald-50 border-emerald-400 text-emerald-800', reprovado: 'bg-red-50 border-red-400 text-red-800',
    parcial: 'bg-amber-50 border-amber-400 text-amber-800', incompleto: 'bg-slate-50 border-slate-400 text-slate-700'
  };
  const verdictCls = RESULT_CLS[result];
  const rt = company?.technicalResponsible;

  return (
    <div className="space-y-4 text-slate-800">
      {/* Cabeçalho */}
      <div className="border-b-2 border-[#0A2540] pb-3 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          {company?.logoUrl ? (
            <img src={company.logoUrl} alt={company.name} className="max-h-14 max-w-[140px] object-contain" />
          ) : (
            <div className="w-12 h-12 rounded-xl bg-[#0A2540] flex items-center justify-center text-white font-black text-xl">JVM</div>
          )}
          <div>
            <p className="font-black text-base text-[#0A2540]">{company?.name || 'Empresa'}</p>
            <p className="text-[11px] text-slate-600">{company?.legalName}</p>
            <p className="text-[10px] text-slate-500">{[company?.creaCompanyRegister, company?.cnpj && `${docLabel(company.cnpj)}: ${company.cnpj}`].filter(Boolean).join(' • ')}</p>
          </div>
        </div>
        <div className="sm:text-right">
          <span className="text-[10px] font-bold uppercase tracking-widest text-slate-400 block">Laudo nº</span>
          <span className="text-base font-extrabold text-blue-700 font-mono block">{laudoNumber(v)}</span>
          {v.serviceOrderNumber && <span className="text-xs font-semibold text-slate-700 font-mono block">OS: {v.serviceOrderNumber}</span>}
          <span className="text-[11px] text-slate-500 block">Inspeção: {formatDateBR(v.inspectionDate)}</span>
          <span className="text-[11px] text-slate-700 font-semibold block">Emissão: {formatDateBR(laudoIssued(v).date)} às {laudoIssued(v).time}</span>
        </div>
      </div>

      <div className="text-center">
        <h1 className="font-black text-sm sm:text-base text-[#0A2540] uppercase tracking-wide">Laudo técnico de inspeção e ensaios do SPDA</h1>
        <p className="text-[11px] text-slate-500">Sistema de Proteção contra Descargas Atmosféricas — ABNT NBR 5419:2026 (Partes 1 a 4)</p>
        <p className="text-[10px] font-bold text-amber-700 mt-1">VERSÃO DE DEMONSTRAÇÃO — documento sem validade como laudo oficial</p>
      </div>

      <Section n={1} title="Identificação do cliente e da estrutura">
        <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
          <Info label="Cliente" value={v.clientName} />
          <Info label="Ordem de serviço" value={v.serviceOrderNumber} />
          <Info label={docLabel(v.clientCnpj)} value={v.clientCnpj} />
          <Info label="Edificação / estrutura" value={v.buildingName} />
          <Info label="Endereço" value={[v.address, v.city && `${v.city}${v.state ? `/${v.state}` : ''}`].filter(Boolean).join(' — ')} />
          <Info label="Uso / ocupação" value={v.buildingUse} />
          <Info label="Dimensões" value={[v.heightM != null && `altura ${String(v.heightM).replace('.', ',')} m`, v.areaM2 != null && `área ${String(v.areaM2).replace('.', ',')} m²`].filter(Boolean).join(' · ')} />
        </div>
      </Section>

      <Section n={2} title="Edificações, características do SPDA e da inspeção" right={`${buildings.length} edificação(ões)`}>
        {buildings.length > 0 && (
          <table className="w-full border-collapse mb-3">
            <thead><tr>
              <th className={th}>#</th><th className={th}>Edificação</th><th className={th}>Tipo / ocupação</th><th className={th}>H × C × L (m)</th>
              <th className={th}>Área</th><th className={th}>SPDA existente</th><th className={th}>NP</th><th className={th}>Descidas (mín.)</th><th className={th}>Coordenadas</th>
            </tr></thead>
            <tbody>
              {buildings.map((b, i) => {
                const area = buildingArea(b);
                const minD = minDownConductors(b);
                const n = (x?: number | null) => (x != null ? String(x).replace('.', ',') : '—');
                return (
                  <tr key={b.id}>
                    <td className={td}>{i + 1}</td>
                    <td className={`${td} font-semibold`}>{b.name || '—'}</td>
                    <td className={td}>{BUILDING_TYPE_LABEL[b.type]}{b.activity ? ` — ${b.activity}` : ''}{b.floors ? ` · ${b.floors} pav.` : ''}</td>
                    <td className={`${td} font-mono`}>{n(b.heightM)} × {n(b.lengthM)} × {n(b.widthM)}</td>
                    <td className={`${td} font-mono`}>{area !== null ? `${area.toLocaleString('pt-BR', { maximumFractionDigits: 1 })} m²` : '—'}</td>
                    <td className={td}>{spdaKindText(b)}{airTerminationText(b) && <span className="block text-[9px] text-slate-600">Captação: {airTerminationText(b)}</span>}{downConductorText(b) && <span className="block text-[9px] text-slate-600">Descidas: {downConductorText(b)}</span>}{groundingSystemText(b) && <span className="block text-[9px] text-slate-600">Aterramento: {groundingSystemText(b)}</span>}</td>
                    <td className={td}>{b.protectionLevel} ({levelEfficiencyPct(b.protectionLevel)} %)</td>
                    <td className={`${td} ${minD !== null && b.downConductorsCount != null && b.downConductorsCount < minD ? 'text-red-700 font-bold' : ''}`}>
                      {n(b.downConductorsCount)}{minD !== null ? ` (${minD})` : ''}
                    </td>
                    <td className={`${td} text-[9px]`}>{formatCoordinates(b.latitude, b.longitude) || '—'}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
        <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
          <Info label="Nível de proteção (mais restritivo)" value={`${PROTECTION_LEVEL_LABEL[v.protectionLevel]} — eficiência ${levelEfficiencyPct(v.protectionLevel)} % (5419-2, Tabela B.2)`} />
          <Info label="Método de proteção" value={METHOD_LABEL[v.method]} />
          <Info label="Nº de descidas" value={v.downConductorsCount != null ? String(v.downConductorsCount) : ''} />
          <Info label="Tipo de inspeção" value={INSPECTION_KIND_LABEL[v.inspectionKind]} />
          <Info label="Condição do solo / tempo" value={[v.soilCondition && SOIL_LABEL[v.soilCondition], v.weather].filter(Boolean).join(' — ')} />
          <Info label="Validade do laudo" value={`${validityText(v)} — NBR 5419-3:2026, 7.3.2 f)`} />
        </div>
        <p className="text-[10px] text-slate-500 mt-2">
          Parâmetros do {PROTECTION_LEVEL_LABEL[v.protectionLevel]} (NBR 5419-3:2026): raio da esfera rolante {lp.sphereRadiusM} m e malha máxima {lp.meshM} m (Tabela 2); distância entre descidas {lp.downSpacingM} m, até +20 % (Tabela 5 e 5.4.3.3).
          {buildings.length > 0 && ' Descidas (mín.): perímetro ÷ distância da Tabela 5, no mínimo 2 (5.4.3.1).'}
        </p>
        {aerials.length > 0 && (
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 mt-3">
            {aerials.map(b => (
              <figure key={b.id} className="spda-avoid border border-slate-200 rounded bg-white p-1">
                <img src={b.aerialImage} alt={`Imagem aérea — ${b.name}`} className="w-full max-h-64 object-contain rounded" />
                <figcaption className="text-[9px] text-slate-600 mt-0.5">Imagem aérea — {b.name || 'edificação'}{formatCoordinates(b.latitude, b.longitude) ? ` · ${formatCoordinates(b.latitude, b.longitude)}` : ''}</figcaption>
              </figure>
            ))}
          </div>
        )}
      </Section>

      <Section n={3} title="Instrumentos utilizados">
        <table className="w-full border-collapse">
          <thead><tr><th className={th}>Ensaio</th><th className={th}>Instrumento</th><th className={th}>Modelo</th><th className={th}>Nº série</th><th className={th}>Certificado de calibração</th><th className={th}>Validade</th></tr></thead>
          <tbody>
            {v.instruments.map(i => (
              <tr key={i.use}>
                <td className={td}>{i.use === 'continuidade' ? 'Continuidade' : 'Aterramento'}</td>
                <td className={td}>{i.description || '—'}</td>
                <td className={td}>{i.model || '—'}</td>
                <td className={td}>{i.serialNumber || '—'}</td>
                <td className={td}>{i.calibrationCert || '—'}{i.calibrationDate ? ` (${formatDateBR(i.calibrationDate)})` : ''}</td>
                <td className={td}>{i.calibrationExpiry ? formatDateBR(i.calibrationExpiry) : '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </Section>

      <Section n={4} title="Inspeção visual por subsistema" right={`${nEvaluated} item(ns) avaliado(s) · ${prog.nc} NC`}>
        {nEvaluated === 0 && <p className="text-slate-500">Nenhum item da inspeção visual foi avaliado.</p>}
        {checklistBlocks.filter(block => block.subsystems.some(sub => evaluatedOf(sub, block.buildingId).length > 0)).map(block => (
          <div key={block.key} className="mb-3 spda-avoid">
            {block.title && (
              <p className="font-bold text-[11px] text-[#0A2540] mb-1 flex items-center gap-2">
                {block.title}
                {block.verdict && <span className={`text-[9px] px-1.5 py-0.5 rounded border ${block.verdict === 'conforme' ? 'border-emerald-400 text-emerald-700' : block.verdict === 'nao_conforme' ? 'border-red-400 text-red-700' : 'border-amber-400 text-amber-700'}`}>{RESULT_LABEL[resultFromVerdict(block.verdict)]}</span>}
              </p>
            )}
            <table className="w-full border-collapse">
              <thead><tr><th className={`${th} w-14`}>Item</th><th className={th}>Verificação</th><th className={`${th} w-28`}>Referência</th><th className={`${th} w-10 text-center`}>Sit.</th><th className={th}>Observação</th></tr></thead>
              <tbody>
                {block.subsystems.filter(sub => evaluatedOf(sub, block.buildingId).length > 0).map(sub => (
                  <React.Fragment key={sub}>
                    <tr><td colSpan={5} className={`${td} bg-slate-200 font-bold text-[10px] uppercase`}>{SUBSYSTEM_LABEL[sub]}</td></tr>
                    {evaluatedOf(sub, block.buildingId).map(({ item, a }) => {
                      return (
                        <tr key={item.id}>
                          <td className={`${td} font-mono`}>{item.id}</td>
                          <td className={td}>{item.title}</td>
                          <td className={`${td} text-[10px] text-slate-500`}>{item.reference}</td>
                          <td className={`${td} text-center font-bold ${a.status === 'NC' ? 'text-red-700' : a.status === 'C' ? 'text-emerald-700' : 'text-slate-500'}`}>{a.status || '—'}</td>
                          <td className={td}>{a.obs?.trim() || ''}</td>
                        </tr>
                      );
                    })}
                  </React.Fragment>
                ))}
              </tbody>
            </table>
          </div>
        ))}
        <p className="text-[10px] text-slate-500 mt-1">C = conforme · NC = não conforme · NA = não aplicável. Constam somente os itens avaliados na inspeção.</p>
      </Section>

      <Section n={5} title="Ensaio de continuidade elétrica" right="NBR 5419-3:2026, Anexo F">
        {continuitySorted.length === 0 ? <p className="text-slate-500">Não realizado (continuidade constatada visualmente, quando possível — 7.3.3 f).</p> : (
          <table className="w-full border-collapse">
            <thead><tr><th className={th}>Identificação</th>{buildingsList.length > 0 && <th className={th}>Edificação</th>}<th className={th}>Origem</th><th className={th}>Destino</th><th className={th}>Corrente injetada</th><th className={th}>Valor medido</th><th className={th}>Critério</th><th className={th}>Resultado</th></tr></thead>
            <tbody>
              {continuitySorted.map(t => {
                const st = continuityStatus(t);
                const kind = kindOf(t);
                const theory = theoreticalResistance(t);
                return (
                  <React.Fragment key={t.id}>
                  <tr>
                    <td className={`${td} font-bold whitespace-nowrap`}>{continuityCode(v.continuity, t)}<span className="block text-[9px] font-normal text-slate-600">{CONTINUITY_GROUP_INFO[continuityGroupOf(t)].label}</span></td>
                    {buildingsList.length > 0 && <td className={`${td} font-semibold`}>{bName(t.buildingId)}</td>}
                    <td className={td}>{t.from || '—'}</td>
                    <td className={td}>{t.to || '—'}</td>
                    <td className={td}>
                      {t.currentA != null ? `${String(t.currentA).replace('.', ',')} A` : '—'}
                      {currentOutOfRange(t) && <span className="block text-[9px] text-amber-700">fora de 1–5 A (F.1.3.1)</span>}
                    </td>
                    <td className={`${td} font-mono`}>{fmtMilliOhm(t.resistanceOhm)}</td>
                    <td className={`${td} font-mono`}>
                      {effectiveLimit(t) !== null ? `≤ ${fmtMilliOhm(effectiveLimit(t))}` : '—'}
                      
                    </td>
                    <td className={`${td} ${STATUS_TXT_CLS[st]}`}>{TEST_STATUS_LABEL[st]}</td>
                  </tr>
                  {t.obs?.trim() && (
                    <tr><td colSpan={20} className="border border-slate-300 px-1.5 py-1 text-[10px] text-slate-600 bg-slate-50"><b>Obs.:</b> {t.obs.trim()}</td></tr>
                  )}
                  </React.Fragment>
                );
              })}
            </tbody>
          </table>
        )}
        <p className="text-[10px] text-slate-500 mt-1">
          Valores em miliohms (mΩ). Critério de aceitação: ≤ 200 mΩ (0,2 Ω — NBR 5419-3:2026, F.4.4) em todas as medições.
          Instrumento a 4 fios, corrente injetada de 1 A a 5 A (F.1.3).
        </p>
      </Section>

      <Section n={6} title="Resistência de aterramento (informativo)" right="NBR 5419-3:2026, 7.1.4">
        <p className="text-[10px] text-slate-500 mb-1">A medição de resistência de aterramento não é necessária para a verificação da eficácia do SPDA (7.1.4); os valores abaixo são apenas registro.</p>
        {groundingSorted.length === 0 ? <p className="text-slate-500">Não realizada.</p> : (
          <table className="w-full border-collapse">
            <thead><tr>{buildingsList.length > 0 && <th className={th}>Edificação</th>}<th className={th}>Ponto</th><th className={th}>Método</th><th className={th}>S1 / S (62 %) / S2</th><th className={th}>Adotado</th><th className={th}>Projeto</th><th className={th}>Situação</th></tr></thead>
            <tbody>
              {groundingSorted.map(t => {
                const st = groundingStatus(t);
                const plateau = t.method === 'queda_potencial' ? fallOfPotentialPlateau(t) : null;
                return (
                  <React.Fragment key={t.id}>
                  <tr>
                    {buildingsList.length > 0 && <td className={`${td} font-semibold`}>{bName(t.buildingId)}</td>}
                    <td className={td}>{t.point || '—'}</td>
                    <td className={td}>{t.method === 'queda_potencial' ? `Queda de potencial${t.currentProbeDistanceM ? ` (${String(t.currentProbeDistanceM).replace('.', ',')} m)` : ''}` : t.method === 'alicate' ? 'Alicate terrômetro' : 'Outro'}</td>
                    <td className={`${td} font-mono`}>
                      {t.method === 'queda_potencial' ? [t.r52, t.r62, t.r72].map(x => fmtOhm(x, 2)).join(' / ') : '—'}
                      {t.method === 'queda_potencial' && (() => { const pc = fopPcts(fopStep(t)); const pd = probeDistances(t.currentProbeDistanceM, fopStep(t)); return <span className="block text-[9px] text-slate-600 font-sans">{pc.join(' / ')} %{pd ? ` — a ${pd.m.map(fmtMeters).join(' / ')}` : ''}</span>; })()}
                      {plateau && <span className={`block text-[9px] ${plateau.ok ? 'text-emerald-700' : 'text-amber-700'}`}>{plateau.ok ? 'patamar verificado' : 'fora do patamar'} ({(plateau.maxDeviation * 100).toFixed(1)} %)</span>}
                    </td>
                    <td className={`${td} font-mono`}>{fmtOhm(groundingValue(t), 2)}</td>
                    <td className={`${td} font-mono`}>{fmtOhm(designValue(t), 2)}</td>
                    <td className={`${td} ${STATUS_TXT_CLS[st]}`}>{TEST_STATUS_LABEL[st]}{aboveDesign(t) && <span className="block text-[9px] text-amber-700">acima do valor de projeto</span>}</td>
                  </tr>
                  {t.obs?.trim() && (
                    <tr><td colSpan={20} className="border border-slate-300 px-1.5 py-1 text-[10px] text-slate-600 bg-slate-50"><b>Obs.:</b> {t.obs.trim()}</td></tr>
                  )}
                  </React.Fragment>
                );
              })}
            </tbody>
          </table>
        )}
        <FopPrintBlock tests={groundingSorted} label={t => `${buildingsList.length > 0 ? `${bName(t.buildingId)} — ` : ''}${t.point || 'Ponto'}`} />
        {buildings.some(b => sketchOf(b, 'captacao') || sketchOf(b, 'descida') || sketchOf(b, 'aterramento')) && (
          <div className="mt-3">
            <p className="font-bold text-[11px] text-[#0A2540] mb-1 border-b border-slate-300">Projetos / croquis dos pontos de captação, descida e aterramento</p>
            {buildings.map(b => (
              <React.Fragment key={b.id}>
                <SketchPrint kind="captacao" building={b} />
                <SketchPrint kind="descida" building={b} />
                <SketchPrint kind="aterramento" building={b} />
              </React.Fragment>
            ))}
          </div>
        )}
      </Section>

      <Section n={7} title="Não conformidades" right={`${ncs.length} encontrada(s)`}>
        {ncs.length === 0 ? <p>Nenhuma não conformidade registrada.</p> : (
          <ol className="list-decimal pl-5 space-y-1">
            {ncs.map((n, i) => (
              <li key={i}>
                <span className="font-semibold">{n.buildingName ? `${n.buildingName} — ` : ''}{SUBSYSTEM_LABEL[n.subsystem]}:</span> {n.description} <span className="text-slate-500">({n.reference})</span>
                {n.obs?.trim() && <span className="block text-slate-600">{n.obs.trim()}</span>}
              </li>
            ))}
          </ol>
        )}
      </Section>

      <Section n={8} title="Recomendações" right={improvements.length ? `${improvements.length} melhoria(s) apontada(s)` : undefined}>
        {improvements.length > 0 && (
          <div className="mb-2">
            <p className="font-semibold">Melhorias apontadas na inspeção</p>
            <ol className="list-decimal pl-5 space-y-1">
              {improvements.map(m => (
                <li key={m.code + (m.buildingId || '')}>
                  <span className="font-semibold">{m.buildingName ? `${m.buildingName} — ` : ''}{SUBSYSTEM_LABEL[m.subsystem]}:</span> {m.code}{m.comment ? ` — ${m.comment}` : ''}
                  {m.photos > 0 && <span className="text-slate-500"> ({m.photos} foto(s) no relatório fotográfico)</span>}
                </li>
              ))}
            </ol>
          </div>
        )}
        <p className="whitespace-pre-line">{v.recommendations?.trim() || '—'}</p>
        {v.notes?.trim() && <p className="whitespace-pre-line mt-2 text-slate-600">{v.notes.trim()}</p>}
      </Section>

      <Section n={9} title="Conclusão">
        <div className={`border-2 rounded-lg px-3 py-2 text-center font-black text-sm ${verdictCls}`}>{LAUDO_RESULT_LABEL[result]}</div>
        {buildingsList.length > 1 && (
          <ul className="mt-2 space-y-0.5">
            {buildingsList.map((b, i) => {
              const bv = buildingVerdict({ ...v, buildings: buildingsList }, b.id);
              return <li key={b.id}><b>{b.name || `Edificação ${i + 1}`}:</b> <span className={bv === 'conforme' ? 'text-emerald-700' : bv === 'nao_conforme' ? 'text-red-700 font-bold' : 'text-amber-700'}>{RESULT_LABEL[resultFromVerdict(bv)]}</span></li>;
            })}
          </ul>
        )}
        <p className="mt-2 whitespace-pre-line">{techText(v, 'laudoConclusao')}</p>
        <p className="mt-1">Próxima inspeção recomendada até: <b>{formatDateBR(nextInspectionDate(v))}</b>.</p>
      </Section>

      {/* Assinaturas */}
      <div className="grid grid-cols-1 sm:grid-cols-2 items-end gap-6 pt-8 spda-avoid">
        <div className="text-center text-xs">
          <div className="border-t border-slate-500 pt-1 font-bold">{v.technicianName || 'Responsável pela inspeção'}</div>
          <div className="text-slate-500">{[v.technicianTitle, v.technicianCrea].filter(Boolean).join(' — ') || 'Inspeção e ensaios'}</div>
        </div>
        <div className="text-center text-xs">
          {rt?.signatureUrl && <img src={rt.signatureUrl} alt="Assinatura do responsável técnico" className="h-12 mx-auto object-contain -mb-1" />}
          <div className="border-t border-slate-500 pt-1 font-bold">{rt?.name || 'Responsável técnico'}</div>
          <div className="text-slate-500">{[rt?.title, rt?.creaNumber && `CREA ${rt.creaNumber}`].filter(Boolean).join(' — ') || 'Responsável técnico'}</div>
        </div>
      </div>

      {(photos.length > 0 || testPhotos.length > 0) && (
        <Section n={10} title="Registro fotográfico">
          {testPhotos.length > 0 && (
            <>
              <p className="font-bold text-[11px] text-[#0A2540] mb-1">Ensaios</p>
              <div className="grid grid-cols-2 sm:grid-cols-3 gap-2 mb-3">
                {testPhotos.map(p => (
                  <figure key={p.key} className="spda-avoid border border-slate-200 rounded bg-white p-1">
                    <img src={p.src} alt={p.caption} className="w-full h-32 object-cover rounded" />
                    <figcaption className="text-[9px] text-slate-600 mt-0.5">{p.caption}{p.meta && <span className="block text-slate-400">{p.meta}</span>}</figcaption>
                  </figure>
                ))}
              </div>
            </>
          )}
          {photos.length > 0 && (
            <>
              {testPhotos.length > 0 && <p className="font-bold text-[11px] text-[#0A2540] mb-1">Inspeção visual</p>}
              <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
                {photos.map(p => (
                  <figure key={p.key} className="spda-avoid border border-slate-200 rounded bg-white p-1">
                    <img src={p.src} alt={p.caption} className="w-full h-32 object-cover rounded" />
                    <figcaption className="text-[9px] text-slate-600 mt-0.5">{p.caption}</figcaption>
                  </figure>
                ))}
              </div>
            </>
          )}
        </Section>
      )}
    </div>
  );
};

/** Laudo em tela cheia, com botão de imprimir (salvar como PDF pelo navegador). */
type LaudoFields = Pick<SpdaInspection, 'laudoNumber' | 'laudoDate' | 'laudoTime'>;

/**
 * Laudo em tela, com a faixa para editar Nº do laudo, Data e Hora de emissão (salva na inspeção por `onPatch`).
 * Sem `onPatch` (ou somente leitura), a faixa mostra os valores sem edição.
 */
export const ReportOverlay: React.FC<{ inspection: SpdaInspection; onClose: () => void; onPatch?: (p: LaudoFields) => void; readOnly?: boolean }> = ({ inspection, onClose, onPatch, readOnly }) => {
  const [fields, setFields] = useState<LaudoFields>({ laudoNumber: inspection.laudoNumber, laudoDate: inspection.laudoDate, laudoTime: inspection.laudoTime });
  const v = { ...inspection, ...fields };
  const set = (p: LaudoFields) => { setFields(cur => ({ ...cur, ...p })); onPatch?.(p); };
  const editable = !!onPatch && !readOnly;
  const issued = laudoIssued(v);
  const inp = 'w-full px-2 py-1.5 border border-slate-300 rounded-lg text-xs bg-white disabled:bg-slate-100';
  const toolbar = (
    <div className="flex flex-wrap items-end gap-2 text-xs">
      <label className="flex-1 min-w-[150px]">
        <span className="block text-[10px] font-bold text-slate-600 mb-0.5">Nº do laudo</span>
        <input className={`${inp} font-mono`} value={fields.laudoNumber ?? ''} placeholder={inspection.number} disabled={!editable} onChange={e => set({ laudoNumber: e.target.value })} aria-label="Nº do laudo" />
      </label>
      <label className="w-[150px]">
        <span className="block text-[10px] font-bold text-slate-600 mb-0.5">Data de emissão</span>
        <input type="date" className={inp} value={fields.laudoDate ?? ''} disabled={!editable} onChange={e => set({ laudoDate: e.target.value })} aria-label="Data de emissão do laudo" />
      </label>
      <label className="w-[110px]">
        <span className="block text-[10px] font-bold text-slate-600 mb-0.5">Hora</span>
        <input type="time" className={inp} value={fields.laudoTime ?? ''} disabled={!editable} onChange={e => set({ laudoTime: e.target.value })} aria-label="Hora de emissão do laudo" />
      </label>
      <span className="text-[10px] text-slate-500 basis-full">
        {!fields.laudoDate && !fields.laudoTime ? `Em branco: emissão automática (${formatDateBR(issued.date)} às ${issued.time}).` : 'Saem no cabeçalho do laudo e no relatório técnico.'}
        {editable && !fields.laudoDate && <button type="button" className="ml-1 text-blue-700 underline" onClick={() => set({ laudoDate: issued.date, laudoTime: issued.time })}>Fixar agora</button>}
        {!editable && ' Sem permissão para alterar.'}
      </span>
    </div>
  );
  return (
    <PrintOverlay title={`Laudo ${laudoNumber(v)}`} documentTitle={`${laudoNumber(v)} - Laudo SPDA`} onClose={onClose} toolbar={toolbar}>
      <SpdaReport inspection={v} />
    </PrintOverlay>
  );
};

/**
 * Imprime/gera PDF: no app Android (WebView) usa o serviço de impressão do
 * Android (AndroidBridge.printPage — tem "Salvar como PDF"); no navegador, window.print().
 */
export function printDocument(title: string): void {
  const bridge = (window as unknown as { AndroidBridge?: { printPage?: (t: string) => void } }).AndroidBridge;
  if (bridge && typeof bridge.printPage === 'function') { bridge.printPage(title); return; }
  window.print();
}

/** Moldura de visualização/impressão (laudo completo e relatórios por subsistema). */
export const PrintOverlay: React.FC<{ title: string; documentTitle: string; onClose: () => void; children: React.ReactNode; /** faixa só de tela (ex.: editar nº/data do laudo) */ toolbar?: React.ReactNode }> = ({ title, documentTitle, onClose, children, toolbar }) => {
  const sheetRef = useRef<HTMLDivElement>(null);
  const [busy, setBusy] = useState<null | DocAction>(null);
  const [tip, setTip] = useState('');
  /** Visualizar/Compartilhar PDF e Exportar/Compartilhar Word (ver documentActions.ts). */
  const run = async (action: DocAction) => {
    if (!sheetRef.current || busy) return;
    setBusy(action);
    setTip(action.endsWith('pdf') ? 'Gerando o PDF…' : 'Gerando o arquivo Word…');
    try {
      const sheet = sheetRef.current;
      const msg = action === 'view_pdf' ? await viewPdf(documentTitle, () => printDocument(documentTitle))
        : action === 'share_pdf' ? await sharePdf(sheet, documentTitle, setTip)
        : action === 'export_word' ? await exportWord(sheet, documentTitle)
        : await shareWord(sheet, documentTitle);
      setTip(msg);
      setTimeout(() => setTip(''), 8000);
    } catch (err) {
      setTip(`Não foi possível concluir: ${err instanceof Error ? err.message : 'erro'}`);
    } finally {
      setBusy(null);
    }
  };
  const docBtn = (action: DocAction, label: string, Icon: React.ElementType, color: string) => (
    <button type="button" onClick={() => void run(action)} disabled={busy !== null}
      className={`flex items-center justify-center gap-2.5 min-h-[56px] px-3 py-2 rounded-xl text-white text-sm font-bold leading-tight text-left disabled:opacity-60 ${color}`}>
      {busy === action ? <RefreshCw className="w-5 h-5 shrink-0 animate-spin" /> : <Icon className="w-5 h-5 shrink-0" />}
      <span>{label}</span>
    </button>
  );
  useEffect(() => {
    const style = document.createElement('style');
    style.setAttribute('data-spda-print', '');
    style.textContent = PRINT_CSS;
    document.head.appendChild(style);
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', onKey);
    const prevTitle = document.title;
    document.title = documentTitle;
    return () => { style.remove(); window.removeEventListener('keydown', onKey); document.title = prevTitle; };
  }, [documentTitle, onClose]);

  return createPortal(
    <div id={PRINT_ROOT_ID}>
      <div className="spda-overlay fixed inset-0 z-[80] bg-slate-900/60 overflow-y-auto p-0 sm:p-6" role="dialog" aria-label={title}>
        <div className="print:hidden sticky top-0 z-10 max-w-[210mm] mx-auto flex items-center justify-between gap-2 bg-[#0A2540] text-white px-4 py-2.5 sm:rounded-t-xl">
          <span className="text-sm font-bold">{title}</span>
          <div className="flex flex-wrap justify-end gap-2">
            <button type="button" onClick={() => printDocument(documentTitle)} className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-white/10 text-white text-xs font-bold rounded-lg hover:bg-white/20">
              <Printer className="w-3.5 h-3.5" /> Imprimir
            </button>
            <button type="button" onClick={onClose} className="p-1.5 rounded-lg hover:bg-white/10" aria-label="Fechar laudo"><X className="w-4 h-4" /></button>
          </div>
        </div>
        {/* botões no padrão do APP de Ensaios de EPI */}
        <div className="print:hidden max-w-[210mm] mx-auto bg-white border-b border-slate-200 p-3 grid grid-cols-2 gap-3">
          {docBtn('view_pdf', 'Visualizar PDF', FileText, 'bg-[#1D4ED8] hover:bg-[#1E40AF]')}
          {docBtn('share_pdf', 'Compartilhar PDF', Share2, 'bg-[#0A2540] hover:bg-[#071B30]')}
          {docBtn('export_word', 'Exportar Word (.doc)', FileType2, 'bg-[#2B579A] hover:bg-[#244A82]')}
          {docBtn('share_word', 'Compartilhar Word', Share2, 'bg-[#185ABD] hover:bg-[#144C9E]')}
        </div>
        {toolbar && <div className="print:hidden max-w-[210mm] mx-auto bg-blue-50 border-b border-blue-200 px-4 py-2">{toolbar}</div>}
        {tip && <div className="print:hidden sticky top-[44px] z-10 max-w-[210mm] mx-auto bg-amber-50 border-b border-amber-300 text-amber-900 text-xs px-4 py-2">{tip}</div>}
        <div ref={sheetRef} className="spda-sheet max-w-[210mm] mx-auto bg-white shadow-2xl p-4 sm:px-[13mm] sm:py-[12mm] sm:rounded-b-xl">
          {children}
        </div>
      </div>
    </div>,
    document.body
  );
};
