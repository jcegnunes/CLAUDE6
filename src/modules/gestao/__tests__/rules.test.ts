import { describe, expect, it } from 'vitest';
import {
  DEFAULT_REFERENCE_VALUES, addMonths, calculatePricing, cashFlowByPeriod, cashFlowMovements, costsFromSettings, currentBalance,
  defaultSettings, emptyPricingInput, entrySituation, normalizePricingInput, openAmount, realizedSummary, splitInstallments
} from '../rules';
import type { FinanceEntry, PricingInput } from '../types';

const ref = costsFromSettings(defaultSettings('comp-1'));

describe('Precificação — planilha "Precificação Serviços JVM 2026 V3.2"', () => {
  it('valores de referência iguais aos da planilha', () => {
    expect(ref).toEqual({
      helperDailyRate: 250, engineerDailyRate: 650, mealCost: 45, busFare: 20, fuelPrice: 6.7, kmPerLiter: 10,
      technicalVisitCost: 250, fixedCosts: 350, vehicleCostPerKm: 0.5, materialsOtherPercent: 10, materialsTaxPercent: 12,
      taxPercent: 10, marginPercent: 30, negotiationPercent: 13
    });
    expect(emptyPricingInput()).toEqual({ days: 1, helpers: 0, engineers: 1, kmPerDay: 75, visits: 0, materials: 0, rentals: 0 });
  });

  it('exemplo da planilha (1 dia, 1 engenheiro, 75 km/dia): mesmos valores das células', () => {
    const r = calculatePricing(emptyPricingInput(), ref);
    expect(r.engineerLabor).toBe(650);       // J28
    expect(r.engineerMeals).toBe(45);        // J29
    expect(r.fuel).toBe(50.25);              // J32
    expect(r.vehicle).toBe(37.5);            // J34
    expect(r.engineeringTotal).toBe(695);    // H44
    expect(r.operationalTotal).toBe(437.75); // J44
    expect(r.subtotal).toBe(1132.75);        // B46
    expect(r.taxValue).toBe(147.26);         // F46
    expect(r.marginValue).toBe(339.83);      // H46
    expect(r.invoiceTotal).toBe(1619.83);    // J46
    expect(r.negotiationValue).toBe(210.58); // H47
    expect(r.finalPrice).toBe(1830.41);      // J47 — valor da proposta
  });

  it('ajudantes, visitas, materiais e locações', () => {
    const input: PricingInput = { days: 3, helpers: 2, engineers: 1, kmPerDay: 120, visits: 1, materials: 1000, rentals: 200 };
    const r = calculatePricing(input, ref);
    expect(r.helperLabor).toBe(1500);        // 250 × 2 × 3
    expect(r.helperMeals).toBe(270);         // 2 × 3 × 45
    expect(r.helperFares).toBe(120);         // 3 × 2 × 20
    expect(r.engineerLabor).toBe(1950);
    expect(r.engineerMeals).toBe(135);
    expect(r.technicalVisits).toBe(250);
    expect(r.fuel).toBe(241.2);              // 120 ÷ 10 × 6,70 × 3 (sem somar as visitas ao preço da gasolina)
    expect(r.vehicle).toBe(180);             // 0,50 × 120 × 3
    expect(r.materialsTax).toBe(120);        // 12%
    expect(r.materialsOther).toBe(100);      // 10%
    expect(r.totalMaterials).toBe(1420);
    expect(r.helperTotal).toBe(1890);
    expect(r.engineeringTotal).toBe(2335);
    expect(r.operationalTotal).toBe(771.2);
    expect(r.subtotal).toBe(6416.2);
    expect(r.marginValue).toBe(1924.86);
    expect(r.taxValue).toBe(834.11);
    expect(r.invoiceTotal).toBe(9175.17);
    expect(r.negotiationValue).toBe(1192.77);
    expect(r.finalPrice).toBe(10367.94);
    expect(r.profit).toBe(3117.63);          // margem + percentual de desconto
  });

  it('percentuais da precificação substituem os da configuração', () => {
    const r = calculatePricing({ ...emptyPricingInput(), marginPercent: 20, taxPercent: 6, negotiationPercent: 0 }, ref);
    expect(r.marginValue).toBe(226.55);
    expect(r.taxValue).toBe(81.56);          // (1.132,75 + 226,55) × 6%
    expect(r.finalPrice).toBe(1440.86);
  });

  it('precificação gravada por versão anterior do módulo é completada com os padrões', () => {
    expect(normalizePricingInput({ days: 2 } as Partial<PricingInput>)).toMatchObject({ days: 2, helpers: 0, engineers: 1, kmPerDay: 75 });
    expect(costsFromSettings({ fuelPrice: 7 })).toEqual({ ...DEFAULT_REFERENCE_VALUES, fuelPrice: 7 });
  });

  it('despesas realizadas: comparação com o previsto e lucro real', () => {
    const result = calculatePricing(emptyPricingInput(), ref);
    expect(realizedSummary({ result, realized: undefined })).toBeNull();
    const s = realizedSummary({ result, realized: { materials: 0, thirdPartyLabor: 0, engineeringLabor: 800, operational: 450 } })!;
    expect(s.total).toBe(1250);
    expect(s.difference).toBe(117.25);       // 1.250 − 1.132,75
    expect(s.profit).toBe(433.15);           // 1.830,41 − 1.250 − 147,26
  });
});


describe('Parcelas e datas', () => {
  it('centavos que sobram ficam na primeira parcela', () => {
    const p = splitInstallments(100, 3, '2026-01-31');
    expect(p.map(x => x.amount)).toEqual([33.34, 33.33, 33.33]);
    expect(p.reduce((s, x) => s + x.amount, 0)).toBeCloseTo(100, 10);
  });

  it('vencimento no dia 31 cai no último dia dos meses mais curtos', () => {
    expect(splitInstallments(100, 3, '2026-01-31').map(x => x.dueDate)).toEqual(['2026-01-31', '2026-02-28', '2026-03-31']);
    expect(addMonths('2028-01-31', 1)).toBe('2028-02-29');
    expect(addMonths('2026-11-15', 2)).toBe('2027-01-15');
  });
});

const entry = (over: Partial<FinanceEntry>): FinanceEntry => ({
  id: 'e', companyId: 'comp-1', createdAt: '', updatedAt: '', kind: 'receber', description: 'x', category: 'c',
  counterpartyName: 'Cliente', issueDate: '2026-10-01', dueDate: '2026-10-20', amount: 500, payments: [], status: 'aberto',
  ...over
});
const pay = (date: string, amount: number) => ({ id: `p-${date}`, date, amount, method: 'pix' as const });

describe('Situação das contas', () => {
  const today = '2026-10-14';
  it('aberto, parcial, vencido, quitado e cancelado', () => {
    expect(entrySituation(entry({}), today)).toBe('aberto');
    expect(entrySituation(entry({ payments: [pay('2026-10-05', 200)] }), today)).toBe('parcial');
    expect(entrySituation(entry({ dueDate: '2026-10-10', payments: [pay('2026-10-05', 200)] }), today)).toBe('vencido');
    expect(entrySituation(entry({ dueDate: '2026-10-10', payments: [pay('2026-10-05', 500)] }), today)).toBe('pago');
    expect(entrySituation(entry({ status: 'cancelado' }), today)).toBe('cancelado');
  });

  it('saldo em aberto (cancelada não tem saldo)', () => {
    expect(openAmount(entry({ payments: [pay('2026-10-05', 199.9)] }))).toBe(300.1);
    expect(openAmount(entry({ status: 'cancelado' }))).toBe(0);
  });
});

describe('Fluxo de caixa', () => {
  const today = '2026-10-14';
  const entries = [
    entry({ id: 'r1', kind: 'receber', amount: 500, dueDate: '2026-10-20', payments: [pay('2026-10-05', 200)] }),
    entry({ id: 'p1', kind: 'pagar', amount: 300, dueDate: '2026-10-10' }),
    entry({ id: 'p2', kind: 'pagar', amount: 80, dueDate: '2026-09-01', payments: [pay('2026-09-01', 80)] }), // antes da abertura
    entry({ id: 'c1', kind: 'pagar', amount: 999, dueDate: '2026-10-15', status: 'cancelado' })
  ];
  const movements = cashFlowMovements(entries, today);

  it('pagamentos são realizados; saldo em aberto é previsto (vencido entra para hoje); cancelada não entra', () => {
    expect(movements.map(m => [m.entryId, m.date, m.amount, m.realized])).toEqual([
      ['p2', '2026-09-01', 80, true],
      ['r1', '2026-10-05', 200, true],
      ['p1', '2026-10-14', 300, false],
      ['r1', '2026-10-20', 300, false]
    ]);
  });

  it('saldo atual: saldo inicial + realizados a partir da data de abertura', () => {
    expect(currentBalance(movements, 1000, '2026-10-01', today)).toBe(1200);
  });

  it('saldo diário e mensal acumulado', () => {
    const daily = cashFlowByPeriod(movements, { from: '2026-10-01', to: '2026-10-31', granularity: 'day', opening: 1000, openingDate: '2026-10-01' });
    expect(daily.startBalance).toBe(1000);
    expect(daily.periods).toHaveLength(31);
    const byDay = Object.fromEntries(daily.periods.map(p => [p.key, p.balance]));
    expect(byDay['2026-10-05']).toBe(1200);
    expect(byDay['2026-10-14']).toBe(900);
    expect(byDay['2026-10-31']).toBe(1200);

    const monthly = cashFlowByPeriod(movements, { from: '2026-10-01', to: '2026-12-31', granularity: 'month', opening: 1000, openingDate: '2026-10-01' });
    expect(monthly.periods.map(p => p.key)).toEqual(['2026-10', '2026-11', '2026-12']);
    expect(monthly.periods[0]).toMatchObject({ inRealized: 200, inForecast: 300, outForecast: 300, balance: 1200 });
    expect(monthly.periods[2].balance).toBe(1200);
  });

  it('período que começa depois: saldo inicial inclui o movimento anterior', () => {
    const nov = cashFlowByPeriod(movements, { from: '2026-11-01', to: '2026-11-30', granularity: 'day', opening: 1000, openingDate: '2026-10-01' });
    expect(nov.startBalance).toBe(1200);
  });
});
