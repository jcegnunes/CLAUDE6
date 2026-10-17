/**
 * Orçamentos com vários serviços. Cada item vem de uma precificação já feita,
 * do cadastro de serviços ou é digitado na hora (e pode ser salvo no cadastro).
 * Aprovado, gera as contas a receber; a proposta em PDF mostra só os preços.
 */
import React, { useState } from 'react';
import {
  Calculator, CheckCircle2, ChevronDown, ChevronUp, Copy, FilePlus2, FileText, ListPlus, Pencil, Plus, RefreshCw, Save, Search,
  Trash2, Wrench, X
} from 'lucide-react';
import { navigateTo } from '../../registry';
import {
  approveBudget, canDeleteGestao, canManageFinance, canPrice, deleteBudget, getBudgets, getClientsForGestao, getPricings,
  getServiceOrdersForGestao, getServices, getSettings, saveBudget
} from '../repository';
import {
  DEFAULT_SERVICE_CATEGORIES, SERVICE_UNITS, addDays, budgetItemFromPricing, budgetItemFromService, budgetSummary, budgetTotal,
  calculateBudgetItem, costsFromSettings, formatDateBr, formatMoney, manualBudgetItem, normalizePricingInput, pricingExpiryDate, todayIso
} from '../rules';
import { exportBudgetPdf } from '../proposalPdf';
import type { Budget, BudgetItem, CatalogService, PricingReferenceValues, PricingStatus } from '../types';
import { PricingFields } from './PricingFields';
import { BreakdownCard } from './PricingPanel';
import { ServiceEditor, emptyService, servicePrice } from './ServicesPanel';
import {
  EmptyState, Field, Modal, NoAccess, NumberInput, PRICING_STATUS_LABEL, PricingStatusBadge, alertError, btnDanger, btnPrimary,
  btnSecondary, cardCls, inputCls
} from './ui';

const STATUS_LABEL: Record<PricingStatus, string> = { ...PRICING_STATUS_LABEL, aprovada: 'Aprovado', enviada: 'Enviado', recusada: 'Recusado' };

function emptyBudget(): Budget {
  return {
    id: '', companyId: '', createdAt: '', updatedAt: '',
    number: '', title: '', date: todayIso(), validityDays: 15, items: [], costsSnapshot: costsFromSettings(getSettings()),
    total: 0, status: 'rascunho', paymentTerms: '', executionTime: ''
  };
}

export const BudgetsPanel: React.FC = () => {
  const [editing, setEditing] = useState<Budget | null>(null);
  const [approving, setApproving] = useState<Budget | null>(null);
  const [statusFilter, setStatusFilter] = useState<PricingStatus | ''>('');
  const [search, setSearch] = useState('');
  const [exporting, setExporting] = useState<string | null>(null);
  if (!canPrice()) return <NoAccess text="Orçamentos: acesso do administrador, administrativo e Responsável Técnico." />;

  const q = search.trim().toLowerCase();
  const list = getBudgets().filter(b => (!statusFilter || b.status === statusFilter)
    && (!q || [b.number, b.title, b.clientName, ...b.items.map(i => i.name)].some(v => (v || '').toLowerCase().includes(q))));
  const canDelete = canDeleteGestao();

  const duplicate = (b: Budget) => setEditing({
    ...b, id: '', number: '', createdAt: '', updatedAt: '', serverUpdatedAt: undefined, date: todayIso(), status: 'rascunho',
    receivableGroupId: undefined, items: b.items.map(i => ({ ...i, id: `${i.id}-c${Date.now().toString(36)}` }))
  });

  const exportPdf = async (b: Budget) => {
    setExporting(b.id);
    try { await exportBudgetPdf(b); } catch (err) { alertError(err); } finally { setExporting(null); }
  };

  return (
    <div className="space-y-3">
      <div className={`${cardCls} p-3 flex flex-wrap items-center gap-2`}>
        <div className="flex flex-wrap gap-1">
          {(['', 'rascunho', 'enviada', 'aprovada', 'recusada'] as Array<PricingStatus | ''>).map(s => (
            <button key={s || 'todos'} type="button" onClick={() => setStatusFilter(s)}
              className={`px-2.5 py-1 rounded-full text-[11px] font-bold border ${statusFilter === s ? 'bg-blue-600 text-white border-blue-600' : 'bg-white text-slate-600 border-slate-300'}`}>
              {s ? STATUS_LABEL[s] : 'Todos'}
            </button>
          ))}
        </div>
        <div className="relative flex-1 min-w-[200px]">
          <Search className="w-3.5 h-3.5 text-slate-400 absolute left-2.5 top-1/2 -translate-y-1/2" />
          <input className={`${inputCls} pl-8`} placeholder="Buscar nº, cliente, serviço..." value={search} onChange={e => setSearch(e.target.value)} />
        </div>
        <button type="button" className={btnPrimary} onClick={() => setEditing(emptyBudget())}><Plus className="w-4 h-4" /> Novo orçamento</button>
      </div>

      {list.length === 0 ? (
        <EmptyState icon={ListPlus} title="Nenhum orçamento"
          text="Monte o orçamento com serviços do cadastro, com precificações já feitas ou com serviços digitados na hora." />
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-3">
          {list.map(b => {
            const expired = b.status !== 'aprovada' && b.status !== 'recusada' && pricingExpiryDate(b) < todayIso();
            const sum = budgetSummary(b);
            return (
              <div key={b.id} className={`${cardCls} p-4 space-y-2`}>
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0">
                    <p className="text-[11px] font-mono text-slate-500">{b.number} · {formatDateBr(b.date)}</p>
                    <p className="font-bold text-sm text-slate-900 truncate">{b.clientName || 'Sem cliente'}</p>
                    <p className="text-[11px] text-slate-500 truncate">{b.title || b.items.map(i => i.name).join(', ')}</p>
                  </div>
                  <div className="text-right">
                    <span className="inline-block"><PricingStatusBadge status={b.status} /></span>
                    {expired && <p className="text-[10px] text-amber-700 font-bold mt-0.5">Validade vencida</p>}
                  </div>
                </div>
                <div className="grid grid-cols-3 gap-2 text-center">
                  <div className="p-2 rounded-xl bg-slate-50"><p className="text-[10px] text-slate-500">Serviços</p><p className="text-xs font-black">{b.items.length}</p></div>
                  <div className="p-2 rounded-xl bg-blue-50"><p className="text-[10px] text-slate-500">Total</p><p className="text-xs font-black text-blue-700">{formatMoney(b.total)}</p></div>
                  <div className="p-2 rounded-xl bg-slate-50"><p className="text-[10px] text-slate-500">Lucro previsto</p><p className="text-xs font-black text-emerald-700">{formatMoney(sum.plannedProfit)}</p></div>
                </div>
                <div className="flex flex-wrap justify-end gap-1">
                  {b.status !== 'aprovada' && canManageFinance() && (
                    <button type="button" className={btnSecondary} onClick={() => setApproving(b)}><CheckCircle2 className="w-3.5 h-3.5 text-emerald-600" /> Aprovar</button>
                  )}
                  {b.status === 'aprovada' && canManageFinance() && (
                    <button type="button" className={btnSecondary} onClick={() => navigateTo('gestao_receber')}>Ver contas a receber</button>
                  )}
                  <button type="button" className={btnSecondary} disabled={exporting === b.id} title="Proposta comercial para o cliente (só os preços)" onClick={() => exportPdf(b)}>
                    <FileText className="w-3.5 h-3.5 text-blue-600" /> {exporting === b.id ? 'Gerando…' : 'Proposta em PDF'}
                  </button>
                  <button type="button" className={btnSecondary} title="Duplicar" onClick={() => duplicate(b)}><Copy className="w-3.5 h-3.5" /></button>
                  <button type="button" className={btnSecondary} title="Editar" onClick={() => setEditing(b)}><Pencil className="w-3.5 h-3.5" /></button>
                  {canDelete && (
                    <button type="button" className={btnDanger} title="Excluir"
                      onClick={() => { if (window.confirm(`Excluir o orçamento ${b.number}?${b.status === 'aprovada' ? '\nAs contas a receber já geradas NÃO são apagadas.' : ''}`)) deleteBudget(b.id); }}>
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}

      {editing && <BudgetEditor budget={editing} onClose={() => setEditing(null)} />}
      {approving && <ApproveBudgetDialog budget={approving} onClose={() => setApproving(null)} />}
    </div>
  );
};

// --------------------------------------------------------------------- editor
const MODE_LABEL: Record<BudgetItem['mode'], string> = { planilha: 'Planilha', fixo: 'Preço fixo', precificacao: 'Precificação' };

const BudgetEditor: React.FC<{ budget: Budget; onClose: () => void }> = ({ budget, onClose }) => {
  const [form, setForm] = useState<Budget>(budget);
  const [refresh, setRefresh] = useState(false);
  const [open, setOpen] = useState<string | null>(null);
  const [picker, setPicker] = useState<'servicos' | 'precificacoes' | null>(null);
  const [savingToCatalog, setSavingToCatalog] = useState<BudgetItem | null>(null);
  const clients = getClientsForGestao();
  const orders = getServiceOrdersForGestao();
  const current = costsFromSettings(getSettings());
  const saved = costsFromSettings(form.costsSnapshot || {});
  const costs = !budget.id || refresh ? current : saved;
  const costsChanged = !!budget.id && JSON.stringify(current) !== JSON.stringify(saved) && form.items.some(i => i.mode === 'planilha');
  const locked = budget.status === 'aprovada';
  const items = form.items.map(i => calculateBudgetItem(i, costs));
  const total = budgetTotal(items);
  const summary = budgetSummary({ items });

  const set = <K extends keyof Budget>(k: K, v: Budget[K]) => setForm(f => ({ ...f, [k]: v }));
  const setItem = (id: string, patch: Partial<BudgetItem>) => setForm(f => ({ ...f, items: f.items.map(i => i.id === id ? { ...i, ...patch } : i) }));
  const addItems = (list: BudgetItem[]) => {
    setForm(f => ({ ...f, items: [...f.items, ...list] }));
    if (list.length === 1) setOpen(list[0].id);
  };
  const move = (idx: number, dir: -1 | 1) => setForm(f => {
    const next = [...f.items]; const j = idx + dir;
    if (j < 0 || j >= next.length) return f;
    [next[idx], next[j]] = [next[j], next[idx]];
    return { ...f, items: next };
  });

  const pickClient = (id: string) => {
    const c = clients.find(x => x.id === id);
    setForm(f => ({
      ...f, clientId: id || undefined, clientName: c ? (c.nomeFantasia || c.razaoSocial) : f.clientName,
      responsibleName: f.responsibleName || c?.responsavel || '', responsibleEmail: f.responsibleEmail || c?.email || ''
    }));
  };

  const save = () => {
    try {
      const c = clients.find(x => x.id === form.clientId);
      saveBudget({ ...form, clientName: c ? (c.nomeFantasia || c.razaoSocial) : form.clientName, costsSnapshot: costs },
        { refreshCosts: !budget.id || refresh });
      onClose();
    } catch (err) { alertError(err); }
  };

  return (
    <Modal wide title={budget.id ? `Orçamento ${budget.number}` : 'Novo orçamento'} onClose={onClose}
      footer={<><button type="button" className={btnSecondary} onClick={onClose}>Cancelar</button>{!locked && <button type="button" className={btnPrimary} onClick={save}><Save className="w-4 h-4" /> Salvar orçamento</button>}</>}>
      {locked && <p className="mb-3 text-[11px] text-emerald-800 bg-emerald-50 border border-emerald-200 rounded-xl px-3 py-2">Orçamento aprovado: as contas a receber já foram geradas. Para alterar, duplique.</p>}
      {costsChanged && !refresh && !locked && (
        <div className="mb-3 flex flex-wrap items-center justify-between gap-2 text-[11px] text-amber-800 bg-amber-50 border border-amber-200 rounded-xl px-3 py-2">
          Os valores de referência mudaram desde que este orçamento foi salvo.
          <button type="button" className={btnSecondary} onClick={() => setRefresh(true)}><RefreshCw className="w-3.5 h-3.5" /> Usar valores atuais</button>
        </div>
      )}
      <fieldset disabled={locked} className="space-y-4 min-w-0">
        <div className="grid grid-cols-1 sm:grid-cols-4 gap-3">
          <Field label="Cliente / Empresa" className="sm:col-span-2">
            <select className={inputCls} value={form.clientId || ''} onChange={e => pickClient(e.target.value)}>
              <option value="">—</option>
              {clients.map(c => <option key={c.id} value={c.id}>{c.nomeFantasia || c.razaoSocial}</option>)}
            </select>
          </Field>
          <Field label="Responsável"><input className={inputCls} value={form.responsibleName || ''} onChange={e => set('responsibleName', e.target.value)} /></Field>
          <Field label="E-mail"><input type="email" className={inputCls} value={form.responsibleEmail || ''} onChange={e => set('responsibleEmail', e.target.value)} /></Field>
          <Field label="Descrição geral" className="sm:col-span-2">
            <input className={inputCls} value={form.title} onChange={e => set('title', e.target.value)} placeholder="Ex.: Ensaios de EPI e treinamento NR-10 – unidade Campinas" />
          </Field>
          <Field label="Data"><input type="date" className={inputCls} value={form.date} onChange={e => set('date', e.target.value)} /></Field>
          <Field label="Validade (dias)" hint={`Até ${formatDateBr(addDays(form.date, form.validityDays || 0))}`}>
            <NumberInput value={form.validityDays} step={1} onChange={v => set('validityDays', Math.round(v))} />
          </Field>
        </div>

        <div className={`${cardCls} p-3 space-y-2`}>
          <div className="flex flex-wrap items-center justify-between gap-2">
            <p className="text-xs font-black text-slate-800">Serviços do orçamento</p>
            <div className="flex flex-wrap gap-1">
              <button type="button" className={btnSecondary} onClick={() => setPicker('servicos')}><Wrench className="w-3.5 h-3.5" /> Do cadastro</button>
              <button type="button" className={btnSecondary} onClick={() => setPicker('precificacoes')}><Calculator className="w-3.5 h-3.5" /> Da precificação</button>
              <button type="button" className={btnSecondary} onClick={() => addItems([manualBudgetItem('planilha', costs)])}><FilePlus2 className="w-3.5 h-3.5" /> Avulso (planilha)</button>
              <button type="button" className={btnSecondary} onClick={() => addItems([manualBudgetItem('fixo', costs)])}><FilePlus2 className="w-3.5 h-3.5" /> Avulso (preço fixo)</button>
            </div>
          </div>

          {items.length === 0 && <p className="text-xs text-slate-500 py-3 text-center">Nenhum serviço incluído.</p>}
          {items.map((it, idx) => {
            const expanded = open === it.id;
            return (
              <div key={it.id} className="border border-slate-200 rounded-xl">
                <div className="flex flex-wrap items-center gap-2 p-2">
                  <span className="text-[11px] font-black text-slate-400 w-5 text-center">{idx + 1}</span>
                  <div className="flex-1 min-w-[180px]">
                    <input className={inputCls} value={it.name} placeholder="Nome do serviço" onChange={e => setItem(it.id, { name: e.target.value })} />
                    <p className="text-[10px] text-slate-500 mt-0.5">
                      {MODE_LABEL[it.mode]}{it.code ? ` · ${it.code}` : ''}{it.pricingNumber ? ` · ${it.pricingNumber}` : ''}{!it.serviceId && it.mode !== 'precificacao' ? ' · avulso' : ''}
                    </p>
                  </div>
                  <div className="w-20"><NumberInput value={it.quantity} step={1} onChange={v => setItem(it.id, { quantity: v })} /></div>
                  <div className="w-24">
                    <select className={inputCls} value={it.unit} onChange={e => setItem(it.id, { unit: e.target.value })} aria-label="Unidade">
                      {Array.from(new Set([...SERVICE_UNITS, it.unit])).map(u => <option key={u} value={u}>{u}</option>)}
                    </select>
                  </div>
                  <div className="w-28 text-right">
                    <p className="text-[10px] text-slate-500">{formatMoney(it.unitPrice)} / {it.unit}</p>
                    <p className="text-xs font-black">{formatMoney(it.total)}</p>
                  </div>
                  <div className="flex gap-1">
                    <button type="button" className={btnSecondary} aria-label="Subir" onClick={() => move(idx, -1)} disabled={idx === 0}><ChevronUp className="w-3.5 h-3.5" /></button>
                    <button type="button" className={btnSecondary} aria-label="Descer" onClick={() => move(idx, 1)} disabled={idx === items.length - 1}><ChevronDown className="w-3.5 h-3.5" /></button>
                    <button type="button" className={btnSecondary} onClick={() => setOpen(expanded ? null : it.id)}>{expanded ? 'Fechar' : 'Detalhes'}</button>
                    <button type="button" className={btnDanger} aria-label="Remover serviço" onClick={() => setForm(f => ({ ...f, items: f.items.filter(x => x.id !== it.id) }))}><X className="w-3.5 h-3.5" /></button>
                  </div>
                </div>
                {expanded && (
                  <div className="border-t border-slate-200 p-3 space-y-3 bg-slate-50/50">
                    <Field label="Descrição (vai para a proposta)">
                      <textarea className={inputCls} rows={2} value={it.description} onChange={e => setItem(it.id, { description: e.target.value })} />
                    </Field>
                    {it.mode === 'fixo' && (
                      <Field label={`Preço por ${it.unit} (R$)`} className="max-w-xs"><NumberInput value={it.unitPrice} onChange={v => setItem(it.id, { unitPrice: v })} /></Field>
                    )}
                    {it.mode === 'planilha' && it.input && (
                      <div className="grid grid-cols-1 lg:grid-cols-5 gap-3">
                        <div className="lg:col-span-3"><PricingFields input={it.input} costs={costs} onChange={v => setItem(it.id, { input: v })} /></div>
                        <div className="lg:col-span-2">{it.result && <BreakdownCard result={it.result} />}</div>
                      </div>
                    )}
                    {it.mode === 'precificacao' && (
                      <div className="grid grid-cols-1 lg:grid-cols-5 gap-3">
                        <p className="lg:col-span-3 text-[11px] text-slate-600">
                          Preço trazido da precificação {it.pricingNumber}: não muda com a configuração atual. Para recalcular, converta em "planilha".
                          <button type="button" className={`${btnSecondary} mt-2`}
                            onClick={() => setItem(it.id, { mode: 'planilha', input: normalizePricingInput(it.input), pricingId: undefined, pricingNumber: undefined })}>
                            <RefreshCw className="w-3.5 h-3.5" /> Converter em planilha
                          </button>
                        </p>
                        <div className="lg:col-span-2">{it.result && <BreakdownCard result={it.result} />}</div>
                      </div>
                    )}
                    {!it.serviceId && it.mode !== 'precificacao' && (
                      <button type="button" className={btnSecondary} onClick={() => setSavingToCatalog(it)}>
                        <Save className="w-3.5 h-3.5" /> Salvar no cadastro de serviços
                      </button>
                    )}
                  </div>
                )}
              </div>
            );
          })}

          <div className="flex flex-wrap justify-end items-end gap-6 pt-2">
            {summary.plannedCost > 0 && (
              <div className="text-right text-[11px] text-slate-500">
                <p>Custo previsto (itens da planilha): <b>{formatMoney(summary.plannedCost)}</b></p>
                <p>Lucro previsto: <b className="text-emerald-700">{formatMoney(summary.plannedProfit)}</b></p>
              </div>
            )}
            <div className="text-right">
              <p className="text-[10px] text-slate-500">Total do orçamento</p>
              <p className="text-xl font-black text-blue-700">{formatMoney(total)}</p>
            </div>
          </div>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
          <Field label="Cond. PG"><input className={inputCls} value={form.paymentTerms || ''} onChange={e => set('paymentTerms', e.target.value)} placeholder="Ex.: 50% no aceite e 50% na entrega" /></Field>
          <Field label="Prazo de execução"><input className={inputCls} value={form.executionTime || ''} onChange={e => set('executionTime', e.target.value)} placeholder="Ex.: 5 dias úteis após o aceite" /></Field>
          <Field label="Status">
            <select className={inputCls} value={form.status} onChange={e => set('status', e.target.value as PricingStatus)}>
              {(['rascunho', 'enviada', 'recusada'] as PricingStatus[]).map(s => <option key={s} value={s}>{STATUS_LABEL[s]}</option>)}
              {form.status === 'aprovada' && <option value="aprovada">Aprovado</option>}
            </select>
          </Field>
          {orders.length > 0 && (
            <Field label="Ordem de serviço (opcional)">
              <select className={inputCls} value={form.serviceOrderId || ''} onChange={e => set('serviceOrderId', e.target.value || undefined)}>
                <option value="">—</option>
                {orders.map(o => <option key={o.id} value={o.id}>{o.osNumber}{o.clientName ? ` – ${o.clientName}` : ''}</option>)}
              </select>
            </Field>
          )}
          <Field label="Observações internas" className="sm:col-span-2"><textarea className={inputCls} rows={2} value={form.notes || ''} onChange={e => set('notes', e.target.value)} /></Field>
        </div>
      </fieldset>

      {picker === 'servicos' && <ServicePicker costs={costs} onClose={() => setPicker(null)} onPick={list => { addItems(list.map(s => budgetItemFromService(s, costs))); setPicker(null); }} />}
      {picker === 'precificacoes' && <PricingPicker onClose={() => setPicker(null)} onPick={list => { addItems(list.map(budgetItemFromPricing)); setPicker(null); }} />}
      {savingToCatalog && (
        <ServiceEditor
          categories={DEFAULT_SERVICE_CATEGORIES}
          service={{
            ...emptyService(), name: savingToCatalog.name, description: savingToCatalog.description, unit: savingToCatalog.unit,
            mode: savingToCatalog.mode === 'fixo' ? 'fixo' : 'planilha', unitPrice: savingToCatalog.unitPrice,
            defaultInput: normalizePricingInput(savingToCatalog.input)
          }}
          onClose={() => setSavingToCatalog(null)}
          onSaved={s => setItem(savingToCatalog.id, { serviceId: s.id, code: s.code, name: s.name })}
        />
      )}
    </Modal>
  );
};

const ServicePicker: React.FC<{ costs: PricingReferenceValues; onClose: () => void; onPick: (list: CatalogService[]) => void }> = ({ costs, onClose, onPick }) => {
  const [search, setSearch] = useState('');
  const [chosen, setChosen] = useState<string[]>([]);
  const services = getServices().filter(s => s.active);
  const q = search.trim().toLowerCase();
  const list = services.filter(s => !q || [s.code, s.name, s.category, s.description].some(v => (v || '').toLowerCase().includes(q)));
  return (
    <Modal title="Incluir serviços do cadastro" onClose={onClose}
      footer={<>
        <button type="button" className={btnSecondary} onClick={() => { onClose(); navigateTo('gestao_servicos'); }}>Abrir cadastro</button>
        <button type="button" className={btnPrimary} disabled={!chosen.length} onClick={() => onPick(services.filter(s => chosen.includes(s.id)))}>Incluir {chosen.length || ''}</button>
      </>}>
      {services.length === 0 ? (
        <p className="text-xs text-slate-500">Nenhum serviço cadastrado. Cadastre no menu "Serviços" ou inclua um serviço avulso.</p>
      ) : (
        <div className="space-y-2">
          <input className={inputCls} placeholder="Buscar..." value={search} onChange={e => setSearch(e.target.value)} />
          <div className="divide-y divide-slate-100 border border-slate-200 rounded-xl max-h-[50vh] overflow-y-auto">
            {list.map(s => (
              <label key={s.id} className="flex items-center gap-2 px-3 py-2 text-xs hover:bg-slate-50 cursor-pointer">
                <input type="checkbox" checked={chosen.includes(s.id)} onChange={e => setChosen(c => e.target.checked ? [...c, s.id] : c.filter(x => x !== s.id))} />
                <span className="flex-1 min-w-0">
                  <span className="font-bold text-slate-800">{s.code ? `${s.code} – ` : ''}{s.name}</span>
                  <span className="block text-[10px] text-slate-500">{s.category} · {s.mode === 'planilha' ? 'planilha' : `preço fixo / ${s.unit}`}</span>
                </span>
                <span className="font-bold">{formatMoney(servicePrice(s, costs))}</span>
              </label>
            ))}
          </div>
        </div>
      )}
    </Modal>
  );
};

const PricingPicker: React.FC<{ onClose: () => void; onPick: (list: ReturnType<typeof getPricings>) => void }> = ({ onClose, onPick }) => {
  const [chosen, setChosen] = useState<string[]>([]);
  const pricings = getPricings();
  return (
    <Modal title="Incluir precificações" onClose={onClose}
      footer={<button type="button" className={btnPrimary} disabled={!chosen.length} onClick={() => onPick(pricings.filter(p => chosen.includes(p.id)))}>Incluir {chosen.length || ''}</button>}>
      {pricings.length === 0 ? (
        <p className="text-xs text-slate-500">Nenhuma precificação feita. Use o menu "Precificação" ou inclua um serviço avulso pela planilha.</p>
      ) : (
        <div className="divide-y divide-slate-100 border border-slate-200 rounded-xl max-h-[55vh] overflow-y-auto">
          {pricings.map(p => (
            <label key={p.id} className="flex items-center gap-2 px-3 py-2 text-xs hover:bg-slate-50 cursor-pointer">
              <input type="checkbox" checked={chosen.includes(p.id)} onChange={e => setChosen(c => e.target.checked ? [...c, p.id] : c.filter(x => x !== p.id))} />
              <span className="flex-1 min-w-0">
                <span className="font-bold text-slate-800">{p.number} – {p.title}</span>
                <span className="block text-[10px] text-slate-500">{p.clientName || 'Sem cliente'} · {formatDateBr(p.date)} · {PRICING_STATUS_LABEL[p.status]}</span>
              </span>
              <span className="font-bold">{formatMoney(p.result?.finalPrice ?? 0)}</span>
            </label>
          ))}
        </div>
      )}
    </Modal>
  );
};

const ApproveBudgetDialog: React.FC<{ budget: Budget; onClose: () => void }> = ({ budget, onClose }) => {
  const settings = getSettings();
  const [installments, setInstallments] = useState(1);
  const [firstDueDate, setFirstDueDate] = useState(addDays(todayIso(), 30));
  const [category, setCategory] = useState(settings.receivableCategories[0] || 'Serviços');
  const [accountId, setAccountId] = useState(settings.accounts[0]?.id || '');

  const approve = () => {
    try {
      const created = approveBudget(budget.id, { installments, firstDueDate, category, accountId: accountId || undefined });
      window.alert(`${created.length} conta(s) a receber gerada(s).`);
      onClose();
    } catch (err) { alertError(err); }
  };

  return (
    <Modal title={`Aprovar ${budget.number}`} onClose={onClose}
      footer={<><button type="button" className={btnSecondary} onClick={onClose}>Cancelar</button><button type="button" className={btnPrimary} onClick={approve}>Aprovar e gerar contas a receber</button></>}>
      <div className="space-y-3">
        <p className="text-xs text-slate-700">{budget.clientName || 'Sem cliente'} — <b>{formatMoney(budget.total)}</b> ({budget.items.length} serviço(s))</p>
        {budget.paymentTerms && <p className="text-[11px] text-slate-500">Condições: {budget.paymentTerms}</p>}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <Field label="Parcelas" hint={installments > 1 ? `${installments}× de aprox. ${formatMoney(budget.total / installments)}` : undefined}>
            <NumberInput value={installments} step={1} min={1} onChange={v => setInstallments(Math.max(1, Math.min(60, Math.round(v))))} />
          </Field>
          <Field label="Vencimento da 1ª parcela"><input type="date" className={inputCls} value={firstDueDate} onChange={e => setFirstDueDate(e.target.value)} /></Field>
          <Field label="Categoria">
            <select className={inputCls} value={category} onChange={e => setCategory(e.target.value)}>
              {settings.receivableCategories.map(c => <option key={c} value={c}>{c}</option>)}
            </select>
          </Field>
          <Field label="Conta de recebimento">
            <select className={inputCls} value={accountId} onChange={e => setAccountId(e.target.value)}>
              {settings.accounts.map(a => <option key={a.id} value={a.id}>{a.name}</option>)}
            </select>
          </Field>
        </div>
      </div>
    </Modal>
  );
};
