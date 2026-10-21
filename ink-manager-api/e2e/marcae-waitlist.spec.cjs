const { test, expect } = require('@playwright/test');
const { randomUUID } = require('node:crypto');

// Run with a local fake SMTP sink (for example, Mailpit) already listening.
// Set MAIL_ENABLED=true only in the ephemeral Playwright app process, with
// MAIL_HOST=127.0.0.1, fake MAIL_* credentials and SMTP auth/STARTTLS disabled.
// The runner and application inherit these environment values; no external host
// is accepted. The real renderer creates the link consumed by this browser flow.
test.use({ trace: 'off' }); // Offer links contain a bearer token in the fragment.

const LOCAL_HOSTS = new Set(['127.0.0.1', 'localhost', '[::1]', '::1']);
const MAILBOX_API = process.env.E2E_MAILBOX_API;

function requireIsolatedDatabase() {
  const value = process.env.DB_URL || '';
  // Accept only a loopback PostgreSQL JDBC URL whose database or explicit
  // currentSchema is dedicated to validation. Never print the URL on failure.
  const match = value.match(/^jdbc:postgresql:\/\/([^/?#]+)\/([^?#]+)(?:\?([^#]*))?$/i);
  expect(Boolean(match), 'DB_URL must target a loopback PostgreSQL validation database').toBe(true);
  const authority = match[1];
  expect(authority.includes('@'), 'DB_URL must not embed credentials').toBe(false);
  const host = authority.replace(/^\[([^\]]+)\](?::\d+)?$/, '$1').split(':')[0].toLowerCase();
  expect(LOCAL_HOSTS.has(host), 'DB_URL host must be loopback').toBe(true);
  const database = decodeURIComponent(match[2]);
  const params = new URLSearchParams(match[3] || '');
  const schema = params.get('currentSchema') || '';
  expect(/^marcae_validation_[a-z0-9_]+$/i.test(database) || /^marcae_validation_[a-z0-9_]+$/i.test(schema),
    'DB_URL database or currentSchema must use the marcae_validation_ prefix').toBe(true);
}

function requireLocalMailFixture(baseURL) {
  const smtpHost = String(process.env.MAIL_HOST || '').toLowerCase();
  const publicUrl = process.env.PUBLIC_URL;
  const mailboxUrl = MAILBOX_API ? new URL(MAILBOX_API) : null;
  expect(String(process.env.MAIL_ENABLED).toLowerCase(), 'Enable outbox only for the local fake SMTP sink').toBe('true');
  expect(LOCAL_HOSTS.has(smtpHost), 'MAIL_HOST must be loopback; this E2E refuses external SMTP').toBe(true);
  expect(process.env.MAIL_PORT, 'MAIL_PORT must point at the local fake SMTP sink').toBeTruthy();
  expect(process.env.MAIL_USERNAME && process.env.MAIL_PASSWORD && process.env.MAIL_FROM,
    'The local sender requires non-empty fake credentials and sender address').toBeTruthy();
  expect(String(process.env.SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH).toLowerCase(),
    'Disable SMTP authentication for the unauthenticated local fake sink').toBe('false');
  expect(String(process.env.SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE).toLowerCase(),
    'Disable STARTTLS for the local fake sink').toBe('false');
  expect(String(process.env.SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_REQUIRED).toLowerCase(),
    'Do not require STARTTLS for the local fake sink').toBe('false');
  expect(publicUrl, 'PUBLIC_URL must be the ephemeral Playwright app URL').toBe(baseURL);
  expect(mailboxUrl && ['http:', 'https:'].includes(mailboxUrl.protocol)).toBe(true);
  expect(mailboxUrl && LOCAL_HOSTS.has(mailboxUrl.hostname.toLowerCase()), 'E2E_MAILBOX_API must be loopback').toBe(true);
  return mailboxUrl;
}

function addMinutes(date, time, amount) {
  const [year, month, day] = date.split('-').map(Number);
  const [hour, minute] = time.split(':').map(Number);
  const value = new Date(Date.UTC(year, month - 1, day, hour, minute + amount));
  return {
    date: `${value.getUTCFullYear()}-${String(value.getUTCMonth() + 1).padStart(2, '0')}-${String(value.getUTCDate()).padStart(2, '0')}`,
    time: `${String(value.getUTCHours()).padStart(2, '0')}:${String(value.getUTCMinutes()).padStart(2, '0')}`
  };
}

async function findMailpitRaw(request, api, recipient) {
  const apiBase = new URL(api.href.endsWith('/') ? api.href : `${api.href}/`);
  const list = await request.get(new URL('messages?start=0&limit=100', apiBase).toString());
  if (!list.ok()) return null;
  const messages = (await list.json()).messages || [];
  for (const message of messages) {
    const recipients = (message.To || message.to || []).map(item =>
      String(item.Address || item.address || item).toLowerCase());
    if (!recipients.includes(recipient.toLowerCase())) continue;
    const id = message.ID || message.id;
    if (!id) continue;
    const raw = await request.get(new URL(`message/${encodeURIComponent(id)}/raw`, apiBase).toString());
    if (raw.ok()) return raw.text();
  }
  return null;
}

async function waitForOfferLink(request, api, recipient, appOrigin) {
  let rawMessage = null;
  await expect.poll(async () => {
    rawMessage = await findMailpitRaw(request, api, recipient);
    return Boolean(rawMessage && rawMessage.includes('Surgiu uma vaga para voce'));
  }, { timeout: 60000, intervals: [500, 1000, 2000] }).toBe(true);

  const candidates = rawMessage.match(/https?:\/\/[^\s<>"']+\/agendar\.html\?local=[^\s<>"']+#token=[a-f0-9]{64}/gi) || [];
  const link = candidates.map(value => value.replace(/[),.;]+$/, '')).find(value => {
    try { return new URL(value).origin === appOrigin; } catch { return false; }
  });
  expect(Boolean(link), 'The real renderer email must contain a same-origin offer link').toBe(true);
  return link;
}

async function exerciseAuthenticationUi(page, baseURL) {
  const pageErrors = [];
  page.on('pageerror', error => pageErrors.push(error.message));
  await page.goto(`${baseURL}/`);
  await expect(page.locator('#auth')).toBeVisible();
  await expect(page.locator('#auth-form')).toBeVisible();
  await expect(page.locator('#auth-form [name=email]')).toBeVisible();
  await expect(page.locator('#auth-form [name=senha]')).toBeVisible();
  await expect(page.locator('#toggle-auth')).toBeVisible();
  await page.locator('#toggle-auth').click();
  await expect(page.locator('#auth-form [name=nome]')).toBeVisible();
  await expect(page.locator('#auth-form [name=nomeEstudio]')).toBeVisible();
  await page.locator('#toggle-auth').click();
  await page.locator('#forgot-password').click();
  await expect(page.locator('#password-dialog')).toBeVisible();
  await expect(page.locator('#recovery-email')).toBeVisible();
  await expect(page.locator('#password-form [name=email]')).toBeVisible();
  await expect(page.locator('#reset-fields')).toBeHidden();
  await page.locator('#close-password').click();
  await page.goto(`${baseURL}/#redefinir=${'a'.repeat(64)}`);
  await expect(page.locator('#password-dialog')).toBeVisible();
  await expect(page.locator('#reset-fields')).toBeVisible();
  await expect(page.locator('#recovery-email')).toBeHidden();
  await expect(page.locator('#reset-fields [name=senha]')).toBeVisible();
  await expect(page.locator('#reset-fields [name=confirmacao]')).toBeVisible();
  expect(pageErrors, 'Root must not throw JS errors while rendering login, signup, recovery, or reset').toEqual([]);
}

async function prepareStudio(request, width) {
  const suffix = randomUUID();
  const email = `waitlist-owner-${suffix}@example.test`;
  const password = 'SenhaTeste123';
  const signup = await request.post('/auth/estudios', { data: {
    nome: 'Responsavel E2E', nomeEstudio: `Lista visual ${width} ${suffix}`, email, senha: password
  }});
  expect(signup.status()).toBe(201);
  const login = await request.post('/auth/login', { data: { email, senha: password } });
  expect(login.ok()).toBe(true);
  const { token } = await login.json();
  const headers = { Authorization: `Bearer ${token}` };
  const serviceResponse = await request.post('/servicos', { headers, data: {
    nome: `Corte E2E ${width}`, preco: 72.50, duracaoMinutos: 30, ativo: true
  }});
  expect(serviceResponse.status()).toBe(201);
  const service = await serviceResponse.json();
  const linkResponse = await request.get('/servicos/link', { headers });
  expect(linkResponse.ok()).toBe(true);
  const publicLink = new URL((await linkResponse.json()).caminho, 'http://127.0.0.1').searchParams.get('local');
  expect(Boolean(publicLink)).toBe(true);
  const publicPath = `/publico/${encodeURIComponent(publicLink)}`;
  const catalogResponse = await request.get(publicPath);
  expect(catalogResponse.ok()).toBe(true);
  const catalog = await catalogResponse.json();
  const professional = catalog.profissionais[0];
  expect(professional).toBeTruthy();

  let slot;
  for (let offset = 2; offset <= 14 && !slot; offset++) {
    const candidate = new Date(`${catalog.hoje}T12:00:00Z`);
    candidate.setUTCDate(candidate.getUTCDate() + offset);
    const date = candidate.toISOString().slice(0, 10);
    const available = await request.get(`${publicPath}/horarios`, { params: {
      servicoId: service.id, profissionalId: professional.id, data: date
    }});
    expect(available.ok()).toBe(true);
    const hours = (await available.json()).horarios;
    if (hours.length) slot = { date, time: hours[0].slice(0, 5), rawTime: hours[0] };
  }
  expect(slot, 'The seeded studio must expose a slot in the next 14 days').toBeTruthy();

  const booking = await request.post(`${publicPath}/reservas`, { data: {
    chave: randomUUID(), servicoId: service.id, profissionalId: professional.id,
    data: slot.date, horario: slot.rawTime, nome: 'Reserva base E2E',
    telefone: '11988887777', email: `waitlist-seed-${suffix}@example.test`, avisoPrivacidadeLido: true
  }});
  expect(booking.status()).toBe(201);
  return { headers, service, professional, publicLink, publicPath, catalog, slot, suffix };
}

for (const width of [1366, 390]) {
  test(`lista de espera e oferta real sem duplicidade (${width}px)`, async ({ page, request, baseURL }) => {
    test.setTimeout(150000);
    requireIsolatedDatabase();
    const mailboxApi = requireLocalMailFixture(baseURL);
    const appOrigin = new URL(baseURL).origin;
    const mailboxProbe = await request.get(new URL('messages?start=0&limit=1',
      new URL(mailboxApi.href.endsWith('/') ? mailboxApi.href : `${mailboxApi.href}/`)).toString());
    expect(mailboxProbe.ok(), 'Start the local fake SMTP mailbox before the Playwright app').toBe(true);
    await exerciseAuthenticationUi(page, baseURL);
    const fixture = await prepareStudio(request, width);
    const waitEmail = `waitlist-customer-${fixture.suffix}@example.test`;
    const publicUrl = `${baseURL}/agendar.html?local=${encodeURIComponent(fixture.publicLink)}`;
    const guestRequests = [];
    await page.setViewportSize({ width, height: 900 });
    page.on('request', outgoing => {
      const url = new URL(outgoing.url());
      if (!url.pathname.startsWith('/publico/')) return;
      guestRequests.push({ path: url.pathname, method: outgoing.method(), authorization: outgoing.headers().authorization });
    });
    await page.goto(publicUrl);
    await expect(page.locator('#waitlist-panel')).toBeVisible();
    await page.locator('#waitlist-panel summary').click();
    await expect(page.locator('#waitlist-fields')).toBeEnabled();
    await page.locator('#waitlist-service').selectOption(String(fixture.service.id));
    await page.locator('#waitlist-professional').selectOption(String(fixture.professional.id));
    await page.locator('#waitlist-form [name=inicio]').fill(`${fixture.slot.date}T${fixture.slot.time}`);
    const end = addMinutes(fixture.slot.date, fixture.slot.time, fixture.service.duracaoMinutos);
    await page.locator('#waitlist-form [name=fim]').fill(`${end.date}T${end.time}`);
    await page.locator('#waitlist-form [name=nome]').fill(`Cliente fila ${width}`);
    await page.locator('#waitlist-form [name=email]').fill(waitEmail);
    await page.locator('#waitlist-form [name=telefone]').fill('11977776666');
    await page.locator('#waitlist-form [name=avisoPrivacidadeLido]').check();
    await expect(page.locator('#waitlist-privacy-version')).not.toBeEmpty();
    await expect(page.locator('#waitlist-submit')).toBeEnabled();
    await page.locator('#waitlist-submit').click();
    await expect(page.locator('#waitlist-success')).toBeVisible();
    await expect(page.locator('#waitlist-success')).toContainText('entrou na lista');
    expect(guestRequests.length).toBeGreaterThan(0);
    expect(guestRequests.every(item => item.authorization == null)).toBe(true);
    expect(await page.evaluate(() => sessionStorage.getItem('ink.token'))).toBeNull();

    const existingRowsResponse = await request.get('/marcacoes', { headers: fixture.headers });
    expect(existingRowsResponse.ok()).toBe(true);
    const existingRows = await existingRowsResponse.json();
    const original = existingRows.find(item => item.cliente.nome === 'Reserva base E2E' && item.status === 'AGENDADA');
    expect(original).toBeTruthy();
    const cancel = await request.put(`/marcacoes/${original.id}`, { headers: fixture.headers, data: {
      cliente: { id: original.cliente.id }, data: original.data, horario: original.horario,
      descricao: original.descricao, status: 'CANCELADA', duracaoMinutos: original.duracaoMinutos,
      version: original.version, profissionalId: fixture.professional.id
    }});
    expect(cancel.status()).toBe(200);

    const offerLink = await waitForOfferLink(request, mailboxApi, waitEmail, appOrigin);
    const parsedLink = new URL(offerLink);
    expect(parsedLink.pathname).toBe('/agendar.html');
    expect(parsedLink.searchParams.get('local')).toBe(fixture.publicLink);
    expect(/^#token=[a-f0-9]{64}$/i.test(parsedLink.hash)).toBe(true);
    await page.goto(offerLink).catch(() => { throw new Error('The renderer-generated offer page could not be opened'); });
    await expect(page.locator('#offer-panel')).toBeVisible();
    await expect(page.locator('#offer-details')).toBeVisible();
    await expect(page.locator('#offer-details')).toContainText(fixture.service.nome);
    await expect(page.locator('#offer-details')).toContainText(fixture.professional.nome);
    await expect(page.locator('#offer-details')).toContainText(fixture.slot.date.split('-').reverse().join('/'));
    await expect(page.locator('#offer-details')).toContainText(fixture.slot.time);
    await expect(page.locator('#offer-details')).toContainText(`${fixture.service.duracaoMinutos} min`);
    await expect(page.locator('#offer-details')).toContainText('72,50');
    await expect.poll(() => page.evaluate(() => location.hash)).toBe('');
    const tokenLeaked = await page.evaluate(token =>
      location.href.includes(token) || Object.values(sessionStorage).some(value => value.includes(token)),
    parsedLink.hash.slice('#token='.length));
    expect(tokenLeaked).toBe(false);

    const firstAcceptResponse = page.waitForResponse(response =>
      response.request().method() === 'POST' && new URL(response.url()).pathname.endsWith('/oferta/aceitar'));
    await page.locator('#offer-accept').click();
    const firstAccepted = await (await firstAcceptResponse).json();
    await expect(page.locator('#offer-confirmation')).toBeVisible();
    await expect(page.locator('#offer-confirmation-details')).toContainText(fixture.service.nome);
    await expect(page.locator('#offer-confirmation-details')).toContainText(fixture.professional.nome);
    expect(firstAccepted).toMatchObject({
      servico: fixture.service.nome, profissional: fixture.professional.nome,
      data: fixture.slot.date, horario: fixture.slot.rawTime,
      duracaoMinutos: fixture.service.duracaoMinutos, preco: 72.50
    });

    const replayPage = await page.context().newPage();
    await replayPage.goto(offerLink).catch(() => { throw new Error('The renderer-generated replay page could not be opened'); });
    await expect(replayPage.locator('#offer-check-accepted')).toBeVisible();
    const replayResponse = replayPage.waitForResponse(response =>
      response.request().method() === 'POST' && new URL(response.url()).pathname.endsWith('/oferta/aceitar'));
    await replayPage.locator('#offer-check-accepted').click();
    const replayed = await (await replayResponse).json();
    await expect(replayPage.locator('#offer-confirmation')).toBeVisible();
    expect(replayed).toEqual(firstAccepted);
    expect(guestRequests.every(item => item.authorization == null)).toBe(true);

    const finalAgendaResponse = await request.get('/marcacoes', { headers: fixture.headers });
    expect(finalAgendaResponse.ok()).toBe(true);
    const sameSlotActive = (await finalAgendaResponse.json()).filter(item =>
      item.data === fixture.slot.date && item.horario === fixture.slot.rawTime && ['AGENDADA', 'CONFIRMADA'].includes(item.status));
    expect(sameSlotActive).toHaveLength(1);
    expect(sameSlotActive[0].id).toBe(firstAccepted.reservaId);
    await replayPage.close();
  });
}
