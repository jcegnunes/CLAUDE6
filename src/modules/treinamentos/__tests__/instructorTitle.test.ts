import { describe, expect, it } from 'vitest';
import { instructorTitle } from '../rules';
import { normalizeLayout } from '../layout';

describe('Termo do instrutor na assinatura', () => {
  it('usa "Instrutor" quando o cadastro não escolhe', () => {
    expect(instructorTitle({})).toBe('Instrutor');
    expect(instructorTitle(undefined)).toBe('Instrutor');
    expect(instructorTitle({ title: 'Instrutor' }, 'Instrutor Responsável')).toBe('Instrutor Responsável');
  });

  it('troca só a palavra inicial do cargo do modelo por "Instrutora"', () => {
    expect(instructorTitle({ title: 'Instrutora' })).toBe('Instrutora');
    expect(instructorTitle({ title: 'Instrutora' }, 'Instrutor Responsável')).toBe('Instrutora Responsável');
    expect(instructorTitle({ title: 'Instrutora' }, 'INSTRUTOR')).toBe('INSTRUTORA');
    expect(instructorTitle({ title: 'Instrutora' }, 'Docente')).toBe('Docente');
  });
});

describe('Termo abaixo da assinatura do aluno', () => {
  it('padrão é "Aluno", inclusive em modelos salvos com o antigo "Participante"', () => {
    expect(normalizeLayout({}).participantLabel).toBe('Aluno');
    expect(normalizeLayout({ participantLabel: 'Participante' }).participantLabel).toBe('Aluno');
    expect(normalizeLayout({ participantLabel: 'Treinando' }).participantLabel).toBe('Treinando');
  });
});
