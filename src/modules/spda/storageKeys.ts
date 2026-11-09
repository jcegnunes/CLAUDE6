/**
 * Chaves de armazenamento do módulo SPDA (DEMO). Arquivo leve: lido na
 * abertura do app (main.tsx, via src/modules/storageKeys.ts).
 * Demo: somente no aparelho — nada vai para o Supabase.
 */
export const SPDA_KEYS = {
  INSPECTIONS: 'jvm_spda_demo_inspections'
};

/** Chaves guardadas no IndexedDB (fotos ficam dentro das inspeções). */
export const SPDA_MANAGED_KEYS = [SPDA_KEYS.INSPECTIONS];
