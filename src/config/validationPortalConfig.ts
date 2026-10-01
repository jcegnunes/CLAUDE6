/**
 * Endereços públicos da plataforma.
 * - Sistema (login, ensaios, laudos): https://jvmlab.com.br
 * - Validação de certificados/laudos (QR Code): https://validador.jvmlab.com.br
 * Não são banco de dados: o portal /validar/CODIGO consulta o Supabase.
 */
export const APP_BASE_URL = 'https://jvmlab.com.br';
export const DEFAULT_VALIDATION_BASE_URL = 'https://validador.jvmlab.com.br';

/**
 * Domínios onde o app funciona SOMENTE como portal de validação: qualquer
 * endereço abre a consulta de certificados (nunca a tela de login do sistema).
 */
// validador.localhost: mesmo modo para testes no próprio computador
export const PORTAL_ONLY_HOSTS = ['validador.jvmlab.com.br', 'validador.localhost'];

export function isPortalOnlyHost(hostname: string): boolean {
  return PORTAL_ONLY_HOSTS.includes((hostname || '').toLowerCase());
}

/**
 * Endereços antigos: os salvos nos aparelhos e no cadastro da empresa são
 * trocados automaticamente pelo endereço atual do validador. Documentos já
 * impressos com eles são validados digitando o código no validador.
 */
const LEGACY_DOMAINS = ['mediumturquoise-giraffe-910043', 'mediumvioletred-bison-595566'];
const LEGACY_EXACT = [APP_BASE_URL, 'https://www.jvmlab.com.br'];

export function normalizeValidationBaseUrl(raw?: string | null): string {
  const trimmed = (raw || '').trim().replace(/\/+$/, '');
  if (!trimmed || LEGACY_EXACT.includes(trimmed.toLowerCase()) || LEGACY_DOMAINS.some(d => trimmed.includes(d))) {
    return DEFAULT_VALIDATION_BASE_URL;
  }
  return trimmed;
}
