/** Configuração do Sistema → Custos e despesas: valores de referência da precificação e do financeiro. */
import React, { useState } from 'react';
import { Plus, RotateCcw, Save, X } from 'lucide-react';
import { canManageFinance, canPrice, getSettings, hasSavedSettings, saveSettings } from '../repository';
import { DEFAULT_REFERENCE_VALUES, formatMoney, newId } from '../rules';
import type { GestaoSettings } from '../types';
import { Field, NoAccess, NumberInput, alertError, btnPrimary, btnSecondary, cardCls, inputCls } from './ui';

export const CostSettingsPanel: React.FC = () => {
  const [form, setForm] = useState<GestaoSettings>(() => getSettings());
  const [saved, setSaved] = useState(false);
  if (!canPrice()) return <NoAccess />;
  const canEdit = canManageFinance();
  const set = <K extends keyof GestaoSettings>(k: K, v: GestaoSettings[K]) => { setSaved(false); setForm(f => ({ ...f, [k]: v })); };

  const save = () => {
    try {
      if (!form.accounts.length || form.accounts.some(a => !a.name.trim())) throw new Error('Informe o nome de todas as contas.');
      const clean = (list: string[]) => Array.from(new Set(list.map(s => s.trim()).filter(Boolean)));
      saveSettings({ ...form, payableCategories: clean(form.payableCategories), receivableCategories: clean(form.receivableCategories) });
      setForm(getSettings());
      setSaved(true);
    } catch (err) { alertError(err); }
  };

  const fuelPerKm = form.kmPerLiter > 0 ? form.fuelPrice / form.kmPerLiter : 0;

  return (
    <div className="space-y-4">
      {!hasSavedSettings() && (
        <p className="text-[11px] text-blue-800 bg-blue-50 border border-blue-200 rounded-xl px-3 py-2">
          Valores de referência iniciais: ajuste aos custos reais da empresa e salve.
        </p>
      )}
      {!canEdit && (
        <p className="text-[11px] text-slate-600 bg-slate-50 border border-slate-200 rounded-xl px-3 py-2">Somente o administrador ou o perfil administrativo altera estes valores.</p>
      )}
      <fieldset disabled={!canEdit} className="space-y-4">
        <div className={`${cardCls} p-4 grid grid-cols-1 sm:grid-cols-2 gap-3`}>
          <div className="sm:col-span-2 flex flex-wrap items-center justify-between gap-2">
            <p className="font-black text-sm text-slate-900">Valores de referência (planilha de precificação)</p>
            <button type="button" className={btnSecondary} onClick={() => setForm(f => ({ ...f, ...DEFAULT_REFERENCE_VALUES }))}>
              <RotateCcw className="w-3.5 h-3.5" /> Valores da planilha
            </button>
          </div>
          <Field label="Valor da diária ajudante (R$)"><NumberInput value={form.helperDailyRate} onChange={v => set('helperDailyRate', v)} /></Field>
          <Field label="Valor da diária engenharia (R$)"><NumberInput value={form.engineerDailyRate} onChange={v => set('engineerDailyRate', v)} /></Field>
          <Field label="Valor da refeição (R$)"><NumberInput value={form.mealCost} onChange={v => set('mealCost', v)} /></Field>
          <Field label="Valor da passagem ida e volta (R$)" hint="Por ajudante, por dia"><NumberInput value={form.busFare} onChange={v => set('busFare', v)} /></Field>
          <Field label="Valor da gasolina (R$/litro)"><NumberInput value={form.fuelPrice} onChange={v => set('fuelPrice', v)} /></Field>
          <Field label="Consumo do veículo (km/litro)" hint={`${formatMoney(fuelPerKm)} de combustível por km`}><NumberInput value={form.kmPerLiter} step={0.1} onChange={v => set('kmPerLiter', v)} /></Field>
          <Field label="Valor da visita técnica / orçamento (R$)"><NumberInput value={form.technicalVisitCost} onChange={v => set('technicalVisitCost', v)} /></Field>
          <Field label="Custos fixos por serviço (R$)" hint="Contador, água, luz, uniforme, EPI"><NumberInput value={form.fixedCosts} onChange={v => set('fixedCosts', v)} /></Field>
          <Field label="Manutenção por km (R$/km)" hint="Seguro + manutenção + IPVA"><NumberInput value={form.vehicleCostPerKm} onChange={v => set('vehicleCostPerKm', v)} /></Field>
        </div>

        <div className={`${cardCls} p-4 grid grid-cols-2 sm:grid-cols-3 gap-3`}>
          <p className="col-span-2 sm:col-span-3 font-black text-sm text-slate-900">Percentuais</p>
          <Field label="Impostos materiais (%)"><NumberInput value={form.materialsTaxPercent} onChange={v => set('materialsTaxPercent', v)} /></Field>
          <Field label="Outras despesas materiais (%)"><NumberInput value={form.materialsOtherPercent} onChange={v => set('materialsOtherPercent', v)} /></Field>
          <Field label="Margem JVM (%)" hint="Sobre o subtotal operacional"><NumberInput value={form.marginPercent} onChange={v => set('marginPercent', v)} /></Field>
          <Field label="Imposto (%)" hint="Sobre subtotal + margem"><NumberInput value={form.taxPercent} onChange={v => set('taxPercent', v)} /></Field>
          <Field label="Percentual de desconto (%)" hint="Somado ao total da nota"><NumberInput value={form.negotiationPercent} onChange={v => set('negotiationPercent', v)} /></Field>
        </div>

        <div className={`${cardCls} p-4 space-y-2`}>
          <p className="font-black text-sm text-slate-900">Contas bancárias / caixa (saldo inicial do fluxo de caixa)</p>
          <Field label="Data dos saldos iniciais" className="max-w-xs" hint="Pagamentos anteriores a esta data não entram no saldo">
            <input type="date" className={inputCls} value={form.openingDate} onChange={e => set('openingDate', e.target.value)} />
          </Field>
          {form.accounts.map((a, i) => (
            <div key={a.id} className="flex gap-2 items-end">
              <Field label={i === 0 ? 'Conta' : ''} className="flex-1">
                <input className={inputCls} value={a.name} onChange={e => set('accounts', form.accounts.map((x, j) => j === i ? { ...x, name: e.target.value } : x))} />
              </Field>
              <Field label={i === 0 ? 'Saldo inicial (R$)' : ''} className="w-40">
                <NumberInput value={a.openingBalance} min={-1e12} onChange={v => set('accounts', form.accounts.map((x, j) => j === i ? { ...x, openingBalance: v } : x))} />
              </Field>
              <button type="button" className={`${btnSecondary} mb-0.5`} aria-label="Remover conta" disabled={form.accounts.length <= 1} onClick={() => set('accounts', form.accounts.filter((_, j) => j !== i))}><X className="w-3.5 h-3.5" /></button>
            </div>
          ))}
          <button type="button" className={btnSecondary} onClick={() => set('accounts', [...form.accounts, { id: newId('cta'), name: '', openingBalance: 0 }])}>
            <Plus className="w-3.5 h-3.5" /> Adicionar conta
          </button>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div className={`${cardCls} p-4`}>
            <Field label="Categorias de contas a pagar (uma por linha)">
              <textarea className={inputCls} rows={8} value={form.payableCategories.join('\n')} onChange={e => set('payableCategories', e.target.value.split('\n'))} />
            </Field>
          </div>
          <div className={`${cardCls} p-4`}>
            <Field label="Categorias de contas a receber (uma por linha)">
              <textarea className={inputCls} rows={8} value={form.receivableCategories.join('\n')} onChange={e => set('receivableCategories', e.target.value.split('\n'))} />
            </Field>
          </div>
        </div>
      </fieldset>

      {canEdit && (
        <div className="flex items-center justify-end gap-3">
          {saved && <span className="text-xs font-bold text-emerald-700">Configuração salva.</span>}
          <button type="button" className={btnPrimary} onClick={save}><Save className="w-4 h-4" /> Salvar configuração</button>
        </div>
      )}
    </div>
  );
};
