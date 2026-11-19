/**
 * Exporta o documento aberto (laudo, relatório técnico ou relatório simplificado) para Word (.doc editável).
 * Gera um arquivo MHTML (formato "Página da Web de arquivo único" que o Word abre como documento):
 * - estilos calculados do navegador copiados para cada elemento (o Word não conhece as classes do app);
 * - grades e linhas flexíveis viram tabelas (o Word não tem grid/flex);
 * - desenhos SVG (figuras, curvas de patamar) viram imagens PNG;
 * - imagens embutidas como partes do arquivo; quebras de página preservadas (.spda-break).
 */

const STYLE_PROPS = [
  'color', 'background-color', 'font-size', 'font-weight', 'font-style', 'font-family', 'text-align', 'text-transform',
  'letter-spacing', 'line-height', 'vertical-align', 'white-space',
  'border-top-width', 'border-top-style', 'border-top-color', 'border-right-width', 'border-right-style', 'border-right-color',
  'border-bottom-width', 'border-bottom-style', 'border-bottom-color', 'border-left-width', 'border-left-style', 'border-left-color',
  'padding-top', 'padding-right', 'padding-bottom', 'padding-left', 'margin-top', 'margin-bottom'
];
const SKIP_TAGS = new Set(['SCRIPT', 'STYLE', 'BUTTON', 'INPUT', 'TEXTAREA', 'SELECT']);

function styleOf(el: Element): string {
  const cs = getComputedStyle(el);
  const out: string[] = [];
  for (const p of STYLE_PROPS) {
    let val = cs.getPropertyValue(p);
    if (!val) continue;
    if (p === 'background-color' && (val === 'rgba(0, 0, 0, 0)' || val === 'transparent')) continue;
    if (p.endsWith('-width') && val === '0px') continue;
    if (p.endsWith('-style') && (val === 'none' || cs.getPropertyValue(p.replace('-style', '-width')) === '0px')) continue;
    if ((p === 'letter-spacing' && val === 'normal') || (p === 'font-style' && val === 'normal') || (p === 'text-transform' && val === 'none')
      || (p === 'white-space' && val === 'normal') || (p === 'vertical-align' && val === 'baseline')) continue;
    if ((p.startsWith('padding') || p.startsWith('margin')) && val === '0px') continue;
    if (p.endsWith('-color') && p.startsWith('border')) {
      const side = p.replace('-color', '-width');
      if (cs.getPropertyValue(side) === '0px') continue;
    }
    if (p === 'font-family') val = 'Calibri, Arial, sans-serif';
    out.push(`${p}:${val}`);
  }
  if (el.classList.contains('spda-break')) out.push('page-break-before:always');
  return out.join(';');
}

/** SVG renderizado → PNG (data URL) no tamanho exibido × 2. */
async function svgToPng(svg: SVGSVGElement): Promise<{ url: string; w: number; h: number } | null> {
  const r = svg.getBoundingClientRect();
  const w = Math.max(1, Math.round(r.width)), h = Math.max(1, Math.round(r.height));
  const clone = svg.cloneNode(true) as SVGSVGElement;
  clone.setAttribute('xmlns', 'http://www.w3.org/2000/svg');
  clone.setAttribute('width', String(w * 2));
  clone.setAttribute('height', String(h * 2));
  const xml = new XMLSerializer().serializeToString(clone);
  const src = `data:image/svg+xml;charset=utf-8,${encodeURIComponent(xml)}`;
  const img = new Image();
  const ok = await new Promise<boolean>(res => { img.onload = () => res(true); img.onerror = () => res(false); img.src = src; });
  if (!ok) return null;
  const c = document.createElement('canvas');
  c.width = w * 2; c.height = h * 2;
  const ctx = c.getContext('2d');
  if (!ctx) return null;
  ctx.fillStyle = '#ffffff';
  ctx.fillRect(0, 0, c.width, c.height);
  ctx.drawImage(img, 0, 0, c.width, c.height);
  return { url: c.toDataURL('image/png'), w, h };
}

/** Converte o nó do documento em HTML "compatível com Word". */
async function convert(node: Node, doc: Document, maxW: number): Promise<Node | null> {
  if (node.nodeType === Node.TEXT_NODE) return doc.createTextNode(node.textContent || '');
  if (!(node instanceof Element)) return null;
  if (SKIP_TAGS.has(node.tagName)) return null;
  const cs = getComputedStyle(node);
  if (cs.display === 'none' || cs.visibility === 'hidden' || node.closest('.print\\:hidden')) return null;

  if (node.tagName.toLowerCase() === 'svg') {
    if (node.getAttribute('aria-hidden') === 'true') return null;
    const png = await svgToPng(node as SVGSVGElement);
    if (!png) return null;
    const img = doc.createElement('img');
    const w = Math.min(png.w, maxW);
    img.setAttribute('src', png.url);
    img.setAttribute('width', String(w));
    img.setAttribute('height', String(Math.round(png.h * (w / png.w))));
    return img;
  }
  if (node.tagName === 'IMG') {
    const r = (node as HTMLImageElement).getBoundingClientRect();
    const img = doc.createElement('img');
    img.setAttribute('src', (node as HTMLImageElement).src);
    const w = Math.min(Math.round(r.width) || maxW, maxW);
    img.setAttribute('width', String(w));
    if (r.width && r.height) img.setAttribute('height', String(Math.round(r.height * (w / r.width))));
    return img;
  }

  const kids = (await Promise.all([...node.childNodes].map(k => convert(k, doc, maxW)))).filter((k): k is Node => !!k);
  const style = styleOf(node);
  const isGrid = cs.display === 'grid' || cs.display === 'inline-grid';
  const isRow = (cs.display === 'flex' || cs.display === 'inline-flex') && !cs.flexDirection.startsWith('column');
  const elementKids = kids.filter(k => !(k.nodeType === Node.TEXT_NODE && !(k.textContent || '').trim()));

  if ((isGrid || isRow) && elementKids.length > 1) {
    const cols = isGrid ? Math.max(1, cs.gridTemplateColumns.split(' ').filter(Boolean).length) : elementKids.length;
    const table = doc.createElement('table');
    table.setAttribute('width', '100%');
    table.setAttribute('style', `${style};border-collapse:collapse`);
    let tr: HTMLTableRowElement | null = null;
    elementKids.forEach((k, i) => {
      if (i % cols === 0) { tr = doc.createElement('tr'); table.appendChild(tr); }
      const td = doc.createElement('td');
      td.setAttribute('valign', 'top');
      td.setAttribute('style', 'padding:2px 4px;vertical-align:top');
      td.appendChild(k);
      tr!.appendChild(td);
    });
    return table;
  }

  const tag = ['TABLE', 'THEAD', 'TBODY', 'TR', 'TH', 'TD', 'P', 'SPAN', 'B', 'STRONG', 'I', 'EM', 'H1', 'H2', 'H3', 'H4', 'UL', 'OL', 'LI', 'FIGURE', 'FIGCAPTION', 'A', 'BR', 'SUP', 'SUB']
    .includes(node.tagName) ? node.tagName.toLowerCase() : 'div';
  const out = doc.createElement(tag === 'figure' || tag === 'figcaption' ? 'div' : tag);
  if (style) out.setAttribute('style', style);
  if (node.tagName === 'TD' || node.tagName === 'TH') {
    const cs2 = (node as HTMLTableCellElement);
    if (cs2.colSpan > 1) out.setAttribute('colspan', String(cs2.colSpan));
    if (cs2.rowSpan > 1) out.setAttribute('rowspan', String(cs2.rowSpan));
  }
  if (node.tagName === 'TABLE') { out.setAttribute('width', '100%'); out.setAttribute('cellspacing', '0'); }
  if (node.tagName === 'A') out.setAttribute('href', (node as HTMLAnchorElement).href);
  kids.forEach(k => out.appendChild(k));
  return out;
}

export function b64utf8(s: string): string {
  const bytes = new TextEncoder().encode(s);
  let bin = '';
  for (let i = 0; i < bytes.length; i += 0x8000) bin += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
  return btoa(bin);
}
const wrap76 = (s: string) => s.replace(/.{1,76}/g, m => `${m}\r\n`);

/** Monta o arquivo .doc (MHTML) a partir do elemento do documento. */
export async function buildWordDoc(root: HTMLElement, title: string): Promise<string> {
  const doc = document.implementation.createHTMLDocument(title);
  const maxW = 640;
  const body = doc.createElement('div');
  body.className = 'WordSection1';
  for (const k of [...root.childNodes]) {
    const c = await convert(k, doc, maxW);
    if (c) body.appendChild(c);
  }
  // imagens como partes do arquivo
  const parts: Array<{ loc: string; type: string; data: string }> = [];
  body.querySelectorAll('img').forEach((img, i) => {
    const src = img.getAttribute('src') || '';
    const m = src.match(/^data:(image\/[a-z+]+);base64,(.*)$/i);
    if (!m) return;
    const ext = m[1].includes('png') ? 'png' : m[1].includes('svg') ? 'svg' : 'jpg';
    const loc = `file:///C:/jvm-spda/imagem${String(i + 1).padStart(3, '0')}.${ext}`;
    parts.push({ loc, type: m[1], data: m[2] });
    img.setAttribute('src', loc);
  });
  const html = `<html xmlns:o="urn:schemas-microsoft-com:office:office" xmlns:w="urn:schemas-microsoft-com:office:word" xmlns="http://www.w3.org/TR/REC-html40">
<head><meta charset="utf-8"><title>${title.replace(/</g, '')}</title>
<!--[if gte mso 9]><xml><w:WordDocument><w:View>Print</w:View><w:Zoom>100</w:Zoom></w:WordDocument></xml><![endif]-->
<style>
@page WordSection1 { size: 21cm 29.7cm; margin: 1.5cm 1.5cm 1.5cm 1.5cm; }
div.WordSection1 { page: WordSection1; }
body { font-family: Calibri, Arial, sans-serif; font-size: 10pt; }
table { border-collapse: collapse; }
td, th { vertical-align: top; }
</style></head><body>${body.outerHTML}</body></html>`;
  const boundary = '----=_NextPart_JVM_SPDA';
  let mht = `MIME-Version: 1.0\r\nContent-Type: multipart/related; boundary="${boundary}"; type="text/html"\r\n\r\n`;
  mht += `--${boundary}\r\nContent-Location: file:///C:/jvm-spda/documento.htm\r\nContent-Transfer-Encoding: base64\r\nContent-Type: text/html; charset="utf-8"\r\n\r\n${wrap76(b64utf8(html))}\r\n`;
  for (const p of parts) {
    mht += `--${boundary}\r\nContent-Location: ${p.loc}\r\nContent-Transfer-Encoding: base64\r\nContent-Type: ${p.type}\r\n\r\n${wrap76(p.data)}\r\n`;
  }
  mht += `--${boundary}--\r\n`;
  return mht;
}

/** Baixa um arquivo (no APK usa a ponte do Android; no navegador, download comum). */
export function downloadFile(filename: string, content: string, mime: string): 'android' | 'browser' {
  const bridge = (window as unknown as { AndroidBridge?: { saveBase64File?: (b64: string, mime: string, name: string, open: boolean) => boolean } }).AndroidBridge;
  if (bridge?.saveBase64File && bridge.saveBase64File(b64utf8(content), mime, filename, true)) return 'android';
  const url = URL.createObjectURL(new Blob([content], { type: mime }));
  const a = document.createElement('a');
  a.href = url; a.download = filename; document.body.appendChild(a); a.click();
  setTimeout(() => { a.remove(); URL.revokeObjectURL(url); }, 500);
  return 'browser';
}

/** Baixa um arquivo binário (ex.: PDF). No app Android, grava em Downloads pelo AndroidBridge. */
export async function downloadBlob(filename: string, blob: Blob, mime: string): Promise<'android' | 'browser'> {
  const bridge = (window as unknown as { AndroidBridge?: { saveBase64File?: (b64: string, mime: string, name: string, open: boolean) => boolean } }).AndroidBridge;
  if (bridge?.saveBase64File) {
    const b64 = await new Promise<string>((res, rej) => { const r = new FileReader(); r.onload = () => res(String(r.result).split(',')[1] || ''); r.onerror = () => rej(r.error); r.readAsDataURL(blob); });
    if (bridge.saveBase64File(b64, mime, filename, true)) return 'android';
  }
  const url = URL.createObjectURL(blob);
  const el = document.createElement('a');
  el.href = url; el.download = filename; document.body.appendChild(el); el.click();
  setTimeout(() => { el.remove(); URL.revokeObjectURL(url); }, 1500);
  return 'browser';
}

export const safeFileName = (s: string) => s.replace(/[\\/:*?"<>|]+/g, '-').replace(/\s+/g, ' ').trim().slice(0, 120);
