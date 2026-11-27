/**
 * Registro fotográfico de NÃO CONFORMIDADES e MELHORIAS de cada subsistema (após os ensaios de continuidade):
 * cada registro tem tipo (não conformidade / melhoria), comentário digitado e fotos (câmera ou galeria).
 * Numeração automática por edificação e subsistema: CAP-NC-01, CAP-M-01…
 * As fotos entram no relatório fotográfico e no relatório técnico.
 */
import React from 'react';
import { AlertTriangle, Lightbulb, Plus, Trash2 } from 'lucide-react';
import { findingsOf, newId } from '../rules';
import type { FindingKind, SpdaFinding, SpdaSubsystem } from '../types';
import { TestPhotos } from './TestPhotos';
import { btnSecondary, cardCls, inputCls } from './ui';
import { confirmAsk } from './dialogs';

const KIND_INFO: Record<FindingKind, { label: string; icon: React.ElementType; pill: string; border: string; placeholder: string }> = {
  nc: {
    label: 'Não conformidade', icon: AlertTriangle, pill: 'bg-red-50 text-red-700 border-red-300', border: 'border-l-red-500',
    placeholder: 'Descreva a não conformidade: o que foi encontrado, onde, e o item da norma, se souber…'
  },
  melhoria: {
    label: 'Melhoria', icon: Lightbulb, pill: 'bg-sky-50 text-sky-700 border-sky-300', border: 'border-l-sky-500',
    placeholder: 'Descreva a melhoria recomendada…'
  }
};

export const FindingsSection: React.FC<{
  findings?: SpdaFinding[];
  onChange: (next: SpdaFinding[]) => void;
  subsystem: SpdaSubsystem;
  buildingId?: string;
  buildingName?: string;
  /** Nome do subsistema na tela (ex.: "Captação") */
  title: string;
  readOnly: boolean;
}> = ({ findings = [], onChange, subsystem, buildingId, buildingName, title, readOnly }) => {
  const mine = findingsOf({ findings }, buildingId, [subsystem]).filter(x => !buildingId || x.f.buildingId === buildingId);
  const nNc = mine.filter(x => x.f.kind === 'nc').length;
  const nM = mine.length - nNc;

  const add = (kind: FindingKind) => onChange([...findings, { id: newId('reg'), buildingId, subsystem, kind, comment: '', photos: [], createdAt: new Date().toISOString() }]);
  const update = (id: string, p: Partial<SpdaFinding>) => onChange(findings.map(f => (f.id === id ? { ...f, ...p } : f)));
  const remove = async (f: SpdaFinding, code: string) => {
    if (!(await confirmAsk(`Excluir o registro ${code}${f.photos.length ? ` e as ${f.photos.length} foto(s) dele` : ''}?`))) return;
    onChange(findings.filter(x => x.id !== f.id));
  };

  if (readOnly && mine.length === 0) return null;

  return (
    <section className="space-y-2" aria-label={`Não conformidades e melhorias — ${title}`}>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 className="font-bold text-sm text-slate-900">
          Registro fotográfico — não conformidades e melhorias
          <span className="font-normal text-teal-700"> · {buildingName || 'Edificação'}</span>
        </h3>
        {mine.length > 0 && (
          <span className="text-[11px] text-slate-500">
            {nNc > 0 && <b className="text-red-700">{nNc} não conformidade(s)</b>}{nNc > 0 && nM > 0 && ' · '}{nM > 0 && `${nM} melhoria(s)`}
          </span>
        )}
      </div>

      {mine.length === 0 && (
        <p className={`${cardCls} p-3 text-xs text-slate-500`}>
          Nenhum registro em {title}. Use os botões abaixo para fotografar cada não conformidade ou melhoria e digitar o comentário.
        </p>
      )}

      {mine.map(({ f, code }) => {
        const k = KIND_INFO[f.kind];
        const Icon = k.icon;
        return (
          <div key={f.id} className={`${cardCls} border-l-4 ${k.border} p-3 space-y-2`}>
            <div className="flex flex-wrap items-center gap-2">
              <span className="font-mono text-xs font-bold text-slate-800">{code}</span>
              {readOnly ? (
                <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full border text-[11px] font-bold ${k.pill}`}><Icon className="w-3 h-3" /> {k.label}</span>
              ) : (
                <div className="inline-flex rounded-full border border-slate-300 overflow-hidden" role="group" aria-label="Tipo do registro">
                  {(Object.keys(KIND_INFO) as FindingKind[]).map(kind => {
                    const I = KIND_INFO[kind].icon;
                    const on = f.kind === kind;
                    return (
                      <button key={kind} type="button" aria-pressed={on} onClick={() => update(f.id, { kind })}
                        className={`inline-flex items-center gap-1 px-2.5 py-1 text-[11px] font-bold ${on ? (kind === 'nc' ? 'bg-red-600 text-white' : 'bg-sky-600 text-white') : 'bg-white text-slate-600'}`}>
                        <I className="w-3 h-3" /> {KIND_INFO[kind].label}
                      </button>
                    );
                  })}
                </div>
              )}
              <span className="flex-1" />
              {!readOnly && (
                <button type="button" className="p-1.5 rounded-lg text-slate-400 hover:text-red-600 hover:bg-red-50" onClick={() => remove(f, code)} aria-label={`Excluir registro ${code}`}>
                  <Trash2 className="w-4 h-4" />
                </button>
              )}
            </div>
            <label className="block">
              <span className="block text-[11px] font-bold text-slate-700 mb-1">Comentário</span>
              <textarea
                className={inputCls}
                rows={3}
                value={f.comment}
                disabled={readOnly}
                placeholder={k.placeholder}
                onChange={e => update(f.id, { comment: e.target.value })}
                aria-label={`Comentário ${code}`}
              />
            </label>
            {(!readOnly || f.correctiveAction?.trim()) && (
              <label className="block">
                <span className="block text-[11px] font-bold text-slate-700 mb-1">
                  {f.kind === 'nc' ? 'Ação de correção' : 'Ação recomendada'} <span className="font-normal text-slate-400">(opcional)</span>
                </span>
                <textarea
                  className={inputCls}
                  rows={2}
                  value={f.correctiveAction || ''}
                  disabled={readOnly}
                  placeholder={f.kind === 'nc' ? 'Como corrigir esta não conformidade…' : 'Ação sugerida para a melhoria…'}
                  onChange={e => update(f.id, { correctiveAction: e.target.value })}
                  aria-label={`Ação de correção ${code}`}
                />
              </label>
            )}
            {(!readOnly || f.normClause?.trim()) && (
              <label className="block">
                <span className="block text-[11px] font-bold text-slate-700 mb-1">Item da norma <span className="font-normal text-slate-400">(opcional)</span></span>
                <input
                  className={inputCls}
                  value={f.normClause || ''}
                  disabled={readOnly}
                  placeholder="Ex.: NBR 5419-3:2026, 5.4.3"
                  onChange={e => update(f.id, { normClause: e.target.value })}
                  aria-label={`Item da norma ${code}`}
                />
              </label>
            )}
            <TestPhotos
              title="Fotos do registro"
              photos={f.photos}
              readOnly={readOnly}
              defaultCaption={`${code} — ${k.label}${f.comment.trim() ? `: ${f.comment.trim().slice(0, 80)}` : ''}`}
              onChange={photos => update(f.id, { photos })}
            />
          </div>
        );
      })}

      {!readOnly && (
        <div className="flex flex-wrap gap-2">
          <button type="button" className={`${btnSecondary} !border-red-300 !text-red-700`} onClick={() => add('nc')}>
            <Plus className="w-3.5 h-3.5" /> Não conformidade
          </button>
          <button type="button" className={`${btnSecondary} !border-sky-300 !text-sky-700`} onClick={() => add('melhoria')}>
            <Plus className="w-3.5 h-3.5" /> Melhoria
          </button>
        </div>
      )}
    </section>
  );
};
