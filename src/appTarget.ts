/**
 * Qual app está rodando (definido no build pelo vite.config.ts):
 * - 'plataforma': a plataforma web completa (Ensaios de EPI, Treinamentos, SPDA...).
 * - 'spda': o APP JVM SPDA (Android), só com o login e o módulo SPDA.
 * O APP de Ensaios de EPI é um projeto à parte (jvm-ensaios); todos usam o mesmo Supabase.
 */
declare const __APP_TARGET__: string | undefined;

export const APP_TARGET: 'plataforma' | 'spda' =
  typeof __APP_TARGET__ !== 'undefined' && __APP_TARGET__ === 'spda' ? 'spda' : 'plataforma';

/** Módulo único do app (null = plataforma com todos os módulos liberados). */
export const APP_ONLY_MODULE: string | null = APP_TARGET === 'spda' ? 'spda' : null;

export const APP_NAME = APP_TARGET === 'spda' ? 'JVM SPDA' : 'JVM Dielectric Lab';
