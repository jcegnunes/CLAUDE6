import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { __resetLocalStoreForTests, StorageStuckError, initLocalStore, isManaged } from '../localStore';

class MemoryStorage {
  private data = new Map<string, string>();
  getItem(k: string) { return this.data.has(k) ? this.data.get(k)! : null; }
  setItem(k: string, v: string) { this.data.set(k, String(v)); }
  removeItem(k: string) { this.data.delete(k); }
  clear() { this.data.clear(); }
}

beforeEach(() => {
  (globalThis as any).localStorage = new MemoryStorage();
  __resetLocalStoreForTests();
  vi.useFakeTimers();
});
afterEach(() => { vi.useRealTimers(); __resetLocalStoreForTests(); });

/** IndexedDB que nunca responde (o que acontece quando o armazenamento do WebView trava). */
const hangingFactory = { open: () => ({}) } as unknown as IDBFactory;

/** Abre, mas a leitura dos dados nunca termina. */
function openButStuckOnRead(): IDBFactory {
  const db = {
    objectStoreNames: { contains: () => true },
    transaction: () => ({ objectStore: () => ({ get: () => ({}) }) }),
    close: () => {}
  };
  return {
    open: () => {
      const req: any = { result: db };
      setTimeout(() => req.onsuccess?.(), 0);
      return req;
    }
  } as unknown as IDBFactory;
}

describe('Início do app com o armazenamento travado', () => {
  it('o banco não abre: avisa (não abre o app vazio)', async () => {
    const p = initLocalStore(['k'], hangingFactory);
    const check = expect(p).rejects.toBeInstanceOf(StorageStuckError);
    await vi.advanceTimersByTimeAsync(10000);
    await check;
    expect(isManaged('k')).toBe(false);
  });

  it('o banco abre mas a leitura não termina: avisa e não fica com dados pela metade', async () => {
    const p = initLocalStore(['k'], openButStuckOnRead());
    const check = expect(p).rejects.toBeInstanceOf(StorageStuckError);
    await vi.advanceTimersByTimeAsync(31000);
    await check;
    expect(isManaged('k')).toBe(false);
  });
});
