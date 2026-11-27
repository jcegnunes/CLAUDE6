/**
 * Tela "Dados relatório" do menu SPDA: a mesma tela de Dados que aparece fora do
 * passo a passo da inspeção (cliente, edificações, características do SPDA,
 * instrumentos, responsável e documentação geral da OS), agora acessível direto
 * pelo menu — escolhe-se a OS/inspeção e edita-se os dados que saem no laudo e
 * no relatório. Os dados são os mesmos da inspeção (aba Dados).
 */
import React from 'react';
import { ClipboardList } from 'lucide-react';
import { canEditSpda, saveInspection } from '../repository';
import type { SpdaInspection } from '../types';
import { GeneralTab } from './GeneralTab';
import { ChecklistTab } from './ChecklistTab';
import { InspectionPicker, useSelectedInspection } from './InspectionPicker';
import { EmptyState, alertError } from './ui';
import { PhotoFolderProvider, photoFolderOf } from '../photoFolder';

export const SpdaDataView: React.FC = () => {
  const { inspections, selected, select } = useSelectedInspection();
  const readOnly = !canEditSpda() || selected?.status === 'concluido';

  const save = (insp: SpdaInspection) => {
    try { saveInspection(insp); } catch (err) { alertError(err, 'Não foi possível salvar'); }
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-3">
        <div className="w-10 h-10 rounded-xl bg-slate-700 text-white flex items-center justify-center"><ClipboardList className="w-5 h-5" /></div>
        <div>
          <h2 className="text-lg font-black text-slate-900">Dados do relatório</h2>
          <p className="text-xs text-slate-500">Cliente, edificações, características do SPDA, instrumentos e responsável — os dados que saem no laudo e no relatório técnico.</p>
        </div>
      </div>

      {inspections.length === 0 ? (
        <EmptyState icon={ClipboardList} title="Nenhuma inspeção de SPDA" text="Crie uma inspeção em Inspeções e laudos (ou em Edificações) para preencher os dados do relatório." />
      ) : (
        <>
          <InspectionPicker inspections={inspections} selected={selected} select={select} />
          {selected && (
            <>
              <GeneralTab
                key={`dados_${selected.id}`}
                value={selected}
                onChange={patch => save({ ...selected, ...patch })}
                readOnly={readOnly}
              />
              <div className="space-y-2">
                <h3 className="font-bold text-sm text-slate-900 pt-2">Documentação do SPDA <span className="font-normal text-slate-500">· checklist geral da OS</span></h3>
                <PhotoFolderProvider value={photoFolderOf(selected, undefined, 'Documentação')}>
                  <ChecklistTab
                    key={`doc_${selected.id}`}
                    only={['documentacao']}
                    checklist={selected.checklist}
                    readOnly={readOnly}
                    onChange={checklist => save({ ...selected, checklist })}
                  />
                </PhotoFolderProvider>
              </div>
            </>
          )}
        </>
      )}
    </div>
  );
};
