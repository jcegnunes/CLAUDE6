/**
 * Leitura do texto de uma Norma Regulamentadora SEM IA: regras sobre a forma
 * como as NRs escrevem a capacitação. Funções puras (testadas em __tests__).
 *
 * Dois formatos são reconhecidos:
 *  - cursos em itens (NR-36, NR-35, NR-10...): "treinamento inicial ... com, no
 *    mínimo, quatro horas", "treinamento periódico anual com carga horária de
 *    duas horas"; o conteúdo vem das listas "a) ... b) ..." dos itens que dizem
 *    que os trabalhadores devem ser treinados sobre / que os treinamentos devem incluir;
 *  - cursos em anexo (NR-20...): "Conteúdo programático" com "a) Curso de ...",
 *    "Carga horária: N horas", tópicos numerados (teórico/prático) e tabelas de
 *    carga horária e periodicidade.
 * O texto extraído de PDF costuma vir com palavras partidas ("mí nimo"), por isso
 * as palavras-chave aceitam espaços soltos entre as letras.
 */
import type { NormAnalysis, NormCourseProposal, NormTopic } from './normAnalysis';
import { STANDARD_VALIDITY_MONTHS } from './defaultCourses';

const strip = (s: string) => s.normalize('NFD').replace(/[̀-ͯ]/g, '');
/** Minúsculas, sem acento e com espaços simples (para procurar palavras). */
const norm = (s: string) => strip(s).toLowerCase().replace(/\s+/g, ' ').trim();
/** Expressão que aceita espaços soltos entre as letras ("carga horaria" casa com "carga hor aria"). */
const loose = (word: string) => strip(word).toLowerCase().split('').map(ch => (ch === ' ' ? '\\s+' : `${ch.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\s?`)).join('');
const has = (text: string, word: string) => new RegExp(loose(word), 'i').test(norm(text));

/** Texto limpo para exibir: espaços simples, sem quebra de linha. */
const clean = (s: string) => s.replace(/\s+/g, ' ').replace(/\s+([,;:.)])/g, '$1').replace(/\(\s+/g, '(').trim();

const NUMBER_WORDS: Record<string, number> = {
  um: 1, uma: 1, dois: 2, duas: 2, tres: 3, quatro: 4, cinco: 5, seis: 6, sete: 7, oito: 8, nove: 9, dez: 10,
  onze: 11, doze: 12, treze: 13, quatorze: 14, catorze: 14, quinze: 15, dezesseis: 16, dezoito: 18, vinte: 20,
  'vinte e quatro': 24, trinta: 30, 'trinta e dois': 32, quarenta: 40, cinquenta: 50, sessenta: 60
};

/** Horas citadas no trecho: "8 (oito) horas", "quatro horas", "40h". Devolve a primeira. */
export function hoursIn(text: string): number | null {
  const t = norm(text);
  const digit = t.match(/(\d+(?:[.,]\d+)?)\s*(?:\([a-z ]+\)\s*)?(?:h\b|horas?\b)/);
  if (digit) return Number(digit[1].replace(',', '.'));
  const words = Object.keys(NUMBER_WORDS).sort((a, b) => b.length - a.length).join('|');
  const word = t.match(new RegExp(`\\b(${words})\\s+horas?\\b`));
  return word ? NUMBER_WORDS[word[1]] : null;
}

/** Periodicidade em meses: anual 12, bienal 24, trienal 36, "a cada dois anos" 24. */
export function periodMonths(text: string): number | null {
  const t = norm(text);
  if (/\bsemestral/.test(t)) return 6;
  if (/\btrienal|a cada (3|tres) anos/.test(t)) return 36;
  if (/\bbienal|a cada (2|dois) anos|bianual/.test(t)) return 24;
  if (/\banual|a cada (1|um) ano\b|anualmente/.test(t)) return 12;
  const months = t.match(/a cada (\d+) meses/);
  return months ? Number(months[1]) : null;
}

interface Item { number: string; text: string; list: string[]; line: number }

const ITEM_RE = /^\s*(\d{1,2}(?:\s?\.\s?\d+)+)\s+(.*)$/;
const LETTER_RE = /^\s*([a-z])\s?\)\s+(.*)$/;

/** Separa o texto em itens numerados ("36.16.4 ..."), com as alíneas "a) b) c)" de cada um. */
function splitItems(lines: string[]): Item[] {
  const items: Item[] = [];
  let cur: Item | null = null;
  let inList = false;
  lines.forEach((raw, i) => {
    const line = raw.replace(/\s+/g, ' ').trim();
    if (!line) return;
    if (/^(este texto nao substitui|anexo\b)/i.test(norm(line))) { if (/^anexo\b/i.test(norm(line))) cur = null; return; }
    const it = line.match(ITEM_RE);
    if (it && !/^\d{1,2}\.\d{1,2}\.\d{2,4}$/.test(it[1])) {
      cur = { number: it[1].replace(/\s/g, ''), text: it[2], list: [], line: i };
      items.push(cur);
      inList = false;
      return;
    }
    if (!cur) return;
    const letter = line.match(LETTER_RE);
    if (letter) { cur.list.push(letter[2]); inList = true; return; }
    if (inList) cur.list[cur.list.length - 1] += ` ${line}`;
    else cur.text += ` ${line}`;
  });
  return items.map(i => ({ ...i, text: clean(i.text), list: i.list.map(l => clean(l).replace(/[;,.]?\s*(e|ou)?$/i, '').replace(/;$/, '')) }));
}

/** Código e título da norma ("NR 36 - SEGURANÇA ..."). */
function normHeader(text: string): { code: string; title: string; revision: string } {
  const head = text.slice(0, 4000);
  const m = head.match(/NR\s*-?\s*(\d{1,2})\s*-\s*([\s\S]*?)(?:\n\s*Publica|\n\s*\d{1,2}\.\d|$)/);
  const any = head.match(/NR\s*-?\s*(\d{1,2})/);
  const code = m ? `NR-${m[1].padStart(2, '0')}` : any ? `NR-${any[1].padStart(2, '0')}` : '';
  const title = m ? clean(m[2]).slice(0, 160) : '';
  // última portaria do histórico: "Portaria MTP nº 4.219, de 20 de dezembro de 2022"
  const portarias = head.split(/\r?\n/)
    .map(l => l.match(/Portaria\s+(?:[A-Za-z]+\s+)?n\s*[º°o]\s*([\d.]+)\s*,?\s*de\s.*?\b(19|20)(\d{2})\b/))
    .filter((p): p is RegExpMatchArray => !!p);
  // a mais recente (maior ano; no empate, a última citada)
  const last = portarias.reduce<RegExpMatchArray | undefined>((best, p) => (!best || Number(p[2] + p[3]) >= Number(best[2] + best[3]) ? p : best), undefined);
  const revision = last ? `Portaria nº ${last[1]}/${last[2]}${last[3]}` : '';
  return { code, title: titleCase(title), revision };
}

const SMALL = new Set(['de', 'da', 'do', 'das', 'dos', 'e', 'em', 'nas', 'nos', 'na', 'no', 'a', 'o', 'com', 'para', 'por']);
function titleCase(s: string): string {
  if (s !== s.toUpperCase()) return s;
  return s.toLowerCase().split(' ').map((w, i) => (i > 0 && SMALL.has(w) ? w : w.charAt(0).toUpperCase() + w.slice(1))).join(' ');
}

/** Divide a carga horária entre os tópicos em múltiplos de 0,5 h (sobra vai para os primeiros). */
export function splitHours(total: number, count: number): number[] {
  if (!count) return [];
  const units = Math.round(total * 2);
  const base = Math.floor(units / count);
  const rest = units - base * count;
  return Array.from({ length: count }, (_, i) => (base + (i < rest ? 1 : 0)) / 2);
}

const cap = (s: string) => s.charAt(0).toUpperCase() + s.slice(1);

/**
 * Tópicos com as horas divididas. Com mais tópicos do que blocos de 0,5 h
 * (ex.: 12 alíneas num curso de 4 h), alíneas vizinhas são agrupadas num tópico
 * só, para nenhum tópico ficar com 0 h.
 */
export function topicsFrom(titles: Array<{ title: string; normItem: string }>, hours: number): NormTopic[] {
  const units = Math.max(1, Math.round(hours * 2));
  let groups = titles.map(t => [t]);
  if (titles.length > units) {
    groups = Array.from({ length: units }, () => [] as typeof titles);
    titles.forEach((t, i) => groups[Math.floor((i * units) / titles.length)].push(t));
  }
  const h = splitHours(hours, groups.length);
  return groups.map((g, i) => ({
    title: g.map((t, j) => (j === 0 ? cap(t.title) : t.title.charAt(0).toLowerCase() + t.title.slice(1))).join('; '),
    normItem: [...new Set(g.map(t => t.normItem))].join(', '),
    hours: h[i]
  }));
}

// ---------------------------------------------------------------- cursos em itens
const TRAINING = ['treinamento', 'capacitacao', 'curso'];
const isTraining = (s: string) => TRAINING.some(w => has(s, w));
/** Alínea que apresenta conteúdo de treinamento ("devem estar treinados ... sobre:", "devem incluir ... os seguintes itens:"). */
const isContentIntro = (it: Item) => it.list.length > 0 && (has(it.text, 'trein') || has(it.text, 'capacitac') || has(it.text, 'curso'))
  && /(sobre|seguintes|incluir|conteudo|abordar|contemplar|programa)/.test(norm(it.text))
  && !/(participacao de|composta por|ministrad[oa]s? por)/.test(norm(it.text));

function itemCourses(items: Item[], nr: string, title: string): { courses: NormCourseProposal[]; warnings: string[] } {
  const warnings: string[] = [];
  const found: Array<{ it: Item; kind: string; hours: number; months: number | null }> = [];
  items.forEach(it => {
    const full = `${it.text} ${it.list.join(' ')}`;
    if (!isTraining(it.text)) return;
    const t = norm(it.text);
    const hours = hoursIn(it.text);
    if (/eventual/.test(t) && hours === null) { warnings.push(`Treinamento eventual (${nr}, item ${it.number}): sem carga horária própria; realizar quando houver mudança de procedimento, condição ou acidente, conforme a NR-1.`); return; }
    if (hours === null || hours <= 0 || hours > 400) return;
    // evita frases sobre horário/jornada ("duas horas de pausa")
    if (!/(carga horaria|duracao|minimo|no minimo|horas)/.test(t) || /(pausa|jornada|intervalo|repouso)/.test(t)) return;
    const kind = /complementa/.test(t) ? 'complementar' : /(periodic|reciclage|atualizac)/.test(t) ? 'periodico' : /eventual/.test(t) ? 'eventual' : 'inicial';
    found.push({ it, kind, hours, months: periodMonths(full) });
  });
  if (!found.length) return { courses: [], warnings };

  // conteúdo: listas da mesma seção (ex.: 36.16.*) que apresentam o conteúdo do treinamento
  const section = (n: string) => n.split('.').slice(0, 2).join('.');
  const periodic = found.find(f => f.kind === 'periodico');
  const courses = found.map(f => {
    const own = f.it.list.length ? [f.it] : [];
    const related = own.length ? own : items.filter(it => section(it.number) === section(f.it.number) && isContentIntro(it));
    const titles = related.flatMap(it => it.list.map(l => ({ title: l, normItem: `${it.number}` })));
    const kindLabel = f.kind === 'periodico' ? 'periódico' : f.kind;
    const months = f.months ?? (f.kind === 'inicial' ? periodic?.months ?? null : null);
    const code = f.kind === 'inicial' ? nr : `${nr} ${f.kind === 'periodico' ? 'PERIÓDICO' : f.kind.toUpperCase()}`;
    if (!titles.length) warnings.push(`${code}: a norma não lista o conteúdo deste treinamento; inclua os tópicos antes de salvar.`);
    if (f.kind === 'periodico' && titles.length) warnings.push(`${code}: a norma não separa o conteúdo do treinamento periódico; foi usado o conteúdo dos itens ${[...new Set(titles.map(t => t.normItem))].join(', ')}. Ajuste se necessário.`);
    const course: NormCourseProposal = {
      code,
      name: `${nr} – ${title || 'Treinamento'} (treinamento ${kindLabel})`,
      kind: f.kind,
      normReference: `${nr}, item ${f.it.number}${related.length ? `; conteúdo: itens ${[...new Set(related.map(r => r.number))].join(', ')}` : ''}`,
      workloadHours: f.hours,
      validityMonths: months || STANDARD_VALIDITY_MONTHS, // sem periodicidade na norma: 2 anos
      modality: 'presencial',
      minAttendance: 100,
      minGrade: null,
      prerequisite: f.kind === 'periodico' ? `Treinamento inicial ${nr}` : '',
      notes: `Treinamento ${kindLabel} conforme ${nr}, item ${f.it.number}: ${f.it.text}`,
      topicHoursFromNorm: false,
      topics: topicsFrom(titles, f.hours),
      evidence: `${f.it.number} ${f.it.text}`
    };
    return course;
  });
  if (courses.some(c => c.topics.length)) warnings.push('As horas de cada tópico foram divididas igualmente (a norma fixa só a carga horária total).');
  return { courses, warnings };
}

// ---------------------------------------------------------------- cursos em anexo
function annexCourses(lines: string[], fullText: string, nr: string): { courses: NormCourseProposal[]; warnings: string[] } {
  const warnings: string[] = [];
  const start = lines.findIndex(l => new RegExp(`^${loose('conteudo programatico')}\\s*$`, 'i').test(norm(l)));
  if (start < 0) return { courses: [], warnings };
  type Draft = { name: string; hours: number | null; topics: Array<{ title: string; normItem: string }>; section: string };
  const drafts: Draft[] = [];
  let cur: Draft | null = null;
  let part = '';
  for (let i = start + 1; i < lines.length; i++) {
    const line = lines[i].replace(/\s+/g, ' ').trim();
    const n = norm(line);
    if (!line || /^este texto nao substitui/.test(n)) continue;
    if (/^anexo\b/.test(n)) break;
    const header = line.match(/^([a-z])\s?\)\s+(Curso\s+.+)$/i);
    if (header) { cur = { name: clean(header[2]), hours: null, topics: [], section: header[1] }; drafts.push(cur); part = ''; continue; }
    if (!cur) continue;
    const ch = n.match(new RegExp(`^${loose('carga horaria')}\\s*:?\\s*(.+)$`));
    if (ch) { cur.hours = hoursIn(ch[1]); continue; }
    if (/^[ivx]+\s?\)/.test(n)) { part = /pratic/.test(n) ? 'prática' : /teoric/.test(n) ? '' : part; continue; }
    const topic = line.match(/^(\d{1,2})\s?\.\s+(.+)$/);
    if (topic) { cur.topics.push({ title: clean(topic[2]).replace(/[;.]$/, '') + (part ? ` (${part})` : ''), normItem: `Anexo, ${cur.section}) item ${topic[1]}${part ? ' prático' : ''}` }); continue; }
    if (cur.topics.length) cur.topics[cur.topics.length - 1].title = clean(`${cur.topics[cur.topics.length - 1].title} ${line}`).replace(/[;.]$/, '');
  }
  if (!drafts.length) return { courses: [], warnings };

  const flat = fullText.replace(/\s+/g, ' ');
  const courses = drafts.map(d => {
    const short = d.name.replace(/^Curso\s+(de\s+)?/i, '').trim();
    const shortLoose = loose(short);
    // tabela de capacitação: "Curso Básico (4 horas) Curso Básico (6 horas) ..."
    const options = [...norm(flat).matchAll(new RegExp(`curso\\s+${shortLoose}\\s*\\(\\s*(\\d+)\\s*horas?\\s*\\)`, 'g'))].map(m => Number(m[1]));
    const unique = [...new Set(options)].sort((a, b) => a - b);
    const hours = d.hours ?? unique[0] ?? 0;
    if (d.hours === null && unique.length > 1) warnings.push(`${d.name}: a carga horária varia conforme a classe da instalação (${unique.join(', ')} h); foi usada a menor (${unique[0]} h).`);
    if (!hours) warnings.push(`${d.name}: a norma não traz a carga horária no texto lido; informe antes de salvar.`);
    // tabela de periodicidade: "Básico Trienal 4 horas", "Intermediário Trienal Bienal Bienal 4 horas"
    const per = norm(flat).match(new RegExp(`(?:^|\\s)${shortLoose}\\s+((?:(?:anual|bienal|trienal)\\s+)+)`));
    const periods = per ? per[1].trim().split(/\s+/) : [];
    const months = periods.length ? periodMonths(periods[0]) ?? 0 : 0;
    if (new Set(periods).size > 1) warnings.push(`${d.name}: a periodicidade da atualização varia conforme a classe (${periods.join(', ')}); foi usada a primeira.`);
    return {
      code: `${nr} ${short.toUpperCase()}`,
      name: `${nr} – ${d.name}`,
      kind: 'inicial',
      normReference: `${nr}, Anexo (conteúdo programático), alínea ${d.section})`,
      workloadHours: hours,
      validityMonths: months || STANDARD_VALIDITY_MONTHS, // sem periodicidade na norma: 2 anos
      modality: 'presencial' as const,
      minAttendance: 100,
      minGrade: null,
      prerequisite: '',
      notes: `${d.name} conforme ${nr}, Anexo (conteúdo programático)${months ? `; atualização a cada ${months} meses` : ''}.`,
      topicHoursFromNorm: false,
      topics: topicsFrom(d.topics, hours),
      evidence: `${d.name}${d.hours ? ` – Carga horária: ${d.hours} horas` : unique.length ? ` – tabela: ${unique.join('/')} horas` : ''}`
    } satisfies NormCourseProposal;
  });
  warnings.push('As horas de cada tópico foram divididas igualmente (a norma fixa só a carga horária total).');
  return { courses, warnings };
}

/** Lê o texto da norma e propõe os cursos (mesmo formato da análise por IA). */
export function parseNormText(text: string, fileName = ''): NormAnalysis {
  const header = normHeader(text);
  const nr = header.code || (fileName.match(/nr[-_ ]?(\d{1,2})/i) ? `NR-${fileName.match(/nr[-_ ]?(\d{1,2})/i)![1].padStart(2, '0')}` : 'NR');
  const lines = text.split(/\r?\n/);
  const annex = annexCourses(lines, text, nr);
  const byItem = itemCourses(splitItems(lines), nr, header.title);
  // norma com conteúdo programático em anexo (ex.: NR-20): os cursos são os do anexo; os
  // treinamentos citados nos itens (complementação, atualização) viram aviso
  let courses: NormCourseProposal[];
  let warnings: string[];
  if (annex.courses.length) {
    courses = annex.courses;
    warnings = [...annex.warnings];
    byItem.courses.forEach(c => warnings.push(
      `${nr} também prevê treinamento ${c.kind === 'periodico' ? 'periódico/atualização' : c.kind} de ${c.workloadHours} h (${c.normReference.split(';')[0]}): ${c.evidence.slice(0, 220)}${c.evidence.length > 220 ? '…' : ''} Cadastre manualmente se oferecer.`
    ));
  } else {
    courses = byItem.courses;
    warnings = [...byItem.warnings];
  }
  if (!courses.length) warnings.push('Não foi encontrado no texto um treinamento com carga horária. Confira se o arquivo é a norma completa ou cadastre o curso manualmente.');
  warnings.push('Leitura automática do texto (sem IA): confira carga horária, validade e conteúdo com a norma antes de salvar.');
  return { norm: header, courses, warnings: [...new Set(warnings)] };
}
