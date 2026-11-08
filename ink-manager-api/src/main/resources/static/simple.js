'use strict';
// ENTREGA STATIC MARCAE CONCLUIDA: catalogo/link, reserva publica e recebimentos; aguardando aceite E2E do Engenheiro.
// Copy MARCAE-COPY.md recebida e aceita pelo Marketing; titulo adaptado ao layout existente.
// Flags recebimentoConfirmado/podeConfirmarRecebimento e snapshots vem da API; team/billing preservados.
(() => {
  const owner = () => state.user?.perfil === 'PROPRIETARIO';
  const money = value => Number(value).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  const context = () => [state.generation, state.user?.id, state.studio?.id].join('|');
  let activeContext = '', services = [], catalogLoaded = false, catalogBusy = false, publicLinkReady = false;
  let serviceLoadError = '', linkLoadError = '';
  let serviceEdit = null, receiptEdit = null, receiptsKey = '', receiptsRequest = 0, receiptBusy = false;
  const knownReceipts = new Map();
  function current(key) { return state.token && owner() && key === context(); }
  function normalizedProfessionalIds(values) {
    if (!Array.isArray(values)) return null;
    return [...new Set(values.map(value => {
      const id = Number(value);
      return Number.isSafeInteger(id) && id > 0 ? String(id) : '';
    }).filter(Boolean))];
  }
  function professionalMap() {
    return new Map((state.professionals || []).map(person => [String(person.id), person]));
  }
  function renderProfessionalOptions(selectedIds = []) {
    const root = $('service-professional-options');
    const note = $('service-professional-note');
    if (!root || !note) return;
    const selected = new Set(normalizedProfessionalIds(selectedIds) || []);
    const professionals = (state.professionals || []).filter(person => normalizedProfessionalIds([person.id])?.length);
    const seen = new Set();
    root.replaceChildren();
    professionals.forEach(person => {
      const id = String(Number(person.id));
      if (seen.has(id)) return;
      seen.add(id);
      const label = document.createElement('label');
      label.className = 'service-professional-option';
      const checkbox = document.createElement('input');
      checkbox.type = 'checkbox'; checkbox.name = 'profissionalIds'; checkbox.value = id; checkbox.checked = selected.has(id);
      const text = document.createElement('span');
      text.textContent = (person.nome || 'Profissional') + (person.ativo ? '' : ' · inativo; não aparece em novas reservas');
      label.append(checkbox, text); root.append(label);
    });
    selected.forEach(id => {
      if (seen.has(id)) return;
      const label = document.createElement('label');
      label.className = 'service-professional-option is-unavailable';
      const checkbox = document.createElement('input');
      checkbox.type = 'checkbox'; checkbox.name = 'profissionalIds'; checkbox.value = id; checkbox.checked = true;
      const text = document.createElement('span'); text.textContent = 'Profissional indisponível (ID ' + id + ') · desmarque para remover o vínculo';
      label.append(checkbox, text); root.append(label);
    });
    if (!professionals.length && !selected.size) {
      note.textContent = 'Não há profissionais cadastrados. Cadastre alguém em Equipe e horários; você pode salvar este serviço sem associação e vinculá-lo depois.';
    } else if (!selected.size) {
      note.textContent = 'Nenhum profissional selecionado. O serviço ficará sem opções no link público até receber um vínculo.';
    } else {
      note.textContent = selected.size + (selected.size === 1 ? ' profissional associado.' : ' profissionais associados.') + ' Somente profissionais ativos aparecem para novas reservas.';
    }
  }
  function selectedProfessionalIds() {
    return [...new Set([...$('service-professional-options').querySelectorAll('input[name="profissionalIds"]:checked')]
      .map(input => Number(input.value)).filter(id => Number.isSafeInteger(id) && id > 0))];
  }
  function serviceRows() {
    const people = professionalMap();
    $('service-list').innerHTML = services.length ? services.map(s => {
      const ids = normalizedProfessionalIds(s.profissionalIds);
      const linked = ids?.length ? ids.map(id => {
        const person = people.get(id);
        return person ? escapeHtml(person.nome || 'Profissional') + (person.ativo ? '' : ' (inativo)') : 'ID ' + escapeHtml(id);
      }).join(', ') : '';
      const association = ids == null ? 'Vínculos não retornados pelo servidor; recarregue antes de editar.' : linked || 'Sem profissionais associados · indisponível no link público';
      return `<article class="service-row"><div><strong>${escapeHtml(s.nome)}</strong><p>${money(s.preco)} · ${s.duracaoMinutos} min · ${s.ativo ? 'Disponível no link' : 'Arquivado'}</p><small class="service-row-professionals">Profissionais: ${association}</small></div><button type="button" class="secondary" data-service-edit="${escapeHtml(s.id)}">Editar</button></article>`;
    }).join('') : '<p>Cadastre seu primeiro serviço com nome, preço e duração.</p>';
  }
  function renderSetupProgress() {
    if (!owner() || !state.loaded || !$('setup-progress-list')) return;
    const activeServices = services.filter(service => service.ativo);
    const activeProfessionals = (state.professionals || []).filter(person => person.ativo);
    const activeProfessionalIds = new Set(activeProfessionals.map(person => String(person.id)));
    const hasActiveService = catalogLoaded && activeServices.length > 0;
    const hasService = hasActiveService && activeServices.some(service =>
      (normalizedProfessionalIds(service.profissionalIds) || []).some(id => activeProfessionalIds.has(id))
    );
    const hasProfessionals = activeProfessionals.length > 0;
    const hasSchedule = hasProfessionals && activeProfessionals.every(person => Array.isArray(person.horarios) && person.horarios.length > 0);
    const hasLink = publicLinkReady;
    const steps = [
      ['services', hasService, catalogLoaded ? (hasService ? 'Concluído' : hasActiveService ? 'Associe profissionais' : 'Precisa configurar') : 'Não verificado'],
      ['professionals', hasProfessionals, hasProfessionals ? 'Concluído' : 'Precisa configurar'],
      ['schedule', hasSchedule, hasSchedule ? 'Concluído' : hasProfessionals ? 'Precisa configurar' : 'Aguardando equipe'],
      ['link', hasLink, hasLink ? 'Pronto para compartilhar' : 'Não verificado']
    ];
    steps.forEach(([name, complete, label]) => {
      const item = document.querySelector(`[data-setup-step="${name}"]`);
      if (item) item.dataset.complete = String(Boolean(complete));
      const status = $('setup-' + name + '-status');
      if (status) status.textContent = label;
    });
    const completed = steps.filter(([, complete]) => complete).length;
    $('setup-progress-summary').textContent = catalogLoaded
      ? `${completed} de 4 etapas verificadas. O andamento usa o catálogo, a equipe e os expedientes já cadastrados.`
      : catalogBusy ? 'Consultando serviços e link de reservas…'
        : serviceLoadError ? 'Não foi possível verificar o catálogo: ' + serviceLoadError
          : 'Aguardando os dados de configuração.';

    const next = $('setup-next-action');
    const message = $('setup-next-message');
    next.removeAttribute('data-view');
    delete next.dataset.setupAction;
    if (!catalogLoaded) {
      message.textContent = serviceLoadError ? 'Abra Meu negócio para recarregar o catálogo existente.' : 'A próxima ação depende do catálogo de serviços.';
      next.dataset.view = 'studio'; next.textContent = 'Abrir Meu negócio';
    } else if (!hasActiveService) {
      message.textContent = 'Próxima ação: cadastre ao menos um serviço ativo.';
      next.dataset.view = 'studio'; next.textContent = 'Cadastrar serviço';
    } else if (!hasProfessionals) {
      message.textContent = 'Próxima ação: cadastre um profissional ativo para atender os serviços.';
      next.dataset.view = 'team'; next.textContent = 'Cadastrar profissional';
    } else if (!hasService) {
      message.textContent = 'Próxima ação: associe um profissional ativo a um serviço ativo para habilitar a reserva pública.';
      next.dataset.view = 'studio'; next.textContent = 'Associar profissional';
    } else if (!hasSchedule) {
      message.textContent = 'Próxima ação: configure pelo menos um expediente para cada profissional ativo.';
      next.dataset.view = 'team'; next.textContent = 'Configurar expediente';
    } else if (!hasLink) {
      message.textContent = linkLoadError ? 'Não foi possível confirmar o link: ' + linkLoadError : 'Próxima ação: abra Meu negócio para consultar o link fixo.';
      next.dataset.view = 'studio'; next.textContent = 'Abrir link de reservas';
    } else {
      message.textContent = 'A configuração básica está pronta. Copie o link fixo para compartilhar quando quiser.';
      next.dataset.setupAction = 'copy-link'; next.textContent = 'Copiar link de reservas';
    }
  }
  async function loadServices() {
    if (!owner() || catalogBusy) return;
    const key = context(); catalogBusy = true; catalogLoaded = false;
    serviceLoadError = ''; linkLoadError = ''; publicLinkReady = false;
    $('service-message').textContent = 'Carregando serviços…';
    $('service-list').textContent = 'Carregando serviços…';
    $('public-link-error').textContent = '';
    $('public-link').value = ''; $('open-public-link').removeAttribute('href'); $('open-public-link').hidden = true; $('copy-public-link').disabled = true;
    renderSetupProgress();
    try {
      const results = await Promise.allSettled([api('/servicos'), api('/servicos/link')]);
      if (!current(key)) return;
      const [catalog, link] = results;
      if (catalog.status === 'fulfilled') {
        if (!Array.isArray(catalog.value)) throw new Error('O servidor não retornou a lista de serviços esperada.');
        services = catalog.value; catalogLoaded = true; serviceRows(); $('service-message').textContent = '';
      } else { catalogLoaded = false; serviceLoadError = catalog.reason.message; $('service-message').textContent = catalog.reason.message; $('service-list').textContent = 'Não foi possível carregar os serviços. Recarregue para tentar novamente.'; }
      if (link.status === 'fulfilled') {
        try {
          const url = new URL(link.value.caminhoCurto || link.value.caminho, location.origin);
          if (url.origin !== location.origin || !(url.pathname === '/agendar.html' || /^\/a\/[A-Za-z0-9_-]+$/.test(url.pathname))) throw new Error('Link de agendamento inválido.');
          $('public-link').value = url.href; $('open-public-link').href = url.href; $('open-public-link').hidden = false; $('copy-public-link').disabled = false; publicLinkReady = true;
        } catch (error) { linkLoadError = error.message; $('public-link-error').textContent = error.message; }
      } else { linkLoadError = link.reason.message; $('public-link-error').textContent = link.reason.message; }
    } catch (error) {
      if (current(key)) { catalogLoaded = false; serviceLoadError = error.message; $('service-message').textContent = error.message; $('service-list').textContent = 'Não foi possível carregar os serviços. Recarregue para tentar novamente.'; }
    } finally {
      if (key === context()) { catalogBusy = false; renderSetupProgress(); }
    }
  }
  function clearService() {
    serviceEdit = null; $('service-form').reset(); $('service-form').elements.ativo.checked = true;
    $('service-save').disabled = false;
    $('service-form-title').textContent = 'Novo serviço'; $('service-save').textContent = 'Salvar serviço';
    renderProfessionalOptions([]);
  }
  $('service-list').addEventListener('click', event => {
    const button = event.target.closest('[data-service-edit]'); if (!button || !owner()) return;
    serviceEdit = services.find(s => String(s.id) === button.dataset.serviceEdit); if (!serviceEdit) return;
    const form = $('service-form');
    if (!Array.isArray(serviceEdit.profissionalIds)) {
      form.elements.nome.value = serviceEdit.nome; form.elements.preco.value = Number(serviceEdit.preco).toFixed(2);
      form.elements.duracaoMinutos.value = serviceEdit.duracaoMinutos; form.elements.ativo.checked = serviceEdit.ativo;
      $('service-form-title').textContent = 'Editar serviço'; $('service-message').textContent = 'O servidor não retornou profissionalIds. Recarregue o catálogo antes de alterar este serviço para preservar os vínculos existentes.';
      $('service-save').disabled = true; renderProfessionalOptions([]); form.elements.nome.focus(); return;
    }
    form.elements.nome.value = serviceEdit.nome; form.elements.preco.value = Number(serviceEdit.preco).toFixed(2);
    form.elements.duracaoMinutos.value = serviceEdit.duracaoMinutos; form.elements.ativo.checked = serviceEdit.ativo;
    renderProfessionalOptions(serviceEdit.profissionalIds);
    $('service-form-title').textContent = 'Editar serviço'; $('service-message').textContent = '';
    $('service-save').disabled = false;
    form.elements.nome.focus();
  });
  $('service-reset').onclick = clearService;
  $('reload-services').onclick = async () => { clearService(); await loadServices(); };
  $('service-form').addEventListener('submit', async event => {
    event.preventDefault(); if (!owner() || $('service-fields').disabled) return;
    const form = event.currentTarget, key = context(), edit = serviceEdit;
    const name = form.elements.nome.value.trim();
    if (!name) { $('service-message').textContent = 'Informe o nome do serviço.'; return; }
    if (edit && (!Array.isArray(edit.profissionalIds) || !Number.isSafeInteger(edit.version))) {
      $('service-message').textContent = 'O catálogo não trouxe os vínculos e a versão do serviço. Recarregue antes de editar.'; return;
    }
    const body = { nome: name, preco: Number(form.elements.preco.value), duracaoMinutos: Number(form.elements.duracaoMinutos.value), ativo: form.elements.ativo.checked, profissionalIds: selectedProfessionalIds() };
    if (edit) body.version = edit.version;
    $('service-fields').disabled = true; $('service-message').textContent = '';
    try {
      await api('/servicos' + (edit ? '/' + edit.id : ''), { method: edit ? 'PUT' : 'POST', body });
      if (!current(key)) return;
      clearService(); $('service-message').textContent = 'Serviço salvo.'; catalogLoaded = false; await loadServices(); notify('Serviço salvo.');
    } catch (error) {
      if (current(key)) $('service-message').textContent = error.status === 409
        ? 'Este serviço foi alterado em outra sessão. Recarregue os serviços e reaplique sua alteração.'
        : error.message + ' Se o serviço mudou, use Recarregar serviços antes de editar novamente.';
    }
    finally { if (key === context()) $('service-fields').disabled = false; }
  });
  async function copyPublicLink(fromSetup = false) {
    const input = $('public-link');
    try {
      await navigator.clipboard.writeText(input.value);
      $('public-link-error').textContent = 'Link copiado. Compartilhe com seus clientes.';
      if ($('setup-next-message')) $('setup-next-message').textContent = 'Link copiado. Compartilhe com seus clientes.';
    } catch {
      $('public-link-error').textContent = 'Selecione e copie o link acima.';
      if (fromSetup) {
        $('setup-next-message').textContent = 'O navegador não permitiu copiar automaticamente. Abra Meu negócio para selecionar e copiar o link.';
        document.querySelector('[data-view="studio"]')?.click();
        setTimeout(() => { input.focus(); input.select(); }, 0);
      } else { input.focus(); input.select(); }
    }
  }
  $('copy-public-link').onclick = () => copyPublicLink();
  $('setup-next-action').addEventListener('click', event => { if (event.currentTarget.dataset.setupAction === 'copy-link') copyPublicLink(true); });
  function appointmentDetails(item) {
    return `<small class="appointment-service">${escapeHtml(item.duracaoMinutos)} min${item.precoPrevisto == null ? ' · preço não informado' : ' · ' + money(item.precoPrevisto)}</small>`;
  }
  function receiptAction(item) {
    if (item.recebimentoConfirmado || knownReceipts.has(item.id)) return '<small class="receipt-recorded">Recebimento confirmado</small>';
    const completed = item.status === 'CONCLUIDA';
    if (!owner() || !item.podeConfirmarRecebimento) return '';
    return `<button type="button" class="text-button" data-confirm-receipt="${item.id}">${completed ? 'Marcar como recebido' : 'Concluir atendimento e informar valor'}</button>`;
  }
  document.addEventListener('click', event => {
    const button = event.target.closest('[data-confirm-receipt]'); if (!button || !owner() || !state.loaded) return;
    receiptEdit = state.appointments.find(item => String(item.id) === button.dataset.confirmReceipt);
    const completed = receiptEdit?.status === 'CONCLUIDA';
    if (!receiptEdit || receiptEdit.recebimentoConfirmado || !receiptEdit.podeConfirmarRecebimento) return;
    $('receipt-title').textContent = completed ? 'Marcar como recebido' : 'Confirmar atendimento e recebimento';
    $('receipt-save').textContent = completed ? 'Marcar como recebido' : 'Confirmar atendimento e recebimento';
    $('receipt-completion-note').textContent = completed
      ? 'Ao confirmar, o valor será incluído no faturamento pela data desta confirmação. Depois, este registro não poderá ser alterado ou excluído.'
      : 'Ao confirmar, o atendimento será concluído e o valor entrará nos totais pela data desta confirmação. Depois, este registro não poderá ser alterado ou excluído.';
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
    activeContext = ''; catalogLoaded = false; catalogBusy = false; services = []; serviceEdit = null; receiptEdit = null; receiptBusy = false; publicLinkReady = false; serviceLoadError = ''; linkLoadError = ''; window.inkWaitlist?.close();
    receiptsKey = ''; ++receiptsRequest; knownReceipts.clear(); $('receipt-dialog').close();
    $('service-list').replaceChildren(); $('receipts-list').replaceChildren(); $('receipts-data').hidden = true;
    ['receipts-week','receipts-month','receipts-week-period','receipts-month-period','receipts-timezone','receipt-description'].forEach(id => { $(id).textContent = ''; });
    $('receipt-form').reset(); $('receipts-date').value = '';
    $('public-link').value = ''; $('open-public-link').removeAttribute('href'); $('open-public-link').hidden = true; $('copy-public-link').disabled = true;
    $('service-fields').disabled = false; $('receipt-fields').disabled = false; $('receipt-close').disabled = false;
    $('service-message').textContent = ''; $('public-link-error').textContent = ''; $('receipts-error').textContent = ''; $('receipt-error').textContent = ''; clearService(); renderSetupProgress();
  }
  function renderSimple() {
    if (!owner() || !state.loaded) return;
    const key = context();
    if (activeContext !== key) { close(); activeContext = key; }
    if ((state.view === 'overview' || state.view === 'studio') && !catalogLoaded && !catalogBusy) loadServices();
    renderSetupProgress();
    window.inkWaitlist?.render();
    if (state.view === 'receipts' && receiptsKey !== key + '|' + $('receipts-date').value) loadReceipts();
  }
  window.inkSimple = { render: renderSimple, close, receiptAction, appointmentDetails };
  if (state.loaded) { render(); }
})();
