/**
 * Dados do módulo SPDA no aparelho (IndexedDB pelo localStore; se o
 * IndexedDB não estiver disponível, localStorage). Cada gravação entra na fila
 * de envio ao Supabase (tabela spda_inspections, ver sync.ts).
 *
 * Empresa, usuário, clientes, instrumentos e OS vêm do "host" (host.ts):
 * na plataforma, o DielectricStorageService (só leitura; "Gerar OS" cria uma
 * OS NOVA no cadastro de OS); no app SPDA independente, cadastros próprios.
 */
import { isManaged, storeGet, storeSet } from '../../services/localStore';
import { host } from './host.platform';
import { CHECKLIST_TEMPLATE } from './checklist';
import { DEFAULT_TEST_CURRENT_A, KIND_SUBSYSTEM, migrateInspection, newId, nextNumber, todayIso } from './rules';
import { CHECKLIST_VERSION } from './checklist';
import { SPDA_KEYS } from './storageKeys';
import { SPDA_MODULE_ID, SPDA_OS_DESCRIPTION } from './osText';
import type { ContinuityTest, GroundingTest, SpdaBuilding, SpdaInspection } from './types';
import type { CompanyLabInfo, ServiceOrder } from '../../types';

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
    try { localStorage.setItem(key, JSON.stringify(value)); } catch {
      throw new Error('Sem espaço no armazenamento do navegador (reduza as fotos).');
    }
  }
}

// ---------------------------------------------------------------- notificações
const listeners = new Set<() => void>();

export function subscribeSpda(listener: () => void): () => void {
  listeners.add(listener);
  return () => { listeners.delete(listener); };
}

let onLocalChange: (() => void) | null = null;

/** Chamado a cada gravação local (a sincronização usa para enviar logo). */
export function setSpdaChangeHandler(handler: (() => void) | null): void {
  onLocalChange = handler;
}

function notify(local = false): void {
  setTimeout(() => listeners.forEach(l => { try { l(); } catch { /* tela fechada */ } }), 0);
  if (local && onLocalChange) { try { onLocalChange(); } catch { /* sem sincronização */ } }
}

export function notifySpdaChanged(): void {
  notify(false);
}

// ------------------------------------------------------------- empresa/usuário
export function currentCompanyId(): string {
  try {
    return host.companyId();
  } catch {
    return '';
  }
}

export function currentUserName(): string {
  try {
    return host.currentUser()?.name || '';
  } catch {
    return '';
  }
}

export function canEditSpda(): boolean {
  try {
    const u = host.currentUser();
    return !!u && ['admin', 'responsavel_tecnico', 'tecnico'].includes(u.role);
  } catch {
    return false;
  }
}

// ------------------------------------------------------------------- registros
/** Lidas já no formato atual (registros antigos ganham o vínculo com a edificação). */
function allInspections(): SpdaInspection[] {
  return read<SpdaInspection[]>(SPDA_KEYS.INSPECTIONS, []).map(migrateInspection);
}

export function listInspections(): SpdaInspection[] {
  const company = currentCompanyId();
  return allInspections()
    .filter(r => !company || r.companyId === company)
    .sort((a, b) => (b.updatedAt || '').localeCompare(a.updatedAt || ''));
}

export function getInspection(id: string): SpdaInspection | undefined {
  return allInspections().find(r => r.id === id);
}

export function saveInspection(record: SpdaInspection): SpdaInspection {
  const now = new Date().toISOString();
  const saved: SpdaInspection = { ...record, companyId: record.companyId || currentCompanyId(), updatedAt: now, createdAt: record.createdAt || now };
  const list = allInspections();
  const idx = list.findIndex(r => r.id === saved.id);
  if (idx >= 0) list[idx] = saved; else list.push(saved);
  write(SPDA_KEYS.INSPECTIONS, list);
  enqueueSpda(saved.id, 'upsert');
  notify(true);
  return saved;
}

export function deleteInspection(id: string): void {
  write(SPDA_KEYS.INSPECTIONS, allInspections().filter(r => r.id !== id));
  enqueueSpda(id, 'delete');
  notify(true);
}

// ---------------------------------------------------------- fila de envio
export interface SpdaQueueItem {
  id: string;
  action: 'upsert' | 'delete';
  /** empresa do registro (para não enviar dados de outro login) */
  companyId: string;
  /** momento em que entrou na fila (nova edição durante o envio = novo valor) */
  queuedAt: number;
  attempts: number;
  lastError?: string;
}

export interface SpdaConflict {
  id: string;
  label: string;
  companyId: string;
  detectedAt: string;
}

export function getSpdaQueue(): SpdaQueueItem[] {
  return read<SpdaQueueItem[]>(SPDA_KEYS.QUEUE, []);
}

export function enqueueSpda(id: string, action: SpdaQueueItem['action']): void {
  const queue = getSpdaQueue().filter(q => q.id !== id);
  queue.push({ id, action, companyId: currentCompanyId(), queuedAt: Date.now() + Math.random(), attempts: 0 });
  write(SPDA_KEYS.QUEUE, queue);
}

export function updateSpdaQueue(mutator: (queue: SpdaQueueItem[]) => SpdaQueueItem[]): void {
  write(SPDA_KEYS.QUEUE, mutator(getSpdaQueue()));
}

/** Gravações deste login ainda não enviadas ao servidor. */
export function pendingSpdaCount(): number {
  const company = currentCompanyId();
  return getSpdaQueue().filter(q => !company || q.companyId === company).length;
}

/** Grava o que veio do servidor sem reenviar (e sem sobrescrever edições pendentes). */
export function applyRemoteSpda(rows: Array<{ id: string; payload: any; deleted_at: string | null; updated_at: string }>): number {
  if (!rows.length) return 0;
  const pending = new Set(getSpdaQueue().map(q => q.id));
  const conflicts = new Set(read<SpdaConflict[]>(SPDA_KEYS.CONFLICTS, []).map(c => c.id));
  const raw = read<SpdaInspection[]>(SPDA_KEYS.INSPECTIONS, []);
  const byId = new Map(raw.map(r => [r.id, r]));
  let changed = 0;
  rows.forEach(row => {
    if (pending.has(row.id) || conflicts.has(row.id)) return;
    if (row.deleted_at) {
      if (byId.delete(row.id)) changed++;
      return;
    }
    if (!row.payload || typeof row.payload !== 'object') return;
    if (byId.get(row.id)?.serverUpdatedAt === row.updated_at) return;
    byId.set(row.id, { ...row.payload, id: row.id, serverUpdatedAt: row.updated_at });
    changed++;
  });
  if (changed) {
    write(SPDA_KEYS.INSPECTIONS, Array.from(byId.values()));
    notify(false);
  }
  return changed;
}

/** Versão do servidor depois do envio (sem marcar como alterado). */
export function markSpdaSent(id: string, serverUpdatedAt: string): void {
  const raw = read<SpdaInspection[]>(SPDA_KEYS.INSPECTIONS, []);
  const idx = raw.findIndex(r => r.id === id);
  if (idx < 0) return;
  raw[idx] = { ...raw[idx], serverUpdatedAt };
  write(SPDA_KEYS.INSPECTIONS, raw);
}

export function clearSpdaServerVersion(id: string): void {
  const raw = read<SpdaInspection[]>(SPDA_KEYS.INSPECTIONS, []);
  const idx = raw.findIndex(r => r.id === id);
  if (idx < 0) return;
  const { serverUpdatedAt: _drop, ...rest } = raw[idx];
  raw[idx] = rest as SpdaInspection;
  write(SPDA_KEYS.INSPECTIONS, raw);
}

/** Inspeção crua (sem migração), para enviar ao servidor. */
export function getRawInspection(id: string): SpdaInspection | undefined {
  return read<SpdaInspection[]>(SPDA_KEYS.INSPECTIONS, []).find(r => r.id === id);
}

// -------------------------------------------------------------- conflitos
export function getSpdaConflicts(): SpdaConflict[] {
  const company = currentCompanyId();
  return read<SpdaConflict[]>(SPDA_KEYS.CONFLICTS, []).filter(c => !company || c.companyId === company);
}

export function addSpdaConflict(c: SpdaConflict): void {
  write(SPDA_KEYS.CONFLICTS, [...read<SpdaConflict[]>(SPDA_KEYS.CONFLICTS, []).filter(x => x.id !== c.id), c]);
  notify(false);
}

export function removeSpdaConflict(id: string): void {
  write(SPDA_KEYS.CONFLICTS, read<SpdaConflict[]>(SPDA_KEYS.CONFLICTS, []).filter(x => x.id !== id));
  notify(false);
}

// ------------------------------------------------- fotos já enviadas ao bucket
export function getSpdaPhotoUrls(): Record<string, string> {
  return read<Record<string, string>>(SPDA_KEYS.PHOTO_URLS, {});
}

export function addSpdaPhotoUrls(map: Record<string, string>): void {
  if (Object.keys(map).length) write(SPDA_KEYS.PHOTO_URLS, { ...getSpdaPhotoUrls(), ...map });
}

// ----------------------------------------------------------------- cursores
export function getSpdaCursor(scope: string): string {
  return read<Record<string, string>>(SPDA_KEYS.CURSORS, {})[scope] || '1970-01-01T00:00:00Z';
}

export function setSpdaCursor(scope: string, value: string): void {
  write(SPDA_KEYS.CURSORS, { ...read<Record<string, string>>(SPDA_KEYS.CURSORS, {}), [scope]: value });
}

// ------------------------------------------------------------------- criação
export function newContinuityTest(partial: Partial<ContinuityTest> = {}): ContinuityTest {
  const kind = partial.kind || 'F4';
  return {
    id: newId('cont'), kind, subsystem: KIND_SUBSYSTEM[kind], from: '', to: '', resistanceOhm: null, limitOhm: null, currentA: DEFAULT_TEST_CURRENT_A,
    ...(kind === 'F4' ? { from: 'Captação (ponto mais alto)', to: 'BEP' } : {}),
    ...partial
  };
}

export function newGroundingTest(partial: Partial<GroundingTest> = {}): GroundingTest {
  return { id: newId('ater'), point: '', method: 'queda_potencial', designOhm: null, ...partial };
}

export function newBuilding(partial: Partial<SpdaBuilding> = {}): SpdaBuilding {
  return { id: newId('edif'), name: '', type: 'industrial', spdaKind: 'externo', protectionLevel: '', ...partial };
}

/** Inspeção nova com dados do técnico logado e do responsável técnico da empresa. */
export function createInspection(): SpdaInspection {
  let technicianName = '';
  let technicianTitle = '';
  let technicianCrea = '';
  try {
    const u = host.currentUser();
    technicianName = u?.name || '';
    technicianTitle = u?.cargo || '';
    technicianCrea = u?.creaOrCft || '';
  } catch { /* sem usuário */ }
  const now = new Date().toISOString();
  return {
    checklistVersion: CHECKLIST_VERSION,
    id: newId('insp'),
    companyId: currentCompanyId(),
    number: nextNumber(allInspections().map(i => i.number), undefined, spdaSettings().numberPrefix || undefined),
    ...(spdaSettings().defaultValidityMonths && spdaSettings().defaultValidityMonths !== 12 ? { validityMonths: spdaSettings().defaultValidityMonths } : {}),
    status: 'rascunho',
    clientName: '',
    buildingName: '',
    address: '',
    protectionLevel: '',
    method: 'gaiola',
    inspectionKind: 'periodica',
    criticalStructure: false,
    inspectionDate: todayIso(),
    soilCondition: 'seco',
    instruments: [
      { use: 'continuidade', description: 'Micro-ohmímetro / miliohmímetro 4 fios (1 A a 5 A)', model: '', serialNumber: '', calibrationCert: '' },
      { use: 'aterramento', description: 'Terrômetro digital (medição informativa)', model: '', serialNumber: '', calibrationCert: '' }
    ],
    technicianName,
    technicianTitle,
    technicianCrea,
    buildings: [newBuilding({ name: 'Edificação 1' })],
    checklist: [],
    continuity: [],
    grounding: [],
    createdAt: now,
    updatedAt: now
  };
}

// ------------------------------------------------------------- dados da empresa
export function readClients() {
  try { return host.clients().filter(c => !c.deletedAt); } catch { return []; }
}

export function readInstruments() {
  try { return host.instruments().filter(i => !i.deletedAt && i.active !== false); } catch { return []; }
}

// ------------------------------------------------------------ ordens de serviço
/** OS do cadastro da plataforma (somente leitura), abertas primeiro; filtra pelo cliente se informado. */
export function readServiceOrders(clientId?: string): ServiceOrder[] {
  try {
    return host.serviceOrders()
      .filter(o => !o.deletedAt && o.status !== 'cancelada' && (!clientId || o.clientId === clientId))
      .sort((a, b) => (b.openDate || '').localeCompare(a.openDate || ''));
  } catch {
    return [];
  }
}

/** Descrição da OS gerada a partir da inspeção: o texto padrão do módulo SPDA. */
export function serviceOrderNotes(_insp?: Pick<SpdaInspection, 'number'>): string {
  return SPDA_OS_DESCRIPTION;
}

/** Monta a OS (função pura, testada). */
export function buildServiceOrder(
  insp: SpdaInspection,
  osNumber: string,
  user: { id: string; name: string } | null,
  client: { id: string; nomeFantasia?: string; razaoSocial?: string; companyId?: string } ,
  now = new Date().toISOString()
): ServiceOrder {
  return {
    id: newId('os'),
    companyId: client.companyId || insp.companyId || undefined,
    osNumber,
    clientId: client.id,
    clientName: client.nomeFantasia || client.razaoSocial || insp.clientName || 'Cliente Geral',
    openDate: insp.inspectionDate || now.slice(0, 10),
    scheduledDate: insp.inspectionDate || undefined,
    responsibleId: user?.id || '',
    responsibleName: user?.name || insp.technicianName || '',
    technicianId: user?.id || '',
    technicianName: insp.technicianName || user?.name || '',
    status: 'aberta',
    notes: serviceOrderNotes(insp),
    equipmentIds: [],
    module: SPDA_MODULE_ID,
    createdAt: now,
    updatedAt: now
  };
}

/**
 * Gera uma OS NOVA no cadastro de OS da plataforma (numeração oficial OS-…,
 * sincronizada pelo sistema). Não altera nenhuma OS existente.
 */
export function generateServiceOrder(insp: SpdaInspection): { id: string; osNumber: string } {
  if (!insp.clientId) throw new Error('Selecione um cliente cadastrado antes de gerar a OS.');
  const client = host.findClient(insp.clientId);
  if (!client) throw new Error('Cliente não encontrado no cadastro.');
  let user: { id: string; name: string } | null = null;
  try { const u = host.currentUser(); user = u ? { id: u.id, name: u.name } : null; } catch { /* sem usuário */ }
  const osNumber = host.nextServiceOrderNumber();
  const saved = host.saveServiceOrder(buildServiceOrder(insp, osNumber, user, client as any));
  return { id: saved.id, osNumber: saved.osNumber };
}

/** Parâmetros do SPDA da empresa (Configuração do Sistema → Parâmetros do SPDA). */
export function spdaSettings(): NonNullable<CompanyLabInfo['spdaSettings']> {
  try { return host.companyInfo()?.spdaSettings || {}; } catch { return {}; }
}

export function saveSpdaSettings(settings: NonNullable<CompanyLabInfo['spdaSettings']>): void {
  if (!host.updateCompanyInfo) throw new Error('Não é possível gravar os parâmetros aqui.');
  host.updateCompanyInfo({ spdaSettings: settings });
}

export function readCompanyInfo() {
  try { return host.companyInfo(); } catch { return null; }
}
