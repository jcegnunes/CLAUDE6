/** Manifesto do módulo Gestão ADM (lido pelo registro de módulos). */
import { ArrowDownCircle, ArrowUpCircle, Calculator, ClipboardList, LayoutDashboard, LineChart, SlidersHorizontal, Wallet, Wrench } from 'lucide-react';
import { lazyView } from '../../utils/lazyView';
import type { UserRole } from '../../types';
import type { PlatformModule } from '../types';

// Contas e fluxo de caixa: financeiro; precificação e custos: também o Responsável Técnico
const FINANCE: UserRole[] = ['admin', 'administrativo'];
const PRICING: UserRole[] = ['admin', 'administrativo', 'responsavel_tecnico'];
const views = () => import('./views/GestaoModuleView');

export const gestaoModule: PlatformModule = {
  id: 'gestao',
  label: 'Gestão ADM',
  description: 'Orçamentos, cadastro de serviços, precificação pela planilha, contas a pagar e a receber e fluxo de caixa.',
  icon: Wallet,
  roles: PRICING,
  // Clientes (quem paga) no topo; OS para vincular contas e precificações
  workspace: { topGroups: ['clientes'], sharedGroups: ['validacao', 'sistema'] },
  View: lazyView(views, 'GestaoModuleView'),
  menuItems: [
    { id: 'gestao', label: 'Dashboard', icon: LayoutDashboard, roles: PRICING, top: true },
    { id: 'gestao_receber', label: 'Contas a receber', icon: ArrowDownCircle, roles: FINANCE, View: lazyView(views, 'GestaoReceivablesView') },
    { id: 'gestao_pagar', label: 'Contas a pagar', icon: ArrowUpCircle, roles: FINANCE, View: lazyView(views, 'GestaoPayablesView') },
    { id: 'gestao_fluxo', label: 'Fluxo de caixa', icon: LineChart, roles: FINANCE, View: lazyView(views, 'GestaoCashFlowView') },
    { id: 'gestao_orcamentos', label: 'Orçamentos', icon: ClipboardList, roles: PRICING, View: lazyView(views, 'GestaoBudgetsView') },
    { id: 'gestao_servicos', label: 'Serviços', icon: Wrench, roles: PRICING, View: lazyView(views, 'GestaoServicesView') },
    { id: 'gestao_precificacao', label: 'Precificação', icon: Calculator, roles: PRICING, View: lazyView(views, 'GestaoPricingView') }
  ],
  // Configuração do Sistema → Custos e despesas (alterar: administrador ou administrativo)
  settingsItems: [{
    id: 'gestao_custos',
    label: 'Custos e despesas',
    icon: SlidersHorizontal,
    roles: PRICING,
    View: lazyView(views, 'GestaoCostSettingsView')
  }]
};
