import React, { useMemo, useRef, useState } from 'react';
import { FileSpreadsheet, Download, Upload, Loader2, CheckCircle2, AlertTriangle, ArrowRight, Users, ClipboardList } from 'lucide-react';
import { downloadGeneratedSheet, readSheetsAsRows } from '../spreadsheetFiles';
import { getCertificates, getCourses } from '../repository';
import {
  APPROVED_SITUATIONS, mergeReports, parseReport, parseRoster, suggestCourse, toImportRecords, type ReportEntry, type RosterEntry
} from '../reportMerge';
import { normalizeText } from '../spreadsheetImport';
import { formatDateBr, todayIso } from '../rules';
import { alertError, btnPrimary, btnSecondary, cardCls, Field, inputCls, Modal } from './ui';
import { ImportCertificatesDialog } from './ImportCertificatesDialog';

/** Primeira aba (ou o CSV) em que o leitor encontra as colunas esperadas. */
async function readFirstMatch<T>(file: File, parse: (rows: unknown[][]) => T[]): Promise<T[]> {
  const sheets = await readSheetsAsRows(file);
  let lastError: unknown;
  for (const rows of sheets) {
    try { const out = parse(rows); if (out.length) return out; } catch (err) { lastError = err; }
  }
  throw lastError || new Error('Nenhuma linha encontrada no arquivo.');
}

/**
 * Gera a planilha de emissão a partir do relatório de conclusão da plataforma EAD
 * e da lista de colaboradores da empresa. A planilha pode ser baixada ou usada
 * direto na importação de certificados.
 */
type UseRecords = (records: Array<Record<string, string>>, label: string) => void;

export const ReportsToSheetDialog: React.FC<{ onClose: () => void; onUse: UseRecords }> = ({ onClose, onUse }) => (
  <ReportsToSheet onClose={onClose} onUse={onUse} />
);

/** Tela do menu Treinamentos › Emissão por relatórios: "Usar na emissão" abre a importação já preenchida. */
export const ReportsToSheetPage: React.FC = () => {
  const [importing, setImporting] = useState<{ records: Array<Record<string, string>>; label: string } | null>(null);
  return (
    <>
      <ReportsToSheet page onUse={(records, label) => setImporting({ records, label })} />
      {importing && <ImportCertificatesDialog initialRows={importing.records} initialLabel={importing.label} onClose={() => setImporting(null)} />}
    </>
  );
};

const ReportsToSheet: React.FC<{ page?: boolean; onClose?: () => void; onUse: UseRecords }> = ({ page, onClose, onUse }) => {
  const courses = getCourses().filter(c => c.active);
  const reportRef = useRef<HTMLInputElement | null>(null);
  const rosterRef = useRef<HTMLInputElement | null>(null);
  const [report, setReport] = useState<{ file: string; rows: ReportEntry[] } | null>(null);
  const [roster, setRoster] = useState<{ file: string; rows: RosterEntry[] } | null>(null);
  const [reading, setReading] = useState<'report' | 'roster' | null>(null);
  const [company, setCompany] = useState('');
  const [mapping, setMapping] = useState<Record<string, string | undefined>>({});
  const [showSkipped, setShowSkipped] = useState(false);
  const [downloading, setDownloading] = useState(false);

  const load = async (kind: 'report' | 'roster', file?: File | null) => {
    if (!file) return;
    setReading(kind);
    try {
      if (kind === 'report') {
        const rows = await readFirstMatch(file, parseReport);
        setReport({ file: file.name, rows });
        // sugestão de curso do sistema para cada curso do relatório (pode ser trocada)
        const next: Record<string, string | undefined> = {};
        [...new Set(rows.map(r => r.courseText))].forEach(t => { next[t] = mapping[t] ?? suggestCourse(t, courses)?.id; });
        setMapping(next);
      } else {
        setRoster({ file: file.name, rows: await readFirstMatch(file, parseRoster) });
      }
    } catch (err) {
      alertError(err, 'Não foi possível ler o arquivo');
    } finally {
      setReading(null);
      const ref = kind === 'report' ? reportRef : rosterRef;
      if (ref.current) ref.current.value = '';
    }
  };

  const reportCourses = useMemo(() => {
    if (!report) return [];
    const map = new Map<string, { text: string; total: number; done: number }>();
    report.rows.forEach(r => {
      const item = map.get(r.courseText) || { text: r.courseText, total: 0, done: 0 };
      item.total++;
      if (APPROVED_SITUATIONS.includes(normalizeText(r.situation)) && (r.completion ?? 0) >= 100 && r.completedAt) item.done++;
      map.set(r.courseText, item);
    });
    return Array.from(map.values()).sort((a, b) => b.done - a.done || a.text.localeCompare(b.text));
  }, [report]);

  const result = useMemo(
    () => (report ? mergeReports(report.rows, roster?.rows || [], { mapping, courses: getCourses(), company, existing: getCertificates() }) : null),
    [report, roster, mapping, company]
  );
  const records = useMemo(() => (result ? toImportRecords(result.rows) : []), [result]);
  const needGrade = useMemo(() => {
    const ids = new Set(result?.rows.map(r => r.course.id));
    return getCourses().filter(c => ids.has(c.id) && c.minGrade !== undefined && c.minGrade !== null && String(c.minGrade) !== '');
  }, [result]);
  const skippedByReason = useMemo(() => {
    const acc: Record<string, number> = {};
    result?.skipped.forEach(s => { acc[s.reason] = (acc[s.reason] || 0) + 1; });
    return Object.entries(acc).sort((a, b) => b[1] - a[1]);
  }, [result]);

  const fileName = `Emissao_certificados_${(company.trim() || 'relatorio').replace(/[^\p{L}\p{N}]+/gu, '_')}_${todayIso()}.xlsx`;
  const handleDownload = async () => {
    if (!result) return;
    setDownloading(true);
    try { await downloadGeneratedSheet(records, result.skipped, fileName); } catch (err) { alertError(err, 'Falha ao gerar a planilha'); } finally { setDownloading(false); }
  };

  const FileBox: React.FC<{ kind: 'report' | 'roster'; icon: React.ElementType; title: string; hint: string; loaded: string | null; detail: string }> = ({ kind, icon: Icon, title, hint, loaded, detail }) => (
    <div className={`${cardCls} p-3 flex items-start gap-3`}>
      <Icon className={`w-5 h-5 shrink-0 mt-0.5 ${loaded ? 'text-emerald-600' : 'text-slate-400'}`} />
      <div className="min-w-0 flex-1">
        <p className="text-xs font-bold text-slate-800">{title}</p>
        <p className="text-[11px] text-slate-500">{loaded ? <><b className="text-slate-700 break-all">{loaded}</b> · {detail}</> : hint}</p>
      </div>
      <button type="button" className={loaded ? btnSecondary : btnPrimary} disabled={reading !== null} onClick={() => (kind === 'report' ? reportRef : rosterRef).current?.click()}>
        {reading === kind ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Upload className="w-3.5 h-3.5" />} {loaded ? 'Trocar' : 'Enviar'}
      </button>
    </div>
  );

  const actions = (
    <>
      <button type="button" className={btnSecondary} disabled={!result || downloading} onClick={handleDownload}>
        {downloading ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Download className="w-3.5 h-3.5" />} Baixar planilha
      </button>
      <button type="button" className={btnPrimary} disabled={!records.length} onClick={() => onUse(records, `Gerada de ${report?.file || 'relatório'}`)}>
        <ArrowRight className="w-3.5 h-3.5" /> Usar na emissão ({records.length})
      </button>
    </>
  );

  const body = (
    <>
      <input ref={reportRef} type="file" accept=".csv,.txt,.xlsx,.xls" className="hidden" onChange={e => load('report', e.target.files?.[0])} />
      <input ref={rosterRef} type="file" accept=".xlsx,.xls,.csv" className="hidden" onChange={e => load('roster', e.target.files?.[0])} />

      <p className="text-xs text-slate-600 mb-3">
        Junta os dois arquivos pelo CPF. Entra na planilha quem tem <b>100% de conclusão</b>, data de conclusão e situação
        <b> Aprovado</b> ou <b>Sem média exigida</b>. Nome e filial vêm da lista de colaboradores; o início é a data da liberação e o término, a data de conclusão.
      </p>

      <div className="grid gap-2 sm:grid-cols-2">
        <FileBox kind="report" icon={ClipboardList} title="1. Relatório de conclusão (CSV ou Excel)"
          hint="Ex.: relatorio-licoes-concluidas.csv da plataforma EAD" loaded={report?.file || null} detail={`${report?.rows.length || 0} linha(s)`} />
        <FileBox kind="roster" icon={Users} title="2. Lista de colaboradores (Excel)"
          hint="Colaborador, CPF, Filial e Data da Liberação (opcional, mas recomendada)" loaded={roster?.file || null}
          detail={`${roster?.rows.filter(r => !r.removed).length || 0} colaborador(es)${roster?.rows.some(r => r.removed) ? `, ${roster.rows.filter(r => r.removed).length} desligado(s)` : ''}`} />
      </div>

      {report && (
        <>
          <Field label="Colaborador da Empresa (empresa dos alunos)" className="mt-3" hint="Sai na coluna Colaborador da Empresa de todas as linhas">
            <input className={inputCls} value={company} onChange={e => setCompany(e.target.value)} placeholder="Ex.: Escamax Elevadores Ltda" />
          </Field>

          <div className="mt-4">
            <h4 className="font-bold text-xs text-slate-800 mb-1">Cursos do relatório → cursos do sistema</h4>
            <p className="text-[11px] text-slate-500 mb-2">Sugeridos pelo número da NR. Confira e troque se precisar; "Não emitir" deixa o curso de fora.</p>
            <div className="space-y-1.5">
              {reportCourses.map(rc => (
                <div key={rc.text} className="grid gap-2 sm:grid-cols-[1fr_minmax(0,18rem)] items-center border border-slate-200 rounded-xl px-2.5 py-2">
                  <div className="min-w-0">
                    <p className="text-[11px] font-semibold text-slate-800 break-words">{rc.text}</p>
                    <p className="text-[10px] text-slate-500">{rc.done} concluído(s) e aprovado(s) de {rc.total}</p>
                  </div>
                  <select className={inputCls} value={mapping[rc.text] || ''} onChange={e => setMapping(m => ({ ...m, [rc.text]: e.target.value || undefined }))}>
                    <option value="">Não emitir</option>
                    {courses.map(c => <option key={c.id} value={c.id}>{c.code ? `${c.code} – ` : ''}{c.name}</option>)}
                  </select>
                </div>
              ))}
            </div>
          </div>
        </>
      )}

      {result && (
        <div className="mt-4">
          <div className="flex flex-wrap items-center gap-2 mb-2">
            <span className="inline-flex items-center gap-1 px-2 py-1 rounded-lg border text-[11px] font-bold text-emerald-700 bg-emerald-50 border-emerald-200"><CheckCircle2 className="w-3.5 h-3.5" /> {result.rows.length} certificado(s)</span>
            <button type="button" className="inline-flex items-center gap-1 px-2 py-1 rounded-lg border text-[11px] font-bold text-slate-600 bg-slate-50 border-slate-200 hover:bg-slate-100" onClick={() => setShowSkipped(s => !s)}>
              {result.skipped.length} fora da planilha {showSkipped ? '▴' : '▾'}
            </button>
            {!roster && <span className="text-[11px] text-amber-700">Sem a lista de colaboradores: sem filial e com início = conclusão.</span>}
          </div>

          {showSkipped && (
            <div className="mb-3 border border-slate-200 rounded-xl p-2.5 bg-slate-50">
              <p className="text-[11px] text-slate-600 mb-1.5">{skippedByReason.map(([r, n]) => `${r}: ${n}`).join(' · ')}</p>
              <div className="max-h-48 overflow-auto">
                <table className="w-full text-[11px]">
                  <tbody>
                    {result.skipped.map((s, i) => (
                      <tr key={i} className="border-t border-slate-200 align-top">
                        <td className="py-1 pr-2 font-semibold text-slate-700">{s.name}</td>
                        <td className="py-1 pr-2 text-slate-500">{s.courseText}</td>
                        <td className="py-1 text-slate-600 whitespace-nowrap">{s.reason}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {needGrade.length > 0 && (
            <p className="mb-2 text-[11px] text-amber-800 bg-amber-50 border border-amber-200 rounded-lg px-2 py-1.5 flex gap-1.5">
              <AlertTriangle className="w-3.5 h-3.5 shrink-0 mt-px" />
              <span>{needGrade.map(c => c.code || c.name).join(', ')} exige(m) nota mínima e o relatório não traz a nota: informe a nota na tela de emissão (vale para quem não tem a coluna Nota).</span>
            </p>
          )}

          {result.rows.length > 0 ? (
            <div className="overflow-x-auto border border-slate-200 rounded-xl">
              <table className="w-full text-[11px]">
                <thead className="bg-slate-50 text-slate-600">
                  <tr>
                    <th className="text-left font-bold px-2 py-1.5">Curso</th>
                    <th className="text-left font-bold px-2 py-1.5">Nome</th>
                    <th className="text-left font-bold px-2 py-1.5">CPF</th>
                    <th className="text-left font-bold px-2 py-1.5">Filial</th>
                    <th className="text-left font-bold px-2 py-1.5">Período</th>
                  </tr>
                </thead>
                <tbody>
                  {result.rows.map((r, i) => (
                    <tr key={i} className="border-t border-slate-100 align-top">
                      <td className="px-2 py-1.5 font-semibold text-slate-700 whitespace-nowrap">{r.course.code || r.course.name}</td>
                      <td className="px-2 py-1.5 text-slate-800">
                        {r.name}
                        {r.warnings.length > 0 && <span className="block text-[10px] text-amber-700">{r.warnings.join(' · ')}</span>}
                      </td>
                      <td className="px-2 py-1.5 text-slate-600 whitespace-nowrap">{r.cpf || '—'}</td>
                      <td className="px-2 py-1.5 text-slate-600">{r.branch || '—'}</td>
                      <td className="px-2 py-1.5 text-slate-600 whitespace-nowrap">{r.startDate === r.endDate ? formatDateBr(r.endDate) : `${formatDateBr(r.startDate)} a ${formatDateBr(r.endDate)}`}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <div className="text-center py-6 text-xs text-slate-500">
              <FileSpreadsheet className="w-8 h-8 mx-auto mb-1 text-slate-300" />
              Nenhum aluno com curso concluído e aprovado nos cursos associados.
            </div>
          )}
        </div>
      )}
    </>
  );

  if (page) {
    return (
      <div className={`${cardCls} p-4`}>
        {body}
        <div className="flex flex-wrap justify-end gap-2 mt-4 pt-3 border-t border-slate-200">{actions}</div>
      </div>
    );
  }
  return (
    <Modal title="Gerar planilha de emissão a partir dos relatórios" onClose={onClose || (() => {})} wide
      footer={<><button type="button" className={btnSecondary} onClick={onClose}>Cancelar</button>{actions}</>}>
      {body}
    </Modal>
  );
};
