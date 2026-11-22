import {StrictMode} from 'react';
import {createRoot} from 'react-dom/client';
import './index.css';
import { initPwaInstall } from './services/pwaInstallService';
import { StorageStuckError, initLocalStore } from './services/localStore';
import { loadWebFonts, showBootProblem, startBootWatchdog } from './utils/bootWatchdog';
import { MANAGED_STORAGE_KEYS } from './services/storageKeys';
import { MODULE_STORAGE_KEYS } from './modules/storageKeys';
import { ensureWebStorage } from './utils/storageFallback';
import { installStaleChunkReload } from './utils/staleChunkReload';

// Iframe restrito (sem acesso ao armazenamento): usa memória
ensureWebStorage();

// Versão nova publicada com a página aberta: recarrega quando faltar um arquivo antigo
installStaleChunkReload();

// Instalação pelo navegador (PWA) + armazenamento persistente dos dados offline
initPwaInstall();

// Fonte do Google depois de abrir (no <head> bloqueava o início com internet ruim → tela branca)
loadWebFonts();

// Se o app não abrir a tempo, a tela de carregamento mostra "Reiniciar o app"
const stopBootWatchdog = startBootWatchdog();

// Os dados do aparelho (IndexedDB) precisam estar carregados antes de o app
// ser importado: o motor de sincronização lê o armazenamento ao iniciar.
initLocalStore([...MANAGED_STORAGE_KEYS, ...MODULE_STORAGE_KEYS])
  .then(() => true, err => {
    if (err instanceof StorageStuckError) {
      // armazenamento travado: abrir agora seria abrir SEM os dados (e gravar por cima deles)
      stopBootWatchdog();
      showBootProblem(err.message, 'Toque em Reiniciar o app. Os dados guardados no celular continuam lá; se não resolver, reinicie o celular.');
      return false;
    }
    console.error('[Armazenamento] Falha ao iniciar:', err);
    return true;
  })
  .then(async ok => {
    if (!ok) return;
    const { default: App } = await import('./App.tsx');
    createRoot(document.getElementById('root')!).render(
      <StrictMode>
        <App />
      </StrictMode>,
    );
    stopBootWatchdog();
  })
  .catch(err => {
    console.error('[Início] Falha ao abrir o app:', err);
    stopBootWatchdog();
    showBootProblem('Não foi possível abrir o app.', 'Toque em Reiniciar o app.');
  });
