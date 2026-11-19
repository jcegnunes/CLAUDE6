/**
 * Indicadores do Dashboard (só dados do aparelho): inspeções e pareceres, não conformidades,
 * ensaios, próximas inspeções periódicas, ordens de serviço e calibração dos instrumentos.
 */
import type { LabInstrument, ServiceOrder, ServiceOrderStatus } from '../../types';
import { SUBSYSTEM_LABEL } from './checklist';
import { continuityStatus, listNonConformities, nextInspectionDate } from './rules';
import { finalResult, laudoNumber, type ShownResult } from './techReport';
import type { SpdaInspection, SpdaSubsystem } from './types';

const DAY = 86_400_000;
const dayDiff = (iso: string, today: string) => Math.round((Date.parse(`${iso}T00:00:00`) - Date.parse(`${today}T00:00:00`)) / DAY);

export interface DashboardData {
  inspections: { total: number; draft: number; done: number; byResult: Record<ShownResult, number> };
  ncs: { total: number; open: number; bySubsystem: Array<{ subsystem: SpdaSubsystem; label: string; count: number }> };
  tests: { measured: number; approved: number; rejected: number };
  nextInspections: Array<{ id: string; number: string; client: string; date: string; days: number }>;
  overdue: number;
  dueSoon: number;
  orders: { total: number; byStatus: Partial<Record<ServiceOrderStatus, number>>; withArt: number; open: number };
  instruments: { total: number; expired: Array<{ id: string; name: string; date: string; days: number }>; expiring: Array<{ id: string; name: string; date: string; days: number }> };
  recent: Array<{ id: string; number: string; client: string; date: string; result: ShownResult; status: SpdaInspection['status'] }>;
}

/** Calcula o dashboard. `today` em AAAA-MM-DD; prazos: próximas inspeções em 90 dias, calibração em 30 dias. */
export function buildDashboard(inspections: SpdaInspection[], orders: ServiceOrder[], instruments: LabInstrument[], today: string): DashboardData {
  const byResult: Record<ShownResult, number> = { aprovado: 0, reprovado: 0, parcial: 0, incompleto: 0 };
  let ncTotal = 0, ncOpen = 0, measured = 0, approved = 0;
  const bySub = new Map<SpdaSubsystem, number>();
  inspections.forEach(v => {
    byResult[finalResult(v)] += 1;
    const ncs = listNonConformities(v);
    ncTotal += ncs.length;
    if (v.status !== 'concluido') ncOpen += ncs.length;
    ncs.forEach(n => bySub.set(n.subsystem, (bySub.get(n.subsystem) || 0) + 1));
    v.continuity.forEach(t => {
      const st = continuityStatus(t);
      if (st === 'conforme' || st === 'nao_conforme') { measured += 1; if (st === 'conforme') approved += 1; }
    });
  });

  const next = inspections
    .filter(v => v.status === 'concluido' && v.inspectionDate)
    .map(v => { const date = nextInspectionDate(v); return { id: v.id, number: laudoNumber(v), client: v.clientName || '—', date, days: dayDiff(date, today) }; })
    .sort((a, b) => a.days - b.days);

  const byStatus: Partial<Record<ServiceOrderStatus, number>> = {};
  orders.forEach(o => { byStatus[o.status] = (byStatus[o.status] || 0) + 1; });

  const cal = instruments
    .filter(i => i.calibrationExpiryDate)
    .map(i => ({ id: i.id, name: [i.type, i.manufacturer, i.model].filter(Boolean).join(' ') || 'Instrumento', date: i.calibrationExpiryDate, days: dayDiff(i.calibrationExpiryDate, today) }))
    .sort((a, b) => a.days - b.days);

  return {
    inspections: { total: inspections.length, draft: inspections.filter(v => v.status !== 'concluido').length, done: inspections.filter(v => v.status === 'concluido').length, byResult },
    ncs: {
      total: ncTotal, open: ncOpen,
      bySubsystem: [...bySub.entries()].map(([subsystem, count]) => ({ subsystem, label: SUBSYSTEM_LABEL[subsystem], count })).sort((a, b) => b.count - a.count)
    },
    tests: { measured, approved, rejected: measured - approved },
    nextInspections: next.slice(0, 8),
    overdue: next.filter(n => n.days < 0).length,
    dueSoon: next.filter(n => n.days >= 0 && n.days <= 90).length,
    orders: { total: orders.length, byStatus, withArt: orders.filter(o => !!o.artFileUrl).length, open: orders.filter(o => o.status !== 'concluida' && o.status !== 'cancelada').length },
    instruments: { total: instruments.length, expired: cal.filter(c => c.days < 0), expiring: cal.filter(c => c.days >= 0 && c.days <= 30) },
    recent: [...inspections]
      .sort((a, b) => (b.updatedAt || '').localeCompare(a.updatedAt || ''))
      .slice(0, 6)
      .map(v => ({ id: v.id, number: laudoNumber(v), client: v.clientName || '—', date: v.inspectionDate, result: finalResult(v), status: v.status }))
  };
}
