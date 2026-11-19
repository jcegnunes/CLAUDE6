/**
 * Registro dos módulos da plataforma. Para incluir um módulo novo: crie a
 * pasta em src/modules/<nome>, exporte o manifesto e acrescente-o aqui
 * (e as chaves de armazenamento em src/modules/storageKeys.ts).
 */
import type { CompanyLabInfo } from '../types';
import type { PlatformModule } from './types';
import { treinamentosModule } from './treinamentos';
import { spdaModule } from './spda';
import { acessosModule } from './acessos';

export const PLATFORM_MODULES: PlatformModule[] = [treinamentosModule, spdaModule, acessosModule];

/** Módulo ligado para a empresa (ligado por padrão; desligado em Configurações). */
export function isModuleEnabled(info: Pick<CompanyLabInfo, 'enabledModules'> | null | undefined, moduleId: string): boolean {
  return info?.enabledModules?.[moduleId] !== false;
}

/** Módulo só do master (Gestão de Acessos) fica escondido para os demais usuários. */
export function canSeeModule(m: Pick<PlatformModule, 'masterOnly'>, user: { isMasterAdmin?: boolean } | null | undefined): boolean {
  return !m.masterOnly || !!user?.isMasterAdmin;
}

export function getModule(id: string): PlatformModule | undefined {
  return PLATFORM_MODULES.find(m => m.id === id);
}

/** Abre outra tela do app a partir de um módulo (ex.: atalho do painel para "Certificados"). */
export function navigateTo(view: string): void {
  window.dispatchEvent(new CustomEvent<string>('jvm-navigate', { detail: view }));
}
