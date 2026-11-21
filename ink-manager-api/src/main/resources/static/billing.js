'use strict';
// Tela "Assinatura" do painel (Paper "Checkout Stripe — proposta"): status da conta à esquerda,
// plano e forma de pagamento à direita. O pagamento acontece na página do Stripe; o acesso só é
// liberado quando o servidor confirma o pagamento com o Stripe, nunca pelo retorno do checkout.
(() => {
  const DAY = 86400000;
  const TRIAL_DAYS = 14;
  let info = null;
  let loadedFor = null;
  let loading = false;
  let busy = false;
  let metodo = 'CARTAO';
  let message = '';
  let pendingReturn = '';
  let returnChecked = false;

  const owner = () => state.user?.perfil === 'PROPRIETARIO';
  const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const date = value => value ? new Date(value).toLocaleDateString('pt-BR') : '';
  const amount = value => Number(value).toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  const daysLeft = value => value ? Math.max(0, Math.ceil((new Date(value).getTime() - Date.now()) / DAY)) : 0;
  const check = '<svg class="bill-check" viewBox="0 0 16 16" aria-hidden="true"><circle cx="8" cy="8" r="8"/><path d="M4.8 8.2 7 10.3 11.2 5.9"/></svg>';

  function statusCard() {
    if (info.status === 'ATIVA') {
      const renews = info.cartaoEmRecorrencia;
      return `<article class="bill-card bill-status">
        <div class="bill-status-head"><span class="bill-badge is-success">Assinatura ativa</span><span class="bill-meta">Pago até ${date(info.pagoAte)}</span></div>
        <div class="bill-big"><strong>Em dia</strong><span>${renews ? 'renova sozinha no cartão' : 'mensalidade paga por Pix'}</span></div>
        <p>${renews
          ? `A próxima cobrança acontece no cartão em ${date(info.pagoAte)}. Você não precisa fazer nada.`
          : `Sem renovação automática: pague a próxima mensalidade por Pix até ${date(info.pagoAte)} ou passe para o cartão.`}</p>
      </article>`;
    }
    if (info.status === 'EXPIRADA') {
      return `<article class="bill-card bill-status">
        <div class="bill-status-head"><span class="bill-badge is-danger">Avaliação encerrada</span>${info.avaliacaoAte ? `<span class="bill-meta">Terminou em ${date(info.avaliacaoAte)}</span>` : ''}</div>
        <div class="bill-big"><strong>Assine para continuar</strong></div>
        <p>${info.cobrancaObrigatoria
          ? 'Seus dados estão guardados. Você ainda consulta agenda e clientes, mas novas marcações e alterações ficam pausadas até o pagamento ser confirmado.'
          : 'Seus dados estão guardados. Assine para manter o Marcaê funcionando no seu negócio.'}</p>
      </article>`;
    }
    const left = daysLeft(info.avaliacaoAte);
    return `<article class="bill-card bill-status">
      <div class="bill-status-head"><span class="bill-badge"><i aria-hidden="true"></i>Avaliação gratuita</span><span class="bill-meta">Termina em ${date(info.avaliacaoAte)}</span></div>
      <div class="bill-big"><strong>${left > 1 ? `${left} dias` : left === 1 ? '1 dia' : 'Último dia'}</strong>${left > 0 ? '<span>restantes na avaliação</span>' : ''}</div>
      <progress class="bill-progress" max="${TRIAL_DAYS}" value="${Math.min(left, TRIAL_DAYS)}" aria-label="Dias restantes da avaliação"></progress>
      <p>Assine agora e nada muda: agenda, clientes e link de reservas seguem no ar. No cartão, a primeira cobrança só acontece quando a avaliação termina.</p>
    </article>`;
  }

  function howItWorks() {
    return `<article class="bill-card bill-steps"><h2>Como funciona</h2><ol>
      <li><b>1</b><strong>Escolha cartão ou Pix</strong><span>Cartão renova sozinho todo mês. Pix é pago mês a mês.</span></li>
      <li><b>2</b><strong>Pague no Stripe</strong><span>Página segura. O Marcaê não vê nem guarda seu cartão.</span></li>
      <li><b class="is-success">3</b><strong>Pronto, acesso liberado</strong><span>Assim que o pagamento é confirmado, sem precisar fazer nada.</span></li>
    </ol></article>`;
  }

  function included() {
    return `<article class="bill-card bill-included"><div><h2>Incluso</h2><p>Sem taxa por agendamento.</p></div><ul>
      <li>${check}Agenda da equipe</li><li>${check}Link de reservas online</li>
      <li>${check}Lembretes por e-mail</li><li>${check}Recebimentos e relatórios</li>
    </ul></article>`;
  }

  function option(value, title, description, badge, icon) {
    return `<label class="bill-option${metodo === value ? ' is-selected' : ''}">
      <input type="radio" name="bill-method" value="${value}"${metodo === value ? ' checked' : ''}${busy ? ' disabled' : ''}>
      <span class="bill-option-copy"><span class="bill-option-title"><span>${title}</span>${badge ? `<em>${badge}</em>` : ''}</span><small>${description}</small></span>
      ${icon}
    </label>`;
  }

  function chargeNote() {
    const price = `R$ ${amount(info.mensalidade)}`;
    if (metodo === 'PIX') return `Você paga o QR Code na página do Stripe · ${price}`;
    if (info.status === 'AVALIACAO' && info.avaliacaoAte) return `Primeira cobrança em ${date(info.avaliacaoAte)} · ${price} por mês`;
    return `${price} por mês, com renovação automática`;
  }

  function planCard() {
    const recurring = info.status === 'ATIVA' && info.cartaoEmRecorrencia;
    const disabled = busy || !info.cobrancaDisponivel ? ' disabled' : '';
    const head = `<header class="bill-plan-head">
      <div class="bill-plan-row"><span class="bill-plan-label">PLANO MARCAÊ</span><span class="bill-plan-chip">Tudo incluso</span></div>
      <div class="bill-price"><span>R$</span><strong>${amount(info.mensalidade)}</strong><small>/mês</small></div>
      <p>Menos de R$ 1 por dia para a agenda do negócio inteiro.</p>
    </header>`;
    const sync = `<p class="bill-sync">Já pagou e o acesso não liberou? <button type="button" class="bill-link" id="sync-subscription"${disabled}>Atualizar status</button></p>`;
    const status = `<p id="billing-message" class="bill-message" role="status">${esc(message)}</p>`;
    if (recurring) {
      return `<article class="bill-plan">${head}<div class="bill-plan-body">
        <div class="bill-current">${check}<div><strong>Você assina com cartão</strong><small>Renovação automática todo mês. Para trocar de cartão ou de forma de pagamento, cancele a renovação e assine de novo.</small></div></div>
        <button type="button" class="secondary bill-cancel" id="cancel-subscription"${busy ? ' disabled' : ''}>Cancelar renovação automática</button>
        <p class="bill-note">Cancelar a renovação mantém o acesso até ${date(info.pagoAte)}.</p>
        ${status}${sync}
      </div></article>`;
    }
    const cardIcon = '<svg class="bill-option-icon" viewBox="0 0 28 20" aria-hidden="true"><rect x="1" y="1" width="26" height="18" rx="4"/><path d="M1 7h26M5 14h6"/></svg>';
    const pixIcon = '<svg class="bill-option-icon is-pix" viewBox="0 0 22 22" aria-hidden="true"><path d="M11 2.5 19.5 11 11 19.5 2.5 11Z"/><path class="is-fill" d="M7.5 11 11 7.5 14.5 11 11 14.5Z"/></svg>';
    return `<article class="bill-plan">${head}<div class="bill-plan-body">
      <fieldset class="bill-methods"><legend>Como você prefere pagar?</legend>
        ${option('CARTAO', 'Cartão<span class="bill-wide"> de crédito</span>', 'Renova sozinho todo mês. Sem lembrar de pagar.', 'MAIS PRÁTICO', cardIcon)}
        ${option('PIX', 'Pix', 'Pague uma mensalidade por vez, sem renovação automática.', '', pixIcon)}
      </fieldset>
      <button type="button" class="primary bill-submit" id="billing-submit" data-billing="${metodo}"${disabled}>${metodo === 'PIX' ? 'Pagar mensalidade com Pix' : 'Assinar com cartão'}<svg viewBox="0 0 18 18" aria-hidden="true"><path d="M3.5 9h11M10 4.5 14.5 9 10 13.5"/></svg></button>
      <p class="bill-note">${info.cobrancaDisponivel ? chargeNote() : 'A cobrança ainda está sendo configurada. Nenhum valor será cobrado agora.'}</p>
      ${status}
      <div class="bill-trust"><span><svg viewBox="0 0 14 14" aria-hidden="true"><rect x="2.5" y="6" width="9" height="6.5" rx="1.5"/><path d="M4.5 6V4.5a2.5 2.5 0 0 1 5 0V6"/></svg>Pagamento seguro pelo Stripe</span><span><svg viewBox="0 0 14 14" aria-hidden="true"><path d="M3 7.2 5.8 10 11 4.5"/></svg>${metodo === 'PIX' ? 'Sem renovação automática' : 'Cancele quando quiser'}</span></div>
      ${sync}
    </div></article>`;
  }

  function paint() {
    const container = $('billing-content');
    if (!container) return;
    if (!info) { container.innerHTML = `<p class="bill-loading" role="status">${esc(message || 'Consultando sua assinatura…')}</p>`; return; }
    const recurring = info.status === 'ATIVA' && info.cartaoEmRecorrencia;
    container.innerHTML = `<div class="bill-column">${statusCard()}${recurring ? '' : howItWorks()}${included()}</div>${planCard()}`;
  }

  async function load() {
    const generation = state.generation;
    loading = true;
    try {
      info = await api('/assinatura');
      if (generation !== state.generation || !state.token) return;
      if (pendingReturn === 'retorno' && info.cobrancaDisponivel) {
        pendingReturn = '';
        message = 'Confirmando o pagamento com o Stripe…';
        paint();
        await api('/assinatura/sincronizar', { method: 'POST' });
        info = await api('/assinatura');
        if (generation !== state.generation || !state.token) return;
        message = info.status === 'ATIVA' || info.cartaoEmRecorrencia
          ? 'Pagamento confirmado. Obrigado por assinar o Marcaê!'
          : 'Ainda aguardando a confirmação do Stripe. Isso pode levar alguns minutos; depois, toque em Atualizar status.';
      } else if (pendingReturn === 'cancelada') {
        pendingReturn = '';
        message = 'Pagamento não concluído. Nada foi cobrado.';
      }
    } catch (error) {
      if (generation === state.generation) { info = null; message = error.message; }
    } finally {
      loading = false;
      if (generation === state.generation) paint();
    }
  }

  async function act(run) {
    if (busy) return;
    busy = true; message = ''; paint();
    try { await run(); } catch (error) { message = error.message; }
    finally { busy = false; paint(); }
  }

  function render() {
    if (!owner() || !state.loaded) return;
    if (!returnChecked) {
      returnChecked = true;
      const back = new URLSearchParams(location.search).get('assinatura');
      if (back) {
        pendingReturn = back;
        history.replaceState(null, '', location.pathname + location.hash);
        if (state.view !== 'billing') { setTimeout(() => setView('billing'), 0); return; }
      }
    }
    if (state.view !== 'billing') { loadedFor = null; return; }
    if (loadedFor === state.generation || loading) return;
    loadedFor = state.generation;
    message = pendingReturn ? message : '';
    paint();
    load();
  }

  const container = $('billing-content');
  if (container) {
    container.addEventListener('change', (event) => {
      if (event.target.name !== 'bill-method') return;
      metodo = event.target.value === 'PIX' ? 'PIX' : 'CARTAO';
      message = '';
      paint();
      $('billing-submit')?.focus();
    });
    container.addEventListener('click', (event) => {
      const button = event.target.closest('button');
      if (!button || button.disabled || !info) return;
      const generation = state.generation;
      if (button.id === 'billing-submit') {
        act(async () => {
          const checkout = await api('/assinatura/checkout', { method: 'POST', body: { metodo: button.dataset.billing } });
          if (generation === state.generation && state.token) location.assign(checkout.url);
        });
      } else if (button.id === 'sync-subscription') {
        act(async () => {
          await api('/assinatura/sincronizar', { method: 'POST' });
          info = await api('/assinatura');
          message = 'Status atualizado.';
        });
      } else if (button.id === 'cancel-subscription') {
        if (!confirm('Cancelar a renovação automática? O acesso continua até o fim do período pago.')) return;
        act(async () => {
          await api('/assinatura/cancelar-recorrencia', { method: 'POST' });
          info = await api('/assinatura');
          message = 'Renovação automática cancelada.';
        });
      }
    });
  }

  window.inkBilling = {
    render,
    close() { info = null; loadedFor = null; loading = false; busy = false; metodo = 'CARTAO'; message = ''; pendingReturn = ''; $('billing-content')?.replaceChildren(); }
  };
  render();
})();
