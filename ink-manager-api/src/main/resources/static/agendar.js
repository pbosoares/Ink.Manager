'use strict';
(() => {
  const byId = id => document.getElementById(id);
  const form = byId('booking-form');
  const fields = form.elements;
  const local = new URLSearchParams(location.search).get('local') || location.pathname.match(/^\/a\/([A-Za-z0-9_-]+)\/?$/)?.[1];
  const base = local ? '/publico/' + encodeURIComponent(local) : '';
  const storageKey = 'marcae.reserva.' + (local || '');
  const money = value => Number(value).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  const dateLabel = value => new Date(value + 'T12:00:00').toLocaleDateString('pt-BR');
  let catalog, slotsReady = false, requestNumber = 0, busy = false, finished = false, pending = null;
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
    enableSubmit();
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
    if (!catalog || pending || busy || finished) return;
    const key = selectionKey(), number = ++requestNumber;
    if (reset) { fields.horario.value = ''; slotsReady = false; }
    if (!fields.servicoId.value || !fields.profissionalId.value || !fields.data.value || !fields.data.checkValidity()) {
      fields.horario.replaceChildren(new Option('Escolha serviço, profissional e dia', ''));
      fields.horario.disabled = true; slotsReady = false;
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
    } finally { summary(); enableSubmit(); }
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
      fields.profissionalId.replaceChildren(new Option('Selecione o profissional', ''));
      catalog.profissionais.forEach(p => fields.profissionalId.add(new Option(p.nome, p.id)));
      if (catalog.servicos.length === 1) fields.servicoId.value = String(catalog.servicos[0].id);
      byId('privacy-version').textContent = catalog.versaoAvisoPrivacidade ? '· Versão ' + catalog.versaoAvisoPrivacidade : '';
      byId('public-timezone').textContent = 'Horários no fuso ' + catalog.fusoHorario + '. Antecedência mínima para reservar: ' + catalog.antecedenciaMinimaMinutos + ' minutos. Atualização a cada 20 segundos.';
      if (catalog.profissionais.length === 1) fields.profissionalId.value = String(catalog.profissionais[0].id);
      fields.data.min = catalog.hoje;
      const max = new Date(catalog.hoje + 'T12:00:00Z'); max.setUTCDate(max.getUTCDate() + 365);
      fields.data.max = max.toISOString().slice(0, 10);
      fields.data.value = catalog.hoje;

      if ((!catalog.servicos.length || !catalog.profissionais.length) && !pending) throw new Error('Este estabelecimento ainda não tem serviços ou profissionais disponíveis. Fale com a equipe.');
      form.hidden = false;
      if (pending) {
        for (const name of ['servicoId', 'profissionalId', 'data', 'nome', 'telefone', 'email']) if (pending[name] != null && fields[name]) fields[name].value = String(pending[name]);
        if (pending.avisoPrivacidadeLido === true) fields.avisoPrivacidadeLido.checked = true;
        fields.horario.replaceChildren(new Option(pending.horario.slice(0, 5), pending.horario));
        fields.horario.value = pending.horario;
        summary(); setPending();
      } else { summary(); await loadSlots(true); }
    } catch (error) {
      byId('public-error').textContent = error.message;
      byId('retry-catalog').hidden = !base;
    } finally { byId('public-loading').hidden = true; }
  }
  for (const name of ['servicoId', 'profissionalId', 'data']) fields[name].addEventListener('change', () => { summary(); loadSlots(true); });
  fields.telefone.addEventListener('input', () => {
    const value = fields.telefone.value.trim();
    const valid = !value || (/^[0-9+() .-]{8,30}$/.test(value) && value.replace(/[^0-9]/g, '').length >= 8);
    fields.telefone.setCustomValidity(valid ? '' : 'Informe um telefone com pelo menos 8 dígitos.');
  });
  fields.horario.addEventListener('change', () => { summary(); enableSubmit(); });
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
      finished = true; pending = null; persist();
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
    slotsReady = false; setPending(); loadCatalog();
  };
  setInterval(() => { if (!document.hidden) loadSlots(); }, 20000);
  document.addEventListener('visibilitychange', () => { if (!document.hidden) loadSlots(); });
  const offerHash = new URLSearchParams(location.hash.replace(/^#/, ''));
  if (offerHash.has('token') || offerHash.has('oferta')) {
    byId('public-loading').hidden = true;
  } else loadCatalog();
})();
