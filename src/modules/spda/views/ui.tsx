/**
 * Peças visuais do JVM SPDA — no padrão do APP de Ensaios de EPI (Material):
 * campos contornados altos com o nome do campo na borda, texto 16 px no celular
 * (sem zoom ao tocar), contorno cinza e foco azul-marinho #0A2540, botões principais
 * laranja #F97316 e cartões brancos com cantos 16.
 */
import React, { useEffect, useState } from 'react';
import { CheckCircle2, Clock, Info, X, XCircle } from 'lucide-react';
import { parseDecimal } from '../rules';
import type { ChecklistStatus, TestStatus } from '../types';
import { notify } from './dialogs';

export const inputCls = 'w-full min-h-[48px] px-3.5 py-2.5 border border-[#94A3B8] rounded-xl bg-white text-base sm:text-sm text-black placeholder:text-slate-500 hover:border-slate-600 focus:border-[#0A2540] focus:ring-1 focus:ring-[#0A2540] focus:outline-none disabled:bg-slate-100 disabled:text-slate-500 transition-colors';
export const labelCls = 'block font-semibold text-black mb-1 text-xs';
export const btnPrimary = 'inline-flex items-center justify-center gap-1.5 min-h-[44px] px-4 py-2 bg-[#F97316] hover:bg-[#EA580C] active:bg-[#C2410C] disabled:opacity-50 text-white text-sm font-bold rounded-xl shadow-sm transition-colors';
export const btnSecondary = 'inline-flex items-center justify-center gap-1.5 min-h-[44px] px-4 py-2 bg-white border border-[#0A2540]/40 hover:bg-[#EFF6FF] disabled:opacity-50 text-[#0A2540] text-sm font-semibold rounded-xl transition-colors';
export const btnDanger = 'inline-flex items-center justify-center gap-1.5 min-h-[44px] px-4 py-2 bg-white border border-red-300 hover:bg-red-50 text-red-700 text-sm font-semibold rounded-xl transition-colors';
export const cardCls = 'bg-white rounded-2xl border border-slate-200 shadow-sm';

/** O campo é uma caixa só (input, select, textarea ou número)? Então o nome vai na borda, como no app de EPI. */
function isSingleBox(children: React.ReactNode): boolean {
  const list = React.Children.toArray(children);
  if (list.length !== 1 || !React.isValidElement(list[0])) return false;
  const t = list[0].type;
  return t === 'input' || t === 'select' || t === 'textarea' || t === DecimalField;
}

export const Field: React.FC<{ label: string; children: React.ReactNode; className?: string; hint?: string }> = ({ label, children, className, hint }) => {
  if (isSingleBox(children)) {
    return (
      <label className={`block relative pt-2 ${className || ''}`}>
        <span className="absolute left-3 top-0 z-[1] max-w-[calc(100%-1.5rem)] truncate px-1 bg-[linear-gradient(to_bottom,transparent_40%,#fff_40%)] text-[11px] leading-4 font-medium text-black pointer-events-none">{label}</span>
        {children}
        {hint && <span className="block text-[11px] text-slate-600 mt-1 px-1">{hint}</span>}
      </label>
    );
  }
  return (
    <label className={`block ${className || ''}`}>
      <span className={labelCls}>{label}</span>
      {children}
      {hint && <span className="block text-[11px] text-slate-600 mt-1">{hint}</span>}
    </label>
  );
};

export const Modal: React.FC<{ title: string; onClose: () => void; children: React.ReactNode; footer?: React.ReactNode; wide?: boolean }> = ({ title, onClose, children, footer, wide }) => (
  <div className="fixed inset-0 z-[60] bg-slate-900/50 flex items-end sm:items-center justify-center sm:p-4" onClick={onClose}>
    <div
      className={`bg-white w-full ${wide ? 'sm:max-w-4xl' : 'sm:max-w-xl'} max-h-[94vh] flex flex-col rounded-t-2xl sm:rounded-2xl shadow-2xl`}
      onClick={e => e.stopPropagation()}
      role="dialog"
      aria-label={title}
    >
      <div className="flex items-center justify-between px-4 py-3 border-b border-slate-200">
        <h3 className="font-bold text-sm text-slate-900">{title}</h3>
        <button type="button" onClick={onClose} className="p-1.5 rounded-lg hover:bg-slate-100 text-slate-500" aria-label="Fechar">
          <X className="w-4 h-4" />
        </button>
      </div>
      <div className="p-4 overflow-y-auto flex-1">{children}</div>
      {footer && <div className="px-4 py-3 border-t border-slate-200 flex flex-wrap justify-end gap-2">{footer}</div>}
    </div>
  </div>
);

export const EmptyState: React.FC<{ icon: React.ElementType; title: string; text?: string; action?: React.ReactNode }> = ({ icon: Icon, title, text, action }) => (
  <div className={`${cardCls} p-8 text-center`}>
    <Icon className="w-10 h-10 text-slate-300 mx-auto mb-2" />
    <p className="font-bold text-sm text-slate-700">{title}</p>
    {text && <p className="text-xs text-slate-500 mt-1 max-w-md mx-auto">{text}</p>}
    {action && <div className="mt-4">{action}</div>}
  </div>
);

export function alertError(err: unknown, prefix = 'Não foi possível concluir') {
  const msg = err instanceof Error ? err.message : String(err);
  void notify(`${prefix}: ${msg}`);
}

/** Campo numérico com vírgula decimal; devolve null quando vazio. */
export const DecimalField: React.FC<{
  value: number | null | undefined;
  onChange: (v: number | null) => void;
  className?: string;
  placeholder?: string;
  disabled?: boolean;
  ariaLabel?: string;
}> = ({ value, onChange, className, placeholder, disabled, ariaLabel }) => {
  const fmt = (v: number | null | undefined) => (typeof v === 'number' && Number.isFinite(v) ? String(v).replace('.', ',') : '');
  const [text, setText] = useState(fmt(value));
  useEffect(() => {
    // valor alterado por fora (ex.: outro campo) sem perder o que está sendo digitado
    if (parseDecimal(text) !== (value ?? null)) setText(fmt(value));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value]);
  return (
    <input
      type="text"
      inputMode="decimal"
      aria-label={ariaLabel}
      className={className}
      placeholder={placeholder}
      disabled={disabled}
      value={text}
      onChange={e => { setText(e.target.value); onChange(parseDecimal(e.target.value)); }}
    />
  );
};

const TEST_CLS: Record<TestStatus, string> = {
  conforme: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  nao_conforme: 'bg-red-50 text-red-700 border-red-200',
  pendente: 'bg-slate-100 text-slate-500 border-slate-300',
  informativo: 'bg-sky-50 text-sky-700 border-sky-200'
};
const TEST_TXT: Record<TestStatus, string> = { conforme: 'Conforme', nao_conforme: 'Não conforme', pendente: 'Pendente', informativo: 'Informativo' };

export const TestBadge: React.FC<{ status: TestStatus }> = ({ status }) => (
  <span className={`inline-block px-2 py-0.5 rounded-full border text-[10px] font-bold whitespace-nowrap ${TEST_CLS[status]}`}>{TEST_TXT[status]}</span>
);

/** Faixa de resultado do ensaio: deixa evidente se está conforme, reprovado, pendente ou informativo. */
const RESULT_STYLE: Record<TestStatus, { box: string; icon: string; title: string; Icon: React.FC<{ className?: string }> }> = {
  conforme: { box: 'bg-emerald-50 border-emerald-300 text-emerald-800', icon: 'bg-emerald-600 text-white', title: 'CONFORME', Icon: CheckCircle2 },
  nao_conforme: { box: 'bg-red-50 border-red-300 text-red-800', icon: 'bg-red-600 text-white', title: 'REPROVADO', Icon: XCircle },
  pendente: { box: 'bg-amber-50 border-amber-300 text-amber-800', icon: 'bg-amber-500 text-white', title: 'PENDENTE', Icon: Clock },
  informativo: { box: 'bg-sky-50 border-sky-300 text-sky-800', icon: 'bg-sky-600 text-white', title: 'INFORMATIVO', Icon: Info }
};

/** Cor da borda lateral do cartão do ensaio, conforme o resultado. */
export const RESULT_ACCENT: Record<TestStatus, string> = {
  conforme: 'border-l-emerald-500', nao_conforme: 'border-l-red-500', pendente: 'border-l-amber-400', informativo: 'border-l-sky-500'
};

export const TestResultBanner: React.FC<{ status: TestStatus; detail?: React.ReactNode; extra?: React.ReactNode }> = ({ status, detail, extra }) => {
  const s = RESULT_STYLE[status];
  return (
    <div role="status" aria-label={`Resultado do ensaio: ${s.title}`} className={`flex items-center gap-3 border rounded-lg px-3 py-2 ${s.box}`}>
      <span className={`shrink-0 w-8 h-8 rounded-full flex items-center justify-center ${s.icon}`}><s.Icon className="w-5 h-5" /></span>
      <div className="min-w-0 flex-1">
        <p className="text-[10px] font-bold uppercase tracking-wide opacity-70 leading-none">Resultado do ensaio</p>
        <p className="text-sm font-extrabold tracking-wide leading-tight mt-0.5">{s.title}</p>
        {detail && <p className="text-[11px] leading-snug mt-0.5">{detail}</p>}
      </div>
      {extra}
    </div>
  );
};

export const CHECK_OPTIONS: Array<{ v: Exclude<ChecklistStatus, ''>; label: string; on: string }> = [
  { v: 'C', label: 'C', on: 'bg-emerald-600 text-white border-emerald-600' },
  { v: 'NC', label: 'NC', on: 'bg-red-600 text-white border-red-600' },
  { v: 'NA', label: 'NA', on: 'bg-slate-500 text-white border-slate-500' }
];
