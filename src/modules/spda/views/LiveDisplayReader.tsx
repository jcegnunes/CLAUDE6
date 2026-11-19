/**
 * Leitura AO VIVO do visor do instrumento pela câmera (offline).
 *
 * - Moldura-guia: o usuário encaixa o visor no retângulo; só essa área é analisada.
 * - Lê vários quadros por segundo com o leitor de 7 segmentos e só aceita o valor quando
 *   3 leituras seguidas concordam (consenso) — então vibra, congela a imagem e devolve a foto.
 * - Lanterna (quando o aparelho permite) e "Capturar agora" (foto manual, leitura normal).
 */
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Camera, Flashlight, FlashlightOff, Smartphone, X } from 'lucide-react';
import { readSevenSegment, type Box, type Pixels } from '../sevenSeg';
import { CONSENSUS, stableReading, streak, type LiveSample } from '../liveConsensus';

export interface LiveReadingResult {
  /** Foto do quadro inteiro (JPEG) */
  photo: string;
  /** Texto do visor, ex. "21.4" — ausente na captura manual */
  text?: string;
  confidence?: number;
  /** Região do visor na foto (px da foto) */
  box?: Box;
  /** Moldura-guia na foto (frações 0–1) — usada na captura manual */
  guide: Box;
  imageSize: { w: number; h: number };
}

type Props = {
  onResult: (r: LiveReadingResult) => void;
  onClose: () => void;
  /** Abre a câmera nativa do celular (quando a câmera ao vivo não está disponível) */
  onNativeCamera: () => void;
};

// moldura-guia (frações da área visível do vídeo)
const GUIDE = { w: 0.8, aspect: 2.4, maxH: 0.5 };
const MAX_ANALYSIS_W = 640;
const MAX_PHOTO_SIDE = 1600;

/** Retângulo do vídeo efetivamente desenhado no elemento (object-fit: contain). */
function videoRect(v: HTMLVideoElement) {
  const ew = v.clientWidth, eh = v.clientHeight, vw = v.videoWidth || 1, vh = v.videoHeight || 1;
  const s = Math.min(ew / vw, eh / vh);
  const w = vw * s, h = vh * s;
  return { x: (ew - w) / 2, y: (eh - h) / 2, w, h };
}

/** Moldura-guia em frações do quadro do vídeo. */
function guideFrac(v: HTMLVideoElement): Box {
  const r = videoRect(v);
  let gw = r.w * GUIDE.w, gh = gw / GUIDE.aspect;
  if (gh > r.h * GUIDE.maxH) { gh = r.h * GUIDE.maxH; gw = gh * GUIDE.aspect; }
  return { x: (1 - gw / r.w) / 2, y: (1 - gh / r.h) / 2, w: gw / r.w, h: gh / r.h };
}

function vibrate(ms: number) {
  try {
    const bridge = (window as unknown as { AndroidBridge?: { vibrate?: (n: number) => void } }).AndroidBridge;
    if (bridge?.vibrate) bridge.vibrate(ms);
    else navigator.vibrate?.(ms);
  } catch { /* sem vibração */ }
}

export const LiveDisplayReader: React.FC<Props> = ({ onResult, onClose, onNativeCamera }) => {
  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const historyRef = useRef<LiveSample[]>([]);
  const runningRef = useRef(true);
  const doneRef = useRef(false);
  const [error, setError] = useState('');
  const [ready, setReady] = useState(false);
  const [live, setLive] = useState<LiveSample | null>(null);
  const [count, setCount] = useState(0);
  const [torchOk, setTorchOk] = useState(false);
  const [torch, setTorch] = useState(false);
  const [frozen, setFrozen] = useState<string | null>(null);
  const [, force] = useState(0);

  const stop = useCallback(() => {
    runningRef.current = false;
    streamRef.current?.getTracks().forEach(t => { try { t.stop(); } catch { /* */ } });
    streamRef.current = null;
  }, []);

  // abre a câmera traseira
  useEffect(() => {
    let cancelled = false;
    (async () => {
      if (!navigator.mediaDevices?.getUserMedia) {
        setError('Este aparelho não oferece a câmera ao vivo. Use a câmera nativa.');
        return;
      }
      const tries: MediaStreamConstraints[] = [
        { audio: false, video: { facingMode: { ideal: 'environment' }, width: { ideal: 1920 }, height: { ideal: 1080 } } },
        { audio: false, video: { facingMode: { ideal: 'environment' } } },
        { audio: false, video: true }
      ];
      let stream: MediaStream | null = null;
      let last: unknown = null;
      for (const c of tries) {
        try { stream = await navigator.mediaDevices.getUserMedia(c); break; } catch (e) { last = e; }
      }
      if (cancelled) { stream?.getTracks().forEach(t => t.stop()); return; }
      if (!stream) {
        const name = (last as { name?: string })?.name || '';
        setError(name === 'NotAllowedError' || name === 'PermissionDeniedError'
          ? 'Permissão da câmera negada. Libere a câmera nas configurações do app (Permissões → Câmera) ou use a câmera nativa.'
          : name === 'NotReadableError' ? 'A câmera está em uso por outro app. Feche-o e tente de novo, ou use a câmera nativa.'
          : 'Não foi possível abrir a câmera ao vivo. Use a câmera nativa.');
        return;
      }
      streamRef.current = stream;
      const track = stream.getVideoTracks()[0];
      try {
        const caps = (track.getCapabilities?.() || {}) as MediaTrackCapabilities & { torch?: boolean; focusMode?: string[] };
        setTorchOk(!!caps.torch);
        if (caps.focusMode?.includes('continuous')) await track.applyConstraints({ advanced: [{ focusMode: 'continuous' } as MediaTrackConstraintSet] }).catch(() => undefined);
      } catch { /* sem recursos extras */ }
      const v = videoRef.current;
      if (v) {
        v.srcObject = stream;
        await v.play().catch(() => undefined);
        setReady(true);
      }
    })();
    return () => { cancelled = true; stop(); };
  }, [stop]);

  // redesenha a moldura quando a tela gira / muda de tamanho
  useEffect(() => {
    const on = () => force(n => n + 1);
    window.addEventListener('resize', on);
    return () => window.removeEventListener('resize', on);
  }, []);

  /** Foto do quadro atual (limitada a MAX_PHOTO_SIDE). */
  const grabPhoto = useCallback((): { url: string; w: number; h: number; scale: number } | null => {
    const v = videoRef.current;
    if (!v || !v.videoWidth) return null;
    const scale = Math.min(1, MAX_PHOTO_SIDE / Math.max(v.videoWidth, v.videoHeight));
    const c = document.createElement('canvas');
    c.width = Math.round(v.videoWidth * scale);
    c.height = Math.round(v.videoHeight * scale);
    c.getContext('2d')!.drawImage(v, 0, 0, c.width, c.height);
    return { url: c.toDataURL('image/jpeg', 0.88), w: c.width, h: c.height, scale };
  }, []);

  const finish = useCallback((sample?: LiveSample, boxInGuidePx?: Box, guideScale?: number) => {
    if (doneRef.current) return;
    const v = videoRef.current;
    const shot = grabPhoto();
    if (!v || !shot) return;
    doneRef.current = true;
    runningRef.current = false;
    const g = guideFrac(v);
    let box: Box | undefined;
    if (boxInGuidePx && guideScale) {
      // caixa do visor: px da análise → px do vídeo → px da foto
      const gx = g.x * v.videoWidth, gy = g.y * v.videoHeight;
      box = {
        x: (gx + boxInGuidePx.x / guideScale) * shot.scale,
        y: (gy + boxInGuidePx.y / guideScale) * shot.scale,
        w: (boxInGuidePx.w / guideScale) * shot.scale,
        h: (boxInGuidePx.h / guideScale) * shot.scale
      };
    }
    setFrozen(shot.url);
    if (sample) vibrate(90);
    stop();
    // pequena pausa para o usuário ver o valor congelado
    setTimeout(() => onResult({ photo: shot.url, text: sample?.text, confidence: sample?.confidence, box, guide: g, imageSize: { w: shot.w, h: shot.h } }), sample ? 600 : 0);
  }, [grabPhoto, onResult, stop]);

  // laço de leitura: analisa a área da moldura em sequência (cada leitura começa quando a anterior termina)
  useEffect(() => {
    if (!ready) return;
    runningRef.current = true;
    const canvas = document.createElement('canvas');
    const ctx = canvas.getContext('2d', { willReadFrequently: true })!;
    let timer = 0;
    const tick = () => {
      if (!runningRef.current) return;
      const v = videoRef.current;
      if (v && v.videoWidth && !document.hidden) {
        const g = guideFrac(v);
        const sx = g.x * v.videoWidth, sy = g.y * v.videoHeight, sw = g.w * v.videoWidth, sh = g.h * v.videoHeight;
        const s = Math.min(1, MAX_ANALYSIS_W / sw);
        canvas.width = Math.max(8, Math.round(sw * s));
        canvas.height = Math.max(8, Math.round(sh * s));
        ctx.drawImage(v, sx, sy, sw, sh, 0, 0, canvas.width, canvas.height);
        const img = ctx.getImageData(0, 0, canvas.width, canvas.height);
        const px: Pixels = { w: canvas.width, h: canvas.height, data: img.data };
        let r: ReturnType<typeof readSevenSegment> = null;
        try { r = readSevenSegment(px); } catch { r = null; }
        const sample: LiveSample = r ? { text: r.text, value: r.value, confidence: r.confidence } : { text: '', value: null, confidence: 0 };
        const h = historyRef.current;
        h.push(sample);
        if (h.length > 12) h.shift();
        setLive(sample.value !== null ? sample : null);
        setCount(streak(h));
        const stable = stableReading(h);
        if (stable && r) { finish(stable, r.box, s); return; }
      }
      timer = window.setTimeout(tick, 120);
    };
    timer = window.setTimeout(tick, 300);
    return () => { window.clearTimeout(timer); };
  }, [ready, finish]);

  const toggleTorch = async () => {
    const track = streamRef.current?.getVideoTracks()[0];
    if (!track) return;
    try {
      await track.applyConstraints({ advanced: [{ torch: !torch } as MediaTrackConstraintSet] });
      setTorch(t => !t);
    } catch { setTorchOk(false); }
  };

  // posição da moldura na tela
  const v = videoRef.current;
  const vr = v && v.videoWidth ? videoRect(v) : null;
  const gf = v && v.videoWidth ? guideFrac(v) : null;
  const guideStyle = vr && gf ? { left: vr.x + gf.x * vr.w, top: vr.y + gf.y * vr.h, width: gf.w * vr.w, height: gf.h * vr.h } : null;
  const ok = count >= CONSENSUS.needed;

  return (
    <div className="fixed inset-0 z-[95] bg-black flex flex-col" role="dialog" aria-label="Leitura ao vivo do visor">
      <div className="flex items-center justify-between px-4 py-3 text-white bg-black/70">
        <span className="text-sm font-bold flex items-center gap-2"><Camera className="w-4 h-4 text-amber-400" /> Leitura ao vivo do visor</span>
        <button type="button" onClick={() => { stop(); onClose(); }} className="p-1.5 rounded-lg hover:bg-white/10" aria-label="Fechar"><X className="w-5 h-5" /></button>
      </div>

      <div className="relative flex-1 min-h-0">
        <video ref={videoRef} playsInline muted autoPlay className="absolute inset-0 w-full h-full object-contain" />
        {frozen && <img src={frozen} alt="" className="absolute inset-0 w-full h-full object-contain" />}
        {guideStyle && !error && (
          <>
            {/* escurece fora da moldura */}
            <div className="absolute pointer-events-none rounded-xl" style={{ ...guideStyle, boxShadow: '0 0 0 9999px rgba(0,0,0,0.45)' }} />
            <div className={`absolute pointer-events-none rounded-xl border-[3px] transition-colors ${ok ? 'border-emerald-400' : live ? 'border-amber-400' : 'border-white/80'}`} style={guideStyle} />
            <div className="absolute left-0 right-0 text-center pointer-events-none" style={{ top: (guideStyle.top as number) + (guideStyle.height as number) + 10 }}>
              <span className="inline-block px-3 py-1 rounded-full bg-black/60 text-white text-xs">
                {ok ? 'Leitura confirmada' : live ? 'Segure firme…' : 'Encaixe o visor dentro do retângulo'}
              </span>
            </div>
          </>
        )}
        {error && (
          <div className="absolute inset-0 flex flex-col items-center justify-center gap-3 p-6 text-center">
            <p className="text-sm text-white max-w-sm">{error}</p>
            <button type="button" onClick={() => { stop(); onNativeCamera(); }} className="inline-flex items-center gap-2 px-4 py-2 rounded-xl bg-white text-slate-900 font-bold text-sm">
              <Smartphone className="w-4 h-4" /> Usar a câmera nativa
            </button>
          </div>
        )}
      </div>

      {!error && (
        <div className="bg-black/80 text-white px-4 pt-3 pb-5 space-y-3">
          <div className="flex items-center justify-center gap-3">
            <span className={`font-mono text-3xl font-bold tabular-nums ${ok ? 'text-emerald-400' : live ? 'text-amber-300' : 'text-white/40'}`}>{live ? live.text.replace('.', ',') : '— — —'}</span>
            <span className="flex gap-1" aria-label={`${Math.min(count, CONSENSUS.needed)} de ${CONSENSUS.needed} leituras iguais`}>
              {Array.from({ length: CONSENSUS.needed }, (_, i) => (
                <span key={i} className={`w-2.5 h-2.5 rounded-full ${i < count ? (ok ? 'bg-emerald-400' : 'bg-amber-400') : 'bg-white/25'}`} />
              ))}
            </span>
          </div>
          <div className="flex items-center justify-center gap-3">
            {torchOk && (
              <button type="button" onClick={toggleTorch} className={`inline-flex items-center gap-1.5 px-3 py-2 rounded-xl text-xs font-bold ${torch ? 'bg-amber-400 text-slate-900' : 'bg-white/15 text-white'}`} aria-pressed={torch}>
                {torch ? <FlashlightOff className="w-4 h-4" /> : <Flashlight className="w-4 h-4" />} Lanterna
              </button>
            )}
            <button type="button" disabled={!ready || !!frozen} onClick={() => finish()} className="inline-flex items-center gap-1.5 px-4 py-2 rounded-xl text-xs font-bold bg-white text-slate-900 disabled:opacity-40">
              <Camera className="w-4 h-4" /> Capturar agora
            </button>
          </div>
          <p className="text-[11px] text-center text-white/60">O valor é aceito sozinho quando {CONSENSUS.needed} leituras seguidas forem iguais. Sem reflexo no vidro do visor.</p>
        </div>
      )}
    </div>
  );
};
