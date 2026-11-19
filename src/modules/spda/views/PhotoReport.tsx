/**
 * Relatório fotográfico de cada subsistema (captação, descidas, aterramento, DPS/equipotencialização), no padrão
 * visual do laudo. Reúne as fotos já lançadas na inspeção: itens do checklist (inspeção visual), ensaios de
 * continuidade e, no aterramento, medições de resistência — numeradas, com legenda e a origem de cada foto.
 */
import React from 'react';
import { CHECKLIST_TEMPLATE } from '../checklist';
import { readCompanyInfo } from '../repository';
import {
  CONTINUITY_GROUP_INFO, answerOf, buildingsOf, continuityCode, continuityGroupOf, continuityStatus, fmtMilliOhm, fmtOhm,
  groundingValue, isPerBuilding, type ContinuityGroup, AIR_TERMINATION_ORDER, airTerminationName, DOWN_CONDUCTOR_ORDER, downConductorName, GROUNDING_SYSTEM_LABEL, GROUNDING_SYSTEM_ORDER, groundingSystemName } from '../rules';
import type { SpdaBuilding, SpdaInspection } from '../types';
import { formatDateBR } from '../../../utils/dateUtils';
import { Info, PrintOverlay, Section } from './ReportView';
import { SUBSYSTEMS_OF } from './ContinuitySheet';
import { findingsOf } from '../rules';

export const PHOTO_TITLE: Record<ContinuityGroup, string> = {
  captacao: 'Relatório fotográfico — Subsistema de captação',
  descida: 'Relatório fotográfico — Subsistema de descidas',
  aterramento: 'Relatório fotográfico — Subsistema de aterramento',
  dps: 'Relatório fotográfico — Equipotencialização e DPS'
};

export type PhotoEntry = {
  key: string; url: string; caption: string; source: 'sistema' | 'inspecao' | 'continuidade' | 'aterramento' | 'registro';
  origin: string; detail?: string; tone?: 'ok' | 'bad' | 'info'; when?: string; author?: string;
};

const STATUS_TXT: Record<string, string> = { C: 'Conforme', NC: 'Não conforme', NA: 'Não aplicável' };
const fmtWhen = (iso?: string) => (iso ? new Date(iso).toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' }) : undefined);

/**
 * Tira da legenda o texto que o app gera sozinho e que o relatório já mostra na origem e no resultado
 * (código e trecho do ensaio, "leitura do instrumento …", "Captação existente —", "Resistência de aterramento — ponto",
 * "CAP-NC-01 — Não conformidade:"). Sobra só o que o inspetor escreveu ('' se nada).
 */
export function cleanCaption(raw: string | undefined): string {
  let s = (raw || '').trim();
  s = s.replace(/\s*—\s*leitura do instrumento\b.*$/i, '');
  s = s.replace(/^[A-Z]{2,4}-[CR]\d+\s*·[^—]*—\s*continuidade\s+[^—]*/, '');
  s = s.replace(/^(?:Captação|Descida|Aterramento) existente\s*—\s*/, '');
  s = s.replace(/^Resistência de aterramento\s*—\s*[^—]*/, '');
  s = s.replace(/^[A-Z]{2,4}-(?:NC|M)-\d+\s*—\s*(?:Não conformidade|Melhoria)\s*:?\s*/, '');
  return s.replace(/^[\s—·:,-]+|[\s—·:,-]+$/g, '').trim();
}

/** Fotos do subsistema para uma edificação, na ordem: inspeção visual, continuidade, resistência de aterramento. */
export function photoEntries(v: SpdaInspection, b: SpdaBuilding | undefined, group: ContinuityGroup): PhotoEntry[] {
  const out: PhotoEntry[] = [];
  // sistemas de captação existentes (fotos por tipo selecionado)
  if (group === 'aterramento' && b?.groundingSystem) {
    GROUNDING_SYSTEM_ORDER.filter(t => b.groundingSystem!.types?.includes(t)).forEach(t => {
      (b.groundingSystem!.photos?.[t] || []).forEach(p => out.push({
        key: p.id, url: p.url, source: 'sistema', caption: p.caption,
        origin: `Aterramento existente · ${GROUNDING_SYSTEM_LABEL[t].label}`,
        detail: groundingSystemName(b, t) !== GROUNDING_SYSTEM_LABEL[t].label ? groundingSystemName(b, t) : undefined,
        when: fmtWhen(p.timestamp), author: p.userName
      }));
    });
  }
  if (group === 'descida' && b?.downConductor) {
    DOWN_CONDUCTOR_ORDER.filter(t => b.downConductor!.types?.includes(t)).forEach(t => {
      (b.downConductor!.photos?.[t] || []).forEach(p => out.push({
        key: p.id, url: p.url, source: 'sistema', caption: p.caption,
        origin: `Descida existente · ${downConductorName(b, t)}`,
        when: fmtWhen(p.timestamp), author: p.userName
      }));
    });
  }
  if (group === 'captacao' && b?.airTermination) {
    AIR_TERMINATION_ORDER.filter(t => b.airTermination!.types?.includes(t)).forEach(t => {
      (b.airTermination!.photos?.[t] || []).forEach(p => out.push({
        key: p.id, url: p.url, source: 'sistema', caption: p.caption,
        origin: `Sistema de captação existente · ${airTerminationName(b, t)}`,
        when: fmtWhen(p.timestamp), author: p.userName
      }));
    });
  }
  CHECKLIST_TEMPLATE.filter(i => SUBSYSTEMS_OF[group].includes(i.subsystem)).forEach(item => {
    const a = answerOf(v.checklist, item.id, isPerBuilding(item.subsystem) ? b?.id : undefined);
    (a.photos || []).forEach((url, i) => out.push({
      key: `${item.id}_${i}`, url, source: 'inspecao',
      caption: a.obs?.trim() || item.title,
      origin: `Inspeção visual · ${item.id} — ${item.title}`,
      detail: a.status ? STATUS_TXT[a.status] : undefined,
      tone: a.status === 'NC' ? 'bad' : a.status === 'C' ? 'ok' : undefined
    }));
  });
  v.continuity.filter(t => continuityGroupOf(t) === group && (!b || t.buildingId === b.id)).forEach(t => {
    const st = continuityStatus(t);
    (t.photos || []).forEach(p => out.push({
      key: p.id, url: p.url, source: 'continuidade', caption: p.caption,
      origin: `Continuidade ${continuityCode(v.continuity, t)} · ${t.from || '?'} → ${t.to || '?'}`,
      detail: `${fmtMilliOhm(t.resistanceOhm)} — ${st === 'conforme' ? 'Aprovado' : st === 'nao_conforme' ? 'Reprovado' : 'Pendente'}`,
      tone: st === 'conforme' ? 'ok' : st === 'nao_conforme' ? 'bad' : undefined,
      when: fmtWhen(p.timestamp), author: p.userName
    }));
  });
  // não conformidades e melhorias registradas após os ensaios
  findingsOf(v, b?.id, SUBSYSTEMS_OF[group]).forEach(({ f, code }) => {
    f.photos.forEach(p => out.push({
      key: p.id, url: p.url, source: 'registro',
      caption: f.comment.trim() || p.caption,
      origin: `${f.kind === 'nc' ? 'Não conformidade' : 'Melhoria'} ${code}`,
      detail: f.kind === 'nc' ? 'Não conforme' : 'Melhoria recomendada', tone: f.kind === 'nc' ? 'bad' : 'info',
      when: fmtWhen(p.timestamp), author: p.userName
    }));
  });
  if (group === 'aterramento') {
    v.grounding.filter(g => !b || g.buildingId === b.id).forEach(g => {
      (g.photos || []).forEach(p => out.push({
        key: p.id, url: p.url, source: 'aterramento', caption: p.caption,
        origin: `Resistência de aterramento · ${g.point || 'ponto'}`,
        detail: groundingValue(g) != null ? `${fmtOhm(groundingValue(g), 2)} — informativo` : undefined, tone: 'info',
        when: fmtWhen(p.timestamp), author: p.userName
      }));
    });
  }
  return out.map(e => ({ ...e, caption: cleanCaption(e.caption) }));
}

const TONE: Record<NonNullable<PhotoEntry['tone']>, string> = { ok: 'text-emerald-700', bad: 'text-red-700 font-bold', info: 'text-sky-700' };
const SOURCE_TITLE: Record<PhotoEntry['source'], string> = {
  sistema: 'Sistema de captação existente',
  inspecao: 'Inspeção visual', continuidade: 'Ensaios de continuidade', aterramento: 'Resistência de aterramento',
  registro: 'Não conformidades e melhorias'
};

/** Uma edificação: cabeçalho + identificação + fotos em grade de 2 colunas, agrupadas pela origem. */
export const PhotoReport: React.FC<{
  inspection: SpdaInspection; building?: SpdaBuilding; group: ContinuityGroup; startAt?: number;
  /** dentro do relatório técnico: sem cabeçalho/nº e sem assinaturas; identificação só com a edificação */
  embedded?: boolean;
}> = ({ inspection: v, building: b, group, startAt = 1, embedded = false }) => {
  const company = readCompanyInfo();
  const rt = company?.technicalResponsible;
  const info = CONTINUITY_GROUP_INFO[group];
  const photos = photoEntries(v, b, group);
  const local = [v.address, v.city && `${v.city}${v.state ? `/${v.state}` : ''}`].filter(Boolean).join(' — ');
  const sources = (['sistema', 'inspecao', 'continuidade', 'registro', 'aterramento'] as const).filter(s => photos.some(p => p.source === s));
  let n = startAt - 1;

  return (
    <div className="space-y-4 text-slate-800">
      {!embedded && <div className="border-b-2 border-[#0A2540] pb-3 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
        <div className="flex items-center gap-3 min-h-[48px]">
          {company?.logoUrl && <img src={company.logoUrl} alt="Logotipo" className="max-h-14 max-w-[150px] object-contain" />}
        </div>
        <div className="sm:text-right">
          <span className="text-[10px] font-bold uppercase tracking-widest text-slate-400 block">Relatório fotográfico nº</span>
          <span className="text-base font-extrabold text-blue-700 font-mono block">{v.number}-{info.code}-F</span>
          {v.serviceOrderNumber && <span className="text-xs font-semibold text-slate-700 font-mono block">OS: {v.serviceOrderNumber}</span>}
          <span className="text-[11px] text-slate-500 block">Inspeção: {formatDateBR(v.inspectionDate)}</span>
        </div>
      </div>}

      <div className="text-center">
        <h1 className="font-black text-sm sm:text-base text-[#0A2540] uppercase tracking-wide">{PHOTO_TITLE[group]}</h1>
        {!embedded && <p className="text-[11px] text-slate-500">Registro fotográfico da inspeção e dos ensaios do SPDA — ABNT NBR 5419:2026</p>}
      </div>

      <Section n={1} title="Identificação" right={`${photos.length} foto(s)`}>
        {embedded ? <Info label="Edificação" value={b?.name || buildingsOf(v)[0]?.name} /> : <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
          <Info label="Cliente" value={v.clientName} />
          <Info label="Edificação" value={b?.name || buildingsOf(v)[0]?.name} />
          <Info label="Local" value={local} />
          <Info label="Ordem de serviço" value={v.serviceOrderNumber} />
          <Info label="Inspeção" value={v.number} />
          <Info label="Subsistema" value={info.label} />
        </div>}
      </Section>

      {photos.length === 0 ? (
        <Section n={2} title="Fotos"><p className="text-slate-500">Nenhuma foto registrada para este subsistema nesta edificação (fotos dos itens do checklist e dos ensaios da tela {info.label}).</p></Section>
      ) : sources.map((s, si) => (
        <Section key={s} n={si + 2} title={s === 'sistema' && group === 'descida' ? 'Descidas existentes' : s === 'sistema' && group === 'aterramento' ? 'Aterramento existente' : SOURCE_TITLE[s]} right={`${photos.filter(p => p.source === s).length} foto(s)`}>
          <div className="grid grid-cols-2 gap-3">
            {photos.filter(p => p.source === s).map(p => {
              n += 1;
              return (
                <figure key={p.key} className="spda-avoid border border-slate-300 rounded bg-white p-1.5">
                  <div className="h-[62mm] flex items-center justify-center bg-slate-50 rounded overflow-hidden">
                    <img src={p.url} alt={p.caption} className="max-w-full max-h-full object-contain" />
                  </div>
                  <figcaption className="mt-1 text-[10px] leading-snug">
                    {p.caption && !p.origin.includes(p.caption) ? (
                      <>
                        <p><b>Foto {n}</b> — {p.caption}</p>
                        <p className="text-slate-600">{p.origin}</p>
                      </>
                    ) : (
                      <p><b>Foto {n}</b> — {p.origin}</p>
                    )}
                    {(p.detail || p.when) && (
                      <p className="text-slate-500">
                        {p.detail && <span className={p.tone ? TONE[p.tone] : ''}>{p.detail}</span>}
                        {p.detail && p.when && ' · '}
                        {p.when && <span>{p.when}</span>}
                      </p>
                    )}
                  </figcaption>
                </figure>
              );
            })}
          </div>
        </Section>
      ))}

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

/** Janela do relatório fotográfico: uma parte por edificação (a selecionada ou todas). */
export const PhotoReportOverlay: React.FC<{ inspection: SpdaInspection; buildings: SpdaBuilding[]; group: ContinuityGroup; onClose: () => void }> = ({ inspection, buildings, group, onClose }) => {
  const info = CONTINUITY_GROUP_INFO[group];
  const list = buildings.length ? buildings : [undefined];
  let start = 1;
  return (
    <PrintOverlay
      title={`Relatório fotográfico — ${info.label}${buildings.length === 1 ? ` — ${buildings[0].name}` : ''}`}
      documentTitle={`${inspection.number}-${info.code}-F - Relatório fotográfico ${info.label}${buildings.length === 1 ? ` - ${buildings[0].name}` : ''}`}
      onClose={onClose}
    >
      {list.map((b, i) => {
        const s = start;
        start += photoEntries(inspection, b, group).length;
        return (
          <div key={b?.id || 'geral'} className={i > 0 ? 'spda-break pt-4' : ''}>
            <PhotoReport inspection={inspection} building={b} group={group} startAt={s} />
          </div>
        );
      })}
    </PrintOverlay>
  );
};
