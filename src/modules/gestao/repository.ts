/**
 * Dados do módulo Gestão ADM no aparelho (IndexedDB), com fila própria de envio
 * ao Supabase. Funciona offline; a sincronização fica em sync.ts.
 *
 * Do restante da plataforma o módulo só LÊ: empresa ativa, usuário logado,
 * clientes e ordens de serviço. Nada dos ensaios é alterado aqui.
 */
import { isManaged, storeGet, storeSet } from '../../services/localStore';
import { DielectricStorageService, getDeviceId } from '../../services/syncEngine';
import {
  addRange, currentPeriod, deviceTag, fallbackNumber, formatNumber, maxSequence,
  remaining, takeFromBlocks, type NumberBlockState
} from '../../services/numberBlocks';
import { GESTAO_KEYS, GESTAO_MANAGED_KEYS } from './storageKeys';
import {
  BUDGET_PREFIX, PRICING_PREFIX, addMonths, budgetTotal, calculateBudgetItem, calculatePricing, costsFromSettings, normalizePricingInput, defaultSettings, newId, openAmount, roundMoney, settingsId,
  splitInstallments, todayIso
} from './rules';
import type {
  Budget, CatalogService, EntryPayment, FinanceEntry, GestaoConflict, GestaoQueueItem, GestaoSettings, GestaoTable, ServicePricing
} from './types';

export { GESTAO_KEYS, GESTAO_MANAGED_KEYS };

const TABLE_KEY: Record<GestaoTable, string> = {
  gestao_entries: GESTAO_KEYS.ENTRIES,
  gestao_pricings: GESTAO_KEYS.PRICINGS,
  gestao_settings: GESTAO_KEYS.SETTINGS,
  gestao_services: GESTAO_KEYS.SERVICES,
  gestao_budgets: GESTAO_KEYS.BUDGETS
};

type AnyRecord = FinanceEntry | ServicePricing | GestaoSettings | CatalogService | Budget;

// ----------------------------------------------------------------- leitura base
function read<T>(key: string, fallback: T): T {
  if (isManaged(key)) {
    const v = storeGet<T>(key);
    return v === undefined || v === null ? fallback : v;
  }
  try {
    const raw = localStorage.getItem(key);
    return raw ? JSON.parse(raw) : fallback;
  } catch {
    return fallback;
  }
}

function write<T>(key: string, value: T): void {
  if (isManaged(key)) {
    storeSet(key, value);
  } else {
    try { localStorage.setItem(key, JSON.stringify(value)); } catch { /* sem espaço */ }
  }
}

// ---------------------------------------------------------------- notificações
const listeners = new Set<() => void>();
let changeTimer: ReturnType<typeof setTimeout> | null = null;

export function subscribeGestao(listener: () => void): () => void {
  listeners.add(listener);
  return () => { listeners.delete(listener); };
}

let onLocalChange: (() => void) | null = null;
/** sync.ts registra aqui o envio automático após cada gravação. */
export function setGestaoChangeHandler(handler: (() => void) | null): void {
  onLocalChange = handler;
}

export function notifyGestaoChanged(local = false): void {
  if (changeTimer) clearTimeout(changeTimer);
  changeTimer = setTimeout(() => listeners.forEach(l => { try { l(); } catch { /* tela fechada */ } }), 0);
  if (local && onLocalChange) onLocalChange();
}

// ------------------------------------------------------------- empresa/usuário
export function currentCompanyId(): string {
  return DielectricStorageService.getSessionCompanyId() || DielectricStorageService.getActiveCompany()?.id || '';
}

export function currentUser() {
  return DielectricStorageService.getCurrentUser();
}

/** Contas a pagar/receber e fluxo de caixa: administrador e administrativo. */
export function canManageFinance(): boolean {
  const u = currentUser();
  return !!u && (!!u.isMasterAdmin || ['admin', 'administrativo'].includes(u.role));
}

/** Precificação: também o Responsável Técnico (monta o preço do serviço). */
export function canPrice(): boolean {
  const u = currentUser();
  return !!u && (!!u.isMasterAdmin || ['admin', 'administrativo', 'responsavel_tecnico'].includes(u.role));
}

export function canDeleteGestao(): boolean {
  const u = currentUser();
  return !!u && (u.role === 'admin' || !!u.isMasterAdmin);
}

export function getClientsForGestao() {
  return DielectricStorageService.getClients(currentCompanyId()).filter(c => !c.deletedAt);
}

export function getServiceOrdersForGestao() {
  return DielectricStorageService.getServiceOrders(currentCompanyId()).filter(o => !o.deletedAt);
}

// ------------------------------------------------------------- listas genéricas
function allOf<T extends AnyRecord>(table: GestaoTable): T[] {
  return read<T[]>(TABLE_KEY[table], []);
}

function listOf<T extends AnyRecord>(table: GestaoTable): T[] {
  const company = currentCompanyId();
  return allOf<T>(table).filter(r => !company || r.companyId === company);
}

function putRecord<T extends AnyRecord>(table: GestaoTable, record: T, enqueue = true): T {
  const now = new Date().toISOString();
  const saved = { ...record, companyId: record.companyId || currentCompanyId(), updatedAt: now, createdAt: record.createdAt || now } as T;
  const list = allOf<T>(table);
  const idx = list.findIndex(r => r.id === saved.id);
  if (idx >= 0) list[idx] = saved; else list.push(saved);
  write(TABLE_KEY[table], list);
  if (enqueue) enqueueGestao(table, saved.id, 'upsert');
  notifyGestaoChanged(enqueue);
  return saved;
}

function removeRecord(table: GestaoTable, id: string, enqueue = true): void {
  write(TABLE_KEY[table], allOf(table).filter(r => r.id !== id));
  if (enqueue) enqueueGestao(table, id, 'delete');
  notifyGestaoChanged(enqueue);
}

export function getGestaoRecord(table: GestaoTable, id: string): AnyRecord | undefined {
  return allOf(table).find(r => r.id === id);
}

// ---------------------------------------------------------------- configuração
/** Configuração da empresa (valores de referência enquanto não for salva). */
export function getSettings(): GestaoSettings {
  const company = currentCompanyId();
  const saved = allOf<GestaoSettings>('gestao_settings').find(s => s.id === settingsId(company));
  const defaults = defaultSettings(company);
  return saved ? { ...defaults, ...saved } : defaults;
}

export function hasSavedSettings(): boolean {
  return allOf<GestaoSettings>('gestao_settings').some(s => s.id === settingsId(currentCompanyId()));
}

export const saveSettings = (s: GestaoSettings) => putRecord('gestao_settings', { ...s, id: settingsId(currentCompanyId()) });

// ---------------------------------------------------------------------- contas
export const getEntries = () => listOf<FinanceEntry>('gestao_entries')
  .sort((a, b) => a.dueDate.localeCompare(b.dueDate) || a.description.localeCompare(b.description));
export const getEntry = (id: string) => getEntries().find(e => e.id === id);
export const saveEntry = (e: FinanceEntry) => putRecord('gestao_entries', { ...e, amount: roundMoney(e.amount) });
export const deleteEntry = (id: string) => removeRecord('gestao_entries', id);

/**
 * Cria a conta (ou várias): "parcelado" divide o valor entre as parcelas;
 * "recorrente" repete o mesmo valor todo mês (aluguel, contador...).
 */
export function createEntries(
  base: Omit<FinanceEntry, 'id' | 'companyId' | 'createdAt' | 'updatedAt' | 'groupId' | 'installment' | 'installments'>,
  opts: { mode: 'unica' | 'parcelado' | 'recorrente'; count: number }
): FinanceEntry[] {
  const count = opts.mode === 'unica' ? 1 : Math.min(120, Math.max(1, Math.floor(opts.count) || 1));
  const groupId = count > 1 ? newId('grp') : undefined;
  const parts = opts.mode === 'recorrente'
    ? Array.from({ length: count }, (_, i) => ({ amount: roundMoney(base.amount), dueDate: addMonths(base.dueDate, i), installment: i + 1 }))
    : splitInstallments(base.amount, count, base.dueDate);
  return parts.map(p => saveEntry({
    ...base,
    id: newId(base.kind === 'pagar' ? 'pag' : 'rec'),
    companyId: '', createdAt: '', updatedAt: '',
    amount: p.amount,
    dueDate: p.dueDate,
    payments: [],
    groupId,
    installment: count > 1 ? p.installment : undefined,
    installments: count > 1 ? count : undefined,
    description: count > 1 && opts.mode === 'parcelado' ? `${base.description} (${p.installment}/${count})` : base.description
  }));
}

/** Registra um pagamento/recebimento (total ou parcial). */
export function registerPayment(entryId: string, payment: Omit<EntryPayment, 'id'>): FinanceEntry {
  const entry = getEntry(entryId);
  if (!entry) throw new Error('Conta não encontrada.');
  if (entry.status === 'cancelado') throw new Error('Conta cancelada: reabra antes de registrar o pagamento.');
  const amount = roundMoney(payment.amount);
  if (!(amount > 0)) throw new Error('Informe um valor maior que zero.');
  if (amount > openAmount(entry) + 0.001) throw new Error('O valor é maior que o saldo em aberto da conta.');
  if (!/^\d{4}-\d{2}-\d{2}$/.test(payment.date)) throw new Error('Informe a data do pagamento.');
  return saveEntry({ ...entry, payments: [...(entry.payments || []), { ...payment, amount, id: newId('pgt') }] });
}

export function removePayment(entryId: string, paymentId: string): FinanceEntry | null {
  const entry = getEntry(entryId);
  if (!entry) return null;
  return saveEntry({ ...entry, payments: (entry.payments || []).filter(p => p.id !== paymentId) });
}

export function cancelEntry(id: string, reason: string): FinanceEntry | null {
  const entry = getEntry(id);
  if (!entry) return null;
  return saveEntry({ ...entry, status: 'cancelado', cancelReason: reason.trim() || 'Cancelada' });
}

export function reopenEntry(id: string): FinanceEntry | null {
  const entry = getEntry(id);
  if (!entry) return null;
  return saveEntry({ ...entry, status: 'aberto', cancelReason: undefined });
}

// ------------------------------------------------------------------ precificação
export const getPricings = () => listOf<ServicePricing>('gestao_pricings')
  .sort((a, b) => (b.date || '').localeCompare(a.date || '') || (b.number || '').localeCompare(a.number || ''));
export const getPricing = (id: string) => getPricings().find(p => p.id === id);
export const deletePricing = (id: string) => removeRecord('gestao_pricings', id);

/** Salva recalculando com os custos da configuração atual (ou os já guardados). */
export function savePricing(p: ServicePricing, opts: { refreshCosts?: boolean } = {}): ServicePricing {
  const costs = costsFromSettings(opts.refreshCosts || !p.costsSnapshot ? getSettings() : p.costsSnapshot);
  const input = normalizePricingInput(p.input);
  return putRecord('gestao_pricings', {
    ...p,
    number: p.number || nextGestaoNumber('pricing'),
    costsSnapshot: costs,
    input,
    result: calculatePricing(input, costs)
  });
}

/** Aprova a precificação e gera as contas a receber (parcelas). */
export function approvePricing(id: string, opts: { installments: number; firstDueDate: string; category: string; accountId?: string }): FinanceEntry[] {
  const p = getPricing(id);
  if (!p) throw new Error('Precificação não encontrada.');
  if (!(p.result?.finalPrice > 0)) throw new Error('A precificação não tem preço válido.');
  if (p.receivableGroupId && getEntries().some(e => e.pricingId === p.id && e.status !== 'cancelado')) {
    throw new Error('Esta precificação já gerou contas a receber.');
  }
  const entries = createEntries({
    kind: 'receber',
    description: `${p.number} – ${p.title}`,
    category: opts.category,
    counterpartyName: p.clientName || 'Cliente',
    clientId: p.clientId,
    documentNumber: p.number,
    issueDate: todayIso(),
    dueDate: opts.firstDueDate,
    amount: p.result.finalPrice,
    payments: [],
    status: 'aberto',
    serviceOrderId: p.serviceOrderId,
    pricingId: p.id,
    accountId: opts.accountId,
    notes: p.paymentTerms
  }, { mode: opts.installments > 1 ? 'parcelado' : 'unica', count: opts.installments });
  putRecord('gestao_pricings', { ...p, status: 'aprovada', receivableGroupId: entries[0]?.groupId || entries[0]?.id });
  return entries;
}

// ------------------------------------------------------- cadastro de serviços
export const getServices = () => listOf<CatalogService>('gestao_services')
  .sort((a, b) => (a.category || '').localeCompare(b.category || '') || a.name.localeCompare(b.name));
export const getService = (id: string) => getServices().find(s => s.id === id);
export function saveService(s: CatalogService): CatalogService {
  if (!s.name.trim()) throw new Error('Informe o nome do serviço.');
  const code = (s.code || '').trim();
  if (code && getServices().some(x => x.id !== s.id && (x.code || '').trim().toLowerCase() === code.toLowerCase())) {
    throw new Error(`Já existe um serviço com o código ${code}.`);
  }
  return putRecord('gestao_services', { ...s, id: s.id || newId('srv'), name: s.name.trim(), code, unitPrice: roundMoney(s.unitPrice) });
}
export const deleteService = (id: string) => removeRecord('gestao_services', id);

/** Próximo código livre (SRV-001, SRV-002...). */
export function nextServiceCode(): string {
  const max = getServices().reduce((m, s) => Math.max(m, Number(((s.code || '').match(/(\d+)$/) || [])[1]) || 0), 0);
  return `SRV-${String(max + 1).padStart(3, '0')}`;
}

// ------------------------------------------------------------------ orçamentos
export const getBudgets = () => listOf<Budget>('gestao_budgets')
  .sort((a, b) => (b.date || '').localeCompare(a.date || '') || (b.number || '').localeCompare(a.number || ''));
export const getBudget = (id: string) => getBudgets().find(b => b.id === id);
export const deleteBudget = (id: string) => removeRecord('gestao_budgets', id);

/** Salva recalculando os itens (itens "planilha" com os valores de referência do orçamento). */
export function saveBudget(b: Budget, opts: { refreshCosts?: boolean } = {}): Budget {
  if (!b.items?.length) throw new Error('Inclua ao menos um serviço no orçamento.');
  if (b.items.some(i => !i.name.trim())) throw new Error('Informe o nome de todos os serviços do orçamento.');
  const costs = costsFromSettings(opts.refreshCosts || !b.costsSnapshot ? getSettings() : b.costsSnapshot);
  const items = b.items.map(i => calculateBudgetItem({ ...i, name: i.name.trim() }, costs));
  return putRecord('gestao_budgets', {
    ...b,
    id: b.id || newId('orc'),
    number: b.number || nextGestaoNumber('budget'),
    costsSnapshot: costs,
    items,
    total: budgetTotal(items)
  });
}

/** Aprova o orçamento e gera as contas a receber (parcelas). */
export function approveBudget(id: string, opts: { installments: number; firstDueDate: string; category: string; accountId?: string }): FinanceEntry[] {
  const b = getBudget(id);
  if (!b) throw new Error('Orçamento não encontrado.');
  if (!(b.total > 0)) throw new Error('O orçamento não tem valor.');
  if (getEntries().some(e => e.budgetId === b.id && e.status !== 'cancelado')) throw new Error('Este orçamento já gerou contas a receber.');
  const entries = createEntries({
    kind: 'receber',
    description: `${b.number} – ${b.title || b.items[0]?.name || 'Orçamento'}`,
    category: opts.category,
    counterpartyName: b.clientName || 'Cliente',
    clientId: b.clientId,
    documentNumber: b.number,
    issueDate: todayIso(),
    dueDate: opts.firstDueDate,
    amount: b.total,
    payments: [],
    status: 'aberto',
    serviceOrderId: b.serviceOrderId,
    budgetId: b.id,
    accountId: opts.accountId,
    notes: b.paymentTerms
  }, { mode: opts.installments > 1 ? 'parcelado' : 'unica', count: opts.installments });
  putRecord('gestao_budgets', { ...b, status: 'aprovada', receivableGroupId: entries[0]?.groupId || entries[0]?.id });
  return entries;
}

// ------------------------------------------------------------------- numeração
type NumberKind = 'pricing' | 'budget';
const PREFIX: Record<NumberKind, string> = { pricing: PRICING_PREFIX, budget: BUDGET_PREFIX };
export const GESTAO_BLOCK_SIZE: Record<NumberKind, number> = { pricing: 20, budget: 20 };
const REFILL_AT: Record<NumberKind, number> = { pricing: 6, budget: 6 };

function usedNumbers(kind: NumberKind): string[] {
  return kind === 'budget'
    ? allOf<Budget>('gestao_budgets').map(b => b.number)
    : allOf<ServicePricing>('gestao_pricings').map(p => p.number);
}

export function nextGestaoNumber(kind: NumberKind): string {
  const company = currentCompanyId();
  const period = currentPeriod();
  const key = `${company}|${kind}`;
  const state = read<NumberBlockState>(GESTAO_KEYS.NUMBER_BLOCKS, {});
  const localMax = maxSequence(usedNumbers(kind), period);
  let taken = takeFromBlocks(state, key);
  // descarta números da faixa que já foram usados (ex.: vindos de outro aparelho)
  while (taken.value !== null && taken.value <= localMax) taken = takeFromBlocks(taken.state, key);
  write(GESTAO_KEYS.NUMBER_BLOCKS, taken.state);
  if (taken.value !== null) return formatNumber(PREFIX[kind], period, taken.value);
  return fallbackNumber(PREFIX[kind], period, localMax + 1, deviceTag(getDeviceId()));
}

export function getGestaoRefillRequests(): Array<{ kind: NumberKind; quantity: number; localMax: number; period: string }> {
  const company = currentCompanyId();
  const state = read<NumberBlockState>(GESTAO_KEYS.NUMBER_BLOCKS, {});
  const period = currentPeriod();
  return (['pricing', 'budget'] as NumberKind[])
    .filter(kind => remaining(state, `${company}|${kind}`) < REFILL_AT[kind])
    .map(kind => ({ kind, quantity: GESTAO_BLOCK_SIZE[kind], localMax: maxSequence(usedNumbers(kind), period), period }));
}

export function addGestaoNumberRange(kind: NumberKind, start: number, end: number): void {
  const key = `${currentCompanyId()}|${kind}`;
  write(GESTAO_KEYS.NUMBER_BLOCKS, addRange(read<NumberBlockState>(GESTAO_KEYS.NUMBER_BLOCKS, {}), key, start, end));
}

// ------------------------------------------------------------- fila de envio
export function getGestaoQueue(): GestaoQueueItem[] {
  return read<GestaoQueueItem[]>(GESTAO_KEYS.QUEUE, []);
}

export function enqueueGestao(table: GestaoTable, id: string, action: GestaoQueueItem['action']): void {
  const queue = getGestaoQueue().filter(q => !(q.table === table && q.id === id));
  queue.push({ table, id, action, attempts: 0 });
  write(GESTAO_KEYS.QUEUE, queue);
}

export function updateGestaoQueue(mutator: (queue: GestaoQueueItem[]) => GestaoQueueItem[]): void {
  write(GESTAO_KEYS.QUEUE, mutator(getGestaoQueue()));
}

export function hasPending(table: GestaoTable, id: string): boolean {
  return getGestaoQueue().some(q => q.table === table && q.id === id);
}

export function pendingGestaoCount(): number {
  const company = currentCompanyId();
  return getGestaoQueue().filter(q => {
    const rec = getGestaoRecord(q.table, q.id);
    return !rec || rec.companyId === company;
  }).length;
}

// ------------------------------------------------- dados recebidos do servidor
/** Grava o que veio do servidor sem reenviar (e sem sobrescrever edições pendentes). */
export function applyRemoteGestao(table: GestaoTable, rows: Array<{ id: string; payload: any; deleted_at: string | null; updated_at: string }>): number {
  if (!rows.length) return 0;
  const list = allOf<AnyRecord>(table);
  const byId = new Map(list.map(r => [r.id, r]));
  let changed = 0;
  const conflicts = read<GestaoConflict[]>(GESTAO_KEYS.CONFLICTS, []);
  rows.forEach(row => {
    // edição pendente ou conflito aguardando decisão: mantém a versão do aparelho
    if (hasPending(table, row.id) || conflicts.some(c => c.table === table && c.id === row.id)) return;
    if (row.deleted_at) {
      if (byId.delete(row.id)) changed++;
      return;
    }
    if (!row.payload || typeof row.payload !== 'object') return;
    const current = byId.get(row.id);
    if (current?.serverUpdatedAt === row.updated_at) return;
    byId.set(row.id, { ...row.payload, id: row.id, serverUpdatedAt: row.updated_at });
    changed++;
  });
  if (changed) {
    write(TABLE_KEY[table], Array.from(byId.values()));
    notifyGestaoChanged(false);
  }
  return changed;
}

export function markGestaoSent(table: GestaoTable, id: string, serverUpdatedAt: string): void {
  const list = allOf<AnyRecord>(table);
  const idx = list.findIndex(r => r.id === id);
  if (idx < 0) return;
  list[idx] = { ...list[idx], serverUpdatedAt };
  write(TABLE_KEY[table], list);
}

export function clearServerVersion(table: GestaoTable, id: string): void {
  const list = allOf<AnyRecord>(table);
  const idx = list.findIndex(r => r.id === id);
  if (idx < 0) return;
  const { serverUpdatedAt: _drop, ...rest } = list[idx];
  list[idx] = rest as AnyRecord;
  write(TABLE_KEY[table], list);
}

export function getGestaoCursors(): Record<string, string> {
  return read<Record<string, string>>(GESTAO_KEYS.CURSORS, {});
}

export function setGestaoCursor(scope: string, value: string): void {
  write(GESTAO_KEYS.CURSORS, { ...getGestaoCursors(), [scope]: value });
}

// ------------------------------------------------------------------ conflitos
export function getGestaoConflicts(): GestaoConflict[] {
  const company = currentCompanyId();
  return read<GestaoConflict[]>(GESTAO_KEYS.CONFLICTS, []).filter(c => {
    const rec = getGestaoRecord(c.table, c.id);
    return !rec || rec.companyId === company;
  });
}

export function addGestaoConflict(conflict: GestaoConflict): void {
  const list = read<GestaoConflict[]>(GESTAO_KEYS.CONFLICTS, []).filter(c => !(c.table === conflict.table && c.id === conflict.id));
  write(GESTAO_KEYS.CONFLICTS, [...list, conflict]);
  notifyGestaoChanged(false);
}

export function removeGestaoConflict(table: GestaoTable, id: string): void {
  write(GESTAO_KEYS.CONFLICTS, read<GestaoConflict[]>(GESTAO_KEYS.CONFLICTS, []).filter(c => !(c.table === table && c.id === id)));
  notifyGestaoChanged(false);
}

export function recordLabel(table: GestaoTable, id: string): string {
  const r: any = getGestaoRecord(table, id);
  if (!r) return id;
  if (table === 'gestao_entries') return `${r.kind === 'pagar' ? 'Conta a pagar' : 'Conta a receber'} – ${r.description}`;
  if (table === 'gestao_pricings') return `Precificação ${r.number} – ${r.title}`;
  if (table === 'gestao_services') return `Serviço ${r.code ? `${r.code} – ` : ''}${r.name}`;
  if (table === 'gestao_budgets') return `Orçamento ${r.number}${r.clientName ? ` – ${r.clientName}` : ''}`;
  return 'Configuração de custos';
}
