'use strict';
(() => {
  let rendering = false;
  let returned = false;
  const formatDate = value => value ? new Date(value).toLocaleDateString('pt-BR') : '-';
  async function render() {
    const container = $('billing-content');
    if (!container || state.user?.perfil !== 'PROPRIETARIO' || rendering) return;
    rendering = true;
    const generation = state.generation;
    try {
      const info = await api('/assinatura');
      if (generation !== state.generation || !state.token) return;
      const price = Number(info.mensalidade).toLocaleString('pt-BR',{style:'currency',currency:'BRL'});
      const labels = {AVALIACAO:'Periodo de avaliacao',ATIVA:'Assinatura ativa',EXPIRADA:'Renovacao necessaria'};
      container.innerHTML = `<p><strong>${labels[info.status] || info.status}</strong></p><p>${price} por mes / avaliacao ate ${formatDate(info.avaliacaoAte)}${info.pagoAte ? `<br>Periodo pago ate ${formatDate(info.pagoAte)}` : ''}</p><p>Cartao: renovacao automatica mensal. A avaliacao e de ate 14 dias, limitada ao prazo ainda disponivel desde a criacao da conta. Pix: pagamento avulso de cada mensalidade, sem renovacao automatica.</p>${!info.cobrancaDisponivel ? '<p>Stripe ainda esta sendo configurado pela plataforma. Nenhuma cobranca sera feita nesta tela.</p>' : ''}<div class="availability-slots"><button class="primary" data-billing="CARTAO" ${info.cobrancaDisponivel ? '' : 'disabled'}>Assinar com cartao</button><button class="secondary" data-billing="PIX" ${info.cobrancaDisponivel ? '' : 'disabled'}>Pagar mensalidade por Pix</button><button class="text-button" id="sync-subscription" ${info.cobrancaDisponivel ? '' : 'disabled'}>Ja paguei: atualizar status</button>${info.cartaoEmRecorrencia ? '<button class="text-button" id="cancel-subscription">Cancelar renovacao automatica</button>' : ''}</div><p id="billing-message" role="status"></p><small>Cancelar a recorrencia preserva o periodo ja pago. A liberacao depende da confirmacao do Stripe, nunca apenas do retorno do checkout.</small>`;
      container.onclick = async event => {
        const button = event.target.closest('button'); if (!button || button.disabled) return;
        container.querySelectorAll('button').forEach(item => {item.disabled=true;});
        try {
          if (button.dataset.billing) {
            const actionGeneration = state.generation;
            const checkout = await api('/assinatura/checkout',{method:'POST',body:{metodo:button.dataset.billing}});
            if (actionGeneration === state.generation && state.token) location.assign(checkout.url);
          } else if (button.id === 'sync-subscription') {
            await api('/assinatura/sincronizar',{method:'POST'}); rendering=false; await render();
          } else if (button.id === 'cancel-subscription') {
            if (!confirm('Cancelar a renovacao automatica? O acesso continua ate o fim do periodo pago.')) return;
            await api('/assinatura/cancelar-recorrencia',{method:'POST'}); rendering=false; await render();
          }
        } catch (error) { if ($('billing-message')) $('billing-message').textContent=error.message; }
        finally { container.querySelectorAll('button').forEach(item => {item.disabled=!info.cobrancaDisponivel;}); }
      };
      if (!returned && new URLSearchParams(location.search).get('assinatura') === 'retorno') {
        returned=true; history.replaceState(null,'',location.pathname); setTimeout(() => setView('studio'),0);
        if (info.cobrancaDisponivel) {await api('/assinatura/sincronizar',{method:'POST'});rendering=false;await render();}
      }
    } catch (error) { if (generation === state.generation) container.textContent=error.message; }
    finally {rendering=false;}
  }
  window.inkBilling={render};
  if(state.loaded)render();
})();
