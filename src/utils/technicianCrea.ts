/**
 * CREA/CFT do técnico executor para o laudo: em branco não aparece. Ensaios
 * antigos gravavam um número de exemplo quando o técnico não tinha registro;
 * esses valores também são tratados como em branco.
 */
const PLACEHOLDERS = ['CFT-SP 1458920', 'CFT-SP 123456'];

export function technicianCrea(value?: string | null): string {
  const v = String(value ?? '').trim();
  if (!v || PLACEHOLDERS.includes(v)) return '';
  return v;
}
