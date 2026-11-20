import { describe, expect, it } from 'vitest';
import { existsSync } from 'fs';
import path from 'path';
import { MODULE_APPS, appDownloadUrl } from './ModuleAppDownload';

describe('Baixar o aplicativo do módulo', () => {
  it('cada módulo com app aponta para o seu APK', () => {
    expect(MODULE_APPS.spda.file).toBe('apps/JVM-SPDA.apk');
    expect(MODULE_APPS.ensaios.file).toBe('apps/JVM-Ensaios-EPI.apk');
    expect(MODULE_APPS.treinamentos).toBeUndefined();
  });
  it('monta o endereço completo para o QR Code', () => {
    expect(appDownloadUrl('apps/JVM-SPDA.apk', 'https://site.com.br/')).toBe('https://site.com.br/apps/JVM-SPDA.apk');
  });
  it('os APKs estão em public/apps (publicados com a plataforma)', () => {
    for (const a of Object.values(MODULE_APPS)) expect(existsSync(path.resolve(__dirname, '../../public', a.file))).toBe(true);
  });
});
