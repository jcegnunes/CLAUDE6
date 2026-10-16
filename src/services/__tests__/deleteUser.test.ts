import { describe, expect, it } from 'vitest';
import { canDeleteUser } from '../../components/CompanyUsersPanel';
import { userToRow } from '../supabaseMappers';
import type { User } from '../../types';

const u = (over: Partial<User>): User => ({ id: 'x', companyId: 'c1', name: 'X', email: 'x@x.com', role: 'tecnico', active: true, ...over } as User);

describe('Excluir usuário', () => {
  const admin = u({ id: 'adm', role: 'admin' });
  const tec = u({ id: 'tec', role: 'tecnico', hasLogin: true });

  it('só o administrador exclui; nunca a si mesmo nem o administrador da plataforma', () => {
    expect(canDeleteUser(admin, tec, [admin, tec])).toBeNull();
    expect(canDeleteUser(u({ id: 'rt', role: 'responsavel_tecnico' }), tec, [tec])).toMatch(/Somente o administrador/);
    expect(canDeleteUser(admin, admin, [admin])).toMatch(/próprio usuário/);
    expect(canDeleteUser(admin, u({ id: 'm', role: 'admin', isMasterAdmin: true }), [admin])).toMatch(/plataforma/);
  });

  it('não deixa a empresa sem administrador ativo', () => {
    const outroAdmin = u({ id: 'adm2', role: 'admin' });
    const master = u({ id: 'mst', role: 'admin', isMasterAdmin: true });
    expect(canDeleteUser(master, outroAdmin, [outroAdmin])).toMatch(/único administrador/);
    expect(canDeleteUser(master, outroAdmin, [outroAdmin, admin])).toBeNull();
    expect(canDeleteUser(master, outroAdmin, [outroAdmin, { ...admin, active: false }])).toMatch(/único administrador/);
  });

  it('a exclusão vai para o banco como deleted_at (o banco bloqueia o login)', () => {
    const row = userToRow({ ...tec, deletedAt: '2026-10-15T12:00:00Z', active: false } as any, 'DEV-1');
    expect(row.deleted_at).toBe('2026-10-15T12:00:00Z');
    expect(row.active).toBe(false);
  });
});
