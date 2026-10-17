import React, { useEffect, useState } from 'react';
import { AlertTriangle, Briefcase, CloudOff, RefreshCw } from 'lucide-react';
import { getGestaoConflicts, pendingGestaoCount, subscribeGestao } from '../repository';
import {
  getGestaoSyncStatus, resolveConflictKeepMine, resolveConflictUseServer, startGestaoSync, subscribeGestaoSync, syncGestao
} from '../sync';
import { GestaoDashboard } from './GestaoDashboard';
import { EntriesPanel } from './EntriesPanel';
import { CashFlowPanel } from './CashFlowPanel';
import { PricingPanel } from './PricingPanel';
import { CostSettingsPanel } from './CostSettingsPanel';
import { ServicesPanel } from './ServicesPanel';
import { BudgetsPanel } from './BudgetsPanel';
import { alertError, btnSecondary, cardCls } from './ui';

type Tab = 'painel' | 'receber' | 'pagar' | 'fluxo' | 'precificacao' | 'custos' | 'servicos' | 'orcamentos';

const TAB_TITLE: Record<Tab, string> = {
  painel: 'Dashboard — Gestão ADM',
  receber: 'Contas a receber',
  pagar: 'Contas a pagar',
  fluxo: 'Fluxo de caixa',
  precificacao: 'Precificação de serviços',
  custos: 'Custos e despesas',
  servicos: 'Cadastro de serviços',
  orcamentos: 'Orçamentos'
};

const TAB_SUBTITLE: Record<Tab, string> = {
  painel: 'Contas a pagar e a receber, saldo e precificações em aberto',
  receber: 'Valores a receber dos clientes, parcelas e recebimentos',
  pagar: 'Despesas, fornecedores, contas recorrentes e pagamentos',
  fluxo: 'Saldo realizado e projetado por dia e por mês',
  precificacao: 'Preço do serviço a partir dos custos de mão de obra, refeição e deslocamento',
  custos: 'Valores de referência da precificação e saldos iniciais do caixa',
  servicos: 'Serviços vendidos pela empresa, com preço pela planilha ou preço fixo',
  orcamentos: 'Orçamentos com serviços do cadastro, de precificações ou digitados na hora'
};

/** Telas do módulo Gestão ADM (cada uma é um subitem do menu). */
const GestaoScreen: React.FC<{ tab: Tab }> = ({ tab }) => {
  const [, setVersion] = useState(0);
  const [sync, setSync] = useState(getGestaoSyncStatus());

  useEffect(() => {
    const unsubData = subscribeGestao(() => setVersion(v => v + 1));
    const unsubSync = subscribeGestaoSync(setSync);
    const stop = startGestaoSync();
    return () => { unsubData(); unsubSync(); stop(); };
  }, []);

  const pending = pendingGestaoCount();
  const conflicts = getGestaoConflicts();

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-emerald-600 text-white flex items-center justify-center"><Briefcase className="w-5 h-5" /></div>
          <div>
            <h2 className="text-lg font-black text-slate-900">{TAB_TITLE[tab]}</h2>
            <p className="text-xs text-slate-500">{TAB_SUBTITLE[tab]}</p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <span className="text-[11px] text-slate-500 flex items-center gap-1">
            {typeof navigator !== 'undefined' && navigator.onLine === false && <CloudOff className="w-3.5 h-3.5" />}
            {pending > 0 ? `${pending} alteração(ões) a enviar` : sync.lastSyncAt ? `Sincronizado ${new Date(sync.lastSyncAt).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}` : 'Dados no aparelho'}
          </span>
          <button type="button" className={btnSecondary} disabled={sync.running} onClick={() => syncGestao()}>
            <RefreshCw className={`w-3.5 h-3.5 ${sync.running ? 'animate-spin' : ''}`} /> Sincronizar
          </button>
        </div>
      </div>

      {sync.lastError && (
        <div className="text-[11px] text-amber-800 bg-amber-50 border border-amber-200 rounded-xl px-3 py-2">{sync.lastError}</div>
      )}

      {conflicts.length > 0 && (
        <div className={`${cardCls} p-3 border-amber-300`}>
          <p className="text-xs font-bold text-amber-800 flex items-center gap-1.5 mb-2"><AlertTriangle className="w-4 h-4" /> Alterado em outro aparelho ao mesmo tempo</p>
          {conflicts.map(c => (
            <div key={`${c.table}|${c.id}`} className="flex flex-wrap items-center justify-between gap-2 py-1">
              <span className="text-xs text-slate-700">{c.label}</span>
              <div className="flex gap-2">
                <button type="button" className={btnSecondary} onClick={() => resolveConflictKeepMine(c.table, c.id).catch(err => alertError(err))}>Manter a deste aparelho</button>
                <button type="button" className={btnSecondary} onClick={() => resolveConflictUseServer(c.table, c.id).catch(err => alertError(err))}>Usar a do servidor</button>
              </div>
            </div>
          ))}
        </div>
      )}

      {tab === 'painel' && <GestaoDashboard />}
      {tab === 'receber' && <EntriesPanel kind="receber" />}
      {tab === 'pagar' && <EntriesPanel kind="pagar" />}
      {tab === 'fluxo' && <CashFlowPanel />}
      {tab === 'precificacao' && <PricingPanel />}
      {tab === 'custos' && <CostSettingsPanel />}
      {tab === 'servicos' && <ServicesPanel />}
      {tab === 'orcamentos' && <BudgetsPanel />}
    </div>
  );
};

/** Dashboard (tela principal do módulo) e demais subitens do menu. */
export const GestaoModuleView: React.FC = () => <GestaoScreen tab="painel" />;
export const GestaoReceivablesView: React.FC = () => <GestaoScreen tab="receber" />;
export const GestaoPayablesView: React.FC = () => <GestaoScreen tab="pagar" />;
export const GestaoCashFlowView: React.FC = () => <GestaoScreen tab="fluxo" />;
export const GestaoPricingView: React.FC = () => <GestaoScreen tab="precificacao" />;
export const GestaoCostSettingsView: React.FC = () => <GestaoScreen tab="custos" />;
export const GestaoServicesView: React.FC = () => <GestaoScreen tab="servicos" />;
export const GestaoBudgetsView: React.FC = () => <GestaoScreen tab="orcamentos" />;

export default GestaoModuleView;
