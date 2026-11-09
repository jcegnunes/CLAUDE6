/** Manifesto do módulo SPDA (DEMO) — lido pelo registro de módulos. */
import { Anchor, ArrowDownToLine, Building2, ClipboardCheck, ShieldCheck, Zap } from 'lucide-react';
import { lazyView } from '../../utils/lazyView';
import type { PlatformModule } from '../types';

export const spdaModule: PlatformModule = {
  id: 'spda',
  label: 'SPDA (demo)',
  description: 'Inspeção visual e ensaios de continuidade e aterramento de SPDA conforme ABNT NBR 5419, com laudo. Versão de demonstração: dados só no aparelho.',
  icon: Zap,
  // demo: apenas administrador e responsável técnico enxergam o módulo
  roles: ['admin', 'responsavel_tecnico'],
  // Bloco "Clientes & OS" no topo, igual aos Ensaios de EPI (Clientes e Ordens de Serviço);
  // o bloco do módulo vem logo abaixo, começando por Edificações
  workspace: { topGroups: ['clientes'], sharedGroups: ['sistema'] },
  View: lazyView(() => import('./views/SpdaModuleView'), 'SpdaModuleView'),
  menuItems: [
    { id: 'spda_edificacoes', label: 'Edificações', icon: Building2, roles: ['admin', 'responsavel_tecnico'], View: lazyView(() => import('./views/BuildingsView'), 'SpdaBuildingsView') },
    // subsistemas: inspeção visual + ensaios do Anexo F de cada um, para a OS escolhida
    { id: 'spda_captacao', label: 'Captação', icon: Zap, roles: ['admin', 'responsavel_tecnico'], View: lazyView(() => import('./views/SubsystemView'), 'SpdaCaptacaoView') },
    { id: 'spda_descida', label: 'Descida', icon: ArrowDownToLine, roles: ['admin', 'responsavel_tecnico'], View: lazyView(() => import('./views/SubsystemView'), 'SpdaDescidaView') },
    { id: 'spda_aterramento', label: 'Aterramento', icon: Anchor, roles: ['admin', 'responsavel_tecnico'], View: lazyView(() => import('./views/SubsystemView'), 'SpdaAterramentoView') },
    { id: 'spda_dps', label: 'DPS', icon: ShieldCheck, roles: ['admin', 'responsavel_tecnico'], View: lazyView(() => import('./views/SubsystemView'), 'SpdaDpsView') },
    // mesmo id do módulo: tela principal (abre ao escolher o módulo)
    { id: 'spda', label: 'Inspeções e laudos', icon: ClipboardCheck, roles: ['admin', 'responsavel_tecnico'] }
  ]
};
