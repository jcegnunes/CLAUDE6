/** Contas a pagar ou a receber: lista, cadastro (única, parcelada, recorrente) e baixas. */
import React, { useState } from 'react';
import { ArrowDownCircle, ArrowUpCircle, Ban, CheckCircle2, Pencil, Plus, RotateCcw, Search, Trash2 } from 'lucide-react';
import {
  canDeleteGestao, canManageFinance, cancelEntry, createEntries, deleteEntry, getClientsForGestao, getEntries,
  getServiceOrdersForGestao, getSettings, registerPayment, removePayment, reopenEntry, saveEntry
} from '../repository';
import {
  DUE_SOON_DAYS, KIND_LABEL, addDays, entrySituation, formatDateBr, formatMoney, openAmount, paidAmount, roundMoney, todayIso
} from '../rules';
import type { EntryKind, EntrySituation, FinanceEntry, PaymentMethod } from '../types';
import {
  EmptyState, Field, Modal, NoAccess, NumberInput, SituationBadge, alertError, btnDanger, btnPrimary, btnSecondary, cardCls, inputCls
} from './ui';

type Filter = 'abertas' | 'vencidas' | 'semana' | 'quitadas' | 'canceladas' | 'todas';

const FILTER_LABEL: Record<Filter, string> = {
  abertas: 'Em aberto', vencidas: 'Vencidas', semana: `Vencem em ${DUE_SOON_DAYS} dias`, quitadas: 'Quitadas', canceladas: 'Canceladas', todas: 'Todas'
};

export const METHOD_LABEL: Record<PaymentMethod, string> = {
  pix: 'PIX', boleto: 'Boleto', transferencia: 'Transferência', dinheiro: 'Dinheiro', cartao: 'Cartão', cheque: 'Cheque', outro: 'Outro'
};

// filtro pedido por um atalho do Dashboard
let pendingFilter: Filter | null = null;
export function requestEntriesFilter(f: Filter) { pendingFilter = f; }

function matchesFilter(e: FinanceEntry, s: EntrySituation, f: Filter, today: string): boolean {
  switch (f) {
    case 'abertas': return s === 'aberto' || s === 'parcial' || s === 'vencido';
    case 'vencidas': return s === 'vencido';
    case 'semana': return (s === 'aberto' || s === 'parcial') && e.dueDate <= addDays(today, DUE_SOON_DAYS);
    case 'quitadas': return s === 'pago';
    case 'canceladas': return s === 'cancelado';
    default: return true;
  }
}

export const EntriesPanel: React.FC<{ kind: EntryKind }> = ({ kind }) => {
  const [filter, setFilter] = useState<Filter>(() => { const f = pendingFilter || 'abertas'; pendingFilter = null; return f; });
  const [search, setSearch] = useState('');
  const [month, setMonth] = useState('');
  const [editing, setEditing] = useState<FinanceEntry | null>(null);
  const [paying, setPaying] = useState<FinanceEntry | null>(null);
  const canEdit = canManageFinance();
  const canDelete = canDeleteGestao();
  const today = todayIso();

  const all = getEntries().filter(e => e.kind === kind);
  const q = search.trim().toLowerCase();
  const list = all.filter(e => {
    const s = entrySituation(e, today);
    if (!matchesFilter(e, s, filter, today)) return false;
    if (month && !e.dueDate.startsWith(month)) return false;
    if (q && ![e.description, e.counterpartyName, e.category, e.documentNumber].some(v => (v || '').toLowerCase().includes(q))) return false;
    return true;
  });

  if (!canEdit) return <NoAccess text="Contas a pagar e a receber: acesso do administrador e do perfil administrativo." />;

  const totalOpen = roundMoney(list.reduce((s, e) => s + openAmount(e), 0));
  const totalPaid = roundMoney(list.reduce((s, e) => s + paidAmount(e), 0));
  const overdue = all.filter(e => entrySituation(e, today) === 'vencido');
  const isPay = kind === 'pagar';
  const Icon = isPay ? ArrowUpCircle : ArrowDownCircle;

  const handleCancel = (e: FinanceEntry) => {
    const reason = window.prompt(`Cancelar "${e.description}"? Motivo:`, '');
    if (reason === null) return;
    cancelEntry(e.id, reason);
  };

  const handleDelete = (e: FinanceEntry) => {
    if (!window.confirm(`Excluir definitivamente "${e.description}"${e.payments?.length ? ` e os ${e.payments.length} pagamento(s) registrados` : ''}?\nPrefira "Cancelar" para manter o histórico.`)) return;
    deleteEntry(e.id);
  };

  return (
    <div className="space-y-3">
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
        <div className={`${cardCls} p-3`}>
          <p className="text-[11px] font-semibold text-slate-500">{isPay ? 'A pagar (filtro)' : 'A receber (filtro)'}</p>
          <p className={`text-lg font-black ${isPay ? 'text-red-700' : 'text-emerald-700'}`}>{formatMoney(totalOpen)}</p>
        </div>
        <div className={`${cardCls} p-3`}>
          <p className="text-[11px] font-semibold text-slate-500">{isPay ? 'Pago (filtro)' : 'Recebido (filtro)'}</p>
          <p className="text-lg font-black text-slate-900">{formatMoney(totalPaid)}</p>
        </div>
        <button type="button" onClick={() => setFilter('vencidas')} className={`${cardCls} p-3 text-left hover:border-red-300`}>
          <p className="text-[11px] font-semibold text-slate-500">Vencidas</p>
          <p className="text-lg font-black text-red-700">{overdue.length} · {formatMoney(overdue.reduce((s, e) => s + openAmount(e), 0))}</p>
        </button>
        <div className="flex items-stretch">
          <button type="button" className={`${btnPrimary} w-full`} onClick={() => setEditing(emptyEntry(kind))}>
            <Plus className="w-4 h-4" /> Nova conta {isPay ? 'a pagar' : 'a receber'}
          </button>
        </div>
      </div>

      <div className={`${cardCls} p-3 flex flex-wrap gap-2 items-center`}>
        <div className="flex flex-wrap gap-1">
          {(Object.keys(FILTER_LABEL) as Filter[]).map(f => (
            <button key={f} type="button" onClick={() => setFilter(f)}
              className={`px-2.5 py-1 rounded-full text-[11px] font-bold border ${filter === f ? 'bg-blue-600 text-white border-blue-600' : 'bg-white text-slate-600 border-slate-300'}`}>
              {FILTER_LABEL[f]}
            </button>
          ))}
        </div>
        <div className="flex flex-wrap sm:flex-nowrap gap-2 flex-1 min-w-[240px]">
          <div className="relative flex-1 min-w-[180px]">
            <Search className="w-3.5 h-3.5 text-slate-400 absolute left-2.5 top-1/2 -translate-y-1/2" />
            <input className={`${inputCls} pl-8`} placeholder={`Buscar descrição, ${isPay ? 'fornecedor' : 'cliente'}, documento...`} value={search} onChange={e => setSearch(e.target.value)} />
          </div>
          <div className="w-full sm:w-44 shrink-0">
            <input type="month" className={inputCls} value={month} onChange={e => setMonth(e.target.value)} aria-label="Mês de vencimento" title="Filtrar pelo mês de vencimento" />
          </div>
        </div>
      </div>

      {list.length === 0 ? (
        <EmptyState icon={Icon} title={`Nenhuma conta ${isPay ? 'a pagar' : 'a receber'} neste filtro`}
          text={isPay ? 'Cadastre despesas como combustível, aluguel, salários e fornecedores. Use "recorrente" para as mensais.' : 'Cadastre os valores a receber dos clientes ou aprove uma precificação para gerar as parcelas.'} />
      ) : (
        <div className={`${cardCls} overflow-x-auto`}>
          <table className="w-full text-xs min-w-[860px]">
            <thead className="bg-slate-50 text-slate-500 text-[11px] uppercase">
              <tr>
                <th className="p-2 text-left">Vencimento</th>
                <th className="p-2 text-left">Descrição</th>
                <th className="p-2 text-left">{isPay ? 'Fornecedor' : 'Cliente'}</th>
                <th className="p-2 text-right">Valor</th>
                <th className="p-2 text-right">Em aberto</th>
                <th className="p-2 text-center">Situação</th>
                <th className="p-2 text-right">Ações</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {list.map(e => {
                const s = entrySituation(e, today);
                return (
                  <tr key={e.id} className="hover:bg-slate-50">
                    <td className="p-2 font-mono whitespace-nowrap">{formatDateBr(e.dueDate)}</td>
                    <td className="p-2 min-w-[220px]">
                      <p className="font-bold text-slate-800">{e.description}</p>
                      <p className="text-[10px] text-slate-500">{e.category}{e.documentNumber ? ` · Doc. ${e.documentNumber}` : ''}</p>
                    </td>
                    <td className="p-2 text-slate-700">{e.counterpartyName}</td>
                    <td className="p-2 text-right font-semibold">{formatMoney(e.amount)}</td>
                    <td className="p-2 text-right font-black">{formatMoney(openAmount(e))}</td>
                    <td className="p-2 text-center"><SituationBadge situation={s} /></td>
                    <td className="p-2">
                      <div className="flex justify-end gap-1">
                        {(s === 'aberto' || s === 'parcial' || s === 'vencido') && (
                          <button type="button" className={btnSecondary} title={isPay ? 'Registrar pagamento' : 'Registrar recebimento'} onClick={() => setPaying(e)}>
                            <CheckCircle2 className="w-3.5 h-3.5 text-emerald-600" /> {isPay ? 'Pagar' : 'Receber'}
                          </button>
                        )}
                        {s === 'pago' && (
                          <button type="button" className={btnSecondary} title="Ver pagamentos" onClick={() => setPaying(e)}>Pagamentos</button>
                        )}
                        <button type="button" className={btnSecondary} title="Editar" onClick={() => setEditing(e)}><Pencil className="w-3.5 h-3.5" /></button>
                        {s === 'cancelado'
                          ? <button type="button" className={btnSecondary} title="Reabrir" onClick={() => reopenEntry(e.id)}><RotateCcw className="w-3.5 h-3.5" /></button>
                          : <button type="button" className={btnSecondary} title="Cancelar" onClick={() => handleCancel(e)}><Ban className="w-3.5 h-3.5" /></button>}
                        {canDelete && <button type="button" className={btnDanger} title="Excluir" onClick={() => handleDelete(e)}><Trash2 className="w-3.5 h-3.5" /></button>}
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {editing && <EntryEditor entry={editing} onClose={() => setEditing(null)} />}
      {paying && <PaymentDialog entryId={paying.id} onClose={() => setPaying(null)} />}
    </div>
  );
};

function emptyEntry(kind: EntryKind): FinanceEntry {
  const settings = getSettings();
  const cats = kind === 'pagar' ? settings.payableCategories : settings.receivableCategories;
  return {
    id: '', companyId: '', createdAt: '', updatedAt: '',
    kind, description: '', category: cats[0] || '', counterpartyName: '', issueDate: todayIso(), dueDate: todayIso(),
    amount: 0, payments: [], status: 'aberto', accountId: settings.accounts[0]?.id
  };
}

const EntryEditor: React.FC<{ entry: FinanceEntry; onClose: () => void }> = ({ entry, onClose }) => {
  const [form, setForm] = useState<FinanceEntry>(entry);
  const [mode, setMode] = useState<'unica' | 'parcelado' | 'recorrente'>('unica');
  const [count, setCount] = useState(2);
  const settings = getSettings();
  const clients = getClientsForGestao();
  const orders = getServiceOrdersForGestao();
  const isNew = !entry.id;
  const isPay = form.kind === 'pagar';
  const categories = Array.from(new Set([...(isPay ? settings.payableCategories : settings.receivableCategories), form.category].filter(Boolean)));
  const set = <K extends keyof FinanceEntry>(k: K, v: FinanceEntry[K]) => setForm(f => ({ ...f, [k]: v }));

  const save = () => {
    try {
      if (!form.description.trim()) throw new Error('Informe a descrição.');
      if (!(form.amount > 0)) throw new Error('Informe o valor.');
      if (!/^\d{4}-\d{2}-\d{2}$/.test(form.dueDate)) throw new Error('Informe o vencimento.');
      if (!isNew && paidAmount(form) > form.amount + 0.001) throw new Error('O valor não pode ser menor que o total já pago.');
      const data = { ...form, description: form.description.trim(), counterpartyName: form.counterpartyName.trim() };
      if (isNew) {
        const { id: _i, companyId: _c, createdAt: _cr, updatedAt: _u, groupId: _g, installment: _n, installments: _t, ...base } = data;
        createEntries(base, { mode, count });
      } else {
        saveEntry(data);
      }
      onClose();
    } catch (err) { alertError(err); }
  };

  const pickClient = (id: string) => {
    const c = clients.find(x => x.id === id);
    setForm(f => ({ ...f, clientId: id || undefined, counterpartyName: c ? (c.nomeFantasia || c.razaoSocial) : f.counterpartyName }));
  };

  return (
    <Modal title={`${isNew ? 'Nova conta' : 'Editar conta'} ${KIND_LABEL[form.kind].toLowerCase()}`} onClose={onClose}
      footer={<><button type="button" className={btnSecondary} onClick={onClose}>Cancelar</button><button type="button" className={btnPrimary} onClick={save}>Salvar</button></>}>
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <Field label="Descrição" className="sm:col-span-2">
          <input className={inputCls} value={form.description} onChange={e => set('description', e.target.value)} placeholder={isPay ? 'Ex.: Combustível da semana, Aluguel de outubro' : 'Ex.: Ensaios de luvas – OS 2610-0004'} />
        </Field>
        {!isPay && (
          <Field label="Cliente cadastrado">
            <select className={inputCls} value={form.clientId || ''} onChange={e => pickClient(e.target.value)}>
              <option value="">— Outro / digitar abaixo —</option>
              {clients.map(c => <option key={c.id} value={c.id}>{c.nomeFantasia || c.razaoSocial}</option>)}
            </select>
          </Field>
        )}
        <Field label={isPay ? 'Fornecedor / favorecido' : 'Nome do pagador'} className={isPay ? 'sm:col-span-2' : ''}>
          <input className={inputCls} value={form.counterpartyName} onChange={e => set('counterpartyName', e.target.value)} />
        </Field>
        <Field label="Categoria">
          <select className={inputCls} value={form.category} onChange={e => set('category', e.target.value)}>
            {categories.map(c => <option key={c} value={c}>{c}</option>)}
          </select>
        </Field>
        <Field label="Nº do documento (NF, boleto...)">
          <input className={inputCls} value={form.documentNumber || ''} onChange={e => set('documentNumber', e.target.value)} />
        </Field>
        <Field label={isNew && mode === 'parcelado' ? 'Valor total (R$)' : 'Valor (R$)'}>
          <NumberInput value={form.amount} onChange={v => set('amount', v)} />
        </Field>
        <Field label={isNew && mode !== 'unica' ? 'Vencimento da 1ª' : 'Vencimento'}>
          <input type="date" className={inputCls} value={form.dueDate} onChange={e => set('dueDate', e.target.value)} />
        </Field>
        <Field label="Data de emissão">
          <input type="date" className={inputCls} value={form.issueDate} onChange={e => set('issueDate', e.target.value)} />
        </Field>
        <Field label="Conta / caixa">
          <select className={inputCls} value={form.accountId || ''} onChange={e => set('accountId', e.target.value || undefined)}>
            {settings.accounts.map(a => <option key={a.id} value={a.id}>{a.name}</option>)}
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
        {isNew && (
          <div className="sm:col-span-2 grid grid-cols-1 sm:grid-cols-2 gap-3 p-3 rounded-xl bg-slate-50 border border-slate-200">
            <Field label="Lançamento">
              <select className={inputCls} value={mode} onChange={e => setMode(e.target.value as typeof mode)}>
                <option value="unica">Conta única</option>
                <option value="parcelado">Parcelado (divide o valor total)</option>
                <option value="recorrente">Recorrente mensal (repete o valor)</option>
              </select>
            </Field>
            {mode !== 'unica' && (
              <Field label={mode === 'parcelado' ? 'Número de parcelas' : 'Quantidade de meses'}
                hint={mode === 'parcelado' && form.amount > 0 && count > 0 ? `${count}× de aprox. ${formatMoney(form.amount / count)}` : undefined}>
                <NumberInput value={count} step={1} min={2} onChange={v => setCount(Math.max(2, Math.min(120, Math.round(v))))} />
              </Field>
            )}
          </div>
        )}
        <Field label="Observações" className="sm:col-span-2">
          <textarea className={inputCls} rows={2} value={form.notes || ''} onChange={e => set('notes', e.target.value)} />
        </Field>
      </div>
    </Modal>
  );
};

const PaymentDialog: React.FC<{ entryId: string; onClose: () => void }> = ({ entryId, onClose }) => {
  const entry = getEntries().find(e => e.id === entryId);
  const settings = getSettings();
  const [date, setDate] = useState(todayIso());
  const [amount, setAmount] = useState(entry ? openAmount(entry) : 0);
  const [method, setMethod] = useState<PaymentMethod>('pix');
  const [accountId, setAccountId] = useState(entry?.accountId || settings.accounts[0]?.id || '');
  if (!entry) return null;
  const open = openAmount(entry);
  const isPay = entry.kind === 'pagar';

  const save = () => {
    try {
      registerPayment(entry.id, { date, amount, method, accountId: accountId || undefined });
      onClose();
    } catch (err) { alertError(err); }
  };

  return (
    <Modal title={`${isPay ? 'Pagamento' : 'Recebimento'} – ${entry.description}`} onClose={onClose}
      footer={open > 0
        ? <><button type="button" className={btnSecondary} onClick={onClose}>Fechar</button><button type="button" className={btnPrimary} onClick={save}>Registrar {isPay ? 'pagamento' : 'recebimento'}</button></>
        : <button type="button" className={btnSecondary} onClick={onClose}>Fechar</button>}>
      <div className="space-y-3">
        <div className="grid grid-cols-3 gap-2 text-center">
          <div className="p-2 rounded-xl bg-slate-50"><p className="text-[10px] text-slate-500">Valor</p><p className="text-sm font-black">{formatMoney(entry.amount)}</p></div>
          <div className="p-2 rounded-xl bg-slate-50"><p className="text-[10px] text-slate-500">{isPay ? 'Pago' : 'Recebido'}</p><p className="text-sm font-black">{formatMoney(paidAmount(entry))}</p></div>
          <div className="p-2 rounded-xl bg-slate-50"><p className="text-[10px] text-slate-500">Em aberto</p><p className="text-sm font-black text-blue-700">{formatMoney(open)}</p></div>
        </div>

        {(entry.payments || []).length > 0 && (
          <div className="divide-y divide-slate-100 border border-slate-200 rounded-xl">
            {entry.payments.map(p => (
              <div key={p.id} className="flex items-center justify-between gap-2 px-3 py-2 text-xs">
                <span className="font-mono">{formatDateBr(p.date)}</span>
                <span>{METHOD_LABEL[p.method]}{p.accountId ? ` · ${settings.accounts.find(a => a.id === p.accountId)?.name || ''}` : ''}</span>
                <span className="font-bold">{formatMoney(p.amount)}</span>
                <button type="button" className="text-red-600 hover:underline text-[11px]"
                  onClick={() => { if (window.confirm('Estornar (apagar) este pagamento?')) removePayment(entry.id, p.id); }}>Estornar</button>
              </div>
            ))}
          </div>
        )}

        {open > 0 && (
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <Field label="Data"><input type="date" className={inputCls} value={date} onChange={e => setDate(e.target.value)} /></Field>
            <Field label="Valor (R$)" hint="Valor menor que o aberto registra pagamento parcial">
              <NumberInput value={amount} onChange={setAmount} />
            </Field>
            <Field label="Forma">
              <select className={inputCls} value={method} onChange={e => setMethod(e.target.value as PaymentMethod)}>
                {(Object.keys(METHOD_LABEL) as PaymentMethod[]).map(m => <option key={m} value={m}>{METHOD_LABEL[m]}</option>)}
              </select>
            </Field>
            <Field label="Conta / caixa">
              <select className={inputCls} value={accountId} onChange={e => setAccountId(e.target.value)}>
                {settings.accounts.map(a => <option key={a.id} value={a.id}>{a.name}</option>)}
              </select>
            </Field>
          </div>
        )}
      </div>
    </Modal>
  );
};
