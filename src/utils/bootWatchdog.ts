/**
 * Início do app sem tela branca:
 * - a fonte do Google é carregada depois (não bloqueia o início com internet ruim);
 * - se o app não abrir em BOOT_WAIT_MS, a tela de carregamento (index.html, #boot-splash)
 *   mostra o aviso e o botão "Reiniciar o app" (no APP Android reinicia o processo inteiro,
 *   o mesmo efeito de reiniciar o celular; no navegador recarrega a página).
 */
const FONTS_URL = 'https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&family=JetBrains+Mono:wght@400;500;700&display=swap';
export const BOOT_WAIT_MS = 20000;

/** Fonte do sistema: adicionada pelo JavaScript não bloqueia a abertura do app. */
export function loadWebFonts(doc: Document = document): void {
  try {
    if (doc.querySelector('link[data-jvm-fonts]')) return;
    const link = doc.createElement('link');
    link.rel = 'stylesheet';
    link.href = FONTS_URL;
    link.setAttribute('data-jvm-fonts', '1');
    doc.head.appendChild(link);
  } catch { /* sem fonte do Google: usa a do sistema */ }
}

type RestartBridge = { restartApp?: () => void };

/** Reinicia o app: no Android o processo todo (destrava o armazenamento do WebView); no navegador recarrega. */
export function restartApp(): void {
  try {
    const b = (window as unknown as { AndroidBridge?: RestartBridge }).AndroidBridge;
    if (b && typeof b.restartApp === 'function') { b.restartApp(); return; }
  } catch { /* segue para o reload */ }
  window.location.reload();
}

/** Mostra o problema na tela de carregamento, com o botão Reiniciar o app. */
export function showBootProblem(message: string, help: string, doc: Document = document): boolean {
  const msg = doc.getElementById('boot-msg');
  if (!msg) return false; // o app já abriu (o React trocou a tela de carregamento)
  msg.textContent = message;
  const helpEl = doc.getElementById('boot-help');
  if (helpEl) { helpEl.textContent = help; helpEl.style.display = 'block'; }
  const btn = doc.getElementById('boot-restart') as HTMLButtonElement | null;
  if (btn) {
    btn.style.display = 'inline-block';
    btn.onclick = () => { btn.disabled = true; btn.textContent = 'Reiniciando…'; restartApp(); };
  }
  return true;
}

/** Avisa se o app não abrir a tempo. Devolve a função que cancela o aviso (chamar quando o app abrir). */
export function startBootWatchdog(ms = BOOT_WAIT_MS): () => void {
  const timer = setTimeout(() => {
    showBootProblem(
      'O app está demorando para abrir.',
      'Se continuar nesta tela, toque em Reiniciar o app. Os dados guardados no celular não são apagados.'
    );
  }, ms);
  return () => clearTimeout(timer);
}
