import { describe, expect, it } from 'vitest';
import { hoursIn, parseNormText, periodMonths, splitHours, topicsFrom } from '../normParser';
import { joinPdfItems } from '../normAnalysis';

// trecho no formato da NR-36 (cursos em itens, conteúdo nas alíneas)
const ITENS = `NR 36 - SEGURANÇA E SAÚDE NO TRABALHO NAS ORGANIZAÇÕES DE ABATE E
PROCESSAMENTO DE CARNES E DERIVADOS
Publicação D.O.U.
Portaria MTE nº 555, de 18 de abril de 2013 19/04/13
Portaria MTE nº 1.065, de 1º de julho de 2024 02/07/24
36.16 Informações e treinamentos em segurança e saúde no trabalho
36.16.1.2 Os trabalhadores devem estar treinados e suficientemente informados sobre:
a) os métodos e procedimentos de trabalho;
b) o uso de EPI e suas limitações; e
c) as ações de emergência.
36.16.4 Deve ser realizado treinamento inicial, antes de o trabalhador iniciar suas funções, com,
no mínimo, quatro horas de duração.
36.16.4.1 Deve ser realizado treinamento periódico anual com carga horária de, no mí nimo, duas
horas.
36.16.5 Os trabalhadores devem receber treinamento eventual nos termos do subitem 1.7.1.2.3
da NR-1.
36.16.6 A elaboração do conteúdo dos treinamentos deve contar com a participação de:
a) membros da CIPA; e
b) integrantes do SESMT.`;

// trecho no formato da NR-20 (cursos no anexo, tabelas de carga e periodicidade)
const ANEXO = `NR 20 - SEGURANÇA E SAÚDE NO TRABALHO COM INFLAMÁVEIS E COMBUSTÍVEIS
Publicação D.O.U.
Portaria MTE nº 60, de 21 de janeiro de 2025 22/01/25
20.12.6 Os trabalhadores que realizaram o curso Básico devem fazer complementação com carga horária de 8 horas.
ANEXO I da NR-20
Curso Periodicidade Carga Horária
Básico Trienal 4 horas
curta duração Curso Básico (4 horas) Curso Básico (6 horas) Curso Básico (8
horas)
Conteúdo programático
a) Curso de Iniciação sobre Inflamáveis e Combustíveis
Carga horária: 3 horas
1. Inflamáveis: características, propriedades, perigos e riscos;
2. Fontes de ignição e seu controle;
b) Curso Básico
I) Conteúdo programático teórico:
1. Controles coletivo e individual para trabalhos com inflamáveis;
2. Proteção contra incêndio com inflamáveis;
II) Conteúdo programático prático:
1. Conhecimentos e utilização dos sistemas de segurança contra incêndio.
ANEXO II da NR-20`;

describe('Leitura da norma sem IA', () => {
  it('lê horas e periodicidade escritas por extenso ou em números', () => {
    expect(hoursIn('com, no mínimo, quatro horas de duração')).toBe(4);
    expect(hoursIn('carga horária mínima de 8 (oito) horas')).toBe(8);
    expect(hoursIn('Carga horária: 40h')).toBe(40);
    expect(hoursIn('sem horas definidas')).toBeNull();
    expect(periodMonths('treinamento periódico anual')).toBe(12);
    expect(periodMonths('a cada dois anos')).toBe(24);
    expect(periodMonths('Trienal')).toBe(36);
  });

  it('divide as horas em 0,5 h e agrupa alíneas quando há mais tópicos que horas', () => {
    expect(splitHours(4, 3)).toEqual([1.5, 1.5, 1]);
    const t = topicsFrom([1, 2, 3, 4, 5].map(i => ({ title: `tópico ${i}`, normItem: 'x' })), 1);
    expect(t.map(x => x.hours)).toEqual([0.5, 0.5]);
    expect(t[0].title).toBe('Tópico 1; tópico 2; tópico 3');
  });

  it('cursos em itens: inicial e periódico com conteúdo das alíneas, eventual como aviso', () => {
    const a = parseNormText(ITENS);
    expect(a.norm).toEqual({ code: 'NR-36', title: 'Segurança e Saúde no Trabalho nas Organizações de Abate e Processamento de Carnes e Derivados', revision: 'Portaria nº 1.065/2024' });
    expect(a.courses.map(c => [c.code, c.kind, c.workloadHours, c.validityMonths])).toEqual([['NR-36', 'inicial', 4, 12], ['NR-36 PERIÓDICO', 'periodico', 2, 12]]);
    expect(a.courses[0].topics.map(t => t.title)).toEqual(['Os métodos e procedimentos de trabalho', 'O uso de EPI e suas limitações', 'As ações de emergência']);
    expect(a.courses[0].topics.reduce((s, t) => s + t.hours, 0)).toBe(4);
    expect(a.warnings.some(w => w.includes('Treinamento eventual (NR-36, item 36.16.5)'))).toBe(true);
    // lista de quem participa da elaboração não vira conteúdo
    expect(a.courses[0].topics.some(t => /CIPA/.test(t.title))).toBe(false);
  });

  it('cursos em anexo: carga, periodicidade da tabela, tópicos teóricos e práticos; complementação como aviso', () => {
    const a = parseNormText(ANEXO);
    expect(a.norm.revision).toBe('Portaria nº 60/2025');
    expect(a.courses.map(c => [c.code, c.workloadHours, c.validityMonths])).toEqual([
      ['NR-20 INICIAÇÃO SOBRE INFLAMÁVEIS E COMBUSTÍVEIS', 3, 24],
      ['NR-20 BÁSICO', 4, 36]
    ]);
    expect(a.courses[1].topics.map(t => t.title)).toEqual([
      'Controles coletivo e individual para trabalhos com inflamáveis',
      'Proteção contra incêndio com inflamáveis',
      'Conhecimentos e utilização dos sistemas de segurança contra incêndio (prática)'
    ]);
    expect(a.warnings.some(w => w.includes('varia conforme a classe da instalação (4, 6, 8 h)'))).toBe(true);
    expect(a.warnings.some(w => w.includes('complementar de 8 h (NR-20, item 20.12.6)'))).toBe(true);
  });

  it('texto sem treinamento: nenhum curso e aviso', () => {
    const a = parseNormText('NR 99 - TESTE\n99.1 Disposições gerais sem capacitação.');
    expect(a.courses).toEqual([]);
    expect(a.warnings[0]).toMatch(/Não foi encontrado/);
  });

  it('junta trechos do PDF sem espaço quando a palavra foi partida', () => {
    const item = (str: string, x: number, width: number, y = 100, hasEOL = false) => ({ str, transform: [10, 0, 0, 10, x, y], width, hasEOL });
    expect(joinPdfItems([item('uti', 0, 15), item('lização', 15.2, 35), item('de', 53, 10, 100, true), item('EPI', 0, 15, 88)])).toBe('utilização de\nEPI');
  });
});
