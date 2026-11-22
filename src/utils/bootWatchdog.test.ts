import { describe, expect, it } from 'vitest';
import { loadWebFonts, showBootProblem } from './bootWatchdog';

/** Documento mínimo (os testes rodam fora do navegador). */
function fakeDoc(withSplash: boolean) {
  const els: Record<string, any> = withSplash
    ? { 'boot-msg': { textContent: 'Carregando…' }, 'boot-help': { textContent: '', style: { display: 'none' } }, 'boot-restart': { style: { display: 'none' } } }
    : {};
  const head: any[] = [];
  return {
    els, head,
    doc: {
      getElementById: (id: string) => els[id] || null,
      querySelector: () => (head.length ? head[0] : null),
      createElement: () => ({ setAttribute() {}, rel: '', href: '' }),
      head: { appendChild: (e: any) => head.push(e) }
    } as unknown as Document
  };
}

describe('Tela de carregamento', () => {
  it('mostra o problema e o botão Reiniciar o app', () => {
    const { doc, els } = fakeDoc(true);
    expect(showBootProblem('Travou.', 'Toque em reiniciar.', doc)).toBe(true);
    expect(els['boot-msg'].textContent).toBe('Travou.');
    expect(els['boot-help'].style.display).toBe('block');
    expect(els['boot-restart'].style.display).toBe('inline-block');
    expect(typeof els['boot-restart'].onclick).toBe('function');
  });
  it('com o app já aberto não faz nada', () => {
    expect(showBootProblem('x', 'y', fakeDoc(false).doc)).toBe(false);
  });
  it('a fonte do Google entra uma vez só, sem bloquear o início', () => {
    const { doc, head } = fakeDoc(true);
    loadWebFonts(doc); loadWebFonts(doc);
    expect(head.length).toBe(1);
    expect(head[0].href).toContain('fonts.googleapis.com');
  });
});
