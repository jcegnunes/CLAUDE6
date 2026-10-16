/**
 * Cursos NR-20 (Inflamáveis e Combustíveis – texto atualizado 2025), Anexo I:
 * Tabela 1 (curso conforme a atividade e a classe da instalação), Tabela 2
 * (atualização) e conteúdo programático. A divisão das horas por tópico é uma
 * sugestão: a norma define o conteúdo e a carga horária total.
 */
import type { TrainingCourse } from './types';

type CourseSeed = Omit<TrainingCourse, 'id' | 'companyId' | 'createdAt' | 'updatedAt' | 'serverUpdatedAt'>;
type Seed = { key: string } & CourseSeed;

const NR20 = 'NR-20 (texto atualizado 2025), item 20.12 e Anexo I';

// Conteúdo programático teórico do Anexo I (itens numerados como na norma)
const T = {
  inflamaveis: 'Inflamáveis: características, propriedades, perigos e riscos',
  controles: 'Controles coletivo e individual para trabalhos com inflamáveis',
  ignicao: 'Fontes de ignição e seu controle',
  incendio: 'Proteção contra incêndio com inflamáveis',
  emergenciaBasica: 'Procedimentos básicos em situações de emergência com inflamáveis',
  emergencia: 'Procedimentos em situações de emergência com inflamáveis',
  nr20: 'Estudo da Norma Regulamentadora nº 20',
  app: 'Análise Preliminar de Perigos/Riscos: conceitos e exercícios práticos',
  analiseRiscos: 'Metodologias de Análise de Riscos: conceitos e exercícios práticos',
  pt: 'Permissão para Trabalho com Inflamáveis',
  acidentes: 'Acidentes com inflamáveis: análise de causas e medidas preventivas',
  planoEmergencia: 'Planejamento de Resposta a emergências com Inflamáveis',
  processo: 'Noções básicas de segurança de processo da instalação',
  mudancas: 'Noções básicas de gestão de mudanças'
};
const PRATICA = 'Prática: conhecimentos e utilização dos sistemas de segurança contra incêndio com inflamáveis existentes na instalação';

const AVALIACAO = 'Certificado somente para quem obtiver aproveitamento satisfatório na avaliação (item 20.12.13); material didático aos participantes (item 20.12.14).';
const ATUALIZACAO_EVENTUAL = 'Atualização também quando o histórico de acidentes/incidentes exigir, em até 30 dias após modificação significativa, em até 45 dias após ferimentos por explosão ou queimaduras de 2º/3º grau com internação e em até 90 dias após morte de trabalhador (item 20.12.9.1).';

const base = { modality: 'presencial' as const, minAttendance: 100, minGrade: 7, active: true };
const topics = (list: Array<[string, number]>) => list.map(([title, hours]) => ({ title, hours }));
const periodicidade = (m: number) => (m === 12 ? 'anual' : m === 24 ? 'bienal' : 'trienal');

const CLASSES = ['I', 'II', 'III'] as const;

/** Curso Básico: atividade específica, pontual e de curta duração (Classe I 4 h, II 6 h, III 8 h). */
const basico: Seed[] = ([
  ['I', 4, [[T.inflamaveis, 0.5], [T.controles, 0.5], [T.ignicao, 0.5], [T.incendio, 0.5], [T.emergenciaBasica, 0.5], [PRATICA, 1.5]]],
  ['II', 6, [[T.inflamaveis, 1], [T.controles, 0.5], [T.ignicao, 0.5], [T.incendio, 1], [T.emergenciaBasica, 1], [PRATICA, 2]]],
  ['III', 8, [[T.inflamaveis, 1], [T.controles, 1], [T.ignicao, 1], [T.incendio, 1], [T.emergenciaBasica, 1], [PRATICA, 3]]]
] as Array<[typeof CLASSES[number], number, Array<[string, number]>]>).map(([cl, h, t]) => ({
  ...base,
  key: `nr20-basico-classe-${cl.toLowerCase()}`,
  code: `NR-20 BÁSICO CL. ${cl}`,
  name: `NR-20 – Curso Básico de Segurança e Saúde no Trabalho com Inflamáveis e Combustíveis (Instalação Classe ${cl})`,
  normReference: `${NR20} – Tabela 1 (atividade específica, pontual e de curta duração; instalação Classe ${cl}) e conteúdo programático "b"`,
  workloadHours: h,
  validityMonths: 36,
  notes: `Atualização trienal de 4 h (Anexo I, Tabela 2). ${ATUALIZACAO_EVENTUAL} ${AVALIACAO}`,
  topics: topics(t)
}));

/** Curso Intermediário: manutenção e inspeção (I 12 h, II 14 h, III 16 h) e operação/emergência na Classe I. */
const intermediario: Seed[] = ([
  ['I', 12, 36, [[T.inflamaveis, 1], [T.controles, 1], [T.ignicao, 1], [T.incendio, 1], [T.emergencia, 1], [T.nr20, 1.5], [T.app, 1.5], [T.pt, 1], [PRATICA, 3]]],
  ['II', 14, 24, [[T.inflamaveis, 1], [T.controles, 1], [T.ignicao, 1], [T.incendio, 1], [T.emergencia, 1], [T.nr20, 2], [T.app, 2], [T.pt, 1], [PRATICA, 4]]],
  ['III', 16, 24, [[T.inflamaveis, 1], [T.controles, 1], [T.ignicao, 1], [T.incendio, 1.5], [T.emergencia, 1.5], [T.nr20, 2], [T.app, 2], [T.pt, 1], [PRATICA, 5]]]
] as Array<[typeof CLASSES[number], number, number, Array<[string, number]>]>).map(([cl, h, val, t]) => ({
  ...base,
  key: `nr20-intermediario-classe-${cl.toLowerCase()}`,
  code: `NR-20 INTERMEDIÁRIO CL. ${cl}`,
  name: `NR-20 – Curso Intermediário de Segurança e Saúde no Trabalho com Inflamáveis e Combustíveis (Instalação Classe ${cl})`,
  normReference: `${NR20} – Tabela 1 (manutenção e inspeção${cl === 'I' ? '; operação e atendimento a emergências' : ''}; instalação Classe ${cl}) e conteúdo programático "c"`,
  workloadHours: h,
  validityMonths: val,
  notes: `Atualização ${periodicidade(val)} de 4 h (Anexo I, Tabela 2). ${ATUALIZACAO_EVENTUAL} ${AVALIACAO}`,
  topics: topics(t)
}));

const avancados: Seed[] = [
  {
    ...base,
    key: 'nr20-avancado-1',
    code: 'NR-20 AVANÇADO I',
    name: 'NR-20 – Curso Avançado I de Segurança e Saúde no Trabalho com Inflamáveis e Combustíveis (Instalação Classe II)',
    normReference: `${NR20} – Tabela 1 (operação e atendimento a emergências; instalação Classe II) e conteúdo programático "d"`,
    workloadHours: 20,
    validityMonths: 24,
    notes: `Atualização bienal de 4 h (Anexo I, Tabela 2). Curso com profissional habilitado como responsável técnico (item 20.12.12). ${ATUALIZACAO_EVENTUAL} ${AVALIACAO}`,
    topics: topics([[T.inflamaveis, 1], [T.controles, 1], [T.ignicao, 1], [T.incendio, 1.5], [T.emergencia, 1.5], [T.nr20, 2], [T.analiseRiscos, 2], [T.pt, 1.5], [T.acidentes, 1.5], [T.planoEmergencia, 2], [PRATICA, 5]])
  },
  {
    ...base,
    key: 'nr20-avancado-2',
    code: 'NR-20 AVANÇADO II',
    name: 'NR-20 – Curso Avançado II de Segurança e Saúde no Trabalho com Inflamáveis e Combustíveis (Instalação Classe III)',
    normReference: `${NR20} – Tabela 1 (operação e atendimento a emergências; instalação Classe III) e conteúdo programático "e"`,
    workloadHours: 32,
    validityMonths: 12,
    notes: `Atualização anual de 4 h (Anexo I, Tabela 2). Curso com profissional habilitado como responsável técnico (item 20.12.12). ${ATUALIZACAO_EVENTUAL} ${AVALIACAO}`,
    topics: topics([[T.inflamaveis, 1.5], [T.controles, 1.5], [T.ignicao, 1.5], [T.incendio, 2], [T.emergencia, 2], [T.nr20, 2], [T.analiseRiscos, 3], [T.pt, 2], [T.acidentes, 2], [T.planoEmergencia, 3], [T.processo, 2], [T.mudancas, 2], [PRATICA, 7.5]])
  }
];

/** Curso Específico: segurança e saúde no trabalho (Classe II 14 h, III 16 h); sem parte prática na norma. */
const especifico: Seed[] = ([
  ['II', 14, [[T.nr20, 3], [T.analiseRiscos, 4], [T.pt, 2], [T.acidentes, 2.5], [T.planoEmergencia, 2.5]]],
  ['III', 16, [[T.nr20, 3], [T.analiseRiscos, 4], [T.pt, 3], [T.acidentes, 3], [T.planoEmergencia, 3]]]
] as Array<['II' | 'III', number, Array<[string, number]>]>).map(([cl, h, t]) => ({
  ...base,
  key: `nr20-especifico-classe-${cl.toLowerCase()}`,
  code: `NR-20 ESPECÍFICO CL. ${cl}`,
  name: `NR-20 – Curso Específico para Profissionais de Segurança e Saúde no Trabalho (Instalação Classe ${cl})`,
  normReference: `${NR20} – Tabela 1 (segurança e saúde no trabalho; instalação Classe ${cl}) e conteúdo programático "f"`,
  workloadHours: h,
  validityMonths: 0,
  notes: `A Tabela 2 do Anexo I não prevê atualização periódica para o curso Específico. ${ATUALIZACAO_EVENTUAL} Curso com profissional habilitado como responsável técnico (item 20.12.12). ${AVALIACAO}`,
  topics: topics(t)
}));

const iniciacao: Seed = {
  ...base,
  key: 'nr20-iniciacao',
  code: 'NR-20 INICIAÇÃO',
  name: 'NR-20 – Curso de Iniciação sobre Inflamáveis e Combustíveis',
  normReference: `${NR20} – itens 20.12.3 "a" e 20.12.5; conteúdo programático "a"`,
  workloadHours: 3,
  validityMonths: 0,
  notes: `Para quem adentra a área ou local com inflamáveis e combustíveis (instalações Classes I, II ou III), mas não mantém contato direto com o processo ou processamento (item 20.12.5). ${ATUALIZACAO_EVENTUAL} ${AVALIACAO}`,
  topics: topics([[T.inflamaveis, 1], [T.controles, 0.5], [T.ignicao, 0.5], [T.emergenciaBasica, 1]])
};

/** Complementações de 8 h (itens 20.12.6 a 20.12.8), incluindo a parte prática. */
const complementacoes: Seed[] = [
  {
    ...base,
    key: 'nr20-complementacao-intermediario',
    code: 'NR-20 COMPL. INTERMEDIÁRIO',
    name: 'NR-20 – Complementação do Curso Básico para o Curso Intermediário',
    normReference: `${NR20} – item 20.12.6 (itens 6, 7 e 8 do curso Intermediário, incluindo a parte prática)`,
    workloadHours: 8,
    validityMonths: 24,
    prerequisite: 'Curso Básico NR-20 concluído.',
    notes: `Validade conforme o curso Intermediário da classe da instalação: trienal na Classe I e bienal nas Classes II e III (Anexo I, Tabela 2); ajuste se necessário. ${AVALIACAO}`,
    topics: topics([[T.nr20, 2], [T.app, 3], [T.pt, 1.5], [PRATICA, 1.5]])
  },
  {
    ...base,
    key: 'nr20-complementacao-avancado-1',
    code: 'NR-20 COMPL. AVANÇADO I',
    name: 'NR-20 – Complementação do Curso Intermediário para o Curso Avançado I',
    normReference: `${NR20} – item 20.12.7 (itens 9 e 10 do curso Avançado I, incluindo a parte prática)`,
    workloadHours: 8,
    validityMonths: 24,
    prerequisite: 'Curso Intermediário NR-20 concluído.',
    notes: `Atualização bienal de 4 h, como no curso Avançado I (Anexo I, Tabela 2). ${AVALIACAO}`,
    topics: topics([[T.acidentes, 3], [T.planoEmergencia, 3], [PRATICA, 2]])
  },
  {
    ...base,
    key: 'nr20-complementacao-avancado-2',
    code: 'NR-20 COMPL. AVANÇADO II',
    name: 'NR-20 – Complementação do Curso Avançado I para o Curso Avançado II',
    normReference: `${NR20} – item 20.12.8 (itens 11 e 12 do curso Avançado II, incluindo a parte prática)`,
    workloadHours: 8,
    validityMonths: 12,
    prerequisite: 'Curso Avançado I NR-20 concluído.',
    notes: `Atualização anual de 4 h, como no curso Avançado II (Anexo I, Tabela 2). ${AVALIACAO}`,
    topics: topics([[T.processo, 3], [T.mudancas, 3], [PRATICA, 2]])
  }
];

/** Cursos de Atualização de 4 h (item 20.12.9 e Anexo I, Tabela 2); conteúdo definido pelo empregador. */
const atualizacao = (key: string, code: string, curso: string, validityMonths: number, aplicacao: string): Seed => ({
  ...base,
  key,
  code,
  name: `NR-20 – Atualização do Curso ${curso}`,
  normReference: `${NR20} – item 20.12.9 e Tabela 2 (${curso}: ${periodicidade(validityMonths)}, 4 h)`,
  workloadHours: 4,
  validityMonths,
  prerequisite: `Curso ${curso} NR-20 concluído${aplicacao}.`,
  notes: `Conteúdo estabelecido pelo empregador (item 20.12.9). ${ATUALIZACAO_EVENTUAL} ${AVALIACAO}`,
  topics: topics([
    ['Revisão: inflamáveis, fontes de ignição, controles coletivo e individual e procedimentos de emergência', 1.5],
    ['Acidentes e incidentes, modificações na instalação e lições aprendidas', 1],
    [PRATICA, 1.5]
  ])
});

const atualizacoes: Seed[] = [
  atualizacao('nr20-atualizacao-basico', 'NR-20 ATUALIZAÇÃO BÁSICO', 'Básico', 36, ''),
  atualizacao('nr20-atualizacao-intermediario-classe-i', 'NR-20 ATUALIZAÇÃO INTERMEDIÁRIO CL. I', 'Intermediário', 36, ' (instalação Classe I)'),
  atualizacao('nr20-atualizacao-intermediario-classe-ii-iii', 'NR-20 ATUALIZAÇÃO INTERMEDIÁRIO CL. II/III', 'Intermediário', 24, ' (instalação Classe II ou III)'),
  atualizacao('nr20-atualizacao-avancado-1', 'NR-20 ATUALIZAÇÃO AVANÇADO I', 'Avançado I', 24, ''),
  atualizacao('nr20-atualizacao-avancado-2', 'NR-20 ATUALIZAÇÃO AVANÇADO II', 'Avançado II', 12, '')
];

export const NR20_COURSES: Seed[] = [iniciacao, ...basico, ...intermediario, ...avancados, ...especifico, ...complementacoes, ...atualizacoes];
