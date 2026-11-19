import { describe, expect, it } from 'vitest';
import { buildDashboard } from '../dashboard';
import type { SpdaInspection } from '../types';

const insp = (p: Partial<SpdaInspection>): SpdaInspection => ({
  id: Math.random().toString(36).slice(2), companyId: 'c', number: 'SPDA-2026-0001', status: 'rascunho', clientName: 'Cliente', buildingName: '', address: '',
  protectionLevel: 'III', method: 'gaiola', inspectionKind: 'periodica', criticalStructure: false, inspectionDate: '2023-12-01', instruments: [], technicianName: '',
  checklist: [], continuity: [], grounding: [], buildings: [{ id: 'b', name: 'B', type: 'industrial', spdaKind: 'externo', protectionLevel: 'III' }],
  createdAt: '', updatedAt: '2026-11-10', ...p
} as SpdaInspection);

describe('Dashboard', () => {
  it('conta inspeções, pareceres, NCs, ensaios, próximas inspeções, OS e calibração', () => {
    const ok = insp({ number: 'A', status: 'concluido', inspectionDate: '2025-12-01', checklist: [{ itemId: 'CAP-01', buildingId: 'b', status: 'C' }], continuity: [{ id: '1', kind: 'F4', subsystem: 'captacao', buildingId: 'b', from: 'x', to: 'y', resistanceOhm: 0.02 }] });
    const nc = insp({ number: 'B', checklist: [{ itemId: 'DES-04', buildingId: 'b', status: 'NC' }], continuity: [{ id: '2', kind: 'F5', subsystem: 'descida', buildingId: 'b', from: 'D1', to: 'D2', resistanceOhm: 0.3 }], updatedAt: '2026-11-12' });
    const parcial = insp({ number: 'C', inspectionDate: '2026-01-10', status: 'concluido', techReport: { results: { final: 'parcial' } } });
    const orders = [
      { id: 'o1', osNumber: 'OS-1', clientId: 'c', openDate: '', responsibleId: '', technicianId: '', equipmentIds: [], status: 'aberta', artFileUrl: 'data:application/pdf;base64,AA', createdAt: '', updatedAt: '' },
      { id: 'o2', osNumber: 'OS-2', clientId: 'c', openDate: '', responsibleId: '', technicianId: '', equipmentIds: [], status: 'concluida', createdAt: '', updatedAt: '' }
    ] as never;
    const instruments = [
      { id: 'i1', type: 'Micro-ohmímetro', manufacturer: 'X', model: 'A', calibrationExpiryDate: '2026-11-01' },
      { id: 'i2', type: 'Terrômetro', manufacturer: 'Y', model: 'B', calibrationExpiryDate: '2026-12-01' },
      { id: 'i3', type: 'Outro', manufacturer: 'Z', model: 'C', calibrationExpiryDate: '2027-06-01' }
    ] as never;
    const d = buildDashboard([ok, nc, parcial], orders, instruments, '2026-11-16');
    expect(d.inspections).toMatchObject({ total: 3, done: 2, draft: 1 });
    expect(d.inspections.byResult).toEqual({ aprovado: 1, reprovado: 1, parcial: 1, incompleto: 0 });
    expect(d.ncs.total).toBe(2);
    expect(d.ncs.open).toBe(2);
    expect(d.ncs.bySubsystem[0]).toMatchObject({ subsystem: 'descida', count: 2 });
    expect(d.tests).toEqual({ measured: 2, approved: 1, rejected: 1 });
    // validade automática de 12 meses — A: 01/12/2025 → 01/12/2026 (em 15 dias); C: 10/01/2026 → 10/01/2027 (em 55 dias)
    expect(d.nextInspections.map(n => n.number)).toEqual(['A', 'C']);
    expect(d.nextInspections[0].days).toBe(15);
    expect(d.dueSoon).toBe(2);
    expect(d.orders).toMatchObject({ total: 2, open: 1, withArt: 1 });
    expect(d.instruments.expired.map(i => i.id)).toEqual(['i1']);
    expect(d.instruments.expiring.map(i => i.id)).toEqual(['i2']);
    expect(d.recent[0].number).toBe('B');
  });
});
