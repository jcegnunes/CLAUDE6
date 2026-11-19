import { describe, expect, it } from 'vitest';
import { nextNumber } from '../rules';

describe('Parâmetros do SPDA — numeração', () => {
  it('usa o prefixo configurado e continua a sequência só desse prefixo', () => {
    expect(nextNumber([], 2026)).toBe('SPDA-2026-0001');
    expect(nextNumber(['LAU-SPDA-2026-0004', 'SPDA-2026-0009'], 2026, 'LAU-SPDA-')).toBe('LAU-SPDA-2026-0005');
    expect(nextNumber(['SPDA-2026-0009'], 2026, 'JVM-')).toBe('JVM-2026-0001');
  });
});
