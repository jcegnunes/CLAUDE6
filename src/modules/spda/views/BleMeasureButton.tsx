/**
 * Botão "Medir pelo Bluetooth" ao lado do valor medido da continuidade (só no APP JVM SPDA):
 * dá a partida no miliohmímetro INBRAT INMD 1 Pro pareado no cadastro de instrumentos,
 * mostra a leitura para conferir e, ao confirmar, grava o valor (e a corrente) no ensaio.
 */
import React, { useEffect, useRef, useState } from 'react';
import { AlertTriangle, Bluetooth, CheckCircle2, Loader2, RefreshCw, X } from 'lucide-react';
import type { LabInstrument } from '../../../types';
import { bleAvailable, cancelBle, instrumentHasInmd, measureInmd, type InmdReading, type MeasureStage } from '../../../services/bluetooth/inbratInmd';
import { host } from '../host.platform';
import type { InstrumentReading } from '../types';
import { btnPrimary, btnSecondary, inputCls } from './ui';

const STAGE_TEXT: Record<MeasureStage, string> = {
  connecting: 'Conectando ao instrumento…',
  connected: 'Conectado. Enviando o comando…',
  measuring: 'Medindo… (cerca de 6 segundos)'
};

function pairedInstruments(): LabInstrument[] {
  try { return host.instruments().filter(i => !i.deletedAt && i.active !== false && instrumentHasInmd(i)); } catch { return []; }
}

const fmt = (n: number, d: number) => n.toLocaleString('pt-BR', { minimumFractionDigits: d, maximumFractionDigits: d });

export const BleMeasureButton: React.FC<{
  /** nome do ensaio gravado no instrumento (ex.: código do ensaio) */
  testName: string;
  /** nº de série do instrumento de continuidade da inspeção (escolhe o pareado com o mesmo nº) */
  preferredSerial?: string;
  onUse: (valueOhm: number, currentA: number, reading: InstrumentReading) => void;
}> = ({ testName, preferredSerial, onUse }) => {
  const [open, setOpen] = useState(false);
  const [instId, setInstId] = useState('');
  const [stage, setStage] = useState<MeasureStage | 'idle' | 'done' | 'error'>('idle');
  const [result, setResult] = useState<(InmdReading & { serial?: string }) | null>(null);
  const [error, setError] = useState('');
  const alive = useRef(true);
  useEffect(() => () => { alive.current = false; }, []);

  if (!bleAvailable()) return null;
  const list = pairedInstruments();
  if (list.length === 0) return null;

  const chosen = list.find(i => i.id === instId)
    || list.find(i => preferredSerial && i.serialNumber?.trim() === preferredSerial.trim())
    || list[0];

  const start = async (inst: LabInstrument) => {
    setResult(null);
    setError('');
    setStage('connecting');
    try {
      const r = await measureInmd(inst.bluetooth!.address, testName, s => { if (alive.current) setStage(s); });
      if (!alive.current) return;
      setResult(r);
      setStage('done');
    } catch (e) {
      if (!alive.current) return;
      setError(e instanceof Error ? e.message : String(e));
      setStage('error');
    }
  };

  const openModal = () => { setOpen(true); void start(chosen); };
  const close = () => { if (stage !== 'done' && stage !== 'error' && stage !== 'idle') cancelBle(); setOpen(false); setStage('idle'); };

  const use = () => {
    if (!result || result.invalid) return;
    const serial = result.serial || chosen.bluetooth?.serial || chosen.serialNumber;
    onUse(result.resistanceMilliOhm / 1000, Math.round(result.currentA * 100) / 100, {
      source: 'bluetooth',
      instrument: `${[chosen.manufacturer, chosen.model].filter(Boolean).join(' ') || 'INBRAT INMD 1 Pro'}${serial ? ` — nº ${serial}` : ''}`,
      serial,
      deviceTestNo: result.deviceTestNo,
      resistanceMilliOhm: Math.round(result.resistanceMilliOhm * 1000) / 1000,
      currentA: Math.round(result.currentA * 1000) / 1000,
      at: new Date().toISOString()
    });
    setOpen(false);
    setStage('idle');
  };

  const busy = stage === 'connecting' || stage === 'connected' || stage === 'measuring';

  return (
    <>
      <button type="button" onClick={openModal} title="Medir pelo Bluetooth (INBRAT INMD 1 Pro)" aria-label="Medir pelo Bluetooth"
        className="shrink-0 w-10 h-10 rounded-lg border border-blue-300 bg-blue-50 text-blue-700 flex items-center justify-center hover:bg-blue-100">
        <Bluetooth className="w-5 h-5" />
      </button>

      {open && (
        <div className="fixed inset-0 z-[95] bg-slate-900/60 flex items-end sm:items-center justify-center p-3" role="dialog" aria-label="Medir pelo Bluetooth">
          <div className="w-full max-w-sm bg-white rounded-2xl shadow-2xl text-slate-900 text-sm">
            <div className="flex items-center justify-between px-4 py-3 border-b border-slate-200">
              <h3 className="font-bold inline-flex items-center gap-2"><Bluetooth className="w-4 h-4 text-blue-600" /> Medir pelo Bluetooth</h3>
              <button type="button" onClick={close} className="p-1.5 rounded-lg hover:bg-slate-100 text-slate-500" aria-label="Fechar"><X className="w-4 h-4" /></button>
            </div>
            <div className="p-4 space-y-3">
              {list.length > 1 ? (
                <select className={inputCls} value={chosen.id} disabled={busy} aria-label="Instrumento"
                  onChange={e => { setInstId(e.target.value); setStage('idle'); setResult(null); }}>
                  {list.map(i => <option key={i.id} value={i.id}>{i.model} — nº {i.serialNumber} ({i.bluetooth?.deviceName})</option>)}
                </select>
              ) : (
                <p className="text-xs text-slate-500">{chosen.manufacturer} {chosen.model} — nº {chosen.serialNumber} · {chosen.bluetooth?.deviceName}</p>
              )}

              {busy && (
                <div className="flex items-center gap-3 rounded-xl bg-blue-50 border border-blue-200 px-3 py-4 text-blue-900">
                  <Loader2 className="w-6 h-6 animate-spin shrink-0" />
                  <span className="font-semibold">{STAGE_TEXT[stage as MeasureStage]}</span>
                </div>
              )}

              {stage === 'done' && result && !result.invalid && (
                <div className="rounded-xl bg-emerald-50 border border-emerald-300 px-3 py-3 text-center">
                  <p className="text-[11px] font-bold text-emerald-800 uppercase">Valor medido</p>
                  <p className="text-3xl font-bold text-emerald-900">{fmt(result.resistanceMilliOhm, 3)} mΩ</p>
                  <p className="text-xs text-emerald-800 mt-1">Corrente de ensaio {fmt(result.currentA, 2)} A · ensaio nº {result.deviceTestNo} no instrumento</p>
                </div>
              )}

              {stage === 'done' && result?.invalid && (
                <div className="rounded-xl bg-amber-50 border border-amber-300 px-3 py-3 text-amber-900 flex gap-2">
                  <AlertTriangle className="w-5 h-5 shrink-0" /><span>{result.invalidReason}</span>
                </div>
              )}

              {stage === 'error' && (
                <div className="rounded-xl bg-red-50 border border-red-300 px-3 py-3 text-red-900 flex gap-2">
                  <AlertTriangle className="w-5 h-5 shrink-0" /><span>{error}</span>
                </div>
              )}

              <div className="grid grid-cols-2 gap-2">
                <button type="button" className={btnSecondary} disabled={busy} onClick={() => void start(chosen)}>
                  <RefreshCw className="w-4 h-4" /> {stage === 'idle' ? 'Medir' : 'Medir de novo'}
                </button>
                <button type="button" className={btnPrimary} disabled={busy || !result || result.invalid} onClick={use}>
                  <CheckCircle2 className="w-4 h-4" /> Usar valor
                </button>
              </div>
              <p className="text-[10px] text-slate-400">Confira o valor no visor do instrumento antes de usar. O valor e a corrente vão para o ensaio.</p>
            </div>
          </div>
        </div>
      )}
    </>
  );
};
