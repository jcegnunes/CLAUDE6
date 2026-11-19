/**
 * Backup do módulo SPDA (inspeções, com fotos e croquis) dentro do backup completo da plataforma.
 * Arquivo leve (só o armazenamento local): não carrega as telas do módulo.
 */
import { storeGet, storeSet } from '../../services/localStore';
import { SPDA_KEYS } from './storageKeys';

export interface SpdaBackup {
  version: 1;
  inspections: Array<{ id: string } & Record<string, unknown>>;
}

export function exportSpdaBackup(): SpdaBackup {
  const list = storeGet<SpdaBackup['inspections']>(SPDA_KEYS.INSPECTIONS);
  return { version: 1, inspections: Array.isArray(list) ? list : [] };
}

/** Restaura juntando por id (o do backup substitui o de mesmo id; os demais ficam). */
export function restoreSpdaBackup(backup: unknown): number {
  const b = backup as SpdaBackup | null;
  if (!b || !Array.isArray(b.inspections)) return 0;
  const valid = b.inspections.filter(r => r && typeof r.id === 'string');
  const current = storeGet<SpdaBackup['inspections']>(SPDA_KEYS.INSPECTIONS) || [];
  const byId = new Map(current.map(r => [r.id, r]));
  valid.forEach(r => byId.set(r.id, r));
  storeSet(SPDA_KEYS.INSPECTIONS, Array.from(byId.values()));
  return valid.length;
}
