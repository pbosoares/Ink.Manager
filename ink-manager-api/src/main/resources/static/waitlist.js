'use strict';
(() => {
  const $ = id => document.getElementById(id);
  const escapeText = value => String(value ?? '');
  const publicLocal = new URLSearchParams(location.search).get('local') || '';
  const publicBase = publicLocal ? '/publico/' + encodeURIComponent(publicLocal) : '';
  const storageKey = 'marcae.lista.' + publicLocal;
  const form = $('waitlist-form');
  const panel = $('waitlist-panel');
  const offerPanel = $('offer-panel');
  let catalog = null, catalogBusy = false, pending = null, sending = false, waitlistDone = false;
  let offerToken = new URLSearchParams(location.hash.replace(/^#/, '')).get('token') || new URLSearchParams(location.hash.replace(/^#/, '')).get('oferta');
  try {
    const saved = JSON.parse(sessionStorage.getItem(storageKey) || 'null');
    if (saved?.chaveIdempotencia && saved?.servicoId && saved?.inicio && saved?.fim && saved?.nome && saved?.email) pending = saved;
  } catch {}
  if (offerToken) history.replaceState(null, '', location.pathname + location.search);

  async function publicRequest(path, body) {
    let response;
    try {
      response = await fetch(publicBase + path, {
        method: body === undefined ? 'GET' : 'POST',
        credentials: 'omit',
        cache: 'no-store',
        headers: body === undefined ? {} : { 'Content-Type': 'application/json' },
        body: body === undefined ? undefined : JSON.stringify(body),
        signal: AbortSignal.timeout(20000)
      });
    } catch { throw new Error('N\u00e3o foi poss\u00edvel conectar. Confira sua conex\u00e3o e tente novamente.'); }
    const data = await response.json().catch(() => null);
    if (!response.ok) {
      const error = new Error(data?.erro || (response.status === 410 ? 'Esta oferta expirou ou foi encerrada.' : 'N\u00e3o foi poss\u00edvel concluir. Confira o link e tente novamente.'));
      error.status = response.status;
      throw error;
    }
    return data;
  }
  function localTimeAsInstant(value, zone) {
    const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})$/.exec(value);
    if (!match) throw new Error('Informe o in\u00edcio e o fim do per\u00edodo.');
    const target = Date.UTC(Number(match[1]), Number(match[2]) - 1, Number(match[3]), Number(match[4]), Number(match[5]));
    const formatter = new Intl.DateTimeFormat('en-CA', { timeZone: zone, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23' });
    let instant = target;
    for (let attempt = 0; attempt < 4; attempt++) {
      const parts = Object.fromEntries(formatter.formatToParts(new Date(instant)).filter(part => part.type !== 'literal').map(part => [part.type, Number(part.value)]));
      const actual = Date.UTC(parts.year, parts.month - 1, parts.day, parts.hour, parts.minute, parts.second);
      const difference = target - actual;
      if (!difference) break;
      instant += difference;
    }
    const check = Object.fromEntries(formatter.formatToParts(new Date(instant)).filter(part => part.type !== 'literal').map(part => [part.type, Number(part.value)]));
    if (Date.UTC(check.year, check.month - 1, check.day, check.hour, check.minute, 0) !== target) throw new Error('Esse hor\u00e1rio local n\u00e3o existe por causa da mudan\u00e7a de fuso. Escolha outro.');
    return new Date(instant).toISOString();
  }
  function localInputValue(epoch, zone) {
    const parts = Object.fromEntries(new Intl.DateTimeFormat('en-CA', { timeZone: zone, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }).formatToParts(new Date(epoch)).filter(part => part.type !== 'literal').map(part => [part.type, part.value]));
    return parts.year + '-' + parts.month + '-' + parts.day + 'T' + parts.hour + ':' + parts.minute;
  }
  function persistPending() {
    try { if (pending) sessionStorage.setItem(storageKey, JSON.stringify(pending)); else sessionStorage.removeItem(storageKey); } catch {}
  }
  function setPending() {
    $('waitlist-fields').disabled = waitlistDone || Boolean(pending) || sending || !catalog;
    $('waitlist-pending').hidden = !pending;
    $('waitlist-submit').disabled = waitlistDone || sending || !catalog;
    $('waitlist-submit').textContent = waitlistDone ? 'Pedido recebido' : sending ? 'Enviando...' : pending ? 'Tentar confirmar o mesmo pedido' : 'Entrar na lista de espera';
  }
  async function loadCatalog() {
    if (catalog || catalogBusy || !publicBase) return;
    catalogBusy = true;
    $('waitlist-catalog-status').textContent = 'Carregando servi\u00e7os e profissionais...';
    try {
      const response = await fetch(publicBase, { credentials: 'omit', cache: 'no-store', signal: AbortSignal.timeout(20000) });
      const result = await response.json().catch(() => null);
      if (!response.ok) throw new Error(result?.erro || 'N\u00e3o foi poss\u00edvel consultar este estabelecimento.');
      if (!Array.isArray(result?.servicos) || !Array.isArray(result?.profissionais) || !result.fusoHorario) throw new Error('O cat\u00e1logo n\u00e3o retornou as op\u00e7\u00f5es esperadas.');
      catalog = result;
      const service = $('waitlist-service'), professional = $('waitlist-professional');
      service.replaceChildren(new Option('Selecione o servi\u00e7o', ''));
      result.servicos.forEach(item => service.add(new Option(item.nome, item.id)));
      professional.replaceChildren(new Option('Qualquer profissional', ''));
      result.profissionais.forEach(item => professional.add(new Option(item.nome, item.id)));
      $('waitlist-privacy-version').textContent = result.versaoAvisoPrivacidade ? ' - Versao ' + result.versaoAvisoPrivacidade : '';
      if (pending) {
        const fields = form.elements;
        fields.servicoId.value = String(pending.servicoId);
        fields.profissionalId.value = pending.profissionalId == null ? '' : String(pending.profissionalId);
        fields.inicio.value = localInputValue(Date.parse(pending.inicio), result.fusoHorario);
        fields.fim.value = localInputValue(Date.parse(pending.fim), result.fusoHorario);
        fields.nome.value = pending.nome; fields.email.value = pending.email; fields.telefone.value = pending.telefone;
        fields.avisoPrivacidadeLido.checked = true;
      } else {
        const now = Math.ceil((Date.now() + Number(result.antecedenciaMinimaMinutos || 0) * 60000) / 60000) * 60000;
        const end = now + 60 * 60000;
        form.elements.inicio.value = localInputValue(now, result.fusoHorario);
        form.elements.fim.value = localInputValue(end, result.fusoHorario);
      }
      $('waitlist-catalog-status').textContent = 'Hor\u00e1rios no fuso ' + result.fusoHorario + '. Escolha um periodo de ate 90 dias.';
      setPending();
    } catch (error) { $('waitlist-catalog-status').textContent = error.message; }
    finally { catalogBusy = false; }
  }
  if (panel && !offerToken && publicBase) {
    panel.hidden = false;
    panel.addEventListener('toggle', () => { if (panel.open) loadCatalog(); });
  }
  if (form) form.addEventListener('submit', async event => {
    event.preventDefault();
    if (sending || waitlistDone || !catalog) return;
    $('waitlist-error').textContent = '';
    if (!pending) {
      const fields = form.elements;
      const nome = fields.nome.value.trim(), email = fields.email.value.trim(), telefone = fields.telefone.value.trim();
      if (!nome || !email || !fields.email.checkValidity() || !fields.avisoPrivacidadeLido.checked || !(/^[0-9+() .-]{8,30}$/.test(telefone) && telefone.replace(/[^0-9]/g, '').length >= 8)) {
        $('waitlist-error').textContent = 'Informe nome, e-mail v\u00e1lido, telefone com pelo menos 8 d\u00edgitos e leia o aviso de privacidade.';
        return;
      }
      let inicio, fim;
      try { inicio = localTimeAsInstant(fields.inicio.value, catalog.fusoHorario); fim = localTimeAsInstant(fields.fim.value, catalog.fusoHorario); }
      catch (error) { $('waitlist-error').textContent = error.message; return; }
      const start = Date.parse(inicio), finish = Date.parse(fim);
      if (finish <= start || finish - start > 90 * 24 * 60 * 60 * 1000) {
        $('waitlist-error').textContent = 'Escolha um per\u00edodo futuro de at\u00e9 90 dias, com fim depois do in\u00edcio.';
        return;
      }
      if (start < Date.now() + Number(catalog.antecedenciaMinimaMinutos || 0) * 60000 || start > Date.now() + 365 * 24 * 60 * 60 * 1000) {
        $('waitlist-error').textContent = start > Date.now() + 365 * 24 * 60 * 60 * 1000 ? 'O in\u00edcio deve estar dentro do pr\u00f3ximo ano.' : 'O in\u00edcio precisa respeitar a anteced\u00eancia m\u00ednima do estabelecimento.';
        return;
      }
      pending = { chaveIdempotencia: crypto.randomUUID(), servicoId: Number(fields.servicoId.value), inicio, fim, nome, email, telefone, avisoPrivacidadeLido: true };
      if (fields.profissionalId.value) pending.profissionalId = Number(fields.profissionalId.value);
      persistPending();
    }
    sending = true; setPending();
    try {
      const result = await publicRequest('/lista-espera', pending);
      if (result?.recebida !== true) throw new Error('N\u00e3o foi poss\u00edvel confirmar sua entrada. Tente novamente com o mesmo pedido.');
      pending = null; persistPending(); waitlistDone = true;
      $('waitlist-fields').disabled = true; $('waitlist-pending').hidden = true; $('waitlist-submit').disabled = true;
      $('waitlist-success').textContent = 'Seu pedido entrou na lista. O estabelecimento poder\u00e1 entrar em contato se surgir uma vaga compat\u00edvel.';
      $('waitlist-success').hidden = false;
    } catch (error) {
      $('waitlist-error').textContent = error.message;
      if (error.status && error.status !== 429 && error.status < 500) { pending = null; persistPending(); $('waitlist-fields').disabled = false; $('waitlist-pending').hidden = true; }
    } finally { sending = false; setPending(); }
  });
  function addDetails(targetId, entries) {
    const list = $(targetId); list.replaceChildren();
    entries.forEach(entry => {
      const term = document.createElement('dt'), description = document.createElement('dd');
      term.textContent = entry[0]; description.textContent = String(entry[1]); list.append(term, description);
    });
  }
  function dateLabel(value) { return new Date(value + 'T12:00:00').toLocaleDateString('pt-BR'); }
  function money(value) { return Number(value).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' }); }
  function deadlineLabel(value, zone) {
    try { return new Date(value).toLocaleString('pt-BR', { timeZone: zone, dateStyle: 'short', timeStyle: 'short' }); }
    catch { return new Date(value).toLocaleString('pt-BR'); }
  }
  async function loadOffer() {
    $('public-loading').hidden = true;
    $('booking-form').hidden = true;
    if (panel) panel.hidden = true;
    $('offer-panel').hidden = false;
    $('business-name').textContent = 'Consulte sua oferta de hor\u00e1rio';
    if (!publicBase || !offerToken) { $('offer-error').textContent = 'Este link de oferta esta incompleto. Reabra a mensagem recebida do estabelecimento.'; return; }
    $('offer-loading').hidden = false;
    $('offer-retry').hidden = true; $('offer-check-accepted').hidden = true;
    try {
      const offer = await publicRequest('/oferta/visualizar', { token: offerToken });
      if (!offer?.servico || !offer?.profissional || !offer?.data || !offer?.horario || !offer?.fusoHorario || offer.preco == null || !Number.isInteger(offer.duracaoMinutos) || !offer.expiraEm) throw new Error('A oferta n\u00e3o retornou os detalhes esperados.');
      addDetails('offer-details', [['Servi\u00e7o', offer.servico], ['Profissional', offer.profissional], ['Dia', dateLabel(offer.data)], ['Hor\u00e1rio', String(offer.horario).slice(0, 5)], ['Fuso', offer.fusoHorario], ['Dura\u00e7\u00e3o', offer.duracaoMinutos + ' min'], ['Pre\u00e7o', money(offer.preco)]]);
      $('offer-deadline').textContent = 'Oferta v\u00e1lida at\u00e9 ' + deadlineLabel(offer.expiraEm, offer.fusoHorario) + ' (' + offer.fusoHorario + ').';
      $('offer-details').hidden = false; $('offer-deadline').hidden = false; $('offer-accept').hidden = false;
    } catch (error) {
      $('offer-error').textContent = error.message;
      $('offer-retry').hidden = false;
      $('offer-check-accepted').hidden = error.status !== 410;
    }
    finally { $('offer-loading').hidden = true; }
  }
  if (offerToken) {
    $('public-loading').hidden = true;
    $('public-error').textContent = '';
    $('retry-catalog').hidden = true;
    $('booking-form').hidden = true;
    if (panel) panel.hidden = true;
    offerPanel.hidden = false;
    loadOffer();
  }
  $('offer-retry').onclick = loadOffer;
  $('offer-check-accepted').onclick = async () => {
    if (!offerToken || $('offer-check-accepted').disabled) return;
    $('offer-check-accepted').disabled = true; $('offer-error').textContent = '';
    try {
      const result = await publicRequest('/oferta/aceitar', { token: offerToken });
      if (!result?.reservaId || !result?.servico || !result?.profissional || !result?.data || !result?.horario || !result?.fusoHorario || result.preco == null || !Number.isInteger(result.duracaoMinutos)) throw new Error('A confirma\u00e7\u00e3o da reserva n\u00e3o chegou completa.');
      addDetails('offer-confirmation-details', [['Protocolo', result.reservaId], ['Servi\u00e7o', result.servico], ['Profissional', result.profissional], ['Dia', dateLabel(result.data)], ['Hor\u00e1rio', String(result.horario).slice(0, 5)], ['Fuso', result.fusoHorario], ['Dura\u00e7\u00e3o', result.duracaoMinutos + ' min'], ['Pre\u00e7o', money(result.preco)]]);
      offerToken = null;
      offerPanel.querySelector('.eyebrow').textContent = 'TUDO CERTO';
      $('offer-heading').hidden = true; $('offer-details').hidden = true; $('offer-deadline').hidden = true;
      $('offer-accept').hidden = true; $('offer-check-accepted').hidden = true; $('offer-retry').hidden = true;
      $('offer-confirmation').hidden = false;
    } catch (error) {
      $('offer-error').textContent = error.status === 410 ? 'A oferta expirou e n\u00e3o existe uma reserva confirmada.' : error.message;
      $('offer-check-accepted').disabled = false;
    }
  };
  $('offer-accept').onclick = async () => {
    if (!offerToken || $('offer-accept').disabled) return;
    $('offer-accept').disabled = true; $('offer-error').textContent = '';
    try {
      const result = await publicRequest('/oferta/aceitar', { token: offerToken });
      if (!result?.reservaId || !result?.servico || !result?.profissional || !result?.data || !result?.horario || !result?.fusoHorario || result.preco == null || !Number.isInteger(result.duracaoMinutos)) throw new Error('A confirma\u00e7\u00e3o da reserva n\u00e3o chegou completa.');
      addDetails('offer-confirmation-details', [['Protocolo', result.reservaId], ['Servi\u00e7o', result.servico], ['Profissional', result.profissional], ['Dia', dateLabel(result.data)], ['Hor\u00e1rio', String(result.horario).slice(0, 5)], ['Fuso', result.fusoHorario], ['Dura\u00e7\u00e3o', result.duracaoMinutos + ' min'], ['Pre\u00e7o', money(result.preco)]]);
      offerToken = null;
      offerPanel.querySelector('.eyebrow').textContent = 'TUDO CERTO';
      $('offer-heading').hidden = true; $('offer-details').hidden = true; $('offer-deadline').hidden = true;
      $('offer-accept').hidden = true; $('offer-confirmation').hidden = false;
    } catch (error) {
      $('offer-error').textContent = error.status === 410 ? 'Esta oferta expirou. Fale com o estabelecimento para consultar outra vaga.' : error.message;
      $('offer-accept').disabled = false;
    }
  };

  const adminPanel = $('waitlist-management');
  let adminKey = '', adminRequest = 0, adminRows = [];
  function displayOffset(value) { return String(value || '').replace('T', ' ').replace(/([+-]\d\d:\d\d|Z)$/, ' $1'); }
  function renderAdminList() {
    const list = $('waitlist-management-list');
    list.replaceChildren();
    if (!adminRows.length) { list.textContent = 'Nenhuma entrada na lista de espera.'; return; }
    const labels = { AGUARDANDO: 'Aguardando', OFERTADA: 'Oferta em andamento', RESERVADA: 'Reserva criada', CANCELADA: 'Cancelada', EXPIRADA: 'Expirada' };
    adminRows.forEach(item => {
      const card = document.createElement('article'); card.className = 'team-card waitlist-entry';
      const title = document.createElement('strong'); title.textContent = (item.servico || 'Servi\u00e7o') + (item.profissional ? ' / ' + item.profissional : ' / Qualquer profissional');
      const person = document.createElement('p'); person.textContent = [item.nome, item.email, item.telefone].filter(Boolean).join(' / ');
      const period = document.createElement('p'); period.textContent = 'Prefer\u00eancia: ' + displayOffset(item.inicio) + ' at\u00e9 ' + displayOffset(item.fim);
      const status = document.createElement('small'); status.textContent = labels[item.estado] || 'Estado atualizado';
      card.append(title, person, period, status);
      if (['AGUARDANDO', 'OFERTADA'].includes(item.estado)) {
        const cancel = document.createElement('button'); cancel.type = 'button'; cancel.className = 'text-button delete';
        cancel.dataset.cancelWaitlist = String(item.id); cancel.textContent = 'Cancelar entrada'; card.append(cancel);
      }
      list.append(card);
    });
  }
  async function loadAdmin(force = false) {
    const app = window.inkApp, user = app?.user?.();
    if (!user || user.perfil !== 'PROPRIETARIO' || app.view() !== 'studio') return;
    const key = String(user.id);
    if (!force && adminKey === key) return;
    adminKey = key; const number = ++adminRequest;
    $('waitlist-management-error').textContent = ''; $('waitlist-management-loading').hidden = false;
    try {
      const rows = await app.api('/lista-espera');
      if (number !== adminRequest || app.user()?.id !== user.id) return;
      if (!Array.isArray(rows)) throw new Error('Resposta invalida ao consultar a lista.');
      adminRows = rows; renderAdminList();
    } catch (error) {
      if (number === adminRequest) $('waitlist-management-error').textContent = error.message;
    } finally { if (number === adminRequest) $('waitlist-management-loading').hidden = true; }
  }
  if (adminPanel) {
    $('waitlist-reload').onclick = () => loadAdmin(true);
    $('waitlist-management-list').addEventListener('click', async event => {
      const button = event.target.closest('[data-cancel-waitlist]');
      if (!button || button.disabled) return;
      const app = window.inkApp, user = app?.user?.(); if (!user || user.perfil !== 'PROPRIETARIO') return;
      button.disabled = true; $('waitlist-management-error').textContent = '';
      try {
        await app.api('/lista-espera/' + encodeURIComponent(button.dataset.cancelWaitlist) + '/cancelar', { method: 'POST' });
        adminKey = ''; await loadAdmin(true);
      } catch (error) { $('waitlist-management-error').textContent = error.message; button.disabled = false; }
    });
  }
  window.inkWaitlist = {
    render() { loadAdmin(); },
    close() { adminKey = ''; adminRows = []; ++adminRequest; if ($('waitlist-management-list')) $('waitlist-management-list').replaceChildren(); }
  };
})();