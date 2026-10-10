/** Tela "Modelo do Certificado" (menu Configuração do Sistema, no módulo Treinamentos). */
import React from 'react';
import { Palette } from 'lucide-react';
import { LayoutPanel } from './LayoutPanel';

export const CertificateModelView: React.FC = () => (
  <div className="space-y-4">
    <div className="flex items-center gap-3">
      <div className="w-10 h-10 rounded-xl bg-orange-500 text-white flex items-center justify-center"><Palette className="w-5 h-5" /></div>
      <div>
        <h2 className="text-lg font-black text-slate-900">Modelo do Certificado</h2>
        <p className="text-xs text-slate-500">Logo, textos, assinaturas, molduras e modelo importado dos certificados de treinamento</p>
      </div>
    </div>
    <LayoutPanel />
  </div>
);

export default CertificateModelView;
