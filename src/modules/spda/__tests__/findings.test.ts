import { describe, expect, it } from 'vitest';
import { findingsOf } from '../rules';
import type { SpdaFinding, SpdaInspection } from '../types';

const photo = (id: string) => ({ id, url: 'data:image/jpeg;base64,AA', caption: 'foto', timestamp: '2026-11-15T10:00:00Z', userName: 'T' });
const f = (p: Partial<SpdaFinding>): SpdaFinding => ({ id: Math.random().toString(36).slice(2), subsystem: 'captacao', kind: 'nc', comment: '', photos: [], createdAt: '', ...p });

describe('Registros de não conformidade e melhoria', () => {
  const findings = [
    f({ buildingId: 'A', kind: 'nc', comment: 'Captor solto' }),
    f({ buildingId: 'A', kind: 'melhoria', comment: 'Pintar suportes' }),
    f({ buildingId: 'A', kind: 'nc', comment: 'Emenda oxidada' }),
    f({ buildingId: 'B', kind: 'nc', comment: 'Outro prédio' }),
    f({ buildingId: 'A', subsystem: 'descida', kind: 'nc', comment: 'Descida' })
  ];

  it('numera por edificação, subsistema e tipo', () => {
    expect(findingsOf({ findings }, 'A', ['captacao']).map(x => x.code)).toEqual(['CAP-NC-01', 'CAP-M-01', 'CAP-NC-02']);
    expect(findingsOf({ findings }, 'B', ['captacao']).map(x => x.code)).toEqual(['CAP-NC-01']);
    expect(findingsOf({ findings }, 'A', ['descida']).map(x => x.code)).toEqual(['DES-NC-01']);
  });

  it('fotos dos registros entram no relatório fotográfico do subsistema, com o comentário como legenda', async () => {
    const { photoEntries } = await import('../views/PhotoReport');
    const v = {
      id: 'i', number: 'SPDA-1', checklist: [], continuity: [], grounding: [], buildings: [{ id: 'A', name: 'Bloco A' }],
      findings: [f({ buildingId: 'A', kind: 'nc', comment: 'Captor solto', photos: [photo('p1'), photo('p2')] }), f({ buildingId: 'A', kind: 'melhoria', comment: 'Pintar', photos: [photo('p3')] })]
    } as unknown as SpdaInspection;
    const list = photoEntries(v, v.buildings![0], 'captacao');
    expect(list.map(p => p.origin)).toEqual(['Não conformidade CAP-NC-01', 'Não conformidade CAP-NC-01', 'Melhoria CAP-M-01']);
    expect(list[0].caption).toBe('Captor solto');
    expect(list.every(p => p.source === 'registro')).toBe(true);
    expect(photoEntries(v, v.buildings![0], 'descida')).toHaveLength(0);
  });
});

describe('Registros nos laudos e relatórios', () => {
  it('não conformidade registrada entra na lista de NCs e deixa o laudo não conforme; melhoria vai para recomendações', async () => {
    const { inspectionVerdict, listImprovements, listNonConformities, subsystemSummary, suggestRecommendations, buildingVerdict } = await import('../rules');
    const v = {
      checklist: [], continuity: [], buildings: [{ id: 'A', name: 'Bloco A' }, { id: 'B', name: 'Bloco B' }], criticalStructure: false,
      findings: [
        f({ buildingId: 'A', kind: 'nc', comment: 'Captor solto', photos: [photo('p1')] }),
        f({ buildingId: 'B', kind: 'nc', comment: 'Emenda oxidada' }),
        f({ buildingId: 'A', kind: 'melhoria', subsystem: 'descida', comment: 'Instalar proteção mecânica' })
      ]
    } as unknown as Parameters<typeof listNonConformities>[0] & { criticalStructure: boolean };
    const ncs = listNonConformities(v).filter(n => n.source === 'registro');
    expect(ncs.map(n => `${n.buildingName}: ${n.description}`)).toEqual(['Bloco A: CAP-NC-01 — Captor solto', 'Bloco B: CAP-NC-01 — Emenda oxidada']);
    expect(inspectionVerdict(v)).toBe('nao_conforme');
    expect(buildingVerdict(v, 'A')).toBe('nao_conforme');
    expect(listImprovements(v).map(m => `${m.code} ${m.comment}`)).toEqual(['DES-M-01 Instalar proteção mecânica']);
    const cap = subsystemSummary(v, ['captacao'], ['F4'], 'A');
    expect(cap.verdict).toBe('nao_conforme');
    expect(cap.registered).toBe(1);
    const des = subsystemSummary(v, ['descida'], ['F1', 'F2', 'F5'], 'A');
    expect(des.verdict).toBe('conforme');
    expect(des.improvements).toHaveLength(1);
    const rec = suggestRecommendations(v);
    expect(rec).toContain('CAP-NC-01 — Captor solto');
    expect(rec).toContain('Melhorias recomendadas: Bloco A — DES-M-01 — Instalar proteção mecânica');
  });
});
