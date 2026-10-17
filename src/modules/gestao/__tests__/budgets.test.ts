import { describe, expect, it } from 'vitest';
import type { jsPDF } from 'jspdf';
import { buildBudgetPdf } from '../proposalPdf';
import {
  budgetItemFromPricing, budgetItemFromService, budgetSummary, budgetTotal, calculateBudgetItem, calculatePricing, costsFromSettings,
  defaultSettings, emptyPricingInput, formatMoney, manualBudgetItem
} from '../rules';
import type { Budget, CatalogService, ServicePricing } from '../types';
import type { CompanyLabInfo } from '../../../types';

const ref = costsFromSettings(defaultSettings('comp-1'));
const base = { companyId: 'comp-1', createdAt: '', updatedAt: '' };

const servicePlanilha: CatalogService = {
  ...base, id: 'srv-1', code: 'SRV-001', name: 'Ensaio de luvas em campo', category: 'Ensaios de EPI/EPC',
  description: 'Ensaio dielétrico conforme NBR 16295', unit: 'serviço', mode: 'planilha',
  defaultInput: emptyPricingInput(), unitPrice: 0, active: true
};
const serviceFixo: CatalogService = {
  ...base, id: 'srv-2', code: 'SRV-002', name: 'Ensaio de luva isolante (laboratório)', category: 'Ensaios de EPI/EPC',
  description: '', unit: 'peça', mode: 'fixo', defaultInput: emptyPricingInput(), unitPrice: 38.5, active: true
};

// precificação feita com valores de referência antigos: o orçamento mantém o preço dela
const oldRef = { ...ref, engineerDailyRate: 500 };
const oldInput = { ...emptyPricingInput(), days: 2 };
const pricing: ServicePricing = {
  ...base, id: 'prc-1', number: 'PRC-2610-0003', title: 'Inspeção de SPDA', date: '2026-10-01', validityDays: 15,
  input: oldInput, result: calculatePricing(oldInput, oldRef), costsSnapshot: oldRef, status: 'enviada'
};

describe('Itens do orçamento', () => {
  it('do cadastro, pela planilha: mesmo valor da planilha', () => {
    const it1 = budgetItemFromService(servicePlanilha, ref);
    expect(it1).toMatchObject({ serviceId: 'srv-1', code: 'SRV-001', mode: 'planilha', unitPrice: 1830.41, total: 1830.41 });
    expect(calculateBudgetItem({ ...it1, quantity: 2 }, ref).total).toBe(3660.82);
  });

  it('do cadastro, preço fixo: quantidade × preço', () => {
    const it2 = calculateBudgetItem({ ...budgetItemFromService(serviceFixo, ref), quantity: 40 }, ref);
    expect(it2).toMatchObject({ mode: 'fixo', unit: 'peça', unitPrice: 38.5, total: 1540 });
    expect(it2.result).toBeUndefined();
  });

  it('da precificação: mantém o preço dela, mesmo com valores de referência diferentes', () => {
    const it3 = budgetItemFromPricing(pricing);
    expect(it3).toMatchObject({ pricingId: 'prc-1', pricingNumber: 'PRC-2610-0003', name: 'Inspeção de SPDA', mode: 'precificacao' });
    expect(it3.unitPrice).toBe(pricing.result.finalPrice);
    expect(calculateBudgetItem(it3, ref).unitPrice).toBe(pricing.result.finalPrice);
    expect(calculatePricing(oldInput, ref).finalPrice).not.toBe(pricing.result.finalPrice);
  });

  it('avulso: planilha começa com os valores iniciais; preço fixo começa zerado', () => {
    expect(manualBudgetItem('planilha', ref)).toMatchObject({ mode: 'planilha', unitPrice: 1830.41, name: '' });
    expect(manualBudgetItem('fixo', ref)).toMatchObject({ mode: 'fixo', unitPrice: 0, total: 0 });
  });

  it('total e resumo interno (custo e lucro só dos itens calculados)', () => {
    const items = [
      budgetItemFromService(servicePlanilha, ref),
      calculateBudgetItem({ ...budgetItemFromService(serviceFixo, ref), quantity: 40 }, ref)
    ];
    expect(budgetTotal(items)).toBe(3370.41);
    const s = budgetSummary({ items });
    expect(s.plannedCost).toBe(1132.75);
    expect(s.plannedProfit).toBe(550.4);
    expect(s.fixedItemsTotal).toBe(1540);
  });
});

/** Texto escrito no PDF (jsPDF grava sem compressão). */
function pdfText(doc: jsPDF): string {
  const raw = Buffer.from(doc.output('arraybuffer')).toString('latin1');
  const parts: string[] = [];
  for (const m of raw.matchAll(/\(((?:\\.|[^\\)])*)\)\s*Tj/g)) {
    parts.push(m[1].replace(/\\([()\\])/g, '$1').replace(/\\(\d{3})/g, (_, o) => String.fromCharCode(parseInt(o, 8))));
  }
  return parts.join('\n').replace(/ /g, ' ');
}

describe('Proposta em PDF do orçamento', () => {
  const items = [
    budgetItemFromService(servicePlanilha, ref),
    calculateBudgetItem({ ...budgetItemFromService(serviceFixo, ref), quantity: 40 }, ref),
    budgetItemFromPricing(pricing)
  ];
  const budget: Budget = {
    ...base, id: 'orc-1', number: 'ORC-2610-0007', title: 'Ensaios e inspeção – unidade Campinas', clientName: 'Cliente Teste Ltda',
    date: '2026-10-14', validityDays: 10, items, costsSnapshot: ref, total: budgetTotal(items), status: 'enviada',
    paymentTerms: '30 dias', executionTime: '5 dias úteis', notes: 'NOTA INTERNA'
  };
  const company = { name: 'JVM Engenharia', legalName: 'JVM', cnpj: '', creaCompanyRegister: '', address: '', phone: '', email: '', website: '',
    technicalResponsible: { name: 'Fulano', title: 'Eng.', creaNumber: '', rnp: '' } } as CompanyLabInfo;
  const text = pdfText(buildBudgetPdf(budget, { company }));

  it('lista os serviços com quantidade, unidade, preço e o total', () => {
    expect(text).toContain('ORC-2610-0007');
    expect(text).toContain('Ensaios e inspeção'); // o travessão sai como byte Windows-1252 na extração
    expect(text).toContain('unidade Campinas');
    expect(text).toContain('Ensaio de luvas em campo');
    expect(text).toContain('Ensaio dielétrico conforme NBR 16295');
    expect(text).toContain('Inspeção de SPDA');
    expect(text).toContain('peça');
    expect(text).toContain(formatMoney(38.5).replace(/ /g, ' '));
    expect(text).toContain(formatMoney(1540).replace(/ /g, ' '));
    expect(text).toContain(formatMoney(budget.total).replace(/ /g, ' '));
    expect(text).toContain('5 dias úteis');
    expect(text).toContain('válida até 24/10/2026');
  });

  it('não expõe custos, margem, imposto nem lucro', () => {
    const lower = text.toLowerCase();
    for (const word of ['custo', 'margem', 'lucro', 'imposto', 'desconto']) expect(lower).not.toContain(word);
    const s = budgetSummary(budget);
    expect(text).not.toContain(formatMoney(s.plannedCost).replace(/ /g, ' '));
    expect(text).not.toContain(formatMoney(s.plannedProfit).replace(/ /g, ' '));
    expect(text).not.toContain('NOTA INTERNA');
    expect(text).not.toContain('PRC-2610-0003');
  });
});
