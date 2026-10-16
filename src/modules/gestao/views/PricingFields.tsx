/** Campos de entrada da planilha de precificação (usados no cadastro de serviços e nos itens do orçamento). */
import React from 'react';
import type { PricingInput, PricingReferenceValues } from '../types';
import { Field, NumberInput } from './ui';

export const PricingFields: React.FC<{
  input: PricingInput;
  costs: PricingReferenceValues;
  onChange: (input: PricingInput) => void;
  disabled?: boolean;
}> = ({ input, costs, onChange, disabled }) => {
  const set = <K extends keyof PricingInput>(k: K, v: PricingInput[K]) => onChange({ ...input, [k]: v });
  return (
    <fieldset disabled={disabled} className="space-y-3 min-w-0">
      <div className="grid grid-cols-2 sm:grid-cols-5 gap-2">
        <Field label="Dias trabalhados"><NumberInput value={input.days} step={0.5} onChange={v => set('days', v)} /></Field>
        <Field label="Ajudantes"><NumberInput value={input.helpers} step={1} onChange={v => set('helpers', Math.round(v))} /></Field>
        <Field label="Engenheiros"><NumberInput value={input.engineers} step={1} onChange={v => set('engineers', Math.round(v))} /></Field>
        <Field label="Dist. km/dia"><NumberInput value={input.kmPerDay} step={1} onChange={v => set('kmPerDay', v)} /></Field>
        <Field label="Visitas p/ orçamento"><NumberInput value={input.visits} step={1} onChange={v => set('visits', Math.round(v))} /></Field>
      </div>
      <div className="grid grid-cols-2 sm:grid-cols-5 gap-2">
        <Field label="Materiais (R$)" className="col-span-2 sm:col-span-1"
          hint={`+${costs.materialsTaxPercent}% imp. +${costs.materialsOtherPercent}% outras`}>
          <NumberInput value={input.materials} onChange={v => set('materials', v)} />
        </Field>
        <Field label="Locações (R$)"><NumberInput value={input.rentals} onChange={v => set('rentals', v)} /></Field>
        <Field label="Margem JVM (%)"><NumberInput value={input.marginPercent ?? costs.marginPercent} onChange={v => set('marginPercent', v)} /></Field>
        <Field label="Imposto (%)"><NumberInput value={input.taxPercent ?? costs.taxPercent} onChange={v => set('taxPercent', v)} /></Field>
        <Field label="% desconto"><NumberInput value={input.negotiationPercent ?? costs.negotiationPercent} onChange={v => set('negotiationPercent', v)} /></Field>
      </div>
    </fieldset>
  );
};
