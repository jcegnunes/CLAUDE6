/**
 * Ícone de câmera ao lado do campo "Medido": fotografa o visor do instrumento,
 * lê o valor por OCR (no aparelho) e, após confirmação, preenche o campo.
 * A foto pode ser anexada ao registro fotográfico do ensaio.
 */
import React, { useRef, useState } from 'react';
import { Camera, CheckCircle2, Crop, FolderOpen, RefreshCw, ScanLine, Smartphone, X } from 'lucide-react';
import { LiveDisplayReader, type LiveReadingResult } from './LiveDisplayReader';
import { compressImage } from '../../../utils/imageCompressor';
import { parseMeasuredValue, readMeasuredValue, type OcrReading } from '../ocr';
import type { Box } from '../sevenSeg';
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
  // no celular abre direto a leitura ao vivo (câmera com moldura-guia)
  const [live, setLive] = useState(() => {
    try { return !!navigator.mediaDevices?.getUserMedia && window.matchMedia('(pointer: coarse)').matches; } catch { return false; }
  });
  const [photo, setPhoto] = useState<string | null>(null);
  const [reading, setReading] = useState<OcrReading | null>(null);
  const [value, setValue] = useState<number | null>(null);
  const [status, setStatus] = useState<'idle' | 'reading' | 'done' | 'error'>('idle');
  const [error, setError] = useState('');
  const [attach, setAttach] = useState(true);
  // marcação manual do visor na foto (frações 0–1)
  const [marking, setMarking] = useState(false);
  const [sel, setSel] = useState<Box | null>(null);
  const dragFrom = useRef<{ x: number; y: number } | null>(null);
  // unidade do visor quando o OCR não reconheceu o símbolo (mΩ x Ω)
  const [assumed, setAssumed] = useState<'mΩ' | 'Ω'>(unit);
  const asMilli = reading ? parseMeasuredValue(reading.text, 1e-3).valueOhm : null;
  const asOhm = reading ? parseMeasuredValue(reading.text, 1).valueOhm : null;
  const unitAmbiguous = asMilli !== null && asOhm !== null && asMilli !== asOhm;
  const chooseUnit = (u: 'mΩ' | 'Ω') => { setAssumed(u); setValue(u === 'mΩ' ? asMilli : asOhm); };

  const run = async (dataUrl: string, region?: Box) => {
    setPhoto(dataUrl);
    setMarking(false);
    if (!region) setSel(null);
    setReading(null);
    setValue(null);
    setError('');
    setStatus('reading');
    try {
      const r = await readMeasuredValue(dataUrl, milli ? 1e-3 : 1, region);
      setReading(r);
      setValue(r.valueOhm);
      setAssumed(unit);
      setStatus('done');
      // não achou o visor sozinho: já deixa o usuário marcar a região dos números
      if (r.valueOhm === null) { setSel(null); setMarking(true); }
    } catch (err) {
      setMarking(true);
      setStatus('error');
      setError(err instanceof Error ? err.message : String(err));
    }
  };

  /** Resultado da leitura ao vivo: valor já confirmado por consenso, ou foto manual (lê a área da moldura). */
  const fromLive = (r: LiveReadingResult) => {
    setLive(false);
    if (!r.text) { run(r.photo, r.guide); return; }
    const parsed = parseMeasuredValue(r.text, milli ? 1e-3 : 1);
    setPhoto(r.photo);
    setMarking(false);
    setSel(null);
    setError('');
    setReading({ text: r.text, confidence: r.confidence ?? 0, ...parsed, method: '7seg', box: r.box, imageSize: r.imageSize });
    setValue(parsed.valueOhm);
    setAssumed(unit);
    setStatus('done');
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
    <div className="fixed inset-0 z-[85] bg-slate-900/60 flex items-end sm:items-center justify-center sm:p-4" onClick={e => { if (e.target === e.currentTarget) onClose(); }}>
      <div className="bg-white w-full sm:max-w-lg max-h-[94vh] flex flex-col rounded-t-2xl sm:rounded-2xl shadow-2xl" onClick={e => e.stopPropagation()} role="dialog" aria-label="Ler valor medido pela câmera">
        <div className="flex items-center justify-between px-4 py-3 border-b border-slate-200">
          <h3 className="font-bold text-sm text-slate-900 flex items-center gap-2"><ScanLine className="w-4 h-4 text-teal-700" /> Ler valor do instrumento</h3>
          <button type="button" onClick={onClose} className="p-1.5 rounded-lg hover:bg-slate-100 text-slate-500" aria-label="Fechar"><X className="w-4 h-4" /></button>
        </div>

        <div className="p-4 overflow-y-auto space-y-3">
          <p className="text-[11px] text-slate-500">
            Use a <b>Leitura ao vivo</b>: encaixe o visor na moldura e segure firme — o valor é aceito sozinho quando 3 leituras seguidas concordam. Ou fotografe o visor de frente, sem reflexo.
            Visores de 7 segmentos (LCD/LED) são lidos no próprio aparelho, sem internet. Se o valor não sair certo,
            use <b>Marcar o visor</b> e arraste um retângulo sobre os números.
          </p>

          <div className="grid grid-cols-3 gap-2">
            <button type="button" className="inline-flex flex-col items-center gap-1 p-2.5 rounded-xl text-[11px] font-bold text-white bg-gradient-to-r from-orange-600 to-amber-600" onClick={() => setLive(true)}>
              <Camera className="w-4 h-4" /> Leitura ao vivo
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

          {photo && (
            <div className="space-y-1.5">
              <div
                className={`relative mx-auto w-fit select-none ${marking ? 'cursor-crosshair touch-none' : ''}`}
                onPointerDown={e => {
                  if (!marking) return;
                  const r = e.currentTarget.getBoundingClientRect();
                  const p = { x: (e.clientX - r.left) / r.width, y: (e.clientY - r.top) / r.height };
                  dragFrom.current = p;
                  setSel({ ...p, w: 0, h: 0 });
                  e.currentTarget.setPointerCapture(e.pointerId);
                }}
                onPointerMove={e => {
                  if (!marking || !dragFrom.current) return;
                  const r = e.currentTarget.getBoundingClientRect();
                  const cx = Math.min(1, Math.max(0, (e.clientX - r.left) / r.width)), cy = Math.min(1, Math.max(0, (e.clientY - r.top) / r.height));
                  const a = dragFrom.current;
                  setSel({ x: Math.min(a.x, cx), y: Math.min(a.y, cy), w: Math.abs(cx - a.x), h: Math.abs(cy - a.y) });
                }}
                onPointerUp={() => {
                  if (!marking || !dragFrom.current) return;
                  dragFrom.current = null;
                  if (sel && sel.w > 0.03 && sel.h > 0.02) run(photo, sel);
                }}
              >
                <img src={photo} alt="Visor do instrumento" draggable={false} className="block max-w-full w-auto max-h-64 rounded-lg border border-slate-200 bg-slate-50" />
                {(() => {
                  const b = marking || sel ? sel : reading?.box && reading.imageSize
                    ? { x: reading.box.x / reading.imageSize.w, y: reading.box.y / reading.imageSize.h, w: reading.box.w / reading.imageSize.w, h: reading.box.h / reading.imageSize.h }
                    : null;
                  return b && b.w > 0 ? (
                    <div className="absolute border-2 border-teal-500 bg-teal-400/10 rounded pointer-events-none"
                      style={{ left: `${b.x * 100}%`, top: `${b.y * 100}%`, width: `${b.w * 100}%`, height: `${b.h * 100}%` }} />
                  ) : null;
                })()}
              </div>
              <div className="flex items-center justify-center gap-2">
                <button type="button" disabled={status === 'reading'}
                  className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-full border text-[11px] font-bold ${marking ? 'border-teal-600 bg-teal-600 text-white' : 'border-slate-300 bg-white text-slate-700'}`}
                  onClick={() => { setMarking(m => !m); setSel(null); }}>
                  <Crop className="w-3.5 h-3.5" /> {marking ? 'Arraste sobre os números do visor…' : 'Marcar o visor'}
                </button>
                {reading?.method === '7seg' && !marking && <span className="text-[10px] text-slate-500">Retângulo verde = visor lido</span>}
              </div>
            </div>
          )}

          {status === 'reading' && (
            <p className="text-xs text-teal-700 flex items-center gap-2"><RefreshCw className="w-4 h-4 animate-spin" /> Lendo o valor do visor…</p>
          )}
          {status === 'error' && (
            <p className="text-xs text-red-700">Não foi possível ler a foto ({error}). Marque o visor na foto ou digite o valor.</p>
          )}

          {(status === 'done' || status === 'error') && (
            <div className="space-y-2">
              {reading && (
                <p className="text-[11px] text-slate-500">
                  {reading.method === '7seg' ? 'Visor (7 segmentos)' : 'Texto reconhecido'}: <span className="font-mono text-slate-700">{reading.text || '—'}</span>
                  {reading.text && <span> · confiança {reading.confidence} %</span>}
                </p>
              )}
              {unitAmbiguous && (
                <div className="flex flex-wrap items-center gap-2 text-[11px] bg-amber-50 border border-amber-200 rounded-lg px-2 py-1.5">
                  <span className="text-amber-800 font-bold">{reading?.method === '7seg' ? 'Confirme a unidade do visor (escala do instrumento):' : 'Unidade do visor não reconhecida — o visor mostra:'}</span>
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
                {milli && <span className="block text-[11px] text-slate-500 mt-1">Micro/miliohmímetro (escalas 200 / 2000 mΩ): o número do visor já está em mΩ — ex.: visor 21.4 na escala 200 mΩ = 21,4 mΩ. Se o visor mostrar Ω, a unidade é respeitada.</span>}
                {reading && reading.valueOhm === null && <span className="block text-[11px] text-amber-700 mt-1">Nenhum número reconhecido — use “Marcar o visor” ou digite o valor.</span>}
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

      {/* os toques na câmera não podem chegar ao fundo desta janela (fechava a leitura antes da foto) */}
      {live && (
        <div onClick={e => e.stopPropagation()}>
          <LiveDisplayReader
            onResult={fromLive}
            onClose={() => setLive(false)}
            onNativeCamera={() => { setLive(false); nativeRef.current?.click(); }}
          />
        </div>
      )}
    </div>
  );
};
