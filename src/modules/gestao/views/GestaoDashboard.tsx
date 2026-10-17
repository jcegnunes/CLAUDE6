import React from 'react';
import { AlertTriangle, ArrowDownCircle, ArrowUpCircle, Calculator, Wallet } from 'lucide-react';
import { navigateTo } from '../../registry';
import { canManageFinance, getBudgets, getEntries, getSettings } from '../repository';
import {
  DUE_SOON_DAYS, addDays, cashFlowMovements, currentBalance, entrySituation, formatDateBr, formatMoney, openAmount, openingBalance, todayIso
} from '../rules';
import { requestEntriesFilter } from './EntriesPanel';
import { SituationBadge, cardCls } from './ui';

export const GestaoDashboard: React.FC = () => {
  const today = todayIso();
  const finance = canManageFinance();
  const budgets = getBudgets();
  const pendingBudgets = budgets.filter(b => b.status === 'rascunho' || b.status === 'enviada');

  if (!finance) {
    return (
      <div className={`${cardCls} p-4`}>
        <p className="text-sm font-bold text-slate-900">Orçamentos</p>
        <p className="text-xs text-slate-500 mt-1">{pendingBudgets.length} orçamento(s) em aberto.</p>
        <button type="button" className="mt-3 text-xs font-bold text-blue-700 hover:underline" onClick={() => navigateTo('gestao_orcamentos')}>Abrir orçamentos</button>
      </div>
    );
  }

  const settings = getSettings();
  const entries = getEntries();
  const open = (kind: 'pagar' | 'receber') => entries.filter(e => e.kind === kind && openAmount(e) > 0);
  const sum = (list: typeof entries) => list.reduce((s, e) => s + openAmount(e), 0);
  const receivable = open('receber');
  const payable = open('pagar');
  const overdueRec = receivable.filter(e => entrySituation(e, today) === 'vencido');
  const overduePay = payable.filter(e => entrySituation(e, today) === 'vencido');
  const movements = cashFlowMovements(entries, today);
  const balance = currentBalance(movements, openingBalance(settings), settings.openingDate, today);
  const in30 = addDays(today, 30);
  const projected30 = movements.filter(m => !m.realized && m.date <= in30).reduce((s, m) => s + (m.kind === 'receber' ? m.amount : -m.amount), balance);

  const go = (view: 'gestao_receber' | 'gestao_pagar', filter: 'abertas' | 'vencidas' | 'semana') => {
    requestEntriesFilter(filter);
    navigateTo(view);
  };

  const cards = [
    { label: 'A receber em aberto', value: formatMoney(sum(receivable)), sub: `${receivable.length} conta(s)`, icon: ArrowDownCircle, cls: 'text-emerald-700 bg-emerald-50', onClick: () => go('gestao_receber', 'abertas') },
    { label: 'A pagar em aberto', value: formatMoney(sum(payable)), sub: `${payable.length} conta(s)`, icon: ArrowUpCircle, cls: 'text-red-700 bg-red-50', onClick: () => go('gestao_pagar', 'abertas') },
    { label: 'Saldo atual', value: formatMoney(balance), sub: `Em 30 dias: ${formatMoney(projected30)}`, icon: Wallet, cls: 'text-blue-700 bg-blue-50', onClick: () => navigateTo('gestao_fluxo') },
    { label: 'Orçamentos em aberto', value: String(pendingBudgets.length), sub: formatMoney(pendingBudgets.reduce((s, b) => s + (b.total || 0), 0)), icon: Calculator, cls: 'text-slate-700 bg-slate-100', onClick: () => navigateTo('gestao_orcamentos') }
  ];

  const soon = addDays(today, DUE_SOON_DAYS);
  const attention = [...receivable, ...payable]
    .filter(e => e.dueDate <= soon)
    .sort((a, b) => a.dueDate.localeCompare(b.dueDate))
    .slice(0, 15);

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
        {cards.map(c => (
          <button key={c.label} type="button" onClick={c.onClick} className={`${cardCls} p-4 text-left hover:border-blue-300 transition-colors`}>
            <div className={`w-9 h-9 rounded-xl flex items-center justify-center ${c.cls}`}><c.icon className="w-5 h-5" /></div>
            <div className="text-lg font-black text-slate-900 mt-2">{c.value}</div>
            <div className="text-[11px] font-semibold text-slate-500">{c.label}</div>
            <div className="text-[10px] text-slate-400">{c.sub}</div>
          </button>
        ))}
      </div>

      {(overdueRec.length > 0 || overduePay.length > 0) && (
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          {overdueRec.length > 0 && (
            <button type="button" onClick={() => go('gestao_receber', 'vencidas')} className="text-left text-xs text-amber-900 bg-amber-50 border border-amber-200 rounded-xl px-3 py-2 flex items-center gap-2">
              <AlertTriangle className="w-4 h-4 shrink-0" /> {overdueRec.length} recebimento(s) vencido(s): {formatMoney(sum(overdueRec))} a cobrar
            </button>
          )}
          {overduePay.length > 0 && (
            <button type="button" onClick={() => go('gestao_pagar', 'vencidas')} className="text-left text-xs text-red-900 bg-red-50 border border-red-200 rounded-xl px-3 py-2 flex items-center gap-2">
              <AlertTriangle className="w-4 h-4 shrink-0" /> {overduePay.length} pagamento(s) vencido(s): {formatMoney(sum(overduePay))}
            </button>
          )}
        </div>
      )}

      <div className={`${cardCls} p-4`}>
        <h3 className="font-bold text-sm text-slate-900 mb-1">Vencidas e próximos {DUE_SOON_DAYS} dias</h3>
        {attention.length === 0 ? (
          <p className="text-xs text-emerald-700">Nada vencendo nos próximos dias.</p>
        ) : (
          <div className="divide-y divide-slate-100">
            {attention.map(e => (
              <button key={e.id} type="button" onClick={() => go(e.kind === 'pagar' ? 'gestao_pagar' : 'gestao_receber', 'abertas')}
                className="w-full py-2 flex flex-wrap items-center gap-2 justify-between text-left hover:bg-slate-50">
                <div className="min-w-0">
                  <p className="text-xs font-bold text-slate-800">
                    <span className={e.kind === 'pagar' ? 'text-red-700' : 'text-emerald-700'}>{e.kind === 'pagar' ? '↑ Pagar' : '↓ Receber'}</span> · {e.description}
                  </p>
                  <p className="text-[11px] text-slate-500 truncate">{e.counterpartyName} · vence {formatDateBr(e.dueDate)}</p>
                </div>
                <div className="text-right">
                  <p className="text-xs font-black">{formatMoney(openAmount(e))}</p>
                  <SituationBadge situation={entrySituation(e, today)} />
                </div>
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};
