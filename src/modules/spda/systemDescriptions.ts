/**
 * Descrição técnica dos subsistemas evidenciados na inspeção (relatório técnico, capítulo 5):
 * texto por tipo selecionado em Captação, Descida e Aterramento, com os dados informados
 * (material, afastamento, profundidade, observações), e equipotencialização/DPS a partir dos ensaios.
 */
import {
  AIR_TERMINATION_LABEL, AIR_TERMINATION_ORDER, DOWN_CONDUCTOR_LABEL, DOWN_CONDUCTOR_ORDER,
  GROUNDING_SYSTEM_LABEL, GROUNDING_SYSTEM_ORDER, levelParams, airTerminationName, downConductorName, groundingSystemName, kindOf
} from './rules';
import type { SpdaBuilding, SpdaInspection, SpdaPhoto } from './types';

export type SubsystemKey = 'captacao' | 'descida' | 'aterramento' | 'dps';

export interface SystemItem {
  /** chave do tipo (para a ilustração) */
  type: string;
  name: string;
  text: string;
  photos: SpdaPhoto[];
}

export interface SubsystemDescription {
  key: SubsystemKey;
  title: string;
  /** texto de abertura (nível de proteção, observações gerais) */
  intro: string;
  items: SystemItem[];
  /** nada informado na inspeção para este subsistema */
  empty: boolean;
}

const m = (x?: number | null) => (typeof x === 'number' && Number.isFinite(x) ? `${String(x).replace('.', ',')} m` : '');

function captacao(b: SpdaBuilding): SubsystemDescription {
  const a = b.airTermination;
  const lp = levelParams(b.protectionLevel);
  const np = b.protectionLevel || 'a definir';
  const sphere = lp ? `${lp.sphereRadiusM} m` : 'a definir conforme o NP';
  const mesh = lp ? `${lp.meshM} m` : 'a definir conforme o NP';
  const sel = AIR_TERMINATION_ORDER.filter(t => a?.types?.includes(t));
  const TEXT: Record<string, string> = {
    franklin: `Captor tipo Franklin (haste de pontas) instalado em mastro elevado acima da cobertura, dimensionado pelo método do ângulo de proteção (NBR 5419-3:2026, 5.3 e Tabela 2). O volume protegido é o cone definido pelo ângulo da Tabela 2 em função da altura do captor e do nível de proteção ${np}; o mastro deve estar interligado ao restante da captação e às descidas.`,
    mini_captores: `Minicaptores (captores verticais curtos, da ordem de 0,3 m a 0,6 m) distribuídos ao longo das bordas, cumeeiras e pontos salientes da cobertura e interligados pelos condutores horizontais, com espaçamento compatível com o método da esfera rolante (raio de ${sphere} para o nível ${np}).`,
    faraday: `Captação em malha (método das malhas / gaiola de Faraday): condutores horizontais sobre a cobertura formando quadrículas de, no máximo, ${mesh} para o nível de proteção ${np} (NBR 5419-3:2026, Tabela 2), com condutores nas bordas e cumeeiras e fixação conforme 5.6.2.`,
    telhas_metalicas: 'Cobertura em telhas metálicas utilizada como captor natural (NBR 5419-3:2026, 5.3.2.16): a chapa deve ter espessura mínima conforme a Tabela 3 e continuidade elétrica garantida entre as peças (solda, rebites ou parafusos), interligada às descidas.',
    anel_superior: `Condutor em anel no topo da edificação (platibanda / perímetro da cobertura)${a?.ringMaterial === 'cobre' ? ', em cobre' : a?.ringMaterial === 'aluminio' ? ', em alumínio' : ''}, interligando os captores e as descidas e equalizando o potencial no nível da cobertura. Material e seção mínima conforme a Tabela 7 da NBR 5419-3:2026${a?.ringMaterial === 'cobre' ? ' (cobre: 35 mm²)' : a?.ringMaterial === 'aluminio' ? ' (alumínio: 70 mm²)' : ''}.`,
    outro: 'Sistema de captação descrito pelo inspetor (ver registro fotográfico).'
  };
  return {
    key: 'captacao', title: 'Subsistema de captação', empty: sel.length === 0,
    intro: [
      sel.length ? `Foram evidenciados na inspeção ${sel.length} sistema(s) de captação: ${sel.map(t => airTerminationName(b, t)).join('; ')}.` : 'O tipo de captação não foi informado na inspeção.',
      a?.notes?.trim() ? `Observações do inspetor: ${a.notes.trim()}.`.replace(/\.\.$/, '.') : ''
    ].filter(Boolean).join(' '),
    items: sel.map(t => ({ type: t, name: t === 'outro' ? airTerminationName(b, t) : AIR_TERMINATION_LABEL[t].label + (t === 'anel_superior' && a?.ringMaterial ? (a.ringMaterial === 'cobre' ? ' em cobre' : ' em alumínio') : ''), text: TEXT[t], photos: a?.photos?.[t] || [] }))
  };
}

function descida(b: SpdaBuilding): SubsystemDescription {
  const d = b.downConductor;
  const lp = levelParams(b.protectionLevel);
  const np = b.protectionLevel || 'a definir';
  const spacing = lp ? `${lp.downSpacingM} m` : 'a definir conforme o NP';
  const sel = DOWN_CONDUCTOR_ORDER.filter(t => d?.types?.includes(t));
  const mat = d?.material === 'cobre' ? 'cobre' : d?.material === 'aluminio' ? 'alumínio' : '';
  const TEXT: Record<string, string> = {
    externa: `Descidas externas não naturais, em condutor ${mat || 'metálico'} aparente fixado na fachada, como extensão direta da captação até o conector de ensaio e a caixa de inspeção próxima ao solo (NBR 5419-3:2026, 5.4 e 5.4.6). Seção mínima conforme a Tabela 7${mat === 'cobre' ? ' (cobre: 35 mm²)' : mat === 'alumínio' ? ' (alumínio: 70 mm²)' : ''}; fixação a cada 1,0 m na horizontal e 1,5 m na vertical (5.6.2.2); espaçamento médio entre descidas de até ${spacing} para o nível ${np} (Tabela 5).`,
    pilar_estrutural: 'Descidas naturais pelas armaduras de aço dos pilares de concreto armado (NBR 5419-3:2026, 5.4.5), interligadas à captação no topo e ao aterramento nas fundações. A continuidade elétrica das armaduras deve ser comprovada por ensaio (Anexo F), e as ligações externas são feitas por pontos de conexão (terminais) embutidos no pilar.',
    pilar_metalico: 'Descidas naturais pelos pilares da estrutura metálica (ferro/aço), eletricamente contínuos da cobertura até a fundação (NBR 5419-3:2026, 5.4.5), com as ligações à captação e ao aterramento por conexões aparafusadas ou soldadas e continuidade verificada por ensaio.'
  };
  const count = typeof b.downConductorsCount === 'number' ? ` A edificação possui ${b.downConductorsCount} descida(s).` : '';
  return {
    key: 'descida', title: 'Subsistema de descida', empty: sel.length === 0,
    intro: [
      sel.length ? `Foram evidenciados na inspeção ${sel.length} tipo(s) de descida: ${sel.map(t => downConductorName(b, t)).join('; ')}.${count}` : `O tipo de descida não foi informado na inspeção.${count}`,
      d?.notes?.trim() ? `Observações do inspetor: ${d.notes.trim()}.`.replace(/\.\.$/, '.') : ''
    ].filter(Boolean).join(' '),
    items: sel.map(t => ({ type: t, name: t === 'externa' ? downConductorName(b, t) : DOWN_CONDUCTOR_LABEL[t].label, text: TEXT[t], photos: d?.photos?.[t] || [] }))
  };
}

function aterramento(b: SpdaBuilding): SubsystemDescription {
  const g = b.groundingSystem;
  const sel = GROUNDING_SYSTEM_ORDER.filter(t => g?.types?.includes(t));
  const pos = [g?.ringDistanceM != null ? `afastado ${m(g.ringDistanceM)} das paredes externas` : 'afastado das paredes externas', g?.ringDepthM != null ? `enterrado a ${m(g.ringDepthM)} de profundidade` : ''].filter(Boolean).join(' e ');
  const TEXT: Record<string, string> = {
    anel_cobre: `Eletrodo de aterramento em anel (arranjo B — NBR 5419-3:2026, 5.5.2.2): condutor de cobre nu ${pos}, contornando a edificação e complementado por hastes de aterramento cravadas ao longo do anel, em geral junto às caixas de inspeção das descidas. A norma recomenda profundidade mínima de 0,5 m e afastamento de cerca de 1 m das paredes (5.5.3).`,
    anel_sem_hastes: `Eletrodo de aterramento em anel (arranjo B — NBR 5419-3:2026, 5.5.2.2): condutor de cobre nu ${pos}, contornando a edificação, sem hastes complementares. A norma recomenda profundidade mínima de 0,5 m e afastamento de cerca de 1 m das paredes (5.5.3).`,
    hastes_linha: 'Eletrodos de aterramento formados por hastes cravadas em linha reta e interligadas por condutor, sem formar anel fechado em torno da edificação (arranjo A — NBR 5419-3:2026, 5.5.2.1). Cada descida deve estar ligada a pelo menos um eletrodo, com o comprimento mínimo da Figura 3 da norma para o nível de proteção.',
    estrutural: 'Aterramento natural pelas armaduras de aço das fundações (sapatas, blocos, estacas e vigas baldrame) interligadas, formando a malha estrutural da própria edificação (NBR 5419-3:2026, 5.5.3 e 5.5.4). A continuidade das armaduras deve ser comprovada por ensaio, e as ligações das descidas e da BEP são feitas por terminais embutidos.'
  };
  return {
    key: 'aterramento', title: 'Subsistema de aterramento', empty: sel.length === 0,
    intro: [
      sel.length ? `Foram evidenciados na inspeção ${sel.length} tipo(s) de aterramento: ${sel.map(t => groundingSystemName(b, t)).join('; ')}.` : 'O tipo de aterramento não foi informado na inspeção.',
      g?.notes?.trim() ? `Observações do inspetor: ${g.notes.trim()}.`.replace(/\.\.$/, '.') : ''
    ].filter(Boolean).join(' '),
    items: sel.map(t => ({ type: t, name: GROUNDING_SYSTEM_LABEL[t].label, text: TEXT[t], photos: g?.photos?.[t] || [] }))
  };
}

function dps(v: SpdaInspection, b: SpdaBuilding): SubsystemDescription {
  const eq = v.continuity.filter(t => t.buildingId === b.id && kindOf(t) === 'EQ');
  const points = [...new Set(eq.map(t => [t.from, t.to].filter(Boolean).join(' → ')).filter(Boolean))];
  return {
    key: 'dps', title: 'Equipotencialização e DPS', empty: eq.length === 0,
    intro: eq.length
      ? `Foram evidenciadas e ensaiadas ${eq.length} ligação(ões) equipotencial(is) na edificação: ${points.join('; ')}. A barra de equipotencialização principal (BEP) interliga o aterramento do SPDA às massas metálicas, tubulações e ao condutor de proteção da instalação elétrica (NBR 5419-3:2026, 6.2), e os DPS classe I protegem as linhas que entram na estrutura (NBR 5419-4:2026).`
      : 'Não foram registradas ligações equipotenciais ensaiadas para esta edificação.',
    items: []
  };
}

/** Subsistemas evidenciados numa edificação, na ordem do relatório. */
export function describeSubsystems(v: SpdaInspection, b: SpdaBuilding): SubsystemDescription[] {
  return [captacao(b), descida(b), aterramento(b), dps(v, b)];
}
