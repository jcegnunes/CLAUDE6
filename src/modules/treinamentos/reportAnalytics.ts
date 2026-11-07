/**
 * Relatório analítico do desempenho dos alunos a partir do relatório de
 * conclusão da plataforma EAD e da lista de colaboradores: perfil de conclusão
 * por curso, por filial, por filial × curso, por aluno e pontos de atenção.
 * Funções puras.
 */
import { APPROVED_SITUATIONS, type ReportEntry, type RosterEntry } from './reportMerge';
import { formatCpf } from './rules';
import { normalizeText } from './spreadsheetImport';
import type { TrainingCourse } from './types';

export type ProgressStatus = 'aprovado' | 'reprovado' | 'andamento' | 'nao_iniciado';

export const STATUS_LABEL: Record<ProgressStatus, string> = {
  aprovado: 'Concluído e aprovado',
  reprovado: 'Reprovado',
  andamento: 'Em andamento',
  nao_iniciado: 'Não iniciado'
};

/** Situação de uma matrícula (aluno × curso) no relatório. */
export function progressStatus(e: Pick<ReportEntry, 'situation' | 'completion' | 'completedAt'>): ProgressStatus {
  const situation = normalizeText(e.situation);
  const completion = e.completion ?? 0;
  if (/reprovad/.test(situation)) return 'reprovado';
  if (APPROVED_SITUATIONS.includes(situation) && completion >= 100 && e.completedAt) return 'aprovado';
  return completion > 0 ? 'andamento' : 'nao_iniciado';
}

const LOWER = new Set(['da', 'de', 'do', 'das', 'dos', 'e', 'di', 'du']);
/** "UILTON SANTOS FERREIRA" -> "Uilton Santos Ferreira" (nomes já com minúsculas ficam como estão). */
export function displayName(name: string): string {
  const n = name.replace(/\s+/g, ' ').trim();
  if (n !== n.toUpperCase()) return n;
  return n.toLowerCase().split(' ').map((w, i) => (i > 0 && LOWER.has(w) ? w : w.charAt(0).toUpperCase() + w.slice(1))).join(' ');
}

/** Primeiro e último nome ("Darlan Antônio dos Santos" -> "Darlan Santos"). */
export function shortName(name: string): string {
  const parts = displayName(name).split(' ').filter(p => !LOWER.has(p));
  return parts.length > 1 ? `${parts[0]} ${parts[parts.length - 1]}` : parts[0] || name;
}

// capitais e cidades usadas como filial: "São Paulo" -> "São Paulo/SP"
const CITY_UF: Record<string, string> = {
  'sao paulo': 'SP', brasilia: 'DF', 'rio de janeiro': 'RJ', 'belo horizonte': 'MG', salvador: 'BA', curitiba: 'PR',
  'porto alegre': 'RS', florianopolis: 'SC', recife: 'PE', fortaleza: 'CE', goiania: 'GO', manaus: 'AM', belem: 'PA',
  vitoria: 'ES', natal: 'RN', 'joao pessoa': 'PB', maceio: 'AL', aracaju: 'SE', teresina: 'PI', 'sao luis': 'MA',
  cuiaba: 'MT', 'campo grande': 'MS', palmas: 'TO', 'porto velho': 'RO', 'rio branco': 'AC', macapa: 'AP', 'boa vista': 'RR',
  campinas: 'SP', santos: 'SP', 'sao jose dos campos': 'SP', joinville: 'SC', 'blumenau': 'SC'
};
const ACCENTED: Record<string, string> = { 'sao paulo': 'São Paulo', brasilia: 'Brasília', goiania: 'Goiânia', belem: 'Belém', vitoria: 'Vitória',
  'joao pessoa': 'João Pessoa', maceio: 'Maceió', 'sao luis': 'São Luís', cuiaba: 'Cuiabá', macapa: 'Macapá', florianopolis: 'Florianópolis', 'sao jose dos campos': 'São José dos Campos' };

/** Filial com acento e UF quando é uma cidade conhecida ("Sao Paulo" -> "São Paulo/SP"). */
export function branchLabel(branch: string): string {
  const b = branch.replace(/\s+/g, ' ').trim();
  if (!b || /\/\s*[A-Za-z]{2}$|-\s*[A-Za-z]{2}$/.test(b)) return b;
  const k = normalizeText(b);
  return CITY_UF[k] ? `${ACCENTED[k] || b}/${CITY_UF[k]}` : b;
}

export interface Metrics {
  /** Matrículas (aluno × curso) */
  enrollments: number;
  students: number;
  aprovado: number;
  reprovado: number;
  andamento: number;
  nao_iniciado: number;
  /** Média do % de conclusão das matrículas */
  avgCompletion: number;
  /** Aprovados / matrículas, em % */
  approvalRate: number;
}

export interface StudentProfile {
  name: string;
  cpf: string;
  branch: string;
  courses: Array<{ course: string; status: ProgressStatus; completion: number; completedAt: string | null }>;
  metrics: Metrics;
}

export interface Analytics {
  /** Turma / grupo da plataforma (o mais frequente no relatório) */
  group: string;
  summary: Metrics;
  byCourse: Array<{ course: string } & Metrics>;
  byBranch: Array<{ branch: string } & Metrics>;
  /** Filial × curso: situações e conclusão média de cada célula */
  matrix: { courses: string[]; rows: Array<{ branch: string; students: number; cells: Record<string, Metrics> }> };
  /** Alunos agrupados por filial (mais aprovações primeiro) */
  students: StudentProfile[];
  /** Pontos de atenção (texto pronto) */
  attention: string[];
  /** Desligados na lista da empresa (fora das contas) */
  removed: number;
}

export const NO_BRANCH = 'Sem filial';

interface Item { key: string; name: string; cpf: string; branch: string; course: string; status: ProgressStatus; completion: number; completedAt: string | null }

export function metricsOf(items: Array<Pick<Item, 'key' | 'status' | 'completion'>>): Metrics {
  const count = (s: ProgressStatus) => items.filter(i => i.status === s).length;
  const n = items.length;
  const avg = n ? items.reduce((t, i) => t + i.completion, 0) / n : 0;
  return {
    enrollments: n,
    students: new Set(items.map(i => i.key)).size,
    aprovado: count('aprovado'),
    reprovado: count('reprovado'),
    andamento: count('andamento'),
    nao_iniciado: count('nao_iniciado'),
    avgCompletion: Math.round(avg * 10) / 10,
    approvalRate: n ? Math.round((count('aprovado') / n) * 1000) / 10 : 0
  };
}

function groupBy<T>(items: T[], key: (i: T) => string): Map<string, T[]> {
  const map = new Map<string, T[]>();
  items.forEach(i => { const k = key(i); map.set(k, [...(map.get(k) || []), i]); });
  return map;
}

const pct = (n: number) => `${String(n).replace('.', ',')}%`;

/** Lista de nomes: "A, B, C e mais 2". */
function nameList(names: string[], max = 6): string {
  return names.length > max ? `${names.slice(0, max).join(', ')} e mais ${names.length - max}` : names.join(', ');
}

/**
 * Monta as métricas. O curso é o curso do sistema associado (sigla) quando há
 * associação; senão, o nome do curso no relatório. A filial vem da lista de
 * colaboradores (pelo CPF ou nome).
 */
export function buildAnalytics(
  report: ReportEntry[],
  roster: RosterEntry[],
  ctx: { mapping?: Record<string, string | undefined>; courses?: TrainingCourse[] } = {}
): Analytics {
  const byCpf = new Map(roster.filter(r => r.cpf).map(r => [r.cpf, r]));
  const byName = new Map(roster.map(r => [normalizeText(r.name), r]));
  const courseLabel = (text: string) => {
    const c = ctx.courses?.find(x => x.id === ctx.mapping?.[text]);
    return c ? (c.code || c.name) : text;
  };
  const removedKeys = new Set<string>();
  const items: Item[] = [];
  report.forEach(e => {
    const person = (e.cpf && byCpf.get(e.cpf)) || byName.get(normalizeText(e.name));
    const key = e.cpf || person?.cpf || normalizeText(e.name);
    if (person?.removed) { removedKeys.add(key); return; }
    items.push({
      key,
      name: displayName(person?.name || e.name),
      cpf: e.cpf || person?.cpf || '',
      branch: branchLabel(person?.branch || '') || NO_BRANCH,
      course: courseLabel(e.courseText),
      status: progressStatus(e),
      completion: Math.max(0, Math.min(100, e.completion ?? 0)),
      completedAt: e.completedAt
    });
  });

  const byCourse = Array.from(groupBy(items, i => i.course)).map(([course, list]) => ({ course, ...metricsOf(list) }))
    .sort((a, b) => a.course.localeCompare(b.course));
  const byBranch = Array.from(groupBy(items, i => i.branch)).map(([branch, list]) => ({ branch, ...metricsOf(list) }))
    .sort((a, b) => (a.branch === NO_BRANCH ? 1 : b.branch === NO_BRANCH ? -1 : b.enrollments - a.enrollments || a.branch.localeCompare(b.branch)));

  const courses = byCourse.map(c => c.course);
  const matrix = {
    courses,
    rows: byBranch.map(b => {
      const cells: Record<string, Metrics> = {};
      courses.forEach(c => {
        const list = items.filter(i => i.branch === b.branch && i.course === c);
        if (list.length) cells[c] = metricsOf(list);
      });
      return { branch: b.branch, students: b.students, cells };
    })
  };

  const branchOrder = new Map(byBranch.map((b, i) => [b.branch, i]));
  const students: StudentProfile[] = Array.from(groupBy(items, i => i.key)).map(([, list]) => ({
    name: list[0].name,
    cpf: list[0].cpf ? formatCpf(list[0].cpf) : '',
    branch: list[0].branch,
    courses: list.map(i => ({ course: i.course, status: i.status, completion: i.completion, completedAt: i.completedAt }))
      .sort((a, b) => a.course.localeCompare(b.course)),
    metrics: metricsOf(list)
  })).sort((a, b) => (branchOrder.get(a.branch)! - branchOrder.get(b.branch)!)
    || b.metrics.aprovado - a.metrics.aprovado || b.metrics.avgCompletion - a.metrics.avgCompletion || a.name.localeCompare(b.name));

  // ----------------------------------------------------- pontos de atenção
  const attention: string[] = [];
  const failedDone = items.filter(i => i.status === 'reprovado' && i.completion >= 100);
  if (failedDone.length) {
    const perCourse = Array.from(groupBy(failedDone, i => i.course)).sort((a, b) => b[1].length - a[1].length).map(([c, l]) => `${c} (${l.length})`);
    attention.push(`${failedDone.length} reprovação(ões) após concluir 100% do conteúdo, envolvendo ${new Set(failedDone.map(i => i.key)).size} aluno(s): ${perCourse.join(', ')}. Programar nova avaliação.`);
  }
  byCourse.filter(c => c.enrollments && c.nao_iniciado === c.enrollments)
    .forEach(c => attention.push(`${c.course}: nenhum dos ${c.enrollments} matriculados iniciou o curso.`));
  const notStarted = students.filter(s => s.metrics.nao_iniciado === s.metrics.enrollments);
  if (notStarted.length) attention.push(`${notStarted.length} aluno(s) ainda não iniciaram nenhum curso: ${nameList(notStarted.map(s => s.name))}.`);
  const ongoing = items.filter(i => i.status === 'andamento').sort((a, b) => a.name.localeCompare(b.name));
  if (ongoing.length) attention.push(`${ongoing.length} matrícula(s) em andamento: ${nameList(ongoing.map(i => `${shortName(i.name)} (${i.course}, ${pct(i.completion)})`), 8)}.`);
  const noBranch = students.filter(s => s.branch === NO_BRANCH);
  if (!roster.length) attention.push('Lista de colaboradores não enviada: perfil por filial indisponível e certificados sem filial.');
  else if (noBranch.length) attention.push(`${noBranch.length} aluno(s) sem filial reconhecida na lista de colaboradores: ${nameList(noBranch.map(s => s.name), 8)}. O certificado sai com a cidade do laboratório.`);

  const groups = Array.from(groupBy(report.filter(r => r.group), r => r.group!)).sort((a, b) => b[1].length - a[1].length);
  return { group: groups[0]?.[0] || '', summary: metricsOf(items), byCourse, byBranch, matrix, students, attention, removed: removedKeys.size };
}

const br = (iso: string | null) => (iso ? iso.split('-').reverse().join('/') : '');

/** Abas do Excel do relatório analítico (linhas prontas para aoa_to_sheet). */
export function analyticsSheets(a: Analytics, meta: { title: string; generatedAt: string }): Array<{ name: string; rows: Array<Array<string | number>> }> {
  const head = ['Matrículas', 'Alunos', 'Aprovados', 'Reprovados', 'Em andamento', 'Não iniciados', 'Conclusão média (%)', 'Aprovação (%)'];
  const vals = (m: Metrics) => [m.enrollments, m.students, m.aprovado, m.reprovado, m.andamento, m.nao_iniciado, m.avgCompletion, m.approvalRate];
  return [
    {
      name: 'Resumo',
      rows: [[meta.title], [`${a.group ? `${a.group} · ` : ''}Gerado em ${meta.generatedAt}`], [], head, vals(a.summary),
        ...(a.removed ? [[], [`${a.removed} desligado(s) fora das contas`]] : []),
        ...(a.attention.length ? [[], ['Pontos de atenção'], ...a.attention.map(t => [`• ${t}`])] : [])]
    },
    { name: 'Por curso', rows: [['Curso', ...head], ...a.byCourse.map(c => [c.course, ...vals(c)])] },
    { name: 'Por filial', rows: [['Filial', ...head], ...a.byBranch.map(b => [b.branch, ...vals(b)])] },
    {
      name: 'Filial x Curso',
      rows: [['Filial', ...a.matrix.courses],
        ...a.matrix.rows.map(r => [r.branch, ...a.matrix.courses.map(c => (r.cells[c] ? `${r.cells[c].aprovado}/${r.cells[c].enrollments} (${pct(r.cells[c].avgCompletion)})` : '—'))]),
        ['Total da turma', ...a.byCourse.map(c => `${c.aprovado}/${c.enrollments} (${pct(c.avgCompletion)})`)]]
    },
    {
      name: 'Alunos',
      rows: [['Aluno', 'CPF', 'Filial', 'Curso', 'Situação', 'Conclusão (%)', 'Data de conclusão'],
        ...a.students.flatMap(s => s.courses.map(c => [s.name, s.cpf, s.branch, c.course, STATUS_LABEL[c.status], c.completion, br(c.completedAt)]))]
    }
  ];
}

export const formatPct = pct;
