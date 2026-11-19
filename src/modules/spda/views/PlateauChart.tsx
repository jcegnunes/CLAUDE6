/**
 * Curva da queda de potencial e patamar de resistência (ABNT NBR 15749), a partir dos três pontos
 * (eletrodo de potencial P em S1 = 62 − passo, S = 62 % e S2 = 62 + passo, passo de 10 % ou 5 % da distância E → C):
 * - curva padrão R(x) de E (0 %) a C (100 %), com as duas extremidades (subida junto de E e de C),
 *   escalada para passar pela leitura a 62 % (R62, resistência adotada);
 * - região de patamar (curva dentro de ±5 % de R62) sombreada em verde;
 * - os três pontos medidos, em verde quando dentro da faixa de patamar e em vermelho quando fora;
 * - linha de R62 e, se houver, o valor de projeto.
 */
import React, { useState } from 'react';
import { CheckCircle2, AlertTriangle } from 'lucide-react';
import { FALL_OF_POTENTIAL_TOLERANCE, FOP_NAMES, designValue, fmtMeters, fop3Plateau, fop3Points, fopStandardCurve, type FopSlot } from '../rules';
import type { GroundingTest } from '../types';

const W = 640, H = 290;
const M = { l: 58, r: 22, t: 34, b: 46 };
const PW = W - M.l - M.r, PH = H - M.t - M.b;
const IN = '#16a34a', OUT = '#dc2626', CURVE = '#0f766e';
const fmtR = (v: number) => `${v.toLocaleString('pt-BR', { maximumFractionDigits: 2 })} Ω`;
const fmtDev = (v: number) => `${(v * 100).toLocaleString('pt-BR', { maximumFractionDigits: 1, signDisplay: 'always' })} %`;

function niceMax(v: number): { max: number; step: number } {
  const raw = v / 4;
  const mag = Math.pow(10, Math.floor(Math.log10(raw)));
  const step = ([1, 2, 2.5, 3, 5, 10].find(m => m * mag >= raw) || 10) * mag;
  return { max: step * 4, step };
}

export const PlateauChart: React.FC<{ t: GroundingTest; print?: boolean; /** só o gráfico (sem título, selos, cartões e nota) */ bare?: boolean }> = ({ t, print, bare }) => {
  const [hover, setHover] = useState<number | null>(null);
  const pts = fop3Points(t).filter((p): p is { slot: FopSlot; pct: number; r: number } => p.r !== null);
  if (pts.length === 0) return null;

  const D = typeof t.currentProbeDistanceM === 'number' && t.currentProbeDistanceM > 0 ? t.currentProbeDistanceM : null;
  const tol = FALL_OF_POTENTIAL_TOLERANCE;
  const pl = fop3Plateau(t);
  const r62 = pl?.r62 ?? null;
  const std = r62 !== null ? fopStandardCurve(r62) : null;
  const design = designValue(t);
  const meters = (p: number) => (D ? fmtMeters(Math.round(D * p) / 100) : '');

  // escala Y: até ~2× R62 (extremidades da curva), leituras e valor de projeto (se não for muito maior)
  const top = Math.max(r62 !== null ? r62 * 2.2 : 0, ...pts.map(p => p.r * 1.15), design !== null && r62 !== null && design <= r62 * 6 ? design * 1.12 : 0);
  const { max: yMax, step } = niceMax(top || 1);
  const xs = (p: number) => M.l + (p / 100) * PW;
  const ys = (v: number) => M.t + PH - (Math.min(v, yMax) / yMax) * PH;
  const yTicks = Array.from({ length: 5 }, (_, i) => i * step);
  const posPcts = fop3Points(t).map(p => p.pct);
  const xTicks = [...new Set([0, 20, 40, ...posPcts, 80, 100])].sort((a, b) => a - b);
  const clipId = `fop-clip-${t.id}`;
  const curvePath = std ? std.points.map((p, i) => `${i ? 'L' : 'M'}${xs(p.pct).toFixed(1)},${ys(p.r).toFixed(1)}`).join(' ') : '';
  const inBand = (slot: FopSlot) => pl?.inBand[slot] !== false;

  return (
    <figure className={bare ? 'spda-avoid' : `bg-white border border-slate-200 rounded-xl p-3 ${print ? 'spda-avoid' : ''} max-w-3xl`} aria-label="Curva de patamar da queda de potencial">
      {!bare && <figcaption className="flex flex-wrap items-start gap-2 mb-1">
        <div className="flex-1 min-w-[220px]">
          <p className="text-[12px] font-black text-slate-900 uppercase tracking-wide">Curva da queda de potencial e patamar de resistência <span className="text-[10px] font-normal text-slate-500 normal-case">(ABNT NBR 15749)</span></p>
          <p className="text-[10px] text-slate-500">Ponto: {t.point || '—'}{D ? ` · distância E → C = ${fmtMeters(D)}` : ''}</p>
        </div>
        {pl && pts.length === 3 && (
          <span className={`inline-flex items-center gap-1 text-[10px] font-bold px-2 py-1 rounded-full border ${pl.ok ? 'bg-emerald-50 text-emerald-800 border-emerald-300' : 'bg-red-50 text-red-800 border-red-300'}`}>
            {pl.ok ? <CheckCircle2 className="w-3.5 h-3.5" /> : <AlertTriangle className="w-3.5 h-3.5" />}
            {pl.ok ? `Patamar verificado (±${Math.round(tol * 100)} %)` : `Fora do patamar: ${3 - pl.count} ponto${3 - pl.count > 1 ? 's' : ''}`}
          </span>
        )}
        {r62 !== null && (
          <span className="text-[10px] font-mono font-bold px-2 py-1 rounded border border-slate-300 bg-slate-50 text-slate-700">
            R = {fmtR(r62)}{pl ? ` | desvio máx. ${(pl.maxDeviation * 100).toLocaleString('pt-BR', { maximumFractionDigits: 1 })} %` : ''}
          </span>
        )}
      </figcaption>}

      <svg viewBox={`0 0 ${W} ${H}`} className="w-full h-auto" role="img"
        aria-label={`Curva padrão de queda de potencial com as medições: ${pts.map(p => `${p.pct} %: ${fmtR(p.r)}`).join('; ')}`}>
        <defs><clipPath id={clipId}><rect x={M.l} y={M.t} width={PW} height={PH} /></clipPath></defs>

        {/* legenda */}
        <g fontSize={10} fill="#334155">
          <circle cx={M.l + 6} cy={14} r={4.5} fill={IN} stroke="#fff" strokeWidth={1.5} />
          <text x={M.l + 14} y={14} dominantBaseline="middle">no patamar</text>
          <circle cx={M.l + 82} cy={14} r={4.5} fill={OUT} stroke="#fff" strokeWidth={1.5} />
          <text x={M.l + 90} y={14} dominantBaseline="middle">fora do patamar</text>
          <line x1={M.l + 178} x2={M.l + 198} y1={14} y2={14} stroke={CURVE} strokeWidth={2} />
          <text x={M.l + 202} y={14} dominantBaseline="middle">curva padrão R(x)</text>
          <rect x={M.l + 300} y={9} width={14} height={10} fill="#dcfce7" stroke="#86efac" />
          <text x={M.l + 318} y={14} dominantBaseline="middle">região de patamar (±{Math.round(tol * 100)} % de R62)</text>
        </g>

        {/* grade */}
        {yTicks.map((v, i) => (
          <g key={i}>
            <line x1={M.l} x2={W - M.r} y1={ys(v)} y2={ys(v)} stroke="#e2e8f0" strokeDasharray={i ? '3 3' : undefined} />
            <text x={M.l - 6} y={ys(v)} textAnchor="end" dominantBaseline="middle" fontSize={10} fill="#64748b">{v.toLocaleString('pt-BR', { maximumFractionDigits: 2 })} Ω</text>
          </g>
        ))}
        {xTicks.map(p => <line key={p} x1={xs(p)} x2={xs(p)} y1={M.t} y2={M.t + PH} stroke="#f1f5f9" />)}

        {/* região de patamar da curva padrão */}
        {std && (
          <g>
            <rect x={xs(std.plateau.fromPct)} width={xs(std.plateau.toPct) - xs(std.plateau.fromPct)} y={M.t} height={PH} fill="#dcfce7" opacity={0.75} />
            <text x={(xs(std.plateau.fromPct) + xs(std.plateau.toPct)) / 2} y={M.t + 12} textAnchor="middle" fontSize={10} fontWeight={700} fill="#15803d">
              Patamar ({std.plateau.fromPct.toLocaleString('pt-BR')} % a {std.plateau.toPct.toLocaleString('pt-BR')} %)
            </text>
          </g>
        )}

        {/* valor de projeto e R62 */}
        {design !== null && design <= yMax && (
          <g>
            <line x1={M.l} x2={W - M.r} y1={ys(design)} y2={ys(design)} stroke={OUT} strokeWidth={1.2} strokeDasharray="6 4" />
            <text x={W - M.r - 2} y={ys(design) - 4} textAnchor="end" fontSize={9.5} fill={OUT}>valor de projeto {fmtR(design)}</text>
          </g>
        )}
        {r62 !== null && (
          <g>
            <line x1={M.l} x2={W - M.r} y1={ys(r62)} y2={ys(r62)} stroke="#334155" strokeWidth={1} strokeDasharray="2 3" />
            <text x={M.l + 4} y={ys(r62) - 4} fontSize={9.5} fill="#334155">R62: {fmtR(r62)}</text>
          </g>
        )}

        {/* curva padrão com as extremidades (subida junto de E e de C) */}
        {std && <path d={curvePath} fill="none" stroke={CURVE} strokeWidth={2.2} strokeLinejoin="round" clipPath={`url(#${clipId})`} />}

        {/* eixos */}
        <line x1={M.l} x2={W - M.r} y1={M.t + PH} y2={M.t + PH} stroke="#94a3b8" />
        <line x1={M.l} x2={M.l} y1={M.t} y2={M.t + PH} stroke="#94a3b8" />
        {xTicks.map(p => (
          <g key={p}>
            <line x1={xs(p)} x2={xs(p)} y1={M.t + PH} y2={M.t + PH + 4} stroke="#94a3b8" />
            <text x={xs(p)} y={M.t + PH + 15} textAnchor="middle" fontSize={9.5} fontWeight={posPcts.includes(p) ? 700 : 400} fill="#334155">{p} %</text>
          </g>
        ))}
        <text x={M.l} y={H - 8} fontSize={9.5} fontWeight={700} fill="#0f172a">E</text>
        <text x={W - M.r} y={H - 8} textAnchor="end" fontSize={9.5} fontWeight={700} fill="#0f172a">C</text>
        <text x={M.l + PW / 2} y={H - 8} textAnchor="middle" fontSize={10} fill="#475569">Distância do eletrodo de potencial (% da distância E → C)</text>

        {/* pontos medidos */}
        {pts.map((p, i) => {
          const ok = inBand(p.slot);
          const on = hover === i;
          const dev = r62 ? (p.r - r62) / r62 : null;
          return (
            <g key={p.pct} onPointerEnter={print ? undefined : () => setHover(i)} onPointerLeave={print ? undefined : () => setHover(null)}>
              <line x1={xs(p.pct)} x2={xs(p.pct)} y1={ys(p.r)} y2={M.t + PH} stroke={ok ? IN : OUT} strokeWidth={1} strokeDasharray="2 2" opacity={0.6} />
              <circle cx={xs(p.pct)} cy={ys(p.r)} r={14} fill="transparent" />
              <circle cx={xs(p.pct)} cy={ys(p.r)} r={on ? 7.5 : 6.5} fill={ok ? IN : OUT} stroke="#ffffff" strokeWidth={2} />
              {on && (
                <text x={xs(p.pct)} y={ys(p.r) - 12} textAnchor="middle" fontSize={10} fontWeight={700} fill="#0f172a" stroke="#fff" strokeWidth={3} paintOrder="stroke">
                  {FOP_NAMES[p.slot]} ({p.pct} %): {fmtR(p.r)}{dev !== null && p.slot !== 1 ? ` (${fmtDev(dev)})` : ''}
                </text>
              )}
              <title>{`${FOP_NAMES[p.slot]} — ${p.pct} %${D ? ` (${meters(p.pct)})` : ''}: ${fmtR(p.r)}${dev !== null && p.slot !== 1 ? `, ${fmtDev(dev)} de R62` : ''} — ${ok ? 'no patamar' : 'fora do patamar'}`}</title>
            </g>
          );
        })}
      </svg>

      {/* cartões das três medições */}
      {!bare && <><div className="grid grid-cols-3 gap-2 mt-2">
        {fop3Points(t).map(p => {
          const ok = p.r !== null && inBand(p.slot);
          const dev = p.r !== null && r62 ? (p.r - r62) / r62 : null;
          return (
            <div key={p.pct} className={`rounded-lg border px-2 py-1.5 text-center ${p.r === null ? 'bg-slate-50 border-slate-200' : ok ? 'bg-emerald-50 border-emerald-300' : 'bg-red-50 border-red-300'}`}>
              <p className="text-[10px] font-bold text-slate-600">{FOP_NAMES[p.slot]} ({p.pct} %){p.slot === 1 ? ' · adotado' : ''}</p>
              <p className="text-sm font-black text-slate-900">{p.r !== null ? fmtR(p.r) : '—'}</p>
              <p className="text-[10px] text-slate-500">
                {D ? meters(p.pct) : 'sem distância'}
                {p.r !== null && <span className={`ml-1 font-bold ${ok ? 'text-emerald-700' : 'text-red-700'}`}>{p.slot === 1 ? 'referência' : `${dev !== null ? fmtDev(dev) : ''} ${ok ? '· no patamar' : '· fora'}`}</span>}
              </p>
            </div>
          );
        })}
      </div>
      <p className="text-[9px] text-slate-400 mt-1">Curva padrão: referência teórica (eletrodo hemisférico) ajustada à leitura a 62 %; a região de patamar é o trecho dela dentro de ±{Math.round(tol * 100)} % de R62.</p></>}
    </figure>
  );
};

/** Bloco de impressão (laudo / relatório): curva de patamar de cada medição com as três leituras. */
export const FopPrintBlock: React.FC<{ tests: GroundingTest[]; label?: (t: GroundingTest) => string }> = ({ tests, label }) => {
  const list = tests.filter(t => t.method === 'queda_potencial' && fop3Points(t).every(p => p.r !== null));
  if (!list.length) return null;
  return (
    <div className="mt-3 space-y-3">
      <p className="font-bold text-[11px] text-[#0A2540] border-b border-slate-300">Curvas de patamar — queda de potencial (ABNT NBR 15749)</p>
      {list.map(t => (
        <div key={t.id} className="spda-avoid">
          {label && <p className="text-[10px] font-bold text-slate-700 mb-1">{label(t)}</p>}
          <PlateauChart t={t} print />
        </div>
      ))}
    </div>
  );
};
