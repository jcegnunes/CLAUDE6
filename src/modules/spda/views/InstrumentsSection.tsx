/**
 * Seção "Instrumentos de medição" (ensaio de continuidade e resistência de aterramento).
 * Fica na aba Dados (editor livre) e também no passo a passo da nova inspeção, para que a
 * escolha dos instrumentos esteja sempre disponível.
 */
import React from 'react';
import { readInstruments } from '../repository';
import type { InstrumentInfo, SpdaInspection } from '../types';
import { Field, cardCls, inputCls } from './ui';

export const InstrumentsSection: React.FC<{ value: SpdaInspection; onChange: (patch: Partial<SpdaInspection>) => void; readOnly?: boolean }> = ({ value: v, onChange, readOnly }) => {
  const instruments = readInstruments();

  const setInstrument = (idx: number, patch: Partial<InstrumentInfo>) => {
    onChange({ instruments: v.instruments.map((ins, i) => (i === idx ? { ...ins, ...patch } : ins)) });
  };
  const pickInstrument = (idx: number, id: string) => {
    const li = instruments.find(x => x.id === id);
    if (!li) return;
    setInstrument(idx, {
      description: [li.type, li.manufacturer].filter(Boolean).join(' — '),
      model: li.model, serialNumber: li.serialNumber, calibrationCert: li.calibrationCertNumber,
      calibrationDate: li.calibrationDate, calibrationExpiry: li.calibrationExpiryDate
    });
  };

  return (
    <section className={`${cardCls} p-4`}>
      <h3 className="font-bold text-sm text-slate-900 mb-3">Instrumentos de medição</h3>
      <div className="space-y-4">
        {v.instruments.map((ins, idx) => (
          <div key={ins.use} className="border border-slate-200 rounded-xl p-3">
            <div className="flex flex-wrap items-center justify-between gap-2 mb-2">
              <p className="text-xs font-bold text-slate-800">{ins.use === 'continuidade' ? 'Ensaio de continuidade' : 'Resistência de aterramento'}</p>
              {!readOnly && instruments.length > 0 && (
                <select className={`${inputCls} sm:max-w-xs`} value="" onChange={e => pickInstrument(idx, e.target.value)} aria-label="Usar instrumento cadastrado">
                  <option value="">Usar instrumento cadastrado…</option>
                  {instruments.map(i => <option key={i.id} value={i.id}>{i.type} {i.model} — nº {i.serialNumber}</option>)}
                </select>
              )}
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
              <Field label="Instrumento"><input className={inputCls} disabled={readOnly} value={ins.description} onChange={e => setInstrument(idx, { description: e.target.value })} /></Field>
              <Field label="Modelo"><input className={inputCls} disabled={readOnly} value={ins.model} onChange={e => setInstrument(idx, { model: e.target.value })} /></Field>
              <Field label="Nº de série"><input className={inputCls} disabled={readOnly} value={ins.serialNumber} onChange={e => setInstrument(idx, { serialNumber: e.target.value })} /></Field>
              <Field label="Certificado de calibração"><input className={inputCls} disabled={readOnly} value={ins.calibrationCert} onChange={e => setInstrument(idx, { calibrationCert: e.target.value })} /></Field>
              <Field label="Data da calibração"><input type="date" className={inputCls} disabled={readOnly} value={ins.calibrationDate || ''} onChange={e => setInstrument(idx, { calibrationDate: e.target.value })} /></Field>
              <Field label="Validade da calibração"><input type="date" className={inputCls} disabled={readOnly} value={ins.calibrationExpiry || ''} onChange={e => setInstrument(idx, { calibrationExpiry: e.target.value })} /></Field>
            </div>
            {ins.calibrationExpiry && v.inspectionDate && ins.calibrationExpiry < v.inspectionDate && (
              <p className="text-[11px] text-red-700 mt-2">Calibração vencida na data da inspeção.</p>
            )}
          </div>
        ))}
      </div>
    </section>
  );
};
