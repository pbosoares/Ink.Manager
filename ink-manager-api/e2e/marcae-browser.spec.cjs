const { test, expect } = require('@playwright/test');
const { randomUUID } = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const { runAuthenticatedReloadDiagnostics } = require('./diagnostics.cjs');

// Keep multi-context headless pages scheduled while the other role is active.
test.use({ launchOptions: { args: ['--disable-background-timer-throttling', '--disable-renderer-backgrounding', '--disable-backgrounding-occluded-windows'] } });

const workspaceBootstrapPaths = new Set(['/clientes', '/marcacoes', '/profissionais', '/auth/me']);

function workspaceBootstrapSummary(page) {
  return (page.__inkWorkspaceBootstrapAttempts || []).map(({ method, path, status, responseElapsedMs, elapsedMs, failure, finished }) => ({
    method, path, status, responseElapsedMs, elapsedMs, failure, pending: !finished
  }));
}

function assertFirstBootstrapRequest(attempts, method, path) {
  const attempt = attempts.find(candidate => candidate.method === method && candidate.path === path);
  expect(attempt, `first bootstrap request ${method} ${path} must be captured`).toBeTruthy();
  expect(attempt.failure, `first bootstrap request ${method} ${path} must not fail`).toBeNull();
  expect(attempt.status, `first bootstrap request ${method} ${path} must return HTTP 200`).toBe(200);
  expect(attempt.finished, `first bootstrap request ${method} ${path} must finish reading its response`).toBe(true);
  expect(attempt.elapsedMs, `first bootstrap request ${method} ${path} must finish before the 20s client abort`).toBeLessThan(20000);
}

async function waitForWorkspace(page, { captureBootstrap = false } = {}) {
  const waitForState = async (allowRetry) => {
    const result = await page.waitForFunction((retryAllowed) => {
      const workspace = document.querySelector('#workspace');
      const action = document.querySelector('#main-action');
      const style = workspace ? getComputedStyle(workspace) : null;
      const workspaceVisible = Boolean(workspace && !workspace.hidden && workspace.getClientRects().length > 0 &&
        style.display !== 'none' && style.visibility !== 'hidden');
      const authenticated = Boolean(sessionStorage.getItem('ink.token')?.trim());
      const ariaBusy = workspace?.getAttribute('aria-busy') ?? null;
      const actionEnabled = Boolean(action && !action.hidden && !action.disabled);
      const ready = workspaceVisible && ariaBusy === 'false' && actionEnabled && authenticated;
      const retry = workspaceVisible ? workspace.querySelector('#page-error button') : null;
      const retryVisible = Boolean(retry && !retry.hidden && retry.getClientRects().length > 0);
      return ready || (retryAllowed && retryVisible)
        ? { ready, workspaceVisible, ariaBusy, actionEnabled, authenticated, retryVisible }
        : false;
    }, allowRetry, { timeout: 30000 });
    return result.jsonValue();
  };

  let state;
  try {
    state = await waitForState(true);
    if (!state.ready) {
      const failure = await page.locator('#workspace #page-error').innerText();
      const requests = page.__inkFailedRequests || [];
      const responses = page.__inkHttpFailures || [];
      test.info().annotations.push({
        type: 'workspace-fetch-failure',
        description: JSON.stringify({ failure, requests, responses, bootstrap: workspaceBootstrapSummary(page), state })
      });
      const retry = page.locator('#workspace #page-error button');
      if (state.workspaceVisible && state.retryVisible && await retry.isVisible().catch(() => false)) {
        await retry.click();
        state = await waitForState(false);
      }
    }

    expect(state.workspaceVisible, 'workspace must be visible after authentication').toBe(true);
    expect(state.ariaBusy, 'workspace data loading must be finished').toBe('false');
    expect(state.actionEnabled, 'main action must be enabled after workspace load').toBe(true);
    expect(state.authenticated, 'browser session must contain the authenticated token').toBe(true);
    await expect(page.locator('#main-action')).toBeEnabled({ timeout: 30000 });
  } catch (error) {
    if (captureBootstrap) {
      page.__inkWorkspaceBootstrapCapture = false;
      test.info().annotations.push({
        type: 'authenticated-workspace-bootstrap',
        description: JSON.stringify({ viewportWidth: page.viewportSize()?.width ?? null, outcome: 'not-ready', requests: workspaceBootstrapSummary(page) })
      });
    }
    throw error;
  }

  if (captureBootstrap) {
    page.__inkWorkspaceBootstrapCapture = false;
    const attempts = page.__inkWorkspaceBootstrapAttempts || [];
    test.info().annotations.push({
      type: 'authenticated-workspace-bootstrap',
      description: JSON.stringify({ viewportWidth: page.viewportSize()?.width ?? null, outcome: 'ready', requests: workspaceBootstrapSummary(page) })
    });
    assertFirstBootstrapRequest(attempts, 'POST', '/auth/login');
    for (const path of workspaceBootstrapPaths) assertFirstBootstrapRequest(attempts, 'GET', path);
  }
}
function recordWorkspaceFailures(page) {
  page.__inkFailedRequests = [];
  page.__inkHttpFailures = [];
  page.__inkWorkspaceBootstrapCapture = true;
  page.__inkWorkspaceBootstrapAttempts = [];
  page.__inkWorkspaceBootstrapByRequest = new WeakMap();
  page.on('request', request => {
    if (!page.__inkWorkspaceBootstrapCapture) return;
    let url;
    try { url = new URL(request.url()); } catch { return; }
    if (url.origin !== 'http://127.0.0.1:8087') return;
    const method = request.method();
    const isLogin = method === 'POST' && url.pathname === '/auth/login';
    const isWorkspaceGet = method === 'GET' && workspaceBootstrapPaths.has(url.pathname);
    if (!isLogin && !isWorkspaceGet) return;
    const attempt = { method, path: url.pathname, status: null, responseElapsedMs: null, elapsedMs: null, failure: null, finished: false, startedAt: Date.now() };
    page.__inkWorkspaceBootstrapByRequest.set(request, attempt);
    page.__inkWorkspaceBootstrapAttempts.push(attempt);
  });
  page.on('requestfinished', request => {
    const attempt = page.__inkWorkspaceBootstrapByRequest.get(request);
    if (!attempt) return;
    attempt.elapsedMs = Date.now() - attempt.startedAt;
    attempt.finished = true;
  });
  page.on('requestfailed', request => {
    let url;
    try { url = new URL(request.url()); } catch { return; }
    if (url.origin !== 'http://127.0.0.1:8087') return;
    const attempt = page.__inkWorkspaceBootstrapByRequest.get(request);
    const failureText = request.failure()?.errorText || '';
    const errorCode = failureText.match(/(?:net::)?ERR_[A-Z0-9_]+/i)?.[0] || 'request failed';
    if (attempt) {
      attempt.failure = errorCode;
      attempt.elapsedMs = Date.now() - attempt.startedAt;
      attempt.finished = true;
    }
    page.__inkFailedRequests.push({ path: url.pathname, failure: errorCode });
  });
  page.on('response', response => {
    let url;
    try { url = new URL(response.url()); } catch { return; }
    if (url.origin !== 'http://127.0.0.1:8087') return;
    const attempt = page.__inkWorkspaceBootstrapByRequest.get(response.request());
    if (attempt) {
      attempt.status = response.status();
      attempt.responseElapsedMs = Date.now() - attempt.startedAt;
    }
    if (response.status() >= 400) page.__inkHttpFailures.push({ path: url.pathname, status: response.status() });
  });
}

function effectiveDiagnosticsMetadata(testInfo) {
  const metadata = {
    ...(testInfo.config?.metadata || {}),
    ...(testInfo.project?.metadata || {})
  };
  const envRunId = process.env.E2E_DIAGNOSTICS_RUN_ID;
  const envPidFile = process.env.E2E_DIAGNOSTICS_PIDFILE;
  const envAccessLog = process.env.E2E_DIAGNOSTICS_ACCESS_LOG;
  const diagnosticsRunId = envRunId || metadata.diagnosticsRunId;
  const diagnosticsPidFile = envPidFile || metadata.diagnosticsPidFile;
  const diagnosticsAccessLog = envAccessLog || metadata.diagnosticsAccessLog;
  if (!/^[A-Za-z0-9_-]+$/.test(diagnosticsRunId || '') || !diagnosticsPidFile) {
    throw new Error('Playwright diagnostics handoff is missing for the current run');
  }
  if ((envRunId && metadata.diagnosticsRunId && envRunId !== metadata.diagnosticsRunId) ||
      (envPidFile && metadata.diagnosticsPidFile &&
       path.resolve(envPidFile).toLowerCase() !== path.resolve(metadata.diagnosticsPidFile).toLowerCase()) ||
      (envAccessLog && metadata.diagnosticsAccessLog &&
       path.resolve(envAccessLog).toLowerCase() !== path.resolve(metadata.diagnosticsAccessLog).toLowerCase())) {
    throw new Error('Playwright diagnostics environment and config metadata disagree');
  }

  const expectedPidFile = path.resolve(process.cwd(), 'target', 'diagnostics', 'runs', diagnosticsRunId, 'application.pid');
  if (!path.isAbsolute(diagnosticsPidFile) ||
      path.resolve(diagnosticsPidFile).toLowerCase() !== expectedPidFile.toLowerCase()) {
    throw new Error('Playwright diagnostics PID file does not match the current run id');
  }
  let pidContent;
  try { pidContent = fs.readFileSync(expectedPidFile, 'utf8').trim(); }
  catch { throw new Error('Playwright diagnostics PID file is unavailable for the current run'); }
  if (!/^\d+$/.test(pidContent)) {
    throw new Error('Playwright diagnostics PID file is invalid for the current run');
  }

  const expectedAccessLog = path.join('target', 'diagnostics', 'access', diagnosticsRunId + '.log');
  if (!diagnosticsAccessLog ||
      path.resolve(diagnosticsAccessLog).toLowerCase() !== path.resolve(expectedAccessLog).toLowerCase()) {
    throw new Error('Playwright diagnostics access log does not match the current run id');
  }
  return {
    diagnosticsPidFile: expectedPidFile,
    diagnosticsRunId,
    diagnosticsAccessLog: expectedAccessLog
  };
}

async function reloadWorkspaceWithoutSlowFetches(page, request, headers, width) {
  const watched = new Set(['/clientes', '/marcacoes', '/profissionais', '/auth/me']);
  const byRequest = new WeakMap();
  const attempts = [];
  const onRequest = request => {
    const url = new URL(request.url());
    if (request.method() !== 'GET' || url.origin !== 'http://127.0.0.1:8087' || !watched.has(url.pathname)) return;
    const attempt = { path: url.pathname, status: null, elapsedMs: null, failure: null, startedAt: Date.now() };
    byRequest.set(request, attempt);
    attempts.push(attempt);
  };
  const onResponse = response => {
    const attempt = byRequest.get(response.request());
    if (!attempt) return;
    attempt.status = response.status();
    attempt.elapsedMs = Date.now() - attempt.startedAt;
  };
  const onFailure = request => {
    const attempt = byRequest.get(request);
    if (!attempt) return;
    attempt.failure = request.failure()?.errorText || 'request failed';
    attempt.elapsedMs = Date.now() - attempt.startedAt;
  };

  page.on('request', onRequest);
  page.on('response', onResponse);
  page.on('requestfailed', onFailure);
  const diagnosticsMetadata = effectiveDiagnosticsMetadata(test.info());
  try {
    await runAuthenticatedReloadDiagnostics({
      page,
      request,
      headers,
      testInfo: test.info(),
      pidFile: diagnosticsMetadata.diagnosticsPidFile,
      runId: diagnosticsMetadata.diagnosticsRunId,
      accessLogPath: diagnosticsMetadata.diagnosticsAccessLog,
      viewportWidth: width,
      waitForReady: () => waitForWorkspace(page)
    });
  } finally {
    page.off('request', onRequest);
    page.off('response', onResponse);
    page.off('requestfailed', onFailure);
  }

  test.info().annotations.push({
    type: 'authenticated-workspace-reload',
    description: JSON.stringify(attempts.map(({ path, status, elapsedMs, failure }) => ({ path, status, elapsedMs, failure })))
  });
  expect(attempts.map(attempt => attempt.path).sort()).toEqual([...watched].sort());
  for (const attempt of attempts) {
    expect(attempt.failure, `${attempt.path} must complete without a network failure`).toBeNull();
    expect(attempt.status, `${attempt.path} must return HTTP 200`).toBe(200);
    expect(attempt.elapsedMs, `${attempt.path} must finish before the 20s client abort`).toBeLessThan(20000);
  }
}

for (const width of [1366, 390]) {
  test(`Marcae navegador ${width}px: catalogo, reserva anonima e recebimento`, async ({ page, request, browser }) => {
    test.setTimeout(150000);
    await page.bringToFront();
    await page.setViewportSize({ width, height: 900 });
    const errors = [];
    recordWorkspaceFailures(page);
    page.on('pageerror', error => errors.push(error.message));
    const email = `marcae-ui-${randomUUID()}@example.com`;
    const signup = await request.post('/auth/estudios', { data: {
      nome: 'Responsavel UI', nomeEstudio: `Negocio UI ${width}`, email, senha: 'SenhaTeste123'
    }});
    expect(signup.status()).toBe(201);
    const login = await request.post('/auth/login', { data: { email, senha: 'SenhaTeste123' } });
    expect(login.ok()).toBeTruthy();
    const { token } = await login.json();
    const headers = { Authorization: `Bearer ${token}` };
    await page.goto('/#login');
    await page.locator('#auth-form [name=email]').fill(email);
    await page.locator('#auth-form [name=senha]').fill('SenhaTeste123');
    await page.locator('#auth-submit').click();
    await waitForWorkspace(page, { captureBootstrap: true });
    await expect(page.locator('#monthly-report')).toBeVisible();
    await expect(page.locator('#report-data')).toBeVisible();
    await page.locator('#report-range').selectOption('week');
    await expect(page.locator('#report-chart-label')).toContainText('semana');
    await page.locator('.nav-item[data-view=studio]').click();
    await expect(page.locator('#schedule-config-form [name=antecedenciaMinimaMinutos]')).toHaveValue(/\d+/);
    await page.locator('#schedule-config-form [name=antecedenciaMinimaMinutos]').fill('0');
    await page.locator('#schedule-config-form [name=limiteAgendamentosPorEmail]').fill('5');
    await page.locator('#schedule-config-form button[type=submit]').click();
    await expect(page.locator('#schedule-config-message')).toContainText('salvas');
    // The existing catalog loader reports readiness through this status before editing.
    await expect(page.locator('#service-message')).toBeEmpty();
    await page.locator('#service-form [name=nome]').fill('Servico UI');
    await page.locator('#service-form [name=preco]').fill('85.50');
    await page.locator('#service-form [name=duracaoMinutos]').fill('30');
    await page.locator('#service-form [name=ativo]').check();
    await page.locator('#service-save').click();
    await expect(page.locator('#service-list')).toContainText('Servico UI');
    const linksResponse = await request.get('/servicos/link', { headers });
    expect(linksResponse.ok()).toBeTruthy();
    const link = await linksResponse.json();
    const publicUrl = await page.locator('#public-link').inputValue();
    const shortUrl = new URL(link.caminhoCurto, page.url());
    expect(new URL(publicUrl).href).toBe(shortUrl.href);
    expect(shortUrl.pathname).toMatch(/^\/a\/[A-Za-z0-9_-]+$/);
    const linkPublico = new URL(link.caminho, page.url()).searchParams.get('local');
    expect(linkPublico).toBeTruthy();
    const catalogResponse = await request.get(`/publico/${encodeURIComponent(linkPublico)}`);
    expect(catalogResponse.ok()).toBeTruthy();
    const catalog = await catalogResponse.json();
    const service = catalog.servicos.find(s => s.nome === 'Servico UI');
    const professional = catalog.profissionais[0];
    let selectedDate;
    for (let offset = 1; offset <= 7; offset++) {
      const date = new Date(`${catalog.hoje}T12:00:00Z`);
      date.setUTCDate(date.getUTCDate() + offset);
      const data = date.toISOString().slice(0, 10);
      const response = await request.get(`/publico/${encodeURIComponent(linkPublico)}/horarios`, { params: {
        servicoId: service.id, profissionalId: professional.id, data
      }});
      expect(response.ok()).toBeTruthy();
      if ((await response.json()).horarios.length) { selectedDate = data; break; }
    }
    expect(selectedDate).toBeTruthy();
    const guestContext = await browser.newContext({ viewport: { width, height: 900 } });
    try {
      const guest = await guestContext.newPage();
      await guest.bringToFront();
      guest.on('pageerror', error => errors.push(error.message));
      const publicAuthHeaders = [];
      const reservationPayloads = [];
      guest.on('request', req => {
        if (new URL(req.url()).pathname.startsWith('/publico/') && req.headers().authorization) publicAuthHeaders.push(req.url());
        if (req.method() === 'POST' && new URL(req.url()).pathname.endsWith('/reservas')) reservationPayloads.push(req.postDataJSON());
      });
      await guest.goto(publicUrl);
      expect(new URL(guest.url()).pathname).toBe(shortUrl.pathname);
      await expect(guest.locator('#booking-form')).toBeVisible();
      await guest.locator('#public-service').selectOption(String(service.id));
      await guest.locator('#public-professional').selectOption(String(professional.id));
      await guest.locator('#public-date').fill(selectedDate);
      await expect(guest.locator('#public-time')).toBeEnabled();
      const time = await guest.locator('#public-time option').evaluateAll(options => options.find(o => o.value)?.value);
      expect(time).toBeTruthy();
      await guest.locator('#public-time').selectOption(time);
      await guest.locator('#refresh-slots').click();
      await expect(guest.locator('#slots-status')).not.toContainText('Consultando');
      await expect(guest.locator('#public-time')).toHaveValue(time);
      await guest.locator('#booking-form [name=nome]').fill(`Cliente UI ${width}`);
      await guest.locator('#booking-form [name=email]').fill(`cliente-ui-${width}@example.com`);
      await guest.locator('#booking-form [name=telefone]').fill('21988887777');
      await guest.locator('#booking-form [name=avisoPrivacidadeLido]').check();
      await expect(guest.locator('#privacy-version')).not.toBeEmpty();
      await expect(guest.locator('#reserve-button')).toBeEnabled();
      await expect(guest.locator('#service-summary')).toContainText('85,50');
      expect(await guest.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
      // Drop only the first response AFTER the real API commits the booking.
      // A reload must preserve UUID and retry against the real API without duplicating it.
      let firstPayload;
      if (width === 1366) {
        await guest.route('**/publico/*/reservas', async route => {
          firstPayload = route.request().postDataJSON();
          const committed = await route.fetch();
          expect(committed.status()).toBe(201);
          await route.abort('failed');
          await guest.unroute('**/publico/*/reservas');
        }, { times: 1 });
      }
      await guest.locator('#reserve-button').click();
      if (width === 1366) {
        await expect(guest.locator('#pending-note')).toBeVisible();
        await expect(guest.locator('#reserve-button')).toBeEnabled();
        await guest.reload();
        await expect(guest.locator('#reserve-button')).toBeEnabled();
        const retryRequest = guest.waitForRequest(req => req.method() === 'POST' && new URL(req.url()).pathname.endsWith('/reservas'));
        await guest.locator('#reserve-button').click();
        expect((await retryRequest).postDataJSON()).toEqual(firstPayload);
      }
      await expect(guest.locator('#booking-confirmation')).toBeVisible();
      expect(reservationPayloads[0]).toMatchObject({ email: `cliente-ui-${width}@example.com`, avisoPrivacidadeLido: true });
      await expect(guest.locator('#confirmation-details')).toContainText('85,50');
      await expect(guest.locator('#confirmation-details')).toContainText('30 min');
      expect(publicAuthHeaders).toEqual([]);
      expect(await guest.evaluate(() => sessionStorage.getItem('ink.token'))).toBeNull();
    } finally { await guestContext.close(); }
    const agendaResponse = await request.get('/marcacoes', { headers });
    expect(agendaResponse.ok()).toBeTruthy();
    const agenda = await agendaResponse.json();
    expect(agenda).toHaveLength(1);
    expect(agenda[0]).toMatchObject({ precoPrevisto: 85.50, duracaoMinutos: 30, recebimentoConfirmado: false, podeConfirmarRecebimento: false });
    // Private synthetic history through the API; no direct database changes.
    const pastDate = new Date(`${catalog.hoje}T12:00:00Z`);
    pastDate.setUTCDate(pastDate.getUTCDate() - 1);
    const pastResponse = await request.post('/marcacoes', { headers, data: {
      cliente: { id: agenda[0].cliente.id }, profissionalId: professional.id,
      data: pastDate.toISOString().slice(0,10), horario: '09:00:00', status: 'CONCLUIDA',
      descricao: 'Historico sintetico E2E', duracaoMinutos: 30
    }});
    expect(pastResponse.status()).toBe(201);
    const past = await pastResponse.json();
    const eligibleResponse = await request.get(`/marcacoes/${past.id}`, { headers });
    expect(eligibleResponse.ok()).toBeTruthy();
    expect(await eligibleResponse.json()).toMatchObject({ recebimentoConfirmado: false, podeConfirmarRecebimento: true });
    const historyResponse = await request.post('/marcacoes', { headers, data: {
      cliente: { id: agenda[0].cliente.id }, profissionalId: professional.id,
      data: pastDate.toISOString().slice(0,10), horario: '11:00:00', status: 'CONCLUIDA',
      descricao: `Presenca sintetica E2E ${width}`, duracaoMinutos: 30
    }});
    expect(historyResponse.status()).toBe(201);
    const ownerVisibility = await page.evaluate(() => ({ hidden: document.hidden, visibility: document.visibilityState, focused: document.hasFocus() }));
    test.info().annotations.push({ type: 'owner-before-reload', description: JSON.stringify(ownerVisibility) });
    await page.bringToFront();
    await expect.poll(() => page.evaluate(() => document.visibilityState)).toBe('visible');
    await reloadWorkspaceWithoutSlowFetches(page, request, headers, width);
    await page.locator('[data-view=agenda]').click();
    await expect(page.locator('#agenda-list')).toContainText(`Presenca sintetica E2E ${width}`);
    await expect(page.locator('#agenda-list')).toContainText('Compareceu');
    await page.locator(`[data-confirm-receipt="${past.id}"]`).click();
    await page.locator('#receipt-form [name=valor]').fill('80.25');
    await page.locator('#receipt-save').click();
    await expect(page.locator('#receipt-dialog')).not.toBeVisible();
    await page.locator('[data-view=receipts]').click();
    await expect(page.locator('#receipts-week')).toContainText('80,25');
    await expect(page.locator('#receipts-month')).toContainText('80,25');    await expect(page.locator('#receipts-list')).toContainText(`Cliente UI ${width}`);
    await page.screenshot({ path: `target/marcae-dashboard-${width}.png`, fullPage: true });
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    expect(errors).toEqual([]);
  });
}
