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

  // As fontes (Inter e Inter Tight) vêm do próprio domínio, declaradas no landing.css.
  const preencherAno = () => {
    const ano = document.getElementById('landing-year');
    if (ano) ano.textContent = String(new Date().getFullYear());
  };
  // Demonstrações animadas (SVG com animação infinita) custam processamento a cada quadro. A página
  // abre com a imagem parada e só troca pela animada quando a seção aparece na tela.
  const animarAoAparecer = () => {
    const imagens = document.querySelectorAll('img[data-animated-src]');
    if (!imagens.length || !('IntersectionObserver' in window)) return;
    if (window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    const observador = new IntersectionObserver((entradas) => {
      for (const entrada of entradas) {
        if (!entrada.isIntersecting) continue;
        const img = entrada.target;
        observador.unobserve(img);
        const animada = new Image();
        animada.onload = () => { img.src = animada.src; };
        animada.src = img.dataset.animatedSrc;
      }
    }, { threshold: 0.2 });
    imagens.forEach((img) => observador.observe(img));
  };
  const iniciar = () => { preencherAno(); animarAoAparecer(); };
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', iniciar);
  else iniciar();
})();
