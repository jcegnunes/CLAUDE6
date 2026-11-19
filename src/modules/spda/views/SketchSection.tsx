/**
 * Projeto / croqui dos pontos de descida ou de aterramento de uma edificação.
 * Fica após os ensaios nas telas Descida e Aterramento; sai no relatório do
 * subsistema e no laudo. Edição explícita: Editar → Salvar / Cancelar.
 */
import React, { useRef, useState } from 'react';
import { ArrowDownToLine, Anchor, Zap, ClipboardPaste, FileImage, FolderOpen, PenLine, Pencil, Save, Smartphone, Trash2, X, RefreshCw, CopyPlus, CheckSquare, Square } from 'lucide-react';
import { SketchEditorModal } from './SketchEditorModal';
import { compressImage } from '../../../utils/imageCompressor';
import { currentUserName } from '../repository';
import { newId } from '../rules';
import type { BuildingSketch, SketchKind, SpdaBuilding, SpdaPhoto } from '../types';
import { alertError, btnDanger, btnPrimary, btnSecondary, cardCls, inputCls } from './ui';
import { confirmAsk, notify } from './dialogs';

export const MAX_SKETCH_IMAGES = 6;

export const SKETCH_INFO: Record<SketchKind, { title: string; points: string; placeholder: string; Icon: React.ElementType; color: string }> = {
  captacao: {
    title: 'Projeto / croqui dos pontos de captação',
    points: 'captação',
    placeholder: 'Ex.: C1 — captor Franklin na caixa d’água; malha de captação 10 × 10 m na cobertura; terminais aéreos T1 a T8 na platibanda…',
    Icon: Zap,
    color: 'bg-amber-500'
  },
  descida: {
    title: 'Projeto / croqui dos pontos de descida',
    points: 'descida',
    placeholder: 'Ex.: D1 — fachada norte, canto NE (conector de ensaio a 1,5 m); D2 — fachada norte, centro; D3 — fachada leste…',
    Icon: ArrowDownToLine,
    color: 'bg-sky-600'
  },
  aterramento: {
    title: 'Projeto / croqui dos pontos de aterramento',
    points: 'aterramento',
    placeholder: 'Ex.: A1 — caixa de inspeção junto à D1; anel enterrado a 0,5 m e 1 m da fachada; haste H3 próxima ao portão…',
    Icon: Anchor,
    color: 'bg-emerald-700'
  }
};

export const sketchOf = (b: SpdaBuilding | undefined, kind: SketchKind): BuildingSketch | undefined => {
  const s = b?.sketches?.[kind];
  return s && (s.images?.length || s.notes?.trim()) ? s : undefined;
};

export const SketchSection: React.FC<{
  kind: SketchKind;
  building: SpdaBuilding;
  readOnly: boolean;
  onSave: (sketch: BuildingSketch) => void;
}> = ({ kind, building, readOnly, onSave }) => {
  const info = SKETCH_INFO[kind];
  const saved: BuildingSketch = building.sketches?.[kind] || { images: [] };
  const [draft, setDraft] = useState<BuildingSketch | null>(null);
  const [busy, setBusy] = useState(false);
  const [markup, setMarkup] = useState<SpdaPhoto | null>(null);
  const [zoom, setZoom] = useState<SpdaPhoto | null>(null);
  const [copyOpen, setCopyOpen] = useState(false);
  const [picked, setPicked] = useState<string[]>([]);
  /** Imagens dos croquis dos outros subsistemas da mesma edificação. */
  const otherKinds = (Object.keys(SKETCH_INFO) as SketchKind[]).filter(k => k !== kind && (building.sketches?.[k]?.images?.length || 0) > 0);
  const galleryRef = useRef<HTMLInputElement>(null);
  const camRef = useRef<HTMLInputElement>(null);

  const editing = !!draft;
  const s = draft || saved;
  const images = s.images || [];
  const room = MAX_SKETCH_IMAGES - images.length;
  const caption = `${info.title.replace('Projeto / croqui', 'Croqui')} — ${building.name || 'edificação'}`;
  const update = (p: Partial<BuildingSketch>) => setDraft(d => ({ ...(d || saved), ...p }));

  const addFiles = async (files: Array<File | Blob>) => {
    const chosen = files.slice(0, Math.max(0, room));
    if (!chosen.length) { if (files.length) void notify(`Limite de ${MAX_SKETCH_IMAGES} imagens.`); return; }
    setBusy(true);
    try {
      const urls = (await Promise.all(chosen.map(f => compressImage(f instanceof File ? f : new File([f], 'croqui.png', { type: f.type || 'image/png' }), 2000, 1500, 0.82)))).filter(Boolean) as string[];
      const user = currentUserName();
      const now = new Date().toISOString();
      setDraft(d => {
        const base = d || saved;
        return { ...base, images: [...(base.images || []), ...urls.map(url => ({ id: newId('croqui'), url, caption, timestamp: now, userName: user }))].slice(0, MAX_SKETCH_IMAGES) };
      });
    } catch (err) {
      alertError(err, 'Não foi possível carregar a imagem');
    } finally {
      setBusy(false);
      if (galleryRef.current) galleryRef.current.value = '';
      if (camRef.current) camRef.current.value = '';
    }
  };

  const pasteImage = async () => {
    try {
      const items = await navigator.clipboard.read();
      for (const it of items) {
        const type = it.types.find(t => t.startsWith('image/'));
        if (type) { await addFiles([await it.getType(type)]); return; }
      }
      void notify('Não há imagem na área de transferência. Copie o desenho/print do projeto e tente de novo.');
    } catch {
      void notify('O navegador não liberou a área de transferência. Clique na área tracejada e use Ctrl+V.');
    }
  };

  /** Copia as imagens escolhidas (com as marcações editáveis) para este croqui. */
  const copyPicked = () => {
    const src = otherKinds.flatMap(k => (building.sketches?.[k]?.images || []).map(p => ({ k, p }))).filter(x => picked.includes(x.p.id));
    const chosen = src.slice(0, Math.max(0, room));
    if (src.length > chosen.length) void notify(`Limite de ${MAX_SKETCH_IMAGES} imagens: ${src.length - chosen.length} não foram copiadas.`);
    const now = new Date().toISOString();
    const copies: SpdaPhoto[] = chosen.map(({ k, p }) => {
      const srcDefault = `${SKETCH_INFO[k].title.replace('Projeto / croqui', 'Croqui')} — ${building.name || 'edificação'}`;
      return { ...p, id: newId('croqui'), caption: p.caption === srcDefault ? caption : p.caption, timestamp: now, layers: p.layers ? p.layers.map(l => ({ ...l })) : undefined };
    });
    update({ images: [...images, ...copies] });
    setCopyOpen(false);
    setPicked([]);
  };

  const save = () => { if (draft) onSave({ images: draft.images || [], notes: draft.notes || '' }); setDraft(null); };
  const cancel = async () => {
    const norm = (x: BuildingSketch) => JSON.stringify({ images: x.images || [], notes: x.notes || '' });
    if (draft && norm(draft) !== norm(saved) && !(await confirmAsk('Descartar as alterações do croqui?'))) return;
    setDraft(null);
  };
  const remove = async () => {
    if (!(await confirmAsk(`Excluir o ${info.title.toLowerCase()} desta edificação (imagens e identificação dos pontos)?`))) return;
    onSave({ images: [], notes: '' });
    setDraft(null);
  };
  const hasContent = images.length > 0 || !!s.notes?.trim();
  const Icon = info.Icon;

  return (
    <section className={`${cardCls} ${editing ? 'ring-2 ring-blue-200 border-blue-400' : ''}`}>
      <div className="px-4 py-3 border-b border-slate-200 flex flex-wrap items-center gap-2">
        <span className={`w-7 h-7 rounded-lg ${info.color} text-white flex items-center justify-center`}><Icon className="w-4 h-4" /></span>
        <div className="flex-1 min-w-0">
          <h3 className="font-bold text-sm text-slate-900">{info.title}<span className="font-normal text-teal-700"> · {building.name || 'Edificação'}</span></h3>
          <p className="text-[11px] text-slate-500">Insira o projeto (as built) ou um croqui com a localização dos pontos de {info.points}. Use "Marcar" para indicar os pontos sobre a imagem. Sai no relatório do subsistema e no laudo.</p>
        </div>
        {editing && <span className="text-[11px] font-bold text-blue-700">Editando — Salvar grava, Cancelar descarta.</span>}
      </div>

      <div className="p-3 space-y-3">
        <input ref={galleryRef} type="file" accept="image/*" multiple className="hidden" onChange={e => addFiles(Array.from((e.target.files || []) as ArrayLike<File>))} />
        <input ref={camRef} type="file" accept="image/*" capture="environment" className="hidden" onChange={e => addFiles(Array.from((e.target.files || []) as ArrayLike<File>))} />

        {images.length > 0 && (
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            {images.map((p, i) => (
              <figure key={p.id} className="border border-slate-200 rounded-xl bg-white p-2">
                <button type="button" className="block w-full" onClick={() => setZoom(p)} aria-label={`Ampliar ${p.caption}`}>
                  <img src={p.url} alt={p.caption} className="w-full max-h-80 object-contain rounded bg-slate-50" />
                </button>
                <div className="flex items-center gap-1 mt-1.5">
                  <span className="text-[10px] font-bold text-slate-500">Fig. {i + 1}</span>
                  <input className="flex-1 min-w-0 px-1.5 py-1 text-[11px] border border-slate-200 rounded disabled:bg-slate-50" value={p.caption} disabled={!editing}
                    onChange={e => update({ images: images.map(x => (x.id === p.id ? { ...x, caption: e.target.value } : x)) })} aria-label="Legenda do croqui" />
                  {editing && (
                    <>
                      <button type="button" className="p-1 text-slate-500 hover:text-blue-600" onClick={() => setMarkup(p)} title="Marcar os pontos na imagem" aria-label="Marcar os pontos na imagem"><PenLine className="w-3.5 h-3.5" /></button>
                      <button type="button" className="p-1 text-slate-500 hover:text-red-600" onClick={async () => { if (await confirmAsk('Remover esta imagem do croqui?')) update({ images: images.filter(x => x.id !== p.id) }); }} aria-label="Remover imagem do croqui"><Trash2 className="w-3.5 h-3.5" /></button>
                    </>
                  )}
                </div>
              </figure>
            ))}
          </div>
        )}

        {editing ? (
          room > 0 && (
            <div
              tabIndex={0}
              onPaste={e => { const f = Array.from(e.clipboardData.files as ArrayLike<File>).filter(x => x.type.startsWith('image/')); if (f.length) { e.preventDefault(); void addFiles(f); } }}
              onDragOver={e => e.preventDefault()}
              onDrop={e => { e.preventDefault(); void addFiles(Array.from(e.dataTransfer.files as ArrayLike<File>).filter(x => x.type.startsWith('image/'))); }}
              className="border-2 border-dashed border-slate-300 rounded-xl p-4 text-xs text-slate-500 bg-white focus:outline-none focus:border-teal-500 flex flex-col items-center gap-2"
              aria-label="Área para inserir o projeto ou croqui"
            >
              <FileImage className="w-5 h-5" />
              <span className="text-center">Arraste o projeto/croqui aqui, ou clique nesta área e cole (Ctrl+V) · até {MAX_SKETCH_IMAGES} imagens ({images.length}/{MAX_SKETCH_IMAGES})</span>
              <div className="flex flex-wrap justify-center gap-2">
                <button type="button" className={btnSecondary} disabled={busy} onClick={() => galleryRef.current?.click()}><FolderOpen className="w-3.5 h-3.5" /> Galeria / arquivo</button>
                <button type="button" className={btnSecondary} disabled={busy} onClick={() => camRef.current?.click()}><Smartphone className="w-3.5 h-3.5" /> Fotografar o projeto</button>
                <button type="button" className={btnSecondary} disabled={busy} onClick={pasteImage}><ClipboardPaste className="w-3.5 h-3.5" /> Colar imagem</button>
              </div>
              {busy && <span className="text-orange-700 flex items-center gap-1"><RefreshCw className="w-3 h-3 animate-spin" /> Processando a imagem…</span>}
              <span className="text-[10px] text-slate-400">Projeto em PDF/DWG: exporte ou faça um print da prancha e insira como imagem.</span>
            </div>
          )
        ) : !hasContent && (
          <div className="border-2 border-dashed border-slate-200 rounded-xl py-4 text-xs text-slate-400 flex flex-col items-center gap-1">
            <FileImage className="w-5 h-5" /> Nenhum projeto/croqui inserido — toque em Editar para inserir.
          </div>
        )}

        {(editing || s.notes?.trim()) && (
          <label className="block">
            <span className="text-[10px] font-bold text-slate-600 uppercase tracking-wide">Identificação dos pontos de {info.points}</span>
            <textarea className={`${inputCls} mt-1`} rows={3} disabled={!editing} placeholder={info.placeholder} value={s.notes || ''} onChange={e => update({ notes: e.target.value })} />
          </label>
        )}

        <div className="flex flex-wrap justify-end gap-2 pt-2 border-t border-slate-200">
          <button type="button" className={btnSecondary} disabled={readOnly || busy || otherKinds.length === 0 || room <= 0}
            title={otherKinds.length === 0 ? 'Nenhum croqui com imagem nos outros subsistemas desta edificação' : room <= 0 ? `Limite de ${MAX_SKETCH_IMAGES} imagens` : 'Copiar imagens do croqui de outro subsistema'}
            onClick={() => { if (!editing) setDraft({ images: [...(saved.images || [])], notes: saved.notes || '' }); setPicked([]); setCopyOpen(true); }}>
            <CopyPlus className="w-3.5 h-3.5" /> Copiar de outro subsistema
          </button>
          <span className="flex-1" />
          <button type="button" className={btnDanger} disabled={readOnly || editing || !hasContent} onClick={remove}><Trash2 className="w-3.5 h-3.5" /> Excluir</button>
          <button type="button" className={btnSecondary} disabled={readOnly || editing} onClick={() => setDraft({ images: [...(saved.images || [])], notes: saved.notes || '' })}><Pencil className="w-3.5 h-3.5" /> Editar</button>
          <button type="button" className={btnSecondary} disabled={readOnly || !editing} onClick={cancel}><X className="w-3.5 h-3.5" /> Cancelar</button>
          <button type="button" className={btnPrimary} disabled={readOnly || !editing || busy} onClick={save}><Save className="w-3.5 h-3.5" /> Salvar</button>
        </div>
      </div>

      {markup && (
        <SketchEditorModal
          baseUrl={markup.layers ? (markup.originalUrl || markup.url) : markup.url}
          initial={markup.layers}
          title={`Marcar pontos — ${markup.caption}`}
          onClose={() => setMarkup(null)}
          onSave={({ url, layers }) => {
            update({
              images: images.map(p => (p.id === markup.id
                ? { ...p, url, layers, originalUrl: p.layers ? (p.originalUrl || p.url) : p.url, editedAt: new Date().toISOString() }
                : p))
            });
            setMarkup(null);
          }}
        />
      )}

      {copyOpen && (
        <div className="fixed inset-0 z-[95] bg-slate-900/70 flex items-center justify-center p-4" role="dialog" aria-label="Copiar imagens de outro subsistema" onClick={() => setCopyOpen(false)}>
          <div className="bg-white rounded-2xl w-full max-w-3xl max-h-[85vh] flex flex-col" onClick={e => e.stopPropagation()}>
            <div className="px-4 py-3 border-b border-slate-200">
              <p className="font-bold text-sm text-slate-900">Copiar imagens para o {info.title.toLowerCase()}</p>
              <p className="text-[11px] text-slate-500">Escolha as imagens dos croquis dos outros subsistemas de {building.name || 'esta edificação'}. As marcações (setas, textos…) vêm junto e continuam editáveis. Espaço livre: {room} imagem(ns).</p>
            </div>
            <div className="p-4 overflow-y-auto space-y-4">
              {otherKinds.map(k => {
                const list = building.sketches?.[k]?.images || [];
                const allOn = list.every(p => picked.includes(p.id));
                return (
                  <div key={k}>
                    <div className="flex items-center gap-2 mb-2">
                      <p className="font-bold text-xs text-slate-700">{SKETCH_INFO[k].title.replace('Projeto / croqui dos pontos de', 'Croqui da')}</p>
                      <button type="button" className="text-[11px] text-blue-700 font-bold" onClick={() => setPicked(cur => allOn ? cur.filter(id => !list.some(p => p.id === id)) : [...new Set([...cur, ...list.map(p => p.id)])])}>
                        {allOn ? 'Desmarcar todas' : 'Marcar todas'}
                      </button>
                    </div>
                    <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
                      {list.map((p, i) => {
                        const on = picked.includes(p.id);
                        return (
                          <button key={p.id} type="button" aria-pressed={on} onClick={() => setPicked(cur => on ? cur.filter(id => id !== p.id) : [...cur, p.id])}
                            className={`text-left border-2 rounded-xl p-1.5 ${on ? 'border-blue-600 bg-blue-50' : 'border-slate-200 bg-white hover:bg-slate-50'}`}>
                            <img src={p.url} alt={p.caption} className="w-full h-28 object-contain rounded bg-slate-50" />
                            <span className="flex items-center gap-1 mt-1 text-[10px] text-slate-700">
                              {on ? <CheckSquare className="w-3.5 h-3.5 text-blue-600 shrink-0" /> : <Square className="w-3.5 h-3.5 text-slate-400 shrink-0" />}
                              <span className="truncate">Fig. {i + 1} — {p.caption}</span>
                            </span>
                          </button>
                        );
                      })}
                    </div>
                  </div>
                );
              })}
            </div>
            <div className="px-4 py-3 border-t border-slate-200 flex justify-end gap-2">
              <button type="button" className={btnSecondary} onClick={() => setCopyOpen(false)}><X className="w-3.5 h-3.5" /> Cancelar</button>
              <button type="button" className={btnPrimary} disabled={!picked.length} onClick={copyPicked}><CopyPlus className="w-3.5 h-3.5" /> Copiar {picked.length || ''} imagem(ns)</button>
            </div>
          </div>
        </div>
      )}

      {zoom && (
        <div className="fixed inset-0 z-[90] bg-slate-900/80 flex items-center justify-center p-4" onClick={() => setZoom(null)} role="dialog" aria-label="Croqui ampliado">
          <div className="relative max-w-5xl w-full" onClick={e => e.stopPropagation()}>
            <img src={zoom.url} alt={zoom.caption} className="w-full max-h-[85vh] object-contain rounded-lg bg-white" />
            <p className="text-xs text-white mt-2">{zoom.caption}</p>
            <button type="button" className="absolute top-2 right-2 bg-white rounded-full p-1" onClick={() => setZoom(null)} aria-label="Fechar"><X className="w-4 h-4" /></button>
          </div>
        </div>
      )}
    </section>
  );
};

/** Bloco de impressão (relatório do subsistema e laudo). */
export const SketchPrint: React.FC<{ kind: SketchKind; building?: SpdaBuilding; figCls?: string }> = ({ kind, building, figCls = 'max-h-72' }) => {
  const s = sketchOf(building, kind);
  if (!s || !s.images.length) return null; // sem arquivo de croqui: nada no lugar
  const info = SKETCH_INFO[kind];
  return (
    <div className="spda-avoid mb-3">
      <p className="font-bold text-[11px] text-[#0A2540] mb-1">{info.title} — {building?.name || 'edificação'}</p>
      {s.images.length > 0 && (
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
          {s.images.map((p, i) => (
            <figure key={p.id} className="spda-avoid border border-slate-200 rounded bg-white p-1">
              <img src={p.url} alt={p.caption} className={`w-full ${figCls} object-contain rounded`} />
              <figcaption className="text-[9px] text-slate-600 mt-0.5">Fig. {i + 1} — {p.caption}</figcaption>
            </figure>
          ))}
        </div>
      )}
      {s.notes?.trim() && <p className="text-[10px] text-slate-700 mt-1 whitespace-pre-line"><b>Identificação dos pontos:</b> {s.notes.trim()}</p>}
    </div>
  );
};
