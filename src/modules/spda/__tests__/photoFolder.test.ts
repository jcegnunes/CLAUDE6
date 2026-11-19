import { afterEach, describe, expect, it } from 'vitest';
import { autoSavePhotos, photoFileName, photoFolderOf, photoFolderPath, safeName } from '../photoFolder';

const w = globalThis as unknown as { window?: { AndroidBridge?: unknown } };

describe('Pasta das fotos por OS (app Android)', () => {
  afterEach(() => { if (w.window) delete w.window.AndroidBridge; });

  it('pasta: OS / edificação / subsistema; sem OS usa o nº da inspeção', () => {
    expect(photoFolderPath(photoFolderOf({ serviceOrderNumber: 'OS-SPDA-2026-0001', number: 'SPDA-2026-0001' }, 'Bloco A', 'Captação'))).toBe('OS-SPDA-2026-0001/Bloco A/Captação');
    expect(photoFolderPath(photoFolderOf({ number: 'SPDA-2026-0007' }, undefined, 'Documentação'))).toBe('SPDA-2026-0007/Documentação');
    expect(safeName('F.4 — Captação → BEP: ok?')).toBe('F.4 — Captação - BEP ok');
  });

  it('arquivo: legenda + data e hora', () => {
    expect(photoFileName('Descida D1 / D2', '2026-11-16T10:05:09')).toBe('Descida D1 D2_20261116-100509.jpg');
    expect(photoFileName('x', '2026-11-16T10:05:09', 1, 'png')).toBe('x_20261116-100509_2.png');
  });

  it('grava pela ponte do app; no navegador não grava', () => {
    const calls: string[][] = [];
    const folder = photoFolderOf({ serviceOrderNumber: 'OS-1', number: 'S-1' }, 'Bloco A', 'Descida');
    const photo = { url: 'data:image/jpeg;base64,QUJD', caption: 'Descida D1', timestamp: '2026-11-16T10:05:09' };
    w.window = w.window || {};
    expect(autoSavePhotos(folder, [photo])).toBe(0);
    w.window.AndroidBridge = { savePhotoFile: (b64: string, mime: string, dir: string, name: string) => { calls.push([b64, mime, dir, name]); return true; } };
    expect(autoSavePhotos(folder, [photo])).toBe(1);
    expect(calls[0]).toEqual(['QUJD', 'image/jpeg', 'OS-1/Bloco A/Descida', 'Descida D1_20261116-100509.jpg']);
    expect(autoSavePhotos(null, [photo])).toBe(0);
  });
});
