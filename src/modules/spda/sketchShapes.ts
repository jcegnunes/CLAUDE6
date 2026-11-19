/**
 * Modelo e desenho dos elementos do editor de croqui (setas, linhas, círculos,
 * retângulos e textos), em pixels da imagem base. Funções puras + desenho em canvas.
 */
import type { SketchElement, SketchElementKind } from './types';

export interface Pt { x: number; y: number }

/** Tamanhos padrão proporcionais à imagem. */
export function defaultsFor(w: number, h: number) {
  const base = Math.max(w, h) || 1000;
  return { width: Math.max(2, Math.round(base * 0.005)), fontSize: Math.max(12, Math.round(base * 0.03)) };
}

export function moveElement(el: SketchElement, dx: number, dy: number): SketchElement {
  return { ...el, x1: el.x1 + dx, y1: el.y1 + dy, x2: el.x2 + dx, y2: el.y2 + dy };
}

/** Elemento com tamanho desprezível (clique sem arrastar) — descartado, exceto texto. */
export function isTiny(el: SketchElement, minPx = 4): boolean {
  if (el.kind === 'text') return !el.text?.trim();
  return Math.hypot(el.x2 - el.x1, el.y2 - el.y1) < minPx;
}

/** Pontas da cabeça da seta. */
export function arrowHeadPoints(from: Pt, to: Pt, width: number): [Pt, Pt] {
  const ang = Math.atan2(to.y - from.y, to.x - from.x);
  const len = Math.max(10, width * 4.5);
  const spread = Math.PI / 7;
  return [
    { x: to.x - len * Math.cos(ang - spread), y: to.y - len * Math.sin(ang - spread) },
    { x: to.x - len * Math.cos(ang + spread), y: to.y - len * Math.sin(ang + spread) }
  ];
}

export const textLines = (el: SketchElement) => (el.text || '').split('\n');
export const fontOf = (el: SketchElement) => `${el.bold ? 'bold ' : ''}${Math.round(el.fontSize || 24)}px Arial, Helvetica, sans-serif`;

/** Desenha um elemento no canvas (mesma aparência da tela). */
export function drawElement(ctx: CanvasRenderingContext2D, el: SketchElement): void {
  ctx.save();
  ctx.strokeStyle = el.color;
  ctx.fillStyle = el.color;
  ctx.lineWidth = el.width;
  ctx.lineCap = 'round';
  ctx.lineJoin = 'round';
  const k: SketchElementKind = el.kind;
  if (k === 'arrow' || k === 'line') {
    ctx.beginPath(); ctx.moveTo(el.x1, el.y1); ctx.lineTo(el.x2, el.y2); ctx.stroke();
    if (k === 'arrow') {
      const [a, b] = arrowHeadPoints({ x: el.x1, y: el.y1 }, { x: el.x2, y: el.y2 }, el.width);
      ctx.beginPath(); ctx.moveTo(el.x2, el.y2); ctx.lineTo(a.x, a.y); ctx.lineTo(b.x, b.y); ctx.closePath(); ctx.fill();
    }
  } else if (k === 'circle') {
    const cx = (el.x1 + el.x2) / 2, cy = (el.y1 + el.y2) / 2;
    ctx.beginPath(); ctx.ellipse(cx, cy, Math.abs(el.x2 - el.x1) / 2, Math.abs(el.y2 - el.y1) / 2, 0, 0, Math.PI * 2); ctx.stroke();
  } else if (k === 'rect') {
    ctx.strokeRect(Math.min(el.x1, el.x2), Math.min(el.y1, el.y2), Math.abs(el.x2 - el.x1), Math.abs(el.y2 - el.y1));
  } else if (k === 'text') {
    const size = el.fontSize || 24;
    ctx.font = fontOf(el);
    ctx.textBaseline = 'top';
    textLines(el).forEach((line, i) => {
      const y = el.y1 + i * size * 1.2;
      if (el.halo) { ctx.strokeStyle = '#ffffff'; ctx.lineWidth = Math.max(2, size / 6); ctx.strokeText(line, el.x1, y); }
      ctx.fillText(line, el.x1, y);
    });
  }
  ctx.restore();
}

/** Gera a imagem final (base + elementos) como data URL JPEG. */
export async function renderSketch(baseUrl: string, elements: SketchElement[], quality = 0.92): Promise<string> {
  const img = await new Promise<HTMLImageElement>((resolve, reject) => {
    const i = new Image();
    i.onload = () => resolve(i);
    i.onerror = () => reject(new Error('Não foi possível abrir a imagem.'));
    i.src = baseUrl;
  });
  const canvas = document.createElement('canvas');
  canvas.width = img.naturalWidth;
  canvas.height = img.naturalHeight;
  const ctx = canvas.getContext('2d');
  if (!ctx) throw new Error('Canvas indisponível.');
  ctx.fillStyle = '#ffffff';
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  ctx.drawImage(img, 0, 0);
  elements.forEach(el => drawElement(ctx, el));
  return canvas.toDataURL('image/jpeg', quality);
}
