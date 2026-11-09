import { describe, expect, it } from 'vitest';
import {
  buildingArea, buildingsOf, formatCoordinates, levelEfficiencyPct, mapUrl, minDownConductors, validCoordinates, withBuildings
} from '../rules';
import type { SpdaBuilding } from '../types';

const b = (p: Partial<SpdaBuilding>): SpdaBuilding => ({ id: 'b', name: 'Galpão', type: 'industrial', spdaKind: 'externo', protectionLevel: 'III', ...p });

describe('Edificações da OS', () => {
  it('área projetada C × L (50 × 30 = 1500 m²)', () => {
    expect(buildingArea({ lengthM: 50, widthM: 30 })).toBe(1500);
    expect(buildingArea({ lengthM: 50 })).toBeNull();
  });

  it('nº mínimo de descidas = perímetro ÷ distância da Tabela 5, nunca menos que 2 (5.4.3.1)', () => {
    expect(minDownConductors(b({ lengthM: 50, widthM: 30, protectionLevel: 'II' }))).toBe(16); // 160 m / 10 m
    expect(minDownConductors(b({ lengthM: 50, widthM: 30, protectionLevel: 'III' }))).toBe(11); // 160 / 15 = 10,7
    expect(minDownConductors(b({ lengthM: 5, widthM: 4, protectionLevel: 'IV' }))).toBe(2); // 18 / 20 < 2
    expect(minDownConductors(b({}))).toBeNull();
  });

  it('eficiência do NP = 1 − PB (NBR 5419-2:2026, Tabela B.2)', () => {
    expect([levelEfficiencyPct('I'), levelEfficiencyPct('II'), levelEfficiencyPct('III'), levelEfficiencyPct('IV')]).toEqual([98, 95, 90, 80]);
  });

  it('resumo da inspeção: nomes, NP mais restritivo, maior altura e total de descidas', () => {
    const insp = withBuildings({ protectionLevel: 'IV' as const }, [
      b({ id: '1', name: 'Galpão A', protectionLevel: 'III', heightM: 12, downConductorsCount: 8, activity: 'Manufatura' }),
      b({ id: '2', name: 'Ambulatório', type: 'hospitalar', protectionLevel: 'II', heightM: 9, downConductorsCount: 4 })
    ]);
    expect(insp.buildingName).toBe('Galpão A / Ambulatório');
    expect(insp.protectionLevel).toBe('II');
    expect(insp.heightM).toBe(12);
    expect(insp.downConductorsCount).toBe(12);
    expect(insp.buildingUse).toBe('Manufatura / Hospitalar');
  });

  it('registro antigo sem lista vira uma edificação com os dados que já tinha', () => {
    const list = buildingsOf({ buildingName: 'Galpão A', heightM: 10, protectionLevel: 'II', downConductorsCount: 6 });
    expect(list).toHaveLength(1);
    expect(list[0]).toMatchObject({ name: 'Galpão A', heightM: 10, protectionLevel: 'II', downConductorsCount: 6 });
    expect(buildingsOf({ protectionLevel: 'III', buildingName: '' })).toEqual([]);
    expect(buildingsOf({ protectionLevel: 'III', buildingName: 'X', buildings: [] })).toEqual([]);
  });

  it('coordenadas geográficas: validação, formato e link do mapa', () => {
    expect(validCoordinates(-15.793889, -47.882778)).toBe(true);
    expect(validCoordinates(95, 10)).toBe(false);
    expect(validCoordinates(0, 0)).toBe(false);
    expect(validCoordinates(null, -47)).toBe(false);
    expect(formatCoordinates(-15.793889, -47.882778)).toBe('15,793889° S, 47,882778° O');
    expect(mapUrl(-15.793889, -47.882778)).toMatch(/center=-15\.793889,-47\.882778/);
    expect(mapUrl(null, null)).toBe('');
  });
});
