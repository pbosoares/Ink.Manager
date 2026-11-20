'use strict';
// Visão geral: agenda do dia por profissional, próximos atendimentos e primeiros passos.
// Uses the data app.js already loads (state.appointments / state.professionals); the only
// extra requests are the owner's service catalog and booking link for the first steps.
(() => {
  const PX_PER_MIN = 1.1;           // 66px per hour
  const COMPACT_FROM = 4;           // professionals per day that switch cards to compact mode
  const DEFAULT_START = 8 * 60;
  const DEFAULT_END = 18 * 60;
  let day = localDate();
  let setup = { generation: -1, loading: false, services: null, link: '', at: 0 };

  const owner = () => state.user?.perfil === 'PROPRIETARIO';
  const toMinutes = (time) => { const [h, m] = String(time || '0:0').split(':').map(Number); return h * 60 + m; };
  const clock = (minutes) => `${String(Math.floor(minutes / 60)).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')}`;
  const nowMinutes = () => { const now = new Date(); return now.getHours() * 60 + now.getMinutes(); };
  const initials = (name) => String(name || '?').trim().split(/\s+/).slice(0, 2).map((part) => part[0]).join('').toUpperCase();
  const plural = (count, one, many) => `${count} ${count === 1 ? one : many}`;
  const shiftDay = (value, days) => { const date = new Date(`${value}T12:00:00`); date.setDate(date.getDate() + days); return localDate(date); };
  const dayLabel = (value) => new Date(`${value}T12:00:00`).toLocaleDateString('pt-BR', { weekday: 'long', day: 'numeric', month: 'long' });

  // Where an appointment stands right now. "pending" needs the owner to record the amount.
  function phase(item) {
    const today = localDate();
    const start = toMinutes(item.horario);
    const end = start + Number(item.duracaoMinutos || 0);
    const now = item.data < today ? Infinity : item.data > today ? -Infinity : nowMinutes();
    const canRecord = owner() && item.podeConfirmarRecebimento && !item.recebimentoConfirmado;
    if (item.recebimentoConfirmado) return { key: 'done', label: 'Recebido' };
    if (item.status === 'CONCLUIDA') return canRecord ? { key: 'pending', label: 'Concluída' } : { key: 'done', label: 'Concluída' };
    if (end <= now) return canRecord ? { key: 'pending', label: 'Horário encerrado' } : { key: 'ended', label: 'Horário encerrado' };
    if (start <= now) return { key: 'live', label: 'Em atendimento' };
    return item.status === 'CONFIRMADA' ? { key: 'confirmed', label: 'Confirmada' } : { key: 'scheduled', label: 'Agendada' };
  }

  function card(item, range, compact) {
    const start = toMinutes(item.horario);
    const duration = Math.max(15, Number(item.duracaoMinutos || 0));
    const top = (start - range.start) * PX_PER_MIN;
    const height = Math.max(26, duration * PX_PER_MIN - 4);
    const short = height < 46;
    const current = phase(item);
    const service = item.servicoNome || item.descricao || 'Atendimento';
    const who = `<strong>${escapeHtml(item.cliente?.nome)}</strong> · ${escapeHtml(service)}`;
    const fullTime = `${clock(start)}–${clock(start + Number(item.duracaoMinutos || 0))}`;
    // Short cards are a single line: the start time alone leaves room for the client's name.
    const time = short ? clock(start) : fullTime;
    const action = current.key === 'pending'
      ? `<button type="button" class="day-card-action" data-confirm-receipt="${Number(item.id)}">${short || compact ? 'Registrar' : 'Registrar valor'}</button>`
      : `<span class="day-card-status">${current.label}</span>`;
    const open = item.recebimentoConfirmado
      ? `<span class="day-card-who">${who}</span>`
      : `<button type="button" class="day-card-who" data-edit-appointment="${Number(item.id)}" aria-label="Editar marcação de ${escapeHtml(item.cliente?.nome)}, ${fullTime}">${who}</button>`;
    return `<article class="day-card is-${current.key}${short ? ' is-short' : ''}" style="top:${top}px;height:${height}px" title="${escapeHtml(`${fullTime} ·${item.cliente?.nome || ''} · ${service} · ${current.label}`)}">
      <div class="day-card-head"><span class="day-card-time">${time}</span>${action}</div>${open}</article>`;
  }

  function renderBoard() {
    const target = $('today-list');
    const items = state.appointments.filter((item) => item.data === day && item.status !== 'CANCELADA');
    const ids = new Set(items.map((item) => item.profissional?.id));
    const professionals = state.professionals.filter((pro) => pro.ativo || ids.has(pro.id));
    let start = DEFAULT_START;
    let end = DEFAULT_END;
    items.forEach((item) => {
      const begin = toMinutes(item.horario);
      start = Math.min(start, Math.floor(begin / 60) * 60);
      end = Math.max(end, Math.ceil((begin + Number(item.duracaoMinutos || 0)) / 60) * 60);
    });
    const range = { start, end };
    const height = (end - start) * PX_PER_MIN;
    const compact = professionals.length >= COMPACT_FROM;
    const isToday = day === localDate();
    const now = nowMinutes();
    const nowLine = isToday && now >= start && now <= end
      ? `<div class="day-now" style="top:${(now - start) * PX_PER_MIN}px" aria-hidden="true"></div>` : '';
    const hours = [];
    for (let minute = start; minute <= end; minute += 60) hours.push(`<span style="top:${(minute - start) * PX_PER_MIN}px">${clock(minute)}</span>`);
    const head = `<div class="day-head"><div><h2>Agenda da equipe</h2><p>${plural(items.length, 'atendimento', 'atendimentos')} · ${plural(professionals.length, 'profissional', 'profissionais')}</p></div>
      <div class="day-nav" role="group" aria-label="Escolher dia"><button type="button" class="day-nav-step" data-day-shift="-1" aria-label="Dia anterior">‹</button><strong>${escapeHtml(dayLabel(day))}</strong><button type="button" class="day-nav-step" data-day-shift="1" aria-label="Próximo dia">›</button><button type="button" class="day-nav-today" data-day-today ${isToday ? 'disabled' : ''}>Hoje</button></div></div>`;
    if (!professionals.length) {
      target.innerHTML = head + empty('Nenhum profissional ativo.', 'Cadastre a equipe e os horários para montar a agenda do dia.', '');
      return;
    }
    const columns = professionals.map((pro) => {
      const own = items.filter((item) => item.profissional?.id === pro.id);
      return `<section class="day-col" aria-label="Agenda de ${escapeHtml(pro.nome)}"><header class="day-col-head"><span class="day-avatar" aria-hidden="true">${escapeHtml(initials(pro.nome))}</span><span><strong>${escapeHtml(pro.nome)}</strong><small>${plural(own.length, 'atendimento', 'atendimentos')}</small></span></header>
        <div class="day-track" style="height:${height}px">${nowLine}${own.map((item) => card(item, range, compact)).join('')}</div></section>`;
    }).join('');
    const emptyNote = items.length ? '' : `<p class="day-empty">Nenhum atendimento ${isToday ? 'marcado para hoje' : 'neste dia'}.</p>`;
    target.innerHTML = `${head}${emptyNote}<div class="day-board${compact ? ' is-compact' : ''}" style="--day-cols:${professionals.length}">
      <div class="day-times" style="height:${height}px" aria-hidden="true">${hours.join('')}</div><div class="day-cols">${columns}</div></div>`;
  }

  function renderNextUp() {
    const target = $('next-up');
    const today = localDate();
    const now = nowMinutes();
    const next = state.appointments
      .filter((item) => item.data === today && ['AGENDADA', 'CONFIRMADA'].includes(item.status) && toMinutes(item.horario) > now)
      .sort((a, b) => a.horario.localeCompare(b.horario));
    if (!next.length) {
      target.innerHTML = '<div><strong>Sem mais atendimentos hoje.</strong><span>Os próximos aparecem aqui assim que forem marcados.</span></div><button type="button" class="text-button" data-view="agenda">Abrir agenda ›</button>';
      return;
    }
    const first = next.filter((item) => item.horario === next[0].horario);
    const names = first.slice(0, 3).map((item) => `${item.cliente?.nome || 'Cliente'} com ${(item.profissional?.nome || '').split(' ')[0]}`).join(' · ');
    const service = first.length === 1 ? ` · ${first[0].servicoNome || first[0].descricao || 'Atendimento'}` : '';
    const more = first.length > 3 ? ` e mais ${first.length - 3}` : '';
    target.innerHTML = `<div><strong>A seguir · ${escapeHtml(first[0].horario.slice(0, 5))}${first.length > 1 ? ` · ${first.length} atendimentos` : ''}</strong><span>${escapeHtml(names + service + more)}</span></div><button type="button" class="text-button" data-view="agenda">Ver na agenda ›</button>`;
  }

  function renderStats() {
    const today = localDate();
    const now = nowMinutes();
    const todays = state.appointments.filter((item) => item.data === today && item.status !== 'CANCELADA');
    const upcoming = todays.filter((item) => ['AGENDADA', 'CONFIRMADA'].includes(item.status) && toMinutes(item.horario) > now);
    const done = todays.filter((item) => item.status === 'CONCLUIDA' || item.recebimentoConfirmado);
    const pending = state.appointments.filter((item) => phase(item).key === 'pending');
    $('stat-today').textContent = String(todays.length).padStart(2, '0');
    $('stat-upcoming').textContent = String(upcoming.length).padStart(2, '0');
    $('stat-done').textContent = String(done.length).padStart(2, '0');
    const note = $('stat-done-note');
    note.textContent = pending.length ? `${plural(pending.length, 'atendimento', 'atendimentos')} sem valor registrado` : 'Atendimentos concluídos hoje';
    note.classList.toggle('stat-alert', pending.length > 0);
  }

  // Refreshed at most every 30s so a service registered in "Meu negócio" shows up on return.
  async function loadSetup() {
    if (!owner()) return;
    const generation = state.generation;
    if (setup.generation !== generation) setup = { generation, loading: false, services: null, link: '', at: 0 };
    if (setup.loading || Date.now() - setup.at < 30000) return;
    setup.loading = true;
    const [services, link] = await Promise.allSettled([api('/servicos'), api('/servicos/link')]);
    if (generation !== state.generation) return;
    setup.at = Date.now();
    if (services.status === 'fulfilled' && Array.isArray(services.value)) setup.services = services.value;
    if (link.status === 'fulfilled') {
      try {
        const url = new URL(link.value.caminhoCurto || link.value.caminho, location.origin);
        if (url.origin === location.origin) setup.link = url.href;
      } catch { setup.link = ''; }
    }
    setup.loading = false;
    render();
  }

  function renderFirstSteps() {
    const target = $('first-steps');
    if (!owner()) { target.hidden = true; return; }
    loadSetup();
    if (setup.generation !== state.generation || setup.services === null) { target.hidden = true; return; }
    const steps = [
      { done: setup.services.length > 0, title: 'Cadastre seus serviços', text: setup.services.length ? plural(setup.services.length, 'serviço cadastrado', 'serviços cadastrados') + ' com preço e duração.' : 'Nome, preço e duração. É o que seu cliente escolhe na reserva.', action: '<button type="button" class="secondary" data-view="studio">Cadastrar serviços</button>' },
      { done: state.professionals.some((pro) => pro.ativo && pro.horarios?.length), title: 'Defina os horários da equipe', text: 'Expediente, intervalos e folgas de cada profissional. O link só oferece horários livres.', action: '<button type="button" class="primary" data-view="team">Configurar horários</button>' },
      { done: state.appointments.length > 0, title: 'Compartilhe seu link de reservas', text: 'Coloque na bio do Instagram e mande para quem já te chama no WhatsApp.', link: true },
    ];
    const completed = steps.filter((step) => step.done).length;
    if (completed === steps.length) { target.hidden = true; return; }
    const share = setup.link
      ? `<div class="first-steps-link"><input readonly value="${escapeHtml(setup.link)}" aria-label="Seu link de reservas"><button type="button" class="secondary" data-copy-booking-link>Copiar link</button><a class="first-steps-whatsapp" href="https://wa.me/?text=${encodeURIComponent(`Agora você pode marcar seu horário comigo por aqui: ${setup.link}`)}" target="_blank" rel="noopener noreferrer">Enviar no WhatsApp</a></div>`
      : '<button type="button" class="secondary" data-view="studio">Ver meu link</button>';
    target.hidden = false;
    target.innerHTML = `<div class="first-steps-head"><span class="eyebrow accent">PRIMEIROS PASSOS</span><h2>Sua agenda está quase pronta para receber reservas.</h2>
      <div class="first-steps-progress"><span><i style="width:${Math.round(completed / steps.length * 100)}%"></i></span><small>${completed} de ${steps.length} concluído${completed === 1 ? '' : 's'}</small></div></div>
      <ol class="first-steps-list">${steps.map((step, index) => `<li class="${step.done ? 'is-done' : ''}"><span class="first-steps-mark" aria-hidden="true">${step.done ? '✓' : index + 1}</span><div><strong>${step.title}</strong><p>${escapeHtml(step.text)}</p>${!step.done && step.link ? share : ''}</div>${!step.done && !step.link ? step.action : ''}</li>`).join('')}</ol>`;
  }

  function render() {
    if (!state.loaded) return;
    renderStats();
    renderFirstSteps();
    // Before the first booking ever, the checklist is the whole page: an empty grid only distracts.
    const onboarding = !$('first-steps').hidden && state.appointments.length === 0;
    $('today-list').closest('.team-day').hidden = onboarding;
    $('next-up').hidden = onboarding;
    if (onboarding) return;
    renderBoard();
    renderNextUp();
  }

  document.addEventListener('click', async (event) => {
    const button = event.target.closest('button');
    if (!button || !state.loaded) return;
    if (button.dataset.dayShift) { day = shiftDay(day, Number(button.dataset.dayShift)); renderBoard(); }
    if (button.hasAttribute('data-day-today')) { day = localDate(); renderBoard(); }
    if (button.hasAttribute('data-copy-booking-link') && setup.link) {
      try { await navigator.clipboard.writeText(setup.link); notify('Link copiado.'); }
      catch { notify('Não foi possível copiar. Selecione o link e copie.'); }
    }
  });

  // Keep "em atendimento", the now line and "a seguir" in step with the clock.
  setInterval(() => { if (state.loaded && state.view === 'overview' && !document.hidden) render(); }, 60000);

  window.inkOverview = { render, close() { day = localDate(); setup = { generation: -1, loading: false, services: null, link: '', at: 0 }; } };
})();
