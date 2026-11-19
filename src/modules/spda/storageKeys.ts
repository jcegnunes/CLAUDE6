/**
 * Chaves de armazenamento do módulo SPDA. Arquivo leve: lido na abertura do
 * app (main.tsx, via src/modules/storageKeys.ts).
 * As inspeções ficam no aparelho e são sincronizadas com o Supabase (sync.ts).
 */
export const SPDA_KEYS = {
  /** nome mantido da versão demo: as inspeções já gravadas continuam valendo */
  INSPECTIONS: 'jvm_spda_demo_inspections',
  /** fila de envio ao Supabase */
  QUEUE: 'jvm_spda_queue',
  /** inspeções editadas em dois aparelhos ao mesmo tempo (aguardando decisão) */
  CONFLICTS: 'jvm_spda_conflicts',
  /** fotos já enviadas ao bucket: assinatura da foto → endereço público */
  PHOTO_URLS: 'jvm_spda_photo_urls',
  // pequena: fica no localStorage
  CURSORS: 'jvm_spda_cursors'
};

/** Chaves guardadas no IndexedDB (fotos ficam dentro das inspeções). */
export const SPDA_MANAGED_KEYS = [SPDA_KEYS.INSPECTIONS, SPDA_KEYS.QUEUE, SPDA_KEYS.CONFLICTS, SPDA_KEYS.PHOTO_URLS];
