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
import { CHECKLIST_MIGRATIONS, CHECKLIST_TEMPLATE, CHECKLIST_VERSION } from './checklist';
import type {
  AirTerminationType, BuildingType, DownConductorType, GroundingSystemType, ChecklistAnswer, ChecklistTemplateItem, ConductorMaterial, ContinuityKind, ContinuityTest, GroundingTest, InspectionKind, ProtectionLevel,
  SpdaBuilding, SpdaFinding, SpdaKind,
  SpdaInspection, SpdaMethod, SpdaSubsystem, TestStatus
} from './types';

export const FIRST_VERIFICATION_LIMIT_OHM = 1;
export const PROOF_VERIFICATION_LIMIT_OHM = 0.2;
export const TEST_CURRENT_MIN_A = 1;
export const TEST_CURRENT_MAX_A = 5;
/** Corrente de ensaio preenchida automaticamente em todos os ensaios de continuidade (A) */
export const DEFAULT_TEST_CURRENT_A = 1;
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
/**
 * Critério de aceitação da continuidade: único, ≤ 200 mΩ (0,2 Ω — NBR 5419-3:2026, F.4.4),
 * para qualquer medição (decisão do projeto JVM SPDA; o "tipo" só indica a tela/subsistema).
 */
export const CONTINUITY_LIMIT_OHM = PROOF_VERIFICATION_LIMIT_OHM;

export function normLimit(_kind?: ContinuityKind): number {
  return CONTINUITY_LIMIT_OHM;
}

export const KIND_SUBSYSTEM: Record<ContinuityKind, ContinuityTest['subsystem']> = {
  F1: 'descida', F2: 'descida', F3: 'aterramento', F4: 'captacao', F5: 'descida', F6: 'aterramento', EQ: 'equipotencializacao'
};

/** Subsistema (tela) a que cada medição de continuidade pertence. */
export type ContinuityGroup = 'captacao' | 'descida' | 'aterramento' | 'dps';
export const CONTINUITY_GROUP: Record<ContinuityKind, ContinuityGroup> = {
  F4: 'captacao', F1: 'descida', F2: 'descida', F5: 'descida', F3: 'aterramento', F6: 'aterramento', EQ: 'dps'
};
export const CONTINUITY_GROUP_INFO: Record<ContinuityGroup, { code: string; label: string }> = {
  captacao: { code: 'CAP', label: 'Captação' },
  descida: { code: 'DES', label: 'Descida' },
  aterramento: { code: 'ATE', label: 'Aterramento' },
  dps: { code: 'DPS', label: 'DPS e equipotencialização' }
};
export function continuityGroupOf(t: ContinuityTest): ContinuityGroup {
  return CONTINUITY_GROUP[kindOf(t)];
}

/**
 * Código de identificação da medição: subsistema + sequência na edificação (ex.: CAP-C01, DES-C03).
 * A sequência segue a ordem de lançamento; medição ainda não gravada recebe o próximo número.
 */
export function continuityCode(all: ContinuityTest[], t: ContinuityTest): string {
  const g = continuityGroupOf(t);
  const same = all.filter(x => continuityGroupOf(x) === g && (x.buildingId || '') === (t.buildingId || ''));
  const i = same.findIndex(x => x.id === t.id);
  const n = i >= 0 ? i + 1 : same.length + 1;
  return `${CONTINUITY_GROUP_INFO[g].code}-C${String(n).padStart(2, '0')}`;
}

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
export function effectiveLimit(_t?: ContinuityTest): number {
  return CONTINUITY_LIMIT_OHM;
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
 * Queda de potencial (ABNT NBR 15749) — três leituras com o eletrodo de potencial P:
 * S1 = (62 − passo) %, S = 62 % (nominal, resistência adotada) e S2 = (62 + passo) % da distância E → C.
 * Passo configurável por medição: 10 % (52/62/72 %, padrão) ou 5 % (57/62/67 %).
 * As leituras ficam em r52 (S1), r62 (S) e r72 (S2), qualquer que seja o passo.
 */
export type FopStep = 5 | 10;
export function fopStep(t: Pick<GroundingTest, 'probeStepPct'>): FopStep {
  return t.probeStepPct === 5 ? 5 : 10;
}
export function fopPcts(step: FopStep = 10): [number, number, number] {
  return [62 - step, 62, 62 + step];
}

/** Posições do eletrodo de potencial (m) para a distância E → C e o passo escolhido. null sem distância válida. */
export function probeDistances(distanceM?: number | null, step: FopStep = 10): { pcts: [number, number, number]; m: [number, number, number] } | null {
  if (!isNum(distanceM) || distanceM <= 0) return null;
  const pcts = fopPcts(step);
  const r = (p: number) => Math.round(distanceM * p) / 100;
  return { pcts, m: [r(pcts[0]), r(pcts[1]), r(pcts[2])] };
}

/** Distância em metros no formato brasileiro (até 2 casas): 10,4 m */
export function fmtMeters(v?: number | null): string {
  return isNum(v) ? `${v.toLocaleString('pt-BR', { maximumFractionDigits: 2 })} m` : '—';
}

/** Patamar pelas leituras S1 e S2 dentro de ±tolerância de S (62 %). null = leituras insuficientes. */
export function fallOfPotentialPlateau(t: Pick<GroundingTest, 'r52' | 'r62' | 'r72'>, tolerance = FALL_OF_POTENTIAL_TOLERANCE): { ok: boolean; maxDeviation: number } | null {
  if (!isNum(t.r52) || !isNum(t.r62) || !isNum(t.r72) || t.r62 <= 0) return null;
  const maxDeviation = Math.max(Math.abs(t.r52 - t.r62), Math.abs(t.r72 - t.r62)) / t.r62;
  return { ok: maxDeviation <= tolerance + 1e-9, maxDeviation };
}

// ------------------------------------------------- queda de potencial em 3 pontos (NBR 15749)
export const FOP_NAMES = ['S1', 'S', 'S2'] as const;
export type FopSlot = 0 | 1 | 2;

/** As três leituras (S1, S, S2) com as posições do passo escolhido. */
export function fop3Points(t: Pick<GroundingTest, 'r52' | 'r62' | 'r72' | 'probeStepPct'>): Array<{ slot: FopSlot; pct: number; r: number | null }> {
  const pcts = fopPcts(fopStep(t));
  return [
    { slot: 0, pct: pcts[0], r: isNum(t.r52) ? t.r52 : null },
    { slot: 1, pct: pcts[1], r: isNum(t.r62) ? t.r62 : null },
    { slot: 2, pct: pcts[2], r: isNum(t.r72) ? t.r72 : null }
  ];
}

/**
 * Patamar a partir dos três pontos: leituras dentro de ±tolerância da leitura a 62 %.
 * Patamar caracterizado só com os 3 pontos na faixa. Sem leitura a 62 % → null.
 */
export function fop3Plateau(t: Pick<GroundingTest, 'r52' | 'r62' | 'r72' | 'probeStepPct'>, tolerance = FALL_OF_POTENTIAL_TOLERANCE):
  { ok: boolean; count: number; r62: number; maxDeviation: number; inBand: [boolean | null, boolean | null, boolean | null] } | null {
  if (!isNum(t.r62) || t.r62 <= 0) return null;
  const r62 = t.r62;
  const dev = (r: number) => Math.abs(r - r62) / r62;
  const pts = fop3Points(t);
  const inBand = pts.map(p => (p.r === null ? null : dev(p.r) <= tolerance + 1e-9)) as [boolean | null, boolean | null, boolean | null];
  const measured = pts.filter((p): p is { slot: FopSlot; pct: number; r: number } => p.r !== null);
  const count = measured.filter(p => inBand[p.slot]).length;
  return { ok: count === 3, count, r62, maxDeviation: Math.max(...measured.map(p => dev(p.r))), inBand };
}

/**
 * Curva padrão da queda de potencial (referência visual): eletrodo hemisférico de raio a = 2 % de D,
 * corrente entrando em E (0 %) e saindo em C (100 %). Resistência aparente com P a x (fração de D):
 *   R(x) ∝ 1/a − 1/(1−a) − 1/x + 1/(1−x)
 * escalada para passar por R62 em 62 %. Sobe junto de E e de C e é praticamente plana no meio (patamar).
 * Retorna pontos de a até 1−a e o trecho em que a curva fica dentro de ±tolerância de R62.
 */
export const FOP_STD_A = 0.02;
function fopShape(x: number, a = FOP_STD_A): number {
  return 1 / a - 1 / (1 - a) - 1 / x + 1 / (1 - x);
}
export function fopStandardCurve(r62: number, samples = 120, tolerance = FALL_OF_POTENTIAL_TOLERANCE):
  { points: Array<{ pct: number; r: number }>; plateau: { fromPct: number; toPct: number } } {
  const a = FOP_STD_A;
  const k = r62 / fopShape(0.62);
  const points: Array<{ pct: number; r: number }> = [];
  for (let i = 0; i <= samples; i++) {
    const x = a + ((1 - 2 * a) * i) / samples;
    points.push({ pct: x * 100, r: Math.max(0, k * fopShape(x)) });
  }
  // trecho de patamar: busca fina em torno de 62 %
  const inside = (x: number) => Math.abs(k * fopShape(x) - r62) / r62 <= tolerance;
  let lo = 0.62, hi = 0.62;
  while (lo - 0.001 > a && inside(lo - 0.001)) lo -= 0.001;
  while (hi + 0.001 < 1 - a && inside(hi + 0.001)) hi += 0.001;
  return { points, plateau: { fromPct: Math.round(lo * 1000) / 10, toPct: Math.round(hi * 1000) / 10 } };
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

/**
 * Copia as respostas do checklist (C/NC/NA) de uma edificação para outra, nos itens indicados.
 * Substitui as respostas da edificação de destino nesses itens; itens sem resposta na origem ficam em branco.
 * Observações e fotos só vêm junto quando pedido.
 */
export function copyChecklistAnswers(
  checklist: ChecklistAnswer[], itemIds: string[], fromBuildingId: string, toBuildingId: string,
  opts: { obs?: boolean; photos?: boolean } = {}
): ChecklistAnswer[] {
  const ids = new Set(itemIds);
  const kept = checklist.filter(a => !(ids.has(a.itemId) && a.buildingId === toBuildingId));
  const copied = checklist
    .filter(a => ids.has(a.itemId) && a.buildingId === fromBuildingId && a.status)
    .map(a => {
      const c: ChecklistAnswer = { itemId: a.itemId, buildingId: toBuildingId, status: a.status };
      if (opts.obs && a.obs) c.obs = a.obs;
      if (opts.photos && a.photos?.length) c.photos = [...a.photos];
      return c;
    });
  return [...kept, ...copied];
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
  /** registro = não conformidade registrada com foto/comentário após os ensaios */
  source: 'inspecao' | 'continuidade' | 'registro';
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

export type WithBuildings = Pick<SpdaInspection, 'checklist' | 'continuity'> & { buildings?: SpdaBuilding[]; findings?: SpdaFinding[] };

const ALL_SUBSYSTEMS: SpdaSubsystem[] = ['documentacao', 'captacao', 'descida', 'aterramento', 'equipotencializacao'] as SpdaSubsystem[];

/** Melhoria registrada (foto + comentário) — recomendação, não altera o parecer. */
export interface Improvement {
  code: string; subsystem: SpdaSubsystem; comment: string; photos: number; buildingId?: string; buildingName?: string;
}

/** Melhorias registradas nas telas dos subsistemas (opcionalmente só de uma edificação / subsistemas). */
export function listImprovements(insp: WithBuildings, buildingId?: string, subsystems: SpdaSubsystem[] = ALL_SUBSYSTEMS): Improvement[] {
  return findingsOf(insp, buildingId, subsystems).filter(x => x.f.kind === 'melhoria').map(({ f, code }) => ({
    code, subsystem: f.subsystem, comment: f.comment.trim(), photos: f.photos.length,
    buildingId: f.buildingId, buildingName: buildingNameOf(insp, f.buildingId)
  }));
}

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
        reference: 'NBR 5419-3:2026, Anexo F (≤ 0,2 Ω, F.4.4)',
        obs: t.obs, buildingId: t.buildingId, buildingName: buildingNameOf(insp, t.buildingId)
      });
    }
  });
  // não conformidades registradas com foto e comentário nas telas dos subsistemas
  findingsOf(insp, undefined, ALL_SUBSYSTEMS).filter(x => x.f.kind === 'nc').forEach(({ f, code }) => {
    out.push({
      source: 'registro', subsystem: f.subsystem,
      description: `${code} — ${f.comment.trim() || 'Não conformidade registrada (ver fotos)'}`,
      reference: `registro fotográfico${f.photos.length ? `, ${f.photos.length} foto(s)` : ''}`,
      buildingId: f.buildingId, buildingName: buildingNameOf(insp, f.buildingId)
    });
  });
  return out;
}

// ------------------------------------------------------------------ conclusão
export type Verdict = 'conforme' | 'nao_conforme' | 'incompleto';

export const VERDICT_LABEL: Record<Verdict, string> = {
  conforme: 'SPDA CONFORME',
  nao_conforme: 'SPDA NÃO CONFORME',
  incompleto: 'SEM ITENS AVALIADOS'
};

/** Item do checklist avaliado (C, NC ou NA). Itens em branco não entram no laudo nem no parecer. */
export const isEvaluated = (a: Pick<ChecklistAnswer, 'status'>) => a.status === 'C' || a.status === 'NC' || a.status === 'NA';
/** Item ainda existe no checklist (itens retirados do modelo são ignorados nas inspeções antigas). */
const TEMPLATE_IDS = new Set(CHECKLIST_TEMPLATE.map(i => i.id));
export const isKnownItem = (a: Pick<ChecklistAnswer, 'itemId'>) => TEMPLATE_IDS.has(a.itemId);
/** Ensaio de continuidade realizado (com valor medido). Ensaios sem valor não entram no laudo nem no parecer. */
export const isMeasured = (t: ContinuityTest) => continuityStatus(t) !== 'pendente';

/**
 * Parecer com base apenas no que foi inspecionado e ensaiado: não conforme se houver qualquer NC (checklist ou
 * continuidade, em qualquer edificação); conforme se houver ao menos um item avaliado ou um ensaio medido e
 * nenhuma NC; "sem itens avaliados" se nada foi inspecionado nem medido. Itens em branco e ensaios sem valor são
 * ignorados. A resistência de aterramento não entra (7.1.4).
 */
export function inspectionVerdict(insp: WithBuildings): Verdict {
  if (listNonConformities(insp).length > 0) return 'nao_conforme';
  const evaluated = insp.checklist.some(a => isKnownItem(a) && isEvaluated(a)) || insp.continuity.some(isMeasured) || (insp.findings || []).length > 0;
  return evaluated ? 'conforme' : 'incompleto';
}

/** Parecer de uma edificação (só os itens e ensaios dela), com o mesmo critério. */
export function buildingVerdict(insp: WithBuildings, buildingId: string): Verdict {
  const part: WithBuildings = {
    checklist: insp.checklist.filter(a => a.buildingId === buildingId),
    continuity: insp.continuity.filter(t => t.buildingId === buildingId),
    buildings: (insp.buildings || []).filter(b => b.id === buildingId),
    findings: (insp.findings || []).filter(f => f.buildingId === buildingId)
  };
  if (listNonConformities(part).length > 0) return 'nao_conforme';
  return part.checklist.some(a => isKnownItem(a) && isEvaluated(a)) || part.continuity.some(isMeasured) || (part.findings || []).length ? 'conforme' : 'incompleto';
}

/**
 * Respostas gravadas com numeração antiga do checklist passam, etapa por etapa, para a numeração atual;
 * respostas de itens retirados são descartadas.
 */
export function migrateChecklistCodes(insp: SpdaInspection): SpdaInspection {
  const from = insp.checklistVersion || 1;
  if (from >= CHECKLIST_VERSION) return insp;
  let checklist = insp.checklist || [];
  for (let v = from; v < CHECKLIST_VERSION; v++) {
    const map = CHECKLIST_MIGRATIONS[v - 1];
    checklist = checklist.flatMap(a => {
      if (!(a.itemId in map)) return [a];
      const to = map[a.itemId];
      return to ? [{ ...a, itemId: to }] : [];
    });
  }
  return { ...insp, checklist, checklistVersion: CHECKLIST_VERSION };
}

/**
 * Registros da versão anterior (sem vínculo com edificação): cria a lista de
 * edificações se faltar e liga as respostas por edificação e os ensaios sem
 * edificação à primeira edificação. Não altera o que já está vinculado.
 */
export function migrateInspection(input: SpdaInspection): SpdaInspection {
  return withDefaultTestCurrent(migrateBuildingLinks(migrateChecklistCodes(input)));
}

// ------------------------------------------------- não conformidades e melhorias
export const FINDING_SUBSYSTEM_CODE: Partial<Record<SpdaSubsystem, string>> = {
  captacao: 'CAP', descida: 'DES', aterramento: 'ATE', equipotencializacao: 'DPS'
};

/**
 * Registros de não conformidade/melhoria de uma edificação e subsistema(s), com o código de cada um
 * (ex.: CAP-NC-01, CAP-M-01 — numerados por edificação, subsistema e tipo, na ordem de criação).
 */
export function findingsOf(insp: { findings?: SpdaFinding[] }, buildingId: string | undefined, subsystems: SpdaSubsystem[]): Array<{ f: SpdaFinding; code: string }> {
  const list = (insp.findings || []).filter(f => subsystems.includes(f.subsystem) && (!buildingId || !f.buildingId || f.buildingId === buildingId));
  const count: Record<string, number> = {};
  return list.map(f => {
    const k = `${f.buildingId || ''}_${f.subsystem}_${f.kind}`;
    count[k] = (count[k] || 0) + 1;
    return { f, code: `${FINDING_SUBSYSTEM_CODE[f.subsystem] || 'SPDA'}-${f.kind === 'nc' ? 'NC' : 'M'}-${String(count[k]).padStart(2, '0')}` };
  });
}

/** Ensaios de continuidade sem corrente informada recebem a corrente padrão (1 A). */
export function withDefaultTestCurrent(insp: SpdaInspection): SpdaInspection {
  if (!insp.continuity?.some(t => t.currentA == null)) return insp;
  return { ...insp, continuity: insp.continuity.map(t => (t.currentA == null ? { ...t, currentA: DEFAULT_TEST_CURRENT_A } : t)) };
}

function migrateBuildingLinks(insp: SpdaInspection): SpdaInspection {
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

export const VALIDITY_OPTIONS = [12, 24, 36] as const;
/** Validade automática do laudo (sem escolha no relatório). */
export const DEFAULT_VALIDITY_MONTHS = 12;

/** Validade do laudo em meses: a escolhida no relatório (12, 24 ou 36) ou a automática de 12 meses. */
export function validityMonths(insp: Pick<SpdaInspection, 'criticalStructure' | 'validityMonths'>): number {
  return insp.validityMonths && (VALIDITY_OPTIONS as readonly number[]).includes(insp.validityMonths) ? insp.validityMonths : DEFAULT_VALIDITY_MONTHS;
}

/** Texto da validade: "36 meses (3 anos)". */
export function validityText(insp: Pick<SpdaInspection, 'criticalStructure' | 'validityMonths'>): string {
  const m = validityMonths(insp);
  const y = m / 12;
  return `${m} meses (${y} ${y === 1 ? 'ano' : 'anos'})`;
}

export function addMonths(isoDate: string, months: number): string {
  const [y, m, d] = isoDate.split('-').map(Number);
  if (!y || !m || !d) return '';
  const total = m - 1 + months;
  const ty = y + Math.floor(total / 12), tm = ((total % 12) + 12) % 12;
  const last = new Date(Date.UTC(ty, tm + 1, 0)).getUTCDate();
  return new Date(Date.UTC(ty, tm, Math.min(d, last))).toISOString().slice(0, 10);
}

/** Próxima inspeção = data da inspeção + validade do laudo. */
export function nextInspectionDate(insp: Pick<SpdaInspection, 'inspectionDate' | 'criticalStructure' | 'validityMonths'>): string {
  return insp.inspectionDate ? addMonths(insp.inspectionDate, validityMonths(insp)) : '';
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
export function suggestRecommendations(insp: WithBuildings & Pick<SpdaInspection, 'criticalStructure' | 'validityMonths'>): string {
  const ncs = listNonConformities(insp);
  const lines: string[] = [];
  if (ncs.some(n => n.source === 'inspecao')) {
    lines.push('Corrigir os itens não conformes da inspeção visual indicados neste laudo e registrar as correções com fotos. Prazo de manutenção de cada item conforme a criticidade, de "imediato" a "manutenção preventiva" (NBR 5419-3:2026, 7.4.2).');
  }
  if (ncs.some(n => n.source === 'continuidade')) {
    lines.push('Nos trechos com continuidade acima do critério do Anexo F, localizar e corrigir conexões/emendas (limpeza, reaperto, substituição de conectores ou do condutor) e repetir o ensaio com instrumento a 4 fios (F.1.3).');
  }
  const reg = ncs.filter(n => n.source === 'registro');
  if (reg.length) {
    lines.push(`Corrigir as não conformidades registradas com foto: ${reg.map(n => `${n.buildingName ? `${n.buildingName} — ` : ''}${n.description}`).join('; ')}.`);
  }
  const imps = listImprovements(insp);
  if (imps.length) {
    lines.push(`Melhorias recomendadas: ${imps.map(m => `${m.buildingName ? `${m.buildingName} — ` : ''}${m.code}${m.comment ? ` — ${m.comment}` : ''}`).join('; ')}.`);
  }
  if (ncs.length) lines.push('O responsável pela estrutura deve assegurar a adequação de todas as não conformidades apresentadas (NBR 5419-3:2026, 7.4.3).');
  lines.push(`Realizar nova inspeção periódica em até ${validityText(insp)} (validade do laudo; 7.3.2 f), ou antes, após alteração na estrutura ou no SPDA e quando houver suspeita de descarga atmosférica (7.3.2 c) a e).`);
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
  misto: { label: 'Misto', hint: 'Integra partes do estrutural, do natural e do externo (ex.: captação externa + descidas pelas armaduras) (5.1.3)' },
  isolado: { label: 'Externo isolado', hint: 'Captação e descidas afastadas da estrutura pela distância de segurança (5.4.2, 6.3)' },
  inexistente: { label: 'Inexistente', hint: 'Edificação sem SPDA instalado' }
};

/** Texto do tipo de SPDA para telas e laudos. */
export function spdaKindText(b: Pick<SpdaBuilding, 'spdaKind'>): string {
  return SPDA_KIND_LABEL[b.spdaKind]?.label || '—';
}

// ---------------------------------------------------------------- sistemas de captação existentes
export const AIR_TERMINATION_LABEL: Record<AirTerminationType, { label: string; hint: string }> = {
  franklin: { label: 'Mastro com captor Franklin', hint: 'Haste/mastro elevado com captor de pontas (método do ângulo de proteção)' },
  mini_captores: { label: 'Minicaptores', hint: 'Pequenos captores verticais (≈ 0,3 a 0,6 m) ao longo da malha e das bordas' },
  faraday: { label: 'Malha de Faraday', hint: 'Condutores em malha sobre a cobertura (método das malhas / gaiola)' },
  telhas_metalicas: { label: 'Telhas metálicas', hint: 'Cobertura metálica como captor natural (espessura e continuidade conforme 5.3.2)' },
  anel_superior: { label: 'Anel superior', hint: 'Condutor em anel no topo/platibanda, em cobre ou alumínio, interligando as descidas' },
  outro: { label: 'Outro', hint: 'Descreva o sistema existente' }
};
export const AIR_TERMINATION_ORDER: AirTerminationType[] = ['franklin', 'mini_captores', 'faraday', 'telhas_metalicas', 'anel_superior', 'outro'];

/** Nome de um sistema de captação (com material do anel / descrição de "outro"). */
export function airTerminationName(b: Pick<SpdaBuilding, 'airTermination'>, t: AirTerminationType): string {
  const a = b.airTermination;
  if (t === 'anel_superior') return `Anel superior${a?.ringMaterial === 'cobre' ? ' em cobre' : a?.ringMaterial === 'aluminio' ? ' em alumínio' : ''}`;
  if (t === 'outro') return a?.otherText?.trim() || 'Outro';
  return AIR_TERMINATION_LABEL[t].label;
}

/** Texto dos sistemas de captação selecionados (ordem fixa), ou "" se nenhum. */
export function airTerminationText(b: Pick<SpdaBuilding, 'airTermination'>): string {
  const sel = b.airTermination?.types || [];
  return AIR_TERMINATION_ORDER.filter(t => sel.includes(t)).map(t => airTerminationName(b, t)).join('; ');
}

// ---------------------------------------------------------------- tipos de descida existentes
export const DOWN_CONDUCTOR_LABEL: Record<DownConductorType, { label: string; hint: string }> = {
  externa: { label: 'Descida externa', hint: 'Condutor aparente na fachada, em cobre ou alumínio, fixado até a caixa de inspeção' },
  pilar_estrutural: { label: 'Descida interna ao pilar estrutural', hint: 'Armaduras de aço do pilar de concreto como descida natural (5.4.5)' },
  pilar_metalico: { label: 'Descida natural em pilar de ferro', hint: 'Pilar/estrutura metálica contínua usada como descida natural' }
};
export const DOWN_CONDUCTOR_ORDER: DownConductorType[] = ['externa', 'pilar_estrutural', 'pilar_metalico'];

/** Nome de um tipo de descida (com o material da descida externa). */
export function downConductorName(b: Pick<SpdaBuilding, 'downConductor'>, t: DownConductorType): string {
  const m = b.downConductor?.material;
  if (t === 'externa') return `Descida externa${m === 'cobre' ? ' em cobre' : m === 'aluminio' ? ' em alumínio' : ''}`;
  return DOWN_CONDUCTOR_LABEL[t].label;
}

/** Texto dos tipos de descida selecionados (ordem fixa), ou "" se nenhum. */
export function downConductorText(b: Pick<SpdaBuilding, 'downConductor'>): string {
  const sel = b.downConductor?.types || [];
  return DOWN_CONDUCTOR_ORDER.filter(t => sel.includes(t)).map(t => downConductorName(b, t)).join('; ');
}

// ---------------------------------------------------------------- tipos de aterramento existentes
export const GROUNDING_SYSTEM_LABEL: Record<GroundingSystemType, { label: string; hint: string }> = {
  anel_cobre: { label: 'Anel de aterramento em cobre com hastes', hint: 'Condutor de cobre nu enterrado em anel, afastado da edificação, com hastes de aterramento (arranjo B, 5.5.2.2)' },
  anel_sem_hastes: { label: 'Anel de aterramento em cobre', hint: 'Só o condutor de cobre nu enterrado em anel, afastado da edificação, sem hastes (arranjo B, 5.5.2.2)' },
  hastes_linha: { label: 'Hastes de aterramento em linha', hint: 'Hastes cravadas em linha reta, interligadas por condutor, sem fechar o anel (arranjo A, 5.5.2.1)' },
  estrutural: { label: 'Malha de aterramento estrutural', hint: 'Na própria ferragem das fundações da edificação (eletrodo natural, 5.5.3)' }
};
export const GROUNDING_SYSTEM_ORDER: GroundingSystemType[] = ['anel_cobre', 'anel_sem_hastes', 'hastes_linha', 'estrutural'];

const fmtM = (x?: number | null) => (x != null && Number.isFinite(x) ? `${String(x).replace('.', ',')} m` : '');

/** Nome de um tipo de aterramento (com afastamento/profundidade do anel, se informados). */
export function groundingSystemName(b: Pick<SpdaBuilding, 'groundingSystem'>, t: GroundingSystemType): string {
  const g = b.groundingSystem;
  if (t === 'anel_cobre' || t === 'anel_sem_hastes') {
    const extra = [g?.ringDistanceM != null ? `afastado ${fmtM(g.ringDistanceM)} da edificação` : 'afastado da edificação', g?.ringDepthM != null ? `profundidade ${fmtM(g.ringDepthM)}` : ''].filter(Boolean).join(', ');
    return `${GROUNDING_SYSTEM_LABEL[t].label} (${extra})`;
  }
  if (t === 'hastes_linha') return 'Hastes de aterramento em linha (sem anel)';
  return 'Malha de aterramento estrutural (ferragem da edificação)';
}

/** Texto dos tipos de aterramento selecionados (ordem fixa), ou "" se nenhum. */
export function groundingSystemText(b: Pick<SpdaBuilding, 'groundingSystem'>): string {
  const sel = b.groundingSystem?.types || [];
  return GROUNDING_SYSTEM_ORDER.filter(t => sel.includes(t)).map(t => groundingSystemName(b, t)).join('; ');
}

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

/** Mapa de satélite incorporável (pré-visualização na tela, precisa de internet). */
export function mapEmbedUrl(lat?: number | null, lng?: number | null): string {
  return validCoordinates(lat, lng) ? `https://maps.google.com/maps?q=${lat},${lng}&t=k&z=19&output=embed` : '';
}

/**
 * Lê as coordenadas de um link de mapa colado (Google Maps: "@lat,lng", "!3dlat!4dlng",
 * "?q=lat,lng", "ll=", "center=", "query="), ou de um texto "lat, lng". Link curto (maps.app.goo.gl) não traz
 * coordenadas → null.
 */
export function parseMapsLink(text?: string | null): { latitude: number; longitude: number } | null {
  if (!text) return null;
  const s = decodeURIComponent(text.trim());
  const num = '(-?\\d{1,3}(?:\\.\\d+)?)';
  const patterns = [
    new RegExp(`!3d${num}!4d${num}`),
    new RegExp(`@${num},${num}`),
    new RegExp(`[?&](?:q|query|ll|center|destination)=(?:loc:)?${num},\\s*${num}`),
    new RegExp(`^${num}\\s*[,;]\\s*${num}$`)
  ];
  for (const re of patterns) {
    const m = s.match(re);
    if (m) {
      const latitude = Number(m[1]);
      const longitude = Number(m[2]);
      if (validCoordinates(latitude, longitude)) return { latitude: Number(latitude.toFixed(6)), longitude: Number(longitude.toFixed(6)) };
    }
  }
  return null;
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
): { answered: number; total: number; nc: number; tests: ContinuityTest[]; testsNc: number; ncs: NonConformity[]; improvements: Improvement[]; registered: number; verdict: Verdict } {
  const items = CHECKLIST_TEMPLATE.filter(i => subsystems.includes(i.subsystem));
  const bidOf = (sub: SpdaSubsystem) => (isPerBuilding(sub) ? buildingId : undefined);
  const answers = items.map(i => answerOf(insp.checklist, i.id, bidOf(i.subsystem)));
  // só ensaios realizados (com valor medido)
  const tests = insp.continuity.filter(t => kinds.includes(kindOf(t)) && (!buildingId || t.buildingId === buildingId) && isMeasured(t));
  // NCs só deste subsistema (e edificação): checklist filtrado + ensaios do subsistema
  const itemIds = new Set(items.map(i => i.id));
  const ncs = listNonConformities({
    buildings: insp.buildings,
    checklist: insp.checklist.filter(a => itemIds.has(a.itemId) && (!buildingId || !a.buildingId || a.buildingId === buildingId)),
    continuity: tests,
    findings: (insp.findings || []).filter(f => subsystems.includes(f.subsystem) && (!buildingId || !f.buildingId || f.buildingId === buildingId))
  });
  const registered = ncs.filter(n => n.source === 'registro').length;
  const improvements = listImprovements(insp, buildingId, subsystems);
  const nc = answers.filter(a => a.status === 'NC').length;
  const testsNc = tests.filter(t => continuityStatus(t) === 'nao_conforme').length;
  const answered = answers.filter(a => a.status !== '').length;
  const verdict: Verdict = nc + testsNc + registered > 0 ? 'nao_conforme' : answered > 0 || tests.length > 0 || improvements.length > 0 ? 'conforme' : 'incompleto';
  return { answered, total: items.length, nc, tests, testsNc, ncs, improvements, registered, verdict };
}
