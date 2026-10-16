/** Tipos do módulo Gestão ADM (contas a pagar/receber, fluxo de caixa e precificação). */

/** Campos de controle comuns a todos os registros do módulo. */
export interface GestaoRecordBase {
  id: string;
  companyId: string;
  createdAt: string;
  updatedAt: string;
  /** Versão do servidor em que a última edição local se baseou (conflitos). */
  serverUpdatedAt?: string;
}

// ------------------------------------------------------------ contas (lançamentos)
export type EntryKind = 'pagar' | 'receber';
export type EntryStatus = 'aberto' | 'cancelado';
/** Situação calculada (não gravada): depende dos pagamentos e da data de hoje. */
export type EntrySituation = 'aberto' | 'vencido' | 'parcial' | 'pago' | 'cancelado';

export type PaymentMethod = 'pix' | 'boleto' | 'transferencia' | 'dinheiro' | 'cartao' | 'cheque' | 'outro';

export interface EntryPayment {
  id: string;
  /** AAAA-MM-DD */
  date: string;
  amount: number;
  method: PaymentMethod;
  accountId?: string;
  notes?: string;
}

/** Conta a pagar ou a receber (uma parcela). */
export interface FinanceEntry extends GestaoRecordBase {
  kind: EntryKind;
  description: string;
  category: string;
  /** Cliente (a receber) ou fornecedor (a pagar) */
  counterpartyName: string;
  clientId?: string;
  documentNumber?: string;
  /** AAAA-MM-DD */
  issueDate: string;
  /** AAAA-MM-DD */
  dueDate: string;
  amount: number;
  payments: EntryPayment[];
  status: EntryStatus;
  cancelReason?: string;
  /** Parcelamento / recorrência: lançamentos do mesmo grupo */
  groupId?: string;
  installment?: number;
  installments?: number;
  /** Origem: ordem de serviço ou precificação aprovada */
  serviceOrderId?: string;
  pricingId?: string;
  budgetId?: string;
  accountId?: string;
  notes?: string;
}

// ------------------------------------------------------------- configuração de custos
export interface BankAccount {
  id: string;
  name: string;
  /** Saldo na data de abertura (R$) */
  openingBalance: number;
}

/**
 * Valores de referência da precificação — os mesmos da planilha
 * "Precificação Serviços JVM" (bloco "Valores de Referência" e percentuais).
 */
export interface PricingReferenceValues {
  /** Valor da diária do ajudante (R$) */
  helperDailyRate: number;
  /** Valor da diária de engenharia (R$) */
  engineerDailyRate: number;
  /** Valor da refeição (R$) */
  mealCost: number;
  /** Valor da passagem ida e volta do ajudante (R$/dia) */
  busFare: number;
  /** Valor da gasolina (R$/litro) */
  fuelPrice: number;
  /** Consumo do veículo (km/litro) — 10 na planilha */
  kmPerLiter: number;
  /** Valor da visita técnica / orçamento (R$ por visita) */
  technicalVisitCost: number;
  /** Custos fixos por serviço: contador, água, luz, uniforme, EPI (R$) */
  fixedCosts: number;
  /** Manutenção por km: seguro + manutenção + IPVA (R$/km) */
  vehicleCostPerKm: number;
  /** Outras despesas sobre os materiais (%) — 10% na planilha */
  materialsOtherPercent: number;
  /** Impostos sobre os materiais (%) — 12% na planilha */
  materialsTaxPercent: number;
  /** Imposto da nota (%), sobre subtotal + margem — 10% na planilha */
  taxPercent: number;
  /** Margem JVM (%), sobre o subtotal operacional — 30% na planilha */
  marginPercent: number;
  /** Percentual de desconto (%), somado ao total da nota — 13% na planilha */
  negotiationPercent: number;
}

/** Despesas e parâmetros pré-configurados da empresa (um registro por empresa). */
export interface GestaoSettings extends GestaoRecordBase, PricingReferenceValues {
  /** Categorias usadas nas contas */
  payableCategories: string[];
  receivableCategories: string[];
  accounts: BankAccount[];
  /** Data a partir da qual valem os saldos iniciais das contas (AAAA-MM-DD) */
  openingDate: string;
}

// ------------------------------------------------------------------ precificação
/** Campos de "Locação de Homem Hora - HH" e "Custo dos Materiais" da planilha. */
export interface PricingInput {
  /** Qt de dias trabalhados */
  days: number;
  /** Qtd de ajudantes */
  helpers: number;
  /** Qtd de engenheiros */
  engineers: number;
  /** Distância percorrida por dia (km/dia) */
  kmPerDay: number;
  /** Qt de visitas para orçamento */
  visits: number;
  /** Valor dos materiais (R$) */
  materials: number;
  /** Locações (R$) */
  rentals: number;
  /** Percentuais desta precificação (vazio = usa os da configuração) */
  marginPercent?: number;
  taxPercent?: number;
  negotiationPercent?: number;
}

export interface PricingBreakdown {
  // Custos por despesas
  helperLabor: number;
  engineerLabor: number;
  engineerMeals: number;
  helperMeals: number;
  helperFares: number;
  fuel: number;
  technicalVisits: number;
  vehicle: number;
  fixedCosts: number;
  // Custo dos materiais
  materials: number;
  materialsOther: number;
  materialsTax: number;
  rentals: number;
  // Previsão de despesas
  totalMaterials: number;
  helperTotal: number;
  engineeringTotal: number;
  operationalTotal: number;
  /** Subtotal operacional */
  subtotal: number;
  marginPercent: number;
  marginValue: number;
  taxPercent: number;
  taxValue: number;
  /** Total da nota */
  invoiceTotal: number;
  negotiationPercent: number;
  negotiationValue: number;
  /** Valor da proposta */
  finalPrice: number;
  /** Lucro previsto: proposta − subtotal − imposto */
  profit: number;
  /** Lucro / valor da proposta (%) */
  effectiveMarginPercent: number;
}

/** "Despesas Realizadas" da planilha, lançadas depois da execução. */
export interface RealizedExpenses {
  materials: number;
  thirdPartyLabor: number;
  engineeringLabor: number;
  operational: number;
}

export type PricingStatus = 'rascunho' | 'enviada' | 'aprovada' | 'recusada';

export interface ServicePricing extends GestaoRecordBase {
  number: string;
  /** Descrição do serviço */
  title: string;
  clientId?: string;
  clientName?: string;
  /** Responsável no cliente e e-mail */
  responsibleName?: string;
  responsibleEmail?: string;
  serviceOrderId?: string;
  /** AAAA-MM-DD */
  date: string;
  /** Dias de validade da proposta */
  validityDays: number;
  input: PricingInput;
  /** Resultado no momento em que foi salva (os custos configurados podem mudar) */
  result: PricingBreakdown;
  /** Valores de referência usados no cálculo (cópia da configuração no momento do cálculo) */
  costsSnapshot: PricingReferenceValues;
  status: PricingStatus;
  /** Condições de pagamento */
  paymentTerms?: string;
  /** Observação */
  notes?: string;
  realized?: RealizedExpenses;
  /** Contas a receber geradas na aprovação */
  receivableGroupId?: string;
}

// ---------------------------------------------------------- cadastro de serviços
/** Como o serviço é cobrado no orçamento */
export type ServicePriceMode = 'planilha' | 'fixo';

/** Serviço do catálogo da empresa (menu "Serviços"). */
export interface CatalogService extends GestaoRecordBase {
  code: string;
  name: string;
  category: string;
  /** Texto que vai para o orçamento/proposta */
  description: string;
  /** Unidade: serviço, un, dia, hora, m², ponto... */
  unit: string;
  mode: ServicePriceMode;
  /** Parâmetros da planilha usados como padrão (modo "planilha") */
  defaultInput: PricingInput;
  /** Preço unitário (modo "fixo") */
  unitPrice: number;
  /** Normas aplicáveis (ex.: NR-10, ABNT NBR 16295) */
  normReference?: string;
  active: boolean;
}

// ---------------------------------------------------------------- orçamentos
/**
 * Origem e cálculo do item do orçamento:
 *  planilha     — calculado aqui pela planilha (serviço cadastrado ou digitado)
 *  fixo         — preço unitário informado
 *  precificacao — trazido de uma precificação já feita (mantém o preço dela)
 */
export type BudgetItemMode = ServicePriceMode | 'precificacao';

export interface BudgetItem {
  id: string;
  serviceId?: string;
  pricingId?: string;
  pricingNumber?: string;
  code?: string;
  name: string;
  description: string;
  unit: string;
  quantity: number;
  mode: BudgetItemMode;
  /** Modos "planilha" e "precificacao": parâmetros e resultado do cálculo */
  input?: PricingInput;
  result?: PricingBreakdown;
  /** Preço unitário (planilha: valor da proposta calculado) */
  unitPrice: number;
  /** quantidade × preço unitário */
  total: number;
}

export interface Budget extends GestaoRecordBase {
  number: string;
  /** Descrição geral do orçamento */
  title: string;
  clientId?: string;
  clientName?: string;
  responsibleName?: string;
  responsibleEmail?: string;
  serviceOrderId?: string;
  /** AAAA-MM-DD */
  date: string;
  validityDays: number;
  items: BudgetItem[];
  /** Valores de referência usados nos itens "planilha" */
  costsSnapshot: PricingReferenceValues;
  total: number;
  status: PricingStatus;
  paymentTerms?: string;
  /** Prazo de execução (texto livre, vai na proposta) */
  executionTime?: string;
  notes?: string;
  receivableGroupId?: string;
}

// ------------------------------------------------------------ sincronização
export type GestaoTable = 'gestao_entries' | 'gestao_pricings' | 'gestao_settings' | 'gestao_services' | 'gestao_budgets';

export interface GestaoQueueItem {
  table: GestaoTable;
  id: string;
  action: 'upsert' | 'delete';
  attempts: number;
  lastError?: string;
}

export interface GestaoConflict {
  table: GestaoTable;
  id: string;
  label: string;
  detectedAt: string;
}
