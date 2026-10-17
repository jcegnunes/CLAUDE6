import { describe, expect, it } from 'vitest';
import { evaluateDielectricTest } from '../normsEngine';
import { INITIAL_NORMS } from '../../data/seedData';

// Escada isolante: limite de fuga = quantidade de pontos ensaiados × limite da norma da escada
const evalLadder = (measured_mA: number, ladderTestPoints?: number, wet = false) =>
  evaluateDielectricTest({
    equipmentType: 'escada_isolada',
    dielectricClass: 'Geral',
    appliedVoltage_kV: 100,
    voltageType: 'AC',
    durationSeconds: 60,
    measuredLeakageCurrent_mA: measured_mA,
    withstandWithoutPuncture: true,
    visualChecklist: [],
    allCriteria: INITIAL_NORMS,
    customNormCriterionId: 'norm-escada-nbr-iec-61478-100kv',
    ladderTestMethod: 'segmento_300mm_100kv',
    ladderMoistureConditioned: wet,
    ladderTestPoints
  });

describe('Escada isolante — quantidade de pontos ensaiados', () => {
  it('sem informar conta como 1 ponto (limite da norma)', () => {
    expect(evalLadder(0.05).appliedLimit).toBeCloseTo(0.1);
    expect(evalLadder(0.05, 0).appliedLimit).toBeCloseTo(0.1);
  });

  it('limite = pontos × limite da norma, e o parecer cita a conta', () => {
    const ok = evalLadder(0.35, 4);
    expect(ok.appliedLimit).toBeCloseTo(0.4);
    expect(ok.result).toBe('APROVADO');
    expect(ok.rationale).toContain('4 pontos ensaiados × 0.10 mA');
    const fail = evalLadder(0.45, 4);
    expect(fail.result).toBe('REPROVADO');
    expect(fail.rationale).toContain('4 pontos ensaiados');
  });

  it('com condicionamento de umidade multiplica o limite úmido', () => {
    expect(evalLadder(0.1, 3, true).appliedLimit).toBeCloseTo(0.6);
  });
});
