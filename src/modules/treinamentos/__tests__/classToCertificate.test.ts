import { describe, expect, it } from 'vitest';
import { certificateChanged, certificateWithClassData } from '../repository';
import type { TrainingCertificate, TrainingClass, TrainingCourse } from '../types';

const cert = {
  id: 'trc1', companyId: 'c1', createdAt: '', updatedAt: '', certificateNumber: 'TRE-1', validationCode: 'VAL-1',
  classId: 'tur1', classNumber: 'T-1', courseId: 'crs-a', courseName: 'Curso A', normReference: 'NR-A', workloadHours: 8,
  modality: 'presencial', topics: [{ title: 'A', hours: 8 }], courseNotes: 'obs A', participantName: 'ANA', participantCpf: '',
  startDate: '2026-11-02', endDate: '2026-11-03', location: 'Obra 1', issueDate: '2026-11-03', expiryDate: '2027-11-03',
  instructorIds: ['i1'], instructorNames: ['Instrutor'], instructors: [{ name: 'Instrutor' }], status: 'valido'
} as TrainingCertificate;

const turma = {
  ...cert, id: 'tur1', classNumber: 'T-1', courseId: 'crs-a', courseName: 'Curso A', startDate: '2026-11-02', endDate: '2026-11-03',
  location: 'Obra 1', modality: 'presencial', workloadHours: 8, instructorIds: ['i1'], participants: [], status: 'concluida'
} as unknown as TrainingClass;

const courseA = { id: 'crs-a', name: 'Curso A', normReference: 'NR-A', validityMonths: 12, topics: [{ title: 'A', hours: 8 }], notes: 'obs A' } as TrainingCourse;
const courseB = { id: 'crs-b', name: 'Curso B', normReference: 'NR-B', validityMonths: 24, topics: [{ title: 'B', hours: 16 }], notes: 'obs B' } as TrainingCourse;

describe('Dados da turma nos certificados já emitidos', () => {
  it('sem mudança na turma, o certificado fica igual', () => {
    expect(certificateChanged(cert, certificateWithClassData(cert, turma, courseA))).toBe(false);
  });

  it('datas, local e carga da turma vão para o certificado; vencimento recalculado', () => {
    const next = certificateWithClassData(cert, { ...turma, startDate: '2026-11-04', endDate: '2026-11-05', location: 'Obra 2', workloadHours: 16 }, courseA);
    expect([next.startDate, next.endDate, next.location, next.workloadHours, next.expiryDate]).toEqual(['2026-11-04', '2026-11-05', 'Obra 2', 16, '2027-11-05']);
    expect([next.certificateNumber, next.validationCode, next.participantName]).toEqual(['TRE-1', 'VAL-1', 'ANA']);
  });

  it('turma com outro curso: nome, norma, conteúdo, observações e vencimento do novo curso', () => {
    const next = certificateWithClassData(cert, { ...turma, courseId: 'crs-b', workloadHours: 16 }, courseB);
    expect([next.courseId, next.courseName, next.normReference, next.courseNotes, next.expiryDate]).toEqual(['crs-b', 'Curso B', 'NR-B', 'obs B', '2028-11-03']);
    expect(next.topics).toEqual([{ title: 'B', hours: 16 }]);
  });
});
