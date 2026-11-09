/**
 * Relatório simplificado de um subsistema (Captação, Descida, Aterramento ou
 * DPS) para a OS escolhida: identificação, por edificação os itens da inspeção
 * visual, os ensaios do Anexo F, não conformidades, parecer e fotos.
 * É parcial — não substitui o laudo completo (NBR 5419-3:2026, 7.5.4).
 */
import React from 'react';
import { CHECKLIST_TEMPLATE, SUBSYSTEM_LABEL } from '../checklist';
import { readCompanyInfo } from '../repository';
import {
  BUILDING_TYPE_LABEL, CONTINUITY_KIND_LABEL, PROTECTION_LEVEL_LABEL, SPDA_KIND_LABEL, TEST_STATUS_LABEL, VERDICT_LABEL,
  answerOf, buildingsOf, fmtMilliOhm, continuityStatus, currentOutOfRange, effectiveLimit, fmtOhm, formatCoordinates, groundingStatus,
  groundingValue, isPerBuilding, kindOf, levelEfficiencyPct, subsystemSummary, theoreticalResistance, type Verdict
} from '../rules';
import type { ContinuityKind, SpdaBuilding, SpdaInspection, SpdaSubsystem } from '../types';
import { formatDateBR } from '../../../utils/dateUtils';
import { Info, PrintOverlay, STATUS_TXT_CLS, Section, td, th } from './ReportView';

export interface SubsystemReportConfig {
  title: string;
  code: string;
  subsystems: SpdaSubsystem[];
  kinds: ContinuityKind[];
  grounding: boolean;
  norm: string;
}

const VERDICT_BOX: Record<Verdict, string> = {
  conforme: 'bg-emerald-50 border-emerald-400 text-emerald-800',
  nao_conforme: 'bg-red-50 border-red-400 text-red-800',
  incompleto: 'bg-amber-50 border-amber-400 text-amber-800'
};

const verdictText = (v: Verdict) => VERDICT_LABEL[v].replace('SPDA ', '').replace('INSPEÇÃO ', '');

const BuildingBlock: React.FC<{ insp: SpdaInspection; buildings: SpdaBuilding[]; b?: SpdaBuilding; index: number; cfg: SubsystemReportConfig }> = ({ insp, buildings, b, index, cfg }) => {
  const s = subsystemSummary({ ...insp, buildings }, cfg.subsystems, cfg.kinds, b?.id);
  const items = CHECKLIST_TEMPLATE.filter(i => cfg.subsystems.includes(i.subsystem));
  const grounding = cfg.grounding ? insp.grounding.filter(g => !b || g.buildingId === b.id) : [];
  const answers = items.map(i => ({ item: i, a: answerOf(insp.checklist, i.id, isPerBuilding(i.subsystem) ? b?.id : undefined) }));
  const photos = [
    ...s.tests.flatMap(t => (t.photos || []).map(p => ({ key: p.id, src: p.url, caption: `${p.caption} — ${fmtMilliOhm(t.resistanceOhm)}` }))),
    ...grounding.flatMap(g => (g.photos || []).map(p => ({ key: p.id, src: p.url, caption: `${p.caption} — ${fmtOhm(groundingValue(g), 2)}` }))),
    ...answers.flatMap(({ item, a }) => (a.photos || []).map((src, i) => ({ key: `${item.id}_${i}`, src, caption: `${item.id} — ${item.title}` })))
  ];

  return (
    <Section n={index + 2} title={b ? `${b.name || `Edificação ${index + 1}`}` : 'Resultado'} right={verdictText(s.verdict)}>
      {b && (
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 mb-2">
          <Info label="Tipo" value={`${BUILDING_TYPE_LABEL[b.type]}${b.activity ? ` — ${b.activity}` : ''}`} />
          <Info label="SPDA existente" value={SPDA_KIND_LABEL[b.spdaKind].label} />
          <Info label="Nível de proteção" value={`${PROTECTION_LEVEL_LABEL[b.protectionLevel]} (${levelEfficiencyPct(b.protectionLevel)} %)`} />
          <Info label="Coordenadas" value={formatCoordinates(b.latitude, b.longitude)} />
        </div>
      )}

      <p className="font-bold text-[11px] text-[#0A2540] mb-1">Inspeção visual — {cfg.subsystems.map(x => SUBSYSTEM_LABEL[x]).join(' e ')} ({s.answered}/{s.total} itens · {s.nc} NC)</p>
      <table className="w-full border-collapse mb-3">
        <thead><tr><th className={`${th} w-14`}>Item</th><th className={th}>Verificação</th><th className={`${th} w-28`}>Referência</th><th className={`${th} w-10 text-center`}>Sit.</th><th className={th}>Observação</th></tr></thead>
        <tbody>
          {answers.map(({ item, a }) => (
            <tr key={item.id}>
              <td className={`${td} font-mono`}>{item.id}</td>
              <td className={td}>{item.title}</td>
              <td className={`${td} text-[10px] text-slate-500`}>{item.reference}</td>
              <td className={`${td} text-center font-bold ${a.status === 'NC' ? 'text-red-700' : a.status === 'C' ? 'text-emerald-700' : 'text-slate-500'}`}>{a.status || '—'}</td>
              <td className={td}>{a.obs?.trim() || ''}</td>
            </tr>
          ))}
        </tbody>
      </table>

      <p className="font-bold text-[11px] text-[#0A2540] mb-1">Ensaios de continuidade ({s.tests.length} · {s.testsNc} NC)</p>
      {s.tests.length === 0 ? <p className="text-slate-500 mb-3">Nenhum ensaio lançado.</p> : (
        <table className="w-full border-collapse mb-3">
          <thead><tr><th className={th}>Ensaio</th><th className={th}>De</th><th className={th}>Para</th><th className={th}>Corrente</th><th className={th}>Medido</th><th className={th}>Critério</th><th className={th}>Resultado</th></tr></thead>
          <tbody>
            {s.tests.map(t => {
              const st = continuityStatus(t);
              const theory = theoreticalResistance(t);
              return (
                <React.Fragment key={t.id}>
                <tr>
                  <td className={td}>{CONTINUITY_KIND_LABEL[kindOf(t)]}</td>
                  <td className={td}>{t.from || '—'}</td>
                  <td className={td}>{t.to || '—'}</td>
                  <td className={td}>{t.currentA != null ? `${String(t.currentA).replace('.', ',')} A` : '—'}{currentOutOfRange(t) && <span className="block text-[9px] text-amber-700">fora de 1–5 A</span>}</td>
                  <td className={`${td} font-mono`}>{fmtMilliOhm(t.resistanceOhm)}</td>
                  <td className={`${td} font-mono`}>{effectiveLimit(t) !== null ? `≤ ${fmtMilliOhm(effectiveLimit(t))}` : '—'}{theory !== null && <span className="block text-[9px] text-slate-500">ρ·L/S {fmtMilliOhm(theory, 2)}</span>}</td>
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

      {cfg.grounding && grounding.length > 0 && (
        <>
          <p className="font-bold text-[11px] text-[#0A2540] mb-1">Resistência de aterramento — informativo (NBR 5419-3:2026, 7.1.4)</p>
          <table className="w-full border-collapse mb-3">
            <thead><tr><th className={th}>Ponto</th><th className={th}>Método</th><th className={th}>Adotado</th><th className={th}>Situação</th></tr></thead>
            <tbody>
              {grounding.map(g => {
                const st = groundingStatus(g);
                return (
                  <React.Fragment key={g.id}>
                  <tr>
                    <td className={td}>{g.point || '—'}</td>
                    <td className={td}>{g.method === 'queda_potencial' ? 'Queda de potencial' : g.method === 'alicate' ? 'Alicate terrômetro' : 'Outro'}</td>
                    <td className={`${td} font-mono`}>{fmtOhm(groundingValue(g), 2)}</td>
                    <td className={`${td} ${STATUS_TXT_CLS[st]}`}>{TEST_STATUS_LABEL[st]}</td>
                  </tr>
                  {g.obs?.trim() && (
                    <tr><td colSpan={20} className="border border-slate-300 px-1.5 py-1 text-[10px] text-slate-600 bg-slate-50"><b>Obs.:</b> {g.obs.trim()}</td></tr>
                  )}
                  </React.Fragment>
                );
              })}
            </tbody>
          </table>
        </>
      )}

      <p className="font-bold text-[11px] text-[#0A2540] mb-1">Não conformidades ({s.ncs.length})</p>
      {s.ncs.length === 0 ? <p className="mb-2">Nenhuma não conformidade neste subsistema.</p> : (
        <ol className="list-decimal pl-5 space-y-0.5 mb-2">
          {s.ncs.map((n, i) => (
            <li key={i}>{n.description} <span className="text-slate-500">({n.reference})</span>{n.obs?.trim() && <span className="block text-slate-600">{n.obs.trim()}</span>}</li>
          ))}
        </ol>
      )}

      <div className={`border-2 rounded-lg px-3 py-1.5 text-center font-black text-xs ${VERDICT_BOX[s.verdict]}`}>
        {cfg.title.toUpperCase()}{b ? ` — ${(b.name || `Edificação ${index + 1}`).toUpperCase()}` : ''}: {verdictText(s.verdict)}
      </div>

      {photos.length > 0 && (
        <div className="grid grid-cols-3 gap-2 mt-3">
          {photos.map(p => (
            <figure key={p.key} className="spda-avoid border border-slate-200 rounded bg-white p-1">
              <img src={p.src} alt={p.caption} className="w-full h-28 object-cover rounded" />
              <figcaption className="text-[9px] text-slate-600 mt-0.5">{p.caption}</figcaption>
            </figure>
          ))}
        </div>
      )}
    </Section>
  );
};

export const SubsystemReport: React.FC<{ inspection: SpdaInspection; cfg: SubsystemReportConfig; buildingId?: string }> = ({ inspection: v, cfg, buildingId }) => {
  const company = readCompanyInfo();
  const rt = company?.technicalResponsible;
  const all = buildingsOf(v);
  const scope = buildingId ? all.filter(b => b.id === buildingId) : all;
  const verdicts = scope.map(b => subsystemSummary({ ...v, buildings: all }, cfg.subsystems, cfg.kinds, b.id).verdict);
  const overall: Verdict = (scope.length ? verdicts : [subsystemSummary(v, cfg.subsystems, cfg.kinds).verdict])
    .reduce<Verdict>((acc, x) => (acc === 'nao_conforme' || x === 'nao_conforme' ? 'nao_conforme' : acc === 'incompleto' || x === 'incompleto' ? 'incompleto' : 'conforme'), 'conforme');
  const instrument = v.instruments.find(i => i.use === 'continuidade');

  return (
    <div className="space-y-4 text-slate-800">
      <div className="border-b-2 border-[#0A2540] pb-3 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          {company?.logoUrl
            ? <img src={company.logoUrl} alt={company.name} className="max-h-14 max-w-[140px] object-contain" />
            : <div className="w-12 h-12 rounded-xl bg-[#0A2540] flex items-center justify-center text-white font-black text-xl">JVM</div>}
          <div>
            <p className="font-black text-base text-[#0A2540]">{company?.name || 'Empresa'}</p>
            <p className="text-[10px] text-slate-500">{[company?.creaCompanyRegister, company?.cnpj && `CNPJ: ${company.cnpj}`].filter(Boolean).join(' • ')}</p>
          </div>
        </div>
        <div className="sm:text-right">
          <span className="text-[10px] font-bold uppercase tracking-widest text-slate-400 block">Relatório nº</span>
          <span className="text-base font-extrabold text-blue-700 font-mono block">{v.number}-{cfg.code}</span>
          {v.serviceOrderNumber && <span className="text-xs font-semibold text-slate-700 font-mono block">OS: {v.serviceOrderNumber}</span>}
          <span className="text-[11px] text-slate-500 block">Inspeção: {formatDateBR(v.inspectionDate)}</span>
        </div>
      </div>

      <div className="text-center">
        <h1 className="font-black text-sm sm:text-base text-[#0A2540] uppercase tracking-wide">Relatório simplificado — {cfg.title}</h1>
        <p className="text-[11px] text-slate-500">{cfg.norm}</p>
        <p className="text-[10px] text-slate-500 mt-1">Relatório parcial do subsistema; não substitui o laudo completo da inspeção ({v.number}).</p>
        <p className="text-[10px] font-bold text-amber-700">VERSÃO DE DEMONSTRAÇÃO</p>
      </div>

      <Section n={1} title="Identificação">
        <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
          <Info label="Cliente" value={v.clientName} />
          <Info label="Ordem de serviço" value={v.serviceOrderNumber} />
          <Info label="Endereço" value={[v.address, v.city && `${v.city}${v.state ? `/${v.state}` : ''}`].filter(Boolean).join(' — ')} />
          <Info label="Edificações" value={scope.length ? scope.map(b => b.name || 'Edificação').join(' / ') : v.buildingName} />
          <Info label="Instrumento (continuidade)" value={instrument && [instrument.description, instrument.model, instrument.serialNumber && `nº ${instrument.serialNumber}`].filter(Boolean).join(' · ')} />
          <Info label="Responsável pela inspeção" value={[v.technicianName, v.technicianCrea].filter(Boolean).join(' — ')} />
        </div>
      </Section>

      {scope.length
        ? scope.map(b => <BuildingBlock key={b.id} insp={v} buildings={all} b={b} index={all.indexOf(b)} cfg={cfg} />)
        : <BuildingBlock insp={v} buildings={all} index={0} cfg={cfg} />}

      <div className={`border-2 rounded-lg px-3 py-2 text-center font-black text-sm ${VERDICT_BOX[overall]}`}>
        {cfg.title.toUpperCase()}: {VERDICT_LABEL[overall].replace('SPDA ', '')}
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 items-end gap-6 pt-6 spda-avoid">
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
    </div>
  );
};

export const SubsystemReportOverlay: React.FC<{ inspection: SpdaInspection; cfg: SubsystemReportConfig; buildingId?: string; onClose: () => void }> = ({ inspection, cfg, buildingId, onClose }) => (
  <PrintOverlay title={`Relatório ${cfg.title} — ${inspection.number}`} documentTitle={`${inspection.number}-${cfg.code} - Relatório ${cfg.title}`} onClose={onClose}>
    <SubsystemReport inspection={inspection} cfg={cfg} buildingId={buildingId} />
  </PrintOverlay>
);
