/**
 * Consulta de CNPJ (dados públicos do cadastro da Receita Federal) para preencher o cliente.
 * Fontes públicas gratuitas, com CORS, tentadas em ordem:
 *   1. BrasilAPI  — https://brasilapi.com.br/api/cnpj/v1/{cnpj}
 *   2. CNPJ.ws    — https://publica.cnpj.ws/cnpj/{cnpj}
 * Precisa de internet. Só o número do CNPJ é enviado.
 */

export interface CnpjData {
  razaoSocial: string;
  nomeFantasia: string;
  endereco: string;
  numero: string;
  complemento: string;
  bairro: string;
  cidade: string;
  estado: string;
  cep: string;
  telefone: string;
  email: string;
  inscricaoEstadual: string;
  /** Situação cadastral (ATIVA, BAIXADA, INAPTA, SUSPENSA…) */
  situacao: string;
  fonte: 'BrasilAPI' | 'CNPJ.ws';
}

export const onlyDigits = (s: string) => (s || '').replace(/\D/g, '');

/** 12345678000195 → 12.345.678/0001-95 (formata enquanto digita). */
export function formatCnpj(s: string): string {
  const d = onlyDigits(s).slice(0, 14);
  return d
    .replace(/^(\d{2})(\d)/, '$1.$2')
    .replace(/^(\d{2})\.(\d{3})(\d)/, '$1.$2.$3')
    .replace(/\.(\d{3})(\d)/, '.$1/$2')
    .replace(/(\d{4})(\d)/, '$1-$2');
}

/** Valida os dígitos verificadores do CNPJ. */
export function validCnpj(s: string): boolean {
  const d = onlyDigits(s);
  if (d.length !== 14 || /^(\d)\1{13}$/.test(d)) return false;
  const calc = (len: number) => {
    const w = len === 12 ? [5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2] : [6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2];
    const sum = w.reduce((acc, wi, i) => acc + Number(d[i]) * wi, 0);
    const r = sum % 11;
    return r < 2 ? 0 : 11 - r;
  };
  return calc(12) === Number(d[12]) && calc(13) === Number(d[13]);
}

// ------------------------------------------------------------------ CPF
/** 12345678909 → 123.456.789-09 (formata enquanto digita). */
export function formatCpf(s: string): string {
  const d = onlyDigits(s).slice(0, 11);
  return d
    .replace(/^(\d{3})(\d)/, '$1.$2')
    .replace(/^(\d{3})\.(\d{3})(\d)/, '$1.$2.$3')
    .replace(/\.(\d{3})(\d{1,2})$/, '.$1-$2');
}

/** Valida os dígitos verificadores do CPF. */
export function validCpf(s: string): boolean {
  const d = onlyDigits(s);
  if (d.length !== 11 || /^(\d)\1{10}$/.test(d)) return false;
  const calc = (len: number) => {
    let sum = 0;
    for (let i = 0; i < len; i++) sum += Number(d[i]) * (len + 1 - i);
    const r = (sum * 10) % 11;
    return r === 10 ? 0 : r;
  };
  return calc(9) === Number(d[9]) && calc(10) === Number(d[10]);
}

export type DocKind = 'cnpj' | 'cpf';
/** Tipo do documento já gravado: 11 dígitos = CPF; demais = CNPJ. */
export const docKindOf = (s?: string): DocKind => (onlyDigits(s || '').length === 11 ? 'cpf' : 'cnpj');
/** Rótulo para laudos e relatórios: "CPF" ou "CNPJ". */
export const docLabel = (s?: string): 'CPF' | 'CNPJ' => (docKindOf(s) === 'cpf' ? 'CPF' : 'CNPJ');
export const formatDoc = (s: string, kind: DocKind) => (kind === 'cpf' ? formatCpf(s) : formatCnpj(s));
export const validDoc = (s: string, kind: DocKind) => (kind === 'cpf' ? validCpf(s) : validCnpj(s));

const str = (v: unknown) => (v === null || v === undefined ? '' : String(v)).trim();
const fmtCep = (v: unknown) => { const d = onlyDigits(str(v)); return d.length === 8 ? `${d.slice(0, 5)}-${d.slice(5)}` : d; };
const fmtPhone = (v: unknown) => {
  const d = onlyDigits(str(v));
  if (d.length === 10) return `(${d.slice(0, 2)}) ${d.slice(2, 6)}-${d.slice(6)}`;
  if (d.length === 11) return `(${d.slice(0, 2)}) ${d.slice(2, 7)}-${d.slice(7)}`;
  return d;
};
const joinStreet = (tipo: unknown, rua: unknown) => {
  const t = str(tipo), r = str(rua);
  return t && r && !r.toUpperCase().startsWith(t.toUpperCase()) ? `${t} ${r}` : r || t;
};

/** Resposta da BrasilAPI → dados do cliente. */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function mapBrasilApi(j: any): CnpjData {
  return {
    razaoSocial: str(j.razao_social),
    nomeFantasia: str(j.nome_fantasia),
    endereco: joinStreet(j.descricao_tipo_de_logradouro, j.logradouro),
    numero: str(j.numero),
    complemento: str(j.complemento),
    bairro: str(j.bairro),
    cidade: str(j.municipio),
    estado: str(j.uf).toUpperCase(),
    cep: fmtCep(j.cep),
    telefone: fmtPhone(j.ddd_telefone_1),
    email: str(j.email).toLowerCase(),
    inscricaoEstadual: '',
    situacao: str(j.descricao_situacao_cadastral).toUpperCase(),
    fonte: 'BrasilAPI'
  };
}

/** Resposta da CNPJ.ws (pública) → dados do cliente. */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function mapCnpjWs(j: any): CnpjData {
  const e = j.estabelecimento || {};
  const ie = Array.isArray(e.inscricoes_estaduais) ? e.inscricoes_estaduais.find((x: { ativo?: boolean }) => x.ativo !== false) : null;
  return {
    razaoSocial: str(j.razao_social),
    nomeFantasia: str(e.nome_fantasia),
    endereco: joinStreet(e.tipo_logradouro, e.logradouro),
    numero: str(e.numero),
    complemento: str(e.complemento),
    bairro: str(e.bairro),
    cidade: str(e.cidade?.nome),
    estado: str(e.estado?.sigla).toUpperCase(),
    cep: fmtCep(e.cep),
    telefone: fmtPhone(`${str(e.ddd1)}${str(e.telefone1)}`),
    email: str(e.email).toLowerCase(),
    inscricaoEstadual: str(ie?.inscricao_estadual),
    situacao: str(e.situacao_cadastral).toUpperCase(),
    fonte: 'CNPJ.ws'
  };
}

async function getJson(url: string, timeoutMs: number): Promise<{ status: number; json?: unknown }> {
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), timeoutMs);
  try {
    const r = await fetch(url, { signal: ctrl.signal, headers: { Accept: 'application/json' } });
    return { status: r.status, json: r.ok ? await r.json() : undefined };
  } finally {
    clearTimeout(timer);
  }
}

/** Busca os dados do CNPJ. Lança Error com mensagem em português quando não encontra ou não há conexão. */
export async function lookupCnpj(cnpj: string, timeoutMs = 12000): Promise<CnpjData> {
  const d = onlyDigits(cnpj);
  if (!validCnpj(d)) throw new Error('CNPJ inválido: confira os 14 dígitos.');
  if (typeof navigator !== 'undefined' && navigator.onLine === false) throw new Error('Sem conexão com a internet: a consulta do CNPJ precisa de internet.');
  let notFound = false;
  const sources: Array<[string, (j: unknown) => CnpjData]> = [
    [`https://brasilapi.com.br/api/cnpj/v1/${d}`, mapBrasilApi],
    [`https://publica.cnpj.ws/cnpj/${d}`, mapCnpjWs]
  ];
  for (const [url, map] of sources) {
    try {
      const r = await getJson(url, timeoutMs);
      if (r.json) {
        const data = map(r.json);
        if (data.razaoSocial) return data;
      }
      if (r.status === 404) notFound = true;
    } catch {
      // tenta a próxima fonte
    }
  }
  throw new Error(notFound
    ? 'CNPJ não encontrado no cadastro da Receita Federal.'
    : 'Não foi possível consultar o CNPJ agora (serviço indisponível ou sem internet). Tente de novo ou preencha manualmente.');
}
