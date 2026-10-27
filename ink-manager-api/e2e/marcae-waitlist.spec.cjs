const { test, expect } = require('@playwright/test');
const { randomUUID } = require('node:crypto');

// Run with a local fake SMTP sink (for example, Mailpit) already listening.
// The Playwright config preflights both loopback endpoints and injects all
// MAIL_* settings only into the ephemeral app process. The runner stays mail-off.
// The real renderer creates the link consumed by this browser flow.
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
  const publicUrl = process.env.PUBLIC_URL;
  const mailboxUrl = MAILBOX_API ? new URL(MAILBOX_API) : null;
  const runnerMailVariables = Object.keys(process.env).filter(key => /^(MAIL_|SMTP_|SPRING_MAIL_)/i.test(key));
  expect(runnerMailVariables, 'SMTP settings must stay out of the Playwright runner').toEqual([]);
  expect(process.env.E2E_LOCAL_MAIL_FIXTURE,
    'The config must complete the loopback SMTP and mailbox preflight').toBe('loopback-smtp-api-preflight-passed');
  expect(publicUrl, 'PUBLIC_URL must be the ephemeral Playwright app URL').toBe(baseURL);
  expect(mailboxUrl && mailboxUrl.href, 'E2E_MAILBOX_API must be configured').toBe('http://127.0.0.1:8025/api/v1/');
  expect(mailboxUrl && LOCAL_HOSTS.has(mailboxUrl.hostname.toLowerCase()), 'E2E_MAILBOX_API must be loopback').toBe(true);
  return mailboxUrl;
}

function sanitizeDiagnosticText(value) {
  return String(value || '')
    .replace(/https?:\/\/[^\s"'<>]+/gi, value => {
      try {
        const url = new URL(value);
        return `${url.origin}${url.pathname}`;
      } catch { return '<url>'; }
    })
    .replace(/\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b/gi, '<email>')
    .replace(/\b[a-f0-9]{64}\b/gi, '<token>')
    .replace(/\b(Bearer\s+)\S+/gi, '$1<redacted>');
}

function isAuthConfigRequest(request, expectedOrigin) {
  try {
    const url = new URL(request.url());
    return request.method() === 'GET' && url.pathname === '/auth/config' &&
      (!expectedOrigin || url.origin === expectedOrigin);
  } catch { return false; }
}

function isRelevantAuthDiagnostic(text) {
  return /auth\/config|fonts\.googleapis\.com|app\.js|failed to load|network|fetch/i.test(text);
}

function browserAuthConfigDiagnostics(page, baseURL) {
  const appOrigin = new URL(baseURL).origin;
  const startedAt = new WeakMap();
  const events = [];
  const consoleErrors = [];
  const pageErrors = [];
  const relevantPageErrors = [];

  page.on('request', request => {
    if (isAuthConfigRequest(request, appOrigin)) {
      startedAt.set(request, Date.now());
      return;
    }
    let url;
    try { url = new URL(request.url()); } catch { return; }
    if (url.hostname === 'fonts.googleapis.com') {
      startedAt.set(request, Date.now());
      events.push({ event: 'requested', host: 'fonts.googleapis.com', resourceType: request.resourceType() });
    } else if (url.origin === appOrigin && url.pathname === '/app.js') {
      startedAt.set(request, Date.now());
      events.push({ event: 'requested', method: request.method(), path: '/app.js' });
    }
  });

  page.on('response', response => {
    const request = response.request();
    const durationMs = startedAt.has(request) ? Date.now() - startedAt.get(request) : null;
    if (isAuthConfigRequest(request, appOrigin)) {
      events.push({ event: 'response', method: 'GET', path: '/auth/config', status: response.status(), durationMs });
      return;
    }
    let url;
    try { url = new URL(request.url()); } catch { return; }
    if (url.hostname === 'fonts.googleapis.com') {
      events.push({ event: 'response', host: 'fonts.googleapis.com', resourceType: request.resourceType(), status: response.status(), durationMs });
    } else if (url.origin === appOrigin && url.pathname === '/app.js') {
      events.push({ event: response.ok() ? 'loaded' : 'response', path: '/app.js', status: response.status(), durationMs });
    }
  });

  page.on('requestfailed', request => {
    const durationMs = startedAt.has(request) ? Date.now() - startedAt.get(request) : null;
    const failure = sanitizeDiagnosticText(request.failure()?.errorText || 'request failed');
    if (isAuthConfigRequest(request, appOrigin)) {
      events.push({ event: 'requestfailed', method: 'GET', path: '/auth/config', durationMs, failure });
      return;
    }
    let url;
    try { url = new URL(request.url()); } catch { return; }
    if (url.hostname === 'fonts.googleapis.com') {
      events.push({ event: 'requestfailed', host: 'fonts.googleapis.com', resourceType: request.resourceType(), durationMs, failure });
    } else if (url.origin === appOrigin && url.pathname === '/app.js') {
      events.push({ event: 'requestfailed', path: '/app.js', durationMs, failure });
    }
  });

  page.on('console', message => {
    if (message.type() !== 'error') return;
    const text = sanitizeDiagnosticText(message.text());
    if (isRelevantAuthDiagnostic(text)) consoleErrors.push(text);
  });
  page.on('pageerror', error => {
    const text = sanitizeDiagnosticText(error.message);
    pageErrors.push(text);
    if (isRelevantAuthDiagnostic(text)) relevantPageErrors.push(text);
  });

  return { events, consoleErrors, pageErrors, relevantPageErrors };
}

function waitForBrowserAuthConfigOutcome(page, appOrigin, diagnostics) {
  return new Promise(resolve => {
    let settled = false;
    const cleanup = () => {
      page.off('response', onResponse);
      page.off('requestfailed', onRequestFailed);
    };
    const latestAuthEvent = () => {
      for (let index = diagnostics.events.length - 1; index >= 0; index--) {
        const event = diagnostics.events[index];
        if (event.path === '/auth/config' &&
            (event.event === 'response' || event.event === 'requestfailed')) return event;
      }
      return null;
    };
    const finish = result => {
      if (settled) return;
      settled = true;
      cleanup();
      resolve(result);
    };
    const onResponse = response => {
      const request = response.request();
      if (!isAuthConfigRequest(request, appOrigin)) return;
      const status = response.status();
      void (async () => {
        let registrationEnabled = null;
        if (status === 200) {
          try {
            const config = await response.json();
            registrationEnabled = typeof config.registrationEnabled === 'boolean'
              ? config.registrationEnabled
              : null;
          } catch { registrationEnabled = null; }
        }
        const timing = latestAuthEvent();
        finish({
          method: 'GET', path: '/auth/config', status, registrationEnabled,
          durationMs: timing?.durationMs ?? null
        });
      })();
    };
    const onRequestFailed = request => {
      if (!isAuthConfigRequest(request, appOrigin)) return;
      const timing = latestAuthEvent();
      finish({
        method: 'GET', path: '/auth/config', status: null, registrationEnabled: null,
        durationMs: timing?.durationMs ?? null,
        failure: sanitizeDiagnosticText(request.failure()?.errorText || 'request failed')
      });
    };
    page.on('response', onResponse);
    page.on('requestfailed', onRequestFailed);
  });
}

async function assertApiRegistrationEnabled(request, testInfo) {
  const startedAt = Date.now();
  let status = null;
  let registrationEnabled = null;
  let failure = null;
  try {
    const response = await request.get('/auth/config');
    status = response.status();
    if (status === 200) {
      const config = await response.json();
      registrationEnabled = typeof config.registrationEnabled === 'boolean'
        ? config.registrationEnabled
        : null;
    }
  } catch (error) {
    failure = sanitizeDiagnosticText(error.message || String(error));
  }
  const result = {
    method: 'GET', path: '/auth/config', status,
    durationMs: Date.now() - startedAt, registrationEnabled, failure
  };
  if (status !== 200 || registrationEnabled !== true) {
    await testInfo.attach('auth-config-api-preflight.json', {
      body: Buffer.from(JSON.stringify(result, null, 2)), contentType: 'application/json'
    });
    throw new Error(`Pre-UI GET /auth/config failed: status=${status ?? 'unavailable'}, durationMs=${result.durationMs}, registrationEnabled=${registrationEnabled ?? 'unavailable'}, error=${failure ?? 'none'}`);
  }
  return result;
}

async function attachBrowserAuthConfigDiagnostics(testInfo, apiPreflight, browserPreflight, diagnostics, error) {
  const data = {
    apiPreflight,
    browserPreflight,
    browserEvents: diagnostics.events,
    consoleErrors: diagnostics.consoleErrors,
    pageErrors: diagnostics.relevantPageErrors,
    failure: sanitizeDiagnosticText(error?.message || String(error))
  };
  await testInfo.attach('auth-config-browser-diagnostics.json', {
    body: Buffer.from(JSON.stringify(data, null, 2)), contentType: 'application/json'
  });
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

function decodeQuotedPrintableBody(rawMessage) {
  const separator = /\r?\n\r?\n/;
  const match = separator.exec(rawMessage);
  if (!match) return rawMessage;

  const headers = rawMessage.slice(0, match.index);
  const body = rawMessage.slice(match.index + match[0].length);
  if (!/^content-transfer-encoding:[ \t]*quoted-printable[ \t]*\r?$/im.test(headers)) return body;

  return body
    .replace(/=\r?\n/g, '')
    .replace(/=([a-f0-9]{2})/gi, (_, hex) => String.fromCharCode(parseInt(hex, 16)));
}

async function waitForOfferLink(request, api, recipient, appOrigin, expectedShortPath) {
  let rawMessage = null;
  let messageBody = '';
  await expect.poll(async () => {
    rawMessage = await findMailpitRaw(request, api, recipient);
    messageBody = rawMessage ? decodeQuotedPrintableBody(rawMessage) : '';
    const subject = rawMessage?.match(/^Subject:[^\r\n]*/im)?.[0] || '';
    return subject.includes('Surgiu uma vaga para voce') || messageBody.includes('Surgiu uma vaga para voce');
  }, { timeout: 60000, intervals: [500, 1000, 2000] }).toBe(true);

  const candidates = messageBody.match(/https?:\/\/[^\s<>"']+/gi) || [];
  const link = candidates.map(value => value.replace(/[),.;]+$/, '')).find(value => {
    try {
      const url = new URL(value);
      return url.origin === appOrigin &&
        url.pathname === expectedShortPath && /^\/a\/[^/]+$/.test(url.pathname) &&
        url.search === '' && /^#token=[a-f0-9]{64}$/i.test(url.hash);
    } catch { return false; }
  });
  expect(Boolean(link), 'The real renderer email must contain a same-origin offer link').toBe(true);
  return link;
}

async function exerciseAuthenticationUi(page, baseURL, testInfo, apiPreflight) {
  const diagnostics = browserAuthConfigDiagnostics(page, baseURL);
  let browserPreflight = null;
  try {
    const appOrigin = new URL(baseURL).origin;
    const browserConfigPromise = waitForBrowserAuthConfigOutcome(page, appOrigin, diagnostics);
    const navigationPromise = page.goto(`${baseURL}/#login`).then(() => ({ ok: true }), error => ({
      ok: false, failure: sanitizeDiagnosticText(error.message || String(error))
    }));
    const navigationFailurePromise = navigationPromise.then(navigation =>
      navigation.ok ? new Promise(() => {}) : { navigation }
    );
    const outcome = await Promise.race([
      browserConfigPromise.then(browserConfig => ({ browserConfig })),
      navigationFailurePromise
    ]);
    if (outcome.navigation) throw new Error(`Browser navigation failed: ${outcome.navigation.failure}`);
    const browserConfig = outcome.browserConfig;
    browserPreflight = browserConfig;
    if (browserConfig.failure) throw new Error(`Browser GET /auth/config failed: ${browserConfig.failure}`);
    expect(browserConfig.status, 'Browser GET /auth/config must return HTTP 200').toBe(200);
    expect(browserConfig.registrationEnabled,
      'Browser GET /auth/config must enable registration').toBe(true);
    const navigation = await navigationPromise;
    if (!navigation.ok) throw new Error(`Browser navigation failed: ${navigation.failure}`);

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
    // Change the query too so the fake reset token is read by a fresh document load.
    await page.goto(`${baseURL}/?e2e-reset-smoke=1#redefinir=${'a'.repeat(64)}`);
    await expect(page.locator('#password-dialog')).toBeVisible();
    await expect(page.locator('#reset-fields')).toBeVisible();
    await expect(page.locator('#recovery-email')).toBeHidden();
    await expect(page.locator('#reset-fields [name=senha]')).toBeVisible();
    await expect(page.locator('#reset-fields [name=confirmacao]')).toBeVisible();
    expect(diagnostics.pageErrors,
      'Root must not throw JS errors while rendering login, signup, recovery, or reset').toEqual([]);
  } catch (error) {
    await attachBrowserAuthConfigDiagnostics(testInfo, apiPreflight, browserPreflight, diagnostics, error);
    throw error;
  }
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
  const serviceLink = await linkResponse.json();
  const publicLink = new URL(serviceLink.caminho, 'http://127.0.0.1').searchParams.get('local');
  expect(Boolean(publicLink)).toBe(true);
  const caminhoCurto = serviceLink.caminhoCurto;
  const shortUrl = new URL(caminhoCurto, 'http://127.0.0.1:8087');
  expect(shortUrl.origin).toBe('http://127.0.0.1:8087');
  expect(shortUrl.search).toBe('');
  expect(shortUrl.hash).toBe('');
  expect(shortUrl.pathname).toMatch(/^\/a\/[^/]+$/);
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
  return {
    headers, service, professional, publicLink, caminhoCurto, caminhoCurtoPath: shortUrl.pathname,
    publicPath, catalog, slot, suffix
  };
}

for (const width of [1366, 390]) {
  test(`lista de espera e oferta real sem duplicidade (${width}px)`, async ({ page, request, baseURL }, testInfo) => {
    test.setTimeout(150000);
    requireIsolatedDatabase();
    const apiPreflight = await assertApiRegistrationEnabled(request, testInfo);
    const mailboxApi = requireLocalMailFixture(baseURL);
    const appOrigin = new URL(baseURL).origin;
    const mailboxProbe = await request.get(new URL('messages?start=0&limit=1',
      new URL(mailboxApi.href.endsWith('/') ? mailboxApi.href : `${mailboxApi.href}/`)).toString());
    expect(mailboxProbe.ok(), 'Start the local fake SMTP mailbox before the Playwright app').toBe(true);
    await exerciseAuthenticationUi(page, baseURL, testInfo, apiPreflight);
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
    await page.locator('#waitlist-panel > summary').click();
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

    const offerLink = await waitForOfferLink(request, mailboxApi, waitEmail, appOrigin, fixture.caminhoCurtoPath);
    const parsedLink = new URL(offerLink);
    expect(parsedLink.pathname).toBe(fixture.caminhoCurtoPath);
    expect(parsedLink.pathname).toMatch(/^\/a\/[^/]+$/);
    expect(parsedLink.search).toBe('');
    expect(/^#token=[a-f0-9]{64}$/i.test(parsedLink.hash)).toBe(true);
    await page.goto('about:blank');
    await page.goto(offerLink).catch(() => { throw new Error('The renderer-generated offer page could not be opened'); });
    await expect(page.locator('#offer-panel')).toBeVisible();
    await expect(page.locator('#offer-details')).toBeVisible();
    await expect(page.locator('#offer-deadline')).toBeVisible();
    await expect(page.locator('#offer-deadline')).toContainText('Oferta válida até');
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
