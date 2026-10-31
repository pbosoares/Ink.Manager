'use strict';

// Load brand fonts without holding up the authenticated app bootstrap.
const brandFonts = document.createElement('link');
brandFonts.rel = 'preload';
brandFonts.as = 'style';
brandFonts.href = 'https://fonts.googleapis.com/css2?family=DM+Sans:wght@400;500;600;700&family=Inter:wght@400;500;600;700&family=Inter+Tight:wght@600;700&family=Libre+Caslon+Display&family=Rye&display=swap';
brandFonts.onload = () => { brandFonts.rel = 'stylesheet'; };
document.head.append(brandFonts);

const $ = (id) => document.getElementById(id);
const statuses = { AGENDADA: 'Agendada', CONFIRMADA: 'Confirmada', CONCLUIDA: 'Concluída', CANCELADA: 'Cancelada' };
const state = { token: sessionStorage.getItem('ink.token'), clients: [], appointments: [], professionals: [], user: null, studio: null, view: 'overview', register: false, registrationEnabled: false, edit: null, deletion: null, generation: 0, loaded: false, loading: false, loadRequest: 0 };
const escapeHtml = (value) => String(value ?? '').replace(/[&<>"']/g, (char) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[char]));
const localDate = (date = new Date()) => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
const formatDate = (value) => value ? new Date(`${value}T12:00:00`).toLocaleDateString('pt-BR', { day: '2-digit', month: 'short' }) : 'Sem data';
const badge = (value) => `<span class="badge ${Object.hasOwn(statuses, value) ? value : ''}">${escapeHtml(statuses[value] || 'Sem status')}</span>`;
function whatsappAction(item) {
  let digits = String(item.cliente?.telefone || '').replace(/\D/g, '');
  if (digits.length === 10 || digits.length === 11) digits = `55${digits}`;
  if (!(digits.startsWith('55') && (digits.length === 12 || digits.length === 13))) return '';
  const name = String(item.cliente?.nome || '').trim();
  if (!name) return '';
  const service = String(item.servicoNome || item.descricao || 'atendimento').trim();
  const time = String(item.horario || '').slice(0, 5);
  const message = `Oi, ${name}! Estou entrando em contato sobre seu atendimento de ${service} marcado para ${formatDate(item.data)} às ${time}. Se quiser confirmar ou precisar reagendar, me avise por aqui.`;
  const href = `https://wa.me/${digits}?text=${encodeURIComponent(message)}`;
  return `<a class="whatsapp-icon-button" href="${href}" target="_blank" rel="noopener noreferrer" aria-label="Abrir WhatsApp para ${escapeHtml(name)}; mensagem de confirmação" title="WhatsApp para ${escapeHtml(name)}"><svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M12 2a9.8 9.8 0 0 0-8.4 14.8L2.3 22l5.4-1.4A10 10 0 1 0 12 2Zm0 18.2a8.2 8.2 0 0 1-4.2-1.2l-.3-.2-3.2.8.9-3.1-.2-.3A8.2 8.2 0 1 1 12 20.2Z" fill="currentColor"/><path d="M16.7 14.1c-.3-.1-1.5-.7-1.7-.8-.2-.1-.4-.1-.5.1-.2.3-.6.8-.8 1-.1.2-.3.2-.5.1-.3-.1-1.1-.4-2-1.2-.8-.7-1.3-1.5-1.4-1.7-.2-.3 0-.4.1-.6l.4-.4c.1-.2.2-.3.2-.4.1-.2 0-.3 0-.5l-.8-1.8c-.2-.5-.4-.4-.6-.4H8.6c-.2 0-.4.1-.7.3-.2.3-.8.9-.8 2.1s.9 2.4 1 2.6c.1.2 1.8 2.7 4.2 3.7.6.3 1.1.4 1.4.5.6.2 1.1.2 1.6.1.5-.1 1.5-.6 1.7-1.2.2-.6.2-1.1.1-1.2 0-.1-.2-.2-.5-.3Z" fill="#fff"/></svg></a>`;
}
const sortedAppointments = () => [...state.appointments].sort((a, b) => `${a.data}T${a.horario}`.localeCompare(`${b.data}T${b.horario}`) || a.id - b.id);
let toastTimer;

function notify(message) {
  $('toast').textContent = message;
  $('toast').hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { $('toast').hidden = true; }, 4500);
}

function logout(message = '') {
  state.generation++;
  state.loaded = false;
  state.loading = false;
  state.token = null;
  sessionStorage.removeItem('ink.token');
  state.clients = [];
  state.appointments = [];
  state.professionals = []; state.user = null; state.studio = null; state.edit = null; state.deletion = null;
  ['today-list','agenda-list','client-list','account-name'].forEach(id => { $(id).textContent = ''; });
  $('workspace').hidden = true;
  $('auth').hidden = false;
  $('editor').close();
  $('confirmation').close();
  window.inkTeam?.close();
  window.inkSimple?.close();
  window.inkAnalytics?.close();
  $('auth-form').reset();
  $('auth-error').textContent = message;
}

async function api(path, { method = 'GET', body, publicRequest = false } = {}) {
  const headers = {};
  const requestToken = state.token;
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (!publicRequest && state.token) headers.Authorization = `Bearer ${state.token}`;
  let response;
  try {
    response = await fetch(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(20000) });
  } catch {
    throw new Error('Não foi possível conectar ao negócio. Confira a conexão e tente novamente.');
  }
  const data = response.status === 204 ? null : await response.json().catch(() => null);
  if (!response.ok) {
    if (response.status === 401 && !publicRequest && state.token === requestToken) logout('Sua sessão expirou. Entre novamente.');
    const message = data?.erro || (data && !data.timestamp && typeof data === 'object' ? Object.values(data).filter((v) => typeof v === 'string').join(' ') : '');
    throw new Error(message || (response.status === 401 ? 'E-mail ou senha inválidos.' : 'Não foi possível concluir. Tente novamente.'));
  }
  return data;
}

async function loadData() {
  const generation = state.generation;
  const request = ++state.loadRequest;
  state.loaded = false;
  state.loading = true;
  render();
  $('loading').hidden = false;
  $('page-error').textContent = '';
  try {
    const [clients, appointments, professionals, session] = await Promise.all([api('/clientes'), api('/marcacoes'), api('/profissionais'), api('/auth/me')]);
    if (generation !== state.generation || request !== state.loadRequest) return;
    if (!Array.isArray(clients) || !Array.isArray(appointments)) throw new Error('Resposta inválida do servidor.');
    state.loaded = true;
    state.clients = clients;
    state.appointments = appointments;
    state.professionals = professionals;
    state.user = session.usuario;
    state.studio = session.estudio;
    $('account-name').textContent = state.studio.nome;
    render();
  } catch (error) {
    if (generation === state.generation && request === state.loadRequest) {
      state.loading = false;
      render();
      $('page-error').textContent = `${error.message} `;
      const retry = document.createElement('button');
      retry.className = 'text-button';
      retry.textContent = 'Tentar novamente';
      retry.onclick = loadData;
      $('page-error').append(retry);
    }
  } finally {
    if (generation === state.generation && request === state.loadRequest) {
      state.loading = false;
      $('loading').hidden = true;
      render();
    }
  }
}

function openWorkspace() {
  $('auth').hidden = true;
  $('workspace').hidden = false;
  $('account-name').textContent = 'Seu negócio';
  $('today-label').textContent = new Date().toLocaleDateString('pt-BR', { weekday: 'long', day: 'numeric', month: 'long' });
  setView('overview');
  loadData();
}

$('toggle-auth').addEventListener('click', () => {
  state.register = !state.register;
  $('name-field').hidden = !state.register;
  $('studio-name-field').hidden = !state.register;
  $('auth-form').elements.nomeEstudio.required = state.register;
  $('auth-form').elements.nome.required = state.register;
  $('auth-form').elements.senha.minLength = state.register ? 8 : 1;
  $('auth-form').elements.senha.autocomplete = state.register ? 'new-password' : 'current-password';
  $('auth-title').textContent = state.register ? 'Abra as portas.' : 'Bom te ver por aqui.';
  $('auth-description').textContent = state.register ? 'Crie seu neg\u00f3cio e comece a organizar a agenda.' : 'Entre para cuidar da agenda e dos clientes do seu neg\u00f3cio.';
  $('auth-submit').textContent = state.register ? 'Testar gr\u00e1tis por 14 dias \u2192' : 'Entrar no neg\u00f3cio \u2192';
  $('auth-submit').classList.toggle('primary', state.register);
  $('auth-submit').classList.toggle('secondary', !state.register);
  $('auth-question').textContent = state.register ? 'J\u00e1 tem uma conta?' : 'Primeira vez aqui?';
  $('toggle-auth').textContent = state.register ? 'Entrar' : 'Criar uma conta';
  $('auth-registration').hidden = !state.registrationEnabled;
  $('landing-offer').hidden = !state.registrationEnabled || state.register;
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
      await api('/auth/estudios', { method: 'POST', body: values, publicRequest: true });
      // Account creation is complete even if a subsequent login fails.
      $('toggle-auth').disabled = false;
      $('toggle-auth').click();
      $('toggle-auth').disabled = true;
      notify('Conta criada. Bem-vindo ao negócio!');
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
  if (view === 'receipts' && state.user?.perfil !== 'PROPRIETARIO') view = 'overview';
  state.view = view;
  const copy = {
    overview: ['A CASA ESTÁ ABERTA', 'Cada dia, uma nova história.', 'Sua rotina organizada. Seus atendimentos em primeiro lugar.'],
    agenda: ['TEMPO PARA ATENDER BEM', 'Sua agenda, no lugar.', 'Cada atendimento tem seu espaço. Cuide dos próximos encontros.'],
    team: ['A EQUIPE', 'Cada profissional, sua agenda.', 'Expedientes e folgas compartilhados com a equipe.'],
    studio: ['SEU ESPACO', 'Seu negócio tem a sua cara.', 'Serviços, link de reservas, acessos e assinatura no mesmo lugar.'],
    receipts: ['VALORES CONFIRMADOS', 'Recebimentos, sem complicação.', 'Confirme o atendimento e o valor recebido para acompanhar a semana e o mês.'],
    clients: ['QUEM CONFIA NO SEU TRABALHO', 'Seus clientes, sempre por perto.', 'Contatos e histórias para manter sempre por perto.']
  }[view];
  ['overview', 'agenda', 'clients', 'team', 'studio', 'receipts'].forEach((key) => { $(`${key}-view`).hidden = key !== view; });
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
  $('main-action').disabled = !state.loaded;
  $('main-action').hidden = ['team', 'studio', 'receipts'].includes(state.view);
  document.querySelectorAll('.owner-only').forEach((element) => { element.hidden = state.user?.perfil !== 'PROPRIETARIO'; });
  ['overview', 'agenda', 'clients', 'team', 'studio', 'receipts'].forEach((view) => { $(`${view}-view`).hidden = !state.loaded || state.view !== view; });
  $('workspace').setAttribute('aria-busy', String(state.loading));
  if (!state.loaded) {
    ['stat-today', 'stat-upcoming', 'stat-clients'].forEach((id) => { $(id).textContent = '—'; });
    ['today-list', 'agenda-list', 'client-list', 'agenda-count', 'client-count'].forEach((id) => { $(id).textContent = ''; });
    return;
  }
  const today = localDate();
  const appointments = sortedAppointments();
  const todays = appointments.filter((item) => item.data === today && item.status !== 'CANCELADA');
  $('stat-today').textContent = String(todays.length).padStart(2, '0');
  const now = new Date();
  const currentTime = `${today}T${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}:00`;
  $('stat-upcoming').textContent = String(appointments.filter((item) => `${item.data}T${item.horario}` >= currentTime && ['AGENDADA', 'CONFIRMADA'].includes(item.status)).length).padStart(2, '0');
  $('stat-clients').textContent = String(state.clients.length).padStart(2, '0');
  $('today-list').innerHTML = todays.length ? todays.map((item) => `<div class="session"><span class="session-time">${escapeHtml(item.horario?.slice(0, 5))}</span><div class="session-info"><div class="session-name">${escapeHtml(item.cliente?.nome)}</div><div class="session-description">${escapeHtml(item.servicoNome || item.descricao || 'Atendimento')}${window.inkSimple?.appointmentDetails(item) || ''}</div>${window.inkAnalytics?.attendanceAction(item) || ''}</div>${badge(item.status)}${window.inkSimple?.receiptAction(item) || ''}${whatsappAction(item)}<button class="text-button" ${item.recebimentoConfirmado ? 'disabled title="Recebimento confirmado"' : ''} data-edit-appointment="${item.id}" aria-label="Editar marcação de ${escapeHtml(item.cliente?.nome)}">Editar</button></div>`).join('') : empty('Um respiro na agenda.', 'Nenhum atendimento marcado para hoje.', 'appointment');
  renderAgenda();
  renderClients();
  window.inkTeam?.render();
  window.inkSimple?.render();
  window.inkAnalytics?.render();
}

function renderAgenda() {
  if (!state.loaded) return;
  const query = $('agenda-search').value.toLocaleLowerCase('pt-BR');
  const date = $('agenda-date').value;
  const status = $('agenda-status').value;
  const professional = $('agenda-professional').value;
  const pendingReceipt = status === 'PENDENTE_RECEBIMENTO';
  const items = sortedAppointments().filter((item) => (!professional || String(item.profissional?.id) === professional) && (!date || item.data === date) && (pendingReceipt ? item.status === 'CONCLUIDA' && !item.recebimentoConfirmado && item.podeConfirmarRecebimento : !status || item.status === status) && `${item.cliente?.nome || ''} ${item.servicoNome || ''} ${item.descricao || ''}`.toLocaleLowerCase('pt-BR').includes(query));
  $('agenda-count').textContent = `${items.length} registro(s)`;
  $('agenda-list').innerHTML = items.length ? `<div class="table-scroll"><table><thead><tr><th>DATA / HORA</th><th>CLIENTE / ATENDIMENTO</th><th>STATUS</th><th>AÇÕES</th></tr></thead><tbody>${items.map((item) => `<tr><td>${escapeHtml(formatDate(item.data))}<small>${escapeHtml(item.data?.slice(0, 4))} · ${escapeHtml(item.horario?.slice(0, 5))}</small></td><td>${escapeHtml(item.cliente?.nome)}<small>${escapeHtml(item.profissional?.nome)} / ${escapeHtml(item.servicoNome || item.descricao || 'Sem descrição')}</small>${window.inkSimple?.appointmentDetails(item) || ''}${window.inkAnalytics?.attendanceAction(item) || ''}</td><td>${badge(item.status)}</td><td><div class="row-actions">${window.inkSimple?.receiptAction(item) || ''}${whatsappAction(item)}<button class="text-button" ${item.recebimentoConfirmado ? 'disabled title="Recebimento confirmado"' : ''} data-edit-appointment="${item.id}">Editar</button><button class="text-button delete" ${state.user?.perfil === 'PROPRIETARIO' ? '' : 'hidden'} ${item.recebimentoConfirmado ? 'disabled title="Recebimento confirmado"' : ''} data-delete-appointment="${item.id}">Excluir</button></div></td></tr>`).join('')}</tbody></table></div>` : empty('Nenhuma marcação por aqui.', query || date || status ? 'Experimente mudar os filtros.' : 'Reserve um horário para o próximo atendimento.', query || date || status ? '' : 'appointment');
}

function renderClients() {
  if (!state.loaded) return;
  const query = $('client-search').value.toLocaleLowerCase('pt-BR');
  const nextAppointmentByClient = new Map();
  const today = localDate();
  for (const appointment of sortedAppointments()) {
    if (!['AGENDADA', 'CONFIRMADA'].includes(appointment.status) || appointment.presenca === 'FALTOU' || appointment.recebimentoConfirmado || !appointment.data || appointment.data < today) continue;
    const clientId = appointment.cliente?.id;
    if (clientId != null && !nextAppointmentByClient.has(String(clientId))) {
      nextAppointmentByClient.set(String(clientId), `${appointment.data}T${appointment.horario?.slice(0, 5) || '00:00'}`);
    }
  }
  const items = [...state.clients]
    .filter((item) => `${item.nome} ${item.telefone} ${item.instagram || ''}`.toLocaleLowerCase('pt-BR').includes(query))
    .sort((a, b) => {
      const aNext = nextAppointmentByClient.get(String(a.id));
      const bNext = nextAppointmentByClient.get(String(b.id));
      if (aNext && bNext && aNext !== bNext) return aNext < bNext ? -1 : 1;
      if (aNext && !bNext) return -1;
      if (!aNext && bNext) return 1;
      return Number(a.id) - Number(b.id);
    });
  $('client-count').textContent = `${items.length} cliente(s)`;
  $('client-list').innerHTML = items.length ? `<div class="table-scroll"><table><thead><tr><th>NOME</th><th>CONTATO</th><th>IDADE</th><th>AÇÕES</th></tr></thead><tbody>${items.map((item) => `<tr><td>${escapeHtml(item.nome)}<small>CLIENTE #${item.id}</small></td><td>${escapeHtml(item.telefone)}<small>${escapeHtml(item.instagram || 'Sem Instagram')}</small></td><td>${item.idade == null ? '—' : escapeHtml(item.idade)}</td><td><div class="row-actions"><button class="text-button" data-edit-client="${item.id}">Editar</button><button class="text-button delete" ${state.user?.perfil === 'PROPRIETARIO' ? '' : 'hidden'} data-delete-client="${item.id}">Excluir</button></div></td></tr>`).join('')}</tbody></table></div>` : empty('Toda história começa com alguém.', query ? 'Nenhum cliente encontrado nessa busca.' : 'Cadastre seu primeiro cliente para começar.', query ? '' : 'client');
}


function openEditor(type, id = null) {
  if (!state.loaded) return;
  const item = (type === 'client' ? state.clients : state.appointments).find((entry) => entry.id === id) || {};
  if (type === 'appointment' && item.recebimentoConfirmado) { notify('Atendimento com recebimento confirmado não pode ser alterado.'); return; }
  state.edit = { type, id, clientId: item.cliente?.id, version: item.version, original: item };
  $('editor-title').textContent = `${id ? 'Editar' : type === 'client' ? 'Novo' : 'Nova'} ${type === 'client' ? 'cliente' : 'marcacao'}`;
  $('editor-error').textContent = '';
  const customerFields = `<label>Nome completo<input name="nome" required maxlength="255" autocomplete="name"></label><div class="form-row"><label>Celular com DDD<input name="telefone" type="tel" required maxlength="30" autocomplete="tel" placeholder="(11) 99999-9999"></label><label>Idade<input name="idade" type="number" min="0" max="150" step="1"></label></div><label>Instagram (opcional)<input name="instagram" maxlength="100" placeholder="@cliente"></label>`;
  if (type === 'client') {
    $('editor-fields').innerHTML = customerFields;
  } else {
    const customer = id
      ? `<p>Cliente: <strong>${escapeHtml(item.cliente?.nome)}</strong></p>`
      : `<label>Cliente<select name="clientMode" id="client-mode"><option value="existing">Cliente existente</option><option value="new">Novo cliente</option></select></label><fieldset id="existing-client"><legend>Cliente existente</legend><label>Selecione o cliente<select name="clienteId" required><option value="">Selecione...</option>${[...state.clients].sort((a,b) => a.nome.localeCompare(b.nome, 'pt-BR')).map((c) => `<option value="${c.id}">${escapeHtml(c.nome)} - ${escapeHtml(c.telefone)}</option>`).join('')}</select></label></fieldset><fieldset id="new-client" hidden disabled><legend>Novo cliente</legend>${customerFields}</fieldset>`;
    $('editor-fields').innerHTML = `${customer}<label>Profissional<select name="profissionalId" required>${state.professionals.filter((p) => p.ativo || p.id === item.profissional?.id).map((p) => `<option value="${p.id}">${escapeHtml(p.nome)}${p.ativo ? '' : ' (inativo)'}</option>`).join('')}</select></label><div class="form-row"><label>Data<input name="data" type="date" required></label><label>Horario<select name="horario" required><option value="">Consultando...</option></select></label></div><label>Descricao<textarea name="descricao" maxlength="255"></textarea></label><label>Duracao (minutos)<input name="duracaoMinutos" type="number" min="1" max="720" required></label><label>Status<select name="status">${Object.entries(statuses).map(([v,label]) => `<option value="${v}">${label}</option>`).join('')}</select></label>`;
  }
  const values = type === 'client' ? item : { ...item, data: item.data || localDate(), horario: item.horario?.slice(0,5), status: item.status || 'AGENDADA', duracaoMinutos: item.duracaoMinutos || 60, profissionalId: item.profissional?.id || state.professionals.find((p) => p.ativo)?.id };
  for (const field of $('editor-fields').querySelectorAll('[name]')) field.value = values[field.name] ?? '';
  const mode = $('client-mode');
  if (mode) {
    mode.value = state.clients.length ? 'existing' : 'new';
    mode.onchange = () => {
      const isNew = mode.value === 'new';
      $('new-client').hidden = !isNew;
      $('new-client').disabled = !isNew;
      $('existing-client').hidden = isNew;
      $('existing-client').disabled = isNew;
    };
    mode.onchange();
  }
  $('save-editor').textContent = type === 'client' ? 'Salvar cliente' : 'Salvar marcacao';
  $('editor').showModal();
  if (type === 'appointment') window.inkTeam?.editorAvailability(item.horario?.slice(0, 5));
}

$('editor-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  if ($('save-editor').disabled) return;
  const { type, id, clientId, version } = state.edit;
  const values = Object.fromEntries(new FormData(event.currentTarget));
  const newClient = type === 'appointment' && !id && values.clientMode === 'new';
  const client = type === 'client' || newClient
    ? { nome: values.nome.trim(), telefone: values.telefone.trim(), idade: values.idade === '' ? null : Number(values.idade), instagram: values.instagram.trim() }
    : { id: id ? clientId : Number(values.clienteId) };
  const body = type === 'client' ? client : { cliente: client, data: values.data, horario: values.horario, descricao: values.descricao.trim(), status: values.status, duracaoMinutos: Number(values.duracaoMinutos), profissionalId: Number(values.profissionalId) };
  if (id) body.version = version;
  const buttons = ['save-editor', 'cancel-editor', 'close-editor'];
  buttons.forEach((key) => { $(key).disabled = true; });
  $('editor-error').textContent = '';
  try {
    await api(`/${type === 'client' ? 'clientes' : 'marcacoes'}${id ? `/${id}` : ''}${newClient ? '/com-cliente' : ''}`, { method: id ? 'PUT' : 'POST', body });
    $('editor').close();
    notify('Registro salvo. Tudo em ordem!');
    await loadData();
    if (type === 'appointment' && state.token) {
      ['agenda-search', 'agenda-date', 'agenda-status', 'agenda-professional'].forEach((key) => { $(key).value = ''; });
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
$('logout').onclick = async () => { try { await api('/auth/logout', { method: 'POST' }); } catch {} finally { logout(); } };
$('main-action').onclick = () => openEditor(state.view === 'clients' ? 'client' : 'appointment');
$('see-agenda').onclick = () => setView('agenda');
$('close-editor').onclick = $('cancel-editor').onclick = () => $('editor').close();
$('cancel-delete').onclick = () => $('confirmation').close();
$('editor').addEventListener('cancel', (event) => { if ($('save-editor').disabled) event.preventDefault(); });
$('confirmation').addEventListener('cancel', (event) => { if ($('confirm-delete').disabled) event.preventDefault(); });
['agenda-search', 'agenda-date', 'agenda-status', 'agenda-professional'].forEach((id) => $(id).addEventListener('input', renderAgenda));
$('client-search').addEventListener('input', renderClients);
$('clear-filters').onclick = () => {
  ['agenda-search', 'agenda-date', 'agenda-status', 'agenda-professional'].forEach((id) => { $(id).value = ''; });
  renderAgenda();
};
async function loadAuthConfig() {
  $('toggle-auth').disabled = true;
  try {
    const config = await api('/auth/config', { publicRequest: true });
    state.registrationEnabled = Boolean(config.registrationEnabled);
    $('auth-registration').hidden = !state.registrationEnabled;
    $('landing-offer').hidden = !state.registrationEnabled || state.register;
    $('registration-notice').hidden = state.registrationEnabled;
  } catch {
    state.registrationEnabled = false;
    $('auth-registration').hidden = true;
    $('landing-offer').hidden = true;
    $('registration-notice').hidden = false;
    $('registration-notice').textContent = 'Cadastro indisponível. Entre com uma conta existente.';
  } finally {
    $('toggle-auth').disabled = false;
  }
}
const startLandingTrial = () => {
  if (!state.registrationEnabled || state.register) return;
  $('toggle-auth').click();
  $('auth-form').elements.nomeEstudio.focus();
};
$('landing-trial').addEventListener('click', startLandingTrial);
document.querySelectorAll('[data-start-trial]').forEach(link => link.addEventListener('click', startLandingTrial));
window.inkApp = { api, user: () => state.user, appointments: () => state.appointments, view: () => state.view, reload: loadData, render, notify };
loadAuthConfig();
if (state.token) openWorkspace();
