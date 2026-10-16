import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { IDBFactory } from 'fake-indexeddb';
import { __resetLocalStoreForTests, initLocalStore, storeGet, storeSet } from '../../../services/localStore';
import { GESTAO_KEYS, GESTAO_MANAGED_KEYS } from '../storageKeys';
import { exportModuleBackups, restoreModuleBackups } from '../../backup';

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
  await initLocalStore(GESTAO_MANAGED_KEYS, new IDBFactory());
});
afterEach(() => __resetLocalStoreForTests());

describe('Backup completo — módulo Gestão ADM', () => {
  it('exporta contas, precificações e configuração (sem a versão do servidor)', async () => {
    await storeSet(GESTAO_KEYS.ENTRIES, [{ id: 'rec-1', description: 'Ensaios', serverUpdatedAt: '2026-10-01T00:00:00Z' }]);
    await storeSet(GESTAO_KEYS.SETTINGS, [{ id: 'gcfg-comp-1', mealCost: 45 }]);
    const b: any = exportModuleBackups().gestao;
    expect(b.version).toBe(1);
    expect(b.tables.gestao_entries).toEqual([{ id: 'rec-1', description: 'Ensaios' }]);
    expect(b.tables.gestao_settings[0].mealCost).toBe(45);
    expect(b.tables.gestao_pricings).toEqual([]);
  });

  it('restaura juntando por id e coloca na fila de envio', async () => {
    await storeSet(GESTAO_KEYS.ENTRIES, [{ id: 'rec-1', description: 'Antiga' }, { id: 'pag-1', description: 'Fica' }]);
    const restored = restoreModuleBackups({ gestao: { version: 1, tables: { gestao_entries: [{ id: 'rec-1', description: 'Nova' }] } } });
    expect(restored.gestao).toBe(1);
    const list = storeGet<Array<{ id: string; description: string }>>(GESTAO_KEYS.ENTRIES)!;
    expect(list.find(e => e.id === 'rec-1')!.description).toBe('Nova');
    expect(list.some(e => e.id === 'pag-1')).toBe(true);
    expect(storeGet<Array<{ id: string }>>(GESTAO_KEYS.QUEUE)!.map(q => q.id)).toEqual(['rec-1']);
  });
});
