/**
 * Configuração do Sistema → Parâmetros do SPDA (só aparece com o módulo SPDA aberto).
 * Ficam nos dados da empresa e sincronizam com a plataforma: valem para todos os usuários e aparelhos.
 */
import React, { useState } from 'react';
import { Save, SlidersHorizontal } from 'lucide-react';
import { canEditSpda, listInspections, saveSpdaSettings, spdaSettings } from '../repository';
import { NUMBER_PREFIX, nextNumber } from '../rules';
import { Field, btnPrimary, cardCls, inputCls } from './ui';
import { notify } from './dialogs';

const VALIDITY: Array<{ v: 12 | 24 | 36; label: string }> = [
  { v: 12, label: '12 meses (1 ano)' },
  { v: 24, label: '24 meses (2 anos)' },
  { v: 36, label: '36 meses (3 anos)' }
];

export const SpdaSettingsView: React.FC = () => {
  const current = spdaSettings();
  const [prefix, setPrefix] = useState(current.numberPrefix || NUMBER_PREFIX);
  const [validity, setValidity] = useState<12 | 24 | 36>(current.defaultValidityMonths || 12);
  const [saved, setSaved] = useState(false);
  const canEdit = canEditSpda();
  const clean = prefix.toUpperCase().replace(/[^A-Z0-9-]/g, '');
  const valid = /^[A-Z0-9][A-Z0-9-]{0,14}$/.test(clean);
  const preview = valid ? nextNumber(listInspections().map(i => i.number), undefined, clean.endsWith('-') ? clean : `${clean}-`) : '';
  const dirty = clean !== (current.numberPrefix || NUMBER_PREFIX) || validity !== (current.defaultValidityMonths || 12);

  const save = () => {
    if (!valid) { void notify('Prefixo inválido: use letras, números e hífen (até 15 caracteres).'); return; }
    try {
      saveSpdaSettings({ numberPrefix: clean.endsWith('-') ? clean : `${clean}-`, defaultValidityMonths: validity });
      setSaved(true);
      setTimeout(() => setSaved(false), 5000);
    } catch (e) {
      void notify(`Não foi possível salvar: ${e instanceof Error ? e.message : String(e)}`);
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex items-start gap-3">
        <div className="w-10 h-10 rounded-xl bg-[#0A2540] text-white flex items-center justify-center shrink-0"><SlidersHorizontal className="w-5 h-5" /></div>
        <div>
          <h2 className="text-lg font-black text-slate-900">Parâmetros do SPDA</h2>
          <p className="text-xs text-slate-500">Valem para as inspeções novas de todos os usuários da empresa (sincronizam com a plataforma). Dados da empresa, responsável técnico e logotipo ficam em Configurações & Backup.</p>
        </div>
      </div>

      <section className={`${cardCls} p-4 space-y-4`}>
        <div className="grid gap-4 sm:grid-cols-2">
          <div>
            <Field label="Prefixo da numeração das inspeções e laudos">
              <input className={inputCls} value={prefix} disabled={!canEdit} onChange={e => setPrefix(e.target.value.toUpperCase())} maxLength={15} />
            </Field>
            <p className="text-[11px] text-slate-600 mt-1 px-1">
              Formato: prefixo + ano + sequência. {preview ? <>Próxima inspeção: <b className="font-mono">{preview}</b></> : <span className="text-red-700">Use letras, números e hífen.</span>}
            </p>
          </div>
          <div>
            <Field label="Validade padrão do laudo">
              <select className={inputCls} value={validity} disabled={!canEdit} onChange={e => setValidity(Number(e.target.value) as 12 | 24 | 36)}>
                {VALIDITY.map(o => <option key={o.v} value={o.v}>{o.label}</option>)}
              </select>
            </Field>
            <p className="text-[11px] text-slate-600 mt-1 px-1">Usada nas inspeções novas e na próxima inspeção indicada no laudo. Pode ser trocada em cada inspeção (Relatório → Validade do laudo).</p>
          </div>
        </div>
        <p className="text-[11px] text-slate-500">As inspeções já existentes mantêm o número e a validade que têm.</p>
        <div className="flex flex-wrap items-center justify-end gap-3">
          {saved && <span className="text-xs font-bold text-emerald-700">Parâmetros salvos.</span>}
          {!canEdit && <span className="text-xs text-slate-500">Somente administrador, responsável técnico ou técnico alteram.</span>}
          <button type="button" className={btnPrimary} disabled={!canEdit || !dirty || !valid} onClick={save}><Save className="w-4 h-4" /> Salvar parâmetros</button>
        </div>
      </section>
    </div>
  );
};
