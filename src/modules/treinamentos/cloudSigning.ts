/**
 * Certificado digital em nuvem (BirdID, VaultID, VIDaaS, SafeID, NeoID) pela
 * API padrão dos PSCs (ITI). As chamadas ao PSC passam pela função do servidor
 * "psc-assinatura" (supabase/functions), que guarda as credenciais da
 * aplicação. O titular autoriza uma sessão de assinatura (até 7 dias para
 * e-CPF) no site/app do PSC ou com o código OTP; durante a sessão os PDFs são
 * assinados sem nova confirmação. Só o hash do PDF vai ao PSC.
 */
import { SupabaseService } from '../../services/supabaseService';

export const CLOUD_FUNCTION = 'psc-assinatura';
/** Página que recebe o retorno do PSC e devolve o código para esta janela */
export const CLOUD_RETURN_PATH = '/psc-retorno.html';
/** Sessão pedida ao PSC (o PSC pode conceder menos) */
export const CLOUD_SESSION_SECONDS = 7 * 24 * 3600;

export interface CloudProvider { id: string; label: string; otp: boolean }
export interface CloudCertificate { alias: string; certificate: string }
export interface CloudAuthorization { token: string; expiresAt: string; certificates: CloudCertificate[] }

/** Nomes dos PSCs (para exibir mesmo sem consultar o servidor) */
export const CLOUD_PROVIDER_LABELS: Record<string, string> = {
  birdid: 'BirdID (Soluti)',
  vaultid: 'VaultID',
  vidaas: 'VIDaaS (Valid)',
  safeid: 'SafeID (Safeweb)',
  neoid: 'NeoID (Serpro)'
};

export const providerLabel = (id?: string) => (id ? CLOUD_PROVIDER_LABELS[id] || id : '');

async function call<T>(body: Record<string, unknown>): Promise<T> {
  if (typeof navigator !== 'undefined' && navigator.onLine === false) throw new Error('O certificado em nuvem exige internet.');
  const { data, error } = await SupabaseService.getClient().functions.invoke(CLOUD_FUNCTION, { body });
  if (error) {
    const msg = String((error as { message?: string }).message || error);
    if (/not found|404|Failed to send|FunctionsFetchError|FunctionsRelayError/i.test(msg)) {
      throw new Error('O servidor ainda não tem a função "psc-assinatura" do certificado em nuvem (veja MUDANCAS_V7.md, "Certificado em nuvem").');
    }
    throw new Error(msg);
  }
  const d = (typeof data === 'string' ? JSON.parse(data) : data) as T & { error?: string };
  if (d && d.error) throw new Error(d.error);
  return d;
}

/** PSCs configurados no servidor (com credenciais da aplicação). */
export async function listCloudProviders(): Promise<CloudProvider[]> {
  const r = await call<{ providers: CloudProvider[] }>({ action: 'providers' });
  return Array.isArray(r?.providers) ? r.providers : [];
}

const b64url = (bytes: Uint8Array) => btoa(String.fromCharCode(...bytes)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
const randomToken = (n = 32) => b64url(crypto.getRandomValues(new Uint8Array(n)));

export async function pkceChallenge(verifier: string): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier));
  return b64url(new Uint8Array(digest));
}

const toAuthorization = (r: { token: string; expiresIn: number; certificates: CloudCertificate[] }): CloudAuthorization => ({
  token: r.token,
  expiresAt: new Date(Date.now() + Math.max(60, Number(r.expiresIn) || CLOUD_SESSION_SECONDS) * 1000).toISOString(),
  certificates: r.certificates || []
});

/** Autorização com o código OTP do aplicativo do PSC (BirdID/VaultID). */
export async function authorizeWithOtp(provider: string, cpf: string, otp: string): Promise<CloudAuthorization> {
  if (cpf.replace(/\D/g, '').length < 11) throw new Error('Informe o CPF do titular do certificado.');
  if (!/^\d{4,8}$/.test(otp.trim())) throw new Error('Informe o código OTP mostrado no aplicativo do provedor.');
  return toAuthorization(await call({ action: 'token', provider, cpf, otp: otp.trim(), lifetime: CLOUD_SESSION_SECONDS }));
}

/**
 * Autorização no site do PSC (OAuth com PKCE) em uma janela: o titular entra
 * na conta, escolhe o certificado e aprova a sessão de assinatura.
 * Abrir a janela antes de qualquer espera (bloqueador de pop-up).
 */
export async function authorizeInPopup(provider: string, cpf: string, signal?: AbortSignal): Promise<CloudAuthorization> {
  const popup = window.open('', 'jvm-psc', 'width=520,height=720');
  if (!popup) throw new Error('O navegador bloqueou a janela do provedor: permita pop-ups para este site e tente de novo.');
  try {
    popup.document.title = 'Certificado em nuvem';
    popup.document.body.innerHTML = '<p style="font:14px sans-serif;padding:24px">Abrindo o provedor do certificado…</p>';
  } catch { /* janela já em outro endereço */ }
  try {
    const verifier = randomToken(48);
    const state = randomToken(16);
    const redirectUri = `${window.location.origin}${CLOUD_RETURN_PATH}`;
    const { url } = await call<{ url: string }>({
      action: 'authorize_url', provider, cpf, challenge: await pkceChallenge(verifier), state, redirectUri, lifetime: CLOUD_SESSION_SECONDS
    });
    try { localStorage.removeItem(CLOUD_RETURN_KEY); } catch { /* */ }
    popup.location.href = url;
    const code = await waitForCode(popup, state, signal);
    return toAuthorization(await call({ action: 'token', provider, code, verifier, redirectUri, lifetime: CLOUD_SESSION_SECONDS }));
  } catch (err) {
    try { popup.close(); } catch { /* fechada */ }
    throw err;
  }
}

/** Chave em que a página de retorno também grava o resultado (mesma origem) */
export const CLOUD_RETURN_KEY = 'jvm_psc_return';

/**
 * Espera o código do PSC: pela mensagem da janela de retorno ou, quando o site
 * do PSC corta o vínculo entre as janelas, pelo localStorage. Não desiste só
 * porque a janela parece fechada (o corte do vínculo também aparece assim).
 */
function waitForCode(popup: Window, state: string, signal?: AbortSignal): Promise<string> {
  return new Promise((resolve, reject) => {
    const handle = (d: any) => {
      if (!d || d.type !== 'jvm-psc' || d.state !== state) return;
      if (d.error) finish(() => reject(new Error(`Autorização recusada no provedor: ${d.error}`)));
      else if (d.code) finish(() => resolve(String(d.code)));
    };
    const readStored = () => {
      try {
        const raw = localStorage.getItem(CLOUD_RETURN_KEY);
        if (raw) handle(JSON.parse(raw));
      } catch { /* sem armazenamento */ }
    };
    const onMessage = (e: MessageEvent) => { if (e.origin === window.location.origin) handle(e.data); };
    const onStorage = (e: StorageEvent) => { if (e.key === CLOUD_RETURN_KEY) readStored(); };
    const onAbort = () => finish(() => { try { popup.close(); } catch { /* */ } reject(new Error('Autorização cancelada.')); });
    const started = Date.now();
    const timer = setInterval(() => {
      readStored();
      if (Date.now() - started > 10 * 60_000) finish(() => { try { popup.close(); } catch { /* */ } reject(new Error('Tempo esgotado esperando a autorização.')); });
    }, 800);
    let finished = false;
    function finish(fn: () => void) {
      if (finished) return;
      finished = true;
      window.removeEventListener('message', onMessage);
      window.removeEventListener('storage', onStorage);
      signal?.removeEventListener('abort', onAbort);
      clearInterval(timer);
      try { localStorage.removeItem(CLOUD_RETURN_KEY); } catch { /* */ }
      fn();
    }
    window.addEventListener('message', onMessage);
    window.addEventListener('storage', onStorage);
    signal?.addEventListener('abort', onAbort);
  });
}

const toHex = (bytes: Uint8Array) => Array.from(bytes, b => b.toString(16).padStart(2, '0')).join('');

/** Assina no PSC o SHA-256 dos atributos assinados (assinatura RAW, PKCS#1 v1.5). */
export async function signHashInCloud(provider: string, token: string, alias: string, digest: Uint8Array, label?: string): Promise<Uint8Array> {
  const r = await call<{ signature: string }>({ action: 'sign', provider, token, alias, hashHex: toHex(digest), label });
  const bin = atob(String(r.signature || '').replace(/\s+/g, ''));
  return Uint8Array.from(bin, c => c.charCodeAt(0));
}

/** Escolhe o certificado da conta: o do CPF informado, senão o primeiro. */
export function pickCertificate(certs: CloudCertificate[], cpf: string, parse: (pem: string) => { holderDoc: string }): CloudCertificate | undefined {
  const d = cpf.replace(/\D/g, '');
  if (d) {
    const match = certs.find(c => { try { return parse(c.certificate).holderDoc === d; } catch { return false; } });
    if (match) return match;
  }
  return certs[0];
}
