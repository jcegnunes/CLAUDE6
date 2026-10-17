/** Cadastro de serviços: catálogo usado para montar os orçamentos. */
import React, { useState } from 'react';
import { Copy, Pencil, Plus, Search, Trash2, Wrench } from 'lucide-react';
import { canDeleteGestao, canPrice, deleteService, getServices, getSettings, nextServiceCode, saveService } from '../repository';
import {
  DEFAULT_SERVICE_CATEGORIES, SERVICE_UNITS, calculatePricing, costsFromSettings, emptyPricingInput, formatMoney, normalizePricingInput
} from '../rules';
import type { CatalogService, ServicePriceMode } from '../types';
import { PricingFields } from './PricingFields';
import { EmptyState, Field, Modal, NoAccess, NumberInput, alertError, btnDanger, btnPrimary, btnSecondary, cardCls, inputCls } from './ui';

export function emptyService(): CatalogService {
  return {
    id: '', companyId: '', createdAt: '', updatedAt: '',
    code: nextServiceCode(), name: '', category: DEFAULT_SERVICE_CATEGORIES[0], description: '', unit: 'serviço',
    mode: 'planilha', defaultInput: emptyPricingInput(), unitPrice: 0, normReference: '', active: true
  };
}

/** Preço de referência do serviço (planilha: valor da proposta com os parâmetros padrão). */
export function servicePrice(s: CatalogService, costs = costsFromSettings(getSettings())): number {
  return s.mode === 'planilha' ? calculatePricing(normalizePricingInput(s.defaultInput), costs).finalPrice : s.unitPrice;
}

export const ServicesPanel: React.FC = () => {
  const [editing, setEditing] = useState<CatalogService | null>(null);
  const [search, setSearch] = useState('');
  const [category, setCategory] = useState('');
  const [showInactive, setShowInactive] = useState(false);
  if (!canPrice()) return <NoAccess text="Serviços: acesso do administrador, administrativo e Responsável Técnico." />;

  const costs = costsFromSettings(getSettings());
  const all = getServices();
  const categories = Array.from(new Set([...DEFAULT_SERVICE_CATEGORIES, ...all.map(s => s.category).filter(Boolean)]));
  const q = search.trim().toLowerCase();
  const list = all.filter(s => (showInactive || s.active)
    && (!category || s.category === category)
    && (!q || [s.code, s.name, s.description, s.normReference].some(v => (v || '').toLowerCase().includes(q))));
  const canDelete = canDeleteGestao();

  return (
    <div className="space-y-3">
      <div className={`${cardCls} p-3 flex flex-wrap items-center gap-2`}>
        <div className="relative flex-1 min-w-[200px]">
          <Search className="w-3.5 h-3.5 text-slate-400 absolute left-2.5 top-1/2 -translate-y-1/2" />
          <input className={`${inputCls} pl-8`} placeholder="Buscar código, nome, norma..." value={search} onChange={e => setSearch(e.target.value)} />
        </div>
        <div className="w-full sm:w-56">
          <select className={inputCls} value={category} onChange={e => setCategory(e.target.value)} aria-label="Categoria">
            <option value="">Todas as categorias</option>
            {categories.map(c => <option key={c} value={c}>{c}</option>)}
          </select>
        </div>
        <label className="flex items-center gap-1.5 text-xs text-slate-600">
          <input type="checkbox" checked={showInactive} onChange={e => setShowInactive(e.target.checked)} /> Mostrar inativos
        </label>
        <button type="button" className={btnPrimary} onClick={() => setEditing(emptyService())}><Plus className="w-4 h-4" /> Novo serviço</button>
      </div>

      {list.length === 0 ? (
        <EmptyState icon={Wrench} title={all.length ? 'Nenhum serviço neste filtro' : 'Nenhum serviço cadastrado'}
          text="Cadastre os serviços que a empresa vende. Cada um pode ter preço calculado pela planilha (dias, equipe, km, materiais) ou preço fixo por unidade, e é usado para montar os orçamentos." />
      ) : (
        <div className={`${cardCls} overflow-x-auto`}>
          <table className="w-full text-xs min-w-[720px]">
            <thead className="bg-slate-50 text-slate-500 text-[11px] uppercase">
              <tr>
                <th className="p-2 text-left">Código</th>
                <th className="p-2 text-left">Serviço</th>
                <th className="p-2 text-left">Categoria</th>
                <th className="p-2 text-left">Cálculo</th>
                <th className="p-2 text-right">Preço de referência</th>
                <th className="p-2 text-right">Ações</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {list.map(s => (
                <tr key={s.id} className={s.active ? '' : 'opacity-50'}>
                  <td className="p-2 font-mono">{s.code}</td>
                  <td className="p-2 min-w-[220px]">
                    <p className="font-bold text-slate-800">{s.name}{!s.active && ' (inativo)'}</p>
                    {s.normReference && <p className="text-[10px] text-slate-500">{s.normReference}</p>}
                  </td>
                  <td className="p-2 text-slate-600">{s.category}</td>
                  <td className="p-2 text-slate-600">{s.mode === 'planilha' ? 'Planilha' : `Preço fixo / ${s.unit}`}</td>
                  <td className="p-2 text-right font-bold">{formatMoney(servicePrice(s, costs))}</td>
                  <td className="p-2">
                    <div className="flex justify-end gap-1">
                      <button type="button" className={btnSecondary} title="Duplicar"
                        onClick={() => setEditing({ ...s, id: '', code: nextServiceCode(), name: `${s.name} (cópia)`, createdAt: '', updatedAt: '', serverUpdatedAt: undefined })}>
                        <Copy className="w-3.5 h-3.5" />
                      </button>
                      <button type="button" className={btnSecondary} title="Editar" onClick={() => setEditing(s)}><Pencil className="w-3.5 h-3.5" /></button>
                      {canDelete && (
                        <button type="button" className={btnDanger} title="Excluir"
                          onClick={() => { if (window.confirm(`Excluir o serviço "${s.name}"?\nOrçamentos já feitos não mudam. Prefira desmarcar "Ativo" para só esconder.`)) deleteService(s.id); }}>
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {editing && <ServiceEditor service={editing} categories={categories} onClose={() => setEditing(null)} />}
    </div>
  );
};

export const ServiceEditor: React.FC<{ service: CatalogService; categories: string[]; onClose: () => void; onSaved?: (s: CatalogService) => void }> = ({ service, categories, onClose, onSaved }) => {
  const [form, setForm] = useState<CatalogService>(() => ({ ...service, defaultInput: normalizePricingInput(service.defaultInput) }));
  const costs = costsFromSettings(getSettings());
  const set = <K extends keyof CatalogService>(k: K, v: CatalogService[K]) => setForm(f => ({ ...f, [k]: v }));
  const price = servicePrice(form, costs);

  const save = () => {
    try {
      const saved = saveService(form);
      onSaved?.(saved);
      onClose();
    } catch (err) { alertError(err); }
  };

  return (
    <Modal wide title={service.id ? `Serviço ${service.code}` : 'Novo serviço'} onClose={onClose}
      footer={<><button type="button" className={btnSecondary} onClick={onClose}>Cancelar</button><button type="button" className={btnPrimary} onClick={save}>Salvar serviço</button></>}>
      <div className="space-y-4">
        <div className="grid grid-cols-1 sm:grid-cols-4 gap-3">
          <Field label="Código"><input className={inputCls} value={form.code} onChange={e => set('code', e.target.value)} /></Field>
          <Field label="Nome do serviço" className="sm:col-span-3">
            <input className={inputCls} value={form.name} onChange={e => set('name', e.target.value)} placeholder="Ex.: Ensaio dielétrico de luva isolante classe 2" />
          </Field>
          <Field label="Categoria" className="sm:col-span-2">
            <input className={inputCls} list="gestao-service-categories" value={form.category} onChange={e => set('category', e.target.value)} />
            <datalist id="gestao-service-categories">{categories.map(c => <option key={c} value={c} />)}</datalist>
          </Field>
          <Field label="Normas aplicáveis" className="sm:col-span-2">
            <input className={inputCls} value={form.normReference || ''} onChange={e => set('normReference', e.target.value)} placeholder="Ex.: NR-10, ABNT NBR 16295" />
          </Field>
          <Field label="Descrição (vai para o orçamento)" className="sm:col-span-4">
            <textarea className={inputCls} rows={3} value={form.description} onChange={e => set('description', e.target.value)} />
          </Field>
        </div>

        <div className="p-3 rounded-xl border border-slate-200 space-y-3">
          <div className="flex flex-wrap items-end gap-3">
            <Field label="Como cobrar" className="w-full sm:w-64">
              <select className={inputCls} value={form.mode} onChange={e => set('mode', e.target.value as ServicePriceMode)}>
                <option value="planilha">Calcular pela planilha (dias, equipe, km...)</option>
                <option value="fixo">Preço fixo por unidade</option>
              </select>
            </Field>
            <Field label="Unidade" className="w-32">
              <select className={inputCls} value={form.unit} onChange={e => set('unit', e.target.value)}>
                {Array.from(new Set([...SERVICE_UNITS, form.unit])).map(u => <option key={u} value={u}>{u}</option>)}
              </select>
            </Field>
            {form.mode === 'fixo' && (
              <Field label={`Preço por ${form.unit} (R$)`} className="w-40"><NumberInput value={form.unitPrice} onChange={v => set('unitPrice', v)} /></Field>
            )}
            <div className="ml-auto text-right">
              <p className="text-[10px] text-slate-500">Preço de referência</p>
              <p className="text-lg font-black text-blue-700">{formatMoney(price)}</p>
            </div>
          </div>
          {form.mode === 'planilha' && (
            <>
              <p className="text-[11px] text-slate-500">Parâmetros padrão: ao incluir o serviço num orçamento eles vêm preenchidos e podem ser ajustados.</p>
              <PricingFields input={form.defaultInput} costs={costs} onChange={v => set('defaultInput', v)} />
            </>
          )}
        </div>

        <label className="flex items-center gap-2 text-xs text-slate-700">
          <input type="checkbox" checked={form.active} onChange={e => set('active', e.target.checked)} /> Ativo (aparece para incluir em orçamentos)
        </label>
      </div>
    </Modal>
  );
};
