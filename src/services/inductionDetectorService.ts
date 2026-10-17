/**
 * Detector de tensão por indução (classe 1 kV a 60 kV): ensaio de detecção
 * por aproximação. Para cada tensão fixa da tabela o técnico registra a
 * distância (cm) em que o detector sinalizou; o resultado sai na tabela
 * Distância × Tensão e no gráfico "Detector de Tensão" do laudo.
 * Critério: o detector deve sinalizar em todas as tensões da tabela, conforme
 * as especificações do fabricante.
 */

export interface InductionDetectorPoint {
  voltage_kV: number;
  /** Distância de detecção obtida (cm) */
  distance_cm?: number;
  /** O detector sinalizou nesta tensão? (padrão: sim) */
  detected?: boolean;
}

export const INDUCTION_DETECTOR_CLASS = '1 kV a 60 kV';
export const INDUCTION_DETECTOR_VOLTAGES_KV = [1, 2, 5, 8, 13.8, 19, 24, 34.5, 43, 52, 60];
export const INDUCTION_DETECTOR_NOTE = 'RESULTADOS SATISFATÓRIOS CONFORME ESPECIFICAÇÕES DO FABRICANTE.';
export const INDUCTION_DETECTOR_NORM_ID = 'norm-detector-inducao';

/** Tabela vazia: tensões fixas, distâncias a preencher. */
export function defaultInductionPoints(): InductionDetectorPoint[] {
  return INDUCTION_DETECTOR_VOLTAGES_KV.map(v => ({ voltage_kV: v, distance_cm: undefined, detected: true }));
}

/** Completa/ordena a tabela gravada (ensaios antigos ou incompletos). */
export function normalizeInductionPoints(points?: InductionDetectorPoint[] | null): InductionDetectorPoint[] {
  const byVoltage = new Map((points || []).map(p => [Number(p.voltage_kV), p]));
  return INDUCTION_DETECTOR_VOLTAGES_KV.map(v => {
    const p = byVoltage.get(v);
    const d = p?.distance_cm;
    return {
      voltage_kV: v,
      distance_cm: typeof d === 'number' && isFinite(d) && d > 0 ? d : undefined,
      detected: p?.detected !== false
    };
  });
}

/** Formato brasileiro com 1 casa (5,0 / 13,8). */
export const fmtBr1 = (n?: number) => (typeof n === 'number' && isFinite(n) ? n.toFixed(1).replace('.', ',') : '');

export interface InductionDetectorEvaluation {
  result: 'APROVADO' | 'REPROVADO' | 'PENDENTE';
  rationale: string;
  missing: number[];
  notDetected: number[];
  /** Avisos que não reprovam (ex.: distância que não cresce com a tensão) */
  warnings: string[];
}

export function evaluateInductionDetector(points: InductionDetectorPoint[] | undefined, visualFailedItems: string[] = []): InductionDetectorEvaluation {
  const list = normalizeInductionPoints(points);
  const notDetected = list.filter(p => p.detected === false).map(p => p.voltage_kV);
  const missing = list.filter(p => p.detected !== false && !p.distance_cm).map(p => p.voltage_kV);
  const warnings: string[] = [];
  const filled = list.filter(p => p.detected !== false && p.distance_cm);
  for (let i = 1; i < filled.length; i++) {
    if ((filled[i].distance_cm || 0) < (filled[i - 1].distance_cm || 0)) {
      warnings.push(`Distância em ${fmtBr1(filled[i].voltage_kV)} kV (${fmtBr1(filled[i].distance_cm)} cm) menor que em ${fmtBr1(filled[i - 1].voltage_kV)} kV (${fmtBr1(filled[i - 1].distance_cm)} cm): confira a medição.`);
    }
  }
  const kvList = (vs: number[]) => vs.map(v => `${fmtBr1(v)} kV`).join(', ');
  const parts: string[] = [];
  let result: InductionDetectorEvaluation['result'] = 'APROVADO';

  if (visualFailedItems.length) {
    result = 'REPROVADO';
    parts.push(`Reprovado na inspeção visual: ${visualFailedItems.join('; ')}.`);
  }
  if (notDetected.length) {
    result = 'REPROVADO';
    parts.push(`O detector NÃO sinalizou a presença de tensão em ${kvList(notDetected)}: não atende às especificações do fabricante para a classe ${INDUCTION_DETECTOR_CLASS}.`);
  }
  if (result === 'APROVADO' && missing.length) {
    return {
      result: 'PENDENTE',
      rationale: `Preencha a distância de detecção obtida (cm) em: ${kvList(missing)}.`,
      missing, notDetected, warnings
    };
  }
  if (result === 'APROVADO') {
    const ds = filled.map(p => p.distance_cm || 0);
    parts.push(
      `Detector de tensão por indução (classe ${INDUCTION_DETECTOR_CLASS}) CONFORME: sinalizou a presença de tensão nas ${list.length} tensões ensaiadas ` +
      `(${fmtBr1(list[0].voltage_kV)} a ${fmtBr1(list[list.length - 1].voltage_kV)} kV), nas distâncias obtidas de ${fmtBr1(Math.min(...ds))} a ${fmtBr1(Math.max(...ds))} cm. ` +
      'Resultados satisfatórios conforme especificações do fabricante.'
    );
  }
  if (warnings.length) parts.push(`Observação: ${warnings.join(' ')}`);
  return { result, rationale: parts.join('\n\n'), missing, notDetected, warnings };
}
