/**
 * Barras do APP JVM SPDA, no padrão do APP de Ensaios de EPI:
 * barra superior azul-marinho com o título centralizado e o logotipo num quadrado laranja,
 * e barra inferior branca (Painel, Inspeções, Nova inspeção, Relatório, Menu).
 * Só no app (APP_TARGET = 'spda'); a plataforma web mantém a barra e o menu dela.
 */
import React from 'react';
import { BookOpenText, ClipboardList, LayoutDashboard, Menu, Plus, RefreshCw, Zap } from 'lucide-react';
import { requestNewInspection } from '../modules/spda/views/navigation';

export const SpdaAppTopBar: React.FC<{
  onOpenMenu: () => void;
  onSync: () => void;
  isSyncing: boolean;
  pendingSyncCount: number;
  isOnline: boolean;
}> = ({ onOpenMenu, onSync, isSyncing, pendingSyncCount, isOnline }) => (
  <header className="sticky top-0 z-40 bg-[#0A2540] text-white shadow-md safe-area-pt">
    <div className="h-14 px-2 flex items-center">
      <button type="button" onClick={onOpenMenu} className="w-11 h-11 flex items-center justify-center rounded-full hover:bg-white/10" aria-label="Abrir menu">
        <Menu className="w-6 h-6" />
      </button>
      <div className="flex-1 flex items-center justify-center gap-2 min-w-0">
        <span className="w-7 h-7 shrink-0 rounded-md bg-[#F97316] flex items-center justify-center"><Zap className="w-4 h-4 text-white" /></span>
        <span className="font-bold text-base truncate">JVM SPDA</span>
        {!isOnline && <span className="text-[10px] font-bold px-1.5 py-0.5 rounded bg-white/15">offline</span>}
      </div>
      <button type="button" onClick={onSync} disabled={isSyncing} className="relative w-11 h-11 flex items-center justify-center rounded-full hover:bg-white/10 disabled:opacity-60" aria-label="Sincronizar com a plataforma">
        <RefreshCw className={`w-5 h-5 ${isSyncing ? 'animate-spin' : ''}`} />
        {pendingSyncCount > 0 && (
          <span className="absolute top-1 right-1 min-w-4 h-4 px-1 rounded-full bg-[#F97316] text-white font-bold text-[9px] flex items-center justify-center">{pendingSyncCount}</span>
        )}
      </button>
    </div>
  </header>
);

export const SpdaAppBottomNav: React.FC<{
  activeView: string;
  onNavigate: (view: string) => void;
  onOpenMenu: () => void;
}> = ({ activeView, onNavigate, onOpenMenu }) => {
  const item = (id: string, label: string, Icon: React.ElementType, onClick: () => void, accent = false) => {
    const on = activeView === id;
    const color = accent ? 'text-[#F97316]' : on ? 'text-[#0A2540]' : 'text-slate-600';
    return (
      <button key={label} type="button" onClick={onClick} aria-current={on ? 'page' : undefined}
        className={`flex-1 min-w-0 h-full flex flex-col items-center justify-center gap-0.5 ${color}`}>
        <span className={`h-8 w-14 rounded-full flex items-center justify-center ${on ? (accent ? 'bg-[#FFF7ED]' : 'bg-[#EFF6FF]') : ''}`}>
          <Icon className="w-5 h-5" />
        </span>
        <span className={`text-[10px] leading-tight truncate max-w-full ${on ? 'font-bold' : 'font-medium'}`}>{label}</span>
      </button>
    );
  };
  return (
    <nav className="fixed bottom-0 left-0 right-0 z-40 bg-white border-t border-slate-200 shadow-[0_-2px_8px_rgba(15,23,42,0.08)] safe-area-pb" aria-label="Navegação do app">
      <div className="h-16 flex items-stretch px-1">
        {item('spda_dashboard', 'Painel', LayoutDashboard, () => onNavigate('spda_dashboard'))}
        {item('spda', 'Inspeções', ClipboardList, () => onNavigate('spda'))}
        {item('spda_nova', 'Nova inspeção', Plus, () => { requestNewInspection(); onNavigate('spda'); }, true)}
        {item('spda_relatorio', 'Relatório', BookOpenText, () => onNavigate('spda_relatorio'))}
        {item('menu', 'Menu', Menu, onOpenMenu)}
      </div>
    </nav>
  );
};
