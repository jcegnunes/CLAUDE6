import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { IDBFactory } from 'fake-indexeddb';
import { __resetLocalStoreForTests, initLocalStore } from '../../../services/localStore';
import { SPDA_MANAGED_KEYS } from '../storageKeys';
import {
  applyRemoteSpda, createInspection, deleteInspection, getInspection, getSpdaQueue, listInspections, markSpdaSent, pendingSpdaCount, saveInspection
} from '../repository';
import { inlineImages, photoKey, replaceImages, toSpdaRow } from '../sync';

vi.mock('../host.platform', () => ({
  host: {
    companyId: () => 'c1',
    currentUser: () => ({ id: 'u1', name: 'Técnico', role: 'tecnico' }),
    clients: () => [], findClient: () => undefined, instruments: () => [], serviceOrders: () => [],
    nextServiceOrderNumber: () => 'OS-1', saveServiceOrder: (o: unknown) => o, companyInfo: () => null
  }
}));

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
});
afterEach(() => __resetLocalStoreForTests());

const IMG_A = 'data:image/jpeg;base64,AAAA';
const IMG_B = 'data:image/png;base64,BBBB';

describe('Sincronização SPDA — fotos', () => {
  it('acha as imagens embutidas em qualquer campo, sem repetir', () => {
    const rec = { a: IMG_A, list: [{ url: IMG_B }, { url: IMG_A }], deep: { x: { y: [IMG_B] } }, pdf: 'data:application/pdf;base64,CCCC', txt: 'texto' };
    expect(Array.from(inlineImages(rec)).sort()).toEqual([IMG_A, IMG_B].sort());
  });

  it('troca só as imagens já enviadas, sem alterar o original', () => {
    const rec = { a: IMG_A, list: [{ url: IMG_B }] };
    const out = replaceImages(rec, { [photoKey(IMG_A)]: 'https://x/a.jpg' });
    expect(out).toEqual({ a: 'https://x/a.jpg', list: [{ url: IMG_B }] });
    expect(rec.a).toBe(IMG_A);
  });

  it('assinatura da foto: igual para a mesma foto, diferente para outra', () => {
    expect(photoKey(IMG_A)).toBe(photoKey(String(IMG_A)));
    expect(photoKey(IMG_A)).not.toBe(photoKey(IMG_B));
  });
});

describe('Sincronização SPDA — fila e dados do servidor', () => {
  it('gravar e excluir entram na fila (uma entrada por inspeção)', () => {
    const a = saveInspection(createInspection());
    saveInspection({ ...a, buildingName: 'Bloco A' });
    expect(getSpdaQueue().map(q => [q.id, q.action])).toEqual([[a.id, 'upsert']]);
    expect(pendingSpdaCount()).toBe(1);
    deleteInspection(a.id);
    expect(getSpdaQueue().map(q => [q.id, q.action])).toEqual([[a.id, 'delete']]);
  });

  it('linha do servidor com colunas de consulta, empresa e versão de base', () => {
    const a = { ...createInspection(), clientName: 'Cliente X', inspectionDate: '2026-11-17', serverUpdatedAt: '2026-11-17T10:00:00Z', techReport: { results: { final: 'aprovado' as const } } };
    const row = toSpdaRow(a, { ok: true });
    expect(row).toMatchObject({ id: a.id, company_id: 'c1', client_name: 'Cliente X', inspection_date: '2026-11-17', result: 'aprovado', base_updated_at: '2026-11-17T10:00:00Z', deleted_at: null, payload: { ok: true } });
  });

  it('recebe do servidor sem sobrescrever edição pendente e aplica exclusões', () => {
    const a = saveInspection(createInspection());
    // pendente: o servidor não sobrescreve
    applyRemoteSpda([{ id: a.id, payload: { ...a, buildingName: 'Servidor' }, deleted_at: null, updated_at: '2026-11-17T10:00:00Z' }]);
    expect(getInspection(a.id)?.buildingName).not.toBe('Servidor');
    // enviada: aceita a versão do servidor
    const queueless = { ...a, id: 'remoto-1', companyId: 'c1' };
    applyRemoteSpda([{ id: 'remoto-1', payload: queueless, deleted_at: null, updated_at: '2026-11-17T11:00:00Z' }]);
    expect(getInspection('remoto-1')?.serverUpdatedAt).toBe('2026-11-17T11:00:00Z');
    expect(listInspections().some(i => i.id === 'remoto-1')).toBe(true);
    applyRemoteSpda([{ id: 'remoto-1', payload: null, deleted_at: '2026-11-17T12:00:00Z', updated_at: '2026-11-17T12:00:00Z' }]);
    expect(getInspection('remoto-1')).toBeUndefined();
  });

  it('marca a versão do servidor depois do envio', () => {
    const a = saveInspection(createInspection());
    markSpdaSent(a.id, '2026-11-17T13:00:00Z');
    expect(getInspection(a.id)?.serverUpdatedAt).toBe('2026-11-17T13:00:00Z');
  });
});
