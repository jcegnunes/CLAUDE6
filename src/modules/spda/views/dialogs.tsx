/**
 * Caixas de confirmação e aviso do próprio app (no lugar de window.confirm /
 * window.alert, que alguns navegadores embutidos e WebViews bloqueiam — aí os
 * botões pareciam "não fazer nada").
 *
 *   if (await confirmAsk('Excluir?')) …       await notify('Salvo.')
 *
 * O <DialogHost /> fica montado uma vez na raiz do app.
 */
import React, { useEffect, useState } from 'react';
import { AlertTriangle, Info } from 'lucide-react';

type Pending = {
  id: number;
  kind: 'confirm' | 'alert';
  message: string;
  confirmLabel: string;
  danger: boolean;
  resolve: (ok: boolean) => void;
};

let seq = 0;
let queue: Pending[] = [];
const listeners = new Set<() => void>();
const emit = () => listeners.forEach(l => l());

function open(p: Omit<Pending, 'id' | 'resolve'>): Promise<boolean> {
  return new Promise<boolean>(resolve => {
    // sem a caixa montada (ex.: testes), cai no comportamento do navegador
    if (listeners.size === 0) {
      try {
        if (p.kind === 'confirm') resolve(typeof window !== 'undefined' ? window.confirm(p.message) : true);
        else { if (typeof window !== 'undefined') window.alert(p.message); resolve(true); }
      } catch { resolve(p.kind !== 'confirm'); }
      return;
    }
    queue = [...queue, { ...p, id: ++seq, resolve }];
    emit();
  });
}

/** Pergunta Sim/Não. danger = botão vermelho (exclusões). */
export function confirmAsk(message: string, opts: { confirmLabel?: string; danger?: boolean } = {}): Promise<boolean> {
  const verb = /^(Excluir|Remover|Descartar|Restaurar)/i.exec(message)?.[1];
  const danger = opts.danger ?? !!verb;
  return open({ kind: 'confirm', message, confirmLabel: opts.confirmLabel || (verb ? verb[0].toUpperCase() + verb.slice(1).toLowerCase() : 'Confirmar'), danger });
}

/** Aviso com botão OK. */
export async function notify(message: string): Promise<void> {
  await open({ kind: 'alert', message, confirmLabel: 'OK', danger: false });
}

export const DialogHost: React.FC = () => {
  const [, setV] = useState(0);
  useEffect(() => {
    const l = () => setV(x => x + 1);
    listeners.add(l);
    return () => { listeners.delete(l); };
  }, []);
  const current = queue[0];

  useEffect(() => {
    if (!current) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') close(false);
      if (e.key === 'Enter') close(true);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  });

  if (!current) return null;
  function close(ok: boolean) {
    const c = queue[0];
    if (!c) return;
    queue = queue.slice(1);
    c.resolve(c.kind === 'alert' ? true : ok);
    emit();
  }
  const Icon = current.kind === 'alert' ? Info : AlertTriangle;
  const confirmLabel = current.confirmLabel;

  return (
    <div className="fixed inset-0 z-[120] bg-slate-900/50 flex items-center justify-center p-4" onClick={() => close(false)}>
      <div role="alertdialog" aria-modal="true" aria-label={current.kind === 'alert' ? 'Aviso' : 'Confirmação'}
        className="bg-white w-full max-w-sm rounded-2xl shadow-2xl p-5" onClick={e => e.stopPropagation()}>
        <div className="flex items-start gap-3">
          <div className={`w-9 h-9 shrink-0 rounded-full flex items-center justify-center ${current.danger ? 'bg-red-100 text-red-600' : current.kind === 'alert' ? 'bg-blue-100 text-blue-600' : 'bg-amber-100 text-amber-600'}`}>
            <Icon className="w-5 h-5" />
          </div>
          <p className="text-sm text-slate-800 whitespace-pre-line pt-1.5">{current.message}</p>
        </div>
        <div className="flex justify-end gap-2 mt-5">
          {current.kind === 'confirm' && (
            <button type="button" onClick={() => close(false)}
              className="min-h-[44px] px-4 py-2 bg-white border border-[#0A2540]/40 hover:bg-[#EFF6FF] text-[#0A2540] text-sm font-semibold rounded-xl">Cancelar</button>
          )}
          <button type="button" autoFocus onClick={() => close(true)}
            className={`min-h-[44px] px-4 py-2 text-white text-sm font-bold rounded-xl ${current.danger ? 'bg-red-600 hover:bg-red-700' : 'bg-[#F97316] hover:bg-[#EA580C]'}`}>{confirmLabel}</button>
        </div>
      </div>
    </div>
  );
};
