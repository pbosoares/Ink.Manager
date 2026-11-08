'use strict';
(() => {
  let rendering = false;
  let returned = false;
  const formatDate = value => {
    const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(String(value || ''));
    return match ? match[3] + '/' + match[2] + '/' + match[1] : '-';
  };
  async function render(summaryOverride = null) {
    const container = $('billing-content');
    if (!container || state.user?.perfil !== 'PROPRIETARIO' || rendering) return;
    rendering = true;
    const generation = state.generation;
    try {
      const info = summaryOverride || await api('/assinatura');
      if (generation !== state.generation || !state.token) return;
      const price = Number(info.mensalidade).toLocaleString('pt-BR',{style:'currency',currency:'BRL'});
      const labels = {AVALIACAO:'Periodo de avaliacao',ATIVA:'Assinatura ativa',EXPIRADA:'Assinatura expirada'};
      const cancellationScheduled = info.recorrenciaCancelamentoAgendado === true;
      const cancellationNotice = cancellationScheduled
        ? '<p role="status">Cancelamento programado; acesso ate ' + formatDate(info.pagoAte || info.avaliacaoAte) + '.</p><small>A reativacao nao altera as datas de acesso nem confirma pagamento.</small>'
        : '';
      const expiredNotice = info.status === 'EXPIRADA'
        ? '<p role="status">EXPIRADA continua bloqueada ate o pagamento ser confirmado. Reativar a recorrencia nao libera acesso nem prorroga o periodo.</p>'
        : '';
      const cardCheckout = cancellationScheduled ? '' : '<button class="primary" data-billing="CARTAO" ' + (info.cobrancaDisponivel ? '' : 'disabled') + '>Assinar com cartao</button>';
      const recurrenceAction = cancellationScheduled
        ? '<button class="secondary" id="reactivate-subscription" ' + (info.cobrancaDisponivel ? '' : 'disabled') + '>Reativar recorrencia do cartao</button>'
        : info.cartaoEmRecorrencia ? '<button class="text-button" id="cancel-subscription">Cancelar renovacao automatica</button>' : '';
      container.innerHTML = '<p><strong>' + (labels[info.status] || info.status) + '</strong></p><p>' + price + ' por mes / avaliacao ate ' + formatDate(info.avaliacaoAte) + (info.pagoAte ? '<br>Periodo pago ate ' + formatDate(info.pagoAte) : '') + '</p><p>Cartao: renovacao automatica mensal. A avaliacao e de ate 14 dias, limitada ao prazo ainda disponivel desde a criacao da conta. Pix: pagamento avulso de cada mensalidade, sem renovacao automatica.</p>' + cancellationNotice + expiredNotice + (!info.cobrancaDisponivel ? '<p>Stripe ainda esta sendo configurado pela plataforma. Nenhuma cobranca sera feita nesta tela.</p>' : '') + '<div class="availability-slots">' + cardCheckout + '<button class="secondary" data-billing="PIX" ' + (info.cobrancaDisponivel ? '' : 'disabled') + '>Pagar mensalidade por Pix</button><button class="text-button" id="sync-subscription" ' + (info.cobrancaDisponivel ? '' : 'disabled') + '>Ja paguei: atualizar status</button>' + recurrenceAction + '</div><p id="billing-message" role="status"></p><small>Cancelar a recorrencia preserva o periodo ja pago. A liberacao depende da confirmacao do Stripe, nunca apenas do retorno do checkout.</small>';
      container.onclick = async event => {
        const button = event.target.closest('button'); if (!button || button.disabled) return;
        const actionGeneration = state.generation;
        let rerendered = false;
        container.querySelectorAll('button').forEach(item => {item.disabled=true;});
        try {
          if (button.dataset.billing) {
            const checkout = await api('/assinatura/checkout',{method:'POST',body:{metodo:button.dataset.billing}});
            if (actionGeneration === state.generation && state.token) location.assign(checkout.url);
          } else if (button.id === 'sync-subscription') {
            await api('/assinatura/sincronizar',{method:'POST'}); rendering=false; await render();
          } else if (button.id === 'cancel-subscription') {
            if (!confirm('Cancelar a renovacao automatica? O acesso continua ate o fim do periodo pago.')) return;
            await api('/assinatura/cancelar-recorrencia',{method:'POST'}); rendering=false; await render();
          } else if (button.id === 'reactivate-subscription') {
            const updated = await api('/assinatura/reativar-recorrencia',{method:'POST'});
            if (actionGeneration === state.generation && state.token) { rendering=false; await render(updated); rerendered=true; }
          }
        } catch (error) {
          if (button.id === 'reactivate-subscription' && error.status === 409) {
            try {
              const latest = await api('/assinatura');
              if (actionGeneration === state.generation && state.token) {
                rendering = false;
                await render(latest);
                rerendered = true;
                if ($('billing-message')) $('billing-message').textContent = latest.recorrenciaCancelamentoAgendado === true
                  ? error.message
                  : 'A recorrencia nao esta mais agendada. As opcoes de pagamento foram atualizadas.';
              }
            } catch (refreshError) {
              if (actionGeneration === state.generation && $('billing-message')) $('billing-message').textContent = refreshError.message || error.message;
            }
          } else if (actionGeneration === state.generation && $('billing-message')) {
            $('billing-message').textContent = error.message;
          }
        } finally {
          if (!rerendered && actionGeneration === state.generation) {
            container.querySelectorAll('button').forEach(item => {
              item.disabled = Boolean(item.dataset.billing || item.id === 'sync-subscription' || item.id === 'reactivate-subscription') && !info.cobrancaDisponivel;
            });
          }
        }
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
