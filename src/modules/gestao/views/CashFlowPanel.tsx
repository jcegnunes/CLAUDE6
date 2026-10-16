/** Fluxo de caixa: saldo realizado e projetado por mês (12 meses) e por dia (mês escolhido). */
import React, { useState } from 'react';
import { Bar, BarChart, CartesianGrid, Legend, Line, ComposedChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { canManageFinance, getEntries, getSettings } from '../repository';
import {
  addMonths, cashFlowByPeriod, cashFlowMovements, currentBalance, formatDateBr, formatMoney, openingBalance, todayIso
} from '../rules';
import { NoAccess, cardCls, inputCls } from './ui';

const MONTHS = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez'];
const monthLabel = (key: string) => `${MONTHS[+key.slice(5, 7) - 1]}/${key.slice(2, 4)}`;
const lastDayOf = (iso: string) => new Date(Date.UTC(+iso.slice(0, 4), +iso.slice(5, 7), 0)).toISOString().slice(0, 10);

export const CashFlowPanel: React.FC = () => {
  const today = todayIso();
  const [month, setMonth] = useState(today.slice(0, 7));
  const [accountId, setAccountId] = useState('');
  if (!canManageFinance()) return <NoAccess text="Fluxo de caixa: acesso do administrador e do perfil administrativo." />;

  const settings = getSettings();
  const accounts = accountId ? settings.accounts.filter(a => a.id === accountId) : settings.accounts;
  const opening = openingBalance({ accounts });
  const movements = cashFlowMovements(getEntries(), today)
    .filter(m => !accountId || (m.accountId || settings.accounts[0]?.id) === accountId);
  const balanceNow = currentBalance(movements, opening, settings.openingDate, today);

  // 12 meses: 2 anteriores + atual + 9 seguintes
  const thisMonth = `${today.slice(0, 7)}-01`;
  const yearly = cashFlowByPeriod(movements, {
    from: addMonths(thisMonth, -2), to: lastDayOf(addMonths(thisMonth, 9)), granularity: 'month', opening, openingDate: settings.openingDate
  });
  const chartData = yearly.periods
    .map(p => ({
      mes: monthLabel(p.key),
      Entradas: p.inRealized + p.inForecast,
      Saídas: p.outRealized + p.outForecast,
      Saldo: p.balance
    }));

  const monthStart = `${month}-01`;
  const daily = cashFlowByPeriod(movements, { from: monthStart, to: lastDayOf(monthStart), granularity: 'day', opening, openingDate: settings.openingDate });
  const activeDays = daily.periods.filter(p => p.inRealized || p.inForecast || p.outRealized || p.outForecast);
  const totals = daily.periods.reduce((t, p) => ({
    inR: t.inR + p.inRealized, inF: t.inF + p.inForecast, outR: t.outR + p.outRealized, outF: t.outF + p.outForecast
  }), { inR: 0, inF: 0, outR: 0, outF: 0 });
  const endBalance = daily.periods[daily.periods.length - 1]?.balance ?? daily.startBalance;
  const negativeDay = daily.periods.find(p => p.balance < 0 && p.key >= today);

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
        <div className={`${cardCls} p-3`}>
          <p className="text-[11px] font-semibold text-slate-500">Saldo atual (realizado)</p>
          <p className={`text-lg font-black ${balanceNow < 0 ? 'text-red-700' : 'text-slate-900'}`}>{formatMoney(balanceNow)}</p>
          <p className="text-[10px] text-slate-400">Saldo inicial {formatMoney(opening)} em {formatDateBr(settings.openingDate)}</p>
        </div>
        <div className={`${cardCls} p-3`}>
          <p className="text-[11px] font-semibold text-slate-500">Entradas no mês</p>
          <p className="text-lg font-black text-emerald-700">{formatMoney(totals.inR + totals.inF)}</p>
          <p className="text-[10px] text-slate-400">{formatMoney(totals.inR)} recebido · {formatMoney(totals.inF)} previsto</p>
        </div>
        <div className={`${cardCls} p-3`}>
          <p className="text-[11px] font-semibold text-slate-500">Saídas no mês</p>
          <p className="text-lg font-black text-red-700">{formatMoney(totals.outR + totals.outF)}</p>
          <p className="text-[10px] text-slate-400">{formatMoney(totals.outR)} pago · {formatMoney(totals.outF)} previsto</p>
        </div>
        <div className={`${cardCls} p-3`}>
          <p className="text-[11px] font-semibold text-slate-500">Saldo projetado no fim do mês</p>
          <p className={`text-lg font-black ${endBalance < 0 ? 'text-red-700' : 'text-blue-700'}`}>{formatMoney(endBalance)}</p>
        </div>
      </div>

      {negativeDay && (
        <div className="text-[11px] text-red-800 bg-red-50 border border-red-200 rounded-xl px-3 py-2">
          Atenção: o saldo projetado fica negativo em {formatDateBr(negativeDay.key)} ({formatMoney(negativeDay.balance)}). Antecipe recebimentos ou renegocie pagamentos.
        </div>
      )}

      <div className={`${cardCls} p-4`}>
        <div className="flex flex-wrap items-center justify-between gap-2 mb-2">
          <h3 className="font-bold text-sm text-slate-900">Próximos 12 meses</h3>
          {settings.accounts.length > 1 && (
            <div className="w-48 max-w-full"><select className={inputCls} value={accountId} onChange={e => setAccountId(e.target.value)} aria-label="Conta">
              <option value="">Todas as contas</option>
              {settings.accounts.map(a => <option key={a.id} value={a.id}>{a.name}</option>)}
            </select></div>
          )}
        </div>
        <p className="text-[11px] text-slate-500 mb-3">Realizado (pagamentos registrados) + previsto (contas em aberto pelo vencimento; as vencidas entram como previstas para hoje).</p>
        <div className="h-64">
          <ResponsiveContainer width="100%" height="100%">
            <ComposedChart data={chartData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#E2E8F0" />
              <XAxis dataKey="mes" tick={{ fontSize: 11 }} />
              <YAxis tick={{ fontSize: 10 }} width={70} tickFormatter={v => `${Math.round(Number(v) / 1000)} mil`} />
              <Tooltip formatter={(v: number) => formatMoney(v)} />
              <Legend wrapperStyle={{ fontSize: 11 }} />
              <Bar dataKey="Entradas" fill="#10B981" radius={[4, 4, 0, 0]} />
              <Bar dataKey="Saídas" fill="#EF4444" radius={[4, 4, 0, 0]} />
              <Line dataKey="Saldo" stroke="#2563EB" strokeWidth={2} dot={{ r: 2 }} />
            </ComposedChart>
          </ResponsiveContainer>
        </div>
      </div>

      <div className={`${cardCls} p-4`}>
        <div className="flex flex-wrap items-center justify-between gap-2 mb-3">
          <h3 className="font-bold text-sm text-slate-900">Movimento diário</h3>
          <div className="w-44 max-w-full"><input type="month" className={inputCls} value={month} onChange={e => e.target.value && setMonth(e.target.value)} aria-label="Mês" /></div>
        </div>
        <p className="text-xs text-slate-600 mb-2">Saldo no início do mês: <b>{formatMoney(daily.startBalance)}</b></p>
        {activeDays.length === 0 ? (
          <p className="text-xs text-slate-500">Nenhum movimento neste mês.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-xs min-w-[560px]">
              <thead className="bg-slate-50 text-slate-500 text-[11px] uppercase">
                <tr>
                  <th className="p-2 text-left">Dia</th>
                  <th className="p-2 text-right">Entradas</th>
                  <th className="p-2 text-right">Saídas</th>
                  <th className="p-2 text-right">Saldo do dia</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {activeDays.map(p => {
                  const isPast = p.key < today;
                  return (
                    <tr key={p.key}>
                      <td className="p-2 font-mono">{formatDateBr(p.key)}{p.key === today ? ' (hoje)' : ''}</td>
                      <td className="p-2 text-right text-emerald-700">
                        {formatMoney(p.inRealized + p.inForecast)}
                        {p.inForecast > 0 && <span className="block text-[10px] text-slate-400">{formatMoney(p.inForecast)} previsto</span>}
                      </td>
                      <td className="p-2 text-right text-red-700">
                        {formatMoney(p.outRealized + p.outForecast)}
                        {p.outForecast > 0 && <span className="block text-[10px] text-slate-400">{formatMoney(p.outForecast)} previsto</span>}
                      </td>
                      <td className={`p-2 text-right font-black ${p.balance < 0 ? 'text-red-700' : isPast ? 'text-slate-700' : 'text-blue-700'}`}>{formatMoney(p.balance)}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
};
