/** Aba "Dados": cliente, edificação, características do SPDA, instrumentos e responsável. */
import React, { useState } from 'react';
import { ClipboardPlus } from 'lucide-react';
import { generateServiceOrder, readClients, readInstruments, readServiceOrders } from '../repository';
import {
  INSPECTION_KIND_LABEL, LEVEL_PARAMS, METHOD_LABEL, PROTECTION_LEVEL_LABEL, buildingsOf, inspectionIntervalYears, nextInspectionDate,
  withBuildings
} from '../rules';
import { BuildingsSection } from './BuildingsSection';
import type { InspectionKind, InstrumentInfo, ProtectionLevel, SpdaInspection, SpdaMethod } from '../types';
import { DecimalField, Field, alertError, btnPrimary, cardCls, inputCls } from './ui';
import { formatDateBR } from '../../../utils/dateUtils';

type Props = { value: SpdaInspection; onChange: (patch: Partial<SpdaInspection>) => void; readOnly: boolean };

export const GeneralTab: React.FC<Props> = ({ value: v, onChange, readOnly }) => {
  const clients = readClients();
  const instruments = readInstruments();
  const [, setVersion] = useState(0);
  const serviceOrders = readServiceOrders(v.clientId);
  const lp = LEVEL_PARAMS[v.protectionLevel];
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

  const setInstrument = (idx: number, patch: Partial<InstrumentInfo>) => {
    onChange({ instruments: v.instruments.map((ins, i) => (i === idx ? { ...ins, ...patch } : ins)) });
  };

  const pickInstrument = (idx: number, id: string) => {
    const li = instruments.find(x => x.id === id);
    if (!li) return;
    setInstrument(idx, {
      description: [li.type, li.manufacturer].filter(Boolean).join(' — '),
      model: li.model, serialNumber: li.serialNumber, calibrationCert: li.calibrationCertNumber,
      calibrationDate: li.calibrationDate, calibrationExpiry: li.calibrationExpiryDate
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

  const createServiceOrder = () => {
    if (!v.clientId) { window.alert('Selecione um cliente cadastrado para gerar a OS.'); return; }
    if (!window.confirm('Gerar uma nova Ordem de Serviço no cadastro de OS da plataforma para este cliente? Ela recebe a numeração oficial e é sincronizada normalmente.')) return;
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
          <Field label="CNPJ">
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
          NP mais restritivo entre as edificações: <b>{PROTECTION_LEVEL_LABEL[v.protectionLevel]}</b> — raio da esfera rolante <b>{lp.sphereRadiusM} m</b> e malha máxima <b>{lp.meshM} m</b> (Tabela 2) · distância entre descidas <b>{lp.downSpacingM} m</b>, até +20 % (Tabela 5, 5.4.3.3).
          {' '}Próxima inspeção: <b>{formatDateBR(nextInspectionDate(v)) || '—'}</b> ({inspectionIntervalYears(v.criticalStructure)} ano(s)).
        </div>
      </section>

      <section className={`${cardCls} p-4`}>
        <h3 className="font-bold text-sm text-slate-900 mb-3">Instrumentos de medição</h3>
        <div className="space-y-4">
          {v.instruments.map((ins, idx) => (
            <div key={ins.use} className="border border-slate-200 rounded-xl p-3">
              <div className="flex flex-wrap items-center justify-between gap-2 mb-2">
                <p className="text-xs font-bold text-slate-800">{ins.use === 'continuidade' ? 'Ensaio de continuidade' : 'Resistência de aterramento'}</p>
                {instruments.length > 0 && (
                  <select className={`${inputCls} sm:max-w-xs`} value="" onChange={e => pickInstrument(idx, e.target.value)} aria-label="Usar instrumento cadastrado">
                    <option value="">Usar instrumento cadastrado…</option>
                    {instruments.map(i => <option key={i.id} value={i.id}>{i.type} {i.model} — nº {i.serialNumber}</option>)}
                  </select>
                )}
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                <Field label="Instrumento"><input className={inputCls} value={ins.description} onChange={e => setInstrument(idx, { description: e.target.value })} /></Field>
                <Field label="Modelo"><input className={inputCls} value={ins.model} onChange={e => setInstrument(idx, { model: e.target.value })} /></Field>
                <Field label="Nº de série"><input className={inputCls} value={ins.serialNumber} onChange={e => setInstrument(idx, { serialNumber: e.target.value })} /></Field>
                <Field label="Certificado de calibração"><input className={inputCls} value={ins.calibrationCert} onChange={e => setInstrument(idx, { calibrationCert: e.target.value })} /></Field>
                <Field label="Data da calibração"><input type="date" className={inputCls} value={ins.calibrationDate || ''} onChange={e => setInstrument(idx, { calibrationDate: e.target.value })} /></Field>
                <Field label="Validade da calibração"><input type="date" className={inputCls} value={ins.calibrationExpiry || ''} onChange={e => setInstrument(idx, { calibrationExpiry: e.target.value })} /></Field>
              </div>
              {ins.calibrationExpiry && v.inspectionDate && ins.calibrationExpiry < v.inspectionDate && (
                <p className="text-[11px] text-red-700 mt-2">Calibração vencida na data da inspeção.</p>
              )}
            </div>
          ))}
        </div>
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
