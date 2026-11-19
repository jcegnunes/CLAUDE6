import { describe, expect, it } from 'vitest';
import { arrowHeadPoints, defaultsFor, isTiny, moveElement } from '../sketchShapes';
import type { SketchElement } from '../types';

const el = (p: Partial<SketchElement>): SketchElement => ({ id: 'e', kind: 'arrow', x1: 10, y1: 10, x2: 110, y2: 10, color: '#f00', width: 4, ...p });

describe('Editor de croqui — elementos', () => {
  it('mover desloca as duas pontas', () => {
    expect(moveElement(el({}), 5, -3)).toMatchObject({ x1: 15, y1: 7, x2: 115, y2: 7 });
  });
  it('clique sem arrastar descarta forma; texto vazio é descartado', () => {
    expect(isTiny(el({ x2: 11, y2: 11 }))).toBe(true);
    expect(isTiny(el({}))).toBe(false);
    expect(isTiny(el({ kind: 'text', text: '  ' }))).toBe(true);
    expect(isTiny(el({ kind: 'text', text: 'D1', x2: 10, y2: 10 }))).toBe(false);
  });
  it('cabeça da seta fica atrás da ponta', () => {
    const [a, b] = arrowHeadPoints({ x: 0, y: 0 }, { x: 100, y: 0 }, 4);
    expect(a.x).toBeLessThan(100);
    expect(b.x).toBeLessThan(100);
    expect(Math.sign(a.y)).toBe(-Math.sign(b.y));
  });
  it('tamanhos padrão proporcionais à imagem', () => {
    expect(defaultsFor(2000, 1500)).toEqual({ width: 10, fontSize: 60 });
    expect(defaultsFor(200, 100).fontSize).toBe(12);
  });
});
