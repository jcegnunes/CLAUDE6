/**
 * Modelo da etiqueta Niimbot — o mesmo desenho do aplicativo (NiimbotPrinterManager.kt).
 *
 * Coordenadas em pontos da impressora (8 pontos/mm, 203 DPI):
 *  - 30x20 mm: 240 x 160 (compacto);
 *  - 50x30 mm: 384 x 240 (padrão; 50 mm = 400 pontos, a cabeça da B1 imprime 384);
 *  - 50x80 mm: 384 x 640 (em pé).
 * Título JVM ENGENHARIA com telefone, TAG e laudo no mesmo tamanho, data do ensaio e próximo
 * reensaio no mesmo formato, QR do validador com o código na vertical à esquerda e "VALIDADOR"
 * abaixo, status numa faixa na borda inferior. Sem bordas laterais: as faixas do título e do
 * status vão de ponta a ponta.
 */
import { TestRecord } from '../types';
import { formatDateBR } from '../utils/dateUtils';
import { ValidationPortalService } from './validationPortalService';

export const NIIMBOT_LABEL_TITLE = 'JVM ENGENHARIA';
export const NIIMBOT_LABEL_PHONE = '61 98530-0679';

type Align = 'left' | 'center' | 'right';

export type LabelPrimitive =
  | { kind: 'rect'; id: string; x: number; y: number; w: number; h: number; fill?: boolean; stroke?: number }
  | {
      kind: 'text'; id: string; x: number; y: number; size: number; content: string;
      bold?: boolean; mono?: boolean; white?: boolean; align?: Align;
      /** largura máxima: a fonte diminui só se o texto passar dela */
      maxWidth?: number;
      /** -90 = texto na vertical, de baixo para cima */
      rotate?: number;
      /** y no meio da altura do texto (faixa de status) */
      middle?: boolean;
      binding?: string;
    }
  | { kind: 'qr'; id: string; x: number; y: number; size: number; content: string; binding?: string };

export interface LabelModel {
  width: number;
  height: number;
  primitives: LabelPrimitive[];
}

const reportNumber = (t: TestRecord) => (t.reportNumber || '').trim() || '—';

export type LabelVariant = '30x20' | '50x30' | '50x80';

/** Modelo da etiqueta no tamanho escolhido. */
export function buildLabelModel(test: TestRecord, variant: LabelVariant): LabelModel {
  const url = ValidationPortalService.buildPublicValidationUrl(test.validationCode);
  const status = `STATUS: ${(test.result || '').toUpperCase()}`;

  if (variant === '50x80') {
    return {
      width: 384,
      height: 640,
      primitives: [
        { kind: 'rect', id: 'header_bg', x: 0, y: 0, w: 384, h: 67, fill: true },
        { kind: 'text', id: 'title', x: 192, y: 31, size: 24, bold: true, white: true, align: 'center', content: NIIMBOT_LABEL_TITLE },
        { kind: 'text', id: 'phone', x: 192, y: 57, size: 18, bold: true, white: true, align: 'center', content: NIIMBOT_LABEL_PHONE },
        { kind: 'text', id: 'norm', x: 358, y: 57, size: 13, bold: true, mono: true, white: true, align: 'right', content: 'NR-10' },
        { kind: 'text', id: 'tag_label', x: 12, y: 104, size: 15, bold: true, content: 'TAG:' },
        { kind: 'text', id: 'tag', x: 92, y: 104, size: 26, bold: true, mono: true, maxWidth: 280, content: test.equipmentTag, binding: '{TAG}' },
        { kind: 'text', id: 'report_label', x: 12, y: 142, size: 15, bold: true, content: 'LAUDO:' },
        { kind: 'text', id: 'report', x: 92, y: 142, size: 26, bold: true, mono: true, maxWidth: 280, content: reportNumber(test), binding: '{NUM_LAUDO}' },
        { kind: 'rect', id: 'test_date_box', x: 8, y: 158, w: 368, h: 66, stroke: 2 },
        { kind: 'text', id: 'test_date_label', x: 16, y: 177, size: 13, bold: true, content: 'DATA DO ENSAIO:' },
        { kind: 'text', id: 'test_date', x: 16, y: 213, size: 30, bold: true, mono: true, content: formatDateBR(test.testDate), binding: '{DATA_ENSAIO}' },
        { kind: 'rect', id: 'retest_box', x: 8, y: 232, w: 368, h: 66, stroke: 2 },
        { kind: 'text', id: 'retest_label', x: 16, y: 251, size: 13, bold: true, content: 'PRÓXIMO REENSAIO / VALIDADE:' },
        { kind: 'text', id: 'retest', x: 16, y: 287, size: 30, bold: true, mono: true, content: formatDateBR(test.retestDueDate), binding: '{VALIDADE_REENSAIO}' },
        { kind: 'qr', id: 'qr', x: 99, y: 310, size: 210, content: url, binding: '{URL_VALIDACAO_QR}' },
        { kind: 'text', id: 'validation_code', x: 94, y: 415, size: 20, bold: true, mono: true, align: 'center', rotate: -90, maxWidth: 202, content: test.validationCode, binding: '{CODIGO_VALIDACAO}' },
        { kind: 'text', id: 'validator', x: 204, y: 542, size: 17, bold: true, align: 'center', content: 'VALIDADOR' },
        { kind: 'rect', id: 'status_bg', x: 0, y: 558, w: 384, h: 82, fill: true },
        { kind: 'text', id: 'status', x: 192, y: 599, size: 30, bold: true, white: true, align: 'center', middle: true, content: status, binding: '{STATUS}' }
      ]
    };
  }

  if (variant === '30x20') {
    return {
      width: 240,
      height: 160,
      primitives: [
        { kind: 'rect', id: 'header_bg', x: 0, y: 0, w: 240, h: 22, fill: true },
        { kind: 'text', id: 'title', x: 6, y: 16, size: 10, bold: true, white: true, content: NIIMBOT_LABEL_TITLE },
        { kind: 'text', id: 'phone', x: 234, y: 16, size: 9, bold: true, white: true, align: 'right', content: NIIMBOT_LABEL_PHONE },
        { kind: 'text', id: 'tag_label', x: 6, y: 37, size: 8, bold: true, content: 'TAG:' },
        { kind: 'text', id: 'tag', x: 40, y: 37, size: 12, bold: true, mono: true, maxWidth: 112, content: test.equipmentTag, binding: '{TAG}' },
        { kind: 'text', id: 'report_label', x: 6, y: 52, size: 8, bold: true, content: 'LAUDO:' },
        { kind: 'text', id: 'report', x: 40, y: 52, size: 12, bold: true, mono: true, maxWidth: 112, content: reportNumber(test), binding: '{NUM_LAUDO}' },
        { kind: 'text', id: 'test_date_label', x: 6, y: 64, size: 8, bold: true, content: 'ENSAIO:' },
        { kind: 'text', id: 'test_date', x: 6, y: 78, size: 13, bold: true, mono: true, content: formatDateBR(test.testDate), binding: '{DATA_ENSAIO}' },
        { kind: 'text', id: 'retest_label', x: 6, y: 90, size: 8, bold: true, content: 'VALIDADE:' },
        { kind: 'text', id: 'retest', x: 6, y: 104, size: 13, bold: true, mono: true, content: formatDateBR(test.retestDueDate), binding: '{VALIDADE_REENSAIO}' },
        { kind: 'qr', id: 'qr', x: 160, y: 26, size: 76, content: url, binding: '{URL_VALIDACAO_QR}' },
        { kind: 'text', id: 'validation_code', x: 157, y: 64, size: 10, bold: true, mono: true, align: 'center', rotate: -90, maxWidth: 72, content: test.validationCode, binding: '{CODIGO_VALIDACAO}' },
        { kind: 'text', id: 'validator', x: 198, y: 110, size: 7, bold: true, align: 'center', content: 'VALIDADOR' },
        { kind: 'rect', id: 'status_bg', x: 0, y: 124, w: 240, h: 36, fill: true },
        { kind: 'text', id: 'status', x: 120, y: 142, size: 14, bold: true, white: true, align: 'center', middle: true, content: status, binding: '{STATUS}' }
      ]
    };
  }

  return {
    width: 384,
    height: 240,
    primitives: [
      { kind: 'rect', id: 'header_bg', x: 0, y: 0, w: 384, h: 34, fill: true },
      { kind: 'text', id: 'title', x: 10, y: 24, size: 14, bold: true, white: true, content: NIIMBOT_LABEL_TITLE },
      { kind: 'text', id: 'phone', x: 236, y: 24, size: 12, bold: true, white: true, align: 'center', content: NIIMBOT_LABEL_PHONE },
      { kind: 'text', id: 'norm', x: 360, y: 24, size: 12, bold: true, mono: true, white: true, align: 'right', content: 'NR-10' },
      { kind: 'text', id: 'tag_label', x: 10, y: 56, size: 11, bold: true, content: 'TAG:' },
      { kind: 'text', id: 'tag', x: 62, y: 56, size: 18, bold: true, mono: true, maxWidth: 160, content: test.equipmentTag, binding: '{TAG}' },
      { kind: 'text', id: 'report_label', x: 10, y: 80, size: 11, bold: true, content: 'LAUDO:' },
      { kind: 'text', id: 'report', x: 62, y: 80, size: 18, bold: true, mono: true, maxWidth: 160, content: reportNumber(test), binding: '{NUM_LAUDO}' },
      { kind: 'rect', id: 'test_date_box', x: 8, y: 90, w: 218, h: 47, stroke: 2 },
      { kind: 'text', id: 'test_date_label', x: 14, y: 103, size: 9, bold: true, content: 'DATA DO ENSAIO:' },
      { kind: 'text', id: 'test_date', x: 14, y: 128, size: 19, bold: true, mono: true, content: formatDateBR(test.testDate), binding: '{DATA_ENSAIO}' },
      { kind: 'rect', id: 'retest_box', x: 8, y: 142, w: 218, h: 47, stroke: 2 },
      { kind: 'text', id: 'retest_label', x: 14, y: 155, size: 9, bold: true, content: 'PRÓXIMO REENSAIO / VALIDADE:' },
      { kind: 'text', id: 'retest', x: 14, y: 180, size: 19, bold: true, mono: true, content: formatDateBR(test.retestDueDate), binding: '{VALIDADE_REENSAIO}' },
      { kind: 'qr', id: 'qr', x: 252, y: 40, size: 120, content: url, binding: '{URL_VALIDACAO_QR}' },
      { kind: 'text', id: 'validation_code', x: 249, y: 100, size: 14, bold: true, mono: true, align: 'center', rotate: -90, maxWidth: 112, content: test.validationCode, binding: '{CODIGO_VALIDACAO}' },
      { kind: 'text', id: 'validator', x: 312, y: 172, size: 11, bold: true, align: 'center', content: 'VALIDADOR' },
      { kind: 'rect', id: 'status_bg', x: 0, y: 196, w: 384, h: 44, fill: true },
      { kind: 'text', id: 'status', x: 192, y: 218, size: 20, bold: true, white: true, align: 'center', middle: true, content: status, binding: '{STATUS}' }
    ]
  };
}

const SANS = '"Helvetica Neue", Roboto, Arial, sans-serif';
const MONO = '"Droid Sans Mono", Consolas, "Courier New", monospace';

function loadImage(src: string): Promise<HTMLImageElement | null> {
  return new Promise(resolve => {
    if (!src) return resolve(null);
    const img = new Image();
    img.onload = () => resolve(img);
    img.onerror = () => resolve(null);
    img.src = src;
  });
}

/**
 * Desenha o modelo ocupando o canvas inteiro (centralizado, mesma proporção).
 * [qrDataUrl] é o QR já gerado para o endereço do validador.
 */
export async function drawLabelModel(
  ctx: CanvasRenderingContext2D,
  model: LabelModel,
  canvasWidth: number,
  canvasHeight: number,
  qrDataUrl: string
): Promise<void> {
  const qrImg = await loadImage(qrDataUrl);
  const k = Math.min(canvasWidth / model.width, canvasHeight / model.height);
  const ox = (canvasWidth - model.width * k) / 2;
  const oy = (canvasHeight - model.height * k) / 2;

  ctx.save();
  ctx.fillStyle = '#FFFFFF';
  ctx.fillRect(0, 0, canvasWidth, canvasHeight);
  ctx.translate(ox, oy);
  ctx.scale(k, k);

  for (const p of model.primitives) {
    if (p.kind === 'rect') {
      if (p.fill) {
        ctx.fillStyle = '#000000';
        ctx.fillRect(p.x, p.y, p.w, p.h);
      } else {
        ctx.strokeStyle = '#000000';
        ctx.lineWidth = p.stroke || 1;
        ctx.strokeRect(p.x, p.y, p.w, p.h);
      }
    } else if (p.kind === 'qr') {
      if (qrImg) {
        ctx.imageSmoothingEnabled = false; // módulos nítidos na térmica
        ctx.drawImage(qrImg, p.x, p.y, p.size, p.size);
        ctx.imageSmoothingEnabled = true;
      }
    } else {
      let size = p.size;
      const font = (s: number) => `${p.bold ? 'bold ' : ''}${s}px ${p.mono ? MONO : SANS}`;
      ctx.font = font(size);
      if (p.maxWidth) {
        const w = ctx.measureText(p.content).width;
        if (w > p.maxWidth) {
          size = Math.max(5, (size * p.maxWidth) / w);
          ctx.font = font(size);
        }
      }
      ctx.fillStyle = p.white ? '#FFFFFF' : '#000000';
      ctx.textAlign = p.align || 'left';
      ctx.textBaseline = p.middle ? 'middle' : 'alphabetic';
      if (p.rotate) {
        ctx.save();
        ctx.translate(p.x, p.y);
        ctx.rotate((p.rotate * Math.PI) / 180);
        ctx.fillText(p.content, 0, 0);
        ctx.restore();
      } else {
        ctx.fillText(p.content, p.x, p.y);
      }
    }
  }
  ctx.textBaseline = 'alphabetic';
  ctx.restore();
}

/** Escala do modelo (pontos → mm) dentro de uma etiqueta de largura x altura em mm. */
export function modelMmScale(model: LabelModel, widthMm: number, heightMm: number) {
  const k = Math.min(widthMm / (model.width / 8), heightMm / (model.height / 8)) / 8; // mm por ponto
  return { k, ox: (widthMm - model.width * k) / 2, oy: (heightMm - model.height * k) / 2 };
}
