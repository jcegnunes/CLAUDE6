import { hardenPdfText } from '../utils/safePdf';
import QRCode from 'qrcode';
import jsPDF from 'jspdf';
import JSZip from 'jszip';
import { TestRecord, CompanyLabInfo } from '../types';
import { formatDateBR } from '../utils/dateUtils';
import { saveDocLocally } from '../utils/nativeFileSaver';
import { ValidationPortalService } from './validationPortalService';
import { buildLabelModel, drawLabelModel, modelMmScale } from './niimbotLabelModel';

export type NiimbotRollSize = '30x20' | '50x30' | '50x80';

/** Tamanho válido (valores antigos salvos, como 50x50 ou 40x30, viram 50x30). */
export const normalizeRollSize = (size?: string): NiimbotRollSize =>
  size === '30x20' || size === '50x80' ? size : '50x30';

export interface NiimbotRollConfig {
  id: NiimbotRollSize;
  name: string;
  widthMm: number;
  heightMm: number;
  description: string;
  recommendedFor: string;
  dpi: number;
}

export const NIIMBOT_ROLL_CONFIGS: Record<NiimbotRollSize, NiimbotRollConfig> = {
  '30x20': {
    id: '30x20',
    name: '30 x 20 mm (Pequena)',
    widthMm: 30,
    heightMm: 20,
    description: 'Modelo compacto: TAG, laudo, datas, QR do validador e status.',
    recommendedFor: 'Ferramentas, ponteiras e equipamentos pequenos',
    dpi: 203
  },
  '50x30': {
    id: '50x30',
    name: '50 x 30 mm (Padrão Niimbot B1)',
    widthMm: 50,
    heightMm: 30,
    description: 'Tamanho padrão e mais utilizado na impressora Niimbot B1.',
    recommendedFor: 'Luvas, mangas, capacetes e ferramental NR-10',
    dpi: 203
  },
  '50x80': {
    id: '50x80',
    name: '50 x 80 mm (Em pé)',
    widthMm: 50,
    heightMm: 80,
    description: 'Etiqueta em pé com QR Code grande, mesmas informações do modelo padrão.',
    recommendedFor: 'Bastões, varas, escadas, tapetes e aterramentos',
    dpi: 203
  }
};

/**
 * Gera QR Code em base64 com alto contraste (Preto puro sobre Branco)
 */
export async function generateNiimbotQRCodeDataUrl(text: string): Promise<string> {
  try {
    return await QRCode.toDataURL(text, {
      width: 300,
      margin: 1,
      errorCorrectionLevel: 'M',
      color: {
        dark: '#000000',
        light: '#FFFFFF'
      }
    });
  } catch (err) {
    console.error('Erro ao gerar QR Code para Niimbot:', err);
    return '';
  }
}

/**
 * Renderiza uma etiqueta Niimbot B1 em um elemento HTMLCanvasElement de alta resolução (203 DPI / 2x Supersampled)
 */
export async function renderNiimbotLabelToCanvas(
  test: TestRecord,
  company: CompanyLabInfo,
  rollSize: NiimbotRollSize = '50x30'
): Promise<HTMLCanvasElement> {
  const config = NIIMBOT_ROLL_CONFIGS[rollSize] || NIIMBOT_ROLL_CONFIGS['50x30'];
  
  // Scale factor for supersampling (2x for ultra sharp text rendering)
  const scale = 3;
  const dotsPerMm = (config.dpi / 25.4); // ~8 dots/mm for 203 DPI
  const widthPx = Math.round(config.widthMm * dotsPerMm * scale);
  const heightPx = Math.round(config.heightMm * dotsPerMm * scale);

  const canvas = document.createElement('canvas');
  canvas.width = widthPx;
  canvas.height = heightPx;
  const ctx = canvas.getContext('2d');
  if (!ctx) throw new Error('Não foi possível obter contexto 2D do canvas');

  const size = normalizeRollSize(rollSize);
  const validationUrl = ValidationPortalService.buildPublicValidationUrl(test.validationCode);
  const qrDataUrl = await generateNiimbotQRCodeDataUrl(validationUrl);

  // mesmo desenho da etiqueta do aplicativo (APK)
  await drawLabelModel(ctx, buildLabelModel(test, size), widthPx, heightPx, qrDataUrl);
  return canvas;
}

/**
 * Baixa etiqueta individual como imagem PNG de alta resolução 203 DPI
 */
export async function exportNiimbotPNG(
  test: TestRecord,
  company: CompanyLabInfo,
  rollSize: NiimbotRollSize = '50x30'
): Promise<void> {
  const canvas = await renderNiimbotLabelToCanvas(test, company, rollSize);
  const dataUrl = canvas.toDataURL('image/png');
  
  const link = document.createElement('a');
  link.download = `Etiqueta_Niimbot_${rollSize}_${test.equipmentTag}_${test.reportNumber}.png`;
  link.href = dataUrl;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
}

/**
 * Baixa arquivo ZIP contendo todas as etiquetas em PNG (1:1 203 DPI) para importação em lote no software Niimbot
 */
export async function exportNiimbotBatchZIP(
  tests: TestRecord[],
  company: CompanyLabInfo,
  rollSize: NiimbotRollSize = '50x30',
  onProgress?: (current: number, total: number, message: string) => void
): Promise<void> {
  if (!tests || tests.length === 0) return;

  const zip = new JSZip();
  const folder = zip.folder(`Etiquetas_Niimbot_${rollSize}`);
  const total = tests.length;

  for (let i = 0; i < total; i++) {
    const test = tests[i];
    if (onProgress) {
      onProgress(i + 1, total, `Renderizando etiqueta ${i + 1} de ${total}: ${test.equipmentTag}...`);
    }

    const canvas = await renderNiimbotLabelToCanvas(test, company, rollSize);
    const dataUrl = canvas.toDataURL('image/png');
    const base64Data = dataUrl.replace(/^data:image\/png;base64,/, '');
    
    folder?.file(`Etiqueta_${i + 1}_${test.equipmentTag}_${test.reportNumber}.png`, base64Data, { base64: true });
  }

  // Also include CSV in the zip for easy Niimbot Excel import
  const csvContent = generateNiimbotCSVContent(tests, company);
  zip.file(`Dados_Lote_Niimbot_${tests.length}_itens.csv`, '\uFEFF' + csvContent);

  // Also include JCPS project template
  try {
    const jcpsProject = await buildNiimbotJCPSObject(tests, company, rollSize);
    zip.file(`Projeto_Niimbot_${rollSize}_${tests.length}_itens.jcps`, JSON.stringify(jcpsProject, null, 2));
  } catch (err) {
    console.warn('Erro ao embutir JCPS no ZIP:', err);
  }

  if (onProgress) {
    onProgress(total, total, 'Compactando arquivo ZIP...');
  }

  const zipBlob = await zip.generateAsync({ type: 'blob' });
  const todayStr = new Date().toISOString().split('T')[0];
  const url = URL.createObjectURL(zipBlob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `Etiquetas_Niimbot_B1_${rollSize}_${tests.length}_itens_${todayStr}.zip`;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}

/**
 * Gera arquivo CSV compatível com o módulo de impressão em lote / Excel do software NIIMBOT
 */
export function generateNiimbotCSVContent(tests: TestRecord[], company: CompanyLabInfo): string {
  const headers = [
    'TAG',
    'NUM_LAUDO',
    'NUM_CERTIFICADO',
    'CLIENTE',
    'COLABORADOR',
    'EQUIPAMENTO',
    'CLASSE',
    'TENSAO_KV',
    'CORRENTE_MA',
    'DATA_ENSAIO',
    'VALIDADE_REENSAIO',
    'STATUS',
    'ANALISTA_EXECUTOR',
    'RESPONSAVEL_TECNICO',
    'CREA_RT',
    'CODIGO_VALIDACAO',
    'URL_VALIDACAO_QR'
  ];

  const rows = tests.map(t => [
    `"${t.equipmentTag}"`,
    `"${t.reportNumber}"`,
    `"${t.certificateNumber || 'N/A'}"`,
    `"${t.clientName.replace(/"/g, '""')}"`,
    `"${(t.collaboratorName || '').replace(/"/g, '""')}"`,
    `"${t.equipmentType.replace(/_/g, ' ').toUpperCase()}"`,
    `"${t.equipmentClass}"`,
    `"${t.appliedVoltage_kV} kV"`,
    `"${t.measuredLeakageCurrent_mA} ${t.currentUnit}"`,
    `"${formatDateBR(t.testDate)}"`,
    `"${formatDateBR(t.retestDueDate)}"`,
    `"${t.result}"`,
    `"${t.technicianName.replace(/"/g, '""')}"`,
    `"${(t.techResponsibleName || company.technicalResponsible?.name || '').replace(/"/g, '""')}"`,
    `"${t.techResponsibleCrea || company.technicalResponsible?.creaNumber || ''}"`,
    `"${t.validationCode}"`,
    `"${ValidationPortalService.buildPublicValidationUrl(t.validationCode)}"`
  ]);

  return [headers.join(';'), ...rows.map(r => r.join(';'))].join('\r\n');
}

/**
 * Interface dos elementos para o formato de projeto .JCPS (Jingchen Print Schema / Niimbot Project)
 */
export interface NiimbotJCPSElement {
  id: string;
  type: 'text' | 'qrcode' | 'barcode' | 'line' | 'rect' | 'image';
  x: number; // mm
  y: number; // mm
  width: number; // mm
  height: number; // mm
  content?: string;
  variableBinding?: string;
  fontSize?: number;
  fontFamily?: string;
  fontWeight?: 'bold' | 'normal';
  textAlign?: 'left' | 'center' | 'right';
  textColor?: string;
  backgroundColor?: string;
  borderColor?: string;
  borderWidth?: number;
  rotation?: number;
  errorCorrectionLevel?: 'L' | 'M' | 'Q' | 'H';
  base64Image?: string;
}

export interface NiimbotJCPSProject {
  schema: string;
  app: string;
  version: string;
  createdAt: string;
  generator: string;
  device: {
    brand: string;
    model: string;
    dpi: number;
    printType: string;
    connection: string;
  };
  paper: {
    name: string;
    width: number;
    height: number;
    unit: 'mm';
    orientation: 'landscape' | 'portrait';
    type: string;
    margin: number;
  };
  canvas: {
    widthPx: number;
    heightPx: number;
    dpi: number;
    scale: number;
    backgroundColor: string;
  };
  metadata: {
    title: string;
    standard: string;
    client: string;
    tag: string;
    reportNumber: string;
    certificateNumber?: string;
    generatedAt: string;
  };
  template: {
    id: string;
    title: string;
    elements: NiimbotJCPSElement[];
  };
  preview?: string;
  dataSource?: {
    columns: string[];
    records: Record<string, string>[];
  };
  pages?: Array<{
    pageIndex: number;
    testId: string;
    reportNumber: string;
    tag: string;
    preview?: string;
    data: Record<string, string>;
  }>;
}

/**
 * Constrói o objeto de projeto .JCPS estruturado para um ou múltiplos ensaios
 */
export async function buildNiimbotJCPSObject(
  tests: TestRecord[],
  company: CompanyLabInfo,
  rollSize: NiimbotRollSize = '50x30'
): Promise<NiimbotJCPSProject> {
  const config = NIIMBOT_ROLL_CONFIGS[rollSize] || NIIMBOT_ROLL_CONFIGS['50x30'];
  const firstTest = tests[0];
  const isApproved = firstTest.result === 'APROVADO';
  const validationUrl = ValidationPortalService.buildPublicValidationUrl(firstTest.validationCode);

  // Render thumbnail for preview
  let previewDataUrl = '';
  try {
    const canvas = await renderNiimbotLabelToCanvas(firstTest, company, rollSize);
    previewDataUrl = canvas.toDataURL('image/png');
  } catch (err) {
    console.warn('Não foi possível gerar preview para JCPS:', err);
  }

  // Define layout elements in mm based on roll size (modelo do aplicativo nos tamanhos 30x20, 50x30 e 80x50)
  const elements: NiimbotJCPSElement[] = modelJcpsElements(firstTest, rollSize, config);

  // Records data source table
  const columns = [
    'TAG',
    'NUM_LAUDO',
    'NUM_CERTIFICADO',
    'CLIENTE',
    'COLABORADOR',
    'EQUIPAMENTO',
    'CLASSE',
    'TENSAO_KV',
    'CORRENTE_MA',
    'DATA_ENSAIO',
    'VALIDADE_REENSAIO',
    'STATUS',
    'ANALISTA_EXECUTOR',
    'RESPONSAVEL_TECNICO',
    'CREA_RT',
    'CODIGO_VALIDACAO',
    'URL_VALIDACAO_QR'
  ];

  const records = tests.map(t => ({
    TAG: t.equipmentTag,
    NUM_LAUDO: t.reportNumber,
    NUM_CERTIFICADO: t.certificateNumber || 'N/A',
    CLIENTE: t.clientName,
    COLABORADOR: t.collaboratorName || '',
    EQUIPAMENTO: `${t.equipmentType.replace(/_/g, ' ').toUpperCase()} (CL. ${t.equipmentClass})`,
    CLASSE: t.equipmentClass,
    TENSAO_KV: `${t.appliedVoltage_kV} kV`,
    CORRENTE_MA: `${t.measuredLeakageCurrent_mA} ${t.currentUnit}`,
    DATA_ENSAIO: formatDateBR(t.testDate),
    VALIDADE_REENSAIO: formatDateBR(t.retestDueDate),
    STATUS: t.result,
    ANALISTA_EXECUTOR: t.technicianName,
    RESPONSAVEL_TECNICO: t.techResponsibleName || company.technicalResponsible?.name || '',
    CREA_RT: t.techResponsibleCrea || company.technicalResponsible?.creaNumber || '',
    CODIGO_VALIDACAO: t.validationCode,
    URL_VALIDACAO_QR: ValidationPortalService.buildPublicValidationUrl(t.validationCode)
  }));

  const pages = tests.map((t, idx) => ({
    pageIndex: idx + 1,
    testId: t.id,
    reportNumber: t.reportNumber,
    tag: t.equipmentTag,
    preview: idx === 0 ? previewDataUrl : undefined,
    data: records[idx]
  }));

  return {
    schema: 'https://niimbot.com/schema/label_project_v2.json',
    app: 'NIIMBOT',
    version: '2.1.0',
    createdAt: new Date().toISOString(),
    generator: 'JVM DielectricLab NR-10 Enterprise System',
    device: {
      brand: 'NIIMBOT',
      model: 'B1',
      dpi: config.dpi || 203,
      printType: 'thermal',
      connection: 'bluetooth_usb'
    },
    paper: {
      name: config.name,
      width: config.widthMm,
      height: config.heightMm,
      unit: 'mm',
      orientation: config.widthMm >= config.heightMm ? 'landscape' : 'portrait',
      type: 'gap',
      margin: 1.0
    },
    canvas: {
      widthPx: Math.round(config.widthMm * 8),
      heightPx: Math.round(config.heightMm * 8),
      dpi: config.dpi || 203,
      scale: 1.0,
      backgroundColor: '#ffffff'
    },
    metadata: {
      title: `Etiqueta Ensaio Dielétrico - ${firstTest.equipmentTag}`,
      standard: 'NR-10 / ABNT NBR',
      client: firstTest.clientName,
      tag: firstTest.equipmentTag,
      reportNumber: firstTest.reportNumber,
      certificateNumber: firstTest.certificateNumber,
      generatedAt: new Date().toISOString()
    },
    template: {
      id: `tmpl_jvm_${rollSize}`,
      title: `Modelo JVM ${config.widthMm}x${config.heightMm}mm`,
      elements
    },
    preview: previewDataUrl,
    dataSource: {
      columns,
      records
    },
    pages
  };
}

/**
 * Exporta arquivo individual de projeto no formato .JCPS (Jingchen Print Schema) para o software Niimbot
 */
export async function exportNiimbotJCPS(
  test: TestRecord,
  company: CompanyLabInfo,
  rollSize: NiimbotRollSize = '50x30'
): Promise<void> {
  const jcpsProject = await buildNiimbotJCPSObject([test], company, rollSize);
  const jsonStr = JSON.stringify(jcpsProject, null, 2);
  const blob = new Blob([jsonStr], { type: 'application/json;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `Etiqueta_Niimbot_${rollSize}_${test.equipmentTag}_${test.reportNumber}.jcps`;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}

/**
 * Exporta projeto em lote de todas as etiquetas selecionadas no formato .JCPS
 */
export async function exportNiimbotBatchJCPS(
  tests: TestRecord[],
  company: CompanyLabInfo,
  rollSize: NiimbotRollSize = '50x30',
  onProgress?: (current: number, total: number, message: string) => void
): Promise<void> {
  if (!tests || tests.length === 0) return;

  if (onProgress) {
    onProgress(1, 2, 'Estruturando projeto JCPS para Niimbot...');
  }

  const jcpsProject = await buildNiimbotJCPSObject(tests, company, rollSize);
  
  if (onProgress) {
    onProgress(2, 2, 'Gerando arquivo .jcps...');
  }

  const jsonStr = JSON.stringify(jcpsProject, null, 2);
  const blob = new Blob([jsonStr], { type: 'application/json;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  const todayStr = new Date().toISOString().split('T')[0];
  link.href = url;
  link.download = `Projeto_Etiquetas_Niimbot_Lote_${rollSize}_${tests.length}_itens_${todayStr}.jcps`;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}

/**
 * Baixa o arquivo CSV pronto para ser carregado no software Niimbot PC / App
 */
export function exportNiimbotCSV(tests: TestRecord[], company: CompanyLabInfo): void {
  const csv = generateNiimbotCSVContent(tests, company);
  const blob = new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  const todayStr = new Date().toISOString().split('T')[0];
  link.href = url;
  link.download = `Planilha_Lote_Niimbot_B1_${tests.length}_itens_${todayStr}.csv`;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}

/**
 * Exporta documento PDF com dimensões milimétricas exatas (1:1) para a impressora Niimbot B1
 */
export async function exportNiimbotPDF(
  tests: TestRecord[],
  company: CompanyLabInfo,
  rollSize: NiimbotRollSize = '50x30',
  onProgress?: (current: number, total: number, message: string) => void
): Promise<void> {
  if (!tests || tests.length === 0) return;

  const config = NIIMBOT_ROLL_CONFIGS[rollSize] || NIIMBOT_ROLL_CONFIGS['50x30'];
  const doc = new jsPDF({
    orientation: config.widthMm >= config.heightMm ? 'landscape' : 'portrait',
    unit: 'mm',
    format: [config.widthMm, config.heightMm]
  });
  hardenPdfText(doc);

  const total = tests.length;

  for (let i = 0; i < total; i++) {
    const test = tests[i];
    if (onProgress) {
      onProgress(i + 1, total, `Gerando página PDF ${i + 1} de ${total}: ${test.equipmentTag}...`);
    }

    if (i > 0) {
      doc.addPage([config.widthMm, config.heightMm], config.widthMm >= config.heightMm ? 'landscape' : 'portrait');
    }

    const canvas = await renderNiimbotLabelToCanvas(test, company, rollSize);
    const imgData = canvas.toDataURL('image/png');
    doc.addImage(imgData, 'PNG', 0, 0, config.widthMm, config.heightMm);
  }

  const todayStr = new Date().toISOString().split('T')[0];
  const fileName = `Etiquetas_Niimbot_B1_${rollSize}_${tests.length}_itens_${todayStr}.pdf`;
  await saveDocLocally(doc, fileName, `Etiquetas Niimbot B1 (${rollSize} - ${tests.length} itens)`, 'etiqueta');
}

/**
 * Impressão Direta no Navegador com layout térmico @page milimétrico
 */
export async function printNiimbotDirect(
  tests: TestRecord[],
  company: CompanyLabInfo,
  rollSize: NiimbotRollSize = '50x30'
): Promise<void> {
  const config = NIIMBOT_ROLL_CONFIGS[rollSize] || NIIMBOT_ROLL_CONFIGS['50x30'];
  const canvases: HTMLCanvasElement[] = [];

  for (const test of tests) {
    const canvas = await renderNiimbotLabelToCanvas(test, company, rollSize);
    canvases.push(canvas);
  }

  const iframe = document.createElement('iframe');
  iframe.style.position = 'fixed';
  iframe.style.right = '0';
  iframe.style.bottom = '0';
  iframe.style.width = '0';
  iframe.style.height = '0';
  iframe.style.border = '0';

  document.body.appendChild(iframe);

  const doc = iframe.contentWindow?.document;
  if (!doc) return;

  const imagesHtml = canvases.map(c => `<div class="label-page"><img src="${c.toDataURL('image/png')}" /></div>`).join('');

  doc.open();
  doc.write(`
    <!DOCTYPE html>
    <html>
      <head>
        <title>Imprimir Etiquetas Niimbot B1</title>
        <style>
          @page {
            size: ${config.widthMm}mm ${config.heightMm}mm;
            margin: 0;
          }
          * {
            box-sizing: border-box;
            margin: 0;
            padding: 0;
          }
          body {
            background: #ffffff;
            margin: 0;
            padding: 0;
          }
          .label-page {
            width: ${config.widthMm}mm;
            height: ${config.heightMm}mm;
            page-break-after: always;
            page-break-inside: avoid;
            display: flex;
            align-items: center;
            justify-content: center;
          }
          .label-page:last-child {
            page-break-after: auto;
          }
          img {
            width: 100%;
            height: 100%;
            display: block;
            image-rendering: pixelated;
          }
        </style>
      </head>
      <body>
        ${imagesHtml}
      </body>
    </html>
  `);
  doc.close();

  setTimeout(() => {
    iframe.contentWindow?.focus();
    iframe.contentWindow?.print();
    setTimeout(() => {
      document.body.removeChild(iframe);
    }, 1000);
  }, 400);
}

/** Elementos JCPS (mm) do modelo do aplicativo, com as variáveis de lote nos campos do ensaio. */
function modelJcpsElements(test: TestRecord, rollSize: NiimbotRollSize, config: NiimbotRollConfig): NiimbotJCPSElement[] {
  const model = buildLabelModel(test, normalizeRollSize(rollSize));
  const qrPrim = model.primitives.find(p => p.kind === 'qr');
  const qrSide = qrPrim && qrPrim.kind === 'qr' ? qrPrim.size : 120;
  const { k, ox, oy } = modelMmScale(model, config.widthMm, config.heightMm);
  const r = (v: number) => Math.round(v * 100) / 100;
  const ptPerMm = 2.835;

  return model.primitives.map((p): NiimbotJCPSElement => {
    if (p.kind === 'rect') {
      return {
        id: `elem_${p.id}`, type: 'rect',
        x: r(ox + p.x * k), y: r(oy + p.y * k), width: r(p.w * k), height: r(p.h * k),
        ...(p.fill ? { backgroundColor: '#000000' } : { borderColor: '#000000', borderWidth: r((p.stroke || 1) * k) })
      };
    }
    if (p.kind === 'qr') {
      return {
        id: `elem_${p.id}`, type: 'qrcode',
        x: r(ox + p.x * k), y: r(oy + p.y * k), width: r(p.size * k), height: r(p.size * k),
        content: p.content, variableBinding: p.binding, errorCorrectionLevel: 'M'
      };
    }
    const h = p.size * 1.25 * k;
    const align = p.align || 'left';
    const xMm = ox + p.x * k;
    const labelW = config.widthMm;
    let width: number;
    let x: number;
    if (p.rotate) {
      width = qrSide * k; // ao longo do QR
      x = xMm - width / 2;
    } else if (align === 'center') {
      width = 2 * Math.min(xMm - ox, labelW - ox - xMm);
      x = xMm - width / 2;
    } else if (align === 'right') {
      width = xMm - ox - 1;
      x = xMm - width;
    } else {
      width = (p.maxWidth ? p.maxWidth * k : labelW - xMm - ox - 1);
      x = xMm;
    }
    const y = p.middle || p.rotate ? oy + p.y * k - h / 2 : oy + (p.y - p.size * 0.95) * k;
    return {
      id: `elem_${p.id}`, type: 'text',
      x: r(x), y: r(y), width: r(width), height: r(h),
      content: p.content, variableBinding: p.binding,
      fontSize: r(p.size * k * ptPerMm),
      fontFamily: p.mono ? 'monospace' : 'sans-serif',
      fontWeight: p.bold ? 'bold' : 'normal',
      textAlign: align,
      textColor: p.white ? '#FFFFFF' : '#000000',
      ...(p.rotate ? { rotation: 270 } : {})
    };
  });
}
