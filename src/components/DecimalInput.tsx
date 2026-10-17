/**
 * Campo numérico decimal que pode ficar vazio enquanto se digita: apagar o
 * valor não recoloca "0" no campo (o valor numérico vira 0 só por baixo).
 * Aceita vírgula ou ponto como separador decimal.
 */
import React, { useEffect, useState } from 'react';

export function parseDecimal(text: string): number {
  const t = String(text ?? '').trim().replace(/\s+/g, '').replace(',', '.');
  if (!t || t === '-' || t === '.' || t === '-.') return NaN;
  const n = Number(t);
  return Number.isFinite(n) ? n : NaN;
}

const toText = (v: number | undefined | null) => (typeof v === 'number' && Number.isFinite(v) ? String(v) : '');

type Props = Omit<React.InputHTMLAttributes<HTMLInputElement>, 'value' | 'onChange' | 'type'> & {
  value: number | undefined | null;
  onValue: (n: number) => void;
};

export const DecimalInput: React.FC<Props> = ({ value, onValue, ...rest }) => {
  const [text, setText] = useState(toText(value));

  // valor trocado de fora (ex.: ensaio carregado): atualiza o texto, sem brigar com a digitação
  useEffect(() => {
    const current = parseDecimal(text);
    if (Number.isNaN(current) ? value !== 0 && value != null : current !== value) setText(toText(value));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value]);

  return (
    <input
      {...rest}
      type="text"
      inputMode="decimal"
      autoComplete="off"
      value={text}
      onChange={e => {
        const t = e.target.value.replace(/[^0-9.,-]/g, '');
        setText(t);
        const n = parseDecimal(t);
        onValue(Number.isNaN(n) ? 0 : n);
      }}
    />
  );
};

export default DecimalInput;
