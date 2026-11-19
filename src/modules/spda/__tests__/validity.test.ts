import { describe, expect, it } from 'vitest';
import { addMonths, nextInspectionDate, suggestRecommendations, validityMonths, validityText } from '../rules';

describe('Validade do laudo (12, 24 ou 36 meses)', () => {
  it('automático: 12 meses', () => {
    expect(validityMonths({ criticalStructure: false })).toBe(12);
    expect(validityMonths({ criticalStructure: true })).toBe(12);
    expect(nextInspectionDate({ inspectionDate: '2026-11-16', criticalStructure: false })).toBe('2027-11-16');
  });

  it('escolhida no relatório vale para a próxima inspeção e os textos', () => {
    const v = { inspectionDate: '2026-11-16', criticalStructure: false, validityMonths: 24 as const };
    expect(validityMonths(v)).toBe(24);
    expect(validityText(v)).toBe('24 meses (2 anos)');
    expect(validityText({ criticalStructure: false, validityMonths: 12 })).toBe('12 meses (1 ano)');
    expect(nextInspectionDate(v)).toBe('2028-11-16');
    expect(nextInspectionDate({ ...v, criticalStructure: true })).toBe('2028-11-16');
    expect(suggestRecommendations({ checklist: [], continuity: [], ...v })).toMatch(/em até 24 meses \(2 anos\)/);
  });

  it('soma de meses respeita o fim do mês', () => {
    expect(addMonths('2028-02-29', 12)).toBe('2029-02-28');
    expect(addMonths('2026-01-31', 1)).toBe('2026-02-28');
  });
});
