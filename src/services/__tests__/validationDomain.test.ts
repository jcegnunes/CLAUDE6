import { describe, expect, it } from 'vitest';
import {
  APP_BASE_URL,
  DEFAULT_VALIDATION_BASE_URL,
  isPortalOnlyHost,
  normalizeValidationBaseUrl
} from '../../config/validationPortalConfig';

describe('Endereços da plataforma', () => {
  it('sistema em jvmlab.com.br e validação em validador.jvmlab.com.br', () => {
    expect(APP_BASE_URL).toBe('https://jvmlab.com.br');
    expect(DEFAULT_VALIDATION_BASE_URL).toBe('https://validador.jvmlab.com.br');
    expect(normalizeValidationBaseUrl('')).toBe('https://validador.jvmlab.com.br');
    expect(normalizeValidationBaseUrl(undefined)).toBe('https://validador.jvmlab.com.br');
  });

  it('endereços antigos passam para o validador', () => {
    expect(normalizeValidationBaseUrl('https://mediumvioletred-bison-595566.hostingersite.com')).toBe(DEFAULT_VALIDATION_BASE_URL);
    expect(normalizeValidationBaseUrl('https://mediumturquoise-giraffe-910043.hostingersite.com/')).toBe(DEFAULT_VALIDATION_BASE_URL);
    expect(normalizeValidationBaseUrl('https://jvmlab.com.br')).toBe(DEFAULT_VALIDATION_BASE_URL);
    expect(normalizeValidationBaseUrl('https://jvmlab.com.br/')).toBe(DEFAULT_VALIDATION_BASE_URL);
  });

  it('o próprio validador e endereços configurados manualmente são mantidos', () => {
    expect(normalizeValidationBaseUrl('https://validador.jvmlab.com.br/')).toBe('https://validador.jvmlab.com.br');
    expect(normalizeValidationBaseUrl('https://certificados.cliente.com.br')).toBe('https://certificados.cliente.com.br');
  });

  it('só o validador funciona como portal exclusivo', () => {
    expect(isPortalOnlyHost('validador.jvmlab.com.br')).toBe(true);
    expect(isPortalOnlyHost('VALIDADOR.jvmlab.com.br')).toBe(true);
    expect(isPortalOnlyHost('jvmlab.com.br')).toBe(false);
    expect(isPortalOnlyHost('localhost')).toBe(false);
  });
});
