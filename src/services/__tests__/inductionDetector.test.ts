import { describe, expect, it } from 'vitest';
import {
  defaultInductionPoints,
  evaluateInductionDetector,
  fmtBr1,
  INDUCTION_DETECTOR_VOLTAGES_KV,
  normalizeInductionPoints
} from '../inductionDetectorService';
import { evaluateDielectricTest, getDefaultChecklistForEquipment } from '../normsEngine';
import { INITIAL_NORMS } from '../../data/seedData';
import { inductionChartPoints, inductionChartXMax } from '../../utils/inductionChart';

// valores do modelo (distâncias obtidas)
const SAMPLE_CM = [5, 15, 25, 35, 45, 60, 75, 90, 100, 115];
const filled = () => INDUCTION_DETECTOR_VOLTAGES_KV.map((v, i) => ({ voltage_kV: v, distance_cm: SAMPLE_CM[i], detected: true }));

describe('Detector de tensão por indução (1 kV a 50 kV)', () => {
  it('tabela com as 11 tensões fixas e distâncias em branco', () => {
    const pts = defaultInductionPoints();
    expect(pts.map(p => p.voltage_kV)).toEqual([1, 2, 5, 8, 13.8, 19, 24, 34.5, 43, 50]);
    expect(pts.every(p => p.distance_cm === undefined && p.detected === true)).toBe(true);
    expect(fmtBr1(13.8)).toBe('13,8');
    expect(fmtBr1(5)).toBe('5,0');
  });

  it('completa e ordena a tabela gravada; ignora distância inválida', () => {
    const pts = normalizeInductionPoints([{ voltage_kV: 50, distance_cm: 115 }, { voltage_kV: 1, distance_cm: -3 }]);
    expect(pts).toHaveLength(10);
    expect(pts[0]).toEqual({ voltage_kV: 1, distance_cm: undefined, detected: true });
    expect(pts[9].distance_cm).toBe(115);
    // ensaio gravado com a tabela antiga (até 60 kV) mantém as tensões dele
    const old = normalizeInductionPoints([{ voltage_kV: 52, distance_cm: 110 }, { voltage_kV: 60, distance_cm: 120 }, { voltage_kV: 1, distance_cm: 5 }]);
    expect(old.map(p => p.voltage_kV)).toEqual([1, 52, 60]);
  });

  it('pendente enquanto faltar distância', () => {
    const ev = evaluateInductionDetector(defaultInductionPoints());
    expect(ev.result).toBe('PENDENTE');
    expect(ev.missing).toHaveLength(10);
    expect(ev.rationale).toContain('Preencha');
  });

  it('aprovado com todas as distâncias: "resultados satisfatórios"', () => {
    const ev = evaluateInductionDetector(filled());
    expect(ev.result).toBe('APROVADO');
    expect(ev.rationale).toContain('Resultados satisfatórios conforme especificações do fabricante');
    expect(ev.rationale).toContain('5,0 a 115,0 cm');
    expect(ev.rationale).toContain('1,0 a 50,0 kV');
    expect(ev.warnings).toHaveLength(0);
  });

  it('reprova se não detectar em alguma tensão', () => {
    const pts = filled();
    pts[9] = { voltage_kV: 50, distance_cm: undefined, detected: false };
    const ev = evaluateInductionDetector(pts);
    expect(ev.result).toBe('REPROVADO');
    expect(ev.notDetected).toEqual([50]);
    expect(ev.rationale).toContain('50,0 kV');
  });

  it('distância que não cresce com a tensão é aviso, não reprovação', () => {
    const pts = filled();
    pts[5] = { ...pts[5], distance_cm: 40 }; // 19 kV menor que 13,8 kV
    const ev = evaluateInductionDetector(pts);
    expect(ev.result).toBe('APROVADO');
    expect(ev.warnings[0]).toContain('19,0 kV');
  });

  it('motor de normas: norma oficial, checklist próprio e avaliação pela tabela', () => {
    expect(INITIAL_NORMS.some(n => n.applicableEquipmentTypes.includes('detector_inducao'))).toBe(true);
    expect(getDefaultChecklistForEquipment('detector_inducao')[1].item).toContain('Autoteste');
    const base = {
      equipmentType: 'detector_inducao' as const,
      dielectricClass: 'Geral',
      appliedVoltage_kV: 0,
      voltageType: 'AC' as const,
      durationSeconds: 0,
      measuredLeakageCurrent_mA: 0,
      withstandWithoutPuncture: true,
      visualChecklist: getDefaultChecklistForEquipment('detector_inducao'),
      allCriteria: INITIAL_NORMS
    };
    expect(evaluateDielectricTest({ ...base, inductionPoints: filled() }).result).toBe('APROVADO');
    expect(evaluateDielectricTest({ ...base, inductionPoints: defaultInductionPoints() }).result).toBe('PENDENTE');
    const visualFail = base.visualChecklist.map((c, i) => (i === 0 ? { ...c, status: 'nao_conforme' as const } : c));
    expect(evaluateDielectricTest({ ...base, visualChecklist: visualFail, inductionPoints: filled() }).result).toBe('REPROVADO');
  });

  it('gráfico: pontos ordenados pela tensão; eixo X de 140 cm (ou mais)', () => {
    expect(inductionChartPoints(filled())[0]).toEqual({ x: 5, y: 1 });
    expect(inductionChartPoints(filled())).toHaveLength(10);
    expect(inductionChartXMax(filled())).toBe(140);
    expect(inductionChartXMax([{ voltage_kV: 60, distance_cm: 150 }])).toBe(180);
  });
});
