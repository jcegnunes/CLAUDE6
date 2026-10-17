import { describe, expect, it } from 'vitest';
import { evaluateDielectricTest, findMatchingCriterion, getNormPresetForEquipment, isOldProbeNorm, PROBE_NORM_ID } from '../normsEngine';
import { INITIAL_NORMS } from '../../data/seedData';

const evalProbe = (kv: number, mA: number, withstand = true) =>
  evaluateDielectricTest({
    equipmentType: 'ponteira_prova',
    dielectricClass: '0',
    appliedVoltage_kV: kv,
    voltageType: 'AC',
    durationSeconds: 60,
    measuredLeakageCurrent_mA: mA,
    withstandWithoutPuncture: withstand,
    visualChecklist: [],
    allCriteria: INITIAL_NORMS
  });

describe('Ponteiras de prova e cabos (até 1 kV): ABNT NBR 6881 a 1,2 kV', () => {
  it('norma oficial e predefinição: 1,2 kV, 60 s, 5,0 mA adotado pelo laboratório', () => {
    const n = INITIAL_NORMS.find(x => x.id === PROBE_NORM_ID)!;
    expect(n.normCode).toBe('ABNT NBR 6881');
    expect(n.testVoltage_kV).toBe(1.2);
    expect(n.maxLeakageCurrent).toBe(5);
    expect(n.notes).toContain('20 %');
    expect(n.notes).toContain('adotado pelo laboratório');
    const p = getNormPresetForEquipment('ponteira_prova');
    expect(p.testVoltage_kV).toBe(1.2);
    expect(p.maxLeakageCurrent).toBe(5);
  });

  it('o ensaio encontra o critério e cita a norma e o limite adotado', () => {
    expect(findMatchingCriterion('ponteira_prova', '0', INITIAL_NORMS)?.id).toBe(PROBE_NORM_ID);
    const ok = evalProbe(1.2, 0.8);
    expect(ok.result).toBe('APROVADO');
    expect(ok.rationale).toContain('sem perfuração');
    expect(ok.rationale).toContain('20 %');
    expect(ok.rationale).toContain('adotado pelo laboratório');
    expect(ok.normReferenceClause).toContain('item 4.3');
  });

  it('reprova com perfuração, tensão abaixo de 1,2 kV ou fuga acima de 5,0 mA', () => {
    expect(evalProbe(1.2, 0.8, false).result).toBe('REPROVADO');
    expect(evalProbe(1.0, 0.8).result).toBe('REPROVADO');
    const leak = evalProbe(1.2, 5.5);
    expect(leak.result).toBe('REPROVADO');
    expect(leak.rationale).toContain('não estipula corrente de fuga');
  });

  it('atualiza só as normas com valores anteriores', () => {
    expect(isOldProbeNorm({ applicableEquipmentTypes: ['ponteira_prova'], normCode: 'IEC 61010-031', testVoltage_kV: 5, maxLeakageCurrent: 1 })).toBe(true);
    expect(isOldProbeNorm({ applicableEquipmentTypes: ['ponteira_prova'], normCode: 'ABNT NBR 6881', testVoltage_kV: 2.5, maxLeakageCurrent: 1 })).toBe(true);
    expect(isOldProbeNorm({ applicableEquipmentTypes: ['ponteira_prova'], normCode: 'ABNT NBR 6881', testVoltage_kV: 3.5, maxLeakageCurrent: 2 })).toBe(false);
    expect(isOldProbeNorm({ applicableEquipmentTypes: ['ponteira_prova'], normCode: 'ABNT NBR 6881', testVoltage_kV: 1.2, maxLeakageCurrent: 5 })).toBe(false);
    expect(isOldProbeNorm({ applicableEquipmentTypes: ['luva_isolante'], normCode: 'IEC 61010-031', testVoltage_kV: 5, maxLeakageCurrent: 1 })).toBe(false);
  });
});
