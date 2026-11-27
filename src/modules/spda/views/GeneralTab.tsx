/** Aba "Dados": cliente, edificação, características do SPDA, instrumentos e responsável. */
import React, { useState } from 'react';
import { ClipboardPlus } from 'lucide-react';
import { generateServiceOrder, readClients, readServiceOrders } from '../repository';
import { InstrumentsSection } from './InstrumentsSection';
import {
  INSPECTION_KIND_LABEL, METHOD_LABEL, protectionLabel, levelParams, buildingsOf, validityText, nextInspectionDate,
  withBuildings
} from '../rules';
import { BuildingsSection } from './BuildingsSection';
import type { InspectionKind, ProtectionLevel, SpdaInspection, SpdaMethod } from '../types';
import { DecimalField, Field, alertError, btnPrimary, cardCls, inputCls } from './ui';
import { formatDateBR } from '../../../utils/dateUtils';
import { confirmAsk, notify } from './dialogs';

type Props = { value: SpdaInspection; onChange: (patch: Partial<SpdaInspection>) => void; readOnly: boolean };

export const GeneralTab: React.FC<Props> = ({ value: v, onChange, readOnly }) => {
  const clients = readClients();
  const [, setVersion] = useState(0);
  const serviceOrders = readServiceOrders(v.clientId);
  const lp = levelParams(v.protectionLevel);
  const buildings = buildingsOf(v);

  const pickClient = (id: string) => {
    const c = clients.find(x => x.id === id);
    if (!c) { onChange({ clientId: undefined }); return; }
    onChange({
      clientId: c.id,
      clientName: c.razaoSocial || c.nomeFantasia,
      clientCnpj: c.cnpj,
      address: v.address || [c.endereco, c.numero, c.bairro].filter(Boolean).join(', '),
      city: v.city || c.cidade,
      state: v.state || c.estado
    });
  };

  const pickServiceOrder = (id: string) => {
    if (!id) { onChange({ serviceOrderId: undefined, serviceOrderNumber: undefined }); return; }
    const os = serviceOrders.find(o => o.id === id);
    if (!os) return;
    const patch: Partial<SpdaInspection> = { serviceOrderId: os.id, serviceOrderNumber: os.osNumber };
    // OS escolhida sem cliente na inspeção: traz o cliente da OS
    const c = !v.clientId ? clients.find(x => x.id === os.clientId) : undefined;
    if (c) Object.assign(patch, { clientId: c.id, clientName: c.razaoSocial || c.nomeFantasia, clientCnpj: c.cnpj });
    onChange(patch);
  };

  const createServiceOrder = async () => {
    if (!v.clientId) { await notify('Selecione um cliente cadastrado para gerar a OS.'); return; }
    if (!(await confirmAsk('Gerar uma nova Ordem de Serviço para este cliente? Ela entra no cadastro de Ordens de Serviço com a numeração oficial.', { confirmLabel: 'Gerar OS' }))) return;
    try {
      const os = generateServiceOrder(v);
      onChange({ serviceOrderId: os.id, serviceOrderNumber: os.osNumber });
      setVersion(x => x + 1);
    } catch (err) {
      alertError(err, 'Não foi possível gerar a OS');
    }
  };

  const linked = v.serviceOrderId ? serviceOrders.find(o => o.id === v.serviceOrderId) : undefined;

  return (
    <fieldset disabled={readOnly} className="space-y-4">
      <section className={`${cardCls} p-4`}>
        <h3 className="font-bold text-sm text-slate-900 mb-3">Ordem de serviço</h3>
        <div className="flex flex-col sm:flex-row sm:items-end gap-3">
          <Field label="OS vinculada" className="flex-1" hint={v.clientId ? 'OS abertas do cliente selecionado' : 'Todas as OS abertas — selecione o cliente para filtrar'}>
            <select className={inputCls} value={v.serviceOrderId || ''} onChange={e => pickServiceOrder(e.target.value)} aria-label="OS vinculada">
              <option value="">— sem OS —</option>
              {v.serviceOrderId && !linked && <option value={v.serviceOrderId}>{v.serviceOrderNumber}</option>}
              {serviceOrders.map(o => (
                <option key={o.id} value={o.id}>{o.osNumber} — {o.clientName || 'Cliente'} ({formatDateBR(o.openDate)})</option>
              ))}
            </select>
          </Field>
          <button type="button" className={btnPrimary} onClick={createServiceOrder} disabled={!v.clientId}>
            <ClipboardPlus className="w-3.5 h-3.5" /> Gerar nova OS
          </button>
        </div>
        {v.serviceOrderNumber && (
          <p className="text-[11px] text-slate-600 mt-2">
            OS <b className="font-mono text-blue-700">{v.serviceOrderNumber}</b>{linked ? ` · ${linked.clientName || ''} · situação: ${linked.status.replace('_', ' ')}` : ''}. O número sai no laudo.
          </p>
        )}
        {!v.clientId && <p className="text-[11px] text-slate-500 mt-2">Para gerar a OS, escolha um cliente cadastrado abaixo.</p>}
      </section>

      <BuildingsSection buildings={buildings} onChange={next => onChange(withBuildings(v, next))} readOnly={readOnly} />

      <section className={`${cardCls} p-4`}>
        <h3 className="font-bold text-sm text-slate-900 mb-3">Cliente e local</h3>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <Field label="Cliente cadastrado" hint="Opcional — preenche os dados abaixo">
            <select className={inputCls} value={v.clientId || ''} onChange={e => pickClient(e.target.value)}>
              <option value="">— digitar manualmente —</option>
              {clients.map(c => <option key={c.id} value={c.id}>{c.nomeFantasia || c.razaoSocial}</option>)}
            </select>
          </Field>
          <Field label="Razão social do cliente *">
            <input className={inputCls} value={v.clientName} onChange={e => onChange({ clientName: e.target.value })} />
          </Field>
          <Field label="CNPJ / CPF">
            <input className={inputCls} value={v.clientCnpj || ''} onChange={e => onChange({ clientCnpj: e.target.value })} />
          </Field>
          <Field label="Endereço" className="sm:col-span-2">
            <input className={inputCls} value={v.address} onChange={e => onChange({ address: e.target.value })} />
          </Field>
          <div className="grid grid-cols-3 gap-3 sm:col-span-2">
            <Field label="Cidade" className="col-span-2">
              <input className={inputCls} value={v.city || ''} onChange={e => onChange({ city: e.target.value })} />
            </Field>
            <Field label="UF">
              <input className={inputCls} maxLength={2} value={v.state || ''} onChange={e => onChange({ state: e.target.value.toUpperCase() })} />
            </Field>
          </div>
        </div>
      </section>

      <section className={`${cardCls} p-4`}>
        <h3 className="font-bold text-sm text-slate-900 mb-3">SPDA e inspeção</h3>
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
          <Field label="Método de posicionamento da captação">
            <select className={inputCls} value={v.method} onChange={e => onChange({ method: e.target.value as SpdaMethod })}>
              {(Object.keys(METHOD_LABEL) as SpdaMethod[]).map(k => <option key={k} value={k}>{METHOD_LABEL[k]}</option>)}
            </select>
          </Field>
          <Field label="Tipo de inspeção">
            <select className={inputCls} value={v.inspectionKind} onChange={e => onChange({ inspectionKind: e.target.value as InspectionKind })}>
              {(Object.keys(INSPECTION_KIND_LABEL) as InspectionKind[]).map(k => <option key={k} value={k}>{INSPECTION_KIND_LABEL[k]}</option>)}
            </select>
          </Field>
          <Field label="Data da inspeção">
            <input type="date" className={inputCls} value={v.inspectionDate} onChange={e => onChange({ inspectionDate: e.target.value })} />
          </Field>
          <Field label="Condição do solo">
            <select className={inputCls} value={v.soilCondition || ''} onChange={e => onChange({ soilCondition: (e.target.value || undefined) as SpdaInspection['soilCondition'] })}>
              <option value="seco">Seco</option>
              <option value="umido">Úmido</option>
              <option value="encharcado">Encharcado</option>
            </select>
          </Field>
          <Field label="Condição do tempo">
            <input className={inputCls} placeholder="Ex.: ensolarado, sem chuva há 3 dias" value={v.weather || ''} onChange={e => onChange({ weather: e.target.value })} />
          </Field>
        </div>
        <label className="flex items-start gap-2 mt-3 text-xs text-slate-700">
          <input type="checkbox" className="mt-0.5" checked={v.criticalStructure} onChange={e => onChange({ criticalStructure: e.target.checked })} />
          <span>Estrutura com áreas classificadas 0, 1, 20 ou 21, munição, explosivos, componentes tóxicos, corrosão atmosférica severa (litoral, atmosfera industrial agressiva) ou de serviço essencial (energia, água, sinais, apoio à vida) — periodicidade de 1 ano (NBR 5419-3:2026, 7.3.2 f)</span>
        </label>
        <div className="mt-3 text-[11px] text-slate-600 bg-slate-50 border border-slate-200 rounded-xl px-3 py-2">
          NP mais restritivo entre as edificações: <b>{protectionLabel(v.protectionLevel)}</b>{lp
            ? <> — raio da esfera rolante <b>{lp.sphereRadiusM} m</b> e malha máxima <b>{lp.meshM} m</b> (Tabela 2) · distância entre descidas <b>{lp.downSpacingM} m</b>, até +20 % (Tabela 5, 5.4.3.3).</>
            : <> (defina o nível de proteção nas edificações para ver os parâmetros da NBR 5419).</>}
          {' '}Próxima inspeção: <b>{formatDateBR(nextInspectionDate(v)) || '—'}</b> (validade do laudo: {validityText(v)}).
        </div>
      </section>

      <InstrumentsSection value={v} onChange={onChange} readOnly={readOnly} />

      <section className={`${cardCls} p-4`}>
        <h3 className="font-bold text-sm text-slate-900 mb-3">Título dos documentos</h3>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <Field label="Título do laudo" hint="Em branco: usa o título padrão">
            <input className={inputCls} placeholder="Laudo técnico de inspeção e ensaios do SPDA" value={v.laudoTitle || ''} onChange={e => onChange({ laudoTitle: e.target.value })} />
          </Field>
          <Field label="Título do relatório técnico" hint="Em branco: usa o título padrão">
            <input className={inputCls} placeholder="Inspeção e ensaios do Sistema de Proteção contra Descargas Atmosféricas — SPDA" value={v.reportTitle || ''} onChange={e => onChange({ reportTitle: e.target.value })} />
          </Field>
        </div>
        <p className="text-[11px] text-slate-500 mt-2">Personalize o cabeçalho do laudo e a capa do relatório. Deixe em branco para manter o título padrão.</p>
      </section>

      <section className={`${cardCls} p-4`}>
        <h3 className="font-bold text-sm text-slate-900 mb-3">Responsável pela inspeção</h3>
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
          <Field label="Nome"><input className={inputCls} value={v.technicianName} onChange={e => onChange({ technicianName: e.target.value })} /></Field>
          <Field label="Título / cargo"><input className={inputCls} value={v.technicianTitle || ''} onChange={e => onChange({ technicianTitle: e.target.value })} /></Field>
          <Field label="CREA / CFT"><input className={inputCls} value={v.technicianCrea || ''} onChange={e => onChange({ technicianCrea: e.target.value })} /></Field>
        </div>
        <p className="text-[11px] text-slate-500 mt-2">O responsável técnico da empresa (Configurações) também assina o laudo.</p>
      </section>
    </fieldset>
  );
};
