import React, { useState } from 'react';
import { formatPct, NO_BRANCH, STATUS_LABEL, type Analytics, type Metrics, type ProgressStatus } from '../reportAnalytics';
import { formatDateBr } from '../rules';

const STATUS_CLS: Record<ProgressStatus, string> = {
  aprovado: 'bg-emerald-500',
  reprovado: 'bg-red-500',
  andamento: 'bg-amber-400',
  nao_iniciado: 'bg-slate-300'
};
const ORDER: ProgressStatus[] = ['aprovado', 'reprovado', 'andamento', 'nao_iniciado'];

/** Barra empilhada com a distribuição das situações. */
const StatusBar: React.FC<{ m: Metrics }> = ({ m }) => (
  <div className="flex h-2 w-full rounded-full overflow-hidden bg-slate-100" title={ORDER.map(s => `${STATUS_LABEL[s]}: ${m[s]}`).join(' · ')}>
    {ORDER.map(s => (m[s] ? <div key={s} className={STATUS_CLS[s]} style={{ width: `${(m[s] / Math.max(1, m.enrollments)) * 100}%` }} /> : null))}
  </div>
);

const Legend: React.FC = () => (
  <div className="flex flex-wrap gap-3 text-[10px] text-slate-600">
    {ORDER.map(s => <span key={s} className="inline-flex items-center gap-1"><span className={`w-2.5 h-2.5 rounded-sm ${STATUS_CLS[s]}`} />{STATUS_LABEL[s]}</span>)}
  </div>
);

const MetricsTable: React.FC<{ title: string; label: string; rows: Array<{ name: string; m: Metrics }> }> = ({ title, label, rows }) => (
  <div>
    <h4 className="font-bold text-xs text-slate-800 mb-1.5">{title}</h4>
    <div className="overflow-x-auto border border-slate-200 rounded-xl">
      <table className="w-full text-[11px]">
        <thead className="bg-slate-50 text-slate-600">
          <tr>
            <th className="text-left font-bold px-2 py-1.5">{label}</th>
            <th className="font-bold px-2 py-1.5">Alunos</th>
            <th className="font-bold px-2 py-1.5">Matrículas</th>
            <th className="font-bold px-2 py-1.5 text-emerald-700">Aprovados</th>
            <th className="font-bold px-2 py-1.5 text-red-700">Reprovados</th>
            <th className="font-bold px-2 py-1.5 text-amber-700">Em andamento</th>
            <th className="font-bold px-2 py-1.5">Não iniciados</th>
            <th className="font-bold px-2 py-1.5">Conclusão média</th>
            <th className="font-bold px-2 py-1.5 min-w-[7rem]">Perfil</th>
          </tr>
        </thead>
        <tbody>
          {rows.map(({ name, m }) => (
            <tr key={name} className="border-t border-slate-100 text-center">
              <td className="text-left px-2 py-1.5 font-semibold text-slate-800">{name}</td>
              <td className="px-2 py-1.5">{m.students}</td>
              <td className="px-2 py-1.5">{m.enrollments}</td>
              <td className="px-2 py-1.5 font-bold text-emerald-700">{m.aprovado} <span className="font-normal text-slate-400">({formatPct(m.approvalRate)})</span></td>
              <td className="px-2 py-1.5 text-red-700">{m.reprovado}</td>
              <td className="px-2 py-1.5 text-amber-700">{m.andamento}</td>
              <td className="px-2 py-1.5 text-slate-500">{m.nao_iniciado}</td>
              <td className="px-2 py-1.5">{formatPct(m.avgCompletion)}</td>
              <td className="px-2 py-1.5"><StatusBar m={m} /></td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  </div>
);

/** Relatório analítico na tela: resumo, por curso, por filial, filial × curso e perfil de cada aluno. */
export const AnalyticsPanel: React.FC<{ analytics: Analytics }> = ({ analytics: a }) => {
  const [branch, setBranch] = useState('');
  const students = branch ? a.students.filter(s => s.branch === branch) : a.students;
  const s = a.summary;
  const cards: Array<[string, string, string]> = [
    ['Alunos', String(s.students), 'text-slate-900'],
    ['Matrículas', String(s.enrollments), 'text-slate-900'],
    ['Concluídas e aprovadas', `${s.aprovado} (${formatPct(s.approvalRate)})`, 'text-emerald-700'],
    ['Reprovadas', String(s.reprovado), 'text-red-700'],
    ['Em andamento', String(s.andamento), 'text-amber-700'],
    ['Não iniciadas', String(s.nao_iniciado), 'text-slate-500'],
    ['Conclusão média', formatPct(s.avgCompletion), 'text-blue-700']
  ];
  return (
    <div className="space-y-4">
      <div className="grid gap-2 grid-cols-2 sm:grid-cols-4 lg:grid-cols-7">
        {cards.map(([label, value, cls]) => (
          <div key={label} className="border border-slate-200 rounded-xl px-3 py-2">
            <p className="text-[10px] text-slate-500">{label}</p>
            <p className={`text-base font-black ${cls}`}>{value}</p>
          </div>
        ))}
      </div>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <Legend />
        {a.removed > 0 && <span className="text-[11px] text-slate-500">{a.removed} desligado(s) fora das contas.</span>}
      </div>

      <MetricsTable title="Por curso" label="Curso" rows={a.byCourse.map(c => ({ name: c.course, m: c }))} />
      <MetricsTable title="Por filial" label="Filial" rows={a.byBranch.map(b => ({ name: b.branch, m: b }))} />

      {a.attention.length > 0 && (
        <div className="border-l-4 border-amber-400 bg-amber-50 rounded-xl px-4 py-3">
          <h4 className="font-bold text-xs text-slate-800 mb-1.5">Pontos de atenção</h4>
          <ul className="space-y-1 text-[11px] text-slate-700 list-disc pl-4 marker:text-amber-600">
            {a.attention.map(t => <li key={t}>{t}</li>)}
          </ul>
        </div>
      )}

      <div>
        <h4 className="font-bold text-xs text-slate-800 mb-1.5">Filial × curso <span className="font-normal text-slate-500">(aprovados / matrículas · conclusão média)</span></h4>
        <div className="overflow-x-auto border border-slate-200 rounded-xl">
          <table className="w-full text-[11px]">
            <thead className="bg-slate-50 text-slate-600">
              <tr>
                <th className="text-left font-bold px-2 py-1.5">Filial</th>
                {a.matrix.courses.map(c => <th key={c} className="font-bold px-2 py-1.5">{c}</th>)}
              </tr>
            </thead>
            <tbody>
              {a.matrix.rows.map(r => (
                <tr key={r.branch} className="border-t border-slate-100 text-center">
                  <td className="text-left px-2 py-1.5 font-semibold text-slate-800">{r.branch}</td>
                  {a.matrix.courses.map(c => {
                    const cell = r.cells[c];
                    if (!cell) return <td key={c} className="px-2 py-1.5 text-slate-300">—</td>;
                    const ratio = cell.aprovado / cell.enrollments;
                    const tone = ratio >= 0.8 ? 'bg-emerald-50 text-emerald-800' : ratio >= 0.4 ? 'bg-amber-50 text-amber-800' : 'bg-red-50 text-red-800';
                    return <td key={c} className={`px-2 py-1.5 ${tone}`}><b>{cell.aprovado}/{cell.enrollments}</b> <span className="opacity-70">· {formatPct(cell.avgCompletion)}</span></td>;
                  })}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      <div>
        <div className="flex flex-wrap items-center justify-between gap-2 mb-1.5">
          <h4 className="font-bold text-xs text-slate-800">Perfil de conclusão por aluno</h4>
          <select className="p-1.5 border border-slate-300 rounded-lg bg-white text-[11px]" value={branch} onChange={e => setBranch(e.target.value)}>
            <option value="">Todas as filiais</option>
            {a.byBranch.map(b => <option key={b.branch} value={b.branch}>{b.branch}</option>)}
          </select>
        </div>
        <div className="overflow-x-auto border border-slate-200 rounded-xl max-h-[60vh]">
          <table className="w-full text-[11px]">
            <thead className="bg-slate-50 text-slate-600 sticky top-0">
              <tr>
                <th className="text-left font-bold px-2 py-1.5">Aluno</th>
                {a.matrix.courses.map(c => <th key={c} className="font-bold px-2 py-1.5">{c}</th>)}
                <th className="font-bold px-2 py-1.5">Aprovados</th>
              </tr>
            </thead>
            <tbody>
              {students.map(st => (
                <tr key={`${st.cpf}|${st.name}`} className="border-t border-slate-100 text-center align-top">
                  <td className="text-left px-2 py-1.5">
                    <span className="font-semibold text-slate-800">{st.name}</span>
                    <span className="block text-[10px] text-slate-500">{st.branch === NO_BRANCH ? 'sem filial' : st.branch}{st.cpf ? ` · ${st.cpf}` : ''}</span>
                  </td>
                  {a.matrix.courses.map(course => {
                    const c = st.courses.find(x => x.course === course);
                    if (!c) return <td key={course} className="px-2 py-1.5 text-slate-300">—</td>;
                    return (
                      <td key={course} className="px-2 py-1.5" title={STATUS_LABEL[c.status]}>
                        <span className="inline-flex items-center gap-1">
                          <span className={`w-2 h-2 rounded-full ${STATUS_CLS[c.status]}`} />
                          {c.status === 'aprovado' ? (c.completedAt ? formatDateBr(c.completedAt) : 'Aprovado') : c.status === 'reprovado' ? 'Reprovado' : formatPct(c.completion)}
                        </span>
                      </td>
                    );
                  })}
                  <td className="px-2 py-1.5 font-bold">{st.metrics.aprovado}/{st.metrics.enrollments}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};
