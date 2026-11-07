/**
 * Cadastro de curso a partir do texto da Norma Regulamentadora: o texto é lido
 * no aparelho (PDF com texto ou TXT) e analisado pela função "analisar-norma"
 * do servidor (IA). A proposta volta para o formulário do curso, para conferir
 * antes de salvar. normalizeProposal é pura (testada em __tests__).
 */
import { SupabaseService } from '../../services/supabaseService';
import type { TrainingCourse } from './types';

export const NORM_FUNCTION = 'analisar-norma';

export interface NormTopic { title: string; hours: number; normItem: string }

export interface NormCourseProposal {
  code: string;
  name: string;
  kind: string;
  normReference: string;
  workloadHours: number;
  validityMonths: number;
  modality: TrainingCourse['modality'];
  minAttendance: number;
  minGrade: number | null;
  prerequisite: string;
  notes: string;
  topicHoursFromNorm: boolean;
  topics: NormTopic[];
  evidence: string;
}

export interface NormAnalysis {
  norm: { code: string; title: string; revision: string };
  courses: NormCourseProposal[];
  warnings: string[];
}

/** Nota usada nos cursos da plataforma quando a norma não fixa nota mínima. */
export const DEFAULT_MIN_GRADE = 7;

/** Texto do arquivo da norma: PDF (texto de todas as páginas) ou TXT. */
export async function extractNormText(file: File): Promise<{ text: string; pages: number }> {
  if (/\.txt$/i.test(file.name) || file.type === 'text/plain') {
    return { text: await file.text(), pages: 0 };
  }
  if (!/\.pdf$/i.test(file.name) && file.type !== 'application/pdf') throw new Error('Envie a norma em PDF ou TXT.');
  // build "legacy": funciona também em navegadores/WebViews mais antigos
  const pdfjs = await import('pdfjs-dist/legacy/build/pdf.mjs');
  const workerUrl = (await import('pdfjs-dist/legacy/build/pdf.worker.min.mjs?url')).default;
  pdfjs.GlobalWorkerOptions.workerSrc = workerUrl;
  const pdf = await pdfjs.getDocument({ data: new Uint8Array(await file.arrayBuffer()), isEvalSupported: false }).promise;
  try {
    const parts: string[] = [];
    for (let n = 1; n <= pdf.numPages; n++) {
      const page = await pdf.getPage(n);
      const content = await page.getTextContent();
      // quebra de linha onde o PDF marca fim de linha; espaço entre os demais trechos
      parts.push(content.items.map((it: any) => `${it.str ?? ''}${it.hasEOL ? '\n' : ' '}`).join(''));
    }
    const text = parts.join('\n\n').replace(/[ \t]+\n/g, '\n').replace(/\n{3,}/g, '\n\n').trim();
    return { text, pages: pdf.numPages };
  } finally {
    await pdf.destroy();
  }
}

/** Arredonda para múltiplos de 0,5 h. */
const half = (n: number) => Math.round(n * 2) / 2;
const num = (v: unknown, fallback: number) => (typeof v === 'number' && Number.isFinite(v) ? v : fallback);
const str = (v: unknown) => (typeof v === 'string' ? v.trim() : '');

/**
 * Confere e ajusta a proposta da IA antes de ir para o formulário: números nos
 * limites do cadastro, horas em múltiplos de 0,5 e avisos quando a soma dos
 * tópicos não fecha com a carga horária.
 */
export function normalizeProposal(raw: unknown): NormAnalysis {
  const r = (raw || {}) as Record<string, any>;
  const warnings: string[] = Array.isArray(r.warnings) ? r.warnings.map(str).filter(Boolean) : [];
  const courses: NormCourseProposal[] = (Array.isArray(r.courses) ? r.courses : []).map((c: any) => {
    const topics: NormTopic[] = (Array.isArray(c?.topics) ? c.topics : [])
      .map((t: any) => ({ title: str(t?.title), hours: Math.max(0, half(num(t?.hours, 0))), normItem: str(t?.normItem) }))
      .filter((t: NormTopic) => t.title);
    const workload = Math.max(0, half(num(c?.workloadHours, 0)));
    const modality = (['presencial', 'semipresencial', 'ead'] as const).includes(c?.modality) ? c.modality : 'presencial';
    const grade = c?.minGrade === null || c?.minGrade === undefined ? null : Math.min(10, Math.max(0, num(c.minGrade, DEFAULT_MIN_GRADE)));
    const p: NormCourseProposal = {
      code: str(c?.code).toUpperCase(),
      name: str(c?.name),
      kind: str(c?.kind) || 'outro',
      normReference: str(c?.normReference),
      workloadHours: workload,
      validityMonths: Math.max(0, Math.round(num(c?.validityMonths, 0))),
      modality,
      minAttendance: Math.min(100, Math.max(0, Math.round(num(c?.minAttendance, 100)))),
      minGrade: grade,
      prerequisite: str(c?.prerequisite),
      notes: str(c?.notes),
      topicHoursFromNorm: c?.topicHoursFromNorm === true,
      topics,
      evidence: str(c?.evidence)
    };
    const sum = topics.reduce((t, x) => t + x.hours, 0);
    if (topics.length && workload && Math.abs(sum - workload) > 0.01) {
      warnings.push(`${p.code || p.name}: a soma dos tópicos (${sum} h) é diferente da carga horária (${workload} h). Ajuste antes de salvar.`);
    }
    return p;
  }).filter((c: NormCourseProposal) => c.name || c.code);
  return {
    norm: { code: str(r.norm?.code), title: str(r.norm?.title), revision: str(r.norm?.revision) },
    courses,
    warnings
  };
}

/** Campos do curso preenchidos com a proposta (o resto do cadastro continua como estava). */
export function proposalToCourse(p: NormCourseProposal, base: TrainingCourse): TrainingCourse {
  return {
    ...base,
    code: p.code || base.code,
    name: p.name || base.name,
    normReference: p.normReference || base.normReference,
    workloadHours: p.workloadHours || base.workloadHours,
    validityMonths: p.validityMonths,
    modality: p.modality,
    minAttendance: p.minAttendance,
    // a norma não fixa nota: fica a nota padrão dos cursos da plataforma
    minGrade: p.minGrade ?? DEFAULT_MIN_GRADE,
    prerequisite: p.prerequisite,
    notes: p.notes,
    topics: p.topics.length ? p.topics.map(t => ({ title: t.normItem ? `${t.title} (${t.normItem})` : t.title, hours: t.hours })) : base.topics
  };
}

/** Envia o texto para a função do servidor e devolve a análise conferida. */
export async function analyzeNormText(text: string, fileName: string): Promise<NormAnalysis> {
  if (typeof navigator !== 'undefined' && navigator.onLine === false) throw new Error('A análise da norma por IA exige internet.');
  const { data, error } = await SupabaseService.getClient().functions.invoke(NORM_FUNCTION, { body: { text, fileName } });
  if (error) {
    const msg = String((error as { message?: string }).message || error);
    if (/not found|404|Failed to send|FunctionsFetchError|FunctionsRelayError/i.test(msg)) {
      throw new Error('O servidor ainda não tem a função "analisar-norma" (veja MUDANCAS_V7.md, "Curso a partir da norma").');
    }
    throw new Error(msg);
  }
  const d = (typeof data === 'string' ? JSON.parse(data) : data) as { analysis?: unknown; error?: string };
  if (d?.error) throw new Error(d.error);
  return normalizeProposal(d?.analysis);
}
