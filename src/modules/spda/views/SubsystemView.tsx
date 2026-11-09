/**
 * Telas por subsistema do menu SPDA — Captação, Descida, Aterramento e DPS:
 * inspeção visual (itens do checklist do subsistema) e os ensaios de
 * continuidade correspondentes do Anexo F, para a OS escolhida.
 * Os dados são os mesmos da inspeção (aba Inspeção visual / Ensaios).
 */
import React, { useCallback, useState } from 'react';
import { Anchor, ArrowDownToLine, Cable, FileText, Printer, ShieldCheck, Zap } from 'lucide-react';
import { canEditSpda, saveInspection } from '../repository';
import { SUBSYSTEM_LABEL } from '../checklist';
import { CONTINUITY_KIND_SHORT, VERDICT_LABEL, continuityStatus, kindOf, subsystemSummary, type Verdict } from '../rules';
import type { ContinuityKind, SpdaInspection, SpdaSubsystem } from '../types';
import { ChecklistTab } from './ChecklistTab';
import { BuildingTabs, InspectionPicker, useSelectedBuilding, useSelectedInspection } from './InspectionPicker';
import { TestsTab } from './TestsTab';
import { EmptyState, alertError, btnPrimary, btnSecondary, cardCls } from './ui';
import { SubsystemReportOverlay } from './SubsystemReport';

const VERDICT_PILL: Record<Verdict, string> = {
  conforme: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  nao_conforme: 'bg-red-50 text-red-700 border-red-200',
  incompleto: 'bg-amber-50 text-amber-700 border-amber-200'
};

interface SubsystemConfig {
  title: string;
  /** Sufixo do nº do relatório simplificado (ex.: SPDA-2026-0001-CAP) */
  code: string;
  subtitle: string;
  icon: React.ElementType;
  color: string;
  subsystems: SpdaSubsystem[];
  kinds: ContinuityKind[];
  grounding: boolean;
  norm: string;
}

const CONFIG: Record<'captacao' | 'descida' | 'aterramento' | 'dps', SubsystemConfig> = {
  captacao: {
    title: 'Captação', code: 'CAP', icon: Zap, color: 'bg-amber-500', subsystems: ['captacao'], kinds: ['F4'], grounding: false,
    subtitle: 'Captores, malha, fixação, materiais e componentes naturais da cobertura',
    norm: 'NBR 5419-3:2026, 5.3, 5.6, 5.7 · ensaio comprobatório captação ↔ BEP ≤ 0,2 Ω (F.4.4)'
  },
  descida: {
    title: 'Descida', code: 'DES', icon: ArrowDownToLine, color: 'bg-sky-600', subsystems: ['descida'], kinds: ['F1', 'F2', 'F5'], grounding: false,
    subtitle: 'Número e espaçamento das descidas, percurso, conexões e conectores de ensaio',
    norm: 'NBR 5419-3:2026, 5.4 e 5.6.3 · armaduras ≤ 1 Ω (F.1, F.2) · descidas não naturais (F.5)'
  },
  aterramento: {
    title: 'Aterramento', code: 'ATE', icon: Anchor, color: 'bg-emerald-700', subsystems: ['aterramento', 'protecao_pessoas'], kinds: ['F3', 'F6'], grounding: true,
    subtitle: 'Arranjo do eletrodo, anel, caixas de inspeção, materiais, tensões de toque e passo',
    norm: 'NBR 5419-3:2026, 5.5 e Seção 8 · fundação ≤ 1 Ω (F.3) · anel não natural (F.6) · resistência de aterramento informativa (7.1.4)'
  },
  dps: {
    title: 'DPS e equipotencialização', code: 'DPS', icon: ShieldCheck, color: 'bg-violet-600', subsystems: ['equipotencializacao'], kinds: ['EQ'], grounding: false,
    subtitle: 'BEP/BEL, ligações equipotenciais, DPS classe I, centelhadores e fronteiras de ZPR',
    norm: 'NBR 5419-3:2026, 6.2 e 7.3.3 g) · NBR 5419-4:2026, 5.4, 5.5 e 9.2'
  }
};

const SubsystemScreen: React.FC<{ which: keyof typeof CONFIG }> = ({ which }) => {
  const cfg = CONFIG[which];
  const Icon = cfg.icon;
  const { inspections, selected, select } = useSelectedInspection();
  const readOnly = !canEditSpda() || selected?.status === 'concluido';

  const save = (insp: SpdaInspection) => {
    try { saveInspection(insp); } catch (err) { alertError(err, 'Não foi possível salvar'); }
  };

  const { buildings, building, selectBuilding } = useSelectedBuilding(selected);
  const tests = selected && building ? selected.continuity.filter(t => t.buildingId === building.id && cfg.kinds.includes(kindOf(t))) : [];
  const nc = tests.filter(t => continuityStatus(t) === 'nao_conforme').length;
  // relatório simplificado: só a edificação selecionada ou todas
  const [report, setReport] = useState<null | 'one' | 'all'>(null);
  const closeReport = useCallback(() => setReport(null), []);
  const summary = selected && building ? subsystemSummary({ ...selected, buildings }, cfg.subsystems, cfg.kinds, building.id) : null;

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-3">
        <div className={`w-10 h-10 rounded-xl ${cfg.color} text-white flex items-center justify-center`}><Icon className="w-5 h-5" /></div>
        <div>
          <h2 className="text-lg font-black text-slate-900 flex items-center gap-2">{cfg.title}<span className="text-[10px] font-bold px-1.5 py-0.5 rounded bg-amber-100 text-amber-800 border border-amber-300">DEMO</span></h2>
          <p className="text-xs text-slate-500">{cfg.subtitle}</p>
        </div>
      </div>
      <p className="text-[11px] text-slate-500 flex items-center gap-1"><Cable className="w-3.5 h-3.5" /> {cfg.norm}</p>

      {inspections.length === 0 ? (
        <EmptyState icon={Icon} title="Nenhuma inspeção de SPDA" text="Crie uma inspeção em Inspeções e laudos (ou em Edificações) para lançar os dados." />
      ) : (
        <>
          <InspectionPicker inspections={inspections} selected={selected} select={select} />
          {selected && <BuildingTabs insp={selected} buildings={buildings} building={building} select={selectBuilding} />}
          {selected && building && (
            <div className={`${cardCls} p-3 flex flex-wrap items-center gap-2`}>
              <FileText className="w-4 h-4 text-slate-500" />
              <span className="text-xs font-bold text-slate-700">Relatório simplificado — {cfg.title}</span>
              {summary && <span className={`text-[10px] font-bold px-2 py-0.5 rounded-full border ${VERDICT_PILL[summary.verdict]}`}>{VERDICT_LABEL[summary.verdict].replace('SPDA ', '')}</span>}
              <span className="flex-1" />
              <button type="button" className={btnPrimary} onClick={() => setReport('one')}><Printer className="w-3.5 h-3.5" /> {building.name || 'Esta edificação'}</button>
              {buildings.length > 1 && <button type="button" className={btnSecondary} onClick={() => setReport('all')}><Printer className="w-3.5 h-3.5" /> Todas as edificações</button>}
            </div>
          )}
          {selected && report && (
            <SubsystemReportOverlay
              inspection={{ ...selected, buildings }}
              cfg={cfg}
              buildingId={report === 'one' ? building?.id : undefined}
              onClose={closeReport}
            />
          )}
          {selected && building && (
            <>
              <h3 className="font-bold text-sm text-slate-900">
                Inspeção visual — {cfg.subsystems.map(s => SUBSYSTEM_LABEL[s]).join(' e ')}
                <span className="font-normal text-teal-700"> · {building.name || 'Edificação'}</span>
              </h3>
              <ChecklistTab
                key={`chk_${selected.id}_${building.id}`}
                only={cfg.subsystems}
                buildingId={building.id}
                checklist={selected.checklist}
                readOnly={readOnly}
                onChange={checklist => save({ ...selected, checklist })}
              />
              <div className="flex flex-wrap items-center justify-between gap-2">
                <h3 className="font-bold text-sm text-slate-900">
                  Ensaios ({cfg.kinds.map(k => CONTINUITY_KIND_SHORT[k]).join(', ')})
                  <span className="font-normal text-teal-700"> · {building.name || 'Edificação'}</span>
                </h3>
                {tests.length > 0 && <span className={`text-[11px] ${nc ? 'text-red-700 font-bold' : 'text-slate-500'}`}>{tests.length} medição(ões) · {nc} não conforme(s)</span>}
              </div>
              <TestsTab
                key={`ens_${selected.id}_${building.id}`}
                value={selected}
                building={building}
                readOnly={readOnly}
                kinds={cfg.kinds}
                showGrounding={cfg.grounding}
                onChange={patch => save({ ...selected, ...patch })}
              />
            </>
          )}
        </>
      )}
    </div>
  );
};

export const SpdaCaptacaoView: React.FC = () => <SubsystemScreen which="captacao" />;
export const SpdaDescidaView: React.FC = () => <SubsystemScreen which="descida" />;
export const SpdaAterramentoView: React.FC = () => <SubsystemScreen which="aterramento" />;
export const SpdaDpsView: React.FC = () => <SubsystemScreen which="dps" />;
