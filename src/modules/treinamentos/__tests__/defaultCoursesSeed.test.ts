import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { IDBFactory } from 'fake-indexeddb';
import { __resetLocalStoreForTests, initLocalStore, storeGet, storeSet } from '../../../services/localStore';
import { DielectricStorageService } from '../../../services/syncEngine';
import { TRAINING_KEYS, TRAINING_MANAGED_KEYS } from '../storageKeys';
import { ensureDefaultCourses } from '../repository';
import { DEFAULT_COURSES } from '../defaultCourses';

class MemoryStorage {
  private data = new Map<string, string>();
  getItem(k: string) { return this.data.has(k) ? this.data.get(k)! : null; }
  setItem(k: string, v: string) { this.data.set(k, String(v)); }
  removeItem(k: string) { this.data.delete(k); }
  clear() { this.data.clear(); }
}

const OLD_NR35 = {
  id: 'crs-c1-nr35', companyId: 'c1', createdAt: '', updatedAt: '', code: 'NR-35', name: 'NR-35 – Trabalho em Altura',
  normReference: 'NR-35, item 35.3 – capacitação e treinamento', workloadHours: 8, validityMonths: 24, modality: 'presencial',
  minAttendance: 100, minGrade: 7, active: true, topics: [{ title: 'Normas e regulamentos aplicáveis ao trabalho em altura', hours: 8 }]
};

beforeEach(async () => {
  (globalThis as any).localStorage = new MemoryStorage();
  __resetLocalStoreForTests();
  await initLocalStore(TRAINING_MANAGED_KEYS, new IDBFactory());
  vi.spyOn(DielectricStorageService, 'getSessionCompanyId').mockReturnValue('c1');
  vi.spyOn(DielectricStorageService, 'getCurrentUser').mockReturnValue({ id: 'u1', role: 'admin', companyId: 'c1' } as any);
});
afterEach(() => { vi.restoreAllMocks(); __resetLocalStoreForTests(); });

const courses = () => storeGet<any[]>(TRAINING_KEYS.COURSES) || [];

describe('Cursos padrão em empresa que já usa o módulo', () => {
  it('acrescenta os cursos novos e atualiza a NR-35 criada pelo sistema', async () => {
    await storeSet(TRAINING_KEYS.COURSES, [OLD_NR35]);
    localStorage.setItem(TRAINING_KEYS.SEEDED, JSON.stringify({ c1: true })); // marca da primeira versão
    ensureDefaultCourses();
    const nr35 = courses().find(c => c.id === 'crs-c1-nr35');
    expect(nr35.normReference).toMatch(/35\.4\.2\.1/);
    expect(nr35.topics.some((t: any) => /escada/i.test(t.title))).toBe(true);
    expect(courses().some(c => c.code === 'NR-35 RECICLAGEM')).toBe(true);
    expect(courses().some(c => c.code === 'NR-33 VIGIA/TA')).toBe(true);
    // os cursos da primeira versão (já recebidos) não são recriados
    expect(courses().some(c => c.code === 'NR-10 BÁSICO')).toBe(false);
  });

  it('não mexe na NR-35 editada pela empresa e não repete a atualização', async () => {
    const custom = { ...OLD_NR35, normReference: 'NR-35 – conforme plano da empresa', workloadHours: 12 };
    await storeSet(TRAINING_KEYS.COURSES, [custom]);
    localStorage.setItem(TRAINING_KEYS.SEEDED, JSON.stringify({ c1: true }));
    ensureDefaultCourses();
    expect(courses().find(c => c.id === 'crs-c1-nr35')).toMatchObject({ normReference: 'NR-35 – conforme plano da empresa', workloadHours: 12 });
    expect(ensureDefaultCourses()).toBe(0);
  });

  it('empresa nova recebe todos os cursos padrão', async () => {
    expect(ensureDefaultCourses()).toBe(DEFAULT_COURSES.length);
  });
});
