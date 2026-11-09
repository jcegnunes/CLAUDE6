import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { IDBFactory } from 'fake-indexeddb';
import { __resetLocalStoreForTests, initLocalStore, storeGet } from '../../../services/localStore';
import { DielectricStorageService } from '../../../services/syncEngine';
import { SPDA_KEYS, SPDA_MANAGED_KEYS } from '../storageKeys';
import { buildServiceOrder, createInspection, deleteInspection, generateServiceOrder, listInspections, newGroundingTest, readServiceOrders, saveInspection } from '../repository';
import { CHECKLIST_TEMPLATE } from '../checklist';
import { checklistProgress } from '../rules';

class MemoryStorage {
  private data = new Map<string, string>();
  getItem(k: string) { return this.data.has(k) ? this.data.get(k)! : null; }
  setItem(k: string, v: string) { this.data.set(k, String(v)); }
  removeItem(k: string) { this.data.delete(k); }
  clear() { this.data.clear(); }
}

let company = 'c1';

beforeEach(async () => {
  (globalThis as any).localStorage = new MemoryStorage();
  __resetLocalStoreForTests();
  await initLocalStore(SPDA_MANAGED_KEYS, new IDBFactory());
  company = 'c1';
  vi.spyOn(DielectricStorageService, 'getSessionCompanyId').mockImplementation(() => company);
  vi.spyOn(DielectricStorageService, 'getCurrentUser').mockReturnValue({ id: 'u1', name: 'Técnico Teste', role: 'admin', companyId: 'c1', creaOrCft: 'CREA 123' } as any);
});
afterEach(() => { vi.restoreAllMocks(); __resetLocalStoreForTests(); });

describe('Repositório SPDA (demo, somente local)', () => {
  it('cria inspeção com uma edificação, checklist em branco, técnico logado e número sequencial', () => {
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

  it('grava no IndexedDB (chave própria do módulo) e filtra pela empresa', () => {
    const a = saveInspection({ ...createInspection(), clientName: 'Cliente A', grounding: [newGroundingTest({ point: 'D1', r62: 4 })] });
    company = 'c2';
    saveInspection({ ...createInspection(), clientName: 'Cliente B' });
    expect(listInspections().map(i => i.clientName)).toEqual(['Cliente B']);
    company = 'c1';
    expect(listInspections().map(i => i.id)).toEqual([a.id]);
    expect((storeGet<any[]>(SPDA_KEYS.INSPECTIONS) || []).length).toBe(2);
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

  it('monta a OS aberta, com cliente, técnico, data da inspeção e descrição com o nº do laudo', () => {
    const insp = { ...createInspection(), clientId: 'cli1', buildingName: 'Galpão A', city: 'Brasília', state: 'DF', inspectionDate: '2026-11-10' };
    const os = buildServiceOrder(insp, 'OS-2611-0007', { id: 'u1', name: 'Técnico Teste' }, client, '2026-11-09T10:00:00.000Z');
    expect(os).toMatchObject({
      osNumber: 'OS-2611-0007', clientId: 'cli1', clientName: 'Cliente A', companyId: 'c1', status: 'aberta',
      openDate: '2026-11-10', responsibleId: 'u1', technicianName: 'Técnico Teste', equipmentIds: []
    });
    expect(os.notes).toMatch(/NBR 5419:2026/);
    expect(os.notes).toContain(insp.number);
    expect(os.notes).toContain('Galpão A');
  });

  it('gerar OS exige cliente cadastrado', () => {
    expect(() => generateServiceOrder({ ...createInspection(), clientId: undefined })).toThrow(/cliente cadastrado/);
  });

  it('gera pelo cadastro de OS da plataforma (numeração oficial) sem alterar OS existentes', () => {
    vi.spyOn(DielectricStorageService, 'getClients').mockReturnValue([client] as any);
    vi.spyOn(DielectricStorageService, 'generateNextOSNumber').mockReturnValue('OS-2611-0001');
    const save = vi.spyOn(DielectricStorageService, 'saveServiceOrder').mockImplementation((o: any) => o);
    const res = generateServiceOrder({ ...createInspection(), clientId: 'cli1' });
    expect(res.osNumber).toBe('OS-2611-0001');
    expect(save).toHaveBeenCalledTimes(1);
    expect(save.mock.calls[0][0].id).toMatch(/^os-/);
  });

  it('lista OS do cliente, sem canceladas nem excluídas, mais recentes primeiro', () => {
    vi.spyOn(DielectricStorageService, 'getServiceOrders').mockReturnValue([
      { id: 'a', osNumber: 'OS-1', clientId: 'cli1', openDate: '2026-01-01', status: 'aberta' },
      { id: 'b', osNumber: 'OS-2', clientId: 'cli1', openDate: '2026-03-01', status: 'em_execucao' },
      { id: 'c', osNumber: 'OS-3', clientId: 'cli1', openDate: '2026-04-01', status: 'cancelada' },
      { id: 'd', osNumber: 'OS-4', clientId: 'cli2', openDate: '2026-05-01', status: 'aberta' },
      { id: 'e', osNumber: 'OS-5', clientId: 'cli1', openDate: '2026-06-01', status: 'aberta', deletedAt: 'x' }
    ] as any);
    expect(readServiceOrders('cli1').map(o => o.osNumber)).toEqual(['OS-2', 'OS-1']);
    expect(readServiceOrders().map(o => o.osNumber)).toEqual(['OS-4', 'OS-2', 'OS-1']);
  });
});
