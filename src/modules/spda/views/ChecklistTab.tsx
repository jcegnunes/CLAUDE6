/** Aba "Inspeção visual": checklist C / NC / NA por subsistema, com observação e fotos. */
import React, { useRef, useState } from 'react';
import { Camera, CopyPlus, PenLine, X } from 'lucide-react';
import { PhotoEditorModal } from '../../../components/PhotoEditorModal';
import { CHECKLIST_TEMPLATE, SUBSYSTEM_LABEL, SUBSYSTEM_ORDER } from '../checklist';
import { answerOf, copyChecklistAnswers, isPerBuilding } from '../rules';
import type { ChecklistAnswer, ChecklistStatus, SpdaSubsystem } from '../types';
import { CHECK_OPTIONS, Modal, alertError, btnPrimary, btnSecondary, cardCls, inputCls } from './ui';
import { confirmAsk } from './dialogs';
import { compressImage } from '../../../utils/imageCompressor';
import { autoSavePhotos, usePhotoFolder } from '../photoFolder';

const MAX_PHOTOS_PER_ITEM = 4;

type Props = {
  checklist: ChecklistAnswer[]; onChange: (next: ChecklistAnswer[]) => void; readOnly: boolean;
  /** Mostra só estes subsistemas (telas Captação, Descida, Aterramento, DPS) */
  only?: SpdaSubsystem[];
  /** Edificação inspecionada (itens por edificação; a documentação é geral da OS) */
  buildingId?: string;
  /** Edificações da inspeção: com mais de uma, aparece "Copiar checklist de outra edificação" */
  buildings?: Array<{ id: string; name?: string }>;
};

export const ChecklistTab: React.FC<Props> = ({ checklist, onChange, readOnly, only, buildingId, buildings = [] }) => {
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
  const [editing, setEditing] = useState<{ itemId: string; idx: number } | null>(null);
  const folder = usePhotoFolder();
  const [openObs, setOpenObs] = useState<Set<string>>(new Set());

  // ---- copiar checklist de outra edificação (só itens por edificação; documentação é geral)
  const perBuildingIds = items.filter(i => isPerBuilding(i.subsystem)).map(i => i.id);
  const answeredIn = (bid: string) => checklist.filter(a => a.buildingId === bid && a.status && perBuildingIds.includes(a.itemId)).length;
  const sources = buildingId ? buildings.filter(b => b.id !== buildingId) : [];
  const [copy, setCopy] = useState<{ from: string; obs: boolean; photos: boolean } | null>(null);
  const nameOf = (bid?: string) => buildings.find(b => b.id === bid)?.name || 'Edificação';
  const openCopy = () => setCopy({ from: (sources.find(b => answeredIn(b.id) > 0) || sources[0])?.id || '', obs: true, photos: false });
  const doCopy = async () => {
    if (!copy || !buildingId || !copy.from) return;
    const n = answeredIn(copy.from);
    if (answeredIn(buildingId) > 0 && !(await confirmAsk(
      `${nameOf(buildingId)} já tem ${answeredIn(buildingId)} item(ns) respondido(s) nesta tela. Substituir pelas respostas de ${nameOf(copy.from)} (${n} item(ns))?`,
      { confirmLabel: 'Substituir', danger: true }
    ))) return;
    onChange(copyChecklistAnswers(checklist, perBuildingIds, copy.from, buildingId, { obs: copy.obs, photos: copy.photos }));
    setCopy(null);
  };

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
      const now = new Date().toISOString();
      autoSavePhotos(folder, (compressed as string[]).map(url => ({ url, caption: `Inspeção visual ${target}`, timestamp: now })));
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
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="text-xs text-slate-600">
          {prog.answered} de {prog.total} itens respondidos · <span className={prog.nc ? 'text-red-700 font-bold' : ''}>{prog.nc} não conforme(s)</span>
          <span className="text-slate-400"> · C = conforme, NC = não conforme, NA = não aplicável</span>
        </div>
        {sources.length > 0 && perBuildingIds.length > 0 && (
          <button type="button" className={btnSecondary} disabled={readOnly || !sources.some(b => answeredIn(b.id) > 0)}
            title={sources.some(b => answeredIn(b.id) > 0) ? 'Copiar as respostas do checklist de outra edificação' : 'Nenhuma outra edificação tem itens respondidos nesta tela'}
            onClick={openCopy}>
            <CopyPlus className="w-3.5 h-3.5" /> Copiar checklist de outra edificação
          </button>
        )}
      </div>

      {copy && (
        <Modal title={`Copiar checklist para ${nameOf(buildingId)}`} onClose={() => setCopy(null)}
          footer={<>
            <button type="button" className={btnSecondary} onClick={() => setCopy(null)}>Cancelar</button>
            <button type="button" className={btnPrimary} disabled={!copy.from || answeredIn(copy.from) === 0} onClick={doCopy}><CopyPlus className="w-3.5 h-3.5" /> Copiar</button>
          </>}>
          <div className="space-y-3 text-xs">
            <p className="text-slate-600">Copia as respostas (C, NC, NA) dos itens desta tela ({subsystems.map(s => SUBSYSTEM_LABEL[s]).join(', ')}) da edificação escolhida para <b>{nameOf(buildingId)}</b>. Depois é possível ajustar item por item.</p>
            <div className="space-y-1.5" role="radiogroup" aria-label="Copiar de">
              {sources.map(b => {
                const n = answeredIn(b.id);
                return (
                  <label key={b.id} className={`flex items-center gap-2 border rounded-xl px-3 py-2 ${copy.from === b.id ? 'border-blue-600 bg-blue-50' : 'border-slate-200'} ${n ? 'cursor-pointer' : 'opacity-50'}`}>
                    <input type="radio" name="copy-from" disabled={!n} checked={copy.from === b.id} onChange={() => setCopy({ ...copy, from: b.id })} />
                    <span className="font-semibold flex-1">{b.name || 'Edificação'}</span>
                    <span className="text-slate-500">{n} item(ns) respondido(s)</span>
                  </label>
                );
              })}
            </div>
            <label className="flex items-center gap-2"><input type="checkbox" checked={copy.obs} onChange={e => setCopy({ ...copy, obs: e.target.checked })} /> Copiar também as observações</label>
            <label className="flex items-center gap-2"><input type="checkbox" checked={copy.photos} onChange={e => setCopy({ ...copy, photos: e.target.checked })} /> Copiar também as fotos</label>
            {answeredIn(buildingId || '') > 0 && <p className="text-amber-700">As respostas atuais de {nameOf(buildingId)} nesta tela serão substituídas.</p>}
          </div>
        </Modal>
      )}

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
                            <>
                              <button
                                type="button"
                                aria-label="Editar foto (girar/marcar)"
                                title="Girar / marcar / texto"
                                className="absolute -bottom-1.5 -right-1.5 bg-white border border-slate-300 rounded-full p-0.5 text-slate-600 hover:text-blue-600"
                                onClick={() => setEditing({ itemId: item.id, idx })}
                              ><PenLine className="w-3 h-3" /></button>
                              <button
                                type="button"
                                aria-label="Remover foto"
                                className="absolute -top-1.5 -right-1.5 bg-white border border-slate-300 rounded-full p-0.5 text-slate-600 hover:text-red-600"
                                onClick={() => update(item.id, { photos: a.photos!.filter((_, i) => i !== idx) })}
                              ><X className="w-3 h-3" /></button>
                            </>
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

      {editing && (() => {
        const src = get(editing.itemId).photos?.[editing.idx];
        if (!src) return null;
        return (
          <PhotoEditorModal
            src={src}
            title="Editar foto — inspeção visual"
            onClose={() => setEditing(null)}
            onSave={dataUrl => {
              const cur = get(editing.itemId).photos || [];
              update(editing.itemId, { photos: cur.map((x, i) => (i === editing.idx ? dataUrl : x)) });
              setEditing(null);
            }}
          />
        );
      })()}
    </div>
  );
};
