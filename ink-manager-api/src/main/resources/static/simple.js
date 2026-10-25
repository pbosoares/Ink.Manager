'use strict';
// ENTREGA STATIC MARCAE CONCLUIDA: catalogo/link, reserva publica e recebimentos; aguardando aceite E2E do Engenheiro.
// Copy MARCAE-COPY.md recebida e aceita pelo Marketing; titulo adaptado ao layout existente.
// Flags recebimentoConfirmado/podeConfirmarRecebimento e snapshots vem da API; team/billing preservados.
(() => {
  const owner = () => state.user?.perfil === 'PROPRIETARIO';
  const money = value => Number(value).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  const context = () => [state.generation, state.user?.id, state.studio?.id].join('|');
  let activeContext = '', services = [], catalogLoaded = false, catalogBusy = false;
  let serviceEdit = null, receiptEdit = null, receiptsKey = '', receiptsRequest = 0, receiptBusy = false;
  const knownReceipts = new Map();
  function current(key) { return state.token && owner() && key === context(); }
  function serviceRows() {
    $('service-list').innerHTML = services.length ? services.map(s => `<article class="service-row"><div><strong>${escapeHtml(s.nome)}</strong><p>${money(s.preco)} · ${s.duracaoMinutos} min · ${s.ativo ? 'Disponível no link' : 'Arquivado'}</p></div><button type="button" class="secondary" data-service-edit="${s.id}">Editar</button></article>`).join('') : '<p>Cadastre seu primeiro serviço com nome, preço e duração.</p>';
  }
  async function loadServices() {
    if (!owner() || catalogBusy) return;
    const key = context(); catalogBusy = true;
    $('service-message').textContent = 'Carregando serviços…';
    $('public-link-error').textContent = '';
    try {
      const results = await Promise.allSettled([api('/servicos'), api('/servicos/link')]);
      if (!current(key)) return;
      const [catalog, link] = results;
      if (catalog.status === 'fulfilled') {
        services = catalog.value; catalogLoaded = true; serviceRows(); $('service-message').textContent = '';
      } else $('service-message').textContent = catalog.reason.message;
      if (link.status === 'fulfilled') {
        const url = new URL(link.value.caminhoCurto || link.value.caminho, location.origin);
        if (url.origin !== location.origin || !(url.pathname === '/agendar.html' || /^\/a\/[A-Za-z0-9_-]+$/.test(url.pathname))) throw new Error('Link de agendamento inválido.');
        $('public-link').value = url.href; $('open-public-link').href = url.href; $('open-public-link').hidden = false; $('copy-public-link').disabled = false;
      } else $('public-link-error').textContent = link.reason.message;
    } catch (error) { if (current(key)) $('service-message').textContent = error.message; }
    finally { if (key === context()) catalogBusy = false; }
  }
  function clearService() {
    serviceEdit = null; $('service-form').reset(); $('service-form').elements.ativo.checked = true;
    $('service-form-title').textContent = 'Novo serviço'; $('service-save').textContent = 'Salvar serviço';
  }
  $('service-list').addEventListener('click', event => {
    const button = event.target.closest('[data-service-edit]'); if (!button || !owner()) return;
    serviceEdit = services.find(s => String(s.id) === button.dataset.serviceEdit); if (!serviceEdit) return;
    const form = $('service-form');
    form.elements.nome.value = serviceEdit.nome; form.elements.preco.value = Number(serviceEdit.preco).toFixed(2);
    form.elements.duracaoMinutos.value = serviceEdit.duracaoMinutos; form.elements.ativo.checked = serviceEdit.ativo;
    $('service-form-title').textContent = 'Editar serviço'; $('service-message').textContent = '';
    form.elements.nome.focus();
  });
  $('service-reset').onclick = clearService;
  $('reload-services').onclick = async () => { clearService(); await loadServices(); };
  $('service-form').addEventListener('submit', async event => {
    event.preventDefault(); if (!owner() || $('service-fields').disabled) return;
    const form = event.currentTarget, key = context(), edit = serviceEdit;
    const name = form.elements.nome.value.trim();
    if (!name) { $('service-message').textContent = 'Informe o nome do serviço.'; return; }
    const body = { nome: name, preco: Number(form.elements.preco.value), duracaoMinutos: Number(form.elements.duracaoMinutos.value), ativo: form.elements.ativo.checked };
    if (edit) body.version = edit.version;
    $('service-fields').disabled = true; $('service-message').textContent = '';
    try {
      await api('/servicos' + (edit ? '/' + edit.id : ''), { method: edit ? 'PUT' : 'POST', body });
      if (!current(key)) return;
      clearService(); $('service-message').textContent = 'Serviço salvo.'; catalogLoaded = false; await loadServices(); notify('Serviço salvo.');
    } catch (error) { if (current(key)) $('service-message').textContent = error.message + ' Se o serviço mudou, use Recarregar serviços antes de editar novamente.'; }
    finally { if (key === context()) $('service-fields').disabled = false; }
  });
  $('copy-public-link').onclick = async () => {
    const input = $('public-link');
    try { await navigator.clipboard.writeText(input.value); $('public-link-error').textContent = 'Link copiado. Compartilhe com seus clientes.'; }
    catch { input.focus(); input.select(); $('public-link-error').textContent = 'Selecione e copie o link acima.'; }
  };
  function appointmentDetails(item) {
    return `<small class="appointment-service">${escapeHtml(item.duracaoMinutos)} min${item.precoPrevisto == null ? ' · preço não informado' : ' · ' + money(item.precoPrevisto)}</small>`;
  }
  function receiptAction(item) {
    if (item.recebimentoConfirmado || knownReceipts.has(item.id)) return '<small class="receipt-recorded">Recebimento confirmado</small>';
    if (!owner() || !item.podeConfirmarRecebimento) return '';
    return `<button type="button" class="text-button" data-confirm-receipt="${item.id}">Concluir atendimento e informar valor</button>`;
  }
  document.addEventListener('click', event => {
    const button = event.target.closest('[data-confirm-receipt]'); if (!button || !owner() || !state.loaded) return;
    receiptEdit = state.appointments.find(item => String(item.id) === button.dataset.confirmReceipt);
    if (!receiptEdit || receiptEdit.recebimentoConfirmado || !receiptEdit.podeConfirmarRecebimento) return;
    $('receipt-description').textContent = receiptEdit.cliente.nome + ' · ' + (receiptEdit.servicoNome || receiptEdit.descricao || 'Atendimento') + ' · ' + receiptEdit.data.split('-').reverse().join('/') + ' às ' + receiptEdit.horario.slice(0, 5);
    $('receipt-form').reset(); $('receipt-form').elements.valor.value = receiptEdit.precoPrevisto == null ? '' : Number(receiptEdit.precoPrevisto).toFixed(2);
    $('receipt-error').textContent = ''; $('receipt-dialog').showModal();
  });
  $('receipt-close').onclick = () => $('receipt-dialog').close();
  $('receipt-dialog').addEventListener('cancel', event => { if (receiptBusy) event.preventDefault(); });
  $('receipt-form').addEventListener('submit', async event => {
    event.preventDefault(); if (!owner() || !receiptEdit || receiptBusy) return;
    const key = context(), id = receiptEdit.id, value = Number(event.currentTarget.elements.valor.value);
    if (!Number.isFinite(value) || value < 0) return;
    receiptBusy = true; $('receipt-fields').disabled = true; $('receipt-close').disabled = true; $('receipt-error').textContent = '';
    try {
      const result = await api('/recebimentos/marcacoes/' + id, { method: 'POST', body: { valor: value } });
      if (!current(key)) return;
      knownReceipts.set(id, result); receiptsKey = ''; $('receipt-dialog').close(); notify('Atendimento e recebimento confirmados.');
      await loadData();
    } catch (error) { if (current(key)) $('receipt-error').textContent = error.message + ' Em falha de conexão, tente novamente com o mesmo valor.'; }
    finally { if (key === context()) { receiptBusy = false; $('receipt-fields').disabled = false; $('receipt-close').disabled = false; } }
  });
  const period = (start, end) => start.split('-').reverse().join('/') + ' a ' + end.split('-').reverse().join('/');
  async function loadReceipts() {
    if (!owner() || !state.loaded) return;
    const key = context(), number = ++receiptsRequest, date = $('receipts-date').value;
    receiptsKey = key + '|' + date;
    $('receipts-error').textContent = ''; $('receipts-loading').hidden = false;
    $('receipts-data').hidden = true; $('receipts-view').setAttribute('aria-busy', 'true');
    try {
      const result = await api('/recebimentos' + (date ? '?data=' + encodeURIComponent(date) : ''));
      if (!current(key) || number !== receiptsRequest) return;
      $('receipts-week').textContent = money(result.totalSemana); $('receipts-month').textContent = money(result.totalMes);
      $('receipts-week-period').textContent = period(result.inicioSemana, result.fimSemana);
      $('receipts-month-period').textContent = period(result.inicioMes, result.fimMes);
      $('receipts-timezone').textContent = 'Totais pela data de confirmação, no fuso ' + result.fusoHorario + '. A lista reúne o mês e a semana selecionados.';
      result.recebimentos.forEach(r => knownReceipts.set(r.marcacaoId, r));
      $('receipts-list').innerHTML = result.recebimentos.length ? `<div class="table-scroll"><table><thead><tr><th>CONFIRMADO EM</th><th>CLIENTE / SERVIÇO</th><th>VALOR</th></tr></thead><tbody>${result.recebimentos.map(r => `<tr><td>${escapeHtml(new Date(r.confirmadoEm).toLocaleString('pt-BR', { timeZone: result.fusoHorario, dateStyle: 'short', timeStyle: 'short' }))}</td><td>${escapeHtml(r.cliente)}<small>${escapeHtml(r.servico || 'Atendimento')}</small></td><td>${money(r.valor)}</td></tr>`).join('')}</tbody></table></div>` : '<p class="empty">Nenhum recebimento confirmado neste período.</p>';
      $('receipts-data').hidden = false;
    } catch (error) { if (current(key) && number === receiptsRequest) $('receipts-error').textContent = error.message + ' Use Atualizar para tentar novamente.'; }
    finally { if (current(key) && number === receiptsRequest) { $('receipts-loading').hidden = true; $('receipts-view').setAttribute('aria-busy', 'false'); } }
  }
  $('receipts-date').onchange = loadReceipts;
  $('reload-receipts').onclick = loadReceipts;
  function close() {
    activeContext = ''; catalogLoaded = false; catalogBusy = false; services = []; serviceEdit = null; receiptEdit = null; receiptBusy = false; window.inkWaitlist?.close();
    receiptsKey = ''; ++receiptsRequest; knownReceipts.clear(); $('receipt-dialog').close();
    $('service-list').replaceChildren(); $('receipts-list').replaceChildren(); $('receipts-data').hidden = true;
    ['receipts-week','receipts-month','receipts-week-period','receipts-month-period','receipts-timezone','receipt-description'].forEach(id => { $(id).textContent = ''; });
    $('receipt-form').reset(); $('receipts-date').value = '';
    $('public-link').value = ''; $('open-public-link').removeAttribute('href'); $('open-public-link').hidden = true; $('copy-public-link').disabled = true;
    $('service-fields').disabled = false; $('receipt-fields').disabled = false; $('receipt-close').disabled = false;
    $('service-message').textContent = ''; $('public-link-error').textContent = ''; $('receipts-error').textContent = ''; $('receipt-error').textContent = ''; clearService();
  }
  function renderSimple() {
    if (!owner() || !state.loaded) return;
    const key = context();
    if (activeContext !== key) { close(); activeContext = key; }
    if (state.view === 'studio' && !catalogLoaded) loadServices();
    window.inkWaitlist?.render();
    if (state.view === 'receipts' && receiptsKey !== key + '|' + $('receipts-date').value) loadReceipts();
  }
  window.inkSimple = { render: renderSimple, close, receiptAction, appointmentDetails };
  if (state.loaded) { render(); }
})();
