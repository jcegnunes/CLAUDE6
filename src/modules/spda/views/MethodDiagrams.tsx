/**
 * Esquemas ilustrativos dos ensaios (desenhos próprios do app, em SVG — não reproduzem figuras das normas):
 * - Figura 1: continuidade das descidas por injeção direta de corrente, a 4 fios, com os conectores de ensaio
 *   abertos nas caixas de inspeção (ABNT NBR 5419-3:2026, Anexo F);
 * - Figura 2: resistência de aterramento pelo método da queda de potencial (ABNT NBR 15749).
 */
import React from 'react';

const INK = '#0f172a', MUTED = '#64748b', CU = '#b45309', CUR = '#dc2626', POT = '#2563eb', GROUND = '#a16207';

const Arrow: React.FC<{ id: string; color: string }> = ({ id, color }) => (
  <marker id={id} viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
    <path d="M0,0 L10,5 L0,10 z" fill={color} />
  </marker>
);

/**
 * Figura 1 — continuidade das descidas por injeção direta de corrente (ABNT NBR 5419-3:2026, Anexo F):
 * conectores de ensaio abertos nas caixas de inspeção; mede-se o trecho do início da descida ensaiada,
 * pela captação, até o início da descida adjacente (micro-ohmímetro a 4 fios). Repete-se descida a descida.
 */
export const ContinuityDiagram: React.FC<{
  /** descida: início da descida → captação → início da descida adjacente;
   *  aterramento: início do condutor de aterramento → eletrodo → início do condutor de aterramento adjacente */
  target?: 'descida' | 'aterramento';
}> = ({ target = 'descida' }) => {
  const xs = [110, 250, 390, 530];
  const yCap = 70, yBox = 232, yGnd = 250, yEl = 330;
  const top = yBox - 10, bot = yBox + 12; // terminais superior/inferior do conector aberto
  const a = xs[1], b = xs[2];              // trecho ensaiado entre D2 e D3
  const ground = target === 'aterramento';
  const tp = ground ? bot : top;           // terminal em que os cabos do instrumento são ligados
  const path = ground
    ? `M${a},${bot} L${a},${yEl} L${b},${yEl} L${b},${bot}`
    : `M${a},${top} L${a},${yCap} L${b},${yCap} L${b},${top}`;
  return (
    <svg viewBox="0 0 660 496" className="w-full h-auto" role="img"
      aria-label={ground
        ? 'Esquema do ensaio de continuidade do eletrodo de aterramento: conectores de ensaio abertos nas caixas de inspeção e micro-ohmímetro a quatro fios entre o início dos condutores de aterramento de duas descidas adjacentes, passando pelo eletrodo'
        : 'Esquema do ensaio de continuidade das descidas: conectores de ensaio abertos nas caixas de inspeção e micro-ohmímetro a quatro fios entre o início de duas descidas adjacentes, passando pela captação'}>
      {/* solo */}
      <rect x="40" y={yGnd} width="580" height="110" fill="#fef3c7" opacity="0.45" />
      <line x1="40" y1={yGnd} x2="620" y2={yGnd} stroke={GROUND} strokeWidth="1.5" />

      {/* trecho ensaiado (destaque) */}
      <path d={path} fill="none" stroke="#fde047" strokeWidth="12" strokeLinejoin="round" opacity="0.7" />

      {/* captação */}
      <line x1="60" y1={yCap} x2="600" y2={yCap} stroke={CU} strokeWidth="3" />
      {[85, 180, 320, 460, 575].map(x => <g key={x}><line x1={x} y1={yCap} x2={x} y2={yCap - 22} stroke={CU} strokeWidth="2.5" /><circle cx={x} cy={yCap - 24} r="3" fill={CU} /></g>)}
      <text x="60" y={yCap - 32} fontSize="11" fontWeight="700" fill={INK}>Captação</text>

      {/* descidas, caixas de inspeção (conector aberto) e condutores de aterramento */}
      {xs.map((x, i) => (
        <g key={x}>
          <line x1={x} y1={yCap} x2={x} y2={top} stroke={CU} strokeWidth="3" />
          <text x={x + 6} y={yCap + 18} fontSize="10" fontWeight="700" fill={INK}>D{i + 1}</text>
          <rect x={x - 22} y={yBox - 22} width="44" height="46" rx="3" fill="#ffffff" stroke="#475569" strokeWidth="1.5" />
          <circle cx={x} cy={top} r="3.5" fill={CU} />
          <circle cx={x} cy={bot} r="3.5" fill={CU} />
          <line x1={x - 5} y1={top + 5} x2={x + 5} y2={bot - 5} stroke={MUTED} strokeWidth="1" strokeDasharray="2 2" />
          <line x1={x} y1={bot} x2={x} y2={yEl} stroke={CU} strokeWidth="3" />
        </g>
      ))}
      {/* eletrodo de aterramento (anel) */}
      <line x1="80" y1={yEl} x2="560" y2={yEl} stroke={CU} strokeWidth="4" />
      <text x="80" y={yEl + 18} fontSize="10" fill={INK}>Eletrodo de aterramento (anel)</text>

      {/* rótulos */}
      <g fontSize="9.5" fill={INK}>
        <text x="570" y={yBox - 26}>Caixa de inspeção</text>
        <text x="570" y={yBox - 14}>conector de</text>
        <text x="570" y={yBox - 2} fontWeight="700">ensaio aberto</text>
        <line x1="568" y1={yBox - 10} x2={xs[3] + 23} y2={yBox - 2} stroke={MUTED} strokeWidth="1" />
        <text x="570" y={yBox + 40}>Condutor de</text>
        <text x="570" y={yBox + 52}>aterramento</text>
        <line x1="568" y1={yBox + 44} x2={xs[3] + 3} y2={yBox + 50} stroke={MUTED} strokeWidth="1" />
        <text x={xs[0] - 50} y={yCap + 70}>Condutor de</text>
        <text x={xs[0] - 50} y={yCap + 82}>descida</text>
        <text x={(a + b) / 2} y={ground ? yEl - 10 : yCap + 22} textAnchor="middle" fontWeight="700" fill="#a16207">trecho ensaiado</text>
      </g>

      {/* micro-ohmímetro: bornes no topo — C1/P1 para a descida ensaiada, P2/C2 para a adjacente */}
      <rect x="236" y="380" width="168" height="72" rx="8" fill="#fff" stroke={INK} strokeWidth="1.5" />
      <rect x="268" y="404" width="104" height="24" rx="3" fill="#0f172a" />
      <text x="320" y="421" textAnchor="middle" fontSize="13" fontWeight="700" fill="#22c55e" fontFamily="monospace">096,4 mΩ</text>
      <text x="320" y="444" textAnchor="middle" fontSize="9.5" fontWeight="700" fill={INK}>Micro-ohmímetro</text>
      {[['C1', 262, CUR], ['P1', 284, POT], ['P2', 356, POT], ['C2', 378, CUR]].map(([t, x, c]) => (
        <g key={t as string}>
          <circle cx={x as number} cy="380" r="4.5" fill="#fff" stroke={c as string} strokeWidth="2" />
          <text x={x as number} y="397" textAnchor="middle" fontSize="8.5" fontWeight="700" fill={c as string}>{t}</text>
        </g>
      ))}

      {/* cabos: C1/P1 no início de D2; P2/C2 no início de D3 (por fora da caixa, sem cruzar) */}
      <path d={`M262,375 C262,330 ${a - 12},${ground ? 320 : 300} ${a - 2},${tp + 1}`} fill="none" stroke={CUR} strokeWidth="1.8" />
      <path d={`M284,375 C284,320 ${a + 14},${ground ? 310 : 300} ${a + 2},${tp + 2}`} fill="none" stroke={POT} strokeWidth="1.5" strokeDasharray="5 3" />
      <path d={`M356,375 C356,320 ${b - 14},${ground ? 310 : 300} ${b - 2},${tp + 2}`} fill="none" stroke={POT} strokeWidth="1.5" strokeDasharray="5 3" />
      <path d={`M378,375 C378,330 ${b + 12},${ground ? 320 : 300} ${b + 2},${tp + 1}`} fill="none" stroke={CUR} strokeWidth="1.8" />

      {/* legenda */}
      <g fontSize="9.5" fill={INK}>
        <line x1="40" y1="388" x2="66" y2="388" stroke={CUR} strokeWidth="2" /><text x="72" y="391">Corrente I (C1–C2): 1 A a 5 A</text>
        <line x1="40" y1="404" x2="66" y2="404" stroke={POT} strokeWidth="1.5" strokeDasharray="5 3" /><text x="72" y="407">Tensão V (P1–P2)</text>
        <text x="40" y="424" fontWeight="700">R = V / I ≤ 200 mΩ</text>
      </g>
      <text x="40" y="476" fontSize="9" fill={MUTED}>{ground
        ? 'Conectores de ensaio abertos. Mede-se do início do condutor de aterramento de uma descida, pelo eletrodo, até o início do da descida adjacente;'
        : 'Conectores de ensaio abertos. Mede-se do início da descida ensaiada, pela captação, até o início da descida adjacente;'}</text>
      <text x="40" y="489" fontSize="9" fill={MUTED}>{ground
        ? 'repetir até ensaiar todos os trechos do eletrodo de aterramento (D1–D2, D2–D3, D3–D4, D4–D1).'
        : 'repetir até ensaiar todos os trechos (D1–D2, D2–D3, D3–D4, D4–D1).'}</text>
    </svg>
  );
};

/** Figura 2 — queda de potencial: E (ensaiado), P (potencial) a 52/62/72 % e C (corrente) a uma distância D. */
export const FallOfPotentialDiagram: React.FC<{ step?: 5 | 10 }> = ({ step = 10 }) => {
  const xE = 80, xC = 580, y0 = 200;
  const xs = (pct: number) => xE + ((xC - xE) * pct) / 100;
  const ps = [62 - step, 62, 62 + step];
  return (
    <svg viewBox="0 0 660 330" className="w-full h-auto" role="img"
      aria-label={`Esquema do método da queda de potencial: eletrodo ensaiado E, eletrodo de potencial P em ${ps.join(', ')} por cento da distância e eletrodo de corrente C`}>
      <defs><Arrow id="ar-d" color={MUTED} /></defs>

      {/* solo */}
      <rect x="20" y={y0} width="620" height="110" fill="#fef3c7" opacity="0.5" />
      <line x1="20" y1={y0} x2="640" y2={y0} stroke={GROUND} strokeWidth="2" />

      {/* terrômetro */}
      <rect x="250" y="20" width="160" height="78" rx="8" fill="#fff" stroke={INK} strokeWidth="1.5" />
      <rect x="265" y="30" width="130" height="24" rx="3" fill="#0f172a" />
      <text x="330" y="47" textAnchor="middle" fontSize="13" fontWeight="700" fill="#22c55e" fontFamily="monospace">2,16 Ω</text>
      <text x="330" y="70" textAnchor="middle" fontSize="10" fontWeight="700" fill={INK}>Terrômetro</text>
      {[['E', 275], ['P', 330], ['C', 385]].map(([t, x]) => (
        <g key={t as string}><circle cx={x as number} cy="86" r="5" fill="#fff" stroke={INK} strokeWidth="1.5" /><text x={(x as number) + 9} y="90" fontSize="9" fontWeight="700" fill={INK}>{t}</text></g>
      ))}

      {/* eletrodo ensaiado E (descida do SPDA + haste/anel) */}
      <line x1={xE} y1="60" x2={xE} y2={y0} stroke={CU} strokeWidth="3" />
      <rect x={xE - 9} y={y0 - 14} width="18" height="14" fill="#fff" stroke={CU} strokeWidth="1.5" />
      <line x1={xE} y1={y0} x2={xE} y2={y0 + 80} stroke={CU} strokeWidth="5" />
      <text x={xE - 6} y="52" fontSize="10" fill={MUTED}>descida do SPDA</text>
      <text x={xE} y={y0 + 98} textAnchor="middle" fontSize="11" fontWeight="700" fill={INK}>E</text>
      <text x={xE + 10} y={y0 + 60} fontSize="9" fill={MUTED}>eletrodo ensaiado</text>

      {/* eletrodo de corrente C */}
      <line x1={xC} y1={y0 - 10} x2={xC} y2={y0 + 60} stroke={CUR} strokeWidth="4" />
      <text x={xC} y={y0 + 78} textAnchor="middle" fontSize="11" fontWeight="700" fill={CUR}>C</text>
      <text x={xC - 4} y={y0 + 92} textAnchor="end" fontSize="9" fill={MUTED}>eletrodo auxiliar de corrente</text>

      {/* eletrodo de potencial P em três posições */}
      {ps.map((p, i) => (
        <g key={p}>
          <line x1={xs(p)} y1={y0 - 10} x2={xs(p)} y2={y0 + 40} stroke={POT} strokeWidth={i === 1 ? 4 : 2.5} opacity={i === 1 ? 1 : 0.55} strokeDasharray={i === 1 ? undefined : '4 3'} />
          <text x={xs(p)} y={y0 + 56} textAnchor="middle" fontSize="10" fontWeight={i === 1 ? 700 : 400} fill={POT}>{p} %</text>
        </g>
      ))}
      <text x={xs(62)} y={y0 + 70} textAnchor="middle" fontSize="11" fontWeight="700" fill={POT}>P</text>

      {/* cabos */}
      <path d={`M275,91 L275,120 L${xE},120`} fill="none" stroke={INK} strokeWidth="1.5" />
      <path d={`M330,91 L330,140 L${xs(62)},140 L${xs(62)},${y0 - 10}`} fill="none" stroke={POT} strokeWidth="1.5" strokeDasharray="5 3" />
      <path d={`M385,91 L385,110 L${xC},110 L${xC},${y0 - 10}`} fill="none" stroke={CUR} strokeWidth="1.8" />

      {/* cotas */}
      <line x1={xE} y1={y0 + 112} x2={xC} y2={y0 + 112} stroke={MUTED} strokeWidth="1" markerStart="url(#ar-d)" markerEnd="url(#ar-d)" />
      <text x={(xE + xC) / 2} y={y0 + 108} textAnchor="middle" fontSize="10" fill={INK}>D — distância E → C</text>
      <line x1={xE} y1={y0 + 24} x2={xs(62) - 3} y2={y0 + 24} stroke={MUTED} strokeWidth="1" markerStart="url(#ar-d)" markerEnd="url(#ar-d)" />
      <text x={(xE + xs(62)) / 2} y={y0 + 20} textAnchor="middle" fontSize="10" fill={INK}>0,62 · D</text>

      <text x="20" y="324" fontSize="9" fill={MUTED}>
        Corrente injetada entre E e C; tensão medida entre E e P. Patamar: leituras com P a {ps[0]} %, {ps[1]} % e {ps[2]} % de D dentro de ±5 % da leitura a 62 % (resistência adotada).
      </text>
    </svg>
  );
};
