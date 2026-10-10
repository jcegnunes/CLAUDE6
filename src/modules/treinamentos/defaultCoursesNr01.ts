/**
 * Curso NR-01 (texto atualizado 2025): fatores de risco psicossociais
 * relacionados ao trabalho no gerenciamento de riscos (GRO/PGR) e prevenção
 * ao assédio e à violência. A NR-01 não define um curso específico: o
 * conteúdo segue os itens 1.4.1, 1.4.1.1, 1.5.3 e 1.5.4; a carga horária e a
 * periodicidade são definidas pelo empregador (item 1.7.1.2.2), com ações
 * anuais sobre assédio e violência nas empresas com CIPA (item 1.4.1.1 "c").
 */
import type { TrainingCourse } from './types';

type CourseSeed = Omit<TrainingCourse, 'id' | 'companyId' | 'createdAt' | 'updatedAt' | 'serverUpdatedAt'>;

export const NR01_COURSES: Array<{ key: string } & CourseSeed> = [
  {
    key: 'nr01-psicossocial',
    code: 'NR-01 PSICOSSOCIAL',
    name: 'NR-01 – Fatores de Risco Psicossociais Relacionados ao Trabalho e Prevenção ao Assédio',
    normReference: 'NR-01 (texto atualizado 2025), itens 1.4.1 "g", 1.4.1.1, 1.5.3.1.4, 1.5.3.2.1, 1.5.3.3 e 1.5.4.4.5.3; NR-17',
    workloadHours: 4,
    validityMonths: 12,
    modality: 'presencial',
    minAttendance: 100,
    active: true,
    notes: 'A NR-01 não define curso específico; carga horária e periodicidade definidas pelo empregador (item 1.7.1.2.2). Nas organizações obrigadas a constituir CIPA, as ações de capacitação, orientação e sensibilização sobre violência, assédio, igualdade e diversidade devem ocorrer no mínimo a cada 12 meses, para todos os níveis hierárquicos (item 1.4.1.1 "c"). Pode ser ministrado a distância ou semipresencial (item 1.7.9). Treinamento eventual em mudança de procedimentos ou condições de trabalho, após acidente grave ou fatal e no retorno de afastamento superior a 180 dias (item 1.7.1.2.3).',
    topics: [
      { title: 'NR-01 e o gerenciamento de riscos ocupacionais (GRO/PGR): riscos físicos, químicos, biológicos, de acidentes e ergonômicos, incluindo os fatores de risco psicossociais relacionados ao trabalho (itens 1.5.3.1.4 e 1.5.3.2.1); relação com a NR-17', hours: 0.5 },
      { title: 'Fatores de risco psicossociais: organização do trabalho, demandas e ritmo, metas, jornada e pausas, autonomia, apoio, relações interpessoais, conflitos, trabalho isolado, violência e assédio', hours: 1 },
      { title: 'Efeitos sobre a saúde e a segurança: estresse, esgotamento profissional (burnout), ansiedade, depressão e acidentes; sinais de alerta e onde buscar apoio', hours: 0.5 },
      { title: 'Identificação, avaliação e classificação dos riscos psicossociais no PGR: exigências da atividade e eficácia das medidas (item 1.5.4.4.5.3); participação e consulta aos trabalhadores e à CIPA (item 1.5.3.3); revisão da avaliação (item 1.5.4.4.6)', hours: 0.5 },
      { title: 'Medidas de prevenção na ordem de prioridade (item 1.4.1 "g"): eliminação dos fatores de risco, medidas de organização do trabalho, comunicação, gestão de metas e jornada, e acompanhamento', hours: 0.5 },
      { title: 'Prevenção e combate ao assédio sexual e às demais formas de violência no trabalho; igualdade e diversidade; regras de conduta e canal de denúncias com anonimato (item 1.4.1.1)', hours: 0.5 },
      { title: 'Atividade prática: estudo de casos e identificação de fatores psicossociais com propostas de medidas de prevenção', hours: 0.5 }
    ]
  }
];
