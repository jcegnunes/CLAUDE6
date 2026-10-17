/** Regras do módulo Gestão ADM (funções puras, testadas em __tests__). */
import type {
  Budget, BudgetItem, CatalogService, EntryKind, EntrySituation, FinanceEntry, GestaoSettings, PricingBreakdown, PricingInput,
  PricingReferenceValues, ServicePricing
} from './types';

/** Precificações (cálculo avulso) e orçamentos (proposta com vários serviços) */
export const PRICING_PREFIX = 'PRC-';
export const BUDGET_PREFIX = 'ORC-';
/** Contas que vencem nos próximos N dias aparecem como "a vencer" no painel */
export const DUE_SOON_DAYS = 7;

// ------------------------------------------------------------------- utilidades
export function newId(prefix: string): string {
  const rand = Math.random().toString(36).slice(2, 8);
  return `${prefix}-${Date.now().toString(36)}-${rand}`;
}

/** Data de hoje no fuso do aparelho (AAAA-MM-DD). */
export function todayIso(date: Date = new Date()): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

export function formatDateBr(iso?: string): string {
  if (!iso || !/^\d{4}-\d{2}-\d{2}/.test(iso)) return iso || '';
  const [y, m, d] = iso.slice(0, 10).split('-');
  return `${d}/${m}/${y}`;
}

/** Arredonda para centavos (evita 0,1 + 0,2 = 0,30000000000000004). */
export function roundMoney(value: number): number {
  return Math.round((Number(value) || 0) * 100 + Number.EPSILON) / 100;
}

const money = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
export function formatMoney(value: number): string {
  return money.format(Number(value) || 0);
}

const num = (v: unknown) => (Number.isFinite(Number(v)) ? Number(v) : 0);
const nonNeg = (v: unknown) => Math.max(0, num(v));

/** Dias corridos de a até b (b − a). */
export function daysBetween(a: string, b: string): number {
  const ta = Date.UTC(+a.slice(0, 4), +a.slice(5, 7) - 1, +a.slice(8, 10));
  const tb = Date.UTC(+b.slice(0, 4), +b.slice(5, 7) - 1, +b.slice(8, 10));
  return Math.round((tb - ta) / 86_400_000);
}

/**
 * Soma meses mantendo o dia; se o mês não tem o dia (ex.: 31), usa o último
 * dia do mês (31/01 + 1 mês = 28/02 ou 29/02).
 */
export function addMonths(iso: string, months: number): string {
  const y = +iso.slice(0, 4);
  const m = +iso.slice(5, 7) - 1 + months;
  const d = +iso.slice(8, 10);
  const target = new Date(Date.UTC(y, m, 1));
  const lastDay = new Date(Date.UTC(target.getUTCFullYear(), target.getUTCMonth() + 1, 0)).getUTCDate();
  target.setUTCDate(Math.min(d, lastDay));
  return target.toISOString().slice(0, 10);
}

export function addDays(iso: string, days: number): string {
  const t = new Date(Date.UTC(+iso.slice(0, 4), +iso.slice(5, 7) - 1, +iso.slice(8, 10) + days));
  return t.toISOString().slice(0, 10);
}

// ------------------------------------------------------------------ configuração
export const DEFAULT_PAYABLE_CATEGORIES = [
  'Mão de obra / salários', 'Encargos e benefícios', 'Combustível', 'Alimentação', 'Hospedagem',
  'Manutenção de veículos', 'Materiais', 'Locação de equipamentos', 'Calibração de instrumentos',
  'Serviços de terceiros', 'Impostos', 'Aluguel', 'Contabilidade', 'Sistemas e telefonia', 'Outras despesas'
];

export const DEFAULT_RECEIVABLE_CATEGORIES = [
  'Ensaios de EPI/EPC', 'Treinamentos', 'Serviços de engenharia', 'Manutenção', 'Laudos e inspeções', 'Outras receitas'
];

/** Valores de referência da planilha "Precificação Serviços JVM 2026 V3.2". */
export const DEFAULT_REFERENCE_VALUES: PricingReferenceValues = {
  helperDailyRate: 250,
  engineerDailyRate: 650,
  mealCost: 45,
  busFare: 20,
  fuelPrice: 6.7,
  kmPerLiter: 10,
  technicalVisitCost: 250,
  fixedCosts: 350,
  vehicleCostPerKm: 0.5,
  materialsOtherPercent: 10,
  materialsTaxPercent: 12,
  taxPercent: 10,
  marginPercent: 30,
  negotiationPercent: 13
};

/** Configuração inicial: valores de referência da planilha, ajustáveis pela empresa. */
export function defaultSettings(companyId: string): GestaoSettings {
  return {
    id: settingsId(companyId),
    companyId,
    createdAt: '',
    updatedAt: '',
    ...DEFAULT_REFERENCE_VALUES,
    payableCategories: [...DEFAULT_PAYABLE_CATEGORIES],
    receivableCategories: [...DEFAULT_RECEIVABLE_CATEGORIES],
    accounts: [{ id: 'caixa', name: 'Conta principal', openingBalance: 0 }],
    openingDate: todayIso()
  };
}

export function settingsId(companyId: string): string {
  return `gcfg-${companyId}`;
}

// ------------------------------------------------------------------ precificação
/** Valores iniciais da planilha: 1 dia, 1 engenheiro, 75 km/dia. */
export function emptyPricingInput(): PricingInput {
  return { days: 1, helpers: 0, engineers: 1, kmPerDay: 75, visits: 0, materials: 0, rentals: 0 };
}

/** Completa uma precificação gravada com campos que faltem (versões anteriores do módulo). */
export function normalizePricingInput(input: Partial<PricingInput> | undefined): PricingInput {
  const base = emptyPricingInput();
  const src = input || {};
  const pick = (k: 'days' | 'helpers' | 'engineers' | 'kmPerDay' | 'visits' | 'materials' | 'rentals') =>
    (src[k] !== undefined && src[k] !== null && Number.isFinite(Number(src[k])) ? Number(src[k]) : base[k]);
  return {
    days: pick('days'), helpers: pick('helpers'), engineers: pick('engineers'), kmPerDay: pick('kmPerDay'),
    visits: pick('visits'), materials: pick('materials'), rentals: pick('rentals'),
    marginPercent: src.marginPercent, taxPercent: src.taxPercent, negotiationPercent: src.negotiationPercent
  };
}

/**
 * Cálculo da planilha "Precificação Serviços JVM", célula a célula:
 *
 *  Custos por despesas
 *   Mão de obra colaborador   = diária ajudante × ajudantes × dias          (J27)
 *   Mão de obra engenharia    = dias × engenheiros × diária engenharia      (J28)
 *   Refeição engenharia       = dias × refeição × engenheiros               (J29)
 *   Refeição colaborador      = ajudantes × dias × refeição                 (J30)
 *   Passagem colaborador      = dias × ajudantes × passagem                 (J31)
 *   Combustível               = km/dia ÷ consumo × gasolina × dias          (J32)
 *   Visita técnica            = visitas × valor da visita                   (J33)
 *   Manutenção do veículo     = R$/km × km/dia × dias                       (J34)
 *  Materiais: outras despesas = materiais × 10%; impostos = materiais × 12%  (J37, E38)
 *  Previsão de despesas
 *   Total dos materiais       = materiais + impostos + outras + locações    (B44)
 *   Mão de obra colaborador   = J27 + J30 + J31                             (F44)
 *   Mão de obra engenharia    = J28 + J29 + J33                             (H44)
 *   Despesas operacionais     = J32 + J34 + custos fixos                    (J44)
 *   Subtotal operacional      = soma dos quatro                             (B46)
 *   Margem JVM                = subtotal × 30%                              (H46)
 *   Imposto                   = (subtotal + margem) × 10%                   (F46)
 *   Total da nota             = subtotal + imposto + margem                 (J46)
 *   Percentual de desconto    = total da nota × 13%                         (H47)
 *   Valor da proposta         = total da nota + percentual de desconto      (J47)
 *
 * Única diferença da planilha: lá o combustível soma a quantidade de visitas
 * ao preço da gasolina ((km ÷ 10) × (gasolina + visitas) × dias); aqui o
 * custo das visitas entra só na linha "Visita técnica".
 */
export function calculatePricing(rawInput: Partial<PricingInput>, ref: PricingReferenceValues): PricingBreakdown {
  const input = normalizePricingInput(rawInput);
  const days = nonNeg(input.days);
  const helpers = nonNeg(input.helpers);
  const engineers = nonNeg(input.engineers);
  const kmPerDay = nonNeg(input.kmPerDay);
  const kmPerLiter = nonNeg(ref.kmPerLiter);

  const helperLabor = nonNeg(ref.helperDailyRate) * helpers * days;
  const engineerLabor = days * engineers * nonNeg(ref.engineerDailyRate);
  const engineerMeals = days * nonNeg(ref.mealCost) * engineers;
  const helperMeals = helpers * days * nonNeg(ref.mealCost);
  const helperFares = days * helpers * nonNeg(ref.busFare);
  const fuel = kmPerLiter > 0 ? (kmPerDay / kmPerLiter) * nonNeg(ref.fuelPrice) * days : 0;
  const technicalVisits = nonNeg(input.visits) * nonNeg(ref.technicalVisitCost);
  const vehicle = nonNeg(ref.vehicleCostPerKm) * kmPerDay * days;
  const fixedCosts = nonNeg(ref.fixedCosts);

  const materials = nonNeg(input.materials);
  const materialsOther = materials * (nonNeg(ref.materialsOtherPercent) / 100);
  const materialsTax = materials * (nonNeg(ref.materialsTaxPercent) / 100);
  const rentals = nonNeg(input.rentals);

  const totalMaterials = materials + materialsTax + materialsOther + rentals;
  const helperTotal = helperLabor + helperMeals + helperFares;
  const engineeringTotal = engineerLabor + engineerMeals + technicalVisits;
  const operationalTotal = fuel + vehicle + fixedCosts;
  const subtotal = totalMaterials + helperTotal + engineeringTotal + operationalTotal;

  const pick = (v: number | undefined, fallback: number) => (v === undefined || v === null || !Number.isFinite(Number(v)) ? nonNeg(fallback) : nonNeg(v));
  const marginPercent = pick(input.marginPercent, ref.marginPercent);
  const taxPercent = pick(input.taxPercent, ref.taxPercent);
  const negotiationPercent = pick(input.negotiationPercent, ref.negotiationPercent);

  const marginValue = subtotal * (marginPercent / 100);
  const taxValue = (subtotal + marginValue) * (taxPercent / 100);
  const invoiceTotal = subtotal + taxValue + marginValue;
  const negotiationValue = invoiceTotal * (negotiationPercent / 100);
  const finalPrice = invoiceTotal + negotiationValue;
  const profit = finalPrice - subtotal - taxValue;

  const r = roundMoney;
  return {
    helperLabor: r(helperLabor), engineerLabor: r(engineerLabor), engineerMeals: r(engineerMeals), helperMeals: r(helperMeals),
    helperFares: r(helperFares), fuel: r(fuel), technicalVisits: r(technicalVisits), vehicle: r(vehicle), fixedCosts: r(fixedCosts),
    materials: r(materials), materialsOther: r(materialsOther), materialsTax: r(materialsTax), rentals: r(rentals),
    totalMaterials: r(totalMaterials), helperTotal: r(helperTotal), engineeringTotal: r(engineeringTotal), operationalTotal: r(operationalTotal),
    subtotal: r(subtotal), marginPercent, marginValue: r(marginValue), taxPercent, taxValue: r(taxValue),
    invoiceTotal: r(invoiceTotal), negotiationPercent, negotiationValue: r(negotiationValue), finalPrice: r(finalPrice),
    profit: r(profit), effectiveMarginPercent: finalPrice > 0 ? Math.round((profit / finalPrice) * 10000) / 100 : 0
  };
}

/** Valores de referência da configuração usados no cálculo (guardados junto da precificação). */
export function costsFromSettings(s: Partial<PricingReferenceValues>): PricingReferenceValues {
  const out = { ...DEFAULT_REFERENCE_VALUES };
  (Object.keys(DEFAULT_REFERENCE_VALUES) as Array<keyof PricingReferenceValues>).forEach(k => {
    const v = s[k];
    if (v !== undefined && v !== null && Number.isFinite(Number(v))) out[k] = Number(v);
  });
  return out;
}

/** "Despesas Realizadas": total gasto e lucro real (valor da proposta − realizado − imposto previsto). */
export function realizedSummary(p: Pick<ServicePricing, 'realized' | 'result'>): { total: number; profit: number; difference: number } | null {
  const r = p.realized;
  if (!r) return null;
  const total = roundMoney(nonNeg(r.materials) + nonNeg(r.thirdPartyLabor) + nonNeg(r.engineeringLabor) + nonNeg(r.operational));
  if (!(total > 0)) return null;
  return {
    total,
    profit: roundMoney(p.result.finalPrice - total - p.result.taxValue),
    difference: roundMoney(total - p.result.subtotal)
  };
}

// ---------------------------------------------------------- serviços e orçamentos
export const DEFAULT_SERVICE_CATEGORIES = [
  'Ensaios de EPI/EPC', 'Treinamentos', 'Inspeções e laudos', 'SPDA e aterramento', 'Manutenção elétrica',
  'Subestações e média tensão', 'Termografia', 'Projetos e consultoria', 'Outros serviços'
];

export const SERVICE_UNITS = ['serviço', 'un', 'peça', 'dia', 'hora', 'visita', 'ponto', 'm²', 'm', 'kit', 'turma', 'aluno'];

/** Calcula preço unitário e total do item (planilha: valor da proposta da planilha). */
export function calculateBudgetItem(item: BudgetItem, costs: PricingReferenceValues): BudgetItem {
  const quantity = nonNeg(item.quantity);
  if (item.mode === 'precificacao') {
    // preço da precificação de origem: não recalcula com os valores atuais
    const unitPrice = roundMoney(nonNeg(item.result?.finalPrice ?? item.unitPrice));
    return { ...item, quantity, unitPrice, total: roundMoney(unitPrice * quantity) };
  }
  if (item.mode === 'planilha') {
    const input = normalizePricingInput(item.input);
    const result = calculatePricing(input, costs);
    return { ...item, quantity, input, result, unitPrice: result.finalPrice, total: roundMoney(result.finalPrice * quantity) };
  }
  const unitPrice = roundMoney(nonNeg(item.unitPrice));
  const { input: _i, result: _r, ...rest } = item;
  return { ...rest, quantity, unitPrice, total: roundMoney(unitPrice * quantity) };
}

/** Item novo a partir do cadastro de serviços. */
export function budgetItemFromService(service: CatalogService, costs: PricingReferenceValues): BudgetItem {
  return calculateBudgetItem({
    id: newId('itm'),
    serviceId: service.id,
    code: service.code,
    name: service.name,
    description: service.description,
    unit: service.unit || 'serviço',
    quantity: 1,
    mode: service.mode,
    input: service.mode === 'planilha' ? normalizePricingInput(service.defaultInput) : undefined,
    unitPrice: service.unitPrice,
    total: 0
  }, costs);
}

/** Item a partir de uma precificação já feita (mesmo preço, mesmos parâmetros). */
export function budgetItemFromPricing(p: ServicePricing): BudgetItem {
  return calculateBudgetItem({
    id: newId('itm'),
    pricingId: p.id,
    pricingNumber: p.number,
    name: p.title,
    description: '',
    unit: 'serviço',
    quantity: 1,
    mode: 'precificacao',
    input: normalizePricingInput(p.input),
    result: p.result,
    unitPrice: p.result?.finalPrice ?? 0,
    total: 0
  }, DEFAULT_REFERENCE_VALUES);
}

/** Item digitado na hora (sem cadastro). */
export function manualBudgetItem(mode: 'planilha' | 'fixo', costs: PricingReferenceValues): BudgetItem {
  return calculateBudgetItem({
    id: newId('itm'), name: '', description: '', unit: 'serviço', quantity: 1, mode,
    input: mode === 'planilha' ? emptyPricingInput() : undefined, unitPrice: 0, total: 0
  }, costs);
}

export function budgetTotal(items: BudgetItem[]): number {
  return roundMoney((items || []).reduce((s, i) => s + nonNeg(i.total), 0));
}

/**
 * Resumo interno do orçamento (não vai para o cliente): custo previsto e lucro
 * dos itens calculados pela planilha; itens de preço fixo não têm custo.
 */
export function budgetSummary(b: Pick<Budget, 'items'>): { total: number; plannedCost: number; plannedProfit: number; taxValue: number; fixedItemsTotal: number } {
  let plannedCost = 0; let plannedProfit = 0; let taxValue = 0; let fixedItemsTotal = 0;
  (b.items || []).forEach(i => {
    const q = nonNeg(i.quantity);
    if ((i.mode === 'planilha' || i.mode === 'precificacao') && i.result) {
      plannedCost += i.result.subtotal * q;
      plannedProfit += i.result.profit * q;
      taxValue += i.result.taxValue * q;
    } else {
      fixedItemsTotal += nonNeg(i.total);
    }
  });
  return {
    total: budgetTotal(b.items), plannedCost: roundMoney(plannedCost), plannedProfit: roundMoney(plannedProfit),
    taxValue: roundMoney(taxValue), fixedItemsTotal: roundMoney(fixedItemsTotal)
  };
}

export function pricingExpiryDate(p: Pick<ServicePricing, 'date' | 'validityDays'>): string {
  return addDays(p.date, nonNeg(p.validityDays));
}

// ---------------------------------------------------------------------- contas
export function paidAmount(entry: Pick<FinanceEntry, 'payments'>): number {
  return roundMoney((entry.payments || []).reduce((s, p) => s + num(p.amount), 0));
}

export function openAmount(entry: Pick<FinanceEntry, 'payments' | 'amount' | 'status'>): number {
  if (entry.status === 'cancelado') return 0;
  return Math.max(0, roundMoney(num(entry.amount) - paidAmount(entry)));
}

export function entrySituation(entry: Pick<FinanceEntry, 'payments' | 'amount' | 'status' | 'dueDate'>, today = todayIso()): EntrySituation {
  if (entry.status === 'cancelado') return 'cancelado';
  const paid = paidAmount(entry);
  if (paid >= roundMoney(num(entry.amount)) && num(entry.amount) > 0) return 'pago';
  if (entry.dueDate < today) return 'vencido';
  return paid > 0 ? 'parcial' : 'aberto';
}

export const SITUATION_LABEL: Record<EntrySituation, string> = {
  aberto: 'Em aberto',
  vencido: 'Vencido',
  parcial: 'Pago em parte',
  pago: 'Quitado',
  cancelado: 'Cancelado'
};

export const KIND_LABEL: Record<EntryKind, string> = { pagar: 'A pagar', receber: 'A receber' };

/**
 * Divide o valor em parcelas mensais. Os centavos que sobram da divisão vão
 * para a primeira parcela, para a soma bater exatamente com o total.
 */
export function splitInstallments(total: number, count: number, firstDueDate: string, intervalMonths = 1): Array<{ amount: number; dueDate: string; installment: number }> {
  const n = Math.max(1, Math.floor(num(count)));
  const cents = Math.round(roundMoney(total) * 100);
  const base = Math.floor(cents / n);
  const rest = cents - base * n;
  return Array.from({ length: n }, (_, i) => ({
    amount: (base + (i === 0 ? rest : 0)) / 100,
    dueDate: addMonths(firstDueDate, i * intervalMonths),
    installment: i + 1
  }));
}

// -------------------------------------------------------------- fluxo de caixa
export interface CashFlowMovement {
  date: string;
  kind: EntryKind;
  amount: number;
  /** realizado = pagamento registrado; previsto = saldo em aberto pelo vencimento */
  realized: boolean;
  entryId: string;
  description: string;
  counterpartyName: string;
  accountId?: string;
}

/**
 * Movimentos do fluxo de caixa: cada pagamento registrado é "realizado" na data
 * em que foi pago; o que falta pagar é "previsto" na data de vencimento (contas
 * vencidas entram como previstas para hoje, pois ainda vão entrar/sair).
 */
export function cashFlowMovements(entries: FinanceEntry[], today = todayIso()): CashFlowMovement[] {
  const out: CashFlowMovement[] = [];
  entries.forEach(e => {
    if (e.status === 'cancelado') return;
    (e.payments || []).forEach(p => {
      out.push({ date: p.date, kind: e.kind, amount: roundMoney(num(p.amount)), realized: true, entryId: e.id, description: e.description, counterpartyName: e.counterpartyName, accountId: p.accountId || e.accountId });
    });
    const open = openAmount(e);
    if (open > 0) {
      out.push({ date: e.dueDate < today ? today : e.dueDate, kind: e.kind, amount: open, realized: false, entryId: e.id, description: e.description, counterpartyName: e.counterpartyName, accountId: e.accountId });
    }
  });
  return out.sort((a, b) => a.date.localeCompare(b.date));
}

export interface CashFlowPeriod {
  /** AAAA-MM-DD (dia) ou AAAA-MM (mês) */
  key: string;
  inRealized: number;
  outRealized: number;
  inForecast: number;
  outForecast: number;
  /** Saldo ao fim do período (realizado + previsto acumulados) */
  balance: number;
}

/** Saldo inicial: soma das contas na data de abertura. */
export function openingBalance(settings: Pick<GestaoSettings, 'accounts'>): number {
  return roundMoney((settings.accounts || []).reduce((s, a) => s + num(a.openingBalance), 0));
}

/**
 * Agrupa os movimentos por dia ('day') ou mês ('month') entre from e to
 * (inclusive). O saldo começa no saldo inicial + tudo o que aconteceu entre a
 * data de abertura e o início do período.
 */
export function cashFlowByPeriod(
  movements: CashFlowMovement[],
  opts: { from: string; to: string; granularity: 'day' | 'month'; opening: number; openingDate: string }
): { startBalance: number; periods: CashFlowPeriod[] } {
  const keyOf = (date: string) => (opts.granularity === 'day' ? date.slice(0, 10) : date.slice(0, 7));
  const signed = (m: CashFlowMovement) => (m.kind === 'receber' ? m.amount : -m.amount);
  const counted = movements.filter(m => m.date >= opts.openingDate);
  const startBalance = roundMoney(opts.opening + counted.filter(m => m.date < opts.from).reduce((s, m) => s + signed(m), 0));

  const keys: string[] = [];
  if (opts.granularity === 'day') {
    for (let d = opts.from; d <= opts.to; d = addDays(d, 1)) keys.push(d);
  } else {
    for (let d = `${opts.from.slice(0, 7)}-01`; d.slice(0, 7) <= opts.to.slice(0, 7); d = addMonths(d, 1)) keys.push(d.slice(0, 7));
  }
  const map = new Map<string, CashFlowPeriod>(keys.map(k => [k, { key: k, inRealized: 0, outRealized: 0, inForecast: 0, outForecast: 0, balance: 0 }]));
  counted.filter(m => m.date >= opts.from && m.date <= opts.to).forEach(m => {
    const p = map.get(keyOf(m.date));
    if (!p) return;
    if (m.kind === 'receber') { if (m.realized) p.inRealized += m.amount; else p.inForecast += m.amount; }
    else if (m.realized) p.outRealized += m.amount; else p.outForecast += m.amount;
  });
  let balance = startBalance;
  const periods = keys.map(k => {
    const p = map.get(k)!;
    balance += p.inRealized + p.inForecast - p.outRealized - p.outForecast;
    return {
      ...p,
      inRealized: roundMoney(p.inRealized), outRealized: roundMoney(p.outRealized),
      inForecast: roundMoney(p.inForecast), outForecast: roundMoney(p.outForecast),
      balance: roundMoney(balance)
    };
  });
  return { startBalance, periods };
}

/** Saldo realizado até hoje (saldo inicial + pagamentos registrados). */
export function currentBalance(movements: CashFlowMovement[], opening: number, openingDate: string, today = todayIso()): number {
  return roundMoney(movements
    .filter(m => m.realized && m.date >= openingDate && m.date <= today)
    .reduce((s, m) => s + (m.kind === 'receber' ? m.amount : -m.amount), opening));
}
