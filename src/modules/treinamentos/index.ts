/** Manifesto do módulo Treinamentos (lido pelo registro de módulos). */
import { GraduationCap, Palette, LayoutDashboard, Users, Award, BookOpen, UserCheck } from 'lucide-react';
import { lazyView } from '../../utils/lazyView';
import type { PlatformModule } from '../types';

export const treinamentosModule: PlatformModule = {
  id: 'treinamentos',
  label: 'Treinamentos',
  description: 'Turmas, certificados de treinamento (NR-10, NR-35...) com QR Code e controle de reciclagem.',
  icon: GraduationCap,
  roles: ['admin', 'responsavel_tecnico', 'tecnico', 'administrativo'],
  // Clientes (empresa contratante da turma), validação e configuração; OS são dos ensaios
  workspace: { topGroups: ['clientes'], sharedGroups: ['validacao', 'sistema'], hiddenItems: ['service_orders'] },
  View: lazyView(() => import('./views/TrainingModuleView'), 'TrainingModuleView'),
  // Subitens do menu Treinamentos (antes eram abas dentro da tela)
  menuItems: [
    { id: 'treinamentos', label: 'Dashboard', icon: LayoutDashboard, roles: ['admin', 'responsavel_tecnico', 'tecnico', 'administrativo'], top: true },
    { id: 'treinamentos_turmas', label: 'Turmas', icon: Users, roles: ['admin', 'responsavel_tecnico', 'tecnico', 'administrativo'], View: lazyView(() => import('./views/TrainingModuleView'), 'TrainingClassesView') },
    { id: 'treinamentos_certificados', label: 'Certificados', icon: Award, roles: ['admin', 'responsavel_tecnico', 'tecnico', 'administrativo'], View: lazyView(() => import('./views/TrainingModuleView'), 'TrainingCertificatesView') },
    { id: 'treinamentos_cursos', label: 'Cursos', icon: BookOpen, roles: ['admin', 'responsavel_tecnico', 'tecnico', 'administrativo'], View: lazyView(() => import('./views/TrainingModuleView'), 'TrainingCoursesView') },
    { id: 'treinamentos_instrutores', label: 'Instrutores', icon: UserCheck, roles: ['admin', 'responsavel_tecnico', 'tecnico', 'administrativo'], View: lazyView(() => import('./views/TrainingModuleView'), 'TrainingInstructorsView') }
  ],
  // Configuração do Sistema → Modelo do Certificado (alterar: só administrador ou RT)
  settingsItems: [{
    id: 'treinamentos_modelo',
    label: 'Modelo do Certificado',
    icon: Palette,
    roles: ['admin', 'responsavel_tecnico', 'tecnico', 'administrativo'],
    View: lazyView(() => import('./views/CertificateModelView'), 'CertificateModelView')
  }]
};
