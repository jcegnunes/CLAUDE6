import React, { useRef, useState } from 'react';
import { Sparkles, Upload, Loader2, AlertTriangle, ChevronDown, ChevronUp, X, XCircle } from 'lucide-react';
import { analyzeNormText, extractNormText, type NormAnalysis, type NormCourseProposal } from '../normAnalysis';
import { formatHours } from '../rules';
import { btnPrimary, btnSecondary } from './ui';

/**
 * Importa a Norma Regulamentadora (PDF/TXT) e analisa o texto com IA no
 * servidor. Os cursos propostos vão direto para o formulário (um por aba);
 * aqui ficam a norma analisada, os pontos a conferir e a base na norma do
 * curso que está aberto.
 */
export const NormImportBox: React.FC<{
  /** Recebe a análise com pelo menos um curso; devolve false se o usuário não quiser substituir o formulário */
  onAnalyzed: (a: NormAnalysis, source: string) => boolean;
  /** Curso proposto que está aberto no formulário (para mostrar a base na norma) */
  active?: NormCourseProposal;
}> = ({ onAnalyzed, active }) => {
  const fileRef = useRef<HTMLInputElement | null>(null);
  const [open, setOpen] = useState(false);
  const [step, setStep] = useState<'idle' | 'lendo' | 'analisando'>('idle');
  const [file, setFile] = useState<{ name: string; chars: number; pages: number } | null>(null);
  const [result, setResult] = useState<NormAnalysis | null>(null);
  const [error, setError] = useState('');
  const [details, setDetails] = useState(false);

  const handleFile = async (f?: File | null) => {
    if (!f) return;
    setResult(null);
    setError('');
    setStep('lendo');
    try {
      const { text, pages } = await extractNormText(f);
      setFile({ name: f.name, chars: text.length, pages });
      if (text.length < 500) throw new Error('Quase não há texto neste arquivo. Se o PDF é digitalizado (imagem), envie a versão com texto do site do Ministério do Trabalho ou um TXT.');
      setStep('analisando');
      const analysis = await analyzeNormText(text, f.name);
      if (!analysis.courses.length) {
        setResult(analysis);
        throw new Error('A IA não encontrou treinamento a cadastrar neste texto. Veja os pontos a conferir abaixo.');
      }
      if (onAnalyzed(analysis, analysis.norm.code || f.name)) setResult(analysis);
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setStep('idle');
      if (fileRef.current) fileRef.current.value = '';
    }
  };

  if (!open) {
    return (
      <button type="button" className={`${btnSecondary} mb-3 border-violet-300 text-violet-700 hover:bg-violet-50`} onClick={() => setOpen(true)}>
        <Sparkles className="w-3.5 h-3.5" /> Preencher a partir da norma (IA)
      </button>
    );
  }

  const busy = step !== 'idle';
  return (
    <div className="mb-3 rounded-xl border border-violet-200 bg-violet-50/60 p-3">
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="text-xs font-bold text-violet-900 flex items-center gap-1.5"><Sparkles className="w-4 h-4" /> Curso a partir da Norma Regulamentadora</p>
          <p className="text-[11px] text-violet-900/80 mt-0.5">
            Envie a NR em PDF (com texto) ou TXT. A IA lê a norma e preenche o formulário com cada curso exigido (um por aba):
            carga horária, periodicidade, modalidade e conteúdo programático com os itens da norma. Confira e salve cada um.
          </p>
        </div>
        <button type="button" className="p-1 text-violet-500 hover:text-violet-800" onClick={() => setOpen(false)} aria-label="Fechar"><X className="w-4 h-4" /></button>
      </div>

      <input ref={fileRef} type="file" accept=".pdf,.txt,application/pdf,text/plain" className="hidden" onChange={e => handleFile(e.target.files?.[0])} />
      <div className="flex flex-wrap items-center gap-2 mt-2">
        <button type="button" className={btnPrimary} disabled={busy} onClick={() => fileRef.current?.click()}>
          {busy ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Upload className="w-3.5 h-3.5" />} {file ? 'Analisar outra norma' : 'Enviar a norma'}
        </button>
        {step === 'lendo' && <span className="text-[11px] text-violet-900">Lendo o texto do arquivo…</span>}
        {step === 'analisando' && <span className="text-[11px] text-violet-900">Analisando a norma com IA (pode levar 1 a 2 minutos)…</span>}
        {!busy && file && <span className="text-[11px] text-slate-600">{file.name}{file.pages ? ` · ${file.pages} página(s)` : ''} · {file.chars.toLocaleString('pt-BR')} caracteres</span>}
      </div>

      {error && (
        <p className="mt-2 text-[11px] text-red-800 bg-red-50 border border-red-200 rounded-lg px-2.5 py-1.5 flex gap-1.5">
          <XCircle className="w-3.5 h-3.5 shrink-0 mt-px" /> <span>Não foi possível analisar a norma: {error}</span>
        </p>
      )}

      {result && (
        <div className="mt-2 space-y-2">
          {result.courses.length > 0 && (
            <p className="text-[11px] text-slate-700">
              <b>{result.norm.code || 'Norma'}</b>{result.norm.title ? ` – ${result.norm.title}` : ''}{result.norm.revision ? ` (${result.norm.revision})` : ''}:
              {' '}{result.courses.length} curso(s) preenchido(s) abaixo{result.courses.length > 1 ? ', um por aba' : ''}.
            </p>
          )}
          {active && (
            <>
              <button type="button" className="text-[11px] font-semibold text-violet-800 inline-flex items-center gap-1" onClick={() => setDetails(d => !d)}>
                {details ? <ChevronUp className="w-3.5 h-3.5" /> : <ChevronDown className="w-3.5 h-3.5" />} Base na norma de {active.code || active.name}
              </button>
              {details && (
                <div className="rounded-lg bg-white border border-slate-200 p-2.5 text-[11px] text-slate-700 space-y-1.5">
                  <p><b>Referência:</b> {active.normReference || '—'}</p>
                  {active.evidence && <p className="whitespace-pre-line"><b>Trechos da norma:</b> {active.evidence}</p>}
                  <ol className="list-decimal pl-5 space-y-0.5">
                    {active.topics.map((t, i) => <li key={i}>{t.title} <span className="text-slate-500">— {formatHours(t.hours)}{t.normItem ? ` · ${t.normItem}` : ''}</span></li>)}
                  </ol>
                  {!active.topicHoursFromNorm && active.topics.length > 0 && <p className="text-amber-800">As horas de cada tópico são sugestão da IA (a norma fixa só o total).</p>}
                </div>
              )}
            </>
          )}
          {result.warnings.length > 0 && (
            <div className="rounded-lg border border-amber-300 bg-amber-50 p-2.5 text-[11px] text-amber-900">
              <p className="font-bold flex items-center gap-1"><AlertTriangle className="w-3.5 h-3.5" /> Conferir</p>
              <ul className="list-disc pl-4 mt-1 space-y-0.5">{result.warnings.map((w, i) => <li key={i}>{w}</li>)}</ul>
            </div>
          )}
        </div>
      )}
    </div>
  );
};
