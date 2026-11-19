/** Manifesto do módulo SPDA (lido pelo registro de módulos). */
import { Anchor, ArrowDownToLine, BookOpenText, Building2, FileText, Gauge, LayoutDashboard, ShieldCheck, SlidersHorizontal, Zap } from 'lucide-react';
import { lazyView } from '../../utils/lazyView';
import type { UserRole } from '../../types';
import type { PlatformModule } from '../types';

const STAFF: UserRole[] = ['admin', 'responsavel_tecnico', 'tecnico', 'administrativo'];
const screen = (name: string) => lazyView(() => import('./views/platformScreens'), name);

export const spdaModule: PlatformModule = {
  id: 'spda',
  label: 'SPDA',
  description: 'Inspeção visual e ensaios de continuidade e aterramento de SPDA (ABNT NBR 5419:2026), laudo e relatório técnico.',
  icon: Zap,
  roles: STAFF,
  // Clientes & OS no topo (a inspeção usa o cliente e gera/vincula a OS), validação e configuração
  workspace: { topGroups: ['clientes'], sharedGroups: ['validacao', 'sistema'] },
  View: screen('SpdaInspectionsScreen'),
  menuItems: [
    { id: 'spda_dashboard', label: 'Dashboard', icon: LayoutDashboard, roles: STAFF, top: true, View: screen('SpdaDashboardScreen') },
    { id: 'spda', label: 'Inspeções e laudos', icon: FileText, roles: STAFF },
    { id: 'spda_edificacoes', label: 'Edificações', icon: Building2, roles: STAFF, View: screen('SpdaBuildingsScreen') },
    { id: 'spda_captacao', label: 'Captação', icon: Zap, roles: STAFF, View: screen('SpdaCaptacaoScreen') },
    { id: 'spda_descida', label: 'Descida', icon: ArrowDownToLine, roles: STAFF, View: screen('SpdaDescidaScreen') },
    { id: 'spda_aterramento', label: 'Aterramento', icon: Anchor, roles: STAFF, View: screen('SpdaAterramentoScreen') },
    { id: 'spda_dps', label: 'DPS', icon: ShieldCheck, roles: STAFF, View: screen('SpdaDpsScreen') },
    { id: 'spda_relatorio', label: 'Relatório', icon: BookOpenText, roles: STAFF, View: screen('SpdaReportScreen') },
    // tela comum da plataforma (a mesma dos ensaios de EPI)
    { id: 'instruments', label: 'Instrumentos', icon: Gauge, roles: ['admin', 'responsavel_tecnico', 'tecnico'] }
  ],
  // Configuração do Sistema → Parâmetros do SPDA (numeração e validade padrão do laudo)
  settingsItems: [
    { id: 'spda_parametros', label: 'Parâmetros do SPDA', icon: SlidersHorizontal, roles: STAFF, View: screen('SpdaSettingsScreen') }
  ]
};
