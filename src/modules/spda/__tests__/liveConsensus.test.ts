import { describe, expect, it } from 'vitest';
import { stableReading, streak, type LiveSample } from '../liveConsensus';

const s = (text: string, confidence = 95): LiveSample => ({ text, value: text ? Number(text) : null, confidence });
const none: LiveSample = { text: '', value: null, confidence: 0 };

describe('Consenso da leitura ao vivo', () => {
  it('aceita só com 3 leituras iguais seguidas', () => {
    expect(stableReading([s('21.4'), s('21.4')])).toBeNull();
    expect(stableReading([s('21.4'), s('21.4'), s('21.4')])?.text).toBe('21.4');
  });

  it('leitura diferente ou vazia no meio zera a contagem', () => {
    expect(stableReading([s('21.4'), s('27.4'), s('21.4'), s('21.4')])).toBeNull();
    expect(stableReading([s('21.4'), s('21.4'), none, s('21.4')])).toBeNull();
    expect(streak([s('21.4'), s('27.4'), s('21.4'), s('21.4')])).toBe(2);
  });

  it('confiança baixa não conta', () => {
    expect(stableReading([s('21.4', 60), s('21.4', 60), s('21.4', 60)])).toBeNull();
    expect(streak([s('21.4'), s('21.4'), s('21.4', 50)])).toBe(0);
  });

  it('valor que muda e depois estabiliza é aceito no valor final', () => {
    expect(stableReading([s('21.6'), s('21.5'), s('21.4'), s('21.4'), s('21.4')])?.text).toBe('21.4');
  });
});
