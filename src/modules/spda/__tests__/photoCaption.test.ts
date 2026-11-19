import { describe, expect, it } from 'vitest';
import { cleanCaption } from '../views/PhotoReport';

describe('Legendas dos relatórios fotográficos sem repetição', () => {
  it('tira o texto automático e mantém o que o inspetor escreveu', () => {
    expect(cleanCaption('CAP-C01 · Captação · Armazém 1 — continuidade Captação (ponto mais alto) → BEP — leitura do instrumento 200 mΩ')).toBe('');
    expect(cleanCaption('CAP-C01 · Captação · Armazém 1 — continuidade Captação (ponto mais alto) → BEP')).toBe('');
    expect(cleanCaption('DES-C02 · Descida — continuidade D2 → D3 — conector oxidado')).toBe('conector oxidado');
    expect(cleanCaption('Captação existente — Mastro com captor Franklin')).toBe('Mastro com captor Franklin');
    expect(cleanCaption('Resistência de aterramento — Descida D1 / caixa')).toBe('');
    expect(cleanCaption('CAP-NC-01 — Não conformidade: Captor solto')).toBe('Captor solto');
    expect(cleanCaption('Vista geral da cobertura')).toBe('Vista geral da cobertura');
  });

  it('relatório fotográfico: foto do ensaio sem texto próprio não repete código e trecho', async () => {
    const { photoEntries } = await import('../views/PhotoReport');
    const { newContinuityTest } = await import('../repository');
    const t = newContinuityTest({ kind: 'F4', buildingId: 'A', resistanceOhm: 0.2, photos: [{ id: 'p', url: 'data:image/jpeg;base64,AA', caption: 'CAP-C01 · Captação · Armazém 1 — continuidade Captação (ponto mais alto) → BEP — leitura do instrumento 200 mΩ', timestamp: '2026-11-16T08:08:00', userName: 'T' }] });
    const v = { checklist: [], continuity: [t], grounding: [], buildings: [{ id: 'A', name: 'Armazém 1' }] } as never;
    const [e] = photoEntries(v, { id: 'A', name: 'Armazém 1' } as never, 'captacao');
    expect(e.caption).toBe('');
    expect(e.origin).toBe('Continuidade CAP-C01 · Captação (ponto mais alto) → BEP');
    expect(e.detail).toBe('200 mΩ — Aprovado');
  });
});
