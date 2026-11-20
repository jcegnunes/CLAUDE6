import { beforeEach, describe, expect, it } from 'vitest';
import { __resetLocalStoreForTests } from '../localStore';
import { DielectricStorageService } from '../syncEngine';
import type { Company } from '../../types';

class MemoryStorage {
  private data = new Map<string, string>();
  getItem(k: string) { return this.data.has(k) ? this.data.get(k)! : null; }
  setItem(k: string, v: string) { this.data.set(k, String(v)); }
  removeItem(k: string) { this.data.delete(k); }
  clear() { this.data.clear(); }
}

const comp = (p: Partial<Company>): Company => ({ id: 'c', name: '', legalName: '', cnpj: '', active: true, createdAt: '', updatedAt: '', ...p });

beforeEach(() => {
  (globalThis as any).localStorage = new MemoryStorage();
  (globalThis as any).window = (globalThis as any).window || { dispatchEvent: () => true, addEventListener: () => {}, removeEventListener: () => {} };
  (globalThis as any).CustomEvent = (globalThis as any).CustomEvent || class { constructor(public type: string, public init?: unknown) {} };
  __resetLocalStoreForTests();
});

describe('Dados da empresa ao trocar de empresa no aparelho', () => {
  it('empresa nova começa em branco (sem logotipo, RT ou endereço da anterior)', () => {
    DielectricStorageService.syncCompanyInfoFromCompany(comp({ id: 'comp-jvm-real', name: 'JVM Engenharia', cnpj: '29.894.500/0001-04' }));
    DielectricStorageService.saveCompanyInfo({
      ...DielectricStorageService.getCompanyInfo(),
      logoUrl: 'data:image/png;base64,LOGO', address: 'Rua da JVM', technicalResponsible: { name: 'RT JVM', title: 'Eng.', creaNumber: '1', rnp: '' }
    });
    DielectricStorageService.syncCompanyInfoFromCompany(comp({ id: 'comp-nova', name: 'Empresa Nova' }), 'comp-jvm-real');
    const info = DielectricStorageService.getCompanyInfo();
    expect(info.name).toBe('Empresa Nova');
    expect(info.logoUrl).toBeFalsy();
    expect(info.address).toBe('');
    expect(info.technicalResponsible?.name).toBe('');
    expect(info.ownerCompanyId).toBe('comp-nova');
  });

  it('a mesma empresa mantém os dados dela', () => {
    DielectricStorageService.syncCompanyInfoFromCompany(comp({ id: 'comp-a', name: 'A' }));
    DielectricStorageService.saveCompanyInfo({ ...DielectricStorageService.getCompanyInfo(), logoUrl: 'data:image/png;base64,A' });
    DielectricStorageService.syncCompanyInfoFromCompany(comp({ id: 'comp-a', name: 'A' }), 'comp-a');
    expect(DielectricStorageService.getCompanyInfo().logoUrl).toBe('data:image/png;base64,A');
  });
});
