'use strict';
// Visão geral: indicadores do dia, agenda por profissional, "a seguir" e primeiros passos.
// Usa os dados que o app.js já carrega (state.appointments / state.professionals). As únicas
// requisições extras são do proprietário: catálogo e link (primeiros passos) e recebimentos de hoje.
(() => {
  const DEFAULT_START = 9 * 60;
  const DEFAULT_END = 18 * 60;
  const COMPACT_FROM = 4;            // a partir de quantos profissionais os cards ficam compactos
  const phone = window.matchMedia('(max-width: 700px)');
  let day = localDate();
  let picked = null;                 // profissional exibido no celular
  let scrolledFor = '';              // evita reposicionar a rolagem a cada atualização
  let setup = { generation: -1, loading: false, services: null, link: '', at: 0 };
  let received = { generation: -1, loading: false, total: null, at: 0, key: '' };

  const owner = () => state.user?.perfil === 'PROPRIETARIO';
  const hourPx = () => (phone.matches ? 83 : 73);
  const toMinutes = (time) => { const [h, m] = String(time || '0:0').split(':').map(Number); return h * 60 + m; };
  const clock = (minutes) => `${String(Math.floor(minutes / 60)).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')}`;
  const nowMinutes = () => { const now = new Date(); return now.getHours() * 60 + now.getMinutes(); };
  const initials = (name) => String(name || '?').trim().split(/\s+/).slice(0, 2).map((part) => part[0]).join('').toUpperCase();
  const plural = (count, one, many) => `${count} ${count === 1 ? one : many}`;
  const two = (count) => String(count).padStart(2, '0');
  const shiftDay = (value, days) => { const date = new Date(`${value}T12:00:00`); date.setDate(date.getDate() + days); return localDate(date); };
  const dayLabel = (value) => { const text = new Date(`${value}T12:00:00`).toLocaleDateString('pt-BR', { weekday: 'long', day: 'numeric', month: 'long' }); return text.charAt(0).toUpperCase() + text.slice(1); };
  const isoWeekday = (value) => { const weekday = new Date(`${value}T12:00:00`).getDay(); return weekday === 0 ? 7 : weekday; };
  const firstName = (name) => String(name || '').trim().split(/\s+/)[0] || '';
  const brl = (value) => Number(value).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL', minimumFractionDigits: Number.isInteger(Number(value)) ? 0 : 2 });

  // Em que pé o atendimento está agora. "pending" = falta o proprietário registrar o valor.
  function phase(item) {
    const today = localDate();
    const start = toMinutes(item.horario);
    const end = start + Number(item.duracaoMinutos || 0);
    const now = item.data < today ? Infinity : item.data > today ? -Infinity : nowMinutes();
    const canRecord = owner() && item.podeConfirmarRecebimento && !item.recebimentoConfirmado;
    if (item.recebimentoConfirmado) return { key: 'done', label: 'Concluída' };
    if (item.status === 'CONCLUIDA') return canRecord ? { key: 'pending', label: 'Concluída' } : { key: 'done', label: 'Concluída' };
    if (end <= now) return canRecord ? { key: 'pending', label: 'Horário encerrado' } : { key: 'ended', label: 'Horário encerrado' };
    if (start <= now) return { key: 'live', label: 'Em atendimento' };
    return item.status === 'CONFIRMADA' ? { key: 'confirmed', label: 'Confirmada' } : { key: 'scheduled', label: 'Agendada' };
  }

  // Turnos do profissional no dia escolhido (diaSemana: 1 = segunda ... 7 = domingo).
  const shiftsOf = (pro) => (pro.horarios || [])
    .filter((turno) => Number(turno.diaSemana) === isoWeekday(day))
    .map((turno) => ({ start: toMinutes(turno.inicio), end: toMinutes(turno.fim) }))
    .sort((a, b) => a.start - b.start);

  function card(item, range, compact) {
    const px = hourPx() / 60;
    const start = toMinutes(item.horario);
    const duration = Math.max(15, Number(item.duracaoMinutos || 0));
    const top = (start - range.start) * px + 2;
    const height = Math.max(18, duration * px - 3);
    const short = height < 30;
    const current = phase(item);
    const service = item.servicoNome || item.descricao || 'Atendimento';
    const who = `<strong>${escapeHtml(item.cliente?.nome)}</strong> · ${escapeHtml(service)}`;
    const fullTime = `${clock(start)}–${clock(start + Number(item.duracaoMinutos || 0))}`;
    const action = current.key === 'pending'
      ? `<button type="button" class="day-card-action" data-confirm-receipt="${Number(item.id)}">${compact ? 'Registrar' : 'Registrar valor'}</button>`
      : `<span class="day-card-status">${current.label}</span>`;
    const open = item.recebimentoConfirmado
      ? `<span class="day-card-who">${who}</span>`
      : `<button type="button" class="day-card-who" data-edit-appointment="${Number(item.id)}" aria-label="Editar marcação de ${escapeHtml(item.cliente?.nome)}, ${fullTime}">${who}</button>`;
    return `<article class="day-card is-${current.key}${short ? ' is-short' : height < 46 ? ' is-tight' : ''}" style="top:${top}px;height:${height}px" title="${escapeHtml(`${fullTime} · ${item.cliente?.nome || ''} · ${service} · ${current.label}`)}">
      <div class="day-card-head"><span class="day-card-time">${short ? clock(start) : fullTime}</span>${action}</div>${open}</article>`;
  }

  function renderBoard() {
    const target = $('today-list');
    const px = hourPx() / 60;
    const items = state.appointments.filter((item) => item.data === day && item.status !== 'CANCELADA');
    const ids = new Set(items.map((item) => item.profissional?.id));
    const professionals = state.professionals.filter((pro) => pro.ativo || ids.has(pro.id));
    const head = `<div class="day-head"><h2>Agenda por profissional</h2><span>${plural(items.length, 'horário', 'horários')}<i aria-hidden="true">·</i>${plural(professionals.length, 'profissional', 'profissionais')}</span></div>`;
    if (!professionals.length) {
      target.innerHTML = head + empty('Nenhum profissional ativo.', 'Cadastre a equipe e os horários para montar a agenda do dia.', '');
      return;
    }
    const shifts = new Map(professionals.map((pro) => [pro.id, shiftsOf(pro)]));
    let start = Infinity;
    let end = -Infinity;
    shifts.forEach((list) => list.forEach((shift) => { start = Math.min(start, shift.start); end = Math.max(end, shift.end); }));
    items.forEach((item) => {
      const begin = toMinutes(item.horario);
      start = Math.min(start, begin);
      end = Math.max(end, begin + Number(item.duracaoMinutos || 0));
    });
    if (!Number.isFinite(start)) { start = DEFAULT_START; end = DEFAULT_END; }
    const range = { start: Math.floor(start / 60) * 60, end: Math.max(Math.ceil(end / 60) * 60, Math.floor(start / 60) * 60 + 60) };
    const height = (range.end - range.start) * px;
    const compact = professionals.length >= COMPACT_FROM;
    const isToday = day === localDate();
    const now = nowMinutes();
    const nowTop = isToday && now >= range.start && now <= range.end ? (now - range.start) * px : null;
    const nowLine = nowTop === null ? '' : `<div class="day-now" style="top:${nowTop}px" aria-hidden="true"><span>${clock(now)}</span></div>`;
    if (!professionals.some((pro) => pro.id === picked)) {
      picked = (professionals.find((pro) => ids.has(pro.id)) || professionals[0]).id;
    }
    const hours = [];
    for (let minute = range.start; minute < range.end; minute += 60) hours.push(`<span style="top:${(minute - range.start) * px}px">${clock(minute)}</span>`);
    hours.push(`<span class="is-last" style="top:${height}px">${clock(range.end)}</span>`);
    const pill = nowTop === null ? '' : `<b class="day-now-pill" style="top:${nowTop}px">${clock(now)}</b>`;
    const subtitle = (pro, own) => {
      const list = shifts.get(pro.id);
      const hoursText = !pro.ativo ? 'Fora da agenda' : list.length ? `${clock(list[0].start)}–${clock(list[list.length - 1].end)}` : 'Folga';
      return `${plural(own.length, 'atendimento', 'atendimentos')} · ${hoursText}`;
    };
    const owned = (pro) => items.filter((item) => item.profissional?.id === pro.id);
    const heads = professionals.map((pro) => `<header class="day-col-head${pro.id === picked ? ' is-picked' : ''}"><span class="day-avatar" aria-hidden="true">${escapeHtml(initials(pro.nome))}</span><span><strong>${escapeHtml(pro.nome)}</strong><small>${escapeHtml(subtitle(pro, owned(pro)))}</small></span></header>`).join('');
    const columns = professionals.map((pro) => {
      const list = shifts.get(pro.id);
      const breaks = list.slice(1).map((shift, index) => ({ start: list[index].end, end: shift.start })).filter((gap) => gap.end > gap.start)
        .map((gap) => `<div class="day-break" style="top:${(gap.start - range.start) * px + 2}px;height:${(gap.end - gap.start) * px - 4}px">${clock(gap.start)}–${clock(gap.end)} · Intervalo</div>`).join('');
      return `<section class="day-col${pro.id === picked ? ' is-picked' : ''}" aria-label="Agenda de ${escapeHtml(pro.nome)}" style="height:${height}px">${breaks}${nowLine}${owned(pro).map((item) => card(item, range, compact)).join('')}</section>`;
    }).join('');
    const pickedPro = professionals.find((pro) => pro.id === picked);
    const picker = `<label class="pro-picker"><span class="day-avatar" aria-hidden="true">${escapeHtml(initials(pickedPro.nome))}</span><span class="pro-picker-copy"><strong>${escapeHtml(pickedPro.nome)}</strong><small>${escapeHtml(subtitle(pickedPro, owned(pickedPro)))}</small></span><svg viewBox="0 0 24 24" aria-hidden="true"><path d="m6 9 6 6 6-6"/></svg><select id="pro-picker" aria-label="Profissional exibido">${professionals.map((pro) => `<option value="${pro.id}"${pro.id === picked ? ' selected' : ''}>${escapeHtml(pro.nome)}</option>`).join('')}</select></label>`;
    const emptyNote = items.length ? '' : `<p class="day-empty">Nenhum atendimento ${isToday ? 'marcado para hoje' : 'neste dia'}.</p>`;
    target.innerHTML = `${head}${picker}${emptyNote}<div class="day-scroll"><div class="day-grid${compact ? ' is-compact' : ''}" style="--day-cols:${professionals.length};--hour:${hourPx()}px">
      <div class="day-corner">HORÁRIO</div>${heads}
      <div class="day-times" style="height:${height}px" aria-hidden="true">${hours.join('')}${pill}</div>${columns}</div></div>`;
    // Ao abrir o dia, deixa o horário atual (ou o primeiro atendimento) à vista.
    const key = `${day}|${phone.matches}|${picked}`;
    if (scrolledFor !== key) {
      scrolledFor = key;
      const first = items.length ? Math.min(...items.map((item) => toMinutes(item.horario))) : range.start;
      const focus = nowTop !== null ? nowTop - hourPx() * 1.5 : (first - range.start) * px - 8;
      target.querySelector('.day-scroll').scrollTop = Math.max(0, focus);
    }
  }

  function renderNextUp() {
    const target = $('next-up');
    const today = localDate();
    const now = nowMinutes();
    const icon = '<span class="next-up-icon" aria-hidden="true">▦</span>';
    const next = state.appointments
      .filter((item) => item.data === today && ['AGENDADA', 'CONFIRMADA'].includes(item.status) && toMinutes(item.horario) > now)
      .sort((a, b) => a.horario.localeCompare(b.horario));
    if (!next.length) {
      target.innerHTML = `<div class="next-up-main">${icon}<div><strong>Sem mais atendimentos hoje.</strong><span>Os próximos aparecem aqui assim que forem marcados.</span></div></div><button type="button" class="next-up-button" data-go="agenda" aria-label="Abrir agenda"><span>Abrir agenda</span> ›</button>`;
      return;
    }
    const first = next.filter((item) => item.horario === next[0].horario);
    const time = escapeHtml(first[0].horario.slice(0, 5));
    if (first.length === 1) {
      const item = first[0];
      const waiting = item.status === 'AGENDADA' ? ' · aguarda confirmação' : ' · confirmada';
      target.innerHTML = `<div class="next-up-main">${icon}<div><strong>A seguir · ${time}</strong><span>${escapeHtml(item.cliente?.nome || 'Cliente')}<span class="next-up-extra"> · ${escapeHtml(item.servicoNome || item.descricao || 'Atendimento')}</span> · com ${escapeHtml(item.profissional?.nome || '')}<span class="next-up-extra">${waiting}</span></span></div></div><button type="button" class="next-up-button" data-edit-appointment="${Number(item.id)}" aria-label="Ver detalhes da marcação de ${escapeHtml(item.cliente?.nome)}"><span>Ver detalhes</span> ›</button>`;
      return;
    }
    const names = first.slice(0, 3).map((item) => `${item.cliente?.nome || 'Cliente'} com ${firstName(item.profissional?.nome)}`).join(' · ');
    const more = first.length > 3 ? ` e mais ${first.length - 3}` : '';
    target.innerHTML = `<div class="next-up-main">${icon}<div><strong>A seguir · ${time} · ${first.length} atendimentos</strong><span>${escapeHtml(names + more)}</span></div></div><button type="button" class="next-up-button" data-go="agenda" aria-label="Ver na agenda"><span>Ver na agenda</span> ›</button>`;
  }

  // "Recebido hoje" soma os recebimentos confirmados hoje, no fuso do negócio (somente proprietário).
  async function loadReceived() {
    if (!owner() || received.loading) return;
    const generation = state.generation;
    const key = state.appointments.filter((item) => item.recebimentoConfirmado).length;
    if (received.generation === generation && received.key === key && Date.now() - received.at < 60000) return;
    received = { ...received, generation, key, loading: true };
    try {
      const result = await api(`/recebimentos?data=${localDate()}`);
      if (generation !== state.generation) return;
      const zoneDay = (value) => new Intl.DateTimeFormat('en-CA', { timeZone: result.fusoHorario, year: 'numeric', month: '2-digit', day: '2-digit' }).format(value);
      const today = zoneDay(new Date());
      received.total = (result.recebimentos || []).filter((row) => zoneDay(new Date(row.confirmadoEm)) === today).reduce((sum, row) => sum + Number(row.valor || 0), 0);
    } catch {
      received.total = null;
    } finally {
      if (generation === state.generation) { received.loading = false; received.at = Date.now(); $('stat-received').textContent = received.total === null ? '—' : brl(received.total); }
    }
  }

  function renderStats() {
    const items = state.appointments.filter((item) => item.data === day && item.status !== 'CANCELADA');
    const toConfirm = items.filter((item) => item.status === 'AGENDADA');
    const done = items.filter((item) => item.status === 'CONCLUIDA' || item.recebimentoConfirmado);
    const pending = items.filter((item) => phase(item).key === 'pending');
    $('stat-today').textContent = two(items.length);
    $('stat-upcoming').textContent = two(toConfirm.length);
    $('stat-done').textContent = two(done.length);
    const note = $('stat-done-note');
    note.textContent = pending.length ? `${pending.length} sem valor registrado` : 'Nesta data';
    note.classList.toggle('stat-alert', pending.length > 0);
    if (owner()) {
      if (received.generation === state.generation && received.total !== null) $('stat-received').textContent = brl(received.total);
      loadReceived();
    }
  }

  // Atualizado no máximo a cada 30s para um serviço cadastrado em "Meu negócio" aparecer ao voltar.
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
      { done: setup.services.length > 0, title: 'Cadastre seus serviços', text: setup.services.length ? `${plural(setup.services.length, 'serviço cadastrado', 'serviços cadastrados')} com preço e duração.` : 'Nome, preço e duração. É o que seu cliente escolhe na reserva.', action: 'Cadastrar serviços', review: 'Ver serviços', go: 'studio' },
      { done: state.professionals.some((pro) => pro.ativo && pro.horarios?.length), title: 'Defina os horários da equipe', text: 'Expediente, intervalos e folgas de cada profissional.', action: 'Configurar horários', review: 'Ver horários', go: 'team' },
      { done: state.appointments.length > 0, title: 'Compartilhe seu link de reservas', text: 'Coloque na bio do Instagram e mande para quem já te chama no WhatsApp.', link: true },
    ];
    const completed = steps.filter((step) => step.done).length;
    if (completed === steps.length) { target.hidden = true; return; }
    const remaining = steps.length - completed;
    const lead = remaining === 1 ? 'Falta um passo rápido.' : `Faltam ${remaining === 2 ? 'dois' : 'três'} passos rápidos.`;
    const nextIndex = steps.findIndex((step) => !step.done);
    const share = setup.link
      ? `<div class="first-steps-link"><input readonly value="${escapeHtml(setup.link.replace(/^https?:\/\//, ''))}" aria-label="Seu link de reservas"><button type="button" class="secondary" data-copy-booking-link>Copiar link</button><a class="first-steps-whatsapp" href="https://wa.me/?text=${encodeURIComponent(`Agora você pode marcar seu horário comigo por aqui: ${setup.link}`)}" target="_blank" rel="noopener noreferrer"><span class="first-steps-wa-extra">Enviar no </span>WhatsApp</a></div>`
      : '<div class="first-steps-link"><button type="button" class="secondary" data-go="studio">Ver meu link</button></div>';
    const actionFor = (step, index) => {
      if (step.link) return '';
      if (step.done) return `<button type="button" class="first-steps-review" data-go="${step.go}">${step.review}</button>`;
      return `<button type="button" class="${index === nextIndex ? 'primary' : 'secondary'} first-steps-action" data-go="${step.go}">${step.action}</button>`;
    };
    const mark = (step, index) => step.done
      ? '<span class="first-steps-mark" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M5 12.5l4.5 4.5L19 7.5"/></svg></span>'
      : `<span class="first-steps-mark" aria-hidden="true">${index + 1}</span>`;
    target.hidden = false;
    target.innerHTML = `<div class="first-steps-body"><div class="first-steps-head"><span class="eyebrow accent">PRIMEIROS PASSOS</span><h2>Sua agenda está quase pronta para receber reservas.</h2><p>${lead} Depois disso, seus clientes já podem marcar sozinhos.</p></div>
      <div class="first-steps-progress"><span><i style="width:${Math.round(completed / steps.length * 100)}%"></i></span><small>${completed} de ${steps.length} concluído${completed === 1 ? '' : 's'}</small></div>
      <ol class="first-steps-list">${steps.map((step, index) => `<li class="${step.done ? 'is-done' : ''}">${mark(step, index)}<div><strong>${step.title}</strong><p>${escapeHtml(step.text)}</p>${!step.done && step.link ? share : ''}</div>${actionFor(step, index)}</li>`).join('')}</ol>
      <p class="first-steps-self">Prefere começar você mesmo? <button type="button" class="text-button" data-create="appointment">Nova marcação</button></p></div>
      <aside class="first-steps-preview"><div><strong>É assim que seu cliente reserva</strong><p>Pelo celular, sem baixar app nem criar conta.</p></div><img src="/landing/reserva-preview-mobile.svg" alt="Página de reserva do Marcaê no celular: o cliente escolhe serviço, profissional e horário" width="262" height="342" loading="lazy"><p>Prefere começar você mesmo?<button type="button" class="text-button" data-create="appointment">Criar a primeira marcação</button></p></aside>`;
  }

  function renderPicker() {
    $('day-label').textContent = dayLabel(day);
    $('day-input').value = day;
    document.querySelector('[data-day-today]').disabled = day === localDate();
  }

  function render() {
    if (!state.loaded) return;
    renderPicker();
    renderStats();
    renderFirstSteps();
    // Antes da primeira marcação, a lista de passos é a página inteira: uma grade vazia só distrai.
    const onboarding = !$('first-steps').hidden && state.appointments.length === 0;
    $('workspace').classList.toggle('is-onboarding', onboarding);
    $('overview-stats').hidden = onboarding;
    $('today-list').closest('.team-day').hidden = onboarding;
    $('next-up').hidden = onboarding;
    if (onboarding) return;
    renderBoard();
    renderNextUp();
  }

  function goTo(value) { day = value; render(); }

  document.addEventListener('click', async (event) => {
    const button = event.target.closest('button');
    if (!button || !state.loaded) return;
    if (button.dataset.dayShift) goTo(shiftDay(day, Number(button.dataset.dayShift)));
    if (button.hasAttribute('data-day-today')) goTo(localDate());
    if (button.id === 'day-picker-label') {
      const input = $('day-input');
      try { input.showPicker(); } catch { input.focus(); }
    }
    if (button.hasAttribute('data-copy-booking-link') && setup.link) {
      try { await navigator.clipboard.writeText(setup.link); notify('Link copiado.'); }
      catch { notify('Não foi possível copiar. Selecione o link e copie.'); }
    }
  });
  $('day-input').addEventListener('change', (event) => { if (event.target.value) goTo(event.target.value); });
  document.addEventListener('change', (event) => {
    if (event.target.id !== 'pro-picker') return;
    picked = Number(event.target.value);
    renderBoard();
  });
  phone.addEventListener('change', () => { if (state.loaded && state.view === 'overview') render(); });

  // Mantém "em atendimento", a linha de agora e o "a seguir" acompanhando o relógio.
  setInterval(() => { if (state.loaded && state.view === 'overview' && !document.hidden) render(); }, 60000);

  window.inkOverview = {
    render,
    close() {
      day = localDate(); picked = null; scrolledFor = '';
      setup = { generation: -1, loading: false, services: null, link: '', at: 0 };
      received = { generation: -1, loading: false, total: null, at: 0, key: '' };
      $('workspace').classList.remove('is-onboarding');
    },
  };
})();
