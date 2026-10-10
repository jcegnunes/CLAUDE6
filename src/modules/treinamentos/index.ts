/** Manifesto do módulo Treinamentos (lido pelo registro de módulos). */
import { GraduationCap, Palette } from 'lucide-react';
import { lazyView } from '../../utils/lazyView';
import type { PlatformModule } from '../types';

export const treinamentosModule: PlatformModule = {
  id: 'treinamentos',
  label: 'Treinamentos',
  description: 'Turmas, certificados de treinamento (NR-10, NR-35...) com QR Code e controle de reciclagem.',
  icon: GraduationCap,
  roles: ['admin', 'responsavel_tecnico', 'tecnico', 'administrativo'],
  // Clientes (empresa contratante da turma), validação e configuração; OS são dos ensaios
  workspace: { sharedGroups: ['clientes', 'validacao', 'sistema'], hiddenItems: ['service_orders'] },
  View: lazyView(() => import('./views/TrainingModuleView'), 'TrainingModuleView'),
  // Configuração do Sistema → Modelo do Certificado (alterar: só administrador ou RT)
  settingsItems: [{
    id: 'treinamentos_modelo',
    label: 'Modelo do Certificado',
    icon: Palette,
    roles: ['admin', 'responsavel_tecnico', 'tecnico', 'administrativo'],
    View: lazyView(() => import('./views/CertificateModelView'), 'CertificateModelView')
  }]
};
