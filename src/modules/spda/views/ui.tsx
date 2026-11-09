/** Peças visuais do módulo SPDA (mesmas classes do módulo Treinamentos). */
import React, { useEffect, useState } from 'react';
import { parseDecimal } from '../rules';
import type { ChecklistStatus, TestStatus } from '../types';

export { inputCls, labelCls, btnPrimary, btnSecondary, btnDanger, cardCls, Field, EmptyState, alertError } from '../../treinamentos/views/ui';

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

export const CHECK_OPTIONS: Array<{ v: Exclude<ChecklistStatus, ''>; label: string; on: string }> = [
  { v: 'C', label: 'C', on: 'bg-emerald-600 text-white border-emerald-600' },
  { v: 'NC', label: 'NC', on: 'bg-red-600 text-white border-red-600' },
  { v: 'NA', label: 'NA', on: 'bg-slate-500 text-white border-slate-500' }
];
