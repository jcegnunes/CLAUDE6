/**
 * PDF do relatório analítico de desempenho dos alunos (A4 paisagem, 3 partes):
 * 1) indicadores, situação por curso, por filial e pontos de atenção;
 * 2) filial × curso; 3) perfil de conclusão por aluno, agrupado por filial.
 */
import jsPDF from 'jspdf';
import { hardenPdfText } from '../../utils/safePdf';
import { saveDocLocally } from '../../utils/nativeFileSaver';
import { DielectricStorageService } from '../../services/syncEngine';
import type { CompanyLabInfo } from '../../types';
import { imageFormat, loadAssets } from './certificatePdf';
import { formatPct, NO_BRANCH, type Analytics, type Metrics, type ProgressStatus } from './reportAnalytics';

type Rgb = [number, number, number];
const GREEN: Rgb = [22, 160, 40];
const RED: Rgb = [214, 64, 69];
const AMBER: Rgb = [247, 181, 41];
const BAR_GRAY: Rgb = [210, 214, 220];
const TEXT: Rgb = [30, 41, 59];
const MUTED: Rgb = [110, 120, 135];
const LIGHT: Rgb = [245, 247, 250];
const BAND: Rgb = [232, 236, 243];
const STATUS_COLOR: Record<ProgressStatus, Rgb> = { aprovado: GREEN, reprovado: RED, andamento: AMBER, nao_iniciado: BAR_GRAY };
const ORDER: ProgressStatus[] = ['aprovado', 'reprovado', 'andamento', 'nao_iniciado'];
const LEGEND: Record<ProgressStatus, string> = { aprovado: 'Aprovado', reprovado: 'Reprovado', andamento: 'Em andamento', nao_iniciado: 'Não iniciado' };

const W = 297;
const H = 210;
const M = 10.5;
const RIGHT = W - M;
const plural = (n: number, one: string, many: string) => `${n} ${n === 1 ? one : many}`;

export interface AnalyticsPdfMeta {
  /** Turma / grupo (subtítulo); vazio = nome do arquivo do relatório */
  group: string;
  generatedAt: string;
  /** "07/11/2026 14:11" (linhas de informação do relatório) */
  reportIssuedAt: string | null;
  hasRoster: boolean;
  filename: string;
}

export async function exportAnalyticsReport(a: Analytics, meta: AnalyticsPdfMeta, company: CompanyLabInfo = DielectricStorageService.getCompanyInfo()): Promise<void> {
  const doc = hardenPdfText(new jsPDF({ orientation: 'landscape', unit: 'mm', format: 'a4' }));
  const assets = await loadAssets(company);
  const navy = assets.navy;

  const font = (size: number, style: 'normal' | 'bold' = 'normal', color: Rgb = TEXT) => {
    doc.setFont('helvetica', style); doc.setFontSize(size); doc.setTextColor(...color);
  };
  const fill = (c: Rgb) => doc.setFillColor(...c);

  /** Logo dentro de uma caixa, mantendo a proporção. */
  const logo = (img: string, x: number, y: number, maxW: number, maxH: number, align: 'left' | 'right') => {
    if (!img) return 0;
    try {
      const p = doc.getImageProperties(img);
      const r = Math.min(maxW / p.width, maxH / p.height);
      const w = p.width * r; const h = p.height * r;
      doc.addImage(img, imageFormat(img), align === 'left' ? x : x - w, y + (maxH - h) / 2, w, h, undefined, 'FAST');
      return w;
    } catch { return 0; }
  };

  const header = () => {
    const lw = logo(assets.logo, M, 7, 26, 17, 'left');
    logo(assets.logo2, RIGHT, 8, 48, 13, 'right');
    const x = M + (lw ? lw + 6 : 0);
    font(16, 'bold', navy);
    doc.text('Relatório analítico de desempenho dos alunos', x, 15.5);
    font(9, 'normal', MUTED);
    doc.text(`${meta.group}   ·   Gerado em ${meta.generatedAt}`, x, 21.5);
    doc.setDrawColor(...TEXT); doc.setLineWidth(0.35);
    doc.line(M, 27, RIGHT, 27);
  };
  const newPage = () => { doc.addPage(); header(); };

  /** Título de seção com subtítulo cinza ao lado. */
  const section = (title: string, y: number, sub?: string) => {
    font(11, 'bold', navy);
    doc.text(title, M, y);
    if (sub) { const tw = doc.getTextWidth(title); font(7.5, 'normal', MUTED); doc.text(sub, M + tw + 3, y); }
  };

  const legend = (x: number, y: number, align: 'left' | 'right' = 'left') => {
    font(7.5, 'normal', TEXT);
    const items = ORDER.map(s => ({ s, w: 4.5 + doc.getTextWidth(LEGEND[s]) + 5 }));
    let cx = align === 'left' ? x : x - items.reduce((t, i) => t + i.w, 0);
    items.forEach(({ s, w }) => {
      fill(STATUS_COLOR[s]); doc.roundedRect(cx, y - 2.6, 3, 3, 0.5, 0.5, 'F');
      doc.text(LEGEND[s], cx + 4.5, y);
      cx += w;
    });
  };

  /** Barra empilhada; com números dentro dos segmentos quando cabem. */
  const stackBar = (m: Metrics, x: number, y: number, w: number, h: number, numbers: boolean) => {
    const total = Math.max(1, m.enrollments);
    const segs = ORDER.filter(s => m[s] > 0);
    const gap = segs.length > 1 ? 0.7 : 0;
    const usable = w - gap * (segs.length - 1);
    let cx = x;
    if (!segs.length) { fill(BAR_GRAY); doc.roundedRect(x, y, w, h, h / 2.5, h / 2.5, 'F'); return; }
    segs.forEach(s => {
      const sw = Math.max(numbers ? 4 : 1.2, (m[s] / total) * usable);
      fill(STATUS_COLOR[s]); doc.roundedRect(cx, y, sw, h, Math.min(1, h / 2.5), Math.min(1, h / 2.5), 'F');
      if (numbers && sw >= 3.5) {
        font(6.5, 'bold', s === 'nao_iniciado' ? [95, 105, 120] : s === 'andamento' ? [60, 40, 0] : [255, 255, 255]);
        doc.text(String(m[s]), cx + sw / 2, y + h / 2 + 1.1, { align: 'center' });
      }
      cx += sw + gap;
    });
  };

  const footers = () => {
    const pages = doc.getNumberOfPages();
    const source = `Fonte: relatório da plataforma EAD${meta.reportIssuedAt ? ` emitido em ${meta.reportIssuedAt}` : ''}${meta.hasRoster ? ' + lista de colaboradores da turma' : ''}`;
    for (let p = 1; p <= pages; p++) {
      doc.setPage(p);
      doc.setDrawColor(225, 229, 235); doc.setLineWidth(0.2);
      doc.line(M, H - 9, RIGHT, H - 9);
      font(6.5, 'normal', MUTED);
      doc.text(source, M, H - 5.5);
      doc.text(`Página ${p} de ${pages}`, RIGHT, H - 5.5, { align: 'right' });
    }
  };

  // ============================================================== página 1
  header();
  const s = a.summary;
  const share = (n: number) => `${formatPct(s.enrollments ? Math.round((n / s.enrollments) * 1000) / 10 : 0)} das matrículas`;
  const branches = a.byBranch.filter(b => b.branch !== NO_BRANCH).length;
  const cards: Array<{ label: string; value: string; sub: string; color: Rgb }> = [
    { label: 'ALUNOS', value: String(s.students), sub: `${branches} filial(is)`, color: navy },
    { label: 'MATRÍCULAS', value: String(s.enrollments), sub: plural(a.byCourse.length, 'curso', 'cursos'), color: navy },
    { label: 'APROVADAS', value: String(s.aprovado), sub: share(s.aprovado), color: GREEN },
    { label: 'REPROVADAS', value: String(s.reprovado), sub: share(s.reprovado), color: RED },
    { label: 'EM ANDAMENTO', value: String(s.andamento), sub: share(s.andamento), color: AMBER },
    { label: 'NÃO INICIADAS', value: String(s.nao_iniciado), sub: share(s.nao_iniciado), color: [190, 196, 205] },
    { label: 'CONCLUSÃO MÉDIA', value: formatPct(s.avgCompletion), sub: 'do conteúdo, por matrícula', color: navy }
  ];
  const cardGap = 3.2;
  const cardW = (RIGHT - M - cardGap * (cards.length - 1)) / cards.length;
  cards.forEach((c, i) => {
    const x = M + i * (cardW + cardGap);
    fill(LIGHT); doc.roundedRect(x, 32, cardW, 19, 1.5, 1.5, 'F');
    fill(c.color); doc.rect(x, 32, 1.1, 19, 'F');
    font(6.6, 'bold', [70, 80, 95]); doc.text(c.label, x + 4, 37);
    font(17, 'bold', TEXT); doc.text(c.value, x + 4, 45);
    font(6.2, 'normal', MUTED); doc.text(c.sub, x + 4, 49.3, { maxWidth: cardW - 5 });
  });

  // --- situação por curso (esquerda)
  const CX = { name: M, bar: 64, barW: 81, approval: 164, conclusion: 182 };
  section('Situação das matrículas por curso', 60);
  legend(M, 66);
  font(6.3, 'normal', MUTED);
  doc.text('APROVAÇÃO', CX.approval, 71, { align: 'right' });
  doc.text('CONCLUSÃO', CX.conclusion, 71, { align: 'right' });
  let yc = 77;
  const courseStep = Math.min(9.5, 110 / Math.max(1, a.byCourse.length));
  a.byCourse.forEach(c => {
    font(8.3, 'bold', TEXT); doc.text(c.course, CX.name, yc, { maxWidth: CX.bar - CX.name - 3 });
    font(6.3, 'normal', MUTED); doc.text(plural(c.enrollments, 'matrícula', 'matrículas'), CX.name, yc + 3.3);
    stackBar(c, CX.bar, yc - 3.2, CX.barW, 5.2, true);
    font(8.5, 'bold', c.approvalRate > 0 ? GREEN : [150, 158, 170]);
    doc.text(formatPct(c.approvalRate), CX.approval, yc + 0.4, { align: 'right' });
    font(8.5, 'normal', TEXT); doc.text(formatPct(c.avgCompletion), CX.conclusion, yc + 0.4, { align: 'right' });
    yc += courseStep;
  });

  // --- por filial (direita)
  const BX = { name: 191, bar: 224, barW: 48, approval: RIGHT };
  font(11, 'bold', navy); doc.text('Por filial', BX.name, 60);
  font(6.3, 'normal', MUTED); doc.text('APROVAÇÃO', BX.approval, 71, { align: 'right' });
  let yb = 77;
  a.byBranch.forEach(b => {
    font(8.3, 'bold', TEXT); doc.text(b.branch, BX.name, yb, { maxWidth: BX.bar - BX.name - 2 });
    font(6.3, 'normal', MUTED); doc.text(`${plural(b.students, 'aluno', 'alunos')} · ${b.enrollments} matr.`, BX.name, yb + 3.3);
    stackBar(b, BX.bar, yb - 3.2, BX.barW, 5.2, true);
    font(6.3, 'normal', MUTED); doc.text(`conclusão ${formatPct(b.avgCompletion)}`, BX.bar, yb + 5.6);
    font(8.5, 'bold', b.approvalRate > 0 ? GREEN : [150, 158, 170]);
    doc.text(formatPct(b.approvalRate), BX.approval, yb + 0.4, { align: 'right' });
    yb += 12;
  });

  // --- pontos de atenção
  if (a.attention.length) {
    const top = Math.max(yc, yb) + 4;
    const textW = RIGHT - M - 16;
    font(7.6, 'normal', TEXT);
    const lines = a.attention.map(t => doc.splitTextToSize(t, textW) as string[]);
    const boxH = 11 + lines.reduce((t, l) => t + l.length * 3.6 + 1.6, 0);
    let y0 = top;
    if (y0 + boxH > H - 12) { newPage(); y0 = 33; }
    fill([255, 248, 230]); doc.roundedRect(M, y0, RIGHT - M, boxH, 1.5, 1.5, 'F');
    fill(AMBER); doc.rect(M, y0, 1.1, boxH, 'F');
    font(10, 'bold', TEXT); doc.text('Pontos de atenção', M + 6, y0 + 6.5);
    let y = y0 + 12;
    lines.forEach(l => {
      fill([190, 130, 10]); doc.circle(M + 7.5, y - 1.1, 0.75, 'F');
      font(7.6, 'normal', TEXT); doc.text(l, M + 10.5, y);
      y += l.length * 3.6 + 1.6;
    });
  }

  // ============================================================== página 2
  newPage();
  section('Filial × curso', 33, 'aprovados / matrículas, distribuição por situação e conclusão média');
  const courses = a.matrix.courses;
  const firstW = 52;
  const colW = (RIGHT - M - firstW) / Math.max(1, courses.length);
  const tableHead = (y: number, first: string, extra?: { label: string; w: number }) => {
    const width = RIGHT - M;
    fill(navy); doc.roundedRect(M, y, width, 9, 1.2, 1.2, 'F');
    font(7.3, 'bold', [255, 255, 255]);
    doc.text(first, M + 3, y + 5.6);
    const cw = extra ? (RIGHT - M - firstW - extra.w) / Math.max(1, courses.length) : colW;
    courses.forEach((c, i) => {
      const cx = M + firstW + i * cw + cw / 2;
      const t = doc.splitTextToSize(c, cw - 3) as string[];
      doc.text(t.slice(0, 2), cx, y + (t.length > 1 ? 3.9 : 5.6), { align: 'center' });
    });
    if (extra) doc.text(extra.label, RIGHT - extra.w / 2, y + 5.6, { align: 'center' });
    return cw;
  };
  tableHead(37, 'Filial');
  let ym = 46;
  const matrixRow = (label: string, sub: string, cells: Array<Metrics | undefined>, bg: Rgb | null) => {
    const rh = 14;
    if (bg) { fill(bg); doc.rect(M, ym, RIGHT - M, rh, 'F'); }
    font(8.8, 'bold', TEXT); doc.text(label, M + 3, ym + 5.5);
    font(6.6, 'normal', MUTED); doc.text(sub, M + 3, ym + 9.3);
    cells.forEach((m, i) => {
      const x = M + firstW + i * colW + 3;
      const w = colW - 6;
      if (!m) { font(8, 'normal', [180, 186, 195]); doc.text('—', x + w / 2, ym + 7.5, { align: 'center' }); return; }
      font(8.4, 'bold', m.aprovado > 0 ? GREEN : [100, 110, 125]);
      const ratio = `${m.aprovado}/${m.enrollments}`;
      doc.text(ratio, x, ym + 4.6);
      const rw = doc.getTextWidth(ratio); // medida com a fonte em negrito do número
      font(6.8, 'normal', MUTED); doc.text('aprovados', x + rw + 1.2, ym + 4.6);
      stackBar(m, x, ym + 6.2, w, 2.1, false);
      font(6.6, 'normal', [70, 80, 95]); doc.text(`conclusão ${formatPct(m.avgCompletion)}`, x, ym + 11.6);
    });
    ym += rh;
  };
  a.matrix.rows.forEach((r, i) => {
    if (ym + 14 > H - 12) { newPage(); tableHead(33, 'Filial'); ym = 42; }
    matrixRow(r.branch, plural(r.students, 'aluno', 'alunos'), courses.map(c => r.cells[c]), i % 2 ? LIGHT : null);
  });
  if (ym + 14 > H - 12) { newPage(); tableHead(33, 'Filial'); ym = 42; }
  matrixRow('Total da turma', plural(a.summary.students, 'aluno', 'alunos'), a.byCourse.map(c => c), BAND);
  doc.setDrawColor(225, 229, 235); doc.setLineWidth(0.2); doc.line(M, ym, RIGHT, ym);

  // ============================================================== página 3
  newPage();
  const profileTitle = (y: number) => {
    section('Perfil de conclusão por aluno', y, 'situação em cada curso; datas = conclusão');
    legend(RIGHT - 2, y, 'right');
  };
  const nameW = 75;
  const apW = 24;
  const profileHead = (y: number) => {
    fill(navy); doc.roundedRect(M, y, RIGHT - M, 9, 1.2, 1.2, 'F');
    font(7.3, 'bold', [255, 255, 255]);
    doc.text('Aluno', M + 3, y + 5.6);
    const cw = (RIGHT - M - nameW - apW) / Math.max(1, courses.length);
    courses.forEach((c, i) => {
      const t = doc.splitTextToSize(c, cw - 3) as string[];
      doc.text(t.slice(0, 2), M + nameW + i * cw + cw / 2, y + (t.length > 1 ? 3.9 : 5.6), { align: 'center' });
    });
    doc.text('Aprovados', RIGHT - apW / 2, y + 5.6, { align: 'center' });
    return cw;
  };
  profileTitle(33);
  const cw = profileHead(37);
  let yp = 46;
  const ensure = (h: number) => {
    if (yp + h <= H - 12) return;
    newPage(); profileTitle(33); profileHead(37); yp = 46;
  };
  const dm = (iso: string | null) => (iso ? `${iso.slice(8, 10)}/${iso.slice(5, 7)}` : '');

  const statusCell = (st: Analytics['students'][number]['courses'][number] | undefined, x: number, y: number, w: number) => {
    const cx = x + w / 2;
    if (!st) { font(7, 'normal', [175, 182, 192]); doc.text('não matriculado', cx, y + 5, { align: 'center' }); return; }
    if (st.status === 'nao_iniciado') { font(7, 'normal', [130, 138, 150]); doc.text('Não iniciado', cx, y + 5, { align: 'center' }); return; }
    if (st.status === 'andamento') {
      font(6.8, 'bold', [150, 95, 0]); doc.text('Em andamento', x + 2, y + 3.2);
      font(6.8, 'bold', TEXT); doc.text(formatPct(st.completion), x + w - 2, y + 3.2, { align: 'right' });
      fill([235, 238, 242]); doc.roundedRect(x + 2, y + 4.6, w - 4, 1.5, 0.7, 0.7, 'F');
      fill(AMBER); doc.roundedRect(x + 2, y + 4.6, Math.max(1, (w - 4) * st.completion / 100), 1.5, 0.7, 0.7, 'F');
      return;
    }
    const ok = st.status === 'aprovado';
    fill(ok ? [228, 246, 231] : [252, 232, 233]); doc.rect(x + 1, y + 0.6, w - 2, 6.4, 'F');
    fill(ok ? GREEN : RED); doc.rect(x + 1, y + 0.6, 0.8, 6.4, 'F');
    font(7.2, 'bold', ok ? [20, 120, 35] : [185, 40, 45]); doc.text(ok ? 'Aprovado' : 'Reprovado', x + 3.5, y + 4.9);
    font(6.6, 'normal', ok ? [20, 120, 35] : [185, 40, 45]); doc.text(dm(st.completedAt), x + w - 2.5, y + 4.9, { align: 'right' });
  };

  a.byBranch.forEach(b => {
    const list = a.students.filter(st => st.branch === b.branch);
    if (!list.length) return;
    ensure(6.5 + 8.6);
    fill(BAND); doc.rect(M, yp, RIGHT - M, 6.5, 'F');
    font(7.8, 'bold', navy); doc.text(b.branch, M + 3, yp + 4.4);
    const bw = doc.getTextWidth(b.branch);
    font(6.8, 'normal', MUTED);
    doc.text(`${b.students} aluno(s) · ${b.aprovado} aprovação(ões) · conclusão média ${formatPct(b.avgCompletion)}`, M + 3 + bw + 2.5, yp + 4.4);
    yp += 6.5;
    list.forEach(st => {
      ensure(8.6);
      font(8, 'bold', TEXT); doc.text(st.name, M + 3, yp + 5.1, { maxWidth: nameW - 5 });
      courses.forEach((c, i) => statusCell(st.courses.find(x => x.course === c), M + nameW + i * cw, yp + 0.6, cw));
      const ax = RIGHT - apW + 2;
      font(8.3, 'bold', st.metrics.aprovado > 0 ? GREEN : [160, 166, 176]);
      doc.text(`${st.metrics.aprovado}/${st.metrics.enrollments}`, ax, yp + 5.1);
      fill([229, 232, 237]); doc.roundedRect(ax + 9, yp + 3.3, 11, 1.4, 0.7, 0.7, 'F');
      if (st.metrics.aprovado) { fill(GREEN); doc.roundedRect(ax + 9, yp + 3.3, Math.max(1.2, 11 * st.metrics.aprovado / st.metrics.enrollments), 1.4, 0.7, 0.7, 'F'); }
      doc.setDrawColor(228, 231, 236); doc.setLineWidth(0.2); doc.line(M, yp + 8.6, RIGHT, yp + 8.6);
      yp += 8.6;
    });
  });

  footers();
  await saveDocLocally(doc, meta.filename, meta.filename.replace(/\.pdf$/, ''), 'outro');
}
