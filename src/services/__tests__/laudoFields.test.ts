import { describe, expect, it } from 'vitest';
import { isGloveTest } from '../nbr16295Service';
import { technicianCrea } from '../../utils/technicianCrea';
import { parseDecimal } from '../../components/DecimalInput';
import { lastTestEnvironment } from '../../views/TestWizardView';

describe('Laudo: tabela da NBR 16295 só para luvas', () => {
  it('detector, bastão e demais tipos não são luva, mesmo com comprimento preenchido (ensaios do Android)', () => {
    expect(isGloveTest({ equipmentType: 'luva_isolante' })).toBe(true);
    expect(isGloveTest({ equipmentType: 'detector_inducao', gloveLength_mm: 360 })).toBe(false);
    expect(isGloveTest({ equipmentType: 'bastao_manobra', gloveLength_mm: 38 })).toBe(false);
    // registro antigo sem tipo: o comprimento ainda indica luva
    expect(isGloveTest({ gloveLength_mm: 360 })).toBe(true);
    expect(isGloveTest(null)).toBe(false);
  });
});

describe('CREA/CFT do técnico executor', () => {
  it('em branco ou com o número de exemplo antigo não aparece', () => {
    expect(technicianCrea('')).toBe('');
    expect(technicianCrea('   ')).toBe('');
    expect(technicianCrea(undefined)).toBe('');
    expect(technicianCrea('CFT-SP 1458920')).toBe('');
    expect(technicianCrea(' CREA-PR 12345 ')).toBe('CREA-PR 12345');
  });
});

describe('Campos decimais', () => {
  it('vazio fica vazio (sem virar 0 no campo) e aceita vírgula', () => {
    expect(Number.isNaN(parseDecimal(''))).toBe(true);
    expect(Number.isNaN(parseDecimal('-'))).toBe(true);
    expect(parseDecimal('23,5')).toBe(23.5);
    expect(parseDecimal('0.8')).toBe(0.8);
    expect(parseDecimal('12.')).toBe(12);
  });
});

describe('Temperatura e umidade do ensaio anterior', () => {
  it('pega o ensaio mais recente que tem condições ambientais', () => {
    const tests: any[] = [
      { createdAt: '2026-10-10T10:00:00Z', environmental: { temperatureC: 21, relativeHumidityPercent: 60 } },
      { createdAt: '2026-10-16T09:00:00Z', environmental: { temperatureC: 25.5, relativeHumidityPercent: 48 } },
      { createdAt: '2026-10-17T09:00:00Z' }
    ];
    expect(lastTestEnvironment(tests)).toEqual({ temperatureC: 25.5, relativeHumidityPercent: 48 });
    expect(lastTestEnvironment([])).toBeUndefined();
  });
});
