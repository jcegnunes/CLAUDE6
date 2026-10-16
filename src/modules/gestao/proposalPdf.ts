/**
 * Proposta comercial em PDF a partir de uma precificação ou de um orçamento.
 * Vai para o cliente: mostra só os preços — nunca custos, margem, impostos,
 * comissão, desconto nem lucro (a composição do preço é interna).
 */
import jsPDF from 'jspdf';
import autoTable from 'jspdf-autotable';
import { hardenPdfText } from '../../utils/safePdf';
import { loadImageAsDataUrl } from '../../services/pdfGenerator';
import { cleanSignatureImage } from '../../utils/signatureCleaner';
import { saveDocLocally } from '../../utils/nativeFileSaver';
import { DielectricStorageService } from '../../services/syncEngine';
import type { Client, CompanyLabInfo } from '../../types';
import { formatDateBr, formatMoney, pricingExpiryDate, todayIso } from './rules';
import type { Budget, ServicePricing } from './types';
import { getClientsForGestao } from './repository';

type Rgb = [number, number, number];
const NAVY: Rgb = [15, 37, 87];
const GRAY: Rgb = [90, 100, 115];
const TEXT: Rgb = [30, 41, 59];
const BORDER: Rgb = [203, 213, 225];

export interface ProposalPdfOptions {
  company: CompanyLabInfo;
  client?: Client;
  /** Logo e assinatura já carregadas (data URL); vazio = sem imagem */
  logo?: string;
  signature?: string;
  /** Data de emissão (AAAA-MM-DD); padrão: data da precificação */
  issueDate?: string;
}

function imageFormat(dataUrl: string): 'PNG' | 'JPEG' {
  return /^data:image\/jpe?g/i.test(dataUrl) ? 'JPEG' : 'PNG';
}

async function safeImage(url?: string, clean = false): Promise<string> {
  if (!url) return '';
  try {
    const data = await loadImageAsDataUrl(url);
    if (!data) return '';
    return clean ? (await cleanSignatureImage(data)) || data : data;
  } catch {
    return '';
  }
}

function onlyDigits(v: string): string {
  return (v || '').replace(/\D/g, '');
}

function formatCnpj(v: string): string {
  const d = onlyDigits(v);
  return d.length === 14 ? `${d.slice(0, 2)}.${d.slice(2, 5)}.${d.slice(5, 8)}/${d.slice(8, 12)}-${d.slice(12)}` : v;
}

function longDate(iso: string): string {
  const d = new Date(`${iso.slice(0, 10)}T12:00:00`);
  return Number.isNaN(d.getTime()) ? formatDateBr(iso) : d.toLocaleDateString('pt-BR', { day: 'numeric', month: 'long', year: 'numeric' });
}

function companyCity(company: CompanyLabInfo): string {
  return company.city || (company.cityState || '').split(/[-/]/)[0].trim();
}

export function proposalFileName(p: { number: string; clientName?: string }): string {
  const slug = (p.clientName || '').normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[^\w]+/g, '_').replace(/^_|_$/g, '').slice(0, 40);
  return `Proposta_${p.number || 'ORC'}${slug ? `_${slug}` : ''}.pdf`;
}

/** O que pode ir ao cliente: número, cliente, serviços com preço, condições e validade. */
interface ProposalContent {
  number: string;
  date: string;
  clientName?: string;
  paymentTerms?: string;
  validityDays: number;
  expiryDate: string;
  total: number;
  /** Precificação: um serviço com prazo em dias de campo */
  single?: { title: string; days: number };
  /** Orçamento: tabela de serviços */
  items?: Array<{ name: string; description?: string; quantity: number; unit: string; unitPrice: number; total: number }>;
  title?: string;
  executionTime?: string;
}

/** Monta o PDF da proposta (sem salvar). Só lê da precificação o que pode ir ao cliente. */
export function buildProposalPdf(pricing: ServicePricing, opts: ProposalPdfOptions): jsPDF {
  return renderProposal({
    number: pricing.number, date: pricing.date, clientName: pricing.clientName, paymentTerms: pricing.paymentTerms,
    validityDays: pricing.validityDays, expiryDate: pricingExpiryDate(pricing), total: pricing.result?.finalPrice ?? 0,
    single: { title: pricing.title || '', days: Number(pricing.input?.days) || 0 }
  }, opts);
}

/** PDF do orçamento: tabela com os serviços (quantidade, unidade, preço unitário e total). */
export function buildBudgetPdf(budget: Budget, opts: ProposalPdfOptions): jsPDF {
  return renderProposal({
    number: budget.number, date: budget.date, clientName: budget.clientName, paymentTerms: budget.paymentTerms,
    validityDays: budget.validityDays, expiryDate: pricingExpiryDate(budget), total: budget.total,
    title: budget.title, executionTime: budget.executionTime,
    items: budget.items.map(i => ({ name: i.name, description: i.description, quantity: i.quantity, unit: i.unit, unitPrice: i.unitPrice, total: i.total }))
  }, opts);
}

function renderProposal(pricing: ProposalContent, opts: ProposalPdfOptions): jsPDF {
  const { company, client, logo = '', signature = '' } = opts;
  const issueDate = opts.issueDate || pricing.date || todayIso();
  const doc = hardenPdfText(new jsPDF({ orientation: 'portrait', unit: 'mm', format: 'a4' }));
  const pageW = doc.internal.pageSize.getWidth();
  const pageH = doc.internal.pageSize.getHeight();
  const margin = 15;
  const contentW = pageW - 2 * margin;
  let y = margin;

  // ---------------------------------------------------------- cabeçalho da empresa
  let textX = margin;
  if (logo) {
    try {
      doc.addImage(logo, imageFormat(logo), margin, y, 28, 20, undefined, 'FAST');
      textX = margin + 32;
    } catch { /* imagem inválida: cabeçalho só com texto */ }
  }
  doc.setTextColor(...NAVY);
  doc.setFont('helvetica', 'bold');
  doc.setFontSize(12);
  doc.text((company.name || company.legalName || '').toUpperCase(), textX, y + 5);
  doc.setFont('helvetica', 'normal');
  doc.setFontSize(7.5);
  doc.setTextColor(...GRAY);
  const street = [company.address, company.number, company.neighborhood].filter(Boolean).join(', ');
  const place = [company.city ? [company.city, company.state].filter(Boolean).join('/') : company.cityState, company.cep ? `CEP ${company.cep}` : '']
    .filter(Boolean).join(' – ');
  const headerLines = [
    company.legalName && company.legalName !== company.name ? company.legalName : '',
    [company.cnpj ? `CNPJ ${formatCnpj(company.cnpj)}` : '', company.creaCompanyRegister ? `CREA ${company.creaCompanyRegister.replace(/^CREA[\s:-]*/i, '')}` : '']
      .filter(Boolean).join(' · '),
    [street, place].filter(Boolean).join(' – '),
    [company.phone, company.email, company.website].filter(Boolean).join(' · ')
  ].filter(Boolean);
  headerLines.forEach((line, i) => doc.text(doc.splitTextToSize(line, pageW - margin - textX)[0], textX, y + 9.5 + i * 3.6));
  y += Math.max(22, 9.5 + headerLines.length * 3.6);
  doc.setDrawColor(...NAVY);
  doc.setLineWidth(0.6);
  doc.line(margin, y, pageW - margin, y);
  y += 8;

  // ---------------------------------------------------------------------- título
  doc.setTextColor(...NAVY);
  doc.setFont('helvetica', 'bold');
  doc.setFontSize(15);
  doc.text('PROPOSTA COMERCIAL', margin, y);
  doc.setFontSize(10);
  doc.text(pricing.number || '', pageW - margin, y - 4, { align: 'right' });
  doc.setFont('helvetica', 'normal');
  doc.setFontSize(8.5);
  doc.setTextColor(...GRAY);
  doc.text(`Emitida em ${formatDateBr(issueDate)}`, pageW - margin, y + 1, { align: 'right' });
  y += 7;

  const section = (title: string) => {
    doc.setFillColor(...NAVY);
    doc.rect(margin, y, contentW, 6, 'F');
    doc.setTextColor(255, 255, 255);
    doc.setFont('helvetica', 'bold');
    doc.setFontSize(8.5);
    doc.text(title.toUpperCase(), margin + 2.5, y + 4.2);
    y += 6;
  };
  const table = (rows: string[][]) => {
    autoTable(doc, {
      startY: y,
      margin: { left: margin, right: margin },
      body: rows,
      theme: 'grid',
      styles: { fontSize: 9, cellPadding: 2, textColor: TEXT, lineColor: BORDER, lineWidth: 0.2 },
      columnStyles: { 0: { cellWidth: 45, fontStyle: 'bold', textColor: GRAY } }
    });
    y = ((doc as unknown as { lastAutoTable?: { finalY: number } }).lastAutoTable?.finalY ?? y) + 6;
  };

  // ------------------------------------------------------------------- cliente
  section('Cliente');
  const clientName = client ? (client.razaoSocial || client.nomeFantasia) : (pricing.clientName || '—');
  const clientRows: string[][] = [['Razão social / nome', clientName]];
  if (client) {
    if (client.nomeFantasia && client.nomeFantasia !== clientName) clientRows.push(['Nome fantasia', client.nomeFantasia]);
    if (client.cnpj) clientRows.push(['CNPJ / CPF', formatCnpj(client.cnpj)]);
    const addr = [[client.endereco, client.numero, client.complemento, client.bairro].filter(Boolean).join(', '),
      [client.cidade, client.estado].filter(Boolean).join('/'), client.cep ? `CEP ${client.cep}` : ''].filter(Boolean).join(' – ');
    if (addr) clientRows.push(['Endereço', addr]);
    const contact = [client.responsavel, client.cargoResponsavel].filter(Boolean).join(' – ');
    if (contact) clientRows.push(['A/C', contact]);
    const reach = [client.telefone || client.whatsapp, client.email].filter(Boolean).join(' · ');
    if (reach) clientRows.push(['Contato', reach]);
  }
  table(clientRows);

  // ------------------------------------------------------------- serviço e preço
  if (pricing.items) {
    section('Serviços propostos');
    if (pricing.title?.trim()) {
      doc.setTextColor(...TEXT);
      doc.setFont('helvetica', 'bold');
      doc.setFontSize(9);
      const lines = doc.splitTextToSize(pricing.title.trim(), contentW - 4);
      doc.text(lines, margin + 2, y + 5);
      y += 3 + lines.length * 4.2;
    }
    const qty = (n: number) => String(Math.round(n * 100) / 100).replace('.', ',');
    autoTable(doc, {
      startY: y + 1,
      margin: { left: margin, right: margin },
      head: [['Item', 'Serviço', 'Qtd', 'Un.', 'Valor unit.', 'Total']],
      body: pricing.items.map((i, idx) => [
        String(idx + 1),
        i.description?.trim() ? `${i.name}\n${i.description.trim()}` : i.name,
        qty(i.quantity), i.unit, formatMoney(i.unitPrice), formatMoney(i.total)
      ]),
      theme: 'grid',
      headStyles: { fillColor: [241, 245, 249], textColor: NAVY, fontStyle: 'bold', fontSize: 8.5 },
      styles: { fontSize: 8.5, cellPadding: 2, textColor: TEXT, lineColor: BORDER, lineWidth: 0.2, valign: 'top' },
      columnStyles: {
        0: { cellWidth: 11, halign: 'center' }, 2: { cellWidth: 14, halign: 'right' }, 3: { cellWidth: 16 },
        4: { cellWidth: 28, halign: 'right' }, 5: { cellWidth: 30, halign: 'right', fontStyle: 'bold' }
      }
    });
    y = ((doc as unknown as { lastAutoTable?: { finalY: number } }).lastAutoTable?.finalY ?? y) + 6;
  } else {
    section('Serviço proposto');
    const days = pricing.single?.days || 0;
    const serviceRows: string[][] = [['Descrição', pricing.single?.title || '']];
    if (days > 0) serviceRows.push(['Prazo de execução', `${String(days).replace('.', ',')} dia(s) de trabalho em campo`]);
    table(serviceRows);
  }
  if (y > pageH - 40) { doc.addPage(); y = margin + 10; }

  doc.setFillColor(239, 246, 255);
  doc.setDrawColor(...NAVY);
  doc.setLineWidth(0.4);
  doc.roundedRect(margin, y, contentW, 14, 1.5, 1.5, 'FD');
  doc.setTextColor(...NAVY);
  doc.setFont('helvetica', 'bold');
  doc.setFontSize(10);
  doc.text('VALOR TOTAL DA PROPOSTA', margin + 4, y + 8.7);
  doc.setFontSize(15);
  doc.text(formatMoney(pricing.total), pageW - margin - 4, y + 9.3, { align: 'right' });
  y += 20;

  // ------------------------------------------------------------------ condições
  section('Condições comerciais');
  const conditions: string[][] = [['Condições de pagamento', pricing.paymentTerms?.trim() || 'A combinar']];
  if (pricing.executionTime?.trim()) conditions.push(['Prazo de execução', pricing.executionTime.trim()]);
  conditions.push(['Validade da proposta', `${pricing.validityDays || 0} dia(s) – válida até ${formatDateBr(pricing.expiryDate)}`]);
  table(conditions);

  // ------------------------------------------------------------------ assinatura
  const rt = company.technicalResponsible || { name: '', title: '', creaNumber: '', rnp: '' };
  if (y > pageH - 60) { doc.addPage(); y = margin + 10; }
  doc.setTextColor(...TEXT);
  doc.setFont('helvetica', 'normal');
  doc.setFontSize(9);
  const city = companyCity(company);
  doc.text(`${city ? `${city}, ` : ''}${longDate(issueDate)}.`, margin, y + 4);
  y += 26;
  const sigW = 80;
  const sigX = (pageW - sigW) / 2;
  if (signature) {
    try { doc.addImage(signature, imageFormat(signature), sigX + 10, y - 17, sigW - 20, 16); } catch { /* sem assinatura */ }
  }
  doc.setDrawColor(...TEXT);
  doc.setLineWidth(0.3);
  doc.line(sigX, y, sigX + sigW, y);
  doc.setFont('helvetica', 'bold');
  doc.text(rt.name || company.name || '', pageW / 2, y + 4.5, { align: 'center' });
  doc.setFont('helvetica', 'normal');
  doc.setFontSize(8);
  doc.setTextColor(...GRAY);
  const rtLines = [rt.title, rt.creaNumber ? `CREA ${rt.creaNumber.replace(/^CREA[\s:-]*/i, '')}` : '', company.name].filter(Boolean);
  rtLines.forEach((l, i) => doc.text(l, pageW / 2, y + 8.5 + i * 3.8, { align: 'center' }));

  // --------------------------------------------------------------------- rodapé
  const pages = doc.getNumberOfPages();
  for (let i = 1; i <= pages; i++) {
    doc.setPage(i);
    doc.setFontSize(7);
    doc.setTextColor(...GRAY);
    doc.text(`${pricing.number || ''} · Página ${i} de ${pages}`, pageW - margin, pageH - 8, { align: 'right' });
  }
  return doc;
}

/** Gera e salva a proposta usando a empresa, o cliente, a logo e a assinatura do RT cadastrados. */
export async function exportProposalPdf(pricing: ServicePricing): Promise<void> {
  const company = DielectricStorageService.getCompanyInfo();
  const client = pricing.clientId ? getClientsForGestao().find(c => c.id === pricing.clientId) : undefined;
  const [logo, signature] = await Promise.all([
    safeImage(company.logoUrl),
    safeImage(company.technicalResponsible?.signatureUrl, true)
  ]);
  const doc = buildProposalPdf(pricing, { company, client, logo, signature });
  const name = proposalFileName(pricing);
  await saveDocLocally(doc, name, name.replace(/\.pdf$/, ''), 'outro');
}

/** Gera e salva a proposta do orçamento. */
export async function exportBudgetPdf(budget: Budget): Promise<void> {
  const company = DielectricStorageService.getCompanyInfo();
  const client = budget.clientId ? getClientsForGestao().find(c => c.id === budget.clientId) : undefined;
  const [logo, signature] = await Promise.all([
    safeImage(company.logoUrl),
    safeImage(company.technicalResponsible?.signatureUrl, true)
  ]);
  const doc = buildBudgetPdf(budget, { company, client, logo, signature });
  const name = proposalFileName(budget);
  await saveDocLocally(doc, name, name.replace(/\.pdf$/, ''), 'outro');
}
