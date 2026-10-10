import { describe, expect, it } from 'vitest';
import {
  addMonths, certificateSituation, computeExpiryDate, formatCpf, generateTrainingValidationCode,
  isParticipantApproved, isTrainingValidationCode, isValidCpf, maskCpf, totalTopicHours
} from '../rules';
import { DEFAULT_COURSES } from '../defaultCourses';
import { extractValidationCode, buildValidationUrl } from '../../../config/validationPortalConfig';

describe('Treinamentos — CPF', () => {
  it('valida pelos dígitos verificadores', () => {
    expect(isValidCpf('529.982.247-25')).toBe(true);
    expect(isValidCpf('52998224725')).toBe(true);
    expect(isValidCpf('529.982.247-24')).toBe(false);
    expect(isValidCpf('111.111.111-11')).toBe(false);
    expect(isValidCpf('123')).toBe(false);
  });

  it('formata e mascara como no validador público', () => {
    expect(formatCpf('52998224725')).toBe('529.982.247-25');
    expect(maskCpf('529.982.247-25')).toBe('***.982.247-**');
    expect(maskCpf('')).toBe('');
  });
});

describe('Treinamentos — aprovação', () => {
  const course = { minAttendance: 100, minGrade: 7 };
  const base = { id: 'a', name: 'Ana', cpf: '', attendance: 100, grade: 8 };

  it('exige presença e nota mínimas do curso', () => {
    expect(isParticipantApproved(base, course)).toBe(true);
    expect(isParticipantApproved({ ...base, attendance: 90 }, course)).toBe(false);
    expect(isParticipantApproved({ ...base, grade: 6.9 }, course)).toBe(false);
    expect(isParticipantApproved({ ...base, grade: undefined }, course)).toBe(false);
  });

  it('curso sem avaliação aprova só pela presença', () => {
    expect(isParticipantApproved({ ...base, grade: undefined }, { minAttendance: 75 })).toBe(true);
    expect(isParticipantApproved({ ...base, attendance: 70, grade: undefined }, { minAttendance: 75 })).toBe(false);
  });

  it('decisão manual do instrutor prevalece', () => {
    expect(isParticipantApproved({ ...base, attendance: 50, approvedOverride: true }, course)).toBe(true);
    expect(isParticipantApproved({ ...base, approvedOverride: false }, course)).toBe(false);
  });
});

describe('Treinamentos — validade e reciclagem', () => {
  it('vencimento conta a partir do término, sem pular mês', () => {
    expect(computeExpiryDate('2026-10-05', 24)).toBe('2028-10-05');
    expect(addMonths('2026-01-31', 1)).toBe('2026-02-28');
    expect(addMonths('2028-02-29', 12)).toBe('2029-02-28');
    expect(computeExpiryDate('2026-10-05', 0)).toBeUndefined();
  });

  it('situação: válido, vence em breve (60 dias), vencido e cancelado', () => {
    const today = '2026-10-01';
    expect(certificateSituation({ status: 'valido', expiryDate: '2027-10-01' }, today)).toBe('valido');
    expect(certificateSituation({ status: 'valido', expiryDate: '2026-11-30' }, today)).toBe('vencendo');
    expect(certificateSituation({ status: 'valido', expiryDate: '2026-09-30' }, today)).toBe('vencido');
    expect(certificateSituation({ status: 'valido', expiryDate: undefined }, today)).toBe('valido');
    expect(certificateSituation({ status: 'cancelado', expiryDate: '2027-10-01' }, today)).toBe('cancelado');
  });
});

describe('Treinamentos — QR Code', () => {
  it('código de validação próprio, imprevisível e reconhecido no validador', () => {
    const code = generateTrainingValidationCode(new Date(2026, 9, 1));
    expect(code).toMatch(/^VAL-TRE-2610-[A-HJ-NP-Z2-9]{8}$/);
    expect(generateTrainingValidationCode()).not.toBe(generateTrainingValidationCode());
    expect(isTrainingValidationCode(code.toLowerCase())).toBe(true);
    expect(isTrainingValidationCode('VAL-JVM-2610-ABCD2345')).toBe(false);
  });

  it('usa o mesmo link do site Wix dos laudos', () => {
    const url = buildValidationUrl(undefined, 'VAL-TRE-2610-ABCD2345');
    expect(url).toBe('https://www.jvmengenharia.com.br/validar?codigo=VAL-TRE-2610-ABCD2345');
    expect(extractValidationCode(url)).toBe('VAL-TRE-2610-ABCD2345');
  });
});

describe('Treinamentos — cursos padrão', () => {
  it('NR-10 Básico, NR-10 SEP, NR-35, EPI/EPC e NR-33 com a soma dos tópicos igual à carga horária', () => {
    expect(DEFAULT_COURSES.map(c => c.key)).toEqual(['nr10-basico', 'nr10-sep', 'nr35', 'nr35-reciclagem', 'epi-epc-isolantes', 'nr33-vigia-ta', 'nr33-vigia-ta-reciclagem',
      'nr33-supervisor', 'nr33-supervisor-reciclagem', 'nr33-resgate', 'nr33-resgate-reciclagem',
      ...DEFAULT_COURSES.filter(c => c.key.startsWith('nr20-')).map(c => c.key),
      'nr18-elevadores-montagem', 'nr18-elevadores-montagem-reciclagem', 'nr06-epi']);
    DEFAULT_COURSES.forEach(c => {
      expect(totalTopicHours(c.topics), c.name).toBe(c.workloadHours);
      // NR-20: Iniciação e Específico não têm atualização periódica (Anexo I, Tabela 2)
      if (/^nr20-(iniciacao|especifico)|^nr06-/.test(c.key)) expect(c.validityMonths).toBe(0);
      else expect(c.validityMonths).toBeGreaterThan(0);
    });
    expect(DEFAULT_COURSES.find(c => c.key === 'nr10-basico')!.workloadHours).toBe(40);
    expect(DEFAULT_COURSES.find(c => c.key === 'nr10-sep')!.workloadHours).toBe(40);
    expect(DEFAULT_COURSES.find(c => c.key === 'nr35')!.workloadHours).toBe(8);
  });
});

describe('Treinamentos — aprovação na turma (sem campo de nota)', () => {
  it('sem nota informada vale a presença; nota informada (planilha) continua valendo', async () => {
    const { isApprovedInClass } = await import('../rules');
    const course = { minAttendance: 100, minGrade: 7 };
    const p = { id: 'a', name: 'Ana', cpf: '', attendance: 100 };
    expect(isApprovedInClass(p, course)).toBe(true);
    expect(isApprovedInClass({ ...p, attendance: 90 }, course)).toBe(false);
    expect(isApprovedInClass({ ...p, grade: 5 }, course)).toBe(false);
    expect(isApprovedInClass({ ...p, grade: 8 }, course)).toBe(true);
    expect(isApprovedInClass({ ...p, approvedOverride: false }, course)).toBe(false);
  });
});

describe('Treinamentos — empresa não escolhida', () => {
  it('vazio, espaços ou marcadores como "-" e "Selecione" não aparecem no certificado', async () => {
    const { displayCompany } = await import('../rules');
    expect(displayCompany('Empresa Cliente Ltda')).toBe('Empresa Cliente Ltda');
    expect(displayCompany('  ACME   Ltda ')).toBe('ACME Ltda');
    ['', '   ', '-', '—', 'N/A', 'Não informado', 'Selecione', undefined, null].forEach(v => expect(displayCompany(v as string)).toBe(''));
  });
});

describe('Treinamentos — NR-33 (Anexo III, 2022)', () => {
  const prat = (key: string) => {
    const c = DEFAULT_COURSES.find(x => x.key === key)!;
    return { c, pratica: c.topics.filter(t => t.title.startsWith('Prática')).reduce((a, t) => a + t.hours, 0) };
  };
  it('inicial: 16 h, anual, ao menos 50% de prática', () => {
    const { c, pratica } = prat('nr33-vigia-ta');
    expect([c.workloadHours, c.validityMonths]).toEqual([16, 12]);
    expect(pratica).toBeGreaterThanOrEqual(c.workloadHours / 2);
  });
  it('reciclagem: 8 h, anual, ao menos 50% de prática, exige o inicial', () => {
    const { c, pratica } = prat('nr33-vigia-ta-reciclagem');
    expect([c.workloadHours, c.validityMonths]).toEqual([8, 12]);
    expect(pratica).toBeGreaterThanOrEqual(c.workloadHours / 2);
    expect(c.prerequisite).toMatch(/16 h/);
  });
  it('supervisor 40 h + 8 h anual; equipe de emergência 24 h bienal; todos com ao menos 50% de prática', () => {
    expect([prat('nr33-supervisor').c.workloadHours, prat('nr33-supervisor-reciclagem').c.workloadHours]).toEqual([40, 8]);
    expect([prat('nr33-resgate').c.validityMonths, prat('nr33-resgate-reciclagem').c.validityMonths]).toEqual([24, 24]);
    ['nr33-supervisor', 'nr33-supervisor-reciclagem', 'nr33-resgate', 'nr33-resgate-reciclagem'].forEach(k => {
      const { c, pratica } = prat(k);
      expect(pratica, k).toBeGreaterThanOrEqual(c.workloadHours / 2);
    });
  });
});

describe('Treinamentos — NR-20 (Anexo I, Tabelas 1 e 2)', () => {
  const get = (key: string) => DEFAULT_COURSES.find(c => c.key === key)!;
  it('carga horária e atualização conforme a classe da instalação', () => {
    const h = (k: string) => [get(k).workloadHours, get(k).validityMonths];
    expect(h('nr20-iniciacao')).toEqual([3, 0]);
    expect([h('nr20-basico-classe-i'), h('nr20-basico-classe-ii'), h('nr20-basico-classe-iii')]).toEqual([[4, 36], [6, 36], [8, 36]]);
    expect([h('nr20-intermediario-classe-i'), h('nr20-intermediario-classe-ii'), h('nr20-intermediario-classe-iii')]).toEqual([[12, 36], [14, 24], [16, 24]]);
    expect([h('nr20-avancado-1'), h('nr20-avancado-2')]).toEqual([[20, 24], [32, 12]]);
    expect([h('nr20-especifico-classe-ii'), h('nr20-especifico-classe-iii')]).toEqual([[14, 0], [16, 0]]);
    expect(DEFAULT_COURSES.filter(c => c.key.startsWith('nr20-complementacao')).every(c => c.workloadHours === 8)).toBe(true);
    expect(DEFAULT_COURSES.filter(c => c.key.startsWith('nr20-atualizacao')).map(c => [c.workloadHours, c.validityMonths])).toEqual([[4, 36], [4, 36], [4, 24], [4, 24], [4, 12]]);
  });
  it('básico, intermediário e avançados têm parte prática; códigos únicos', () => {
    DEFAULT_COURSES.filter(c => /^nr20-(basico|intermediario|avancado)/.test(c.key))
      .forEach(c => expect(c.topics.some(t => t.title.startsWith('Prática')), c.key).toBe(true));
    const codes = DEFAULT_COURSES.map(c => c.code);
    expect(new Set(codes).size).toBe(codes.length);
  });
});

describe('Treinamentos — NR-18 elevadores (Anexo I, Quadro 1)', () => {
  it('inicial e periódico anual, com parte prática e responsabilidade do item 18.11.5', () => {
    const ini = DEFAULT_COURSES.find(c => c.key === 'nr18-elevadores-montagem')!;
    const rec = DEFAULT_COURSES.find(c => c.key === 'nr18-elevadores-montagem-reciclagem')!;
    expect([ini.workloadHours, ini.validityMonths, rec.workloadHours, rec.validityMonths]).toEqual([24, 12, 8, 12]);
    [ini, rec].forEach(c => expect(c.topics.some(t => t.title.startsWith('Prática'))).toBe(true));
    expect(ini.notes).toMatch(/18.11.5/);
  });
});

describe('Treinamentos — NR-06 (EPI)', () => {
  it('cobre as informações do item 6.7.2 e os tipos do Anexo I, com prática', () => {
    const c = DEFAULT_COURSES.find(x => x.key === 'nr06-epi')!;
    const all = c.topics.map(t => t.title).join(' ');
    ['restrições e limitações', 'uso e ajuste', 'higienização', 'Certificado de Aprovação', 'respiratória', 'quedas'].forEach(t => expect(all).toContain(t));
    expect(c.topics.some(t => t.title.startsWith('Prática'))).toBe(true);
  });
});

describe('Treinamentos — NR-35 atualizada (Portaria MTE nº 1.259/2026)', () => {
  it('inicial 8 h teórico e prático, presencial, com escadas; reciclagem 8 h a cada 2 anos', () => {
    const ini = DEFAULT_COURSES.find(c => c.key === 'nr35')!;
    const rec = DEFAULT_COURSES.find(c => c.key === 'nr35-reciclagem')!;
    expect([ini.workloadHours, ini.validityMonths, ini.modality, rec.workloadHours, rec.validityMonths]).toEqual([8, 24, 'presencial', 8, 24]);
    expect(ini.normReference).toMatch(/35\.4\.2\.1/);
    expect(ini.notes).toMatch(/35\.4\.5/);
    expect(ini.topics.some(t => /escada/i.test(t.title))).toBe(true);
    [ini, rec].forEach(c => expect(c.topics.some(t => t.title.startsWith('Prática'))).toBe(true));
  });
});
