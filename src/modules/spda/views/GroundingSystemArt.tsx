/**
 * Ilustrações dos tipos de aterramento (desenhos próprios do app, em SVG):
 * anel de aterramento em cobre afastado da edificação (com e sem hastes), hastes em linha e malha estrutural na ferragem das fundações.
 */
import React from 'react';
import type { GroundingSystemType } from '../types';

const EDGE = '#64748b', CU = '#b45309', SKY = '#f0f9ff', SOIL = '#fde68a', REBAR = '#7c2d12';

const Scene: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <>
    <rect x="0" y="0" width="160" height="110" fill={SKY} />
    <rect x="0" y="62" width="160" height="48" fill={SOIL} opacity="0.75" />
    <line x1="0" y1="62" x2="160" y2="62" stroke="#a16207" strokeWidth="1" />
    {/* edificação */}
    <rect x="50" y="18" width="60" height="44" fill="#e2e8f0" stroke={EDGE} />
    {[0, 1].map(r => [0, 1, 2].map(c => <rect key={`${r}${c}`} x={57 + c * 18} y={25 + r * 17} width="9" height="9" fill="#bae6fd" stroke="#94a3b8" strokeWidth="0.6" />))}
    {children}
  </>
);

/** Haste de aterramento cravada (ponto de ligação no topo). */
const Rod: React.FC<{ x: number; y: number; len?: number }> = ({ x, y, len = 13 }) => (
  <g>
    <line x1={x} y1={y} x2={x} y2={y + len} stroke="#78716c" strokeWidth="2" />
    <path d={`M${x - 1.6},${y + len} L${x},${y + len + 3} L${x + 1.6},${y + len} Z`} fill="#78716c" />
    <circle cx={x} cy={y} r="2.2" fill="#f8fafc" stroke={CU} strokeWidth="1.2" />
  </g>
);

/** Anel em cobre enterrado, afastado da edificação (elipse em perspectiva), com ou sem hastes. */
const Anel: React.FC<{ rods: boolean }> = ({ rods }) => (
  <Scene>
    <ellipse cx="80" cy="74" rx="68" ry="11" fill="none" stroke={CU} strokeWidth="2.4" />
    {rods && [200, 250, 290, 340, 20, 160].map(deg => {
      const a = (deg * Math.PI) / 180;
      return <Rod key={deg} x={80 + 68 * Math.cos(a)} y={74 + 11 * Math.sin(a)} />;
    })}
    {/* cota do afastamento (acima do solo, à direita) */}
    <path d="M110,58 L148,58 M110,55 L110,61 M148,55 L148,61" stroke="#334155" strokeWidth="0.7" />
    <text x="129" y="54" fontSize="6.5" textAnchor="middle" fill="#334155" fontWeight="bold">≥ 1 m</text>
    {/* ligações das descidas ao anel */}
    <path d="M52,62 L30,70 M108,62 L130,70" stroke={CU} strokeWidth="1.4" />
    <text x="80" y="106" fontSize="7" textAnchor="middle" fill="#334155" fontWeight="bold">{rods ? 'anel em cobre nu + hastes' : 'só o anel em cobre nu'}</text>
  </Scene>
);
const AnelCobre = () => <Anel rods />;
const AnelSemHastes = () => <Anel rods={false} />;

/** Hastes cravadas em linha reta, interligadas por condutor, sem fechar o anel. */
const HastesLinha = () => {
  const xs = [22, 50, 78, 106, 134];
  const y = 80;
  return (
    <Scene>
      <line x1={xs[0]} y1={y} x2={xs[xs.length - 1]} y2={y} stroke={CU} strokeWidth="2.2" />
      {xs.map(x => <Rod key={x} x={x} y={y} len={14} />)}
      {/* ligação da descida à linha de hastes */}
      <path d="M108,62 L106,80" stroke={CU} strokeWidth="1.4" />
      {/* espaçamento entre hastes */}
      <path d={`M${xs[1]},70 L${xs[2]},70 M${xs[1]},67 L${xs[1]},73 M${xs[2]},67 L${xs[2]},73`} stroke="#334155" strokeWidth="0.7" />
      <text x={(xs[1] + xs[2]) / 2} y="68" fontSize="6" textAnchor="middle" fill="#334155" fontWeight="bold">espaç.</text>
      <text x="80" y="108" fontSize="7" textAnchor="middle" fill="#334155" fontWeight="bold">hastes em linha (sem anel)</text>
    </Scene>
  );
};

/** Malha estrutural: ferragem das sapatas/blocos e vigas baldrame interligada. */
const Estrutural = () => (
  <Scene>
    {/* vigas baldrame e sapatas sob a edificação */}
    <rect x="48" y="62" width="64" height="7" fill="#d6d3d1" stroke="#78716c" strokeWidth="0.8" />
    {[50, 77, 104].map(x => <rect key={x} x={x - 2} y="69" width="10" height="20" fill="#d6d3d1" stroke="#78716c" strokeWidth="0.8" />)}
    {[44, 74, 100].map(x => <rect key={x} x={x} y="88" width="16" height="6" fill="#d6d3d1" stroke="#78716c" strokeWidth="0.8" />)}
    {/* ferragem */}
    <path d="M50,65.5 L110,65.5" stroke={REBAR} strokeWidth="1.3" />
    {[53, 80, 107].map(x => <line key={x} x1={x} y1="64" x2={x} y2="92" stroke={REBAR} strokeWidth="1.3" />)}
    {[46, 76, 102].map(x => <line key={x} x1={x} y1="91" x2={x + 12} y2="91" stroke={REBAR} strokeWidth="1.1" />)}
    <text x="80" y="104" fontSize="7" textAnchor="middle" fill="#334155" fontWeight="bold">ferragem das fundações</text>
  </Scene>
);

const ART: Record<GroundingSystemType, React.FC> = { anel_cobre: AnelCobre, anel_sem_hastes: AnelSemHastes, hastes_linha: HastesLinha, estrutural: Estrutural };

export const GroundingSystemArt: React.FC<{ type: GroundingSystemType; label: string; className?: string }> = ({ type, label, className = 'w-full h-auto' }) => {
  const Art = ART[type];
  return (
    <svg viewBox="0 0 160 110" className={className} role="img" aria-label={`Ilustração: ${label}`}>
      <Art />
    </svg>
  );
};
