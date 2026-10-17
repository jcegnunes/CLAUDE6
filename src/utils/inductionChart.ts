/**
 * Gráfico "Detector de Tensão" (Tensão kV × Distância cm) do ensaio do
 * detector por indução: imagem PNG (PDF/Word) com a mesma geometria do
 * componente de tela (InductionDetectorChart).
 */
import type { InductionDetectorPoint } from '../services/inductionDetectorService';

export const INDUCTION_CHART = {
  width: 560,
  height: 300,
  pad: { left: 52, right: 18, top: 36, bottom: 40 },
  xMax: 140,
  xStep: 20,
  yMax: 60,
  yStep: 10,
  line: '#F4A259',
  grid: '#E2E8F0',
  minorGrid: '#F1F5F9',
  axisText: '#64748B',
  title: 'Detector de Tensão'
};

/** Escala do eixo X: 140 cm (como no modelo) ou mais, se alguma distância passar. */
export function inductionChartXMax(points: InductionDetectorPoint[]): number {
  const max = Math.max(0, ...points.map(p => p.distance_cm || 0));
  return Math.max(INDUCTION_CHART.xMax, Math.ceil((max * 1.1) / INDUCTION_CHART.xStep) * INDUCTION_CHART.xStep);
}

/** Pontos ligados no gráfico: só os com distância obtida, na ordem da tensão. */
export function inductionChartPoints(points: InductionDetectorPoint[]): Array<{ x: number; y: number }> {
  return points
    .filter(p => p.detected !== false && p.distance_cm && p.distance_cm > 0)
    .map(p => ({ x: p.distance_cm as number, y: p.voltage_kV }))
    .sort((a, b) => a.y - b.y);
}

const pad2 = (n: number) => String(n).padStart(2, '0');

export function generateInductionChartDataUrl(points: InductionDetectorPoint[]): string {
  if (typeof document === 'undefined') return '';
  const C = INDUCTION_CHART;
  const scale = 3;
  const canvas = document.createElement('canvas');
  canvas.width = C.width * scale;
  canvas.height = C.height * scale;
  const ctx = canvas.getContext('2d');
  if (!ctx) return '';
  ctx.scale(scale, scale);

  const xMax = inductionChartXMax(points);
  const plotW = C.width - C.pad.left - C.pad.right;
  const plotH = C.height - C.pad.top - C.pad.bottom;
  const px = (x: number) => C.pad.left + (x / xMax) * plotW;
  const py = (y: number) => C.pad.top + plotH - (y / C.yMax) * plotH;

  ctx.fillStyle = '#FFFFFF';
  ctx.fillRect(0, 0, C.width, C.height);
  ctx.strokeStyle = '#CBD5E1';
  ctx.lineWidth = 0.8;
  ctx.strokeRect(0.5, 0.5, C.width - 1, C.height - 1);

  // título
  ctx.fillStyle = '#475569';
  ctx.font = '15px Helvetica, Arial, sans-serif';
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  ctx.fillText(C.title, C.width / 2, 18);

  // grade fina (a cada 1/5 da divisão) e principal
  for (let x = 0; x <= xMax; x += C.xStep / 5) {
    ctx.strokeStyle = x % C.xStep === 0 ? C.grid : C.minorGrid;
    ctx.lineWidth = x % C.xStep === 0 ? 0.8 : 0.5;
    ctx.beginPath(); ctx.moveTo(px(x), py(0)); ctx.lineTo(px(x), py(C.yMax)); ctx.stroke();
  }
  for (let y = 0; y <= C.yMax; y += C.yStep / 5) {
    ctx.strokeStyle = y % C.yStep === 0 ? C.grid : C.minorGrid;
    ctx.lineWidth = y % C.yStep === 0 ? 0.8 : 0.5;
    ctx.beginPath(); ctx.moveTo(px(0), py(y)); ctx.lineTo(px(xMax), py(y)); ctx.stroke();
  }

  // rótulos dos eixos
  ctx.fillStyle = C.axisText;
  ctx.font = '9px Helvetica, Arial, sans-serif';
  ctx.textAlign = 'center';
  ctx.textBaseline = 'top';
  for (let x = 0; x <= xMax; x += C.xStep) ctx.fillText(pad2(x), px(x), py(0) + 5);
  ctx.textAlign = 'right';
  ctx.textBaseline = 'middle';
  for (let y = 0; y <= C.yMax; y += C.yStep) ctx.fillText(pad2(y), px(0) - 6, py(y));
  ctx.font = '10px Helvetica, Arial, sans-serif';
  ctx.textAlign = 'center';
  ctx.textBaseline = 'alphabetic';
  ctx.fillText('Distância (cm)', C.pad.left + plotW / 2, C.height - 8);
  ctx.save();
  ctx.translate(14, C.pad.top + plotH / 2);
  ctx.rotate(-Math.PI / 2);
  ctx.fillText('Tensão (KV)', 0, 0);
  ctx.restore();

  // curva
  const pts = inductionChartPoints(points);
  if (pts.length) {
    ctx.strokeStyle = C.line;
    ctx.lineWidth = 1.8;
    ctx.lineJoin = 'round';
    ctx.beginPath();
    pts.forEach((p, i) => (i ? ctx.lineTo(px(p.x), py(p.y)) : ctx.moveTo(px(p.x), py(p.y))));
    ctx.stroke();
    ctx.fillStyle = C.line;
    pts.forEach(p => { ctx.beginPath(); ctx.arc(px(p.x), py(p.y), 3, 0, Math.PI * 2); ctx.fill(); });
  }

  return canvas.toDataURL('image/png');
}
