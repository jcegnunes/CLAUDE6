/**
 * Cursos NR-18 (texto atualizado 2026): instalação, montagem, desmontagem e
 * manutenção de elevadores de passageiros em canteiros de obras. Pelo Anexo I
 * (Quadro 1), a carga horária inicial e o conteúdo são definidos pelo
 * empregador e o treinamento periódico é anual; o conteúdo abaixo segue os
 * requisitos do item 18.11. A divisão das horas por tópico é uma sugestão.
 */
import type { TrainingCourse } from './types';

type CourseSeed = Omit<TrainingCourse, 'id' | 'companyId' | 'createdAt' | 'updatedAt' | 'serverUpdatedAt'>;
type Seed = { key: string } & CourseSeed;

const RESPONSABILIDADE = 'Os serviços de instalação, montagem, desmontagem e manutenção devem ser executados por profissional capacitado, com anuência formal da empresa e sob responsabilidade de profissional legalmente habilitado; a empresa deve ser registrada no conselho de classe (itens 18.11.3 e 18.11.5).';

const topics = (list: Array<[string, number]>) => list.map(([title, hours]) => ({ title, hours }));

export const NR18_COURSES: Seed[] = [
  {
    key: 'nr18-elevadores-montagem',
    code: 'NR-18 ELEVADORES MONTAGEM',
    name: 'NR-18 – Instalação, Montagem, Desmontagem e Manutenção de Elevadores de Passageiros em Canteiros de Obras',
    normReference: 'NR-18 (texto atualizado 2026), itens 18.11 e 18.14 e Anexo I – Quadro 1 (instalação, montagem, desmontagem e manutenção de elevadores); normas técnicas nacionais de elevadores de canteiro de obras',
    workloadHours: 24,
    validityMonths: 12,
    modality: 'presencial',
    minAttendance: 100,
    minGrade: 7,
    active: true,
    prerequisite: 'Recomendado: capacitação em trabalho em altura (NR-35) e, para intervenções em instalações elétricas, NR-10.',
    notes: `Carga horária e conteúdo do treinamento inicial definidos pelo empregador; treinamento periódico anual (Anexo I, Quadro 1); eventual a critério do empregador. Capacitação compatível com o equipamento utilizado (item 18.14.2). ${RESPONSABILIDADE}`,
    topics: topics([
      ['Legislação e responsabilidades: NR-18 (item 18.11), NR-12, NR-35 e normas técnicas nacionais de elevadores de canteiro de obras; registro no conselho de classe, profissional legalmente habilitado e anuência formal da empresa (18.11.3 a 18.11.5)', 2],
      ['Tipos de elevadores de obra e componentes: cremalheira e tração a cabo (proibição de cabo único, 18.11.2), torre, cabine, motofreios, freio de emergência, amarrações e ancoragens', 2],
      ['Requisitos de instalação: afastamento das redes elétricas, distância máxima de 0,2 m entre cabine e edificação, cancelas de 1,8 m com intertravamento, fechamento da base de 2 m, rampa de acesso e altura livre de 2 m (18.11.11 a 18.11.16)', 2],
      ['Itens e dispositivos de segurança obrigatórios: intertravamentos de duplo canal com ruptura positiva, freio de emergência contra queda livre, limites de curso, dispositivo anti-desprendimento, amortecedores, bloqueio do acionamento e limitador de carga (18.11.18 e 18.11.19)', 2],
      ['Elevador de passageiros: prioridade do transporte de pessoas, obrigatoriedade em obras a partir de 24 m e instalação até 15 m de deslocamento vertical; torre de cremalheira – altura livre após a amarração e último elemento com régua invertida ou sem cremalheira (18.11.20 a 18.11.23)', 1],
      ['Procedimentos de montagem, desmontagem e ascensão: isolamento da área, restrição de atividades na periferia, condições meteorológicas adversas (18.11.10); trabalho em altura e sistemas de proteção contra quedas; movimentação de componentes', 2],
      ['Instalação elétrica e aterramento, proteção contra intempéries, proibição de chave comutadora ou reversora (18.11.8 e 18.11.9); bloqueio e sinalização de energias', 1],
      ['Documentação no canteiro (18.11.7): termo de entrega técnica, programa de manutenção preventiva, laudo de teste dos freios de emergência (no máximo a cada 90 dias), ensaios não destrutivos, registro de manutenção (NR-12), laudo de aterramento e vistoria diária', 1],
      ['Prevenção de acidentes, procedimentos de emergência e resgate na torre e na cabine', 1],
      ['Prática: montagem da base, da torre, das amarrações e da cabine conforme o manual do fabricante', 4],
      ['Prática: instalação e teste dos dispositivos de segurança, intertravamentos, cancelas e rampas', 3],
      ['Prática: teste do freio de emergência, entrega técnica e inspeção antes do uso', 2],
      ['Prática: ascensão e desmontagem seguras; simulado de resgate', 1]
    ])
  },
  {
    key: 'nr18-elevadores-montagem-reciclagem',
    code: 'NR-18 ELEVADORES MONTAGEM RECICLAGEM',
    name: 'Reciclagem NR-18 – Instalação, Montagem, Desmontagem e Manutenção de Elevadores de Passageiros (Treinamento Periódico)',
    normReference: 'NR-18 (texto atualizado 2026), item 18.14 e Anexo I – Quadro 1 (periódico anual) e item 2.2',
    workloadHours: 8,
    validityMonths: 12,
    modality: 'presencial',
    minAttendance: 100,
    minGrade: 7,
    active: true,
    prerequisite: 'Treinamento inicial NR-18 de instalação, montagem, desmontagem e manutenção de elevadores.',
    notes: `Treinamento periódico anual; carga horária e conteúdo definidos pelo empregador, com os princípios básicos de segurança compatíveis com o equipamento e a atividade (Anexo I, item 2.2). Treinamento com avaliação do conhecimento (item 18.14.5). ${RESPONSABILIDADE}`,
    topics: topics([
      ['Revisão dos requisitos do item 18.11 da NR-18 e atualizações das normas técnicas e do manual do fabricante', 1.5],
      ['Análise de acidentes, incidentes e não conformidades em montagens e manutenções', 1],
      ['Dispositivos de segurança, freio de emergência, aterramento e documentação do equipamento', 1.5],
      ['Prática: montagem e ascensão, verificação dos intertravamentos e teste do freio de emergência', 3],
      ['Prática: procedimentos de emergência e resgate', 1]
    ])
  }
];
