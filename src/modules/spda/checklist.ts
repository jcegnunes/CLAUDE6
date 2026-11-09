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

export const SUBSYSTEM_ORDER: SpdaSubsystem[] = ['documentacao', 'captacao', 'descida', 'aterramento', 'equipotencializacao', 'protecao_pessoas'];

const P3 = 'NBR 5419-3:2026';
const P4 = 'NBR 5419-4:2026';

export const CHECKLIST_TEMPLATE: ChecklistTemplateItem[] = [
  // Documentação (7.3.3 a/b, 7.5)
  { id: 'DOC-01', subsystem: 'documentacao', title: 'Análise de risco (NBR 5419-2) com a necessidade de SPDA e de DPS classe I nas entradas das linhas, nível de proteção e premissas do estudo', reference: `${P3}, 7.5.3 b)` },
  { id: 'DOC-02', subsystem: 'documentacao', title: 'Projeto / "as built" com desenhos em escala, dimensões, materiais e posições de todos os componentes do SPDA externo e interno', reference: `${P3}, 7.5.1 e 7.5.3 f)` },
  { id: 'DOC-03', subsystem: 'documentacao', title: 'Projeto conforme a norma de origem (edição vigente na elaboração) e instalação conforme o projeto', reference: `${P3}, 7.3.1 a) e 7.3.3 a)` },
  { id: 'DOC-04', subsystem: 'documentacao', title: 'Relatório de inspeção vigente e relatórios anteriores disponíveis', reference: `${P3}, 7.3.3 b)` },
  { id: 'DOC-05', subsystem: 'documentacao', title: 'Responsáveis técnicos identificados (nome, registro no conselho e ART/TRT)', reference: `${P3}, 7.5.3 a)` },
  { id: 'DOC-06', subsystem: 'documentacao', title: 'Definição de SPDA isolado ou não isolado, método de posicionamento e dimensionamento de captores, descidas e eletrodo', reference: `${P3}, 7.5.3 e)` },
  { id: 'DOC-07', subsystem: 'documentacao', title: 'Validação dos elementos estruturais usados como componentes naturais (laudo da 1ª verificação ou registro da obra)', reference: `${P3}, 7.5.3 c) e F.1.2.2` },
  { id: 'DOC-08', subsystem: 'documentacao', title: 'Dados de resistividade/estratificação do solo (NBR 7117-1), quando aplicável', reference: `${P3}, 7.5.3 d)` },
  { id: 'DOC-09', subsystem: 'documentacao', title: 'Distâncias de segurança (s) e parcelas de corrente usadas no dimensionamento de DPS/centelhadores registradas', reference: `${P3}, 7.5.3 h)` },
  { id: 'DOC-10', subsystem: 'documentacao', title: 'Plano de inspeção e manutenção documentado, com intervalos', reference: `${P3}, 7.2` },

  // Captação (5.3, 5.6, 5.7)
  { id: 'CAP-01', subsystem: 'captacao', title: 'Captores posicionados nos cantos salientes, pontas expostas e beiradas (nível superior das fachadas)', reference: `${P3}, 5.3.2.1` },
  { id: 'CAP-02', subsystem: 'captacao', title: 'Método de posicionamento compatível com o NP: esfera rolante (R = 20/30/45/60 m), malha (5×5 a 20×20 m) ou ângulo dentro dos limites de altura da Figura 1', reference: `${P3}, 5.3.2.2 a 5.3.2.13, Tabela 2` },
  { id: 'CAP-03', subsystem: 'captacao', title: 'Malha com condutores na periferia (platibandas) e saliências; cumeeiras quando inclinação > 10 %; não aplicada em superfície curva', reference: `${P3}, 5.3.2.5 e 5.3.2.6` },
  { id: 'CAP-04', subsystem: 'captacao', title: 'Módulo da malha: só uma dimensão aumentada em até 25 %, sem aumentar o perímetro', reference: `${P3}, 5.3.2.7` },
  { id: 'CAP-05', subsystem: 'captacao', title: 'Captores verticais individuais interligados entre si no topo da estrutura', reference: `${P3}, 5.3.1.5` },
  { id: 'CAP-06', subsystem: 'captacao', title: 'Fixação dos condutores: flexíveis e rígidos a cada 1,0 m na horizontal e 1,5 m na vertical/inclinado; sem afrouxamento', reference: `${P3}, 5.6.2` },
  { id: 'CAP-07', subsystem: 'captacao', title: 'Material e seção mínima dos captores e condutores conforme a Tabela 7 (ex.: cobre 35 mm², aço zincado 50 mm²)', reference: `${P3}, 5.7.2.1 e 7.3.3 e)` },
  { id: 'CAP-08', subsystem: 'captacao', title: 'Sem deterioração, corrosão, partes rompidas ou sinais de fusão', reference: `${P3}, 7.3.1 b) e 7.3.3 c)` },
  { id: 'CAP-09', subsystem: 'captacao', title: 'Componentes naturais da cobertura (chapas, treliças, rufos, tubulações) atendem às condições de espessura e continuidade; sem armaduras protendidas', reference: `${P3}, 5.3.2.16` },
  { id: 'CAP-10', subsystem: 'captacao', title: 'Estrutura acima de 60 m: captação lateral nas partes superiores', reference: `${P3}, 5.3.2.14.2` },

  // Descida e conexões (5.4, 5.6.3)
  { id: 'DES-01', subsystem: 'descida', title: 'Número de descidas ≥ perímetro ÷ espaçamento da Tabela 5 e nunca inferior a dois', reference: `${P3}, 5.4.3.1` },
  { id: 'DES-02', subsystem: 'descida', title: 'Espaçamento uniforme (10/10/15/20 m para NP I a IV; até +20 % sem reduzir o número), com preferência aos cantos', reference: `${P3}, 5.4.3.1, 5.4.3.3, Tabela 5` },
  { id: 'DES-03', subsystem: 'descida', title: 'Descidas como extensão direta da captação, em linha reta e vertical, sem laços', reference: `${P3}, 5.4.4.1 e 5.4.4.2` },
  { id: 'DES-04', subsystem: 'descida', title: 'Distância de segurança respeitada a portas, janelas e instalações internas', reference: `${P3}, 5.4.4.4 e 6.3; 7.3.3 d)` },
  { id: 'DES-05', subsystem: 'descida', title: 'Afastamento ≥ 0,10 m em superfícies de madeira, vidro ou polímeros; equivalência térmica a 100 mm² de aço junto a combustíveis', reference: `${P3}, 5.4.4.5 e 5.4.4.6` },
  { id: 'DES-06', subsystem: 'descida', title: 'Anéis intermediários interligando as descidas, quando previstos (mesmo espaçamento da Tabela 5)', reference: `${P3}, 5.4.1.2, Tabela 5` },
  { id: 'DES-07', subsystem: 'descida', title: 'Sem emendas nos cabos de descida, exceto o conector de ensaio; conexões por solda, exotérmica, pressão ou compressão', reference: `${P3}, 5.6.3.1 e 5.6.3.2` },
  { id: 'DES-08', subsystem: 'descida', title: 'Conector de ensaio em cada descida, próximo ao solo (sugerido 1,5 m), de fácil acesso', reference: `${P3}, 5.4.6.1` },
  { id: 'DES-09', subsystem: 'descida', title: 'Conector de ensaio abre só com ferramenta, permanece fechado e sem contato com o solo', reference: `${P3}, 5.4.6.2` },
  { id: 'DES-10', subsystem: 'descida', title: 'Fixação a cada 1,0 m (horizontal) / 1,5 m (vertical); material e seção conforme a Tabela 7', reference: `${P3}, 5.6.2.2 e 5.7.2.1` },
  { id: 'DES-11', subsystem: 'descida', title: 'Sem corrosão, deterioração ou danos mecânicos nas descidas e conexões', reference: `${P3}, 7.3.3 c)` },

  // Aterramento (5.5, 5.6.3.3, 5.7.2.2)
  { id: 'ATE-01', subsystem: 'aterramento', title: 'Arranjo conforme a ordem preferencial: armadura da fundação interligada; fundação + condutor adicional; anel fechado (hastes só como complemento)', reference: `${P3}, 5.5.2.1` },
  { id: 'ATE-02', subsystem: 'aterramento', title: 'Complementação do eletrodo (l1, eletrodos verticais/radiais) ou medidas da Seção 8 quando as condições de 5.5.2.3 não são atendidas', reference: `${P3}, 5.5.2.3 e 5.5.2.4` },
  { id: 'ATE-03', subsystem: 'aterramento', title: 'Anel enterrado a no mínimo 0,5 m e a aproximadamente 1 m das paredes externas (ou das descidas afastadas)', reference: `${P3}, 5.5.3.1 e 5.5.3.2` },
  { id: 'ATE-04', subsystem: 'aterramento', title: 'Conexões enterradas em caixa de inspeção acessível e desobstruída (dispensável para compressão, exotérmica ou fusão)', reference: `${P3}, 5.6.3.3` },
  { id: 'ATE-05', subsystem: 'aterramento', title: 'Aterramento único e integrado (energia, sinal, SPDA)', reference: `${P3}, 5.5.1.3 e 5.5.1.4` },
  { id: 'ATE-06', subsystem: 'aterramento', title: 'Sem corrosão nos eletrodos e conexões; materiais compatíveis (sem par galvânico; aço zincado fora da transição concreto–terra)', reference: `${P3}, 5.5.1.5, Tabela 6 e 7.3.3 c)` },
  { id: 'ATE-07', subsystem: 'aterramento', title: 'Material e dimensões dos eletrodos não naturais conforme a Tabela 8 (ex.: cobre 50 mm²)', reference: `${P3}, 5.7.2.2` },

  // Equipotencialização e DPS (6.2; Parte 4, 9.2.5)
  { id: 'EQU-01', subsystem: 'equipotencializacao', title: 'BEP existente, acessível, ligada diretamente ao aterramento e coordenada com as BEL', reference: `${P3}, 6.2.1.6 e 6.2.2.2 b)` },
  { id: 'EQU-02', subsystem: 'equipotencializacao', title: 'Estrutura com mais de 20 m: várias barras interligadas ou barra em anel', reference: `${P3}, 6.2.2.2 b)` },
  { id: 'EQU-03', subsystem: 'equipotencializacao', title: 'Instalações metálicas, sistemas internos, partes condutivas externas e linhas elétricas equipotencializados', reference: `${P3}, 6.2.1.1` },
  { id: 'EQU-04', subsystem: 'equipotencializacao', title: 'Interligação direta por condutor ou indireta por DPS classe I / centelhador de isolação onde a direta não é possível', reference: `${P3}, 6.2.1.3` },
  { id: 'EQU-05', subsystem: 'equipotencializacao', title: 'Seção dos condutores entre barras e barra–aterramento (Tabela 9: Cu/aço cobreado 16, Al 25, aço zincado 50 mm²; enterrado: Tabela 8)', reference: `${P3}, 6.2.2.4, Tabela 9` },
  { id: 'EQU-06', subsystem: 'equipotencializacao', title: 'Seção dos condutores das instalações metálicas internas às barras (Tabela 10: Cu 6, Al 10, aço zincado 16 mm²)', reference: `${P3}, 6.2.2.7, Tabela 10` },
  { id: 'EQU-07', subsystem: 'equipotencializacao', title: 'Ligações equipotenciais curtas e retilíneas, no ponto mais próximo da entrada das partes condutivas/linhas', reference: `${P3}, 6.2.2.3 e 6.2.3.1` },
  { id: 'EQU-08', subsystem: 'equipotencializacao', title: 'Tubulações com flanges isolantes interligadas (condutor ou centelhador de isolação)', reference: `${P3}, 6.2.2.9` },
  { id: 'EQU-09', subsystem: 'equipotencializacao', title: 'DPS classe I (NBR IEC 61643-11) em todos os condutores vivos das linhas que entram na estrutura; PE/PEN direto ou por DPS', reference: `${P3}, 6.2.4.2 e 6.2.4.7` },
  { id: 'EQU-10', subsystem: 'equipotencializacao', title: 'Blindagens de cabos e condutos metálicos equipotencializados no ponto de entrada', reference: `${P3}, 6.2.4.3 e 6.2.4.4` },
  { id: 'EQU-11', subsystem: 'equipotencializacao', title: 'Linhas que cruzam a fronteira de ZPR equipotencializadas por DPS, em barra próxima ao ponto de entrada', reference: `${P4}, 5.5.2 e 5.5.4` },
  { id: 'EQU-12', subsystem: 'equipotencializacao', title: 'DPS facilmente inspecionáveis, com conexões as mais curtas possíveis à barra e aos condutores vivos', reference: `${P3}, 6.2.1.4; ${P4}, 5.4.2 d)` },
  { id: 'EQU-13', subsystem: 'equipotencializacao', title: 'DPS sem indicação de dano/fim de vida; dispositivo de sobrecorrente a montante e centelhadores em bom estado', reference: `${P3}, 7.3.3 g) e 7.3.5; ${P4}, 9.2.5 e) f)` },
  { id: 'EQU-14', subsystem: 'equipotencializacao', title: 'Sem maus contatos ou corrosão; condutores de equipotencialização e blindagens interligados e intactos', reference: `${P4}, 9.2.5 a) b) c)` },
  { id: 'EQU-15', subsystem: 'equipotencializacao', title: 'Contador de descargas (se houver) íntegro; atuações registradas', reference: `${P3}, 7.3.3 h)` },

  // Toque e passo (Seção 8)
  { id: 'PES-01', subsystem: 'protecao_pessoas', title: 'Condição de 8.1.2 atendida: acesso limitado até 3 m das descidas, ≥ 10 descidas naturais interconectadas, ou camada isolante equivalente a 5 cm de asfalto até 3 m', reference: `${P3}, 8.1.2 e 8.2.1` },
  { id: 'PES-02', subsystem: 'protecao_pessoas', title: 'Se 8.1.2 não for atendido (toque): descidas expostas isoladas para 100 kV 1,2/50 µs (ex.: 3 mm de XLPE) ou barreiras/sinalização', reference: `${P3}, 8.1.3` },
  { id: 'PES-03', subsystem: 'protecao_pessoas', title: 'Se 8.1.2 não for atendido (passo): barreiras/sinalização até 3 m ou eletrodo reticulado complementar com raio mínimo de 3 m', reference: `${P3}, 8.2.2` },
  { id: 'PES-04', subsystem: 'protecao_pessoas', title: 'Tubos de PVC nas descidas não considerados como isolação elétrica', reference: `${P3}, 8.1.1 Nota 2` }
];

export function templateItem(id: string): ChecklistTemplateItem | undefined {
  return CHECKLIST_TEMPLATE.find(i => i.id === id);
}
