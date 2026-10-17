import { describe, expect, it } from 'vitest';
import {
  evaluateDielectricTest,
  getClosestPoleDiameter,
  getNBR11854LeakageLimit_uA,
  getNormPresetForEquipment,
  isOldPoleNorm,
  poleNormFields,
  POLE_NORM_ID
} from '../normsEngine';
import { INITIAL_NORMS } from '../../data/seedData';
import type { EquipmentType } from '../../types';

const poleNorm = INITIAL_NORMS.find(n => n.id === POLE_NORM_ID)!;

const evalPole = (type: EquipmentType, diameter: number | undefined, measured_uA: number, kv = 50) =>
  evaluateDielectricTest({
    equipmentType: type,
    dielectricClass: 'Geral',
    appliedVoltage_kV: kv,
    voltageType: 'AC',
    durationSeconds: 60,
    measuredLeakageCurrent_mA: measured_uA,
    withstandWithoutPuncture: true,
    visualChecklist: [],
    allCriteria: INITIAL_NORMS,
    poleDiameter_mm: diameter
  });

describe('NBR 11854 — bastões, varas telescópicas e bastões de resgate (50 kV)', () => {
  it('tabela de corrente de fuga a 50 kV pelo diâmetro', () => {
    expect([32, 38, 51, 64].map(d => getNBR11854LeakageLimit_uA(d))).toEqual([5, 6, 8, 10]);
  });

  it('diâmetro fora da tabela usa o mais próximo; sem diâmetro, 32 mm (mais exigente)', () => {
    expect(getClosestPoleDiameter(40)).toBe(38);
    expect(getClosestPoleDiameter(60)).toBe(64);
    expect(getClosestPoleDiameter(undefined)).toBe(32);
    expect(getNBR11854LeakageLimit_uA(undefined)).toBe(5);
  });

  it('norma oficial: 50 kV, limite em µA', () => {
    expect(poleNorm.normCode).toBe('ABNT NBR 11854');
    expect(poleNorm.testVoltage_kV).toBe(50);
    expect(poleNorm.currentUnit).toBe('uA');
    expect(poleNorm.applicableEquipmentTypes).toEqual(['bastao_manobra', 'vara_manobra']);
    for (const t of ['bastao_manobra', 'vara_manobra'] as EquipmentType[]) {
      const p = getNormPresetForEquipment(t);
      expect(p?.testVoltage_kV).toBe(50);
      expect(p?.currentUnit).toBe('uA');
      expect(p?.normCode).toBe('ABNT NBR 11854');
    }
  });

  it('aplica o limite do diâmetro e avalia em µA', () => {
    const ok = evalPole('vara_manobra', 51, 7.9);
    expect(ok.appliedLimit).toBe(8);
    expect(ok.appliedUnit).toBe('uA');
    expect(ok.result).toBe('APROVADO');
    expect(ok.rationale).toContain('NBR 11854');
    expect(ok.normReferenceClause).toContain('Ø 51 mm');

    const reprov = evalPole('bastao_manobra', 32, 5.5);
    expect(reprov.appliedLimit).toBe(5);
    expect(reprov.result).toBe('REPROVADO');
    expect(reprov.rationale).toContain('5 µA');

    expect(evalPole('bastao_manobra', 64, 10).result).toBe('APROVADO');
  });

  it('tensão abaixo de 50 kV reprova', () => {
    expect(evalPole('bastao_manobra', 38, 2, 40).result).toBe('REPROVADO');
  });

  it('atualiza só a norma antiga de bastão/vara (100 kV / mA)', () => {
    expect(isOldPoleNorm({ applicableEquipmentTypes: ['bastao_manobra', 'vara_manobra'], testVoltage_kV: 100, currentUnit: 'mA' })).toBe(true);
    expect(isOldPoleNorm({ applicableEquipmentTypes: ['vara_manobra'], testVoltage_kV: 75, currentUnit: 'mA' })).toBe(false);
    expect(isOldPoleNorm({ applicableEquipmentTypes: ['bastao_manobra'], testVoltage_kV: 50, currentUnit: 'uA' })).toBe(false);
    expect(isOldPoleNorm({ applicableEquipmentTypes: ['luva_isolante'], testVoltage_kV: 100, currentUnit: 'mA' })).toBe(false);
    const updated = { ...poleNorm, testVoltage_kV: 100, currentUnit: 'mA' as const, maxLeakageCurrent: 0.1, ...poleNormFields(poleNorm) };
    expect(updated.testVoltage_kV).toBe(50);
    expect(updated.maxLeakageCurrent).toBe(5);
    expect(updated.currentUnit).toBe('uA');
  });
});
