'use strict';

const { test, expect } = require('@playwright/test');
const { randomUUID } = require('node:crypto');
const { runAvailabilityRequestDiagnostics } = require('./diagnostics.cjs');

const APP_ORIGIN = 'http://127.0.0.1:8087';
const AVAILABILITY_PATH = /^\/profissionais\/\d+\/dias-disponiveis$/;
const SAFE_AVAILABILITY_PATH = '/profissionais/{id}/dias-disponiveis';

function captureAvailabilityRequests(page) {
  const attempts = [];
  const byRequest = new WeakMap();
  let currentWindow = null;

  const onRequest = request => {
    if (!currentWindow || request.method() !== 'GET') return;
    let url;
    try { url = new URL(request.url()); } catch { return; }
    if (url.origin !== APP_ORIGIN || !AVAILABILITY_PATH.test(url.pathname)) return;
    const attempt = {
      method: 'GET',
      path: SAFE_AVAILABILITY_PATH,
      status: null,
      responseElapsedMs: null,
      elapsedMs: null,
      failure: null,
      finished: false,
      startedAt: Date.now()
    };
    byRequest.set(request, attempt);
    attempts.push(attempt);
    currentWindow.push(attempt);
  };

  const onResponse = response => {
    const attempt = byRequest.get(response.request());
    if (!attempt) return;
    attempt.status = response.status();
    attempt.responseElapsedMs = Date.now() - attempt.startedAt;
  };

  const onFinished = request => {
    const attempt = byRequest.get(request);
    if (!attempt) return;
    attempt.elapsedMs = Date.now() - attempt.startedAt;
    attempt.finished = true;
  };

  const onFailed = request => {
    const attempt = byRequest.get(request);
    if (!attempt) return;
    const failureText = request.failure()?.errorText || '';
    attempt.failure = failureText.match(/(?:net::)?ERR_[A-Z0-9_]+/i)?.[0] || 'request failed';
    attempt.elapsedMs = Date.now() - attempt.startedAt;
    attempt.finished = true;
  };

  page.on('request', onRequest);
  page.on('response', onResponse);
  page.on('requestfinished', onFinished);
  page.on('requestfailed', onFailed);

  return {
    begin() {
      currentWindow = [];
      return currentWindow;
    },
    end() {
      currentWindow = null;
    },
    dispose() {
      page.off('request', onRequest);
      page.off('response', onResponse);
      page.off('requestfinished', onFinished);
      page.off('requestfailed', onFailed);
      currentWindow = null;
    }
  };
}

test('diagnostico focado da disponibilidade: primeiro GET e repeticao', async ({ page, request }, testInfo) => {
  await page.route('**/*', route => {
    let url;
    try { url = new URL(route.request().url()); } catch { return route.abort(); }
    return url.origin === APP_ORIGIN ? route.continue() : route.abort();
  });

  const nonce = randomUUID();
  const email = 'availability-' + nonce + '@example.test';
  const password = 'SenhaTeste123';
  const signup = await request.post('/auth/estudios', { data: {
    nome: 'Responsavel E2E',
    nomeEstudio: 'Estudio Sintetico Disponibilidade ' + nonce,
    email,
    senha: password
  }});
  if (signup.status() !== 201) throw new Error('Synthetic studio registration failed with HTTP ' + signup.status());

  const login = await request.post('/auth/login', { data: { email, senha: password } });
  if (!login.ok()) throw new Error('Synthetic API login failed with HTTP ' + login.status());
  const loginData = await login.json();
  if (typeof loginData.token !== 'string' || loginData.token.length < 20) {
    throw new Error('Synthetic API login did not return an access token');
  }
  const headers = { Authorization: 'Bearer ' + loginData.token };

  const existingResponse = await request.get('/profissionais', { headers });
  if (!existingResponse.ok()) throw new Error('Synthetic professional lookup failed with HTTP ' + existingResponse.status());
  const existingProfessionals = await existingResponse.json();
  let professional = existingProfessionals.find(item => Number(item.id) === 2);
  const workdays = Array.from({ length: 7 }, (_, index) => ({
    diaSemana: index + 1,
    inicio: '09:00',
    fim: '18:00'
  }));
  for (let index = 0; !professional && index < 3; index += 1) {
    const createdResponse = await request.post('/profissionais', { headers, data: {
      nome: 'Profissional sintetico de disponibilidade ' + index,
      ativo: true,
      horarios: workdays
    }});
    if (createdResponse.status() !== 201) {
      throw new Error('Synthetic professional creation failed with HTTP ' + createdResponse.status());
    }
    const created = await createdResponse.json();
    if (Number(created.id) === 2) professional = created;
  }
  if (!professional) throw new Error('Synthetic fixture could not create professional ID 2');

  await page.goto('/#login');
  await expect(page.locator('#auth-form')).toBeVisible();
  await page.locator('#auth-form [name=email]').fill(email);
  await page.locator('#auth-form [name=senha]').fill(password);
  await page.locator('#auth-submit').click();
  await expect(page.locator('#workspace')).toBeVisible();
  await expect(page.locator('#workspace')).toHaveAttribute('aria-busy', 'false');
  await expect(page.locator('#main-action')).toBeEnabled();
  const authenticated = await page.evaluate(() => Boolean(sessionStorage.getItem('ink.token')?.trim()));
  if (!authenticated) throw new Error('Synthetic browser login did not establish a session');

  const now = new Date();
  const year = now.getFullYear();
  const monthNumber = now.getMonth() + 1;
  const month = String(year) + '-' + String(monthNumber).padStart(2, '0');
  const daysInMonth = new Date(year, monthNumber, 0).getDate();
  const endpoint = '/profissionais/' + professional.id + '/dias-disponiveis' +
    '?inicio=' + month + '-01&dias=' + daysInMonth + '&duracaoMinutos=60';

  const diagnosticsRunId = process.env.E2E_DIAGNOSTICS_RUN_ID;
  const pidFile = process.env.E2E_DIAGNOSTICS_PIDFILE;
  const accessLogPath = process.env.E2E_DIAGNOSTICS_ACCESS_LOG;
  if (!diagnosticsRunId || !pidFile || !accessLogPath) {
    throw new Error('Current Playwright diagnostics handoff is unavailable');
  }

  const capture = captureAvailabilityRequests(page);
  const operationDurations = [];
  try {
    for (const operation of ['availability-first', 'availability-repeat']) {
      const windowAttempts = capture.begin();
      let resultCount = -1;
      await runAvailabilityRequestDiagnostics({
        operation,
        page,
        request,
        headers,
        testInfo,
        pidFile,
        runId: diagnosticsRunId,
        accessLogPath,
        viewportWidth: page.viewportSize()?.width ?? null,
        browserRequests: () => windowAttempts.map(attempt => ({
          method: attempt.method,
          path: attempt.path,
          status: attempt.status,
          responseElapsedMs: attempt.responseElapsedMs,
          elapsedMs: attempt.elapsedMs,
          failure: attempt.failure,
          pending: !attempt.finished
        })),
        action: async () => {
          resultCount = await page.evaluate(async path => {
            if (typeof window.api !== 'function') throw new Error('Browser API helper is unavailable');
            const days = await window.api(path);
            return Array.isArray(days) ? days.length : -1;
          }, endpoint);
          if (resultCount !== daysInMonth) throw new Error('Availability response shape is invalid');
        }
      });
      capture.end();
      expect(windowAttempts).toHaveLength(1);
      const attempt = windowAttempts[0];
      expect(attempt.failure, 'availability request must not fail').toBeNull();
      expect(attempt.status, 'availability request must return HTTP 200').toBe(200);
      expect(attempt.finished, 'availability response body must finish').toBe(true);
      expect(attempt.elapsedMs, 'availability request must finish before the existing 20s client timeout').toBeLessThan(20000);
      expect(resultCount).toBe(daysInMonth);
      operationDurations.push(attempt.elapsedMs);
    }
  } finally {
    capture.dispose();
  }

  expect(operationDurations).toHaveLength(2);
});