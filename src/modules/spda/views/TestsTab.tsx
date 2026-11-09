/**
 * Aba "Ensaios" — NBR 5419:2026:
 * - continuidade elétrica conforme o Anexo F da Parte 3 (status automático);
 * - resistência de aterramento apenas informativa (Parte 3, 7.1.4).
 */
import React from 'react';
import { AlertTriangle, MessageSquare, Plus, Trash2 } from 'lucide-react';
import { newContinuityTest, newGroundingTest } from '../repository';
import {
  CONTINUITY_KIND_LABEL, CONTINUITY_KIND_SHORT, KIND_SUBSYSTEM, fmtMilliOhm, fromMilli, toMilli, RESISTIVITY, aboveDesign, continuityStatus, currentOutOfRange, effectiveLimit,
  fallOfPotentialPlateau, fmtOhm, groundingStatus, groundingValue, kindOf, normLimit, theoreticalResistance
} from '../rules';
import type { ConductorMaterial, ContinuityKind, ContinuityTest, GroundingMethod, GroundingTest, SpdaBuilding, SpdaInspection } from '../types';
import { DecimalField, TestBadge, btnSecondary, cardCls, inputCls } from './ui';
import { TestPhotos } from './TestPhotos';
import { ReadingCaptureButton } from './ReadingCapture';

type Props = {
  value: SpdaInspection; onChange: (patch: Partial<SpdaInspection>) => void; readOnly: boolean;
  /** Só estes tipos de ensaio de continuidade (telas por subsistema); padrão: todos */
  kinds?: ContinuityKind[];
  /** Mostra o bloco de resistência de aterramento (padrão: sim) */
  showGrounding?: boolean;
  /** Edificação ensaiada: mostra só os ensaios dela e vincula os novos */
  building?: SpdaBuilding;
};

const cell = 'px-2 py-1.5 align-top';
const smallInput = `${inputCls} !p-1.5`;
const KINDS = Object.keys(CONTINUITY_KIND_LABEL) as ContinuityKind[];
const MATERIALS = Object.keys(RESISTIVITY) as ConductorMaterial[];

/** Observações do ensaio (condições, pontos, ocorrências) — saem no laudo e no relatório do subsistema. */
const TestObs: React.FC<{ value?: string; readOnly: boolean; onChange: (v: string) => void }> = ({ value, readOnly, onChange }) => {
  if (readOnly && !value?.trim()) return null;
  return (
    <label className="block border-t border-dashed border-slate-200 pt-2 mt-1">
      <span className="text-[10px] font-bold text-slate-600 uppercase tracking-wide flex items-center gap-1"><MessageSquare className="w-3 h-3" /> Observações do ensaio</span>
      <textarea
        className={`${inputCls} mt-1`}
        rows={2}
        disabled={readOnly}
        placeholder="Ex.: conector de ensaio aberto, solo úmido, ponto de medição escarificado, valor instável…"
        value={value || ''}
        onChange={e => onChange(e.target.value)}
      />
    </label>
  );
};

const ContinuityRow: React.FC<{ t: ContinuityTest; readOnly: boolean; set: (p: Partial<ContinuityTest>) => void; remove: () => void }> = ({ t, readOnly, set, remove }) => {
  const kind = kindOf(t);
  const fixed = normLimit(kind);
  const theory = theoreticalResistance(t);
  const limit = effectiveLimit(t);
  return (
    <div className="border border-slate-200 rounded-xl p-3 space-y-2">
      <div className="flex flex-wrap items-center gap-2">
        <select
          className={`${smallInput} sm:max-w-sm`}
          disabled={readOnly}
          value={kind}
          aria-label="Tipo de ensaio"
          onChange={e => { const k = e.target.value as ContinuityKind; set({ kind: k, subsystem: KIND_SUBSYSTEM[k] }); }}
        >
          {KINDS.map(k => <option key={k} value={k}>{CONTINUITY_KIND_LABEL[k]}</option>)}
        </select>
        <TestBadge status={continuityStatus(t)} />
        <span className="flex-1" />
        {!readOnly && (
          <button type="button" className="p-1.5 text-slate-400 hover:text-red-600" aria-label="Remover medição" onClick={remove}><Trash2 className="w-3.5 h-3.5" /></button>
        )}
      </div>
      <div className="grid grid-cols-2 sm:grid-cols-6 gap-2">
        <label className="col-span-2 text-[10px] text-slate-500">De (ponto)
          <input className={smallInput} disabled={readOnly} value={t.from} onChange={e => set({ from: e.target.value })} />
        </label>
        <label className="col-span-2 text-[10px] text-slate-500">Para (ponto)
          <input className={smallInput} disabled={readOnly} value={t.to} onChange={e => set({ to: e.target.value })} />
        </label>
        <label className="text-[10px] text-slate-500">Corrente (A)
          <DecimalField className={smallInput} disabled={readOnly} value={t.currentA} onChange={n => set({ currentA: n })} ariaLabel="Corrente" />
        </label>
        <div className="text-[10px] text-slate-500">Medido (mΩ)
          <div className="flex items-center gap-1">
            <DecimalField className={smallInput} disabled={readOnly} value={toMilli(t.resistanceOhm)} onChange={n => set({ resistanceOhm: fromMilli(n) })} ariaLabel="Resistência medida (mΩ)" />
            {!readOnly && (
              <ReadingCaptureButton
                unit="mΩ"
                caption={`${CONTINUITY_KIND_SHORT[kind]} — ${t.from || 'ponto'} → ${t.to || 'ponto'}`}
                onUse={(value, photo) => set({ resistanceOhm: value, ...(photo ? { photos: [...(t.photos || []), photo] } : {}) })}
              />
            )}
          </div>
        </div>
      </div>
      {fixed === null && (
        <div className="grid grid-cols-2 sm:grid-cols-6 gap-2 bg-slate-50 rounded-lg p-2">
          <label className="col-span-2 text-[10px] text-slate-500">Material do condutor
            <select className={smallInput} disabled={readOnly} value={t.material || ''} onChange={e => set({ material: (e.target.value || undefined) as ConductorMaterial | undefined })} aria-label="Material">
              <option value="">—</option>
              {MATERIALS.map(m => <option key={m} value={m}>{RESISTIVITY[m].label}</option>)}
            </select>
          </label>
          <label className="text-[10px] text-slate-500">Comprimento (m)
            <DecimalField className={smallInput} disabled={readOnly} value={t.lengthM} onChange={n => set({ lengthM: n })} ariaLabel="Comprimento" />
          </label>
          <label className="text-[10px] text-slate-500">Seção (mm²)
            <DecimalField className={smallInput} disabled={readOnly} value={t.sectionMm2} onChange={n => set({ sectionMm2: n })} ariaLabel="Seção" />
          </label>
          <label className="text-[10px] text-slate-500">Limite adotado (mΩ)
            <DecimalField className={smallInput} disabled={readOnly} value={toMilli(t.limitOhm)} onChange={n => set({ limitOhm: fromMilli(n) })} ariaLabel="Limite adotado (mΩ)" />
          </label>
          <div className="text-[10px] text-slate-600 self-end">
            ρ·L/S = <b>{fmtMilliOhm(theory, 2)}</b>
            {theory !== null && !readOnly && (
              <button type="button" className="block text-blue-700 hover:underline" onClick={() => set({ limitOhm: Number(theory.toPrecision(3)) })}>usar como limite</button>
            )}
          </div>
        </div>
      )}
      <p className="text-[10px] text-slate-500">
        Critério: {fixed !== null
          ? `≤ ${fmtMilliOhm(fixed)} (${kind === 'F4' ? 'F.4.4' : 'primeira verificação'})`
          : `valor compatível com o material e o comprimento do trecho (${kind === 'EQ' ? 'NBR 5419-4, 9.2.6' : kind === 'F5' ? 'F.5.2.1.3' : 'F.6.2.1.3'})${limit === null ? ' — informe o limite adotado' : `: ≤ ${fmtMilliOhm(limit)}`}`}
      </p>
      {currentOutOfRange(t) && (
        <p className="text-[10px] text-amber-700 flex items-center gap-1"><AlertTriangle className="w-3 h-3" /> Corrente fora da faixa de 1 A a 5 A (F.1.3.1).</p>
      )}
      <TestPhotos
        photos={t.photos}
        readOnly={readOnly}
        onChange={photos => set({ photos })}
        defaultCaption={`${CONTINUITY_KIND_SHORT[kind]} — ${t.from || 'ponto'} → ${t.to || 'ponto'}`}
      />
      <TestObs value={t.obs} readOnly={readOnly} onChange={obs => set({ obs })} />
    </div>
  );
};

export const TestsTab: React.FC<Props> = ({ value: v, onChange, readOnly, kinds, showGrounding = true, building }) => {
  const allow = (k: ContinuityKind) => !kinds || kinds.includes(k);
  const bid = building?.id;
  const mine = (t: { buildingId?: string }) => !bid || t.buildingId === bid;
  const tests = v.continuity.filter(t => mine(t) && allow(kindOf(t)));
  const groundings = v.grounding.filter(mine);
  const newCont = (p: Partial<ContinuityTest>) => newContinuityTest({ ...p, buildingId: bid });
  const newGround = (p: Partial<GroundingTest> = {}) => newGroundingTest({ ...p, buildingId: bid });
  const setCont = (id: string, patch: Partial<ContinuityTest>) => onChange({ continuity: v.continuity.map(t => (t.id === id ? { ...t, ...patch } : t)) });
  const setGround = (id: string, patch: Partial<GroundingTest>) => onChange({ grounding: v.grounding.map(t => (t.id === id ? { ...t, ...patch } : t)) });
  const downs = Math.max(2, Math.round((building ? building.downConductorsCount : v.downConductorsCount) || 0));

  const addPerDown = (kind: ContinuityKind) => {
    const add = Array.from({ length: downs }, (_, i) => {
      const a = i + 1;
      const b = i + 2 > downs ? 1 : i + 2;
      if (kind === 'F5') return newCont({ kind, from: `Conector de ensaio D${a}`, to: `Conector de ensaio D${b} (via captação)` });
      if (kind === 'F6') return newCont({ kind, from: `Condutor de aterramento D${a}`, to: `Condutor de aterramento D${b} (via anel)` });
      if (kind === 'F2') return newCont({ kind, from: `Topo do pilar P${a}`, to: `Base do pilar P${a}` });
      if (kind === 'F3') return newCont({ kind, from: `Fundação — ponto ${a}`, to: `Fundação — ponto ${b}` });
      return newCont({ kind, from: `Topo do pilar P${a}`, to: `Base do pilar P${b}` });
    });
    onChange({ continuity: [...v.continuity, ...add] });
  };

  const addDownConductorPoints = () => {
    const existing = new Set(groundings.map(g => g.point));
    const add = Array.from({ length: downs }, (_, i) => `Descida D${i + 1}`).filter(p => !existing.has(p)).map(point => newGround({ point }));
    onChange({ grounding: [...v.grounding, ...add] });
  };

  return (
    <div className="space-y-4">
      {/* ---------------------------------------------------------- continuidade */}
      <section className={cardCls}>
        <div className="px-4 py-3 border-b border-slate-200">
          <h3 className="font-bold text-sm text-slate-900">Ensaio de continuidade elétrica — NBR 5419-3:2026, Anexo F</h3>
          <ul className="text-[11px] text-slate-500 mt-1 list-disc pl-4 space-y-0.5">
            <li>1ª verificação das armaduras (F.1, F.2, F.3): nenhum valor maior que 1 Ω (1000 mΩ); medições cruzadas topo × base em F.1.</li>
            <li>Verificação comprobatória (F.4): entre a captação e o BEP, no máximo 0,2 Ω (200 mΩ).</li>
            <li>Leituras de continuidade em <b>miliohms (mΩ)</b>, como no visor do micro-ohmímetro. Use o ícone de câmera ao lado do campo Medido para ler o visor.</li>
            <li>Descidas não naturais não visíveis (F.5), eletrodo em anel não natural (F.6) e equipotencialização (5419-4, 9.2.6): valor compatível com material × comprimento; abrir os conectores de ensaio.</li>
            <li>Instrumento (F.1.3): 4 fios, 1 A a 5 A, frequência ≠ 60 Hz ou CC. Multímetro e terrômetro-alicate não são admitidos.</li>
          </ul>
        </div>
        <div className="p-3 space-y-3">
          {tests.map(t => (
            <ContinuityRow key={t.id} t={t} readOnly={readOnly} set={p => setCont(t.id, p)} remove={() => onChange({ continuity: v.continuity.filter(x => x.id !== t.id) })} />
          ))}
          {tests.length === 0 && <p className="text-center text-xs text-slate-400 py-3">Nenhuma medição lançada.</p>}
        </div>
        {!readOnly && (
          <div className="px-4 py-3 border-t border-slate-200 flex flex-wrap gap-2">
            {allow('F4') && <button type="button" className={btnSecondary} onClick={() => onChange({ continuity: [...v.continuity, newCont({ kind: 'F4' })] })}><Plus className="w-3.5 h-3.5" /> Comprobatória captação ↔ BEP</button>}
            {allow('F1') && <button type="button" className={btnSecondary} onClick={() => addPerDown('F1')}><Plus className="w-3.5 h-3.5" /> 1ª verificação cruzada ({downs})</button>}
            {allow('F2') && kinds && <button type="button" className={btnSecondary} onClick={() => addPerDown('F2')}><Plus className="w-3.5 h-3.5" /> Pilares topo × base ({downs})</button>}
            {allow('F5') && <button type="button" className={btnSecondary} onClick={() => addPerDown('F5')}><Plus className="w-3.5 h-3.5" /> Descidas não naturais ({downs})</button>}
            {allow('F3') && kinds && <button type="button" className={btnSecondary} onClick={() => addPerDown('F3')}><Plus className="w-3.5 h-3.5" /> Fundação entre pontos adjacentes ({downs})</button>}
            {allow('F6') && <button type="button" className={btnSecondary} onClick={() => addPerDown('F6')}><Plus className="w-3.5 h-3.5" /> Trechos do anel ({downs})</button>}
            {allow('EQ') && <button type="button" className={btnSecondary} onClick={() => onChange({ continuity: [...v.continuity, newCont({ kind: 'EQ', from: 'BEP', to: 'Eletrodo de aterramento' })] })}><Plus className="w-3.5 h-3.5" /> Equipotencialização</button>}
          </div>
        )}
      </section>

      {showGrounding && <>
      {/* ----------------------------------------------------------- aterramento */}
      <section className={cardCls}>
        <div className="px-4 py-3 border-b border-slate-200">
          <h3 className="font-bold text-sm text-slate-900">Resistência de aterramento <span className="text-[10px] font-bold text-slate-500 bg-slate-100 border border-slate-300 rounded px-1.5 py-0.5 ml-1">INFORMATIVO</span></h3>
          <p className="text-[11px] text-slate-500 mt-0.5">
            Pela NBR 5419-3:2026 (7.1.4) a medição de resistência de aterramento não é necessária para verificar a eficácia do SPDA; a integridade do eletrodo
            é verificada pelo ensaio de continuidade (5.5.1.2 e F.6). Registre aqui, se medida, apenas como informação (não gera não conformidade).
            Queda de potencial: leituras a 52 %, 62 % e 72 % — adota-se a de 62 % quando estiverem no patamar (±5 %).
          </p>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-xs min-w-[900px]">
            <thead className="bg-slate-50 text-slate-600 text-[10px] uppercase">
              <tr>
                <th className={`${cell} text-left`}>Ponto</th>
                <th className={`${cell} text-left`}>Método</th>
                <th className={`${cell} text-left w-16`}>Dist. (m)</th>
                <th className={`${cell} text-left w-16`}>52 %</th>
                <th className={`${cell} text-left w-16`}>62 %</th>
                <th className={`${cell} text-left w-16`}>72 %</th>
                <th className={`${cell} text-left w-20`}>Adotado (Ω)</th>
                <th className={`${cell} text-left w-20`}>Projeto (Ω)</th>
                <th className={`${cell} text-left`}>Situação</th>
                <th className={cell}></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {groundings.map(t => {
                const fop = t.method === 'queda_potencial';
                const plateau = fop ? fallOfPotentialPlateau(t) : null;
                return (
                  <React.Fragment key={t.id}>
                  <tr>
                    <td className={cell}><input className={smallInput} disabled={readOnly} value={t.point} onChange={e => setGround(t.id, { point: e.target.value })} aria-label="Ponto" /></td>
                    <td className={cell}>
                      <select className={smallInput} disabled={readOnly} value={t.method} onChange={e => setGround(t.id, { method: e.target.value as GroundingMethod })} aria-label="Método">
                        <option value="queda_potencial">Queda de potencial</option>
                        <option value="alicate">Alicate terrômetro</option>
                        <option value="outro">Outro</option>
                      </select>
                    </td>
                    <td className={cell}><DecimalField className={smallInput} disabled={readOnly || !fop} value={t.currentProbeDistanceM} onChange={n => setGround(t.id, { currentProbeDistanceM: n })} ariaLabel="Distância" /></td>
                    <td className={cell}><DecimalField className={smallInput} disabled={readOnly || !fop} value={t.r52} onChange={n => setGround(t.id, { r52: n })} ariaLabel="Leitura 52%" /></td>
                    <td className={cell}><DecimalField className={smallInput} disabled={readOnly || !fop} value={t.r62} onChange={n => setGround(t.id, { r62: n })} ariaLabel="Leitura 62%" /></td>
                    <td className={cell}><DecimalField className={smallInput} disabled={readOnly || !fop} value={t.r72} onChange={n => setGround(t.id, { r72: n })} ariaLabel="Leitura 72%" /></td>
                    <td className={cell}>
                      <div className="flex items-center gap-1">
                        <DecimalField className={smallInput} disabled={readOnly} value={t.resistanceOhm} onChange={n => setGround(t.id, { resistanceOhm: n })}
                          placeholder={fop && typeof t.r62 === 'number' ? String(t.r62).replace('.', ',') : ''} ariaLabel="Resistência adotada" />
                        {!readOnly && (
                          <ReadingCaptureButton
                            caption={`Resistência de aterramento — ${t.point || 'ponto'}`}
                            onUse={(value, photo) => setGround(t.id, { resistanceOhm: value, ...(photo ? { photos: [...(t.photos || []), photo] } : {}) })}
                          />
                        )}
                      </div>
                    </td>
                    <td className={cell}><DecimalField className={smallInput} disabled={readOnly} value={t.designOhm ?? t.limitOhm} onChange={n => setGround(t.id, { designOhm: n, limitOhm: undefined })} ariaLabel="Valor de projeto" /></td>
                    <td className={`${cell} pt-2`}>
                      <TestBadge status={groundingStatus(t)} />
                      {plateau && (
                        <p className={`text-[10px] mt-0.5 ${plateau.ok ? 'text-emerald-700' : 'text-amber-700'}`}>
                          {plateau.ok ? 'Patamar OK' : 'Fora do patamar'} ({(plateau.maxDeviation * 100).toFixed(1)} %)
                        </p>
                      )}
                      {groundingValue(t) !== null && <p className="text-[10px] text-slate-400">{fmtOhm(groundingValue(t), 2)}</p>}
                      {aboveDesign(t) && <p className="text-[10px] text-amber-700">Acima do valor de projeto</p>}
                    </td>
                    <td className={cell}>
                      {!readOnly && (
                        <button type="button" className="p-1.5 text-slate-400 hover:text-red-600" aria-label="Remover medição" onClick={() => onChange({ grounding: v.grounding.filter(x => x.id !== t.id) })}>
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      )}
                    </td>
                  </tr>
                  {(!readOnly || !!t.photos?.length || !!t.obs?.trim()) && (
                    <tr key={`${t.id}_fotos`}>
                      <td colSpan={10} className="px-2 pb-2">
                        <TestPhotos
                          photos={t.photos}
                          readOnly={readOnly}
                          onChange={photos => setGround(t.id, { photos })}
                          defaultCaption={`Resistência de aterramento — ${t.point || 'ponto'}`}
                        />
                        <TestObs value={t.obs} readOnly={readOnly} onChange={obs => setGround(t.id, { obs })} />
                      </td>
                    </tr>
                  )}
                  </React.Fragment>
                );
              })}
              {groundings.length === 0 && (
                <tr><td colSpan={10} className="px-4 py-4 text-center text-slate-400">Nenhuma medição registrada (opcional).</td></tr>
              )}
            </tbody>
          </table>
        </div>
        {!readOnly && (
          <div className="px-4 py-3 border-t border-slate-200 flex flex-wrap gap-2">
            <button type="button" className={btnSecondary} onClick={() => onChange({ grounding: [...v.grounding, newGround()] })}><Plus className="w-3.5 h-3.5" /> Adicionar ponto</button>
            <button type="button" className={btnSecondary} onClick={addDownConductorPoints}><Plus className="w-3.5 h-3.5" /> Um ponto por descida ({downs})</button>
          </div>
        )}
      </section>
      </>}
    </div>
  );
};
