import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const auth = vi.hoisted(() => ({ impl: (() => Promise.resolve({ success: false })) as (...a: unknown[]) => Promise<any>, access: { state: 'ok' } as any }));

vi.mock('../syncEngine', () => ({
  DielectricStorageService: {
    getCompanyById: () => undefined, getUsers: () => [], setCurrentUser: () => {}, setActiveCompanyById: () => {},
    clearLegacyPendingPasswords: () => {}, getCompanies: () => []
  }
}));
vi.mock('../supabaseService', () => ({
  SupabaseService: {
    authenticateWithSupabase: (...a: unknown[]) => auth.impl(...a),
    signOut: () => Promise.resolve(), getSession: () => Promise.resolve(null),
    checkAccountAccess: () => Promise.resolve(auth.access)
  }
}));

import { AuthService, SESSION_EXPIRED_REASON, SESSION_MAX_DAYS } from '../authService';

class MemoryStorage {
  private data = new Map<string, string>();
  getItem(k: string) { return this.data.has(k) ? this.data.get(k)! : null; }
  setItem(k: string, v: string) { this.data.set(k, String(v)); }
  removeItem(k: string) { this.data.delete(k); }
  clear() { this.data.clear(); }
}

const DAY = 86400000;
const USER = { id: 'u1', name: 'Técnico', email: 'tec@x.com', username: 'tec', role: 'tecnico', companyId: 'c1' } as any;

beforeEach(() => {
  (globalThis as any).localStorage = new MemoryStorage();
  (globalThis as any).sessionStorage = new MemoryStorage();
  (globalThis as any).window = globalThis;
  if (!(globalThis as any).CustomEvent) (globalThis as any).CustomEvent = class extends Event { detail: unknown; constructor(t: string, o?: any) { super(t); this.detail = o?.detail; } };
  Object.defineProperty(globalThis, 'navigator', { value: { onLine: true }, configurable: true });
});
afterEach(() => { vi.useRealTimers(); });

describe(`Login vence a cada ${SESSION_MAX_DAYS} dias`, () => {
  it('logo após o login a sessão vale; depois de 7 dias pede o login de novo com o aviso', () => {
    AuthService.setCurrentUser(USER, true, true);
    const start = AuthService.sessionStartedAt()!;
    expect(AuthService.isAuthenticated()).toBe(true);
    expect(AuthService.enforceSessionLimit(start + 6.9 * DAY)).toBe(false);
    expect(AuthService.isSessionExpired(start + 7 * DAY + 1000)).toBe(true);
    expect(AuthService.enforceSessionLimit(start + 7 * DAY + 1000)).toBe(true);
    expect(AuthService.getCurrentUser()).toBeNull();
    expect(AuthService.isAuthenticated()).toBe(false);
    expect(AuthService.consumeLogoutReason()).toBe(SESSION_EXPIRED_REASON);
  });

  it('trocar de empresa/perfil não renova o prazo; só um novo login com senha', () => {
    AuthService.setCurrentUser(USER, true, true);
    const start = AuthService.sessionStartedAt();
    AuthService.setCurrentUser({ ...USER, companyId: 'c2' }); // sem conferir senha
    expect(AuthService.sessionStartedAt()).toBe(start);
  });
});

describe('Login offline (APP no campo)', () => {
  it('sem conexão, entra com a senha de quem já entrou online neste aparelho', async () => {
    auth.impl = () => Promise.resolve({ success: true, user: USER });
    expect((await AuthService.loginAsync('tec', 'senha-1')).source).toBe('supabase');
    AuthService.logout();

    auth.impl = () => Promise.resolve({ success: false, networkError: true });
    const ok = await AuthService.loginAsync('tec', 'senha-1');
    expect(ok.success).toBe(true);
    expect(ok.source).toBe('offline');
    expect(AuthService.isAuthenticated()).toBe(true);
    AuthService.logout();
    expect((await AuthService.loginAsync('tec', 'errada')).success).toBe(false);
  });

  it('com sinal fraco (sem resposta do banco), entra offline após a espera', async () => {
    auth.impl = () => Promise.resolve({ success: true, user: USER });
    await AuthService.loginAsync('tec', 'senha-1');
    AuthService.logout();

    vi.useFakeTimers({ toFake: ['setTimeout'] });
    auth.impl = () => new Promise(() => {}); // nunca responde
    const p = AuthService.loginAsync('tec', 'senha-1');
    await vi.advanceTimersByTimeAsync(15000);
    vi.useRealTimers();
    const res = await p;
    expect(res.success).toBe(true);
    expect(res.source).toBe('offline');
  });

  it('quem nunca entrou online neste aparelho não entra offline', async () => {
    auth.impl = () => Promise.resolve({ success: false, networkError: true });
    const res = await AuthService.loginAsync('outro', 'x');
    expect(res.success).toBe(false);
  });
});

describe('Usuário bloqueado', () => {
  const loginOnline = async () => {
    auth.impl = () => Promise.resolve({ success: true, user: USER });
    await AuthService.loginAsync('tec', 'senha-1');
  };

  it('com o sistema aberto: sai na hora e não entra mais offline neste aparelho', async () => {
    await loginOnline();
    auth.access = { state: 'blocked', error: 'Usuário inativo. Contate o administrador.' };
    expect(await AuthService.enforceAccountAccess()).toBe('blocked');
    expect(AuthService.getCurrentUser()).toBeNull();
    expect(AuthService.consumeLogoutReason()).toMatch(/Acesso bloqueado/);

    auth.impl = () => Promise.resolve({ success: false, networkError: true });
    expect((await AuthService.loginAsync('tec', 'senha-1')).success).toBe(false);
  });

  it('ao tentar entrar com internet e o banco dizer "inativo", a senha offline é apagada', async () => {
    await loginOnline();
    AuthService.logout();
    auth.impl = () => Promise.resolve({ success: false, error: 'Usuário inativo. Contate o administrador.' });
    expect((await AuthService.loginAsync('tec', 'senha-1')).success).toBe(false);
    auth.impl = () => Promise.resolve({ success: false, networkError: true });
    expect((await AuthService.loginAsync('tec', 'senha-1')).success).toBe(false);
  });

  it('acesso da empresa vencido: sai com o aviso (mantém a senha offline)', async () => {
    await loginOnline();
    auth.access = { state: 'expired', error: 'O acesso da empresa venceu em 01/01/2026.' };
    expect(await AuthService.enforceAccountAccess()).toBe('expired');
    expect(AuthService.consumeLogoutReason()).toMatch(/venceu/);
  });

  it('sem internet ou erro de rede: continua logado', async () => {
    await loginOnline();
    auth.access = { state: 'unknown' };
    expect(await AuthService.enforceAccountAccess()).toBe('unknown');
    expect(AuthService.getCurrentUser()).not.toBeNull();
    Object.defineProperty(globalThis, 'navigator', { value: { onLine: false }, configurable: true });
    auth.access = { state: 'blocked' };
    expect(await AuthService.enforceAccountAccess()).toBe('unknown');
    expect(AuthService.getCurrentUser()).not.toBeNull();
  });
});
