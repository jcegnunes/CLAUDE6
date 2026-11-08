import React, { useState } from 'react';
import { Save } from 'lucide-react';
import { DielectricStorageService } from '../../../services/syncEngine';
import {
  certificateInstructors, courseSnapshot, currentCompanyId, getCertificates, getClass, getCourse, getCourses, getInstructors,
  saveCertificate, saveClass
} from '../repository';
import { computeExpiryDate, formatCpf, isValidCpf } from '../rules';
import type { TrainingCertificate, TrainingModality } from '../types';
import { alertError, btnPrimary, btnSecondary, Field, inputCls, Modal } from './ui';

const MODALITIES: Array<{ id: TrainingModality; label: string }> = [
  { id: 'presencial', label: 'Presencial' },
  { id: 'semipresencial', label: 'Semipresencial' },
  { id: 'ead', label: 'A distância (EAD)' }
];

const numOrUndef = (v: string) => (v.trim() === '' ? undefined : Number(v.replace(',', '.')));

/**
 * Edição de um certificado já emitido: o número e o código do QR Code não mudam.
 * Os dados do aluno também são corrigidos na turma de origem, para a turma e o
 * certificado continuarem iguais.
 */
export const EditCertificate: React.FC<{ cert: TrainingCertificate; onClose: () => void; onSaved: (c: TrainingCertificate) => void }> = ({ cert, onClose, onSaved }) => {
  const courses = getCourses().filter(c => c.active || c.id === cert.courseId);
  const instructors = getInstructors().filter(i => i.active || cert.instructorIds.includes(i.id));
  const clients = DielectricStorageService.getClients(currentCompanyId());
  const turma = cert.classId ? getClass(cert.classId) : undefined;

  const [courseId, setCourseId] = useState(cert.courseId);
  const [name, setName] = useState(cert.participantName);
  const [cpf, setCpf] = useState(cert.participantCpf);
  const [role, setRole] = useState(cert.participantRole || '');
  const [company, setCompany] = useState(cert.participantCompany || '');
  const [branch, setBranch] = useState(cert.participantBranch || '');
  const [startDate, setStartDate] = useState(cert.startDate);
  const [endDate, setEndDate] = useState(cert.endDate || cert.startDate);
  const [location, setLocation] = useState(cert.location || '');
  const [modality, setModality] = useState<TrainingModality>(cert.modality);
  const [workload, setWorkload] = useState(String(cert.workloadHours ?? ''));
  const [attendance, setAttendance] = useState(cert.attendance === undefined ? '' : String(cert.attendance));
  const [grade, setGrade] = useState(cert.grade === undefined || cert.grade === null ? '' : String(cert.grade));
  const [issueDate, setIssueDate] = useState(cert.issueDate);
  const [expiryDate, setExpiryDate] = useState(cert.expiryDate || '');
  const [instructorIds, setInstructorIds] = useState<string[]>(cert.instructorIds);
  const course = getCourse(courseId);

  // vencimento acompanha o término e a validade do curso (pode ser ajustado à mão depois)
  const recomputeExpiry = (end: string, cId: string) => {
    const c = getCourse(cId);
    if (c) setExpiryDate(computeExpiryDate(end, c.validityMonths) || '');
  };

  const handleSave = () => {
    if (!name.trim()) return window.alert('Informe o nome do aluno.');
    if (cpf && !isValidCpf(cpf)) return window.alert('CPF inválido.');
    if (!startDate) return window.alert('Informe a data de início.');
    if (endDate && endDate < startDate) return window.alert('A data de término é anterior à de início.');
    const hours = numOrUndef(workload);
    if (!hours || hours <= 0) return window.alert('Informe a carga horária.');
    const att = numOrUndef(attendance);
    if (att !== undefined && !(att >= 0 && att <= 100)) return window.alert('Presença deve ficar entre 0 e 100%.');
    const g = numOrUndef(grade);
    if (g !== undefined && !(g >= 0 && g <= 10)) return window.alert('Nota deve ficar entre 0 e 10.');
    const cpfFmt = cpf ? formatCpf(cpf) : '';
    if (cpfFmt && getCertificates().some(c => c.id !== cert.id && c.courseId === courseId && c.status === 'valido' && c.participantCpf === cpfFmt && c.endDate === (endDate || startDate))
      && !window.confirm('Já existe outro certificado válido deste curso para este CPF com a mesma data. Salvar assim mesmo?')) return;

    const sameInstructors = instructorIds.join('|') === cert.instructorIds.join('|');
    const next: TrainingCertificate = {
      ...cert,
      ...(course && course.id !== cert.courseId ? courseSnapshot(course) : {}),
      participantName: name.trim(),
      participantCpf: cpfFmt,
      participantRole: role.trim() || undefined,
      participantCompany: company.trim() || undefined,
      participantBranch: branch.trim() || undefined,
      startDate,
      endDate: endDate || startDate,
      location: location.trim(),
      modality,
      workloadHours: hours,
      attendance: att,
      grade: g,
      issueDate: issueDate || cert.issueDate,
      expiryDate: expiryDate || undefined,
      ...(sameInstructors ? {} : certificateInstructors(instructorIds))
    };
    try {
      const saved = saveCertificate(next);
      // o aluno na turma de origem recebe as mesmas correções
      if (turma) {
        const p = turma.participants.find(x => x.certificateId === cert.id);
        if (p) {
          const fixed = { ...p, name: next.participantName, cpf: next.participantCpf, role: next.participantRole, company: next.participantCompany, branch: next.participantBranch, attendance: next.attendance ?? p.attendance, grade: next.grade };
          if (JSON.stringify(fixed) !== JSON.stringify(p)) saveClass({ ...turma, participants: turma.participants.map(x => (x.id === p.id ? fixed : x)) });
        }
      }
      onClose();
      onSaved(saved);
    } catch (err) {
      alertError(err, 'Falha ao salvar o certificado');
    }
  };

  return (
    <Modal
      title={`Editar certificado ${cert.certificateNumber}`}
      onClose={onClose}
      wide
      footer={<>
        <button type="button" className={btnSecondary} onClick={onClose}>Cancelar</button>
        <button type="button" className={btnPrimary} onClick={handleSave}><Save className="w-3.5 h-3.5" /> Salvar certificado</button>
      </>}
    >
      <p className="mb-3 text-[11px] text-slate-600 bg-slate-50 border border-slate-200 rounded-lg px-2.5 py-1.5">
        O número <b className="font-mono">{cert.certificateNumber}</b> e o código do QR Code <b className="font-mono">{cert.validationCode}</b> não mudam: o validador mostra os dados corrigidos.
        {turma ? <> Os dados do aluno também são corrigidos na turma <b>{turma.classNumber}</b>. Para mudar datas, local ou instrutores de todos os alunos, edite a turma.</> : null}
      </p>
      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="Curso *" className="sm:col-span-2" hint={course && course.id !== cert.courseId ? 'Ao salvar, nome, norma, conteúdo programático e observações passam a ser os deste curso.' : undefined}>
          <select className={inputCls} value={courseId} onChange={e => { setCourseId(e.target.value); const c = getCourse(e.target.value); if (c) { setWorkload(String(c.workloadHours)); setModality(c.modality); } recomputeExpiry(endDate, e.target.value); }}>
            {courses.map(c => <option key={c.id} value={c.id}>{c.code ? `${c.code} – ` : ''}{c.name}</option>)}
          </select>
        </Field>
        <Field label="Nome do aluno *" className="sm:col-span-2"><input className={inputCls} value={name} onChange={e => setName(e.target.value)} /></Field>
        <Field label="CPF"><input className={inputCls} value={cpf} onChange={e => setCpf(e.target.value)} onBlur={e => setCpf(e.target.value ? formatCpf(e.target.value) : '')} inputMode="numeric" /></Field>
        <Field label="Função"><input className={inputCls} value={role} onChange={e => setRole(e.target.value)} /></Field>
        <Field label="Empresa">
          <input className={inputCls} value={company} onChange={e => setCompany(e.target.value)} list="trn-clientes-edit" />
          <datalist id="trn-clientes-edit">{clients.map(c => <option key={c.id} value={c.nomeFantasia || c.razaoSocial} />)}</datalist>
        </Field>
        <Field label="Filial"><input className={inputCls} value={branch} onChange={e => setBranch(e.target.value)} placeholder="Cidade/UF" /></Field>
        <Field label="Início *"><input type="date" className={inputCls} value={startDate} onChange={e => { setStartDate(e.target.value); if (endDate < e.target.value) { setEndDate(e.target.value); recomputeExpiry(e.target.value, courseId); } }} /></Field>
        <Field label="Término *"><input type="date" className={inputCls} value={endDate} min={startDate} onChange={e => { setEndDate(e.target.value); recomputeExpiry(e.target.value, courseId); }} /></Field>
        <Field label="Local"><input className={inputCls} value={location} onChange={e => setLocation(e.target.value)} /></Field>
        <Field label="Modalidade">
          <select className={inputCls} value={modality} onChange={e => setModality(e.target.value as TrainingModality)}>
            {MODALITIES.map(m => <option key={m.id} value={m.id}>{m.label}</option>)}
          </select>
        </Field>
        <Field label="Carga horária (h) *"><input type="number" min={0.5} step={0.5} className={inputCls} value={workload} onChange={e => setWorkload(e.target.value)} /></Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Presença (%)"><input type="number" min={0} max={100} className={inputCls} value={attendance} onChange={e => setAttendance(e.target.value)} /></Field>
          <Field label="Nota" hint={course?.minGrade !== undefined ? `mínima ${course.minGrade}` : undefined}><input type="number" min={0} max={10} step={0.1} className={inputCls} value={grade} onChange={e => setGrade(e.target.value)} /></Field>
        </div>
        <Field label="Data de emissão"><input type="date" className={inputCls} value={issueDate} onChange={e => setIssueDate(e.target.value)} /></Field>
        <Field label="Vencimento" hint="Vazio = sem vencimento. Recalculado ao mudar o término ou o curso."><input type="date" className={inputCls} value={expiryDate} onChange={e => setExpiryDate(e.target.value)} /></Field>
        <div className="sm:col-span-2">
          <span className="block font-bold text-slate-700 mb-1 text-xs">Instrutor(es)</span>
          <div className="flex flex-wrap gap-2">
            {instructors.map(i => {
              const on = instructorIds.includes(i.id);
              return (
                <button key={i.id} type="button" onClick={() => setInstructorIds(on ? instructorIds.filter(x => x !== i.id) : [...instructorIds, i.id])}
                  className={`px-3 py-1.5 rounded-full border text-xs font-semibold ${on ? 'bg-blue-600 border-blue-600 text-white' : 'bg-white border-slate-300 text-slate-700'}`}>{i.name}</button>
              );
            })}
            {!instructors.length && <span className="text-[11px] text-amber-700">Nenhum instrutor cadastrado.</span>}
          </div>
        </div>
      </div>
      {course && course.minGrade !== undefined && grade !== '' && Number(grade.replace(',', '.')) < course.minGrade && (
        <p className="mt-3 text-[11px] text-amber-700 bg-amber-50 border border-amber-200 rounded-lg px-2 py-1">A nota está abaixo da mínima do curso ({course.minGrade}).</p>
      )}
    </Modal>
  );
};
