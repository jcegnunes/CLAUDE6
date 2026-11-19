import { describe, expect, it } from 'vitest';
import { isSpdaServiceOrder, withoutSpdaOrders } from '../osText';
import { buildServiceOrder, createInspection } from '../repository';

describe('OS do módulo SPDA', () => {
  it('só as OS do SPDA aparecem no módulo; as dos Ensaios de EPI não', () => {
    expect(isSpdaServiceOrder({ module: 'spda', notes: 'qualquer' })).toBe(true);
    expect(isSpdaServiceOrder({ module: 'ensaios', notes: 'Inspeção de SPDA' })).toBe(false);
    expect(isSpdaServiceOrder({ notes: 'Ensaio de rotina e periódico em EPIs/EPCs dielétricos' })).toBe(false);
    expect(isSpdaServiceOrder({})).toBe(false);
  });

  it('OS antigas sem marca contam como SPDA quando a descrição é de SPDA', () => {
    expect(isSpdaServiceOrder({ notes: 'Inspeção e ensaios de SPDA conforme ABNT NBR 5419:2026 (inspeção visual…)' })).toBe(true);
    expect(isSpdaServiceOrder({ notes: 'Inspeção visual e ensaios de continuidade e de resistência de aterramento dos subsistemas que compõem o SPDA.' })).toBe(true);
  });

  it('a OS gerada pela inspeção sai marcada como do SPDA', () => {
    const os = buildServiceOrder(createInspection(), 'OS-1', null, { id: 'c1', razaoSocial: 'Cliente' });
    expect(os.module).toBe('spda');
    expect(isSpdaServiceOrder(os)).toBe(true);
  });

  it('nos Ensaios de EPI as OS do SPDA não aparecem', () => {
    const list = [{ id: 'a', module: 'spda' }, { id: 'b', notes: 'Ensaio de rotina em EPIs' }, { id: 'c', notes: 'Inspeção e ensaios de SPDA' }, { id: 'd' }];
    expect(withoutSpdaOrders(list).map(o => o.id)).toEqual(['b', 'd']);
  });
});
