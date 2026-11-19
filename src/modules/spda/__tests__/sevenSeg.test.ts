import { describe, expect, it } from 'vitest';
import { readBox, readSevenSegment, type Pixels } from '../sevenSeg';

/**
 * Visor sintético: dígitos de 7 segmentos (traços separados, inclinados como num LCD),
 * desenhados num fundo cinza-esverdeado dentro de um painel azul.
 */
const SEG_ON: Record<string, string> = {
  '0': 'abcdef', '1': 'bc', '2': 'abdeg', '3': 'abcdg', '4': 'bcfg', '5': 'acdfg', '6': 'acdefg', '7': 'abc', '8': 'abcdefg', '9': 'abcdfg', '-': 'g'
};

function drawDisplay(text: string, opts: { slant?: number; digitH?: number; panel?: boolean } = {}): Pixels {
  const slant = opts.slant ?? 0.12;
  const dh = opts.digitH ?? 60;
  const dw = Math.round(dh * 0.55), t = Math.max(3, Math.round(dh * 0.12)), gap = Math.round(dh * 0.25);
  const chars = [...text].filter(c => c !== '.');
  const lcdW = chars.length * (dw + gap) + gap * 2 + 20, lcdH = Math.round(dh * 1.6);
  const pad = opts.panel ? 120 : 0;
  const w = lcdW + pad * 2, h = lcdH + pad * 2;
  const data = new Uint8ClampedArray(w * h * 4);
  const set = (x: number, y: number, rgb: [number, number, number]) => {
    x = Math.round(x); y = Math.round(y);
    if (x < 0 || y < 0 || x >= w || y >= h) return;
    const i = (y * w + x) * 4;
    data[i] = rgb[0]; data[i + 1] = rgb[1]; data[i + 2] = rgb[2]; data[i + 3] = 255;
  };
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) set(x, y, opts.panel ? [70, 90, 140] : [180, 190, 185]);
  for (let y = pad; y < pad + lcdH; y++) for (let x = pad; x < pad + lcdW; x++) set(x, y, [180, 190, 185]);
  const ink: [number, number, number] = [60, 65, 70];
  const top = pad + Math.round((lcdH - dh) / 2);
  const rect = (x0: number, y0: number, x1: number, y1: number) => {
    for (let y = y0; y < y1; y++) for (let x = x0; x < x1; x++) set(x + slant * (top + dh - y), y, ink);
  };
  let x = pad + gap + 10;
  for (const c of text) {
    if (c === '.') { rect(x - gap / 2 - t / 2, top + dh - t, x - gap / 2 + t / 2, top + dh); continue; }
    const on = SEG_ON[c];
    const half = top + Math.round(dh / 2);
    const g = 2; // folga entre segmentos (LCD real)
    if (on.includes('a')) rect(x + t + g, top, x + dw - t - g, top + t);
    if (on.includes('g')) rect(x + t + g, half - t / 2, x + dw - t - g, half + t / 2);
    if (on.includes('d')) rect(x + t + g, top + dh - t, x + dw - t - g, top + dh);
    if (on.includes('f')) rect(x, top + g, x + t, half - g);
    if (on.includes('b')) rect(x + dw - t, top + g, x + dw, half - g);
    if (on.includes('e')) rect(x, half + g, x + t, top + dh - g);
    if (on.includes('c')) rect(x + dw - t, half + g, x + dw, top + dh - g);
    x += dw + gap;
  }
  return { w, h, data };
}

const readAll = (p: Pixels) => readBox(p, { x: 0, y: 0, w: p.w, h: p.h });

describe('Leitor de visor de 7 segmentos', () => {
  it.each(['21.4', '0.85', '1234', '567.8', '90.6', '3.07', '188.8'])('lê "%s" no recorte do visor', txt => {
    const p = drawDisplay(txt);
    const r = readAll(p);
    expect(r?.text).toBe(txt);
    expect(r?.value).toBe(Number(txt));
  });

  it('lê dígitos sem inclinação', () => {
    const p = drawDisplay('48.9', { slant: 0 });
    expect(readAll(p)?.text).toBe('48.9');
  });

  it('localiza o visor dentro do painel do instrumento', () => {
    const r = readSevenSegment(drawDisplay('21.4', { panel: true }));
    expect(r?.value).toBe(21.4);
    expect(r?.confidence).toBeGreaterThanOrEqual(90);
  });

  it('sem dígitos → null', () => {
    const w = 200, h = 80;
    const data = new Uint8ClampedArray(w * h * 4).fill(200);
    expect(readSevenSegment({ w, h, data })).toBeNull();
  });
});
