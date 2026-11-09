/** Aba "Inspeção visual": checklist C / NC / NA por subsistema, com observação e fotos. */
import React, { useRef, useState } from 'react';
import { Camera, X } from 'lucide-react';
import { CHECKLIST_TEMPLATE, SUBSYSTEM_LABEL, SUBSYSTEM_ORDER } from '../checklist';
import { answerOf, isPerBuilding } from '../rules';
import type { ChecklistAnswer, ChecklistStatus, SpdaSubsystem } from '../types';
import { CHECK_OPTIONS, alertError, btnSecondary, cardCls, inputCls } from './ui';
import { compressImage } from '../../../utils/imageCompressor';

const MAX_PHOTOS_PER_ITEM = 4;

type Props = {
  checklist: ChecklistAnswer[]; onChange: (next: ChecklistAnswer[]) => void; readOnly: boolean;
  /** Mostra só estes subsistemas (telas Captação, Descida, Aterramento, DPS) */
  only?: SpdaSubsystem[];
  /** Edificação inspecionada (itens por edificação; a documentação é geral da OS) */
  buildingId?: string;
};

export const ChecklistTab: React.FC<Props> = ({ checklist, onChange, readOnly, only, buildingId }) => {
  const subsystems = only ? SUBSYSTEM_ORDER.filter(s => only.includes(s)) : SUBSYSTEM_ORDER;
  const items = CHECKLIST_TEMPLATE.filter(i => subsystems.includes(i.subsystem));
  // edificação de cada item: documentação é geral (sem edificação)
  const bidOf = (itemId: string) => {
    const it = CHECKLIST_TEMPLATE.find(i => i.id === itemId);
    return it && isPerBuilding(it.subsystem) ? buildingId : undefined;
  };
  const get = (itemId: string) => answerOf(checklist, itemId, bidOf(itemId));
  const same = (a: ChecklistAnswer, itemId: string) => a.itemId === itemId && (a.buildingId || undefined) === (bidOf(itemId) || undefined);
  const prog = {
    total: items.length,
    answered: items.filter(i => get(i.id).status !== '').length,
    nc: items.filter(i => get(i.id).status === 'NC').length
  };
  const fileRef = useRef<HTMLInputElement>(null);
  const [photoTarget, setPhotoTarget] = useState<string | null>(null);
  const [openObs, setOpenObs] = useState<Set<string>>(new Set());

  const update = (itemId: string, patch: Partial<ChecklistAnswer>) => {
    const next = { ...get(itemId), ...patch, buildingId: bidOf(itemId) };
    const exists = checklist.some(a => same(a, itemId));
    onChange(exists ? checklist.map(a => (same(a, itemId) ? next : a)) : [...checklist, next]);
  };

  const markAll = (subsystem: string, status: ChecklistStatus) => {
    const ids = new Set(CHECKLIST_TEMPLATE.filter(i => i.subsystem === subsystem).map(i => i.id));
    const kept = checklist.filter(a => !(ids.has(a.itemId) && same(a, a.itemId)));
    const changed = CHECKLIST_TEMPLATE.filter(i => ids.has(i.id)).map(i => {
      const a = { ...get(i.id), buildingId: bidOf(i.id) };
      return a.status ? a : { ...a, status };
    });
    onChange([...kept, ...changed]);
  };

  const onFiles = async (files: FileList | null) => {
    const target = photoTarget;
    if (!files || !target) return;
    try {
      const current = get(target).photos || [];
      const room = MAX_PHOTOS_PER_ITEM - current.length;
      const list = Array.from(files).slice(0, Math.max(0, room));
      const compressed = (await Promise.all(list.map(f => compressImage(f, 1024, 768, 0.7)))).filter(Boolean);
      update(target, { photos: [...current, ...compressed] });
    } catch (err) {
      alertError(err, 'Não foi possível adicionar a foto');
    } finally {
      setPhotoTarget(null);
      if (fileRef.current) fileRef.current.value = '';
    }
  };

  return (
    <div className="space-y-4">
      <input ref={fileRef} type="file" accept="image/*" capture="environment" multiple className="hidden" onChange={e => onFiles(e.target.files)} />
      <div className="text-xs text-slate-600">
        {prog.answered} de {prog.total} itens respondidos · <span className={prog.nc ? 'text-red-700 font-bold' : ''}>{prog.nc} não conforme(s)</span>
        <span className="text-slate-400"> · C = conforme, NC = não conforme, NA = não aplicável</span>
      </div>

      {subsystems.map(sub => (
        <section key={sub} className={cardCls}>
          <div className="flex flex-wrap items-center justify-between gap-2 px-4 py-2.5 border-b border-slate-200 bg-slate-50 rounded-t-2xl">
            <h3 className="font-bold text-sm text-slate-900">{SUBSYSTEM_LABEL[sub]}</h3>
            {!readOnly && (
              <button type="button" className={btnSecondary} onClick={() => markAll(sub, 'C')}>Marcar restantes como C</button>
            )}
          </div>
          <div className="divide-y divide-slate-100">
            {CHECKLIST_TEMPLATE.filter(i => i.subsystem === sub).map(item => {
              const a = get(item.id);
              return (
                <div key={item.id} className={`px-4 py-3 ${a.status === 'NC' ? 'bg-red-50/40' : ''}`}>
                  <div className="flex flex-col sm:flex-row sm:items-start gap-2">
                    <div className="flex-1 min-w-0">
                      <p className="text-xs text-slate-900"><span className="font-bold text-slate-500 mr-1">{item.id}</span>{item.title}</p>
                      <p className="text-[10px] text-slate-400 mt-0.5">{item.reference}</p>
                    </div>
                    <div className="flex gap-1 shrink-0" role="radiogroup" aria-label={`Situação ${item.id}`}>
                      {CHECK_OPTIONS.map(o => (
                        <button
                          key={o.v}
                          type="button"
                          role="radio"
                          aria-checked={a.status === o.v}
                          disabled={readOnly}
                          onClick={() => update(item.id, { status: a.status === o.v ? '' : o.v })}
                          className={`w-11 py-1.5 rounded-lg border text-[11px] font-bold transition-colors ${a.status === o.v ? o.on : 'bg-white text-slate-600 border-slate-300 hover:bg-slate-50'}`}
                        >{o.label}</button>
                      ))}
                      <button
                        type="button"
                        disabled={readOnly || (a.photos?.length || 0) >= MAX_PHOTOS_PER_ITEM}
                        onClick={() => { setPhotoTarget(item.id); fileRef.current?.click(); }}
                        className="px-2 py-1.5 rounded-lg border border-slate-300 bg-white text-slate-600 hover:bg-slate-50 disabled:opacity-40"
                        aria-label={`Adicionar foto ${item.id}`}
                        title="Adicionar foto"
                      ><Camera className="w-3.5 h-3.5" /></button>
                    </div>
                  </div>
                  {(a.status === 'NC' || a.obs || openObs.has(item.id)) && (
                    <textarea
                      className={`${inputCls} mt-2`}
                      rows={2}
                      disabled={readOnly}
                      placeholder={a.status === 'NC' ? 'Descreva a não conformidade (local, situação encontrada)…' : 'Observação'}
                      value={a.obs || ''}
                      onChange={e => update(item.id, { obs: e.target.value })}
                    />
                  )}
                  {a.status !== 'NC' && !a.obs && !openObs.has(item.id) && !readOnly && (
                    <button type="button" className="text-[11px] text-blue-700 mt-1 hover:underline" onClick={() => setOpenObs(prev => new Set(prev).add(item.id))}>+ observação</button>
                  )}
                  {!!a.photos?.length && (
                    <div className="flex flex-wrap gap-2 mt-2">
                      {a.photos.map((p, idx) => (
                        <div key={idx} className="relative">
                          <img src={p} alt={`Foto ${idx + 1} de ${item.id}`} className="w-20 h-16 object-cover rounded-lg border border-slate-200" />
                          {!readOnly && (
                            <button
                              type="button"
                              aria-label="Remover foto"
                              className="absolute -top-1.5 -right-1.5 bg-white border border-slate-300 rounded-full p-0.5 text-slate-600 hover:text-red-600"
                              onClick={() => update(item.id, { photos: a.photos!.filter((_, i) => i !== idx) })}
                            ><X className="w-3 h-3" /></button>
                          )}
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </section>
      ))}
    </div>
  );
};
