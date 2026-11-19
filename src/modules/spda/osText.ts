/**
 * Ordens de Serviço do módulo SPDA (arquivo leve: usado também pela tela de OS da plataforma).
 * Cada OS aberta pelo módulo SPDA leva module = 'spda'; o módulo SPDA só enxerga essas OS
 * (não as dos Ensaios de EPI nem de outros módulos).
 */
import type { ServiceOrder } from '../../types';

export const SPDA_MODULE_ID = 'spda';

/** Descrição padrão da OS do módulo SPDA. */
export const SPDA_OS_DESCRIPTION = 'Inspeção visual e ensaios de continuidade e de resistência de aterramento dos subsistemas que compõem o SPDA.';

/**
 * A OS é do módulo SPDA? Marcadas com module = 'spda'; as antigas, sem marca, contam como SPDA
 * só quando a descrição é de SPDA (as geradas pelas inspeções antes desta versão).
 */
export function isSpdaServiceOrder(os: Pick<ServiceOrder, 'module' | 'notes'>): boolean {
  if (os.module) return os.module === SPDA_MODULE_ID;
  return /\bSPDA\b/i.test(os.notes || '');
}

/** OS dos Ensaios de EPI: a lista sem as OS do módulo SPDA (telas do módulo de ensaios). */
export function withoutSpdaOrders<T extends Pick<ServiceOrder, 'module' | 'notes'>>(list: T[]): T[] {
  return list.filter(os => !isSpdaServiceOrder(os));
}
