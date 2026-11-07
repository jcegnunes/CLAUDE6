// Supabase Edge Function "analisar-norma": lê o texto de uma Norma
// Regulamentadora (enviado pelo JVM Lab) e, com o Claude (Anthropic), propõe
// os cursos que a norma exige: carga horária, periodicidade (validade),
// modalidade, pré-requisito, conteúdo programático com horas por tópico e os
// itens da norma que sustentam cada informação.
//
// Segredo da função (Edge Functions → Secrets):
//   ANTHROPIC_API_KEY  (chave criada em console.anthropic.com)
//
// Só aceita usuários logados no JVM Lab. O texto da norma é enviado à API da
// Anthropic para a análise; nada é gravado aqui.

import Anthropic from 'npm:@anthropic-ai/sdk@1.6.0';
import { zodOutputFormat } from 'npm:@anthropic-ai/sdk@1.6.0/helpers/zod';
import { z } from 'npm:zod@4.6.7';

const MODEL = 'claude-opus-5-5';
/** Texto máximo aceito (caracteres): as NRs maiores (ex.: NR-12 com anexos) ficam abaixo disso */
const MAX_CHARS = 900_000;

const cors = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS'
};

// Erros voltam com status 200 e { error } para a mensagem chegar ao app
const reply = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { ...cors, 'Content-Type': 'application/json' } });
const fail = (message: string) => reply({ error: message });

const Topic = z.object({
  title: z.string().describe('Tópico do conteúdo programático, com o texto da norma (pode resumir subitens)'),
  hours: z.number().describe('Horas do tópico; a soma dos tópicos deve dar a carga horária do curso'),
  normItem: z.string().describe('Item/anexo da norma de onde vem o tópico, ex.: "Anexo II, item 1.2" ou "35.3.2 a)"')
});

const Course = z.object({
  code: z.string().describe('Sigla curta em maiúsculas, ex.: "NR-35", "NR-35 RECICLAGEM", "NR-10 SEP"'),
  name: z.string().describe('Nome do curso como sai no certificado, ex.: "NR-35 – Trabalho em Altura"'),
  kind: z.enum(['inicial', 'periodico', 'reciclagem', 'complementar', 'eventual', 'outro']),
  normReference: z.string().describe('Norma e itens que exigem o treinamento, ex.: "NR-35 (Portaria MTP nº 4.218/2022), item 35.3"'),
  workloadHours: z.number().describe('Carga horária mínima total exigida pela norma, em horas'),
  validityMonths: z.number().describe('Periodicidade do treinamento em meses (ex.: bienal = 24). 0 quando a norma não fixa periodicidade'),
  modality: z.enum(['presencial', 'semipresencial', 'ead']).describe('Modalidade mais restritiva que a norma permite para o curso'),
  minAttendance: z.number().describe('Frequência mínima exigida (%); 100 se a norma não disser'),
  minGrade: z.number().nullable().describe('Nota mínima (0 a 10) só se a norma fixar; null se não fixar'),
  prerequisite: z.string().describe('Pré-requisito para o curso (ex.: curso básico válido); vazio se não houver'),
  notes: z.string().describe('Observações para o verso do certificado: parte prática, instrutor, modalidade, periodicidade, com os itens da norma'),
  topicHoursFromNorm: z.boolean().describe('true se a norma define as horas de cada tópico; false se as horas por tópico são sugestão sua'),
  topics: z.array(Topic),
  evidence: z.string().describe('Trechos curtos (com o número do item) que fixam carga horária, periodicidade e conteúdo')
});

const Analysis = z.object({
  norm: z.object({
    code: z.string().describe('Ex.: "NR-35"'),
    title: z.string().describe('Título da norma'),
    revision: z.string().describe('Portaria/ano da redação analisada, se aparecer no texto; vazio se não aparecer')
  }),
  courses: z.array(Course).describe('Um item por treinamento distinto que a norma exige (inicial, periódico, complementar, por função...)'),
  warnings: z.array(z.string()).describe('Pontos para o responsável conferir: lacunas, ambiguidades, exigências que não cabem no cadastro')
});

const SYSTEM = `Você é engenheiro de segurança do trabalho e analisa Normas Regulamentadoras (NR) brasileiras
para cadastrar os cursos de capacitação de uma empresa de treinamentos.

A partir do texto da norma enviado:
- Identifique cada treinamento que a norma exige (inicial, periódico/reciclagem, complementar, por função ou
  classe de instalação) e proponha um curso para cada um.
- Use somente o que está no texto: carga horária mínima, periodicidade, conteúdo programático, modalidade
  permitida, pré-requisitos e critérios de aprovação. Cite o item da norma em cada informação.
- Conteúdo programático: siga a ordem e a numeração da norma (anexos de capacitação, quando houver). Se a norma
  não der as horas de cada tópico, distribua a carga horária mínima de forma proporcional ao conteúdo, em
  múltiplos de 0,5 h, marque topicHoursFromNorm=false e avise em warnings. A soma das horas dos tópicos deve ser
  igual à carga horária do curso.
- Periodicidade: converta para meses (anual = 12, bienal = 24). Sem periodicidade na norma, use 0 e explique em
  warnings os casos de treinamento eventual (mudança de procedimento, retorno de afastamento etc.).
- Nota mínima: só preencha se a norma fixar; caso contrário null.
- Se o texto não for uma NR ou não exigir treinamento, devolva courses vazio e explique em warnings.
- Escreva em português do Brasil, sem inventar itens que não estão no texto.`;

Deno.serve(async req => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers: cors });
  if (req.method !== 'POST') return reply({ error: 'Método não permitido' }, 405);

  // só usuários logados (a chave pública "anon" sozinha não basta)
  const auth = req.headers.get('Authorization') || '';
  let loggedIn = false;
  try {
    const who = await fetch(`${Deno.env.get('SUPABASE_URL')}/auth/v1/user`, {
      headers: { Authorization: auth, apikey: Deno.env.get('SUPABASE_ANON_KEY') || '' }
    });
    loggedIn = who.ok && !!(await who.json())?.id;
  } catch { loggedIn = false; }
  if (!loggedIn) return reply({ error: 'Faça login no JVM Lab.' }, 401);

  if (!Deno.env.get('ANTHROPIC_API_KEY')) return fail('A análise por IA não está configurada no servidor (segredo ANTHROPIC_API_KEY).');

  let body: { text?: unknown; fileName?: unknown };
  try { body = await req.json(); } catch { return fail('Requisição inválida.'); }
  const text = String(body?.text || '').trim();
  const fileName = String(body?.fileName || 'norma');
  if (text.length < 500) return fail('O arquivo tem pouco texto. Envie o PDF da norma com texto (não digitalizado como imagem) ou um TXT.');
  if (text.length > MAX_CHARS) return fail('O texto da norma é grande demais para uma análise. Envie só a norma (sem manuais ou anexos extras).');

  const client = new Anthropic();
  try {
    const response = await client.messages.parse({
      model: MODEL,
      max_tokens: 32000,
      output_config: { effort: 'medium', format: zodOutputFormat(Analysis) },
      system: SYSTEM,
      messages: [{
        role: 'user',
        content: `Arquivo: ${fileName}\n\n<norma>\n${text}\n</norma>\n\nAnalise a norma e proponha os cursos de capacitação.`
      }]
    });
    if (response.stop_reason === 'refusal') return fail('A IA não analisou este texto. Confira se o arquivo é a norma correta.');
    if (response.stop_reason === 'max_tokens') return fail('A análise ficou longa demais. Envie só a parte da norma que trata da capacitação.');
    if (!response.parsed_output) return fail('A IA não devolveu a análise no formato esperado. Tente de novo.');
    return reply({ analysis: response.parsed_output, model: response.model, usage: response.usage });
  } catch (err) {
    if (err instanceof Anthropic.AuthenticationError) return fail('Chave da Anthropic inválida (segredo ANTHROPIC_API_KEY).');
    if (err instanceof Anthropic.RateLimitError) return fail('Limite de uso da IA atingido. Tente de novo em alguns minutos.');
    if (err instanceof Anthropic.BadRequestError) return fail(`A IA recusou a requisição: ${err.message}`);
    if (err instanceof Anthropic.APIError) return fail(`Erro da IA (${err.status ?? 'sem status'}): ${err.message}`);
    return fail(err instanceof Error ? err.message : String(err));
  }
});
