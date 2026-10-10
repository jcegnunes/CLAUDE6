/**
 * Cursos NR-12 (texto atualizado 2022): segurança em máquinas e equipamentos
 * na instalação e manutenção de elevadores, com uso de ferramentas manuais
 * elétricas (furadeira, lixadeira) e talhas. Carga horária definida pelo
 * empregador (item 12.16.3 "c"); conteúdo mínimo do Anexo II e
 * procedimentos de manutenção do item 12.11. Horas por tópico: sugestão.
 */
import type { TrainingCourse } from './types';

type CourseSeed = Omit<TrainingCourse, 'id' | 'companyId' | 'createdAt' | 'updatedAt' | 'serverUpdatedAt'>;

const topics = (list: Array<[string, number]>) => list.map(([title, hours]) => ({ title, hours }));

const REGRAS = 'Capacitação antes de o trabalhador assumir a função, sem ônus e na jornada de trabalho, com carga horária mínima definida pelo empregador (item 12.16.3); ministrada por profissionais qualificados, sob supervisão de profissional legalmente habilitado, responsável pelo conteúdo, carga horária, instrutores e avaliação (item 12.16.3 "e"). A capacitação só vale para o empregador que a realizou (item 12.16.6). Manter material didático, lista de presença, currículo dos instrutores e avaliações (item 12.16.5).';

export const NR12_COURSES: Array<{ key: string } & CourseSeed> = [
  {
    key: 'nr12-elevadores',
    code: 'NR-12 ELEVADORES',
    name: 'NR-12 – Segurança em Máquinas e Equipamentos na Instalação e Manutenção de Elevadores (Ferramentas Elétricas Portáteis e Talhas)',
    normReference: 'NR-12 (texto atualizado 2022), itens 12.11 e 12.16 e Anexo II – conteúdo programático da capacitação',
    workloadHours: 16,
    validityMonths: 0,
    modality: 'presencial',
    minAttendance: 100,
    minGrade: 7,
    active: true,
    prerequisite: 'Recomendado: NR-10 (intervenções em instalações elétricas) e NR-35 (trabalho em altura na caixa do elevador).',
    notes: `${REGRAS} Ferramentas portáteis elétricas que atendem às normas técnicas tipo "C" estão fora do campo de aplicação da NR-12 (item 12.1.4 "e"), mas o uso seguro é abordado por fazer parte da atividade. Reciclagem quando houver modificações significativas nas instalações, máquinas, métodos, processos ou organização do trabalho (item 12.16.8). Registrar a função do trabalhador (item 12.16.9).`,
    topics: topics([
      ['Legislação: NR-12 (manutenção – item 12.11; capacitação – item 12.16), NR-10, NR-11 e NR-35; trabalhador habilitado, qualificado, capacitado e autorizado (item 12.16.1)', 1],
      ['Descrição e identificação dos riscos de cada máquina e equipamento – máquina de tração do elevador, talhas, furadeira e lixadeira – e as proteções específicas contra cada um (Anexo II, "a")', 1],
      ['Funcionamento das proteções, como e por que devem ser usadas; como, quando e por quem podem ser removidas; o que fazer se uma proteção for danificada ou perder a função (Anexo II, "b", "c" e "d")', 1],
      ['Princípios de segurança na utilização das máquinas e equipamentos; segurança para riscos mecânicos, elétricos e outros relevantes (Anexo II, "e" e "f")', 1],
      ['Ferramentas elétricas portáteis (furadeira, lixadeira/esmerilhadeira): seleção, inspeção antes do uso, brocas e discos, proteções, cabo, plugue e dupla isolação, fixação da peça e EPI', 1],
      ['Talhas e movimentação de componentes na casa de máquinas e na caixa do elevador: capacidade, inspeção de corrente, cabo e gancho, pontos de ancoragem, amarração e isolamento da área', 1],
      ['Método de trabalho seguro e permissão de trabalho (Anexo II, "g" e "h"); manutenção conforme o fabricante e registro das intervenções (itens 12.11.1 e 12.11.2)', 1],
      ['Bloqueio de funcionamento durante inspeção, limpeza, lubrificação e manutenção (Anexo II, "i"; item 12.11.3): isolamento e descarga das energias, bloqueio mecânico e elétrico, cartão com data, hora, motivo e responsável; travamento da cabine e do contrapeso', 1],
      ['Prática: inspeção e uso seguro de furadeira e lixadeira – troca de broca e disco, fixação da peça e postura', 2],
      ['Prática: inspeção e operação de talha; amarração e içamento de componentes (máquina de tração, guias, portas)', 2],
      ['Prática: bloqueio e etiquetagem do quadro de comando e da máquina de tração; travamento da cabine', 2],
      ['Prática: permissão de trabalho e método seguro em simulação de instalação e manutenção', 2]
    ])
  },
  {
    key: 'nr12-elevadores-reciclagem',
    code: 'NR-12 ELEVADORES RECICLAGEM',
    name: 'Reciclagem NR-12 – Segurança em Máquinas e Equipamentos na Instalação e Manutenção de Elevadores',
    normReference: 'NR-12 (texto atualizado 2022), itens 12.16.8 e 12.16.8.1 – capacitação para reciclagem',
    workloadHours: 4,
    validityMonths: 0,
    modality: 'presencial',
    minAttendance: 100,
    minGrade: 7,
    active: true,
    prerequisite: 'Capacitação NR-12 para instalação e manutenção de elevadores.',
    notes: `Reciclagem sempre que ocorrerem modificações significativas nas instalações e na operação de máquinas ou troca de métodos, processos e organização do trabalho que impliquem novos riscos (item 12.16.8); conteúdo conforme a situação que a motivou e carga horária definida pelo empregador (item 12.16.8.1). ${REGRAS}`,
    topics: topics([
      ['Modificações nas instalações, equipamentos, métodos ou processos que motivaram a reciclagem e os novos riscos', 1],
      ['Revisão: proteções, bloqueio e etiquetagem e permissão de trabalho', 1],
      ['Prática: ferramentas elétricas portáteis e talhas', 1],
      ['Prática: bloqueio e etiquetagem', 1]
    ])
  }
];
