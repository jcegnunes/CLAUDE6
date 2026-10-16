/**
 * Precificação de serviços no modelo da planilha "Precificação Serviços JVM":
 * homem-hora (ajudantes e engenheiros), refeição, passagem, combustível,
 * visitas, manutenção do veículo, custos fixos e materiais + margem JVM,
 * imposto e percentual de desconto. Aprovada, gera as contas a receber.
 */
import React, { useState } from 'react';
import { Calculator, CheckCircle2, Copy, FileText, Pencil, Plus, RefreshCw, Trash2 } from 'lucide-react';
import { navigateTo } from '../../registry';
import {
  approvePricing, canDeleteGestao, canManageFinance, canPrice, deletePricing, getClientsForGestao, getPricings,
  getServiceOrdersForGestao, getSettings, savePricing
} from '../repository';
import {
  addDays, calculatePricing, costsFromSettings, emptyPricingInput, formatDateBr, formatMoney, normalizePricingInput, pricingExpiryDate,
  realizedSummary, todayIso
} from '../rules';
import { exportProposalPdf } from '../proposalPdf';
import type { PricingBreakdown, PricingInput, PricingStatus, RealizedExpenses, ServicePricing } from '../types';
import {
  EmptyState, Field, Modal, NoAccess, NumberInput, PRICING_STATUS_LABEL, PricingStatusBadge, alertError, btnDanger, btnPrimary,
  btnSecondary, cardCls, inputCls
} from './ui';

function emptyPricing(): ServicePricing {
  const settings = getSettings();
  const input = emptyPricingInput();
  const costs = costsFromSettings(settings);
  return {
    id: '', companyId: '', createdAt: '', updatedAt: '',
    number: '', title: '', date: todayIso(), validityDays: 15, input, costsSnapshot: costs,
    result: calculatePricing(input, costs), status: 'rascunho', paymentTerms: ''
  };
}

export const PricingPanel: React.FC = () => {
  const [editing, setEditing] = useState<ServicePricing | null>(null);
  const [approving, setApproving] = useState<ServicePricing | null>(null);
  const [statusFilter, setStatusFilter] = useState<PricingStatus | ''>('');
  const [exporting, setExporting] = useState<string | null>(null);
  if (!canPrice()) return <NoAccess text="Precificação: acesso do administrador, administrativo e Responsável Técnico." />;

  const list = getPricings().filter(p => !statusFilter || p.status === statusFilter);
  const canDelete = canDeleteGestao();

  const duplicate = (p: ServicePricing) => setEditing({
    ...p, id: '', number: '', createdAt: '', updatedAt: '', serverUpdatedAt: undefined, date: todayIso(), status: 'rascunho',
    receivableGroupId: undefined, realized: undefined, title: `${p.title} (cópia)`, input: normalizePricingInput(p.input)
  });

  const exportProposal = async (p: ServicePricing) => {
    setExporting(p.id);
    try { await exportProposalPdf(p); } catch (err) { alertError(err); } finally { setExporting(null); }
  };

  return (
    <div className="space-y-3">
      <div className={`${cardCls} p-3 flex flex-wrap items-center justify-between gap-2`}>
        <div className="flex flex-wrap gap-1">
          {(['', 'rascunho', 'enviada', 'aprovada', 'recusada'] as Array<PricingStatus | ''>).map(s => (
            <button key={s || 'todas'} type="button" onClick={() => setStatusFilter(s)}
              className={`px-2.5 py-1 rounded-full text-[11px] font-bold border ${statusFilter === s ? 'bg-blue-600 text-white border-blue-600' : 'bg-white text-slate-600 border-slate-300'}`}>
              {s ? PRICING_STATUS_LABEL[s] : 'Todas'}
            </button>
          ))}
        </div>
        <button type="button" className={btnPrimary} onClick={() => setEditing(emptyPricing())}><Plus className="w-4 h-4" /> Nova precificação</button>
      </div>

      {list.length === 0 ? (
        <EmptyState icon={Calculator} title="Nenhuma precificação"
          text="Monte o preço como na planilha: dias, ajudantes, engenheiros, km/dia, visitas e materiais. Os valores de referência ficam em Configuração do Sistema → Custos e despesas." />
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-3">
          {list.map(p => {
            const expired = p.status !== 'aprovada' && p.status !== 'recusada' && pricingExpiryDate(p) < todayIso();
            return (
              <div key={p.id} className={`${cardCls} p-4 space-y-2`}>
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0">
                    <p className="text-[11px] font-mono text-slate-500">{p.number} · {formatDateBr(p.date)}</p>
                    <p className="font-bold text-sm text-slate-900 truncate">{p.title}</p>
                    <p className="text-[11px] text-slate-500 truncate">{p.clientName || 'Sem cliente'}</p>
                  </div>
                  <div className="text-right">
                    <PricingStatusBadge status={p.status} />
                    {expired && <p className="text-[10px] text-amber-700 font-bold mt-0.5">Validade vencida</p>}
                  </div>
                </div>
                <div className="grid grid-cols-3 gap-2 text-center">
                  <div className="p-2 rounded-xl bg-slate-50"><p className="text-[10px] text-slate-500">Subtotal</p><p className="text-xs font-black">{formatMoney(p.result.subtotal ?? 0)}</p></div>
                  <div className="p-2 rounded-xl bg-blue-50"><p className="text-[10px] text-slate-500">Preço</p><p className="text-xs font-black text-blue-700">{formatMoney(p.result.finalPrice)}</p></div>
                  <div className="p-2 rounded-xl bg-slate-50"><p className="text-[10px] text-slate-500">Lucro</p><p className={`text-xs font-black ${p.result.profit < 0 ? 'text-red-700' : 'text-emerald-700'}`}>{formatMoney(p.result.profit)} ({p.result.effectiveMarginPercent}%)</p></div>
                </div>
                <div className="flex flex-wrap justify-end gap-1">
                  {p.status !== 'aprovada' && canManageFinance() && (
                    <button type="button" className={btnSecondary} onClick={() => setApproving(p)}><CheckCircle2 className="w-3.5 h-3.5 text-emerald-600" /> Aprovar</button>
                  )}
                  {p.status === 'aprovada' && canManageFinance() && (
                    <button type="button" className={btnSecondary} onClick={() => navigateTo('gestao_receber')}>Ver contas a receber</button>
                  )}
                  <button type="button" className={btnSecondary} disabled={exporting === p.id} title="Proposta comercial para o cliente (só o preço final)"
                    onClick={() => exportProposal(p)}>
                    <FileText className="w-3.5 h-3.5 text-blue-600" /> {exporting === p.id ? 'Gerando…' : 'Proposta em PDF'}
                  </button>
                  <button type="button" className={btnSecondary} title="Duplicar" onClick={() => duplicate(p)}><Copy className="w-3.5 h-3.5" /></button>
                  <button type="button" className={btnSecondary} title="Editar" onClick={() => setEditing(p)}><Pencil className="w-3.5 h-3.5" /></button>
                  {canDelete && (
                    <button type="button" className={btnDanger} title="Excluir"
                      onClick={() => { if (window.confirm(`Excluir a precificação ${p.number}?${p.status === 'aprovada' ? '\nAs contas a receber já geradas NÃO são apagadas.' : ''}`)) deletePricing(p.id); }}>
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}

      {editing && <PricingEditor pricing={editing} onClose={() => setEditing(null)} />}
      {approving && <ApproveDialog pricing={approving} onClose={() => setApproving(null)} />}
    </div>
  );
};

const PricingEditor: React.FC<{ pricing: ServicePricing; onClose: () => void }> = ({ pricing, onClose }) => {
  const [form, setForm] = useState<ServicePricing>(() => ({ ...pricing, input: normalizePricingInput(pricing.input) }));
  const [refresh, setRefresh] = useState(false);
  const clients = getClientsForGestao();
  const orders = getServiceOrdersForGestao();
  const current = costsFromSettings(getSettings());
  const saved = costsFromSettings(form.costsSnapshot || {});
  // nova precificação usa sempre a configuração atual; a já salva mantém os valores da época (até "Usar valores atuais")
  const costs = !pricing.id || refresh ? current : saved;
  const result = calculatePricing(form.input, costs);
  const costsChanged = !!pricing.id && JSON.stringify(current) !== JSON.stringify(saved);
  const locked = pricing.status === 'aprovada';
  const realized = realizedSummary({ realized: form.realized, result });

  const setInput = <K extends keyof PricingInput>(k: K, v: PricingInput[K]) => setForm(f => ({ ...f, input: { ...f.input, [k]: v } }));
  const set = <K extends keyof ServicePricing>(k: K, v: ServicePricing[K]) => setForm(f => ({ ...f, [k]: v }));
  const setRealized = (k: keyof RealizedExpenses, v: number) => setForm(f => ({
    ...f, realized: { materials: 0, thirdPartyLabor: 0, engineeringLabor: 0, operational: 0, ...f.realized, [k]: v }
  }));

  const pickClient = (id: string) => {
    const c = clients.find(x => x.id === id);
    setForm(f => ({
      ...f, clientId: id || undefined, clientName: c ? (c.nomeFantasia || c.razaoSocial) : f.clientName,
      responsibleName: f.responsibleName || c?.responsavel || '', responsibleEmail: f.responsibleEmail || c?.email || ''
    }));
  };

  const save = (opts: { onlyRealized?: boolean } = {}) => {
    try {
      if (!form.title.trim()) throw new Error('Informe a descrição do serviço.');
      const c = clients.find(x => x.id === form.clientId);
      if (opts.onlyRealized) {
        // precificação aprovada: só as despesas realizadas mudam (o preço já gerou as contas a receber)
        savePricing({ ...pricing, realized: form.realized });
      } else {
        savePricing({ ...form, title: form.title.trim(), clientName: c ? (c.nomeFantasia || c.razaoSocial) : form.clientName, costsSnapshot: costs },
          { refreshCosts: !pricing.id || refresh });
      }
      onClose();
    } catch (err) { alertError(err); }
  };

  return (
    <Modal wide title={pricing.id ? `Precificação ${pricing.number}` : 'Nova precificação'} onClose={onClose}
      footer={<>
        <button type="button" className={btnSecondary} onClick={onClose}>Cancelar</button>
        {locked
          ? <button type="button" className={btnPrimary} onClick={() => save({ onlyRealized: true })}>Salvar despesas realizadas</button>
          : <button type="button" className={btnPrimary} onClick={() => save()}>Salvar</button>}
      </>}>
      {locked && <p className="mb-3 text-[11px] text-emerald-800 bg-emerald-50 border border-emerald-200 rounded-xl px-3 py-2">Precificação aprovada: as contas a receber já foram geradas. Só as despesas realizadas podem ser lançadas; para mudar o preço, duplique.</p>}
      {costsChanged && !refresh && !locked && (
        <div className="mb-3 flex flex-wrap items-center justify-between gap-2 text-[11px] text-amber-800 bg-amber-50 border border-amber-200 rounded-xl px-3 py-2">
          Os valores de referência mudaram desde que esta precificação foi salva.
          <button type="button" className={btnSecondary} onClick={() => setRefresh(true)}><RefreshCw className="w-3.5 h-3.5" /> Usar valores atuais</button>
        </div>
      )}
      <div className="grid grid-cols-1 lg:grid-cols-5 gap-4">
        <fieldset disabled={locked} className="lg:col-span-3 space-y-4 min-w-0">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <Field label="Cliente / Empresa">
              <select className={inputCls} value={form.clientId || ''} onChange={e => pickClient(e.target.value)}>
                <option value="">—</option>
                {clients.map(c => <option key={c.id} value={c.id}>{c.nomeFantasia || c.razaoSocial}</option>)}
              </select>
            </Field>
            <Field label="Data"><input type="date" className={inputCls} value={form.date} onChange={e => set('date', e.target.value)} /></Field>
            <Field label="Responsável"><input className={inputCls} value={form.responsibleName || ''} onChange={e => set('responsibleName', e.target.value)} /></Field>
            <Field label="E-mail"><input type="email" className={inputCls} value={form.responsibleEmail || ''} onChange={e => set('responsibleEmail', e.target.value)} /></Field>
            <Field label="Descrição" className="sm:col-span-2">
              <input className={inputCls} value={form.title} onChange={e => set('title', e.target.value)} placeholder="Ex.: Ensaio dielétrico de 40 luvas + 10 mangas" />
            </Field>
            <Field label="Cond. PG"><input className={inputCls} value={form.paymentTerms || ''} onChange={e => set('paymentTerms', e.target.value)} placeholder="Ex.: 50% no aceite e 50% na entrega do laudo" /></Field>
            <Field label="Status">
              <select className={inputCls} value={form.status} onChange={e => set('status', e.target.value as PricingStatus)}>
                {(['rascunho', 'enviada', 'recusada'] as PricingStatus[]).map(s => <option key={s} value={s}>{PRICING_STATUS_LABEL[s]}</option>)}
                {form.status === 'aprovada' && <option value="aprovada">Aprovada</option>}
              </select>
            </Field>
            <Field label="Validade (dias)" hint={`Válida até ${formatDateBr(addDays(form.date, form.validityDays || 0))}`}>
              <NumberInput value={form.validityDays} step={1} onChange={v => set('validityDays', Math.round(v))} />
            </Field>
            {orders.length > 0 && (
              <Field label="Ordem de serviço (opcional)">
                <select className={inputCls} value={form.serviceOrderId || ''} onChange={e => set('serviceOrderId', e.target.value || undefined)}>
                  <option value="">—</option>
                  {orders.map(o => <option key={o.id} value={o.id}>{o.osNumber}{o.clientName ? ` – ${o.clientName}` : ''}</option>)}
                </select>
              </Field>
            )}
          </div>

          <div className="p-3 rounded-xl border border-slate-200 grid grid-cols-2 sm:grid-cols-4 gap-3">
            <p className="col-span-2 sm:col-span-4 text-xs font-black text-slate-800">Locação de Homem Hora — HH</p>
            <Field label="Qt de dias trabalhados"><NumberInput value={form.input.days} step={0.5} onChange={v => setInput('days', v)} /></Field>
            <Field label="Qtd de ajudantes"><NumberInput value={form.input.helpers} step={1} onChange={v => setInput('helpers', Math.round(v))} /></Field>
            <Field label="Qtd de engenheiros"><NumberInput value={form.input.engineers} step={1} onChange={v => setInput('engineers', Math.round(v))} /></Field>
            <Field label="Dist. km/dia"><NumberInput value={form.input.kmPerDay} step={1} onChange={v => setInput('kmPerDay', v)} /></Field>
            <Field label="Qt de visitas p/ orçamento" className="col-span-2"><NumberInput value={form.input.visits} step={1} onChange={v => setInput('visits', Math.round(v))} /></Field>
          </div>

          <div className="p-3 rounded-xl border border-slate-200 grid grid-cols-2 gap-3">
            <p className="col-span-2 text-xs font-black text-slate-800">Custo dos materiais</p>
            <Field label="Valor dos materiais (R$)" hint={`+ ${costs.materialsTaxPercent}% impostos + ${costs.materialsOtherPercent}% outras despesas`}>
              <NumberInput value={form.input.materials} onChange={v => setInput('materials', v)} />
            </Field>
            <Field label="Locações (R$)"><NumberInput value={form.input.rentals} onChange={v => setInput('rentals', v)} /></Field>
          </div>

          <div className="p-3 rounded-xl border border-slate-200 grid grid-cols-3 gap-3">
            <p className="col-span-3 text-xs font-black text-slate-800">Percentuais desta precificação</p>
            <Field label="Margem JVM (%)"><NumberInput value={form.input.marginPercent ?? costs.marginPercent} onChange={v => setInput('marginPercent', v)} /></Field>
            <Field label="Imposto (%)"><NumberInput value={form.input.taxPercent ?? costs.taxPercent} onChange={v => setInput('taxPercent', v)} /></Field>
            <Field label="Percentual de desconto (%)"><NumberInput value={form.input.negotiationPercent ?? costs.negotiationPercent} onChange={v => setInput('negotiationPercent', v)} /></Field>
          </div>

          <Field label="Observação"><textarea className={inputCls} rows={2} value={form.notes || ''} onChange={e => set('notes', e.target.value)} /></Field>
        </fieldset>

        <div className="lg:col-span-2 space-y-3">
          <BreakdownCard result={result} />
          {!!pricing.id && (
            <div className={`${cardCls} p-4 text-xs space-y-2`}>
              <p className="font-black text-sm text-slate-900">Despesas realizadas</p>
              <p className="text-[11px] text-slate-500">Lance o que foi gasto depois da execução para comparar com a previsão.</p>
              <div className="grid grid-cols-2 gap-2">
                <Field label="Total dos materiais"><NumberInput value={form.realized?.materials} onChange={v => setRealized('materials', v)} /></Field>
                <Field label="Mão de obra terceira"><NumberInput value={form.realized?.thirdPartyLabor} onChange={v => setRealized('thirdPartyLabor', v)} /></Field>
                <Field label="Mão de obra engenharia"><NumberInput value={form.realized?.engineeringLabor} onChange={v => setRealized('engineeringLabor', v)} /></Field>
                <Field label="Despesas operacionais"><NumberInput value={form.realized?.operational} onChange={v => setRealized('operational', v)} /></Field>
              </div>
              {realized && (
                <div className="divide-y divide-slate-100">
                  <Row label="Previsto (subtotal operacional)" value={result.subtotal} />
                  <Row label="Realizado" value={realized.total} cls={realized.difference > 0 ? 'text-red-700' : 'text-emerald-700'}
                    hint={realized.difference > 0 ? `${formatMoney(realized.difference)} acima do previsto` : `${formatMoney(-realized.difference)} abaixo do previsto`} />
                  <Row label="Lucro real" value={realized.profit} strong cls={realized.profit < 0 ? 'text-red-700' : 'text-emerald-700'} />
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </Modal>
  );
};

const Row: React.FC<{ label: string; value: number; strong?: boolean; cls?: string; hint?: string }> = ({ label, value, strong, cls, hint }) => (
  <div className={`flex justify-between gap-2 py-1 ${strong ? 'font-black' : ''}`}>
    <span className="text-slate-600">{label}{hint && <span className="block text-[10px] text-slate-400 font-normal">{hint}</span>}</span>
    <span className={cls || 'text-slate-900'}>{formatMoney(value)}</span>
  </div>
);

/** Mesmos blocos da planilha: custos por despesas, previsão de despesas e valor da proposta. */
export const BreakdownCard: React.FC<{ result: PricingBreakdown }> = ({ result: r }) => (
  <div className={`${cardCls} p-4 text-xs`}>
    <p className="font-black text-sm text-slate-900 mb-2">Custos por despesas</p>
    <div className="divide-y divide-slate-100">
      <Row label="Mão de obra colaborador" value={r.helperLabor} />
      <Row label="Mão de obra engenharia" value={r.engineerLabor} />
      <Row label="Refeição engenharia" value={r.engineerMeals} />
      <Row label="Refeição colaborador" value={r.helperMeals} />
      <Row label="Passagem ida e volta colaborador" value={r.helperFares} />
      <Row label="Combustível" value={r.fuel} />
      <Row label="Visita técnica / orçamento" value={r.technicalVisits} />
      <Row label="Manutenção do veículo" value={r.vehicle} />
      {(r.materials > 0 || r.rentals > 0) && <>
        <Row label="Materiais" value={r.materials} />
        <Row label="Impostos materiais" value={r.materialsTax} />
        <Row label="Outras despesas (materiais)" value={r.materialsOther} />
        <Row label="Locações" value={r.rentals} />
      </>}
    </div>
    <p className="font-black text-sm text-slate-900 mt-3 mb-1">Previsão de despesas</p>
    <div className="divide-y divide-slate-100">
      <Row label="Total dos materiais" value={r.totalMaterials} />
      <Row label="Mão de obra colaborador" value={r.helperTotal} />
      <Row label="Mão de obra engenharia" value={r.engineeringTotal} />
      <Row label="Despesas operacionais" value={r.operationalTotal} hint="Combustível + manutenção + custos fixos" />
      <Row label="Subtotal operacional" value={r.subtotal} strong />
      <Row label={`Margem JVM ${r.marginPercent}%`} value={r.marginValue} />
      <Row label={`Imposto ${r.taxPercent}%`} value={r.taxValue} hint="Sobre subtotal + margem" />
      <Row label="Total da nota" value={r.invoiceTotal} strong />
      <Row label={`Percentual de desconto ${r.negotiationPercent}%`} value={r.negotiationValue} hint="Somado ao total da nota" />
    </div>
    <div className="flex justify-between gap-2 py-2 mt-1 text-base font-black border-t-2 border-slate-200">
      <span>Valor da proposta</span><span className="text-blue-700">{formatMoney(r.finalPrice)}</span>
    </div>
    <div className={`flex justify-between gap-2 text-xs font-bold ${r.profit < 0 ? 'text-red-700' : 'text-emerald-700'}`}>
      <span>Lucro previsto (margem + desconto)</span><span>{formatMoney(r.profit)} ({r.effectiveMarginPercent}%)</span>
    </div>
  </div>
);


const ApproveDialog: React.FC<{ pricing: ServicePricing; onClose: () => void }> = ({ pricing, onClose }) => {
  const settings = getSettings();
  const [installments, setInstallments] = useState(1);
  const [firstDueDate, setFirstDueDate] = useState(addDays(todayIso(), 30));
  const [category, setCategory] = useState(settings.receivableCategories[0] || 'Serviços');
  const [accountId, setAccountId] = useState(settings.accounts[0]?.id || '');

  const approve = () => {
    try {
      const created = approvePricing(pricing.id, { installments, firstDueDate, category, accountId: accountId || undefined });
      window.alert(`${created.length} conta(s) a receber gerada(s).`);
      onClose();
    } catch (err) { alertError(err); }
  };

  return (
    <Modal title={`Aprovar ${pricing.number}`} onClose={onClose}
      footer={<><button type="button" className={btnSecondary} onClick={onClose}>Cancelar</button><button type="button" className={btnPrimary} onClick={approve}>Aprovar e gerar contas a receber</button></>}>
      <div className="space-y-3">
        <p className="text-xs text-slate-700">{pricing.title} — <b>{formatMoney(pricing.result.finalPrice)}</b>{pricing.clientName ? ` · ${pricing.clientName}` : ''}</p>
        {pricing.paymentTerms && <p className="text-[11px] text-slate-500">Condições: {pricing.paymentTerms}</p>}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <Field label="Parcelas" hint={installments > 1 ? `${installments}× de aprox. ${formatMoney(pricing.result.finalPrice / installments)}` : undefined}>
            <NumberInput value={installments} step={1} min={1} onChange={v => setInstallments(Math.max(1, Math.min(60, Math.round(v))))} />
          </Field>
          <Field label="Vencimento da 1ª parcela"><input type="date" className={inputCls} value={firstDueDate} onChange={e => setFirstDueDate(e.target.value)} /></Field>
          <Field label="Categoria">
            <select className={inputCls} value={category} onChange={e => setCategory(e.target.value)}>
              {settings.receivableCategories.map(c => <option key={c} value={c}>{c}</option>)}
            </select>
          </Field>
          <Field label="Conta de recebimento">
            <select className={inputCls} value={accountId} onChange={e => setAccountId(e.target.value)}>
              {settings.accounts.map(a => <option key={a.id} value={a.id}>{a.name}</option>)}
            </select>
          </Field>
        </div>
      </div>
    </Modal>
  );
};
