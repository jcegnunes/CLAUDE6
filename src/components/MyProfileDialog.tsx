import React, { useState } from 'react';
import { X, UserCircle2, Save, Trash2, PenTool, Lock } from 'lucide-react';
import { DielectricStorageService } from '../services/syncEngine';
import { SignatureCanvas } from './SignatureCanvas';
import type { User } from '../types';

const ROLE_LABEL: Record<string, string> = {
  admin: 'Administrador',
  responsavel_tecnico: 'Responsável Técnico',
  tecnico: 'Técnico',
  administrativo: 'Administrativo',
  cliente: 'Cliente'
};

const inputCls = 'w-full p-2 border border-slate-300 rounded-xl bg-white text-xs text-slate-900 focus:ring-2 focus:ring-blue-500 focus:outline-none';
const readOnlyCls = 'w-full p-2 border border-slate-200 rounded-xl bg-slate-100 text-xs text-slate-500';

/**
 * Meu perfil: o próprio usuário edita os seus dados e a sua assinatura.
 * Perfil, e-mail, usuário, empresa e permissões só o administrador altera
 * (o banco também protege esses campos).
 */
export const MyProfileDialog: React.FC<{ currentUser: User; onClose: () => void; onSaved: (user: User) => void }> = ({ currentUser, onClose, onSaved }) => {
  // dados mais recentes do cadastro (a sessão pode estar desatualizada)
  const stored = DielectricStorageService.getUsers().find(u => u.id === currentUser.id) || currentUser;
  const [name, setName] = useState(stored.name || '');
  const [cargo, setCargo] = useState(stored.cargo || '');
  const [creaOrCft, setCreaOrCft] = useState(stored.creaOrCft || '');
  const [registrationNumber, setRegistrationNumber] = useState(stored.registrationNumber || '');
  const [phone, setPhone] = useState(stored.phone || '');
  const [signatureUrl, setSignatureUrl] = useState(stored.signatureUrl || '');
  const [editingSignature, setEditingSignature] = useState(!stored.signatureUrl);

  const handleSave = () => {
    if (!name.trim()) return window.alert('Informe o seu nome.');
    try {
      const saved = DielectricStorageService.saveUser({
        ...stored,
        name: name.trim(),
        cargo: cargo.trim(),
        creaOrCft: creaOrCft.trim(),
        registrationNumber: registrationNumber.trim(),
        phone: phone.trim(),
        signatureUrl
      });
      const session = { ...currentUser, ...saved };
      DielectricStorageService.setCurrentUser(session);
      onSaved(session);
      onClose();
    } catch (err) {
      window.alert(`Não foi possível salvar: ${err instanceof Error ? err.message : String(err)}`);
    }
  };

  return (
    <div className="fixed inset-0 z-[70] bg-slate-900/50 flex items-end sm:items-center justify-center sm:p-4" onClick={onClose}>
      <div className="bg-white w-full sm:max-w-2xl max-h-[94vh] flex flex-col rounded-t-2xl sm:rounded-2xl shadow-2xl" onClick={e => e.stopPropagation()} role="dialog" aria-label="Meu perfil">
        <div className="flex items-center justify-between px-4 py-3 border-b border-slate-200">
          <h3 className="font-bold text-sm text-slate-900 flex items-center gap-2"><UserCircle2 className="w-4 h-4 text-blue-600" /> Meu perfil</h3>
          <button type="button" onClick={onClose} className="p-1.5 rounded-lg hover:bg-slate-100 text-slate-500" aria-label="Fechar"><X className="w-4 h-4" /></button>
        </div>

        <div className="p-4 overflow-y-auto flex-1 space-y-4">
          <div className="grid sm:grid-cols-2 gap-3">
            <label className="block sm:col-span-2">
              <span className="block font-bold text-slate-700 mb-1 text-xs">Nome completo *</span>
              <input className={inputCls} value={name} onChange={e => setName(e.target.value)} />
            </label>
            <label className="block">
              <span className="block font-bold text-slate-700 mb-1 text-xs">Cargo / função</span>
              <input className={inputCls} value={cargo} onChange={e => setCargo(e.target.value)} placeholder="Ex.: Técnico em Eletrotécnica" />
            </label>
            <label className="block">
              <span className="block font-bold text-slate-700 mb-1 text-xs">CREA / CFT</span>
              <input className={inputCls} value={creaOrCft} onChange={e => setCreaOrCft(e.target.value)} placeholder="Ex.: CFT 12345678901" />
            </label>
            <label className="block">
              <span className="block font-bold text-slate-700 mb-1 text-xs">Registro / matrícula</span>
              <input className={inputCls} value={registrationNumber} onChange={e => setRegistrationNumber(e.target.value)} />
            </label>
            <label className="block">
              <span className="block font-bold text-slate-700 mb-1 text-xs">Telefone</span>
              <input className={inputCls} value={phone} onChange={e => setPhone(e.target.value)} inputMode="tel" />
            </label>
          </div>

          <div className="grid sm:grid-cols-3 gap-3">
            <label className="block sm:col-span-2">
              <span className="block font-bold text-slate-500 mb-1 text-xs flex items-center gap-1"><Lock className="w-3 h-3" /> E-mail de acesso</span>
              <input className={readOnlyCls} value={stored.email || ''} readOnly />
            </label>
            <label className="block">
              <span className="block font-bold text-slate-500 mb-1 text-xs flex items-center gap-1"><Lock className="w-3 h-3" /> Perfil</span>
              <input className={readOnlyCls} value={ROLE_LABEL[stored.role] || stored.role} readOnly />
            </label>
            <p className="sm:col-span-3 text-[11px] text-slate-400 -mt-1">E-mail, perfil, empresa e módulos liberados são alterados pelo administrador.</p>
          </div>

          <div className="border-t border-slate-100 pt-4">
            <div className="flex items-center justify-between mb-2">
              <span className="font-bold text-slate-800 text-xs flex items-center gap-1.5"><PenTool className="w-4 h-4 text-orange-500" /> Minha assinatura</span>
              {signatureUrl && !editingSignature && (
                <div className="flex gap-3">
                  <button type="button" className="text-[11px] text-blue-600 hover:underline" onClick={() => setEditingSignature(true)}>Trocar assinatura</button>
                  <button type="button" className="text-[11px] text-red-600 hover:underline inline-flex items-center gap-1" onClick={() => { if (window.confirm('Remover a sua assinatura?')) { setSignatureUrl(''); setEditingSignature(true); } }}><Trash2 className="w-3 h-3" /> Remover</button>
                </div>
              )}
            </div>
            <p className="text-[11px] text-slate-500 mb-2">Usada nos laudos e certificados em que você é o técnico executor ou o responsável técnico. Desenhe ou envie uma imagem; o fundo é limpo automaticamente.</p>
            {signatureUrl && !editingSignature ? (
              <div className="h-28 border border-slate-200 rounded-xl bg-white flex items-center justify-center">
                <img src={signatureUrl} alt="Minha assinatura" className="max-h-24 max-w-full object-contain" />
              </div>
            ) : (
              <SignatureCanvas
                title="Minha assinatura"
                signerName={name || stored.name}
                signerRole={cargo || ROLE_LABEL[stored.role] || ''}
                initialSignature={signatureUrl || undefined}
                onSave={dataUrl => { setSignatureUrl(dataUrl); setEditingSignature(false); }}
              />
            )}
          </div>
        </div>

        <div className="px-4 py-3 border-t border-slate-200 flex flex-wrap justify-end gap-2">
          <button type="button" onClick={onClose} className="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-xl text-xs font-bold">Cancelar</button>
          <button type="button" onClick={handleSave} className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-xl text-xs font-bold inline-flex items-center gap-1.5"><Save className="w-3.5 h-3.5" /> Salvar meus dados</button>
        </div>
      </div>
    </div>
  );
};

export default MyProfileDialog;
