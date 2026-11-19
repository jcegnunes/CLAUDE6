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

  it('critério único ≤ 200 mΩ para qualquer medição (o tipo só indica a tela)', () => {
    for (const kind of ['F1', 'F2', 'F3', 'F4', 'F5', 'F6', 'EQ'] as const) {
      expect(continuityStatus(cont({ kind, resistanceOhm: 0.2 }))).toBe('conforme');
      expect(continuityStatus(cont({ kind, resistanceOhm: 0.201 }))).toBe('nao_conforme');
      // limite antigo gravado no registro não muda o critério
      expect(effectiveLimit(cont({ kind, limitOhm: 5 }))).toBe(0.2);
    }
  });

  it('sem valor medido fica pendente; com valor, sempre avaliado (não depende de limite adotado)', () => {
    expect(continuityStatus(cont({ kind: 'F5', resistanceOhm: null }))).toBe('pendente');
    expect(continuityStatus(cont({ kind: 'F6', resistanceOhm: 0.05 }))).toBe('conforme');
    expect(continuityStatus(cont({ kind: 'EQ', resistanceOhm: 0.35 }))).toBe('nao_conforme');
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

describe('Migração da numeração do checklist', () => {
  it('converte códigos da versão 1 e descarta itens retirados', async () => {
    const { migrateChecklistCodes } = await import('../rules');
    const old = { checklist: [
      { itemId: 'CAP-01', buildingId: 'b', status: 'NC' }, { itemId: 'CAP-05', buildingId: 'b', status: 'C' },
      { itemId: 'DES-11', buildingId: 'b', status: 'NA' }, { itemId: 'PES-04', buildingId: 'b', status: 'NC', obs: 'PVC' },
      { itemId: 'PES-01', buildingId: 'b', status: 'C' }, { itemId: 'DOC-03', status: 'C' }, { itemId: 'EQU-15', buildingId: 'b', status: 'C' }
    ] } as unknown as Parameters<typeof migrateChecklistCodes>[0];
    const m = migrateChecklistCodes(old);
    expect(m.checklistVersion).toBe(5);
    // EQU-15 (v1/v2) foi retirado na versão 3
    expect(m.checklist.map(a => a.itemId)).toEqual(['CAP-01', 'DES-08', 'ATE-06', 'DOC-02']); // DOC-03 → DOC-02 na versão 5
    expect(m.checklist[0].status).toBe('C'); // o antigo CAP-01 (retirado) sumiu; o novo CAP-01 é o antigo CAP-05
    expect(migrateChecklistCodes(m)).toBe(m); // já na versão atual: não mexe
  });

  it('versão 4 → 5: documentação reduzida e renumerada', async () => {
    const { migrateChecklistCodes } = await import('../rules');
    const v4 = { checklistVersion: 4, checklist: ['DOC-01', 'DOC-02', 'DOC-05', 'DOC-07', 'DOC-10']
      .map(itemId => ({ itemId, status: 'C' })) } as unknown as Parameters<typeof migrateChecklistCodes>[0];
    expect(migrateChecklistCodes(v4).checklist.map(a => a.itemId)).toEqual(['DOC-01', 'DOC-04', 'DOC-05']);
  });

  it('versão 2 → 4: equipotencialização e DPS reduzidos e renumerados', async () => {
    const { migrateChecklistCodes } = await import('../rules');
    const v2 = { checklistVersion: 2, checklist: ['EQU-01', 'EQU-02', 'EQU-03', 'EQU-09', 'EQU-12', 'EQU-13', 'EQU-15', 'CAP-01']
      .map(itemId => ({ itemId, buildingId: 'b', status: 'C' })) } as unknown as Parameters<typeof migrateChecklistCodes>[0];
    // v2 EQU-01→01, 03→(v3 02)→02, 09→(v3 06)→03, 13→(v3 08)→04; 12→(v3 07) retirado
    expect(migrateChecklistCodes(v2).checklist.map(a => a.itemId)).toEqual(['EQU-01', 'EQU-02', 'EQU-03', 'EQU-04', 'CAP-01']);
    const v3 = { checklistVersion: 3, checklist: ['EQU-01', 'EQU-02', 'EQU-03', 'EQU-04', 'EQU-05', 'EQU-06', 'EQU-07', 'EQU-08']
      .map(itemId => ({ itemId, buildingId: 'b', status: 'C' })) } as unknown as Parameters<typeof migrateChecklistCodes>[0];
    expect(migrateChecklistCodes(v3).checklist.map(a => a.itemId)).toEqual(['EQU-01', 'EQU-02', 'EQU-03', 'EQU-04']);
  });
});

describe('Parecer e não conformidades', () => {
  const ok = { checklist: allC(), continuity: [cont({ resistanceOhm: 0.1 })] };

  it('tudo conforme → conforme; aterramento alto não muda o parecer', () => {
    expect(inspectionVerdict(ok)).toBe('conforme');
    expect(listNonConformities(ok)).toEqual([]);
  });

  it('item NC no checklist → não conforme, com a observação', () => {
    const checklist = allC().map(a => (a.itemId === 'ATE-02' ? { ...a, status: 'NC' as const, obs: 'Caixa D3 obstruída' } : a));
    const ncs = listNonConformities({ ...ok, checklist });
    expect(inspectionVerdict({ ...ok, checklist })).toBe('nao_conforme');
    expect(ncs).toHaveLength(1);
    expect(ncs[0]).toMatchObject({ source: 'inspecao', subsystem: 'aterramento', obs: 'Caixa D3 obstruída' });
    expect(ncs[0].reference).toMatch(/5419-3:2026, 5\.6\.3\.3/);
  });

  it('continuidade fora do critério entra nas NCs com a referência do Anexo F', () => {
    const ncs = listNonConformities({ ...ok, continuity: [cont({ kind: 'F1', resistanceOhm: 0.35 })] });
    expect(ncs).toHaveLength(1);
    expect(ncs[0].reference).toMatch(/Anexo F.*0,2 Ω/);
    expect(ncs[0].description).toMatch(/350 mΩ.*limite 200 mΩ/);
  });

  it('parecer só com o que foi avaliado: item em branco e ensaio sem valor são ignorados', () => {
    expect(inspectionVerdict({ ...ok, checklist: allC().map(a => (a.itemId === 'CAP-06' ? { ...a, status: 'NA' as const } : a)) })).toBe('conforme');
    expect(inspectionVerdict({ ...ok, checklist: allC().slice(1) })).toBe('conforme');
    expect(inspectionVerdict({ ...ok, continuity: [cont({ kind: 'F5', resistanceOhm: null })] })).toBe('conforme');
    expect(inspectionVerdict({ ...ok, continuity: [] })).toBe('conforme');
    // só alguns itens avaliados e um ensaio medido já permitem parecer
    expect(inspectionVerdict({ checklist: [{ itemId: 'DES-01', status: 'C' }], continuity: [] })).toBe('conforme');
    expect(inspectionVerdict({ checklist: [], continuity: [cont({ resistanceOhm: 0.05 })] })).toBe('conforme');
    // nada avaliado nem medido
    expect(inspectionVerdict({ checklist: [{ itemId: 'DES-01', status: '' }], continuity: [cont({ resistanceOhm: null })] })).toBe('incompleto');
    // códigos que não existem no checklist são ignorados
    expect(inspectionVerdict({ checklist: [{ itemId: 'XYZ-99', status: 'NC' }], continuity: [] })).toBe('incompleto');
  });

  it('NC prevalece sobre incompleto', () => {
    expect(inspectionVerdict({ checklist: [], continuity: [cont({ resistanceOhm: 1 })] })).toBe('nao_conforme');
  });

  it('recomendações citam prazo (7.4.2), Anexo F e periodicidade', () => {
    const checklist = allC().map(a => (a.itemId === 'DES-04' ? { ...a, status: 'NC' as const } : a));
    const txt = suggestRecommendations({ checklist, continuity: [cont({ resistanceOhm: 0.5 })], criticalStructure: true });
    expect(txt).toMatch(/7\.4\.2/);
    expect(txt).toMatch(/Anexo F/);
    expect(txt).toMatch(/1 ano/);
  });
});

describe('Periodicidade (7.3.2 f) e numeração', () => {
  it('validade automática de 12 meses para qualquer estrutura', () => {
    expect(nextInspectionDate({ inspectionDate: '2026-11-09', criticalStructure: true })).toBe('2027-11-09');
    expect(nextInspectionDate({ inspectionDate: '2026-11-09', criticalStructure: false })).toBe('2027-11-09');
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
    // numeração em sequência em cada grupo (CAP-01…, DES-01…, ATE-01…); tensões de toque e passo fora do checklist
    expect(CHECKLIST_TEMPLATE.filter(i => i.id.startsWith('EQU-'))).toHaveLength(4);
    expect(CHECKLIST_TEMPLATE.filter(i => i.id.startsWith('DOC-'))).toHaveLength(5);
    for (const p of ['DOC', 'CAP', 'DES', 'ATE', 'EQU']) {
      const ids = CHECKLIST_TEMPLATE.filter(i => i.id.startsWith(p + '-')).map(i => i.id);
      expect(ids).toEqual(ids.map((_, k) => `${p}-${String(k + 1).padStart(2, '0')}`));
    }
    expect(CHECKLIST_TEMPLATE.find(i => i.id === 'ATE-06')?.title).toMatch(/PVC/);
    expect(CHECKLIST_TEMPLATE.some(i => i.subsystem === 'protecao_pessoas' || i.id.startsWith('PES-'))).toBe(false);
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

describe('Identificação da medição de continuidade por subsistema', () => {
  it('código por subsistema e edificação, na ordem de lançamento', async () => {
    const { continuityCode, continuityGroupOf } = await import('../rules');
    const list = [
      cont({ id: 'a', kind: 'F4', buildingId: 'b1' }),
      cont({ id: 'b', kind: 'F5', buildingId: 'b1' }),
      cont({ id: 'c', kind: 'F4', buildingId: 'b1' }),
      cont({ id: 'd', kind: 'F4', buildingId: 'b2' }),
      cont({ id: 'e', kind: 'F1', buildingId: 'b1' }),
      cont({ id: 'f', kind: 'EQ', buildingId: 'b1' }),
      cont({ id: 'g', kind: 'F6', buildingId: 'b1' })
    ];
    expect(list.map(t => continuityCode(list, t))).toEqual(['CAP-C01', 'DES-C01', 'CAP-C02', 'CAP-C01', 'DES-C02', 'DPS-C01', 'ATE-C01']);
    expect(continuityCode(list, cont({ id: 'novo', kind: 'F4', buildingId: 'b1' }))).toBe('CAP-C03');
    expect(continuityGroupOf(cont({ kind: 'F3' }))).toBe('aterramento');
  });
});

describe('Queda de potencial — posições do eletrodo de potencial', () => {
  it('52 %, 62 % e 72 % da distância ao eletrodo de corrente', async () => {
    const { probeDistances, fmtMeters } = await import('../rules');
    expect(probeDistances(20)).toEqual({ pcts: [52, 62, 72], m: [10.4, 12.4, 14.4] });
    expect(probeDistances(35)).toEqual({ pcts: [52, 62, 72], m: [18.2, 21.7, 25.2] });
    expect(probeDistances(30, 5)).toEqual({ pcts: [57, 62, 67], m: [17.1, 18.6, 20.1] });
    expect(probeDistances(0)).toBeNull();
    expect(probeDistances(null)).toBeNull();
    expect(fmtMeters(10.4)).toBe('10,4 m');
  });
});

describe('Queda de potencial — patamar com três pontos (NBR 15749)', () => {
  it('patamar só com os 3 pontos dentro de ±5 % de R62', async () => {
    const { fop3Plateau, fop3Points } = await import('../rules');
    expect(fop3Points(ground({ r52: 9.7, r62: 10 })).map(p => p.r)).toEqual([9.7, 10, null]);
    expect(fop3Points(ground({})).map(p => p.pct)).toEqual([52, 62, 72]);              // padrão: passo de 10 %
    expect(fop3Points(ground({ probeStepPct: 5 })).map(p => p.pct)).toEqual([57, 62, 67]); // passo de 5 %
    expect(fop3Plateau(ground({ probeStepPct: 5, r52: 2.12, r62: 2.16, r72: 2.11 }))).toMatchObject({ ok: true, count: 3 });
    expect(fop3Plateau(ground({ r52: 9.7, r62: 10, r72: 10.3 }))).toMatchObject({ ok: true, count: 3, r62: 10 });
    expect(fop3Plateau(ground({ r52: 9.5, r62: 10, r72: 10.5 }))!.ok).toBe(true); // exatamente 5 %
    const out = fop3Plateau(ground({ r52: 9.7, r62: 10, r72: 11.2 }))!;
    expect(out).toMatchObject({ ok: false, count: 2 });
    expect(out.inBand).toEqual([true, true, false]);
    expect(out.maxDeviation).toBeCloseTo(0.12, 5);
    expect(fop3Plateau(ground({ r52: 9.7, r62: 10 }))).toMatchObject({ ok: false, count: 2 });
    expect(fop3Plateau(ground({ r52: 9.7 }))).toBeNull();
  });
});

describe('Curva padrão da queda de potencial', () => {
  it('passa por R62 em 62 %, sobe nas extremidades e tem patamar no meio', async () => {
    const { fopStandardCurve } = await import('../rules');
    const c = fopStandardCurve(10);
    const near = (pct: number) => c.points.reduce((a, b) => (Math.abs(b.pct - pct) < Math.abs(a.pct - pct) ? b : a));
    expect(near(62).r).toBeCloseTo(10, 0);
    expect(c.points[0].r).toBeCloseTo(0, 5);                    // junto de E
    expect(c.points[c.points.length - 1].r).toBeGreaterThan(18); // junto de C
    expect(c.plateau.fromPct).toBeGreaterThan(25);
    expect(c.plateau.fromPct).toBeLessThan(52);
    expect(c.plateau.toPct).toBeGreaterThan(72);
    expect(c.plateau.toPct).toBeLessThan(90);
  });
});
