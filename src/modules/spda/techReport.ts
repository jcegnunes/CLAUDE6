/**
 * Relatório técnico (menu Relatório): textos padrão gerados a partir da inspeção.
 * Cada texto pode ser editado; vazio = usa o padrão.
 */
import { formatDateBR } from '../../utils/dateUtils';
import { INSPECTION_KIND_LABEL, PROTECTION_LEVEL_LABEL, buildingsOf, inspectionVerdict, listNonConformities, nextInspectionDate, spdaKindText, type ContinuityGroup, type Verdict } from './rules';
import type { ProtectionLevel, ReportResult, SpdaInspection, TechReportTexts } from './types';

/** Textos livres do relatório técnico (os resultados e os textos por subsistema ficam à parte). */
export type TechTextKey = Exclude<keyof TechReportTexts, 'results' | 'subsystemTexts'>;

// ---------------------------------------------------------------- resultados (aprovado / reprovado / atende parcialmente)
/** Resultado exibido no relatório técnico; "incompleto" = nada avaliado e nada escolhido. */
export type ShownResult = ReportResult | 'incompleto';

export const RESULT_LABEL: Record<ShownResult, string> = {
  aprovado: 'APROVADO', reprovado: 'REPROVADO', parcial: 'ATENDE PARCIALMENTE', incompleto: 'SEM ITENS AVALIADOS'
};
/** Selo do parecer no laudo e nas listas. */
export const LAUDO_RESULT_LABEL: Record<ShownResult, string> = {
  aprovado: 'SPDA APROVADO', reprovado: 'SPDA REPROVADO', parcial: 'SPDA ATENDE PARCIALMENTE', incompleto: 'SEM ITENS AVALIADOS'
};
export const RESULT_OPTION_LABEL: Record<ReportResult, string> = { aprovado: 'Aprovado', reprovado: 'Reprovado', parcial: 'Atende parcialmente' };

/** Resultado calculado pela inspeção (sem escolha manual). */
export function resultFromVerdict(v: Verdict): ShownResult {
  return v === 'conforme' ? 'aprovado' : v === 'nao_conforme' ? 'reprovado' : 'incompleto';
}

export const subsystemKey = (buildingId: string | undefined, group: ContinuityGroup) => `${buildingId || 'geral'}_${group}`;

/** Resultado de um subsistema: o escolhido no relatório ou o calculado. */
export function subsystemResult(v: SpdaInspection, buildingId: string | undefined, group: ContinuityGroup, computed: Verdict): ShownResult {
  return v.techReport?.results?.[subsystemKey(buildingId, group)] || resultFromVerdict(computed);
}

/** Parecer final: o escolhido no relatório ou o calculado. */
export function finalResult(v: SpdaInspection): ShownResult {
  return v.techReport?.results?.final || resultFromVerdict(inspectionVerdict(v));
}

/** Agrega resultados (pior caso): reprovado > parcial > aprovado > incompleto. */
export function worstResult(list: ShownResult[]): ShownResult {
  return list.includes('reprovado') ? 'reprovado' : list.includes('parcial') ? 'parcial' : list.includes('aprovado') ? 'aprovado' : 'incompleto';
}

const GROUP_NAME: Record<ContinuityGroup, string> = {
  captacao: 'Subsistema de captação', descida: 'Subsistema de descida', aterramento: 'Subsistema de aterramento', dps: 'Equipotencialização e DPS'
};

/** Texto padrão ao lado do resultado na folha de ensaio do subsistema. */
export function defaultSubsystemResultText(group: ContinuityGroup, r: ShownResult, ncs: number): string {
  if (r === 'aprovado') return `${GROUP_NAME[group]} aprovado: atende à ABNT NBR 5419:2026 nos itens verificados.`;
  if (r === 'reprovado') return `${GROUP_NAME[group]} reprovado${ncs ? `: ${ncs} não conformidade(s)` : ''} — ver Seção 8.`;
  if (r === 'parcial') return `${GROUP_NAME[group]} atende parcialmente à ABNT NBR 5419:2026${ncs ? `; ${ncs} não conformidade(s) a corrigir` : ''} — ver Seção 8.`;
  return 'Sem itens avaliados.';
}

/** Texto de um subsistema (editado ou padrão). kind: desc = capítulo 5; res = resultado na folha de ensaio. */
export function subsystemText(v: SpdaInspection, kind: 'desc' | 'res', buildingId: string | undefined, group: ContinuityGroup, fallback: string): string {
  return v.techReport?.subsystemTexts?.[`${kind}_${subsystemKey(buildingId, group)}`]?.trim() || fallback;
}

export const TECH_TEXT_LABEL: Record<TechTextKey, string> = {
  resumo: '1. Resumo executivo — texto do parecer',
  introducao: '2. Objetivo e escopo',
  metodoInspecao: '6.1 Metodologia — inspeção visual',
  metodoContinuidade: '6.2 Metodologia — continuidade elétrica (descidas e eletrodo de aterramento)',
  metodoContinuidadeAterramento: '6.2 Complemento (opcional) — continuidade do eletrodo de aterramento',
  metodoAterramento: '6.3 Metodologia — resistência de aterramento (queda de potencial)',
  recomendacoes: '8. Recomendações gerais',
  conclusao: '9. Conclusão e parecer técnico',
  laudoConclusao: 'Texto do parecer do laudo (seção 9 — Conclusão)'
};

const listPt = (xs: string[]) => (xs.length <= 1 ? xs.join('') : `${xs.slice(0, -1).join(', ')} e ${xs[xs.length - 1]}`);

export function defaultResumo(v: SpdaInspection): string {
  const bs = buildingsOf(v);
  const r = finalResult(v);
  const ncs = listNonConformities(v).length;
  const proxima = formatDateBR(nextInspectionDate(v));
  const levels = [...new Set(bs.map(b => b.protectionLevel).filter(Boolean).map(l => PROTECTION_LEVEL_LABEL[l as ProtectionLevel]))].join(' / ');
  const kinds = [...new Set(bs.map(b => spdaKindText(b).toLowerCase()))].join(' / ');
  return `O SPDA ${kinds} ${bs.length > 1 ? `das edificações ${listPt(bs.map(b => b.name))}` : `do ${bs[0]?.name || 'edifício'}`}${levels ? `, ${levels.replace(/^Nível/, 'Nível de Proteção')}` : ''}, foi inspecionado e ensaiado em ${formatDateBR(v.inspectionDate)}. `
    + (r === 'aprovado' ? 'Os subsistemas avaliados atendem à ABNT NBR 5419:2026; o SPDA foi aprovado. '
      : r === 'reprovado' ? `O SPDA foi reprovado${ncs ? `, com ${ncs} não conformidade(s) detalhada(s) nas folhas de ensaio e na Seção 8` : ''}. `
        : r === 'parcial' ? `O SPDA atende parcialmente à ABNT NBR 5419:2026${ncs ? `; ${ncs} não conformidade(s) detalhada(s) nas folhas de ensaio e na Seção 8 devem ser corrigidas` : ''}. `
          : 'Nenhum item foi inspecionado ou ensaiado. ')
    + `Próxima inspeção periódica: ${proxima}.`;
}

export function defaultRecomendacoes(v: SpdaInspection): string {
  const custom = v.recommendations?.trim();
  if (custom) return custom;
  const proxima = formatDateBR(nextInspectionDate(v));
  return [
    'a) Manter o plano de manutenção preventiva do SPDA, com registro das intervenções.',
    'b) Realizar inspeção extraordinária após descarga atmosférica na estrutura, reformas, ampliações ou alteração de uso.',
    `c) Realizar a próxima inspeção periódica até ${proxima} (ABNT NBR 5419-3:2026, 7.3.2 f).`
  ].join('\n');
}

const list = (xs: string[]) => (xs.length <= 1 ? xs.join('') : `${xs.slice(0, -1).join(', ')} e ${xs[xs.length - 1]}`);

export function defaultIntroducao(v: SpdaInspection): string {
  const bs = buildingsOf(v).map(b => b.name || 'edificação');
  const local = [v.address, v.city && `${v.city}${v.state ? `/${v.state}` : ''}`].filter(Boolean).join(' — ');
  const kind = (INSPECTION_KIND_LABEL[v.inspectionKind] || 'periódica').split(' — ')[0].toLowerCase();
  return [
    `Este relatório apresenta os resultados da inspeção ${kind} e dos ensaios do Sistema de Proteção contra Descargas Atmosféricas (SPDA)`
      + `${bs.length ? ` ${bs.length > 1 ? 'das edificações' : 'da edificação'} ${list(bs)}` : ''}`
      + `${v.clientName ? `, de propriedade de ${v.clientName}` : ''}${local ? `, situada${bs.length > 1 ? 's' : ''} em ${local}` : ''}`
      + `${v.serviceOrderNumber ? `, conforme a Ordem de Serviço ${v.serviceOrderNumber}` : ''}. Os serviços foram realizados em ${formatDateBR(v.inspectionDate)}.`,
    'O escopo compreende a verificação dos subsistemas de captação, descida, aterramento e equipotencialização/DPS quanto ao atendimento à ABNT NBR 5419:2026, por meio de inspeção visual e ensaios de continuidade elétrica, além da medição informativa da resistência de aterramento. Não fazem parte do escopo a análise de risco (Parte 2), o reprojeto do SPDA e a inspeção das instalações elétricas internas.'
  ].join('\n\n').replace(/\s+,/g, ',');
}

export function defaultMetodoInspecao(): string {
  return 'Executada conforme ABNT NBR 5419-3:2026, Seção 7, por subsistema: documentação, captação, descida, aterramento e equipotencialização/DPS. Cada item da lista de verificação é classificado como Conforme (C), Não conforme (NC) ou Não aplicável (NA), com observação e registro fotográfico quando pertinente.';
}

export function defaultMetodoContinuidade(): string {
  return 'Método de injeção direta de corrente (Anexo F), com micro-ohmímetro a 4 fios e corrente de 1 A a 5 A (F.1.3). Com os conectores de ensaio abertos nas caixas de inspeção, mede-se o trecho entre duas descidas adjacentes: (a) pelo lado da captação, e (b) pelo lado do eletrodo de aterramento. O processo é repetido até cobrir todos os trechos (D1–D2, D2–D3, D3–D4, D4–D1). As medições são identificadas por subsistema (CAP, DES, ATE e DPS).';
}

/** Complemento opcional da 5.2 (vazio por padrão: o texto da 5.2 já cobre o eletrodo de aterramento). */
export function defaultMetodoContinuidadeAterramento(): string {
  return '';
}

export function defaultMetodoAterramento(): string {
  return 'Medida com terrômetro digital pelo método da queda de potencial (ABNT NBR 15749): eletrodo auxiliar de corrente (C) afastado do eletrodo ensaiado (E) e eletrodo de potencial (P) cravado a 52 %, 62 % e 72 % da distância EC (ou 57/62/67 %, com passo de 5 %). O patamar é caracterizado quando as três leituras ficam dentro de ±5 % da leitura a 62 %, adotada como resistência de aterramento. A medição é informativa (ABNT NBR 5419-3:2026, 7.1.4).';
}

export function defaultConclusao(v: SpdaInspection): string {
  const r = finalResult(v);
  const verdict: Verdict | 'parcial' = r === 'aprovado' ? 'conforme' : r === 'reprovado' ? 'nao_conforme' : r === 'parcial' ? 'parcial' : 'incompleto';
  const ncs = listNonConformities(v).length;
  const proxima = formatDateBR(nextInspectionDate(v));
  if (verdict === 'conforme') {
    return `Com base nos itens inspecionados e nos ensaios realizados, o SPDA encontra-se conforme à ABNT NBR 5419:2026 e foi aprovado${ncs ? '' : ', não tendo sido identificadas não conformidades'}. Próxima inspeção periódica até ${proxima}.`;
  }
  if (verdict === 'parcial') {
    return `Com base nos itens inspecionados e nos ensaios realizados, o SPDA atende parcialmente à ABNT NBR 5419:2026${ncs ? `, com ${ncs} não conformidade(s) descrita(s) na Seção 8` : ''}. As correções recomendadas devem ser executadas para o pleno atendimento à norma. Próxima inspeção periódica: até ${proxima}.`;
  }
  if (verdict === 'nao_conforme') {
    return `Com base nos itens inspecionados e nos ensaios realizados, o SPDA encontra-se não conforme à ABNT NBR 5419:2026 e foi reprovado${ncs ? `, com ${ncs} não conformidade(s) descrita(s) na Seção 8` : ''}. As correções recomendadas devem ser executadas e o sistema reinspecionado após a sua conclusão. Próxima inspeção periódica: até ${proxima}.`;
  }
  return `Nenhum item foi inspecionado ou ensaiado; não é possível emitir parecer sobre a conformidade do SPDA à ABNT NBR 5419:2026.`;
}

/** Laudo, seção 9: texto padrão do parecer, conforme o resultado final (escolhido ou calculado). */
export function defaultLaudoConclusao(v: SpdaInspection): string {
  const r = finalResult(v);
  if (r === 'aprovado') return 'Com base na inspeção visual e nos ensaios realizados, o SPDA encontra-se em conformidade com a ABNT NBR 5419:2026 nos itens verificados e está aprovado.';
  if (r === 'reprovado') return 'Foram encontradas não conformidades que devem ser corrigidas para que o SPDA atenda à ABNT NBR 5419:2026; o SPDA está reprovado. O responsável pela estrutura deve assegurar as adequações (7.4.3); após as correções, recomenda-se nova inspeção dos itens afetados.';
  if (r === 'parcial') return 'O SPDA atende parcialmente à ABNT NBR 5419:2026. As não conformidades e melhorias apontadas devem ser tratadas para o pleno atendimento à norma; o responsável pela estrutura deve assegurar as adequações (7.4.3) e, após as correções, recomenda-se nova inspeção dos itens afetados.';
  return 'Nenhum item foi inspecionado ou ensaiado; não é possível emitir parecer sobre a conformidade do SPDA.';
}

/** Nº do laudo: o editado ou o nº da inspeção. */
export function laudoNumber(v: Pick<SpdaInspection, 'laudoNumber' | 'number'>): string {
  return v.laudoNumber?.trim() || v.number;
}

/** Data (AAAA-MM-DD) e hora (HH:MM) de emissão do laudo: as editadas ou as do momento `now`. */
export function laudoIssued(v: Pick<SpdaInspection, 'laudoDate' | 'laudoTime'>, now = new Date()): { date: string; time: string } {
  const p = (n: number) => String(n).padStart(2, '0');
  const date = /^\d{4}-\d{2}-\d{2}$/.test(v.laudoDate || '') ? v.laudoDate! : `${now.getFullYear()}-${p(now.getMonth() + 1)}-${p(now.getDate())}`;
  const time = /^\d{2}:\d{2}$/.test(v.laudoTime || '') ? v.laudoTime! : `${p(now.getHours())}:${p(now.getMinutes())}`;
  return { date, time };
}

/** Texto efetivo: o editado ou o padrão. */
export function techText(v: SpdaInspection, key: TechTextKey): string {
  const custom = v.techReport?.[key]?.trim();
  if (custom) return custom;
  switch (key) {
    case 'resumo': return defaultResumo(v);
    case 'recomendacoes': return defaultRecomendacoes(v);
    case 'introducao': return defaultIntroducao(v);
    case 'metodoInspecao': return defaultMetodoInspecao();
    case 'metodoContinuidade': return defaultMetodoContinuidade();
    case 'metodoContinuidadeAterramento': return defaultMetodoContinuidadeAterramento();
    case 'metodoAterramento': return defaultMetodoAterramento();
    case 'conclusao': return defaultConclusao(v);
    case 'laudoConclusao': return defaultLaudoConclusao(v);
  }
}

export const TECH_SECTIONS: Array<{ n: string; title: string; sub?: string[] }> = [
  { n: '1', title: 'Resumo executivo' },
  { n: '2', title: 'Objetivo e escopo' },
  { n: '3', title: 'Referências normativas' },
  { n: '4', title: 'Caracterização da edificação' },
  { n: '5', title: 'Descrição dos subsistemas evidenciados na inspeção' },
  { n: '6', title: 'Metodologia', sub: ['6.1 Inspeção visual', '6.2 Continuidade elétrica — descidas e eletrodo de aterramento', '6.3 Resistência de aterramento — queda de potencial', '6.4 Critérios de aceitação', '6.5 Instrumentos utilizados', '6.6 Condições do ensaio'] },
  { n: '7', title: 'Folhas de ensaio e relatórios fotográficos', sub: ['Por subsistema (e por edificação): folha de ensaio com inspeção visual, medições, croqui, NC e resultado; relatório fotográfico'] },
  { n: '8', title: 'Não conformidades e recomendações' },
  { n: '9', title: 'Conclusão e parecer técnico' },
  { n: '10', title: 'Responsabilidade técnica e anexos' }
];
