/**
 * Bloco "Edificações da OS" (aba Dados): várias edificações por inspeção/OS,
 * cada uma com tipo, dimensões, tipo de SPDA existente, nível de proteção,
 * coordenadas geográficas e imagem aérea.
 */
import React, { useEffect, useRef, useState } from 'react';
import {
  Building2, Camera, ClipboardPaste, Copy, Crosshair, ExternalLink, Map as MapIcon, Factory, Home, Hospital, Image as ImageIcon, Landmark, Layers, MapPin, Pencil, PenLine, Plus, Save, Store, Trash2, X, Zap
} from 'lucide-react';
import { newBuilding } from '../repository';
import {
  BUILDING_TYPE_LABEL, LEVEL_PB, PROTECTION_LEVEL_LABEL, SPDA_KIND_LABEL, buildingArea, formatCoordinates,
  levelEfficiencyPct, levelParams, mapEmbedUrl, mapUrl, minDownConductors, parseMapsLink, validCoordinates
} from '../rules';
import type { BuildingType, ProtectionLevel, SpdaBuilding, SpdaKind } from '../types';
import { DecimalField, Field, alertError, btnDanger, btnPrimary, btnSecondary, cardCls, inputCls } from './ui';
import { compressImage } from '../../../utils/imageCompressor';
import { PhotoEditorModal } from '../../../components/PhotoEditorModal';
import { confirmAsk, notify } from './dialogs';

const TYPE_ICON: Record<BuildingType, React.ElementType> = {
  industrial: Factory, hospitalar: Hospital, comercial: Store, residencial: Home, outro: Building2
};
const KIND_ICON: Record<SpdaKind, React.ElementType> = {
  estrutural: Landmark, natural: Building2, externo: Zap, misto: Layers, isolado: Zap, inexistente: X
};
const LEVELS: ProtectionLevel[] = ['I', 'II', 'III', 'IV'];

const choiceCls = (on: boolean) =>
  `flex flex-col items-center justify-center gap-1 p-2.5 rounded-xl border text-xs font-bold transition-colors ${on ? 'border-teal-600 bg-teal-50 text-teal-800 ring-1 ring-teal-600' : 'border-slate-200 bg-white text-slate-600 hover:bg-slate-50'}`;

const BuildingCard: React.FC<{
  b: SpdaBuilding; index: number; readOnly: boolean;
  set: (p: Partial<SpdaBuilding>) => void;
  /** Barra de botões (Editar/Excluir ou Salvar/Cancelar) no rodapé do cartão */
  actions?: React.ReactNode;
  editing?: boolean;
}> = ({ b, index, readOnly, set, actions, editing }) => {
  const fileRef = useRef<HTMLInputElement>(null);
  const [locating, setLocating] = useState(false);
  const [editImg, setEditImg] = useState(false);
  const area = buildingArea(b);
  const minDowns = minDownConductors(b);
  const lp = levelParams(b.protectionLevel);
  const url = mapUrl(b.latitude, b.longitude);
  const embed = mapEmbedUrl(b.latitude, b.longitude);
  const camRef = useRef<HTMLInputElement>(null);

  const useMyLocation = () => {
    if (!navigator.geolocation) { void notify('Este aparelho não fornece localização.'); return; }
    setLocating(true);
    navigator.geolocation.getCurrentPosition(
      pos => { set({ latitude: Number(pos.coords.latitude.toFixed(6)), longitude: Number(pos.coords.longitude.toFixed(6)) }); setLocating(false); },
      err => { setLocating(false); void notify(`Não foi possível obter a localização: ${err.message}`); },
      { enableHighAccuracy: true, timeout: 15000 }
    );
  };

  const onImageFile = async (f: File | Blob) => {
    try {
      const file = f instanceof File ? f : new File([f], 'imagem-aerea.png', { type: f.type || 'image/png' });
      const img = await compressImage(file, 1600, 1200, 0.75);
      if (img) set({ aerialImage: img });
    } catch (err) {
      alertError(err, 'Não foi possível carregar a imagem');
    }
  };
  const onImage = async (files: FileList | null) => {
    const f = files?.[0];
    if (f) await onImageFile(f);
    if (fileRef.current) fileRef.current.value = '';
    if (camRef.current) camRef.current.value = '';
  };
  const pasteImage = async () => {
    try {
      const items = await navigator.clipboard.read();
      for (const it of items) {
        const type = it.types.find(t => t.startsWith('image/'));
        if (type) { await onImageFile(await it.getType(type)); return; }
      }
      void notify('Não há imagem na área de transferência. Copie uma captura do mapa (ex.: Win+Shift+S) e tente de novo.');
    } catch {
      void notify('O navegador não liberou a área de transferência. Clique na área tracejada e use Ctrl+V.');
    }
  };
  const onMapsLink = (link: string) => {
    const c = parseMapsLink(link);
    set(c ? { mapsLink: link, ...c } : { mapsLink: link });
  };

  return (
    <div className={`border rounded-2xl p-3 sm:p-4 bg-white ${editing ? 'border-blue-400 ring-2 ring-blue-100' : 'border-slate-200'}`}>
    {editing && <p className="text-[11px] font-bold text-blue-700 mb-2">Editando — toque em Salvar para gravar ou Cancelar para descartar as alterações.</p>}
    <fieldset disabled={readOnly} className="space-y-4 min-w-0">
      <div className="flex flex-wrap items-center gap-2">
        <span className="w-6 h-6 rounded-full bg-teal-700 text-white text-[11px] font-black flex items-center justify-center">{index + 1}</span>
        <input
          className={`${inputCls} flex-1 min-w-[180px] !text-sm !font-bold`}
          placeholder="Nome da edificação (ex.: Galpão de produção — Bloco A)"
          value={b.name}
          onChange={e => set({ name: e.target.value })}
          aria-label={`Nome da edificação ${index + 1}`}
        />
      </div>

      {/* Tipo da edificação */}
      <div>
        <span className="block font-bold text-slate-700 mb-1 text-xs">Tipo da edificação *</span>
        <div className="grid grid-cols-3 sm:grid-cols-5 gap-2" role="radiogroup" aria-label="Tipo da edificação">
          {(Object.keys(BUILDING_TYPE_LABEL) as BuildingType[]).map(t => {
            const Icon = TYPE_ICON[t];
            return (
              <button key={t} type="button" role="radio" aria-checked={b.type === t} className={choiceCls(b.type === t)} onClick={() => set({ type: t })}>
                <Icon className="w-4 h-4" /> {BUILDING_TYPE_LABEL[t]}
              </button>
            );
          })}
        </div>
      </div>
      <Field label="Descrição específica da atividade / ocupação">
        <input className={inputCls} placeholder="Ex.: Industrial / manufatura" value={b.activity || ''} onChange={e => set({ activity: e.target.value })} />
      </Field>

      {/* Dimensões */}
      <div className="bg-slate-50 border border-slate-200 rounded-xl p-3">
        <p className="font-bold text-slate-700 text-xs mb-2">Dimensões físicas da estrutura</p>
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
          <Field label="Altura H (m)"><DecimalField className={inputCls} value={b.heightM} onChange={n => set({ heightM: n })} /></Field>
          <Field label="Comprimento C (m)"><DecimalField className={inputCls} value={b.lengthM} onChange={n => set({ lengthM: n })} /></Field>
          <Field label="Largura L (m)"><DecimalField className={inputCls} value={b.widthM} onChange={n => set({ widthM: n })} /></Field>
          <Field label="Pavimentos"><DecimalField className={inputCls} value={b.floors} onChange={n => set({ floors: n })} /></Field>
        </div>
        <p className="text-[11px] text-slate-600 mt-2">
          Área projetada em planta: <b>{area !== null ? `${area.toLocaleString('pt-BR', { maximumFractionDigits: 1 })} m²` : '—'}</b>
          {b.heightM != null && b.heightM > 60 && <span className="text-amber-700"> · acima de 60 m: prever captação lateral (5.3.2.14.2)</span>}
        </p>
      </div>

      {/* Tipo de SPDA */}
      <div>
        <span className="block font-bold text-slate-700 mb-1 text-xs">Tipo de SPDA existente (ABNT NBR 5419-3:2026)</span>
        <div className="grid grid-cols-1 sm:grid-cols-3 lg:grid-cols-6 gap-2" role="radiogroup" aria-label="Tipo de SPDA">
          {(Object.keys(SPDA_KIND_LABEL) as SpdaKind[]).map(k => {
            const Icon = KIND_ICON[k];
            const on = b.spdaKind === k;
            return (
              <button key={k} type="button" role="radio" aria-checked={on}
                onClick={() => set({ spdaKind: k })}
                className={`text-left p-2.5 rounded-xl border transition-colors ${on ? 'border-teal-600 bg-teal-50 ring-1 ring-teal-600' : 'border-slate-200 bg-white hover:bg-slate-50'}`}>
                <span className={`flex items-center gap-1.5 text-xs font-bold ${on ? 'text-teal-800' : 'text-slate-700'}`}><Icon className="w-3.5 h-3.5" /> {SPDA_KIND_LABEL[k].label}</span>
                <span className="block text-[10px] text-slate-500 mt-0.5 leading-snug">{SPDA_KIND_LABEL[k].hint}</span>
              </button>
            );
          })}
        </div>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
        <Field label="Nível de proteção (NP)">
          <select className={inputCls} value={b.protectionLevel} onChange={e => set({ protectionLevel: e.target.value as ProtectionLevel | '' })}>
            <option value="">Sem nível definido</option>
            {LEVELS.map(l => <option key={l} value={l}>{PROTECTION_LEVEL_LABEL[l]} — {levelEfficiencyPct(l)} % de eficiência</option>)}
          </select>
        </Field>
        <Field label="Nº de descidas existentes" hint={minDowns !== null ? `Mínimo pela NBR: ${minDowns} (perímetro ÷ ${lp!.downSpacingM} m, 5.4.3.1)` : lp ? 'Informe C e L para calcular o mínimo' : 'Defina o nível de proteção para calcular o mínimo'}>
          <DecimalField className={inputCls} value={b.downConductorsCount} onChange={n => set({ downConductorsCount: n })} />
        </Field>
        <div className="text-[11px] text-slate-600 bg-emerald-50 border border-emerald-200 rounded-xl px-3 py-2 self-end">
          {lp
            ? <>Esfera rolante <b>{lp.sphereRadiusM} m</b> · malha <b>{lp.meshM} m</b> · PB {LEVEL_PB[b.protectionLevel as ProtectionLevel].toLocaleString('pt-BR')} (5419-2, Tabela B.2)</>
            : <>Defina o nível de proteção (NP) para ver os parâmetros da NBR 5419 (esfera rolante, malha e descidas).</>}
          {minDowns !== null && b.downConductorsCount != null && b.downConductorsCount < minDowns && (
            <span className="block text-red-700 font-bold">Descidas abaixo do mínimo ({b.downConductorsCount} &lt; {minDowns})</span>
          )}
        </div>
      </div>

      {/* Link da localização (preenche as coordenadas) */}
      <div className="bg-slate-50 border border-slate-200 rounded-xl p-3">
        <Field label="Link da localização (Google Maps)" hint="Cole o link do local (Compartilhar → Copiar link). Se o link trouxer as coordenadas, a latitude e a longitude abaixo são preenchidas.">
          <div className="flex gap-2">
            <input className={`${inputCls} flex-1 min-w-0`} placeholder="https://www.google.com/maps/@-15.793889,-47.882778,19z" value={b.mapsLink || ''}
              onChange={e => onMapsLink(e.target.value)} aria-label="Link do Google Maps" />
            {/^https?:\/\//i.test(b.mapsLink || '') && (
              <a href={b.mapsLink} target="_blank" rel="noopener noreferrer" className={btnSecondary}><ExternalLink className="w-3.5 h-3.5" /> Abrir</a>
            )}
          </div>
        </Field>
      </div>

      {/* Localização */}
      <div className="bg-slate-50 border border-slate-200 rounded-xl p-3 space-y-2">
        <p className="font-bold text-slate-700 text-xs flex items-center gap-1.5"><MapPin className="w-3.5 h-3.5" /> Localização (coordenadas geográficas)</p>
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 items-end">
          <Field label="Latitude (graus decimais)"><DecimalField className={inputCls} placeholder="-15,793889" value={b.latitude} onChange={n => set({ latitude: n })} /></Field>
          <Field label="Longitude (graus decimais)"><DecimalField className={inputCls} placeholder="-47,882778" value={b.longitude} onChange={n => set({ longitude: n })} /></Field>
          {!readOnly && (
            <button type="button" className={btnSecondary} onClick={useMyLocation} disabled={locating}>
              <Crosshair className={`w-3.5 h-3.5 ${locating ? 'animate-spin' : ''}`} /> {locating ? 'Obtendo…' : 'Usar minha localização'}
            </button>
          )}
          {url && (
            <a href={url} target="_blank" rel="noopener noreferrer" className={btnSecondary}><MapPin className="w-3.5 h-3.5" /> Ver no mapa (satélite)</a>
          )}
        </div>
        {(b.latitude != null || b.longitude != null) && (
          <p className={`text-[11px] ${validCoordinates(b.latitude, b.longitude) ? 'text-slate-600' : 'text-red-700'}`}>
            {validCoordinates(b.latitude, b.longitude) ? formatCoordinates(b.latitude, b.longitude) : 'Coordenadas inválidas (latitude −90 a 90, longitude −180 a 180).'}
          </p>
        )}
      </div>

      {/* Imagem aérea ou mapa da localização */}
      <div className="bg-slate-50 border border-slate-200 rounded-xl p-3 space-y-3">
        <div>
          <p className="font-bold text-slate-700 text-xs flex items-center gap-1.5"><MapIcon className="w-3.5 h-3.5" /> Imagem aérea ou mapa da localização</p>
          <p className="text-[10px] text-slate-500 mt-0.5">Insira uma imagem aérea (captura do mapa, satélite, foto de drone). A imagem sai no laudo.</p>
        </div>

        {/* Imagem aérea */}
        <input ref={fileRef} type="file" accept="image/*" className="hidden" onChange={e => onImage(e.target.files)} />
        <input ref={camRef} type="file" accept="image/*" capture="environment" className="hidden" onChange={e => onImage(e.target.files)} />
        {b.aerialImage ? (
          <div className="relative">
            <img src={b.aerialImage} alt={`Imagem aérea — ${b.name || `edificação ${index + 1}`}`} className="w-full max-h-72 object-contain rounded-lg border border-slate-200 bg-white" />
            {!readOnly && (
              <div className="absolute top-2 right-2 flex gap-1">
                <button type="button" className={btnSecondary} onClick={() => setEditImg(true)}><PenLine className="w-3.5 h-3.5" /> Girar / marcar</button>
                <button type="button" className={btnSecondary} onClick={() => fileRef.current?.click()}><ImageIcon className="w-3.5 h-3.5" /> Trocar</button>
                <button type="button" className={btnDanger} onClick={() => set({ aerialImage: undefined })} aria-label="Remover imagem aérea"><Trash2 className="w-3.5 h-3.5" /></button>
              </div>
            )}
          </div>
        ) : readOnly ? (
          <div className="w-full border-2 border-dashed border-slate-200 rounded-xl py-4 text-xs text-slate-400 flex flex-col items-center gap-1 bg-white">
            <ImageIcon className="w-5 h-5" /> Nenhuma imagem aérea — toque em Editar para inserir.
          </div>
        ) : (
          <div
            tabIndex={0}
            onPaste={e => { const f = Array.from(e.clipboardData.files as ArrayLike<File>).find(x => x.type.startsWith('image/')); if (f) { e.preventDefault(); void onImageFile(f); } }}
            onDragOver={e => e.preventDefault()}
            onDrop={e => { e.preventDefault(); const f = Array.from(e.dataTransfer.files as ArrayLike<File>).find(x => x.type.startsWith('image/')); if (f) void onImageFile(f); }}
            className="w-full border-2 border-dashed border-slate-300 rounded-xl p-4 text-xs text-slate-500 bg-white focus:outline-none focus:border-teal-500 flex flex-col items-center gap-2"
            aria-label="Área para inserir a imagem aérea"
          >
            <ImageIcon className="w-5 h-5" />
            <span className="text-center">Arraste a imagem aqui ou clique nesta área e cole (Ctrl+V) uma captura do mapa</span>
            <div className="flex flex-wrap justify-center gap-2">
              <button type="button" className={btnSecondary} onClick={() => fileRef.current?.click()}><ImageIcon className="w-3.5 h-3.5" /> Galeria / arquivo</button>
              <button type="button" className={btnSecondary} onClick={() => camRef.current?.click()}><Camera className="w-3.5 h-3.5" /> Câmera / drone</button>
              <button type="button" className={btnSecondary} onClick={pasteImage}><ClipboardPaste className="w-3.5 h-3.5" /> Colar imagem</button>
            </div>
          </div>
        )}

        {/* Pré-visualização do mapa */}
        {embed ? (
          <div className="space-y-1">
            <iframe title={`Mapa — ${b.name || `edificação ${index + 1}`}`} src={embed} loading="lazy" referrerPolicy="no-referrer-when-downgrade"
              className="w-full h-64 rounded-lg border border-slate-200 bg-white" />
            <p className="text-[10px] text-slate-500">Mapa de satélite pelas coordenadas (precisa de internet). Para o laudo, faça uma captura e insira como imagem aérea.</p>
          </div>
        ) : (
          <p className="text-[10px] text-slate-400">Informe as coordenadas (ou cole um link com coordenadas) para ver o mapa da localização aqui.</p>
        )}
      </div>
    </fieldset>
    {actions && <div className="flex flex-wrap justify-end gap-2 pt-3 mt-4 border-t border-slate-200">{actions}</div>}
    {editImg && b.aerialImage && (
      <PhotoEditorModal
        src={b.aerialImage}
        title={`Editar imagem aérea — ${b.name || `edificação ${index + 1}`}`}
        onClose={() => setEditImg(false)}
        onSave={dataUrl => { set({ aerialImage: dataUrl }); setEditImg(false); }}
      />
    )}
    </div>
  );
};

/**
 * Lista das edificações com edição explícita:
 * - cada edificação aparece só para leitura, com Editar / Duplicar / Excluir;
 * - em edição as mudanças ficam num rascunho até Salvar (Cancelar descarta);
 * - uma edificação nova só entra na lista ao Salvar.
 */
export const BuildingsSection: React.FC<{
  buildings: SpdaBuilding[]; onChange: (next: SpdaBuilding[]) => void; readOnly: boolean;
  /** Abre já o cadastro de uma edificação nova (ex.: ao criar uma OS sem edificações) */
  startNew?: boolean;
  /** Avisa quando há uma edificação em edição (rascunho ainda não salvo) */
  onEditingChange?: (editing: boolean) => void;
  /** Abre a primeira edificação já em modo de edição (nova inspeção) */
  startEditing?: boolean;
}> = ({ buildings, onChange, readOnly, startNew, onEditingChange, startEditing }) => {
  const [draft, setDraft] = useState<SpdaBuilding | null>(() => {
    if (readOnly) return null;
    if (startEditing && buildings.length > 0) return { ...buildings[0] };
    if ((startNew || startEditing) && buildings.length === 0) return newBuilding({ name: 'Edificação 1' });
    return null;
  });
  useEffect(() => { onEditingChange?.(!!draft); }, [draft, onEditingChange]);
  const isNew = !!draft && !buildings.some(b => b.id === draft.id);
  const editing = !!draft;
  const setDraftField = (p: Partial<SpdaBuilding>) => setDraft(prev => (prev ? { ...prev, ...p } : prev));

  const startEdit = (b: SpdaBuilding) => setDraft({ ...b });
  const cancel = async () => {
    if (draft && !isNew && JSON.stringify(draft) !== JSON.stringify(buildings.find(b => b.id === draft.id)) && !(await confirmAsk('Descartar as alterações desta edificação?'))) return;
    setDraft(null);
  };
  const save = () => {
    if (!draft) return;
    if (!draft.name.trim()) { void notify('Informe o nome da edificação.'); return; }
    onChange(isNew ? [...buildings, { ...draft, name: draft.name.trim() }] : buildings.map(b => (b.id === draft.id ? { ...draft, name: draft.name.trim() } : b)));
    setDraft(null);
  };
  const remove = async (b: SpdaBuilding, i: number) => {
    if (!(await confirmAsk(`Excluir a edificação "${b.name || `Edificação ${i + 1}`}"?\nA inspeção visual, os ensaios e as fotos lançados para ela também serão excluídos.`))) return;
    onChange(buildings.filter(x => x.id !== b.id));
  };
  const duplicate = (b: SpdaBuilding) => {
    setDraft({ ...b, ...newBuilding(), type: b.type, spdaKind: b.spdaKind, protectionLevel: b.protectionLevel, activity: b.activity, heightM: b.heightM, lengthM: b.lengthM, widthM: b.widthM, floors: b.floors, name: `${b.name} (cópia)`.trim() });
  };

  /** Os 4 botões sempre visíveis; cada um ativo só quando faz sentido. */
  const actionsFor = (b: SpdaBuilding, i: number, isEditing: boolean, isNewCard = false) => {
    // inspeção concluída: botões visíveis, porém inativos
    const locked = readOnly;
    return (
    <>
      <button type="button" className={btnDanger} disabled={locked || editing || isNewCard} onClick={() => remove(b, i)}><Trash2 className="w-3.5 h-3.5" /> Excluir</button>
      <button type="button" className={btnSecondary} disabled={locked || editing || isNewCard} onClick={() => duplicate(b)}><Copy className="w-3.5 h-3.5" /> Duplicar</button>
      <button type="button" className={btnSecondary} disabled={locked || editing} onClick={() => startEdit(b)}><Pencil className="w-3.5 h-3.5" /> Editar</button>
      <button type="button" className={btnSecondary} disabled={locked || !isEditing} onClick={cancel}><X className="w-3.5 h-3.5" /> Cancelar</button>
      <button type="button" className={btnPrimary} disabled={locked || !isEditing} onClick={save}><Save className="w-3.5 h-3.5" /> Salvar</button>
    </>
    );
  };

  return (
    <section className={`${cardCls} p-4`}>
      <div className="flex flex-wrap items-center justify-between gap-2 mb-3">
        <h3 className="font-bold text-sm text-slate-900">Edificações da OS <span className="text-slate-400 font-normal">({buildings.length})</span></h3>
        <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200">ABNT NBR 5419:2026</span>
      </div>
      <div className="space-y-3">
        {buildings.map((b, i) => {
          const isEditing = draft?.id === b.id;
          return (
            <BuildingCard
              key={b.id}
              b={isEditing ? draft! : b}
              index={i}
              readOnly={readOnly || !isEditing}
              editing={isEditing}
              set={setDraftField}
              actions={actionsFor(b, i, isEditing)}
            />
          );
        })}
        {isNew && draft && (
          <BuildingCard key={draft.id} b={draft} index={buildings.length} readOnly={false} editing set={setDraftField} actions={actionsFor(draft, buildings.length, true, true)} />
        )}
        {buildings.length === 0 && !isNew && <p className="text-center text-xs text-slate-400 py-3">Nenhuma edificação cadastrada.</p>}
      </div>
      {!readOnly && !editing && (
        <button type="button" className={`${btnSecondary} mt-3`} onClick={() => setDraft(newBuilding({ name: `Edificação ${buildings.length + 1}` }))}>
          <Plus className="w-3.5 h-3.5" /> Adicionar edificação
        </button>
      )}
    </section>
  );
};
