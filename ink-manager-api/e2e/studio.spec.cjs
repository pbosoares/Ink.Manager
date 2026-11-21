const { test, expect } = require('@playwright/test');
test.use({ launchOptions: { args: ['--disable-background-timer-throttling', '--disable-renderer-backgrounding', '--disable-backgrounding-occluded-windows'] } });
let cleanupToken;
let cleanupClientId;
async function waitForWorkspace(page) {
  await page.waitForFunction(() => {
    const action = document.querySelector('#main-action');
    const retry = document.querySelector('#page-error button');
    return Boolean(action && !action.disabled) || Boolean(retry && !retry.hidden);
  }, null, { timeout: 30000 });
  if (await page.locator('#main-action').isDisabled()) {
    const failure = await page.locator('#page-error').innerText();
    const requests = page.__inkFailedRequests || [];
    test.info().annotations.push({ type: 'workspace-fetch-failure', description: JSON.stringify({ failure, requests }) });
    await page.locator('#page-error button').click();
  }
  await expect(page.locator('#main-action')).toBeEnabled({ timeout: 30000 });
}
// No celular, Equipe, Relatórios e Meu negócio ficam atrás do botão "Menu" da barra inferior.
async function openView(page, view) {
  const item = page.locator(`.nav-item[data-view=${view}]`);
  if (!(await item.isVisible()) && await page.locator('#menu-toggle').isVisible()) await page.locator('#menu-toggle').click();
  await item.click();
}
function recordWorkspaceFailures(page) {
  page.__inkFailedRequests = [];
  page.on('requestfailed', request => {
    if (new URL(request.url()).origin !== 'http://127.0.0.1:8087') return;
    page.__inkFailedRequests.push({ path: new URL(request.url()).pathname, failure: request.failure()?.errorText });
  });
}
test.afterEach(async ({ request }) => {
  if (!cleanupToken || !cleanupClientId) return;
  const headers = { Authorization: `Bearer ${cleanupToken}` };
  const response = await request.get('/marcacoes', { headers });
  if (!response.ok()) return;
  for (const item of await response.json()) {
    if (item.cliente.id === cleanupClientId) await request.delete(`/marcacoes/${item.id}`, { headers });
  }
  await request.delete(`/clientes/${cleanupClientId}`, { headers });
});
test('cadastro, cliente recorrente, edicao, persistencia e nova sessao', async ({ page, request, browser }) => {
  const email = `e2e-${Date.now()}@example.com`;
  const staffEmail = `staff-${Date.now()}@example.com`;
  const customerName = `Cliente E2E ${Date.now()}`;
  const errors = [];
  recordWorkspaceFailures(page);
  page.on('pageerror', (error) => errors.push(error.message));
  await page.bringToFront();
  await page.goto('/app#login');
  await page.screenshot({ path: 'target/validation-login.png', fullPage: true });
  await page.locator('#toggle-auth').click();
  await page.locator('#auth-form [name=nome]').fill('Equipe E2E');
  await page.locator('#auth-form [name=email]').fill(email);
  await page.locator('#auth-form [name=senha]').fill('SenhaTeste123');
  await page.locator('#auth-form [name=nomeEstudio]').fill('Estudio Autonomo E2E');
  await page.locator('#auth-submit').click();
  await expect(page.locator('#workspace')).toBeVisible();
  await waitForWorkspace(page);
  // Assinatura é tela própria: sem Stripe configurado, nenhuma cobrança pode ser iniciada.
  await openView(page, 'billing');
  await expect(page.locator('#billing-view')).toBeVisible();
  await expect(page.locator('#billing-content .bill-badge')).toHaveText('Avaliação gratuita');
  await expect(page.locator('#billing-submit')).toHaveAttribute('data-billing', 'CARTAO');
  await expect(page.locator('#billing-submit')).toBeDisabled();
  await page.locator('#billing-content input[name=bill-method][value=PIX]').check();
  await expect(page.locator('#billing-submit')).toHaveAttribute('data-billing', 'PIX');
  await expect(page.locator('#billing-submit')).toHaveText('Pagar mensalidade com Pix');
  await expect(page.locator('#billing-submit')).toBeDisabled();
  await expect(page.locator('#studio-view')).toBeHidden();
  await page.locator('.nav-item[data-view=studio]').click();
  await expect(page.locator('#billing-settings')).toHaveCount(0);
  await page.locator('#studio-name-form [name=nome]').fill('Estudio Personalizado E2E');
  await page.locator('#studio-name-form button').click();
  await expect(page.locator('#account-name')).toHaveText('Estudio Personalizado E2E');
  await page.locator('#account-form [name=nome]').fill('Recepcao E2E');
  await page.locator('#account-form [name=email]').fill(staffEmail);
  await page.locator('#account-form [name=senha]').fill('SenhaTeste123');
  await page.locator('#account-form button').click();
  await expect(page.locator('#account-list')).toContainText(staffEmail);
  await page.locator('[data-view=clients]').click();
  await page.locator('#main-action').click();
  await page.locator('#editor [name=nome]').fill(customerName);
  await page.locator('#editor [name=telefone]').fill('21988887777');
  await page.locator('#save-editor').click();
  await expect(page.locator('#editor')).not.toBeVisible();
  await expect(page.locator('#client-list')).toContainText(customerName);
  const token = await page.evaluate(() => sessionStorage.getItem('ink.token'));
  await expect(page.locator('#account-name')).toHaveText('Estudio Personalizado E2E');
  const headers = { Authorization: `Bearer ${token}` };
  cleanupToken = token;
  const clients = await (await request.get('/clientes', { headers })).json();
  const customer = clients.find((c) => c.nome === customerName);
  cleanupClientId = customer.id;
  await page.locator('[data-view=team]').click();
  await page.locator('#new-professional').click();
  await page.locator('#professional-form [name=nome]').fill('Tatuador E2E');
  await page.locator('#save-professional').click();
  await expect(page.locator('#professional-dialog')).not.toBeVisible();
  await expect(page.locator('#professional-list')).toContainText('Tatuador E2E');
  const professionals = await (await request.get('/profissionais', { headers })).json();
  const professional = professionals.find(p => p.nome === 'Tatuador E2E');
  for (const time of ['10:00', '11:00']) {
    await page.locator('[data-view=agenda]').click();
    await page.locator('#main-action').click();
    await page.locator('#client-mode').selectOption('existing');
    await page.locator('#existing-client [name=clienteId]').selectOption(String(customer.id));
    await page.locator('#editor [name=profissionalId]').selectOption(String(professional.id));
    await page.locator('#editor [name=data]').fill('2098-01-15');
    await page.locator('#editor [name=horario]').selectOption(time);
    await page.locator('#editor [name=descricao]').fill(`Sessao E2E ${time}`);
    await page.locator('#save-editor').click();
    await expect(page.locator('#editor')).not.toBeVisible();
    await waitForWorkspace(page);
  }
  await page.locator('#agenda-search').fill(customerName);
  await expect(page.locator('#agenda-list tbody tr')).toHaveCount(2);
  await page.locator('#agenda-list [data-edit-appointment]').first().click();
  await page.locator('#editor [name=status]').selectOption('CONFIRMADA');
  await page.locator('#save-editor').click();
  await expect(page.locator('#editor')).not.toBeVisible();
  await expect(page.locator('#agenda-list')).toContainText('Confirmada');
  expect((await (await request.get('/clientes', { headers })).json()).length).toBe(clients.length);
  await page.reload();
  await waitForWorkspace(page);
  await page.locator('[data-view=agenda]').click();
  await page.locator('#agenda-search').fill(customerName);
  await expect(page.locator('#agenda-list tbody tr')).toHaveCount(2);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.screenshot({ path: 'target/validation-mobile.png', fullPage: true });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  const staffContext = await browser.newContext();
  try {
    const staffPage = await staffContext.newPage();
    await staffPage.bringToFront();
    await staffPage.goto('/app#login');
    await staffPage.locator('#auth-form [name=email]').fill(staffEmail);
    await staffPage.locator('#auth-form [name=senha]').fill('SenhaTeste123');
    await staffPage.locator('#auth-submit').click();
    await expect(staffPage.locator('#main-action')).toBeEnabled();
    await expect(staffPage.locator('.nav-item[data-view=studio]')).toBeHidden();
    await staffPage.locator('[data-view=agenda]').click();
    await staffPage.locator('#agenda-search').fill(customerName);
    await expect(staffPage.locator('#agenda-list tbody tr')).toHaveCount(2);
    await staffPage.locator('#main-action').click();
    await staffPage.locator('#client-mode').selectOption('existing');
    await staffPage.locator('#existing-client [name=clienteId]').selectOption(String(customer.id));
    await staffPage.locator('#editor [name=profissionalId]').selectOption(String(professional.id));
    await staffPage.locator('#editor [name=data]').fill('2098-01-15');
    await staffPage.locator('#editor [name=horario]').selectOption('12:00');
    await staffPage.locator('#editor [name=descricao]').fill('Reserva pela recepcao');
    await staffPage.locator('#save-editor').click();
    await expect(staffPage.locator('#editor')).not.toBeVisible();
    await page.bringToFront();
    await expect(page.locator('#agenda-list tbody tr')).toHaveCount(3,{timeout:25000});
    const accounts = await (await request.get('/equipe',{headers})).json();
    const staff = accounts.find(account => account.email === staffEmail);
    await openView(page, 'studio');
    await page.locator(`[data-account="${staff.id}"]`).click();
    await expect(page.locator(`[data-account="${staff.id}"]`)).toContainText('Reativar');
    await staffPage.reload();
    await expect(staffPage.locator('#auth')).toBeVisible();
  } finally { await staffContext.close(); }
  if (!(await page.locator('#logout').isVisible())) await page.locator('#menu-toggle').click();
  await page.locator('#logout').click();
  await expect(page.locator('#auth')).toBeVisible();
  await page.locator('#auth-form [name=email]').fill(email);
  await page.locator('#auth-form [name=senha]').fill('SenhaTeste123');
  await page.locator('#auth-submit').click();
  await waitForWorkspace(page);
  cleanupToken = await page.evaluate(() => sessionStorage.getItem('ink.token'));
  expect(errors).toEqual([]);
});
test('landing separada do painel: CTAs e links antigos levam ao app', async ({ page }) => {
  const errors = [];
  const assets = [];
  page.on('pageerror', (error) => errors.push(error.message));
  page.on('request', (request) => { if (/\.(css|js)$/.test(new URL(request.url()).pathname)) assets.push(new URL(request.url()).pathname); });
  await page.goto('/');
  await expect(page.locator('#landing-title')).toBeVisible();
  await expect(page.locator('#workspace, #auth')).toHaveCount(0);
  expect(assets.sort()).toEqual(['/landing.css', '/landing.js']);
  await page.locator('#landing-trial').click();
  await expect(page).toHaveURL(/\/app#cadastro$/);
  await expect(page.locator('#auth-form [name=nomeEstudio]')).toBeVisible();
  await expect(page.locator('#auth-form [name=nomeEstudio]')).toBeFocused();
  await page.goto('/');
  await page.locator('.marketing-login').click();
  await expect(page).toHaveURL(/\/app#login$/);
  await expect(page.locator('#auth-form [name=email]')).toBeVisible();
  await expect(page.locator('#auth-form [name=nomeEstudio]')).toBeHidden();
  // Links antigos (favoritos, e-mails) que apontavam para a landing.
  await page.goto('/#login');
  await expect(page).toHaveURL(/\/app#login$/);
  await expect(page.locator('#auth-form')).toBeVisible();
  await page.goto('/app');
  await expect(page.locator('#auth-form')).toBeVisible();
  expect(errors).toEqual([]);
});
