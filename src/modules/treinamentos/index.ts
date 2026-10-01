/** Manifesto do módulo Treinamentos (lido pelo registro de módulos). */
import { GraduationCap } from 'lucide-react';
import { lazyView } from '../../utils/lazyView';
import type { PlatformModule } from '../types';

export const treinamentosModule: PlatformModule = {
  id: 'treinamentos',
  label: 'Treinamentos',
  description: 'Turmas, certificados de treinamento (NR-10, NR-35...) com QR Code e controle de reciclagem.',
  icon: GraduationCap,
  roles: ['admin', 'responsavel_tecnico', 'tecnico', 'administrativo'],
  View: lazyView(() => import('./views/TrainingModuleView'), 'TrainingModuleView')
};
