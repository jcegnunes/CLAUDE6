/**
 * Sincronização do módulo Gestão ADM com o Supabase (tabelas gestao_*).
 * Independente da sincronização dos ensaios: usa só o cliente e a sessão de
 * login da plataforma.
 */
import { SupabaseService } from '../../services/supabaseService';
import { getDeviceId } from '../../services/syncEngine';
import {
  addGestaoConflict, addGestaoNumberRange, applyRemoteGestao, canManageFinance, canPrice, clearServerVersion,
  currentCompanyId, enqueueGestao, getGestaoCursors, getGestaoQueue, getGestaoRecord, getGestaoRefillRequests,
  markGestaoSent, notifyGestaoChanged, recordLabel, removeGestaoConflict, setGestaoChangeHandler, setGestaoCursor,
  updateGestaoQueue
} from './repository';
import { paidAmount } from './rules';
import type { GestaoTable } from './types';

const PAGE = 500;

export interface GestaoSyncStatus {
  running: boolean;
  lastSyncAt?: string;
  lastError?: string;
}

let status: GestaoSyncStatus = { running: false };
const statusListeners = new Set<(s: GestaoSyncStatus) => void>();
let running: Promise<void> | null = null;

export function getGestaoSyncStatus(): GestaoSyncStatus {
  return status;
}

export function subscribeGestaoSync(listener: (s: GestaoSyncStatus) => void): () => void {
  statusListeners.add(listener);
  return () => { statusListeners.delete(listener); };
}

function setStatus(patch: Partial<GestaoSyncStatus>) {
  status = { ...status, ...patch };
  statusListeners.forEach(l => l(status));
}

const isConflict = (msg?: string) => /JV409|Conflito de edição/i.test(msg || '');
const dateOrNull = (v?: string) => (v && /^\d{4}-\d{2}-\d{2}/.test(v) ? v.slice(0, 10) : null);

/** Tabelas que o perfil pode ler (o RT não vê as contas). */
function readableTables(): GestaoTable[] {
  if (canManageFinance()) return ['gestao_settings', 'gestao_services', 'gestao_entries', 'gestao_pricings', 'gestao_budgets'];
  if (canPrice()) return ['gestao_settings', 'gestao_services', 'gestao_pricings', 'gestao_budgets'];
  return [];
}

/** Colunas pesquisáveis de cada tabela; o registro completo vai em payload. */
export function toGestaoRow(table: GestaoTable, r: any): Record<string, unknown> {
  const { serverUpdatedAt, ...payload } = r;
  const base = {
    id: r.id,
    company_id: r.companyId,
    payload,
    device_id: getDeviceId(),
    deleted_at: null,
    base_updated_at: serverUpdatedAt || null
  };
  switch (table) {
    case 'gestao_entries':
      return {
        ...base, kind: r.kind, description: r.description, category: r.category, counterparty_name: r.counterpartyName,
        client_id: r.clientId || null, issue_date: dateOrNull(r.issueDate), due_date: dateOrNull(r.dueDate),
        amount: r.amount, paid_amount: paidAmount(r), status: r.status, pricing_id: r.pricingId || null,
        budget_id: r.budgetId || null, service_order_id: r.serviceOrderId || null
      };
    case 'gestao_pricings':
      return {
        ...base, number: r.number, title: r.title, client_id: r.clientId || null, client_name: r.clientName || null,
        pricing_date: dateOrNull(r.date), total_cost: r.result?.totalCost ?? null, final_price: r.result?.finalPrice ?? null,
        status: r.status
      };
    case 'gestao_services':
      return { ...base, code: r.code || null, name: r.name, category: r.category || null, mode: r.mode, unit_price: r.unitPrice ?? null, active: r.active !== false };
    case 'gestao_budgets':
      return {
        ...base, number: r.number, title: r.title || null, client_id: r.clientId || null, client_name: r.clientName || null,
        budget_date: dateOrNull(r.date), total: r.total ?? null, status: r.status
      };
    case 'gestao_settings':
      return base;
  }
}

async function canSync(): Promise<boolean> {
  if (!SupabaseService.getConfig().enabled) return false;
  if (typeof navigator !== 'undefined' && navigator.onLine === false) return false;
  if (!currentCompanyId() || !readableTables().length) return false;
  return !!(await SupabaseService.getSession());
}

async function push(): Promise<string | undefined> {
  const client = SupabaseService.getClient();
  const company = currentCompanyId();
  let lastError: string | undefined;
  for (const item of getGestaoQueue()) {
    const record: any = getGestaoRecord(item.table, item.id);
    if (record && record.companyId !== company) continue; // de outra empresa (troca de login)
    let error: { message: string } | null = null;
    let updatedAt: string | undefined;
    if (item.action === 'delete') {
      ({ error } = await client.from(item.table)
        .update({ deleted_at: new Date().toISOString(), device_id: getDeviceId(), base_updated_at: null })
        .eq('id', item.id));
    } else if (record) {
      const res = await client.from(item.table).upsert(toGestaoRow(item.table, record), { onConflict: 'id' }).select('updated_at').maybeSingle();
      error = res.error;
      updatedAt = (res.data as any)?.updated_at;
    }
    if (error) {
      if (isConflict(error.message)) {
        addGestaoConflict({ table: item.table, id: item.id, label: recordLabel(item.table, item.id), detectedAt: new Date().toISOString() });
        updateGestaoQueue(q => q.filter(x => !(x.table === item.table && x.id === item.id)));
        continue;
      }
      lastError = error.message;
      updateGestaoQueue(q => q.map(x => x.table === item.table && x.id === item.id ? { ...x, attempts: x.attempts + 1, lastError: error!.message } : x));
      continue;
    }
    updateGestaoQueue(q => q.filter(x => !(x.table === item.table && x.id === item.id && x.action === item.action)));
    if (updatedAt) markGestaoSent(item.table, item.id, updatedAt);
  }
  return lastError;
}

async function pull(): Promise<void> {
  const client = SupabaseService.getClient();
  const company = currentCompanyId();
  for (const table of readableTables()) {
    const scope = `${company}|${table}`;
    let cursor = getGestaoCursors()[scope] || '1970-01-01T00:00:00Z';
    for (;;) {
      const { data, error } = await client.from(table)
        .select('id, payload, deleted_at, updated_at')
        .eq('company_id', company)
        .gt('updated_at', cursor)
        .order('updated_at', { ascending: true })
        .limit(PAGE);
      if (error) throw new Error(error.message);
      const rows = (data || []) as any[];
      applyRemoteGestao(table, rows);
      if (rows.length) {
        cursor = rows[rows.length - 1].updated_at;
        setGestaoCursor(scope, cursor);
      }
      if (rows.length < PAGE) break;
    }
  }
}

async function refillNumbers(): Promise<void> {
  if (!canPrice()) return;
  const client = SupabaseService.getClient();
  for (const req of getGestaoRefillRequests()) {
    const { data, error } = await client.rpc('jvm_gestao_reserve_numbers', {
      p_kind: req.kind, p_period: req.period, p_quantity: req.quantity, p_local_max: req.localMax
    });
    if (error) return; // perfil sem permissão ou módulo ainda não instalado no banco
    const range: any = typeof data === 'string' ? JSON.parse(data) : data;
    if (range && Number.isFinite(range.start) && Number.isFinite(range.end)) addGestaoNumberRange(req.kind, range.start, range.end);
  }
}

/** Envia o que está pendente, baixa as novidades e reserva numeração. */
export function syncGestao(): Promise<void> {
  if (running) return running;
  running = (async () => {
    if (!(await canSync())) return;
    setStatus({ running: true });
    try {
      const pushError = await push();
      await pull();
      await refillNumbers();
      setStatus({ lastSyncAt: new Date().toISOString(), lastError: pushError });
    } catch (err: any) {
      const msg = String(err?.message || err);
      setStatus({ lastError: /relation .*gestao_|does not exist|schema cache/i.test(msg)
        ? 'O banco ainda não tem o módulo Gestão ADM: execute supabase/modules/gestao.sql no Supabase.'
        : msg });
    } finally {
      setStatus({ running: false });
      notifyGestaoChanged(false);
    }
  })().finally(() => { running = null; });
  return running;
}

let sendTimer: ReturnType<typeof setTimeout> | null = null;
let intervalTimer: ReturnType<typeof setInterval> | null = null;
let users = 0;
const onOnline = () => { void syncGestao(); };

/** Liga a sincronização automática enquanto uma tela do módulo está aberta. */
export function startGestaoSync(): () => void {
  users++;
  if (users === 1) {
    setGestaoChangeHandler(() => {
      if (sendTimer) clearTimeout(sendTimer);
      sendTimer = setTimeout(() => { void syncGestao(); }, 1500);
    });
    intervalTimer = setInterval(() => { void syncGestao(); }, 90_000);
    window.addEventListener('online', onOnline);
  }
  void syncGestao();
  return () => {
    users = Math.max(0, users - 1);
    if (users === 0) {
      setGestaoChangeHandler(null);
      if (intervalTimer) clearInterval(intervalTimer);
      window.removeEventListener('online', onOnline);
    }
  };
}

/** Conflito: manter a versão deste aparelho (sobrescreve a do servidor). */
export async function resolveConflictKeepMine(table: GestaoTable, id: string): Promise<void> {
  clearServerVersion(table, id);
  enqueueGestao(table, id, 'upsert');
  removeGestaoConflict(table, id);
  await syncGestao();
}

/** Conflito: usar a versão do servidor (descarta a edição deste aparelho). */
export async function resolveConflictUseServer(table: GestaoTable, id: string): Promise<void> {
  const { data, error } = await SupabaseService.getClient().from(table)
    .select('id, payload, deleted_at, updated_at').eq('id', id).maybeSingle();
  if (error) throw new Error(error.message);
  removeGestaoConflict(table, id);
  if (data) applyRemoteGestao(table, [data as any]);
}
