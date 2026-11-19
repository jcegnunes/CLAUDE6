/**
 * Ilustrações dos sistemas de captação (desenhos próprios do app, em SVG):
 * mastro com captor Franklin, minicaptores, malha de Faraday, telhas metálicas,
 * anel superior (cobre/alumínio) e "outro".
 */
import React from 'react';
import type { AirTerminationType } from '../types';

const WALL = '#e2e8f0', EDGE = '#64748b', ROOF = '#cbd5e1', CU = '#b45309', AL = '#94a3b8', SKY = '#f0f9ff';

/** Prédio em perspectiva simples: frente + cobertura plana (topo em y=40). */
const Building: React.FC<{ roof?: string }> = ({ roof = ROOF }) => (
  <>
    <rect x="0" y="0" width="160" height="110" fill={SKY} />
    {/* cobertura (paralelogramo) */}
    <path d="M30,40 L120,40 L140,26 L50,26 Z" fill={roof} stroke={EDGE} strokeWidth="1" />
    {/* fachada lateral */}
    <path d="M120,40 L140,26 L140,92 L120,104 Z" fill="#cbd5e1" stroke={EDGE} strokeWidth="1" />
    {/* fachada frontal */}
    <rect x="30" y="40" width="90" height="64" fill={WALL} stroke={EDGE} strokeWidth="1" />
    {[0, 1, 2].map(r => [0, 1, 2, 3].map(c => (
      <rect key={`${r}${c}`} x={38 + c * 20} y={48 + r * 18} width="10" height="9" fill="#bae6fd" stroke="#94a3b8" strokeWidth="0.6" />
    )))}
  </>
);

/** Ponto do topo do prédio (cobertura) em coordenadas u,v ∈ [0,1]. */
const roofPt = (u: number, v: number): [number, number] => [30 + u * 90 + v * 20, 40 - v * 14];

const Franklin = () => {
  const [x, y] = roofPt(0.5, 0.5);
  return (
    <>
      <Building />
      <line x1={x} y1={y} x2={x} y2={y - 26} stroke="#475569" strokeWidth="2.5" />
      <path d={`M${x - 5},${y - 26} L${x},${y - 36} L${x + 5},${y - 26} M${x},${y - 26} L${x},${y - 38}`} stroke={CU} strokeWidth="1.8" fill="none" />
      {/* cone de proteção */}
      <path d={`M${x},${y - 36} L${x - 46},${y + 8} M${x},${y - 36} L${x + 46},${y + 8}`} stroke="#f59e0b" strokeWidth="0.8" strokeDasharray="3 2" />
      {/* descida */}
      <path d={`M${x},${y} L120,40 L120,104`} stroke={CU} strokeWidth="1.5" fill="none" />
    </>
  );
};

const MiniCaptores = () => {
  const pts = [0, 0.33, 0.66, 1].flatMap(u => [roofPt(u, 0), roofPt(u, 1)]);
  return (
    <>
      <Building />
      <path d={`M${roofPt(0, 0)} L${roofPt(1, 0)} L${roofPt(1, 1)} L${roofPt(0, 1)} Z`} fill="none" stroke={CU} strokeWidth="1.4" />
      {pts.map(([x, y], i) => (
        <g key={i}>
          <line x1={x} y1={y} x2={x} y2={y - 9} stroke={CU} strokeWidth="1.6" />
          <circle cx={x} cy={y - 9.5} r="1.4" fill={CU} />
        </g>
      ))}
    </>
  );
};

const Faraday = () => {
  const lines: string[] = [];
  [0, 0.25, 0.5, 0.75, 1].forEach(u => lines.push(`M${roofPt(u, 0)} L${roofPt(u, 1)}`));
  [0, 0.5, 1].forEach(v => lines.push(`M${roofPt(0, v)} L${roofPt(1, v)}`));
  return (
    <>
      <Building />
      <path d={lines.join(' ')} stroke={CU} strokeWidth="1.5" fill="none" />
      <path d="M30,40 L30,104 M120,40 L120,104" stroke={CU} strokeWidth="1.5" />
    </>
  );
};

const Telhas = () => {
  const ribs = Array.from({ length: 10 }, (_, i) => i / 9);
  return (
    <>
      <Building roof="#d1d5db" />
      {/* telhado em duas águas metálico */}
      <path d="M30,40 L50,14 L140,14 L120,40 Z" fill="#9ca3af" stroke={EDGE} strokeWidth="1" />
      <path d="M120,40 L140,14 L140,26 Z" fill="#cbd5e1" stroke={EDGE} strokeWidth="1" />
      {ribs.map(u => {
        const x0 = 30 + u * 90;
        return <line key={u} x1={x0} y1={40} x2={x0 + 20} y2={14} stroke="#6b7280" strokeWidth="1.1" />;
      })}
      <line x1="50" y1="14" x2="140" y2="14" stroke="#4b5563" strokeWidth="1.5" />
      <path d="M30,40 L30,104" stroke={CU} strokeWidth="1.5" />
      <text x="85" y="10" fontSize="7" textAnchor="middle" fill="#334155" fontWeight="bold">captor natural</text>
    </>
  );
};

const Anel = () => (
  <>
    <Building />
    {/* anel no perímetro do topo/platibanda */}
    <path d={`M${roofPt(0, 0)} L${roofPt(1, 0)} L${roofPt(1, 1)} L${roofPt(0, 1)} Z`} fill="none" stroke={CU} strokeWidth="2.6" />
    <path d={`M${roofPt(0, 0)} L${roofPt(1, 0)} L${roofPt(1, 1)} L${roofPt(0, 1)} Z`} fill="none" stroke={AL} strokeWidth="1" strokeDasharray="4 4" />
    <path d="M30,40 L30,104 M120,40 L120,104 M140,26 L140,92" stroke={CU} strokeWidth="1.5" />
    <text x="80" y="20" fontSize="7" textAnchor="middle" fill="#334155" fontWeight="bold">cobre ou alumínio</text>
  </>
);

const Outro = () => (
  <>
    <Building />
    <text x="75" y="33" fontSize="16" textAnchor="middle" fill="#64748b" fontWeight="bold">?</text>
  </>
);

const ART: Record<AirTerminationType, React.FC> = {
  franklin: Franklin, mini_captores: MiniCaptores, faraday: Faraday, telhas_metalicas: Telhas, anel_superior: Anel, outro: Outro
};

export const AirTerminationArt: React.FC<{ type: AirTerminationType; label: string; className?: string }> = ({ type, label, className = 'w-full h-auto' }) => {
  const Art = ART[type];
  return (
    <svg viewBox="0 0 160 110" className={className} role="img" aria-label={`Ilustração: ${label}`}>
      <Art />
    </svg>
  );
};
