/**
 * Checklist da inspeção visual por subsistema conforme ABNT NBR 5419:2026
 * (Parte 3: 5.3 a 5.7, 6.2, 7.3.3, 7.5 e Seção 8; Parte 4: 9.2.5).
 * Revisado a partir do checklist de referência (baseado na edição 2015) — ver
 * ANALISE_CHECKLIST_NBR5419_2026.md nesta pasta.
 */
import type { ChecklistTemplateItem, SpdaSubsystem } from './types';

export const NORM_EDITION = 'ABNT NBR 5419:2026';

export const SUBSYSTEM_LABEL: Record<SpdaSubsystem, string> = {
  documentacao: 'Documentação',
  captacao: 'Subsistema de captação',
  descida: 'Subsistema de descida e conexões',
  aterramento: 'Subsistema de aterramento',
  equipotencializacao: 'Equipotencialização e DPS',
  protecao_pessoas: 'Tensões de toque e de passo'
};

/** 'protecao_pessoas' (tensões de toque e passo) saiu do checklist; o tipo continua só por causa de inspeções antigas. */
export const SUBSYSTEM_ORDER: SpdaSubsystem[] = ['documentacao', 'captacao', 'descida', 'aterramento', 'equipotencializacao'];

const P3 = 'NBR 5419-3:2026';
const P4 = 'NBR 5419-4:2026';

export const CHECKLIST_TEMPLATE: ChecklistTemplateItem[] = [
  // Documentação (7.3.3 a/b, 7.5)
  { id: 'DOC-01', subsystem: 'documentacao', title: 'Projeto / "as built" com desenhos em escala, dimensões, materiais e posições de todos os componentes do SPDA externo e interno', reference: `${P3}, 7.5.1 e 7.5.3 f)` },
  { id: 'DOC-02', subsystem: 'documentacao', title: 'Projeto conforme a norma de origem (edição vigente na elaboração) e instalação conforme o projeto', reference: `${P3}, 7.3.1 a) e 7.3.3 a)` },
  { id: 'DOC-03', subsystem: 'documentacao', title: 'Relatório de inspeção vigente e relatórios anteriores disponíveis', reference: `${P3}, 7.3.3 b)` },
  { id: 'DOC-04', subsystem: 'documentacao', title: 'Validação dos elementos estruturais usados como componentes naturais (laudo da 1ª verificação ou registro da obra)', reference: `${P3}, 7.5.3 c) e F.1.2.2` },
  { id: 'DOC-05', subsystem: 'documentacao', title: 'Plano de inspeção e manutenção documentado, com intervalos', reference: `${P3}, 7.2` },

  // Captação (5.3, 5.6, 5.7)
  { id: 'CAP-01', subsystem: 'captacao', title: 'Captores verticais individuais interligados entre si no topo da estrutura', reference: `${P3}, 5.3.1.5` },
  { id: 'CAP-02', subsystem: 'captacao', title: 'Fixação dos condutores: flexíveis e rígidos a cada 1,0 m na horizontal e 1,5 m na vertical/inclinado; sem afrouxamento', reference: `${P3}, 5.6.2` },
  { id: 'CAP-03', subsystem: 'captacao', title: 'Material e seção mínima dos captores e condutores conforme a Tabela 7 (ex.: cobre 35 mm², aço zincado 50 mm²)', reference: `${P3}, 5.7.2.1 e 7.3.3 e)` },
  { id: 'CAP-04', subsystem: 'captacao', title: 'Sem deterioração, corrosão, partes rompidas ou sinais de fusão', reference: `${P3}, 7.3.1 b) e 7.3.3 c)` },
  { id: 'CAP-05', subsystem: 'captacao', title: 'Componentes naturais da cobertura (chapas, treliças, rufos, tubulações) atendem às condições de espessura e continuidade; sem armaduras protendidas', reference: `${P3}, 5.3.2.16` },
  { id: 'CAP-06', subsystem: 'captacao', title: 'Estrutura acima de 60 m: captação lateral nas partes superiores', reference: `${P3}, 5.3.2.14.2` },

  // Descida e conexões (5.4, 5.6.3)
  { id: 'DES-01', subsystem: 'descida', title: 'Descidas como extensão direta da captação, em linha reta e vertical, sem laços', reference: `${P3}, 5.4.4.1 e 5.4.4.2` },
  { id: 'DES-02', subsystem: 'descida', title: 'Distância de segurança respeitada a portas, janelas e instalações internas', reference: `${P3}, 5.4.4.4 e 6.3; 7.3.3 d)` },
  { id: 'DES-03', subsystem: 'descida', title: 'Anéis intermediários interligando as descidas, quando previstos (mesmo espaçamento da Tabela 5)', reference: `${P3}, 5.4.1.2, Tabela 5` },
  { id: 'DES-04', subsystem: 'descida', title: 'Sem emendas nos cabos de descida, exceto o conector de ensaio; conexões por solda, exotérmica, pressão ou compressão', reference: `${P3}, 5.6.3.1 e 5.6.3.2` },
  { id: 'DES-05', subsystem: 'descida', title: 'Conector de ensaio em cada descida, próximo ao solo (sugerido 1,5 m), de fácil acesso', reference: `${P3}, 5.4.6.1` },
  { id: 'DES-06', subsystem: 'descida', title: 'Conector de ensaio abre só com ferramenta, permanece fechado e sem contato com o solo', reference: `${P3}, 5.4.6.2` },
  { id: 'DES-07', subsystem: 'descida', title: 'Fixação a cada 1,0 m (horizontal) / 1,5 m (vertical); material e seção conforme a Tabela 7', reference: `${P3}, 5.6.2.2 e 5.7.2.1` },
  { id: 'DES-08', subsystem: 'descida', title: 'Sem corrosão, deterioração ou danos mecânicos nas descidas e conexões', reference: `${P3}, 7.3.3 c)` },

  // Aterramento (5.5, 5.6.3.3, 5.7.2.2)
  { id: 'ATE-01', subsystem: 'aterramento', title: 'Anel enterrado a no mínimo 0,5 m e a aproximadamente 1 m das paredes externas (ou das descidas afastadas)', reference: `${P3}, 5.5.3.1 e 5.5.3.2` },
  { id: 'ATE-02', subsystem: 'aterramento', title: 'Conexões enterradas em caixa de inspeção acessível e desobstruída (dispensável para compressão, exotérmica ou fusão)', reference: `${P3}, 5.6.3.3` },
  { id: 'ATE-03', subsystem: 'aterramento', title: 'Aterramento único e integrado (energia, sinal, SPDA)', reference: `${P3}, 5.5.1.3 e 5.5.1.4` },
  { id: 'ATE-04', subsystem: 'aterramento', title: 'Sem corrosão nos eletrodos e conexões; materiais compatíveis (sem par galvânico; aço zincado fora da transição concreto–terra)', reference: `${P3}, 5.5.1.5, Tabela 6 e 7.3.3 c)` },
  { id: 'ATE-05', subsystem: 'aterramento', title: 'Material e dimensões dos eletrodos não naturais conforme a Tabela 8 (ex.: cobre 50 mm²)', reference: `${P3}, 5.7.2.2` },
  { id: 'ATE-06', subsystem: 'aterramento', title: 'Tubos de PVC nas descidas não considerados como isolação elétrica', reference: `${P3}, 8.1.1 Nota 2` },

  // Equipotencialização e DPS (6.2; Parte 4, 9.2.5)
  { id: 'EQU-01', subsystem: 'equipotencializacao', title: 'BEP existente, acessível, ligada diretamente ao aterramento e coordenada com as BEL', reference: `${P3}, 6.2.1.6 e 6.2.2.2 b)` },
  { id: 'EQU-02', subsystem: 'equipotencializacao', title: 'Instalações metálicas, sistemas internos, partes condutivas externas e linhas elétricas equipotencializados', reference: `${P3}, 6.2.1.1` },
  { id: 'EQU-03', subsystem: 'equipotencializacao', title: 'DPS classe I (NBR IEC 61643-11) em todos os condutores vivos das linhas que entram na estrutura; PE/PEN direto ou por DPS', reference: `${P3}, 6.2.4.2 e 6.2.4.7` },
  { id: 'EQU-04', subsystem: 'equipotencializacao', title: 'DPS sem indicação de dano/fim de vida; dispositivo de sobrecorrente a montante e centelhadores em bom estado', reference: `${P3}, 7.3.3 g) e 7.3.5; ${P4}, 9.2.5 e) f)` }
];

/**
 * Versão da numeração do checklist: 2 = captação/descida/aterramento renumerados (14/11/2026);
 * 3 = equipotencialização e DPS renumerados (14/11/2026); 4 = DPS reduzido a 4 itens (14/11/2026);
 * 5 = documentação reduzida a 5 itens (14/11/2026).
 */
export const CHECKLIST_VERSION = 5;

/** Códigos da versão 1 → versão 2 (null = item retirado do checklist). */
export const CHECKLIST_V1_TO_V2: Record<string, string | null> = {
  'CAP-01': null,
  'CAP-02': null,
  'CAP-03': null,
  'CAP-04': null,
  'CAP-05': 'CAP-01',
  'CAP-06': 'CAP-02',
  'CAP-07': 'CAP-03',
  'CAP-08': 'CAP-04',
  'CAP-09': 'CAP-05',
  'CAP-10': 'CAP-06',
  'DES-01': null,
  'DES-02': null,
  'DES-05': null,
  'DES-03': 'DES-01',
  'DES-04': 'DES-02',
  'DES-06': 'DES-03',
  'DES-07': 'DES-04',
  'DES-08': 'DES-05',
  'DES-09': 'DES-06',
  'DES-10': 'DES-07',
  'DES-11': 'DES-08',
  'ATE-01': null,
  'ATE-02': null,
  'ATE-03': 'ATE-01',
  'ATE-04': 'ATE-02',
  'ATE-05': 'ATE-03',
  'ATE-06': 'ATE-04',
  'ATE-07': 'ATE-05',
  'PES-04': 'ATE-06',
  'PES-01': null,
  'PES-02': null,
  'PES-03': null
};

/** Códigos da versão 2 → versão 3 (null = item retirado do checklist). */
export const CHECKLIST_V2_TO_V3: Record<string, string | null> = {
  'EQU-01': 'EQU-01',
  'EQU-02': null,
  'EQU-03': 'EQU-02',
  'EQU-04': 'EQU-03',
  'EQU-05': null,
  'EQU-06': 'EQU-04',
  'EQU-07': 'EQU-05',
  'EQU-08': null,
  'EQU-09': 'EQU-06',
  'EQU-10': null,
  'EQU-11': null,
  'EQU-12': 'EQU-07',
  'EQU-13': 'EQU-08',
  'EQU-14': null,
  'EQU-15': null
};

/** Códigos da versão 3 → versão 4 (null = item retirado do checklist). */
export const CHECKLIST_V3_TO_V4: Record<string, string | null> = {
  'EQU-01': 'EQU-01',
  'EQU-02': 'EQU-02',
  'EQU-03': null,
  'EQU-04': null,
  'EQU-05': null,
  'EQU-06': 'EQU-03',
  'EQU-07': null,
  'EQU-08': 'EQU-04'
};

/** Códigos da versão 4 → versão 5 (null = item retirado do checklist). */
export const CHECKLIST_V4_TO_V5: Record<string, string | null> = {
  'DOC-01': null,
  'DOC-02': 'DOC-01',
  'DOC-03': 'DOC-02',
  'DOC-04': 'DOC-03',
  'DOC-05': null,
  'DOC-06': null,
  'DOC-07': 'DOC-04',
  'DOC-08': null,
  'DOC-09': null,
  'DOC-10': 'DOC-05'
};

/** Conversões em ordem: índice 0 leva da versão 1 à 2, índice 1 da 2 à 3… */
export const CHECKLIST_MIGRATIONS: Array<Record<string, string | null>> = [CHECKLIST_V1_TO_V2, CHECKLIST_V2_TO_V3, CHECKLIST_V3_TO_V4, CHECKLIST_V4_TO_V5];

export function templateItem(id: string): ChecklistTemplateItem | undefined {
  return CHECKLIST_TEMPLATE.find(i => i.id === id);
}
