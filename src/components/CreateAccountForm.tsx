/**
 * "Criar conta" (tela de login da plataforma e do APP JVM SPDA): a pessoa PEDE uma conta, informando
 * os módulos de interesse (empresa e CNPJ são opcionais: a conta é liberada para o e-mail dela). O pedido fica aguardando a aprovação do administrador master (supabase/modules/cadastro.sql);
 * só depois de aprovado ela entra com o e-mail e a senha escolhidos e cadastra a empresa no primeiro acesso.
 */
import React, { useState } from 'react';
import { CheckCircle2, Eye, EyeOff } from 'lucide-react';
import { APP_ONLY_MODULE } from '../appTarget';

interface Props {
  inputClass: string;
  onBack: () => void;
}

const onlyDigits = (s: string) => s.replace(/\D/g, '');

const MODULE_OPTIONS: Array<{ id: string; label: string; hint: string }> = [
  { id: 'spda', label: 'SPDA', hint: 'Inspeção e ensaios de SPDA (NBR 5419), laudos e relatórios' },
  { id: 'ensaios', label: 'Ensaios de EPI', hint: 'Ensaios dielétricos de EPI/EPC, laudos e certificados' },
  { id: 'treinamentos', label: 'Treinamentos', hint: 'Turmas e certificados de treinamento (NR-10, NR-35…)' }
];
function formatDoc(v: string): string {
  const d = onlyDigits(v).slice(0, 14);
  if (d.length <= 11) return d.replace(/(\d{3})(\d)/, '$1.$2').replace(/(\d{3})(\d)/, '$1.$2').replace(/(\d{3})(\d{1,2})$/, '$1-$2');
  return d.replace(/^(\d{2})(\d)/, '$1.$2').replace(/^(\d{2})\.(\d{3})(\d)/, '$1.$2.$3').replace(/\.(\d{3})(\d)/, '.$1/$2').replace(/(\d{4})(\d)/, '$1-$2');
}

/** Mensagem do servidor sem o código técnico. */
function friendly(msg: string): string {
  // a função existe no servidor, mas com outros campos: esta tela (ou o app) é de uma versão diferente
  if (/Could not find the function public\.jvm_request_account\(/i.test(msg)) {
    return 'Esta versão do aplicativo/página está desatualizada para o cadastro. Atualize a página (ou instale o APK mais novo) e tente de novo.';
  }
  if (/jvm_request_account|does not exist|schema cache/i.test(msg)) return 'O cadastro de contas ainda não está ativo no servidor. Fale com o administrador.';
  if (/Failed to fetch|NetworkError|network/i.test(msg)) return 'Sem conexão com o servidor. Verifique a internet e tente de novo.';
  return msg;
}

export const CreateAccountForm: React.FC<Props> = ({ inputClass, onBack }) => {
  const [f, setF] = useState({ name: '', email: '', phone: '', company: '', doc: '', username: '', password: '', confirm: '' });
  // no APP JVM SPDA o SPDA já vem marcado
  const [modules, setModules] = useState<string[]>(() => (APP_ONLY_MODULE ? [APP_ONLY_MODULE] : []));
  const toggleModule = (id: string) => setModules(m => (m.includes(id) ? m.filter(x => x !== id) : [...m, id]));
  const [show, setShow] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement>) => setF(p => ({ ...p, [k]: k === 'doc' ? formatDoc(e.target.value) : e.target.value }));

  const problem = (): string | null => {
    if (f.name.trim().length < 3) return 'Informe o seu nome completo.';
    if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(f.email.trim())) return 'Informe um e-mail válido (ele será usado no login).';
    if (modules.length === 0) return 'Escolha pelo menos um módulo de interesse.';
    const d = onlyDigits(f.doc);
    if (d && d.length !== 11 && d.length !== 14) return 'CNPJ (ou CPF) incompleto.';
    if (f.username.trim() && !/^[a-zA-Z0-9._-]{3,40}$/.test(f.username.trim())) return 'Nome de usuário: de 3 a 40 letras, números, ponto, hífen ou sublinhado.';
    if (f.password.length < 8) return 'A senha deve ter pelo menos 8 caracteres.';
    if (!/[A-Za-z]/.test(f.password) || !/[0-9]/.test(f.password)) return 'A senha deve ter letras e números.';
    if (f.password !== f.confirm) return 'A confirmação não confere com a senha.';
    return null;
  };

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    const p = problem();
    if (p) { setError(p); return; }
    setError(null);
    setBusy(true);
    try {
      const { SupabaseService } = await import('../services/supabaseService');
      const { error: err } = await SupabaseService.getClient().rpc('jvm_request_account', {
        p_name: f.name.trim(), p_email: f.email.trim(), p_password: f.password, p_modules: modules,
        p_company_name: f.company.trim() || null, p_cnpj: onlyDigits(f.doc) || null,
        p_phone: f.phone.trim() || null, p_username: f.username.trim() || null
      });
      if (err) throw new Error(err.message);
      setDone(true);
    } catch (err) {
      setError(friendly(err instanceof Error ? err.message : String(err)));
    } finally {
      setBusy(false);
    }
  };

  const label = 'block text-[13px] font-medium text-[#1D2733] mb-1.5';
  const link = 'text-[13px] text-[#5E6A78] hover:text-[#1D2733] underline underline-offset-4 decoration-[#CBD2DA] hover:decoration-[#1D2733] cursor-pointer';

  if (done) {
    return (
      <div className="space-y-4 text-center">
        <CheckCircle2 className="w-10 h-10 text-emerald-600 mx-auto" />
        <h2 className="text-[16px] font-semibold text-[#1D2733]">Pedido enviado</h2>
        <p className="text-[14px] leading-relaxed text-[#5E6A78]">
          O seu pedido de conta ({MODULE_OPTIONS.filter(m => modules.includes(m.id)).map(m => m.label).join(', ')}) foi recebido e aguarda a aprovação do administrador.
          Depois de aprovado, entre com o e-mail <b className="text-[#1D2733]">{f.email.trim().toLowerCase()}</b> e a senha que você escolheu;
          no primeiro acesso você informa os seus dados (empresa e CNPJ são opcionais).
        </p>
        <button type="button" onClick={onBack} className={link}>Voltar para o login</button>
      </div>
    );
  }

  return (
    <form onSubmit={submit} noValidate className="space-y-3.5">
      <div>
        <h2 className="text-[15px] font-semibold text-[#1D2733]">Criar conta</h2>
        <p className="text-[12px] text-[#5E6A78] mt-0.5">O pedido é analisado pelo administrador. Você recebe acesso depois da aprovação.</p>
      </div>
      <div><label htmlFor="ca-nome" className={label}>Seu nome *</label>
        <input id="ca-nome" className={inputClass} value={f.name} onChange={set('name')} autoComplete="name" /></div>
      <div><label htmlFor="ca-email" className={label}>E-mail (login) *</label>
        <input id="ca-email" type="email" className={inputClass} value={f.email} onChange={set('email')} autoComplete="email" autoCapitalize="none" spellCheck={false} /></div>
      <div><label htmlFor="ca-tel" className={label}>Telefone / WhatsApp</label>
        <input id="ca-tel" type="tel" className={inputClass} value={f.phone} onChange={set('phone')} autoComplete="tel" /></div>
      <fieldset>
        <legend className={label}>Módulos de interesse *</legend>
        <div className="space-y-2">
          {MODULE_OPTIONS.map(m => (
            <label key={m.id} className={`flex items-start gap-2.5 px-3 py-2.5 rounded-md border cursor-pointer ${modules.includes(m.id) ? 'border-[#D9480F] bg-[#FFF4ED]' : 'border-[#CBD2DA] bg-white'}`}>
              <input type="checkbox" className="mt-1" checked={modules.includes(m.id)} onChange={() => toggleModule(m.id)} />
              <span><span className="block text-[14px] font-semibold text-[#1D2733]">{m.label}</span><span className="block text-[12px] text-[#5E6A78]">{m.hint}</span></span>
            </label>
          ))}
        </div>
      </fieldset>
      <div><label htmlFor="ca-empresa" className={label}>Empresa <span className="font-normal text-[#5E6A78]">(opcional)</span></label>
        <input id="ca-empresa" className={inputClass} value={f.company} onChange={set('company')} autoComplete="organization" /></div>
      <div><label htmlFor="ca-cnpj" className={label}>CNPJ ou CPF <span className="font-normal text-[#5E6A78]">(opcional)</span></label>
        <input id="ca-cnpj" inputMode="numeric" className={inputClass} value={f.doc} onChange={set('doc')} placeholder="00.000.000/0000-00" /></div>
      <div><label htmlFor="ca-user" className={label}>Nome de usuário (opcional)</label>
        <input id="ca-user" className={inputClass} value={f.username} onChange={set('username')} autoCapitalize="none" spellCheck={false} placeholder="Ex.: joao.silva" /></div>
      <div>
        <label htmlFor="ca-senha" className={label}>Senha * <span className="font-normal text-[#5E6A78]">(8+ caracteres, letras e números)</span></label>
        <div className="relative">
          <input id="ca-senha" type={show ? 'text' : 'password'} className={`${inputClass} pr-11`} value={f.password} onChange={set('password')} autoComplete="new-password" />
          <button type="button" onClick={() => setShow(v => !v)} className="absolute inset-y-0 right-0 w-11 flex items-center justify-center text-[#5E6A78]" aria-label={show ? 'Ocultar senha' : 'Mostrar senha'}>
            {show ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
          </button>
        </div>
      </div>
      <div><label htmlFor="ca-confirma" className={label}>Confirmar senha *</label>
        <input id="ca-confirma" type={show ? 'text' : 'password'} className={inputClass} value={f.confirm} onChange={set('confirm')} autoComplete="new-password" /></div>

      {error && <p role="alert" className="text-[13px] leading-snug text-[#B42318] bg-[#FEF3F2] border border-[#FECDCA] rounded-md px-3 py-2">{error}</p>}

      <button type="submit" disabled={busy}
        className="w-full h-11 rounded-md bg-[#D9480F] hover:bg-[#C23F0C] text-white text-[15px] font-semibold transition-colors disabled:opacity-50 cursor-pointer">
        {busy ? 'Enviando…' : 'Enviar pedido de conta'}
      </button>
      <div className="text-center"><button type="button" onClick={onBack} className={link}>Já tenho conta — voltar para o login</button></div>
    </form>
  );
};
