'use strict';

const $ = (id) => document.getElementById(id);
const statuses = { AGENDADA: 'Agendada', CONFIRMADA: 'Confirmada', CONCLUIDA: 'Concluída', CANCELADA: 'Cancelada' };
const state = { token: sessionStorage.getItem('ink.token'), clients: [], appointments: [], view: 'overview', register: false, edit: null, deletion: null, generation: 0 };
const escapeHtml = (value) => String(value ?? '').replace(/[&<>"']/g, (char) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[char]));
const localDate = (date = new Date()) => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
const formatDate = (value) => value ? new Date(`${value}T12:00:00`).toLocaleDateString('pt-BR', { day: '2-digit', month: 'short' }) : 'Sem data';
const badge = (value) => `<span class="badge ${Object.hasOwn(statuses, value) ? value : ''}">${escapeHtml(statuses[value] || 'Sem status')}</span>`;
const sortedAppointments = () => [...state.appointments].sort((a, b) => `${a.data}T${a.horario}`.localeCompare(`${b.data}T${b.horario}`));
let toastTimer;

function notify(message) {
  $('toast').textContent = message;
  $('toast').hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { $('toast').hidden = true; }, 4500);
}

function logout(message = '') {
  state.generation++;
  state.token = null;
  sessionStorage.removeItem('ink.token');
  state.clients = [];
  state.appointments = [];
  $('workspace').hidden = true;
  $('auth').hidden = false;
  $('editor').close();
  $('confirmation').close();
  $('auth-form').reset();
  $('auth-error').textContent = message;
}

async function api(path, { method = 'GET', body, publicRequest = false } = {}) {
  const headers = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (!publicRequest && state.token) headers.Authorization = `Bearer ${state.token}`;
  let response;
  try {
    response = await fetch(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  } catch {
    throw new Error('Não foi possível conectar ao estúdio. Confira a conexão e tente novamente.');
  }
  const data = response.status === 204 ? null : await response.json().catch(() => null);
  if (!response.ok) {
    if (response.status === 401 && !publicRequest) logout('Sua sessão expirou. Entre novamente.');
    const message = data?.erro || (data && !data.timestamp && typeof data === 'object' ? Object.values(data).filter((v) => typeof v === 'string').join(' ') : '');
    throw new Error(message || (response.status === 401 ? 'E-mail ou senha inválidos.' : 'Não foi possível concluir. Tente novamente.'));
  }
  return data;
}

async function loadData() {
  const generation = state.generation;
  $('loading').hidden = false;
  $('page-error').textContent = '';
  try {
    const [clients, appointments] = await Promise.all([api('/clientes'), api('/marcacoes')]);
    if (generation !== state.generation) return;
    state.clients = clients;
    state.appointments = appointments;
    render();
  } catch (error) {
    if (generation === state.generation) {
      $('page-error').textContent = `${error.message} `;
      const retry = document.createElement('button');
      retry.className = 'text-button';
      retry.textContent = 'Tentar novamente';
      retry.onclick = loadData;
      $('page-error').append(retry);
    }
  } finally {
    if (generation === state.generation) $('loading').hidden = true;
  }
}

function openWorkspace() {
  $('auth').hidden = true;
  $('workspace').hidden = false;
  $('account-name').textContent = 'Seu estúdio';
  $('today-label').textContent = new Date().toLocaleDateString('pt-BR', { weekday: 'long', day: 'numeric', month: 'long' });
  setView('overview');
  loadData();
}

$('toggle-auth').addEventListener('click', () => {
  state.register = !state.register;
  $('name-field').hidden = !state.register;
  $('auth-form').elements.nome.required = state.register;
  $('auth-form').elements.senha.minLength = state.register ? 8 : 1;
  $('auth-form').elements.senha.autocomplete = state.register ? 'new-password' : 'current-password';
  $('auth-title').textContent = state.register ? 'Abra as portas.' : 'Bom te ver por aqui.';
  $('auth-description').textContent = state.register ? 'Crie seu acesso para começar a organizar o estúdio.' : 'Entre para cuidar da sua agenda e dos seus clientes.';
  $('auth-submit').textContent = state.register ? 'Criar minha conta ↗' : 'Entrar no estúdio ↗';
  $('auth-question').textContent = state.register ? 'Já tem uma conta?' : 'Primeira vez aqui?';
  $('toggle-auth').textContent = state.register ? 'Entrar' : 'Criar uma conta';
  $('auth-error').textContent = '';
});

$('auth-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const values = Object.fromEntries(new FormData(event.currentTarget));
  values.email = values.email.trim();
  values.nome = values.nome.trim();
  $('auth-submit').disabled = true;
  $('toggle-auth').disabled = true;
  $('auth-error').textContent = '';
  try {
    if (state.register) {
      await api('/auth/cadastro', { method: 'POST', body: values, publicRequest: true });
      // Account creation is complete even if a subsequent login fails.
      $('toggle-auth').disabled = false;
      $('toggle-auth').click();
      $('toggle-auth').disabled = true;
      notify('Conta criada. Bem-vindo ao estúdio!');
    }
    const result = await api('/auth/login', { method: 'POST', body: { email: values.email, senha: values.senha }, publicRequest: true });
    if (!result?.token) throw new Error('O servidor não retornou uma sessão válida.');
    state.token = result.token;
    sessionStorage.setItem('ink.token', state.token);
    $('auth-form').reset();
    openWorkspace();
  } catch (error) {
    $('auth-error').textContent = error.message;
  } finally {
    $('auth-submit').disabled = false;
    $('toggle-auth').disabled = false;
  }
});

function setView(view) {
  state.view = view;
  const copy = {
    overview: ['A CASA ESTÁ ABERTA', 'Cada dia, uma nova história.', 'Sua rotina organizada. Sua arte em primeiro lugar.'],
    agenda: ['TEMPO PARA FAZER ARTE', 'Sua agenda, no traço.', 'Cada sessão tem seu espaço. Cuide dos próximos encontros.'],
    clients: ['QUEM CONFIA NO SEU TRAÇO', 'As pessoas por trás da arte.', 'Contatos e histórias para manter sempre por perto.']
  }[view];
  ['overview', 'agenda', 'clients'].forEach((key) => { $(`${key}-view`).hidden = key !== view; });
  document.querySelectorAll('[data-view]').forEach((button) => {
    button.classList.toggle('active', button.dataset.view === view);
    if (button.dataset.view === view) button.setAttribute('aria-current', 'page');
    else button.removeAttribute('aria-current');
  });
  $('page-kicker').textContent = copy[0];
  $('page-title').textContent = copy[1];
  $('page-description').textContent = copy[2];
  $('main-action').textContent = view === 'clients' ? '+ Novo cliente' : '+ Nova marcação';
  render();
}

function empty(title, description, action) {
  return `<div class="empty"><strong>${title}</strong>${description}${action ? `<button class="text-button" data-create="${action}">${action === 'client' ? 'Cadastrar primeiro cliente' : 'Criar uma marcação'} ↗</button>` : ''}</div>`;
}

function render() {
  const today = localDate();
  const appointments = sortedAppointments();
  const todays = appointments.filter((item) => item.data === today && item.status !== 'CANCELADA');
  $('stat-today').textContent = String(todays.length).padStart(2, '0');
  const now = new Date();
  const currentTime = `${today}T${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}:00`;
  $('stat-upcoming').textContent = String(appointments.filter((item) => `${item.data}T${item.horario}` >= currentTime && ['AGENDADA', 'CONFIRMADA'].includes(item.status)).length).padStart(2, '0');
  $('stat-clients').textContent = String(state.clients.length).padStart(2, '0');
  $('today-list').innerHTML = todays.length ? todays.map((item) => `<div class="session"><span class="session-time">${escapeHtml(item.horario?.slice(0, 5))}</span><div class="session-info"><div class="session-name">${escapeHtml(item.cliente?.nome)}</div><div class="session-description">${escapeHtml(item.descricao || 'Sessão de tatuagem')}</div></div>${badge(item.status)}<button class="text-button" data-edit-appointment="${item.id}" aria-label="Editar marcação de ${escapeHtml(item.cliente?.nome)}">Editar</button></div>`).join('') : empty('Um respiro na agenda.', 'Nenhuma sessão marcada para hoje.', 'appointment');
  renderAgenda();
  renderClients();
}

function renderAgenda() {
  const query = $('agenda-search').value.toLocaleLowerCase('pt-BR');
  const date = $('agenda-date').value;
  const status = $('agenda-status').value;
  const items = sortedAppointments().filter((item) => (!date || item.data === date) && (!status || item.status === status) && `${item.cliente?.nome || ''} ${item.descricao || ''}`.toLocaleLowerCase('pt-BR').includes(query));
  $('agenda-count').textContent = `${items.length} registro(s)`;
  $('agenda-list').innerHTML = items.length ? `<div class="table-scroll"><table><thead><tr><th>DATA / HORA</th><th>CLIENTE / SESSÃO</th><th>STATUS</th><th>AÇÕES</th></tr></thead><tbody>${items.map((item) => `<tr><td>${escapeHtml(formatDate(item.data))}<small>${escapeHtml(item.data?.slice(0, 4))} · ${escapeHtml(item.horario?.slice(0, 5))}</small></td><td>${escapeHtml(item.cliente?.nome)}<small>${escapeHtml(item.descricao || 'Sem descrição')}</small></td><td>${badge(item.status)}</td><td><div class="row-actions"><button class="text-button" data-edit-appointment="${item.id}">Editar</button><button class="text-button delete" data-delete-appointment="${item.id}">Excluir</button></div></td></tr>`).join('')}</tbody></table></div>` : empty('Nenhuma marcação por aqui.', query || date || status ? 'Experimente mudar os filtros.' : 'Comece reservando um horário para a próxima arte.', query || date || status ? '' : 'appointment');
}

function renderClients() {
  const query = $('client-search').value.toLocaleLowerCase('pt-BR');
  const items = [...state.clients].filter((item) => `${item.nome} ${item.telefone} ${item.instagram || ''}`.toLocaleLowerCase('pt-BR').includes(query)).sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR'));
  $('client-count').textContent = `${items.length} cliente(s)`;
  $('client-list').innerHTML = items.length ? `<div class="table-scroll"><table><thead><tr><th>NOME</th><th>CONTATO</th><th>IDADE</th><th>AÇÕES</th></tr></thead><tbody>${items.map((item) => `<tr><td>${escapeHtml(item.nome)}<small>CLIENTE #${item.id}</small></td><td>${escapeHtml(item.telefone)}<small>${escapeHtml(item.instagram || 'Sem Instagram')}</small></td><td>${item.idade == null ? '—' : escapeHtml(item.idade)}</td><td><div class="row-actions"><button class="text-button" data-edit-client="${item.id}">Editar</button><button class="text-button delete" data-delete-client="${item.id}">Excluir</button></div></td></tr>`).join('')}</tbody></table></div>` : empty('Toda história começa com alguém.', query ? 'Nenhum cliente encontrado nessa busca.' : 'Cadastre seu primeiro cliente para começar.', query ? '' : 'client');
}

function openEditor(type, id = null) {
  const item = (type === 'client' ? state.clients : state.appointments).find((entry) => entry.id === id) || {};
  state.edit = { type, id, clientId: item.cliente?.id };
  $('editor-title').textContent = `${id ? 'Editar' : type === 'client' ? 'Novo' : 'Nova'} ${type === 'client' ? 'cliente' : 'marcação'}`;
  $('editor-error').textContent = '';
  if (type === 'client') {
    $('editor-fields').innerHTML = `<label>Nome completo<input name="nome" required maxlength="255" autocomplete="name"></label><div class="form-row"><label>Telefone<input name="telefone" type="tel" required maxlength="30" autocomplete="tel"></label><label>Idade<input name="idade" type="number" min="0" max="150" step="1"></label></div><label>Instagram <span class="muted">Opcional</span><input name="instagram" maxlength="100" placeholder="@cliente"></label>`;
  } else {
    const clientFields = id
      ? `<p class="muted">Marcação de <strong>${escapeHtml(item.cliente?.nome)}</strong><br>${escapeHtml(item.cliente?.telefone)}</p>`
      : `<div class="form-row"><label>Nome completo<input name="nome" required maxlength="255" autocomplete="name"></label><label>Telefone<input name="telefone" type="tel" required maxlength="30" autocomplete="tel"></label></div><div class="form-row"><label>Idade (opcional)<input name="idade" type="number" min="0" max="150" step="1"></label><label>Instagram (opcional)<input name="instagram" maxlength="100" placeholder="@cliente"></label></div>`;
    $('editor-fields').innerHTML = `${clientFields}
      <div class="form-row"><label>Data<input name="data" type="date" required></label><label>Horário<input name="horario" type="time" required></label></div><label>Descrição<textarea name="descricao" maxlength="255" placeholder="Ideia, local do corpo ou detalhes da sessão"></textarea></label><label>Status<select name="status">${Object.entries(statuses).map(([value, label]) => `<option value="${value}">${label}</option>`).join('')}</select></label>`;
  }
  const values = type === 'client' ? item : { ...item, data: item.data || localDate(), horario: item.horario?.slice(0, 5), status: item.status || 'AGENDADA' };
  for (const field of $('editor-fields').querySelectorAll('[name]')) field.value = values[field.name] ?? '';
  $('save-editor').textContent = type === 'client' ? 'Salvar cliente' : 'Salvar marcação';
  $('editor').showModal();
}

$('editor-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  if ($('save-editor').disabled) return;
  const { type, id, clientId } = state.edit;
  const values = Object.fromEntries(new FormData(event.currentTarget));
  const newClient = type === 'appointment' && !id;
  const client = type === 'client' || newClient
    ? { nome: values.nome.trim(), telefone: values.telefone.trim(), idade: values.idade === '' ? null : Number(values.idade), instagram: values.instagram.trim() }
    : { id: clientId };
  const body = type === 'client' ? client : { cliente: client, data: values.data, horario: values.horario, descricao: values.descricao.trim(), status: values.status };
  const buttons = ['save-editor', 'cancel-editor', 'close-editor'];
  buttons.forEach((key) => { $(key).disabled = true; });
  $('editor-error').textContent = '';
  try {
    await api(`/${type === 'client' ? 'clientes' : 'marcacoes'}${id ? `/${id}` : ''}${newClient ? '/com-cliente' : ''}`, { method: id ? 'PUT' : 'POST', body });
    $('editor').close();
    notify('Registro salvo. Tudo em ordem!');
    await loadData();
    if (type === 'appointment' && state.token) {
      ['agenda-search', 'agenda-date', 'agenda-status'].forEach((key) => { $(key).value = ''; });
      setView('agenda');
    }
  } catch (error) {
    $('editor-error').textContent = error.message;
  } finally {
    buttons.forEach((key) => { $(key).disabled = false; });
  }
});

function askDelete(type, id) {
  state.deletion = { type, id };
  const item = (type === 'client' ? state.clients : state.appointments).find((entry) => entry.id === id);
  $('confirmation-description').textContent = type === 'client' ? `Excluir ${item.nome}? Clientes com marcações vinculadas não podem ser excluídos. Esta ação não pode ser desfeita.` : `Excluir a marcação de ${item.cliente?.nome} em ${formatDate(item.data)}? Você também pode editar o status para Cancelada e manter o histórico. Esta exclusão não pode ser desfeita.`;
  $('delete-error').textContent = '';
  $('confirmation').showModal();
}

$('confirm-delete').addEventListener('click', async () => {
  const { type, id } = state.deletion;
  $('confirm-delete').disabled = $('cancel-delete').disabled = true;
  try {
    await api(`/${type === 'client' ? 'clientes' : 'marcacoes'}/${id}`, { method: 'DELETE' });
    $('confirmation').close();
    notify('Registro excluído.');
    await loadData();
  } catch (error) {
    $('delete-error').textContent = error.message;
  } finally {
    $('confirm-delete').disabled = $('cancel-delete').disabled = false;
  }
});

document.addEventListener('click', (event) => {
  const button = event.target.closest('button');
  if (!button) return;
  if (button.dataset.view) setView(button.dataset.view);
  if (button.dataset.create) openEditor(button.dataset.create);
  for (const type of ['Client', 'Appointment']) {
    if (button.dataset[`edit${type}`]) openEditor(type.toLowerCase(), Number(button.dataset[`edit${type}`]));
    if (button.dataset[`delete${type}`]) askDelete(type.toLowerCase(), Number(button.dataset[`delete${type}`]));
  }
});
$('logout').onclick = () => logout();
$('main-action').onclick = () => openEditor(state.view === 'clients' ? 'client' : 'appointment');
$('see-agenda').onclick = () => setView('agenda');
$('close-editor').onclick = $('cancel-editor').onclick = () => $('editor').close();
$('cancel-delete').onclick = () => $('confirmation').close();
$('editor').addEventListener('cancel', (event) => { if ($('save-editor').disabled) event.preventDefault(); });
$('confirmation').addEventListener('cancel', (event) => { if ($('confirm-delete').disabled) event.preventDefault(); });
['agenda-search', 'agenda-date', 'agenda-status'].forEach((id) => $(id).addEventListener('input', renderAgenda));
$('client-search').addEventListener('input', renderClients);
$('clear-filters').onclick = () => {
  ['agenda-search', 'agenda-date', 'agenda-status'].forEach((id) => { $(id).value = ''; });
  renderAgenda();
};
if (state.token) openWorkspace();
