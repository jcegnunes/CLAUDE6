/**
 * Editor do croqui: setas, linhas, círculos, retângulos e textos como elementos
 * independentes — selecionar, mover (arrastar ou setas do teclado), redimensionar
 * pelas alças, mudar cor/espessura, e no texto: conteúdo, tamanho, cor, negrito e contorno.
 * Os elementos ficam guardados (photo.layers) e podem ser editados de novo depois.
 */
import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  ArrowUpRight, Bold, Circle, Copy, MousePointer2, Minus, Redo2, Save, Square, Trash2, Type, Undo2, X, RefreshCw,
  ArrowUp, ArrowDown, ArrowLeft, ArrowRight
} from 'lucide-react';
import { newId } from '../rules';
import { defaultsFor, fontOf, isTiny, moveElement, renderSketch, textLines, arrowHeadPoints } from '../sketchShapes';
import type { SketchElement, SketchElementKind } from '../types';
import { btnPrimary, btnSecondary } from './ui';
import { notify } from './dialogs';

type Tool = 'select' | SketchElementKind;

const TOOLS: Array<{ id: Tool; label: string; Icon: React.ElementType }> = [
  { id: 'select', label: 'Selecionar / mover', Icon: MousePointer2 },
  { id: 'arrow', label: 'Seta', Icon: ArrowUpRight },
  { id: 'line', label: 'Linha', Icon: Minus },
  { id: 'circle', label: 'Círculo', Icon: Circle },
  { id: 'rect', label: 'Retângulo', Icon: Square },
  { id: 'text', label: 'Texto', Icon: Type }
];
const COLORS = ['#dc2626', '#f59e0b', '#16a34a', '#2563eb', '#7c3aed', '#000000', '#ffffff'];

let measureCtx: CanvasRenderingContext2D | null = null;
function textBox(el: SketchElement): { w: number; h: number } {
  const size = el.fontSize || 24;
  if (!measureCtx) measureCtx = document.createElement('canvas').getContext('2d');
  let w = size * 2;
  if (measureCtx) {
    measureCtx.font = fontOf(el);
    w = Math.max(...textLines(el).map(l => measureCtx!.measureText(l || ' ').width));
  }
  return { w, h: textLines(el).length * size * 1.2 };
}

type Drag =
  | { mode: 'draw'; id: string }
  | { mode: 'move'; id: string; last: { x: number; y: number } }
  | { mode: 'handle'; id: string; end: 1 | 2 };

export const SketchEditorModal: React.FC<{
  baseUrl: string;
  initial?: SketchElement[];
  title?: string;
  onClose: () => void;
  onSave: (result: { url: string; layers: SketchElement[] }) => void;
}> = ({ baseUrl, initial = [], title = 'Editar croqui', onClose, onSave }) => {
  const svgRef = useRef<SVGSVGElement>(null);
  const textRef = useRef<HTMLTextAreaElement>(null);
  const [size, setSize] = useState<{ w: number; h: number } | null>(null);
  const [els, setEls] = useState<SketchElement[]>(initial);
  const [undo, setUndo] = useState<SketchElement[][]>([]);
  const [redo, setRedo] = useState<SketchElement[][]>([]);
  const [tool, setTool] = useState<Tool>('arrow');
  const [selId, setSelId] = useState<string | null>(null);
  const [drag, setDrag] = useState<Drag | null>(null);
  const [color, setColor] = useState(COLORS[0]);
  const [saving, setSaving] = useState(false);
  const [focusText, setFocusText] = useState(false);
  useEffect(() => {
    if (focusText && textRef.current) { textRef.current.focus(); textRef.current.select(); setFocusText(false); }
  }, [focusText, selId]);

  useEffect(() => {
    const img = new Image();
    img.onload = () => setSize({ w: img.naturalWidth, h: img.naturalHeight });
    img.onerror = () => { void notify('Não foi possível abrir a imagem.'); onClose(); };
    img.src = baseUrl;
  }, [baseUrl, onClose]);

  const def = useMemo(() => (size ? defaultsFor(size.w, size.h) : { width: 4, fontSize: 32 }), [size]);
  const sel = els.find(e => e.id === selId) || null;

  const snapshot = useCallback(() => { setUndo(u => [...u.slice(-49), els]); setRedo([]); }, [els]);
  const patchEl = (id: string, p: Partial<SketchElement>) => setEls(list => list.map(e => (e.id === id ? { ...e, ...p } : e)));
  /** Alteração de propriedade com desfazer. */
  const changeSel = (p: Partial<SketchElement>) => { if (!sel) return; snapshot(); patchEl(sel.id, p); };

  const toImg = (e: React.PointerEvent | PointerEvent) => {
    const svg = svgRef.current;
    if (!svg) return { x: 0, y: 0 };
    const pt = svg.createSVGPoint();
    pt.x = e.clientX; pt.y = e.clientY;
    const m = svg.getScreenCTM();
    const r = m ? pt.matrixTransform(m.inverse()) : pt;
    return { x: r.x, y: r.y };
  };

  // ---------------------------------------------------------------- ponteiro
  const onBackgroundDown = (e: React.PointerEvent) => {
    if (e.button !== 0) return;
    const p = toImg(e);
    if (tool === 'select') { setSelId(null); return; }
    snapshot();
    const el: SketchElement = {
      id: newId('el'), kind: tool, x1: p.x, y1: p.y, x2: p.x, y2: p.y, color, width: def.width,
      ...(tool === 'text' ? { text: 'Texto', fontSize: def.fontSize, bold: true, halo: true } : {})
    };
    setEls(list => [...list, el]);
    setSelId(el.id);
    if (tool === 'text') { setFocusText(true); return; }
    (e.target as Element).setPointerCapture?.(e.pointerId);
    setDrag({ mode: 'draw', id: el.id });
  };

  const onElementDown = (e: React.PointerEvent, el: SketchElement) => {
    if (e.button !== 0) return;
    e.stopPropagation();
    snapshot();
    setSelId(el.id);
    (e.target as Element).setPointerCapture?.(e.pointerId);
    setDrag({ mode: 'move', id: el.id, last: toImg(e) });
  };

  const onHandleDown = (e: React.PointerEvent, el: SketchElement, end: 1 | 2) => {
    e.stopPropagation();
    snapshot();
    (e.target as Element).setPointerCapture?.(e.pointerId);
    setDrag({ mode: 'handle', id: el.id, end });
  };

  const onMove = (e: React.PointerEvent) => {
    if (!drag) return;
    const p = toImg(e);
    if (drag.mode === 'draw') patchEl(drag.id, { x2: p.x, y2: p.y });
    else if (drag.mode === 'handle') patchEl(drag.id, drag.end === 1 ? { x1: p.x, y1: p.y } : { x2: p.x, y2: p.y });
    else {
      const dx = p.x - drag.last.x, dy = p.y - drag.last.y;
      setEls(list => list.map(x => (x.id === drag.id ? moveElement(x, dx, dy) : x)));
      setDrag({ ...drag, last: p });
    }
  };

  const onUp = () => {
    if (drag?.mode === 'draw') {
      const el = els.find(x => x.id === drag.id);
      if (el && isTiny(el, def.width * 3)) {
        setEls(list => list.filter(x => x.id !== el.id));
        setUndo(u => u.slice(0, -1));
        setSelId(null);
      }
    }
    setDrag(null);
  };

  // ---------------------------------------------------------------- ações
  const removeSel = () => { if (!sel) return; snapshot(); setEls(list => list.filter(x => x.id !== sel.id)); setSelId(null); };
  const duplicateSel = () => {
    if (!sel) return;
    snapshot();
    const off = def.fontSize;
    const c = { ...moveElement(sel, off, off), id: newId('el') };
    setEls(list => [...list, c]);
    setSelId(c.id);
  };
  const nudge = (dx: number, dy: number) => { if (!sel) return; snapshot(); setEls(list => list.map(x => (x.id === sel.id ? moveElement(x, dx, dy) : x))); };
  const doUndo = () => { if (!undo.length) return; setRedo(r => [...r, els]); setEls(undo[undo.length - 1]); setUndo(u => u.slice(0, -1)); setSelId(null); };
  const doRedo = () => { if (!redo.length) return; setUndo(u => [...u, els]); setEls(redo[redo.length - 1]); setRedo(r => r.slice(0, -1)); setSelId(null); };

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const typing = (e.target as HTMLElement)?.closest('input, textarea, select');
      if (typing) return;
      const step = (size ? Math.max(size.w, size.h) : 1000) * (e.shiftKey ? 0.02 : 0.004);
      if (e.key === 'Delete' || e.key === 'Backspace') { if (sel) { e.preventDefault(); removeSel(); } }
      else if (e.key === 'ArrowLeft') { e.preventDefault(); nudge(-step, 0); }
      else if (e.key === 'ArrowRight') { e.preventDefault(); nudge(step, 0); }
      else if (e.key === 'ArrowUp') { e.preventDefault(); nudge(0, -step); }
      else if (e.key === 'ArrowDown') { e.preventDefault(); nudge(0, step); }
      else if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'z') { e.preventDefault(); doUndo(); }
      else if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'y') { e.preventDefault(); doRedo(); }
      else if (e.key === 'Escape') setSelId(null);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  });

  const save = async () => {
    setSaving(true);
    try {
      const layers = els.filter(e => !isTiny(e, 1));
      onSave({ url: await renderSketch(baseUrl, layers), layers });
    } catch (err) {
      void notify(err instanceof Error ? err.message : 'Não foi possível salvar o croqui.');
    } finally {
      setSaving(false);
    }
  };

  // ---------------------------------------------------------------- desenho SVG
  const hs = size ? Math.max(size.w, size.h) * 0.012 : 10; // raio das alças
  const renderEl = (el: SketchElement) => {
    const common = { stroke: el.color, strokeWidth: el.width, strokeLinecap: 'round' as const, fill: 'none' };
    const hit = { stroke: 'transparent', strokeWidth: Math.max(el.width, hs * 1.6), fill: 'none', style: { cursor: 'move' }, onPointerDown: (e: React.PointerEvent) => onElementDown(e, el) };
    if (el.kind === 'arrow' || el.kind === 'line') {
      const [a, b] = arrowHeadPoints({ x: el.x1, y: el.y1 }, { x: el.x2, y: el.y2 }, el.width);
      return (
        <g key={el.id}>
          <line x1={el.x1} y1={el.y1} x2={el.x2} y2={el.y2} {...common} />
          {el.kind === 'arrow' && <polygon points={`${el.x2},${el.y2} ${a.x},${a.y} ${b.x},${b.y}`} fill={el.color} />}
          <line x1={el.x1} y1={el.y1} x2={el.x2} y2={el.y2} {...hit} />
        </g>
      );
    }
    if (el.kind === 'circle') {
      const cx = (el.x1 + el.x2) / 2, cy = (el.y1 + el.y2) / 2, rx = Math.abs(el.x2 - el.x1) / 2, ry = Math.abs(el.y2 - el.y1) / 2;
      return (
        <g key={el.id}>
          <ellipse cx={cx} cy={cy} rx={rx} ry={ry} {...common} />
          <ellipse cx={cx} cy={cy} rx={rx} ry={ry} {...hit} />
        </g>
      );
    }
    if (el.kind === 'rect') {
      const x = Math.min(el.x1, el.x2), y = Math.min(el.y1, el.y2), w = Math.abs(el.x2 - el.x1), h = Math.abs(el.y2 - el.y1);
      return (
        <g key={el.id}>
          <rect x={x} y={y} width={w} height={h} {...common} />
          <rect x={x} y={y} width={w} height={h} {...hit} />
        </g>
      );
    }
    const size2 = el.fontSize || 24;
    const box = textBox(el);
    return (
      <g key={el.id} style={{ cursor: 'move' }} onPointerDown={e => onElementDown(e, el)}>
        <rect x={el.x1} y={el.y1} width={box.w} height={box.h} fill="transparent" />
        <text x={el.x1} y={el.y1} fill={el.color} fontSize={size2} fontWeight={el.bold ? 'bold' : 'normal'} fontFamily="Arial, Helvetica, sans-serif"
          stroke={el.halo ? '#ffffff' : 'none'} strokeWidth={el.halo ? Math.max(2, size2 / 6) : 0} paintOrder="stroke" strokeLinejoin="round" style={{ userSelect: 'none' }}>
          {textLines(el).map((l, i) => <tspan key={i} x={el.x1} y={el.y1 + i * size2 * 1.2} dominantBaseline="text-before-edge">{l || ' '}</tspan>)}
        </text>
      </g>
    );
  };

  const selectionUi = (el: SketchElement) => {
    if (el.kind === 'text') {
      const b = textBox(el);
      return <rect x={el.x1 - hs / 2} y={el.y1 - hs / 2} width={b.w + hs} height={b.h + hs} fill="none" stroke="#2563eb" strokeWidth={hs / 5} strokeDasharray={`${hs} ${hs / 2}`} pointerEvents="none" />;
    }
    const handle = (x: number, y: number, end: 1 | 2) => (
      <circle cx={x} cy={y} r={hs} fill="#ffffff" stroke="#2563eb" strokeWidth={hs / 4} style={{ cursor: 'crosshair' }} onPointerDown={e => onHandleDown(e, el, end)} />
    );
    return <g>{handle(el.x1, el.y1, 1)}{handle(el.x2, el.y2, 2)}</g>;
  };

  const btnTool = (on: boolean) => `inline-flex items-center gap-1 px-2.5 py-1.5 rounded-lg text-[11px] font-bold border transition-colors ${on ? 'bg-blue-600 text-white border-blue-600' : 'bg-white text-slate-700 border-slate-300 hover:bg-slate-50'}`;
  const swatch = (c: string, on: boolean, pick: (c: string) => void) => (
    <button key={c} type="button" onClick={() => pick(c)} aria-label={`Cor ${c}`} aria-pressed={on}
      className={`w-6 h-6 rounded-full border-2 ${on ? 'border-blue-600 ring-2 ring-blue-200' : 'border-slate-300'}`} style={{ background: c }} />
  );
  const curColor = sel ? sel.color : color;
  const pickColor = (c: string) => { setColor(c); if (sel) changeSel({ color: c }); };

  return (
    <div className="fixed inset-0 z-[100] bg-slate-800 flex flex-col" role="dialog" aria-label={title}>
      {/* barra superior */}
      <div className="bg-white px-3 py-2 flex flex-wrap items-center gap-2 border-b border-slate-200">
        <p className="font-bold text-sm text-slate-900 mr-2 truncate max-w-[40%]">{title}</p>
        <div className="flex flex-wrap gap-1" role="toolbar" aria-label="Ferramentas">
          {TOOLS.map(t => (
            <button key={t.id} type="button" className={btnTool(tool === t.id)} onClick={() => setTool(t.id)} aria-pressed={tool === t.id} title={t.label}>
              <t.Icon className="w-3.5 h-3.5" /> <span className="hidden sm:inline">{t.label}</span>
            </button>
          ))}
        </div>
        <span className="flex-1" />
        <button type="button" className={btnSecondary} onClick={doUndo} disabled={!undo.length} title="Desfazer (Ctrl+Z)"><Undo2 className="w-3.5 h-3.5" /></button>
        <button type="button" className={btnSecondary} onClick={doRedo} disabled={!redo.length} title="Refazer (Ctrl+Y)"><Redo2 className="w-3.5 h-3.5" /></button>
        <button type="button" className={btnSecondary} onClick={onClose}><X className="w-3.5 h-3.5" /> Cancelar</button>
        <button type="button" className={btnPrimary} onClick={save} disabled={saving || !size}>
          {saving ? <RefreshCw className="w-3.5 h-3.5 animate-spin" /> : <Save className="w-3.5 h-3.5" />} Aplicar
        </button>
      </div>

      <div className="flex-1 min-h-0 flex flex-col lg:flex-row">
        {/* área de desenho */}
        <div className="flex-1 min-h-0 p-3 flex items-center justify-center">
          {size ? (
            <svg
              ref={svgRef}
              viewBox={`0 0 ${size.w} ${size.h}`}
              preserveAspectRatio="xMidYMid meet"
              className="w-full h-full max-h-full touch-none select-none"
              style={{ cursor: tool === 'select' ? 'default' : 'crosshair' }}
              onPointerMove={onMove}
              onPointerUp={onUp}
              onPointerCancel={onUp}
              aria-label="Área do croqui"
            >
              <image href={baseUrl} x={0} y={0} width={size.w} height={size.h} onPointerDown={onBackgroundDown} />
              {els.map(renderEl)}
              {sel && selectionUi(sel)}
            </svg>
          ) : (
            <p className="text-white text-sm flex items-center gap-2"><RefreshCw className="w-4 h-4 animate-spin" /> Abrindo a imagem…</p>
          )}
        </div>

        {/* propriedades */}
        <aside className="bg-white lg:w-72 border-t lg:border-t-0 lg:border-l border-slate-200 p-3 space-y-3 overflow-y-auto h-[38vh] shrink-0 lg:h-auto text-xs">
          {!sel ? (
            <>
              <p className="font-bold text-slate-800">Nova marcação</p>
              <p className="text-slate-500">
                {tool === 'select' ? 'Clique num elemento para selecionar; arraste para mover.'
                  : tool === 'text' ? 'Clique na imagem onde o texto deve ficar.'
                    : 'Clique e arraste sobre a imagem para desenhar.'}
                {' '}Elementos já inseridos podem ser arrastados com qualquer ferramenta.
              </p>
              <div>
                <span className="block font-bold text-slate-600 mb-1">Cor</span>
                <div className="flex flex-wrap gap-1.5 items-center">{COLORS.map(c => swatch(c, color === c, setColor))}
                  <input type="color" value={color} onChange={e => setColor(e.target.value)} className="w-7 h-7 p-0 border-0 bg-transparent" aria-label="Outra cor" />
                </div>
              </div>
            </>
          ) : (
            <>
              <p className="font-bold text-slate-800">{TOOLS.find(t => t.id === sel.kind)?.label} selecionado</p>

              {sel.kind === 'text' && (
                <>
                  <label className="block">
                    <span className="block font-bold text-slate-600 mb-1">Texto</span>
                    <textarea ref={textRef} rows={2} className="w-full border border-slate-300 rounded-lg p-1.5 text-xs" value={sel.text || ''}
                      onFocus={snapshot} onChange={e => patchEl(sel.id, { text: e.target.value })} aria-label="Conteúdo do texto" />
                  </label>
                  <label className="block">
                    <span className="flex justify-between font-bold text-slate-600 mb-1">Tamanho da fonte <span className="font-mono">{Math.round(sel.fontSize || 24)} px</span></span>
                    <input type="range" min={Math.round(def.fontSize * 0.3)} max={Math.round(def.fontSize * 4)} value={Math.round(sel.fontSize || 24)}
                      onPointerDown={snapshot} onChange={e => patchEl(sel.id, { fontSize: Number(e.target.value) })} className="w-full" aria-label="Tamanho da fonte" />
                  </label>
                  <div className="flex gap-1.5">
                    <button type="button" className={btnTool(!!sel.bold)} onClick={() => changeSel({ bold: !sel.bold })} aria-pressed={!!sel.bold}><Bold className="w-3.5 h-3.5" /> Negrito</button>
                    <button type="button" className={btnTool(!!sel.halo)} onClick={() => changeSel({ halo: !sel.halo })} aria-pressed={!!sel.halo}>Contorno branco</button>
                  </div>
                </>
              )}

              <div>
                <span className="block font-bold text-slate-600 mb-1">{sel.kind === 'text' ? 'Cor da fonte' : 'Cor'}</span>
                <div className="flex flex-wrap gap-1.5 items-center">{COLORS.map(c => swatch(c, curColor === c, pickColor))}
                  <input type="color" value={curColor} onChange={e => pickColor(e.target.value)} className="w-7 h-7 p-0 border-0 bg-transparent" aria-label="Outra cor" />
                </div>
              </div>

              {sel.kind !== 'text' && (
                <label className="block">
                  <span className="flex justify-between font-bold text-slate-600 mb-1">Espessura <span className="font-mono">{Math.round(sel.width)} px</span></span>
                  <input type="range" min={1} max={Math.round(def.width * 5)} value={Math.round(sel.width)}
                    onPointerDown={snapshot} onChange={e => patchEl(sel.id, { width: Number(e.target.value) })} className="w-full" aria-label="Espessura" />
                </label>
              )}

              <div>
                <span className="block font-bold text-slate-600 mb-1">Posição</span>
                <div className="grid grid-cols-3 gap-1 w-28">
                  <span />
                  <button type="button" className={btnTool(false)} onClick={() => nudge(0, -def.fontSize / 4)} aria-label="Mover para cima"><ArrowUp className="w-3.5 h-3.5" /></button>
                  <span />
                  <button type="button" className={btnTool(false)} onClick={() => nudge(-def.fontSize / 4, 0)} aria-label="Mover para a esquerda"><ArrowLeft className="w-3.5 h-3.5" /></button>
                  <span />
                  <button type="button" className={btnTool(false)} onClick={() => nudge(def.fontSize / 4, 0)} aria-label="Mover para a direita"><ArrowRight className="w-3.5 h-3.5" /></button>
                  <span />
                  <button type="button" className={btnTool(false)} onClick={() => nudge(0, def.fontSize / 4)} aria-label="Mover para baixo"><ArrowDown className="w-3.5 h-3.5" /></button>
                  <span />
                </div>
                <p className="text-[10px] text-slate-400 mt-1">Ou arraste o elemento; setas do teclado também movem (Shift = passo maior).{sel.kind !== 'text' && ' As alças azuis mudam o tamanho/direção.'}</p>
              </div>

              <div className="flex gap-1.5 pt-2 border-t border-slate-200">
                <button type="button" className={btnTool(false)} onClick={duplicateSel}><Copy className="w-3.5 h-3.5" /> Duplicar</button>
                <button type="button" className="inline-flex items-center gap-1 px-2.5 py-1.5 rounded-lg text-[11px] font-bold border border-red-300 text-red-700 bg-white hover:bg-red-50" onClick={removeSel}><Trash2 className="w-3.5 h-3.5" /> Excluir</button>
                <button type="button" className={btnTool(false)} onClick={() => setSelId(null)}>Desmarcar</button>
              </div>
            </>
          )}
          <p className="text-[10px] text-slate-400 border-t border-slate-100 pt-2">{els.length} elemento(s). "Aplicar" atualiza a imagem; depois toque em Salvar no croqui para gravar.</p>
        </aside>
      </div>
    </div>
  );
};
