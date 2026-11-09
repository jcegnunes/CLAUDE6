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
  /** Leituras do método de queda de potencial a 52 %, 62 % e 72 % da distância */
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
export type SpdaKind = 'estrutural' | 'natural' | 'externo' | 'isolado' | 'inexistente';

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
  protectionLevel: ProtectionLevel;
  /** Nº de descidas existentes */
  downConductorsCount?: number | null;
  /** Coordenadas geográficas (graus decimais, WGS 84) */
  latitude?: number | null;
  longitude?: number | null;
  /** Imagem aérea / de satélite da edificação (data URL comprimida) */
  aerialImage?: string;
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

export interface SpdaInspection {
  id: string;
  companyId: string;
  /** Número do laudo (ex.: SPDA-2026-0001) */
  number: string;
  status: 'rascunho' | 'concluido';

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

  // SPDA (protectionLevel = NP mais restritivo entre as edificações)
  protectionLevel: ProtectionLevel;
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

  recommendations?: string;
  notes?: string;

  createdAt: string;
  updatedAt: string;
}
