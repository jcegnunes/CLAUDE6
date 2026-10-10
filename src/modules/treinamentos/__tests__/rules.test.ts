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
    expect(DEFAULT_COURSES.map(c => c.key)).toEqual(['nr10-basico', 'nr10-sep', 'nr35', 'epi-epc-isolantes', 'nr33-vigia-ta', 'nr33-vigia-ta-reciclagem',
      'nr33-supervisor', 'nr33-supervisor-reciclagem', 'nr33-resgate', 'nr33-resgate-reciclagem']);
    DEFAULT_COURSES.forEach(c => {
      expect(totalTopicHours(c.topics), c.name).toBe(c.workloadHours);
      expect(c.validityMonths).toBeGreaterThan(0);
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
