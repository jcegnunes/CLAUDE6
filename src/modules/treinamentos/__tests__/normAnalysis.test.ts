import { describe, expect, it } from 'vitest';
import { normalizeProposal, proposalToCourse } from '../normAnalysis';
import type { TrainingCourse } from '../types';

const base: TrainingCourse = {
  id: '', companyId: '', createdAt: '', updatedAt: '', code: '', name: '', normReference: '', workloadHours: 8,
  validityMonths: 24, modality: 'presencial', minAttendance: 100, minGrade: 7, topics: [{ title: '', hours: 8 }],
  prerequisite: '', notes: '', active: true
};

const RAW = {
  norm: { code: 'NR-35', title: 'Trabalho em Altura', revision: 'Portaria MTP nº 4.218/2022' },
  courses: [{
    code: 'nr-35', name: 'NR-35 – Trabalho em Altura', kind: 'inicial', normReference: 'NR-35, item 35.3.2',
    workloadHours: 8, validityMonths: 24, modality: 'presencial', minAttendance: 100, minGrade: null,
    prerequisite: '', notes: 'Teórico e prático (35.3.2).', topicHoursFromNorm: false,
    topics: [
      { title: 'Normas e regulamentos', hours: 1.3, normItem: '35.3.2 a)' },
      { title: 'Análise de risco', hours: 2, normItem: '35.3.2 b)' },
      { title: 'Equipamentos de proteção', hours: 3, normItem: '35.3.2 d)' },
      { title: '', hours: 1, normItem: '' }
    ],
    evidence: '35.3.2: carga horária mínima de oito horas'
  }],
  warnings: ['Horas por tópico sugeridas.']
};

describe('Curso a partir do texto da norma (IA)', () => {
  it('confere a proposta: sigla, horas em 0,5 h, tópicos vazios e soma diferente da carga', () => {
    const a = normalizeProposal(RAW);
    const c = a.courses[0];
    expect(c.code).toBe('NR-35');
    expect(c.topics.map(t => t.hours)).toEqual([1.5, 2, 3]);
    expect(a.warnings).toEqual(['Horas por tópico sugeridas.', 'NR-35: a soma dos tópicos (6.5 h) é diferente da carga horária (8 h). Ajuste antes de salvar.']);
  });

  it('limita valores fora do cadastro', () => {
    const a = normalizeProposal({ courses: [{ name: 'X', workloadHours: -2, validityMonths: 12.4, modality: 'online', minAttendance: 150, minGrade: 12 }] });
    expect(a.courses[0]).toMatchObject({ workloadHours: 0, validityMonths: 12, modality: 'presencial', minAttendance: 100, minGrade: 10 });
    expect(normalizeProposal(null)).toEqual({ norm: { code: '', title: '', revision: '' }, courses: [], warnings: [] });
  });

  it('preenche o formulário: nota padrão quando a norma não fixa e item da norma no tópico', () => {
    const c = proposalToCourse(normalizeProposal(RAW).courses[0], base);
    expect(c).toMatchObject({ code: 'NR-35', name: 'NR-35 – Trabalho em Altura', workloadHours: 8, validityMonths: 24, minGrade: 7, active: true });
    expect(c.topics[0]).toEqual({ title: 'Normas e regulamentos (35.3.2 a))', hours: 1.5 });
  });
});
