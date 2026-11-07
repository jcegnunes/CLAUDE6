import { describe, expect, it } from 'vitest';
import { mergeReports, nrNumber, parseCsv, parseReport, parseRoster, suggestCourse, toImportRecords } from '../reportMerge';
import type { TrainingCourse } from '../types';

const course = (id: string, code: string, name: string): TrainingCourse => ({
  id, code, name, companyId: '', createdAt: '', updatedAt: '', normReference: '', workloadHours: 8, validityMonths: 24,
  modality: 'ead', minAttendance: 100, topics: [], active: true
});
const COURSES = [
  course('nr06', 'NR-06 EPI', 'NR-06 – Equipamento de Proteção Individual'),
  course('nr10', 'NR-10 BÁSICO', 'NR-10 – Segurança em Instalações e Serviços com Eletricidade'),
  course('nr10sep', 'NR-10 SEP', 'NR-10 – Curso Complementar SEP'),
  course('nr33', 'NR-33 VIGIA/TA', 'NR-33 – Espaço Confinado Vigia e Trabalhador Autorizado'),
  course('nr33r', 'NR-33 VIGIA/TA RECICLAGEM', 'Reciclagem NR-33 – Vigia e Trabalhador Autorizado'),
  course('nr35', 'NR-35', 'NR-35 – Trabalho em Altura')
];

const CSV = '﻿Aluno;CPF;Telefone;Curso;Turma / Grupo;Conclusão;Data de conclusão;Situação\r\n'
  + 'Maria da Silva ;529.982.247-25;-;Equipamentos de Proteção Individual - NR06 - Turma 2/2026;T6;100,00%;30/09/2026;Sem média exigida\r\n'
  + 'Maria da Silva ;529.982.247-25;-;Trabalho em Altura - NR35 - Turma 2/2026;T6;100,00%;11/10/2026;Reprovado\r\n'
  + 'Maria da Silva ;529.982.247-25;-;Reciclagem Espaço Confinado Vigia -NR33 - Turma 2/2026;T6;100,00%;14/10/2026;Aprovado\r\n'
  + 'joão souza;111.444.777-35;-;Equipamentos de Proteção Individual - NR06 - Turma 2/2026;T6;0,00%;-;Sem média exigida\r\n'
  + 'joão souza;111.444.777-35;-;Trabalho em Altura - NR35 - Turma 2/2026;T6;100,00%;20/10/2026;Aprovado\r\n';

// lista da empresa: título na 2ª linha, CPF sem máscara e sem o zero inicial, seção de desligados
const ROSTER = [
  [],
  ['Colaborador', 'CPF', 'E_mail', 'Filial', 'Data da Liberação'],
  ['Maria da Silva', '52998224725', 'm@x.com', 'São Paulo  ok', '22/09/2026'],
  ['João Souza', '111.444.777-35', 'j@x.com', 'Brasília ', '20/10/2016'],
  [],
  ['DESLIGADOS', 'removidos da turma a pedido do RH'],
  ['Pedro Removido', '935.411.347-80', '', 'Brasília', '01/09/2026']
];

describe('Planilha de emissão a partir dos relatórios', () => {
  it('lê o CSV do relatório (BOM, ";" e percentuais com vírgula)', () => {
    const rows = parseReport(parseCsv(CSV));
    expect(rows).toHaveLength(5);
    expect(rows[0]).toMatchObject({ name: 'Maria da Silva', cpf: '52998224725', completion: 100, completedAt: '2026-09-30', situation: 'Sem média exigida' });
    expect(rows[3].completedAt).toBeNull();
  });

  it('lê o relatório padrão da plataforma (linhas de informação, aspas e coluna E-mail)', () => {
    const csv = '﻿"Lições concluídas por curso: Todos os cursos"\r\n"Turma / Grupo: Turma 6, Cliente"\r\n"Relatório emitido em 07/11/2026 14:11"\r\n\r\n'
      + 'Aluno;E-mail;CPF;Telefone;Curso;"Turma / Grupo";Conclusão;"Data de conclusão";Situação\r\n'
      + '"Maria da Silva ";m@x.com;529.982.247-25;-;"Equipamentos de Proteção Individual - NR06  - Turma 2/2026";"Turma 6, Cliente";100,00%;30/09/2026;"Sem média exigida"\r\n';
    const rows = parseReport(parseCsv(csv));
    expect(rows).toHaveLength(1);
    expect(rows[0]).toMatchObject({ name: 'Maria da Silva', cpf: '52998224725', courseText: 'Equipamentos de Proteção Individual - NR06 - Turma 2/2026', completion: 100, completedAt: '2026-09-30', situation: 'Sem média exigida' });
  });

  it('lê a lista de colaboradores e marca os desligados', () => {
    const roster = parseRoster(ROSTER);
    expect(roster.map(r => r.name)).toEqual(['Maria da Silva', 'João Souza', 'Pedro Removido']);
    expect(roster[0]).toMatchObject({ cpf: '52998224725', branch: 'São Paulo', releasedAt: '2026-09-22', removed: false });
    expect(roster[2].removed).toBe(true);
  });

  it('associa o curso do relatório pela NR, com reciclagem só quando o relatório diz', () => {
    expect(nrNumber('Segurança ... - NR 10 - Turma 2')).toBe('10');
    expect(nrNumber('EPI - NR06')).toBe('06');
    expect(suggestCourse('Equipamentos de Proteção Individual - NR06', COURSES)?.id).toBe('nr06');
    expect(suggestCourse('Segurança em Instalações e Serviços com Eletricidade - NR 10', COURSES)?.id).toBe('nr10');
    expect(suggestCourse('Reciclagem Espaço Confinado Vigia -NR33', COURSES)?.id).toBe('nr33r');
    expect(suggestCourse('Espaço Confinado Vigia - NR33', COURSES)?.id).toBe('nr33');
    expect(suggestCourse('Primeiros socorros', COURSES)).toBeUndefined();
  });

  it('emite só 100% concluído e aprovado, com nome e filial da lista e início na liberação', () => {
    const report = parseReport(parseCsv(CSV));
    const mapping = Object.fromEntries([...new Set(report.map(r => r.courseText))].map(t => [t, suggestCourse(t, COURSES)?.id]));
    const { rows, skipped } = mergeReports(report, parseRoster(ROSTER), { mapping, courses: COURSES, company: 'Cliente Ltda' });
    expect(rows.map(r => `${r.course.id}:${r.name}`)).toEqual(['nr06:Maria da Silva', 'nr33r:Maria da Silva', 'nr35:João Souza']);
    expect(rows[0]).toMatchObject({ cpf: '529.982.247-25', branch: 'São Paulo', company: 'Cliente Ltda', startDate: '2026-09-22', endDate: '2026-09-30' });
    // liberação digitada como 2016: início = conclusão, com aviso
    expect(rows[2]).toMatchObject({ startDate: '2026-10-20', endDate: '2026-10-20' });
    expect(rows[2].warnings.join()).toContain('liberação');
    expect(skipped.map(s => s.reason).sort()).toEqual(['Conclusão de 0%', 'Situação: Reprovado']);
  });

  it('deixa de fora desligados e cursos sem correspondência', () => {
    const report = parseReport(parseCsv('Aluno;CPF;Curso;Conclusão;Data de conclusão;Situação\n'
      + 'Pedro Removido;935.411.347-80;Trabalho em Altura - NR35;100%;01/10/2026;Aprovado\n'
      + 'Maria da Silva;529.982.247-25;Primeiros socorros;100%;01/10/2026;Aprovado\n'));
    const mapping = { 'Trabalho em Altura - NR35': 'nr35', 'Primeiros socorros': undefined };
    const { rows, skipped } = mergeReports(report, parseRoster(ROSTER), { mapping, courses: COURSES, company: '' });
    expect(rows).toHaveLength(0);
    expect(skipped.map(s => s.reason)).toEqual(['Desligado / removido da turma', 'Curso sem correspondência no sistema']);
  });

  it('deixa de fora quem já tem certificado válido do mesmo curso, em qualquer data', () => {
    const report = parseReport(parseCsv(CSV));
    const { rows, skipped } = mergeReports(report, parseRoster(ROSTER), {
      mapping: { [report[0].courseText]: 'nr06' }, courses: COURSES, company: '',
      existing: [
        { courseId: 'nr06', participantCpf: '529.982.247-25', status: 'valido', certificateNumber: 'TRE-2610-0033' },
        { courseId: 'nr35', participantCpf: '529.982.247-25', status: 'cancelado', certificateNumber: 'TRE-2610-0001' }
      ]
    });
    expect(rows).toHaveLength(0);
    expect(skipped.map(s => s.reason)).toContain('Já tem certificado válido (TRE-2610-0033)');
  });

  it('gera as linhas com os títulos do modelo de importação', () => {
    const report = parseReport(parseCsv(CSV));
    const { rows } = mergeReports(report, parseRoster(ROSTER), { mapping: { [report[0].courseText]: 'nr06' }, courses: COURSES, company: 'Cliente Ltda' });
    expect(toImportRecords(rows)[0]).toMatchObject({
      'Nome': 'Maria da Silva', 'CPF': '529.982.247-25', 'Colaborador da Empresa': 'Cliente Ltda', 'Filial': 'São Paulo',
      'Curso': 'NR-06 EPI', 'Início': '22/09/2026', 'Término': '30/09/2026', 'Presença (%)': '100'
    });
  });
});
