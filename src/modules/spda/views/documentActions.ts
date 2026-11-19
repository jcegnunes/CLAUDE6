/**
 * Ações dos laudos e relatórios (botões no padrão do APP de Ensaios de EPI):
 * Visualizar PDF · Compartilhar PDF · Exportar Word (.doc) · Compartilhar Word.
 *
 * - APP JVM SPDA (Android): o PDF é gerado pelo motor de impressão do Android direto em arquivo
 *   (AndroidBridge.createPdf) — PDF VETORIAL, texto nítido e selecionável, com a mesma qualidade
 *   do PDF do app de Ensaios de EPI. Visualizar = salva em Download/JVM_SPDA e abre no leitor de PDF.
 * - Plataforma web: Visualizar PDF abre a visualização de impressão do navegador (PDF vetorial em
 *   "Salvar como PDF"); Compartilhar usa o compartilhar do aparelho quando o navegador permite e,
 *   senão, baixa o arquivo.
 */
import { b64utf8, buildWordDoc, downloadBlob, downloadFile, safeFileName } from './wordExport';

type Bridge = {
  createPdf?: (fileName: string, action: string, requestId: string) => void;
  shareBase64File?: (b64: string, mime: string, name: string, title: string) => boolean;
};
const bridge = (): Bridge | undefined => (window as unknown as { AndroidBridge?: Bridge }).AndroidBridge;

/** O app Android gera o PDF vetorial nativo? */
export const hasNativePdf = (): boolean => typeof bridge()?.createPdf === 'function';

const pending = new Map<string, { resolve: (msg: string) => void; reject: (e: Error) => void }>();
function ensureCallback() {
  const w = window as unknown as { __spdaNativePdf?: (id: string, ok: boolean, msg: string) => void };
  if (w.__spdaNativePdf) return;
  w.__spdaNativePdf = (id, ok, msg) => {
    const p = pending.get(id);
    if (!p) return;
    pending.delete(id);
    if (ok) p.resolve(msg); else p.reject(new Error(msg || 'falha ao gerar o PDF'));
  };
}

/** PDF vetorial nativo do documento aberto (o WebView imprime só a folha, pelo CSS de impressão). */
export function nativePdf(fileName: string, action: 'view' | 'share'): Promise<string> {
  ensureCallback();
  const id = `pdf-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
  return new Promise<string>((resolve, reject) => {
    pending.set(id, { resolve, reject });
    setTimeout(() => { if (pending.delete(id)) reject(new Error('o PDF demorou demais para ficar pronto')); }, 120_000);
    bridge()!.createPdf!(safeFileName(fileName), action, id);
  });
}

/** Compartilha um arquivo pelo aparelho (Web Share); devolve false se o navegador não permite. */
async function webShare(file: File, title: string): Promise<boolean> {
  const nav = navigator as Navigator & { canShare?: (d: ShareData) => boolean };
  if (!nav.share || !nav.canShare?.({ files: [file] })) return false;
  try {
    await nav.share({ files: [file], title, text: `Documento técnico: ${title}` });
    return true;
  } catch (e) {
    if ((e as Error)?.name === 'AbortError') return true; // o usuário fechou o compartilhar
    return false;
  }
}

export type DocResult = string;

/** Visualizar PDF. */
export async function viewPdf(title: string, print: () => void): Promise<DocResult> {
  if (hasNativePdf()) return nativePdf(title, 'view');
  print();
  return 'Na janela de impressão escolha "Salvar como PDF" (PDF com texto nítido e selecionável).';
}

/** Compartilhar PDF. */
export async function sharePdf(sheet: HTMLElement, title: string, onProgress?: (msg: string) => void): Promise<DocResult> {
  if (hasNativePdf()) return nativePdf(title, 'share');
  const { buildPdfBlob } = await import('./pdfExport');
  const blob = await buildPdfBlob(sheet, (done, total) => onProgress?.(`Gerando o PDF… página ${Math.min(done + 1, total)} de ${total}`));
  const name = `${safeFileName(title)}.pdf`;
  if (await webShare(new File([blob], name, { type: 'application/pdf' }), title)) return 'PDF compartilhado.';
  await downloadBlob(name, blob, 'application/pdf');
  return 'Este navegador não compartilha arquivos: o PDF foi baixado para você enviar.';
}

/** Exportar Word (.doc). */
export async function exportWord(sheet: HTMLElement, title: string): Promise<DocResult> {
  const doc = await buildWordDoc(sheet, title);
  const where = downloadFile(`${safeFileName(title)}.doc`, doc, 'application/msword');
  return where === 'android' ? 'Arquivo Word salvo em Download/JVM_SPDA.' : 'Arquivo Word (.doc) baixado — abre e edita no Microsoft Word.';
}

/** Compartilhar Word. */
export async function shareWord(sheet: HTMLElement, title: string): Promise<DocResult> {
  const doc = await buildWordDoc(sheet, title);
  const name = `${safeFileName(title)}.doc`;
  const b = bridge();
  if (b?.shareBase64File && b.shareBase64File(b64utf8(doc), 'application/msword', name, title)) return 'Word pronto para compartilhar.';
  if (await webShare(new File([doc], name, { type: 'application/msword' }), title)) return 'Word compartilhado.';
  downloadFile(name, doc, 'application/msword');
  return 'Este navegador não compartilha arquivos: o Word foi baixado para você enviar.';
}
