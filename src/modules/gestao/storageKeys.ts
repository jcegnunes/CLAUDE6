/**
 * Chaves de armazenamento do módulo Gestão ADM. Arquivo leve: é lido na
 * abertura do app (main.tsx) para carregar os dados do IndexedDB.
 */
export const GESTAO_KEYS = {
  ENTRIES: 'jvm_gestao_entries',
  PRICINGS: 'jvm_gestao_pricings',
  SETTINGS: 'jvm_gestao_settings',
  SERVICES: 'jvm_gestao_services',
  BUDGETS: 'jvm_gestao_budgets',
  QUEUE: 'jvm_gestao_queue',
  CONFLICTS: 'jvm_gestao_conflicts',
  // pequenas: ficam no localStorage
  CURSORS: 'jvm_gestao_cursors',
  NUMBER_BLOCKS: 'jvm_gestao_number_blocks'
};

/** Chaves guardadas no IndexedDB (carregadas na abertura do app). */
export const GESTAO_MANAGED_KEYS = [
  GESTAO_KEYS.ENTRIES, GESTAO_KEYS.PRICINGS, GESTAO_KEYS.SETTINGS, GESTAO_KEYS.SERVICES, GESTAO_KEYS.BUDGETS,
  GESTAO_KEYS.QUEUE, GESTAO_KEYS.CONFLICTS
];
