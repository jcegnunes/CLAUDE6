/**
 * Sincronização do módulo SPDA com o Supabase (tabela spda_inspections).
 * Usada pela plataforma web e pelo APP JVM SPDA: mesmo login e mesmo banco.
 *
 * - Envio: fila local (repository.ts), uma inspeção por vez. As fotos e croquis
 *   (data:image/...) vão para o bucket "jvm-evidencias" na pasta da empresa e,
 *   NO SERVIDOR, a inspeção guarda só o endereço. No aparelho a foto continua
 *   dentro da inspeção (laudo e relatórios funcionam sem internet).
 * - Recebimento: incremental pelo updated_at do servidor; não sobrescreve
 *   edições pendentes deste aparelho.
 * - Conflito (mesma inspeção editada em dois aparelhos): fica aguardando a
 *   decisão do usuário (manter a deste aparelho ou usar a do servidor).
 */
import { EVIDENCE_BUCKET, SupabaseService } from '../../services/supabaseService';
import { getDeviceId } from '../../services/syncEngine';
import {
  addSpdaConflict, addSpdaPhotoUrls, applyRemoteSpda, clearSpdaServerVersion, currentCompanyId, enqueueSpda,
  getRawInspection, getSpdaCursor, getSpdaPhotoUrls, getSpdaQueue, markSpdaSent, notifySpdaChanged, removeSpdaConflict,
  setSpdaChangeHandler, setSpdaCursor, updateSpdaQueue
} from './repository';
import type { SpdaInspection } from './types';

const TABLE = 'spda_inspections';
const PAGE = 200;

export interface SpdaSyncStatus {
  running: boolean;
  lastSyncAt?: string;
  lastError?: string;
}

let status: SpdaSyncStatus = { running: false };
const statusListeners = new Set<(s: SpdaSyncStatus) => void>();
let running: Promise<void> | null = null;

export function getSpdaSyncStatus(): SpdaSyncStatus {
  return status;
}

export function subscribeSpdaSync(listener: (s: SpdaSyncStatus) => void): () => void {
  statusListeners.add(listener);
  return () => { statusListeners.delete(listener); };
}

function setStatus(patch: Partial<SpdaSyncStatus>) {
  status = { ...status, ...patch };
  statusListeners.forEach(l => { try { l(status); } catch { /* tela fechada */ } });
}

const isConflict = (msg?: string) => /JV409|Conflito de edição/i.test(msg || '');
const dateOrNull = (v?: string) => (v && /^\d{4}-\d{2}-\d{2}/.test(v) ? v.slice(0, 10) : null);
const folderOf = (v: string) => (v || 'sem-empresa').replace(/[^a-zA-Z0-9_-]/g, '_');

/** Assinatura curta de uma foto (FNV-1a + tamanho): identifica a foto já enviada. */
export function photoKey(dataUrl: string): string {
  let h = 0x811c9dc5;
  for (let i = 0; i < dataUrl.length; i++) {
    h ^= dataUrl.charCodeAt(i);
    h = Math.imul(h, 0x01000193) >>> 0;
  }
  return `${h.toString(16).padStart(8, '0')}${dataUrl.length.toString(16)}`;
}

/** Todas as imagens embutidas (data:image/...) de um registro, em qualquer campo. */
export function inlineImages(value: unknown, out = new Set<string>()): Set<string> {
  if (typeof value === 'string') {
    if (value.startsWith('data:image/')) out.add(value);
  } else if (Array.isArray(value)) {
    value.forEach(v => inlineImages(v, out));
  } else if (value && typeof value === 'object') {
    Object.values(value as Record<string, unknown>).forEach(v => inlineImages(v, out));
  }
  return out;
}

/** Cópia do registro trocando as imagens embutidas pelos endereços (as que não estão no mapa ficam como estão). */
export function replaceImages<T>(value: T, urls: Record<string, string>): T {
  if (typeof value === 'string') return (value.startsWith('data:image/') && urls[photoKey(value)] ? urls[photoKey(value)] : value) as unknown as T;
  if (Array.isArray(value)) return value.map(v => replaceImages(v, urls)) as unknown as T;
  if (value && typeof value === 'object') {
    const out: Record<string, unknown> = {};
    Object.entries(value as Record<string, unknown>).forEach(([k, v]) => { out[k] = replaceImages(v, urls); });
    return out as T;
  }
  return value;
}

/** Colunas de consulta; o registro completo vai em payload. */
export function toSpdaRow(r: SpdaInspection, payload: unknown): Record<string, unknown> {
  return {
    id: r.id,
    company_id: r.companyId,
    number: r.number || null,
    laudo_number: r.laudoNumber || null,
    client_id: r.clientId || null,
    client_name: r.clientName || null,
    service_order_id: r.serviceOrderId || null,
    building_name: r.buildingName || null,
    city: r.city || null,
    inspection_date: dateOrNull(r.inspectionDate),
    status: r.status || 'rascunho',
    result: r.techReport?.results?.final || null,
    technician_name: r.technicianName || null,
    payload,
    device_id: getDeviceId(),
    deleted_at: null,
    base_updated_at: r.serverUpdatedAt || null
  };
}

function dataUrlToBlob(dataUrl: string): { blob: Blob; mime: string; ext: string } | null {
  const m = dataUrl.match(/^data:([^;,]+)(;base64)?,(.*)$/s);
  if (!m) return null;
  try {
    const mime = m[1] || 'image/jpeg';
    let bytes: Uint8Array;
    if (m[2]) {
      const bin = atob(m[3]);
      bytes = new Uint8Array(bin.length);
      for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
    } else {
      bytes = new TextEncoder().encode(decodeURIComponent(m[3]));
    }
    const ext = (mime.split('/')[1] || 'jpg').replace('jpeg', 'jpg').replace('svg+xml', 'svg').replace(/[^a-z0-9]/gi, '');
    return { blob: new Blob([bytes], { type: mime }), mime, ext };
  } catch {
    return null;
  }
}

let storageUnavailable = false;

/** Envia ao bucket as fotos ainda não enviadas desta inspeção; devolve o mapa assinatura → endereço. */
async function uploadImages(r: SpdaInspection): Promise<Record<string, string>> {
  const known = getSpdaPhotoUrls();
  if (storageUnavailable) return known;
  const client = SupabaseService.getClient();
  const fresh: Record<string, string> = {};
  for (const dataUrl of inlineImages(r)) {
    const key = photoKey(dataUrl);
    if (known[key] || fresh[key]) continue;
    const parsed = dataUrlToBlob(dataUrl);
    if (!parsed) continue;
    const path = `${folderOf(r.companyId)}/spda/${folderOf(r.id)}/${key}.${parsed.ext}`;
    const { error } = await client.storage.from(EVIDENCE_BUCKET).upload(path, parsed.blob, { contentType: parsed.mime, upsert: true, cacheControl: '31536000' });
    if (error) {
      if (/bucket not found|row-level security|unauthorized/i.test(error.message || '')) { storageUnavailable = true; break; }
      continue; // tenta de novo na próxima sincronização (a foto segue embutida)
    }
    fresh[key] = client.storage.from(EVIDENCE_BUCKET).getPublicUrl(path).data.publicUrl;
  }
  addSpdaPhotoUrls(fresh);
  return { ...known, ...fresh };
}

async function canSync(): Promise<boolean> {
  if (!SupabaseService.getConfig().enabled) return false;
  if (typeof navigator !== 'undefined' && navigator.onLine === false) return false;
  if (!currentCompanyId()) return false;
  return !!(await SupabaseService.getSession());
}

async function push(): Promise<string | undefined> {
  const client = SupabaseService.getClient();
  const company = currentCompanyId();
  let lastError: string | undefined;
  for (const item of getSpdaQueue()) {
    if (item.companyId && item.companyId !== company) continue; // de outro login
    let error: { message: string } | null = null;
    let updatedAt: string | undefined;
    if (item.action === 'delete') {
      ({ error } = await client.from(TABLE)
        .update({ deleted_at: new Date().toISOString(), device_id: getDeviceId(), base_updated_at: null })
        .eq('id', item.id));
    } else {
      const record = getRawInspection(item.id);
      if (record) {
        const urls = await uploadImages(record);
        const { serverUpdatedAt: _s, ...payload } = record;
        const res = await client.from(TABLE).upsert(toSpdaRow(record, replaceImages(payload, urls)), { onConflict: 'id' }).select('updated_at').maybeSingle();
        error = res.error;
        updatedAt = (res.data as { updated_at?: string } | null)?.updated_at;
      }
    }
    if (error) {
      if (isConflict(error.message)) {
        const rec = getRawInspection(item.id);
        addSpdaConflict({ id: item.id, label: rec ? `${rec.number}${rec.clientName ? ` — ${rec.clientName}` : ''}` : item.id, companyId: company, detectedAt: new Date().toISOString() });
        updateSpdaQueue(q => q.filter(x => x.id !== item.id));
        continue;
      }
      lastError = error.message;
      updateSpdaQueue(q => q.map(x => (x.id === item.id ? { ...x, attempts: x.attempts + 1, lastError: error!.message } : x)));
      continue;
    }
    // só tira da fila se não houve nova edição durante o envio
    updateSpdaQueue(q => q.filter(x => !(x.id === item.id && x.queuedAt === item.queuedAt)));
    if (updatedAt) markSpdaSent(item.id, updatedAt);
  }
  return lastError;
}

async function pull(): Promise<void> {
  const client = SupabaseService.getClient();
  const company = currentCompanyId();
  let cursor = getSpdaCursor(company);
  for (;;) {
    const { data, error } = await client.from(TABLE)
      .select('id, payload, deleted_at, updated_at')
      .eq('company_id', company)
      .gt('updated_at', cursor)
      .order('updated_at', { ascending: true })
      .limit(PAGE);
    if (error) throw new Error(error.message);
    const rows = (data || []) as Array<{ id: string; payload: unknown; deleted_at: string | null; updated_at: string }>;
    applyRemoteSpda(rows);
    if (rows.length) {
      cursor = rows[rows.length - 1].updated_at;
      setSpdaCursor(company, cursor);
    }
    if (rows.length < PAGE) break;
  }
}

/** Envia o que está pendente e baixa as novidades. */
export function syncSpda(): Promise<void> {
  if (running) return running;
  running = (async () => {
    if (!(await canSync())) return;
    setStatus({ running: true });
    try {
      const pushError = await push();
      await pull();
      setStatus({ lastSyncAt: new Date().toISOString(), lastError: pushError });
    } catch (err: unknown) {
      const msg = String((err as Error)?.message || err);
      setStatus({
        lastError: /spda_inspections|does not exist|schema cache/i.test(msg)
          ? 'O banco ainda não tem o módulo SPDA: execute supabase/modules/spda.sql no Supabase.'
          : msg
      });
    } finally {
      setStatus({ running: false });
      notifySpdaChanged();
    }
  })().finally(() => { running = null; });
  return running;
}

let sendTimer: ReturnType<typeof setTimeout> | null = null;
let intervalTimer: ReturnType<typeof setInterval> | null = null;
let users = 0;
const onOnline = () => { void syncSpda(); };

/** Liga a sincronização automática enquanto uma tela do módulo está aberta. */
export function startSpdaSync(): () => void {
  users++;
  if (users === 1) {
    setSpdaChangeHandler(() => {
      if (sendTimer) clearTimeout(sendTimer);
      sendTimer = setTimeout(() => { void syncSpda(); }, 2000);
    });
    intervalTimer = setInterval(() => { void syncSpda(); }, 90_000);
    window.addEventListener('online', onOnline);
  }
  void syncSpda();
  return () => {
    users = Math.max(0, users - 1);
    if (users === 0) {
      setSpdaChangeHandler(null);
      if (intervalTimer) clearInterval(intervalTimer);
      window.removeEventListener('online', onOnline);
    }
  };
}

/** Conflito: manter a versão deste aparelho (sobrescreve a do servidor). */
export async function resolveSpdaConflictKeepMine(id: string): Promise<void> {
  clearSpdaServerVersion(id);
  enqueueSpda(id, 'upsert');
  removeSpdaConflict(id);
  await syncSpda();
}

/** Conflito: usar a versão do servidor (descarta a edição deste aparelho). */
export async function resolveSpdaConflictUseServer(id: string): Promise<void> {
  const { data, error } = await SupabaseService.getClient().from(TABLE)
    .select('id, payload, deleted_at, updated_at').eq('id', id).maybeSingle();
  if (error) throw new Error(error.message);
  removeSpdaConflict(id);
  if (data) applyRemoteSpda([data as { id: string; payload: unknown; deleted_at: string | null; updated_at: string }]);
}
