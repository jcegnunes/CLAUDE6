import { describe, expect, it } from 'vitest';
import { DEFAULT_COURSES } from '../defaultCourses';
import { fillTemplate, DEFAULT_BODY_TEMPLATE } from '../layout';
import { isNr35, nr35Wording, withNr35Wording } from '../rules';

const nr35 = DEFAULT_COURSES.find(c => c.key === 'nr35')!;
const recicl = DEFAULT_COURSES.find(c => c.key === 'nr35-reciclagem')!;
const asCert = (c: typeof nr35) => ({
  courseName: c.name, normReference: c.normReference, topics: c.topics, courseNotes: c.notes, prerequisite: c.prerequisite,
  modality: c.modality, startDate: '2026-11-10', endDate: '2026-11-10', location: 'Brasília/DF', workloadHours: c.workloadHours,
  participantName: 'Fulano', participantCpf: '', participantCompany: '', expiryDate: '', classNumber: ''
});

describe('Certificado de NR-35 — prática e demonstrativa, sem menção a presencial', () => {
  it('reconhece NR-35 e reciclagem, e não mexe em outros cursos', () => {
    expect(isNr35(asCert(nr35))).toBe(true);
    expect(isNr35(asCert(recicl))).toBe(true);
    const nr10 = DEFAULT_COURSES.find(c => c.key === 'nr10-basico')!;
    const c = asCert(nr10 as typeof nr35);
    expect(isNr35(c)).toBe(false);
    expect(withNr35Wording(c)).toBe(c);
  });

  it('"aula prática" vira "prática e demonstrativa"; tópicos "Prática:" também', () => {
    expect(nr35Wording('Aula prática de resgate')).toBe('Prática e demonstrativa de resgate');
    expect(nr35Wording('teoria e aulas práticas')).toBe('teoria e prática e demonstrativa');
    const cert = withNr35Wording(asCert(nr35));
    const practice = cert.topics.filter(t => /^Prática/.test(t.title));
    expect(practice.length).toBeGreaterThan(0);
    practice.forEach(t => expect(t.title.startsWith('Prática e demonstrativa:')).toBe(true));
  });

  it('frente: sem "presencial" no texto, inclusive "na modalidade presencial"', () => {
    for (const course of [nr35, recicl]) {
      const cert = withNr35Wording(asCert(course));
      const front = nr35Wording(fillTemplate(DEFAULT_BODY_TEMPLATE, cert));
      expect(front).not.toMatch(/presencia/i);
      expect(front).toContain('com carga horária total de 8 horas.');
      expect(front).toContain(', em Brasília/DF,');
    }
  });

  it('verso: referência normativa, tópicos e observações sem "presencial" e com pontuação certa', () => {
    const a = withNr35Wording(asCert(nr35));
    expect(a.normReference).toContain('(mínimo 8 h, teórico e prático)');
    expect(a.courseNotes).toContain('Treinamento teórico e prático, realizado antes de o trabalhador iniciar a atividade.');
    const b = withNr35Wording(asCert(recicl));
    expect(b.normReference).toContain('(a cada dois anos, mínimo 8 h)');
    expect(b.courseNotes).toContain('teórico e prático (item 35.4.2).');
    for (const c of [a, b]) {
      expect([c.normReference, c.courseNotes, ...c.topics.map(t => t.title)].join(' ')).not.toMatch(/presencia/i);
      expect(c.courseNotes).not.toMatch(/,\s*,|\(\s*\)|\s[,.]/);
    }
  });

  it('não confunde "semipresencial"', () => {
    expect(nr35Wording('modalidade semipresencial')).toBe('modalidade semipresencial');
  });
});
