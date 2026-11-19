/**
 * Tela principal do módulo SPDA: lista de inspeções e editor com abas
 * Dados → Inspeção visual → Ensaios → Conclusão/Laudo. Gravação automática no
 * aparelho (sem Supabase).
 */
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { ArrowLeft, CheckCircle2, ClipboardCheck, FileText, Pencil, Plus, RotateCcw, Trash2, Zap } from 'lucide-react';
import {
  canEditSpda, createInspection, deleteInspection, getInspection, listInspections, saveInspection, subscribeSpda
} from '../repository';
import {
  NORM_LABEL, PROTECTION_LEVEL_LABEL, checklistProgress, continuityStatus, inspectionVerdict,
  listImprovements, listNonConformities, nextInspectionDate, suggestRecommendations, withBuildings
} from '../rules';
import type { SpdaInspection } from '../types';
import { formatDateBR } from '../../../utils/dateUtils';
import { useSelectedBuilding } from './InspectionPicker';
import { NEW_INSPECTION_EVENT, takePendingInspection, takePendingNewInspection, takePendingServiceOrderInspection } from './navigation';
import { GuidedBar, GuidedFooter, startGuided, takePendingTab, useGuidedId } from './GuidedFlow';
import { GeneralTab } from './GeneralTab';
import { ChecklistTab } from './ChecklistTab';
import { BuildingsSection } from './BuildingsSection';
import { NewInspectionDialog } from './NewInspectionDialog';
import { ReportOverlay } from './ReportView';
import { ReportOutput } from './ReportTypes';
import { EmptyState, Field, alertError, btnDanger, btnPrimary, btnSecondary, cardCls, inputCls } from './ui';
import { confirmAsk } from './dialogs';
import { laudoIssued, LAUDO_RESULT_LABEL, finalResult, resultFromVerdict, techText, type ShownResult } from '../techReport';
import { ResultPicker } from './TechReportEditor';
import { PhotoFolderProvider, photoFolderOf } from '../photoFolder';
import type { ReportResult } from '../types';

type Tab = 'dados' | 'checklist' | 'ensaios' | 'conclusao';

const RESULT_CLS: Record<ShownResult, string> = {
  aprovado: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  reprovado: 'bg-red-50 text-red-700 border-red-200',
  parcial: 'bg-amber-50 text-amber-700 border-amber-200',
  incompleto: 'bg-slate-50 text-slate-600 border-slate-200'
};

/** Selo do parecer da inspeção (o escolhido na Conclusão ou o calculado). */
const ResultBadge: React.FC<{ insp: SpdaInspection }> = ({ insp }) => {
  const r = finalResult(insp);
  return <span className={`inline-block px-2 py-0.5 rounded-full border text-[10px] font-bold whitespace-nowrap ${RESULT_CLS[r]}`}>{LAUDO_RESULT_LABEL[r]}</span>;
};

const Header: React.FC<{ title: string; right?: React.ReactNode }> = ({ title, right }) => (
  <div className="flex flex-wrap items-center justify-between gap-3">
    <div className="flex items-center gap-3">
      <div className="w-10 h-10 rounded-xl bg-amber-500 text-white flex items-center justify-center"><Zap className="w-5 h-5" /></div>
      <div>
        <h2 className="text-lg font-black text-slate-900 flex items-center gap-2">{title}</h2>
        <p className="text-xs text-slate-500">Inspeção e ensaios de SPDA conforme {NORM_LABEL} — sincronizado com a plataforma</p>
      </div>
    </div>
    {right}
  </div>
);

// ------------------------------------------------------------------- editor
const Editor: React.FC<{ initial: SpdaInspection; onBack: () => void }> = ({ initial, onBack }) => {
  const [draft, setDraft] = useState<SpdaInspection>(initial);
  const [tab, setTab] = useState<Tab>(() => takePendingTab() || 'dados');
  const guidedId = useGuidedId();
  const guided = guidedId === draft.id;
  const showDados = !guided || tab === 'dados';
  const showConclusao = !guided || tab === 'conclusao';
  const [buildingEditing, setBuildingEditing] = useState(false);
  // nova inspeção (ainda sem checklist nem ensaios): a edificação abre já em edição
  const [freshInspection] = useState(() => {
    const bs = initial.buildings || [];
    return initial.checklist.length === 0 && initial.continuity.length === 0 && initial.grounding.length === 0
      && (bs.length === 0 || (bs.length === 1 && /^Edificação 1$/.test(bs[0].name.trim())));
  });
  const [showReport, setShowReport] = useState(false);
  // Salvar na última etapa do passo a passo: abre o relatório simplificado dos ensaios
  const [finalReport, setFinalReport] = useState(false);
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

  const { buildings } = useSelectedBuilding(draft);
  const prog = checklistProgress(draft.checklist, buildings);
  const ncs = listNonConformities(draft);
  const improvements = listImprovements(draft);
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
        <ResultBadge insp={draft} />
      </div>

      {guided && (
        <GuidedBar
          inspectionId={draft.id}
          number={draft.number}
          current={tab === 'conclusao' ? 'laudo' : 'dados'}
          onEditorTab={t => { flush(); setTab(t); window.scrollTo(0, 0); }}
          beforeLeave={flush}
          onFinish={() => setFinalReport(true)}
          blockedMsg={tab === 'dados' && buildingEditing ? 'Salve a edificação (botão Salvar da edificação) ou cancele a edição antes de continuar.' : undefined}
        />
      )}

      {showDados && (guided
        ? (
          // passo a passo (nova inspeção): na etapa Dados, só o cadastro das edificações
          <BuildingsSection
            buildings={buildings}
            onChange={next => patch(withBuildings(draft, next))}
            readOnly={readOnly}
            startEditing={freshInspection}
            onEditingChange={setBuildingEditing}
          />
        )
        : <GeneralTab value={draft} onChange={patch} readOnly={readOnly} />)}
      {showDados && (
        // documentação do SPDA: itens gerais da OS (não dependem da edificação)
        <div className="space-y-2">
          <h3 className="font-bold text-sm text-slate-900 pt-2">Documentação do SPDA <span className="font-normal text-slate-500">· checklist geral da OS</span></h3>
          <PhotoFolderProvider value={photoFolderOf(draft, undefined, 'Documentação')}>
            <ChecklistTab key={'doc_' + draft.id} only={['documentacao']} checklist={draft.checklist} readOnly={readOnly} onChange={checklist => patch({ checklist })} />
          </PhotoFolderProvider>
        </div>
      )}
      {showConclusao && (
        <div className="space-y-4">
          {!guided && <h3 className="font-bold text-sm text-slate-900 pt-2">Conclusão e laudo</h3>}
          <section className={`${cardCls} p-4`}>
            <div className="flex flex-wrap items-center justify-between gap-2 mb-3">
              <h3 className="font-bold text-sm text-slate-900">Resultado</h3>
              <ResultBadge insp={draft} />
            </div>
            {ncs.length === 0 ? (
              <p className="text-xs text-slate-600">Nenhuma não conformidade registrada.</p>
            ) : (
              <ul className="text-xs text-slate-700 space-y-1 list-disc pl-5">
                {ncs.map((n, i) => <li key={i}>{n.buildingName ? `${n.buildingName} — ` : ''}{n.description}</li>)}
              </ul>
            )}
            {improvements.length > 0 && (
              <>
                <p className='text-xs font-bold text-sky-800 mt-3'>Melhorias apontadas ({improvements.length})</p>
                <ul className='text-xs text-slate-700 space-y-1 list-disc pl-5'>
                  {improvements.map(m => <li key={m.code + (m.buildingId || '')}>{m.buildingName ? `${m.buildingName} — ` : ''}{m.code}{m.comment ? ` — ${m.comment}` : ''}</li>)}
                </ul>
              </>
            )}
            {verdict === 'incompleto' && (
              <p className="text-[11px] text-amber-700 mt-2">Nenhum item foi inspecionado ou ensaiado ainda. O parecer considera apenas os itens avaliados (C, NC ou NA) e os ensaios com valor medido.</p>
            )}
            <p className="text-[11px] text-slate-500 mt-2">Próxima inspeção: {formatDateBR(nextInspectionDate(draft))}</p>
          </section>

          <section className={`${cardCls} p-4 space-y-3`}>
            <div>
              <h3 className="font-bold text-sm text-slate-900">Identificação do laudo</h3>
              <p className="text-[11px] text-slate-500">Saem no cabeçalho do laudo e no relatório técnico. Em branco: nº da inspeção e a data/hora em que o laudo é gerado.</p>
            </div>
            <fieldset disabled={!canEdit} className="grid grid-cols-1 sm:grid-cols-3 gap-3">
              <Field label="Nº do laudo">
                <input className={`${inputCls} font-mono`} value={draft.laudoNumber ?? ''} placeholder={draft.number} onChange={e => patch({ laudoNumber: e.target.value })} aria-label="Nº do laudo" />
              </Field>
              <Field label="Data de emissão">
                <input type="date" className={inputCls} value={draft.laudoDate ?? ''} onChange={e => patch({ laudoDate: e.target.value })} aria-label="Data de emissão do laudo" />
              </Field>
              <Field label="Hora de emissão">
                <input type="time" className={inputCls} value={draft.laudoTime ?? ''} onChange={e => patch({ laudoTime: e.target.value })} aria-label="Hora de emissão do laudo" />
              </Field>
            </fieldset>
            <p className="text-[11px] text-slate-600 -mt-1">
              No laudo: <b className="font-mono">{draft.laudoNumber?.trim() || draft.number}</b> · emissão <b>{formatDateBR(laudoIssued(draft).date)} às {laudoIssued(draft).time}</b>
              {!draft.laudoDate && !draft.laudoTime && <span className="text-slate-400"> (automático)</span>}
              {canEdit && !draft.laudoDate && (
                <button type="button" className="ml-2 text-blue-700 underline" onClick={() => { const n = laudoIssued({}); patch({ laudoDate: n.date, laudoTime: n.time }); }}>fixar agora</button>
              )}
            </p>
            <div>
              <h3 className="font-bold text-sm text-slate-900">Parecer do laudo</h3>
              <p className="text-[11px] text-slate-500">Escolha o resultado que sai no laudo e no relatório técnico. Em <b>Automático</b>, vale o calculado pelos itens e ensaios.</p>
            </div>
            <ResultPicker
              label="Parecer do laudo"
              chosen={draft.techReport?.results?.final}
              computed={resultFromVerdict(verdict)}
              readOnly={readOnly}
              onChange={(r: ReportResult | undefined) => {
                const results = { ...draft.techReport?.results };
                if (r) results.final = r; else delete results.final;
                patch({ techReport: { ...draft.techReport, results } });
              }}
            />
            <div>
              <div className="flex flex-wrap items-center justify-between gap-2 mb-1">
                <label htmlFor="laudo_parecer" className="font-bold text-slate-700 text-xs">Texto do parecer (seção 9 — Conclusão do laudo)</label>
                <span className="flex items-center gap-2">
                  <span className="text-[10px] text-slate-400">{draft.techReport?.laudoConclusao?.trim() ? 'texto editado' : 'texto padrão'}</span>
                  {draft.techReport?.laudoConclusao?.trim() && !readOnly && (
                    <button type="button" className={btnSecondary} onClick={() => patch({ techReport: { ...draft.techReport, laudoConclusao: '' } })}><RotateCcw className="w-3.5 h-3.5" /> Restaurar texto padrão</button>
                  )}
                </span>
              </div>
              <textarea id="laudo_parecer" className={inputCls} rows={4} disabled={readOnly}
                value={draft.techReport?.laudoConclusao?.trim() ? draft.techReport.laudoConclusao : techText(draft, 'laudoConclusao')}
                onChange={e => patch({ techReport: { ...draft.techReport, laudoConclusao: e.target.value } })} />
            </div>
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
                onClick={async () => {
                  if (verdict === 'incompleto' && !(await confirmAsk('Nenhum item foi inspecionado ou ensaiado. Concluir mesmo assim?', { confirmLabel: 'Concluir' }))) return;
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

      {guided && (
        <GuidedFooter
          inspectionId={draft.id}
          current={tab === 'conclusao' ? 'laudo' : 'dados'}
          onEditorTab={t => { flush(); setTab(t); window.scrollTo(0, 0); }}
          beforeLeave={flush}
          onFinish={() => setFinalReport(true)}
          blockedMsg={tab === 'dados' && buildingEditing ? 'Salve a edificação (botão Salvar da edificação) ou cancele a edição antes de continuar.' : undefined}
        />
      )}

      {finalReport && <ReportOutput inspection={getInspection(draft.id) || draft} kind="simplificado" group="todos" buildingId="" onClose={() => setFinalReport(false)} />}
      {showReport && <ReportOverlay inspection={draft} onClose={closeReport} readOnly={!canEdit} onPatch={p => patch(p)} />}
    </div>
  );
};

// --------------------------------------------------------------------- lista

export const SpdaModuleView: React.FC = () => {
  const [, setVersion] = useState(0);
  // "Executar Ensaios" da tela Ordens de Serviço: a inspeção dessa OS (ou a nova inspeção já com a OS)
  const [fromOs] = useState(() => {
    const osId = takePendingServiceOrderInspection();
    if (!osId) return null;
    const existing = listInspections().find(i => i.serviceOrderId === osId);
    return { osId, inspectionId: existing?.id || null };
  });
  // abre a inspeção da OS no passo a passo (como em "Editar")
  useEffect(() => { if (fromOs?.inspectionId) startGuided(fromOs.inspectionId); }, [fromOs]);
  const [openId, setOpenId] = useState<string | null>(() => fromOs?.inspectionId || takePendingInspection());
  const [query, setQuery] = useState('');
  const [reportId, setReportId] = useState<string | null>(null);
  const [askOsId] = useState(() => (fromOs && !fromOs.inspectionId ? fromOs.osId : undefined));
  const [asking, setAsking] = useState(() => (!!askOsId && canEditSpda()) || (takePendingNewInspection() && canEditSpda()));
  const closeListReport = useCallback(() => setReportId(null), []);

  useEffect(() => subscribeSpda(() => setVersion(v => v + 1)), []);
  // "Nova inspeção" da barra inferior do app com esta tela já aberta
  useEffect(() => {
    const onNew = () => { if (takePendingNewInspection() && canEditSpda()) { setOpenId(null); setAsking(true); } };
    window.addEventListener(NEW_INSPECTION_EVENT, onNew);
    return () => window.removeEventListener(NEW_INSPECTION_EVENT, onNew);
  }, []);

  const open = openId ? getInspection(openId) : undefined;
  if (open) return <Editor key={open.id} initial={open} onBack={() => setOpenId(null)} />;

  const canEdit = canEditSpda();
  const q = query.trim().toLowerCase();
  const items = listInspections().filter(i => !q || [i.number, i.serviceOrderNumber, i.clientName, i.buildingName, i.city].some(x => (x || '').toLowerCase().includes(q)));

  // nova inspeção: sempre pede a OS (existente ou nova) antes de criar
  const create = () => setAsking(true);
  /** Editar: abre a inspeção no mesmo passo a passo da realização (Dados → Captação → … → Conclusão e laudo). */
  const edit = (i: SpdaInspection) => {
    startGuided(i.id);
    setOpenId(i.id);
  };
  const created = (saved: SpdaInspection) => {
    setAsking(false);
    startGuided(saved.id);
    setOpenId(saved.id);
  };

  const remove = async (i: SpdaInspection) => {
    if (!(await confirmAsk(`Excluir a inspeção ${i.number} e o laudo dela deste aparelho? As edificações, a inspeção visual, os ensaios e as fotos também serão excluídos.`))) return;
    deleteInspection(i.id);
  };

  return (
    <div className="space-y-4">
      <Header
        title="SPDA — Inspeções e laudos"
        right={canEdit ? <button type="button" className={btnPrimary} onClick={create}><Plus className="w-3.5 h-3.5" /> Nova inspeção</button> : undefined}
      />

      {items.length > 0 || q ? (
        <>
          <input className={`${inputCls} max-w-sm`} placeholder="Buscar por nº, cliente, edificação ou cidade…" value={query} onChange={e => setQuery(e.target.value)} aria-label="Buscar inspeções" />
          <div className={`${cardCls} divide-y divide-slate-100`}>
            {items.map(i => {
              const prog = checklistProgress(i.checklist, i.buildings || []);
              return (
                <div key={i.id} className="flex flex-col sm:flex-row sm:items-center gap-2 px-4 py-3">
                  <button type="button" className="flex-1 min-w-0 text-left" onClick={() => edit(i)}>
                    <p className="text-sm font-bold text-slate-900">
                      <span className="font-mono text-blue-700 mr-2">{i.number}</span>
                      {i.clientName || 'Cliente não informado'}
                    </p>
                    <p className="text-[11px] text-slate-500">
                      {[i.serviceOrderNumber && `OS ${i.serviceOrderNumber}`, i.buildingName, i.city, formatDateBR(i.inspectionDate), PROTECTION_LEVEL_LABEL[i.protectionLevel], `checklist ${prog.answered}/${prog.total}`].filter(Boolean).join(' · ')}
                    </p>
                  </button>
                  {/* celular: selos numa linha e os 3 botões dividindo a largura; tela larga: tudo numa linha */}
                  <div className="flex flex-col sm:flex-row sm:items-center gap-2 sm:shrink-0 w-full sm:w-auto">
                    <div className="flex flex-wrap items-center gap-2">
                      {i.status === 'concluido' && <span className="text-[10px] font-bold text-slate-600 bg-slate-100 border border-slate-300 rounded-full px-2 py-0.5">Concluído</span>}
                      <ResultBadge insp={i} />
                    </div>
                    <div className="grid grid-cols-3 gap-2 sm:flex">
                      <button type="button" className={`${btnSecondary} min-w-0 !px-2 sm:!px-3`} onClick={() => edit(i)} aria-label={`Editar ${i.number}`}><Pencil className="w-3.5 h-3.5 shrink-0" /> Editar</button>
                      <button type="button" className={`${btnSecondary} min-w-0 !px-2 sm:!px-3`} onClick={() => setReportId(i.id)} aria-label={`Laudo ${i.number}`}><FileText className="w-3.5 h-3.5 shrink-0" /> Laudo</button>
                      <button type="button" className={`${btnDanger} min-w-0 !px-2 sm:!px-3`} disabled={!canEdit} onClick={() => remove(i)} aria-label={`Excluir ${i.number}`}><Trash2 className="w-3.5 h-3.5 shrink-0" /> Excluir</button>
                    </div>
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
      {reportId && getInspection(reportId) && (
        <ReportOverlay
          inspection={getInspection(reportId)!}
          onClose={closeListReport}
          readOnly={!canEdit}
          onPatch={p => { const cur = getInspection(reportId); if (cur) saveInspection({ ...cur, ...p }); }}
        />
      )}
      {asking && <NewInspectionDialog initialOsId={askOsId} onClose={() => setAsking(false)} onCreated={created} />}
    </div>
  );
};

export default SpdaModuleView;
