import React, { useEffect, useMemo, useRef, useState } from 'react';
import { FileSpreadsheet, Download, Upload, Loader2, CheckCircle2, AlertTriangle, ArrowRight, Users, ClipboardList, Database, ShieldAlert, BarChart3, FileDown } from 'lucide-react';
import { fetchValidCertificatesByCpf, type ServerValidCertificate } from '../sync';
import { downloadAnalyticsSheet, downloadGeneratedSheet, readSheetsAsRows } from '../spreadsheetFiles';
import { analyticsSheets, buildAnalytics } from '../reportAnalytics';
import { exportAnalyticsReport } from '../analyticsPdf';
import { AnalyticsPanel } from './AnalyticsPanel';
import { getCertificates, getCourses } from '../repository';
import {
  APPROVED_SITUATIONS, mergeReports, parseReport, parseRoster, reportIssuedAt, suggestCourse, toImportRecords, type ReportEntry, type RosterEntry, type SheetRow
} from '../reportMerge';
import { normalizeText } from '../spreadsheetImport';
import { formatDateBr, todayIso } from '../rules';
import { alertError, btnPrimary, btnSecondary, cardCls, Field, inputCls } from './ui';
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

type UseRecords = (records: Array<Record<string, string>>, label: string) => void;

/**
 * Tela do menu Treinamentos › Emissão por relatórios: gera a planilha de emissão a
 * partir do relatório de conclusão da plataforma EAD e da lista de colaboradores da
 * empresa. A planilha pode ser baixada ou usada direto na emissão (abre a
 * importação de certificados já preenchida).
 */
export const ReportsToSheetPage: React.FC = () => {
  const [importing, setImporting] = useState<{ records: Array<Record<string, string>>; label: string } | null>(null);
  return (
    <>
      <ReportsToSheet onUse={(records, label) => setImporting({ records, label })} />
      {importing && <ImportCertificatesDialog initialRows={importing.records} initialLabel={importing.label} onClose={() => setImporting(null)} />}
    </>
  );
};

const ReportsToSheet: React.FC<{ onUse: UseRecords }> = ({ onUse }) => {
  const courses = getCourses().filter(c => c.active);
  const reportRef = useRef<HTMLInputElement | null>(null);
  const rosterRef = useRef<HTMLInputElement | null>(null);
  const [report, setReport] = useState<{ file: string; rows: ReportEntry[]; issuedAt: string | null } | null>(null);
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
        setReport({ file: file.name, rows, issuedAt: reportIssuedAt((await readSheetsAsRows(file))[0] || []) });
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

  // certificados válidos destes alunos no banco (não só os deste aparelho)
  const [server, setServer] = useState<{ status: 'consultando' | 'ok' | 'sem-conexao' | 'erro'; certs: ServerValidCertificate[]; error?: string } | null>(null);
  useEffect(() => {
    if (!report) return;
    let alive = true;
    setServer({ status: 'consultando', certs: [] });
    fetchValidCertificatesByCpf(report.rows.map(r => r.cpf))
      .then(certs => { if (alive) setServer(certs ? { status: 'ok', certs } : { status: 'sem-conexao', certs: [] }); })
      .catch(err => { if (alive) setServer({ status: 'erro', certs: [], error: err instanceof Error ? err.message : String(err) }); });
    return () => { alive = false; };
  }, [report]);

  const result = useMemo(
    () => (report ? mergeReports(report.rows, roster?.rows || [], { mapping, courses: getCourses(), company, existing: [...getCertificates(), ...(server?.certs || [])] }) : null),
    [report, roster, mapping, company, server]
  );
  // quem já tem certificado válido só entra na planilha se for marcado aqui
  const [includeCertified, setIncludeCertified] = useState<Set<string>>(new Set());
  const certKey = (r: SheetRow) => `${r.cpf}|${r.course.id}`;
  const chosenCertified = useMemo(() => (result?.certified || []).filter(r => includeCertified.has(certKey(r))), [result, includeCertified]);
  const finalRows = useMemo(() => (result ? [...result.rows, ...chosenCertified.map(r => ({ ...r, warnings: [...r.warnings, `Já tinha o certificado ${r.existingCertificate}`] }))] : []), [result, chosenCertified]);
  const records = useMemo(() => toImportRecords(finalRows), [finalRows]);
  const skippedAll = useMemo(() => [
    ...(result?.skipped || []),
    ...(result?.certified || []).filter(r => !includeCertified.has(certKey(r)))
      .map(r => ({ name: r.name, cpf: r.cpf, courseText: r.courseText, reason: `Já tem certificado válido (${r.existingCertificate})` }))
  ], [result, includeCertified]);

  /** Antes de usar/baixar: confirma quem vai receber um segundo certificado do mesmo curso. */
  const confirmCertified = () => !chosenCertified.length || window.confirm(
    `${chosenCertified.length} aluno(s) JÁ TÊM certificado válido do curso e vão constar na planilha:\n\n`
    + chosenCertified.slice(0, 15).map(r => `• ${r.name} — ${r.course.code || r.course.name} (${r.existingCertificate})`).join('\n')
    + (chosenCertified.length > 15 ? '\n…' : '') + '\n\nContinuar mesmo assim?'
  );
  const needGrade = useMemo(() => {
    const ids = new Set(finalRows.map(r => r.course.id));
    return getCourses().filter(c => ids.has(c.id) && c.minGrade !== undefined && c.minGrade !== null && String(c.minGrade) !== '');
  }, [finalRows]);
  const skippedByReason = useMemo(() => {
    const acc: Record<string, number> = {};
    skippedAll.forEach(s => { const k = s.reason.replace(/ \(TRE-.*\)$/, ''); acc[k] = (acc[k] || 0) + 1; });
    return Object.entries(acc).sort((a, b) => b[1] - a[1]);
  }, [skippedAll]);

  // relatório analítico do desenvolvimento dos alunos (mesmos arquivos)
  const [view, setView] = useState<'emissao' | 'analitico'>('emissao');
  const [exporting, setExporting] = useState<'xlsx' | 'pdf' | null>(null);
  const analytics = useMemo(() => (report ? buildAnalytics(report.rows, roster?.rows || [], { mapping, courses: getCourses() }) : null), [report, roster, mapping]);
  const handleAnalytics = async (kind: 'xlsx' | 'pdf') => {
    if (!analytics || !report) return;
    setExporting(kind);
    const now = new Date();
    const generatedAt = `${now.toLocaleDateString('pt-BR')} ${now.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}`;
    const label = company.trim() || analytics.group || 'alunos';
    const base = `Relatorio_analitico_${label.normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[^A-Za-z0-9]+/g, '_').replace(/^_|_$/g, '')}_${todayIso()}`;
    try {
      if (kind === 'xlsx') await downloadAnalyticsSheet(analyticsSheets(analytics, { title: `Relatório analítico de desempenho dos alunos${company.trim() ? ` — ${company.trim()}` : ''}`, generatedAt }), `${base}.xlsx`);
      else await exportAnalyticsReport(analytics, { group: analytics.group || report.file, generatedAt, reportIssuedAt: report.issuedAt, hasRoster: !!roster, filename: `${base}.pdf` });
    } catch (err) {
      alertError(err, 'Falha ao gerar o relatório');
    } finally {
      setExporting(null);
    }
  };

  const fileName = `Emissao_certificados_${(company.trim() || 'relatorio').replace(/[^\p{L}\p{N}]+/gu, '_')}_${todayIso()}.xlsx`;
  const handleDownload = async () => {
    if (!result || !confirmCertified()) return;
    setDownloading(true);
    try { await downloadGeneratedSheet(records, skippedAll, fileName); } catch (err) { alertError(err, 'Falha ao gerar a planilha'); } finally { setDownloading(false); }
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

  const actions = view === 'analitico' ? (
    <>
      <button type="button" className={btnSecondary} disabled={!analytics || exporting !== null} onClick={() => handleAnalytics('xlsx')}>
        {exporting === 'xlsx' ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Download className="w-3.5 h-3.5" />} Baixar Excel
      </button>
      <button type="button" className={btnPrimary} disabled={!analytics || exporting !== null} onClick={() => handleAnalytics('pdf')}>
        {exporting === 'pdf' ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <FileDown className="w-3.5 h-3.5" />} Baixar PDF
      </button>
    </>
  ) : (
    <>
      <button type="button" className={btnSecondary} disabled={!result || downloading} onClick={handleDownload}>
        {downloading ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Download className="w-3.5 h-3.5" />} Baixar planilha
      </button>
      <button type="button" className={btnPrimary} disabled={!records.length} onClick={() => confirmCertified() && onUse(records, `Gerada de ${report?.file || 'relatório'}`)}>
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
        <div className="mt-4 inline-flex rounded-xl border border-slate-300 overflow-hidden" role="tablist">
          {([['emissao', 'Planilha de emissão'], ['analitico', 'Relatório analítico']] as const).map(([k, label]) => (
            <button key={k} type="button" role="tab" aria-selected={view === k} onClick={() => setView(k)}
              className={`px-4 py-1.5 text-xs font-semibold inline-flex items-center gap-1.5 ${view === k ? 'bg-blue-600 text-white' : 'bg-white text-slate-600 hover:bg-slate-50'}`}>
              {k === 'analitico' ? <BarChart3 className="w-3.5 h-3.5" /> : <FileSpreadsheet className="w-3.5 h-3.5" />} {label}
            </button>
          ))}
        </div>
      )}

      {report && view === 'analitico' && analytics && (
        <div className="mt-4">
          {!roster && <p className="mb-2 text-[11px] text-amber-700">Sem a lista de colaboradores todos ficam em "Sem filial": envie a lista para ver o perfil por filial.</p>}
          <AnalyticsPanel analytics={analytics} />
        </div>
      )}

      {report && view === 'emissao' && (
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

      {result && view === 'emissao' && (
        <div className="mt-4">
          <div className="flex flex-wrap items-center gap-2 mb-2">
            <span className="inline-flex items-center gap-1 px-2 py-1 rounded-lg border text-[11px] font-bold text-emerald-700 bg-emerald-50 border-emerald-200"><CheckCircle2 className="w-3.5 h-3.5" /> {finalRows.length} certificado(s)</span>
            <button type="button" className="inline-flex items-center gap-1 px-2 py-1 rounded-lg border text-[11px] font-bold text-slate-600 bg-slate-50 border-slate-200 hover:bg-slate-100" onClick={() => setShowSkipped(s => !s)}>
              {skippedAll.length} fora da planilha {showSkipped ? '▴' : '▾'}
            </button>
            {!roster && <span className="text-[11px] text-amber-700">Sem a lista de colaboradores: sem filial e com início = conclusão.</span>}
          </div>

          <p className={`mb-2 text-[11px] flex items-center gap-1.5 ${server?.status === 'ok' ? 'text-slate-500' : 'text-amber-700'}`}>
            {server?.status === 'consultando' ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Database className="w-3.5 h-3.5" />}
            {server?.status === 'consultando' && 'Consultando no banco de dados os certificados já emitidos…'}
            {server?.status === 'ok' && `Banco de dados consultado: ${server.certs.length} certificado(s) válido(s) destes alunos.`}
            {server?.status === 'sem-conexao' && 'Sem conexão com o banco de dados: conferido só com os certificados deste aparelho.'}
            {server?.status === 'erro' && `Falha ao consultar o banco (${server.error}): conferido só com os certificados deste aparelho.`}
          </p>

          {result.certified.length > 0 && (
            <div className="mb-3 border border-red-300 bg-red-50 rounded-xl p-3">
              <p className="text-xs font-bold text-red-800 flex items-center gap-1.5">
                <ShieldAlert className="w-4 h-4" /> {result.certified.length} aluno(s) já têm certificado válido deste curso
              </p>
              <p className="text-[11px] text-red-800 mt-0.5 mb-2">Por padrão ficam fora da planilha. Marque quem deve constar na planilha final para emissão (receberá um novo certificado).</p>
              <div className="space-y-1">
                {result.certified.map(r => {
                  const k = certKey(r);
                  return (
                    <label key={k} className="flex items-start gap-2 text-[11px] text-slate-800 bg-white border border-red-200 rounded-lg px-2 py-1.5 cursor-pointer">
                      <input type="checkbox" className="mt-0.5" checked={includeCertified.has(k)}
                        onChange={e => setIncludeCertified(prev => { const n = new Set(prev); if (e.target.checked) n.add(k); else n.delete(k); return n; })} />
                      <span className="flex-1 min-w-0"><b>{r.name}</b> · {r.cpf || 'sem CPF'} · {r.course.code || r.course.name}</span>
                      <span className="font-mono text-red-700 whitespace-nowrap">{r.existingCertificate}</span>
                    </label>
                  );
                })}
              </div>
            </div>
          )}

          {showSkipped && (
            <div className="mb-3 border border-slate-200 rounded-xl p-2.5 bg-slate-50">
              <p className="text-[11px] text-slate-600 mb-1.5">{skippedByReason.map(([r, n]) => `${r}: ${n}`).join(' · ')}</p>
              <div className="max-h-48 overflow-auto">
                <table className="w-full text-[11px]">
                  <tbody>
                    {skippedAll.map((s, i) => (
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

          {finalRows.length > 0 ? (
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
                  {finalRows.map((r, i) => (
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

  return (
    <div className={`${cardCls} p-4`}>
      {body}
      <div className="flex flex-wrap justify-end gap-2 mt-4 pt-3 border-t border-slate-200">{actions}</div>
    </div>
  );
};
