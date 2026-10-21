'use strict';
(() => {
  const $ = id => document.getElementById(id);
  const token = new URLSearchParams(location.hash.slice(1)).get('token');
  const status = $('action-status');
  const error = $('action-error');
  const details = $('action-details');
  const button = $('action-submit');
  let acao = '';
  const labels = { CONFIRMAR: 'Confirmar agendamento', REAGENDAR: 'Salvar novo horário', CANCELAR: 'Cancelar agendamento' };
  const dateLabel = value => new Date(value + 'T12:00:00').toLocaleDateString('pt-BR');
  async function post(path, body) {
    const response = await fetch(path, { method: 'POST', credentials: 'omit', cache: 'no-store',
      headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body), signal: AbortSignal.timeout(15000) });
    const data = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(data.erro || 'Não foi possível atualizar. Peça ajuda ao estabelecimento.');
    return data;
  }
  async function load() {
    if (!token || token.length < 32) { status.textContent = 'Este link expirou ou não é válido.'; return; }
    try {
      const item = await post('/publico/acoes/visualizar', { token });
      acao = item.acao;
      $('action-summary').replaceChildren();
      const rows = [['Serviço', item.servico], ['Profissional', item.profissional], ['Dia', dateLabel(item.data)], ['Horário', item.horario.slice(0, 5)], ['Duração', `${item.duracaoMinutos} min`], ['Fuso horário', item.fusoHorario]];
      for (const [key, value] of rows) { const dt = document.createElement('dt'); const dd = document.createElement('dd'); dt.textContent = key; dd.textContent = value; $('action-summary').append(dt, dd); }
      $('reschedule-form').hidden = acao !== 'REAGENDAR';
      button.textContent = labels[acao] || 'Atualizar agendamento';
      button.disabled = !labels[acao];
      details.hidden = false; status.textContent = acao === 'REAGENDAR' ? 'Escolha um novo horário. A mudança só será concluída se ele ainda estiver livre.' : 'Confira os dados antes de continuar.';
    } catch (e) { status.textContent = ''; error.textContent = e.message; }
  }
  button.addEventListener('click', async () => {
    button.disabled = true; error.textContent = ''; status.textContent = 'Salvando…';
    try {
      const pedido = { token, acao };
      if (acao === 'REAGENDAR') { pedido.data = $('action-date').value; pedido.horario = $('action-time').value; if (!pedido.data || !pedido.horario) throw new Error('Escolha o novo dia e horário.'); }
      await post('/publico/acoes', pedido);
      details.hidden = true; status.textContent = acao === 'CANCELAR' ? 'Agendamento cancelado.' : acao === 'REAGENDAR' ? 'Novo horário salvo.' : 'Presença confirmada.';
    } catch (e) { status.textContent = ''; error.textContent = e.message; button.disabled = false; }
  });
  load();
})();
