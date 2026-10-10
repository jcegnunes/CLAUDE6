import type { ComponentType } from 'react';
import type { LucideIcon } from 'lucide-react';
import type { UserRole } from '../types';

/**
 * Módulo da plataforma: pasta própria (telas, regras, PDF, sincronização e
 * SQL em supabase/modules), ligado ao app só por este manifesto.
 */
export interface PlatformModule {
  /** Também é o id da tela no menu (activeView) */
  id: string;
  label: string;
  description: string;
  icon: LucideIcon;
  /** Perfis que veem o módulo no menu */
  roles: UserRole[];
  View: ComponentType<Record<string, never>>;
  /**
   * Ao escolher este módulo depois do login: blocos comuns do menu que também
   * aparecem (ex.: clientes, validação, configuração) e itens a esconder.
   */
  workspace?: { sharedGroups: string[]; hiddenItems?: string[] };
  /**
   * Telas do módulo no bloco "Configuração do Sistema" do menu (aparecem só
   * quando este módulo está aberto). O id também é o da tela (activeView).
   */
  settingsItems?: ModuleSettingsItem[];
  /**
   * Subitens do bloco do módulo no menu (cada um é uma tela). O item com o
   * mesmo id do módulo usa a tela principal (View) e abre ao escolher o módulo.
   */
  menuItems?: Array<Omit<ModuleSettingsItem, 'View'> & { View?: ComponentType<Record<string, never>> }>;
}

export interface ModuleSettingsItem {
  id: string;
  label: string;
  icon: LucideIcon;
  roles: UserRole[];
  View: ComponentType<Record<string, never>>;
}
