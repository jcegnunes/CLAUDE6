import { describe, expect, it } from 'vitest';
import { CHECKLIST_TEMPLATE } from '../checklist';
import {
  answerOf, buildingVerdict, checklistProgress, expectedAnswers, inspectionVerdict, isPerBuilding, listNonConformities,
  migrateInspection, withBuildings
} from '../rules';
import type { ChecklistAnswer, ContinuityTest, SpdaBuilding, SpdaInspection } from '../types';

const A = { id: 'A', name: 'Galpão A', type: 'industrial', spdaKind: 'externo', protectionLevel: 'III' } as SpdaBuilding;
const B = { id: 'B', name: 'Ambulatório', type: 'hospitalar', spdaKind: 'estrutural', protectionLevel: 'II' } as SpdaBuilding;
const DOC = CHECKLIST_TEMPLATE.filter(i => !isPerBuilding(i.subsystem));
const PER = CHECKLIST_TEMPLATE.filter(i => isPerBuilding(i.subsystem));

/** Todas as respostas C: documentação geral + itens de cada edificação. */
const allC = (buildings: SpdaBuilding[]): ChecklistAnswer[] => [
  ...DOC.map(i => ({ itemId: i.id, status: 'C' as const })),
  ...buildings.flatMap(b => PER.map(i => ({ itemId: i.id, buildingId: b.id, status: 'C' as const })))
];
const cont = (p: Partial<ContinuityTest>): ContinuityTest => ({ id: 'c', kind: 'F4', subsystem: 'captacao', from: 'Captação', to: 'BEP', resistanceOhm: 0.1, ...p });

describe('Inspeção dos subsistemas por edificação', () => {
  it('documentação é geral da OS; captação, descida, aterramento, DPS e toque/passo são por edificação', () => {
    expect(expectedAnswers([A, B])).toHaveLength(DOC.length + PER.length * 2);
    expect(expectedAnswers([])).toHaveLength(CHECKLIST_TEMPLATE.length);
    expect(isPerBuilding('documentacao')).toBe(false);
    expect(['captacao', 'descida', 'aterramento', 'equipotencializacao', 'protecao_pessoas'].every(s => isPerBuilding(s as any))).toBe(true);
  });

  it('a mesma pergunta tem resposta separada em cada edificação', () => {
    const checklist: ChecklistAnswer[] = [
      { itemId: 'CAP-01', buildingId: 'A', status: 'C' },
      { itemId: 'CAP-01', buildingId: 'B', status: 'NC', obs: 'Captor tombado' }
    ];
    expect(answerOf(checklist, 'CAP-01', 'A').status).toBe('C');
    expect(answerOf(checklist, 'CAP-01', 'B').status).toBe('NC');
    expect(answerOf(checklist, 'CAP-01').status).toBe('');
  });

  it('progresso conta documentação uma vez e os itens de cada edificação', () => {
    const p = checklistProgress(allC([A]), [A, B]);
    expect(p.total).toBe(DOC.length + PER.length * 2);
    expect(p.answered).toBe(DOC.length + PER.length);
  });

  it('NC aponta a edificação; parecer geral e de cada edificação', () => {
    const checklist = allC([A, B]).map(a => (a.itemId === 'DES-08' && a.buildingId === 'B' ? { ...a, status: 'NC' as const } : a));
    const insp = { checklist, continuity: [cont({ buildingId: 'A' })], buildings: [A, B] };
    const ncs = listNonConformities(insp);
    expect(ncs).toHaveLength(1);
    expect(ncs[0]).toMatchObject({ buildingId: 'B', buildingName: 'Ambulatório', subsystem: 'descida' });
    expect(inspectionVerdict(insp)).toBe('nao_conforme');
    expect(buildingVerdict(insp, 'A')).toBe('conforme');
    expect(buildingVerdict(insp, 'B')).toBe('nao_conforme');
  });

  it('ensaio fora do critério reprova só a edificação dele', () => {
    const insp = { checklist: allC([A, B]), continuity: [cont({ id: '1', buildingId: 'A' }), cont({ id: '2', buildingId: 'B', kind: 'F1', resistanceOhm: 1.5 })], buildings: [A, B] };
    expect(buildingVerdict(insp, 'A')).toBe('conforme');
    expect(buildingVerdict(insp, 'B')).toBe('nao_conforme');
    expect(listNonConformities(insp)[0].buildingName).toBe('Ambulatório');
  });

  it('edificação sem respostas fica incompleta', () => {
    const insp = { checklist: allC([A]), continuity: [], buildings: [A, B] };
    expect(buildingVerdict(insp, 'A')).toBe('conforme');
    expect(buildingVerdict(insp, 'B')).toBe('incompleto');
    expect(inspectionVerdict(insp)).toBe('incompleto');
  });

  it('remover uma edificação remove as respostas e os ensaios dela', () => {
    const insp = {
      protectionLevel: 'III' as const,
      checklist: allC([A, B]),
      continuity: [cont({ id: '1', buildingId: 'A' }), cont({ id: '2', buildingId: 'B' })],
      grounding: [{ id: 'g', point: 'D1', method: 'queda_potencial' as const, buildingId: 'B' }]
    };
    const next = withBuildings(insp, [A]);
    expect(next.checklist.some(a => a.buildingId === 'B')).toBe(false);
    expect(next.checklist.filter(a => !a.buildingId)).toHaveLength(DOC.length);
    expect(next.continuity.map(t => t.id)).toEqual(['1']);
    expect(next.grounding).toEqual([]);
  });
});

describe('Migração de inspeções da versão anterior', () => {
  const base = {
    id: 'i', companyId: 'c', number: 'SPDA-1', status: 'rascunho', clientName: 'X', buildingName: 'Galpão A', address: '',
    protectionLevel: 'III', method: 'gaiola', inspectionKind: 'periodica', criticalStructure: false, inspectionDate: '2026-11-09',
    instruments: [], technicianName: '', createdAt: '', updatedAt: ''
  } as unknown as SpdaInspection;

  it('sem lista de edificações: cria a edificação e vincula respostas e ensaios a ela', () => {
    const old = {
      ...base,
      checklist: [{ itemId: 'DOC-01', status: 'C' }, { itemId: 'CAP-01', status: 'NC' }] as ChecklistAnswer[],
      continuity: [cont({})],
      grounding: [{ id: 'g', point: 'D1', method: 'queda_potencial' }]
    } as SpdaInspection;
    const m = migrateInspection(old);
    const bid = m.buildings![0].id;
    expect(m.buildings![0].name).toBe('Galpão A');
    expect(answerOf(m.checklist, 'DOC-01').status).toBe('C');
    expect(answerOf(m.checklist, 'CAP-01', bid).status).toBe('NC');
    expect(m.continuity[0].buildingId).toBe(bid);
    expect(m.grounding[0].buildingId).toBe(bid);
  });

  it('já vinculada: nada muda (mesmo objeto)', () => {
    const ok = { ...base, buildings: [A], checklist: [{ itemId: 'CAP-01', buildingId: 'A', status: 'C' }], continuity: [cont({ buildingId: 'A' })], grounding: [] } as SpdaInspection;
    expect(migrateInspection(ok)).toBe(ok);
  });
});
