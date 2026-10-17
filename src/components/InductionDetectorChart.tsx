/** Gráfico "Detector de Tensão" (Tensão kV × Distância cm) em SVG para a tela. */
import React from 'react';
import type { InductionDetectorPoint } from '../services/inductionDetectorService';
import { INDUCTION_CHART as C, inductionChartPoints, inductionChartXMax } from '../utils/inductionChart';

const pad2 = (n: number) => String(n).padStart(2, '0');

export const InductionDetectorChart: React.FC<{ points: InductionDetectorPoint[]; className?: string }> = ({ points, className }) => {
  const xMax = inductionChartXMax(points);
  const plotW = C.width - C.pad.left - C.pad.right;
  const plotH = C.height - C.pad.top - C.pad.bottom;
  const px = (x: number) => C.pad.left + (x / xMax) * plotW;
  const py = (y: number) => C.pad.top + plotH - (y / C.yMax) * plotH;
  const pts = inductionChartPoints(points);
  const xs: number[] = [];
  for (let x = 0; x <= xMax; x += C.xStep / 5) xs.push(x);
  const ys: number[] = [];
  for (let y = 0; y <= C.yMax; y += C.yStep / 5) ys.push(y);

  return (
    <svg viewBox={`0 0 ${C.width} ${C.height}`} className={className} role="img" aria-label="Gráfico do detector de tensão: tensão em kV pela distância em cm">
      <rect x={0.5} y={0.5} width={C.width - 1} height={C.height - 1} fill="#fff" stroke="#CBD5E1" />
      <text x={C.width / 2} y={18} textAnchor="middle" dominantBaseline="middle" fontSize={15} fill="#475569">{C.title}</text>
      {xs.map(x => (
        <line key={`x${x}`} x1={px(x)} y1={py(0)} x2={px(x)} y2={py(C.yMax)} stroke={x % C.xStep === 0 ? C.grid : C.minorGrid} strokeWidth={x % C.xStep === 0 ? 0.8 : 0.5} />
      ))}
      {ys.map(y => (
        <line key={`y${y}`} x1={px(0)} y1={py(y)} x2={px(xMax)} y2={py(y)} stroke={y % C.yStep === 0 ? C.grid : C.minorGrid} strokeWidth={y % C.yStep === 0 ? 0.8 : 0.5} />
      ))}
      {xs.filter(x => x % C.xStep === 0).map(x => (
        <text key={`lx${x}`} x={px(x)} y={py(0) + 13} textAnchor="middle" fontSize={9} fill={C.axisText}>{pad2(x)}</text>
      ))}
      {ys.filter(y => y % C.yStep === 0).map(y => (
        <text key={`ly${y}`} x={px(0) - 6} y={py(y)} textAnchor="end" dominantBaseline="middle" fontSize={9} fill={C.axisText}>{pad2(y)}</text>
      ))}
      <text x={C.pad.left + plotW / 2} y={C.height - 8} textAnchor="middle" fontSize={10} fill={C.axisText}>Distância (cm)</text>
      <text x={14} y={C.pad.top + plotH / 2} textAnchor="middle" fontSize={10} fill={C.axisText} transform={`rotate(-90 14 ${C.pad.top + plotH / 2})`}>Tensão (KV)</text>
      {pts.length > 1 && (
        <polyline points={pts.map(p => `${px(p.x)},${py(p.y)}`).join(' ')} fill="none" stroke={C.line} strokeWidth={1.8} strokeLinejoin="round" />
      )}
      {pts.map(p => <circle key={`${p.x}-${p.y}`} cx={px(p.x)} cy={py(p.y)} r={3} fill={C.line} />)}
    </svg>
  );
};

export default InductionDetectorChart;
