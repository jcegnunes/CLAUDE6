import { describe, expect, it } from 'vitest';
import { analyticsSheets, branchLabel, buildAnalytics, displayName, progressStatus, shortName } from '../reportAnalytics';
import { parseCsv, parseReport, parseRoster, reportIssuedAt } from '../reportMerge';

const CSV = 'Aluno;CPF;Curso;Conclusão;Data de conclusão;Situação\n'
  + 'Maria;529.982.247-25;EPI - NR06;100%;30/09/2026;Sem média exigida\n'
  + 'Maria;529.982.247-25;Altura - NR35;100%;11/10/2026;Reprovado\n'
  + 'Maria;529.982.247-25;Eletricidade - NR10;40%;-;Em andamento\n'
  + 'João;111.444.777-35;EPI - NR06;0%;-;Sem média exigida\n'
  + 'João;111.444.777-35;Altura - NR35;100%;20/10/2026;Aprovado\n'
  + 'Pedro;935.411.347-80;EPI - NR06;100%;01/10/2026;Aprovado\n'
  + 'Ana;;EPI - NR06;50%;-;Em andamento\n';

const ROSTER = [
  ['Colaborador', 'CPF', 'Filial'],
  ['Maria da Silva', '52998224725', 'Sao Paulo'],
  ['João Souza', '111.444.777-35', 'São Paulo'],
  ['DESLIGADOS'],
  ['Pedro', '935.411.347-80', 'Brasília']
];

describe('Relatório analítico do desenvolvimento dos alunos', () => {
  it('classifica a situação de cada matrícula', () => {
    expect(progressStatus({ situation: 'Aprovado', completion: 100, completedAt: '2026-10-01' })).toBe('aprovado');
    expect(progressStatus({ situation: 'Sem média exigida', completion: 100, completedAt: '2026-10-01' })).toBe('aprovado');
    expect(progressStatus({ situation: 'Reprovado', completion: 100, completedAt: '2026-10-01' })).toBe('reprovado');
    expect(progressStatus({ situation: 'Em andamento', completion: 72, completedAt: null })).toBe('andamento');
    expect(progressStatus({ situation: 'Sem média exigida', completion: 0, completedAt: null })).toBe('nao_iniciado');
  });

  it('resume por curso e por filial, juntando a mesma filial escrita sem acento e tirando os desligados', () => {
    const a = buildAnalytics(parseReport(parseCsv(CSV)), parseRoster(ROSTER));
    expect(a.removed).toBe(1);
    expect(a.summary).toMatchObject({ enrollments: 6, students: 3, aprovado: 2, reprovado: 1, andamento: 2, nao_iniciado: 1 });
    expect(a.summary.avgCompletion).toBe(65); // (100+100+40+0+100+50)/6
    expect(a.byCourse.map(c => [c.course, c.enrollments, c.aprovado])).toEqual([
      ['Altura - NR35', 2, 1], ['Eletricidade - NR10', 1, 0], ['EPI - NR06', 3, 1]
    ]);
    expect(a.byBranch.map(b => [b.branch, b.students, b.aprovado])).toEqual([['São Paulo/SP', 2, 2], ['Sem filial', 1, 0]]);
    expect(a.matrix.rows[0].cells['EPI - NR06']).toMatchObject({ aprovado: 1, nao_iniciado: 1, enrollments: 2, avgCompletion: 50 });
    expect(a.students.find(s => s.name === 'Maria da Silva')?.courses.map(c => c.status)).toEqual(['reprovado', 'andamento', 'aprovado']);
    expect(a.attention).toEqual([
      '1 reprovação(ões) após concluir 100% do conteúdo, envolvendo 1 aluno(s): Altura - NR35 (1). Programar nova avaliação.',
      '2 matrícula(s) em andamento: Ana (EPI - NR06, 50%), Maria Silva (Eletricidade - NR10, 40%).',
      '1 aluno(s) sem filial reconhecida na lista de colaboradores: Ana. O certificado sai com a cidade do laboratório.'
    ]);
  });

  it('usa a sigla do curso do sistema quando o curso do relatório está associado', () => {
    const report = parseReport(parseCsv(CSV));
    const a = buildAnalytics(report, [], { mapping: { 'EPI - NR06': 'nr06' }, courses: [{ id: 'nr06', code: 'NR-06 EPI', name: 'NR-06' } as any] });
    expect(a.byCourse.map(c => c.course)).toContain('NR-06 EPI');
  });

  it('gera as abas do Excel', () => {
    const sheets = analyticsSheets(buildAnalytics(parseReport(parseCsv(CSV)), parseRoster(ROSTER)), { title: 'T', generatedAt: '07/11/2026' });
    expect(sheets.map(s => s.name)).toEqual(['Resumo', 'Por curso', 'Por filial', 'Filial x Curso', 'Alunos']);
    expect(sheets[4].rows).toHaveLength(1 + 6);
  });
});

describe('Dados do cabeçalho e nomes do relatório analítico', () => {
  it('lê a turma e a data de emissão do relatório padrão', () => {
    const rows = parseCsv('"Turma / Grupo: Turma 6"\r\n"Relatório emitido em 07/11/2026 14:11"\r\n\r\n'
      + 'Aluno;CPF;Curso;"Turma / Grupo";Conclusão;"Data de conclusão";Situação\r\n'
      + '"Maria";529.982.247-25;"EPI - NR06";"Turma Nº6- Treinamento  4/2026";100%;30/09/2026;Aprovado\r\n');
    expect(reportIssuedAt(rows)).toBe('07/11/2026 14:11');
    expect(buildAnalytics(parseReport(rows), []).group).toBe('Turma Nº6- Treinamento 4/2026');
  });
  it('formata nomes e filiais', () => {
    expect(displayName('UILTON SANTOS FERREIRA')).toBe('Uilton Santos Ferreira');
    expect(displayName('JOSÉ DA SILVA')).toBe('José da Silva');
    expect(displayName('Jorge luiz pereira')).toBe('Jorge luiz pereira');
    expect(shortName('Darlan Antônio dos Santos')).toBe('Darlan Santos');
    expect(branchLabel('Sao Paulo')).toBe('São Paulo/SP');
    expect(branchLabel('Brasília ')).toBe('Brasília/DF');
    expect(branchLabel('Campinas/SP')).toBe('Campinas/SP');
    expect(branchLabel('Santa Catarina')).toBe('Santa Catarina');
  });
});
