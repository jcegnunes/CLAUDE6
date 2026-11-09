/**
 * Ícone de câmera ao lado do campo "Medido": fotografa o visor do instrumento,
 * lê o valor por OCR (no aparelho) e, após confirmação, preenche o campo.
 * A foto pode ser anexada ao registro fotográfico do ensaio.
 */
import React, { useRef, useState } from 'react';
import { Camera, CheckCircle2, FolderOpen, RefreshCw, ScanLine, Smartphone, X } from 'lucide-react';
import { LiveCameraModal } from '../../../components/LiveCameraModal';
import { compressImage } from '../../../utils/imageCompressor';
import { parseMeasuredValue, readMeasuredValue, type OcrReading } from '../ocr';
import { currentUserName } from '../repository';
import { fmtMilliOhm, fmtOhm, fromMilli, newId, toMilli } from '../rules';
import type { SpdaPhoto } from '../types';
import { DecimalField, btnPrimary, btnSecondary, inputCls } from './ui';

type Props = {
  /** Legenda da foto anexada (ex.: "F.4 — Captação → BEP") */
  caption: string;
  /** Chamado ao confirmar: valor em Ω e, se marcado, a foto para anexar */
  onUse: (valueOhm: number, photo?: SpdaPhoto) => void;
  disabled?: boolean;
  /** Unidade de leitura/edição: continuidade em mΩ, aterramento em Ω */
  unit?: 'mΩ' | 'Ω';
};

export const ReadingCaptureButton: React.FC<Props> = ({ caption, onUse, disabled, unit = 'Ω' }) => {
  const [open, setOpen] = useState(false);
  return (
    <>
      <button
        type="button"
        disabled={disabled}
        onClick={() => setOpen(true)}
        className="shrink-0 p-1.5 rounded-lg border border-slate-300 bg-white text-teal-700 hover:bg-teal-50 disabled:opacity-40"
        title="Fotografar o visor do instrumento e preencher o valor medido"
        aria-label="Ler valor medido pela câmera"
      >
        <Camera className="w-3.5 h-3.5" />
      </button>
      {open && <ReadingCaptureModal caption={caption} unit={unit} onClose={() => setOpen(false)} onUse={(v, p) => { onUse(v, p); setOpen(false); }} />}
    </>
  );
};

const ReadingCaptureModal: React.FC<{ caption: string; unit: 'mΩ' | 'Ω'; onClose: () => void; onUse: (valueOhm: number, photo?: SpdaPhoto) => void }> = ({ caption, unit, onClose, onUse }) => {
  const milli = unit === 'mΩ';
  const fmt = (v: number | null | undefined, d = 6) => (milli ? fmtMilliOhm(v, d) : fmtOhm(v, d));
  const nativeRef = useRef<HTMLInputElement>(null);
  const galleryRef = useRef<HTMLInputElement>(null);
  const [live, setLive] = useState(false);
  const [photo, setPhoto] = useState<string | null>(null);
  const [reading, setReading] = useState<OcrReading | null>(null);
  const [value, setValue] = useState<number | null>(null);
  const [status, setStatus] = useState<'idle' | 'reading' | 'done' | 'error'>('idle');
  const [error, setError] = useState('');
  const [attach, setAttach] = useState(true);
  // unidade do visor quando o OCR não reconheceu o símbolo (mΩ x Ω)
  const [assumed, setAssumed] = useState<'mΩ' | 'Ω'>(unit);
  const asMilli = reading ? parseMeasuredValue(reading.text, 1e-3).valueOhm : null;
  const asOhm = reading ? parseMeasuredValue(reading.text, 1).valueOhm : null;
  const unitAmbiguous = asMilli !== null && asOhm !== null && asMilli !== asOhm;
  const chooseUnit = (u: 'mΩ' | 'Ω') => { setAssumed(u); setValue(u === 'mΩ' ? asMilli : asOhm); };

  const run = async (dataUrl: string) => {
    setPhoto(dataUrl);
    setReading(null);
    setValue(null);
    setError('');
    setStatus('reading');
    try {
      const r = await readMeasuredValue(dataUrl, milli ? 1e-3 : 1);
      setReading(r);
      setValue(r.valueOhm);
      setAssumed(unit);
      setStatus('done');
    } catch (err) {
      setStatus('error');
      setError(err instanceof Error ? err.message : String(err));
    }
  };

  const fromFile = async (files: FileList | null) => {
    const f = files?.[0];
    if (nativeRef.current) nativeRef.current.value = '';
    if (galleryRef.current) galleryRef.current.value = '';
    if (!f) return;
    const url = await compressImage(f, 1600, 1200, 0.85);
    if (url) run(url);
  };

  const confirm = () => {
    if (value === null) return;
    const ph: SpdaPhoto | undefined = attach && photo
      ? { id: newId('foto'), url: photo, caption: `${caption} — leitura do instrumento ${fmt(value, 3)}`, timestamp: new Date().toISOString(), userName: currentUserName() }
      : undefined;
    onUse(value, ph);
  };

  return (
    <div className="fixed inset-0 z-[85] bg-slate-900/60 flex items-end sm:items-center justify-center sm:p-4" onClick={onClose}>
      <div className="bg-white w-full sm:max-w-lg max-h-[94vh] flex flex-col rounded-t-2xl sm:rounded-2xl shadow-2xl" onClick={e => e.stopPropagation()} role="dialog" aria-label="Ler valor medido pela câmera">
        <div className="flex items-center justify-between px-4 py-3 border-b border-slate-200">
          <h3 className="font-bold text-sm text-slate-900 flex items-center gap-2"><ScanLine className="w-4 h-4 text-teal-700" /> Ler valor do instrumento</h3>
          <button type="button" onClick={onClose} className="p-1.5 rounded-lg hover:bg-slate-100 text-slate-500" aria-label="Fechar"><X className="w-4 h-4" /></button>
        </div>

        <div className="p-4 overflow-y-auto space-y-3">
          <p className="text-[11px] text-slate-500">
            Fotografe o <b>visor</b> do micro-ohmímetro / terrômetro, de frente, sem reflexo e com o número ocupando boa parte da foto.
            A leitura é feita no próprio aparelho; na primeira vez o reconhecimento é baixado da internet.
          </p>

          <div className="grid grid-cols-3 gap-2">
            <button type="button" className="inline-flex flex-col items-center gap-1 p-2.5 rounded-xl text-[11px] font-bold text-white bg-gradient-to-r from-orange-600 to-amber-600" onClick={() => setLive(true)}>
              <Camera className="w-4 h-4" /> Câmera em tempo real
            </button>
            <button type="button" className="inline-flex flex-col items-center gap-1 p-2.5 rounded-xl text-[11px] font-bold text-white bg-slate-800" onClick={() => nativeRef.current?.click()}>
              <Smartphone className="w-4 h-4 text-amber-400" /> Câmera nativa
            </button>
            <button type="button" className="inline-flex flex-col items-center gap-1 p-2.5 rounded-xl text-[11px] font-bold text-white bg-gradient-to-r from-blue-600 to-indigo-600" onClick={() => galleryRef.current?.click()}>
              <FolderOpen className="w-4 h-4" /> Galeria / arquivos
            </button>
          </div>
          <input ref={nativeRef} type="file" accept="image/*" capture="environment" className="hidden" onChange={e => fromFile(e.target.files)} />
          <input ref={galleryRef} type="file" accept="image/*" className="hidden" onChange={e => fromFile(e.target.files)} />

          {photo && <img src={photo} alt="Visor do instrumento" className="w-full max-h-56 object-contain rounded-lg border border-slate-200 bg-slate-50" />}

          {status === 'reading' && (
            <p className="text-xs text-teal-700 flex items-center gap-2"><RefreshCw className="w-4 h-4 animate-spin" /> Lendo o valor do visor…</p>
          )}
          {status === 'error' && (
            <p className="text-xs text-red-700">Não foi possível ler a foto ({error}). Verifique a internet na primeira leitura ou digite o valor.</p>
          )}

          {(status === 'done' || status === 'error') && (
            <div className="space-y-2">
              {reading && (
                <p className="text-[11px] text-slate-500">
                  Texto reconhecido: <span className="font-mono text-slate-700">{reading.text || '—'}</span>
                  {reading.text && <span> · confiança {reading.confidence} %</span>}
                </p>
              )}
              {unitAmbiguous && (
                <div className="flex flex-wrap items-center gap-2 text-[11px] bg-amber-50 border border-amber-200 rounded-lg px-2 py-1.5">
                  <span className="text-amber-800 font-bold">Unidade do visor não reconhecida — o visor mostra:</span>
                  {(['mΩ', 'Ω'] as const).map(u => (
                    <button key={u} type="button" onClick={() => chooseUnit(u)}
                      className={`px-2.5 py-0.5 rounded-full border font-bold ${assumed === u ? 'border-teal-600 bg-teal-600 text-white' : 'border-slate-300 bg-white text-slate-700'}`}>
                      {u}
                    </button>
                  ))}
                </div>
              )}
              {reading && reading.candidates.length > 1 && (
                <div className="flex flex-wrap gap-1">
                  <span className="text-[11px] text-slate-500">Outros valores:</span>
                  {reading.candidates.map((c, i) => (
                    <button key={i} type="button" className={`px-2 py-0.5 rounded-full border text-[11px] font-mono ${c === value ? 'border-teal-600 bg-teal-50 text-teal-800' : 'border-slate-300 text-slate-600'}`} onClick={() => setValue(c)}>
                      {fmt(c)}
                    </button>
                  ))}
                </div>
              )}
              <label className="block">
                <span className="block font-bold text-slate-700 mb-1 text-xs">Valor medido ({unit}) — confira e corrija se preciso</span>
                <DecimalField
                  className={`${inputCls} !text-base !font-bold font-mono`}
                  value={milli ? toMilli(value) : value}
                  onChange={n => setValue(milli ? fromMilli(n) : n)}
                  ariaLabel="Valor medido lido"
                />
                {milli && <span className="block text-[11px] text-slate-500 mt-1">Número sem unidade no visor é lido como mΩ (micro-ohmímetro). Se o visor mostrar Ω, a unidade é respeitada.</span>}
                {reading && reading.valueOhm === null && <span className="block text-[11px] text-amber-700 mt-1">Nenhum número reconhecido — digite o valor.</span>}
                {reading?.text && /m\s*[Ωω]|mohm|k\s*[Ωω]|[µu]\s*[Ωω]/i.test(reading.text) && <span className="block text-[11px] text-slate-500 mt-1">Unidade do visor convertida para {unit}.</span>}
              </label>
              {photo && (
                <label className="flex items-center gap-2 text-xs text-slate-700">
                  <input type="checkbox" checked={attach} onChange={e => setAttach(e.target.checked)} />
                  Anexar esta foto ao registro fotográfico do ensaio
                </label>
              )}
            </div>
          )}
        </div>

        <div className="px-4 py-3 border-t border-slate-200 flex justify-end gap-2">
          <button type="button" className={btnSecondary} onClick={onClose}>Cancelar</button>
          <button type="button" className={btnPrimary} disabled={value === null || status === 'reading'} onClick={confirm}>
            <CheckCircle2 className="w-3.5 h-3.5" /> Usar {value !== null ? fmt(value) : 'valor'}
          </button>
        </div>
      </div>

      <LiveCameraModal
        isOpen={live}
        onClose={() => setLive(false)}
        defaultCategory="medicao"
        defaultCaption={caption}
        userName={currentUserName()}
        onCapturePhoto={p => { setLive(false); run(p.url); }}
      />
    </div>
  );
};
