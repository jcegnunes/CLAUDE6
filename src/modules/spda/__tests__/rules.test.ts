import { describe, expect, it } from 'vitest';
import { CHECKLIST_TEMPLATE, SUBSYSTEM_ORDER } from '../checklist';
import {
  addYears, aboveDesign, continuityStatus, currentOutOfRange, effectiveLimit, fallOfPotentialPlateau, groundingStatus,
  groundingValue, inspectionVerdict, listNonConformities, nextInspectionDate, nextNumber, parseDecimal, suggestRecommendations,
  theoreticalResistance
} from '../rules';
import type { ChecklistAnswer, ContinuityTest, GroundingTest } from '../types';

const allC = (): ChecklistAnswer[] => CHECKLIST_TEMPLATE.map(i => ({ itemId: i.id, status: 'C' }));
const cont = (p: Partial<ContinuityTest>): ContinuityTest => ({ id: 'c', kind: 'F4', subsystem: 'captacao', from: 'Captação', to: 'BEP', ...p });
const ground = (p: Partial<GroundingTest>): GroundingTest => ({ id: 'g', point: 'D1', method: 'queda_potencial', ...p });

describe('Continuidade — NBR 5419-3:2026, Anexo F', () => {
  it('comprobatória captação ↔ BEP: no máximo 0,2 Ω (F.4.4)', () => {
    expect(continuityStatus(cont({ resistanceOhm: 0.2 }))).toBe('conforme');
    expect(continuityStatus(cont({ resistanceOhm: 0.21 }))).toBe('nao_conforme');
    expect(continuityStatus(cont({ resistanceOhm: null }))).toBe('pendente');
  });

  it('1ª verificação das armaduras (F.1 a F.3): nenhum valor maior que 1 Ω, limite fixo da norma', () => {
    for (const kind of ['F1', 'F2', 'F3'] as const) {
      expect(continuityStatus(cont({ kind, resistanceOhm: 1 }))).toBe('conforme');
      expect(continuityStatus(cont({ kind, resistanceOhm: 1.05 }))).toBe('nao_conforme');
      // o limite digitado não substitui o da norma
      expect(effectiveLimit(cont({ kind, limitOhm: 5 }))).toBe(1);
    }
  });

  it('não naturais (F.5, F.6, EQ): sem limite adotado fica pendente; com limite, compara', () => {
    expect(continuityStatus(cont({ kind: 'F5', resistanceOhm: 0.05 }))).toBe('pendente');
    expect(continuityStatus(cont({ kind: 'F6', resistanceOhm: 0.05, limitOhm: 0.04 }))).toBe('nao_conforme');
    expect(continuityStatus(cont({ kind: 'EQ', resistanceOhm: 0.03, limitOhm: 0.04 }))).toBe('conforme');
  });

  it('resistência teórica ρ·L/S do condutor (cobre 35 mm², 20 m ≈ 9,85 mΩ)', () => {
    expect(theoreticalResistance({ material: 'cobre', lengthM: 20, sectionMm2: 35 })).toBeCloseTo(0.00985, 5);
    expect(theoreticalResistance({ material: 'aco_zincado', lengthM: 10, sectionMm2: 50 })).toBeCloseTo(0.0276, 4);
    expect(theoreticalResistance({ material: 'cobre', lengthM: 20 })).toBeNull();
  });

  it('corrente de ensaio entre 1 A e 5 A (F.1.3.1)', () => {
    expect(currentOutOfRange({ currentA: 0.5 })).toBe(true);
    expect(currentOutOfRange({ currentA: 10 })).toBe(true);
    expect(currentOutOfRange({ currentA: 1 })).toBe(false);
    expect(currentOutOfRange({ currentA: null })).toBe(false);
  });

  it('registros antigos sem tipo são lidos como comprobatória (0,2 Ω)', () => {
    const old = { id: 'x', subsystem: 'descida', from: 'A', to: 'B', resistanceOhm: 0.3, limitOhm: 0.2 } as unknown as ContinuityTest;
    expect(continuityStatus(old)).toBe('nao_conforme');
  });
});

describe('Resistência de aterramento — informativa (7.1.4)', () => {
  it('usa o valor informado ou a leitura a 62 %', () => {
    expect(groundingValue(ground({ r62: 7.4 }))).toBe(7.4);
    expect(groundingValue(ground({ r62: 7.4, resistanceOhm: 8 }))).toBe(8);
    expect(groundingValue(ground({ method: 'alicate', r62: 7.4 }))).toBeNull();
  });

  it('nunca reprova: informativo ou pendente; acima do projeto é só observação', () => {
    expect(groundingStatus(ground({ r62: 50, designOhm: 10 }))).toBe('informativo');
    expect(aboveDesign(ground({ r62: 50, designOhm: 10 }))).toBe(true);
    expect(aboveDesign(ground({ r62: 5, limitOhm: 10 }))).toBe(false);
    expect(groundingStatus(ground({}))).toBe('pendente');
  });

  it('patamar da queda de potencial (±5 % da leitura a 62 %)', () => {
    expect(fallOfPotentialPlateau({ r52: 9.7, r62: 10, r72: 10.4 })!.ok).toBe(true);
    expect(fallOfPotentialPlateau({ r52: 9, r62: 10, r72: 10.2 })!.ok).toBe(false);
    expect(fallOfPotentialPlateau({ r62: 10 })).toBeNull();
  });
});

describe('Parecer e não conformidades', () => {
  const ok = { checklist: allC(), continuity: [cont({ resistanceOhm: 0.1 })] };

  it('tudo conforme → conforme; aterramento alto não muda o parecer', () => {
    expect(inspectionVerdict(ok)).toBe('conforme');
    expect(listNonConformities(ok)).toEqual([]);
  });

  it('item NC no checklist → não conforme, com a observação', () => {
    const checklist = allC().map(a => (a.itemId === 'ATE-04' ? { ...a, status: 'NC' as const, obs: 'Caixa D3 obstruída' } : a));
    const ncs = listNonConformities({ ...ok, checklist });
    expect(inspectionVerdict({ ...ok, checklist })).toBe('nao_conforme');
    expect(ncs).toHaveLength(1);
    expect(ncs[0]).toMatchObject({ source: 'inspecao', subsystem: 'aterramento', obs: 'Caixa D3 obstruída' });
    expect(ncs[0].reference).toMatch(/5419-3:2026, 5\.6\.3\.3/);
  });

  it('continuidade fora do critério entra nas NCs com a referência do Anexo F', () => {
    const ncs = listNonConformities({ ...ok, continuity: [cont({ kind: 'F1', resistanceOhm: 1.4 })] });
    expect(ncs).toHaveLength(1);
    expect(ncs[0].reference).toMatch(/Anexo F \(F\.1\)/);
  });

  it('NA conta como respondido; item em branco ou ensaio pendente → incompleto; sem ensaios é aceito', () => {
    expect(inspectionVerdict({ ...ok, checklist: allC().map(a => (a.itemId === 'CAP-10' ? { ...a, status: 'NA' as const } : a)) })).toBe('conforme');
    expect(inspectionVerdict({ ...ok, checklist: allC().slice(1) })).toBe('incompleto');
    expect(inspectionVerdict({ ...ok, continuity: [cont({ kind: 'F5', resistanceOhm: 0.01 })] })).toBe('incompleto');
    expect(inspectionVerdict({ ...ok, continuity: [] })).toBe('conforme');
  });

  it('NC prevalece sobre incompleto', () => {
    expect(inspectionVerdict({ checklist: [], continuity: [cont({ resistanceOhm: 1 })] })).toBe('nao_conforme');
  });

  it('recomendações citam prazo (7.4.2), Anexo F e periodicidade', () => {
    const checklist = allC().map(a => (a.itemId === 'DES-07' ? { ...a, status: 'NC' as const } : a));
    const txt = suggestRecommendations({ checklist, continuity: [cont({ resistanceOhm: 0.5 })], criticalStructure: true });
    expect(txt).toMatch(/7\.4\.2/);
    expect(txt).toMatch(/Anexo F/);
    expect(txt).toMatch(/1 ano/);
  });
});

describe('Periodicidade (7.3.2 f) e numeração', () => {
  it('1 ano para estrutura crítica, 3 anos nas demais', () => {
    expect(nextInspectionDate({ inspectionDate: '2026-11-09', criticalStructure: true })).toBe('2027-11-09');
    expect(nextInspectionDate({ inspectionDate: '2026-11-09', criticalStructure: false })).toBe('2029-11-09');
  });
  it('29/02 vira 28/02 em ano não bissexto', () => {
    expect(addYears('2028-02-29', 1)).toBe('2029-02-28');
  });
  it('número sequencial por ano', () => {
    expect(nextNumber([], 2026)).toBe('SPDA-2026-0001');
    expect(nextNumber(['SPDA-2026-0001', 'SPDA-2026-0007', 'SPDA-2025-0099'], 2026)).toBe('SPDA-2026-0008');
  });
  it('aceita vírgula decimal', () => {
    expect(parseDecimal('0,15')).toBe(0.15);
    expect(parseDecimal(' ')).toBeNull();
    expect(parseDecimal('abc')).toBeNull();
  });
});

describe('Checklist NBR 5419:2026', () => {
  it('ids únicos, todos os blocos presentes e toda referência citando a edição 2026', () => {
    const ids = CHECKLIST_TEMPLATE.map(i => i.id);
    expect(new Set(ids).size).toBe(ids.length);
    expect(new Set(CHECKLIST_TEMPLATE.map(i => i.subsystem))).toEqual(new Set(SUBSYSTEM_ORDER));
    CHECKLIST_TEMPLATE.forEach(i => expect(i.reference).toMatch(/NBR 5419-[34]:2026/));
  });
});

describe('Exibição da continuidade em mΩ', () => {
  it('converte Ω ↔ mΩ sem erro de arredondamento e formata', async () => {
    const { toMilli, fromMilli, fmtMilliOhm } = await import('../rules');
    expect(toMilli(0.0853)).toBe(85.3);
    expect(fromMilli(85.3)).toBe(0.0853);
    expect(toMilli(null)).toBeNull();
    expect(fmtMilliOhm(0.2)).toBe('200 mΩ');
    expect(fmtMilliOhm(0.0853)).toBe('85,3 mΩ');
    expect(fmtMilliOhm(undefined)).toBe('—');
  });
});
