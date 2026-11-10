/**
 * Curso NR-06 (Equipamento de Proteção Individual – texto atualizado 2025).
 * A norma não fixa carga horária nem periodicidade: manda orientar e treinar
 * (item 6.5.1 "b") conforme a NR-01 e define as informações mínimas (6.7.2).
 * A carga horária e a divisão por tópico são sugestões editáveis.
 */
import type { TrainingCourse } from './types';

type CourseSeed = Omit<TrainingCourse, 'id' | 'companyId' | 'createdAt' | 'updatedAt' | 'serverUpdatedAt'>;

export const NR06_COURSES: Array<{ key: string } & CourseSeed> = [
  {
    key: 'nr06-epi',
    code: 'NR-06 EPI',
    name: 'NR-06 – Equipamento de Proteção Individual (EPI): Seleção, Uso, Guarda e Conservação',
    normReference: 'NR-06 (texto atualizado 2025), itens 6.5.1 "b", 6.6 e 6.7 e Anexo I; NR-01 (capacitação)',
    workloadHours: 4,
    validityMonths: 0,
    modality: 'presencial',
    minAttendance: 100,
    minGrade: 7,
    active: true,
    notes: 'A NR-06 não estabelece carga horária nem periodicidade; o treinamento segue a NR-01 (item 6.7.1). Repetir quando houver troca de EPI, mudança de riscos, de atividade ou de procedimentos, ou quando o EPI tiver características que exijam treinamento específico (item 6.7.2.1). Informações conforme o manual de instruções do fabricante ou importador (item 6.7.2).',
    topics: [
      { title: 'Conceitos: EPI e Equipamento Conjugado de Proteção Individual; Certificado de Aprovação (CA) – validade, marcações obrigatórias (fabricante ou importador, lote e número do CA) e consulta (itens 6.3, 6.4 e 6.9)', hours: 0.5 },
      { title: 'Hierarquia das medidas de prevenção (NR-01) e seleção do EPI conforme a atividade, os riscos e o PGR; participação do SESMT, dos usuários e da CIPA; óculos de sobrepor ou adaptação para quem usa lentes corretivas (itens 6.5.2 a 6.5.4)', hours: 0.5 },
      { title: 'Responsabilidades da organização (item 6.5.1) e do trabalhador (item 6.6.1); registro do fornecimento em ficha, livro ou sistema eletrônico; substituição imediata do EPI danificado ou extraviado', hours: 0.5 },
      { title: 'Tipos de EPI do Anexo I: proteção da cabeça, dos olhos e face, auditiva, respiratória, do tronco, dos membros superiores e inferiores, do corpo inteiro e contra quedas com diferença de nível', hours: 1 },
      { title: 'Informações do fornecimento (item 6.7.2): descrição e componentes, risco contra o qual protege, restrições e limitações, forma adequada de uso e ajuste, manutenção e substituição, limpeza, higienização, guarda e conservação', hours: 0.5 },
      { title: 'Prática: inspeção antes do uso, colocação, ajuste e retirada dos EPI da atividade; higienização e guarda', hours: 1 }
    ]
  }
];
