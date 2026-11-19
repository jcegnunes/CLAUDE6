/**
 * Pedidos de conta ("Criar conta" na tela de login) — só para o administrador master.
 * Aprovar cria o usuário administrador da nova empresa (ele cadastra a empresa no 1º acesso);
 * recusar só fecha o pedido. Regras no banco: supabase/modules/cadastro.sql.
 */
import React, { useCallback, useEffect, useState } from 'react';
import { Check, RefreshCw, UserPlus, X } from 'lucide-react';
import { AuthService } from '../services/authService';

interface AccountRequest {
  id: string; name: string; email: string; username?: string | null; phone?: string | null;
  companyName?: string | null; cnpj?: string | null; module?: string | null; status: string;
  decisionNote?: string | null; decidedAt?: string | null; createdAt: string;
}

const fmtDoc = (d?: string | null) => {
  const s = (d || '').replace(/\D/g, '');
  if (s.length === 14) return s.replace(/^(\d{2})(\d{3})(\d{3})(\d{4})(\d{2})$/, '$1.$2.$3/$4-$5');
  if (s.length === 11) return s.replace(/^(\d{3})(\d{3})(\d{3})(\d{2})$/, '$1.$2.$3-$4');
  return s;
};
const MODULE_LABEL: Record<string, string> = { spda: 'APP JVM SPDA', ensaios: 'Ensaios de EPI', treinamentos: 'Treinamentos' };

export const AccountRequestsPanel: React.FC = () => {
  const me = AuthService.getCurrentUser();
  const [status, setStatus] = useState<'pendente' | 'aprovado' | 'recusado'>('pendente');
  const [list, setList] = useState<AccountRequest[]>([]);
  const [loading, setLoading] = useState(false);
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [notes, setNotes] = useState<Record<string, string>>({});

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const { SupabaseService } = await import('../services/supabaseService');
      const { data, error } = await SupabaseService.getClient().rpc('jvm_list_account_requests', { p_status: status });
      if (error) throw new Error(error.message);
      const rows = (typeof data === 'string' ? JSON.parse(data) : data) as AccountRequest[] | null;
      setList(Array.isArray(rows) ? rows : []);
      setMsg(null);
    } catch (err) {
      const t = err instanceof Error ? err.message : String(err);
      setMsg({ ok: false, text: /jvm_list_account_requests|does not exist|schema cache/i.test(t)
        ? 'O cadastro de contas ainda não está no banco: execute supabase/modules/cadastro.sql no Supabase.'
        : `Não foi possível carregar os pedidos (precisa de internet): ${t}` });
      setList([]);
    } finally {
      setLoading(false);
    }
  }, [status]);

  useEffect(() => { if (me?.isMasterAdmin) void load(); }, [load, me?.isMasterAdmin]);
  if (!me?.isMasterAdmin) return null;

  const decide = async (r: AccountRequest, approve: boolean) => {
    const note = (notes[r.id] || '').trim();
    setBusyId(r.id);
    try {
      const { SupabaseService } = await import('../services/supabaseService');
      const { error } = await SupabaseService.getClient().rpc('jvm_decide_account_request', { p_id: r.id, p_approve: approve, p_note: note || null });
      if (error) throw new Error(error.message);
      setMsg({ ok: true, text: approve
        ? `Conta de ${r.name} aprovada. Ela já pode entrar com ${r.email} e a senha que escolheu; no primeiro acesso cadastra a empresa.`
        : `Pedido de ${r.name} recusado.` });
      await load();
    } catch (err) {
      setMsg({ ok: false, text: `Não foi possível concluir: ${err instanceof Error ? err.message : String(err)}` });
    } finally {
      setBusyId(null);
    }
  };

  const tab = (v: typeof status, label: string) => (
    <button type="button" onClick={() => setStatus(v)}
      className={`px-3 py-1.5 rounded-lg text-xs font-bold border ${status === v ? 'bg-[#0A2540] text-white border-[#0A2540]' : 'bg-white text-slate-700 border-slate-300 hover:bg-slate-50'}`}>{label}</button>
  );

  return (
    <div className="pt-3 border-t border-slate-200 space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <UserPlus className="w-4 h-4 text-[#0A2540]" />
          <h4 className="text-sm font-bold text-slate-900">Pedidos de conta ("Criar conta")</h4>
          <span className="text-[10px] font-bold px-1.5 py-0.5 rounded bg-amber-100 text-amber-800">MASTER</span>
        </div>
        <div className="flex flex-wrap gap-1.5">
          {tab('pendente', 'Pendentes')}{tab('aprovado', 'Aprovados')}{tab('recusado', 'Recusados')}
          <button type="button" onClick={() => void load()} className="px-2 py-1.5 rounded-lg border border-slate-300 text-slate-600 hover:bg-slate-50" aria-label="Atualizar pedidos">
            <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
          </button>
        </div>
      </div>
      <p className="text-[11px] text-slate-500">Novas empresas pedem acesso pela tela de login (plataforma ou APP). Aprovar cria o usuário administrador da empresa; ele cadastra os dados da empresa no primeiro acesso.</p>
      {msg && <p className={`text-xs rounded-lg px-3 py-2 border ${msg.ok ? 'bg-emerald-50 border-emerald-200 text-emerald-800' : 'bg-amber-50 border-amber-200 text-amber-900'}`}>{msg.text}</p>}
      {!loading && list.length === 0 && !msg && <p className="text-xs text-slate-400 text-center py-3">Nenhum pedido {status === 'pendente' ? 'aguardando aprovação' : status}.</p>}
      <div className="space-y-2">
        {list.map(r => (
          <div key={r.id} className="border border-slate-200 rounded-xl p-3 bg-white flex flex-col sm:flex-row sm:items-center gap-3">
            <div className="flex-1 min-w-0 text-xs text-slate-700 space-y-0.5">
              <p className="text-sm font-bold text-slate-900">{r.companyName || 'Empresa não informada'}{r.cnpj ? <span className="font-mono font-normal text-slate-500"> · {fmtDoc(r.cnpj)}</span> : null}</p>
              <p><b>{r.name}</b> · {r.email}{r.username ? ` · usuário ${r.username}` : ''}{r.phone ? ` · ${r.phone}` : ''}</p>
              <p className="text-slate-500">Pedido em {new Date(r.createdAt).toLocaleString('pt-BR')}{r.module ? ` · pelo ${MODULE_LABEL[r.module] || r.module}` : ''}
                {r.decidedAt ? ` · decidido em ${new Date(r.decidedAt).toLocaleString('pt-BR')}` : ''}{r.decisionNote ? ` · ${r.decisionNote}` : ''}</p>
            </div>
            {r.status === 'pendente' && (
              <div className="flex flex-col gap-2 shrink-0 sm:w-56">
                <input value={notes[r.id] || ''} onChange={e => setNotes(n => ({ ...n, [r.id]: e.target.value }))}
                  placeholder="Observação / motivo (opcional)" aria-label="Observação do pedido"
                  className="w-full px-2.5 py-1.5 border border-slate-300 rounded-lg text-xs" />
                <div className="flex gap-2">
                <button type="button" disabled={busyId === r.id} onClick={() => void decide(r, true)}
                  className="inline-flex items-center gap-1.5 px-3 py-2 rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-bold disabled:opacity-50">
                  <Check className="w-3.5 h-3.5" /> Aprovar
                </button>
                <button type="button" disabled={busyId === r.id} onClick={() => void decide(r, false)}
                  className="inline-flex items-center gap-1.5 px-3 py-2 rounded-lg border border-red-300 text-red-700 hover:bg-red-50 text-xs font-bold disabled:opacity-50">
                  <X className="w-3.5 h-3.5" /> Recusar
                </button>
                </div>
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
};
