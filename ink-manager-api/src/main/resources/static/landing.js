'use strict';
// Landing pública: o painel e o login moram em /app.
// Links antigos (/#login, /#cadastro, /#redefinir=..., /?assinatura=retorno) e quem já tem
// sessão aberta nesta aba seguem para o app, como antes da separação.
(() => {
  const hash = location.hash;
  const params = new URLSearchParams(location.search);
  const rotaDoApp = /^#(login|cadastro)$/.test(hash) || /^#redefinir=/.test(hash)
    || params.has('redefinir') || params.has('assinatura');
  let sessaoAberta = false;
  try { sessaoAberta = Boolean(sessionStorage.getItem('ink.token')); } catch {}
  if (rotaDoApp || sessaoAberta) {
    location.replace('/app' + location.search + hash);
    return;
  }

  // Fontes da marca sem bloquear a primeira pintura (a landing só usa Inter e Inter Tight).
  const fontes = document.createElement('link');
  fontes.rel = 'preload';
  fontes.as = 'style';
  fontes.href = 'https://fonts.googleapis.com/css2?family=Inter:wght@400..800&family=Inter+Tight:wght@600..800&display=swap';
  fontes.onload = () => { fontes.rel = 'stylesheet'; };
  document.head.append(fontes);

  document.addEventListener('DOMContentLoaded', () => {
    const ano = document.getElementById('landing-year');
    if (ano) ano.textContent = String(new Date().getFullYear());
  });
})();
