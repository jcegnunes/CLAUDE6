/**
 * Pasta local das fotos por ordem de serviço (app Android): cada foto tirada ou anexada é gravada
 * automaticamente em Download/JVM_SPDA/<OS>/<edificação>/<subsistema>/.
 * No navegador (sem o app) não há gravação automática — as fotos continuam salvas na inspeção.
 */
import React, { createContext, useContext } from 'react';
import type { SpdaInspection } from './types';

export interface PhotoFolder {
  /** Nº da OS (ou da inspeção, sem OS) */
  os: string;
  building?: string;
  subsystem?: string;
}

const Ctx = createContext<PhotoFolder | null>(null);
export const PhotoFolderProvider: React.FC<{ value: PhotoFolder | null; children: React.ReactNode }> = ({ value, children }) => <Ctx.Provider value={value}>{children}</Ctx.Provider>;
export const usePhotoFolder = () => useContext(Ctx);

/** Pasta da OS de uma inspeção. */
export function photoFolderOf(v: Pick<SpdaInspection, 'serviceOrderNumber' | 'number'>, building?: string, subsystem?: string): PhotoFolder {
  return { os: v.serviceOrderNumber?.trim() || v.number || 'Sem OS', building, subsystem };
}

/** Nome seguro para pasta/arquivo (mantém acentos; tira caracteres proibidos). */
export function safeName(s: string, max = 60): string {
  return s.replace(/[\\/:*?"<>|\u0000-\u001f]+/g, ' ').replace(/[→]/g, '-').replace(/\s+/g, ' ').trim().slice(0, max).replace(/[ .]+$/, '') || 'foto';
}

/** Caminho relativo dentro de Download/JVM_SPDA. */
export function photoFolderPath(f: PhotoFolder): string {
  return [f.os, f.building, f.subsystem].filter((x): x is string => !!x && !!x.trim()).map(x => safeName(x)).join('/');
}

/** Nome do arquivo: legenda + data/hora (AAAAMMDD-HHMMSS). */
export function photoFileName(caption: string, iso: string, index = 0, ext = 'jpg'): string {
  const d = new Date(iso);
  const p = (n: number) => String(n).padStart(2, '0');
  const stamp = Number.isNaN(d.getTime()) ? 'sem-data' : `${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}-${p(d.getHours())}${p(d.getMinutes())}${p(d.getSeconds())}`;
  return `${safeName(caption, 50)}_${stamp}${index ? `_${index + 1}` : ''}.${ext}`;
}

type Bridge = { savePhotoFile?: (b64: string, mime: string, folder: string, name: string) => boolean };

/** Grava as fotos na pasta da OS, se estiver no app Android. Não interrompe o fluxo em caso de erro. */
export function autoSavePhotos(folder: PhotoFolder | null, photos: Array<{ url: string; caption: string; timestamp: string }>): number {
  const bridge = (window as unknown as { AndroidBridge?: Bridge }).AndroidBridge;
  if (!folder || !bridge?.savePhotoFile) return 0;
  let n = 0;
  photos.forEach((p, i) => {
    const m = /^data:([^;]+);base64,(.*)$/.exec(p.url);
    if (!m) return;
    try {
      if (bridge.savePhotoFile!(m[2], m[1] || 'image/jpeg', photoFolderPath(folder), photoFileName(p.caption, p.timestamp, photos.length > 1 ? i : 0, /png/.test(m[1]) ? 'png' : /webp/.test(m[1]) ? 'webp' : 'jpg'))) n += 1;
    } catch { /* segue sem a cópia local */ }
  });
  return n;
}
