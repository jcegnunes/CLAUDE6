import { describe, expect, it } from 'vitest';
import { evaluateDielectricTest, findMatchingCriterion, getNormPresetForEquipment, isOldProbeNorm, PROBE_NORM_ID } from '../normsEngine';
import { INITIAL_NORMS } from '../../data/seedData';

const evalProbe = (kv: number, mA: number) =>
  evaluateDielectricTest({
    equipmentType: 'ponteira_prova',
    dielectricClass: '0',
    appliedVoltage_kV: kv,
    voltageType: 'AC',
    durationSeconds: 60,
    measuredLeakageCurrent_mA: mA,
    withstandWithoutPuncture: true,
    visualChecklist: [],
    allCriteria: INITIAL_NORMS
  });

describe('Ponteiras de prova e cabos: ABNT NBR 6881 a 2,5 kV', () => {
  it('norma oficial e predefinição', () => {
    const n = INITIAL_NORMS.find(x => x.id === PROBE_NORM_ID)!;
    expect(n.normCode).toBe('ABNT NBR 6881');
    expect(n.testVoltage_kV).toBe(2.5);
    const p = getNormPresetForEquipment('ponteira_prova');
    expect(p.normCode).toBe('ABNT NBR 6881');
    expect(p.testVoltage_kV).toBe(2.5);
  });

  it('o ensaio encontra o critério (antes ficava "não configurado")', () => {
    expect(findMatchingCriterion('ponteira_prova', '0', INITIAL_NORMS)?.id).toBe(PROBE_NORM_ID);
    expect(evalProbe(2.5, 0.4).result).toBe('APROVADO');
    expect(evalProbe(2.0, 0.4).result).toBe('REPROVADO');
  });

  it('atualiza só a norma criada pela predefinição antiga (IEC 61010-031 a 5 kV)', () => {
    expect(isOldProbeNorm({ applicableEquipmentTypes: ['ponteira_prova'], normCode: 'IEC 61010-031', testVoltage_kV: 5 })).toBe(true);
    expect(isOldProbeNorm({ applicableEquipmentTypes: ['ponteira_prova'], normCode: 'IEC 61010-031', testVoltage_kV: 3 })).toBe(false);
    expect(isOldProbeNorm({ applicableEquipmentTypes: ['luva_isolante'], normCode: 'IEC 61010-031', testVoltage_kV: 5 })).toBe(false);
  });
});
