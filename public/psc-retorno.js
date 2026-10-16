// Retorno do PSC (certificado em nuvem): devolve o código à janela do JVM Lab
// pela mensagem entre janelas e pelo localStorage (quando o site do PSC corta
// o vínculo entre as janelas). O código só vale com o verificador PKCE, que
// fica na janela que pediu a autorização.
(function () {
  var p = new URLSearchParams(window.location.search);
  var data = {
    type: 'jvm-psc',
    code: p.get('code') || '',
    state: p.get('state') || '',
    error: p.get('error_description') || p.get('error') || '',
    at: Date.now()
  };
  var msg = document.getElementById('msg');
  try { localStorage.setItem('jvm_psc_return', JSON.stringify(data)); } catch (e) { /* sem armazenamento */ }
  try { if (window.opener) window.opener.postMessage(data, window.location.origin); } catch (e) { /* vínculo cortado */ }
  if (data.error) {
    msg.textContent = 'Autorização não concluída: ' + data.error + '. Feche esta janela.';
    return;
  }
  msg.textContent = data.code
    ? 'Autorização recebida. Esta janela pode ser fechada.'
    : 'Nenhum código recebido do provedor. Feche esta janela e tente de novo.';
  if (data.code) setTimeout(function () { window.close(); }, 800);
})();
