/**
 * Situação da sincronização das inspeções SPDA com a plataforma (Supabase):
 * pendentes, erro, "Sincronizar agora" e conflitos aguardando decisão.
 */
import React, { useEffect, useState } from 'react';
import { AlertTriangle, CloudOff, CloudUpload, Check, Loader2, RefreshCw } from 'lucide-react';
import { getSpdaConflicts, pendingSpdaCount, subscribeSpda } from '../repository';
import { getSpdaSyncStatus, resolveSpdaConflictKeepMine, resolveSpdaConflictUseServer, subscribeSpdaSync, syncSpda } from '../sync';
import { notify } from './dialogs';

const timeOf = (iso?: string) => (iso ? new Date(iso).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' }) : '');

export const SyncBar: React.FC = () => {
  const [st, setSt] = useState(getSpdaSyncStatus());
  const [, setTick] = useState(0);
  const [online, setOnline] = useState(typeof navigator === 'undefined' ? true : navigator.onLine !== false);
  useEffect(() => subscribeSpdaSync(setSt), []);
  useEffect(() => subscribeSpda(() => setTick(t => t + 1)), []);
  useEffect(() => {
    const on = () => setOnline(true);
    const off = () => setOnline(false);
    window.addEventListener('online', on);
    window.addEventListener('offline', off);
    return () => { window.removeEventListener('online', on); window.removeEventListener('offline', off); };
  }, []);

  const pending = pendingSpdaCount();
  const conflicts = getSpdaConflicts();
  const resolve = async (fn: (id: string) => Promise<void>, id: string) => {
    try { await fn(id); } catch (e) { notify(`Não foi possível resolver: ${(e as Error).message}`); }
  };

  let icon = <Check className="w-3.5 h-3.5 text-emerald-600" />;
  let text = st.lastSyncAt ? `Sincronizado com a plataforma às ${timeOf(st.lastSyncAt)}` : 'Inspeções sincronizadas com a plataforma';
  let tone = 'bg-white border-slate-200 text-slate-600';
  if (st.running) { icon = <Loader2 className="w-3.5 h-3.5 animate-spin text-blue-600" />; text = 'Sincronizando…'; }
  else if (!online) { icon = <CloudOff className="w-3.5 h-3.5 text-slate-500" />; text = `Sem internet${pending ? ` — ${pending} alteração(ões) aguardando envio` : ' — trabalhando no aparelho'}`; }
  else if (st.lastError) { icon = <AlertTriangle className="w-3.5 h-3.5 text-amber-600" />; text = st.lastError; tone = 'bg-amber-50 border-amber-300 text-amber-900'; }
  else if (pending) { icon = <CloudUpload className="w-3.5 h-3.5 text-blue-600" />; text = `${pending} alteração(ões) aguardando envio`; }

  return (
    <div className="space-y-2">
      <div className={`flex items-center gap-2 rounded-xl border px-3 py-1.5 text-[11px] ${tone}`} role="status">
        {icon}
        <span className="flex-1 min-w-0 truncate" title={text}>{text}</span>
        <button type="button" onClick={() => void syncSpda()} disabled={st.running || !online}
          className="shrink-0 inline-flex items-center gap-1 font-bold text-blue-700 disabled:opacity-40">
          <RefreshCw className="w-3 h-3" /> Sincronizar
        </button>
      </div>
      {conflicts.map(c => (
        <div key={c.id} className="rounded-xl border border-red-300 bg-red-50 px-3 py-2 text-xs text-red-900 space-y-1.5">
          <p><b>Inspeção {c.label}</b> foi alterada em outro aparelho enquanto você editava. Escolha qual versão fica.</p>
          <div className="flex flex-wrap gap-2">
            <button type="button" className="rounded-lg bg-red-700 text-white font-bold px-3 py-1" onClick={() => void resolve(resolveSpdaConflictKeepMine, c.id)}>Manter a deste aparelho</button>
            <button type="button" className="rounded-lg border border-red-400 bg-white font-bold px-3 py-1" onClick={() => void resolve(resolveSpdaConflictUseServer, c.id)}>Usar a da plataforma</button>
          </div>
        </div>
      ))}
    </div>
  );
};
