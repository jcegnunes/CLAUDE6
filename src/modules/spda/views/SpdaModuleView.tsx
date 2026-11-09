/**
 * Tela principal do módulo SPDA (DEMO): lista de inspeções e editor com abas
 * Dados → Inspeção visual → Ensaios → Conclusão/Laudo. Gravação automática no
 * aparelho (sem Supabase).
 */
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { ArrowLeft, CheckCircle2, ClipboardCheck, FileText, Plus, RotateCcw, Trash2, Zap } from 'lucide-react';
import {
  canEditSpda, createInspection, deleteInspection, getInspection, listInspections, saveInspection, subscribeSpda
} from '../repository';
import {
  NORM_LABEL, PROTECTION_LEVEL_LABEL, VERDICT_LABEL, checklistProgress, continuityStatus, inspectionVerdict,
  listNonConformities, nextInspectionDate, suggestRecommendations, type Verdict
} from '../rules';
import type { SpdaInspection } from '../types';
import { formatDateBR } from '../../../utils/dateUtils';
import { ChecklistTab } from './ChecklistTab';
import { BuildingTabs, useSelectedBuilding } from './InspectionPicker';
import { takePendingInspection } from './navigation';
import { GeneralTab } from './GeneralTab';
import { ReportOverlay } from './ReportView';
import { TestsTab } from './TestsTab';
import { EmptyState, Field, alertError, btnDanger, btnPrimary, btnSecondary, cardCls, inputCls } from './ui';

type Tab = 'dados' | 'checklist' | 'ensaios' | 'conclusao';
const TABS: Array<{ id: Tab; label: string }> = [
  { id: 'dados', label: '1. Dados' },
  { id: 'checklist', label: '2. Inspeção visual' },
  { id: 'ensaios', label: '3. Ensaios' },
  { id: 'conclusao', label: '4. Conclusão e laudo' }
];

const VERDICT_CLS: Record<Verdict, string> = {
  conforme: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  nao_conforme: 'bg-red-50 text-red-700 border-red-200',
  incompleto: 'bg-amber-50 text-amber-700 border-amber-200'
};

const VerdictBadge: React.FC<{ verdict: Verdict }> = ({ verdict }) => (
  <span className={`inline-block px-2 py-0.5 rounded-full border text-[10px] font-bold whitespace-nowrap ${VERDICT_CLS[verdict]}`}>{VERDICT_LABEL[verdict]}</span>
);

const Header: React.FC<{ title: string; right?: React.ReactNode }> = ({ title, right }) => (
  <div className="flex flex-wrap items-center justify-between gap-3">
    <div className="flex items-center gap-3">
      <div className="w-10 h-10 rounded-xl bg-amber-500 text-white flex items-center justify-center"><Zap className="w-5 h-5" /></div>
      <div>
        <h2 className="text-lg font-black text-slate-900 flex items-center gap-2">{title}<span className="text-[10px] font-bold px-1.5 py-0.5 rounded bg-amber-100 text-amber-800 border border-amber-300">DEMO</span></h2>
        <p className="text-xs text-slate-500">Inspeção e ensaios de SPDA conforme {NORM_LABEL} — dados somente neste aparelho</p>
      </div>
    </div>
    {right}
  </div>
);

// ------------------------------------------------------------------- editor
const Editor: React.FC<{ initial: SpdaInspection; onBack: () => void }> = ({ initial, onBack }) => {
  const [draft, setDraft] = useState<SpdaInspection>(initial);
  const [tab, setTab] = useState<Tab>('dados');
  const [showReport, setShowReport] = useState(false);
  const [savedAt, setSavedAt] = useState<string | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const latest = useRef(draft);
  const dirty = useRef(false);
  const canEdit = canEditSpda();
  const readOnly = !canEdit || draft.status === 'concluido';

  const flush = useCallback(() => {
    if (timer.current) { clearTimeout(timer.current); timer.current = null; }
    if (!dirty.current) return;
    try {
      saveInspection(latest.current);
      dirty.current = false;
      setSavedAt(new Date().toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit', second: '2-digit' }));
    } catch (err) {
      alertError(err, 'Não foi possível salvar');
    }
  }, []);

  useEffect(() => () => flush(), [flush]);

  const patch = (p: Partial<SpdaInspection>) => {
    setDraft(prev => {
      const next = { ...prev, ...p };
      latest.current = next;
      return next;
    });
    dirty.current = true;
    if (timer.current) clearTimeout(timer.current);
    timer.current = setTimeout(flush, 600);
  };

  const setStatus = (status: SpdaInspection['status']) => {
    latest.current = { ...latest.current, status };
    setDraft(latest.current);
    dirty.current = true;
    flush();
  };

  const { buildings, building, selectBuilding } = useSelectedBuilding(draft);
  const prog = checklistProgress(draft.checklist, buildings);
  const ncs = listNonConformities(draft);
  const verdict = inspectionVerdict(draft);
  const pendingTests = draft.continuity.filter(t => continuityStatus(t) === 'pendente').length;
  const closeReport = useCallback(() => setShowReport(false), []);

  return (
    <div className="space-y-4">
      <Header
        title={draft.number}
        right={
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-[11px] text-slate-500">{readOnly ? (draft.status === 'concluido' ? 'Concluído (somente leitura)' : 'Somente leitura') : savedAt ? `Salvo às ${savedAt}` : 'Gravação automática'}</span>
            <button type="button" className={btnSecondary} onClick={() => { flush(); onBack(); }}><ArrowLeft className="w-3.5 h-3.5" /> Lista</button>
            <button type="button" className={btnPrimary} onClick={() => { flush(); setShowReport(true); }}><FileText className="w-3.5 h-3.5" /> Ver laudo</button>
          </div>
        }
      />

      <div className={`${cardCls} p-3 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-slate-600`}>
        <span><b className="text-slate-900">{draft.clientName || 'Cliente não informado'}</b>{draft.buildingName ? ` — ${draft.buildingName}` : ''}</span>
        <span>{PROTECTION_LEVEL_LABEL[draft.protectionLevel]}</span>
        <span>Checklist {prog.answered}/{prog.total}</span>
        <span>Ensaios: {draft.continuity.length} continuidade · {draft.grounding.length} aterramento{pendingTests ? ` (${pendingTests} pendente(s))` : ''}</span>
        <VerdictBadge verdict={verdict} />
      </div>

      <div className="flex gap-1 overflow-x-auto border-b border-slate-200" role="tablist">
        {TABS.map(t => (
          <button
            key={t.id}
            type="button"
            role="tab"
            aria-selected={tab === t.id}
            onClick={() => { flush(); setTab(t.id); }}
            className={`px-3 py-2 text-xs font-bold whitespace-nowrap border-b-2 -mb-px ${tab === t.id ? 'border-blue-600 text-blue-700' : 'border-transparent text-slate-500 hover:text-slate-800'}`}
          >{t.label}</button>
        ))}
      </div>

      {tab === 'dados' && <GeneralTab value={draft} onChange={patch} readOnly={readOnly} />}
      {tab === 'checklist' && (
        <div className="space-y-4">
          {/* documentação: geral da OS */}
          <ChecklistTab only={['documentacao']} checklist={draft.checklist} onChange={checklist => patch({ checklist })} readOnly={readOnly} />
          {/* demais subsistemas: por edificação */}
          <BuildingTabs insp={draft} buildings={buildings} building={building} select={selectBuilding} />
          {building && (
            <ChecklistTab
              key={building.id}
              only={['captacao', 'descida', 'aterramento', 'equipotencializacao', 'protecao_pessoas']}
              buildingId={building.id}
              checklist={draft.checklist}
              onChange={checklist => patch({ checklist })}
              readOnly={readOnly}
            />
          )}
        </div>
      )}
      {tab === 'ensaios' && (
        <div className="space-y-4">
          <BuildingTabs insp={draft} buildings={buildings} building={building} select={selectBuilding} />
          {building && <TestsTab key={building.id} value={draft} building={building} onChange={patch} readOnly={readOnly} />}
        </div>
      )}
      {tab === 'conclusao' && (
        <div className="space-y-4">
          <section className={`${cardCls} p-4`}>
            <div className="flex flex-wrap items-center justify-between gap-2 mb-3">
              <h3 className="font-bold text-sm text-slate-900">Resultado</h3>
              <VerdictBadge verdict={verdict} />
            </div>
            {ncs.length === 0 ? (
              <p className="text-xs text-slate-600">Nenhuma não conformidade registrada.</p>
            ) : (
              <ul className="text-xs text-slate-700 space-y-1 list-disc pl-5">
                {ncs.map((n, i) => <li key={i}>{n.description}</li>)}
              </ul>
            )}
            {verdict === 'incompleto' && (
              <p className="text-[11px] text-amber-700 mt-2">Para concluir: responda todos os itens do checklist (C, NC ou NA) e complete valor e critério de todos os ensaios de continuidade lançados.</p>
            )}
            <p className="text-[11px] text-slate-500 mt-2">Próxima inspeção: {formatDateBR(nextInspectionDate(draft))}</p>
          </section>

          <section className={`${cardCls} p-4`}>
            <fieldset disabled={readOnly} className="space-y-3">
              <Field label="Recomendações">
                <textarea className={inputCls} rows={6} value={draft.recommendations || ''} onChange={e => patch({ recommendations: e.target.value })} />
              </Field>
              <button type="button" className={btnSecondary} onClick={() => patch({ recommendations: suggestRecommendations(draft) })}>Gerar sugestão a partir das não conformidades</button>
              <Field label="Observações gerais">
                <textarea className={inputCls} rows={3} value={draft.notes || ''} onChange={e => patch({ notes: e.target.value })} />
              </Field>
            </fieldset>
          </section>

          <div className="flex flex-wrap gap-2">
            <button type="button" className={btnPrimary} onClick={() => { flush(); setShowReport(true); }}><FileText className="w-3.5 h-3.5" /> Ver / imprimir laudo</button>
            {canEdit && draft.status === 'rascunho' && (
              <button
                type="button"
                className={btnSecondary}
                onClick={() => {
                  if (verdict === 'incompleto' && !window.confirm('A inspeção está incompleta. Concluir mesmo assim?')) return;
                  setStatus('concluido');
                }}
              ><CheckCircle2 className="w-3.5 h-3.5" /> Concluir inspeção</button>
            )}
            {canEdit && draft.status === 'concluido' && (
              <button type="button" className={btnSecondary} onClick={() => setStatus('rascunho')}><RotateCcw className="w-3.5 h-3.5" /> Reabrir para edição</button>
            )}
          </div>
        </div>
      )}

      {showReport && <ReportOverlay inspection={draft} onClose={closeReport} />}
    </div>
  );
};

// --------------------------------------------------------------------- lista

export const SpdaModuleView: React.FC = () => {
  const [, setVersion] = useState(0);
  const [openId, setOpenId] = useState<string | null>(() => takePendingInspection());
  const [query, setQuery] = useState('');

  useEffect(() => subscribeSpda(() => setVersion(v => v + 1)), []);

  const open = openId ? getInspection(openId) : undefined;
  if (open) return <Editor key={open.id} initial={open} onBack={() => setOpenId(null)} />;

  const canEdit = canEditSpda();
  const q = query.trim().toLowerCase();
  const items = listInspections().filter(i => !q || [i.number, i.serviceOrderNumber, i.clientName, i.buildingName, i.city].some(x => (x || '').toLowerCase().includes(q)));

  const create = () => {
    try {
      const saved = saveInspection(createInspection());
      setOpenId(saved.id);
    } catch (err) {
      alertError(err, 'Não foi possível criar a inspeção');
    }
  };

  const remove = (i: SpdaInspection) => {
    if (!window.confirm(`Excluir a inspeção ${i.number} deste aparelho? (somente dados da demo SPDA)`)) return;
    deleteInspection(i.id);
  };

  return (
    <div className="space-y-4">
      <Header
        title="SPDA — Inspeções e laudos"
        right={canEdit ? <button type="button" className={btnPrimary} onClick={create}><Plus className="w-3.5 h-3.5" /> Nova inspeção</button> : undefined}
      />

      <div className="text-[11px] text-amber-800 bg-amber-50 border border-amber-200 rounded-xl px-3 py-2">
        Versão de demonstração: as inspeções ficam salvas apenas neste navegador/aparelho e não são enviadas à nuvem nem incluídas no backup.
      </div>

      {items.length > 0 || q ? (
        <>
          <input className={`${inputCls} max-w-sm`} placeholder="Buscar por nº, cliente, edificação ou cidade…" value={query} onChange={e => setQuery(e.target.value)} aria-label="Buscar inspeções" />
          <div className={`${cardCls} divide-y divide-slate-100`}>
            {items.map(i => {
              const prog = checklistProgress(i.checklist, i.buildings || []);
              return (
                <div key={i.id} className="flex flex-col sm:flex-row sm:items-center gap-2 px-4 py-3">
                  <button type="button" className="flex-1 min-w-0 text-left" onClick={() => setOpenId(i.id)}>
                    <p className="text-sm font-bold text-slate-900">
                      <span className="font-mono text-blue-700 mr-2">{i.number}</span>
                      {i.clientName || 'Cliente não informado'}
                    </p>
                    <p className="text-[11px] text-slate-500">
                      {[i.serviceOrderNumber && `OS ${i.serviceOrderNumber}`, i.buildingName, i.city, formatDateBR(i.inspectionDate), PROTECTION_LEVEL_LABEL[i.protectionLevel], `checklist ${prog.answered}/${prog.total}`].filter(Boolean).join(' · ')}
                    </p>
                  </button>
                  <div className="flex items-center gap-2 shrink-0">
                    {i.status === 'concluido' && <span className="text-[10px] font-bold text-slate-600 bg-slate-100 border border-slate-300 rounded-full px-2 py-0.5">Concluído</span>}
                    <VerdictBadge verdict={inspectionVerdict(i)} />
                    {canEdit && (
                      <button type="button" className={btnDanger} onClick={() => remove(i)} aria-label={`Excluir ${i.number}`}><Trash2 className="w-3.5 h-3.5" /></button>
                    )}
                  </div>
                </div>
              );
            })}
            {items.length === 0 && <p className="px-4 py-6 text-center text-xs text-slate-400">Nenhuma inspeção encontrada.</p>}
          </div>
        </>
      ) : (
        <EmptyState
          icon={ClipboardCheck}
          title="Nenhuma inspeção de SPDA"
          text="Cadastre a edificação, faça a inspeção visual por subsistema (captação, descidas, aterramento, equipotencialização), lance os ensaios de continuidade e aterramento e gere o laudo."
          action={canEdit ? <button type="button" className={btnPrimary} onClick={create}><Plus className="w-3.5 h-3.5" /> Nova inspeção</button> : undefined}
        />
      )}
    </div>
  );
};

export default SpdaModuleView;
