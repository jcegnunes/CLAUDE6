import { describe, expect, it } from 'vitest';
import {
  evaluateDielectricTest,
  findMatchingCriterion,
  getDefaultChecklistForEquipment,
  getNormPresetForEquipment,
  POLE_NORM_ID,
  PROBE_NORM_ID
} from '../normsEngine';
import { INITIAL_NORMS } from '../../data/seedData';
import type { EquipmentType } from '../../types';

const base = (type: EquipmentType, kv: number, measured: number, extra: object = {}) =>
  evaluateDielectricTest({
    equipmentType: type,
    dielectricClass: 'Geral',
    appliedVoltage_kV: kv,
    voltageType: 'AC',
    durationSeconds: 60,
    measuredLeakageCurrent_mA: measured,
    withstandWithoutPuncture: true,
    visualChecklist: [],
    allCriteria: INITIAL_NORMS,
    ...extra
  });

describe('Aterramento temporário com bastão — critério da vara de manobra (NBR 11854)', () => {
  it('usa o critério de bastão/vara da lista de normas', () => {
    expect(findMatchingCriterion('aterramento_com_bastao', 'Geral', INITIAL_NORMS)?.id).toBe(POLE_NORM_ID);
    expect(getNormPresetForEquipment('aterramento_com_bastao').testVoltage_kV).toBe(50);
  });

  it('limite em µA pelo diâmetro do bastão e pela quantidade de pontos', () => {
    const ok = base('aterramento_com_bastao', 50, 11, { poleDiameter_mm: 38, poleTestPoints: 2 });
    expect(ok.appliedLimit).toBe(12);
    expect(ok.appliedUnit).toBe('uA');
    expect(ok.result).toBe('APROVADO');
    expect(ok.rationale).toContain('aterramento temporário com bastão');
    expect(base('aterramento_com_bastao', 50, 13, { poleDiameter_mm: 38, poleTestPoints: 2 }).result).toBe('REPROVADO');
  });

  it('checklist inclui o bastão', () => {
    expect(getDefaultChecklistForEquipment('aterramento_com_bastao').some(i => /Bastão isolante/.test(i.item))).toBe(true);
  });
});

describe('Aterramento temporário sem bastão — critério da ponteira de prova (NBR 6881)', () => {
  it('usa o critério de ponteira de prova da lista de normas', () => {
    expect(findMatchingCriterion('aterramento_sem_bastao', 'Geral', INITIAL_NORMS)?.id).toBe(PROBE_NORM_ID);
    expect(getNormPresetForEquipment('aterramento_sem_bastao').testVoltage_kV).toBe(1.2);
  });

  it('1,2 kV com limite de 5,0 mA', () => {
    const ok = base('aterramento_sem_bastao', 1.2, 2);
    expect(ok.appliedLimit).toBe(5);
    expect(ok.result).toBe('APROVADO');
    expect(ok.rationale).toContain('aterramento temporário sem bastão');
    expect(base('aterramento_sem_bastao', 1.2, 6).result).toBe('REPROVADO');
  });

  it('checklist não inclui bastão', () => {
    expect(getDefaultChecklistForEquipment('aterramento_sem_bastao').some(i => /Bastão/.test(i.item))).toBe(false);
  });
});
