import { describe, expect, it } from 'vitest';
import { CHECKLIST_TEMPLATE } from '../checklist';
import { subsystemSummary } from '../rules';
import type { ChecklistAnswer, ContinuityTest, SpdaBuilding } from '../types';

const A = { id: 'A', name: 'Galpão A', type: 'industrial', spdaKind: 'externo', protectionLevel: 'III' } as SpdaBuilding;
const B = { id: 'B', name: 'Ambulatório', type: 'hospitalar', spdaKind: 'externo', protectionLevel: 'II' } as SpdaBuilding;
const CAP = CHECKLIST_TEMPLATE.filter(i => i.subsystem === 'captacao');
const capC = (bid: string): ChecklistAnswer[] => CAP.map(i => ({ itemId: i.id, buildingId: bid, status: 'C' as const }));
const f4 = (p: Partial<ContinuityTest>): ContinuityTest => ({ id: 't', kind: 'F4', subsystem: 'captacao', from: 'Captação', to: 'BEP', resistanceOhm: 0.1, ...p });

describe('Relatório simplificado por subsistema', () => {
  it('captação toda conforme na edificação A, com ensaio F.4 conforme', () => {
    const insp = { buildings: [A, B], checklist: capC('A'), continuity: [f4({ buildingId: 'A' })] };
    const s = subsystemSummary(insp, ['captacao'], ['F4'], 'A');
    expect(s).toMatchObject({ answered: CAP.length, total: CAP.length, nc: 0, testsNc: 0, verdict: 'conforme' });
    expect(s.tests).toHaveLength(1);
  });

  it('considera só a edificação pedida e só os itens/ensaios do subsistema', () => {
    const checklist = [
      ...capC('A'),
      { itemId: 'CAP-01', buildingId: 'B', status: 'NC' as const },
      { itemId: 'DES-01', buildingId: 'A', status: 'NC' as const }
    ];
    const insp = { buildings: [A, B], checklist, continuity: [f4({ id: '1', buildingId: 'A' }), f4({ id: '2', buildingId: 'B', resistanceOhm: 0.5 })] };
    expect(subsystemSummary(insp, ['captacao'], ['F4'], 'A').verdict).toBe('conforme');
    const b = subsystemSummary(insp, ['captacao'], ['F4'], 'B');
    expect(b.verdict).toBe('nao_conforme');
    expect(b.nc).toBe(1);
    expect(b.testsNc).toBe(1);
    expect(b.ncs.map(n => n.source).sort()).toEqual(['continuidade', 'inspecao']);
    expect(b.ncs.every(n => n.buildingId === 'B')).toBe(true);
  });

  it('itens sem resposta ou ensaio sem valor → incompleto', () => {
    const insp = { buildings: [A], checklist: capC('A').slice(1), continuity: [] };
    expect(subsystemSummary(insp, ['captacao'], ['F4'], 'A').verdict).toBe('incompleto');
    const insp2 = { buildings: [A], checklist: capC('A'), continuity: [f4({ buildingId: 'A', resistanceOhm: null })] };
    expect(subsystemSummary(insp2, ['captacao'], ['F4'], 'A').verdict).toBe('incompleto');
  });
});
