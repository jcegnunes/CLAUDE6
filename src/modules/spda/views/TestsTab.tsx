/**
 * Aba "Ensaios" — NBR 5419:2026:
 * - continuidade elétrica conforme o Anexo F da Parte 3 (status automático, critério único ≤ 200 mΩ);
 * - resistência de aterramento apenas informativa (Parte 3, 7.1.4).
 *
 * Cada medição fica só para leitura; Editar abre um rascunho, Salvar grava,
 * Cancelar descarta. Medição nova (Adicionar) só entra na lista ao Salvar.
 */
import React, { useEffect, useRef, useState } from 'react';
import { AlertTriangle, Anchor, ArrowDownToLine, ShieldCheck, Zap, Copy, MessageSquare, Pencil, Plus, Save, Trash2, X } from 'lucide-react';
import { newContinuityTest, newGroundingTest } from '../repository';
import {
  fromMilli, toMilli, aboveDesign, continuityStatus, currentOutOfRange,
  fallOfPotentialPlateau, probeDistances, fopPcts, fopStep, fmtMeters, fmtOhm, groundingStatus, groundingValue, kindOf, newId, fmtMilliOhm, CONTINUITY_LIMIT_OHM,
  CONTINUITY_GROUP, CONTINUITY_GROUP_INFO, CONTINUITY_KIND_LABEL, continuityCode, continuityGroupOf, type ContinuityGroup
} from '../rules';
import type { ContinuityKind, ContinuityTest, GroundingMethod, GroundingTest, SpdaBuilding, SpdaInspection } from '../types';
import { DecimalField, Field, RESULT_ACCENT, TestBadge, TestResultBanner, btnDanger, btnPrimary, btnSecondary, cardCls, inputCls } from './ui';
import { TestPhotos } from './TestPhotos';
import { PlateauChart } from './PlateauChart';
import { ReadingCaptureButton } from './ReadingCapture';
import { BleMeasureButton } from './BleMeasureButton';
import { confirmAsk, notify } from './dialogs';

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

/** Barra de botões de cada ensaio: Excluir · Duplicar · Editar · Cancelar · Salvar. */
const TestActions: React.FC<{
  locked: boolean; editing: boolean; busy: boolean; isNew: boolean;
  onDelete: () => void; onDuplicate: () => void; onEdit: () => void; onCancel: () => void; onSave: () => void;
}> = ({ locked, editing, busy, isNew, onDelete, onDuplicate, onEdit, onCancel, onSave }) => (
  <div className="flex flex-wrap justify-end gap-2 pt-2 mt-1 border-t border-slate-200">
    <button type="button" className={btnDanger} disabled={locked || busy || isNew} onClick={onDelete}><Trash2 className="w-3.5 h-3.5" /> Excluir</button>
    <button type="button" className={btnSecondary} disabled={locked || busy || isNew} onClick={onDuplicate}><Copy className="w-3.5 h-3.5" /> Duplicar</button>
    <button type="button" className={btnSecondary} disabled={locked || busy} onClick={onEdit}><Pencil className="w-3.5 h-3.5" /> Editar</button>
    <button type="button" className={btnSecondary} disabled={locked || !editing} onClick={onCancel}><X className="w-3.5 h-3.5" /> Cancelar</button>
    <button type="button" className={btnPrimary} disabled={locked || !editing} onClick={onSave}><Save className="w-3.5 h-3.5" /> Salvar</button>
  </div>
);

/** Rascunho de uma medição em edição. */
function useDraft<T extends { id: string }>() {
  const [draft, setDraft] = useState<T | null>(null);
  const [isNew, setIsNew] = useState(false);
  return {
    draft,
    isNew,
    start: (t: T, fresh = false) => { setDraft({ ...t }); setIsNew(fresh); },
    update: (p: Partial<T>) => setDraft(d => (d ? { ...d, ...p } : d)),
    clear: () => { setDraft(null); setIsNew(false); }
  };
}

/** Medição de continuidade: só origem, destino, corrente injetada e valor medido (critério único ≤ 200 mΩ). */
const GROUP_STYLE: Record<ContinuityGroup, { chip: string; bar: string; Icon: React.ElementType }> = {
  captacao: { chip: 'bg-amber-500 text-white', bar: 'bg-amber-50 border-amber-200 text-amber-900', Icon: Zap },
  descida: { chip: 'bg-sky-600 text-white', bar: 'bg-sky-50 border-sky-200 text-sky-900', Icon: ArrowDownToLine },
  aterramento: { chip: 'bg-emerald-700 text-white', bar: 'bg-emerald-50 border-emerald-200 text-emerald-900', Icon: Anchor },
  dps: { chip: 'bg-violet-600 text-white', bar: 'bg-violet-50 border-violet-200 text-violet-900', Icon: ShieldCheck }
};

/** Identificação da medição: código, subsistema, edificação e item do Anexo F. */
const ContinuityIdentity: React.FC<{ t: ContinuityTest; code: string; buildingName?: string }> = ({ t, code, buildingName }) => {
  const g = continuityGroupOf(t);
  const st = GROUP_STYLE[g];
  return (
    <div className={`flex flex-wrap items-center gap-x-3 gap-y-1 border rounded-lg px-2 py-1.5 ${st.bar}`} aria-label={`Medição ${code} — subsistema ${CONTINUITY_GROUP_INFO[g].label}`}>
      <span className={`inline-flex items-center gap-1 rounded-md px-2 py-1 text-xs font-black tracking-wide ${st.chip}`}><st.Icon className="w-3.5 h-3.5" /> {code}</span>
      <span className="text-[11px]"><span className="opacity-70">Subsistema:</span> <b>{CONTINUITY_GROUP_INFO[g].label}</b></span>
      {buildingName && <span className="text-[11px]"><span className="opacity-70">Edificação:</span> <b>{buildingName}</b></span>}
      <span className="text-[11px] opacity-80">{CONTINUITY_KIND_LABEL[kindOf(t)]}</span>
    </div>
  );
};

const ContinuityRow: React.FC<{ t: ContinuityTest; code: string; buildingName?: string; readOnly: boolean; editing: boolean; set: (p: Partial<ContinuityTest>) => void; actions: React.ReactNode; instrumentSerial?: string }> = ({ t, code, buildingName, readOnly, editing, set, actions, instrumentSerial }) => {
  const caption = `${code} · ${CONTINUITY_GROUP_INFO[continuityGroupOf(t)].label}${buildingName ? ` · ${buildingName}` : ''} — continuidade ${t.from || 'ponto'} → ${t.to || 'ponto'}`;
  const status = continuityStatus(t);
  const limit = fmtMilliOhm(CONTINUITY_LIMIT_OHM);
  const detail = status === 'conforme'
    ? <>Valor medido <b>{fmtMilliOhm(t.resistanceOhm)}</b> ≤ {limit} — dentro do critério (NBR 5419-3:2026, F.4.4).</>
    : status === 'nao_conforme'
      ? <>Valor medido <b>{fmtMilliOhm(t.resistanceOhm)}</b> &gt; {limit} — acima do limite; entra como não conformidade no laudo.</>
      : <>Aguardando o valor medido. Critério: ≤ {limit}.</>;
  return (
    <div className={`border border-l-[6px] rounded-xl p-3 space-y-2 ${editing ? 'border-blue-400 ring-2 ring-blue-100' : 'border-slate-200'} ${RESULT_ACCENT[status]}`}>
      <ContinuityIdentity t={t} code={code} buildingName={buildingName} />
      <TestResultBanner
        status={status}
        detail={detail}
        extra={editing ? <span className="text-[11px] font-bold text-blue-700 text-right">Editando — Salvar grava,<br />Cancelar descarta.</span> : undefined}
      />
      <div className="grid grid-cols-2 sm:grid-cols-6 gap-2">
        <label className="col-span-2 text-[10px] text-slate-500">Origem (ponto)
          <input className={smallInput} disabled={readOnly} value={t.from} onChange={e => set({ from: e.target.value })} />
        </label>
        <label className="col-span-2 text-[10px] text-slate-500">Destino (ponto)
          <input className={smallInput} disabled={readOnly} value={t.to} onChange={e => set({ to: e.target.value })} />
        </label>
        <label className="text-[10px] text-slate-500">Corrente injetada (A)
          <DecimalField className={smallInput} disabled={readOnly} value={t.currentA} onChange={n => set({ currentA: n })} ariaLabel="Corrente injetada" />
        </label>
        <div className="text-[10px] text-slate-500">Valor medido (mΩ)
          <div className="flex items-center gap-1">
            <DecimalField className={smallInput} disabled={readOnly} value={toMilli(t.resistanceOhm)} onChange={n => set({ resistanceOhm: fromMilli(n), reading: undefined })} ariaLabel="Resistência medida (mΩ)" />
            {!readOnly && (
              <BleMeasureButton
                testName={code}
                preferredSerial={instrumentSerial}
                onUse={(value, currentA, reading) => set({ resistanceOhm: value, currentA, reading })}
              />
            )}
            {!readOnly && (
              <ReadingCaptureButton
                unit="mΩ"
                caption={caption}
                onUse={(value, photo) => set({ resistanceOhm: value, reading: undefined, ...(photo ? { photos: [...(t.photos || []), photo] } : {}) })}
              />
            )}
          </div>
        </div>
      </div>
      {t.reading && (
        <p className="text-[10px] text-blue-700 flex items-center gap-1">
          Lido pelo Bluetooth: {t.reading.instrument}{t.reading.deviceTestNo != null ? `, ensaio nº ${t.reading.deviceTestNo}` : ''} — {new Date(t.reading.at).toLocaleString('pt-BR')}
        </p>
      )}
      {currentOutOfRange(t) && (
        <p className="text-[10px] text-amber-700 flex items-center gap-1"><AlertTriangle className="w-3 h-3" /> Corrente fora da faixa de 1 A a 5 A (F.1.3.1).</p>
      )}
      <TestPhotos photos={t.photos} readOnly={readOnly} onChange={photos => set({ photos })} defaultCaption={caption} />
      <TestObs value={t.obs} readOnly={readOnly} onChange={obs => set({ obs })} />
      {actions}
    </div>
  );
};

export const TestsTab: React.FC<Props> = ({ value: v, onChange, readOnly, kinds, showGrounding = true, building }) => {
  const allow = (k: ContinuityKind) => !kinds || kinds.includes(k);
  /** Subsistema da tela (Captação/Descida/Aterramento/DPS); na aba Ensaios (todos os tipos) fica indefinido. */
  const screenGroup: ContinuityGroup | undefined = kinds?.length ? CONTINUITY_GROUP[kinds[0]] : undefined;
  const bid = building?.id;
  const mine = (t: { buildingId?: string }) => !bid || t.buildingId === bid;
  const tests = v.continuity.filter(t => mine(t) && allow(kindOf(t)));
  const groundings = v.grounding.filter(mine);
  const newCont = (p: Partial<ContinuityTest>) => newContinuityTest({ ...p, buildingId: bid });
  const newGround = (p: Partial<GroundingTest> = {}) => newGroundingTest({ ...p, buildingId: bid });
  const downs = Math.max(2, Math.round((building ? building.downConductorsCount : v.downConductorsCount) || 0));

  const cd = useDraft<ContinuityTest>();
  const gd = useDraft<GroundingTest>();
  const busy = !!cd.draft || !!gd.draft;

  // ------------------------------------------------------------ continuidade
  const saveCont = () => {
    const d = cd.draft;
    if (!d) return;
    if (!d.from.trim() || !d.to.trim()) { void notify('Informe a origem e o destino da medição.'); return; }
    onChange({ continuity: cd.isNew ? [...v.continuity, d] : v.continuity.map(t => (t.id === d.id ? d : t)) });
    cd.clear();
  };
  const cancelCont = async () => {
    const d = cd.draft;
    if (d && !cd.isNew && JSON.stringify(d) !== JSON.stringify(v.continuity.find(t => t.id === d.id)) && !(await confirmAsk('Descartar as alterações desta medição?'))) return;
    cd.clear();
  };
  const deleteCont = async (t: ContinuityTest) => {
    if (!(await confirmAsk(`Excluir a medição ${t.from || '?'} → ${t.to || '?'}? As fotos e observações dela também serão excluídas.`))) return;
    onChange({ continuity: v.continuity.filter(x => x.id !== t.id) });
  };
  const duplicateCont = (t: ContinuityTest) =>
    cd.start({ ...t, id: newId('cont'), resistanceOhm: null, photos: [], obs: '' }, true);

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

  // ------------------------------------------------------------ aterramento
  const saveGround = () => {
    const d = gd.draft;
    if (!d) return;
    if (!d.point.trim()) { void notify('Informe o ponto da medição.'); return; }
    onChange({ grounding: gd.isNew ? [...v.grounding, d] : v.grounding.map(t => (t.id === d.id ? d : t)) });
    gd.clear();
  };
  const cancelGround = async () => {
    const d = gd.draft;
    if (d && !gd.isNew && JSON.stringify(d) !== JSON.stringify(v.grounding.find(t => t.id === d.id)) && !(await confirmAsk('Descartar as alterações desta medição?'))) return;
    gd.clear();
  };
  const deleteGround = async (t: GroundingTest) => {
    if (!(await confirmAsk(`Excluir a medição de aterramento "${t.point || '?'}"? As fotos e observações dela também serão excluídas.`))) return;
    onChange({ grounding: v.grounding.filter(x => x.id !== t.id) });
  };
  const duplicateGround = (t: GroundingTest) =>
    gd.start({ ...t, id: newId('ater'), r52: null, r62: null, r72: null, resistanceOhm: null, photos: [], obs: '' }, true);

  const addDownConductorPoints = () => {
    const existing = new Set(groundings.map(g => g.point));
    const add = Array.from({ length: downs }, (_, i) => `Descida D${i + 1}`).filter(p => !existing.has(p)).map(point => newGround({ point }));
    onChange({ grounding: [...v.grounding, ...add] });
  };

  // linhas exibidas: salvas (com o rascunho no lugar) + nova em edição no fim
  const contRows = [...tests.map(t => (cd.draft?.id === t.id ? cd.draft : t)), ...(cd.draft && cd.isNew ? [cd.draft] : [])];
  const groundRows = [...groundings.map(t => (gd.draft?.id === t.id ? gd.draft : t)), ...(gd.draft && gd.isNew ? [gd.draft] : [])];

  return (
    <div className="space-y-4">
      {/* ---------------------------------------------------------- continuidade */}
      <section className={cardCls}>
        <div className="px-4 py-3 border-b border-slate-200">
          <h3 className="font-bold text-sm text-slate-900 flex flex-wrap items-center gap-2">
            {screenGroup && (() => { const st = GROUP_STYLE[screenGroup]; return (
              <span className={`inline-flex items-center gap-1 rounded-md px-2 py-0.5 text-[11px] font-black ${st.chip}`}><st.Icon className="w-3.5 h-3.5" /> {CONTINUITY_GROUP_INFO[screenGroup].code}</span>
            ); })()}
            Ensaio de continuidade elétrica{screenGroup ? ` — ${CONTINUITY_GROUP_INFO[screenGroup].label}` : ''}
            <span className="font-normal text-slate-500">· NBR 5419-3:2026, Anexo F</span>
          </h3>
          {screenGroup && (
            <p className="text-[11px] text-slate-600 mt-1">
              As medições desta tela pertencem ao subsistema <b>{CONTINUITY_GROUP_INFO[screenGroup].label}</b>
              {building?.name ? <> da edificação <b>{building.name}</b></> : null} e são numeradas {CONTINUITY_GROUP_INFO[screenGroup].code}-C01, -C02…
              O código aparece nas fotos, no relatório do subsistema e no laudo.
            </p>
          )}
          <ul className="text-[11px] text-slate-500 mt-1 list-disc pl-4 space-y-0.5">
            <li>Critério de aceitação: valor medido <b>≤ 200 mΩ</b> (0,2 Ω — F.4.4) em todas as medições.</li>
            <li>Leituras em <b>miliohms (mΩ)</b>, como no visor do micro-ohmímetro. Use o ícone de câmera ao lado do valor medido para ler o visor.</li>
            <li>Instrumento (F.1.3): 4 fios, 1 A a 5 A, frequência ≠ 60 Hz ou CC. Multímetro e terrômetro-alicate não são admitidos.</li>
          </ul>
        </div>
        <div className="p-3 space-y-3">
          {contRows.map(t => {
            const editing = cd.draft?.id === t.id;
            return (
              <ContinuityRow
                key={t.id}
                t={t}
                code={continuityCode(v.continuity, t)}
                buildingName={building?.name || undefined}
                instrumentSerial={v.instruments.find(i => i.use === 'continuidade')?.serialNumber}
                readOnly={readOnly || !editing}
                editing={editing}
                set={cd.update}
                actions={(
                  <TestActions
                    locked={readOnly} editing={editing} busy={busy} isNew={editing && cd.isNew}
                    onDelete={() => deleteCont(t)} onDuplicate={() => duplicateCont(t)} onEdit={() => cd.start(t)}
                    onCancel={cancelCont} onSave={saveCont}
                  />
                )}
              />
            );
          })}
          {contRows.length === 0 && <p className="text-center text-xs text-slate-400 py-3">Nenhuma medição lançada.</p>}
        </div>
        {!readOnly && !busy && (
          <div className="px-4 py-3 border-t border-slate-200 flex flex-wrap gap-2">
            <button type="button" className={btnSecondary} onClick={() => cd.start(newCont({ kind: kinds?.[0] ?? 'F4' }), true)}><Plus className="w-3.5 h-3.5" /> Adicionar medição{screenGroup ? ` — ${CONTINUITY_GROUP_INFO[screenGroup].label}` : ''}</button>
            {allow('F1') && screenGroup !== 'descida' && <button type="button" className={btnSecondary} onClick={() => addPerDown('F1')}><Plus className="w-3.5 h-3.5" /> Pilares topo × base cruzado ({downs})</button>}
            {allow('F2') && kinds && screenGroup !== 'descida' && <button type="button" className={btnSecondary} onClick={() => addPerDown('F2')}><Plus className="w-3.5 h-3.5" /> Pilares topo × base ({downs})</button>}
            {allow('F5') && screenGroup !== 'descida' && <button type="button" className={btnSecondary} onClick={() => addPerDown('F5')}><Plus className="w-3.5 h-3.5" /> Uma por descida ({downs})</button>}
            {allow('F3') && kinds && screenGroup !== 'aterramento' && <button type="button" className={btnSecondary} onClick={() => addPerDown('F3')}><Plus className="w-3.5 h-3.5" /> Fundação, pontos adjacentes ({downs})</button>}
            {allow('F6') && screenGroup !== 'aterramento' && <button type="button" className={btnSecondary} onClick={() => addPerDown('F6')}><Plus className="w-3.5 h-3.5" /> Trechos do anel ({downs})</button>}
            {allow('EQ') && <button type="button" className={btnSecondary} onClick={() => cd.start(newCont({ kind: 'EQ', from: 'BEP', to: 'Eletrodo de aterramento' }), true)}><Plus className="w-3.5 h-3.5" /> BEP ↔ aterramento</button>}
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
            Queda de potencial (ABNT NBR 15749): informe a distância E → C até o eletrodo auxiliar de corrente; escolha o passo das leituras (±10 %: 52 / 62 / 72 % ou ±5 %: 57 / 62 / 67 %) e o app calcula as posições do eletrodo de potencial. Com as três leituras (S1, S e S2), o gráfico mostra a curva e o patamar: há patamar quando as três ficam dentro de ±5 % da leitura a 62 % (S), que é a resistência adotada.
          </p>
        </div>
        {/* um cartão por ponto: campos em grade que quebra no celular (nada fica escondido na lateral) */}
        <div className="p-3 space-y-3">
          {groundRows.map(t => {
            const editing = gd.draft?.id === t.id;
            const ro = readOnly || !editing;
            const set = gd.update;
            const fop = t.method === 'queda_potencial';
            const plateau = fop ? fallOfPotentialPlateau(t) : null;
            const pcts = fopPcts(fopStep(t));
            const pd = fop ? probeDistances(t.currentProbeDistanceM, fopStep(t)) : null;
            const at = (i: 0 | 1 | 2) => (fop ? (
              <span className="block mt-1 px-1 text-[11px] text-slate-600" title="Posição do eletrodo de potencial a partir do eletrodo ensaiado">
                <b>{pcts[i]} %</b>{pd && <span className="font-bold text-teal-700"> · a {fmtMeters(pd.m[i])}</span>}
              </span>
            ) : null);
            const status = groundingStatus(t);
            return (
              <div key={t.id} className={`border border-l-[6px] rounded-xl p-3 space-y-3 ${editing ? 'border-blue-400 ring-2 ring-blue-100' : 'border-slate-200'} ${RESULT_ACCENT[status]}`}>
                <TestResultBanner
                  status={status}
                  detail={groundingValue(t) !== null
                    ? <>Resistência adotada <b>{fmtOhm(groundingValue(t), 2)}</b> — registro informativo (NBR 5419-3:2026, 7.1.4), não gera não conformidade.{aboveDesign(t) && <b> Acima do valor de projeto.</b>}</>
                    : <>Aguardando a leitura (medição opcional).</>}
                  extra={editing ? <span className="text-[11px] font-bold text-blue-700 text-right">Editando — Salvar grava,<br />Cancelar descarta.</span> : undefined}
                />

                {/* identificação: ponto, método e passo */}
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                  <Field label="Ponto">
                    <input className={inputCls} disabled={ro} value={t.point} onChange={e => set({ point: e.target.value })} placeholder="Ex.: caixa de inspeção D1" />
                  </Field>
                  <Field label="Método">
                    <select className={inputCls} disabled={ro} value={t.method} onChange={e => set({ method: e.target.value as GroundingMethod })}>
                      <option value="queda_potencial">Queda de potencial</option>
                      <option value="alicate">Alicate terrômetro</option>
                      <option value="outro">Outro</option>
                    </select>
                  </Field>
                  {fop && (
                    <Field label="Passo das leituras">
                      <select className={inputCls} disabled={ro} value={fopStep(t)} onChange={e => set({ probeStepPct: Number(e.target.value) as 5 | 10 })}>
                        <option value={10}>±10 % — 52 / 62 / 72 %</option>
                        <option value={5}>±5 % — 57 / 62 / 67 %</option>
                      </select>
                    </Field>
                  )}
                </div>

                {/* queda de potencial: distância e as três leituras */}
                {fop && (
                  <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                    <div>
                      <Field label="Dist. E → C (m)">
                        <DecimalField className={inputCls} disabled={ro} value={t.currentProbeDistanceM} onChange={n => set({ currentProbeDistanceM: n })} />
                      </Field>
                      <span className="block mt-1 px-1 text-[11px] text-slate-600">eletrodo de corrente</span>
                    </div>
                    <div>
                      <div className="flex items-end gap-1.5">
                        <Field label="S1 (Ω)" className="flex-1 min-w-0"><DecimalField className={inputCls} disabled={ro} value={t.r52} onChange={n => set({ r52: n })} /></Field>
                        {!ro && (
                          <ReadingCaptureButton
                            caption={`Resistência de aterramento — ${t.point || 'ponto'} — leitura S1`}
                            onUse={(value, photo) => set({ r52: value, ...(photo ? { photos: [...(t.photos || []), photo] } : {}) })}
                          />
                        )}
                      </div>
                      {at(0)}
                    </div>
                    <div>
                      <div className="flex items-end gap-1.5">
                        <Field label="S — 62 % (Ω)" className="flex-1 min-w-0"><DecimalField className={inputCls} disabled={ro} value={t.r62} onChange={n => set({ r62: n })} /></Field>
                        {!ro && (
                          <ReadingCaptureButton
                            caption={`Resistência de aterramento — ${t.point || 'ponto'} — leitura S (62 %)`}
                            onUse={(value, photo) => set({ r62: value, ...(photo ? { photos: [...(t.photos || []), photo] } : {}) })}
                          />
                        )}
                      </div>
                      {at(1)}
                    </div>
                    <div>
                      <div className="flex items-end gap-1.5">
                        <Field label="S2 (Ω)" className="flex-1 min-w-0"><DecimalField className={inputCls} disabled={ro} value={t.r72} onChange={n => set({ r72: n })} /></Field>
                        {!ro && (
                          <ReadingCaptureButton
                            caption={`Resistência de aterramento — ${t.point || 'ponto'} — leitura S2`}
                            onUse={(value, photo) => set({ r72: value, ...(photo ? { photos: [...(t.photos || []), photo] } : {}) })}
                          />
                        )}
                      </div>
                      {at(2)}
                    </div>
                  </div>
                )}

                {/* resultado: adotado, projeto e situação */}
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 items-start">
                  <Field label="Adotado (Ω)">
                    <DecimalField className={inputCls} disabled={ro} value={t.resistanceOhm} onChange={n => set({ resistanceOhm: n })}
                      placeholder={fop && typeof t.r62 === 'number' ? String(t.r62).replace('.', ',') : ''} />
                  </Field>
                  <Field label="Projeto (Ω)">
                    <DecimalField className={inputCls} disabled={ro} value={t.designOhm ?? t.limitOhm} onChange={n => set({ designOhm: n, limitOhm: undefined })} />
                  </Field>
                  <div className="col-span-2 sm:col-span-2 pt-2">
                    <TestBadge status={status} />
                    {plateau && (
                      <p className={`text-[11px] mt-1 ${plateau.ok ? 'text-emerald-700' : 'text-amber-700'}`}>
                        {plateau.ok ? 'Patamar OK' : 'Fora do patamar'} ({(plateau.maxDeviation * 100).toFixed(1)} %)
                      </p>
                    )}
                    {groundingValue(t) !== null && <p className="text-[11px] text-slate-500">{fmtOhm(groundingValue(t), 2)}</p>}
                    {aboveDesign(t) && <p className="text-[11px] text-amber-700">Acima do valor de projeto</p>}
                  </div>
                </div>

                {fop && <PlateauChart t={t} />}
                <TestPhotos photos={t.photos} readOnly={ro} onChange={photos => set({ photos })} defaultCaption={`Resistência de aterramento — ${t.point || 'ponto'}`} />
                <TestObs value={t.obs} readOnly={ro} onChange={obs => set({ obs })} />
                <TestActions
                  locked={readOnly} editing={editing} busy={busy} isNew={editing && gd.isNew}
                  onDelete={() => deleteGround(t)} onDuplicate={() => duplicateGround(t)} onEdit={() => gd.start(t)}
                  onCancel={cancelGround} onSave={saveGround}
                />
              </div>
            );
          })}
          {groundRows.length === 0 && <p className="px-1 py-3 text-center text-xs text-slate-400">Nenhuma medição registrada (opcional).</p>}
        </div>
        {!readOnly && !busy && (
          <div className="px-4 py-3 border-t border-slate-200 flex flex-wrap gap-2">
            <button type="button" className={btnSecondary} onClick={() => gd.start(newGround(), true)}><Plus className="w-3.5 h-3.5" /> Adicionar ponto</button>
            {screenGroup !== 'aterramento' && <button type="button" className={btnSecondary} onClick={addDownConductorPoints}><Plus className="w-3.5 h-3.5" /> Um ponto por descida ({downs})</button>}
          </div>
        )}
      </section>
      </>}
    </div>
  );
};
