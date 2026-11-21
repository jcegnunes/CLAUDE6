import { describe, expect, it } from 'vitest';
import { decodeInmdResult, inmdTestName, instrumentHasInmd, looksLikeInbrat } from './inbratInmd';

// bytes reais do registro HCI de 20/11/2026 (ensaios feitos com o Inbrat Connect)
describe('INBRAT INMD 1 Pro — resultado do ensaio', () => {
  it('ensaio "teste": 352,244 mΩ com 1,143 A (o visor mostrou 352,2 mΩ)', () => {
    const r = decodeInmdResult('0c02000000491fb0435f49923f');
    expect(r.deviceTestNo).toBe(524);
    expect(r.status).toBe(0);
    expect(r.resistanceMilliOhm).toBeCloseTo(352.244, 3);
    expect(r.currentA).toBeCloseTo(1.1429, 3);
    expect(r.invalid).toBe(false);
  });

  it('ensaio "2": 3000 mΩ sem corrente = pontas abertas, não pode ser usado', () => {
    const r = decodeInmdResult('0d0200000000803b451541c3b5');
    expect(r.deviceTestNo).toBe(525);
    expect(r.resistanceMilliOhm).toBe(3000);
    expect(r.invalid).toBe(true);
    expect(r.invalidReason).toMatch(/Fora de escala/);
  });

  it('resposta curta dá erro', () => {
    expect(() => decodeInmdResult('0c020000')).toThrow();
  });
});

describe('INBRAT INMD 1 Pro — auxiliares', () => {
  it('nome do ensaio: ASCII, até 11 caracteres', () => {
    expect(inmdTestName('F.4 Captação → BEP')).toBe('F.4 Captaca');
    expect(inmdTestName('')).toBe('SPDA');
  });
  it('reconhece o nome anunciado pelo aparelho', () => {
    expect(looksLikeInbrat('IN433A')).toBe(true);
    expect(looksLikeInbrat('Galaxy Buds')).toBe(false);
  });
  it('instrumento pareado', () => {
    expect(instrumentHasInmd({ bluetooth: { protocol: 'inbrat-inmd1pro', deviceName: 'IN433A', address: 'AA:BB', pairedAt: '' } })).toBe(true);
    expect(instrumentHasInmd({})).toBe(false);
  });
});
