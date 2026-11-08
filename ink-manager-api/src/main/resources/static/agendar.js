'use strict';
(() => {
  const byId = id => document.getElementById(id);
  const form = byId('booking-form');
  const fields = form.elements;
  const local = new URLSearchParams(location.search).get('local') || location.pathname.match(/^\/a\/([A-Za-z0-9_-]+)\/?$/)?.[1];
  const base = local ? '/publico/' + encodeURIComponent(local) : '';
  const storageKey = 'marcae.reserva.' + (local || '');
  const money = value => Number(value).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  const civilDateParts = value => {
    const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(String(value || ''));
    return match ? { year: Number(match[1]), month: Number(match[2]), day: Number(match[3]) } : null;
  };
  const civilDaysInMonth = (year, month) => [31, (year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0)) ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31][month - 1] || 0;
  function shiftCivilDate(value, delta) {
    const parts = civilDateParts(value);
    if (!parts || !Number.isInteger(delta)) return '';
    let { year, month, day } = parts;
    for (let step = 0; step < Math.abs(delta); step++) {
      if (delta > 0) {
        day++;
        if (day > civilDaysInMonth(year, month)) { day = 1; month++; if (month > 12) { month = 1; year++; } }
      } else {
        day--;
        if (day < 1) { month--; if (month < 1) { month = 12; year--; } day = civilDaysInMonth(year, month); }
      }
    }
    return String(year).padStart(4, '0') + '-' + String(month).padStart(2, '0') + '-' + String(day).padStart(2, '0');
  }
  const dateLabel = value => {
    const parts = civilDateParts(value);
    return parts ? new Date(parts.year, parts.month - 1, parts.day, 12).toLocaleDateString('pt-BR') : '';
  };
  let catalog, slotsReady = false, requestNumber = 0, busy = false, finished = false, pending = null;

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

  document.addEventListener('input', event => {
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

  try {
    const saved = JSON.parse(sessionStorage.getItem(storageKey) || 'null');
    if (saved?.chave && saved?.servicoId && saved?.profissionalId && saved?.data && saved?.horario && saved?.nome && saved?.telefone) pending = saved;
  } catch { /* Storage may be unavailable; in-memory retries still use the same UUID. */ }
  function persist() {
    try { if (pending) sessionStorage.setItem(storageKey, JSON.stringify(pending)); else sessionStorage.removeItem(storageKey); } catch {}
  }
  async function request(path, body) {
    let response;
    try {
      response = await fetch(base + path, { method: body ? 'POST' : 'GET', credentials: 'omit', cache: 'no-store',
        headers: body ? { 'Content-Type': 'application/json' } : {}, body: body ? JSON.stringify(body) : undefined,
        signal: AbortSignal.timeout(20000) });
    } catch { throw new Error('Não foi possível conectar. Confira sua conexão e tente novamente.'); }
    let data;
    try { data = await response.json(); }
    catch {
      if (response.ok) throw new Error('A confirmação não chegou completa. Tente novamente com o mesmo pedido.');
    }
    if (!response.ok) {
      const error = new Error((response.status === 409 && path.startsWith('/horarios') ? 'Agendamento temporariamente indisponível. Fale com o estabelecimento.' : data?.erro) || (response.status === 429 ? 'Muitas tentativas. Aguarde um pouco e tente novamente.' : response.status === 404 ? 'Serviço ou link indisponível. Fale com o estabelecimento.' : 'Não foi possível concluir. Confira os dados e tente novamente.'));
      error.status = response.status;
      throw error;
    }
    return data;
  }
  function enableSubmit() {
    byId('reserve-button').disabled = busy || (!pending && (!slotsReady || !fields.horario.value)) || finished;
  }
  function setPending() {
    byId('booking-fields').disabled = Boolean(pending);
    byId('pending-note').hidden = !pending;
    byId('reserve-button').textContent = busy ? 'Reservando…' : pending ? 'Tentar confirmar o mesmo pedido' : 'Reservar horário';
    enableSubmit(); syncBookingSteps();
  }
  function syncBookingSteps() {
    const steps = [...document.querySelectorAll('[data-booking-step]')];
    if (!steps.length) return;
    const service = catalog?.servicos.find(item => String(item.id) === fields.servicoId.value);
    const linkedIds = new Set(Array.isArray(service?.profissionalIds) ? service.profissionalIds.map(String) : []);
    const professional = (catalog?.profissionais || []).some(person => linkedIds.has(String(person.id)) && String(person.id) === fields.profissionalId.value);
    const date = Boolean(service && professional && fields.data.value && fields.data.checkValidity());
    const time = Boolean(date && slotsReady && fields.horario.value);
    const phone = fields.telefone.value.trim();
    const phoneValid = /^[0-9+() .-]{8,30}$/.test(phone) && phone.replace(/[^0-9]/g, '').length >= 8;
    const details = Boolean(time && fields.nome.value.trim() && fields.email.checkValidity() && phoneValid && fields.avisoPrivacidadeLido.checked);
    const completed = [Boolean(service), Boolean(service && professional), date, time, details];
    const nextIndex = completed.findIndex(value => !value);
    const currentIndex = nextIndex < 0 ? steps.length - 1 : nextIndex;
    byId('booking-contact-fields').disabled = !time;
    steps.forEach((step, index) => {
      step.dataset.complete = String(Boolean(completed[index]));
      if (index === currentIndex) step.setAttribute('aria-current', 'step');
      else step.removeAttribute('aria-current');
    });
  }
  function syncProfessionalsForService(pendingProfessionalId = null) {
    const service = catalog?.servicos.find(item => String(item.id) === fields.servicoId.value);
    const linkedIds = new Set(Array.isArray(service?.profissionalIds) ? service.profissionalIds.map(String) : []);
    const selected = fields.profissionalId.value;
    const professionals = (catalog?.profissionais || []).filter(person => linkedIds.has(String(person.id)));
    fields.profissionalId.replaceChildren(new Option(
      service ? professionals.length ? 'Selecione o profissional' : 'Nenhum profissional vinculado a este servi\u00E7o' : 'Selecione um servi\u00E7o primeiro',
      ''
    ));
    professionals.forEach(person => fields.profissionalId.add(new Option(person.nome, person.id)));
    if (pendingProfessionalId != null && !professionals.some(person => String(person.id) === String(pendingProfessionalId))) {
      const previous = catalog?.profissionais.find(person => String(person.id) === String(pendingProfessionalId));
      const label = previous ? previous.nome + ' - reserva pendente' : 'Profissional da reserva pendente';
      const option = new Option(label, String(pendingProfessionalId), true, true);
      option.disabled = true;
      fields.profissionalId.add(option);
    }
    if (selected && professionals.some(person => String(person.id) === selected)) fields.profissionalId.value = selected;
    else if (pendingProfessionalId != null) fields.profissionalId.value = String(pendingProfessionalId);
    else fields.profissionalId.value = '';
    const hasSelectedCompatibleProfessional = professionals.some(person => String(person.id) === fields.profissionalId.value);
    fields.profissionalId.disabled = !service || !professionals.length;
    fields.data.disabled = !service || !hasSelectedCompatibleProfessional;
    syncBookingSteps();
  }
  function selectionKey() { return [fields.servicoId.value, fields.profissionalId.value, fields.data.value].join('|'); }
  function summary() {
    const service = catalog?.servicos.find(item => String(item.id) === fields.servicoId.value);
    const professional = catalog?.profissionais.find(item => String(item.id) === fields.profissionalId.value);
    const detail = professional ? ' com ' + professional.nome : '';
    const when = fields.data.value && fields.horario.value ? ', em ' + dateLabel(fields.data.value) + ', às ' + fields.horario.value.slice(0, 5) : '';
    byId('service-summary').textContent = service ? service.nome + detail + when + '. Duração: ' + service.duracaoMinutos + ' min. Preço: ' + money(service.preco) + '.' : 'Escolha um serviço para consultar preço e duração.';
  }
  async function loadSlots(reset = false) {
    if (!catalog || pending || busy || finished) { syncBookingSteps(); return; }
    const key = selectionKey(), number = ++requestNumber;
    if (reset) { fields.horario.value = ''; slotsReady = false; }
    if (!fields.servicoId.value || !fields.profissionalId.value || !fields.data.value || !fields.data.checkValidity()) {
      fields.horario.replaceChildren(new Option('Escolha serviço, profissional e dia', ''));
      fields.horario.disabled = true; slotsReady = false; syncBookingSteps();
      byId('slots-status').textContent = ''; enableSubmit(); return;
    }
    if (reset) fields.horario.disabled = true;
    byId('slots-status').textContent = 'Consultando horários livres…';
    enableSubmit();
    try {
      const query = new URLSearchParams({ servicoId: fields.servicoId.value, profissionalId: fields.profissionalId.value, data: fields.data.value });
      const result = await request('/horarios?' + query);
      if (number !== requestNumber || key !== selectionKey() || pending || finished) return;
      if (!Array.isArray(result?.horarios)) throw new Error('Não foi possível consultar os horários. Tente novamente.');
      const desired = fields.horario.value;
      const times = result.horarios;
      fields.horario.replaceChildren(new Option(times.length ? 'Selecione o horário' : 'Nenhum horário livre neste dia', ''));
      times.forEach(time => fields.horario.add(new Option(time.slice(0, 5), time)));
      if (times.includes(desired)) fields.horario.value = desired;
      fields.horario.disabled = !times.length;
      slotsReady = true;
      byId('slots-status').textContent = desired && !times.includes(desired) ? 'O horário selecionado ficou indisponível. Escolha outro.' : times.length ? 'Horários atualizados. A reserva é garantida após a confirmação.' : 'Sem horários livres. Escolha outro dia ou profissional.';
    } catch (error) {
      if (number === requestNumber && key === selectionKey()) {
        slotsReady = false;
        byId('slots-status').textContent = error.message + ' Use Atualizar horários para tentar de novo.';
      }
    } finally { summary(); enableSubmit(); syncBookingSteps(); }
  }
  async function loadCatalog() {
    byId('public-loading').hidden = false;
    byId('public-error').textContent = '';
    byId('retry-catalog').hidden = true;
    try {
      if (!base) throw new Error('Este link está incompleto. Peça o link de agendamento ao estabelecimento.');
      catalog = await request('');
      if (!Array.isArray(catalog?.servicos) || !Array.isArray(catalog?.profissionais) || !/^\d{4}-\d{2}-\d{2}$/.test(catalog.hoje)) throw new Error('Não foi possível carregar este estabelecimento.');
      byId('business-name').textContent = 'Reserve seu horário em ' + catalog.nome;
      document.title = 'Agendar · ' + catalog.nome + ' — Marcaê';
      fields.servicoId.replaceChildren(new Option('Selecione o serviço', ''));
      catalog.servicos.forEach(s => fields.servicoId.add(new Option(s.nome + ' · ' + s.duracaoMinutos + ' min · ' + money(s.preco), s.id)));
      fields.profissionalId.replaceChildren(new Option('Selecione um servi\u00E7o primeiro', ''));
      if (catalog.servicos.length === 1) fields.servicoId.value = String(catalog.servicos[0].id);
      syncProfessionalsForService();
      byId('privacy-version').textContent = catalog.versaoAvisoPrivacidade ? '· Versão ' + catalog.versaoAvisoPrivacidade : '';
      byId('public-timezone').textContent = 'Horários no fuso ' + catalog.fusoHorario + '. Antecedência mínima para reservar: ' + catalog.antecedenciaMinimaMinutos + ' minutos. Atualização a cada 20 segundos.';
      fields.data.min = catalog.hoje;
      fields.data.max = shiftCivilDate(catalog.hoje, 365);
      fields.data.value = catalog.hoje;

      if ((!catalog.servicos.length || !catalog.profissionais.length) && !pending) throw new Error('Este estabelecimento ainda não tem serviços ou profissionais disponíveis. Fale com a equipe.');
      form.hidden = false;
      if (pending) {
        for (const name of ['servicoId', 'data', 'nome', 'telefone', 'email']) if (pending[name] != null && fields[name]) fields[name].value = String(pending[name]);
        syncProfessionalsForService(pending.profissionalId);
        if (pending.avisoPrivacidadeLido === true) fields.avisoPrivacidadeLido.checked = true;
        fields.horario.replaceChildren(new Option(pending.horario.slice(0, 5), pending.horario));
        fields.horario.value = pending.horario;
        summary(); setPending();
      } else { summary(); syncBookingSteps(); await loadSlots(true); }
    } catch (error) {
      byId('public-error').textContent = error.message;
      byId('retry-catalog').hidden = !base;
    } finally { byId('public-loading').hidden = true; }
  }
  fields.servicoId.addEventListener('change', () => { syncProfessionalsForService(); summary(); loadSlots(true); });
  fields.profissionalId.addEventListener('change', () => { fields.data.disabled = !fields.servicoId.value || !fields.profissionalId.value; syncBookingSteps(); summary(); loadSlots(true); });
  fields.data.addEventListener('change', () => { syncBookingSteps(); summary(); loadSlots(true); });
  for (const name of ['nome', 'email', 'telefone']) fields[name].addEventListener('input', syncBookingSteps);
  fields.avisoPrivacidadeLido.addEventListener('change', syncBookingSteps);
  fields.telefone.addEventListener('input', () => {
    const value = fields.telefone.value.trim();
    const valid = !value || (/^[0-9+() .-]{8,30}$/.test(value) && value.replace(/[^0-9]/g, '').length >= 8);
    fields.telefone.setCustomValidity(valid ? '' : 'Informe um telefone com pelo menos 8 dígitos.');
  });
  fields.horario.addEventListener('change', () => { summary(); enableSubmit(); syncBookingSteps(); });
  byId('refresh-slots').onclick = () => loadSlots();
  byId('retry-catalog').onclick = loadCatalog;
  form.addEventListener('submit', async event => {
    event.preventDefault();
    if (busy || finished || (!pending && (!slotsReady || !fields.horario.value))) return;
    byId('booking-error').textContent = '';
    if (!pending) {
      pending = { chave: crypto.randomUUID(), servicoId: Number(fields.servicoId.value), profissionalId: Number(fields.profissionalId.value),
        data: fields.data.value, horario: fields.horario.value, nome: fields.nome.value.trim(), telefone: fields.telefone.value.trim(), email: fields.email.value.trim(), avisoPrivacidadeLido: fields.avisoPrivacidadeLido.checked };
      if (!pending.nome || !pending.email || !fields.email.checkValidity() || !pending.avisoPrivacidadeLido || (!/^[0-9+() .-]{8,30}$/.test(pending.telefone) || pending.telefone.replace(/[^0-9]/g, '').length < 8)) { pending = null; byId('booking-error').textContent = 'Informe seu nome, um email válido, telefone com pelo menos 8 dígitos e leia o aviso de privacidade.'; return; }
      persist();
    }
    busy = true; setPending();
    try {
      const result = await request('/reservas', pending);
      if (!result?.protocolo || !result.servico || !result.profissional || !result.data || !result.horario || !result.fusoHorario || result.preco == null || !Number.isFinite(Number(result.preco)) || !Number.isInteger(result.duracaoMinutos) || result.duracaoMinutos < 1) throw new Error('A confirmação não chegou completa. Tente confirmar o mesmo pedido.');
      finished = true; pending = null; persist(); syncBookingSteps();
      const details = byId('confirmation-details'); details.replaceChildren();
      const lines = { Protocolo: result.protocolo, Serviço: result.servico, Profissional: result.profissional,
        Dia: dateLabel(result.data), Horário: result.horario.slice(0, 5), Fuso: result.fusoHorario, Duração: result.duracaoMinutos + ' min', Preço: money(result.preco) };
      for (const [label, value] of Object.entries(lines)) {
        const dt = document.createElement('dt'), dd = document.createElement('dd'); dt.textContent = label; dd.textContent = value; details.append(dt, dd);
      }
      form.hidden = true; byId('booking-confirmation').hidden = false; byId('booking-confirmation').focus();
    } catch (error) {
      byId('booking-error').textContent = error.message;
      if (error.status && error.status !== 429 && error.status < 500) { pending = null; persist(); }
    } finally {
      busy = false; setPending();
      if (!pending && !finished) await loadSlots();
    }
  });
  byId('another-booking').onclick = () => {
    finished = false; form.reset(); byId('booking-confirmation').hidden = true; byId('booking-error').textContent = '';
    slotsReady = false; fields.profissionalId.disabled = true; fields.data.disabled = true; setPending(); syncBookingSteps(); loadCatalog();
  };
  setInterval(() => { if (!document.hidden) loadSlots(); }, 20000);
  document.addEventListener('visibilitychange', () => { if (!document.hidden) loadSlots(); });
  const offerHash = new URLSearchParams(location.hash.replace(/^#/, ''));
  if (offerHash.has('token') || offerHash.has('oferta')) {
    byId('public-loading').hidden = true;
  } else loadCatalog();
})();
