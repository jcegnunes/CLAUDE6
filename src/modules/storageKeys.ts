/** Chaves do IndexedDB de todos os módulos (lidas em main.tsx antes de abrir o app). */
import { TRAINING_MANAGED_KEYS } from './treinamentos/storageKeys';
import { SPDA_MANAGED_KEYS } from './spda/storageKeys'; // DEMO SPDA

export const MODULE_STORAGE_KEYS: string[] = [...TRAINING_MANAGED_KEYS, ...SPDA_MANAGED_KEYS];
