import { describe, expect, it } from 'vitest';
import { airTerminationText } from '../rules';
import type { SpdaBuilding, SpdaInspection } from '../types';

const photo = (id: string) => ({ id, url: 'data:image/jpeg;base64,AA', caption: 'foto', timestamp: '2026-11-15T10:00:00Z', userName: 'T' });

describe('Sistemas de captação existentes', () => {
  it('texto em ordem fixa, com material do anel e descrição de "outro"', () => {
    const b = { airTermination: { types: ['anel_superior', 'franklin', 'outro', 'faraday'], ringMaterial: 'aluminio', otherText: 'Haste na antena' } } as Pick<SpdaBuilding, 'airTermination'>;
    expect(airTerminationText(b)).toBe('Mastro com captor Franklin; Malha de Faraday; Anel superior em alumínio; Haste na antena');
    expect(airTerminationText({})).toBe('');
  });

  it('fotos do sistema existente entram primeiro no relatório fotográfico da captação', async () => {
    const { photoEntries } = await import('../views/PhotoReport');
    const b = { id: 'A', name: 'Bloco A', airTermination: { types: ['franklin', 'mini_captores'], photos: { franklin: [photo('p1')], mini_captores: [photo('p2')], faraday: [photo('x')] } } } as unknown as SpdaBuilding;
    const v = { id: 'i', checklist: [], continuity: [], grounding: [], buildings: [b] } as unknown as SpdaInspection;
    const list = photoEntries(v, b, 'captacao');
    expect(list.map(p => p.origin)).toEqual(['Sistema de captação existente · Mastro com captor Franklin', 'Sistema de captação existente · Minicaptores']);
    expect(photoEntries(v, b, 'descida')).toHaveLength(0);
  });
});

describe('Tipos de descida existentes', () => {
  it('texto com material da descida externa', async () => {
    const { downConductorText } = await import('../rules');
    const b = { downConductor: { types: ['pilar_metalico', 'externa'], material: 'cobre' } } as Pick<SpdaBuilding, 'downConductor'>;
    expect(downConductorText(b)).toBe('Descida externa em cobre; Descida natural em pilar de ferro');
    expect(downConductorText({})).toBe('');
  });

  it('fotos das descidas existentes entram só no relatório fotográfico da descida', async () => {
    const { photoEntries } = await import('../views/PhotoReport');
    const b = { id: 'A', name: 'Bloco A', downConductor: { types: ['pilar_estrutural'], photos: { pilar_estrutural: [photo('d1')] } } } as unknown as SpdaBuilding;
    const v = { id: 'i', checklist: [], continuity: [], grounding: [], buildings: [b] } as unknown as SpdaInspection;
    expect(photoEntries(v, b, 'descida').map(p => p.origin)).toEqual(['Descida existente · Descida interna ao pilar estrutural']);
    expect(photoEntries(v, b, 'captacao')).toHaveLength(0);
  });
});

describe('Tipo de malha de aterramento', () => {
  it('texto com afastamento e profundidade do anel', async () => {
    const { groundingSystemText } = await import('../rules');
    const b = { groundingSystem: { types: ['estrutural', 'anel_cobre'], ringDistanceM: 1.5, ringDepthM: 0.6 } } as Pick<SpdaBuilding, 'groundingSystem'>;
    expect(groundingSystemText(b)).toBe('Anel de aterramento em cobre com hastes (afastado 1,5 m da edificação, profundidade 0,6 m); Malha de aterramento estrutural (ferragem da edificação)');
    expect(groundingSystemText({ groundingSystem: { types: ['anel_cobre'] } })).toBe('Anel de aterramento em cobre com hastes (afastado da edificação)');
    expect(groundingSystemText({ groundingSystem: { types: ['hastes_linha', 'anel_sem_hastes'] } })).toBe('Anel de aterramento em cobre (afastado da edificação); Hastes de aterramento em linha (sem anel)');
  });

  it('fotos do aterramento existente entram só no relatório fotográfico do aterramento', async () => {
    const { photoEntries } = await import('../views/PhotoReport');
    const b = { id: 'A', name: 'Bloco A', groundingSystem: { types: ['anel_cobre'], photos: { anel_cobre: [photo('g1')] } } } as unknown as SpdaBuilding;
    const v = { id: 'i', checklist: [], continuity: [], grounding: [], buildings: [b] } as unknown as SpdaInspection;
    expect(photoEntries(v, b, 'aterramento').map(p => p.origin)).toEqual(['Aterramento existente · Anel de aterramento em cobre com hastes']);
    expect(photoEntries(v, b, 'descida')).toHaveLength(0);
  });
});

describe('Descrição dos subsistemas (relatório técnico, capítulo 5)', () => {
  it('descreve cada tipo selecionado com os dados informados e as fotos', async () => {
    const { describeSubsystems } = await import('../systemDescriptions');
    const b = {
      id: 'A', name: 'Bloco A', type: 'industrial', spdaKind: 'externo', protectionLevel: 'III', downConductorsCount: 12,
      airTermination: { types: ['faraday', 'anel_superior'], ringMaterial: 'cobre', photos: { faraday: [photo('f1')] } },
      downConductor: { types: ['externa'], material: 'aluminio' },
      groundingSystem: { types: ['anel_cobre'], ringDistanceM: 1, ringDepthM: 0.6 }
    } as unknown as SpdaBuilding;
    const v = { continuity: [{ id: 'e', kind: 'EQ', buildingId: 'A', from: 'BEP', to: 'QGBT', resistanceOhm: 0.01 }] } as unknown as SpdaInspection;
    const [cap, des, ate, dps] = describeSubsystems(v, b);
    expect(cap.items.map(i => i.name)).toEqual(['Malha de Faraday', 'Anel superior em cobre']);
    expect(cap.items[0].text).toContain('no máximo, 15 × 15 m para');
    expect(cap.items[0].photos).toHaveLength(1);
    expect(des.items[0].text).toContain('alumínio');
    expect(des.intro).toContain('12 descida(s)');
    expect(ate.items[0].text).toContain('afastado 1 m das paredes externas e enterrado a 0,6 m');
    expect(dps.intro).toContain('BEP → QGBT');
  });
});
