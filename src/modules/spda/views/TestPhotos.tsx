/**
 * Registro fotográfico de um ensaio (continuidade ou aterramento), no mesmo
 * padrão dos ensaios de EPI: câmera em tempo real, câmera nativa do celular e
 * galeria/arquivos, com legenda, edição (girar/marcar) e exclusão.
 * Reutiliza os componentes da plataforma (LiveCameraModal, PhotoEditorModal)
 * sem alterá-los. Fotos comprimidas, salvas com a inspeção (só no aparelho).
 */
import React, { useRef, useState } from 'react';
import { Camera, FolderOpen, PenLine, RefreshCw, Smartphone, Trash2, X } from 'lucide-react';
import { LiveCameraModal } from '../../../components/LiveCameraModal';
import { PhotoEditorModal } from '../../../components/PhotoEditorModal';
import { compressImage } from '../../../utils/imageCompressor';
import { currentUserName } from '../repository';
import { newId } from '../rules';
import type { SpdaPhoto } from '../types';
import { alertError } from './ui';

export const MAX_TEST_PHOTOS = 6;

const btn = 'inline-flex items-center justify-center gap-1.5 px-2.5 py-1.5 rounded-lg text-[11px] font-bold text-white transition-colors disabled:opacity-50';

export const TestPhotos: React.FC<{
  photos?: SpdaPhoto[];
  onChange: (next: SpdaPhoto[]) => void;
  readOnly: boolean;
  /** Legenda sugerida (ex.: "F.4 — Captação → BEP") */
  defaultCaption: string;
}> = ({ photos = [], onChange, readOnly, defaultCaption }) => {
  const nativeRef = useRef<HTMLInputElement>(null);
  const galleryRef = useRef<HTMLInputElement>(null);
  const [liveOpen, setLiveOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [editing, setEditing] = useState<SpdaPhoto | null>(null);
  const [zoom, setZoom] = useState<SpdaPhoto | null>(null);
  const room = MAX_TEST_PHOTOS - photos.length;

  const add = (list: SpdaPhoto[]) => onChange([...photos, ...list].slice(0, MAX_TEST_PHOTOS));

  const fromFiles = async (files: FileList | null) => {
    if (!files?.length) return;
    setBusy(true);
    try {
      const chosen = Array.from(files).slice(0, Math.max(0, room));
      const urls = (await Promise.all(chosen.map(f => compressImage(f, 1280, 960, 0.75)))).filter(Boolean);
      const user = currentUserName();
      add(urls.map(url => ({ id: newId('foto'), url, caption: defaultCaption, timestamp: new Date().toISOString(), userName: user })));
    } catch (err) {
      alertError(err, 'Não foi possível adicionar a foto');
    } finally {
      setBusy(false);
      if (nativeRef.current) nativeRef.current.value = '';
      if (galleryRef.current) galleryRef.current.value = '';
    }
  };

  const setCaption = (id: string, caption: string) => onChange(photos.map(p => (p.id === id ? { ...p, caption } : p)));

  if (readOnly && photos.length === 0) return null;

  return (
    <div className="border-t border-dashed border-slate-200 pt-2 mt-1">
      <div className="flex flex-wrap items-center gap-2">
        <span className="text-[10px] font-bold text-slate-600 uppercase tracking-wide flex items-center gap-1"><Camera className="w-3 h-3" /> Registro fotográfico do ensaio</span>
        <span className="text-[10px] text-slate-400">{photos.length}/{MAX_TEST_PHOTOS}</span>
        {!readOnly && (
          <div className="flex flex-wrap gap-1.5 ml-auto">
            <button type="button" className={`${btn} bg-gradient-to-r from-orange-600 to-amber-600 hover:from-orange-500 hover:to-amber-500`} disabled={busy || room <= 0} onClick={() => setLiveOpen(true)} title="Abrir visor da câmera em tempo real">
              <Camera className="w-3.5 h-3.5" /> Câmera em tempo real
            </button>
            <button type="button" className={`${btn} bg-slate-800 hover:bg-slate-700`} disabled={busy || room <= 0} onClick={() => nativeRef.current?.click()} title="Abrir a câmera padrão do celular">
              <Smartphone className="w-3.5 h-3.5 text-amber-400" /> Câmera nativa
            </button>
            <button type="button" className={`${btn} bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-500 hover:to-indigo-500`} disabled={busy || room <= 0} onClick={() => galleryRef.current?.click()} title="Selecionar fotos da galeria ou arquivos">
              <FolderOpen className="w-3.5 h-3.5" /> Galeria / arquivos
            </button>
          </div>
        )}
        <input ref={nativeRef} type="file" accept="image/*" capture="environment" className="hidden" onChange={e => fromFiles(e.target.files)} />
        <input ref={galleryRef} type="file" accept="image/*" multiple className="hidden" onChange={e => fromFiles(e.target.files)} />
      </div>

      {busy && (
        <p className="mt-1.5 text-[11px] text-orange-700 flex items-center gap-1.5"><RefreshCw className="w-3 h-3 animate-spin" /> Processando e anexando a foto ao ensaio…</p>
      )}

      {photos.length > 0 && (
        <div className="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-6 gap-2 mt-2">
          {photos.map(p => (
            <div key={p.id} className="border border-slate-200 rounded-lg bg-white p-1">
              <button type="button" className="block w-full" onClick={() => setZoom(p)} aria-label={`Ampliar foto ${p.caption}`}>
                <img src={p.url} alt={p.caption} className="w-full h-20 object-cover rounded" />
              </button>
              <input
                className="w-full mt-1 px-1 py-0.5 text-[10px] border border-slate-200 rounded disabled:bg-slate-50"
                value={p.caption}
                disabled={readOnly}
                onChange={e => setCaption(p.id, e.target.value)}
                aria-label="Legenda da foto"
              />
              <div className="flex items-center justify-between mt-0.5">
                <span className="text-[9px] text-slate-400 truncate">{new Date(p.timestamp).toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' })}{p.editedAt ? ' · editada' : ''}</span>
                {!readOnly && (
                  <span className="flex gap-0.5">
                    <button type="button" className="p-0.5 text-slate-400 hover:text-blue-600" onClick={() => setEditing(p)} aria-label="Editar foto (girar/marcar)" title="Girar / marcar"><PenLine className="w-3 h-3" /></button>
                    <button type="button" className="p-0.5 text-slate-400 hover:text-red-600" onClick={() => { if (window.confirm('Excluir esta foto do ensaio?')) onChange(photos.filter(x => x.id !== p.id)); }} aria-label="Excluir foto"><Trash2 className="w-3 h-3" /></button>
                  </span>
                )}
              </div>
            </div>
          ))}
        </div>
      )}

      <LiveCameraModal
        isOpen={liveOpen}
        onClose={() => setLiveOpen(false)}
        defaultCategory="medicao"
        defaultCaption={defaultCaption}
        userName={currentUserName()}
        onCapturePhoto={photo => {
          add([{ id: newId('foto'), url: photo.url, caption: photo.caption?.trim() || defaultCaption, timestamp: new Date().toISOString(), userName: photo.userName || currentUserName(), gpsCoords: photo.gpsCoords }]);
        }}
      />

      {editing && (
        <PhotoEditorModal
          src={editing.url}
          title={`Editar foto — ${editing.caption || 'ensaio'}`}
          onClose={() => setEditing(null)}
          onSave={dataUrl => {
            onChange(photos.map(p => (p.id === editing.id ? { ...p, url: dataUrl, originalUrl: p.originalUrl || p.url, editedAt: new Date().toISOString() } : p)));
            setEditing(null);
          }}
        />
      )}

      {zoom && (
        <div className="fixed inset-0 z-[90] bg-slate-900/80 flex items-center justify-center p-4" onClick={() => setZoom(null)} role="dialog" aria-label="Foto ampliada">
          <div className="relative max-w-3xl w-full" onClick={e => e.stopPropagation()}>
            <img src={zoom.url} alt={zoom.caption} className="w-full max-h-[80vh] object-contain rounded-lg bg-white" />
            <p className="text-xs text-white mt-2">{zoom.caption} · {zoom.userName} · {new Date(zoom.timestamp).toLocaleString('pt-BR')}</p>
            <button type="button" className="absolute top-2 right-2 bg-white rounded-full p-1" onClick={() => setZoom(null)} aria-label="Fechar"><X className="w-4 h-4" /></button>
          </div>
        </div>
      )}
    </div>
  );
};
