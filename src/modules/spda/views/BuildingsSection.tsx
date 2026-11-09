/**
 * Bloco "Edificações da OS" (aba Dados): várias edificações por inspeção/OS,
 * cada uma com tipo, dimensões, tipo de SPDA existente, nível de proteção,
 * coordenadas geográficas e imagem aérea.
 */
import React, { useRef, useState } from 'react';
import {
  Building2, Camera, Copy, Crosshair, Factory, Home, Hospital, Image as ImageIcon, Landmark, MapPin, Plus, Store, Trash2, X, Zap
} from 'lucide-react';
import { newBuilding } from '../repository';
import {
  BUILDING_TYPE_LABEL, LEVEL_PARAMS, PROTECTION_LEVEL_LABEL, SPDA_KIND_LABEL, buildingArea, formatCoordinates,
  levelEfficiencyPct, mapUrl, minDownConductors, validCoordinates
} from '../rules';
import type { BuildingType, ProtectionLevel, SpdaBuilding, SpdaKind } from '../types';
import { DecimalField, Field, alertError, btnDanger, btnSecondary, cardCls, inputCls } from './ui';
import { compressImage } from '../../../utils/imageCompressor';

const TYPE_ICON: Record<BuildingType, React.ElementType> = {
  industrial: Factory, hospitalar: Hospital, comercial: Store, residencial: Home, outro: Building2
};
const KIND_ICON: Record<SpdaKind, React.ElementType> = {
  estrutural: Landmark, natural: Building2, externo: Zap, isolado: Zap, inexistente: X
};
const LEVELS: ProtectionLevel[] = ['I', 'II', 'III', 'IV'];

const choiceCls = (on: boolean) =>
  `flex flex-col items-center justify-center gap-1 p-2.5 rounded-xl border text-xs font-bold transition-colors ${on ? 'border-teal-600 bg-teal-50 text-teal-800 ring-1 ring-teal-600' : 'border-slate-200 bg-white text-slate-600 hover:bg-slate-50'}`;

const BuildingCard: React.FC<{
  b: SpdaBuilding; index: number; readOnly: boolean; canRemove: boolean;
  set: (p: Partial<SpdaBuilding>) => void; remove: () => void; duplicate: () => void;
}> = ({ b, index, readOnly, canRemove, set, remove, duplicate }) => {
  const fileRef = useRef<HTMLInputElement>(null);
  const [locating, setLocating] = useState(false);
  const area = buildingArea(b);
  const minDowns = minDownConductors(b);
  const lp = LEVEL_PARAMS[b.protectionLevel];
  const url = mapUrl(b.latitude, b.longitude);

  const useMyLocation = () => {
    if (!navigator.geolocation) { window.alert('Este aparelho não fornece localização.'); return; }
    setLocating(true);
    navigator.geolocation.getCurrentPosition(
      pos => { set({ latitude: Number(pos.coords.latitude.toFixed(6)), longitude: Number(pos.coords.longitude.toFixed(6)) }); setLocating(false); },
      err => { setLocating(false); window.alert(`Não foi possível obter a localização: ${err.message}`); },
      { enableHighAccuracy: true, timeout: 15000 }
    );
  };

  const onImage = async (files: FileList | null) => {
    const f = files?.[0];
    if (!f) return;
    try {
      const img = await compressImage(f, 1600, 1200, 0.75);
      if (img) set({ aerialImage: img });
    } catch (err) {
      alertError(err, 'Não foi possível carregar a imagem');
    } finally {
      if (fileRef.current) fileRef.current.value = '';
    }
  };

  return (
    <div className="border border-slate-200 rounded-2xl p-3 sm:p-4 space-y-4 bg-white">
      <div className="flex flex-wrap items-center gap-2">
        <span className="w-6 h-6 rounded-full bg-teal-700 text-white text-[11px] font-black flex items-center justify-center">{index + 1}</span>
        <input
          className={`${inputCls} flex-1 min-w-[180px] !text-sm !font-bold`}
          placeholder="Nome da edificação (ex.: Galpão de produção — Bloco A)"
          value={b.name}
          onChange={e => set({ name: e.target.value })}
          aria-label={`Nome da edificação ${index + 1}`}
        />
        {!readOnly && (
          <>
            <button type="button" className={btnSecondary} onClick={duplicate} title="Duplicar edificação"><Copy className="w-3.5 h-3.5" /></button>
            {canRemove && <button type="button" className={btnDanger} onClick={remove} aria-label={`Remover edificação ${index + 1}`}><Trash2 className="w-3.5 h-3.5" /></button>}
          </>
        )}
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
        <div className="grid grid-cols-1 sm:grid-cols-5 gap-2" role="radiogroup" aria-label="Tipo de SPDA">
          {(Object.keys(SPDA_KIND_LABEL) as SpdaKind[]).map(k => {
            const Icon = KIND_ICON[k];
            const on = b.spdaKind === k;
            return (
              <button key={k} type="button" role="radio" aria-checked={on} onClick={() => set({ spdaKind: k })}
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
          <select className={inputCls} value={b.protectionLevel} onChange={e => set({ protectionLevel: e.target.value as ProtectionLevel })}>
            {LEVELS.map(l => <option key={l} value={l}>{PROTECTION_LEVEL_LABEL[l]} — {levelEfficiencyPct(l)} % de eficiência</option>)}
          </select>
        </Field>
        <Field label="Nº de descidas existentes" hint={minDowns !== null ? `Mínimo pela NBR: ${minDowns} (perímetro ÷ ${lp.downSpacingM} m, 5.4.3.1)` : 'Informe C e L para calcular o mínimo'}>
          <DecimalField className={inputCls} value={b.downConductorsCount} onChange={n => set({ downConductorsCount: n })} />
        </Field>
        <div className="text-[11px] text-slate-600 bg-emerald-50 border border-emerald-200 rounded-xl px-3 py-2 self-end">
          Esfera rolante <b>{lp.sphereRadiusM} m</b> · malha <b>{lp.meshM} m</b> · PB {String(1 - levelEfficiencyPct(b.protectionLevel) / 100).replace('.', ',')} (5419-2, Tabela B.2)
          {minDowns !== null && b.downConductorsCount != null && b.downConductorsCount < minDowns && (
            <span className="block text-red-700 font-bold">Descidas abaixo do mínimo ({b.downConductorsCount} &lt; {minDowns})</span>
          )}
        </div>
      </div>

      {/* Localização e imagem aérea */}
      <div className="bg-slate-50 border border-slate-200 rounded-xl p-3 space-y-2">
        <p className="font-bold text-slate-700 text-xs flex items-center gap-1.5"><MapPin className="w-3.5 h-3.5" /> Localização e imagem aérea</p>
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
        <input ref={fileRef} type="file" accept="image/*" className="hidden" onChange={e => onImage(e.target.files)} />
        {b.aerialImage ? (
          <div className="relative">
            <img src={b.aerialImage} alt={`Imagem aérea — ${b.name || `edificação ${index + 1}`}`} className="w-full max-h-72 object-contain rounded-lg border border-slate-200 bg-white" />
            {!readOnly && (
              <div className="absolute top-2 right-2 flex gap-1">
                <button type="button" className={btnSecondary} onClick={() => fileRef.current?.click()}><Camera className="w-3.5 h-3.5" /> Trocar</button>
                <button type="button" className={btnDanger} onClick={() => set({ aerialImage: undefined })} aria-label="Remover imagem aérea"><Trash2 className="w-3.5 h-3.5" /></button>
              </div>
            )}
          </div>
        ) : !readOnly && (
          <button type="button" onClick={() => fileRef.current?.click()}
            className="w-full border-2 border-dashed border-slate-300 rounded-xl py-5 text-xs text-slate-500 hover:bg-white hover:border-teal-500 flex flex-col items-center gap-1">
            <ImageIcon className="w-5 h-5" />
            Adicionar imagem aérea / de satélite (captura do mapa, foto de drone…)
          </button>
        )}
      </div>
    </div>
  );
};

export const BuildingsSection: React.FC<{ buildings: SpdaBuilding[]; onChange: (next: SpdaBuilding[]) => void; readOnly: boolean }> = ({ buildings, onChange, readOnly }) => {
  const set = (id: string, p: Partial<SpdaBuilding>) => onChange(buildings.map(b => (b.id === id ? { ...b, ...p } : b)));
  return (
    <section className={`${cardCls} p-4`}>
      <div className="flex flex-wrap items-center justify-between gap-2 mb-3">
        <h3 className="font-bold text-sm text-slate-900">Edificações da OS <span className="text-slate-400 font-normal">({buildings.length})</span></h3>
        <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200">ABNT NBR 5419:2026</span>
      </div>
      <div className="space-y-3">
        {buildings.map((b, i) => (
          <BuildingCard
            key={b.id} b={b} index={i} readOnly={readOnly} canRemove={buildings.length > 1}
            set={p => set(b.id, p)}
            remove={() => { if (window.confirm(`Remover a edificação "${b.name || i + 1}"?`)) onChange(buildings.filter(x => x.id !== b.id)); }}
            duplicate={() => onChange([...buildings.slice(0, i + 1), { ...b, ...newBuilding(), type: b.type, spdaKind: b.spdaKind, protectionLevel: b.protectionLevel, activity: b.activity, name: `${b.name} (cópia)`.trim() }, ...buildings.slice(i + 1)])}
          />
        ))}
        {buildings.length === 0 && <p className="text-center text-xs text-slate-400 py-3">Nenhuma edificação cadastrada.</p>}
      </div>
      {!readOnly && (
        <button type="button" className={`${btnSecondary} mt-3`} onClick={() => onChange([...buildings, newBuilding({ name: `Edificação ${buildings.length + 1}` })])}>
          <Plus className="w-3.5 h-3.5" /> Adicionar edificação
        </button>
      )}
    </section>
  );
};
