// Supabase Edge Function "psc-assinatura": intermediário entre o JVM Lab e os
// PSCs de certificado em nuvem (BirdID, VaultID, VIDaaS, SafeID, NeoID) no
// padrão de API do ITI (OAuth 2 + certificate-discovery + signature).
//
// O client_id/client_secret de cada PSC fica só aqui (segredos da função):
//   PSC_BIRDID_CLIENT_ID / PSC_BIRDID_CLIENT_SECRET
//   PSC_VIDAAS_CLIENT_ID / PSC_VIDAAS_CLIENT_SECRET ... (um par por PSC)
// Opcionais por PSC: PSC_<ID>_URL (ex.: homologação) e PSC_<ID>_HASH (hex|base64).
//
// Só aceita usuários logados no JVM Lab. Nenhum documento passa por aqui: só
// o hash (SHA-256) dos atributos assinados do PDF.
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2.45.4';

type Provider = { label: string; url: string; hash: 'hex' | 'base64'; token: 'json' | 'form'; otp: boolean };

const PROVIDERS: Record<string, Provider> = {
  birdid: { label: 'BirdID (Soluti)', url: 'https://api.birdid.com.br/v0/oauth', hash: 'hex', token: 'json', otp: true },
  vaultid: { label: 'VaultID', url: 'https://apicloudid.vaultid.com.br/v0/oauth', hash: 'hex', token: 'json', otp: true },
  vidaas: { label: 'VIDaaS (Valid)', url: 'https://certificado.vidaas.com.br/v0/oauth', hash: 'base64', token: 'form', otp: false },
  safeid: { label: 'SafeID (Safeweb)', url: 'https://pscsafeweb.safewebpss.com.br/Service/Microservice/OAuth/v0/oauth', hash: 'base64', token: 'form', otp: false },
  neoid: { label: 'NeoID (Serpro)', url: 'https://neoid.estaleiro.serpro.gov.br/smartcert-api/v0/oauth', hash: 'base64', token: 'form', otp: false }
};

const SHA256_OID = '2.16.840.1.101.3.4.2.1';
const MAX_LIFETIME = 30 * 24 * 3600;

const cors = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS'
};

// Erros voltam com status 200 e { error } para a mensagem chegar ao app
const reply = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { ...cors, 'Content-Type': 'application/json' } });
const fail = (message: string) => reply({ error: message });

function config(id: string) {
  const base = PROVIDERS[id];
  if (!base) return null;
  const key = id.toUpperCase();
  const clientId = Deno.env.get(`PSC_${key}_CLIENT_ID`) || '';
  const clientSecret = Deno.env.get(`PSC_${key}_CLIENT_SECRET`) || '';
  if (!clientId || !clientSecret) return null;
  const hash = Deno.env.get(`PSC_${key}_HASH`);
  return {
    ...base,
    url: (Deno.env.get(`PSC_${key}_URL`) || base.url).replace(/\/+$/, ''),
    hash: hash === 'hex' || hash === 'base64' ? hash : base.hash,
    clientId,
    clientSecret
  };
}

const digits = (v: unknown) => String(v ?? '').replace(/\D/g, '');
const docParam = (v: unknown) => { const d = digits(v); return d.length <= 11 ? d.padStart(11, '0') : d.padStart(14, '0'); };
const lifetimeOf = (v: unknown) => Math.max(300, Math.min(MAX_LIFETIME, Math.floor(Number(v) || 7 * 24 * 3600)));

async function pscFetch(url: string, init: RequestInit) {
  const res = await fetch(url, { ...init, headers: { Accept: 'application/json', ...(init.headers || {}) } });
  const text = await res.text();
  let data: any = null;
  try { data = text ? JSON.parse(text) : null; } catch { data = null; }
  if (!res.ok) {
    const msg = data?.error_description || data?.message || data?.error || text.slice(0, 300) || res.statusText;
    throw new Error(`PSC respondeu ${res.status}: ${msg}`);
  }
  return data;
}

async function discoverCertificates(p: NonNullable<ReturnType<typeof config>>, token: string) {
  const data = await pscFetch(`${p.url}/certificate-discovery`, { method: 'GET', headers: { Authorization: `Bearer ${token}` } });
  const list = Array.isArray(data?.certificates) ? data.certificates : [];
  return list
    .map((c: any) => ({ alias: String(c.alias || ''), certificate: String(c.certificate || '') }))
    .filter((c: { alias: string; certificate: string }) => c.alias && c.certificate);
}

const hexToBase64 = (hex: string) => btoa(String.fromCharCode(...(hex.match(/../g) || []).map(h => parseInt(h, 16))));

Deno.serve(async req => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers: cors });
  if (req.method !== 'POST') return reply({ error: 'Método não permitido' }, 405);

  // só usuários logados (a chave pública "anon" sozinha não basta)
  const auth = req.headers.get('Authorization') || '';
  const supabase = createClient(Deno.env.get('SUPABASE_URL')!, Deno.env.get('SUPABASE_ANON_KEY')!, { global: { headers: { Authorization: auth } } });
  const { data: who } = await supabase.auth.getUser();
  if (!who?.user) return reply({ error: 'Faça login no JVM Lab.' }, 401);

  let body: any;
  try { body = await req.json(); } catch { return fail('Requisição inválida.'); }
  const action = String(body?.action || '');

  try {
    if (action === 'providers') {
      return reply({
        providers: Object.keys(PROVIDERS).filter(id => config(id)).map(id => ({ id, label: PROVIDERS[id].label, otp: PROVIDERS[id].otp }))
      });
    }

    const p = config(String(body?.provider || ''));
    if (!p) return fail('Provedor de certificado em nuvem não configurado no servidor.');

    if (action === 'authorize_url') {
      const redirect = String(body.redirectUri || '');
      if (!/^https:\/\/|^http:\/\/(localhost|127\.0\.0\.1)(:\d+)?\//.test(redirect)) return fail('Endereço de retorno inválido.');
      const q = new URLSearchParams({
        response_type: 'code',
        client_id: p.clientId,
        redirect_uri: redirect,
        scope: 'signature_session',
        code_challenge: String(body.challenge || ''),
        code_challenge_method: 'S256',
        state: String(body.state || ''),
        lifetime: String(lifetimeOf(body.lifetime))
      });
      if (digits(body.cpf)) q.set('login_hint', docParam(body.cpf));
      return reply({ url: `${p.url}/authorize?${q.toString()}` });
    }

    if (action === 'token') {
      const lifetime = lifetimeOf(body.lifetime);
      let data: any;
      if (body.otp) {
        // OTP do aplicativo do PSC (BirdID/VaultID)
        if (!p.otp) return fail('Este provedor não aceita código OTP: use "Autorizar no site do provedor".');
        data = await pscFetch(`${p.url}/pwd_authorize`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            client_id: p.clientId, client_secret: p.clientSecret, username: docParam(body.cpf),
            password: String(body.otp), grant_type: 'password', scope: 'signature_session', lifetime
          })
        });
      } else {
        const fields: Record<string, string> = {
          grant_type: 'authorization_code', client_id: p.clientId, client_secret: p.clientSecret,
          code: String(body.code || ''), redirect_uri: String(body.redirectUri || ''), code_verifier: String(body.verifier || ''),
          lifetime: String(lifetime)
        };
        data = p.token === 'json'
          ? await pscFetch(`${p.url}/token`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ ...fields, lifetime }) })
          : await pscFetch(`${p.url}/token`, { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: new URLSearchParams(fields).toString() });
      }
      const token = String(data?.access_token || '');
      if (!token) return fail('O provedor não devolveu a autorização.');
      const certificates = await discoverCertificates(p, token);
      if (!certificates.length) return fail('Nenhum certificado encontrado nesta conta do provedor.');
      return reply({ token, expiresIn: Number(data.expires_in) || lifetime, certificates });
    }

    if (action === 'sign') {
      const hashHex = String(body.hashHex || '').toLowerCase();
      if (!/^[0-9a-f]{64}$/.test(hashHex)) return fail('Hash inválido.');
      const data = await pscFetch(`${p.url}/signature`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${String(body.token || '')}` },
        body: JSON.stringify({
          certificate_alias: String(body.alias || ''),
          hashes: [{
            id: '1',
            alias: String(body.label || 'Certificado de treinamento - JVM Lab').slice(0, 120),
            hash: p.hash === 'hex' ? hashHex : hexToBase64(hashHex),
            hash_algorithm: SHA256_OID,
            signature_format: 'RAW'
          }]
        })
      });
      const signature = String(data?.signatures?.[0]?.raw_signature || '');
      if (!signature) return fail('O provedor não devolveu a assinatura.');
      return reply({ signature });
    }

    return fail('Ação desconhecida.');
  } catch (err) {
    return fail(err instanceof Error ? err.message : String(err));
  }
});
