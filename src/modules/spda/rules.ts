/**
 * Critérios e cálculos do módulo SPDA — ABNT NBR 5419:2026 (funções puras,
 * testadas em __tests__).
 *
 * Continuidade elétrica (NBR 5419-3:2026, Anexo F):
 * - 1ª verificação das armaduras (F.1, F.2, F.3): nenhum valor maior que 1 Ω.
 * - Verificação comprobatória (F.4.4): captação ↔ BEP, no máximo 0,2 Ω.
 * - Condutores não naturais (F.5 descida, F.6 eletrodo em anel) e equipotencialização
 *   (NBR 5419-4:2026, 9.2.6): valor compatível com o material e o comprimento do trecho
 *   (resistência por unidade de comprimento × comprimento). O app calcula o valor
 *   teórico; o limite adotado é informado pelo inspetor.
 * - Instrumento (F.1.3): 4 fios, corrente entre 1 A e 5 A, frequência ≠ 60 Hz ou CC;
 *   multímetro e terrômetro-alicate não são admitidos.
 *
 * Resistência de aterramento: NÃO é necessária para verificar a eficácia do SPDA
 * (7.1.4); na inspeção, a integridade do eletrodo é verificada por continuidade
 * (5.5.1.2 e 7.3.4). A medição, se feita, é registrada como informativa.
 *
 * Periodicidade (7.3.2 f): 1 ano para estruturas com áreas classificadas 0, 1, 20
 * e 21, munição, explosivos, componentes tóxicos, corrosão atmosférica severa ou
 * serviços essenciais; 3 anos para as demais.
 */
import { CHECKLIST_TEMPLATE } from './checklist';
import type {
  BuildingType, ChecklistAnswer, ChecklistTemplateItem, ConductorMaterial, ContinuityKind, ContinuityTest, GroundingTest, InspectionKind, ProtectionLevel,
  SpdaBuilding, SpdaKind,
  SpdaInspection, SpdaMethod, SpdaSubsystem, TestStatus
} from './types';

export const FIRST_VERIFICATION_LIMIT_OHM = 1;
export const PROOF_VERIFICATION_LIMIT_OHM = 0.2;
export const TEST_CURRENT_MIN_A = 1;
export const TEST_CURRENT_MAX_A = 5;
export const FALL_OF_POTENTIAL_TOLERANCE = 0.05;
export const NUMBER_PREFIX = 'SPDA-';
export const NORM_LABEL = 'ABNT NBR 5419:2026';

export const PROTECTION_LEVEL_LABEL: Record<ProtectionLevel, string> = {
  I: 'Nível I', II: 'Nível II', III: 'Nível III', IV: 'Nível IV'
};

/** NBR 5419-3:2026, Tabela 2 (esfera rolante e malha) e Tabela 5 (descidas e anéis intermediários). */
export const LEVEL_PARAMS: Record<ProtectionLevel, { sphereRadiusM: number; meshM: string; downSpacingM: number }> = {
  I: { sphereRadiusM: 20, meshM: '5 × 5', downSpacingM: 10 },
  II: { sphereRadiusM: 30, meshM: '10 × 10', downSpacingM: 10 },
  III: { sphereRadiusM: 45, meshM: '15 × 15', downSpacingM: 15 },
  IV: { sphereRadiusM: 60, meshM: '20 × 20', downSpacingM: 20 }
};

export const METHOD_LABEL: Record<SpdaMethod, string> = {
  franklin: 'Ângulo de proteção',
  gaiola: 'Malhas',
  esfera: 'Esfera rolante',
  misto: 'Combinação de métodos',
  estrutural: 'Componentes naturais (estrutural)'
};

export const INSPECTION_KIND_LABEL: Record<InspectionKind, string> = {
  durante_instalacao: 'Durante a instalação — 7.3.2 a)',
  inicial: 'Após a instalação ("as built") — 7.3.2 b)',
  apos_modificacao: 'Após alteração física/de uso da estrutura — 7.3.2 c)',
  apos_alteracao_spda: 'Após alteração do SPDA em relação ao projeto — 7.3.2 d)',
  apos_descarga: 'Suspeita de descarga / atuação de DPS classe I — 7.3.2 e)',
  periodica: 'Periódica — 7.3.2 f)'
};

export const TEST_STATUS_LABEL: Record<TestStatus, string> = {
  conforme: 'Conforme', nao_conforme: 'Não conforme', pendente: 'Pendente', informativo: 'Informativo'
};

export const CONTINUITY_KIND_LABEL: Record<ContinuityKind, string> = {
  F1: 'F.1 — Armaduras pilares + fundação (1ª verificação)',
  F2: 'F.2 — Armaduras dos pilares (1ª verificação)',
  F3: 'F.3 — Armaduras da fundação (1ª verificação)',
  F4: 'F.4 — Comprobatória captação ↔ BEP',
  F5: 'F.5 — Descida não natural',
  F6: 'F.6 — Eletrodo em anel não natural',
  EQ: 'Equipotencialização / aterramento (5419-4, 9.2.6)'
};

export const CONTINUITY_KIND_SHORT: Record<ContinuityKind, string> = {
  F1: 'F.1', F2: 'F.2', F3: 'F.3', F4: 'F.4', F5: 'F.5', F6: 'F.6', EQ: '5419-4 9.2.6'
};

/** Critério fixo da norma (Ω) ou null quando o valor é calculado pelo material/comprimento. */
export function normLimit(kind: ContinuityKind): number | null {
  if (kind === 'F1' || kind === 'F2' || kind === 'F3') return FIRST_VERIFICATION_LIMIT_OHM;
  if (kind === 'F4') return PROOF_VERIFICATION_LIMIT_OHM;
  return null;
}

export const KIND_SUBSYSTEM: Record<ContinuityKind, ContinuityTest['subsystem']> = {
  F1: 'descida', F2: 'descida', F3: 'aterramento', F4: 'captacao', F5: 'descida', F6: 'aterramento', EQ: 'equipotencializacao'
};

/**
 * Resistividade aproximada a 20 °C (Ω·mm²/m). Cobre 100 % IACS; ligas de alumínio
 * 50 % IACS (Tabela 7, nota d); aço cobreado 30 % IACS (nota f); alumínio cobreado
 * 64 % IACS (nota e); aço zincado e inox: valores típicos de referência.
 */
export const RESISTIVITY: Record<ConductorMaterial, { label: string; rho: number }> = {
  cobre: { label: 'Cobre', rho: 0.01724 },
  aluminio: { label: 'Alumínio (liga 50 % IACS)', rho: 0.0345 },
  aco_cobreado: { label: 'Aço cobreado (30 % IACS)', rho: 0.0575 },
  aluminio_cobreado: { label: 'Alumínio cobreado (64 % IACS)', rho: 0.0269 },
  aco_zincado: { label: 'Aço zincado a quente', rho: 0.138 },
  aco_inox: { label: 'Aço inoxidável', rho: 0.72 }
};

function isNum(v: unknown): v is number {
  return typeof v === 'number' && Number.isFinite(v);
}

export function newId(prefix = 'spda'): string {
  return `${prefix}_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 8)}`;
}

export function todayIso(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

// --------------------------------------------------------------- continuidade
/** Tipo do ensaio (registros da versão anterior, sem tipo, eram a comprobatória de 0,2 Ω). */
export function kindOf(t: Pick<ContinuityTest, 'kind'>): ContinuityKind {
  return t.kind || 'F4';
}

/** Resistência teórica do condutor do trecho: ρ·L/S (Ω). null se faltar dado. */
export function theoreticalResistance(t: Pick<ContinuityTest, 'material' | 'lengthM' | 'sectionMm2'>): number | null {
  if (!t.material || !isNum(t.lengthM) || !isNum(t.sectionMm2) || t.lengthM <= 0 || t.sectionMm2 <= 0) return null;
  return (RESISTIVITY[t.material].rho * t.lengthM) / t.sectionMm2;
}

/** Limite efetivo: o da norma (F.1 a F.4) ou o adotado pelo inspetor (F.5, F.6, EQ). */
export function effectiveLimit(t: ContinuityTest): number | null {
  const fixed = normLimit(kindOf(t));
  if (fixed !== null) return fixed;
  return isNum(t.limitOhm) && t.limitOhm > 0 ? t.limitOhm : null;
}

export function continuityStatus(t: ContinuityTest): TestStatus {
  if (!isNum(t.resistanceOhm)) return 'pendente';
  const limit = effectiveLimit(t);
  if (limit === null) return 'pendente';
  return t.resistanceOhm <= limit + 1e-12 ? 'conforme' : 'nao_conforme';
}

/** Corrente de ensaio fora da faixa de F.1.3.1 (1 A a 5 A). */
export function currentOutOfRange(t: Pick<ContinuityTest, 'currentA'>): boolean {
  return isNum(t.currentA) && (t.currentA < TEST_CURRENT_MIN_A || t.currentA > TEST_CURRENT_MAX_A);
}

// ---------------------------------------------------------------- aterramento
export function designValue(t: GroundingTest): number | null {
  if (isNum(t.designOhm)) return t.designOhm;
  if (isNum(t.limitOhm)) return t.limitOhm;
  return null;
}

/** Resistência adotada: valor informado ou, na queda de potencial, a leitura a 62 %. */
export function groundingValue(t: GroundingTest): number | null {
  if (isNum(t.resistanceOhm)) return t.resistanceOhm;
  if (t.method === 'queda_potencial' && isNum(t.r62)) return t.r62;
  return null;
}

/**
 * Patamar da curva de queda de potencial: leituras a 52 % e 72 % dentro de ±5 %
 * da leitura a 62 %. null = leituras insuficientes. (Boa prática de medição.)
 */
export function fallOfPotentialPlateau(t: Pick<GroundingTest, 'r52' | 'r62' | 'r72'>, tolerance = FALL_OF_POTENTIAL_TOLERANCE): { ok: boolean; maxDeviation: number } | null {
  if (!isNum(t.r52) || !isNum(t.r62) || !isNum(t.r72) || t.r62 <= 0) return null;
  const maxDeviation = Math.max(Math.abs(t.r52 - t.r62), Math.abs(t.r72 - t.r62)) / t.r62;
  return { ok: maxDeviation <= tolerance + 1e-9, maxDeviation };
}

/** Medição de resistência de aterramento é informativa (7.1.4): nunca gera NC. */
export function groundingStatus(t: GroundingTest): TestStatus {
  return groundingValue(t) === null ? 'pendente' : 'informativo';
}

/** Valor medido acima do valor de projeto (apenas observação). */
export function aboveDesign(t: GroundingTest): boolean {
  const v = groundingValue(t);
  const d = designValue(t);
  return v !== null && d !== null && v > d;
}

// ------------------------------------------------------------------ checklist
/** Documentação é geral da OS; os demais blocos são inspecionados por edificação. */
export function isPerBuilding(subsystem: SpdaSubsystem): boolean {
  return subsystem !== 'documentacao';
}

export function answerOf(checklist: ChecklistAnswer[], itemId: string, buildingId?: string): ChecklistAnswer {
  return checklist.find(a => a.itemId === itemId && (a.buildingId || undefined) === (buildingId || undefined)) || { itemId, buildingId, status: '' };
}

/** Pares item × edificação que precisam de resposta (sem edificações: um por item). */
export function expectedAnswers(buildings: Array<Pick<SpdaBuilding, 'id'>> = []): Array<{ item: ChecklistTemplateItem; buildingId?: string }> {
  return CHECKLIST_TEMPLATE.flatMap(item =>
    isPerBuilding(item.subsystem) && buildings.length ? buildings.map(b => ({ item, buildingId: b.id })) : [{ item, buildingId: undefined }]
  );
}

export function checklistProgress(checklist: ChecklistAnswer[], buildings: Array<Pick<SpdaBuilding, 'id'>> = []): { answered: number; total: number; nc: number } {
  const answers = expectedAnswers(buildings).map(e => answerOf(checklist, e.item.id, e.buildingId));
  return {
    total: answers.length,
    answered: answers.filter(a => a.status !== '').length,
    nc: answers.filter(a => a.status === 'NC').length
  };
}

// --------------------------------------------------------- não conformidades
export interface NonConformity {
  source: 'inspecao' | 'continuidade';
  subsystem: SpdaSubsystem;
  description: string;
  reference: string;
  obs?: string;
  buildingId?: string;
  buildingName?: string;
}

/** Continuidade: valor guardado em Ω, exibido em miliohms (mΩ), como no micro-ohmímetro. */
export function fmtMilliOhm(vOhm: number | null | undefined, digits = 3): string {
  if (!isNum(vOhm)) return '—';
  const m = Number((vOhm * 1000).toPrecision(10));
  return `${m.toLocaleString('pt-BR', { minimumFractionDigits: 0, maximumFractionDigits: digits })} mΩ`;
}

/** Ω → mΩ sem erro de arredondamento binário (0,0853 → 85,3). */
export function toMilli(vOhm: number | null | undefined): number | null {
  return isNum(vOhm) ? Number((vOhm * 1000).toPrecision(10)) : null;
}

/** mΩ → Ω. */
export function fromMilli(vMilli: number | null | undefined): number | null {
  return isNum(vMilli) ? Number((vMilli / 1000).toPrecision(10)) : null;
}

export function fmtOhm(v: number | null | undefined, digits = 3): string {
  if (!isNum(v)) return '—';
  return `${v.toLocaleString('pt-BR', { minimumFractionDigits: 0, maximumFractionDigits: digits })} Ω`;
}

export type WithBuildings = Pick<SpdaInspection, 'checklist' | 'continuity'> & { buildings?: SpdaBuilding[] };

export function buildingNameOf(insp: { buildings?: SpdaBuilding[] }, id?: string): string | undefined {
  if (!id) return undefined;
  const list = insp.buildings || [];
  const idx = list.findIndex(b => b.id === id);
  return idx >= 0 ? list[idx].name || `Edificação ${idx + 1}` : undefined;
}

export function listNonConformities(insp: WithBuildings): NonConformity[] {
  const out: NonConformity[] = [];
  expectedAnswers(insp.buildings || []).forEach(({ item, buildingId }) => {
    const a = answerOf(insp.checklist, item.id, buildingId);
    if (a.status === 'NC') {
      out.push({ source: 'inspecao', subsystem: item.subsystem, description: `${item.id} — ${item.title}`, reference: item.reference, obs: a.obs, buildingId, buildingName: buildingNameOf(insp, buildingId) });
    }
  });
  insp.continuity.forEach(t => {
    if (continuityStatus(t) === 'nao_conforme') {
      const kind = kindOf(t);
      out.push({
        source: 'continuidade', subsystem: t.subsystem,
        description: `Continuidade (${CONTINUITY_KIND_SHORT[kind]}) ${t.from || '?'} → ${t.to || '?'}: ${fmtMilliOhm(t.resistanceOhm)} (limite ${fmtMilliOhm(effectiveLimit(t))})`,
        reference: kind === 'EQ' ? 'NBR 5419-4:2026, 9.2.6' : `NBR 5419-3:2026, Anexo F (${CONTINUITY_KIND_SHORT[kind]})`,
        obs: t.obs, buildingId: t.buildingId, buildingName: buildingNameOf(insp, t.buildingId)
      });
    }
  });
  return out;
}

// ------------------------------------------------------------------ conclusão
export type Verdict = 'conforme' | 'nao_conforme' | 'incompleto';

export const VERDICT_LABEL: Record<Verdict, string> = {
  conforme: 'SPDA CONFORME',
  nao_conforme: 'SPDA NÃO CONFORME',
  incompleto: 'INSPEÇÃO INCOMPLETA'
};

/**
 * Parecer: não conforme se houver qualquer NC (checklist ou continuidade, em
 * qualquer edificação); incompleto se faltar resposta (documentação da OS e
 * itens de cada edificação) ou algum ensaio de continuidade estiver sem
 * valor/limite. A resistência de aterramento não entra (7.1.4).
 */
export function inspectionVerdict(insp: WithBuildings): Verdict {
  if (listNonConformities(insp).length > 0) return 'nao_conforme';
  const prog = checklistProgress(insp.checklist, insp.buildings || []);
  if (prog.answered < prog.total || insp.continuity.some(t => continuityStatus(t) === 'pendente')) return 'incompleto';
  return 'conforme';
}

/** Parecer de uma edificação (só os itens e ensaios dela). */
export function buildingVerdict(insp: WithBuildings, buildingId: string): Verdict {
  const part: WithBuildings = {
    checklist: insp.checklist.filter(a => a.buildingId === buildingId),
    continuity: insp.continuity.filter(t => t.buildingId === buildingId),
    buildings: (insp.buildings || []).filter(b => b.id === buildingId)
  };
  if (listNonConformities(part).length > 0) return 'nao_conforme';
  const perBuilding = CHECKLIST_TEMPLATE.filter(i => isPerBuilding(i.subsystem));
  const answered = perBuilding.filter(i => answerOf(part.checklist, i.id, buildingId).status !== '').length;
  if (answered < perBuilding.length || part.continuity.some(t => continuityStatus(t) === 'pendente')) return 'incompleto';
  return 'conforme';
}

/**
 * Registros da versão anterior (sem vínculo com edificação): cria a lista de
 * edificações se faltar e liga as respostas por edificação e os ensaios sem
 * edificação à primeira edificação. Não altera o que já está vinculado.
 */
export function migrateInspection(insp: SpdaInspection): SpdaInspection {
  const buildings = buildingsOf(insp);
  if (!buildings.length) return insp.buildings ? insp : { ...insp, buildings: [] };
  const first = buildings[0].id;
  const perBuilding = new Set(CHECKLIST_TEMPLATE.filter(i => isPerBuilding(i.subsystem)).map(i => i.id));
  const needs = !insp.buildings
    || insp.checklist.some(a => !a.buildingId && perBuilding.has(a.itemId))
    || insp.continuity.some(t => !t.buildingId)
    || insp.grounding.some(t => !t.buildingId);
  if (!needs) return insp;
  return {
    ...insp,
    buildings,
    checklist: insp.checklist.map(a => (!a.buildingId && perBuilding.has(a.itemId) ? { ...a, buildingId: first } : a)),
    continuity: insp.continuity.map(t => (t.buildingId ? t : { ...t, buildingId: first })),
    grounding: insp.grounding.map(t => (t.buildingId ? t : { ...t, buildingId: first }))
  };
}

// ------------------------------------------------------------- periodicidade
export function inspectionIntervalYears(criticalStructure: boolean): number {
  return criticalStructure ? 1 : 3;
}

export function addYears(isoDate: string, years: number): string {
  const [y, m, d] = isoDate.split('-').map(Number);
  if (!y || !m || !d) return '';
  const target = new Date(Date.UTC(y + years, m - 1, d));
  // 29/02 → 28/02 em ano não bissexto
  if (target.getUTCMonth() !== m - 1) target.setUTCDate(0);
  return target.toISOString().slice(0, 10);
}

export function nextInspectionDate(insp: Pick<SpdaInspection, 'inspectionDate' | 'criticalStructure'>): string {
  return insp.inspectionDate ? addYears(insp.inspectionDate, inspectionIntervalYears(insp.criticalStructure)) : '';
}

// ---------------------------------------------------------------- numeração
export function nextNumber(existing: string[], year = new Date().getFullYear()): string {
  const prefix = `${NUMBER_PREFIX}${year}-`;
  const max = existing
    .filter(n => n.startsWith(prefix))
    .map(n => parseInt(n.slice(prefix.length), 10))
    .filter(n => Number.isFinite(n))
    .reduce((a, b) => Math.max(a, b), 0);
  return `${prefix}${String(max + 1).padStart(4, '0')}`;
}

/** Converte texto digitado (vírgula decimal) em número; vazio → null. */
export function parseDecimal(text: string): number | null {
  const t = text.trim().replace(/\s/g, '').replace(',', '.');
  if (!t) return null;
  const n = Number(t);
  return Number.isFinite(n) ? n : null;
}

/** Recomendações automáticas a partir das não conformidades (texto editável). */
export function suggestRecommendations(insp: WithBuildings & Pick<SpdaInspection, 'criticalStructure'>): string {
  const ncs = listNonConformities(insp);
  const lines: string[] = [];
  if (ncs.some(n => n.source === 'inspecao')) {
    lines.push('Corrigir os itens não conformes da inspeção visual indicados neste laudo e registrar as correções com fotos. Prazo de manutenção de cada item conforme a criticidade, de "imediato" a "manutenção preventiva" (NBR 5419-3:2026, 7.4.2).');
  }
  if (ncs.some(n => n.source === 'continuidade')) {
    lines.push('Nos trechos com continuidade acima do critério do Anexo F, localizar e corrigir conexões/emendas (limpeza, reaperto, substituição de conectores ou do condutor) e repetir o ensaio com instrumento a 4 fios (F.1.3).');
  }
  if (ncs.length) lines.push('O responsável pela estrutura deve assegurar a adequação de todas as não conformidades apresentadas (NBR 5419-3:2026, 7.4.3).');
  lines.push(`Realizar nova inspeção periódica em até ${inspectionIntervalYears(insp.criticalStructure)} ano(s) (7.3.2 f), ou antes, após alteração na estrutura ou no SPDA e quando houver suspeita de descarga atmosférica (7.3.2 c) a e).`);
  return lines.map(l => `• ${l}`).join('\n');
}

// ---------------------------------------------------------------- edificações
export const BUILDING_TYPE_LABEL: Record<BuildingType, string> = {
  industrial: 'Industrial', hospitalar: 'Hospitalar', comercial: 'Comercial', residencial: 'Residencial', outro: 'Outro'
};

export const SPDA_KIND_LABEL: Record<SpdaKind, { label: string; hint: string }> = {
  estrutural: { label: 'Estrutural', hint: 'Armaduras de concreto armado como captores/descidas naturais contínuas (5.4.5 b/c)' },
  natural: { label: 'Natural', hint: 'Estruturas metálicas, telhados contínuos ou pilares de aço (5.3.2.16, 5.4.5 d/e)' },
  externo: { label: 'Externo', hint: 'Captores Franklin, condutores em malha e descidas não naturais (não isolado)' },
  isolado: { label: 'Externo isolado', hint: 'Captação e descidas afastadas da estrutura pela distância de segurança (5.4.2, 6.3)' },
  inexistente: { label: 'Inexistente', hint: 'Edificação sem SPDA instalado' }
};

/** Probabilidade PB por NP (NBR 5419-2:2026, Tabela B.2); eficiência = 1 − PB. */
export const LEVEL_PB: Record<ProtectionLevel, number> = { I: 0.02, II: 0.05, III: 0.1, IV: 0.2 };

export function levelEfficiencyPct(level: ProtectionLevel): number {
  return Math.round((1 - LEVEL_PB[level]) * 100);
}

const LEVEL_RANK: Record<ProtectionLevel, number> = { I: 1, II: 2, III: 3, IV: 4 };

export function buildingArea(b: Pick<SpdaBuilding, 'lengthM' | 'widthM'>): number | null {
  return isNum(b.lengthM) && isNum(b.widthM) && b.lengthM > 0 && b.widthM > 0 ? b.lengthM * b.widthM : null;
}

export function buildingPerimeter(b: Pick<SpdaBuilding, 'lengthM' | 'widthM'>): number | null {
  return isNum(b.lengthM) && isNum(b.widthM) && b.lengthM > 0 && b.widthM > 0 ? 2 * (b.lengthM + b.widthM) : null;
}

/** Nº mínimo de descidas: perímetro ÷ distância da Tabela 5, nunca menos que 2 (5.4.3.1). */
export function minDownConductors(b: Pick<SpdaBuilding, 'lengthM' | 'widthM' | 'protectionLevel'>): number | null {
  const p = buildingPerimeter(b);
  if (p === null) return null;
  return Math.max(2, Math.ceil(p / LEVEL_PARAMS[b.protectionLevel].downSpacingM - 1e-9));
}

/** Coordenadas válidas (graus decimais). */
export function validCoordinates(lat: unknown, lng: unknown): boolean {
  return isNum(lat) && isNum(lng) && lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180 && !(lat === 0 && lng === 0);
}

export function formatCoordinates(lat?: number | null, lng?: number | null): string {
  if (!validCoordinates(lat, lng)) return '';
  const f = (v: number, pos: string, neg: string) => `${Math.abs(v).toFixed(6).replace('.', ',')}° ${v >= 0 ? pos : neg}`;
  return `${f(lat as number, 'N', 'S')}, ${f(lng as number, 'L', 'O')}`;
}

/** Link para ver o local no mapa (aberto pelo usuário). */
export function mapUrl(lat?: number | null, lng?: number | null): string {
  return validCoordinates(lat, lng) ? `https://www.google.com/maps/@?api=1&map_action=map&center=${lat},${lng}&zoom=19&basemap=satellite` : '';
}

/** Edificações da inspeção; registros antigos (sem lista) viram uma edificação com os dados que já tinham. */
export function buildingsOf(insp: Pick<SpdaInspection, 'buildings' | 'buildingName' | 'buildingUse' | 'heightM' | 'protectionLevel' | 'downConductorsCount'>): SpdaBuilding[] {
  if (Array.isArray(insp.buildings)) return insp.buildings;
  if (!insp.buildingName && !insp.heightM && !insp.buildingUse) return [];
  return [{
    id: 'edif_legado', name: insp.buildingName || 'Edificação', type: 'industrial', activity: insp.buildingUse,
    heightM: insp.heightM ?? null, spdaKind: 'externo', protectionLevel: insp.protectionLevel, downConductorsCount: insp.downConductorsCount ?? null
  }];
}

/**
 * Aplica a lista de edificações e atualiza os campos-resumo da inspeção:
 * nome (todas), NP mais restritivo, altura máxima e total de descidas.
 */
export function withBuildings<T extends Pick<SpdaInspection, 'protectionLevel'>>(insp: T, buildings: SpdaBuilding[]): T & Pick<SpdaInspection, 'buildings' | 'buildingName' | 'heightM' | 'downConductorsCount' | 'protectionLevel' | 'buildingUse'> {
  const named = buildings.map(b => b.name.trim()).filter(Boolean);
  const heights = buildings.map(b => b.heightM).filter(isNum);
  const downs = buildings.map(b => b.downConductorsCount).filter(isNum);
  const level = buildings.length
    ? buildings.reduce<ProtectionLevel>((acc, b) => (LEVEL_RANK[b.protectionLevel] < LEVEL_RANK[acc] ? b.protectionLevel : acc), 'IV')
    : insp.protectionLevel;
  // edificação removida: saem as respostas e os ensaios vinculados a ela
  const ids = new Set(buildings.map(b => b.id));
  const keep = <R extends { buildingId?: string }>(list: R[] | undefined) => (Array.isArray(list) ? list.filter(r => !r.buildingId || ids.has(r.buildingId)) : list);
  const anyInsp = insp as Partial<SpdaInspection>;
  const cleaned: Partial<SpdaInspection> = {};
  if (Array.isArray(anyInsp.checklist)) cleaned.checklist = keep(anyInsp.checklist);
  if (Array.isArray(anyInsp.continuity)) cleaned.continuity = keep(anyInsp.continuity);
  if (Array.isArray(anyInsp.grounding)) cleaned.grounding = keep(anyInsp.grounding);
  return {
    ...insp,
    ...cleaned,
    buildings,
    buildingName: named.join(' / '),
    buildingUse: [...new Set(buildings.map(b => b.activity?.trim() || BUILDING_TYPE_LABEL[b.type]))].join(' / '),
    heightM: heights.length ? Math.max(...heights) : null,
    downConductorsCount: downs.length ? downs.reduce((a, b) => a + b, 0) : null,
    protectionLevel: level
  };
}

// --------------------------------------------------------- relatório por subsistema
/**
 * Resumo de um subsistema (ex.: captação) em uma edificação: itens do checklist,
 * ensaios dos tipos informados, NCs e parecer (mesmos critérios do laudo).
 */
export function subsystemSummary(
  insp: WithBuildings,
  subsystems: SpdaSubsystem[],
  kinds: ContinuityKind[],
  buildingId?: string
): { answered: number; total: number; nc: number; tests: ContinuityTest[]; testsNc: number; ncs: NonConformity[]; verdict: Verdict } {
  const items = CHECKLIST_TEMPLATE.filter(i => subsystems.includes(i.subsystem));
  const bidOf = (sub: SpdaSubsystem) => (isPerBuilding(sub) ? buildingId : undefined);
  const answers = items.map(i => answerOf(insp.checklist, i.id, bidOf(i.subsystem)));
  const tests = insp.continuity.filter(t => kinds.includes(kindOf(t)) && (!buildingId || t.buildingId === buildingId));
  // NCs só deste subsistema (e edificação): checklist filtrado + ensaios do subsistema
  const itemIds = new Set(items.map(i => i.id));
  const ncs = listNonConformities({
    buildings: insp.buildings,
    checklist: insp.checklist.filter(a => itemIds.has(a.itemId) && (!buildingId || !a.buildingId || a.buildingId === buildingId)),
    continuity: tests
  });
  const nc = answers.filter(a => a.status === 'NC').length;
  const testsNc = tests.filter(t => continuityStatus(t) === 'nao_conforme').length;
  const answered = answers.filter(a => a.status !== '').length;
  const verdict: Verdict = nc + testsNc > 0
    ? 'nao_conforme'
    : answered < items.length || tests.some(t => continuityStatus(t) === 'pendente') ? 'incompleto' : 'conforme';
  return { answered, total: items.length, nc, tests, testsNc, ncs, verdict };
}
