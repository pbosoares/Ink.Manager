'use strict';
(() => {
  let professionalEdit = null;
  let editorRequest = 0;
  let calendarRequest = 0;
  let calendarKey = '';
  let selectedDate = '';
  let settingsUser = null;
  let polling = false, refreshTimer = null, focusTimer = null, queuedRefresh = false;
  const days = ['Segunda', 'Terca', 'Quarta', 'Quinta', 'Sexta', 'Sabado', 'Domingo'];
  const availabilityToolsOpen = () => state.view === 'overview' && Boolean($('agenda-secondary-tools')?.open);
  const owner = () => state.user?.perfil === 'PROPRIETARIO';
  document.body.insertAdjacentHTML('beforeend', `
    <dialog id="professional-dialog" aria-labelledby="professional-title">
      <form id="professional-form"><h2 id="professional-title">Profissional e expediente</h2>
        <label>Nome<input name="nome" required maxlength="100"></label>
        <label><input name="ativo" type="checkbox" checked> Disponivel para novas reservas</label>
        <p>Adicione dois turnos no mesmo dia para reservar um intervalo de almoco.</p>
        <div id="shift-list"></div><button type="button" id="add-shift" class="secondary">+ Turno</button>
        <p id="professional-error" class="form-error" role="alert"></p>
        <div class="dialog-actions"><button type="button" id="close-professional" class="secondary">Fechar</button><button id="save-professional" class="primary">Salvar horários</button></div>
      </form>
      <section id="block-section" hidden><h3>Folgas e ausencias</h3>
        <form id="block-form"><div class="form-row"><label>Inicio<input name="inicio" type="datetime-local" required></label><label>Fim<input name="fim" type="datetime-local" required></label></div><label>Motivo (opcional)<input name="motivo" maxlength="255"></label><button class="secondary">Bloquear periodo</button><p id="block-error" role="alert"></p></form><div id="block-list"></div>
      </section>
    </dialog>
    <dialog id="password-dialog" aria-labelledby="password-title"><form id="password-form">
      <h2 id="password-title">Recuperar acesso</h2><label id="recovery-email">E-mail da conta<input name="email" type="email" required maxlength="255"></label>
      <fieldset id="reset-fields" hidden disabled><label>Nova senha<input name="senha" type="password" minlength="8" maxlength="72" required autocomplete="new-password"></label><label>Repita a nova senha<input name="confirmacao" type="password" minlength="8" maxlength="72" required autocomplete="new-password"></label></fieldset>
      <p id="password-message" role="status"></p><div class="dialog-actions"><button type="button" id="close-password" class="secondary">Fechar</button><button id="save-password" class="primary">Enviar instrucoes</button></div>
    </form></dialog>`);

  function options(select, all = false) {
    const previous = select.value;
    select.innerHTML = `${all ? '<option value="">Todos</option>' : ''}${state.professionals.filter(p => all || p.ativo).map(p => `<option value="${p.id}">${escapeHtml(p.nome)}${p.ativo ? '' : ' (inativo)'}</option>`).join('')}`;
    if ([...select.options].some(option => option.value === previous)) select.value = previous;
  }
  function renderTeam() {
    options($('agenda-professional'), true);
    options($('availability-professional'));
    if (!$('availability-month').value) $('availability-month').value = localDate().slice(0, 7);
    $('professional-list').innerHTML = state.professionals.map(p => `<article class="team-card"><strong>${escapeHtml(p.nome)}</strong><p>${p.ativo ? 'Ativo' : 'Inativo'} / ${p.horarios.length} turno(s) por semana</p>${owner() ? `<button class="secondary" data-professional="${p.id}">Expediente e folgas</button>` : ''}</article>`).join('');
    if (owner()) renderSettings();
    if (availabilityToolsOpen()) loadCalendar(false);
  }
  function shiftRow(shift = { diaSemana: 1, inicio: '09:00', fim: '18:00' }) {
    const row = document.createElement('div'); row.className = 'shift-row';
    row.innerHTML = `<label>Dia<select name="diaSemana">${days.map((day, index) => `<option value="${index + 1}">${day}</option>`).join('')}</select></label><label>Inicio<input name="inicio" type="time" step="1" required></label><label>Fim<input name="fim" type="time" step="1" required></label><button type="button" class="text-button">Remover</button>`;
    row.querySelector('[name=diaSemana]').value = shift.diaSemana;
    row.querySelector('[name=inicio]').value = shift.inicio;
    row.querySelector('[name=fim]').value = shift.fim;
    row.querySelector('button').onclick = () => row.remove();
    $('shift-list').append(row);
  }
  function openProfessional(id = null) {
    professionalEdit = state.professionals.find(p => p.id === id) || null;
    const form = $('professional-form'); form.reset();
    form.elements.nome.value = professionalEdit?.nome || '';
    form.elements.ativo.checked = professionalEdit?.ativo ?? true;
    $('shift-list').replaceChildren();
    (professionalEdit?.horarios || [1,2,3,4,5,6].map(diaSemana => ({diaSemana, inicio:'09:00', fim:'18:00'}))).forEach(shiftRow);
    $('professional-error').textContent = '';
    $('block-error').textContent = '';
    $('block-list').replaceChildren();
    $('block-section').hidden = !id;
    $('professional-dialog').showModal();
    if (id) loadBlocks(id);
  }
  async function loadBlocks(id) {
    try {
      const blocks = await api(`/profissionais/${id}/bloqueios`);
      if (professionalEdit?.id !== id || !$('professional-dialog').open) return;
      $('block-list').innerHTML = blocks.map(b => `<p>${escapeHtml(b.inicio.replace('T', ' '))} ate ${escapeHtml(b.fim.replace('T', ' '))}<br>${escapeHtml(b.motivo || 'Indisponivel')} <button type="button" class="text-button" data-remove-block="${b.id}">Remover bloqueio</button></p>`).join('') || '<p>Nenhum bloqueio.</p>';
    } catch (error) { $('block-error').textContent = error.message; }
  }
  $('new-professional').onclick = () => openProfessional();
  $('add-shift').onclick = () => { if ($('shift-list').children.length < 28) shiftRow(); };
  $('close-professional').onclick = () => $('professional-dialog').close();
  $('professional-form').onsubmit = async event => {
    event.preventDefault();
    const form = event.currentTarget;
    const body = { nome: form.elements.nome.value.trim(), ativo: form.elements.ativo.checked,
      horarios: [...$('shift-list').children].map(row => ({ diaSemana: Number(row.querySelector('[name=diaSemana]').value), inicio: row.querySelector('[name=inicio]').value, fim: row.querySelector('[name=fim]').value })), version: professionalEdit?.version };
    $('save-professional').disabled = true;
    try { await api(`/profissionais${professionalEdit ? `/${professionalEdit.id}` : ''}`, {method: professionalEdit ? 'PUT' : 'POST', body}); $('professional-dialog').close(); calendarKey = ''; await loadData(); }
    catch (error) { $('professional-error').textContent = error.message; }
    finally { $('save-professional').disabled = false; }
  };
  $('block-form').onsubmit = async event => {
    event.preventDefault(); const id = professionalEdit?.id; if (!id) return;
    const button = event.currentTarget.querySelector('button'); button.disabled = true;
    const body = Object.fromEntries(new FormData(event.currentTarget));
    try { await api(`/profissionais/${id}/bloqueios`, {method:'POST', body}); $('block-form').reset(); $('block-error').textContent = ''; await loadBlocks(id); calendarKey = ''; }
    catch (error) { $('block-error').textContent = error.message; }
    finally { button.disabled = false; }
  };
  $('professional-list').onclick = event => { const button = event.target.closest('[data-professional]'); if (button) openProfessional(Number(button.dataset.professional)); };
  $('block-list').onclick = async event => {
    const button = event.target.closest('[data-remove-block]'); if (!button || !professionalEdit) return;
    button.disabled = true;
    try { await api(`/profissionais/${professionalEdit.id}/bloqueios/${button.dataset.removeBlock}`, {method:'DELETE'}); await loadBlocks(professionalEdit.id); calendarKey = ''; }
    catch (error) { $('block-error').textContent = error.message; button.disabled = false; }
  };

  async function editorAvailability(preferred) {
    const form = $('editor-form'); const edit = state.edit;
    if (edit?.type !== 'appointment') return;
    for (const name of ['profissionalId', 'data', 'duracaoMinutos']) form.elements[name].onchange = () => editorAvailability();
    const id = form.elements.profissionalId.value, date = form.elements.data.value, duration = Number(form.elements.duracaoMinutos.value);
    const select = form.elements.horario; const desired = preferred || select.value;
    const request = ++editorRequest;
    $('save-editor').disabled = true;
    select.innerHTML = '<option value="">Consultando horarios...</option>';
    if (!id || !date || !Number.isInteger(duration) || duration < 1 || duration > 720) { select.innerHTML = '<option value="">Preencha profissional, data e duracao</option>'; return; }
    const ignored = edit.id && String(edit.original.profissional.id) === id ? `&ignorarMarcacaoId=${edit.id}` : '';
    try {
      const result = await api(`/profissionais/${id}/disponibilidade?data=${date}&duracaoMinutos=${duration}${ignored}`);
      if (request !== editorRequest || edit !== state.edit || !$('editor').open) return;
      const times = result.horarios.map(time => time.slice(0, 5));
      const original = edit.original;
      const unchanged = edit.id && String(original.profissional.id) === id && original.data === date && original.duracaoMinutos === duration;
      if (unchanged && !times.includes(original.horario.slice(0, 5))) times.push(original.horario.slice(0, 5));
      times.sort();
      select.innerHTML = `<option value="">${times.length ? 'Selecione o horario' : 'Sem horarios livres neste dia'}</option>${times.map(time => `<option value="${time}">${time}</option>`).join('')}`;
      if (times.includes(desired)) select.value = desired;
      $('save-editor').disabled = !times.length;
      $('editor-error').textContent = times.length ? '' : 'Escolha outro dia ou profissional.';
    } catch (error) { if (request === editorRequest && $('editor').open) { $('editor-error').textContent = error.message; select.innerHTML = '<option value="">Consulta indisponivel</option>'; } }
  }
  async function loadCalendar(force) {
    if (!state.loaded || !availabilityToolsOpen()) return;
    const id = $('availability-professional').value, month = $('availability-month').value, duration = Number($('availability-duration').value);
    const key = `${id}|${month}|${duration}`;
    if (!force && calendarKey === key) return;
    calendarKey = key; const request = ++calendarRequest;
    $('availability-error').textContent = '';
    $('availability-days').replaceChildren(); $('availability-slots').replaceChildren();
    if (!id || !/^\d{4}-\d{2}$/.test(month) || duration < 1 || duration > 720 || !Number.isInteger(duration)) { $('availability-error').textContent = 'Selecione profissional, mes e duracao valida.'; return; }
    const [year, number] = month.split('-').map(Number); const count = new Date(year, number, 0).getDate();
    try {
      const result = await api(`/profissionais/${id}/dias-disponiveis?inicio=${month}-01&dias=${count}&duracaoMinutos=${duration}`);
      if (request !== calendarRequest || !state.token) return;
      const offset = (new Date(`${month}-01T12:00:00`).getDay() + 6) % 7;
      $('availability-days').innerHTML = days.map(day => `<small>${day.slice(0,3)}</small>`).join('') + '<span></span>'.repeat(offset) + result.map(day => `<button type="button" data-free-date="${day.data}" ${day.quantidadeHorarios ? '' : 'disabled'}><strong>${Number(day.data.slice(8))}</strong><small>${day.quantidadeHorarios ? `${day.quantidadeHorarios} horarios` : 'Sem vagas'}</small></button>`).join('');
      if (selectedDate.startsWith(month)) showSlots(selectedDate);
    } catch (error) { if (request === calendarRequest) { calendarKey = ''; $('availability-error').textContent = error.message; } }
  }
  async function showSlots(date) {
    selectedDate = date; const key = calendarKey;
    const id = $('availability-professional').value, duration = Number($('availability-duration').value);
    $('availability-slots').textContent = 'Consultando...';
    try {
      const result = await api(`/profissionais/${id}/disponibilidade?data=${date}&duracaoMinutos=${duration}`);
      if (selectedDate !== date || key !== calendarKey || !state.token) return;
      $('availability-slots').innerHTML = `<p>${escapeHtml(new Date(`${date}T12:00:00`).toLocaleDateString('pt-BR'))} / ${escapeHtml(result.fusoHorario)}</p>` + (result.horarios.length ? result.horarios.map(time => `<button type="button" class="secondary" data-free-time="${time.slice(0,5)}">${time.slice(0,5)}</button>`).join('') : '<p>Nenhum horario livre para esta duracao.</p>');
    } catch (error) { if (selectedDate === date) $('availability-slots').textContent = error.message; }
  }
  $('availability-load').onclick = () => loadCalendar(true);
  for (const id of ['availability-professional','availability-month','availability-duration']) $(id).onchange = () => { selectedDate = ''; loadCalendar(true); };
  $('availability-days').onclick = event => { const button = event.target.closest('[data-free-date]'); if (button) showSlots(button.dataset.freeDate); };
  $('availability-slots').onclick = event => {
    const button = event.target.closest('[data-free-time]'); if (!button) return;
    const id = $('availability-professional').value, duration = $('availability-duration').value;
    openEditor('appointment'); const form = $('editor-form');
    form.elements.profissionalId.value = id; form.elements.data.value = selectedDate; form.elements.duracaoMinutos.value = duration;
    editorAvailability(button.dataset.freeTime);
  };

  function renderSettings() {
    if (settingsUser === state.user.id) return;
    settingsUser = state.user.id;
    $('studio-settings').innerHTML = `<section class="settings-section"><h2>Nome do negócio</h2><form id="studio-name-form"><label>Como seu negócio aparece para a equipe<input name="nome" required maxlength="100"></label><button class="primary">Salvar nome</button><p id="studio-name-message" role="status"></p></form></section><section class="settings-section"><h2>Acessos da equipe</h2><p>A equipe consulta a agenda e marca atendimentos. O responsável configura o negócio, controla acessos e exclui registros.</p><div id="account-list"></div><h3>Novo funcionario</h3><form id="account-form"><label>Nome<input name="nome" required maxlength="100"></label><label>E-mail<input name="email" type="email" required maxlength="255"></label><label>Senha inicial<input name="senha" type="password" minlength="8" maxlength="72" required autocomplete="new-password"></label><button class="primary">Criar acesso</button><p id="account-message" role="status"></p></form></section><section class="settings-section" id="billing-settings"><h2>Assinatura</h2><div id="billing-content">Carregando opcoes...</div></section>`;
    $('studio-name-form').elements.nome.value = state.studio.nome;
    $('studio-name-form').onsubmit = async event => {
      event.preventDefault(); const button = event.currentTarget.querySelector('button'); button.disabled = true;
      try { state.studio = await api('/estudio',{method:'PUT',body:{nome:event.currentTarget.elements.nome.value.trim(),version:state.studio.version}}); $('account-name').textContent = state.studio.nome; $('studio-name-message').textContent = 'Nome atualizado para a equipe.'; }
      catch (error) { $('studio-name-message').textContent = error.message; }
      finally { button.disabled = false; }
    };
    $('account-form').onsubmit = async event => {
      event.preventDefault(); const form = event.currentTarget, button = form.querySelector('button'); button.disabled = true;
      try { await api('/equipe',{method:'POST',body:Object.fromEntries(new FormData(form))}); form.reset(); $('account-message').textContent = 'Acesso criado. Compartilhe a senha inicial de forma segura.'; await loadAccounts(); }
      catch (error) { $('account-message').textContent = error.message; }
      finally { button.disabled = false; }
    };
    $('account-list').onclick = async event => {
      const button = event.target.closest('[data-account]'); if (!button) return;
      button.disabled = true;
      try { await api(`/equipe/${button.dataset.account}`,{method:'PUT',body:{ativo:button.dataset.active==='true',version:Number(button.dataset.version)}}); await loadAccounts(); }
      catch (error) { $('account-message').textContent = error.message; button.disabled = false; }
    };
    loadAccounts(); window.inkBilling?.render();
  }
  async function loadAccounts() {
    const generation = state.generation, userId = state.user?.id;
    try {
      const accounts = await api('/equipe'); if (!owner() || generation !== state.generation || userId !== state.user?.id) return;
      $('account-list').innerHTML = accounts.map(account => `<article class="team-card"><strong>${escapeHtml(account.nome)}</strong><p>${escapeHtml(account.email)} / ${account.perfil === 'PROPRIETARIO' ? 'Responsavel' : 'Funcionario'} / ${account.ativo ? 'Ativo' : 'Desativado'}</p>${account.id === state.user.id ? '' : `<button type="button" class="secondary" data-account="${account.id}" data-version="${account.version}" data-active="${!account.ativo}">${account.ativo ? 'Desativar e encerrar sessoes' : 'Reativar acesso'}</button>`}</article>`).join('');
    } catch (error) { if ($('account-message')) $('account-message').textContent = error.message; }
  }

  let resetToken = new URLSearchParams(location.hash.replace(/^#/, '')).get('redefinir') || new URLSearchParams(location.search).get('redefinir');
  function passwordDialog(reset) {
    $('password-form').reset(); $('password-message').textContent = '';
    $('recovery-email').hidden = reset; $('recovery-email').querySelector('input').disabled = reset;
    $('reset-fields').hidden = !reset; $('reset-fields').disabled = !reset;
    $('password-title').textContent = reset ? 'Definir nova senha' : 'Recuperar acesso';
    $('save-password').textContent = reset ? 'Redefinir senha' : 'Enviar instrucoes';
    $('password-dialog').showModal();
  }
  $('forgot-password').onclick = () => { resetToken = null; passwordDialog(false); };
  $('close-password').onclick = () => $('password-dialog').close();
  $('password-form').onsubmit = async event => {
    event.preventDefault(); const values = Object.fromEntries(new FormData(event.currentTarget));
    if (resetToken && values.senha !== values.confirmacao) { $('password-message').textContent = 'As senhas devem ser iguais.'; return; }
    $('save-password').disabled = true;
    try {
      if (resetToken) { await api('/auth/redefinir-senha',{method:'POST',body:{token:resetToken,senha:values.senha},publicRequest:true}); resetToken=null; history.replaceState(null,'',location.pathname); logout(); $('password-message').textContent='Senha redefinida. Entre novamente com a nova senha.'; $('reset-fields').disabled=true; }
      else { const result=await api('/auth/recuperar-senha',{method:'POST',body:{email:values.email},publicRequest:true}); $('password-message').textContent=result.mensagem; }
    } catch (error) { $('password-message').textContent=error.message; }
    finally { $('save-password').disabled=false; }
  };
  if (resetToken) passwordDialog(true);
  window.inkTeam = { render: renderTeam, editorAvailability, close() { ++editorRequest; ++calendarRequest; settingsUser=null;calendarKey='';selectedDate='';$('professional-dialog').close(); professionalEdit=null; ['studio-settings','professional-list','availability-days','availability-slots','block-list','shift-list'].forEach(id => { $(id).replaceChildren(); }); $('professional-form').reset(); } };
  function canRefreshAgenda() {
    return !document.hidden && Boolean(state.token) && state.loaded && !$('editor').open && !$('professional-dialog').open && ['overview', 'agenda'].includes(state.view);
  }
  function scheduleAgendaRefresh(delay = 0, retryAfterFailure = false) {
    if (refreshTimer) clearTimeout(refreshTimer);
    refreshTimer = setTimeout(() => { refreshTimer = null; refreshAgenda(retryAfterFailure); }, delay);
  }
  async function refreshAgenda(retryAfterFailure = false) {
    if (!canRefreshAgenda()) return;
    if (polling) { queuedRefresh = true; return; }
    polling = true;
    const generation = state.generation, request = state.loadRequest;
    try {
      const appointments = await api('/marcacoes');
      if (generation !== state.generation || request !== state.loadRequest || !state.token) return;
      const professionals = await api('/profissionais');
      if (generation === state.generation && request === state.loadRequest && state.token) {
        state.appointments = appointments;
        state.professionals = professionals;
        render();
        if (availabilityToolsOpen()) loadCalendar(true);
      }
    } catch (error) {
      if (generation === state.generation) $('page-error').textContent = error.message;
      if (retryAfterFailure) scheduleAgendaRefresh(350, false);
    } finally {
      polling = false;
      if (queuedRefresh) {
        queuedRefresh = false;
        if (refreshTimer) { clearTimeout(refreshTimer); refreshTimer = null; }
        scheduleAgendaRefresh(0, false);
      }
    }
  }
  function refreshOnReturn() {
    if (document.hidden) return;
    if (focusTimer) clearTimeout(focusTimer);
    focusTimer = setTimeout(() => { focusTimer = null; scheduleAgendaRefresh(0, true); }, 150);
  }
  window.addEventListener('focus', refreshOnReturn);
  document.addEventListener('visibilitychange', refreshOnReturn);
  setInterval(() => { if (!polling) scheduleAgendaRefresh(0, true); }, 20000);})();
