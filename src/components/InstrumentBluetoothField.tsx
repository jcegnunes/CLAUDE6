/**
 * Cadastro do instrumento: "Comunicação Bluetooth". Hoje: miliohmímetro INBRAT INMD 1 Pro.
 * O pareamento (busca e escolha do aparelho) só funciona no APP JVM SPDA; na plataforma web
 * mostra o aparelho pareado e permite remover.
 */
import React, { useState } from 'react';
import { Bluetooth, BluetoothSearching, Loader2, Unlink } from 'lucide-react';
import type { InstrumentBluetooth } from '../types';
import { INBRAT_INMD_LABEL, INBRAT_INMD_PROTOCOL, bleAvailable, cancelBle, looksLikeInbrat, scanBleDevices } from '../services/bluetooth/inbratInmd';

type Found = { address: string; name: string; rssi?: number };

export const InstrumentBluetoothField: React.FC<{
  value?: InstrumentBluetooth;
  onChange: (v: InstrumentBluetooth | undefined) => void;
}> = ({ value, onChange }) => {
  const [enabled, setEnabled] = useState(!!value);
  const [scanning, setScanning] = useState(false);
  const [found, setFound] = useState<Found[]>([]);
  const [error, setError] = useState('');
  const canPair = bleAvailable();

  const startScan = async () => {
    setError('');
    setFound([]);
    setScanning(true);
    try {
      await scanBleDevices(d => setFound(prev => (prev.some(p => p.address === d.address) ? prev : [...prev, d])), 10);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setScanning(false);
    }
  };

  const pick = (d: Found) => {
    cancelBle();
    setScanning(false);
    setFound([]);
    onChange({ protocol: INBRAT_INMD_PROTOCOL, deviceName: d.name, address: d.address, pairedAt: new Date().toISOString() });
  };

  const sorted = [...found].sort((a, b) => Number(looksLikeInbrat(b.name)) - Number(looksLikeInbrat(a.name)) || (b.rssi ?? -999) - (a.rssi ?? -999));

  return (
    <div className="rounded-xl border border-slate-200 p-3 space-y-2">
      <label className="block font-bold text-slate-700">Comunicação Bluetooth</label>
      <select
        value={enabled ? INBRAT_INMD_PROTOCOL : ''}
        onChange={e => { const on = !!e.target.value; setEnabled(on); if (!on) onChange(undefined); }}
        className="w-full p-2.5 border border-slate-300 rounded-xl bg-white"
        aria-label="Comunicação Bluetooth"
      >
        <option value="">Sem Bluetooth</option>
        <option value={INBRAT_INMD_PROTOCOL}>{INBRAT_INMD_LABEL}</option>
      </select>

      {enabled && (
        <div className="space-y-2">
          {value ? (
            <div className="flex flex-wrap items-center gap-2 rounded-lg bg-blue-50 border border-blue-200 px-3 py-2 text-blue-900">
              <Bluetooth className="w-4 h-4 shrink-0" />
              <span className="flex-1 min-w-0">Pareado: <b>{value.deviceName}</b> <span className="text-[10px] text-blue-700">({value.address})</span>{value.serial ? <> · nº {value.serial}</> : null}</span>
              <button type="button" onClick={() => onChange(undefined)} className="inline-flex items-center gap-1 text-red-700 font-bold text-[11px] hover:underline">
                <Unlink className="w-3.5 h-3.5" /> Remover
              </button>
            </div>
          ) : (
            <p className="text-[11px] text-slate-500">Ligue o instrumento, deixe-o perto do celular e toque em Parear.</p>
          )}

          {canPair ? (
            <button type="button" onClick={startScan} disabled={scanning}
              className="w-full min-h-[44px] rounded-xl border border-blue-300 text-blue-800 font-bold inline-flex items-center justify-center gap-2 hover:bg-blue-50 disabled:opacity-70">
              {scanning ? <><Loader2 className="w-4 h-4 animate-spin" /> Procurando aparelhos…</> : <><BluetoothSearching className="w-4 h-4" /> {value ? 'Parear outro aparelho' : 'Parear'}</>}
            </button>
          ) : (
            <p className="text-[11px] text-amber-800 bg-amber-50 border border-amber-200 rounded-lg px-2.5 py-2">
              O pareamento é feito pelo <b>APP JVM SPDA</b> no celular (Instrumentos → editar este instrumento → Parear).
            </p>
          )}

          {error && <p className="text-[11px] text-red-700">{error}</p>}

          {sorted.length > 0 && (
            <ul className="rounded-lg border border-slate-200 divide-y divide-slate-100 max-h-56 overflow-y-auto">
              {sorted.map(d => (
                <li key={d.address}>
                  <button type="button" onClick={() => pick(d)} className="w-full text-left px-3 py-2.5 hover:bg-slate-50 flex items-center gap-2">
                    <Bluetooth className={`w-4 h-4 shrink-0 ${looksLikeInbrat(d.name) ? 'text-blue-600' : 'text-slate-400'}`} />
                    <span className="flex-1 min-w-0">
                      <span className="block font-bold text-slate-800 truncate">{d.name}{looksLikeInbrat(d.name) && <span className="ml-1.5 text-[10px] font-bold text-blue-700">INBRAT</span>}</span>
                      <span className="block text-[10px] text-slate-500">{d.address}{d.rssi != null ? ` · sinal ${d.rssi} dBm` : ''}</span>
                    </span>
                    <span className="text-[11px] font-bold text-blue-700">Usar</span>
                  </button>
                </li>
              ))}
            </ul>
          )}
          {!scanning && canPair && found.length === 0 && !value && !error && (
            <p className="text-[10px] text-slate-400">O INMD 1 Pro aparece com um nome como “IN433A”.</p>
          )}
        </div>
      )}
    </div>
  );
};
