/**
 * "Baixar o aplicativo" no menu lateral da plataforma web: o APK do módulo aberto
 * (SPDA → APP JVM SPDA; Ensaios de EPI → APP de Ensaios de EPI), com link direto e QR Code
 * para baixar pelo celular. Os APKs ficam em public/apps/ e são publicados junto com a plataforma
 * (os compilar-apk.bat de cada app copiam o APK novo para lá).
 */
import React, { useEffect, useState } from 'react';
import { Download, Smartphone, X } from 'lucide-react';
import { APP_TARGET } from '../appTarget';

export const MODULE_APPS: Record<string, { file: string; versionFile: string; name: string; note: string }> = {
  spda: {
    file: 'apps/JVM-SPDA.apk',
    versionFile: 'apps/JVM-SPDA.version.txt',
    name: 'APP JVM SPDA',
    note: 'Inspeção e ensaios de SPDA no celular, funcionando sem internet e sincronizando com a plataforma.'
  },
  ensaios: {
    file: 'apps/JVM-Ensaios-EPI.apk',
    versionFile: 'apps/JVM-Ensaios-EPI.version.txt',
    name: 'APP JVM Ensaios de EPI',
    note: 'Ensaios dielétricos de EPI/EPC no celular. Já tem o app? Instale por cima: os dados do celular são mantidos.'
  }
};

/** Lê a versão gravada ao lado do APK (os compilar-apk.bat escrevem). Vazio se não houver. */
export async function fetchAppVersion(versionFile: string): Promise<string> {
  try {
    const r = await fetch(`${versionFile}?t=${Date.now()}`, { cache: 'no-store' });
    if (!r.ok) return '';
    const txt = (await r.text()).trim();
    // só aceita algo com cara de versão (evita cair numa página HTML de erro)
    return /^[\w.\- ]{1,40}$/.test(txt) && /\d/.test(txt) ? txt : '';
  } catch {
    return '';
  }
}

/** Endereço completo do APK (para o QR Code). */
export function appDownloadUrl(file: string, origin = typeof window !== 'undefined' ? window.location.origin : ''): string {
  return `${origin.replace(/\/+$/, '')}/${file}`;
}

export const ModuleAppDownload: React.FC<{ workspaceId?: string }> = ({ workspaceId }) => {
  const app = workspaceId ? MODULE_APPS[workspaceId] : undefined;
  const [open, setOpen] = useState(false);
  const [qr, setQr] = useState('');
  const [version, setVersion] = useState('');
  const url = app ? appDownloadUrl(app.file) : '';

  useEffect(() => {
    if (!app) return;
    let cancelled = false;
    fetchAppVersion(app.versionFile).then(v => { if (!cancelled) setVersion(v); });
    return () => { cancelled = true; };
  }, [app]);

  useEffect(() => {
    if (!open || !url) return;
    let cancelled = false;
    import('qrcode')
      .then(q => q.toDataURL(url, { margin: 1, width: 220 }))
      .then(d => { if (!cancelled) setQr(d); })
      .catch(() => setQr(''));
    return () => { cancelled = true; };
  }, [open, url]);

  // dentro do próprio APP não faz sentido oferecer o APK
  if (!app || APP_TARGET === 'spda') return null;

  return (
    <div className="pt-2 pb-1">
      <button type="button" onClick={() => setOpen(true)}
        className="w-full p-3 bg-gradient-to-br from-orange-600/90 to-orange-700 hover:from-orange-500 hover:to-orange-600 rounded-2xl text-left flex items-center gap-2.5 text-white shadow-xs cursor-pointer">
        <span className="w-8 h-8 rounded-xl bg-white/15 flex items-center justify-center shrink-0"><Smartphone className="w-4 h-4" /></span>
        <span className="min-w-0">
          <span className="block text-[12px] font-bold leading-tight">Baixar o aplicativo</span>
          <span className="block text-[10px] text-orange-100 truncate">Android{version ? ` · versão ${version}` : ''}</span>
        </span>
      </button>

      {open && (
        <div className="fixed inset-0 z-[90] bg-slate-900/60 flex items-center justify-center p-4" onClick={() => setOpen(false)}>
          <div className="w-full max-w-md bg-white rounded-2xl shadow-2xl text-slate-900" onClick={e => e.stopPropagation()} role="dialog" aria-label={`Baixar o ${app.name}`}>
            <div className="flex items-center justify-between px-5 py-3 border-b border-slate-200">
              <div className="min-w-0">
                <h3 className="font-bold text-base leading-tight">{app.name}</h3>
                <p className="text-[11px] text-slate-500">Android{version ? ` · versão ${version}` : ''}</p>
              </div>
              <button type="button" onClick={() => setOpen(false)} className="p-1.5 rounded-lg hover:bg-slate-100 text-slate-500 shrink-0" aria-label="Fechar"><X className="w-4 h-4" /></button>
            </div>
            <div className="p-5 space-y-4">
              <p className="text-sm text-slate-600">{app.note}</p>
              <div className="flex flex-col sm:flex-row items-center gap-4">
                <div className="w-[180px] h-[180px] shrink-0 rounded-xl border border-slate-200 flex items-center justify-center bg-white">
                  {qr ? <img src={qr} alt="QR Code para baixar o aplicativo" className="w-[170px] h-[170px]" /> : <span className="text-xs text-slate-400">Gerando QR Code…</span>}
                </div>
                <div className="text-xs text-slate-600 space-y-1.5">
                  <p><b>No celular:</b> aponte a câmera para o QR Code e toque no link para baixar.</p>
                  <p><b>Neste computador:</b> baixe e copie o arquivo para o celular.</p>
                </div>
              </div>
              <a href={url} download className="w-full min-h-[48px] rounded-xl bg-[#F97316] hover:bg-[#EA580C] text-white text-sm font-bold inline-flex items-center justify-center gap-2">
                <Download className="w-4 h-4" /> Baixar o aplicativo (.apk)
              </a>
              <ol className="text-[11px] text-slate-600 list-decimal pl-4 space-y-0.5">
                <li>Abra o arquivo baixado no celular Android.</li>
                <li>Se pedir, permita "instalar apps desta fonte" (fontes desconhecidas).</li>
                <li>Entre com o mesmo usuário e senha da plataforma.</li>
              </ol>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
