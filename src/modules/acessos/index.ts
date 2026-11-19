/**
 * Manifesto do módulo Gestão de Acessos (só o administrador master):
 * pedidos de conta, empresas (módulos e validade do acesso) e usuários (bloqueio, perfil e módulos).
 * Banco: supabase/modules/cadastro.sql (pedidos) e supabase/modules/acessos.sql (empresas, usuários e validade).
 */
import { Building2, KeyRound, UserPlus, Users } from 'lucide-react';
import { lazyView } from '../../utils/lazyView';
import type { PlatformModule } from '../types';

const screen = (name: string) => lazyView(() => import('./views/AccessViews'), name);

export const acessosModule: PlatformModule = {
  id: 'acessos',
  label: 'Gestão de Acessos',
  description: 'Pedidos de conta de novas empresas, módulos e validade do acesso por empresa, bloqueio e liberação de usuários.',
  icon: KeyRound,
  roles: ['admin'],
  masterOnly: true,
  workspace: { sharedGroups: ['sistema'] },
  View: screen('AccountRequestsScreen'),
  menuItems: [
    { id: 'acessos', label: 'Pedidos de conta', icon: UserPlus, roles: ['admin'] },
    { id: 'acessos_empresas', label: 'Empresas e validade', icon: Building2, roles: ['admin'], View: screen('CompaniesScreen') },
    { id: 'acessos_usuarios', label: 'Usuários e bloqueio', icon: Users, roles: ['admin'], View: screen('UsersScreen') }
  ]
};
