/**
 * Gerador de PDF direto (sem a janela de impressão): fotografa a folha do relatório, como aparece na tela,
 * e monta um PDF A4 com margens fixas.
 *
 * - Relatório técnico (folhas `.rt-page`): uma folha da tela = uma página do PDF, com o rodapé no pé da página.
 * - Demais relatórios: o conteúdo é paginado respeitando as quebras forçadas (`.spda-break`) e cortando entre
 *   linhas de tabela, parágrafos e blocos (`.spda-avoid` não é partido quando cabe numa página).
 *
 * O PDF é composto por imagens (JPEG) das páginas: fica idêntico à versão web; o texto não é selecionável.
 */
import { downloadBlob } from './wordExport';

export const PAGE = { w: 210, h: 297, top: 11, right: 13, bottom: 9, left: 13 } as const;
const CONTENT_W_MM = PAGE.w - PAGE.left - PAGE.right; // 184 mm
const CONTENT_H_MM = PAGE.h - PAGE.top - PAGE.bottom; // 277 mm
const PX_PER_MM = 96 / 25.4;
const SCALE = 3; // resolução da captura (3 × CSS px: texto nítido também com zoom)
const EXPORT_CLASS = 'spda-pdf-export';

/** CSS aplicado só durante a geração: largura útil do A4 e folhas do relatório técnico na altura da página. */
const EXPORT_CSS = `
.${EXPORT_CLASS} { width: ${CONTENT_W_MM}mm !important; max-width: none !important; padding: 0 !important; margin: 0 !important;
  box-shadow: none !important; border-radius: 0 !important; background: #fff !important; }
.${EXPORT_CLASS} .rt-page { min-height: ${CONTENT_H_MM - 3}mm; }
.${EXPORT_CLASS} .rt-page.rt-cover { min-height: ${CONTENT_H_MM - 2}mm; }
`;

export type PdfProgress = (done: number, total: number) => void;

/** Linhas onde é seguro cortar a página (em px, relativas ao topo do elemento raiz). */
function cutLines(root: HTMLElement): { forced: number[]; safe: number[]; avoid: Array<[number, number]> } {
  const top0 = root.getBoundingClientRect().top;
  const rel = (y: number) => y - top0;
  const forced = [...root.querySelectorAll<HTMLElement>('.spda-break')].map(e => rel(e.getBoundingClientRect().top)).filter(y => y > 1);
  const safe: number[] = [];
  root.querySelectorAll<HTMLElement>('tr, p, li, figure, h1, h2, h3, table, section, .spda-avoid, .spda-block, img, svg, div').forEach(e => {
    const r = e.getBoundingClientRect();
    if (r.height > 0) { safe.push(rel(r.bottom)); safe.push(rel(r.top)); }
  });
  const avoid = [...root.querySelectorAll<HTMLElement>('.spda-avoid, tr, figure, img, svg')]
    .map(e => e.getBoundingClientRect()).map(r => [rel(r.top), rel(r.bottom)] as [number, number]);
  return { forced: [...new Set(forced)].sort((a, b) => a - b), safe: [...new Set(safe.map(Math.round))].sort((a, b) => a - b), avoid };
}

/** Divide a altura total em páginas (px CSS), cortando em quebras forçadas ou entre blocos. */
function paginate(total: number, pageH: number, lines: ReturnType<typeof cutLines>): Array<[number, number]> {
  const out: Array<[number, number]> = [];
  let start = 0;
  const inside = (y: number) => lines.avoid.some(([a, b]) => b - a < pageH && y > a + 1 && y < b - 1);
  while (start < total - 2) {
    const limit = start + pageH;
    const f = lines.forced.find(y => y > start + 5 && y <= limit);
    let end: number;
    if (f !== undefined) end = f;
    else if (limit >= total) end = total;
    else {
      const ok = lines.safe.filter(y => y > start + pageH * 0.4 && y <= limit && !inside(y));
      end = ok.length ? ok[ok.length - 1] : limit;
    }
    out.push([start, end]);
    start = end;
  }
  return out;
}

/**
 * Gera e baixa o PDF da folha `sheet` (elemento `.spda-sheet` do PrintOverlay).
 * Retorna 'android' ou 'browser' conforme o meio de gravação.
 */
export async function exportPdf(sheet: HTMLElement, fileName: string, onProgress?: PdfProgress): Promise<'android' | 'browser'> {
  const blob = await buildPdfBlob(sheet, onProgress);
  return downloadBlob(`${fileName}.pdf`, blob, 'application/pdf');
}

/** Monta o PDF (A4, imagens das páginas) da folha `sheet`, sem baixar. */
export async function buildPdfBlob(sheet: HTMLElement, onProgress?: PdfProgress): Promise<Blob> {
  const [{ toCanvas, getFontEmbedCSS }, { jsPDF }] = await Promise.all([import('html-to-image'), import('jspdf')]);
  const style = document.createElement('style');
  style.textContent = EXPORT_CSS;
  document.head.appendChild(style);
  sheet.classList.add(EXPORT_CLASS);
  try {
    await new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r)));
    await document.fonts?.ready;
    const fontEmbedCSS = await getFontEmbedCSS(sheet).catch(() => '');
    const widthPx = sheet.getBoundingClientRect().width;
    const pageHpx = CONTENT_H_MM * PX_PER_MM * (widthPx / (CONTENT_W_MM * PX_PER_MM));
    const opts = { pixelRatio: SCALE, backgroundColor: '#ffffff', cacheBust: false, fontEmbedCSS, skipAutoScale: true };

    const pdf = new jsPDF({ unit: 'mm', format: 'a4', orientation: 'portrait', compress: true });
    let first = true;
    const addImage = (canvas: HTMLCanvasElement, hMm: number) => {
      if (!first) pdf.addPage('a4', 'portrait');
      first = false;
      pdf.addImage(canvas.toDataURL('image/jpeg', 0.95), 'JPEG', PAGE.left, PAGE.top, CONTENT_W_MM, Math.min(hMm, CONTENT_H_MM), undefined, 'FAST');
    };

    const rtPages = [...sheet.querySelectorAll<HTMLElement>('.rt-page')];
    if (rtPages.length) {
      // relatório técnico: uma folha da tela por página (se a folha for maior que a página, ela é dividida)
      for (let i = 0; i < rtPages.length; i++) {
        onProgress?.(i, rtPages.length);
        const el = rtPages[i];
        const h = el.getBoundingClientRect().height;
        if (h <= pageHpx * 1.01) {
          const canvas = await toCanvas(el, { ...opts, width: el.offsetWidth, height: el.offsetHeight });
          addImage(canvas, (h / widthPx) * CONTENT_W_MM);
        } else {
          const canvas = await toCanvas(el, { ...opts, width: el.offsetWidth, height: el.offsetHeight });
          for (const [a, b] of paginate(h, pageHpx, cutLines(el))) addImage(slice(canvas, a * SCALE, b * SCALE), ((b - a) / widthPx) * CONTENT_W_MM);
        }
      }
      onProgress?.(rtPages.length, rtPages.length);
    } else {
      // demais relatórios: paginação pelo conteúdo, capturando uma página por vez (evita telas gigantes)
      const total = sheet.scrollHeight;
      const pages = paginate(total, pageHpx, cutLines(sheet));
      for (let i = 0; i < pages.length; i++) {
        onProgress?.(i, pages.length);
        const [a, b] = pages[i];
        const canvas = await toCanvas(sheet, {
          ...opts, width: sheet.offsetWidth, height: Math.ceil(b - a),
          style: { transform: `translateY(${-a}px)`, transformOrigin: 'top left' }
        });
        addImage(canvas, ((b - a) / widthPx) * CONTENT_W_MM);
      }
      onProgress?.(pages.length, pages.length);
    }
    return pdf.output('blob');
  } finally {
    sheet.classList.remove(EXPORT_CLASS);
    style.remove();
  }
}

function slice(src: HTMLCanvasElement, y0: number, y1: number): HTMLCanvasElement {
  const c = document.createElement('canvas');
  c.width = src.width;
  c.height = Math.max(1, Math.round(y1 - y0));
  const ctx = c.getContext('2d')!;
  ctx.fillStyle = '#fff';
  ctx.fillRect(0, 0, c.width, c.height);
  ctx.drawImage(src, 0, -Math.round(y0));
  return c;
}
