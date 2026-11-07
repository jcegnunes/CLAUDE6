/**
 * Planilha de emissão a partir de dois arquivos: o relatório da plataforma EAD
 * (aluno, CPF, curso, % de conclusão, data e situação) e a lista de colaboradores
 * da empresa (nome, CPF, filial, data da liberação). Junta os dois pelo CPF e gera
 * as linhas no formato do modelo de importação. Funções puras (testadas em __tests__).
 */
import { formatCpf, isValidCpf, onlyDigits } from './rules';
import { normalizeText, parseDateCell, parseNumberCell } from './spreadsheetImport';
import type { TrainingCertificate, TrainingCourse } from './types';

export type Cell = unknown;

/** Linha do relatório EAD (uma por aluno e curso). */
export interface ReportEntry {
  line: number;
  name: string;
  cpf: string;
  /** Curso como está no relatório (ex.: "Trabalho em Altura - NR35 - Turma 2/2026") */
  courseText: string;
  completion?: number;
  completedAt: string | null;
  situation: string;
}

/** Colaborador da lista da empresa. */
export interface RosterEntry {
  line: number;
  name: string;
  cpf: string;
  branch: string;
  releasedAt: string | null;
  /** Abaixo da linha "Desligados": removido da turma */
  removed: boolean;
}

/** Situações do relatório que dão direito ao certificado (com 100% de conclusão). */
export const APPROVED_SITUATIONS = ['aprovado', 'sem media exigida', 'concluido'];

const REPORT_COLS = {
  name: ['aluno', 'nome', 'colaborador', 'participante'],
  cpf: ['cpf'],
  course: ['curso', 'treinamento'],
  completion: ['conclusao', '% conclusao', 'percentual de conclusao', 'progresso'],
  date: ['data de conclusao', 'data conclusao', 'concluido em'],
  situation: ['situacao', 'status', 'resultado']
};
const ROSTER_COLS = {
  name: ['colaborador', 'nome', 'aluno', 'participante', 'funcionario'],
  cpf: ['cpf'],
  branch: ['filial', 'unidade', 'cidade'],
  released: ['data da liberacao', 'data de liberacao', 'liberacao', 'data liberacao']
};

const cleanName = (v: Cell) => String(v ?? '').replace(/\s+/g, ' ').trim();
const cellText = (v: Cell) => String(v ?? '').trim();

/** CPF com 11 dígitos (o Excel costuma perder o zero inicial). */
export function normalizeCpf(v: Cell): string {
  const d = onlyDigits(String(v ?? ''));
  if (d.length < 9 || d.length > 11) return '';
  return d.padStart(11, '0');
}

/** Acha a linha de títulos e a coluna de cada campo (títulos sem acento/maiúsculas). */
function locateColumns<K extends string>(rows: Cell[][], cols: Record<K, string[]>, required: K[]): { header: number; index: Partial<Record<K, number>> } | null {
  for (let r = 0; r < Math.min(rows.length, 15); r++) {
    const titles = (rows[r] || []).map(c => normalizeText(c));
    const index: Partial<Record<K, number>> = {};
    (Object.keys(cols) as K[]).forEach(k => {
      const i = titles.findIndex(t => cols[k].includes(t));
      if (i >= 0) index[k] = i;
    });
    if (required.every(k => index[k] !== undefined)) return { header: r, index };
  }
  return null;
}

/** Texto CSV (separado por ";", "," ou TAB, com aspas) -> linhas. */
export function parseCsv(text: string): string[][] {
  const src = text.replace(/^﻿/, '');
  // o relatório padrão começa com linhas de informação ("Turma / Grupo: …") sem separador:
  // vale o separador que mais aparece numa das primeiras linhas
  const count = (line: string, ch: string) => line.split(ch).length - 1;
  const head = src.split(/\r?\n/, 20);
  const sep = [';', '\t', ','].reduce((best, ch) =>
    Math.max(...head.map(l => count(l, ch))) > Math.max(...head.map(l => count(l, best))) ? ch : best, ';');
  const rows: string[][] = [];
  let row: string[] = [];
  let cell = '';
  let quoted = false;
  for (let i = 0; i < src.length; i++) {
    const ch = src[i];
    if (quoted) {
      if (ch === '"' && src[i + 1] === '"') { cell += '"'; i++; }
      else if (ch === '"') quoted = false;
      else cell += ch;
    } else if (ch === '"') quoted = true;
    else if (ch === sep) { row.push(cell); cell = ''; }
    else if (ch === '\n' || ch === '\r') {
      if (ch === '\r' && src[i + 1] === '\n') i++;
      row.push(cell); rows.push(row); row = []; cell = '';
    } else cell += ch;
  }
  if (cell || row.length) { row.push(cell); rows.push(row); }
  return rows.filter(r => r.some(c => c.trim()));
}

export function parseReport(rows: Cell[][]): ReportEntry[] {
  const loc = locateColumns(rows, REPORT_COLS, ['name', 'cpf', 'course', 'situation']);
  if (!loc) throw new Error('Relatório de conclusão: não encontrei as colunas Aluno, CPF, Curso e Situação.');
  const { header, index: ix } = loc;
  const at = (r: Cell[], k: keyof typeof REPORT_COLS) => (ix[k] === undefined ? '' : r[ix[k]!]);
  const out: ReportEntry[] = [];
  rows.slice(header + 1).forEach((r, i) => {
    const name = cleanName(at(r, 'name'));
    const courseText = cleanName(at(r, 'course'));
    if (!name && !courseText) return;
    const dateCell = at(r, 'date');
    // relatório em Excel: célula de percentual vem como fração (1 = 100%)
    const rawCompletion = at(r, 'completion');
    const completion = typeof rawCompletion === 'number' && rawCompletion <= 1 ? rawCompletion * 100 : parseNumberCell(rawCompletion);
    out.push({
      line: header + i + 2,
      name,
      cpf: normalizeCpf(at(r, 'cpf')),
      courseText,
      completion,
      completedAt: cellText(dateCell) === '-' ? null : parseDateCell(dateCell),
      situation: cleanName(at(r, 'situation'))
    });
  });
  return out;
}

export function parseRoster(rows: Cell[][]): RosterEntry[] {
  const loc = locateColumns(rows, ROSTER_COLS, ['name', 'cpf']);
  if (!loc) throw new Error('Lista de colaboradores: não encontrei as colunas Colaborador (ou Nome) e CPF.');
  const { header, index: ix } = loc;
  const at = (r: Cell[], k: keyof typeof ROSTER_COLS) => (ix[k] === undefined ? '' : r[ix[k]!]);
  const out: RosterEntry[] = [];
  let removed = false;
  rows.slice(header + 1).forEach((r, i) => {
    // seção "Desligados / removidos da turma": quem vem depois não recebe certificado
    if ((r || []).slice(0, 3).some(c => /^(desligad|removid)/.test(normalizeText(c)))) { removed = true; return; }
    const name = cleanName(at(r, 'name'));
    const cpf = normalizeCpf(at(r, 'cpf'));
    if (!name && !cpf) return;
    out.push({
      line: header + i + 2,
      name,
      cpf,
      // "Sao Paulo  ok" -> "Sao Paulo" (anotação de conferência na planilha)
      branch: cleanName(at(r, 'branch')).replace(/\s+ok$/i, ''),
      releasedAt: parseDateCell(at(r, 'released')),
      removed
    });
  });
  return out;
}

/** Número da NR no texto ("NR35", "NR - 10", "NR-06" -> "35", "10", "06"). */
export function nrNumber(text: string): string | null {
  const m = normalizeText(text).match(/\bnr\s*-?\s*(\d{1,2})\b/);
  return m ? m[1].padStart(2, '0') : null;
}

const STOP = new Set(['de', 'da', 'do', 'das', 'dos', 'e', 'em', 'a', 'o', 'para', 'com', 'no', 'na', 'turma', 'nr', 'treinamento', 'curso', 'regulamentar']);
const words = (t: string) => new Set(normalizeText(t).replace(/[^a-z0-9 ]/g, ' ').split(' ').filter(w => w.length > 1 && !STOP.has(w) && !/^\d+$/.test(w)));

/** Curso do sistema mais parecido com o curso do relatório (mesma NR; reciclagem só se o relatório disser). */
export function suggestCourse(courseText: string, courses: TrainingCourse[]): TrainingCourse | undefined {
  const nr = nrNumber(courseText);
  if (!nr) return undefined;
  const isRecycle = /reciclag|periodic/.test(normalizeText(courseText));
  const candidates = courses.filter(c => nrNumber(`${c.code} ${c.name}`) === nr);
  const sameKind = candidates.filter(c => /reciclag|periodic/.test(normalizeText(`${c.code} ${c.name}`)) === isRecycle);
  const pool = sameKind.length ? sameKind : candidates;
  const want = words(courseText);
  let best: TrainingCourse | undefined;
  let bestScore = -1;
  pool.forEach(c => {
    const have = words(`${c.code} ${c.name}`);
    let score = 0;
    want.forEach(w => { if (have.has(w)) score++; });
    // empate: o curso "básico" da NR (ex.: NR-10 Básico antes do SEP)
    if (/basico/.test(normalizeText(c.code))) score += 0.5;
    if (score > bestScore) { best = c; bestScore = score; }
  });
  return best;
}

export interface SheetRow {
  name: string;
  cpf: string;
  company: string;
  branch: string;
  course: TrainingCourse;
  courseText: string;
  startDate: string;
  endDate: string;
  completion: number;
  warnings: string[];
}

export interface SkippedRow {
  name: string;
  cpf: string;
  courseText: string;
  reason: string;
}

const DAY = 86400000;

/**
 * Junta relatório e lista. Recebe certificado quem tem 100% de conclusão, data de
 * conclusão e situação aprovada, num curso associado a um curso do sistema e sem
 * estar entre os desligados. Início = data da liberação (quando coerente), término =
 * data de conclusão.
 */
export function mergeReports(
  report: ReportEntry[],
  roster: RosterEntry[],
  ctx: {
    mapping: Record<string, string | undefined>; courses: TrainingCourse[]; company: string;
    /** Certificados já emitidos: quem já tem um válido do mesmo curso fica de fora */
    existing?: Array<Pick<TrainingCertificate, 'courseId' | 'participantCpf' | 'status' | 'certificateNumber'>>;
  }
): { rows: SheetRow[]; skipped: SkippedRow[] } {
  const byCpf = new Map(roster.filter(r => r.cpf).map(r => [r.cpf, r]));
  const byName = new Map(roster.map(r => [normalizeText(r.name), r]));
  const rows: SheetRow[] = [];
  const skipped: SkippedRow[] = [];
  const seen = new Map<string, SheetRow>();

  report.forEach(e => {
    const person = (e.cpf && byCpf.get(e.cpf)) || byName.get(normalizeText(e.name));
    const name = person?.name || e.name;
    const cpf = e.cpf || person?.cpf || '';
    const skip = (reason: string) => skipped.push({ name, cpf: cpf ? formatCpf(cpf) : '', courseText: e.courseText, reason });

    const situation = normalizeText(e.situation);
    if (!APPROVED_SITUATIONS.includes(situation)) return skip(e.situation ? `Situação: ${e.situation}` : 'Sem situação');
    if ((e.completion ?? 0) < 100) return skip(`Conclusão de ${e.completion ?? 0}%`);
    if (!e.completedAt) return skip('Sem data de conclusão');
    if (person?.removed) return skip('Desligado / removido da turma');
    const courseId = ctx.mapping[e.courseText];
    const course = ctx.courses.find(c => c.id === courseId);
    if (!course) return skip('Curso sem correspondência no sistema');
    // a importação só barra repetido com a mesma data de término; aqui vale qualquer data
    const issued = cpf ? ctx.existing?.find(c => c.status === 'valido' && c.courseId === course.id && onlyDigits(c.participantCpf) === cpf) : undefined;
    if (issued) return skip(`Já tem certificado válido (${issued.certificateNumber})`);

    const warnings: string[] = [];
    if (!person) warnings.push('Não está na lista de colaboradores');
    if (cpf && !isValidCpf(cpf)) warnings.push('CPF inválido');
    if (!cpf) warnings.push('Sem CPF');
    const released = person?.releasedAt;
    const end = e.completedAt;
    // liberação depois da conclusão ou mais de 1 ano antes: provável erro de digitação
    const coherent = released && released <= end && Date.parse(end) - Date.parse(released) <= 366 * DAY;
    if (released && !coherent) warnings.push('Data da liberação incoerente: início = conclusão');

    const row: SheetRow = {
      name, cpf: cpf ? formatCpf(cpf) : '', company: ctx.company.trim(), branch: person?.branch || '',
      course, courseText: e.courseText, startDate: coherent ? released! : end, endDate: end,
      completion: e.completion ?? 100, warnings
    };
    // mesma pessoa e curso repetidos no relatório: fica a conclusão mais recente
    const key = `${cpf || normalizeText(name)}|${course.id}`;
    const prev = seen.get(key);
    if (prev) {
      if (prev.endDate >= row.endDate) return skip('Repetido no relatório');
      skipped.push({ name, cpf: row.cpf, courseText: prev.courseText, reason: 'Repetido no relatório' });
      rows.splice(rows.indexOf(prev), 1);
    }
    seen.set(key, row);
    rows.push(row);
  });

  rows.sort((a, b) => a.course.code.localeCompare(b.course.code) || a.name.localeCompare(b.name));
  return { rows, skipped };
}

const br = (iso: string) => iso.split('-').reverse().join('/');

/** Títulos da planilha gerada (mesmos aceitos pela importação; "Curso no relatório" é só conferência). */
export const SHEET_HEADERS = ['Nome', 'CPF', 'Colaborador da Empresa', 'Filial', 'Curso', 'Início', 'Término', 'Presença (%)', 'Curso no relatório', 'Avisos'];

/** Linhas no formato da importação de certificados (objetos por título). */
export function toImportRecords(rows: SheetRow[]): Array<Record<string, string>> {
  return rows.map(r => ({
    'Nome': r.name,
    'CPF': r.cpf,
    'Colaborador da Empresa': r.company,
    'Filial': r.branch,
    'Curso': r.course.code || r.course.name,
    'Início': br(r.startDate),
    'Término': br(r.endDate),
    'Presença (%)': String(r.completion),
    'Curso no relatório': r.courseText,
    'Avisos': r.warnings.join('; ')
  }));
}
