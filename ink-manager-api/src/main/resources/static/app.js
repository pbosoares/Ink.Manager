'use strict';

// Load brand fonts without holding up the authenticated app bootstrap.
const brandFonts = document.createElement('link');
brandFonts.rel = 'preload';
brandFonts.as = 'style';
brandFonts.href = 'https://fonts.googleapis.com/css2?family=DM+Sans:wght@400;500;600;700&family=Inter:wght@400..800&family=Inter+Tight:wght@600..800&family=Libre+Caslon+Display&family=Rye&display=swap';
brandFonts.onload = () => { brandFonts.rel = 'stylesheet'; };
document.head.append(brandFonts);

const $ = (id) => document.getElementById(id);
const installDesktopSidebarToggle = () => {
  const workspace = $('workspace');
  const sidebar = workspace?.querySelector('.sidebar');
  const topbar = workspace?.querySelector('.topbar');
  if (!workspace || !sidebar || !topbar || topbar.querySelector('#sidebar-toggle')) return;
  const label = topbar.firstElementChild;
  if (!label) return;
  sidebar.id = 'workspace-sidebar';
  const toggle = document.createElement('button');
  toggle.id = 'sidebar-toggle';
  toggle.type = 'button';
  toggle.className = 'sidebar-toggle';
  toggle.setAttribute('aria-controls', sidebar.id);
  toggle.setAttribute('aria-expanded', 'true');
  toggle.setAttribute('aria-label', 'Recolher navega\u00e7\u00e3o lateral');
  toggle.innerHTML = '<svg viewBox="0 0 24 24" aria-hidden="true"><rect x="3.5" y="4" width="17" height="16" rx="2"></rect><path d="M9 4v16"></path></svg>';
  const leading = document.createElement('div');
  leading.className = 'topbar-leading';
  topbar.insertBefore(leading, label);
  leading.append(toggle, label);
  toggle.addEventListener('click', () => {
    const collapsed = workspace.classList.toggle('sidebar-collapsed');
    toggle.setAttribute('aria-expanded', String(!collapsed));
    toggle.setAttribute('aria-label', collapsed
      ? 'Expandir navega\u00e7\u00e3o lateral'
      : 'Recolher navega\u00e7\u00e3o lateral');
  });
};
installDesktopSidebarToggle();
const statuses = { AGENDADA: 'Agendada', CONFIRMADA: 'Confirmada', CONCLUIDA: 'Concluída', CANCELADA: 'Cancelada' };
const appointmentActionsLocked = (item) => Boolean(item?.presenca || item?.recebimentoConfirmado);
const dailyAgendaColors = ['#2456D8', '#168B74', '#C05B36', '#7A58A7', '#BB4F73', '#667C2B', '#287E9D'];
let dailyAgendaPixelsPerMinute = window.matchMedia('(max-width: 700px)').matches ? 1.35 : 1.05;
const civilMonthNames = ['janeiro', 'fevereiro', 'março', 'abril', 'maio', 'junho', 'julho', 'agosto', 'setembro', 'outubro', 'novembro', 'dezembro'];
const civilWeekdayNames = ['domingo', 'segunda-feira', 'terça-feira', 'quarta-feira', 'quinta-feira', 'sexta-feira', 'sábado'];
const state = { token: sessionStorage.getItem('ink.token'), clients: [], appointments: [], professionals: [], user: null, studio: null, view: 'overview', register: false, registrationEnabled: false, edit: null, deletion: null, generation: 0, loaded: false, loading: false, loadRequest: 0 };
let applyMobileNavigationLayout = () => {};
let updateMobileDockPresentation = () => {};
let closeMobileDockMenu = () => {};
const installMobileNavigationDock = () => {
  const workspace = $('workspace');
  const sidebarNavigation = $('sidebar-navigation');
  const primaryNavigation = $('mobile-primary-navigation');
  const extraNavigation = $('mobile-more-navigation');
  const menu = $('mobile-menu-popover');
  const menuToggle = $('mobile-menu-toggle');
  const dock = $('mobile-navigation-dock');
  const action = $('main-action');
  const actionRow = $('mobile-action-row');
  const stats = $('overview-stats');
  const statsSlot = $('mobile-stats-slot');
  const account = workspace?.querySelector('.sidebar-bottom');
  if (!workspace || !sidebarNavigation || !primaryNavigation || !extraNavigation || !menu || !menuToggle || !dock || !action || !actionRow || !stats || !statsSlot || !account) return;

  const desktopButtons = Array.from(sidebarNavigation.querySelectorAll('[data-view]'));
  const buttonsByView = new Map(desktopButtons.map((button) => [button.dataset.view, button]));
  buttonsByView.get('agenda')?.setAttribute('hidden', '');
  const actionHome = action.parentNode;
  const actionHomeNext = action.nextSibling;
  const statsHome = stats.parentNode;
  const statsHomeNext = stats.nextSibling;
  const accountHome = account.parentNode;
  const accountHomeNext = account.nextSibling;
  const media = window.matchMedia('(max-width: 700px)');
  const primaryViews = ['overview', 'clients', 'receipts'];
  const moreViews = ['team', 'reports', 'subscription', 'studio'];
  const navLabels = {
    subscription: 'Assinatura',
    overview: 'Visão geral',
    agenda: 'Agenda',
    clients: 'Clientes',
    receipts: 'Recebimentos',
    team: 'Equipe e horários',
    studio: 'Meu negócio',
    reports: 'Relatórios'
  };

  const closeMenu = (restoreFocus = false) => {
    if (menuToggle.getAttribute('aria-expanded') !== 'true') return;
    menu.hidden = true;
    menuToggle.setAttribute('aria-expanded', 'false');
    menuToggle.setAttribute('aria-label', 'Abrir menu de navegação');
    if (restoreFocus) menuToggle.focus();
  };
  closeMobileDockMenu = closeMenu;

  updateMobileDockPresentation = () => {
    const mobile = media.matches;
    workspace.classList.toggle('mobile-overview-active', mobile && state.view === 'overview');
    document.body.classList.toggle('workspace-mobile-active', mobile && !workspace.hidden);
    dock.classList.toggle('has-action', !action.hidden);
    menuToggle.classList.toggle('is-current', moreViews.includes(state.view));
  };

  applyMobileNavigationLayout = () => {
    if (media.matches) {
      primaryViews.forEach((view) => {
        const button = buttonsByView.get(view);
        if (!button) return;
        button.setAttribute('aria-label', navLabels[view]);
        primaryNavigation.insertBefore(button, menuToggle);
      });
      moreViews.forEach((view) => {
        const button = buttonsByView.get(view);
        if (!button) return;
        button.setAttribute('aria-label', navLabels[view]);
        extraNavigation.append(button);
      });
      actionRow.append(action);
      statsSlot.append(stats);
      $('mobile-account-slot').append(account);
    } else {
      sidebarNavigation.append(...desktopButtons);
      actionHome.insertBefore(action, actionHomeNext);
      statsHome.insertBefore(stats, statsHomeNext);
      accountHome.insertBefore(account, accountHomeNext);
      closeMenu(false);
    }
    updateMobileDockPresentation();
  };

  menuToggle.addEventListener('click', () => {
    if (menuToggle.getAttribute('aria-expanded') === 'true') {
      closeMenu(false);
      return;
    }
    menu.hidden = false;
    menuToggle.setAttribute('aria-expanded', 'true');
    menuToggle.setAttribute('aria-label', 'Fechar menu de navegação');
  });
  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape' && menuToggle.getAttribute('aria-expanded') === 'true') closeMenu(true);
  });
  document.addEventListener('click', (event) => {
    if (menuToggle.getAttribute('aria-expanded') !== 'true') return;
    if (menu.contains(event.target) || menuToggle.contains(event.target)) return;
    closeMenu(false);
  });

  applyMobileNavigationLayout();
};
installMobileNavigationDock();

function installUnifiedAgendaTools() {
  const overview = $('overview-view');
  const dayActions = $('today-appointments-details');
  const actionButton = $('see-agenda');
  const legacyAgenda = $('agenda-view');
  const availability = legacyAgenda?.querySelector('.availability-panel');
  const history = legacyAgenda?.querySelector('.agenda-book-details');
  if (!overview || !dayActions || !actionButton || !legacyAgenda || !availability || !history) return null;

  history.id = 'agenda-book-details';
  const historyTitle = history.querySelector('.agenda-book-summary-title');
  if (historyTitle?.firstChild) historyTitle.firstChild.textContent = 'Busca e hist\u00f3rico';
  const historyAction = history.querySelector('.agenda-book-summary-action');
  if (historyAction?.firstChild) historyAction.firstChild.textContent = 'Filtrar e consultar marca\u00e7\u00f5es';
  const tools = document.createElement('details');
  tools.id = 'agenda-secondary-tools';
  tools.className = 'agenda-secondary-tools';
  tools.innerHTML = '<summary><span>Disponibilidade, busca e hist\u00f3rico</span><span class="agenda-secondary-tools-indicator" aria-hidden="true"></span></summary>';
  tools.append(availability, history);
  actionButton.insertAdjacentElement('afterend', tools);
  actionButton.textContent = 'Abrir ferramentas da agenda';
  actionButton.setAttribute('aria-controls', tools.id);
  actionButton.setAttribute('aria-expanded', 'false');

  const sync = () => {
    const toolsVisible = dayActions.open && tools.open;
    actionButton.setAttribute('aria-expanded', String(toolsVisible));
    overview.classList.toggle('showing-day-actions', dayActions.open);
    overview.classList.toggle('showing-agenda-history', toolsVisible && history.open);
  };
  const reveal = () => {
    dayActions.open = true;
    tools.open = true;
    sync();
    tools.querySelector('summary')?.focus();
    tools.scrollIntoView({ block: 'nearest' });
  };
  const close = () => {
    tools.open = false;
    history.open = false;
    actionButton.setAttribute('aria-expanded', 'false');
    sync();
  };

  dayActions.addEventListener('toggle', sync);
  tools.addEventListener('toggle', () => {
    sync();
    if (tools.open) $('availability-load')?.click();
  });
  history.addEventListener('toggle', sync);
  return { reveal, close, sync, details: tools, history };
}
const unifiedAgendaTools = installUnifiedAgendaTools();
const escapeHtml = (value) => String(value ?? '').replace(/[&<>"']/g, (char) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[char]));
const ownerAttendanceAction = (item) => {
  const presence = item?.presenca === 'FALTOU' ? 'Faltou' : item?.presenca === 'COMPARECEU' ? 'Compareceu' : '';
  if (presence) return '<span class="daily-agenda-presence ' + (item.presenca === 'FALTOU' ? 'is-absence' : 'is-present') + '">Presen\u00e7a: ' + presence + '</span>';
  if (state.user?.perfil !== 'PROPRIETARIO') return '';
  return window.inkAnalytics?.attendanceAction(item) || '';
};
const ownerReceiptAction = (item) => {
  if (state.user?.perfil === 'PROPRIETARIO') return window.inkSimple?.receiptAction(item) || '';
  return item?.recebimentoConfirmado ? '<small class="receipt-recorded">Recebimento confirmado</small>' : '';
};
const presenceStatusLabel = (item) => item?.presenca === 'FALTOU' ? 'Faltou' : item?.presenca === 'COMPARECEU' ? 'Compareceu' : '';
function studioNowContext() {
  const timeZone = state.studio?.fusoHorario;
  if (typeof timeZone !== 'string' || !timeZone.trim()) return null;
  try {
    const parts = new Intl.DateTimeFormat('en-CA', { timeZone, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }).formatToParts(new Date());
    const values = Object.fromEntries(parts.filter((part) => part.type !== 'literal').map((part) => [part.type, part.value]));
    const date = `${values.year}-${values.month}-${values.day}`;
    const hour = Number(values.hour), minute = Number(values.minute);
    if (!civilDateParts(date) || !Number.isFinite(hour) || !Number.isFinite(minute)) return null;
    return { date, time: `${values.hour}:${values.minute}`, minuteOfDay: hour * 60 + minute };
  } catch {
    return null;
  }
}
const localDate = () => studioNowContext()?.date || '';

function formatCivilDateLong(value) {
  const parts = civilDateParts(value);
  if (!parts) return '';
  const weekday = civilWeekdayNumber(value);
  return `${civilWeekdayNames[(weekday || 7) % 7]}, ${parts.day} de ${civilMonthNames[parts.month - 1]} de ${parts.year}`;
}
function refreshStudioDateContext(seedToday = false) {
  const current = studioNowContext();
  const input = $('daily-agenda-date');
  if (seedToday && input && !input.value && current) input.value = current.date;
  const selected = input?.value || '';
  const label = $('today-label');
  if (label) {
    label.dataset.timezoneValid = current ? 'true' : 'false';
    if (!current) label.textContent = state.loading ? 'Carregando fuso do estúdio…' : (selected ? `Data selecionada · ${formatCivilDateLong(selected)} · fuso do estúdio indisponível` : 'Fuso do estúdio indisponível · selecione uma data');
    else if (state.view === 'overview' && selected) label.textContent = 'FUSO DO ESTÚDIO';
    else label.textContent = `Hoje · ${formatCivilDateLong(current.date)}`;
  }
  return current;
}
const formatDate = (value) => {
  const parts = civilDateParts(value);
  return parts ? `${String(parts.day).padStart(2, '0')} de ${civilMonthNames[parts.month - 1].slice(0, 3)}.` : 'Sem data';
};
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

function formatBrazilianPhone(value) {
  const raw = String(value ?? '');
  if (/^\s*\+(?!55)/.test(raw)) return raw;
  let digits = raw.replace(/\D/g, '');
  const international = /^\s*\+55/.test(raw) || (digits.startsWith('55') && digits.length > 11);
  if (!international && digits.startsWith('0') && [11, 12].includes(digits.length)) digits = digits.slice(1);
  const local = (international ? digits.slice(2) : digits).slice(0, 11);
  const country = international ? '+55 ' : '';
  if (!local) return international ? country : '';
  if (local.length < 2) return `${country}(${local}`;
  const ddd = local.slice(0, 2);
  if (local.length === 2) return `${country}(${ddd})`;
  const subscriber = local.slice(2);
  const group = subscriber.startsWith('9') ? 5 : 4;
  const formatted = subscriber.length > group ? `${subscriber.slice(0, group)}-${subscriber.slice(group)}` : subscriber;
  return `${country}(${ddd}) ${formatted}`;
}

document.addEventListener('input', (event) => {
  const input = event.target;
  if (!(input instanceof HTMLInputElement) || input.type !== 'tel' || input.name !== 'telefone') return;
  const original = input.value;
  const caret = input.selectionStart ?? original.length;
  const digitsBeforeCaret = original.slice(0, caret).replace(/\D/g, '').length;
  const formatted = formatBrazilianPhone(original);
  if (formatted === original) return;
  input.value = formatted;
  let nextCaret = 0, digitsSeen = 0;
  while (nextCaret < formatted.length && digitsSeen < digitsBeforeCaret) {
    if (/\d/.test(formatted[nextCaret])) digitsSeen++;
    nextCaret++;
  }
  while (nextCaret < formatted.length && !/\d/.test(formatted[nextCaret])) nextCaret++;
  input.setSelectionRange(nextCaret, nextCaret);
});

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
  ['today-list','agenda-list','client-list','account-name','topbar-user-name','topbar-user-role','topbar-user-initials'].forEach(id => { $(id).textContent = ''; });
  $('topbar-profile').hidden = true;
  $('daily-agenda-date').value = '';
  $('today-appointments-details').open = false;
  unifiedAgendaTools?.close();
  $('agenda-book-details').open = false;
  $('overview-view').classList.remove('showing-day-actions');
  $('workspace').hidden = true;
  closeMobileDockMenu(false);
  updateMobileDockPresentation();
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
    const error = new Error(message || (response.status === 401 ? 'E-mail ou senha inválidos.' : 'Não foi possível concluir. Tente novamente.'));
    error.status = response.status;
    throw error;
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
    $('topbar-profile').hidden = !state.user?.nome;
    $('topbar-user-name').textContent = state.user?.nome || '';
    $('topbar-user-role').textContent = state.user?.perfil === 'PROPRIETARIO' ? 'Propriet\u00e1rio' : 'Equipe';
    $('topbar-user-initials').textContent = dailyProfessionalInitials(state.user?.nome);
    refreshStudioDateContext(true);
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
  $('today-label').textContent = 'Carregando fuso do estúdio…';
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
  const openAgendaTools = view === 'agenda';
  if (openAgendaTools) view = 'overview';
  if (['receipts', 'reports', 'subscription'].includes(view) && state.user?.perfil !== 'PROPRIETARIO') view = 'overview';
  const studioSection = view === 'subscription' ? 'billing-settings' : null;
  if (studioSection) view = 'studio';
  if (view === 'overview') {
    $('today-appointments-details').open = false;
    unifiedAgendaTools?.close();
    $('overview-view').classList.remove('showing-day-actions', 'showing-agenda-history');
  }
  $('studio-view').classList.toggle('billing-only', Boolean(studioSection));
  state.view = view;
  updateMobileDockPresentation();
  const copy = {
    overview: ['VISÃO GERAL', 'Agenda da equipe', 'Atendimentos, confirmações e horários do dia.'],
    agenda: ['TEMPO PARA ATENDER BEM', 'Sua agenda, no lugar.', 'Cada atendimento tem seu espaço. Cuide dos próximos encontros.'],
    reports: ['CADERNO DO MÊS', 'Agenda e comparecimento.', 'Acompanhe os resultados e o ritmo do negócio.'],
    team: ['A EQUIPE', 'Cada profissional, sua agenda.', 'Expedientes e folgas compartilhados com a equipe.'],
    studio: ['SEU ESPACO', 'Seu negócio tem a sua cara.', 'Serviços, link de reservas, acessos e assinatura no mesmo lugar.'],
    receipts: ['VALORES CONFIRMADOS', 'Recebimentos, sem complicação.', 'Confirme o atendimento e o valor recebido para acompanhar a semana e o mês.'],
    clients: ['QUEM CONFIA NO SEU TRABALHO', 'Seus clientes, sempre por perto.', 'Contatos e histórias para manter sempre por perto.']
  }[view] || ['A CASA ESTÁ ABERTA', 'Cada dia, uma nova história.', 'Sua rotina organizada. Seus atendimentos em primeiro lugar.'];
  ['overview', 'agenda', 'reports', 'clients', 'team', 'studio', 'receipts'].forEach((key) => { $(`${key}-view`).hidden = key !== view; });
  const activeView = studioSection ? 'subscription' : view;
  document.querySelectorAll('[data-view]').forEach((button) => {
    const active = button.dataset.view === activeView;
    button.classList.toggle('active', active);
    if (active) button.setAttribute('aria-current', 'page');
    else button.removeAttribute('aria-current');
  });
  $('page-kicker').textContent = copy[0];
  if (view === 'overview') {
    const title = $('page-title');
    title.replaceChildren(document.createTextNode('Agenda da '), Object.assign(document.createElement('span'), { textContent: 'equipe' }));
  } else $('page-title').textContent = copy[1];
  $('page-description').textContent = copy[2];
  if (studioSection) {
    $('page-kicker').textContent = 'SUA ASSINATURA';
    $('page-title').textContent = 'Assinatura';
    $('page-description').textContent = 'Plano e pagamentos da sua conta.';
  }
  $('main-action').textContent = view === 'clients' ? '+ Novo cliente' : '+ Nova marcação';
  render();
  if (openAgendaTools) unifiedAgendaTools?.reveal();
  if (view === 'reports' && state.user?.perfil === 'PROPRIETARIO') window.inkAnalytics?.ensureReportLoaded();
}

function empty(title, description, action) {
  return `<div class="empty"><strong>${title}</strong>${description}${action ? `<button class="text-button" data-create="${action}">${action === 'client' ? 'Cadastrar primeiro cliente' : 'Criar uma marcação'} ↗</button>` : ''}</div>`;
}

function render() {
  $('main-action').disabled = !state.loaded;
  $('main-action').hidden = ['team', 'studio', 'receipts', 'reports'].includes(state.view);
  updateMobileDockPresentation();
  document.querySelectorAll('.owner-only').forEach((element) => { element.hidden = state.user?.perfil !== 'PROPRIETARIO'; });
  ['overview', 'agenda', 'reports', 'clients', 'team', 'studio', 'receipts'].forEach((view) => { $(`${view}-view`).hidden = !state.loaded || state.view !== view; });
  $('workspace').setAttribute('aria-busy', String(state.loading));
  refreshStudioDateContext();
  if (!state.loaded) {
    ['stat-today', 'stat-upcoming', 'stat-clients'].forEach((id) => { $(id).textContent = '—'; });
    ['today-list', 'agenda-list', 'client-list', 'agenda-count', 'client-count'].forEach((id) => { $(id).textContent = ''; });
    $('today-business-title').textContent = 'Resumo do dia';
    $('today-business-summary').textContent = 'Aguardando os dados da agenda.';
    return;
  }
  const appointments = sortedAppointments();
  const selectedDate = $('daily-agenda-date').value;
  const studioToday = studioNowContext()?.date;
  const dayAppointments = selectedDate ? appointments.filter((item) => item.data === selectedDate) : [];
  const booked = dayAppointments.filter((item) => ['AGENDADA', 'CONFIRMADA'].includes(item.status));
  const awaitingConfirmation = dayAppointments.filter((item) => item.status === 'AGENDADA');
  const completed = dayAppointments.filter((item) => item.status === 'CONCLUIDA');
  const confirmed = dayAppointments.filter((item) => item.status === 'CONFIRMADA');
  const canceled = dayAppointments.filter((item) => item.status === 'CANCELADA').length;
  const selectedWeekday = selectedDate ? civilWeekdayNumber(selectedDate) : null;
  let currentWeekStart = selectedWeekday ? selectedDate : '';
  for (let offset = 1; currentWeekStart && offset < selectedWeekday; offset++) currentWeekStart = shiftCivilDate(currentWeekStart, -1);
  let currentWeekEnd = currentWeekStart;
  for (let offset = 0; currentWeekStart && offset < 6; offset++) currentWeekEnd = shiftCivilDate(currentWeekEnd, 1);
  const previousWeekEnd = currentWeekStart ? shiftCivilDate(currentWeekStart, -1) : '';
  let previousWeekStart = previousWeekEnd;
  for (let offset = 0; previousWeekStart && offset < 6; offset++) previousWeekStart = shiftCivilDate(previousWeekStart, -1);
  const countNonCanceledInRange = (start, end) => start && end
    ? state.appointments.filter((item) => item.status !== 'CANCELADA' && item.data >= start && item.data <= end).length
    : 0;
  const currentWeekCount = currentWeekStart ? countNonCanceledInRange(currentWeekStart, currentWeekEnd) : null;
  const previousWeekCount = previousWeekStart ? countNonCanceledInRange(previousWeekStart, previousWeekEnd) : null;
  const weeklyCaption = $('stat-today-caption');
  let trendClass = 'is-trend-neutral';
  let trendMessage = 'Selecione uma data';
  if (currentWeekStart && previousWeekCount === 0) trendMessage = 'Sem base de comparação';
  else if (currentWeekStart) {
    const percentage = ((currentWeekCount - previousWeekCount) / previousWeekCount) * 100;
    const formattedPercentage = Math.abs(percentage).toLocaleString('pt-BR', { maximumFractionDigits: 1 });
    if (percentage > 0) {
      trendClass = 'is-trend-up';
      trendMessage = '↑ Aumento de ' + formattedPercentage + '% em relação à semana anterior';
    } else if (percentage < 0) {
      trendClass = 'is-trend-down';
      trendMessage = '↓ Redução de ' + formattedPercentage + '% em relação à semana anterior';
    } else trendMessage = 'Sem variação em relação à semana anterior';
  }
  weeklyCaption.classList.remove('is-trend-up', 'is-trend-down', 'is-trend-neutral');
  weeklyCaption.classList.add(trendClass);
  $('stat-today-label').textContent = 'Atendimentos na semana';
  $('stat-today').textContent = currentWeekCount === null ? '—' : String(currentWeekCount).padStart(2, '0');
  weeklyCaption.textContent = trendMessage;
  $('stat-upcoming-label').textContent = 'A CONFIRMAR';
  $('stat-upcoming').textContent = selectedDate ? String(awaitingConfirmation.length).padStart(2, '0') : '—';
  $('stat-upcoming-caption').textContent = selectedDate ? 'Agendadas na data' : 'Selecione uma data';
  $('stat-completed-label').textContent = 'CONCLUÍDOS';
  $('stat-clients').textContent = selectedDate ? String(completed.length).padStart(2, '0') : '—';
  $('stat-completed-caption').textContent = selectedDate ? 'Concluídas na data' : 'Selecione uma data';
  $('today-business-title').textContent = selectedDate && selectedDate === studioToday ? 'Hoje no negócio' : 'Resumo do dia';
  $('today-business-summary').textContent = selectedDate
    ? `${dayAppointments.length} ${dayAppointments.length === 1 ? 'marcação' : 'marcações'} · ${awaitingConfirmation.length} a confirmar · ${confirmed.length} confirmadas · ${completed.length} concluídas · ${canceled} canceladas`
    : 'Selecione uma data; sem o fuso IANA do estúdio, “hoje” não será inferido do navegador.';
  $('today-list').innerHTML = selectedDate && dayAppointments.length
    ? '<div class="daily-agenda-actions-rows">' + dayAppointments.map((item) => {
      const client = escapeHtml(item.cliente?.nome || 'Cliente');
      const professional = escapeHtml(item.profissional?.nome || 'Sem profissional');
      const service = escapeHtml(item.servicoNome || item.descricao || 'Atendimento');
      return '<article class="daily-agenda-action-row">' +
        '<span class="daily-agenda-action-time">' + escapeHtml(item.horario?.slice(0, 5) || '—') + '</span>' +
        '<div class="daily-agenda-action-person"><strong>' + client + '</strong><small title="' + professional + ' &middot; ' + service + '">' + professional + ' &middot; ' + service + '</small></div>' +
        badge(item.status) +
        '<div class="daily-agenda-action-buttons" role="group" aria-label="Ações para ' + client + '">' +
        (ownerAttendanceAction(item)) +
        (ownerReceiptAction(item)) +
        whatsappAction(item) +
        '<button class="text-button" ' + (appointmentActionsLocked(item) ? 'disabled title="Presença ou recebimento registrado"' : '') + ' data-edit-appointment="' + escapeHtml(item.id) + '" aria-label="Editar marcação de ' + client + '">Editar</button>' +
        '</div></article>';
    }).join('') + '</div>'
    : selectedDate ? empty('Um respiro na agenda.', 'Nenhum atendimento marcado para a data selecionada.', 'appointment') : empty('Selecione uma data.', 'O fuso do estúdio não está disponível para determinar “hoje”.', '');
  renderAgenda();
  renderClients();
  window.inkTeam?.render();
  if ($('studio-view').classList.contains('billing-only')) $('billing-settings')?.scrollIntoView({ block: 'start' });
  window.inkSimple?.render();
  if (state.view === 'overview' && state.user?.perfil === 'PROPRIETARIO') {
    // The analytics module lazy-loads owner-only presence rules in the Agenda context.
    const currentView = state.view;
    state.view = 'agenda';
    try { window.inkAnalytics?.render(); } finally { state.view = currentView; }
  } else if (['agenda', 'studio', 'reports'].includes(state.view)) window.inkAnalytics?.render();
  disableLockedAppointmentActions($('today-list'));
  renderDailyAgenda();
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
  $('agenda-list').innerHTML = items.length ? `<div class="table-scroll"><table><thead><tr><th>DATA / HORA</th><th>CLIENTE / ATENDIMENTO</th><th>STATUS</th><th>AÇÕES</th></tr></thead><tbody>${items.map((item) => `<tr><td>${escapeHtml(formatDate(item.data))}<small>${escapeHtml(item.data?.slice(0, 4))} · ${escapeHtml(item.horario?.slice(0, 5))}</small></td><td>${escapeHtml(item.cliente?.nome)}<small>${escapeHtml(item.profissional?.nome)} / ${escapeHtml(item.servicoNome || item.descricao || 'Sem descrição')}</small>${window.inkSimple?.appointmentDetails(item) || ''}${ownerAttendanceAction(item)}</td><td>${badge(item.status)}</td><td><div class="row-actions">${ownerReceiptAction(item)}${whatsappAction(item)}<button class="text-button" ${item.recebimentoConfirmado ? 'disabled title="Recebimento confirmado"' : ''} data-edit-appointment="${item.id}">Editar</button><button class="text-button delete" ${state.user?.perfil === 'PROPRIETARIO' ? '' : 'hidden'} ${item.recebimentoConfirmado ? 'disabled title="Recebimento confirmado"' : ''} data-delete-appointment="${item.id}">Excluir</button></div></td></tr>`).join('')}</tbody></table></div>` : empty('Nenhuma marcação por aqui.', query || date || status ? 'Experimente mudar os filtros.' : 'Reserve um horário para o próximo atendimento.', query || date || status ? '' : 'appointment');
  disableLockedAppointmentActions($('agenda-list'));
}

function civilDateParts(value) {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(String(value || ''));
  return match ? { year: Number(match[1]), month: Number(match[2]), day: Number(match[3]) } : null;
}

function civilDateLabel(value) {
  const parts = civilDateParts(value);
  return parts ? String(parts.day).padStart(2, '0') + '/' + String(parts.month).padStart(2, '0') + '/' + parts.year : '';
}

function shiftCivilDate(value, delta) {
  const parts = civilDateParts(value);
  if (!parts) return '';
  let { year, month, day } = parts;
  const daysInMonth = (y, m) => [31, (y % 4 === 0 && (y % 100 !== 0 || y % 400 === 0)) ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31][m - 1];
  if (delta > 0) {
    day++;
    if (day > daysInMonth(year, month)) { day = 1; month++; if (month > 12) { month = 1; year++; } }
  } else {
    day--;
    if (day < 1) { month--; if (month < 1) { month = 12; year--; } day = daysInMonth(year, month); }
  }
  return String(year).padStart(4, '0') + '-' + String(month).padStart(2, '0') + '-' + String(day).padStart(2, '0');
}

function civilWeekdayNumber(value) {
  const parts = civilDateParts(value);
  if (!parts) return null;
  const offsets = [0, 3, 2, 5, 0, 3, 5, 1, 4, 6, 2, 4];
  const year = parts.month < 3 ? parts.year - 1 : parts.year;
  const sundayZero = (year + Math.floor(year / 4) - Math.floor(year / 100) + Math.floor(year / 400) + offsets[parts.month - 1] + parts.day) % 7;
  return sundayZero === 0 ? 7 : sundayZero;
}

function civilMinutes(value) {
  const match = /^(\d{1,2}):(\d{2})(?::\d{2}(?:\.\d+)?)?$/.exec(String(value || ''));
  if (!match) return null;
  const hour = Number(match[1]), minute = Number(match[2]);
  return hour <= 23 && minute <= 59 ? hour * 60 + minute : null;
}

function civilClockLabel(value) {
  const minute = Math.max(0, Math.floor(value));
  const dayOffset = Math.floor(minute / 1440);
  const minuteOfDay = minute % 1440;
  return String(Math.floor(minuteOfDay / 60)).padStart(2, '0') + ':' + String(minuteOfDay % 60).padStart(2, '0') + (dayOffset ? ' (+1 dia)' : '');
}

function dailyProfessionalKey(item) {
  return item?.profissional?.id == null ? 'sem-profissional' : String(item.profissional.id);
}

function dailyProfessionalColor(id) {
  let hash = 0;
  for (const character of String(id ?? 'sem-profissional')) hash = (hash * 31 + character.charCodeAt(0)) >>> 0;
  return dailyAgendaColors[hash % dailyAgendaColors.length];
}

function dailyProfessionalInitials(value) {
  const words = String(value || '').trim().split(/\s+/).filter(Boolean);
  return words.length ? words.slice(0, 2).map((word) => Array.from(word)[0]).join('').toLocaleUpperCase('pt-BR') : 'PR';
}

function disableLockedAppointmentActions(root) {
  if (!root) return;
  root.querySelectorAll('[data-edit-appointment], [data-delete-appointment]').forEach((button) => {
    const id = Number(button.dataset.editAppointment ?? button.dataset.deleteAppointment);
    const item = state.appointments.find((entry) => Number(entry.id) === id);
    if (!appointmentActionsLocked(item)) return;
    button.disabled = true;
    button.title = 'Presença ou recebimento registrado; ação indisponível.';
  });
}

function packDailyAppointments(items) {
  const events = items.map((item) => {
    const start = civilMinutes(item.horario);
    const rawDuration = Number(item.duracaoMinutos);
    const duration = Number.isFinite(rawDuration) && rawDuration >= 1 && rawDuration <= 720 ? rawDuration : 60;
    return { item, start, duration };
  }).filter((event) => event.start != null).sort((a, b) => a.start - b.start || Number(a.item.id) - Number(b.item.id));
  const trackEnds = [];
  for (const event of events) {
    let track = trackEnds.findIndex((end) => end <= event.start);
    if (track < 0) { track = trackEnds.length; trackEnds.push(event.start + event.duration); }
    else trackEnds[track] = event.start + event.duration;
    event.track = track;
  }
  return { events, trackCount: Math.max(1, trackEnds.length) };
}

function renderDailyAgenda() {
  if (!state.loaded || state.view !== 'overview') return;
  const dateInput = $('daily-agenda-date');
  const previous = $('daily-agenda-previous');
  const todayButton = $('daily-agenda-today');
  const next = $('daily-agenda-next');
  const professionalWrap = $('daily-agenda-professional-wrap');
  const professionalSelect = $('daily-agenda-professional');
  const grid = $('daily-agenda-grid');
  const history = $('daily-agenda-history');
  const summary = $('daily-agenda-summary');
  const date = dateInput.value;
  previous.disabled = next.disabled = !date;
  todayButton.disabled = !studioNowContext();
  if (!date) {
    summary.textContent = 'Selecione uma data para ver os atendimentos.';
    grid.innerHTML = '<p class="daily-agenda-placeholder">' + (studioNowContext() ? 'Selecione uma data para ver a agenda.' : 'O fuso IANA do estúdio não está disponível. Selecione uma data manualmente; a agenda não usará o fuso do navegador.') + '</p>';
    history.hidden = true;
    history.innerHTML = '';
    professionalWrap.hidden = true;
    return;
  }

  const dayItems = sortedAppointments().filter((item) => item.data === date);
  const bookedKeys = new Set(dayItems.map(dailyProfessionalKey));
  const professionalsByKey = new Map(state.professionals
    .filter((professional) => professional.ativo || bookedKeys.has(String(professional.id)))
    .map((professional) => [String(professional.id), {
      id: String(professional.id), nome: professional.nome || 'Profissional',
      ativo: Boolean(professional.ativo), horarios: Array.isArray(professional.horarios) ? professional.horarios : []
    }]));
  for (const item of dayItems) {
    const key = dailyProfessionalKey(item);
    if (!professionalsByKey.has(key)) professionalsByKey.set(key, {
      id: key, nome: item.profissional?.nome || 'Sem profissional',
      ativo: Boolean(item.profissional?.ativo), horarios: []
    });
  }
  const professionals = [...professionalsByKey.values()].sort((a, b) => Number(b.ativo) - Number(a.ativo) || a.nome.localeCompare(b.nome, 'pt-BR'));
  const priorSelection = professionalSelect.value;
  professionalSelect.innerHTML = professionals.length
    ? professionals.map((professional) => '<option value="' + escapeHtml(professional.id) + '">' + escapeHtml(professional.nome) + (professional.ativo ? '' : ' (inativo)') + '</option>').join('')
    : '<option value="">Nenhum profissional</option>';
  const selected = professionals.some((professional) => professional.id === priorSelection)
    ? priorSelection : (professionals.find((professional) => professional.ativo) || professionals[0])?.id || '';
  professionalSelect.value = selected;
  const mobile = window.matchMedia('(max-width: 700px)').matches;
  professionalWrap.hidden = !mobile || !professionals.length;
  const displayedProfessionals = mobile
    ? professionals.filter((professional) => professional.id === selected)
    : [...professionals].sort((a, b) => Number(b.ativo) - Number(a.ativo) || Number(a.id) - Number(b.id));
  const displayedKeys = new Set(displayedProfessionals.map((professional) => professional.id));
  const displayedItems = dayItems.filter((item) => displayedKeys.has(dailyProfessionalKey(item)));
  const occupying = displayedItems.filter((item) => ['AGENDADA', 'CONFIRMADA'].includes(item.status));
  const ended = displayedItems.filter((item) => !['AGENDADA', 'CONFIRMADA'].includes(item.status));
  const invalidTimeCount = occupying.filter((item) => civilMinutes(item.horario) == null).length;
  summary.textContent = occupying.length + ' horários · ' + displayedProfessionals.length + ' profissionais' + (ended.length ? ' · ' + ended.length + ' no histórico' : '') + (invalidTimeCount ? ' · ' + invalidTimeCount + ' sem horário válido' : '');
  history.hidden = !ended.length;
  history.innerHTML = ended.length
    ? '<strong>Histórico do dia · não ocupa horários</strong><ul>' + ended.map((item) => {
      const time = civilMinutes(item.horario);
      const professionalRecord = professionalsByKey.get(dailyProfessionalKey(item));
      const isInactiveProfessional = item.profissional?.id != null && professionalRecord && !professionalRecord.ativo;
      const professional = (item.profissional?.nome || 'Sem profissional') + (isInactiveProfessional ? ' (inativo)' : '');
      const label = statuses[item.status] || item.status || 'Sem status';
      const presence = presenceStatusLabel(item);
      const color = dailyProfessionalColor(item.profissional?.id ?? 'sem-profissional');
      return '<li style="--professional-color:' + color + '"><span>' + (time == null ? 'Horário não informado' : civilClockLabel(time)) + '</span><strong>' + escapeHtml(item.cliente?.nome || 'Cliente') + '</strong><small>' + escapeHtml(item.servicoNome || item.descricao || 'Atendimento') + ' · ' + escapeHtml(professional) + ' · ' + escapeHtml(label) + (presence ? ' \u00b7 Presen\u00e7a: ' + escapeHtml(presence) : '') + '</small></li>';
    }).join('') + '</ul>'
    : '';

  if (!displayedProfessionals.length) {
    summary.textContent = '0 horários · 0 profissionais';
    grid.innerHTML = '<p class="daily-agenda-placeholder">Nenhum profissional ativo ou com marcações neste dia.</p>';
    return;
  }

  const weekday = civilWeekdayNumber(date);
  const shifts = displayedProfessionals.flatMap((professional) => (professional.ativo ? professional.horarios : [])
    .filter((shift) => Number(shift.diaSemana) === weekday)
    .map((shift) => ({ start: civilMinutes(shift.inicio), end: civilMinutes(shift.fim) }))
    .filter((shift) => shift.start != null && shift.end != null && shift.end > shift.start));
  const validOccupying = occupying.filter((item) => civilMinutes(item.horario) != null);
  const starts = shifts.map((shift) => shift.start).concat(validOccupying.map((item) => civilMinutes(item.horario)));
  const ends = shifts.map((shift) => shift.end).concat(validOccupying.map((item) => civilMinutes(item.horario) + (Number(item.duracaoMinutos) || 60)));
  const startMinute = starts.length ? Math.floor(Math.min(...starts) / 60) * 60 : 8 * 60;
  let endMinute = ends.length ? Math.ceil(Math.max(...ends) / 60) * 60 : 18 * 60;
  if (endMinute <= startMinute) endMinute = startMinute + 60;
  const timelineHeight = (endMinute - startMinute) * dailyAgendaPixelsPerMinute;
  const now = studioNowContext();
  const showNowMarker = Boolean(now && now.date === date && now.minuteOfDay >= startMinute && now.minuteOfDay <= endMinute);
  const nowOffset = showNowMarker ? (now.minuteOfDay - startMinute) * dailyAgendaPixelsPerMinute : 0;
  const nowMinutes = showNowMarker ? Number(now.time.slice(3, 5)) : 30;
  const nowLabelTop = nowOffset + (nowMinutes < 12 ? 17 : nowMinutes >= 48 ? -17 : 0);
  const nowLabelMarkup = showNowMarker
    ? '<span class="daily-agenda-now-label" aria-hidden="true" style="--now-top:' + nowOffset + 'px;--now-label-top:' + nowLabelTop + 'px">AGORA ' + escapeHtml(now.time) + '</span>'
    : '';
  const timeLabels = [];
  for (let minute = startMinute; minute <= endMinute; minute += 60) {
    timeLabels.push('<span style="--time-top:' + ((minute - startMinute) * dailyAgendaPixelsPerMinute) + 'px">' + civilClockLabel(minute) + '</span>');
  }
  const headers = displayedProfessionals.map((professional) => {
    const color = dailyProfessionalColor(professional.id);
    const initials = dailyProfessionalInitials(professional.nome);
    return '<div class="daily-agenda-professional-head" style="--professional-color:' + color + '"><i class="daily-agenda-avatar" aria-hidden="true">' + escapeHtml(initials) + '</i><span>' + escapeHtml(professional.nome) + (professional.ativo ? '' : '<small>Inativo</small>') + '</span></div>';
  }).join('');
  const lanes = displayedProfessionals.map((professional) => {
    const color = dailyProfessionalColor(professional.id);
    const packed = packDailyAppointments(validOccupying.filter((item) => dailyProfessionalKey(item) === professional.id));
    const events = packed.events.map((event) => {
      const item = event.item;
      const name = item.cliente?.nome || 'Cliente';
      const service = item.servicoNome || item.descricao || 'Atendimento';
      const end = event.start + event.duration;
      const lockReason = appointmentActionsLocked(item)
        ? (item.presenca && item.recebimentoConfirmado
          ? 'Presen\u00e7a e recebimento registrados; edi\u00e7\u00e3o indispon\u00edvel.'
          : item.presenca
            ? (item.presenca === 'FALTOU' ? 'Faltou registrado' : 'Comparecimento registrado') + '; edi\u00e7\u00e3o indispon\u00edvel.'
            : 'Recebimento confirmado; edi\u00e7\u00e3o indispon\u00edvel.')
        : (!professional.ativo ? 'Profissional inativo; somente leitura no hist\u00f3rico.' : '');
      const locked = Boolean(lockReason);
      const left = (event.track / packed.trackCount) * 100;
      const width = 100 / packed.trackCount;
      const eventClass = (item.status === 'CONFIRMADA' ? 'is-confirmed' : 'is-scheduled') + (event.duration < 24 ? ' is-short' : '') + (event.duration < 12 ? ' is-micro' : '');
      const detail = civilClockLabel(event.start) + '–' + civilClockLabel(end) + ' · ' + event.duration + ' min · ' + (statuses[item.status] || item.status) + ' · ' + service;
      return '<button type="button" class="daily-agenda-event ' + eventClass + (locked ? ' is-locked' : '') + '" ' +
        (locked ? 'aria-disabled="true" title="' + escapeHtml(lockReason + ' ' + name + ', ' + detail + ', ' + professional.nome) + '"' : 'data-edit-appointment="' + escapeHtml(item.id) + '" title="' + escapeHtml(name + ', ' + detail + ', ' + professional.nome) + '"') +
        ' aria-label="' + escapeHtml(name + ', ' + detail + ', ' + professional.nome + (locked ? ', ' + lockReason : '')) + '" ' +
        'style="--event-top:' + ((event.start - startMinute) * dailyAgendaPixelsPerMinute) + 'px;--event-height:' + (event.duration * dailyAgendaPixelsPerMinute) + 'px;--professional-color:' + color + ';left:calc(' + (left + 0.5) + '% + 4px);width:calc(' + Math.max(0, width - 1) + '% - 8px)">' +
        '<span class="daily-agenda-event-meta"><span class="daily-agenda-event-time">' + escapeHtml(civilClockLabel(event.start) + '\u2013' + civilClockLabel(end)) + '</span><span class="daily-agenda-event-status ' + (item.status === 'CONFIRMADA' ? 'is-confirmed' : 'is-scheduled') + '">' + escapeHtml(statuses[item.status] || item.status || 'Sem status') + '</span></span><span class="daily-agenda-event-main"><strong>' + escapeHtml(name) + '</strong><span class="daily-agenda-event-service">' + escapeHtml(service) + '</span></span></button>';
    }).join('');
    return '<section class="daily-agenda-lane' + (professional.ativo ? '' : ' is-inactive') + (showNowMarker ? ' has-now-marker' : '') + '" aria-label="Agenda de ' + escapeHtml(professional.nome) + '" style="--timeline-height:' + timelineHeight + 'px;--professional-color:' + color + ';--now-top:' + nowOffset + 'px">' + events + '</section>';
  }).join('');
  grid.style.setProperty('--agenda-columns', String(displayedProfessionals.length));
  grid.style.setProperty('--timeline-height', timelineHeight + 'px');
  grid.innerHTML = '<div class="daily-agenda-corner">HORÁRIO</div>' + headers +
    '<div class="daily-agenda-time-rail' + (showNowMarker ? ' has-now-marker' : '') + '" aria-hidden="true" style="--timeline-height:' + timelineHeight + 'px;--now-top:' + nowOffset + 'px">' + timeLabels.join('') + nowLabelMarkup + '</div>' + lanes;
}

function renderClients() {
  if (!state.loaded) return;
  const query = $('client-search').value.toLocaleLowerCase('pt-BR');
  const nextAppointmentByClient = new Map();
  const referenceDate = studioNowContext()?.date || '';
  for (const appointment of sortedAppointments()) {
    if (!referenceDate || !['AGENDADA', 'CONFIRMADA'].includes(appointment.status) || appointment.presenca === 'FALTOU' || appointment.recebimentoConfirmado || !appointment.data || appointment.data < referenceDate) continue;
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
  if (type === 'appointment' && appointmentActionsLocked(item)) { notify('Atendimento com presença ou recebimento registrado não pode ser alterado.'); return; }
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
  const values = type === 'client' ? item : { ...item, data: item.data || $('daily-agenda-date').value || studioNowContext()?.date || '', horario: item.horario?.slice(0,5), status: item.status || 'AGENDADA', duracaoMinutos: item.duracaoMinutos || 60, profissionalId: item.profissional?.id || state.professionals.find((p) => p.ativo)?.id };
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
      if (body.data) $('daily-agenda-date').value = body.data;
      setView('overview');
    }
  } catch (error) {
    if (type === 'appointment' && error.status === 409) {
      await loadData();
      if (id) {
        $('editor').close();
        state.edit = null;
        notify(error.message || 'A marcação mudou. A agenda foi atualizada; abra a edição novamente.');
      } else $('editor-error').textContent = error.message || 'O horário mudou. A agenda foi atualizada; escolha outro horário.';
    } else $('editor-error').textContent = error.message;
  } finally {
    buttons.forEach((key) => { $(key).disabled = false; });
  }
});

function askDelete(type, id) {
  const item = (type === 'client' ? state.clients : state.appointments).find((entry) => entry.id === id);
  if (type === 'appointment' && appointmentActionsLocked(item)) { notify('Atendimento com presença ou recebimento registrado não pode ser excluído.'); return; }
  state.deletion = { type, id };
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
    if (type === 'appointment' && error.status === 409) {
      await loadData();
      $('confirmation').close();
      state.deletion = null;
      notify(error.message || 'A marcação mudou. A agenda foi atualizada.');
    } else $('delete-error').textContent = error.message;
  } finally {
    $('confirm-delete').disabled = $('cancel-delete').disabled = false;
  }
});

document.addEventListener('click', (event) => {
  const button = event.target.closest('button');
  if (!button) return;
  if (button.dataset.view) {
    const selectedFromMenu = $("mobile-more-navigation").contains(button);
    setView(button.dataset.view);
    closeMobileDockMenu(selectedFromMenu);
  }
  if (button.dataset.create) openEditor(button.dataset.create);
  for (const type of ['Client', 'Appointment']) {
    if (button.dataset[`edit${type}`]) openEditor(type.toLowerCase(), Number(button.dataset[`edit${type}`]));
    if (button.dataset[`delete${type}`]) askDelete(type.toLowerCase(), Number(button.dataset[`delete${type}`]));
  }
});
$('logout').onclick = async () => { try { await api('/auth/logout', { method: 'POST' }); } catch {} finally { logout(); } };
$('main-action').onclick = () => openEditor(state.view === 'clients' ? 'client' : 'appointment');
$('see-agenda').onclick = () => unifiedAgendaTools?.reveal();
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
function moveDailyAgendaDate(delta) {
  const input = $('daily-agenda-date');
  if (!input.value) return;
  input.value = shiftCivilDate(input.value, delta);
  render();
}
$('daily-agenda-date').addEventListener('input', render);
$('daily-agenda-date').addEventListener('change', render);
$('daily-agenda-previous').onclick = () => moveDailyAgendaDate(-1);
$('daily-agenda-today').onclick = () => {
  const today = studioNowContext();
  if (!today) return;
  $('daily-agenda-date').value = today.date;
  render();
};
$('daily-agenda-next').onclick = () => moveDailyAgendaDate(1);
$('daily-agenda-professional').addEventListener('change', render);
$('today-appointments-details').addEventListener('toggle', () => unifiedAgendaTools?.sync());
window.matchMedia('(max-width: 700px)').addEventListener?.('change', (event) => {
  dailyAgendaPixelsPerMinute = event.matches ? 1.35 : 1.05;
  applyMobileNavigationLayout();
  render();
});
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
