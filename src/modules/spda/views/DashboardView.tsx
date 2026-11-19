/**
 * Dashboard (menu abaixo de Ordens de serviço): visão geral das inspeções, pareceres, não conformidades,
 * ensaios, próximas inspeções periódicas, ordens de serviço e calibração dos instrumentos.
 * Só dados do aparelho; tocar num item abre a tela correspondente.
 */
import React from 'react';
import { AlertTriangle, CalendarClock, ClipboardCheck, ClipboardList, Gauge, LayoutDashboard, Zap } from 'lucide-react';
import { listInspections } from '../repository';
import { readInstruments as listInstruments, readServiceOrders as listOrders } from '../repository';
import { buildDashboard } from '../dashboard';
import { RESULT_LABEL, type ShownResult } from '../techReport';
import { formatDateBR } from '../../../utils/dateUtils';
import { navigateTo, openInspectionOnNextVisit } from './navigation';
import { cardCls } from './ui';
import { todayIso } from '../rules';
import type { ServiceOrderStatus } from '../../../types';

const RESULT_BAR: Record<ShownResult, string> = { aprovado: 'bg-emerald-500', reprovado: 'bg-red-500', parcial: 'bg-amber-400', incompleto: 'bg-slate-300' };
const RESULT_TXT: Record<ShownResult, string> = { aprovado: 'text-emerald-700', reprovado: 'text-red-700', parcial: 'text-amber-700', incompleto: 'text-slate-500' };
const OS_STATUS: Record<ServiceOrderStatus, string> = {
  aberta: 'Aberta', agendada: 'Agendada', em_execucao: 'Em execução', aguardando: 'Aguardando', concluida: 'Concluída', cancelada: 'Cancelada'
};

const Kpi: React.FC<{ icon: React.ElementType; label: string; value: string | number; sub?: string; tone?: string; onClick?: () => void }> = ({ icon: Icon, label, value, sub, tone = 'text-slate-900', onClick }) => (
  <button type="button" onClick={onClick} disabled={!onClick}
    className={`${cardCls} p-3 text-left w-full disabled:cursor-default ${onClick ? 'hover:border-blue-300 hover:bg-blue-50/40' : ''}`}>
    <span className="flex items-center gap-1.5 text-[10px] font-bold uppercase tracking-wide text-slate-500"><Icon className="w-3.5 h-3.5" /> {label}</span>
    <span className={`block text-2xl font-black leading-tight mt-1 ${tone}`}>{value}</span>
    {sub && <span className="block text-[11px] text-slate-500 mt-0.5">{sub}</span>}
  </button>
);

const Panel: React.FC<{ title: string; icon: React.ElementType; right?: React.ReactNode; children: React.ReactNode }> = ({ title, icon: Icon, right, children }) => (
  <section className={`${cardCls} p-4`}>
    <div className="flex items-center justify-between gap-2 mb-3">
      <h3 className="font-bold text-sm text-slate-900 flex items-center gap-1.5"><Icon className="w-4 h-4 text-slate-500" /> {title}</h3>
      {right}
    </div>
    {children}
  </section>
);

const openInspection = (id: string) => { openInspectionOnNextVisit(id); navigateTo('spda'); };
const daysTxt = (d: number) => (d < 0 ? `vencida há ${-d} dia(s)` : d === 0 ? 'hoje' : `em ${d} dia(s)`);

export const DashboardView: React.FC = () => {
  const d = buildDashboard(listInspections(), listOrders(), listInstruments(), todayIso());
  const totalRes = Math.max(1, d.inspections.total);
  const pctOk = d.tests.measured ? Math.round((d.tests.approved / d.tests.measured) * 100) : 0;
  const calAlert = d.instruments.expired.length + d.instruments.expiring.length;

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-3">
        <div className="w-10 h-10 rounded-xl bg-[#0A2540] text-white flex items-center justify-center"><LayoutDashboard className="w-5 h-5" /></div>
        <div>
          <h2 className="text-lg font-black text-slate-900">Dashboard</h2>
          <p className="text-xs text-slate-500">Visão geral das inspeções, ensaios, ordens de serviço e instrumentos deste aparelho</p>
        </div>
      </div>

      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
        <Kpi icon={ClipboardCheck} label="Inspeções" value={d.inspections.total} sub={`${d.inspections.done} concluída(s) · ${d.inspections.draft} em andamento`} onClick={() => navigateTo('spda')} />
        <Kpi icon={AlertTriangle} label="Não conformidades" value={d.ncs.total} sub={`${d.ncs.open} em inspeções em andamento`} tone={d.ncs.total ? 'text-red-700' : 'text-slate-900'} />
        <Kpi icon={Zap} label="Ensaios de continuidade" value={d.tests.measured ? `${pctOk} %` : '—'} sub={d.tests.measured ? `${d.tests.approved} de ${d.tests.measured} aprovados` : 'nenhuma medição'} tone={d.tests.rejected ? 'text-amber-700' : 'text-emerald-700'} />
        <Kpi icon={ClipboardList} label="Ordens de serviço" value={d.orders.total} sub={`${d.orders.open} em aberto · ${d.orders.withArt} com ART anexada`} onClick={() => navigateTo('os')} />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <Panel title="Parecer das inspeções" icon={ClipboardCheck}>
          {d.inspections.total === 0 ? <p className="text-xs text-slate-500">Nenhuma inspeção cadastrada.</p> : (
            <div className="space-y-2">
              <div className="flex h-3 rounded-full overflow-hidden bg-slate-100" role="img" aria-label="Distribuição dos pareceres">
                {(Object.keys(d.inspections.byResult) as ShownResult[]).map(r => d.inspections.byResult[r] > 0 && (
                  <div key={r} className={RESULT_BAR[r]} style={{ width: `${(d.inspections.byResult[r] / totalRes) * 100}%` }} title={`${RESULT_LABEL[r]}: ${d.inspections.byResult[r]}`} />
                ))}
              </div>
              <ul className="grid grid-cols-2 gap-1.5 text-xs">
                {(Object.keys(d.inspections.byResult) as ShownResult[]).map(r => (
                  <li key={r} className="flex items-center gap-1.5">
                    <span className={`w-2.5 h-2.5 rounded-sm ${RESULT_BAR[r]}`} />
                    <span className="text-slate-700">{RESULT_LABEL[r].charAt(0) + RESULT_LABEL[r].slice(1).toLowerCase()}</span>
                    <b className={`ml-auto ${RESULT_TXT[r]}`}>{d.inspections.byResult[r]}</b>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </Panel>

        <Panel title="Não conformidades por subsistema" icon={AlertTriangle}>
          {d.ncs.bySubsystem.length === 0 ? <p className="text-xs text-slate-500">Nenhuma não conformidade registrada.</p> : (
            <ul className="space-y-1.5">
              {d.ncs.bySubsystem.map(s => (
                <li key={s.subsystem} className="text-xs">
                  <div className="flex justify-between"><span className="text-slate-700">{s.label}</span><b className="text-red-700">{s.count}</b></div>
                  <div className="h-1.5 rounded-full bg-slate-100 overflow-hidden"><div className="h-full bg-red-400" style={{ width: `${(s.count / d.ncs.bySubsystem[0].count) * 100}%` }} /></div>
                </li>
              ))}
            </ul>
          )}
        </Panel>

        <Panel title="Próximas inspeções periódicas" icon={CalendarClock}
          right={<span className="text-[11px] text-slate-500">{d.overdue > 0 && <b className="text-red-700">{d.overdue} vencida(s) · </b>}{d.dueSoon} nos próximos 90 dias</span>}>
          {d.nextInspections.length === 0 ? <p className="text-xs text-slate-500">Sem inspeções concluídas para calcular a próxima data.</p> : (
            <ul className="divide-y divide-slate-100">
              {d.nextInspections.map(n => (
                <li key={n.id}>
                  <button type="button" onClick={() => openInspection(n.id)} className="w-full py-1.5 text-left text-xs hover:bg-slate-50 rounded">
                    <span className="flex items-center justify-between gap-2">
                      <span className="font-mono font-bold text-blue-700">{n.number}</span>
                      <span className={`whitespace-nowrap ${n.days < 0 ? 'text-red-700 font-bold' : n.days <= 90 ? 'text-amber-700 font-semibold' : 'text-slate-500'}`}>{formatDateBR(n.date)} · {daysTxt(n.days)}</span>
                    </span>
                    <span className="block truncate text-slate-600">{n.client}</span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </Panel>

        <Panel title="Calibração dos instrumentos" icon={Gauge}
          right={<span className="text-[11px] text-slate-500">{d.instruments.total} instrumento(s)</span>}>
          {calAlert === 0 ? (
            <p className="text-xs text-emerald-700">{d.instruments.total ? 'Nenhuma calibração vencida ou vencendo nos próximos 30 dias.' : 'Nenhum instrumento cadastrado.'}</p>
          ) : (
            <ul className="divide-y divide-slate-100">
              {[...d.instruments.expired, ...d.instruments.expiring].map(i => (
                <li key={i.id}>
                  <button type="button" onClick={() => navigateTo('instrumentos')} className="w-full flex flex-wrap items-center gap-x-2 py-1.5 text-left text-xs hover:bg-slate-50 rounded">
                    <span className="flex-1 min-w-[8rem] text-slate-700">{i.name}</span>
                    <span className={`whitespace-nowrap ${i.days < 0 ? 'text-red-700 font-bold' : 'text-amber-700 font-semibold'}`}>{formatDateBR(i.date)} · {i.days < 0 ? `vencida há ${-i.days} dia(s)` : `vence ${daysTxt(i.days)}`}</span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </Panel>

        <Panel title="Ordens de serviço por situação" icon={ClipboardList}>
          {d.orders.total === 0 ? <p className="text-xs text-slate-500">Nenhuma ordem de serviço.</p> : (
            <ul className="grid grid-cols-2 sm:grid-cols-3 gap-2">
              {(Object.keys(OS_STATUS) as ServiceOrderStatus[]).map(s => (
                <li key={s} className="border border-slate-200 rounded-lg px-2.5 py-1.5">
                  <span className="block text-[10px] text-slate-500">{OS_STATUS[s]}</span>
                  <b className="text-lg text-slate-900">{d.orders.byStatus[s] || 0}</b>
                </li>
              ))}
            </ul>
          )}
        </Panel>

        <Panel title="Inspeções recentes" icon={ClipboardCheck}>
          {d.recent.length === 0 ? <p className="text-xs text-slate-500">Nenhuma inspeção cadastrada.</p> : (
            <ul className="divide-y divide-slate-100">
              {d.recent.map(r => (
                <li key={r.id}>
                  <button type="button" onClick={() => openInspection(r.id)} className="w-full py-1.5 text-left text-xs hover:bg-slate-50 rounded">
                    <span className="flex items-center justify-between gap-2">
                      <span className="font-mono font-bold text-blue-700">{r.number}</span>
                      <span className={`font-bold whitespace-nowrap ${RESULT_TXT[r.result]}`}>{RESULT_LABEL[r.result]}</span>
                    </span>
                    <span className="block truncate text-slate-600">{r.client}{r.date ? ` · inspeção ${formatDateBR(r.date)}` : ''}{r.status === 'concluido' ? ' · concluída' : ' · em andamento'}</span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </Panel>
      </div>
    </div>
  );
};
