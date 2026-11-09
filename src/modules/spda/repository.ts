/**
 * Dados do módulo SPDA (DEMO) no aparelho (IndexedDB pelo localStore; se o
 * IndexedDB não estiver disponível, localStorage). SEM sincronização com o
 * Supabase e sem tabelas novas.
 *
 * Do restante da plataforma o módulo LÊ: empresa ativa, usuário logado,
 * clientes, instrumentos, ordens de serviço e responsável técnico.
 * Única escrita fora do módulo: "Gerar OS" cria uma OS NOVA no cadastro de OS
 * (pelo próprio DielectricStorageService, que a sincroniza); nada existente é alterado.
 */
import { isManaged, storeGet, storeSet } from '../../services/localStore';
import { DielectricStorageService } from '../../services/syncEngine';
import { CHECKLIST_TEMPLATE } from './checklist';
import { KIND_SUBSYSTEM, migrateInspection, newId, nextNumber, todayIso } from './rules';
import { SPDA_KEYS } from './storageKeys';
import type { ContinuityTest, GroundingTest, SpdaBuilding, SpdaInspection } from './types';
import type { ServiceOrder } from '../../types';

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

function notify(): void {
  setTimeout(() => listeners.forEach(l => { try { l(); } catch { /* tela fechada */ } }), 0);
}

// ------------------------------------------------------------- empresa/usuário
export function currentCompanyId(): string {
  try {
    return DielectricStorageService.getSessionCompanyId() || DielectricStorageService.getActiveCompany()?.id || '';
  } catch {
    return '';
  }
}

export function currentUserName(): string {
  try {
    return DielectricStorageService.getCurrentUser()?.name || '';
  } catch {
    return '';
  }
}

export function canEditSpda(): boolean {
  try {
    const u = DielectricStorageService.getCurrentUser();
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
  notify();
  return saved;
}

export function deleteInspection(id: string): void {
  write(SPDA_KEYS.INSPECTIONS, allInspections().filter(r => r.id !== id));
  notify();
}

// ------------------------------------------------------------------- criação
export function newContinuityTest(partial: Partial<ContinuityTest> = {}): ContinuityTest {
  const kind = partial.kind || 'F4';
  return {
    id: newId('cont'), kind, subsystem: KIND_SUBSYSTEM[kind], from: '', to: '', resistanceOhm: null, limitOhm: null, currentA: null,
    ...(kind === 'F4' ? { from: 'Captação (ponto mais alto)', to: 'BEP' } : {}),
    ...partial
  };
}

export function newGroundingTest(partial: Partial<GroundingTest> = {}): GroundingTest {
  return { id: newId('ater'), point: '', method: 'queda_potencial', designOhm: null, ...partial };
}

export function newBuilding(partial: Partial<SpdaBuilding> = {}): SpdaBuilding {
  return { id: newId('edif'), name: '', type: 'industrial', spdaKind: 'externo', protectionLevel: 'III', ...partial };
}

/** Inspeção nova com dados do técnico logado e do responsável técnico da empresa. */
export function createInspection(): SpdaInspection {
  let technicianName = '';
  let technicianTitle = '';
  let technicianCrea = '';
  try {
    const u = DielectricStorageService.getCurrentUser();
    technicianName = u?.name || '';
    technicianTitle = u?.cargo || '';
    technicianCrea = u?.creaOrCft || '';
  } catch { /* sem usuário */ }
  const now = new Date().toISOString();
  return {
    id: newId('insp'),
    companyId: currentCompanyId(),
    number: nextNumber(allInspections().map(i => i.number)),
    status: 'rascunho',
    clientName: '',
    buildingName: '',
    address: '',
    protectionLevel: 'III',
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
  try { return DielectricStorageService.getClients().filter(c => !c.deletedAt); } catch { return []; }
}

export function readInstruments() {
  try { return DielectricStorageService.getInstruments().filter(i => !i.deletedAt && i.active !== false); } catch { return []; }
}

// ------------------------------------------------------------ ordens de serviço
/** OS do cadastro da plataforma (somente leitura), abertas primeiro; filtra pelo cliente se informado. */
export function readServiceOrders(clientId?: string): ServiceOrder[] {
  try {
    return DielectricStorageService.getServiceOrders()
      .filter(o => !o.deletedAt && o.status !== 'cancelada' && (!clientId || o.clientId === clientId))
      .sort((a, b) => (b.openDate || '').localeCompare(a.openDate || ''));
  } catch {
    return [];
  }
}

/** Texto da descrição da OS gerada a partir da inspeção. */
export function serviceOrderNotes(insp: Pick<SpdaInspection, 'number' | 'buildingName' | 'address' | 'city' | 'state'>): string {
  const local = [insp.buildingName, insp.address, insp.city && `${insp.city}${insp.state ? `/${insp.state}` : ''}`].filter(Boolean).join(' — ');
  return `Inspeção e ensaios de SPDA conforme ABNT NBR 5419:2026 (inspeção visual e ensaios de continuidade)${local ? ` — ${local}` : ''}. Laudo ${insp.number}.`;
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
    id: `os-${Date.now()}`,
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
  const client = DielectricStorageService.getClients('ALL').find(c => c.id === insp.clientId);
  if (!client) throw new Error('Cliente não encontrado no cadastro.');
  let user: { id: string; name: string } | null = null;
  try { const u = DielectricStorageService.getCurrentUser(); user = u ? { id: u.id, name: u.name } : null; } catch { /* sem usuário */ }
  const osNumber = DielectricStorageService.generateNextOSNumber();
  const saved = DielectricStorageService.saveServiceOrder(buildServiceOrder(insp, osNumber, user, client as any));
  return { id: saved.id, osNumber: saved.osNumber };
}

export function readCompanyInfo() {
  try { return DielectricStorageService.getCompanyInfo(); } catch { return null; }
}
