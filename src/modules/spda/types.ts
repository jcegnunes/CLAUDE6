/**
 * Tipos do módulo SPDA (DEMO) — inspeção e ensaios de Sistema de Proteção
 * contra Descargas Atmosféricas conforme ABNT NBR 5419 (partes 1 a 4).
 *
 * Demo: dados somente no aparelho (IndexedDB), sem Supabase.
 */

export type ProtectionLevel = 'I' | 'II' | 'III' | 'IV';

export type SpdaMethod = 'franklin' | 'gaiola' | 'esfera' | 'misto' | 'estrutural';

/** Sequência de inspeções (NBR 5419-3:2026, 7.3.2). */
export type InspectionKind = 'durante_instalacao' | 'inicial' | 'periodica' | 'apos_descarga' | 'apos_modificacao' | 'apos_alteracao_spda';

/** Subsistemas do SPDA (NBR 5419-3) + medidas de proteção contra surtos (NBR 5419-4). */
export type SpdaSubsystem = 'documentacao' | 'captacao' | 'descida' | 'aterramento' | 'equipotencializacao' | 'protecao_pessoas';

export type ChecklistStatus = 'C' | 'NC' | 'NA' | '';

export type TestStatus = 'conforme' | 'nao_conforme' | 'pendente' | 'informativo';

export interface ChecklistTemplateItem {
  id: string;
  subsystem: SpdaSubsystem;
  title: string;
  /** Referência normativa resumida (ex.: "NBR 5419-3, 5.2") */
  reference: string;
}

export interface ChecklistAnswer {
  itemId: string;
  /** Edificação a que a resposta se refere (vazio = geral da OS, ex.: documentação) */
  buildingId?: string;
  status: ChecklistStatus;
  obs?: string;
  /** Fotos comprimidas (data URL) */
  photos?: string[];
}

/**
 * Tipos de ensaio de continuidade (NBR 5419-3:2026, Anexo F):
 * - F1: armaduras pilares + fundação (descida e aterramento naturais) — 1ª verificação, ≤ 1 Ω
 * - F2: armaduras dos pilares (só descida natural) — 1ª verificação, ≤ 1 Ω
 * - F3: armaduras da fundação (só aterramento natural) — 1ª verificação, ≤ 1 Ω
 * - F4: verificação comprobatória captação ↔ BEP (componentes naturais) — ≤ 0,2 Ω
 * - F5: condutor de descida não natural não visível — valor compatível com material × comprimento
 * - F6: eletrodo de aterramento em anel não natural — idem
 * - EQ: condutores de equipotencialização / aterramento (NBR 5419-4, 9.2.6) — idem
 */
/** Foto do registro fotográfico de um ensaio (mesmo padrão dos ensaios de EPI). */
/** Registro de não conformidade ou melhoria de um subsistema (fotos + comentário), feito após os ensaios. */
export type FindingKind = 'nc' | 'melhoria';
export interface SpdaFinding {
  id: string;
  /** Edificação (ausente = inspeção sem edificações) */
  buildingId?: string;
  subsystem: SpdaSubsystem;
  kind: FindingKind;
  /** Comentário / descrição digitada pelo inspetor */
  comment: string;
  /** Ação de correção (não conformidade) / ação recomendada (melhoria) — opcional */
  correctiveAction?: string;
  /** Item da norma que se aplica (ex.: "NBR 5419-3:2026, 5.4.3") — opcional */
  normClause?: string;
  photos: SpdaPhoto[];
  createdAt: string;
}

export interface SpdaPhoto {
  id: string;
  /** Imagem comprimida (data URL) */
  url: string;
  caption: string;
  timestamp: string;
  userName: string;
  /** Foto original, sem marcações, guardada na primeira edição (evidência) */
  originalUrl?: string;
  editedAt?: string;
  gpsCoords?: { latitude: number; longitude: number };
  /** Elementos do editor de croqui (setas, círculos, textos…) — editáveis/movíveis; base = originalUrl */
  layers?: SketchElement[];
}

export type SketchElementKind = 'arrow' | 'line' | 'circle' | 'rect' | 'text';
/** Elemento vetorial do croqui, em pixels da imagem base. Texto usa (x1, y1) como canto superior esquerdo. */
export interface SketchElement {
  id: string;
  kind: SketchElementKind;
  x1: number; y1: number; x2: number; y2: number;
  color: string;
  /** Espessura do traço (px da imagem) */
  width: number;
  text?: string;
  fontSize?: number;
  bold?: boolean;
  /** Contorno branco atrás do texto (legibilidade) */
  halo?: boolean;
}

export type ContinuityKind = 'F1' | 'F2' | 'F3' | 'F4' | 'F5' | 'F6' | 'EQ';

export type ConductorMaterial = 'cobre' | 'aluminio' | 'aco_zincado' | 'aco_cobreado' | 'aluminio_cobreado' | 'aco_inox';

export interface ContinuityTest {
  id: string;
  /** Edificação ensaiada */
  buildingId?: string;
  kind: ContinuityKind;
  subsystem: Exclude<SpdaSubsystem, 'documentacao' | 'protecao_pessoas'>;
  /** Ponto inicial (ex.: "Topo do pilar P3", "Captação") */
  from: string;
  /** Ponto final (ex.: "Base do pilar P7", "BEP") */
  to: string;
  /** Resistência medida em Ω (vazio = não medido) */
  resistanceOhm?: number | null;
  /** Limite de aceitação em Ω (F5/F6/EQ: valor de referência adotado pelo inspetor) */
  limitOhm?: number | null;
  /** Corrente de ensaio (A) — F.1.3.1: entre 1 A e 5 A */
  currentA?: number | null;
  /** F5/F6/EQ: dados do condutor para a resistência teórica do trecho */
  material?: ConductorMaterial;
  lengthM?: number | null;
  sectionMm2?: number | null;
  obs?: string;
  /** Registro fotográfico do ensaio */
  photos?: SpdaPhoto[];
  /** Valor lido direto do instrumento pelo Bluetooth (rastreabilidade da medição) */
  reading?: InstrumentReading;
}

export interface InstrumentReading {
  source: 'bluetooth';
  /** ex.: "INBRAT INMD 1 Pro — nº ML2511013" */
  instrument: string;
  serial?: string;
  /** nº do ensaio na memória do instrumento */
  deviceTestNo?: number;
  resistanceMilliOhm: number;
  currentA?: number;
  at: string;
}

export type GroundingMethod = 'queda_potencial' | 'alicate' | 'outro';

export interface GroundingTest {
  id: string;
  /** Edificação medida */
  buildingId?: string;
  /** Ponto medido (ex.: "Descida D1 / caixa de inspeção") */
  point: string;
  method: GroundingMethod;
  /** Distância do eletrodo de corrente (m) — queda de potencial */
  currentProbeDistanceM?: number | null;
  /** Passo das posições do eletrodo de potencial em torno de 62 %: 10 (52/62/72 %, padrão) ou 5 (57/62/67 %) */
  probeStepPct?: 5 | 10;
  /** Leituras da queda de potencial: r52 = S1 (62 − passo %), r62 = S (62 %), r72 = S2 (62 + passo %) */
  r52?: number | null;
  r62?: number | null;
  r72?: number | null;
  /** Resistência adotada (Ω); na queda de potencial, a leitura a 62 % quando não informada */
  resistanceOhm?: number | null;
  /**
   * Valor de projeto (Ω), só para comparação. A medição de resistência de aterramento
   * é informativa: não é necessária para verificar a eficácia do SPDA (NBR 5419-3:2026, 7.1.4).
   */
  designOhm?: number | null;
  /** Campo da versão anterior da demo (lido como designOhm) */
  limitOhm?: number | null;
  obs?: string;
  /** Registro fotográfico da medição */
  photos?: SpdaPhoto[];
}

export type BuildingType = 'industrial' | 'hospitalar' | 'comercial' | 'residencial' | 'outro';

/** Tipo de SPDA existente na edificação (NBR 5419-3:2026, 5.1.2, 5.1.3 e 5.4.2). */
export type SpdaKind = 'estrutural' | 'natural' | 'externo' | 'misto' | 'isolado' | 'inexistente';

/** Edificação atendida pela OS (uma inspeção/OS pode ter várias). */
export interface SpdaBuilding {
  id: string;
  name: string;
  type: BuildingType;
  /** Descrição específica da atividade / ocupação */
  activity?: string;
  heightM?: number | null;
  lengthM?: number | null;
  widthM?: number | null;
  floors?: number | null;
  spdaKind: SpdaKind;
  /** Nível de proteção; '' = sem valor (edificação sem NP definido) */
  protectionLevel: ProtectionLevel | '';
  /** Nº de descidas existentes */
  downConductorsCount?: number | null;
  /** Coordenadas geográficas (graus decimais, WGS 84) */
  latitude?: number | null;
  longitude?: number | null;
  /** Imagem aérea / de satélite da edificação (data URL comprimida) */
  aerialImage?: string;
  /** Link do Google Maps (ou outro mapa) da localização da edificação */
  mapsLink?: string;
  /** Tipos de aterramento existentes (seleção múltipla) + registro fotográfico de cada um */
  groundingSystem?: GroundingSystemInfo;
  /** Tipos de descida existentes (seleção múltipla) + registro fotográfico de cada um */
  downConductor?: DownConductorInfo;
  /** Sistemas de captação existentes (seleção múltipla) + registro fotográfico de cada um */
  airTermination?: AirTerminationInfo;
  /** Projeto / croqui com os pontos de captação, descida e aterramento (imagens + identificação dos pontos) */
  sketches?: Partial<Record<SketchKind, BuildingSketch>>;
}

/** Tipos de sistema de captação existentes na cobertura (seleção múltipla). */
export type AirTerminationType = 'franklin' | 'mini_captores' | 'faraday' | 'telhas_metalicas' | 'anel_superior' | 'outro';
export interface AirTerminationInfo {
  types: AirTerminationType[];
  /** Material do anel superior */
  ringMaterial?: 'cobre' | 'aluminio';
  /** Descrição quando "outro" */
  otherText?: string;
  /** Fotos do sistema existente, por tipo selecionado */
  photos?: Partial<Record<AirTerminationType, SpdaPhoto[]>>;
  notes?: string;
}

/** Tipos de descida existentes (seleção múltipla). */
export type DownConductorType = 'externa' | 'pilar_estrutural' | 'pilar_metalico';
export interface DownConductorInfo {
  types: DownConductorType[];
  /** Material da descida externa */
  material?: 'cobre' | 'aluminio';
  /** Fotos do sistema existente, por tipo selecionado */
  photos?: Partial<Record<DownConductorType, SpdaPhoto[]>>;
  notes?: string;
}

/** Tipos de malha de aterramento existentes (seleção múltipla). */
export type GroundingSystemType = 'anel_cobre' | 'anel_sem_hastes' | 'hastes_linha' | 'estrutural';
export interface GroundingSystemInfo {
  types: GroundingSystemType[];
  /** Anel em cobre: afastamento da edificação e profundidade (m) */
  ringDistanceM?: number | null;
  ringDepthM?: number | null;
  /** Fotos do sistema existente, por tipo selecionado */
  photos?: Partial<Record<GroundingSystemType, SpdaPhoto[]>>;
  notes?: string;
}

export type SketchKind = 'captacao' | 'descida' | 'aterramento';
export interface BuildingSketch {
  images: SpdaPhoto[];
  /** Identificação dos pontos (ex.: D1 — fachada norte, caixa de inspeção…) */
  notes?: string;
}

export interface InstrumentInfo {
  use: 'continuidade' | 'aterramento';
  description: string;
  model: string;
  serialNumber: string;
  calibrationCert: string;
  calibrationDate?: string;
  calibrationExpiry?: string;
}

/** Textos do relatório técnico (menu Relatório). */
/** Resultado escolhido no relatório técnico (vazio = calculado pela inspeção). */
export type ReportResult = 'aprovado' | 'reprovado' | 'parcial';

export interface TechReportTexts {
  /** 1. Resumo executivo — texto do painel do parecer */
  resumo?: string;
  introducao?: string;
  metodoInspecao?: string;
  metodoContinuidade?: string;
  /** Continuidade do eletrodo de aterramento (injeção direta de corrente) */
  metodoContinuidadeAterramento?: string;
  metodoAterramento?: string;
  /** 8. Recomendações gerais */
  recomendacoes?: string;
  conclusao?: string;
  /** Laudo (Inspeções e laudos), seção 9 — texto do parecer */
  laudoConclusao?: string;
  /** Resultado por subsistema (chave `<edificação>_<subsistema>`) e parecer final (chave 'final') */
  results?: Record<string, ReportResult>;
  /** Textos por subsistema: `desc_<edificação>_<subsistema>` (capítulo 5) e `res_<edificação>_<subsistema>` (resultado na folha de ensaio) */
  subsystemTexts?: Record<string, string>;
}

export interface SpdaInspection {
  id: string;
  companyId: string;
  /** Número do laudo (ex.: SPDA-2026-0001) */
  number: string;
  status: 'rascunho' | 'concluido';

  /** Título do laudo técnico (sobrepõe o padrão no cabeçalho do laudo) — opcional */
  laudoTitle?: string;
  /** Título do relatório técnico (sobrepõe o padrão na capa do relatório) — opcional */
  reportTitle?: string;

  // ordem de serviço (cadastro de OS da plataforma)
  serviceOrderId?: string;
  serviceOrderNumber?: string;

  // cliente / edificação
  clientId?: string;
  clientName: string;
  clientCnpj?: string;
  buildingName: string;
  address: string;
  city?: string;
  state?: string;
  buildingUse?: string;
  heightM?: number | null;
  areaM2?: number | null;
  downConductorsCount?: number | null;

  /** Edificações da OS (dados por edificação). Os campos de edificação abaixo são derivados delas. */
  buildings?: SpdaBuilding[];

  // SPDA (protectionLevel = NP mais restritivo entre as edificações; '' = sem valor)
  protectionLevel: ProtectionLevel | '';
  method: SpdaMethod;
  inspectionKind: InspectionKind;
  /** Áreas classificadas, corrosão severa ou serviços essenciais: periodicidade de 1 ano */
  criticalStructure: boolean;
  inspectionDate: string;
  soilCondition?: 'seco' | 'umido' | 'encharcado';
  weather?: string;

  // execução
  instruments: InstrumentInfo[];
  technicianName: string;
  technicianTitle?: string;
  technicianCrea?: string;

  checklist: ChecklistAnswer[];
  continuity: ContinuityTest[];
  grounding: GroundingTest[];
  /** Não conformidades e melhorias registradas por subsistema (fotos + comentários) */
  findings?: SpdaFinding[];

  recommendations?: string;
  /** Textos editáveis do relatório técnico (vazio = texto padrão gerado pelo app) */
  techReport?: TechReportTexts;
  /** Numeração do checklist usada nas respostas (ausente = versão 1) */
  checklistVersion?: number;
  notes?: string;

  /** Laudo: nº próprio (vazio = nº da inspeção), data (AAAA-MM-DD) e hora (HH:MM) de emissão (vazio = momento em que é gerado) */
  laudoNumber?: string;
  laudoDate?: string;
  laudoTime?: string;
  /** Validade do laudo em meses (12, 24 ou 36); vazio = automático: 12 meses */
  validityMonths?: 12 | 24 | 36;

  /** versão do servidor (Supabase) em que este registro se baseia */
  serverUpdatedAt?: string;
  createdAt: string;
  updatedAt: string;
}
