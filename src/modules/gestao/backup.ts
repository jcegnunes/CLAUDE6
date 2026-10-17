/**
 * Backup do módulo Gestão ADM (contas, precificações, serviços, orçamentos e configuração de custos).
 * Arquivo leve (só o armazenamento local): é usado pelo backup completo da
 * plataforma sem carregar as telas do módulo.
 */
import { storeGet, storeSet } from '../../services/localStore';
import { GESTAO_KEYS } from './storageKeys';

const TABLES: Array<[string, string]> = [
  ['gestao_entries', GESTAO_KEYS.ENTRIES],
  ['gestao_pricings', GESTAO_KEYS.PRICINGS],
  ['gestao_settings', GESTAO_KEYS.SETTINGS],
  ['gestao_services', GESTAO_KEYS.SERVICES],
  ['gestao_budgets', GESTAO_KEYS.BUDGETS]
];

export interface GestaoBackup {
  version: 1;
  tables: Record<string, Array<{ id: string } & Record<string, unknown>>>;
}

export function exportGestaoBackup(): GestaoBackup {
  const tables: GestaoBackup['tables'] = {};
  TABLES.forEach(([table, key]) => {
    const list = storeGet<Array<{ id: string }>>(key);
    tables[table] = Array.isArray(list) ? list.map(r => {
      // a versão do servidor não vale para outro banco/aparelho
      const { serverUpdatedAt: _s, ...rest } = r as Record<string, unknown>;
      return rest as { id: string };
    }) : [];
  });
  return { version: 1, tables };
}

/**
 * Restaura juntando por id (o que veio no backup substitui o registro de mesmo
 * id; os demais ficam) e coloca tudo na fila de envio ao servidor.
 */
export function restoreGestaoBackup(backup: unknown): number {
  const b = backup as GestaoBackup | null;
  if (!b || typeof b !== 'object' || !b.tables || typeof b.tables !== 'object') return 0;
  const queue = (storeGet<Array<{ table: string; id: string }>>(GESTAO_KEYS.QUEUE) || []).slice();
  let restored = 0;
  TABLES.forEach(([table, key]) => {
    const incoming = Array.isArray(b.tables[table]) ? b.tables[table].filter(r => r && typeof r.id === 'string') : [];
    if (!incoming.length) return;
    const byId = new Map((storeGet<Array<{ id: string }>>(key) || []).map(r => [r.id, r]));
    incoming.forEach(r => {
      byId.set(r.id, r);
      if (!queue.some(q => q.table === table && q.id === r.id)) queue.push({ table, id: r.id, action: 'upsert', attempts: 0 } as any);
    });
    storeSet(key, Array.from(byId.values()));
    restored += incoming.length;
  });
  storeSet(GESTAO_KEYS.QUEUE, queue);
  return restored;
}
