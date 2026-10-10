/**
 * Cursos cadastrados automaticamente na primeira abertura do módulo (por
 * empresa). Tudo pode ser editado: nome, norma, carga horária de cada tópico,
 * validade e regras de aprovação. A distribuição das horas por tópico é uma
 * sugestão: a norma define o conteúdo mínimo e a carga horária total.
 */
import type { TrainingCourse } from './types';
import { NR20_COURSES } from './defaultCoursesNr20';
import { NR18_COURSES } from './defaultCoursesNr18';
import { NR06_COURSES } from './defaultCoursesNr06';

type CourseSeed = Omit<TrainingCourse, 'id' | 'companyId' | 'createdAt' | 'updatedAt' | 'serverUpdatedAt'>;

export const DEFAULT_COURSES: Array<{ key: string } & CourseSeed> = [
  {
    key: 'nr10-basico',
    code: 'NR-10 BÁSICO',
    name: 'NR-10 – Segurança em Instalações e Serviços em Eletricidade (Curso Básico)',
    normReference: 'NR-10, item 10.8.8 – conteúdo mínimo do curso básico',
    workloadHours: 40,
    validityMonths: 24,
    modality: 'presencial',
    minAttendance: 100,
    minGrade: 7,
    active: true,
    notes: 'Reciclagem bienal e sempre que ocorrer troca de função, retorno de afastamento superior a três meses ou modificações significativas nas instalações (NR-10, item 10.8.8.2).',
    topics: [
      { title: 'Introdução à segurança com eletricidade', hours: 1 },
      { title: 'Riscos em instalações e serviços com eletricidade: choque elétrico (mecanismos e efeitos), arcos elétricos, queimaduras, quedas e campos eletromagnéticos', hours: 4 },
      { title: 'Técnicas de análise de risco', hours: 2 },
      { title: 'Medidas de controle do risco elétrico: desenergização, aterramento (funcional, de proteção e temporário), equipotencialização, seccionamento automático, dispositivos DR, extrabaixa tensão, barreiras e invólucros, bloqueios e impedimentos, obstáculos e anteparos, isolamento das partes vivas, isolação dupla ou reforçada, colocação fora de alcance e separação elétrica', hours: 6 },
      { title: 'Normas técnicas brasileiras – NBR 5410, NBR 14039 e outras', hours: 2 },
      { title: 'Regulamentações do MTE: NRs, NR-10, qualificação, habilitação, capacitação e autorização', hours: 2 },
      { title: 'Equipamentos de proteção coletiva', hours: 2 },
      { title: 'Equipamentos de proteção individual', hours: 2 },
      { title: 'Rotinas de trabalho e procedimentos: instalações desenergizadas, liberação para serviços, sinalização, inspeções de áreas, serviços, ferramental e equipamentos', hours: 3 },
      { title: 'Documentação de instalações elétricas', hours: 1 },
      { title: 'Riscos adicionais: altura, ambientes confinados, áreas classificadas, umidade e condições atmosféricas', hours: 2 },
      { title: 'Proteção e combate a incêndios: noções básicas, medidas preventivas, métodos de extinção e prática', hours: 4 },
      { title: 'Acidentes de origem elétrica: causas diretas e indiretas, discussão de casos', hours: 2 },
      { title: 'Primeiros socorros: lesões, priorização do atendimento, respiração artificial, massagem cardíaca, remoção e transporte de acidentados, práticas', hours: 6 },
      { title: 'Responsabilidades', hours: 1 }
    ]
  },
  {
    key: 'nr10-sep',
    code: 'NR-10 SEP',
    name: 'NR-10 – Curso Complementar: Segurança no Sistema Elétrico de Potência (SEP) e em suas Proximidades',
    normReference: 'NR-10, item 10.8.8 – conteúdo mínimo do curso complementar (SEP)',
    workloadHours: 40,
    validityMonths: 24,
    modality: 'presencial',
    minAttendance: 100,
    minGrade: 7,
    active: true,
    prerequisite: 'Conclusão do curso básico NR-10 (40 h) com aproveitamento satisfatório.',
    notes: 'Reciclagem bienal e nas situações previstas no item 10.8.8.2 da NR-10.',
    topics: [
      { title: 'Organização do Sistema Elétrico de Potência – SEP', hours: 2 },
      { title: 'Organização do trabalho: programação e planejamento dos serviços, trabalho em equipe, prontuário e cadastro das instalações, métodos de trabalho e comunicação', hours: 3 },
      { title: 'Aspectos comportamentais', hours: 2 },
      { title: 'Condições impeditivas para serviços', hours: 2 },
      { title: 'Riscos típicos no SEP e sua prevenção: proximidade e contato com partes energizadas, indução, descargas atmosféricas, estática, campos elétricos e magnéticos, comunicação e identificação, trabalhos em altura, máquinas e equipamentos especiais', hours: 3 },
      { title: 'Técnicas de análise de risco no SEP', hours: 3 },
      { title: 'Procedimentos de trabalho – análise e discussão', hours: 3 },
      { title: 'Técnicas de trabalho sob tensão: em linha viva, ao potencial, em áreas internas, a distância, trabalhos noturnos e ambientes subterrâneos', hours: 3 },
      { title: 'Equipamentos e ferramentas de trabalho: escolha, uso, conservação, verificação e ensaios', hours: 3 },
      { title: 'Sistemas de proteção coletiva', hours: 2 },
      { title: 'Equipamentos de proteção individual', hours: 2 },
      { title: 'Posturas e vestuários de trabalho', hours: 1 },
      { title: 'Segurança com veículos e transporte de pessoas, materiais e equipamentos', hours: 2 },
      { title: 'Sinalização e isolamento de áreas de trabalho', hours: 2 },
      { title: 'Liberação de instalação para serviço e para operação e uso', hours: 2 },
      { title: 'Treinamento em técnicas de remoção, atendimento e transporte de acidentados', hours: 2 },
      { title: 'Acidentes típicos: análise, discussão e medidas de proteção', hours: 2 },
      { title: 'Responsabilidades', hours: 1 }
    ]
  },
  {
    // NR-35 atualizada (Portaria MTE nº 1.259/2026): item 35.4.2.1, 35.4.5 e Anexo III (escadas)
    key: 'nr35',
    code: 'NR-35',
    name: 'NR-35 – Trabalho em Altura',
    normReference: 'NR-35 (texto atualizado – Portaria MTE nº 1.259/2026), item 35.4.2.1 – treinamento inicial (mínimo 8 h, teórico e prático, presencial) e Anexo III, item 4.2.1.1 (escadas de uso individual)',
    workloadHours: 8,
    validityMonths: 24,
    modality: 'presencial',
    minAttendance: 100,
    minGrade: 7,
    active: true,
    notes: 'Treinamento teórico e prático, obrigatoriamente presencial (item 35.4.5), realizado antes de o trabalhador iniciar a atividade. Treinamento periódico a cada dois anos, com mínimo de 8 h e conteúdo definido pelo empregador (item 35.4.2.2); eventual conforme a NR-01. Instrutores com comprovada proficiência, sob a responsabilidade de profissional qualificado ou legalmente habilitado em segurança no trabalho (item 35.4.3). O certificado comprova a capacitação; a autorização para trabalho em altura depende também da aptidão no ASO e da autorização formal da organização (item 35.4.1).',
    topics: [
      { title: 'Normas e regulamentos aplicáveis ao trabalho em altura (NR-35 e seus anexos, NR-01); hierarquia das medidas de prevenção (item 35.5.2)', hours: 1 },
      { title: 'Análise de Risco (AR) e condições impeditivas', hours: 1 },
      { title: 'Riscos potenciais inerentes ao trabalho em altura e medidas de prevenção e controle, inclusive riscos adicionais', hours: 1 },
      { title: 'Sistemas, equipamentos e procedimentos de proteção coletiva', hours: 0.5 },
      { title: 'EPI para trabalho em altura: seleção, inspeção, conservação e limitação de uso; sistemas de proteção individual contra quedas (SPIQ), ancoragens, fator de queda e zona livre de queda', hours: 1 },
      { title: 'Acidentes típicos em trabalhos em altura', hours: 0.5 },
      { title: 'Condutas em situações de emergência, incluindo noções básicas de técnicas de resgate e de primeiros socorros', hours: 0.5 },
      { title: 'Utilização segura de escada de uso individual (Anexo III, item 4.2.1.1)', hours: 0.5 },
      { title: 'Prática: inspeção, ajuste e uso do cinturão tipo paraquedista, talabartes e trava-quedas; conexão a ancoragens e linhas de vida; uso seguro de escadas', hours: 1.5 },
      { title: 'Prática: noções básicas de resgate e primeiros socorros', hours: 0.5 }
    ]
  },
  {
    key: 'nr35-reciclagem',
    code: 'NR-35 RECICLAGEM',
    name: 'Reciclagem NR-35 – Trabalho em Altura (Treinamento Periódico)',
    normReference: 'NR-35 (texto atualizado – Portaria MTE nº 1.259/2026), item 35.4.2.2 – treinamento periódico (a cada dois anos, mínimo 8 h, presencial)',
    workloadHours: 8,
    validityMonths: 24,
    modality: 'presencial',
    minAttendance: 100,
    minGrade: 7,
    active: true,
    prerequisite: 'Treinamento inicial NR-35 (8 h).',
    notes: 'Treinamento periódico a cada dois anos, com carga horária mínima de 8 h e conteúdo definido pelo empregador (item 35.4.2.2), teórico e prático (item 35.4.2) e presencial (item 35.4.5). A autorização para trabalho em altura depende também da aptidão no ASO e da autorização formal da organização (item 35.4.1).',
    topics: [
      { title: 'Revisão: normas e regulamentos, Análise de Risco, condições impeditivas e hierarquia das medidas de prevenção', hours: 1.5 },
      { title: 'Revisão: riscos potenciais, proteção coletiva, SPIQ e EPI para trabalho em altura', hours: 1.5 },
      { title: 'Análise de acidentes e incidentes; mudanças em procedimentos, equipamentos e na norma', hours: 1 },
      { title: 'Utilização segura de escada de uso individual', hours: 0.5 },
      { title: 'Condutas em emergência, resgate e primeiros socorros', hours: 0.5 },
      { title: 'Prática: inspeção e uso dos EPI e dos sistemas de ancoragem', hours: 2 },
      { title: 'Prática: simulado de resgate', hours: 1 }
    ]
  },
  {
    key: 'epi-epc-isolantes',
    code: 'EPI/EPC ISOLANTES',
    name: 'Uso, Inspeção e Cuidados com EPI e EPC Isolantes',
    normReference: 'NR-6 e NR-10 – treinamento sobre uso adequado, guarda e conservação',
    workloadHours: 4,
    validityMonths: 24,
    modality: 'presencial',
    minAttendance: 75,
    minGrade: 7,
    active: true,
    notes: 'Luvas, mangas, tapetes, mantas, ferramentas isoladas e demais equipamentos isolantes.',
    topics: [
      { title: 'Classes de tensão e seleção de EPI/EPC isolantes (luvas, mangas, tapetes, mantas e ferramentas isoladas)', hours: 1 },
      { title: 'Inspeção visual antes do uso e teste de inflação das luvas', hours: 1 },
      { title: 'Uso correto: luvas de cobertura, limites de uso e incompatibilidades', hours: 0.5 },
      { title: 'Guarda, transporte, limpeza e conservação', hours: 0.5 },
      { title: 'Ensaios dielétricos periódicos, rastreabilidade e descarte', hours: 0.5 },
      { title: 'Atividade prática de inspeção', hours: 0.5 }
    ]
  },
  {
    // NR-33 (Portaria MTP nº 1.690/2022): Anexo III, Quadro 1 e item 2.1 b
    key: 'nr33-vigia-ta',
    code: 'NR-33 VIGIA/TA',
    name: 'NR-33 – Segurança e Saúde nos Trabalhos em Espaços Confinados: Trabalhador Autorizado e Vigia',
    normReference: 'NR-33 (Portaria MTP nº 1.690/2022), item 33.6 e Anexo III – Quadro 1 e item 2.1 "b" (vigia e trabalhador autorizado)',
    workloadHours: 16,
    validityMonths: 12,
    modality: 'presencial',
    minAttendance: 100,
    minGrade: 7,
    active: true,
    notes: 'Treinamento inicial de 16 h com, no mínimo, 50% de prática (Anexo III, item 1.2). Treinamento periódico anual de 8 h; treinamento eventual conforme a NR-01 ou quando houver desvios na utilização de equipamentos ou nos procedimentos de entrada. Conhecimentos avaliados (item 33.6.3). Conforme o item 33.6.5, informar no certificado o tipo de espaço confinado e as atividades desenvolvidas, com a anuência do responsável técnico (item 33.3.2).',
    topics: [
      { title: 'Definições: espaço confinado, Permissão de Entrada e Trabalho (PET), supervisor de entrada, vigia, trabalhador autorizado e equipe de emergência e salvamento', hours: 1 },
      { title: 'Reconhecimento, avaliação e controle de riscos: atmosferas perigosas (deficiência ou enriquecimento de oxigênio, gases inflamáveis e tóxicos), riscos físicos, químicos, biológicos, mecânicos e de afogamento; ventilação, isolamento, bloqueio e etiquetagem', hours: 3 },
      { title: 'Funcionamento dos equipamentos utilizados: detectores de gases (teste de resposta, ajuste, sondagem inicial e monitoramento contínuo), ventilação e exaustão, EPI, proteção respiratória, iluminação e comunicação', hours: 2 },
      { title: 'Procedimentos e utilização da PET: emissão, preenchimento, validade, encerramento e cancelamento; atribuições do vigia e do trabalhador autorizado', hours: 1 },
      { title: 'Noções de resgate e primeiros socorros', hours: 1 },
      { title: 'Prática: uso dos equipamentos – avaliação atmosférica com detector de gases, ventilação/exaustão, EPI e proteção respiratória', hours: 3 },
      { title: 'Prática: simulação de entrada com PET, vigilância e comunicação entre vigia e trabalhadores autorizados', hours: 3 },
      { title: 'Prática: simulado de resgate e primeiros socorros', hours: 2 }
    ]
  },
  {
    // NR-33 (2022): treinamento periódico anual de 8 h; conteúdo definido pela organização (Anexo III, item 2.3)
    key: 'nr33-vigia-ta-reciclagem',
    code: 'NR-33 VIGIA/TA RECICLAGEM',
    name: 'Reciclagem NR-33 – Trabalhador Autorizado e Vigia em Espaços Confinados (Treinamento Periódico)',
    normReference: 'NR-33 (Portaria MTP nº 1.690/2022), item 33.6.2 e Anexo III – Quadro 1 (periódico: 8 h/anual) e item 2.3',
    workloadHours: 8,
    validityMonths: 12,
    modality: 'presencial',
    minAttendance: 100,
    minGrade: 7,
    active: true,
    prerequisite: 'Treinamento inicial NR-33 de Trabalhador Autorizado e Vigia (16 h).',
    notes: 'Treinamento periódico anual com, no mínimo, 50% de prática (Anexo III, item 1.2). Conteúdo definido pela organização, com os princípios básicos de segurança compatíveis com o tipo de espaço confinado e as atividades desenvolvidas (Anexo III, item 2.3). Informar no certificado o tipo de espaço confinado e as atividades desenvolvidas, com a anuência do responsável técnico (item 33.6.5).',
    topics: [
      { title: 'Revisão: definições, riscos em espaços confinados e medidas de controle', hours: 2 },
      { title: 'Revisão dos procedimentos de entrada e da PET; análise de ocorrências e desvios', hours: 1 },
      { title: 'Atualização sobre os equipamentos: detectores de gases, ventilação, EPI e proteção respiratória', hours: 1 },
      { title: 'Prática: avaliação atmosférica e uso dos equipamentos', hours: 2 },
      { title: 'Prática: simulado de entrada com PET, vigilância, comunicação, resgate e primeiros socorros', hours: 2 }
    ]
  },
  {
    // NR-33 (2022): Anexo III, Quadro 1 (40 h) e item 2.1 "a"
    key: 'nr33-supervisor',
    code: 'NR-33 SUPERVISOR',
    name: 'NR-33 – Segurança e Saúde nos Trabalhos em Espaços Confinados: Supervisor de Entrada',
    normReference: 'NR-33 (Portaria MTP nº 1.690/2022), item 33.6 e Anexo III – Quadro 1 e item 2.1 "a" (supervisor de entrada)',
    workloadHours: 40,
    validityMonths: 12,
    modality: 'presencial',
    minAttendance: 100,
    minGrade: 7,
    active: true,
    notes: 'Treinamento inicial de 40 h com, no mínimo, 50% de prática (Anexo III, item 1.2). Treinamento periódico anual de 8 h; treinamento eventual conforme a NR-01 ou quando houver desvios na utilização de equipamentos ou nos procedimentos de entrada. Conhecimentos avaliados (item 33.6.3). Conforme o item 33.6.5, informar no certificado o tipo de espaço confinado e as atividades desenvolvidas, com a anuência do responsável técnico (item 33.3.2).',
    topics: [
      { title: 'Definições', hours: 1 },
      { title: 'Identificação dos espaços confinados', hours: 2 },
      { title: 'Reconhecimento, avaliação e controle de riscos', hours: 4 },
      { title: 'Funcionamento de equipamentos utilizados', hours: 2 },
      { title: 'Procedimentos e utilização da PET', hours: 2 },
      { title: 'Critérios de indicação e uso de equipamentos para controle de riscos', hours: 2 },
      { title: 'Conhecimento sobre práticas seguras em espaços confinados', hours: 2 },
      { title: 'Legislação de segurança e saúde no trabalho', hours: 1 },
      { title: 'Programa de Proteção Respiratória', hours: 1 },
      { title: 'Área classificada', hours: 1 },
      { title: 'Noções de resgate e primeiros socorros', hours: 1 },
      { title: 'Operações de salvamento', hours: 1 },
      { title: 'Prática: identificação de espaços confinados e avaliação e controle de riscos no local', hours: 4 },
      { title: 'Prática: detectores de gases, ventilação e exaustão, EPI e proteção respiratória', hours: 5 },
      { title: 'Prática: emissão, operação e encerramento da PET; coordenação da entrada com vigia e trabalhadores autorizados', hours: 6 },
      { title: 'Prática: simulados de resgate, salvamento e primeiros socorros', hours: 5 }
    ]
  },
  {
    key: 'nr33-supervisor-reciclagem',
    code: 'NR-33 SUPERVISOR RECICLAGEM',
    name: 'Reciclagem NR-33 – Supervisor de Entrada em Espaços Confinados (Treinamento Periódico)',
    normReference: 'NR-33 (Portaria MTP nº 1.690/2022), item 33.6.2 e Anexo III – Quadro 1 (periódico: 8 h/anual) e item 2.3',
    workloadHours: 8,
    validityMonths: 12,
    modality: 'presencial',
    minAttendance: 100,
    minGrade: 7,
    active: true,
    prerequisite: 'Treinamento inicial NR-33 de Supervisor de Entrada (40 h).',
    notes: 'Treinamento periódico anual com, no mínimo, 50% de prática (Anexo III, item 1.2). Conteúdo definido pela organização, com os princípios básicos de segurança compatíveis com o tipo de espaço confinado e as atividades desenvolvidas (Anexo III, item 2.3). Informar no certificado o tipo de espaço confinado e as atividades desenvolvidas, com a anuência do responsável técnico (item 33.6.5).',
    topics: [
      { title: 'Revisão: identificação dos espaços confinados, riscos e medidas de controle', hours: 2 },
      { title: 'Revisão da PET e da legislação; análise de ocorrências e desvios', hours: 1 },
      { title: 'Atualização: equipamentos, proteção respiratória e área classificada', hours: 1 },
      { title: 'Prática: avaliação atmosférica e controle de riscos', hours: 2 },
      { title: 'Prática: operação da PET e simulado de entrada e resgate', hours: 2 }
    ]
  },
  {
    // NR-33 (2022): Anexo III, Quadro 1 (24 h ou 32 h) e item 2.1 "c"
    key: 'nr33-resgate',
    code: 'NR-33 RESGATE',
    name: 'NR-33 – Equipe de Emergência e Salvamento em Espaços Confinados',
    normReference: 'NR-33 (Portaria MTP nº 1.690/2022), item 33.6 e Anexo III – Quadro 1 e item 2.1 "c"; ABNT NBR 16710 (resgate técnico industrial) e ABNT NBR 16577',
    workloadHours: 24,
    validityMonths: 24,
    modality: 'presencial',
    minAttendance: 100,
    minGrade: 7,
    active: true,
    notes: 'Carga horária de 24 h ou 32 h conforme o plano de emergência e o nível profissional do resgatista (ajuste a carga e os tópicos para 32 h quando aplicável), com, no mínimo, 50% de prática (Anexo III, item 1.2). Conteúdo conforme as normas técnicas nacionais de resgate técnico em espaços confinados (Anexo III, item 2.1 "c"). Treinamento periódico bienal com a mesma carga horária. Informar no certificado o tipo de espaço confinado e as atividades desenvolvidas, com a anuência do responsável técnico (item 33.6.5).',
    topics: [
      { title: 'Legislação e normas técnicas de resgate em espaços confinados (ABNT NBR 16710 e NBR 16577); plano de emergência e salvamento', hours: 2 },
      { title: 'Riscos em espaços confinados e avaliação da cena de resgate', hours: 2 },
      { title: 'Equipamentos de resgate: ancoragens, cordas, tripé, guinchos, descensores, macas e EPI', hours: 3 },
      { title: 'Técnicas de acesso, estabilização, imobilização e remoção de vítimas', hours: 2 },
      { title: 'Proteção respiratória e atmosferas perigosas durante o resgate', hours: 1 },
      { title: 'Primeiros socorros e suporte básico de vida', hours: 2 },
      { title: 'Prática: montagem de sistemas de ancoragem, tripé e vantagem mecânica', hours: 4 },
      { title: 'Prática: simulados de resgate vertical e horizontal em espaço confinado', hours: 6 },
      { title: 'Prática: primeiros socorros e transporte da vítima', hours: 2 }
    ]
  },
  {
    key: 'nr33-resgate-reciclagem',
    code: 'NR-33 RESGATE RECICLAGEM',
    name: 'Reciclagem NR-33 – Equipe de Emergência e Salvamento em Espaços Confinados (Treinamento Periódico)',
    normReference: 'NR-33 (Portaria MTP nº 1.690/2022), item 33.6.2 e Anexo III – Quadro 1 (periódico bienal: 24 h ou 32 h) e item 2.3; ABNT NBR 16710',
    workloadHours: 24,
    validityMonths: 24,
    modality: 'presencial',
    minAttendance: 100,
    minGrade: 7,
    active: true,
    prerequisite: 'Treinamento inicial NR-33 de Equipe de Emergência e Salvamento.',
    notes: 'Treinamento periódico bienal de 24 h ou 32 h conforme o plano de emergência e o nível profissional do resgatista, com, no mínimo, 50% de prática (Anexo III, item 1.2). Informar no certificado o tipo de espaço confinado e as atividades desenvolvidas, com a anuência do responsável técnico (item 33.6.5).',
    topics: [
      { title: 'Revisão: plano de emergência, normas técnicas de resgate e riscos em espaços confinados', hours: 2 },
      { title: 'Revisão: equipamentos de resgate, inspeção e conservação', hours: 2 },
      { title: 'Revisão: técnicas de resgate e primeiros socorros; lições de simulados e ocorrências', hours: 4 },
      { title: 'Prática: sistemas de ancoragem e vantagem mecânica', hours: 4 },
      { title: 'Prática: simulados de resgate em espaço confinado', hours: 10 },
      { title: 'Prática: primeiros socorros e transporte da vítima', hours: 2 }
    ]
  },
  // NR-20: iniciação, básico, intermediário, avançados, específico, complementações e atualizações
  ...NR20_COURSES,
  // NR-18: instalação, montagem, desmontagem e manutenção de elevadores de passageiros
  ...NR18_COURSES,
  // NR-06: seleção, uso, guarda e conservação de EPI
  ...NR06_COURSES
];
