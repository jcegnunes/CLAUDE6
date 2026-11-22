/**
 * Telas do módulo Gestão de Acessos (só o administrador master). Tudo passa pelas funções do banco
 * (supabase/modules/acessos.sql e cadastro.sql), que conferem que quem chama é master. Precisa de internet.
 */
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Building2, Check, KeyRound, Lock, RefreshCw, Save, Search, Smartphone, Trash2, Unlock, Users } from 'lucide-react';
import { AccountRequestsPanel } from '../../../components/AccountRequestsPanel';
import { AuthService } from '../../../services/authService';

// ---------------------------------------------------------------- utilidades
const MODULES: Array<{ id: string; label: string }> = [
  { id: 'ensaios', label: 'Ensaios de EPI' },
  { id: 'treinamentos', label: 'Treinamentos' },
  { id: 'spda', label: 'SPDA' }
];
const ROLES: Array<{ id: string; label: string }> = [
  { id: 'admin', label: 'Administrador' },
  { id: 'responsavel_tecnico', label: 'Responsável técnico' },
  { id: 'tecnico', label: 'Técnico' },
  { id: 'administrativo', label: 'Administrativo' },
  { id: 'cliente', label: 'Cliente (só consulta)' }
];

async function rpc<T>(fn: string, args?: Record<string, unknown>): Promise<T> {
  const { SupabaseService } = await import('../../../services/supabaseService');
  const { data, error } = await SupabaseService.getClient().rpc(fn, args || {});
  if (error) {
    if (/jvm_master_|does not exist|schema cache/i.test(error.message)) throw new Error('O módulo de acessos ainda não está no banco: execute supabase/modules/acessos.sql no Supabase.');
    if (/fetch|network|Failed to/i.test(error.message)) throw new Error('Sem conexão com o servidor (a Gestão de Acessos precisa de internet).');
    throw new Error(error.message);
  }
  return (typeof data === 'string' ? JSON.parse(data) : data) as T;
}

const todayIso = () => new Date().toISOString().slice(0, 10);
const fmtDate = (iso?: string | null) => (iso ? iso.slice(0, 10).split('-').reverse().join('/') : '');
const daysUntil = (iso: string) => Math.round((Date.parse(`${iso}T00:00:00Z`) - Date.parse(`${todayIso()}T00:00:00Z`)) / 86_400_000);
function addMonths(iso: string, n: number): string {
  const [y, m, d] = iso.split('-').map(Number);
  const t = new Date(Date.UTC(y, m - 1 + n, 1));
  const last = new Date(Date.UTC(t.getUTCFullYear(), t.getUTCMonth() + 1, 0)).getUTCDate();
  t.setUTCDate(Math.min(d, last));
  return t.toISOString().slice(0, 10);
}
const fmtDoc = (d?: string | null) => {
  const s = (d || '').replace(/\D/g, '');
  if (s.length === 14) return s.replace(/^(\d{2})(\d{3})(\d{3})(\d{4})(\d{2})$/, '$1.$2.$3/$4-$5');
  if (s.length === 11) return s.replace(/^(\d{3})(\d{3})(\d{3})(\d{2})$/, '$1.$2.$3-$4');
  return d || '';
};

const card = 'bg-white rounded-2xl border border-slate-200 shadow-sm';
const input = 'w-full min-h-[40px] px-3 py-2 border border-[#94A3B8] rounded-xl bg-white text-sm text-black focus:border-[#0A2540] focus:ring-1 focus:ring-[#0A2540] focus:outline-none';
const btn = 'inline-flex items-center justify-center gap-1.5 min-h-[40px] px-3 py-2 rounded-xl text-sm font-bold disabled:opacity-50';

const Header: React.FC<{ icon: React.ElementType; title: string; text: string; onReload?: () => void; loading?: boolean }> = ({ icon: Icon, title, text, onReload, loading }) => (
  <div className="flex flex-wrap items-start justify-between gap-3">
    <div className="flex items-start gap-3">
      <div className="w-10 h-10 rounded-xl bg-[#0A2540] text-white flex items-center justify-center shrink-0"><Icon className="w-5 h-5" /></div>
      <div>
        <h2 className="text-lg font-black text-slate-900 flex items-center gap-2">{title}<span className="text-[10px] font-bold px-1.5 py-0.5 rounded bg-amber-100 text-amber-800">MASTER</span></h2>
        <p className="text-xs text-slate-500">{text}</p>
      </div>
    </div>
    {onReload && <button type="button" className={`${btn} border border-slate-300 text-slate-700 bg-white hover:bg-slate-50`} onClick={onReload}><RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} /> Atualizar</button>}
  </div>
);

const Msg: React.FC<{ msg: { ok: boolean; text: string } | null }> = ({ msg }) => msg
  ? <p className={`text-sm rounded-xl px-3 py-2 border ${msg.ok ? 'bg-emerald-50 border-emerald-200 text-emerald-800' : 'bg-amber-50 border-amber-300 text-amber-900'}`}>{msg.text}</p>
  : null;

/** Excluir com confirmação na própria tela. */
const DeleteBox: React.FC<{ what: string; detail: string; disabledReason?: string; onConfirm: () => Promise<void> }> = ({ what, detail, disabledReason, onConfirm }) => {
  const [asking, setAsking] = useState(false);
  const [busy, setBusy] = useState(false);
  if (disabledReason) return <p className="text-[11px] text-slate-400">{disabledReason}</p>;
  if (!asking) {
    return (
      <button type="button" onClick={() => setAsking(true)} className={`${btn} border border-red-300 text-red-700 bg-white hover:bg-red-50`}>
        <Trash2 className="w-4 h-4" /> Excluir
      </button>
    );
  }
  return (
    <div className="w-full rounded-xl border border-red-300 bg-red-50 p-3 space-y-2" role="alert">
      <p className="text-sm text-red-900"><b>Excluir {what}?</b> {detail}</p>
      <div className="flex flex-wrap gap-2">
        <button type="button" disabled={busy} className={`${btn} bg-red-600 hover:bg-red-700 text-white`}
          onClick={async () => { setBusy(true); try { await onConfirm(); } finally { setBusy(false); setAsking(false); } }}>
          {busy ? <RefreshCw className="w-4 h-4 animate-spin" /> : <Trash2 className="w-4 h-4" />} Confirmar exclusão
        </button>
        <button type="button" disabled={busy} className={`${btn} border border-slate-300 bg-white text-slate-700 hover:bg-slate-50`} onClick={() => setAsking(false)}>Cancelar</button>
      </div>
    </div>
  );
};

const NotMaster: React.FC = () => (
  <div className={`${card} p-6 text-center text-sm text-slate-600`}>A Gestão de Acessos é só para o administrador master.</div>
);

// -------------------------------------------------------------- pedidos de conta
export const AccountRequestsScreen: React.FC = () => {
  if (!AuthService.getCurrentUser()?.isMasterAdmin) return <NotMaster />;
  return (
    <div className="space-y-4">
      <Header icon={KeyRound} title="Gestão de Acessos — Pedidos de conta" text={'Novas empresas pedem acesso em "Criar conta" na tela de login (plataforma ou APP). Aprovar cria o administrador da empresa; ele cadastra a empresa no primeiro acesso.'} />
      <div className={`${card} p-4`}><AccountRequestsPanel /></div>
    </div>
  );
};

// ------------------------------------------------------------------ empresas
interface CompanyRow {
  id: string; name: string; legalName?: string; cnpj?: string; city?: string; state?: string; email?: string; phone?: string;
  createdAt?: string; accessExpiresAt?: string | null; enabledModules: Record<string, boolean>; users: number; usersWithLogin: number;
}

function ExpiryBadge({ iso }: { iso?: string | null }) {
  if (!iso) return <span className="text-[11px] font-bold px-2 py-0.5 rounded-full bg-slate-100 text-slate-600">Sem vencimento</span>;
  const d = daysUntil(iso);
  if (d < 0) return <span className="text-[11px] font-bold px-2 py-0.5 rounded-full bg-red-100 text-red-700">Vencido em {fmtDate(iso)}</span>;
  if (d <= 30) return <span className="text-[11px] font-bold px-2 py-0.5 rounded-full bg-amber-100 text-amber-800">Vence em {d} dia(s) — {fmtDate(iso)}</span>;
  return <span className="text-[11px] font-bold px-2 py-0.5 rounded-full bg-emerald-100 text-emerald-800">Ativo até {fmtDate(iso)}</span>;
}

const CompanyCard: React.FC<{ c: CompanyRow; isMine: boolean; onSaved: (text: string) => void; onError: (text: string) => void }> = ({ c, isMine, onSaved, onError }) => {
  const [mods, setMods] = useState<Record<string, boolean>>(() => Object.fromEntries(MODULES.map(m => [m.id, c.enabledModules?.[m.id] !== false])));
  const [exp, setExp] = useState(c.accessExpiresAt || '');
  const [busy, setBusy] = useState(false);
  const dirty = MODULES.some(m => mods[m.id] !== (c.enabledModules?.[m.id] !== false)) || exp !== (c.accessExpiresAt || '');
  const base = exp && exp > todayIso() ? exp : todayIso();
  const save = async () => {
    setBusy(true);
    try {
      await rpc('jvm_master_set_company', { p_company_id: c.id, p_enabled_modules: mods, p_expires_at: exp || null });
      onSaved(`${c.name}: alterações salvas. Valem no próximo login dos usuários da empresa (o banco já aplica na hora).`);
    } catch (e) {
      onError(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };
  return (
    <div className={`${card} p-4 space-y-3`}>
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="text-base font-bold text-slate-900">{c.name}</p>
          <p className="text-xs text-slate-500">{[c.legalName && c.legalName !== c.name ? c.legalName : '', fmtDoc(c.cnpj), [c.city, c.state].filter(Boolean).join('/'), c.email, c.phone].filter(Boolean).join(' · ')}</p>
          <p className="text-[11px] text-slate-500 mt-0.5">{c.users} usuário(s), {c.usersWithLogin} com login{c.createdAt ? ` · desde ${fmtDate(c.createdAt)}` : ''}</p>
        </div>
        <ExpiryBadge iso={c.accessExpiresAt} />
      </div>

      <div>
        <p className="text-xs font-bold text-slate-700 mb-1.5">Módulos liberados para a empresa</p>
        <div className="flex flex-wrap gap-2">
          {MODULES.map(m => (
            <label key={m.id} className={`inline-flex items-center gap-2 px-3 py-2 rounded-xl border text-sm cursor-pointer ${mods[m.id] ? 'border-[#0A2540] bg-[#EFF6FF] text-[#0A2540] font-bold' : 'border-slate-300 text-slate-500'}`}>
              <input type="checkbox" checked={!!mods[m.id]} onChange={e => setMods(s => ({ ...s, [m.id]: e.target.checked }))} /> {m.label}
            </label>
          ))}
        </div>
      </div>

      <div>
        <p className="text-xs font-bold text-slate-700 mb-1.5">Validade do acesso</p>
        <div className="flex flex-wrap items-center gap-2">
          <input type="date" className={`${input} !w-auto`} value={exp} onChange={e => setExp(e.target.value)} aria-label={`Validade do acesso de ${c.name}`} />
          <button type="button" className={`${btn} border border-slate-300 bg-white text-slate-700 hover:bg-slate-50`} onClick={() => setExp(addMonths(base, 1))}>+1 mês</button>
          <button type="button" className={`${btn} border border-slate-300 bg-white text-slate-700 hover:bg-slate-50`} onClick={() => setExp(addMonths(base, 12))}>+1 ano</button>
          <button type="button" className={`${btn} border border-slate-300 bg-white text-slate-700 hover:bg-slate-50`} onClick={() => setExp('')}>Sem vencimento</button>
        </div>
        <p className="text-[11px] text-slate-500 mt-1">Depois da data, os usuários da empresa não entram (nem na plataforma nem nos apps) até a renovação. Os dados são mantidos.</p>
      </div>

      <div className="flex flex-wrap items-center justify-between gap-2">
        <DeleteBox
          what={`a empresa ${c.name}`}
          detail={`Os ${c.users} usuário(s) dela também são excluídos e perdem o acesso na hora. Os dados (clientes, OS, ensaios, inspeções…) ficam guardados no banco, mas deixam de aparecer.`}
          disabledReason={isMine ? 'Esta é a sua empresa (não pode ser excluída).' : undefined}
          onConfirm={async () => {
            try {
              await rpc('jvm_master_delete_company', { p_company_id: c.id });
              onSaved(`Empresa ${c.name} excluída (e os usuários dela).`);
            } catch (e) { onError(e instanceof Error ? e.message : String(e)); }
          }}
        />
        <button type="button" disabled={!dirty || busy} onClick={() => void save()} className={`${btn} bg-[#F97316] hover:bg-[#EA580C] text-white`}>
          {busy ? <RefreshCw className="w-4 h-4 animate-spin" /> : <Save className="w-4 h-4" />} Salvar
        </button>
      </div>
    </div>
  );
};

export const CompaniesScreen: React.FC = () => {
  const isMaster = !!AuthService.getCurrentUser()?.isMasterAdmin;
  const [list, setList] = useState<CompanyRow[]>([]);
  const [loading, setLoading] = useState(false);
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null);
  const [q, setQ] = useState('');
  const [version, setVersion] = useState(0);
  const load = useCallback(async () => {
    setLoading(true);
    try {
      setList(await rpc<CompanyRow[]>('jvm_master_companies') || []);
    } catch (e) {
      setMsg({ ok: false, text: e instanceof Error ? e.message : String(e) });
    } finally {
      setLoading(false);
    }
  }, []);
  useEffect(() => { if (isMaster) void load(); }, [isMaster, load]);
  if (!isMaster) return <NotMaster />;
  const term = q.trim().toLowerCase();
  const shown = list.filter(c => !term || [c.name, c.legalName, c.cnpj, c.city, c.email].some(x => (x || '').toLowerCase().includes(term)));
  const expired = list.filter(c => c.accessExpiresAt && daysUntil(c.accessExpiresAt) < 0).length;
  const soon = list.filter(c => c.accessExpiresAt && daysUntil(c.accessExpiresAt) >= 0 && daysUntil(c.accessExpiresAt) <= 30).length;
  return (
    <div className="space-y-4">
      <Header icon={Building2} title="Gestão de Acessos — Empresas e validade" text="Módulos liberados e validade do acesso de cada empresa da plataforma." onReload={() => void load()} loading={loading} />
      <div className="flex flex-wrap gap-2 text-xs">
        <span className="px-2.5 py-1 rounded-full bg-slate-100 text-slate-700 font-bold">{list.length} empresa(s)</span>
        {soon > 0 && <span className="px-2.5 py-1 rounded-full bg-amber-100 text-amber-800 font-bold">{soon} vencendo em 30 dias</span>}
        {expired > 0 && <span className="px-2.5 py-1 rounded-full bg-red-100 text-red-700 font-bold">{expired} vencida(s)</span>}
      </div>
      <div className="relative max-w-md">
        <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
        <input className={`${input} pl-9`} placeholder="Buscar empresa, CNPJ, cidade ou e-mail" value={q} onChange={e => setQ(e.target.value)} />
      </div>
      <Msg msg={msg} />
      {!loading && shown.length === 0 && !msg && <p className="text-sm text-slate-400 text-center py-6">Nenhuma empresa encontrada.</p>}
      <div className="grid gap-3 lg:grid-cols-2">
        {shown.map(c => (
          <CompanyCard key={`${c.id}_${version}`} c={c} isMine={c.id === (AuthService.getCurrentUser()?.companyId || '')}
            onSaved={text => { setMsg({ ok: true, text }); void load().then(() => setVersion(v => v + 1)); }}
            onError={text => setMsg({ ok: false, text })} />
        ))}
      </div>
    </div>
  );
};

// ------------------------------------------------------------------ usuários
interface UserRow {
  id: string; name: string; email: string; username?: string | null; role: string; active: boolean; isMasterAdmin: boolean;
  hasLogin: boolean; allowedModules: string[] | null; companyId?: string | null; companyName?: string | null; createdAt?: string;
}

/** Celular registrado na licença do APP (supabase/modules/aparelhos.sql) */
interface DeviceRow { userId: string; app: string; deviceName?: string | null; registeredAt: string; lastSeenAt?: string | null }

/** Lista os aparelhos; null = o script aparelhos.sql ainda não foi executado. */
async function loadDevices(): Promise<DeviceRow[] | null> {
  const { SupabaseService } = await import('../../../services/supabaseService');
  const { data, error } = await SupabaseService.getClient().rpc('jvm_master_devices', { p_company_id: null });
  if (error) {
    if (/jvm_master_devices|does not exist|schema cache|Could not find/i.test(error.message)) return null;
    throw new Error(error.message);
  }
  return ((typeof data === 'string' ? JSON.parse(data) : data) || []) as DeviceRow[];
}

/** Celular do APP JVM SPDA: mostra o aparelho registrado e libera (com confirmação na tela). */
const AppDeviceBox: React.FC<{ u: UserRow; device?: DeviceRow; devicesMissing: boolean; onSaved: (text: string) => void; onError: (text: string) => void }> = ({ u, device, devicesMissing, onSaved, onError }) => {
  const [asking, setAsking] = useState(false);
  const [busy, setBusy] = useState(false);
  let body: React.ReactNode;
  if (devicesMissing) body = <p className="text-xs text-amber-800">Para controlar o celular de cada usuário, execute supabase/modules/aparelhos.sql no Supabase.</p>;
  else if (u.isMasterAdmin) body = <p className="text-xs text-slate-500">Administrador master: sem limite de aparelho.</p>;
  else if (!device) body = <p className="text-xs text-slate-500">Nenhum celular registrado. O próximo login no APP registra o aparelho.</p>;
  else body = (
    <div className="space-y-2">
      <p className="text-xs text-slate-700"><b>{device.deviceName || 'Celular'}</b> · registrado em {fmtDate(device.registeredAt)}{device.lastSeenAt ? ` · último uso ${fmtDate(device.lastSeenAt)}` : ''}</p>
      {!asking ? (
        <button type="button" onClick={() => setAsking(true)} className={`${btn} border border-[#0A2540]/40 text-[#0A2540] bg-white hover:bg-[#EFF6FF]`}>
          <Smartphone className="w-4 h-4" /> Liberar aparelho
        </button>
      ) : (
        <div className="rounded-xl border border-amber-300 bg-amber-50 p-3 space-y-2" role="alert">
          <p className="text-sm text-amber-900"><b>Liberar o celular de {u.name}?</b> O próximo login no APP registra o celular novo; este ({device.deviceName || 'celular atual'}) é desconectado na próxima conferência.</p>
          <div className="flex flex-wrap gap-2">
            <button type="button" disabled={busy} className={`${btn} bg-[#F97316] hover:bg-[#EA580C] text-white`}
              onClick={async () => {
                setBusy(true);
                try {
                  await rpc('jvm_master_release_device', { p_user_id: u.id, p_app: device.app });
                  onSaved(`${u.name}: aparelho liberado. O próximo login no APP registra o celular novo.`);
                } catch (e) { onError(e instanceof Error ? e.message : String(e)); } finally { setBusy(false); setAsking(false); }
              }}>
              {busy ? <RefreshCw className="w-4 h-4 animate-spin" /> : <Check className="w-4 h-4" />} Confirmar liberação
            </button>
            <button type="button" disabled={busy} className={`${btn} border border-slate-300 bg-white text-slate-700 hover:bg-slate-50`} onClick={() => setAsking(false)}>Cancelar</button>
          </div>
        </div>
      )}
    </div>
  );
  return (
    <div className="rounded-xl border border-slate-200 bg-slate-50/60 p-3">
      <p className="text-xs font-bold text-slate-700 mb-1 inline-flex items-center gap-1.5"><Smartphone className="w-3.5 h-3.5" /> Celular do APP JVM SPDA (1 aparelho por usuário)</p>
      {body}
    </div>
  );
};

const UserCard: React.FC<{ u: UserRow; isMe: boolean; device?: DeviceRow; devicesMissing: boolean; onSaved: (text: string) => void; onError: (text: string) => void }> = ({ u, isMe, device, devicesMissing, onSaved, onError }) => {
  const [active, setActive] = useState(u.active);
  const [role, setRole] = useState(u.role);
  const [all, setAll] = useState(u.allowedModules === null);
  const [mods, setMods] = useState<string[]>(u.allowedModules || MODULES.map(m => m.id));
  const [busy, setBusy] = useState(false);
  const allowed = all || role === 'admin' ? null : mods;
  const dirty = active !== u.active || role !== u.role || JSON.stringify(allowed) !== JSON.stringify(u.role === 'admin' ? null : u.allowedModules);
  const save = async () => {
    setBusy(true);
    try {
      await rpc('jvm_master_set_user', { p_user_id: u.id, p_active: active, p_role: role, p_allowed_modules: allowed });
      onSaved(`${u.name}: ${active ? 'acesso liberado' : 'acesso BLOQUEADO (sessões abertas encerradas)'}. Mudanças de perfil e módulos valem no próximo login.`);
    } catch (e) {
      onError(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };
  return (
    <div className={`${card} p-4 space-y-3 ${!u.active ? 'border-red-200 bg-red-50/30' : ''}`}>
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="text-sm font-bold text-slate-900">{u.name}{u.isMasterAdmin && <span className="ml-2 text-[10px] font-bold px-1.5 py-0.5 rounded bg-amber-100 text-amber-800">MASTER</span>}{isMe && <span className="ml-1 text-[10px] text-slate-500">(você)</span>}</p>
          <p className="text-xs text-slate-500 break-all">{u.email}{u.username ? ` · ${u.username}` : ''}</p>
          <p className="text-[11px] text-slate-500">{u.companyName || 'Sem empresa (cadastra no 1º acesso)'} · {u.hasLogin ? 'com login' : 'sem login (só assinatura)'}</p>
        </div>
        <span className={`text-[11px] font-bold px-2 py-0.5 rounded-full ${u.active ? 'bg-emerald-100 text-emerald-800' : 'bg-red-100 text-red-700'}`}>{u.active ? 'Ativo' : 'Bloqueado'}</span>
      </div>
      <div className="grid gap-3 sm:grid-cols-2">
        <label className="block text-xs font-bold text-slate-700">Perfil
          <select className={`${input} mt-1`} value={role} onChange={e => setRole(e.target.value)} disabled={isMe}>
            {ROLES.map(r => <option key={r.id} value={r.id}>{r.label}</option>)}
          </select>
        </label>
        <div>
          <p className="text-xs font-bold text-slate-700">Acesso</p>
          <button type="button" disabled={isMe} onClick={() => setActive(a => !a)}
            className={`${btn} mt-1 w-full border ${active ? 'border-red-300 text-red-700 bg-white hover:bg-red-50' : 'border-emerald-300 text-emerald-700 bg-white hover:bg-emerald-50'}`}>
            {active ? <><Lock className="w-4 h-4" /> Bloquear acesso</> : <><Unlock className="w-4 h-4" /> Desbloquear acesso</>}
          </button>
        </div>
      </div>
      <div>
        <p className="text-xs font-bold text-slate-700 mb-1.5">Módulos liberados para o usuário</p>
        {role === 'admin'
          ? <p className="text-xs text-slate-500">Administrador usa todos os módulos ligados para a empresa.</p>
          : (
            <div className="flex flex-wrap gap-2">
              <label className={`inline-flex items-center gap-2 px-3 py-2 rounded-xl border text-sm cursor-pointer ${all ? 'border-[#0A2540] bg-[#EFF6FF] text-[#0A2540] font-bold' : 'border-slate-300 text-slate-500'}`}>
                <input type="checkbox" checked={all} onChange={e => setAll(e.target.checked)} /> Todos
              </label>
              {!all && MODULES.map(m => (
                <label key={m.id} className={`inline-flex items-center gap-2 px-3 py-2 rounded-xl border text-sm cursor-pointer ${mods.includes(m.id) ? 'border-[#0A2540] bg-[#EFF6FF] text-[#0A2540] font-bold' : 'border-slate-300 text-slate-500'}`}>
                  <input type="checkbox" checked={mods.includes(m.id)} onChange={e => setMods(s => (e.target.checked ? [...s, m.id] : s.filter(x => x !== m.id)))} /> {m.label}
                </label>
              ))}
            </div>
          )}
      </div>
      <AppDeviceBox u={u} device={device} devicesMissing={devicesMissing} onSaved={onSaved} onError={onError} />
      <div className="flex flex-wrap items-center justify-between gap-2">
        <DeleteBox
          what={`o usuário ${u.name}`}
          detail="O acesso é bloqueado na hora e ele some da lista. O e-mail continua reservado (para usar o mesmo e-mail de novo, prefira Bloquear)."
          disabledReason={isMe ? 'É você (não pode se excluir).' : u.isMasterAdmin ? 'Administrador master (não pode ser excluído aqui).' : undefined}
          onConfirm={async () => {
            try {
              await rpc('jvm_master_delete_user', { p_user_id: u.id });
              onSaved(`Usuário ${u.name} excluído.`);
            } catch (e) { onError(e instanceof Error ? e.message : String(e)); }
          }}
        />
        <button type="button" disabled={!dirty || busy} onClick={() => void save()} className={`${btn} bg-[#F97316] hover:bg-[#EA580C] text-white`}>
          {busy ? <RefreshCw className="w-4 h-4 animate-spin" /> : <Check className="w-4 h-4" />} Salvar
        </button>
      </div>
    </div>
  );
};

export const UsersScreen: React.FC = () => {
  const me = AuthService.getCurrentUser();
  const isMaster = !!me?.isMasterAdmin;
  const [list, setList] = useState<UserRow[]>([]);
  const [loading, setLoading] = useState(false);
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null);
  const [q, setQ] = useState('');
  const [company, setCompany] = useState('');
  const [onlyBlocked, setOnlyBlocked] = useState(false);
  const [version, setVersion] = useState(0);
  const [devices, setDevices] = useState<DeviceRow[]>([]);
  const [devicesMissing, setDevicesMissing] = useState(false);
  const load = useCallback(async () => {
    setLoading(true);
    try {
      setList(await rpc<UserRow[]>('jvm_master_users', { p_company_id: null }) || []);
      const devs = await loadDevices();
      setDevicesMissing(devs === null);
      setDevices(devs || []);
    } catch (e) {
      setMsg({ ok: false, text: e instanceof Error ? e.message : String(e) });
    } finally {
      setLoading(false);
    }
  }, []);
  useEffect(() => { if (isMaster) void load(); }, [isMaster, load]);
  const companies = useMemo(() => Array.from(new Map(list.map(u => [u.companyId || '', u.companyName || 'Sem empresa'])).entries()).sort((a, b) => String(a[1]).localeCompare(String(b[1]))), [list]);
  if (!isMaster) return <NotMaster />;
  const term = q.trim().toLowerCase();
  const shown = list.filter(u => (!company || (u.companyId || '') === company) && (!onlyBlocked || !u.active)
    && (!term || [u.name, u.email, u.username, u.companyName].some(x => (x || '').toLowerCase().includes(term))));
  return (
    <div className="space-y-4">
      <Header icon={Users} title="Gestão de Acessos — Usuários e bloqueio" text="Usuários de todas as empresas: bloquear ou liberar o acesso, perfil, módulos e o celular do APP de cada um." onReload={() => void load()} loading={loading} />
      <div className="grid gap-2 sm:grid-cols-3">
        <div className="relative sm:col-span-1">
          <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
          <input className={`${input} pl-9`} placeholder="Buscar nome, e-mail ou usuário" value={q} onChange={e => setQ(e.target.value)} />
        </div>
        <select className={input} value={company} onChange={e => setCompany(e.target.value)} aria-label="Empresa">
          <option value="">Todas as empresas ({list.length} usuários)</option>
          {companies.map(([id, name]) => <option key={id} value={id}>{name}</option>)}
        </select>
        <label className="inline-flex items-center gap-2 text-sm text-slate-700"><input type="checkbox" checked={onlyBlocked} onChange={e => setOnlyBlocked(e.target.checked)} /> Só bloqueados</label>
      </div>
      <Msg msg={msg} />
      {!loading && shown.length === 0 && !msg && <p className="text-sm text-slate-400 text-center py-6">Nenhum usuário encontrado.</p>}
      <div className="grid gap-3 lg:grid-cols-2">
        {shown.map(u => (
          <UserCard key={`${u.id}_${version}`} u={u} isMe={u.email?.toLowerCase() === (me?.email || '').toLowerCase()}
            device={devices.find(d => d.userId === u.id && d.app === 'spda')} devicesMissing={devicesMissing}
            onSaved={text => { setMsg({ ok: true, text }); void load().then(() => setVersion(v => v + 1)); }}
            onError={text => setMsg({ ok: false, text })} />
        ))}
      </div>
    </div>
  );
};
