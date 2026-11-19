/**
 * Anexo de um PDF (ART da OS, certificado de calibração do instrumento): escolher, abrir e remover.
 * O arquivo fica salvo no aparelho junto com o cadastro (data URL no IndexedDB), entra no backup
 * e funciona sem internet. No app Android, "Abrir" grava em Download/JVM_SPDA e abre no leitor de PDF.
 */
import React, { useRef, useState } from 'react';
import { ExternalLink, FileText, Paperclip, RefreshCw, Trash2 } from 'lucide-react';
import { btnSecondary, btnDanger } from './ui';
import { confirmAsk, notify } from './dialogs';

export const MAX_PDF_MB = 10;

/** Lê um arquivo como data URL. */
export function readAsDataUrl(file: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const r = new FileReader();
    r.onload = () => resolve(String(r.result));
    r.onerror = () => reject(r.error || new Error('Não foi possível ler o arquivo'));
    r.readAsDataURL(file);
  });
}

/** Valida o arquivo escolhido: PDF e até MAX_PDF_MB. Devolve a mensagem de erro ou ''. */
export function pdfProblem(file: { name: string; type: string; size: number }): string {
  const isPdf = file.type === 'application/pdf' || /\.pdf$/i.test(file.name);
  if (!isPdf) return 'Escolha um arquivo PDF.';
  if (file.size > MAX_PDF_MB * 1024 * 1024) return `O PDF tem ${(file.size / 1024 / 1024).toFixed(1)} MB; o limite é ${MAX_PDF_MB} MB.`;
  return '';
}

/** Tamanho aproximado do arquivo guardado (data URL base64). */
export function dataUrlSizeKb(url?: string): number {
  const i = url ? url.indexOf(',') : -1;
  return i < 0 ? 0 : Math.round(((url!.length - i - 1) * 3) / 4 / 1024);
}

/** Abre o PDF: no app Android grava em Download/JVM_SPDA e abre; no navegador, abre numa aba nova. */
export function openPdf(url: string, fileName: string): void {
  const bridge = (window as unknown as { AndroidBridge?: { saveBase64File?: (b64: string, mime: string, name: string, open: boolean) => boolean } }).AndroidBridge;
  const m = /^data:([^;]+);base64,(.*)$/.exec(url);
  if (bridge?.saveBase64File && m) {
    bridge.saveBase64File(m[2], 'application/pdf', fileName || 'documento.pdf', true);
    return;
  }
  try {
    const bin = atob(m ? m[2] : '');
    const bytes = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
    const blobUrl = URL.createObjectURL(new Blob([bytes], { type: 'application/pdf' }));
    const w = window.open(blobUrl, '_blank');
    if (!w) {
      const a = document.createElement('a');
      a.href = blobUrl; a.download = fileName || 'documento.pdf'; document.body.appendChild(a); a.click(); a.remove();
    }
    setTimeout(() => URL.revokeObjectURL(blobUrl), 60_000);
  } catch {
    notify('Não foi possível abrir o PDF.');
  }
}

export const PdfAttachment: React.FC<{
  label: string;
  fileUrl?: string;
  fileName?: string;
  readOnly?: boolean;
  onChange: (file: { url: string; name: string } | null) => void;
}> = ({ label, fileUrl, fileName, readOnly, onChange }) => {
  const ref = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);

  const pick = async (files: FileList | null) => {
    const f = files?.[0];
    if (ref.current) ref.current.value = '';
    if (!f) return;
    const problem = pdfProblem(f);
    if (problem) { notify(problem); return; }
    setBusy(true);
    try {
      const url = await readAsDataUrl(f);
      onChange({ url: url.replace(/^data:[^;]*;/, 'data:application/pdf;'), name: f.name });
    } catch (err) {
      notify(err instanceof Error ? err.message : 'Não foi possível ler o PDF.');
    } finally {
      setBusy(false);
    }
  };

  const remove = async () => {
    if (await confirmAsk(`Remover o anexo "${fileName || 'PDF'}"?`)) onChange(null);
  };

  return (
    <div className="sm:col-span-2 border border-dashed border-slate-300 rounded-xl p-3 bg-slate-50">
      <p className="text-[11px] font-bold text-slate-700 mb-2 flex items-center gap-1.5"><Paperclip className="w-3.5 h-3.5" /> {label}</p>
      {fileUrl ? (
        <div className="flex flex-wrap items-center gap-2">
          <span className="inline-flex items-center gap-1.5 min-w-0 text-xs text-slate-800 bg-white border border-slate-200 rounded-lg px-2 py-1.5">
            <FileText className="w-4 h-4 text-red-600 shrink-0" />
            <span className="truncate max-w-[220px]" title={fileName}>{fileName || 'documento.pdf'}</span>
            <span className="text-[10px] text-slate-400 shrink-0">{dataUrlSizeKb(fileUrl)} KB</span>
          </span>
          <button type="button" className={btnSecondary} onClick={() => openPdf(fileUrl, fileName || 'documento.pdf')}><ExternalLink className="w-3.5 h-3.5" /> Abrir</button>
          {!readOnly && <button type="button" className={btnSecondary} disabled={busy} onClick={() => ref.current?.click()}><RefreshCw className="w-3.5 h-3.5" /> Trocar</button>}
          {!readOnly && <button type="button" className={btnDanger} onClick={remove} aria-label={`Remover ${label}`}><Trash2 className="w-3.5 h-3.5" /></button>}
        </div>
      ) : (
        <div className="flex flex-wrap items-center gap-2">
          {!readOnly && (
            <button type="button" className={btnSecondary} disabled={busy} onClick={() => ref.current?.click()}>
              {busy ? <RefreshCw className="w-3.5 h-3.5 animate-spin" /> : <Paperclip className="w-3.5 h-3.5" />} Anexar PDF
            </button>
          )}
          <span className="text-[10px] text-slate-500">Nenhum PDF anexado · até {MAX_PDF_MB} MB</span>
        </div>
      )}
      <input ref={ref} type="file" accept="application/pdf,.pdf" className="hidden" onChange={e => pick(e.target.files)} aria-label={label} />
    </div>
  );
};
