import { describe, expect, it } from 'vitest';
import type { jsPDF } from 'jspdf';
import { buildProposalPdf, proposalFileName } from '../proposalPdf';
import { calculatePricing, costsFromSettings, defaultSettings, formatMoney } from '../rules';
import type { PricingInput, ServicePricing } from '../types';
import type { Client, CompanyLabInfo } from '../../../types';

/** Texto escrito no PDF: operandos de Tj/TJ das páginas (jsPDF grava sem compressão). */
function pdfText(doc: jsPDF): string {
  const raw = Buffer.from(doc.output('arraybuffer')).toString('latin1');
  const parts: string[] = [];
  for (const m of raw.matchAll(/\(((?:\\.|[^\\)])*)\)\s*Tj/g)) {
    parts.push(m[1].replace(/\\([()\\])/g, '$1').replace(/\\(\d{3})/g, (_, o) => String.fromCharCode(parseInt(o, 8))));
  }
  return parts.join('\n').replace(/ /g, ' ');
}

const costs = costsFromSettings({ ...defaultSettings('comp-1'), marginPercent: 31 });
const input: PricingInput = { days: 2, helpers: 1, engineers: 1, kmPerDay: 100, visits: 1, materials: 137, rentals: 0 };
const result = calculatePricing(input, costs);

const pricing: ServicePricing = {
  id: 'p1', companyId: 'comp-1', createdAt: '2026-10-01T10:00:00Z', updatedAt: '2026-10-01T10:00:00Z',
  number: 'ORC-2026-0042', title: 'Ensaio dielétrico de 40 luvas e 10 mangas', clientId: 'c1', clientName: 'Cliente Teste Ltda',
  date: '2026-10-10', validityDays: 15, input, result, costsSnapshot: costs, status: 'enviada',
  paymentTerms: '50% no aceite e 50% na entrega do laudo', notes: 'NOTA INTERNA: cliente aceita até 10% de desconto'
};

const company = {
  name: 'JVM Engenharia', legalName: 'JVM Engenharia Elétrica Ltda', cnpj: '12345678000199', creaCompanyRegister: '123456',
  address: 'Rua das Flores', number: '100', city: 'Campinas', state: 'SP', cep: '13000-000', phone: '(19) 99999-0000',
  email: 'contato@jvm.test', website: 'jvm.test',
  technicalResponsible: { name: 'Fulano de Tal', title: 'Engenheiro Eletricista', creaNumber: '5061234567', rnp: '' }
} as CompanyLabInfo;

const client = {
  id: 'c1', razaoSocial: 'Cliente Teste Ltda', nomeFantasia: 'Cliente Teste', cnpj: '98765432000111', endereco: 'Av. Brasil',
  numero: '200', bairro: 'Centro', cidade: 'Jundiaí', estado: 'SP', cep: '13200-000', telefone: '(11) 4000-0000',
  email: 'compras@cliente.test', responsavel: 'Beltrana', cargoResponsavel: 'Compras', createdAt: '', updatedAt: ''
} as Client;

describe('Proposta comercial em PDF', () => {
  const text = pdfText(buildProposalPdf(pricing, { company, client }));

  it('traz empresa, cliente, número ORC, serviço, preço final, condições, validade e assinatura', () => {
    expect(text).toContain('JVM ENGENHARIA');
    expect(text).toContain('CNPJ 12.345.678/0001-99');
    expect(text).toContain('Cliente Teste Ltda');
    expect(text).toContain('98.765.432/0001-11');
    expect(text).toContain('ORC-2026-0042');
    expect(text).toContain('Ensaio dielétrico de 40 luvas e 10 mangas');
    expect(text).toContain(formatMoney(result.finalPrice).replace(/ /g, ' '));
    expect(text).toContain('50% no aceite e 50% na entrega do laudo');
    expect(text).toContain('válida até 25/10/2026');
    expect(text).toContain('Fulano de Tal');
    expect(text).toContain('CREA 5061234567');
  });

  it('não expõe custo, margem, impostos, comissão, desconto nem lucro', () => {
    expect(result.profit).toBeGreaterThan(0);
    expect(result.negotiationValue).toBeGreaterThan(0);
    const lower = text.toLowerCase();
    for (const word of ['custo', 'margem', 'lucro', 'imposto', 'comiss', 'desconto', 'markup', 'rateio']) {
      expect(lower).not.toContain(word);
    }
    const hidden = [
      result.subtotal, result.profit, result.taxValue, result.marginValue, result.invoiceTotal, result.negotiationValue,
      result.helperLabor, result.engineerLabor, result.engineerMeals, result.fuel, result.vehicle, result.totalMaterials
    ];
    for (const value of hidden) expect(text).not.toContain(formatMoney(value).replace(/ /g, ' '));
    expect(text).not.toContain(`${result.effectiveMarginPercent}%`);
    expect(text).not.toContain('31%');
    expect(text).not.toContain('NOTA INTERNA');
    expect(text).not.toContain('Engenheiro (');
  });

  it('sem cliente cadastrado usa o nome gravado na precificação e condições "A combinar"', () => {
    const t = pdfText(buildProposalPdf({ ...pricing, clientId: undefined, paymentTerms: '' }, { company }));
    expect(t).toContain('Cliente Teste Ltda');
    expect(t).toContain('A combinar');
  });

  it('nome do arquivo usa o número ORC e o cliente sem acentos', () => {
    expect(proposalFileName({ number: 'ORC-2026-0042', clientName: 'Água & Luz Ltda' })).toBe('Proposta_ORC-2026-0042_Agua_Luz_Ltda.pdf');
  });
});
