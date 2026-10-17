import { describe, expect, it } from 'vitest';
import { evaluateDielectricTest, getNormPresetForEquipment, isOldFootwearNorm, FOOTWEAR_NORM_ID } from '../normsEngine';
import { INITIAL_NORMS } from '../../data/seedData';
import { formatGaugeValue, leakageGaugeScale } from '../../utils/gaugeUtils';

const evalShoe = (measured_mA: number) =>
  evaluateDielectricTest({
    equipmentType: 'bota_dielétrica',
    dielectricClass: 'Especial',
    appliedVoltage_kV: 14,
    voltageType: 'AC',
    durationSeconds: 60,
    measuredLeakageCurrent_mA: measured_mA,
    withstandWithoutPuncture: true,
    visualChecklist: [],
    allCriteria: INITIAL_NORMS
  });

describe('Calçado de segurança dielétrico: corrente de fuga máxima 0,5 mA', () => {
  it('norma oficial e predefinição com 0,5 mA', () => {
    const n = INITIAL_NORMS.find(x => x.id === FOOTWEAR_NORM_ID)!;
    expect(n.maxLeakageCurrent).toBe(0.5);
    expect(n.currentUnit).toBe('mA');
    expect(n.notes).toContain('0,5 mA');
    expect(getNormPresetForEquipment('bota_dielétrica').maxLeakageCurrent).toBe(0.5);
  });

  it('aprova até 0,5 mA e reprova acima', () => {
    expect(evalShoe(0.5).result).toBe('APROVADO');
    expect(evalShoe(0.5).appliedLimit).toBe(0.5);
    expect(evalShoe(0.6).result).toBe('REPROVADO');
  });

  it('medidor: escala pequena para limite pequeno; escalas antigas mantidas', () => {
    expect(leakageGaugeScale(0.5, 0.3)).toBe(1);
    expect(leakageGaugeScale(0.5, 1.2)).toBe(2);
    expect(leakageGaugeScale(5, 5.6)).toBe(10);
    expect(leakageGaugeScale(9, 11.2)).toBe(15);
    expect(leakageGaugeScale(18, 3)).toBe(25);
    expect(formatGaugeValue(0.35)).toBe('0.35');
    expect(formatGaugeValue(11.2)).toBe('11.2');
  });

  it('atualiza só a norma ainda com 3,5 mA', () => {
    expect(isOldFootwearNorm({ applicableEquipmentTypes: ['bota_dielétrica'], maxLeakageCurrent: 3.5, currentUnit: 'mA' })).toBe(true);
    expect(isOldFootwearNorm({ applicableEquipmentTypes: ['bota_dielétrica'], maxLeakageCurrent: 2, currentUnit: 'mA' })).toBe(false);
    expect(isOldFootwearNorm({ applicableEquipmentTypes: ['luva_isolante'], maxLeakageCurrent: 3.5, currentUnit: 'mA' })).toBe(false);
  });
});
