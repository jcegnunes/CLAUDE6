/**
 * Endereço público (hospedagem do app) usado nos QR Codes de validação.
 * Não é banco de dados: o portal /validar/CODIGO consulta o Supabase.
 */
export const DEFAULT_VALIDATION_BASE_URL = 'https://jvmlab.com.br';

/**
 * Domínios antigos que devem ser migrados automaticamente para o endereço atual.
 * ATENÇÃO: QR Codes já impressos com esses endereços continuam apontando para
 * eles. Mantenha-os no ar redirecionando para https://jvmlab.com.br.
 */
const LEGACY_DOMAINS = ['mediumturquoise-giraffe-910043', 'mediumvioletred-bison-595566'];

export function normalizeValidationBaseUrl(raw?: string | null): string {
  const trimmed = (raw || '').trim().replace(/\/+$/, '');
  if (!trimmed || LEGACY_DOMAINS.some(d => trimmed.includes(d))) {
    return DEFAULT_VALIDATION_BASE_URL;
  }
  return trimmed;
}
