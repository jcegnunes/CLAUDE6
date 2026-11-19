/**
 * Nova inspeção: pede sempre a Ordem de Serviço da inspeção realizada — escolher uma OS existente
 * ou gerar uma nova OS para um cliente cadastrado. A inspeção já nasce vinculada à OS e ao cliente.
 */
import React, { useState } from 'react';
import { ClipboardList, Plus } from 'lucide-react';
import { createInspection, generateServiceOrder, listInspections, readClients, readServiceOrders, saveInspection } from '../repository';
import type { SpdaInspection } from '../types';
import { formatDateBR } from '../../../utils/dateUtils';
import { Field, Modal, alertError, btnPrimary, btnSecondary, inputCls } from './ui';
import { navigateTo } from './navigation';

type Mode = 'existente' | 'nova';

export const NewInspectionDialog: React.FC<{
  onClose: () => void;
  onCreated: (insp: SpdaInspection) => void;
  /** OS já escolhida (vinda de "Executar Ensaios" na tela Ordens de Serviço) */
  initialOsId?: string;
}> = ({ onClose, onCreated, initialOsId }) => {
  const orders = readServiceOrders();
  const clients = readClients();
  const inspections = listInspections();
  const startOs = initialOsId && orders.some(o => o.id === initialOsId) ? initialOsId : '';
  const [mode, setMode] = useState<Mode>(orders.length ? 'existente' : 'nova');
  const [osId, setOsId] = useState(startOs);
  const [clientId, setClientId] = useState('');
  const [busy, setBusy] = useState(false);

  const os = orders.find(o => o.id === osId);
  const usedBy = os ? inspections.filter(i => i.serviceOrderId === os.id) : [];
  const ready = mode === 'existente' ? !!os : !!clientId;

  const withClient = (insp: SpdaInspection, id?: string): SpdaInspection => {
    const c = clients.find(x => x.id === id);
    if (!c) return insp;
    return {
      ...insp,
      clientId: c.id,
      clientName: c.razaoSocial || c.nomeFantasia,
      clientCnpj: c.cnpj,
      address: [c.endereco, c.numero, c.bairro].filter(Boolean).join(', '),
      city: c.cidade,
      state: c.estado
    };
  };

  const confirm = () => {
    setBusy(true);
    try {
      let insp = createInspection();
      if (mode === 'existente' && os) {
        insp = { ...withClient(insp, os.clientId), serviceOrderId: os.id, serviceOrderNumber: os.osNumber };
        if (!insp.clientName && os.clientName) insp.clientName = os.clientName;
      } else {
        insp = withClient(insp, clientId);
        const gen = generateServiceOrder(insp);
        insp = { ...insp, serviceOrderId: gen.id, serviceOrderNumber: gen.osNumber };
      }
      onCreated(saveInspection(insp));
    } catch (err) {
      alertError(err, 'Não foi possível criar a inspeção');
      setBusy(false);
    }
  };

  const tab = (on: boolean) => `flex-1 px-3 py-2 text-xs font-bold border ${on ? 'bg-[#0A2540] text-white border-[#0A2540]' : 'bg-white text-slate-600 border-slate-300 hover:bg-slate-50'}`;

  return (
    <Modal title="Nova inspeção — Ordem de Serviço" onClose={onClose} footer={<>
      <button type="button" className={btnSecondary} onClick={onClose}>Cancelar</button>
      <button type="button" className={btnPrimary} disabled={!ready || busy} onClick={confirm}><Plus className="w-3.5 h-3.5" /> Criar inspeção</button>
    </>}>
      <div className="space-y-3">
        <p className="text-xs text-slate-600 flex items-start gap-1.5"><ClipboardList className="w-4 h-4 shrink-0 text-blue-700" /> Informe a Ordem de Serviço da inspeção realizada. A inspeção e o laudo ficam vinculados a ela.</p>

        <div className="flex rounded-lg overflow-hidden" role="radiogroup" aria-label="Ordem de serviço">
          <button type="button" role="radio" aria-checked={mode === 'existente'} className={`${tab(mode === 'existente')} rounded-l-lg`} onClick={() => setMode('existente')}>OS existente</button>
          <button type="button" role="radio" aria-checked={mode === 'nova'} className={`${tab(mode === 'nova')} rounded-r-lg -ml-px`} onClick={() => setMode('nova')}>Gerar nova OS</button>
        </div>

        {mode === 'existente' ? (
          orders.length ? (
            <>
              <Field label="Ordem de serviço *">
                <select className={inputCls} value={osId} onChange={e => setOsId(e.target.value)} aria-label="Ordem de serviço da inspeção">
                  <option value="">— escolha a OS —</option>
                  {orders.map(o => <option key={o.id} value={o.id}>{o.osNumber} · {o.clientName || 'Cliente'} · {formatDateBR(o.openDate)}</option>)}
                </select>
              </Field>
              {usedBy.length > 0 && (
                <p className="text-[11px] text-amber-700">Esta OS já tem a(s) inspeção(ões) {usedBy.map(i => i.number).join(', ')}. Uma nova inspeção será criada para a mesma OS.</p>
              )}
            </>
          ) : (
            <p className="text-[11px] text-slate-500">Nenhuma OS cadastrada. Use “Gerar nova OS”.</p>
          )
        ) : clients.length ? (
          <>
            <Field label="Cliente da nova OS *">
              <select className={inputCls} value={clientId} onChange={e => setClientId(e.target.value)} aria-label="Cliente da nova OS">
                <option value="">— escolha o cliente —</option>
                {clients.map(c => <option key={c.id} value={c.id}>{c.nomeFantasia || c.razaoSocial}{c.cnpj ? ` · ${c.cnpj}` : ''}</option>)}
              </select>
            </Field>
            <p className="text-[11px] text-slate-500">A OS é criada com a numeração oficial (OS-SPDA-AAAA-NNNN) e aparece em Clientes & OS → Ordens de serviço.</p>
          </>
        ) : (
          <div className="text-[11px] text-amber-800 bg-amber-50 border border-amber-200 rounded-lg p-2 flex flex-wrap items-center gap-2">
            Nenhum cliente cadastrado para gerar a OS.
            <button type="button" className={btnSecondary} onClick={() => { onClose(); navigateTo('clientes'); }}>Cadastrar cliente</button>
          </div>
        )}
      </div>
    </Modal>
  );
};
