import { describe, expect, it } from 'vitest';
import { parseMeasuredValue } from '../ocr';

const v = (t: string) => parseMeasuredValue(t).valueOhm;

describe('Leitura do visor do instrumento (interpretação do texto do OCR)', () => {
  it('ohms com ponto ou vírgula', () => {
    expect(v('0.085 Ω')).toBe(0.085);
    expect(v('0,152 ohm')).toBe(0.152);
    expect(v('7.82')).toBe(7.82);
  });

  it('converte mΩ, µΩ e kΩ para Ω', () => {
    expect(v('85.3 mΩ')).toBeCloseTo(0.0853, 6);
    expect(v('120 mohm')).toBeCloseTo(0.12, 6);
    expect(v('950 µΩ')).toBeCloseTo(0.00095, 8);
    expect(v('1.25 kΩ')).toBe(1250);
  });

  it('número começando por ponto', () => {
    expect(v('.08 Ω')).toBe(0.08);
  });

  it('corrige trocas típicas do OCR junto de dígitos', () => {
    expect(v('O.O85')).toBe(0.085);
    expect(v('1O.5 Ω')).toBe(10.5);
    expect(v('0.l2')).toBe(0.12);
    expect(v('S.2 mΩ')).toBeCloseTo(0.0052, 6);
  });

  it('prefere o número com unidade; senão o de mais dígitos', () => {
    expect(v('2 A  0.087 Ω')).toBe(0.087);
    expect(v('RANGE 2\n0.0873\nHOLD')).toBe(0.0873);
  });

  it('sem número → null', () => {
    expect(v('HOLD ---')).toBeNull();
    expect(parseMeasuredValue('').candidates).toEqual([]);
  });
});

describe('Continuidade em miliohms', () => {
  it('número sem unidade é lido como mΩ (micro-ohmímetro); unidade explícita é respeitada', () => {
    expect(parseMeasuredValue('85.3', 1e-3).valueOhm).toBeCloseTo(0.0853, 8);
    expect(parseMeasuredValue('0,152 Ω', 1e-3).valueOhm).toBe(0.152);
    expect(parseMeasuredValue('120 mΩ', 1e-3).valueOhm).toBeCloseTo(0.12, 8);
  });
});

describe('Símbolo Ω lido errado pelo OCR', () => {
  it('"mO", "mQ" e "m" sozinho → mΩ; "O"/"Q" separados → Ω', () => {
    expect(parseMeasuredValue('85.3 mO', 1e-3).valueOhm).toBeCloseTo(0.0853, 8);
    expect(parseMeasuredValue('85.3 mQ', 1).valueOhm).toBeCloseTo(0.0853, 8);
    expect(parseMeasuredValue('85.3 m', 1).valueOhm).toBeCloseTo(0.0853, 8);
    expect(parseMeasuredValue('0.152 O', 1e-3).valueOhm).toBe(0.152);
    expect(parseMeasuredValue('7.82 Q', 1).valueOhm).toBe(7.82);
  });
});
