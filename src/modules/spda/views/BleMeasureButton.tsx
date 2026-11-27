/**
 * Botão "Medir pelo Bluetooth" ao lado do valor medido da continuidade (só no APP JVM SPDA):
 * dá a partida no miliohmímetro INBRAT INMD 1 Pro, mostra a leitura para conferir e, ao
 * confirmar, grava o valor (e a corrente) no ensaio.
 *
 * O pareamento pode ser feito AQUI, durante o ensaio (buscar e escolher o aparelho), sem
 * precisar voltar ao cadastro de instrumentos. Também aceita o instrumento já pareado no
 * cadastro. O aparelho pareado na hora fica lembrado enquanto o app estiver aberto.
 */
import React, { useEffect, useRef, useState } from 'react';
import { AlertTriangle, Bluetooth, BluetoothSearching, CheckCircle2, Loader2, RefreshCw, X } from 'lucide-react';
import type { LabInstrument } from '../../../types';
import { bleAvailable, cancelBle, instrumentHasInmd, looksLikeInbrat, measureInmd, scanBleDevices, type InmdReading, type MeasureStage } from '../../../services/bluetooth/inbratInmd';
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

/** Aparelho pareado na hora (fora do cadastro). Lembrado enquanto o app estiver aberto. */
type Adhoc = { address: string; name: string };
let lastAdhoc: Adhoc | null = null;

type Found = { address: string; name: string; rssi?: number };

const PAIR = '__pair__';
const ADHOC = '__adhoc__';

const fmt = (n: number, d: number) => n.toLocaleString('pt-BR', { minimumFractionDigits: d, maximumFractionDigits: d });

export const BleMeasureButton: React.FC<{
  /** nome do ensaio gravado no instrumento (ex.: código do ensaio) */
  testName: string;
  /** nº de série do instrumento de continuidade da inspeção (escolhe o pareado com o mesmo nº) */
  preferredSerial?: string;
  onUse: (valueOhm: number, currentA: number, reading: InstrumentReading) => void;
}> = ({ testName, preferredSerial, onUse }) => {
  const [open, setOpen] = useState(false);
  const [adhoc, setAdhoc] = useState<Adhoc | null>(lastAdhoc);
  const [selected, setSelected] = useState('');
  const [mode, setMode] = useState<'measure' | 'pair'>('measure');
  // pareamento
  const [scanning, setScanning] = useState(false);
  const [found, setFound] = useState<Found[]>([]);
  const [scanError, setScanError] = useState('');
  // medição
  const [stage, setStage] = useState<MeasureStage | 'idle' | 'done' | 'error'>('idle');
  const [result, setResult] = useState<(InmdReading & { serial?: string }) | null>(null);
  const [error, setError] = useState('');
  const alive = useRef(true);
  useEffect(() => () => { alive.current = false; }, []);

  if (!bleAvailable()) return null;
  const list = pairedInstruments();

  /** Escolha inicial quando abre o modal. */
  const initialSelection = (): string => {
    if (list.length) {
      const pref = preferredSerial && list.find(i => i.serialNumber?.trim() === preferredSerial.trim());
      return (pref || list[0]).id;
    }
    if (adhoc) return ADHOC;
    return PAIR;
  };

  /** Instrumento cadastrado escolhido (se houver). */
  const chosenInst = list.find(i => i.id === selected) || null;
  /** Endereço e rótulo do alvo atual. */
  const target: { address: string; label: string; serial?: string } | null =
    chosenInst ? { address: chosenInst.bluetooth!.address, serial: chosenInst.bluetooth?.serial || chosenInst.serialNumber, label: `${[chosenInst.manufacturer, chosenInst.model].filter(Boolean).join(' ') || 'INBRAT INMD 1 Pro'}${chosenInst.serialNumber ? ` — nº ${chosenInst.serialNumber}` : ''}` }
    : selected === ADHOC && adhoc ? { address: adhoc.address, label: adhoc.name || 'INBRAT INMD 1 Pro' }
    : null;

  const resetMeasure = () => { setResult(null); setError(''); setStage('idle'); };

  const start = async (address: string) => {
    resetMeasure();
    setStage('connecting');
    try {
      const r = await measureInmd(address, testName, s => { if (alive.current) setStage(s); });
      if (!alive.current) return;
      setResult(r);
      setStage('done');
    } catch (e) {
      if (!alive.current) return;
      setError(e instanceof Error ? e.message : String(e));
      setStage('error');
    }
  };

  const startScan = async () => {
    setScanError('');
    setFound([]);
    setScanning(true);
    try {
      await scanBleDevices(d => setFound(prev => (prev.some(p => p.address === d.address) ? prev : [...prev, d])), 10);
    } catch (e) {
      setScanError(e instanceof Error ? e.message : String(e));
    } finally {
      if (alive.current) setScanning(false);
    }
  };

  const pickDevice = (d: Found) => {
    cancelBle();
    setScanning(false);
    setFound([]);
    const a: Adhoc = { address: d.address, name: d.name || 'INMD 1 Pro' };
    lastAdhoc = a;
    setAdhoc(a);
    setSelected(ADHOC);
    setMode('measure');
    void start(a.address);
  };

  const openPairing = () => { cancelBle(); resetMeasure(); setMode('pair'); setFound([]); setScanError(''); void startScan(); };

  const openModal = () => {
    const sel = initialSelection();
    setSelected(sel);
    resetMeasure();
    setFound([]);
    setScanError('');
    if (sel === PAIR) { setMode('pair'); setOpen(true); void startScan(); }
    else {
      setMode('measure');
      setOpen(true);
      const addr = sel === ADHOC ? adhoc?.address : list.find(i => i.id === sel)?.bluetooth?.address;
      if (addr) void start(addr);
    }
  };

  const busy = stage === 'connecting' || stage === 'connected' || stage === 'measuring';

  const close = () => { if (busy || scanning) cancelBle(); setOpen(false); setStage('idle'); setScanning(false); };

  const use = () => {
    if (!result || result.invalid || !target) return;
    const serial = result.serial || target.serial;
    onUse(result.resistanceMilliOhm / 1000, Math.round(result.currentA * 100) / 100, {
      source: 'bluetooth',
      instrument: `${target.label}${serial && !target.label.includes(serial) ? ` — nº ${serial}` : ''}`,
      serial,
      deviceTestNo: result.deviceTestNo,
      resistanceMilliOhm: Math.round(result.resistanceMilliOhm * 1000) / 1000,
      currentA: Math.round(result.currentA * 1000) / 1000,
      at: new Date().toISOString()
    });
    setOpen(false);
    setStage('idle');
  };

  const onSelectChange = (val: string) => {
    setSelected(val);
    resetMeasure();
    if (val === PAIR) openPairing();
    else {
      setMode('measure');
      const addr = val === ADHOC ? adhoc?.address : list.find(i => i.id === val)?.bluetooth?.address;
      if (addr) void start(addr);
    }
  };

  const sortedFound = [...found].sort((a, b) => Number(looksLikeInbrat(b.name)) - Number(looksLikeInbrat(a.name)) || (b.rssi ?? -999) - (a.rssi ?? -999));

  // opções do seletor de aparelho
  const hasChooser = list.length > 0 || !!adhoc;

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
              {/* seletor de aparelho */}
              {hasChooser && (
                <select className={inputCls} value={selected} disabled={busy || scanning} aria-label="Aparelho"
                  onChange={e => onSelectChange(e.target.value)}>
                  {list.map(i => <option key={i.id} value={i.id}>{i.model} — nº {i.serialNumber} ({i.bluetooth?.deviceName})</option>)}
                  {adhoc && <option value={ADHOC}>{adhoc.name} (pareado agora)</option>}
                  <option value={PAIR}>Parear outro aparelho…</option>
                </select>
              )}

              {/* MODO PAREAMENTO: buscar e escolher o aparelho */}
              {mode === 'pair' && (
                <div className="space-y-2">
                  <p className="text-[11px] text-slate-500">Ligue o instrumento, deixe-o perto do celular e escolha-o na lista.</p>
                  <button type="button" onClick={() => void startScan()} disabled={scanning}
                    className="w-full min-h-[44px] rounded-xl border border-blue-300 text-blue-800 font-bold inline-flex items-center justify-center gap-2 hover:bg-blue-50 disabled:opacity-70">
                    {scanning ? <><Loader2 className="w-4 h-4 animate-spin" /> Procurando aparelhos…</> : <><BluetoothSearching className="w-4 h-4" /> Procurar de novo</>}
                  </button>
                  {scanError && <p className="text-[11px] text-red-700">{scanError}</p>}
                  {sortedFound.length > 0 && (
                    <ul className="rounded-lg border border-slate-200 divide-y divide-slate-100 max-h-56 overflow-y-auto">
                      {sortedFound.map(d => (
                        <li key={d.address}>
                          <button type="button" onClick={() => pickDevice(d)} className="w-full text-left px-3 py-2.5 hover:bg-slate-50 flex items-center gap-2">
                            <Bluetooth className={`w-4 h-4 shrink-0 ${looksLikeInbrat(d.name) ? 'text-blue-600' : 'text-slate-400'}`} />
                            <span className="flex-1 min-w-0">
                              <span className="block font-bold text-slate-800 truncate">{d.name || '(sem nome)'}{looksLikeInbrat(d.name) && <span className="ml-1.5 text-[10px] font-bold text-blue-700">INBRAT</span>}</span>
                              <span className="block text-[10px] text-slate-500">{d.address}{d.rssi != null ? ` · sinal ${d.rssi} dBm` : ''}</span>
                            </span>
                            <span className="text-[11px] font-bold text-blue-700">Usar</span>
                          </button>
                        </li>
                      ))}
                    </ul>
                  )}
                  {!scanning && sortedFound.length === 0 && !scanError && (
                    <p className="text-[10px] text-slate-400">O INMD 1 Pro costuma aparecer como “IN433A”; se não vier o nome, escolha o aparelho de sinal mais forte.</p>
                  )}
                </div>
              )}

              {/* MODO MEDIÇÃO */}
              {mode === 'measure' && target && (
                <>
                  {!hasChooser && <p className="text-xs text-slate-500">{target.label}</p>}

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
                    <button type="button" className={btnSecondary} disabled={busy} onClick={() => void start(target.address)}>
                      <RefreshCw className="w-4 h-4" /> {stage === 'idle' ? 'Medir' : 'Medir de novo'}
                    </button>
                    <button type="button" className={btnPrimary} disabled={busy || !result || result.invalid} onClick={use}>
                      <CheckCircle2 className="w-4 h-4" /> Usar valor
                    </button>
                  </div>
                  <p className="text-[10px] text-slate-400">Confira o valor no visor do instrumento antes de usar. O valor e a corrente vão para o ensaio.</p>
                </>
              )}
            </div>
          </div>
        </div>
      )}
    </>
  );
};
