/**
 * Consenso da leitura ao vivo: o valor só é aceito quando várias leituras seguidas do vídeo
 * concordam (elimina erros de reflexo, tremor e desfoque em quadros isolados).
 */

export interface LiveSample {
  text: string;
  value: number | null;
  confidence: number;
}

export const CONSENSUS = { needed: 3, minConfidence: 80 } as const;

/** Leituras iguais seguidas no fim do histórico (0 se a última não serve). */
export function streak(history: LiveSample[], minConfidence: number = CONSENSUS.minConfidence): number {
  const last = history[history.length - 1];
  if (!last || last.value === null || last.confidence < minConfidence) return 0;
  let n = 0;
  for (let i = history.length - 1; i >= 0; i--) {
    const s = history[i];
    if (s.value === null || s.confidence < minConfidence || s.text !== last.text) break;
    n++;
  }
  return n;
}

/** Valor estável (texto do visor) quando há `needed` leituras iguais seguidas; senão null. */
export function stableReading(history: LiveSample[], needed: number = CONSENSUS.needed, minConfidence: number = CONSENSUS.minConfidence): LiveSample | null {
  return streak(history, minConfidence) >= needed ? history[history.length - 1] : null;
}
