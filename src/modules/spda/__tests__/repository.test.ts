import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { IDBFactory } from 'fake-indexeddb';
import { __resetLocalStoreForTests, initLocalStore, storeGet } from '../../../services/localStore';
import { SPDA_KEYS, SPDA_MANAGED_KEYS } from '../storageKeys';
import { buildServiceOrder, createInspection, deleteInspection, generateServiceOrder, listInspections, newGroundingTest, readServiceOrders, saveInspection } from '../repository';
import { CHECKLIST_TEMPLATE } from '../checklist';
import { checklistProgress } from '../rules';
import type { Client, ServiceOrder } from '../../../types';

// Cadastros da plataforma simulados (no app, vêm do DielectricStorageService)
const platform = vi.hoisted(() => ({ clients: [] as any[], orders: [] as any[], seq: 0 }));
vi.mock('../host.platform', () => ({
  host: {
    companyId: () => 'c1',
    currentUser: () => ({ id: 'u1', name: 'Técnico Teste', role: 'tecnico', creaOrCft: 'CREA 123' }),
    clients: () => platform.clients,
    findClient: (id: string) => platform.clients.find(c => c.id === id),
    instruments: () => [],
    serviceOrders: () => platform.orders,
    nextServiceOrderNumber: () => `OS-2611-${String(++platform.seq).padStart(4, '0')}`,
    saveServiceOrder: (os: ServiceOrder) => { platform.orders.push(os); return os; },
    companyInfo: () => null
  }
}));
const newClient = (p: Partial<Client>) => ({ id: 'cli-' + Math.random().toString(36).slice(2), companyId: 'c1', ...p }) as Client;
const saveClient = (c: Client) => { platform.clients.push(c); return c; };

class MemoryStorage {
  private data = new Map<string, string>();
  getItem(k: string) { return this.data.has(k) ? this.data.get(k)! : null; }
  setItem(k: string, v: string) { this.data.set(k, String(v)); }
  removeItem(k: string) { this.data.delete(k); }
  clear() { this.data.clear(); }
}

beforeEach(async () => {
  (globalThis as any).localStorage = new MemoryStorage();
  __resetLocalStoreForTests();
  await initLocalStore([...SPDA_MANAGED_KEYS], new IDBFactory());
  platform.clients = []; platform.orders = []; platform.seq = 0;
});
afterEach(() => __resetLocalStoreForTests());

describe('Repositório SPDA (só no aparelho)', () => {
  it('cria inspeção com uma edificação, checklist em branco, inspetor do app e número sequencial', () => {
    const a = saveInspection(createInspection());
    const b = saveInspection(createInspection());
    expect(a.checklist).toEqual([]);
    expect(a.buildings).toHaveLength(1);
    expect(checklistProgress(a.checklist, a.buildings).total).toBe(CHECKLIST_TEMPLATE.length);
    expect(a.technicianName).toBe('Técnico Teste');
    expect(a.technicianCrea).toBe('CREA 123');
    expect(a.number).toMatch(/^SPDA-\d{4}-0001$/);
    expect(b.number).toMatch(/^SPDA-\d{4}-0002$/);
  });

  it('grava no IndexedDB (chave própria) e não no localStorage', () => {
    const a = saveInspection({ ...createInspection(), clientName: 'Cliente A', grounding: [newGroundingTest({ point: 'D1', r62: 4 })] });
    expect(listInspections().map(i => i.id)).toEqual([a.id]);
    expect((storeGet<any[]>(SPDA_KEYS.INSPECTIONS) || []).length).toBe(1);
    expect(localStorage.getItem(SPDA_KEYS.INSPECTIONS)).toBeNull();
  });

  it('atualiza e exclui só o registro indicado', () => {
    const a = saveInspection(createInspection());
    const b = saveInspection(createInspection());
    saveInspection({ ...a, buildingName: 'Bloco A' });
    deleteInspection(b.id);
    const list = listInspections();
    expect(list).toHaveLength(1);
    expect(list[0].buildingName).toBe('Bloco A');
  });
});

describe('Ordem de serviço da inspeção SPDA', () => {
  const client = { id: 'cli1', nomeFantasia: 'Cliente A', razaoSocial: 'Cliente A Ltda', companyId: 'c1' };

  it('monta a OS aberta, com cliente, técnico, data da inspeção e a descrição padrão do SPDA', () => {
    const insp = { ...createInspection(), clientId: 'cli1', buildingName: 'Galpão A', city: 'Brasília', state: 'DF', inspectionDate: '2026-11-10' };
    const os = buildServiceOrder(insp, 'OS-SPDA-2026-0007', { id: 'u1', name: 'Técnico Teste' }, client, '2026-11-09T10:00:00.000Z');
    expect(os).toMatchObject({
      osNumber: 'OS-SPDA-2026-0007', clientId: 'cli1', clientName: 'Cliente A', companyId: 'c1', status: 'aberta',
      openDate: '2026-11-10', responsibleId: 'u1', technicianName: 'Técnico Teste', equipmentIds: []
    });
    expect(os.notes).toBe('Inspeção visual e ensaios de continuidade e de resistência de aterramento dos subsistemas que compõem o SPDA.');
  });

  it('gerar OS exige cliente cadastrado', () => {
    expect(() => generateServiceOrder({ ...createInspection(), clientId: undefined })).toThrow(/cliente cadastrado/);
    expect(() => generateServiceOrder({ ...createInspection(), clientId: 'nao-existe' })).toThrow(/não encontrado/);
  });

  it('gera a OS no cadastro de OS da plataforma, com a numeração oficial', () => {
    const c = saveClient(newClient({ razaoSocial: 'Cliente A Ltda', nomeFantasia: 'Cliente A' }));
    const r1 = generateServiceOrder({ ...createInspection(), clientId: c.id });
    const r2 = generateServiceOrder({ ...createInspection(), clientId: c.id });
    expect(r1.osNumber).toBe('OS-2611-0001');
    expect(r2.osNumber).toBe('OS-2611-0002');
    expect(platform.orders.map(o => o.clientName)).toEqual(['Cliente A', 'Cliente A']);
  });

  it('lista OS do cliente, sem canceladas nem excluídas, mais recentes primeiro', () => {
    platform.orders.push(...[
      { id: 'a', osNumber: 'OS-1', clientId: 'cli1', openDate: '2026-01-01', status: 'aberta' },
      { id: 'b', osNumber: 'OS-2', clientId: 'cli1', openDate: '2026-03-01', status: 'em_execucao' },
      { id: 'c', osNumber: 'OS-3', clientId: 'cli1', openDate: '2026-04-01', status: 'cancelada' },
      { id: 'd', osNumber: 'OS-4', clientId: 'cli2', openDate: '2026-05-01', status: 'aberta' },
      { id: 'e', osNumber: 'OS-5', clientId: 'cli1', openDate: '2026-06-01', status: 'aberta', deletedAt: 'x' }
    ]);
    expect(readServiceOrders('cli1').map(o => o.osNumber)).toEqual(['OS-2', 'OS-1']);
    expect(readServiceOrders().map(o => o.osNumber)).toEqual(['OS-4', 'OS-2', 'OS-1']);
  });
});
