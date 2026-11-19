/**
 * Ilustrações dos tipos de descida (desenhos próprios do app, em SVG):
 * descida externa (cobre/alumínio), descida interna ao pilar estrutural (armaduras)
 * e descida natural em pilar de ferro (estrutura metálica).
 */
import React from 'react';
import type { DownConductorType } from '../types';

const EDGE = '#64748b', CU = '#b45309', SKY = '#f0f9ff', SOIL = '#fde68a';

const Ground = () => (
  <>
    <rect x="0" y="0" width="160" height="110" fill={SKY} />
    <rect x="0" y="96" width="160" height="14" fill={SOIL} opacity="0.7" />
    <line x1="0" y1="96" x2="160" y2="96" stroke="#a16207" strokeWidth="1" />
  </>
);

const Windows: React.FC<{ x0: number; cols: number }> = ({ x0, cols }) => (
  <>
    {[0, 1, 2].map(r => Array.from({ length: cols }, (_, c) => (
      <rect key={`${r}${c}`} x={x0 + c * 18} y={30 + r * 20} width="9" height="10" fill="#bae6fd" stroke="#94a3b8" strokeWidth="0.6" />
    )))}
  </>
);

/** Descida externa: condutor aparente na fachada, com fixadores e caixa de inspeção. */
const Externa = () => (
  <>
    <Ground />
    <rect x="30" y="20" width="100" height="76" fill="#e2e8f0" stroke={EDGE} />
    <Windows x0={46} cols={4} />
    {[34, 126].map(x => (
      <g key={x}>
        <line x1={x} y1="16" x2={x} y2="92" stroke={CU} strokeWidth="2.2" />
        {[28, 44, 60, 76].map(y => <rect key={y} x={x - 2.5} y={y} width="5" height="2" fill="#475569" />)}
        <rect x={x - 5} y="90" width="10" height="7" fill="#f8fafc" stroke="#475569" strokeWidth="0.8" />
        <line x1={x} y1="97" x2={x} y2="106" stroke={CU} strokeWidth="2" />
      </g>
    ))}
    <line x1="30" y1="16" x2="130" y2="16" stroke={CU} strokeWidth="2" />
    <text x="80" y="12" fontSize="7" textAnchor="middle" fill="#334155" fontWeight="bold">cobre ou alumínio</text>
  </>
);

/** Descida interna ao pilar estrutural: corte do pilar de concreto mostrando as armaduras. */
const PilarEstrutural = () => (
  <>
    <Ground />
    <rect x="30" y="20" width="100" height="76" fill="#e2e8f0" stroke={EDGE} />
    <Windows x0={58} cols={3} />
    {/* pilar em corte */}
    <rect x="34" y="20" width="18" height="86" fill="#d6d3d1" stroke="#78716c" />
    {[38, 43, 48].map(x => <line key={x} x1={x} y1="20" x2={x} y2="106" stroke="#7c2d12" strokeWidth="1.4" />)}
    {[28, 40, 52, 64, 76, 88, 100].map(y => <line key={y} x1="36" y1={y} x2="50" y2={y} stroke="#7c2d12" strokeWidth="0.8" />)}
    <rect x="108" y="20" width="18" height="86" fill="#d6d3d1" stroke="#78716c" />
    {[112, 117, 122].map(x => <line key={x} x1={x} y1="20" x2={x} y2="106" stroke="#7c2d12" strokeWidth="1.4" />)}
    <text x="80" y="14" fontSize="7" textAnchor="middle" fill="#334155" fontWeight="bold">armaduras do pilar</text>
  </>
);

/** Descida natural em pilar de ferro: estrutura metálica (perfil I) contínua. */
const PilarMetalico = () => (
  <>
    <Ground />
    {/* fechamento */}
    <rect x="30" y="24" width="100" height="72" fill="#e5e7eb" stroke={EDGE} />
    {[0, 1, 2, 3, 4, 5, 6, 7].map(i => <line key={i} x1={38 + i * 12} y1="24" x2={38 + i * 12} y2="96" stroke="#cbd5e1" strokeWidth="0.8" />)}
    {/* pilares em perfil I */}
    {[34, 80, 126].map(x => (
      <g key={x}>
        <rect x={x - 4} y="18" width="8" height="82" fill="#6b7280" stroke="#374151" strokeWidth="0.8" />
        <line x1={x} y1="18" x2={x} y2="100" stroke="#9ca3af" strokeWidth="1.5" />
        <rect x={x - 6} y="98" width="12" height="4" fill="#374151" />
      </g>
    ))}
    {/* viga de cobertura */}
    <rect x="28" y="14" width="104" height="5" fill="#6b7280" stroke="#374151" strokeWidth="0.8" />
    <text x="80" y="10" fontSize="7" textAnchor="middle" fill="#334155" fontWeight="bold">pilar de ferro/aço</text>
  </>
);

const ART: Record<DownConductorType, React.FC> = { externa: Externa, pilar_estrutural: PilarEstrutural, pilar_metalico: PilarMetalico };

export const DownConductorArt: React.FC<{ type: DownConductorType; label: string; className?: string }> = ({ type, label, className = 'w-full h-auto' }) => {
  const Art = ART[type];
  return (
    <svg viewBox="0 0 160 110" className={className} role="img" aria-label={`Ilustração: ${label}`}>
      <Art />
    </svg>
  );
};
