import React, { useState } from 'react';
import { BookOpen, Plus, Pencil, Trash2, ArrowUp, ArrowDown, RotateCcw, Copy, ToggleLeft, ToggleRight, CheckCircle2 } from 'lucide-react';
import {
  activeCourseCertificates, canDeleteCourse, canEditTraining, deleteCourse, deleteCourseAndCancelCertificates, getClasses, getCourses, restoreDefaultCourses, saveCourse
} from '../repository';
import { formatHours, newId, totalTopicHours } from '../rules';
import type { TrainingCourse } from '../types';
import { btnPrimary, btnSecondary, cardCls, EmptyState, Field, inputCls, Modal } from './ui';
import { NormImportBox } from './NormImportBox';
import { proposalToCourse, type NormAnalysis, type NormCourseProposal } from '../normAnalysis';

// inputCls traz w-full, que numa linha flex esmaga o texto do tópico; aqui a largura é definida por campo
const rowInputCls = inputCls.replace('w-full ', '');

const emptyCourse = (): TrainingCourse => ({
  id: '', companyId: '', createdAt: '', updatedAt: '',
  code: '', name: '', normReference: '', workloadHours: 8, validityMonths: 24, modality: 'presencial',
  minAttendance: 100, minGrade: 7, topics: [{ title: '', hours: 8 }], prerequisite: '', notes: '', active: true
});

export const CoursesPanel: React.FC = () => {
  const [editing, setEditing] = useState<TrainingCourse | null>(null);
  const courses = getCourses();
  const canEdit = canEditTraining();

  const handleDelete = (c: TrainingCourse) => {
    const classes = getClasses().filter(t => t.courseId === c.id);
    const openClasses = classes.filter(t => t.status === 'planejada' || t.status === 'em_andamento').length;
    const certs = activeCourseCertificates(c.id);
    const lines = [`Excluir o curso "${c.name}"?`];
    if (classes.length || certs.length) {
      lines.push('');
      if (classes.length) lines.push(`• ${classes.length} turma(s) usam este curso${openClasses ? ` (${openClasses} ativa(s))` : ''}: elas continuam cadastradas, mas para emitir novos certificados será preciso editar a turma e escolher outro curso.`);
      if (certs.length) {
        lines.push(`• ${certs.length} certificado(s) emitido(s) serão CANCELADOS (o QR Code passa a mostrar "cancelado"):`);
        certs.slice(0, 10).forEach(x => lines.push(`    ${x.certificateNumber} — ${x.participantName}`));
        if (certs.length > 10) lines.push(`    … e mais ${certs.length - 10}`);
        lines.push('', 'Esta ação não pode ser desfeita.');
      }
    }
    if (!window.confirm(lines.join('\n'))) return;
    if (!certs.length) { deleteCourse(c.id); return; }
    const reason = window.prompt('Motivo do cancelamento (aparece no validador):', `Curso ${c.name} excluído`);
    if (reason === null) return;
    const cancelled = deleteCourseAndCancelCertificates(c.id, reason.trim() || `Curso ${c.name} excluído`);
    window.alert(`Curso "${c.name}" excluído e ${cancelled.length} certificado(s) cancelado(s).`);
  };

  const handleRestore = () => {
    const n = restoreDefaultCourses();
    window.alert(n ? `${n} curso(s) padrão recolocado(s).` : 'Os cursos padrão já estão cadastrados.');
  };

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-xs text-slate-500">Cursos usados nas turmas e certificados. O conteúdo programático é impresso no verso do certificado.</p>
        {canEdit && (
          <div className="flex gap-2">
            <button type="button" className={btnSecondary} onClick={handleRestore}><RotateCcw className="w-3.5 h-3.5" /> Cursos padrão</button>
            <button type="button" className={btnPrimary} onClick={() => setEditing(emptyCourse())}><Plus className="w-3.5 h-3.5" /> Novo curso</button>
          </div>
        )}
      </div>

      {courses.length === 0 ? (
        <EmptyState icon={BookOpen} title="Nenhum curso cadastrado" text="Cadastre os cursos oferecidos ou recoloque os cursos padrão (NR-10 Básico, NR-10 SEP, NR-35 e EPI/EPC isolantes)." />
      ) : (
        <div className="grid gap-3 md:grid-cols-2">
          {courses.map(c => {
            const topicsTotal = totalTopicHours(c.topics);
            return (
              <div key={c.id} className={`${cardCls} p-4 ${c.active ? '' : 'opacity-60'}`}>
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0">
                    <span className="text-[10px] font-bold text-orange-600 uppercase tracking-wide">{c.code || 'Curso'}{c.active ? '' : ' · inativo'}</span>
                    <h4 className="font-bold text-sm text-slate-900 leading-snug">{c.name}</h4>
                    <p className="text-[11px] text-slate-500 mt-0.5">{c.normReference}</p>
                  </div>
                  {canEdit && (
                    <div className="flex gap-1 shrink-0">
                      <button
                        type="button"
                        title={c.active ? 'Desabilitar curso (deixa de aparecer para novas turmas)' : 'Habilitar curso'}
                        className={`p-1.5 rounded-lg ${c.active ? 'hover:bg-slate-100 text-emerald-600' : 'hover:bg-emerald-50 text-slate-400'}`}
                        onClick={() => saveCourse({ ...c, active: !c.active })}
                      >
                        {c.active ? <ToggleRight className="w-4 h-4" /> : <ToggleLeft className="w-4 h-4" />}
                      </button>
                      <button type="button" title="Duplicar" className="p-1.5 rounded-lg hover:bg-slate-100 text-slate-500" onClick={() => setEditing({ ...c, id: '', code: `${c.code} (cópia)`, topics: c.topics.map(t => ({ ...t })), serverUpdatedAt: undefined })}><Copy className="w-4 h-4" /></button>
                      <button type="button" title="Editar" className="p-1.5 rounded-lg hover:bg-slate-100 text-slate-500" onClick={() => setEditing({ ...c, topics: c.topics.map(t => ({ ...t })) })}><Pencil className="w-4 h-4" /></button>
                      {canDeleteCourse() && <button type="button" title="Excluir curso" className="p-1.5 rounded-lg hover:bg-red-50 text-red-600" onClick={() => handleDelete(c)}><Trash2 className="w-4 h-4" /></button>}
                    </div>
                  )}
                </div>
                <div className="flex flex-wrap gap-x-4 gap-y-1 mt-2 text-[11px] text-slate-600">
                  <span><b>{formatHours(c.workloadHours)}</b> de carga horária</span>
                  <span>Validade: <b>{c.validityMonths ? `${c.validityMonths} meses` : 'sem vencimento'}</b></span>
                  <span>Aprovação: presença ≥ {c.minAttendance}%{c.minGrade !== undefined && c.minGrade !== null ? `, nota ≥ ${c.minGrade}` : ''}</span>
                  <span>{c.topics.length} tópico(s)</span>
                </div>
                {c.topics.length > 0 && (
                  <details className="mt-2">
                    <summary className="cursor-pointer text-[11px] font-semibold text-blue-700 hover:underline select-none">Ver conteúdo programático</summary>
                    <ol className="mt-1.5 space-y-1 text-[11px] text-slate-700">
                      {c.topics.map((t, i) => (
                        <li key={i} className="flex gap-2">
                          <span className="font-bold text-slate-400 w-4 shrink-0 text-right">{i + 1}</span>
                          <span className="flex-1 min-w-0 whitespace-pre-line break-words">{t.title}</span>
                          <span className="shrink-0 text-slate-500">{formatHours(Number(t.hours) || 0)}</span>
                        </li>
                      ))}
                    </ol>
                    {canEdit && (
                      <button type="button" className="mt-1.5 text-[11px] font-semibold text-blue-700 hover:underline" onClick={() => setEditing({ ...c, topics: c.topics.map(t => ({ ...t })) })}>Editar conteúdo</button>
                    )}
                  </details>
                )}
                {topicsTotal !== Number(c.workloadHours) && (
                  <p className="mt-2 text-[11px] text-amber-700 bg-amber-50 border border-amber-200 rounded-lg px-2 py-1">
                    A soma dos tópicos ({formatHours(topicsTotal)}) é diferente da carga horária do curso.
                  </p>
                )}
              </div>
            );
          })}
        </div>
      )}

      {editing && (
        <CourseEditor
          course={editing}
          onClose={() => setEditing(null)}
          onDelete={editing.id && canDeleteCourse() ? () => {
            handleDelete(editing);
            if (!getCourses().some(x => x.id === editing.id)) setEditing(null);
          } : undefined}
        />
      )}
    </div>
  );
};

const CourseEditor: React.FC<{ course: TrainingCourse; onClose: () => void; onDelete?: () => void }> = ({ course, onClose, onDelete }) => {
  const [c, setC] = useState<TrainingCourse>(course);
  const [filledFrom, setFilledFrom] = useState('');
  // cursos propostos pela IA a partir da norma: um por aba, salvos um a um
  const [drafts, setDrafts] = useState<Array<{ course: TrainingCourse; proposal: NormCourseProposal; saved: boolean }> | null>(null);
  const [tab, setTab] = useState(0);

  const applyAnalysis = (a: NormAnalysis, source: string) => {
    if ((course.id || c.name.trim()) && !window.confirm(`Substituir os dados do formulário pelo${a.courses.length > 1 ? 's' : ''} curso${a.courses.length > 1 ? 's' : ''} proposto${a.courses.length > 1 ? 's' : ''} a partir da norma?`)) return false;
    // o 1º curso ocupa este formulário (inclusive um curso em edição); os demais são cursos novos
    const blank = emptyCourse();
    const list = a.courses.map((p, i) => ({ proposal: p, saved: false, course: proposalToCourse(p, i === 0 ? c : blank) }));
    setDrafts(list);
    setTab(0);
    setC(list[0].course);
    setFilledFrom(source);
    return true;
  };

  /** Troca de aba guardando o que foi editado na aba atual. */
  const openTab = (i: number) => {
    if (!drafts || i === tab) return;
    setDrafts(drafts.map((d, idx) => (idx === tab ? { ...d, course: c } : d)));
    setTab(i);
    setC(drafts[i].course);
  };
  const set =<K extends keyof TrainingCourse>(k: K, v: TrainingCourse[K]) => setC(prev => ({ ...prev, [k]: v }));
  const total = totalTopicHours(c.topics);

  const setTopic = (i: number, patch: Partial<TrainingCourse['topics'][number]>) =>
    set('topics', c.topics.map((t, idx) => (idx === i ? { ...t, ...patch } : t)));
  const moveTopic = (i: number, dir: -1 | 1) => {
    const j = i + dir;
    if (j < 0 || j >= c.topics.length) return;
    const topics = [...c.topics];
    [topics[i], topics[j]] = [topics[j], topics[i]];
    set('topics', topics);
  };

  const handleSave = () => {
    if (!c.name.trim()) return window.alert('Informe o nome do curso.');
    if (!(Number(c.workloadHours) > 0)) return window.alert('Informe a carga horária.');
    const topics = c.topics.filter(t => t.title.trim()).map(t => ({ title: t.title.trim(), hours: Number(t.hours) || 0 }));
    if (!topics.length) return window.alert('Inclua pelo menos um tópico no conteúdo programático.');
    const id = c.id || newId('crs');
    saveCourse({
      ...c,
      id,
      name: c.name.trim(),
      code: c.code.trim(),
      workloadHours: Number(c.workloadHours),
      validityMonths: Number(c.validityMonths) || 0,
      minAttendance: Math.min(100, Math.max(0, Number(c.minAttendance) || 0)),
      minGrade: c.minGrade === undefined || c.minGrade === null || (c.minGrade as any) === '' ? undefined : Number(c.minGrade),
      topics
    });
    if (drafts && drafts.length > 1) {
      // vários cursos da norma: marca esta aba como salva e abre a próxima pendente
      const next = drafts.map((d, idx) => (idx === tab ? { ...d, course: { ...c, id }, saved: true } : d));
      setDrafts(next);
      const pending = next.findIndex(d => !d.saved);
      if (pending >= 0) { setTab(pending); setC(next[pending].course); return; }
    }
    onClose();
  };

  return (
    <Modal
      title={course.id ? 'Editar curso' : 'Novo curso'}
      onClose={onClose}
      wide
      footer={<>
        {onDelete && <button type="button" className={`${btnSecondary} mr-auto text-red-600 hover:bg-red-50`} onClick={onDelete}><Trash2 className="w-3.5 h-3.5" /> Excluir curso</button>}
        <button type="button" className={btnSecondary} onClick={() => {
          const unsaved = drafts ? drafts.filter(d => !d.saved).length : 0;
          if (drafts && drafts.length > 1 && unsaved && !window.confirm(`${unsaved} curso(s) proposto(s) ainda não foram salvos e serão descartados. Fechar mesmo assim?`)) return;
          onClose();
        }}>{drafts?.some(d => d.saved) ? 'Fechar' : 'Cancelar'}</button>
        <button type="button" className={btnPrimary} onClick={handleSave} disabled={!!drafts?.[tab]?.saved}>
          {drafts && drafts.length > 1 ? (drafts[tab].saved ? 'Curso salvo' : `Salvar este curso (${tab + 1} de ${drafts.length})`) : 'Salvar curso'}
        </button>
      </>}
    >
      <NormImportBox onAnalyzed={applyAnalysis} active={drafts?.[tab]?.proposal} />
      {drafts && drafts.length > 1 && (
        <div className="mb-3 flex flex-wrap gap-1.5" role="tablist" aria-label="Cursos propostos a partir da norma">
          {drafts.map((d, i) => (
            <button key={i} type="button" role="tab" aria-selected={i === tab} onClick={() => openTab(i)}
              className={`px-3 py-1.5 rounded-lg border text-[11px] font-semibold inline-flex items-center gap-1.5 ${i === tab ? 'bg-violet-600 border-violet-600 text-white' : 'bg-white border-slate-300 text-slate-700 hover:bg-slate-50'}`}>
              {d.saved && <CheckCircle2 className={`w-3.5 h-3.5 ${i === tab ? 'text-white' : 'text-emerald-600'}`} />}
              {i + 1}. {(i === tab ? c.code : d.course.code) || d.proposal.name}
            </button>
          ))}
        </div>
      )}
      {filledFrom && (
        <p className="mb-3 text-[11px] text-violet-900 bg-violet-50 border border-violet-200 rounded-lg px-2.5 py-1.5">
          {drafts?.[tab]?.saved
            ? 'Este curso já foi salvo. Alterações aqui não são gravadas: edite o curso pela lista depois.'
            : `Preenchido pela IA a partir da ${filledFrom}. Confira carga horária, validade, nota e o conteúdo programático com a norma e salve${drafts && drafts.length > 1 ? ' cada curso' : ''}.`}
        </p>
      )}
      <div className="grid gap-3 sm:grid-cols-4">
        <Field label="Sigla" className="sm:col-span-1"><input className={inputCls} value={c.code} onChange={e => set('code', e.target.value)} placeholder="NR-10 BÁSICO" /></Field>
        <Field label="Nome do curso *" className="sm:col-span-3"><input className={inputCls} value={c.name} onChange={e => set('name', e.target.value)} /></Field>
        <Field label="Norma / referência legal" className="sm:col-span-4"><input className={inputCls} value={c.normReference} onChange={e => set('normReference', e.target.value)} placeholder="NR-10, item 10.8.8" /></Field>
        <Field label="Carga horária (h) *"><input type="number" min={0.5} step={0.5} className={inputCls} value={c.workloadHours} onChange={e => set('workloadHours', e.target.value as any)} /></Field>
        <Field label="Validade (meses)" hint="0 = sem vencimento"><input type="number" min={0} className={inputCls} value={c.validityMonths} onChange={e => set('validityMonths', e.target.value as any)} /></Field>
        <Field label="Presença mínima (%)"><input type="number" min={0} max={100} className={inputCls} value={c.minAttendance} onChange={e => set('minAttendance', e.target.value as any)} /></Field>
        <Field label="Nota mínima (0–10)" hint="vazio = sem avaliação"><input type="number" min={0} max={10} step={0.1} className={inputCls} value={c.minGrade ?? ''} onChange={e => set('minGrade', (e.target.value === '' ? undefined : e.target.value) as any)} /></Field>
        <Field label="Modalidade">
          <select className={inputCls} value={c.modality} onChange={e => set('modality', e.target.value as any)}>
            <option value="presencial">Presencial</option>
            <option value="semipresencial">Semipresencial</option>
            <option value="ead">A distância (EAD)</option>
          </select>
        </Field>
        <Field label="Pré-requisito" className="sm:col-span-3"><input className={inputCls} value={c.prerequisite || ''} onChange={e => set('prerequisite', e.target.value)} placeholder="Ex.: NR-10 Básico válido" /></Field>
        <Field label="Observações impressas no verso" className="sm:col-span-4"><textarea rows={2} className={inputCls} value={c.notes || ''} onChange={e => set('notes', e.target.value)} /></Field>
        <label className="flex items-center gap-2 text-xs font-semibold text-slate-700 sm:col-span-4">
          <input type="checkbox" checked={c.active} onChange={e => set('active', e.target.checked)} /> Curso ativo (aparece para novas turmas)
        </label>
      </div>

      <div className="mt-4">
        <div className="flex items-center justify-between mb-2">
          <h4 className="font-bold text-xs text-slate-800">Conteúdo programático</h4>
          <span className={`text-[11px] font-semibold ${total === Number(c.workloadHours) ? 'text-emerald-700' : 'text-amber-700'}`}>
            Soma: {formatHours(total)} de {formatHours(Number(c.workloadHours))}
          </span>
        </div>
        <div className="space-y-2">
          {c.topics.map((t, i) => (
            <div key={i} className="flex gap-2 items-start">
              <span className="text-[11px] font-bold text-slate-400 w-5 pt-2 text-right">{i + 1}</span>
              <textarea rows={Math.min(6, Math.max(2, Math.ceil(t.title.length / 80)))} className={`${rowInputCls} flex-1 min-w-0 resize-y`} value={t.title} onChange={e => setTopic(i, { title: e.target.value })} placeholder="Tópico" />
              <input type="number" min={0} step={0.5} className={`${rowInputCls} w-20 shrink-0`} value={t.hours} onChange={e => setTopic(i, { hours: e.target.value as any })} title="Horas" />
              <div className="flex flex-col">
                <button type="button" className="p-1 text-slate-400 hover:text-slate-700" onClick={() => moveTopic(i, -1)} aria-label="Subir"><ArrowUp className="w-3.5 h-3.5" /></button>
                <button type="button" className="p-1 text-slate-400 hover:text-slate-700" onClick={() => moveTopic(i, 1)} aria-label="Descer"><ArrowDown className="w-3.5 h-3.5" /></button>
              </div>
              <button type="button" className="p-1.5 text-red-500 hover:bg-red-50 rounded-lg" onClick={() => set('topics', c.topics.filter((_, idx) => idx !== i))} aria-label="Remover tópico"><Trash2 className="w-3.5 h-3.5" /></button>
            </div>
          ))}
        </div>
        <button type="button" className={`${btnSecondary} mt-2`} onClick={() => set('topics', [...c.topics, { title: '', hours: 1 }])}><Plus className="w-3.5 h-3.5" /> Tópico</button>
        {course.id && <p className="text-[10px] text-slate-400 mt-3">Alterações valem para os próximos certificados. Os já emitidos guardam o conteúdo da época.</p>}
      </div>
    </Modal>
  );
};
